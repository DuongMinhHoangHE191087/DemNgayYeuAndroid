"""`POST /rtdn` — Real-time developer notifications của Play qua Pub/Sub push (1.8.0 T13, D9) + `signals` của `/push`.

Luồng: Play -> Pub/Sub topic -> push subscription -> `POST /rtdn` với envelope
`{"message": {"data": <base64 DeveloperNotification>, "messageId", "attributes", …}, "subscription"}`.
Thứ tự kiểm: bearer (503 khi chưa cấu hình, 401) -> kích thước (413) -> envelope (400) -> tra token -> ghi bền -> 200.
Pub/Sub coi 2xx là ack; mọi mã khác = gửi lại (tới hết hạn giữ của subscription). Nên 2xx chỉ SAU khi ghi bền (doc 46
C.1), 400 chỉ cho envelope không bao giờ hợp lệ, 5xx cho lỗi tạm (ghi hỏng, nguồn chưa nối).

Xác thực: cục bộ/stack thử = bearer tĩnh `BRAIN_RTDN_TOKEN` (so digest SHA-256 bằng `hmac.compare_digest`, như khoá
API). Production = Pub/Sub push kèm token OIDC của service account, kiểm chữ ký/audience — **H7b** (chủ dự án: topic,
push subscription, OIDC; RUNBOOK). Hàng `subscription_events` KHÔNG cấp/thu hồi quyền lợi: `signals` chỉ đặt nhãn vòng
đời phía SDK (§Hợp đồng C, D9).
"""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import logging
import time
from typing import Any

from fastapi import APIRouter, FastAPI, Header, Request
from fastapi.concurrency import run_in_threadpool

from .errors import ApiError
from .normalize import RTDN_SUBSCRIPTION_STATES, RTDN_VOIDED_STATE, subscription_event_row
from .signals_cache import MISS, SignalsCache
from .sinks.subscription_events import build_subscription_store
from .subscription_source import SourceNotConfigured, build_subscription_source

log = logging.getLogger("brain.rtdn")
router = APIRouter()

# Một DeveloperNotification cỡ vài trăm byte; 64 KB là trần rộng mà vẫn không giữ body lạ trong RAM.
MAX_RTDN_BODY_BYTES = 64 * 1024
MAX_MESSAGE_ID_LEN = 128
# §Hợp đồng C: ≤ 5 phần tử, ≤ 7 ngày (giờ server NHẬN RTDN — Pub/Sub giữ tin ≤ 7 ngày nên gần giờ sự kiện ở
# production; fixture có `eventTimeMillis` cố định vẫn dùng được mọi ngày), theo `user_id` của lô.
SIGNAL_WINDOW_MS = 7 * 86_400_000
MAX_SIGNALS = 5
SIGNAL_STATES = frozenset({"GRACE", "ON_HOLD", "PAUSED", "CANCELED", "RECOVERED", "RENEWED", "RESTARTED",
                           "PURCHASED", "REVOKED", "EXPIRED"})
# Khoá trong DeveloperNotification -> `kind` của hàng. Không khoá nào khớp -> "other" (vẫn lưu nguyên văn).
_KINDS = (("subscriptionNotification", "subscription"), ("voidedPurchaseNotification", "voided"),
          ("oneTimeProductNotification", "one_time"), ("testNotification", "test"))


def _now_ms() -> int:
    return int(time.time() * 1000)


def _digest(value: str) -> bytes:
    return hashlib.sha256(value.encode("utf-8")).digest()


class BearerAuth:
    """Chỉ giữ digest của bearer. So sánh luôn chạy (kể cả sai scheme/rỗng): thời gian không lộ phần nào khớp."""

    def __init__(self, token: str) -> None:
        self._digest = _digest(token) if token else None

    @property
    def enabled(self) -> bool:
        return self._digest is not None

    def check(self, authorization: str | None) -> bool:
        if self._digest is None:
            return False
        scheme, _, presented = (authorization or "").strip().partition(" ")
        presented = presented.strip()
        matched = hmac.compare_digest(_digest(presented), self._digest)
        return matched and scheme.lower() == "bearer" and bool(presented)


def install(application: FastAPI, settings: Any, bq_client: Any = None) -> None:
    application.state.rtdn_auth = BearerAuth(settings.rtdn_token)
    application.state.signals_cache = SignalsCache()
    application.state.rtdn_store = build_subscription_store(settings, bq_client)
    application.state.subscription_source = build_subscription_source(settings)
    application.include_router(router)


