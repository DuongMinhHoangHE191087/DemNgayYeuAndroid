-- Phễu paywall theo PHIÊN × (placement, biến thể) (1.8.0 T12; oracle `ad_metrics.paywall_session_funnel`): hiện -> chọn -> mua.
-- Phiên = paywall_session_id của paywall_impression; SDK cũ không có phiên: mỗi lượt hiện là một phiên (khoá dedup_key).
-- `day`/placement/biến thể của phiên = MIN trên các lượt hiện; trọng số phiên = MAX(w) của các lượt hiện.
-- choices = phiên có paywall_choice; closes = phiên hiện mà không chọn (paywall_close suy ở server, §Hợp đồng E);
-- purchases = phiên có iap_purchase cùng paywall_session_id; tiền IAP như v_arpdau_country (trial = 0).
-- Nhánh holdout (1.9.0 T9): hàng variant_id = 'holdout' từ paywall_holdout_skip (phiên khoá như lượt hiện, không có
-- lượt hiện/chọn/đóng); purchases = iap_purchase KHÔNG có paywall_session_id của người từng bị giữ (theo app), tính
-- vào ngày của lượt mua và placement nhỏ nhất (MIN) người đó bị giữ; conversion_rate = purchases / sessions
-- (NULL khi 0 phiên).
WITH imp AS (
  SELECT app,
    IF(IFNULL(paywall_session_id, '') != '', CONCAT('s:', paywall_session_id), CONCAT('d:', dedup_key)) AS sk,
    MIN(day) AS day, MIN(IFNULL(placement, '')) AS placement, MIN(IFNULL(variant_id, '')) AS variant_id,
    MAX(w) AS w, SUM(w) AS impressions
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'paywall_impression'
  GROUP BY app, sk
), ch AS (
  SELECT app, CONCAT('s:', paywall_session_id) AS sk
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'paywall_choice' AND IFNULL(paywall_session_id, '') != ''
  GROUP BY app, sk
), iap AS (
  SELECT e.app, e.day, e.user_id, IFNULL(e.paywall_session_id, '') AS psid, e.w,
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
), buy AS (
  SELECT app, CONCAT('s:', psid) AS sk,
    SUM(IF(iap_usd IS NULL, 0, w * iap_usd)) AS revenue_usd,
    SUM(IF(iap_usd IS NULL, w, 0)) AS unconverted
  FROM iap
  WHERE psid != ''
  GROUP BY app, sk
), hs AS (
  SELECT app,
    IF(IFNULL(paywall_session_id, '') != '', CONCAT('s:', paywall_session_id), CONCAT('d:', dedup_key)) AS sk,
    MIN(day) AS day, MIN(IFNULL(placement, '')) AS placement, MAX(w) AS w
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'paywall_holdout_skip'
  GROUP BY app, sk
), hu AS (
  SELECT app, user_id, MIN(IFNULL(placement, '')) AS placement
  FROM `__DS__.v_events_v2`
  WHERE event_name = 'paywall_holdout_skip' AND IFNULL(user_id, '') != ''
  GROUP BY app, user_id
), harm AS (
  SELECT app, day, placement, w AS sessions, 0.0 AS purchases, 0.0 AS revenue_usd, 0.0 AS unconverted
  FROM hs
  UNION ALL
  SELECT i.app, i.day, hu.placement, 0.0, i.w, IF(i.iap_usd IS NULL, 0.0, i.w * i.iap_usd),
    IF(i.iap_usd IS NULL, i.w, 0.0)
  FROM iap i
  JOIN hu ON hu.app = i.app AND hu.user_id = i.user_id
  WHERE i.psid = ''
)
SELECT imp.app, imp.day, imp.placement, imp.variant_id,
  SUM(imp.impressions) AS impressions,
  SUM(imp.w) AS sessions,
  SUM(IF(ch.sk IS NULL, 0, imp.w)) AS choices,
  SUM(IF(ch.sk IS NULL, imp.w, 0)) AS closes,
  SUM(IF(b.sk IS NULL, 0, imp.w)) AS purchases,
  IFNULL(SUM(b.revenue_usd), 0) AS revenue_usd,
  IFNULL(SUM(b.unconverted), 0) AS unconverted,
  SAFE_DIVIDE(SUM(IF(b.sk IS NULL, 0, imp.w)), SUM(imp.w)) AS conversion_rate
FROM imp
LEFT JOIN ch ON ch.app = imp.app AND ch.sk = imp.sk
LEFT JOIN buy b ON b.app = imp.app AND b.sk = imp.sk
GROUP BY imp.app, imp.day, imp.placement, imp.variant_id
UNION ALL
SELECT app, day, placement, 'holdout' AS variant_id, 0.0 AS impressions, SUM(sessions) AS sessions,
  0.0 AS choices, 0.0 AS closes, SUM(purchases) AS purchases, SUM(revenue_usd) AS revenue_usd,
  SUM(unconverted) AS unconverted, SAFE_DIVIDE(SUM(purchases), SUM(sessions)) AS conversion_rate
FROM harm
GROUP BY app, day, placement
