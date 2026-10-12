"""Chuẩn hoá các event của brain + paywall thành hàng có kiểu.

Vì sao không đổ thẳng `eventData` vào một cột JSON: hai trường `chosen_action_probability`
và `eligible_actions` là thứ **không bao giờ tái tạo được** (doc 46 mục B.1), nên chúng phải
có kiểu rõ ràng và có ràng buộc, không được nằm lẫn trong một túi JSON tự do nơi một chuỗi
rỗng và số 0 trông giống nhau.
"""

from __future__ import annotations

import json
import math
from datetime import datetime, timezone
from typing import Any

from .models import EventRequest

BRAIN_DECISION = "brain_decision"
BRAIN_FEEDBACK = "brain_feedback"
IAP_PURCHASE = "iap_purchase"
BRAIN_COMFORT = "brain_comfort"
# Event có sẵn của pipeline ads, không phải do brain sinh ra. Nhưng thiếu nó thì ARPDAU chỉ
# có nửa vế IAP — mà ARPDAU là North Star số 1 (doc 26 mục C.1), nên nó thuộc về đây.
AD_REVENUE = "ad_revenue"
# Hai event của paywall (doc 49): impression là mẫu số của tỉ lệ chuyển đổi, holdout skip là
# nửa kia — không có nó thì nhóm đối chứng chỉ tồn tại trong bộ đếm trên máy, tức không tồn
# tại trong dữ liệu.
PAYWALL_IMPRESSION = "paywall_impression"
PAYWALL_HOLDOUT_SKIP = "paywall_holdout_skip"
# IAP/paywall v2 (1.8.0, §Hợp đồng E): kết quả mỗi lượt mua (cả huỷ/lỗi), paywall không hiện vì lý do khác
# holdout, và lựa chọn sản phẩm trong một phiên paywall — nối với `iap_purchase` bằng `paywall_session_id`.
IAP_RESULT = "iap_result"
PAYWALL_SKIP = "paywall_skip"
PAYWALL_CHOICE = "paywall_choice"
# Tử số của phễu routing đa kênh (doc 18 mục 6.7, doc 50): người được dẫn ra web shop. Ghép với
# `iap_purchase` channel WEB_SHOP (xác nhận phía server) để đo tỉ lệ chuyển đổi theo kênh.
WEB_CHECKOUT_OPENED = "web_checkout_opened"
# Chuyển đổi vòng đời subscription (doc 23 C.3): một event với cặp from/to phủ toàn bộ
# taxonomy doc 19 — vào grace, hồi phục thanh toán, huỷ, churn, win-back thành công.
SUBSCRIPTION_STATE = "subscription_state"
# Battle pass (doc 23 C.3): vượt bậc và claim phần thưởng — hai funnel riêng
# (tiến độ giữ chân và chuyển đổi premium).
BATTLE_PASS_TIER = "battle_pass_tier"
BATTLE_PASS_CLAIM = "battle_pass_claim"
# Cầu nối VIP ↔ virtual currency (doc 19): mỗi lần người sở hữu nhận dòng phát định kỳ.
RECURRING_GRANT_CLAIM = "recurring_grant_claim"
# Offerwall (doc 20, doc 23 mục C.4): impression là một lượt mở tường, completion là một
# offer được xác nhận hoàn thành — cặp tử/mẫu số của tỉ lệ chuyển đổi theo từng provider.
OFFERWALL_IMPRESSION = "offerwall_impression"
OFFERWALL_COMPLETED = "offerwall_completed"
# Phơi nhiễm thí nghiệm (doc 23 Phase 4 mục 3): mỗi phiên một hàng cho MỌI player trong
# không gian thí nghiệm — mẫu số chung của cả hai phép đo incrementality. Khác mọi event
# khác chỉ phát khi có hành vi, event này phát cho cả người KHÔNG LÀM GÌ: nhóm đối chứng
# chỉ tồn tại trong dữ liệu nếu hàng membership của họ được ghi lại.
EXPERIMENT_EXPOSURE = "experiment_exposure"

# Event schema v2 (1.7.0, "Hợp đồng event v2" của plan): có cột riêng (`V2_FIELDS`), nên là
# event đã biết; eventData gốc vẫn giữ trong `raw_event_data` (trường ngoài bảng không mất).
V2_EVENTS = {
    "ad_request", "ad_fill", "ad_no_fill", "ad_show_attempt", "ad_show", "ad_show_fail",
    "ad_click", "consent_form_shown", "consent_choice", "session_end",
}

