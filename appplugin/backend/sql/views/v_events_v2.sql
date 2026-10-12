-- Hàng cơ sở của 7 view DOC/62 §2.1 — MỘT định nghĩa (oracle: app/ad_metrics.py `_base`).
-- Chỉ schema_version = 2. `day` = ngày địa phương UTC+7 của client_ts_ms (không phải client_ts_date UTC).
-- `w` = 1/sample_rate khi 0 < sample_rate <= 1, còn lại 1 (nhân vào mọi phép đếm event).
-- `country_tier`: country_tier theo UPPER(country); không có (kể cả DEFAULT, rỗng) = OTHER.
-- `value_usd`: value_micros/1e6, USD giữ nguyên, mã khác nhân fx_daily cùng `day`; NULL = unconverted.
SELECT
  e.*,
  DATE(TIMESTAMP_MILLIS(e.client_ts_ms), 'Asia/Ho_Chi_Minh') AS day,
  IFNULL(e.app_id, '') AS app,
  IF(e.sample_rate > 0 AND e.sample_rate <= 1, 1 / e.sample_rate, 1.0) AS w,
  IFNULL(ct.tier, 'OTHER') AS country_tier,
  CASE
    WHEN e.value_micros IS NULL THEN NULL
    WHEN UPPER(e.currency) = 'USD' THEN e.value_micros / 1e6
    ELSE e.value_micros / 1e6 * fx.usd_per_unit
  END AS value_usd,
  (IFNULL(e.mediation, '') = 'meta_direct' AND IFNULL(e.value_micros, 0) = 0) AS meta_zero
FROM `__DS__.events_raw` e
LEFT JOIN `__DS__.country_tier` ct ON ct.country = UPPER(IFNULL(e.country, ''))
LEFT JOIN `__DS__.fx_daily` fx
  ON fx.date = DATE(TIMESTAMP_MILLIS(e.client_ts_ms), 'Asia/Ho_Chi_Minh')
  AND fx.currency = UPPER(e.currency)
  AND fx.usd_per_unit > 0
WHERE e.schema_version = 2
