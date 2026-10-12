-- Retention D1/D7 theo cohort install_day (yyyyMMdd) × nhóm holdout (như metrics.compare_holdout):
-- brain_decision is_holdout 1 -> holdout, 0 -> treatment, cả hai -> conflicting (báo riêng), không có -> unassigned.
-- Đếm người (không nhân trọng số).
WITH grp AS (
  SELECT app, user_id,
    CASE WHEN MIN(IFNULL(is_holdout, 0)) != MAX(IFNULL(is_holdout, 0)) THEN 'conflicting'
         WHEN MAX(IFNULL(is_holdout, 0)) = 1 THEN 'holdout'
         ELSE 'treatment' END AS holdout_group
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'brain_decision' AND IFNULL(user_id, '') != ''
  GROUP BY app, user_id
), u AS (
  SELECT app, user_id, MIN(install_day) AS install_day,
    MAX(IF(retention_day = 1, 1, 0)) AS d1, MAX(IF(retention_day = 7, 1, 0)) AS d7
  FROM `__DS__.v_events_v2`
  WHERE IFNULL(user_id, '') != '' AND install_day > 0
  GROUP BY app, user_id
)
SELECT u.app, u.install_day, IFNULL(g.holdout_group, 'unassigned') AS holdout_group,
  COUNT(*) AS cohort_users, SUM(u.d1) AS d1_users, SUM(u.d7) AS d7_users,
  SAFE_DIVIDE(SUM(u.d1), COUNT(*)) AS d1_rate, SAFE_DIVIDE(SUM(u.d7), COUNT(*)) AS d7_rate
FROM u
LEFT JOIN grp g ON g.app = u.app AND g.user_id = u.user_id
GROUP BY u.app, u.install_day, holdout_group