KNOWN_EVENTS = {
    BRAIN_DECISION, BRAIN_FEEDBACK, IAP_PURCHASE, BRAIN_COMFORT, AD_REVENUE,
    PAYWALL_IMPRESSION, PAYWALL_HOLDOUT_SKIP, WEB_CHECKOUT_OPENED, SUBSCRIPTION_STATE,
    IAP_RESULT, PAYWALL_SKIP, PAYWALL_CHOICE,
    BATTLE_PASS_TIER, BATTLE_PASS_CLAIM, RECURRING_GRANT_CLAIM,
    OFFERWALL_IMPRESSION, OFFERWALL_COMPLETED, EXPERIMENT_EXPOSURE,
} | V2_EVENTS

_INT64_MIN, _INT64_MAX = -(2**63), 2**63 - 1
# `event_id` dài hơn thế này không phải UUID của SDK — không dùng làm khoá, rơi về `seq:`.
_MAX_EVENT_ID_LEN = 128


def _f(data: dict[str, Any], key: str, default: float = 0.0) -> float:
    v = data.get(key, default)
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def _i(data: dict[str, Any], key: str, default: int = 0) -> int:
    v = data.get(key, default)
    try:
        return int(v)
    except (TypeError, ValueError):
        return default


def date_utc(ts_ms: int) -> str:
    """`YYYY-MM-DD` ở UTC. Mốc thời gian vô lý (0, âm, quá xa) rơi về epoch thay vì ném lỗi:
    một bản ghi có đồng hồ máy sai vẫn đáng giữ, chỉ là mang ngày dễ nhận ra."""
    try:
        if ts_ms <= 0:
            return "1970-01-01"
        return datetime.fromtimestamp(ts_ms / 1000, tz=timezone.utc).strftime("%Y-%m-%d")
    except (OverflowError, OSError, ValueError):
        return "1970-01-01"


def _fo(data: dict[str, Any], key: str) -> float | None:
    """Float hoặc None — KHÔNG có default. Dành cho trường mà "vắng mặt" và 0.0 mang nghĩa
    khác nhau: `reward_value` của brain_feedback, thiếu dữ liệu ≠ reward bằng không (0.0 là
    reward hợp lệ — quảng cáo hiện xong người chơi bỏ game). Giá trị có mặt nhưng không
    parse được cũng trả None — bịa số ở đây sẽ đầu độc estimator OPE."""
    v = data.get(key)
    if v is None:
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _s(data: dict[str, Any], key: str, default: str = "") -> str:
    v = data.get(key, default)
    return default if v is None else str(v)


def _int_or_none(v: Any) -> int | None:
    """Số nguyên, hoặc None khi không phải số nguyên. Không có mặc định 0: `value_micros` thiếu
    hay hỏng mà thành 0 thì một lượt hiển thị có tiền trông như lượt không tiền. `bool` bị từ
    chối (Python coi True là 1)."""
    if v is None or isinstance(v, bool):
        return None
    out: int | None = None
    if isinstance(v, int):
        out = v
    elif isinstance(v, float) and math.isfinite(v) and v.is_integer():
        out = int(v)
    elif isinstance(v, str):
        try:
            out = int(v.strip())
        except ValueError:
            return None
    # Ngoài INT64 thì BigQuery từ chối cả lô -> 5xx -> client gửi lại mãi: thành NULL.
    return out if out is not None and _INT64_MIN <= out <= _INT64_MAX else None


def _flag_or_none(v: Any) -> int | None:
    """Cờ 0/1/-1: nhận cả bool của client (True -> 1)."""
    return int(v) if isinstance(v, bool) else _int_or_none(v)


def _float_or_none(v: Any) -> float | None:
    if v is None or isinstance(v, bool):
        return None
    try:
        out = float(v)
    except (TypeError, ValueError):
        return None
    # NaN/Infinity không phải JSON hợp lệ và BigQuery từ chối: thành NULL.
    return out if math.isfinite(out) else None


def _str_or_none(v: Any) -> str | None:
    if v is None or isinstance(v, (dict, list)):
        return None
    return str(v)