def _authorize(request: Request, authorization: str | None) -> None:
    auth: BearerAuth = request.app.state.rtdn_auth
    if not auth.enabled:
        raise ApiError(503, "not_configured", "Chưa cấu hình /rtdn: đặt BRAIN_RTDN_TOKEN (cục bộ); production "
                                              "dùng token OIDC của Pub/Sub push (H7b).")
    if not auth.check(authorization):
        raise ApiError(401, "unauthorized", "invalid bearer", headers={"WWW-Authenticate": "Bearer"})


async def _read_capped(request: Request, limit: int) -> bytes:
    too_big = ApiError(413, "payload_too_large", f"body vượt {limit} byte")
    declared = request.headers.get("content-length")
    if declared is not None and declared.isdigit() and int(declared) > limit:
        raise too_big
    size, chunks = 0, []
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            raise too_big
        chunks.append(chunk)
    return b"".join(chunks)


def _bad(message: str) -> ApiError:
    return ApiError(400, "bad_envelope", message)


def parse_envelope(raw: bytes) -> tuple[str, dict[str, Any]]:
    """(messageId, DeveloperNotification) hoặc 400 — không bao giờ in giá trị đầu vào."""
    try:
        body = json.loads(raw)
    except (ValueError, RecursionError) as exc:
        raise ApiError(400, "malformed_json", "body không phải JSON hợp lệ") from exc
    message = body.get("message") if isinstance(body, dict) else None
    if not isinstance(message, dict):
        raise _bad("thiếu message (envelope Pub/Sub push)")
    message_id = message.get("messageId") or message.get("message_id")
    if not isinstance(message_id, str) or not message_id or len(message_id) > MAX_MESSAGE_ID_LEN:
        raise _bad("message.messageId thiếu hoặc không hợp lệ")
    data = message.get("data")
    try:
        note = json.loads(base64.b64decode(data, validate=True)) if isinstance(data, str) else None
    except (binascii.Error, ValueError, RecursionError):
        note = None
    if not isinstance(note, dict):
        raise _bad("message.data không phải base64 của một DeveloperNotification JSON")
    return message_id, note


def notification_fields(note: dict[str, Any]) -> dict[str, Any]:
    """Trường chuẩn hoá của DeveloperNotification; hình sai -> 400."""
    kind, body = "other", {}
    for key, name in _KINDS:
        if isinstance(note.get(key), dict):
            kind, body = name, note[key]
            break
    raw_ms = note.get("eventTimeMillis")
    if isinstance(raw_ms, bool) or not str(raw_ms).isdigit():
        raise _bad("eventTimeMillis thiếu hoặc không phải số")
    token = body.get("purchaseToken", "")
    if kind in ("subscription", "voided", "one_time") and (not isinstance(token, str) or not token):
        raise _bad(f"{kind}: purchaseToken thiếu")
    ntype = body.get("notificationType")
    if kind in ("subscription", "one_time") and (isinstance(ntype, bool) or not isinstance(ntype, int)):
        raise _bad(f"{kind}: notificationType thiếu hoặc không phải số nguyên")
    state = ""
    if kind == "subscription":
        state = RTDN_SUBSCRIPTION_STATES.get(ntype, "")
    elif kind == "voided":
        state = RTDN_VOIDED_STATE
    legacy_product = body.get("subscriptionId") or body.get("sku") or ""
    refund = body.get("refundType")
    return {"kind": kind, "event_ms": int(raw_ms), "package_name": str(note.get("packageName") or "") or None,
            "notification_type": ntype if isinstance(ntype, int) else None, "state": state,
            "purchase_token": token if isinstance(token, str) else "", "order_id": str(body.get("orderId") or ""),
            "legacy_product": legacy_product if isinstance(legacy_product, str) else "",
            "refund_type": refund if isinstance(refund, int) else None}


