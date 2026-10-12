-- Impression / DAU theo ngày × định dạng. Impression = ad_show (có trọng số; imp_id không là khoá).
-- DAU = số user_id khác nhau (không rỗng) có event v2 bất kỳ trong ngày của app.
WITH dau AS (
  SELECT app, day, COUNT(DISTINCT user_id) AS dau
  FROM `__DS__.v_events_v2`
  WHERE IFNULL(user_id, '') != ''
  GROUP BY app, day
), imp AS (
  SELECT app, day, IFNULL(ad_format, '') AS ad_format, SUM(w) AS impressions
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'ad_show'
  GROUP BY app, day, ad_format
)
SELECT imp.app, imp.day, imp.ad_format, imp.impressions, IFNULL(dau.dau, 0) AS dau,
  SAFE_DIVIDE(imp.impressions, dau.dau) AS imp_per_dau
FROM imp
LEFT JOIN dau ON dau.app = imp.app AND dau.day = imp.day
