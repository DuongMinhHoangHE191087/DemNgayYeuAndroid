-- MERGE staging -> raw, khử trùng (1.7.0 T10). Token DS = `<project>.<dataset>`, do bq_setup.render_sql thay.
-- Chạy: tools/stack_load.py (sau khi nạp JSONL) hoặc lịch định kỳ; chạy lại bao nhiêu lần cũng thêm 0 hàng.
--
-- Khoá = (IFNULL(app_id, ''), dedup_key) — phán quyết R-9: `ev:<event_id>` và `seq:` là khoá TOÀN CỤC,
-- hai app có thể trùng khoá; so riêng dedup_key sẽ nuốt hàng của app thứ hai. IFNULL vì NULL = NULL là false.
-- Giữ bản nhận sớm nhất (received_ts_ms, rồi client_ts_ms).
--
-- Giới hạn đã biết (R-9 minor, không sửa mã): hàng v1 (không event_id) có user_id rỗng mang khoá
-- `seq::<sequenceId>:<ts>` — hai máy khác nhau cùng sequenceId + ts sẽ bị gộp thành một. v2 luôn có
-- event_id (`ev:`), nên chỉ dữ liệu v1 chịu rủi ro này.
--
-- Nguồn MERGE phải là BẢNG (TEMP TABLE): `MERGE … USING (subquery)` treo bigquery-emulator (plan-inputs §d).
-- Không DML trên staging (BigQuery thật từ chối DML trên hàng còn trong streaming buffer); staging tự hết
-- hạn sau 2 ngày, nên MERGE phải chạy ít nhất mỗi ngày.
CREATE TEMP TABLE d AS
SELECT *
FROM `__DS__.events_staging`
WHERE dedup_key IS NOT NULL
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY IFNULL(app_id, ''), dedup_key
  ORDER BY received_ts_ms, client_ts_ms
) = 1;

MERGE `__DS__.events_raw` r
USING d
ON IFNULL(r.app_id, '') = IFNULL(d.app_id, '') AND r.dedup_key = d.dedup_key
WHEN NOT MATCHED THEN INSERT ROW;