# Cột v2 lấy từ eventData (tên cột = tên trường). Chỉ đặt khi trường CÓ trong eventData, để
# không đè giá trị mà nhánh riêng của event đã đặt (vd. `placement` của paywall).
V2_FIELDS: dict[str, Any] = {
    "request_id": _str_or_none, "imp_id": _str_or_none, "mediation": _str_or_none,
    "ad_source": _str_or_none, "ad_unit": _str_or_none, "ad_format": _str_or_none,
    "placement": _str_or_none, "value_micros": _int_or_none, "precision_type": _int_or_none,
    "currency": _str_or_none, "error_code": _str_or_none, "latency_ms": _int_or_none,
    "reason": _str_or_none, "ready": _flag_or_none, "is_action": _flag_or_none,
    "gdpr_applies": _flag_or_none, "gpp_sections": _str_or_none, "form_kind": _str_or_none,
    "duration_ms": _int_or_none, "ads_shown_json": _str_or_none, "screens": _int_or_none,
    "fg_index": _int_or_none, "sample_rate": _float_or_none,
    # 1.9.0 T9 (carry T15): khoá slot mạng của request, tách khỏi `placement`; trước đó chỉ nằm trong `raw_event_data`.
    "slot": _str_or_none,
}

# Trường envelope v2 ở cấp cao nhất của body (`EventEnvelope.wrap` phía SDK).
ENVELOPE_FIELDS: dict[str, Any] = {
    "event_id": _str_or_none, "schema_version": _int_or_none, "sdk_version": _str_or_none,
    "package_name": _str_or_none, "consent_tier": _str_or_none,
}


def _envelope(req: EventRequest) -> dict[str, Any]:
    extra = req.model_extra or {}
    return {k: parse(extra.get(k)) for k, parse in ENVELOPE_FIELDS.items()}


def _event_id(req: EventRequest) -> str | None:
    eid = _envelope(req)["event_id"]
    if not eid or len(eid) > _MAX_EVENT_ID_LEN:
        return None
    return eid


def dedup_key(req: EventRequest) -> str:
    """Khoá chống trùng — có trên MỌI hàng (MERGE của T10 khử trùng theo cột này).

    Hàng đợi của SDK chỉ xoá bản ghi khi server trả 2xx, nên app bị kill đúng giữa hai
    bước sẽ khiến bản ghi được gửi lại (doc 46 mục C.2). Thứ tự:

    1. Khoá tự nhiên: `decision_id` cho hai event của brain, `purchase_token` (không có thì `order_id`)
       cho giao dịch, (provider, transaction) cho offerwall.
    2. `ev:<event_id>` — UUID SDK v2 sinh lúc dựng, replay mang đúng body cũ.
    3. `seq:<user_id>:<sequenceId>:<ts>` — SDK v1 cũng gắn `sequenceId`/`ts` lúc dựng.

    Không bao giờ bịa khoá từ NỘI DUNG: hai lần `brain_comfort` giống hệt nhau của cùng một
    người vẫn khác `sequenceId`, nên vẫn là hai khoá.
    """
    natural = _natural_key(req)
    if natural:
        return natural
    eid = _event_id(req)
    if eid:
        return f"ev:{eid}"
    return f"seq:{req.userProperty.user_id}:{req.sequenceId}:{req.ts}"


def _natural_key(req: EventRequest) -> str | None:
    d = req.eventData
    if req.eventName in (BRAIN_DECISION, BRAIN_FEEDBACK):
        did = _s(d, "decision_id")
        return f"{req.eventName}:{did}" if did else None
    if req.eventName == IAP_PURCHASE:
        # Token trước `order_id` (1.8.0 D5/D13/D15): SDK phát lại `iap_purchase` đã cấp mà chưa báo (restore,
        # pending -> paid, cold start) với `event_id` mới và có thể thiếu `order_id` (PENDING chưa có) — chỉ
        # token là không đổi. Tiền tố `tok:` giữ không gian khoá riêng với `order_id` (web shop không có token).
        token = _s(d, "purchase_token")
        if token:
            return f"{req.eventName}:tok:{token}"
        oid = _s(d, "order_id")
        return f"{req.eventName}:{oid}" if oid else None
    if req.eventName == OFFERWALL_COMPLETED:
        # SDK đã chặn trùng ở nguồn theo (provider, transaction), nhưng hàng đợi replay sau
        # kill-app vẫn có thể đưa cùng bản ghi tới hai lần — dedup server là lớp thứ hai.
        # Khoá ghép cả provider vì transaction id chỉ duy nhất TRONG MỘT vendor.
        pid = _s(d, "provider_id")
        tid = _s(d, "transaction_id")
        return f"{req.eventName}:{pid}:{tid}" if tid else None
    return None


