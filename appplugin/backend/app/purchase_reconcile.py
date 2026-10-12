"""Đối soát giao dịch IAP — phát hiện biên nhận bị dùng lại (doc 45 mục E.1).

`purchase_token` của Play Billing và `order_id` của đơn hàng là duy nhất cho MỘT chủ sở
hữu hợp lệ. Dedup ingestion khoá theo `order_id` KHÔNG kèm user_id (doc 46 mục C.2) — cố ý:
trùng cùng một người là replay vô hại sau kill-app, còn cùng một biên nhận xuất hiện ở
NHIỀU tài khoản thì không dedup nào chặn được và chính là tín hiệu gian lận (chia sẻ
biên nhận, token bị phát tán, hoặc mua bằng tài khoản khác rồi khiếu nại hoàn tiền).

Ba kiểm tra, tất cả thuần trên hàng đã lưu — không gọi API ngoài:

1. **Token xuyên tài khoản** — một purchase_token xuất hiện ở ≥2 user_id khác nhau.
2. **Order xuyên tài khoản** — cùng order_id ở ≥2 user_id (phủ cả kênh web-shop, nơi
   không có token Play).
3. **Token xuyên đơn ngoài subscription** — cùng token gắn ≥2 order_id khác nhau mà có
   hàng KHÔNG phải subscription. Subscription được MIỄN: Play gia hạn định kỳ giữ nguyên
   token và sinh order_id mới mỗi chu kỳ, nên token-nhiều-đơn là hành vi HỢP LỆ của gia
   hạn — chỉ khả nghi khi có hàng phi-subscription mang token đó.

Ngoài ra đếm `play_rows_missing_token` (hàng PLAY_STORE không có token) — KHÔNG phải nghi
vấn gian lận, chỉ là độ phủ dữ liệu: bản build SDK trước khi purchase_token được nối
(doc 45) không gửi trường này, và job xác thực Play Developer API sau này sẽ không có đầu
vào cho những hàng đó.

Hàm `compute()` thuần, cùng pattern với `reconcile.compute()` và `metrics.compute()`.
"""

from __future__ import annotations

from typing import Any, Iterable

_MAX_EXAMPLES = 5


def compute(rows: Iterable[dict[str, Any]]) -> dict[str, Any]:
    total = 0
    play_missing_token = 0

    token_users: dict[str, set[str]] = {}
    order_users: dict[str, set[str]] = {}
    token_orders: dict[str, set[str]] = {}
    token_orders_nonsub: dict[str, bool] = {}

    for r in rows:
        if r.get("event_name") != "iap_purchase":
            continue
        total += 1
        uid = r.get("user_id") or ""
        token = r.get("purchase_token") or ""
        order = r.get("order_id") or ""
        channel = r.get("channel") or ""
        is_sub = int(r.get("is_subscription") or 0)

        if channel == "PLAY_STORE" and not token:
            play_missing_token += 1  # độ phủ dữ liệu, không phải nghi vấn

        if token and uid:
            token_users.setdefault(token, set()).add(uid)
            if order:
                token_orders.setdefault(token, set()).add(order)
                if is_sub == 0:
                    token_orders_nonsub[token] = True
        if order and uid:
            order_users.setdefault(order, set()).add(uid)

    def _cross_user(index: dict[str, set[str]], key_label: str) -> list[dict[str, Any]]:
        out = []
        for key, users in index.items():
            if len(users) >= 2:
                out.append({key_label: key, "user_ids": sorted(users), "user_count": len(users)})
        out.sort(key=lambda e: -e["user_count"])
        return out

    token_across_users = _cross_user(token_users, "purchase_token")
    order_across_users = _cross_user(order_users, "order_id")

    token_across_orders = []
    for token, orders in token_orders.items():
        if len(orders) >= 2 and token_orders_nonsub.get(token):
            token_across_orders.append(
                {"purchase_token": token, "order_ids": sorted(orders), "order_count": len(orders)}
            )
    token_across_orders.sort(key=lambda e: -e["order_count"])

    counts = {
        "token_across_users": len(token_across_users),
        "order_across_users": len(order_across_users),
        "token_across_orders_not_subscription": len(token_across_orders),
    }
    verdict = "no_data" if total == 0 else ("suspects_found" if any(counts.values()) else "clean")

    return {
        "total_purchase_rows": total,
        "play_rows_missing_token": play_missing_token,
        "suspect_counts": counts,
        "suspects": {
            "token_across_users": token_across_users[:_MAX_EXAMPLES],
            "order_across_users": order_across_users[:_MAX_EXAMPLES],
            "token_across_orders_not_subscription": token_across_orders[:_MAX_EXAMPLES],
        },
        "verdict": verdict,
    }
