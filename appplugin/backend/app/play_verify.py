"""Xác thực giao dịch Play Billing phía server — vế còn lại của doc 45 mục E.1.

Trước module này: quyền lợi IAP tin vào những gì Play Billing trả về TRÊN MÁY, và biên nhận
(`purchase_token`) mới chỉ chảy vào log pipeline (đầu vào). Module này là cơ chế xác thực:
hỏi thẳng Google Developer API `androidpublisher` xem biên nhận đó có thật không, trạng thái
giao dịch là gì, và đối chiếu với hàng `iap_purchase` đã lưu.

Ba tầng, đúng nguyên lý "cơ chế xong, triển khai để sau":

1. **`Verifier` protocol + `compute()` thuần** — cặp quyết định với kết quả Google trả về,
   chạy được NGAY trong test và dev với `StubVerifier`.
2. **`HttpPlayVerifier`** — adapter thật tới androidpublisher v3. Toàn bộ logic dựng URL,
   gọi HTTP, đọc đáp án tách khỏi việc lấy OAuth token để test được mà không cần credential
   (tiêm `fetch`). Lấy token dùng `google-auth` — extra tuỳ chọn, import lười đúng pattern
   của `google-cloud-bigquery`: backend vẫn chạy và test được khi chưa cài.
3. **Endpoint** (main.py) — chỉ tồn tại khi đã cấu hình credential; chưa cấu hình thì 503
   nói thẳng, KHÔNG trả kết quả giả.

`compute()` KHÔNG cấp hay thu hồi entitlement — đó là việc server của studio (doc 23 mục
C.2: server-side entitlement là nguồn sự thật duy nhất của HỌ). Ở đây chỉ trả lời câu hỏi
"biên nhận này Google xác nhận thế nào" để studio hành động.
"""

from __future__ import annotations

import json
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Protocol

# purchaseState của Play Billing: 0 = đã mua, 1 = đã huỷ/hoàn tiền, 2 = đang chờ.
PURCHASED, CANCELED, PENDING = 0, 1, 2

_MAX_EXAMPLES = 5

_ANDROID_PUBLISHER = (
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
)


@dataclass(frozen=True)
class PlayReceipt:
    """Phần Google xác nhận về một biên nhận — chỉ giữ trường ta ra quyết định."""

    order_id: str
    purchase_state: int
    consumption_state: int
    acknowledgement_state: int


class Verifier(Protocol):
    def lookup(self, product_id: str, token: str, is_subscription: bool) -> PlayReceipt | None:
        """Trả biên nhận Google xác nhận, hoặc None nếu token KHÔNG tồn tại với app này."""
        ...


class StubVerifier:
    """Verifier giả lập cho test và dev — KHÔNG phải thứ chạy trước dữ liệu thật.

    Nhận diện thật hay giả chỉ Google trả lời được; stub tồn tại để khoá logic của
    `compute()` bằng test và để pipeline chạy thử trước khi có credential dịch vụ.
    """

    def __init__(self, receipts: dict[str, PlayReceipt]):
        self._receipts = receipts
        self.calls: list[tuple[str, str, bool]] = []

    def lookup(self, product_id: str, token: str, is_subscription: bool) -> PlayReceipt | None:
        self.calls.append((product_id, token, is_subscription))
        return self._receipts.get(token)


class HttpPlayVerifier:
    """Adapter thật tới androidpublisher v3.

    `fetch(url, bearer) -> (status, body_json | None)` tách riêng để unit test đi hết luồng
    (404 → None, dựng URL, đọc đáp án) mà không cần credential; mặc định là urllib. Lấy
    access token dùng `google-auth` (extra `playverify`, import lười) — chưa cài thì chỉ nổ
    lúc dựng adapter với message rõ ràng, không nổ giữa chừng request.
    """

    def __init__(
        self,
        package_name: str,
        credentials_path: str,
        fetch: Callable[[str, str], tuple[int, dict[str, Any] | None]] | None = None,
    ):
        if not package_name:
            raise ValueError(
                "HttpPlayVerifier cần BRAIN_PLAY_PACKAGE — tên gói Android của app, vì URL "
                "androidpublisher gắn token vào ĐÚNG app đã phát hành nó."
            )
        self._package = package_name
        self._fetch = fetch or _urllib_fetch
        try:
            from google.oauth2 import service_account  # noqa: PLC0415
        except ImportError as e:  # extra `playverify` — chưa cài thì phải nói thẳng, không nổ giữa request
            raise RuntimeError(
                "Xác thực Play cần gói `google-auth`: cài backend với extra "
                "`[playverify]` (pip install .[playverify]) rồi đặt BRAIN_PLAY_CREDENTIALS."
            ) from e
        self._credentials = service_account.Credentials.from_service_account_file(
            credentials_path,
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )

    def lookup(self, product_id: str, token: str, is_subscription: bool) -> PlayReceipt | None:
        kind = "subscriptions" if is_subscription else "products"
        url = f"{_ANDROID_PUBLISHER}/{self._package}/purchases/{kind}/{product_id}/tokens/{token}"
        self._credentials.refresh(_Http())
        status, body = self._fetch(url, self._credentials.token)
        if status == 404 or body is None:
            return None  # Google không biết token này (với app này) — lookup() trả None
        return PlayReceipt(
            order_id=str(body.get("orderId", "")),
            purchase_state=int(body.get("purchaseState", PENDING)),
            consumption_state=int(body.get("consumptionState", 0)),
            acknowledgement_state=int(body.get("acknowledgementState", 0)),
        )