def normalize(req: EventRequest) -> dict[str, Any]:
    """Trả về một hàng phẳng, luôn kèm phần chung."""
    up = req.userProperty
    row: dict[str, Any] = {
        "event_name": req.eventName,
        "sequence_id": req.sequenceId,
        # Giữ nguyên mili-giây của client. Ngày BÁO CÁO lấy từ cột này (ngày UTC+7, `v_events_v2`),
        # không phải giờ nhận: hàng đợi bền vững của SDK replay bản ghi nhiều ngày sau, và gộp theo
        # giờ nhận sẽ dồn cả lượng tồn đọng vào sai ngày.
        "client_ts_ms": req.ts,
        # Ngày UTC của đồng hồ CLIENT — cột thường (đọc lại/lọc theo ngày client). Phân vùng vật lý
        # là `received_date` (đặt ở `ingest_row`, 1.7.0 F2-be): phân vùng có hạn giữ theo ngày client
        # xoá ngay hàng tới muộn (BigQuery xoá hàng chèn vào phân vùng đã hết hạn).
        "client_ts_date": date_utc(req.ts),
        "user_id": up.user_id,
        "session_id": up.session_id,
        "app_name": up.app_name,
        "app_version": up.app_version,
        "country": up.country,
        "platform": up.platform,
        "install_day": up.install_day,
        "retention_day": up.retention_day,
        "local_hour": up.local_hour,
        "ab_test": up.ab_test,
        "dedup_key": dedup_key(req),
        **_envelope(req),
    }
    d = req.eventData

    if req.eventName == BRAIN_DECISION:
        row.update(
            decision_id=_s(d, "decision_id"),
            decision_point=_s(d, "decision_point"),
            chosen_action_id=_s(d, "chosen_action_id"),
            chosen_action_mode=_s(d, "chosen_action_mode"),
            chosen_action_probability=_f(d, "chosen_action_probability"),
            # Giữ nguyên THỨ TỰ chuỗi gốc, không sort: cùng một tập theo thứ tự khác nhau
            # vẫn là cùng tập, nhưng thứ tự giúp phát hiện lỗi phía client khi đối soát.
            eligible_actions=_s(d, "eligible_actions"),
            guardrails_applied=_s(d, "guardrails_applied"),
            segment=_s(d, "segment"),
            segment_confidence=_f(d, "segment_confidence"),
            scenario=_s(d, "scenario"),
            is_holdout=_i(d, "is_holdout"),
            policy_version=_s(d, "policy_version"),
        )
    elif req.eventName == BRAIN_FEEDBACK:
        row.update(
            decision_id=_s(d, "decision_id"),
            outcome_event=_s(d, "outcome_event"),
            outcome_timestamp_ms=_i(d, "outcome_timestamp_ms"),
            # Reward RewardModel chốt trên máy — chính con số bandit dùng để học (doc 46
            # mục B.1). Cố ý KHÔNG dùng _f: default 0.0 ở đó sẽ biến "bản ghi cũ chưa gửi
            # reward" thành "reward bằng không", làm lệch IPS theo hướng không phát hiện được.
            reward_value=_fo(d, "reward_value"),
        )
    elif req.eventName == IAP_PURCHASE:
        row.update(
            product_id=_s(d, "product_id"),
            order_id=_s(d, "order_id"),
            # KHÔNG quy đổi USD ở đây. Client cố ý gửi nguyên bản của store (doc 45 mục D.1);
            # quy đổi phải làm ở lớp phân tích, theo tỉ giá đúng ngày giao dịch.
            price_micros=_i(d, "price_micros"),
            currency_code=_s(d, "currency_code"),
            quantity=_i(d, "quantity"),
            channel=_s(d, "channel"),
            is_subscription=_i(d, "is_subscription"),
            # Biên nhận Play Billing — đầu vào của xác thực giao dịch phía server (doc 45
            # mục E.1). Kênh web-shop không có token Play nên chuỗi rỗng; phân biệt qua channel.
            purchase_token=_s(d, "purchase_token"),
            # v2 (1.8.0): `price_micros` là số THỰC THU của pha này (trial = 0, D5); `phase` trial|intro|base|
            # inapp; phiên paywall dẫn tới lượt mua (rỗng ngoài paywall). Không có `usd` (D3: quy đổi ở server).
            phase=_s(d, "phase"),
            offer_tag=_s(d, "offer_tag"),
            paywall_session_id=_s(d, "paywall_session_id"),
            placement=_s(d, "placement"),
            variant_id=_s(d, "variant_id"),
        )
    elif req.eventName == IAP_RESULT:
        # Mẫu số của tỉ lệ thành công/huỷ theo phiên paywall; `response_code` là BillingResponseCode của Play.
        row.update(
            outcome=_s(d, "outcome"),
            response_code=_int_or_none(d.get("response_code")),
            product_id=_s(d, "product_id"),
            paywall_session_id=_s(d, "paywall_session_id"),
        )
    elif req.eventName == PAYWALL_SKIP:
        row.update(placement=_s(d, "placement"), reason=_s(d, "reason"))
    elif req.eventName == PAYWALL_CHOICE:
        row.update(
            paywall_session_id=_s(d, "paywall_session_id"),
            placement=_s(d, "placement"),
            variant_id=_s(d, "variant_id"),
            product_id=_s(d, "product_id"),
        )
    elif req.eventName == AD_REVENUE:
        # v1 gửi `ad_revenue` (số thực) + `currency_code`; v2 gửi `value_micros` + `currency`.
        # Cột v1 vẫn được điền cho hàng v2 để `/metrics/northstar` (ARPDAU) không tụt về 0 khi
        # SDK lên 1.7.0; cột v2 (`value_micros`…) điền ở `_apply_v2`.
        micros = _int_or_none(d.get("value_micros"))
        revenue = _f(d, "ad_revenue") if "ad_revenue" in d or micros is None else micros / 1e6
        row.update(
            ad_format=_s(d, "ad_format"),
            ad_platform=_s(d, "ad_platform") or _s(d, "mediation"),
            ad_network=_s(d, "ad_network") or _s(d, "ad_source"),
            placement=_s(d, "placement"),
            # Mediation trả về số thực (không phải micros) — khác hẳn IAP. Ép về cùng đơn vị
            # ở đây sẽ giấu mất sự khác biệt đó; giữ nguyên và để lớp metrics xử lý.
            ad_revenue=revenue,
            currency_code=_s(d, "currency_code") or _s(d, "currency"),
        )
        # Như event v2 khác (nhánh `else`): eventData gốc giữ nguyên, để trường không có cột
        # (`screen_name` của hợp đồng v2) không mất ở ingest — "doanh thu theo màn hình" dựng lại được.
        row["raw_event_data"] = json.dumps(d, ensure_ascii=False)
    elif req.eventName == PAYWALL_IMPRESSION:
        row.update(
            placement=_s(d, "placement"),
            variant_id=_s(d, "variant_id"),
            product_count=_i(d, "product_count"),
            paywall_version=_s(d, "paywall_version"),
            # v2: phiên paywall (khoá của phễu `v_paywall_funnel`) và `schema_version` của cấu hình paywall.
            paywall_session_id=_s(d, "paywall_session_id"),
            schema_version_cfg=_int_or_none(d.get("schema_version_cfg")),
        )
    elif req.eventName == PAYWALL_HOLDOUT_SKIP:
        # `counterfactual_variant_id` là biến thể người này LẼ RA đã thấy. Không có nó thì chỉ
        # so sánh được số gộp, không so được từng biến thể giữa nhóm được hiện và nhóm bị giữ.
        row.update(
            placement=_s(d, "placement"),
            counterfactual_variant_id=_s(d, "counterfactual_variant_id"),
            paywall_version=_s(d, "paywall_version"),
            paywall_session_id=_s(d, "paywall_session_id"),
        )
    elif req.eventName == BRAIN_COMFORT:
        row.update(
            comfort_value=_f(d, "comfort_value"),
            # Không có mặc định "an toàn" nào cho coverage: thiếu nó thì con số comfort
            # không diễn giải được, nên để 0.0 và mọi truy vấn phải lọc theo cột này.
            comfort_coverage=_f(d, "comfort_coverage"),
            components_missing=_s(d, "components_missing"),
            policy_aggressiveness=_s(d, "policy_aggressiveness"),
        )
    elif req.eventName == WEB_CHECKOUT_OPENED:
        # SDK cố ý KHÔNG gửi URL checkout — template của studio có thể nhúng session token.
        row.update(
            product_id=_s(d, "product_id"),
            channel=_s(d, "channel"),
            routing_version=_s(d, "routing_version"),
        )
    elif req.eventName == SUBSCRIPTION_STATE:
        # SDK chỉ phát khi trạng thái THẬT SỰ đổi — dedup nằm ở nguồn nên dedup_key ở đây
        # để None (không được đặt key theo product_id: cùng một cặp from→to xảy ra lại sau
        # nhiều tháng là chuyển đổi thật, không phải bản ghi trùng).
        row.update(
            product_id=_s(d, "product_id"),
            from_state=_s(d, "from_state"),
            to_state=_s(d, "to_state"),
            lifecycle_version=_s(d, "lifecycle_version"),
            # v2: `client` (máy tự thấy) hay `server` (nhãn từ `signals`, D9).
            source=_s(d, "source"),
        )
    elif req.eventName == BATTLE_PASS_TIER:
        # SDK chỉ phát khi bậc thật sự tăng — dedup_key để None cùng lý do subscription_state.
        row.update(
            season_id=_s(d, "season_id"),
            from_tier=_i(d, "from_tier"),
            to_tier=_i(d, "to_tier"),
            is_premium=_i(d, "is_premium"),
            battle_pass_version=_s(d, "battle_pass_version"),
        )
    elif req.eventName == BATTLE_PASS_CLAIM:
        row.update(
            season_id=_s(d, "season_id"),
            tier=_i(d, "tier"),
            track=_s(d, "track"),
            reward_count=_i(d, "reward_count"),
            is_premium=_i(d, "is_premium"),
            battle_pass_version=_s(d, "battle_pass_version"),
        )
    elif req.eventName == RECURRING_GRANT_CLAIM:
        row.update(
            product_id=_s(d, "product_id"),
            reward_type=_s(d, "reward_type"),
            reward_amount=_i(d, "reward_amount"),
            period_hours=_i(d, "period_hours"),
            recurring_grants_version=_s(d, "recurring_grants_version"),
        )
    elif req.eventName == OFFERWALL_IMPRESSION:
        # Mẫu số của phễu offerwall. Không dedup_key: mở tường nhiều lần là hành vi thật,
        # không có id ổn định nào để làm khoá mà không gộp nhầm.
        row.update(
            provider_id=_s(d, "provider_id"),
            offerwall_version=_s(d, "offerwall_version"),
        )
    elif req.eventName == OFFERWALL_COMPLETED:
        row.update(
            provider_id=_s(d, "provider_id"),
            currency_earned=_i(d, "currency_earned"),
            transaction_id=_s(d, "transaction_id"),
            offerwall_version=_s(d, "offerwall_version"),
        )
    elif req.eventName == EXPERIMENT_EXPOSURE:
        # Mỗi phiên một hàng cho mọi player trong không gian thí nghiệm. Không dedup_key:
        # mỗi phiên là một lần phơi nhiễm thật, và đây là hàng duy nhất ghi bucket + salt tại
        # thời điểm đó — đổi muối giữa chừng mà gộp các phiên bằng khoá tự chế thì mất dấu
        # chu kỳ đo.
        row.update(
            experiment_id=_s(d, "experiment_id"),
            holdout_bucket=_i(d, "holdout_bucket"),
            is_holdout=_i(d, "is_holdout"),
            in_rollout=_i(d, "in_rollout"),
            salt_version=_s(d, "salt_version"),
        )
    else:
        # Event lạ vẫn được lưu nguyên vẹn. Ingestion không phải chỗ quyết định dữ liệu nào
        # đáng giữ — bỏ đi ở đây là mất vĩnh viễn. Chuỗi JSON (không phải object): cột JSON
        # của BigQuery nhận chuỗi qua `insert_rows_json`, và emulator cũng vậy.
        row["raw_event_data"] = json.dumps(d, ensure_ascii=False)

    _apply_v2(row, d)
    return row


