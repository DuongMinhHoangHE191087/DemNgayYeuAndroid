-- Show rate theo phiên view (2.2.0 BE3; oracle: app/show_rate_views.py): banner/native_collapse/rect phát nhiều attempt ready
-- cho một view còn sống, nên `v_show_rate` (show/attempt) thấp giả. Đơn vị = (app, session_id, placement) có >= 1 attempt ready.
SELECT app, day, ad_format, SUM(w) AS views, SUM(IF(shown, w, 0)) AS views_shown,
  SAFE_DIVIDE(SUM(IF(shown, w, 0)), SUM(w)) AS view_show_rate
FROM (
  SELECT app, ad_format, MIN(IF(event_name = 'ad_show_attempt' AND ready = 1, day, NULL)) AS day,
    MAX(IF(event_name = 'ad_show_attempt' AND ready = 1, w, NULL)) AS w, COUNTIF(event_name = 'ad_show') > 0 AS shown
  FROM (SELECT *, IFNULL(session_id, '') AS sid, IFNULL(placement, '') AS pl
        FROM `__DS__.v_events_v2`
        WHERE event_name IN ('ad_show_attempt', 'ad_show') AND ad_format IN ('banner', 'native_collapse', 'rect'))
  GROUP BY app, sid, pl, ad_format
  HAVING COUNTIF(event_name = 'ad_show_attempt' AND ready = 1) > 0)
GROUP BY app, day, ad_format
