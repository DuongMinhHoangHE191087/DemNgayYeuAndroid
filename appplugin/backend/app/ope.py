"""Off-policy evaluation (OPE) phía server — doc 28 mục H.1, doc 46 mục B.1.

Vì sao module này tồn tại ở server chứ không trên máy:

  Hai trường `chosen_action_probability` (propensity) và `eligible_actions` được log vì
  chúng KHÔNG BAO GIỜ tái tạo được — nhưng propensity mà thiếu reward thì mọi estimator
  IPS/DR đều vô hiệu. Từ bản này SDK gửi kèm `reward_value` trong `brain_feedback` (chính
  con số bandit dùng để học), nên server lần đầu tiên đủ ba mảnh: ngữ cảnh quyết định,
  xác suất hành vi cũ chọn hành động đó, và reward quan sát được.

Estimator (đúng tên gọi trong doc 28 mục H.1):

  IPS   = (1/n) Σ w_i·r_i,  với w_i = π_e(a_i|x_i) / π_0(a_i|x_i)
          Không chệch, nhưng phương sai nổ khi propensity nhỏ — nên kẹp trọng số.
  SNIPS = Σ w_i·r_i / Σ w_i
          Chệch nhẹ với n nhỏ, đổi lại phương sai thấp hơn hẳn — con số nên đọc.

Hai policy tham chiếu, cố ý tầm thường:

  uniform          — chọn đều trong tập ứng viên: điểm mốc "không học gì".
  always_no_action — không làm gì cả: mốc đạo đức tối thiểu. Nếu một policy tối ưu
                     doanh thu thua cả always_no_action về reward tổng hợp, nó đang
                     phá giá trị người chơi — câu hỏi doc 29 đặt ra cho mọi cơ chế.

Hàng holdout ĐƯỢC tính: quyết định của chúng tất định nên propensity log đúng bằng 1.0,
chúng là mẫu hợp lệ của policy ghi log (và chính là nhóm được thiết kế để so sánh).
"""

from __future__ import annotations

from typing import Any, Iterable

from .normalize import BRAIN_DECISION, BRAIN_FEEDBACK

# Kẹp trọng số importance: propensity 0.01 cho trọng số 100 và một hàng như vậy chi phối
# toàn bộ ước lượng. Kẹp là đánh đổi chệch-lấy-phương-sai, chuẩn mực của OPE thực dụng;
# số lượt bị kẹp được báo ra để người đọc biết ước lượng sạch đến đâu.
_WEIGHT_CLIP = 10.0

# Dưới ngưỡng này thì ước lượng chỉ là nhiễu — báo verdict riêng thay vì đưa ra con số
# trông có vẻ tin được.
_MIN_MATCHED = 5

NO_ACTION = "no_action"


def _parse_eligible(raw: str) -> list[str]:
    """`eligible_actions` là chuỗi nối bằng dấu phẩy, GIỮ thứ tự gốc (doc 46 mục B.1)."""
    return [a.strip() for a in raw.split(",") if a.strip()]


def _target_probabilities(name: str, eligible: list[str], chosen: str) -> float:
    """π_e(chosen|x) của policy tham chiếu."""
    if name == "uniform":
        return 1.0 / len(eligible) if chosen in eligible else 0.0
    if name == "always_no_action":
        return 1.0 if chosen == NO_ACTION and NO_ACTION in eligible else 0.0
    raise ValueError(f"policy tham chiếu không biết: {name}")


def compute(rows: Iterable[dict[str, Any]],
            target_policies: tuple[str, ...] = ("uniform", "always_no_action")) -> dict[str, Any]:
    """Ghép decision ↔ feedback theo decision_id và ước lượng reward của policy tham chiếu.

    Thuần trên hàng đã lưu; KHÔNG huấn luyện hay sửa policy thật — đây là phép đo, và
    quyết định đổi policy là của studio sau khi đọc kết quả.
    """
    decisions: list[dict[str, Any]] = []
    reward_by_decision: dict[str, float] = {}
    feedback_rows = 0
    feedback_with_reward = 0

    for row in rows:
        name = row.get("event_name")
        if name == BRAIN_DECISION:
            decisions.append(row)
        elif name == BRAIN_FEEDBACK:
            feedback_rows += 1
            reward = row.get("reward_value")
            if reward is not None:
                feedback_with_reward += 1
                did = row.get("decision_id") or ""
                # Dedup ingestion giữ một feedback cho mỗi decision_id, nhưng nếu hàng trùng
                # vẫn lọt qua thì lấy bản ghi đầu — ghép đôi tuỳ tiện còn hơn im lặng gộp.
                reward_by_decision.setdefault(did, float(reward))

    counts = {
        "decision_rows": len(decisions),
        "feedback_rows": feedback_rows,
        "feedback_with_reward": feedback_with_reward,
        "matched_decisions": 0,
        "decisions_without_reward": 0,
        "unusable_propensity": 0,
        "unparseable_eligible": 0,
        "holdout_rows": 0,
    }

    # Gom một lần các hàng dùng được: (propensity, chosen, eligible, reward).
    matched: list[tuple[float, str, list[str], float]] = []
    for d in decisions:
        if int(d.get("is_holdout") or 0) == 1:
            counts["holdout_rows"] += 1
        did = d.get("decision_id") or ""
        reward = reward_by_decision.get(did)
        if reward is None:
            counts["decisions_without_reward"] += 1
            continue
        propensity = float(d.get("chosen_action_probability") or 0.0)
        if propensity <= 0.0:
            # Propensity bằng 0 nghĩa là bản ghi hỏng — chia cho nó sẽ cho vô hạn, và im
            # lặng bỏ qua thì không ai biết có hàng hỏng. Đếm riêng.
            counts["unusable_propensity"] += 1
            continue
        eligible = _parse_eligible(str(d.get("eligible_actions") or ""))
        if not eligible:
            counts["unparseable_eligible"] += 1
            continue
        matched.append((propensity, str(d.get("chosen_action_id") or ""), eligible, reward))

    counts["matched_decisions"] = len(matched)

    policies: dict[str, Any] = {}
    for policy in target_policies:
        weighted_rewards: list[float] = []
        weights: list[float] = []
        clipped = 0
        for propensity, chosen, eligible, reward in matched:
            w = _target_probabilities(policy, eligible, chosen) / propensity
            if w > _WEIGHT_CLIP:
                w = _WEIGHT_CLIP
                clipped += 1
            weighted_rewards.append(w * reward)
            weights.append(w)

        n = len(weighted_rewards)
        ips = sum(weighted_rewards) / n if n else None
        sum_w = sum(weights)
        snips = sum(weighted_rewards) / sum_w if sum_w > 0 else None
        policies[policy] = {
            "estimated_reward": snips if snips is not None else ips,
            "ips": ips,
            "snips": snips,
            "matched": n,
            "weights_clipped": clipped,
            "weight_clip": _WEIGHT_CLIP,
        }

    if counts["decision_rows"] == 0:
        verdict = "no_data"
    elif len(matched) < _MIN_MATCHED:
        verdict = "too_few_matches"
    else:
        verdict = "ok"

    return {"verdict": verdict, "counts": counts, "policies": policies,
            "min_matched": _MIN_MATCHED}