def ingest_row(req: EventRequest, app_id: str | None, received_ms: int) -> dict[str, Any]:
    """Hàng đúng như `/push` ghi xuống sink: `normalize` + giờ nhận + cờ event đã biết + `app_id`.

    `app_id` do KHOÁ quyết định (không lấy từ body). Một chỗ dựng hàng cho `/push`, cho fixture
    của test và cho `tools/stack_load.py`, để ba đường không lệch nhau.
    """
    row = normalize(req)
    row["received_ts_ms"] = received_ms
    # Cột phân vùng staging/raw: ngày UTC của giờ NHẬN — luôn là "hôm nay", không bao giờ rơi vào
    # phân vùng đã hết hạn giữ dù `ts` của client cũ bao nhiêu (replay muộn, máy sai giờ, `ts = 0`).
    row["received_date"] = date_utc(received_ms)
    row["is_known_event"] = req.eventName in KNOWN_EVENTS
    row["app_id"] = app_id
    return row


def _apply_v2(row: dict[str, Any], d: dict[str, Any]) -> None:
    for name, parse in V2_FIELDS.items():
        if name in d:
            row[name] = parse(d[name])


# ── RTDN (1.8.0 T13): `DeveloperNotification` của Play (qua Pub/Sub push, `app/rtdn.py`) -> hàng `subscription_events` ──
# `subscriptionNotification.notificationType` -> trạng thái lưu. Số đã kiểm với tài liệu RTDN (DRY-180 §5); tên theo
# §Hợp đồng C (`GRACE`, không phải `IN_GRACE_PERIOD`). 20 được lưu nhưng không phải trạng thái của `signals`; số khác
# (8, 9, 11, 17, 18, 19, 22…) lưu với `state` rỗng — không mất hàng, không phát signal.
RTDN_SUBSCRIPTION_STATES: dict[int, str] = {
    1: "RECOVERED", 2: "RENEWED", 3: "CANCELED", 4: "PURCHASED", 5: "ON_HOLD", 6: "GRACE", 7: "RESTARTED",
    10: "PAUSED", 12: "REVOKED", 13: "EXPIRED", 20: "PENDING_PURCHASE_CANCELED",
}
RTDN_VOIDED_STATE = "VOIDED"
# `voidedPurchaseNotification.refundType`: 1 = hoàn toàn bộ; 2 = hoàn một phần theo số lượng (không biết số tiền).
_FULL_REFUND = 1


