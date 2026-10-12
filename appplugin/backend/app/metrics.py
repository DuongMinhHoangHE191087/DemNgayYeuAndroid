"""Bốn North Star Metric (doc 26 mục C) — tính từ log thô.

═══ RÀNG BUỘC KIẾN TRÚC, KHÔNG PHẢI GỢI Ý ═══

Doc 26 mục C viết: *"mọi thay đổi policy phải hiển thị cả 4 chỉ số cùng lúc, không cho phép
xem riêng ARPDAU/Revenue mà ẩn ComfortIndex"*. Đây là cách kiến trúc ngăn đội vận hành vô
tình tối ưu 3 chỉ số đầu bằng cách hy sinh chỉ số thứ 4.

Cách thực thi ở đây: `compute()` luôn trả về **cả bốn**, và **không có tham số nào để chọn
tập con**. Muốn giấu ComfortIndex thì phải sửa code và vượt qua một unit test — chứ không
phải chỉ bỏ một tham số khỏi URL.

═══ QUY ĐỔI TIỀN TỆ THUỘC VỀ ĐÂY ═══

Client cố ý không quy đổi (doc 45 mục D.1) và lớp ingestion cũng không. Đây là nơi đúng: có
tỉ giá theo ngày, và sửa lại được khi tỉ giá sai mà không phải đụng vào dữ liệu thô.

Nguyên tắc giữ nguyên: **không biết tỉ giá thì không bịa**. Doanh thu ở mã tiền tệ chưa biết
được báo cáo riêng trong `unconverted`, KHÔNG bị âm thầm bỏ qua. Bỏ qua sẽ khiến ARPDAU thấp
giả tạo đúng ở những thị trường chưa nạp tỉ giá — tức sai lệch có hệ thống theo vùng địa lý,
loại sai lệch tệ nhất vì nó trông giống một kết luận về thị trường.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field
from typing import Any, Iterable

from .ad_metrics import _weight as _sample_weight

MICROS = 1_000_000.0

# Chỉ USD là hằng số. Mọi tỉ giá khác phải được truyền vào — cùng nguyên tắc với `FxRates`
# phía SDK, vì cùng một lý do: tỉ giá đổi hằng ngày, code thì không.
_USD = "USD"


@dataclass
class Unconverted:
    """Doanh thu không quy đổi được, báo cáo riêng thay vì bỏ qua."""

    by_currency: dict[str, float] = field(default_factory=dict)
    event_count: int = 0

    def add(self, currency: str, amount: float) -> None:
        self.by_currency[currency] = self.by_currency.get(currency, 0.0) + amount
        self.event_count += 1

    def as_dict(self) -> dict[str, Any]:
        return {"by_currency": self.by_currency, "event_count": self.event_count}


def _to_usd(amount: float, currency: str, fx: dict[str, float]) -> float | None:
    if not currency:
        return None
    code = currency.upper()
    if code == _USD:
        return amount
    rate = fx.get(code)
    if rate is None or rate <= 0:
        return None
    return amount * rate


# Dưới ngưỡng này thì chênh lệch giữa hai nhóm gần như chắc chắn là nhiễu. Đây là một
# CHẶN THẬN TRỌNG chứ không phải kiểm định thống kê: nó không thay thế việc tính ý nghĩa
# thống kê thật, chỉ ngăn kết luận ở quy mô hiển nhiên quá nhỏ.
MIN_USERS_PER_GROUP = 200


def compare_holdout(
    rows: Iterable[dict[str, Any]], fx: dict[str, float] | None = None
) -> dict[str, Any]:
    """So sánh nhóm treatment với nhóm đối chứng.

    Đây là phép đo trả lời câu hỏi quan trọng nhất về brain: *"personalization có tốt hơn
    không làm gì không?"* Mọi con số ARPDAU tăng đều có thể do mùa vụ, do bản cập nhật game,
    do chiến dịch UA — chỉ nhóm đối chứng chạy SONG SONG mới cho so sánh sạch.

    ═══ BA CHỖ PHÉP ĐO NÀY DỄ NÓI DỐI ═══

    1. **Người chưa được phân nhóm.** Ai chưa từng có quyết định nào (ngoài rollout, hoặc
       chơi trước khi brain được bật) thì KHÔNG thuộc nhóm nào. Gán họ vào treatment — nhóm
       đông hơn — sẽ kéo trung bình về phía dân số chung và che mất hiệu ứng thật. Ở đây họ
       được báo cáo riêng.
    2. **Nhóm đối chứng rỗng.** Nếu `holdout_fraction = 0` thì một phép chia ngây thơ cho ra
       "treatment tốt hơn vô hạn". Trả `lift = None` thay vì một con số vô nghĩa.
    3. **Mẫu quá nhỏ.** doc 38 mục F yêu cầu chờ ít nhất 2 tuần. Công cụ phải cưỡng chế sự
       thận trọng đó chứ không chỉ hiện số rồi để người đọc tự nhớ.
    """
    fx = {k.upper(): v for k, v in (fx or {}).items()}

    # user -> True/False; None nghĩa là đã thấy mâu thuẫn
    group: dict[str, bool | None] = {}
    conflicting = 0
    for r in rows if isinstance(rows, list) else (rows := list(rows)):
        if r.get("event_name") != "brain_decision":
            continue
        uid = r.get("user_id") or ""
        if not uid:
            continue
        is_h = int(r.get("is_holdout") or 0) == 1
        prev = group.get(uid, "missing")
        if prev == "missing":
            group[uid] = is_h
        elif prev is not None and prev != is_h:
            # Phân nhóm phía SDK là tất định theo hash nên điều này không được phép xảy ra.
            # Có nghĩa là muối băm bị đổi giữa chừng, hoặc user_id bị dùng lại. Báo lên
            # thay vì im lặng chọn một bên.
            group[uid] = None
            conflicting += 1

    buckets: dict[str, dict[str, Any]] = {
        k: {"users": set(), "revenue_usd": 0.0, "ads": 0, "unconverted": 0}
        for k in ("treatment", "holdout", "unassigned")
    }

    def bucket_of(uid: str) -> str | None:
        g = group.get(uid, "missing")
        if g == "missing":
            return "unassigned"
        if g is None:
            return None      # mâu thuẫn -> loại khỏi cả hai nhóm
        return "holdout" if g else "treatment"

    for r in rows:
        uid = r.get("user_id") or ""
        if not uid:
            continue
        b = bucket_of(uid)
        if b is None:
            continue
        buckets[b]["users"].add(uid)
        name = r.get("event_name")
        if name == "iap_purchase":
            usd = _to_usd(float(r.get("price_micros") or 0) / MICROS,
                          r.get("currency_code") or "", fx)
            if usd is None:
                buckets[b]["unconverted"] += 1
            else:
                buckets[b]["revenue_usd"] += usd
        elif name == "ad_revenue":
            buckets[b]["ads"] += 1
            usd = _to_usd(float(r.get("ad_revenue") or 0.0),
                          r.get("currency_code") or "", fx)
            if usd is None:
                buckets[b]["unconverted"] += 1
            else:
                buckets[b]["revenue_usd"] += usd

    def summarize(b: dict[str, Any]) -> dict[str, Any]:
        n = len(b["users"])
        return {
            "users": n,
            "revenue_usd": round(b["revenue_usd"], 4),
            "revenue_per_user": round(b["revenue_usd"] / n, 6) if n else 0.0,
            "ads_per_user": round(b["ads"] / n, 4) if n else 0.0,
            "unconverted_events": b["unconverted"],
        }

    t = summarize(buckets["treatment"])
    h = summarize(buckets["holdout"])
    u = summarize(buckets["unassigned"])

    warning: str | None = None
    if h["users"] == 0:
        warning = ("Nhóm holdout RỖNG — không có gì để so sánh. Kiểm tra "
                   "experiment.holdout_fraction trong policy artifact.")
    elif t["users"] < MIN_USERS_PER_GROUP or h["users"] < MIN_USERS_PER_GROUP:
        warning = (f"Chưa đủ dữ liệu để kết luận (treatment {t['users']}, holdout "
                   f"{h['users']}, cần ≥{MIN_USERS_PER_GROUP} mỗi nhóm). Chênh lệch ở quy mô "
                   "này gần như chắc chắn là nhiễu.")

    sufficient = warning is None

    lift: dict[str, Any] | None = None
    if h["users"] > 0 and h["revenue_per_user"] > 0:
        lift = {
            "revenue_per_user_pct": round(
                (t["revenue_per_user"] - h["revenue_per_user"]) / h["revenue_per_user"] * 100, 2
            ),
            "ads_per_user_delta": round(t["ads_per_user"] - h["ads_per_user"], 4),
        }
    elif h["users"] > 0:
        lift = {"revenue_per_user_pct": None, "ads_per_user_delta":
                round(t["ads_per_user"] - h["ads_per_user"], 4)}

    return {
        "treatment": t,
        "holdout": h,
        # Báo cáo riêng, không nhập vào nhóm nào — xem lý do ở docstring.
        "unassigned": u,
        "conflicting_users": conflicting,
        "lift": lift,
        "sufficient_data": sufficient,
        "warning": warning,
        "min_users_per_group": MIN_USERS_PER_GROUP,
        # Nhắc lại ngay trong payload: đây không phải kiểm định thống kê.
        "note": "chan than trong theo co mau, KHONG phai kiem dinh y nghia thong ke",
    }


def paywall_funnel(
    rows: Iterable[dict[str, Any]], fx: dict[str, float] | None = None
) -> dict[str, Any]:
    """Phễu paywall theo từng biến thể, và phép so sánh có-paywall vs KHÔNG-có-paywall.

    Trả lời hai câu hỏi cùng lúc:

    1. **Biến thể nào bán?** Với mỗi (placement, biến thể): số lượt hiện, số người mua,
       tỉ lệ chuyển đổi, doanh thu.
    2. **Paywall có tạo giá trị tuyệt đối không?** (câu hỏi doc 19 mục 5 mà không vendor nào
       trả lời được). Nhóm holdout KHÔNG nhận paywall, nhưng mỗi lượt bỏ qua được ghi kèm
       biến thể LẼ RA đã hiện (`counterfactual_variant_id`) — nên so sánh được TỪNG biến thể:
       giữ lại 100 người khỏi biến thể A thì mất bao nhiêu lượt mua so với khi hiện nó.

    ═══ GIỚI HẠN PHẢI NÓI THẲNG ═══

    - Chuyển đổi đo ở cấp USER: người dùng có ≥1 `iap_purchase` trong kỳ được tính là mua.
      Event impression KHÔNG mang danh sách sản phẩm nên không gán lượt mua về đúng biến thể
      được — gán bừa (ví dụ theo sản phẩm trùng tên) sẽ tạo chuyển đổi ảo.
    - Người nhận paywall ở nhiều placement xuất hiện trong nhóm của TỪNG placement đó. Đây là
      overlap thật của phép đo, không phải lỗi, nhưng đọc số phải biết.
    - Cùng ba chỗ dễ nói dối như `compare_holdout`: nhóm đối chứng rỗng, mẫu quá nhỏ, và đây
      là CHẶN THẬN TRỌNG chứ không phải kiểm định thống kê.
    - Số event và tiền nhân `1/sample_rate` như các view (D7, 1.8.0 T12); số người không nhân. Phòng thủ:
      event paywall/IAP thuộc `NEVER_SAMPLE` nên không mang `sample_rate` — trọng số chỉ khác 1 nếu D7 bị nới.
    """
    fx = {k.upper(): v for k, v in (fx or {}).items()}
    rows = list(rows)

    # (placement, biến thể) -> nhóm treatment / nhóm holdout (counterfactual)
    treat: dict[tuple[str, str], dict[str, Any]] = {}
    hold: dict[tuple[str, str], dict[str, Any]] = {}
    purchase_users: set[str] = set()
    revenue_by_user: dict[str, float] = defaultdict(float)
    unconverted_by_user: dict[str, int] = defaultdict(int)

    for r in rows:
        uid = r.get("user_id") or ""
        if not uid:
            continue
        name = r.get("event_name")
        w = _sample_weight(r.get("sample_rate"))
        if name == "paywall_impression":
            key = (r.get("placement") or "", r.get("variant_id") or "")
            g = treat.setdefault(key, {"users": set(), "count": 0})
            g["users"].add(uid)
            g["count"] += w
        elif name == "paywall_holdout_skip":
            key = (r.get("placement") or "", r.get("counterfactual_variant_id") or "")
            g = hold.setdefault(key, {"users": set(), "count": 0})
            g["users"].add(uid)
            g["count"] += w
        elif name == "iap_purchase":
            purchase_users.add(uid)
            usd = _to_usd(float(r.get("price_micros") or 0) / MICROS,
                          r.get("currency_code") or "", fx)
            if usd is None:
                unconverted_by_user[uid] += w
            else:
                revenue_by_user[uid] += w * usd

    # Phân nhóm phía SDK là tất định theo hash, nên một user KHÔNG THỂ vừa thuộc treatment
    # vừa thuộc holdout của cùng một placement. Thấy điều này nghĩa là muối băm bị đổi giữa
    # chừng hoặc user_id bị dùng lại — báo lên thay vì im lặng chọn một bên.
    conflicts = 0
    treat_by_placement: dict[str, set[str]] = defaultdict(set)
    hold_by_placement: dict[str, set[str]] = defaultdict(set)
    for (placement, _), g in treat.items():
        treat_by_placement[placement] |= g["users"]
    for (placement, _), g in hold.items():
        hold_by_placement[placement] |= g["users"]
    for placement in set(treat_by_placement) & set(hold_by_placement):
        conflicts += len(treat_by_placement[placement] & hold_by_placement[placement])

    def summarize(g: dict[str, Any] | None) -> dict[str, Any]:
        if g is None:
            return {"users": 0, "events": 0, "buyers": 0, "conversion_rate": None,
                    "revenue_usd": 0.0, "unconverted_events": 0}
        users = g["users"]
        n = len(users)
        buyers = users & purchase_users
        return {
            "users": n,
            "events": g["count"],
            "buyers": len(buyers),
            "conversion_rate": round(len(buyers) / n, 6) if n else None,
            "revenue_usd": round(sum(revenue_by_user[u] for u in buyers), 4),
            "unconverted_events": sum(unconverted_by_user[u] for u in users),
        }

    placements: dict[str, Any] = {}
    for key in sorted(set(treat) | set(hold)):
        placement, variant = key
        t = summarize(treat.get(key))
        h = summarize(hold.get(key))
        lift = None
        if t["conversion_rate"] is not None and h["conversion_rate"] is not None:
            lift = round((t["conversion_rate"] - h["conversion_rate"]) * 100, 4)
        placements.setdefault(placement, {})[variant] = {
            "treatment": t,
            "holdout": h,
            # Điểm phần trăm (percentage points), KHÔNG phải % tương đối — với tỉ lệ chuyển
            # đổi nhỏ, % tương đối phồng lên rất dễ gây hiểu nhầm.
            "lift_pp": lift,
        }

    total_holdout_users = len({u for g in hold.values() for u in g["users"]})
    group_sizes = [len(g["users"]) for g in list(treat.values()) + list(hold.values())]

    warning: str | None = None
    if not hold:
        warning = ("Nhóm holdout paywall RỖNG — không có phép so sánh tuyệt đối nào. Kiểm tra "
                   "experiment.holdout_fraction trong policy artifact.")
    elif group_sizes and min(group_sizes) < MIN_USERS_PER_GROUP:
        warning = (f"Chưa đủ dữ liệu để kết luận (nhóm nhỏ nhất {min(group_sizes)} user, "
                   f"cần ≥{MIN_USERS_PER_GROUP} mỗi nhóm). Chênh lệch ở quy mô này gần như "
                   "chắc chắn là nhiễu.")

    return {
        "placements": placements,
        "total_holdout_users": total_holdout_users,
        "conflicting_users": conflicts,
        "sufficient_data": warning is None,
        "warning": warning,
        "min_users_per_group": MIN_USERS_PER_GROUP,
        "note": "chuyen doi cap USER (khong gan duoc luot mua ve bien the vi impression "
                "khong mang product_id); chan than trong theo co mau, KHONG phai kiem dinh "
                "y nghia thong ke",
    }


def compute(rows: Iterable[dict[str, Any]], fx: dict[str, float] | None = None) -> dict[str, Any]:
    """Trả về CẢ BỐN chỉ số. Không có cách nào yêu cầu một tập con."""
    fx = {k.upper(): v for k, v in (fx or {}).items()}
    # Vật chất hoá: `rows` thường là generator đọc từ đĩa, và nó bị duyệt hai lần
    # (một cho North Star, một cho so sánh holdout). Duyệt lần hai trên generator đã cạn
    # sẽ trả về rỗng một cách im lặng — không lỗi, chỉ là mọi số bằng 0.
    rows = list(rows)

    dau_by_day: dict[str, set[str]] = defaultdict(set)
    iap_usd = 0.0
    iaa_usd = 0.0
    revenue_by_channel: dict[str, float] = defaultdict(float)
    unconverted = Unconverted()

    sub_purchases = 0
    sub_users: set[str] = set()

    comfort_values: list[float] = []
    comfort_coverages: list[float] = []
    comfort_missing: set[str] = set()

    decisions = 0
    holdout_decisions = 0
    guardrail_hits: dict[str, int] = defaultdict(int)

    for r in rows:
        day = r.get("client_ts_date") or ""
        uid = r.get("user_id") or ""
        if uid:
            dau_by_day[day].add(uid)

        name = r.get("event_name")

        if name == "iap_purchase":
            amount = float(r.get("price_micros") or 0) / MICROS
            cur = r.get("currency_code") or ""
            usd = _to_usd(amount, cur, fx)
            if usd is None:
                unconverted.add(cur or "(rỗng)", amount)
            else:
                iap_usd += usd
                revenue_by_channel[r.get("channel") or "UNKNOWN"] += usd
            if int(r.get("is_subscription") or 0) == 1:
                sub_purchases += 1
                if uid:
                    sub_users.add(uid)

        elif name == "ad_revenue":
            # Mediation trả số thực, không phải micros.
            amount = float(r.get("ad_revenue") or 0.0)
            cur = r.get("currency_code") or ""
            usd = _to_usd(amount, cur, fx)
            if usd is None:
                unconverted.add(cur or "(rỗng)", amount)
            else:
                iaa_usd += usd
                revenue_by_channel["AD"] += usd

        elif name == "brain_comfort":
            comfort_values.append(float(r.get("comfort_value") or 0.0))
            comfort_coverages.append(float(r.get("comfort_coverage") or 0.0))
            for part in (r.get("components_missing") or "").split(";"):
                if part.strip():
                    comfort_missing.add(part.strip())

        elif name == "brain_decision":
            decisions += 1
            if int(r.get("is_holdout") or 0) == 1:
                holdout_decisions += 1
            for code in (r.get("guardrails_applied") or "").split(","):
                if code.strip():
                    guardrail_hits[code.strip()] += 1

    # DAU trung bình theo ngày, không phải số user duy nhất toàn kỳ: ARPDAU là chỉ số
    # THEO NGÀY, chia cho tổng user duy nhất của cả kỳ sẽ cho một con số nhỏ hơn nhiều
    # và không so sánh được với bất kỳ báo cáo ngành nào.
    days = [d for d in dau_by_day if d]
    avg_dau = (sum(len(dau_by_day[d]) for d in days) / len(days)) if days else 0.0

    total_usd = iap_usd + iaa_usd
    arpdau = (total_usd / len(days) / avg_dau) if days and avg_dau else 0.0

    mean_coverage = (sum(comfort_coverages) / len(comfort_coverages)) if comfort_coverages else 0.0
    mean_comfort = (sum(comfort_values) / len(comfort_values)) if comfort_values else 0.0

    return {
        # ═══ 1. ARPDAU ═══
        "arpdau": {
            "value_usd": round(arpdau, 6),
            "avg_dau": round(avg_dau, 2),
            "days_covered": len(days),
            "includes": ["iap", "iaa"],
        },
        # ═══ 2. Revenue theo kênh ═══
        "revenue": {
            "total_usd": round(total_usd, 4),
            "iap_usd": round(iap_usd, 4),
            "iaa_usd": round(iaa_usd, 4),
            "by_channel_usd": {k: round(v, 4) for k, v in revenue_by_channel.items()},
            # Biên lợi nhuận hai kênh chênh nhau rất lớn (~70% store so với ~92% web-shop,
            # doc 18) nên gộp một số là mất thông tin quan trọng nhất về payment routing.
            "unconverted": unconverted.as_dict(),
        },
        # ═══ 3. Subscription ═══
        "subscription": {
            "purchase_events": sub_purchases,
            "distinct_users": len(sub_users),
            # Nói rõ giới hạn ngay trong payload, không giấu xuống tài liệu: SDK chỉ ghi
            # lượt MUA, không theo dõi hạn dùng, nên không suy ra được số sub đang active.
            "note": "so luot MUA trong ky, KHONG phai so subscription dang active "
                    "(SDK chua theo doi expiry/renewal)",
        },
        # ═══ 4. ComfortIndex ═══
        "comfort": {
            "value": round(mean_comfort, 4),
            "coverage": round(mean_coverage, 4),
            "samples": len(comfort_values),
            # Ngưỡng 0.6 khớp `ComfortIndex.Result.isTrustworthy()` phía SDK.
            "trustworthy": mean_coverage >= 0.6,
            "components_missing": sorted(comfort_missing),
            "warning": None if mean_coverage >= 0.6 else
                       "DO PHU THAP - khong duoc dung con so nay de ra quyet dinh kinh doanh",
        },
        # Brain có tạo ra giá trị không — câu hỏi mà holdout tồn tại để trả lời.
        "holdout_comparison": compare_holdout(rows, fx),
        # Bối cảnh chính sách — KHÔNG phải North Star, để ở panel riêng (doc 40).
        "policy_context": {
            "decisions": decisions,
            "holdout_decisions": holdout_decisions,
            "holdout_share": round(holdout_decisions / decisions, 4) if decisions else 0.0,
            "guardrail_hits": dict(guardrail_hits),
        },
    }