def _build_row(st: Any, message_id: str, note: dict[str, Any], fields: dict[str, Any]) -> dict[str, Any]:
    """Tra token -> người mua + sản phẩm (I/O chặn: chạy trong threadpool)."""
    token = fields["purchase_token"]
    purchase = st.rtdn_store.purchase_for_token(token) if token else None
    product = fields["legacy_product"] or (purchase or {}).get("product_id") or ""
    if not product and token and fields["kind"] == "subscription":
        try:
            product = st.subscription_source.lookup(fields["package_name"] or "", token) or ""
        except SourceNotConfigured as exc:
            raise ApiError(503, "not_configured", str(exc)) from exc
    return subscription_event_row(message_id=message_id, received_ms=_now_ms(), note=note, fields=fields,
                                  product_id=product, purchase=purchase)


@router.post("/rtdn")
async def rtdn(request: Request, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    st = request.app.state
    _authorize(request, authorization)
    message_id, note = parse_envelope(await _read_capped(request, MAX_RTDN_BODY_BYTES))
    fields = notification_fields(note)
    if fields["kind"] == "test":
        # Nút "gửi thông báo thử" của Play Console: chỉ xác nhận đường nối, không phải dữ liệu.
        return {"ok": True, "stored": False, "kind": "test"}
    row = await run_in_threadpool(_build_row, st, message_id, note, fields)
    # Ghi bền TRƯỚC khi trả lời; ném ra -> 500 và Pub/Sub gửi lại.
    stored = await run_in_threadpool(st.rtdn_store.write, row)
    st.signals_cache.invalidate(row["app_id"], row["user_id"])  # signal mới ra ở /push kế tiếp, không chờ hết TTL
    if row["user_id"] is None:
        # Không in token (bí mật của người mua): chỉ messageId/loại/trạng thái.
        log.warning("rtdn chua biet nguoi mua message_id=%s kind=%s state=%s: luu, khong co signal",
                    message_id, row["kind"], row["state"] or "-")
    if fields["kind"] == "subscription" and not row["state"]:
        log.warning("rtdn notificationType=%s chua anh xa: luu, khong co signal", row["notification_type"])
    return {"ok": True, "stored": stored, "kind": row["kind"], "state": row["state"]}


def build_signals(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Trạng thái-signal mới nhất (theo `event_ms`) của mỗi sản phẩm; 5 sản phẩm mới nhất, tăng dần theo `event_ms`."""
    latest: dict[str, dict[str, Any]] = {}
    for r in rows:
        product, state, event_ms = r.get("product_id"), r.get("state"), r.get("event_ms")
        if r.get("kind") != "subscription" or state not in SIGNAL_STATES or not product or event_ms is None:
            continue
        rank = (int(event_ms), int(r.get("received_ts_ms") or 0))
        if product not in latest or rank > latest[product]["rank"]:
            latest[product] = {"rank": rank, "product_id": product, "state": state, "event_ms": int(event_ms)}
    newest = sorted(latest.values(), key=lambda s: s["rank"], reverse=True)[:MAX_SIGNALS]
    return [{"v": 1, "type": "sub_state", "product_id": s["product_id"], "state": s["state"],
             "event_ms": s["event_ms"]} for s in sorted(newest, key=lambda s: s["rank"])]


async def push_signals(request: Request, app_id: str, users: set[str]) -> list[dict[str, Any]]:
    """`signals` cho phản hồi 200 của `/push` (§C): chỉ khi lô có đúng MỘT `user_id`, chỉ hàng cùng app của khoá.

    Gọi SAU khi lô đã ghi bền: đọc hỏng thì trả `[]` và ghi log — không biến một lần ghi thành công thành 5xx (client
    sẽ gửi lại cả lô); SDK chỉ mất một lượt nhãn, lượt `/push` kế đọc lại.
    """
    if len(users) != 1:
        return []
    (user_id,) = users
    if not user_id:
        return []
    cache: SignalsCache = request.app.state.signals_cache
    cached = cache.get(app_id, user_id)
    if cached is not MISS:
        return cached
    since = _now_ms() - SIGNAL_WINDOW_MS
    try:
        rows = await run_in_threadpool(request.app.state.rtdn_store.recent, app_id, user_id, since)
    except Exception as exc:  # noqa: BLE001 — mọi lỗi đọc đều là "không có signal lượt này"
        log.warning("signals bo qua: doc subscription_events loi %s", type(exc).__name__)
        return []
    signals = build_signals(rows)
    cache.put(app_id, user_id, signals)  # lỗi đọc (nhánh trên) KHÔNG được lưu: lượt sau đọc lại
    return signals
