-- Show rate tách lý do (2.1.0 BE1): chặn có chủ đích (policy/brain/guard/entitlement) không phải lỗi hiển thị.
-- Nhãn ad_format của ad_show_fail = nhãn ad_show_attempt gần nhất (không sau) cùng (app, session_id, placement) (2.2.0 BE3);
-- không có attempt thì giữ nhãn gốc. blocked_kill / harness: tách khỏi `other`; organic trừ blocked_kill (D21-220), KHÔNG trừ harness.
-- organic_show_rate = shows / (attempts_ready - blocked_*, gồm blocked_kill); lớp reason = D34 (ad_show_fail.reason). Full2 loại như v_show_rate.
SELECT *, SAFE_DIVIDE(shows, attempts_ready - blocked_policy - blocked_brain - blocked_guard - blocked_entitlement - blocked_kill)
  AS organic_show_rate
FROM (
  SELECT app, day, IFNULL(ad_format, '') AS ad_format,
    SUM(IF(event_name = 'ad_show_attempt' AND ready = 1, w, 0)) AS attempts_ready,
    SUM(IF(event_name = 'ad_show', w, 0)) AS shows,
    SUM(IF(rc = 'blocked_policy', w, 0)) AS blocked_policy, SUM(IF(rc = 'blocked_brain', w, 0)) AS blocked_brain,
    SUM(IF(rc = 'blocked_guard', w, 0)) AS blocked_guard, SUM(IF(rc = 'blocked_entitlement', w, 0)) AS blocked_entitlement,
    SUM(IF(rc = 'blocked_kill', w, 0)) AS blocked_kill, SUM(IF(rc = 'harness', w, 0)) AS harness,
    SUM(IF(rc = 'not_ready', w, 0)) AS not_ready, SUM(IF(rc = 'config', w, 0)) AS config,
    SUM(IF(rc = 'other', w, 0)) AS other
  FROM (
    SELECT *, IF(event_name <> 'ad_show_fail', NULL, CASE
      WHEN reason IN ('policy_min_gap', 'rwi_intro_skipped', 'seg_guardrail') THEN 'blocked_policy'
      WHEN reason = 'show_full_brain_declined' THEN 'blocked_brain'
      WHEN reason IN ('ad_showing', 'ad_full_showing', 'show_full_when_flag_showing', 'open_flag_resume') THEN 'blocked_guard'
      WHEN reason IN ('remove_ad', 'show_full_when_remove_ads') THEN 'blocked_entitlement'
      WHEN reason = 'show_blocked_kill_switch' THEN 'blocked_kill'
      WHEN reason = 'show_when_act_null' THEN 'harness'
      WHEN reason = 'show_not_loaded' THEN 'not_ready'
      WHEN reason = 'not_contain_placement' OR STARTS_WITH(reason, 'not_plm_cf_') THEN 'config'
      ELSE 'other' END) AS rc
    FROM (
      SELECT * EXCEPT (ad_format), IFNULL(IF(event_name <> 'ad_show_fail', ad_format, LAST_VALUE(
          IF(event_name = 'ad_show_attempt', ad_format, NULL) IGNORE NULLS) OVER (
          PARTITION BY app, IFNULL(session_id, ''), IFNULL(placement, '')
          ORDER BY IFNULL(client_ts_ms, 0), IFNULL(sequence_id, 0), event_name
          ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)), ad_format) AS ad_format
      FROM `__DS__.v_events_v2`
      WHERE event_name IN ('ad_show_attempt', 'ad_show', 'ad_show_fail')
        AND NOT STARTS_WITH(IFNULL(placement, ''), 'full_2_')))
  GROUP BY app, day, ad_format)
