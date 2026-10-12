-- Fill rate theo ngày của ad_request × định dạng: request có ad_fill / request (distinct request_id).
-- Request không có kết thúc (ad_fill / ad_no_fill) vẫn ở mẫu số và được báo riêng ở `orphans`.
WITH req AS (
  SELECT app, request_id, MIN(day) AS day, MIN(IFNULL(ad_format, '')) AS ad_format, MAX(w) AS w
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'ad_request' AND IFNULL(request_id, '') != ''
  GROUP BY app, request_id
), term AS (
  SELECT app, request_id,
    MAX(IF(event_name = 'ad_fill', 1, 0)) AS filled,
    MAX(IF(event_name = 'ad_no_fill', 1, 0)) AS no_fill
  FROM `__DS__.v_events_v2`
  WHERE event_name IN ('ad_fill', 'ad_no_fill') AND IFNULL(request_id, '') != ''
  GROUP BY app, request_id
)
SELECT req.app, req.day, req.ad_format,
  SUM(req.w) AS requests,
  SUM(IF(IFNULL(t.filled, 0) = 1, req.w, 0)) AS fills,
  SUM(IF(IFNULL(t.filled, 0) = 0 AND IFNULL(t.no_fill, 0) = 1, req.w, 0)) AS no_fills,
  SUM(IF(t.request_id IS NULL, req.w, 0)) AS orphans,
  SAFE_DIVIDE(SUM(IF(IFNULL(t.filled, 0) = 1, req.w, 0)), SUM(req.w)) AS fill_rate
FROM req
LEFT JOIN term t ON t.app = req.app AND t.request_id = req.request_id
GROUP BY req.app, req.day, req.ad_format