class _Http:
    """Chân google-auth cần để refresh token qua urllib — không kéo thêm httpx."""

    def __call__(self, method, url, body, headers, timeout, **kwargs):
        import urllib.error

        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return _Response(resp.status, resp.read())
        except urllib.error.HTTPError as e:
            return _Response(e.code, e.read())


class _Response:
    def __init__(self, status: int, data: bytes):
        self.status = status
        self.data = data


def _urllib_fetch(url: str, bearer: str) -> tuple[int, dict[str, Any] | None]:
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {bearer}"})
    try:
        import urllib.error

        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except Exception as e:  # noqa: BLE001
        status = getattr(e, "code", None)
        if status == 404:
            return 404, None
        raise


def compute(rows: Iterable[dict[str, Any]], verifier: Verifier) -> dict[str, Any]:
    """Đối chiếu từng giao dịch Play Store với xác nhận của Google.

    Kết quả phân loại MỖI hàng — không gộp mờ:
      - verified: Google xác nhận đã mua, order_id khớp hàng đã log.
      - order_mismatch: token thật nhưng orderId Google trả về KHÁC hàng log — biên nhận
        của đơn khác gắn vào đơn này.
      - canceled_or_refunded: purchaseState=1 — quyền lợi có thể đã cấp cho giao dịch bị hoàn.
      - invalid_token: Google không biết token này (với app này).
      - pending: purchaseState=2 — chưa được tin.
      - unverifiable_no_token: hàng PLAY_STORE không có token (bản build SDK cũ) — độ phủ.
      - skipped_non_play: web-shop và kênh khác — con đường xác thực của chúng là server
        studio gọi grantFromServer, không qua module này.
    """
    counts = {
        "verified": 0,
        "order_mismatch": 0,
        "canceled_or_refunded": 0,
        "invalid_token": 0,
        "pending": 0,
        "unverifiable_no_token": 0,
        "skipped_non_play": 0,
    }
    examples: dict[str, list[dict[str, Any]]] = {k: [] for k in counts}
    total = 0

    def _note(bucket: str, entry: dict[str, Any]) -> None:
        counts[bucket] += 1
        if len(examples[bucket]) < _MAX_EXAMPLES:
            examples[bucket].append(entry)

    for r in rows:
        if r.get("event_name") != "iap_purchase":
            continue
        total += 1
        entry = {
            "user_id": r.get("user_id") or "",
            "order_id": r.get("order_id") or "",
            "product_id": r.get("product_id") or "",
        }
        if (r.get("channel") or "") != "PLAY_STORE":
            _note("skipped_non_play", entry)
            continue
        token = r.get("purchase_token") or ""
        if not token:
            _note("unverifiable_no_token", entry)
            continue

        receipt = verifier.lookup(entry["product_id"], token, bool(int(r.get("is_subscription") or 0)))
        if receipt is None:
            _note("invalid_token", entry)
        elif receipt.purchase_state == CANCELED:
            _note("canceled_or_refunded", entry)
        elif receipt.purchase_state == PENDING:
            _note("pending", entry)
        elif receipt.order_id and entry["order_id"] and receipt.order_id != entry["order_id"]:
            _note("order_mismatch", {**entry, "google_order_id": receipt.order_id})
        else:
            _note("verified", entry)

    play_checked = (
        counts["verified"] + counts["order_mismatch"] + counts["canceled_or_refunded"]
        + counts["invalid_token"] + counts["pending"]
    )
    problems = counts["order_mismatch"] + counts["canceled_or_refunded"] + counts["invalid_token"]
    if total == 0:
        verdict = "no_data"
    elif problems:
        verdict = "issues_found"
    else:
        verdict = "clean"

    return {
        "total_purchase_rows": total,
        "play_rows_checked_against_google": play_checked,
        "counts": counts,
        "examples": examples,
        "verdict": verdict,
    }
