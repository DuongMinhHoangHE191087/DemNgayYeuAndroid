"""Ingestion cho Monetization Brain — mốc 1: chỉ nhận và lưu bền.

Chưa train gì cả, và đó là chủ đích (doc 46 mục D): không có log sạch thì không train được,
và phải tích log một thời gian trước khi có đủ dữ liệu để tính bất cứ thứ gì.

Ba ràng buộc từ doc 46 mục C, tất cả đều xuất phát từ việc hàng đợi phía SDK chỉ xoá bản ghi
khi thấy 2xx:

  1. Chỉ trả 2xx SAU KHI đã ghi bền.
  2. Chịu được bản ghi trùng (`dedup_key`).
  3. Chịu được bản ghi tới rất muộn — ngày báo cáo theo `client_ts_ms`; phân vùng vật lý theo `received_date` (ngày server nhận) để hàng muộn không rơi vào phân vùng đã hết hạn (1.7.0 F2-be).

1.7.0 (T9): `create_app(settings)` dựng app từ cấu hình (module giữ `app` cho uvicorn); khoá theo
app (`auth.py`), giới hạn tốc độ (`ratelimit.py`), lỗi có cấu trúc + log JSON (`errors.py`),
giới hạn kích thước lô, `/health` kiểm sẵn sàng thật.

1.8.0 (T13): `POST /rtdn` (RTDN của Play qua Pub/Sub push, `rtdn.py`) và `signals` trong phản hồi 200 của `/push`
(§Hợp đồng C: nhãn vòng đời subscription của người gửi lô; không bao giờ quyền lợi).
"""

from __future__ import annotations

import json
import logging
import time
from pathlib import Path
from typing import Any

from fastapi import APIRouter, FastAPI, Header, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import HTMLResponse

from . import metrics, ope, play_verify, purchase_reconcile, reconcile, rtdn
from .auth import ApiKeyAuth
from .config import Settings, build_bq_client, build_reader, build_sink, build_verifier
from .errors import (ApiError, RequestContextMiddleware, configure_request_logging,
                     error_response, install_error_handlers)
from .models import EventRequest
from .normalize import ingest_row
from .policy_route import client_key, install_policy_state, issue_nonce, require_auth_on, resolve_policy
from .ratelimit import IngestLimiter, retry_after_seconds

log = logging.getLogger("brain.ingest")

# Trần một lô `/push`: body 256 KB, 500 event. Lớn hơn = 413 (client chia nhỏ, không gửi lại y hệt).
MAX_BODY_BYTES = 256 * 1024
MAX_EVENTS_PER_BATCH = 500

_DASHBOARD = Path(__file__).parent / "static" / "dashboard.html"

router = APIRouter()


def create_app(settings: Settings) -> FastAPI:
    """Dựng app từ cấu hình. Cấu hình sai (khoá hỏng, endpoint emulator với project thật…) ném
    ngay ở đây: thà không khởi động còn hơn chạy với xác thực/đích ghi sai."""
    settings.validate()
    auth = ApiKeyAuth.from_settings(settings.api_keys, settings.api_key)
    bq_client = build_bq_client(settings) if settings.sink == "bigquery" else None

    application = FastAPI(title="Monetization Brain — Ingestion", version="0.2.0")
    application.state.settings = settings
    application.state.auth = auth
    application.state.limiter = IngestLimiter(settings.rate_app_rps, settings.rate_app_burst,
                                              settings.rate_user_rps, settings.rate_user_burst)
    application.state.sink = build_sink(settings, bq_client)
    application.state.reader = build_reader(settings, bq_client)
    application.state.verifier = build_verifier(settings)
    install_policy_state(application, settings)

    configure_request_logging()
    install_error_handlers(application)
    application.add_middleware(RequestContextMiddleware)
    application.include_router(router)
    rtdn.install(application, settings, bq_client)
    return application


def _authenticate(request: Request, x_api_key: str | None) -> str:
    """app_id của khoá (dev: `dev`); sai/thiếu khoá -> 401. Không bao giờ ghi khoá ra log."""
    app_id = request.app.state.auth.resolve(x_api_key)
    if app_id is None:
        raise ApiError(401, "unauthorized", "invalid api key")
    request.state.app_id = app_id
    return app_id


def _raise_if_limited(wait: float) -> None:
    if wait > 0:
        seconds = retry_after_seconds(wait)
        raise ApiError(429, "rate_limited", f"quá hạn mức, thử lại sau {seconds} giây",
                       headers={"Retry-After": str(seconds)})


