-- Show rate theo ngày × định dạng: ad_show / ad_show_attempt có ready = 1 (nhãn ad_format giữ nguyên).
-- Loại placement `full_2_*` (Full2): Full2 cố ý không phát ad_show_attempt (T2), nên ad_show của nó chỉ
-- làm phồng tử số (R-2 m4). Impression Full2 vẫn tính ở imp/DAU, eCPM, ARPDAU; fill giữ nguyên vì chuỗi
-- load của Full2 phát đủ ad_request/ad_fill qua cùng đường AdsHelper.onAdLoadStart/onAdLoaded.
SELECT app, day, IFNULL(ad_format, '') AS ad_format,
  SUM(IF(event_name = 'ad_show_attempt', w, 0)) AS attempts,
  SUM(IF(event_name = 'ad_show_attempt' AND ready = 1, w, 0)) AS attempts_ready,
  SUM(IF(event_name = 'ad_show', w, 0)) AS shows,
  SAFE_DIVIDE(SUM(IF(event_name = 'ad_show', w, 0)),
              SUM(IF(event_name = 'ad_show_attempt' AND ready = 1, w, 0))) AS show_rate
FROM `__DS__.v_events_v2`
WHERE event_name IN ('ad_show_attempt', 'ad_show')
  AND NOT STARTS_WITH(IFNULL(placement, ''), 'full_2_')
GROUP BY app, day, ad_format
