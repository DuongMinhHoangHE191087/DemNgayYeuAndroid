-- Quyết định của brain theo ngày × điểm quyết định × chế độ × holdout × policy (1.9.0 T9; oracle
-- `ad_metrics.brain_decisions`). decisions = SUM(w) của brain_decision (grain của hàng). declined_app_day = SUM(w) của
-- ad_show_fail có reason = 'show_full_brain_declined' ở grain app × day, LẶP LẠI theo thiết kế trên mọi hàng quyết định
-- của (app, day) đó (D38) — không cộng cột này qua các hàng (SUM đếm trùng); đọc một giá trị mỗi (app, day)
-- (D12: "brain từ chối" tách khỏi miss của show rate). Ngày chỉ có declined mà không có brain_decision nào thì
-- không có hàng.
-- GROUP BY theo vị trí: cột nhóm là biểu thức IFNULL(...), không phải cột gốc cùng tên.
WITH dec AS (
  SELECT app, day, IFNULL(decision_point, '') AS decision_point,
    IFNULL(chosen_action_mode, '') AS chosen_action_mode, IFNULL(is_holdout, 0) AS is_holdout,
    IFNULL(policy_version, '') AS policy_version, SUM(w) AS decisions
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'brain_decision'
  GROUP BY 1, 2, 3, 4, 5, 6
), dcl AS (
  SELECT app, day, SUM(w) AS declined_app_day
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'ad_show_fail' AND reason = 'show_full_brain_declined'
  GROUP BY app, day
)
SELECT dec.app, dec.day, dec.decision_point, dec.chosen_action_mode, dec.is_holdout, dec.policy_version,
  dec.decisions, IFNULL(dcl.declined_app_day, 0) AS declined_app_day
FROM dec
LEFT JOIN dcl ON dcl.app = dec.app AND dcl.day = dec.day