async def _read_body(request: Request, limit: int) -> bytes:
    """Đọc body có trần: từ chối theo `Content-Length` trước khi đọc, và đếm khi stream (body
    chunked không có `Content-Length`) — không bao giờ giữ quá `limit` byte trong RAM."""
    too_big = ApiError(413, "payload_too_large",
                       f"body vượt {limit} byte hoặc {MAX_EVENTS_PER_BATCH} event; chia nhỏ lô")
    declared = request.headers.get("content-length")
    if declared is not None:
        try:
            if int(declared) > limit:
                raise too_big
        except ValueError as exc:
            raise ApiError(400, "bad_request", "Content-Length không hợp lệ") from exc
    size = 0
    chunks: list[bytes] = []
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise too_big
        chunks.append(chunk)
    return b"".join(chunks)


def _parse_events(raw_body: bytes) -> list[Any]:
    try:
        body = json.loads(raw_body)
    except (ValueError, RecursionError) as exc:
        # 400 chứ không 500: JSON hỏng thì gửi lại bao nhiêu lần cũng hỏng, và client nên
        # bỏ bản ghi đó đi thay vì kẹt lại trong hàng đợi mãi mãi.
        raise ApiError(400, "malformed_json", "body không phải JSON hợp lệ") from exc
    events = body if isinstance(body, list) else [body]
    if len(events) > MAX_EVENTS_PER_BATCH:
        raise ApiError(413, "payload_too_large",
                       f"lô vượt {MAX_EVENTS_PER_BATCH} event; chia nhỏ lô")
    return events


@router.get("/health")
def health(request: Request) -> Any:
    """Sẵn sàng nhận ghi (không chỉ "process còn sống"): JSONL ghi được thư mục, BigQuery đọc
    được dataset. Không cần khoá (probe của hạ tầng không mang khoá); không lộ đường dẫn/project."""
    st = request.app.state
    if not st.sink.ping():
        return error_response(request, 503, "sink_unavailable", "sink chưa sẵn sàng ghi")
    return {"ok": True, "sink": st.sink.kind(), "auth": st.auth.enabled,
            "version": st.settings.version}


@router.post("/push")
async def push(request: Request, x_api_key: str | None = Header(default=None)) -> dict[str, Any]:
    """Đường vào duy nhất. Khớp `LogService.logActionsToServer` (`@POST("push")`).

    Chấp nhận cả một event lẻ lẫn một mảng: SDK hiện gửi từng cái một, nhưng nhận sẵn mảng
    thì sau này gộp batch không phải đổi endpoint và không phải điều phối hai phía cùng lúc.
    Thứ tự kiểm: khoá (401) -> hạn mức app (429) -> kích thước (413) -> JSON (400) -> hạn mức
    user (429) -> ghi bền -> 200 kèm `signals` (§Hợp đồng C, đọc SAU khi ghi; lỗi đọc = `[]`, không 5xx).
    """
    st = request.app.state
    app_id = _authenticate(request, x_api_key)
    _raise_if_limited(st.limiter.check_app(app_id))
    raw_events = _parse_events(await _read_body(request, MAX_BODY_BYTES))

    rows: list[dict[str, Any]] = []
    users: set[str] = set()
    rejected = 0
    received_ms = int(time.time() * 1000)
    for raw in raw_events:
        try:
            req = EventRequest.model_validate(raw)
        except Exception as exc:  # noqa: BLE001 — một event hỏng KHÔNG làm hỏng cả lô
            rejected += 1
            # Chỉ số lỗi, không in giá trị đầu vào (dữ liệu người dùng).
            log.warning("bo qua event khong hop le: %s", type(exc).__name__)
            continue
        # app_id do KHOÁ quyết định, không phải body.
        rows.append(ingest_row(req, app_id, received_ms))
        users.add(req.userProperty.user_id)
    request.state.rejected = rejected

    if not rows:
        raise ApiError(400, "no_valid_events", "không có event hợp lệ nào")
    _raise_if_limited(st.limiter.check_users(app_id, users))

    # Ghi bền TRƯỚC khi trả lời (ngoài event loop: sink BigQuery là I/O chặn). Nếu ném ra thì
    # trả 500 và client giữ lại bản ghi — đúng hành vi mong muốn.
    await run_in_threadpool(st.sink.write, rows)
    request.state.accepted = len(rows)
    signals = await rtdn.push_signals(request, app_id, users)
    return {"ok": True, "accepted": len(rows), "rejected": rejected, "signals": signals}


