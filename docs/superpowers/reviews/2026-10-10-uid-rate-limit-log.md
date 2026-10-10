# Review log: uid rate limit + acceptCoupleInvite idempotency

Plan: `docs/superpowers/plans/2026-10-10-uid-rate-limit-and-accept-idempotency.md`. Codex model: `gpt-6.1-sol`, read-only, xhigh.

| round | stage | finding | Claude verdict | action |
|---|---|---|---|---|
| 1 | plan | [MAJOR] T2/T3: counter get rồi increment không bảo đảm "vượt giới hạn thì từ chối" khi request song song | accept: `index.js` có `maxInstances: 10`, không đặt `concurrency`; `media.js:200-225` đã dùng transaction cho quota | Đổi counter sang `runTransaction`; thêm test đồng thời ở `max-1` (T2 #8); viết lại A9 và Decision 1 |
| 1 | plan | [MAJOR] T2/T4: test tĩnh quá yếu, không có test fail-closed hay wiring | accept: chỉ kiểm có chuỗi `limit('X', uid)` thì bỏ sót vị trí sai | T2 #9, #10 (counter reject thì `enforce` reject), #11 (đúng dòng ngay sau `requireUid`), #12 (wiring bằng `node:vm`, limiter reject thì logic nghiệp vụ không chạy) |
| 1 | plan | [MAJOR] T5: TTL chỉ khai báo trong repo, chưa chứng minh Active; wording retention sai | accept: `firestore.indexes.json` có `ttl: true` nhưng trạng thái trên project thật chưa xác minh; TTL xoá bất đồng bộ | Tách A6a (VERIFIED) / A6b (UNVERIFIED); T5 thêm điều kiện owner kiểm TTL, wording "hết hạn sau 48 giờ, TTL xoá bất đồng bộ", ghi `accountDeletion.js` không dọn `rl_*` |
| 1 | plan | [MINOR] T3: A9 viết như sự thật chắc chắn | accept | A9 tách "quyết định transaction: VERIFIED" và "hiệu năng dưới tải: UNVERIFIED"; thêm rủi ro contention và bước thử tải trước production |
| 1 | plan | [MINOR] T1: `pairing.js` bị sửa tạm nhưng không nằm trong files owned | accept | T1 liệt kê `pairing.js` "chỉ sửa tạm", thêm so hash trước/sau khi hoàn tác |
| 1 | plan | Missing step: test chạy trên Node v26.10.0, runtime là Node 22 | accept | Thêm A11; mục Lệnh xác minh ghi `node --version`, chạy lại bằng Node 22 nếu có, nếu không thì báo rõ khoảng trống |
| 2 | plan | [MAJOR] T2 #12: wiring test không kiểm tham số truyền vào `rateLimit.enforce`; helper dùng uid/name cố định vẫn qua mọi test | accept: đã tự thấy `index.js:64` lấy uid rồi chuyển tiếp, test chỉ kiểm reject nên không bắt được | T2 #12: spy kiểm đúng 1 lần/handler, đúng db, uid, name = tên export, nowMs hợp lệ; chạy hai uid khác nhau |
| 2 | plan | [MINOR] T1/T2 #10: fake `tx.set` có thể trả Promise reject, trong khi SDK thật `set` đồng bộ và lỗi nổi ở commit (`transaction.js:276,507`) | accept | T1 fake: `tx.set`/`tx.update` đồng bộ, trả `tx`; T2 #10 inject lỗi lúc commit, kiểm dữ liệu và `writes` không đổi |
| 2 | plan | Missing steps: none. VERDICT: FAIL (do 1 MAJOR) | Đã sửa plan cho cả 2 finding; chưa chạy vòng 3 để xác nhận lại (hai thay đổi chỉ chặt thêm đặc tả test), diff review sẽ kiểm | — |

| 1 | review (diff) | Không có finding (0 BLOCKER/MAJOR/MINOR). Codex chạy lại 71/71 trên Node 26.10.0 và Node 22.23.3, mutation check T1 làm đúng test (2),(3) đỏ, hash pairing.js không đổi. VERDICT: PASS | Chấp nhận; không có gì để triage | Kết thúc vòng review diff ở vòng 1 (2026-10-10) |
