"""Tra sản phẩm của một purchase token khi RTDN không nói (1.8.0 T13).

`subscriptionNotification` chỉ mang `purchaseToken` + `notificationType` (trường `subscriptionId` là trường cũ, có thể
vắng). `/rtdn` lấy sản phẩm theo thứ tự: `subscriptionId` nếu có -> hàng `iap_purchase` đã lưu của token -> nguồn ở
đây. Bản thật là `purchases.subscriptionsv2.get` của androidpublisher — **H7b** (service account, credential thật,
OWNER-NOTES #4): trong 1.8.0 không lời gọi GCP nào (D9).

- `FixtureSubscriptionSource`: bảng token -> sản phẩm (test, stack cục bộ); mặc định rỗng khi chưa có credential.
- `HttpSubscriptionsV2`: chỗ của bản thật. Thiếu `BRAIN_PLAY_CREDENTIALS` -> `SourceNotConfigured`; có credential
  nhưng chưa nối (H7b) cũng `SourceNotConfigured` -> `/rtdn` trả 503 và Pub/Sub gửi lại — như `/verify/purchases`:
  không bao giờ trả kết quả giả.
"""

from __future__ import annotations

from typing import Any, Mapping, Protocol


class SourceNotConfigured(RuntimeError):
    """Nguồn trạng thái subscription chưa cấu hình/chưa nối: `/rtdn` trả 503, không ghi."""


class SubscriptionSource(Protocol):
    def lookup(self, package_name: str, purchase_token: str) -> str | None:
        """`product_id` của token, hoặc None khi nguồn không biết token này."""
        ...


class FixtureSubscriptionSource:
    """Bảng cố định token -> `product_id`; ghi lại lời gọi để test kiểm."""

    def __init__(self, products: Mapping[str, str] | None = None) -> None:
        self._products = dict(products or {})
        self.calls: list[tuple[str, str]] = []

    def lookup(self, package_name: str, purchase_token: str) -> str | None:
        self.calls.append((package_name, purchase_token))
        return self._products.get(purchase_token)


class HttpSubscriptionsV2:
    """`purchases.subscriptionsv2.get` (androidpublisher v3) — H7b, chưa nối trong 1.8.0."""

    def __init__(self, package_name: str, credentials_path: str) -> None:
        if not credentials_path:
            raise SourceNotConfigured(
                "Chưa cấu hình subscriptionsv2: đặt BRAIN_PLAY_CREDENTIALS (service account JSON với quyền "
                "androidpublisher) và BRAIN_PLAY_PACKAGE.")
        if not package_name:
            raise SourceNotConfigured("subscriptionsv2 cần BRAIN_PLAY_PACKAGE (tên gói Android của app).")
        self._package = package_name
        self._credentials_path = credentials_path

    def lookup(self, package_name: str, purchase_token: str) -> str | None:
        raise SourceNotConfigured(
            "subscriptionsv2 thật chưa nối (H7b, OWNER-NOTES #4): token không có iap_purchase đã lưu và RTDN không "
            "mang subscriptionId — Pub/Sub sẽ gửi lại.")


def build_subscription_source(settings: Any) -> SubscriptionSource:
    """Có `BRAIN_PLAY_CREDENTIALS` -> `HttpSubscriptionsV2` (thiếu package = không khởi động, như verifier);
    không -> bảng fixture rỗng (dev, stack cục bộ: sản phẩm lấy từ `iap_purchase` đã lưu)."""
    if settings.play_credentials:
        return HttpSubscriptionsV2(settings.play_package, settings.play_credentials)
    return FixtureSubscriptionSource()
