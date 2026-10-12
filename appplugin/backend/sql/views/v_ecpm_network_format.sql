-- eCPM (USD) theo ngày × mạng quảng cáo × định dạng (1.9.0 T9, D11; oracle `ad_metrics.ecpm_network_format`).
-- ad_network = chữ thường của COALESCE(ad_network, ad_source): `normalize.py` điền cột ad_network của ad_revenue bằng
-- `ad_network or ad_source` và giữ nhãn như SDK gửi (D35), view gộp các biến thể hoa/thường của cùng một nhãn.
-- GROUP BY theo vị trí: `ad_network` nhóm là biểu thức LOWER(COALESCE(...)), không phải cột gốc cùng tên.
-- Như v_ecpm_format_tier: loại meta_direct với value_micros = 0; eCPM = doanh thu đã quy đổi / số lượt đã quy đổi
-- × 1000.
SELECT
  app, day, LOWER(COALESCE(NULLIF(ad_network, ''), NULLIF(ad_source, ''), '')) AS ad_network,
  IFNULL(ad_format, '') AS ad_format,
  SUM(w) AS impressions,
  SUM(IF(value_usd IS NULL, 0, w * value_usd)) AS revenue_usd,
  SAFE_DIVIDE(SUM(IF(value_usd IS NULL, 0, w * value_usd)), SUM(IF(value_usd IS NULL, 0, w))) * 1000
    AS ecpm_usd
FROM `__DS__.v_events_v2`
WHERE event_name = 'ad_revenue' AND NOT meta_zero
GROUP BY 1, 2, 3, 4