def rtdn_dedup_key(message_id: str) -> str:
    """Pub/Sub giao ít nhất một lần, cùng `messageId` khi gửi lại: khoá chống trùng của `subscription_events`."""
    return f"rtdn:{message_id}"


def subscription_event_row(*, message_id: str, received_ms: int, note: dict[str, Any], fields: dict[str, Any],
                           product_id: str, purchase: dict[str, Any] | None) -> dict[str, Any]:
    """Một hàng `subscription_events` (`schema/subscription_events.json`).

    `purchase` = hàng `iap_purchase` đã lưu của token (hoặc None khi chưa thấy): cho `user_id`/`app_id` (khoá của
    `signals`) và số tiền của hàng hoàn tiền. Voided hoàn toàn bộ -> `price_micros` ÂM của lượt mua; hoàn một phần
    hoặc không có lượt mua -> NULL (không bịa số tiền).
    """
    purchase = purchase or {}
    price: int | None = None
    currency: str | None = None
    if (fields["kind"] == "voided" and fields.get("refund_type") == _FULL_REFUND
            and _int_or_none(purchase.get("price_micros")) is not None):
        price = -abs(int(purchase["price_micros"]))
        currency = purchase.get("currency_code") or None
    return {
        "dedup_key": rtdn_dedup_key(message_id),
        "message_id": message_id,
        "received_ts_ms": received_ms,
        "received_date": date_utc(received_ms),
        "event_ms": fields["event_ms"],
        "package_name": fields["package_name"],
        "kind": fields["kind"],
        "notification_type": fields["notification_type"],
        "state": fields["state"],
        "purchase_token": fields["purchase_token"] or None,
        "order_id": fields["order_id"] or None,
        "product_id": product_id or None,
        "user_id": purchase.get("user_id") or None,
        "app_id": purchase.get("app_id") or None,
        "price_micros": price,
        "currency_code": currency,
        "raw_notification": json.dumps(note, ensure_ascii=False, sort_keys=True),
    }