@router.get("/metrics/northstar")
def northstar(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Bốn North Star Metric — **luôn trả về cả bốn**.

    Endpoint này CỐ Ý không có tham số chọn tập con chỉ số. Doc 26 mục C đặt ràng buộc: không
    được xem riêng ARPDAU/Revenue mà ẩn ComfortIndex, vì đó chính là cách một đội vô tình tối
    ưu ba chỉ số đầu bằng cách hy sinh chỉ số thứ tư.

    Ràng buộc kiểu này chỉ có tác dụng khi nó nằm trong kiến trúc chứ không nằm trong quy ước:
    ở đây, muốn giấu ComfortIndex thì phải sửa code và vượt qua một unit test.
    """
    _authenticate(request, x_api_key)
    rows = request.app.state.reader.read(date_from, date_to)
    result = metrics.compute(rows, request.app.state.settings.fx_rates())
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result


@router.get("/metrics/paywall")
def paywall_funnel(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Phễu paywall: biến thể nào bán, và có paywall so với KHÔNG có paywall.

    Nửa phân tích của holdout native (doc 49 mục E.2): phía SDK đã gửi `paywall_impression`
    (mẫu số treatment) và `paywall_holdout_skip` kèm biến thể lẽ-ra-đã-hiện (mẫu số holdout);
    endpoint này ghép chúng với `iap_purchase` để trả lời câu hỏi giá trị tuyệt đối mà không
    vendor paywall nào trả lời được (doc 19 mục 5).
    """
    _authenticate(request, x_api_key)
    rows = request.app.state.reader.read(date_from, date_to)
    result = metrics.paywall_funnel(rows, request.app.state.settings.fx_rates())
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result


@router.get("/reconcile/buckets")
def reconcile_buckets(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    holdout_fraction: float | None = None,
    rollout_fraction: float | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Đối soát bucket thí nghiệm — quyết định 2 mục B.2 doc 57.

    Server KHÔNG tin cờ membership phía client khai: tái tính bucket từ
    (salt_version, user_id) bằng đúng hàm băm FNV-1a của SDK và kiểm tra ba lớp
    (tái tính bucket → đơn điệu ngưỡng → cờ chính xác nếu được cấp fraction).
    `verdict` trong kết quả là kết luận một dòng cho pipeline cảnh báo.

    Tham số fraction là TUỲ CHỌN: không có chúng thì hai lớp đầu vẫn chạy —
    đủ để phát hiện log hỏng và cờ tự mâu thuẫn mà không cần biết cấu hình.
    """
    _authenticate(request, x_api_key)
    rows = request.app.state.reader.read(date_from, date_to)
    result = reconcile.compute(rows, holdout_fraction, rollout_fraction)
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result

@router.get("/reconcile/purchases")
def reconcile_purchases(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Đối soát giao dịch IAP — biên nhận bị dùng lại giữa các tài khoản (doc 45 mục E.1).

    Thuần trên hàng đã lưu, không gọi API ngoài: token/order xuyên tài khoản và
    token-nhiều-đơn ngoài subscription là ba nghi vấn gian lận cổ điển mà dedup ingestion
    (khoá theo order_id, không kèm user_id — doc 46 mục C.2) cố ý không chặn. `verdict`
    cho pipeline cảnh báo; `play_rows_missing_token` là độ phủ dữ liệu cho job xác thực
    Play Developer API sau này, không phải nghi vấn.
    """
    _authenticate(request, x_api_key)
    rows = request.app.state.reader.read(date_from, date_to)
    result = purchase_reconcile.compute(rows)
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result


@router.get("/verify/purchases")
def verify_purchases(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Xác thực giao dịch Play Billing với Google — doc 45 mục E.1.

    Chỉ tồn tại khi đã cấu hình `BRAIN_PLAY_CREDENTIALS` + `BRAIN_PLAY_PACKAGE`; chưa cấu
    hình thì 503 nói thẳng chứ KHÔNG trả kết quả giả — một dashboard xanh từ verifier giả
    nguy hiểm hơn một endpoint từ chối. Kết quả phân loại từng giao dịch (verified /
    order_mismatch / canceled_or_refunded / invalid_token / pending / độ phủ); module này
    KHÔNG cấp hay thu hồi entitlement — đó là việc server của studio (doc 23 mục C.2).
    """
    _authenticate(request, x_api_key)
    verifier = request.app.state.verifier
    if verifier is None:
        raise ApiError(
            503, "not_configured",
            "Chưa cấu hình xác thực Play: đặt BRAIN_PLAY_CREDENTIALS (service account "
            "JSON với quyền androidpublisher) và BRAIN_PLAY_PACKAGE (tên gói Android).",
        )
    rows = request.app.state.reader.read(date_from, date_to)
    result = play_verify.compute(rows, verifier)
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result


@router.get("/ope/policies")
def ope_policies(
    request: Request,
    date_from: str | None = None,
    date_to: str | None = None,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Off-policy evaluation: policy tham chiếu đáng giá bao nhiêu so với hành vi đang chạy.

    Ghép `brain_decision` với `brain_feedback` theo `decision_id` và tính IPS/SNIPS
    (doc 28 mục H.1). Đầu vào bắt buộc là hai trường không bao giờ tái tạo được
    (`chosen_action_probability`, `eligible_actions`) cộng `reward_value` — thiếu reward
    thì estimator vô hiệu, và đó là lý do SDK gửi reward nguyên bản lên đây thay vì chỉ
    học trên máy (doc 46 mục B.1). Chạy thuần trên hàng đã lưu; module này KHÔNG đổi
    policy thật — nó chỉ trả lời "nếu ngày đó chọn đều / chọn không-làm-gì thì reward
    kỳ vọng là bao nhiêu".
    """
    _authenticate(request, x_api_key)
    rows = request.app.state.reader.read(date_from, date_to)
    result = ope.compute(rows)
    result["window"] = {"date_from": date_from, "date_to": date_to}
    return result


def _policy_gate(request: Request, x_api_key: str | None) -> str:
    require_auth_on(request.app.state)  # 503 khi không có khoá; rồi khoá TRƯỚC, rồi hạn mức theo client và app_id
    app_id = _authenticate(request, x_api_key)
    st = request.app.state  # bucket (app_id, client) TRƯỚC: client đã bị chặn không tiêu token của app (D21-230); rồi trần app
    _raise_if_limited(st.policy_limiter.acquire(f"{app_id}\x00{client_key(request)}"))
    _raise_if_limited(st.policy_app_limiter.acquire(app_id))
    return app_id


@router.post("/policy/nonce")
def policy_nonce(request: Request, x_api_key: str | None = Header(default=None)) -> Any:
    return issue_nonce(request.app.state, _policy_gate(request, x_api_key))


@router.get("/policy")
def policy(request: Request, x_api_key: str | None = Header(default=None),
           x_integrity_token: str | None = Header(default=None)) -> Any:
    return resolve_policy(request.app.state, _policy_gate(request, x_api_key), x_integrity_token)


@router.get("/", response_class=HTMLResponse)
def dashboard() -> HTMLResponse:
    """Dashboard vận hành — bốn North Star cùng một màn hình.

    Phục vụ từ chính backend, không phải một trang tĩnh ở nơi khác: trang này phải gọi được
    `/metrics/northstar`, và đặt nó cùng origin là cách duy nhất tránh CORS cũng như tránh
    việc phải cấu hình thêm một chỗ nữa.

    Trang KHÔNG tự xác thực. Nếu `BRAIN_API_KEY` được đặt thì `/metrics/northstar` trả 401 và
    dashboard hiện đúng lỗi đó — cố ý: nhúng khoá vào HTML để trang "chạy được" là phát khoá
    API cho bất kỳ ai mở được URL. Đặt dashboard sau một lớp xác thực hạ tầng (IAP của Cloud
    Run, VPN) thay vì tự chế xác thực ở đây.
    """
    return HTMLResponse(_DASHBOARD.read_text(encoding="utf-8"))


# Điểm vào của uvicorn (`app.main:app`). Các tên dưới đây trỏ vào CHÍNH đối tượng app dùng: vá
# THUỘC TÍNH của chúng thì endpoint thấy (vd. `monkeypatch.setattr(app.main.sink, "write", …)`).
# Gán lại chính cái tên (`app.main.sink = X`) thì KHÔNG tới endpoint — endpoint đọc `app.state`.
app = create_app(Settings.from_env())
settings: Settings = app.state.settings
sink = app.state.sink
reader = app.state.reader
verifier = app.state.verifier
