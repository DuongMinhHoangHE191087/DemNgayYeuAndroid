-- ARPDAU (USD) = (quảng cáo + IAP) / DAU theo ngày × nước (1.8.0 T12; oracle `ad_metrics.arpdau_country`).
-- Vế quảng cáo đúng như v_arpdau_ads_country. Vế IAP từ iap_purchase (MERGE đã khử trùng theo
-- `iap_purchase:tok:<purchase_token>`): price_micros/1e6 theo currency_code, USD giữ nguyên, mã khác × fx_daily
-- cùng `day`, không có tỉ giá = iap_unconverted; phase = 'trial' = 0 (D5, trial không thu tiền).
WITH users AS (
  SELECT app, day, IFNULL(country, '') AS country, country_tier AS tier, COUNT(DISTINCT user_id) AS dau
  FROM `__DS__.v_events_v2`
  WHERE IFNULL(user_id, '') != ''
  GROUP BY app, day, country, tier
), ads AS (
  SELECT app, day, IFNULL(country, '') AS country,
    SUM(IF(value_usd IS NULL, 0, w * value_usd)) AS revenue_usd,
    SUM(IF(value_usd IS NULL, w, 0)) AS unconverted
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'ad_revenue' AND NOT meta_zero
  GROUP BY app, day, country
), iap_rows AS (
  SELECT e.app, e.day, IFNULL(e.country, '') AS country, e.w,
    CASE
      WHEN IFNULL(e.phase, '') = 'trial' THEN 0.0
      WHEN e.price_micros IS NULL THEN NULL
      WHEN UPPER(IFNULL(e.currency_code, '')) = 'USD' THEN e.price_micros / 1e6
      ELSE e.price_micros / 1e6 * fx.usd_per_unit
    END AS iap_usd
  FROM `__DS__.v_events_v2` e
  LEFT JOIN `__DS__.fx_daily` fx
    ON fx.date = e.day AND fx.currency = UPPER(e.currency_code) AND fx.usd_per_unit > 0
  WHERE e.event_name = 'iap_purchase'
), iap AS (
  SELECT app, day, country,
    SUM(w) AS purchases,
    SUM(IF(iap_usd IS NULL, w, 0)) AS unconverted,
    SUM(IF(iap_usd IS NULL, 0, w * iap_usd)) AS revenue_usd
  FROM iap_rows
  GROUP BY app, day, country
)
SELECT u.app, u.day, u.country, u.tier, u.dau,
  IFNULL(a.revenue_usd, 0) AS ad_revenue_usd,
  IFNULL(i.revenue_usd, 0) AS iap_revenue_usd,
  IFNULL(a.revenue_usd, 0) + IFNULL(i.revenue_usd, 0) AS revenue_usd,
  IFNULL(a.unconverted, 0) AS ad_unconverted,
  IFNULL(i.purchases, 0) AS iap_purchases,
  IFNULL(i.unconverted, 0) AS iap_unconverted,
  SAFE_DIVIDE(IFNULL(a.revenue_usd, 0) + IFNULL(i.revenue_usd, 0), u.dau) AS arpdau_usd
FROM users u
LEFT JOIN ads a ON a.app = u.app AND a.day = u.day AND a.country = u.country
LEFT JOIN iap i ON i.app = u.app AND i.day = u.day AND i.country = u.country
