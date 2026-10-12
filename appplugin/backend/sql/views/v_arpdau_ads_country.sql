-- ARPDAU chỉ quảng cáo (USD) theo ngày × nước; mọi nước có người dùng đều có hàng (doanh thu 0 được).
WITH users AS (
  SELECT app, day, IFNULL(country, '') AS country, country_tier AS tier, COUNT(DISTINCT user_id) AS dau
  FROM `__DS__.v_events_v2`
  WHERE IFNULL(user_id, '') != ''
  GROUP BY app, day, country, tier
), rev AS (
  SELECT app, day, IFNULL(country, '') AS country,
    SUM(IF(value_usd IS NULL, 0, w * value_usd)) AS revenue_usd,
    SUM(IF(value_usd IS NULL, w, 0)) AS unconverted
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'ad_revenue' AND NOT meta_zero
  GROUP BY app, day, country
)
SELECT u.app, u.day, u.country, u.tier, u.dau,
  IFNULL(r.revenue_usd, 0) AS revenue_usd, IFNULL(r.unconverted, 0) AS unconverted,
  SAFE_DIVIDE(IFNULL(r.revenue_usd, 0), u.dau) AS arpdau_usd
FROM users u
LEFT JOIN rev r ON r.app = u.app AND r.day = u.day AND r.country = u.country
