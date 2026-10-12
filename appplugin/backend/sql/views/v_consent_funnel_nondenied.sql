-- Phễu consent của người KHÔNG từ chối (Q2: sau DENIED SDK không gửi gì). Cố ý KHÔNG có cột tỉ lệ đồng ý:
-- view này đọc 100 % theo cấu trúc; tỉ lệ đồng ý lấy từ báo cáo AdMob "Privacy & messaging" (chủ dự án).
SELECT app, day, IFNULL(form_kind, '') AS form_kind,
  SUM(IF(event_name = 'consent_form_shown', w, 0)) AS forms_shown,
  SUM(IF(event_name = 'consent_choice', w, 0)) AS choices,
  SUM(IF(event_name = 'consent_choice' AND JSON_VALUE(raw_event_data, '$.tier') = 'GRANTED', w, 0)) AS granted,
  SUM(IF(event_name = 'consent_choice' AND JSON_VALUE(raw_event_data, '$.tier') = 'PARTIAL', w, 0)) AS partial,
  SUM(IF(event_name = 'consent_choice'
         AND IFNULL(JSON_VALUE(raw_event_data, '$.tier'), '') NOT IN ('GRANTED', 'PARTIAL'), w, 0)) AS other_tier,
  SUM(IF(event_name = 'consent_choice' AND IFNULL(gpp_sections, '') != '', w, 0)) AS with_gpp,
  AVG(IF(event_name = 'consent_choice' AND latency_ms >= 0, latency_ms, NULL)) AS avg_latency_ms,
  MAX(IF(event_name = 'consent_choice' AND latency_ms >= 0, latency_ms, NULL)) AS max_latency_ms
FROM `__DS__.v_events_v2`
WHERE event_name IN ('consent_form_shown', 'consent_choice')
GROUP BY app, day, form_kind
