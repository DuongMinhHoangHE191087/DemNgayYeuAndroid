-- eCPM (USD) theo ngày × định dạng × tier. Loại meta_direct với value_micros = 0.
-- eCPM = doanh thu đã quy đổi / số lượt đã quy đổi × 1000; lượt không quy đổi được đếm ở `unconverted`.
SELECT
  app, day, IFNULL(ad_format, '') AS ad_format, country_tier AS tier,
  SUM(w) AS impressions,
  SUM(IF(value_usd IS NULL, 0, w)) AS impressions_converted,
  SUM(IF(value_usd IS NULL, w, 0)) AS unconverted,
  SUM(IF(value_usd IS NULL, 0, w * value_usd)) AS revenue_usd,
  SAFE_DIVIDE(SUM(IF(value_usd IS NULL, 0, w * value_usd)), SUM(IF(value_usd IS NULL, 0, w))) * 1000
    AS ecpm_usd
FROM `__DS__.v_events_v2`
WHERE event_name = 'ad_revenue' AND NOT meta_zero
GROUP BY app, day, ad_format, tier
