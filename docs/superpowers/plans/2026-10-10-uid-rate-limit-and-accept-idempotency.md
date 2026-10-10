# Plan: rate limit theo uid cho callable + test idempotency của acceptCoupleInvite

Ngày: 2026-10-10. Phạm vi: chỉ `firebase/functions/` và một dòng trong `docs/release/SECRETS_AND_CONFIG.md`.

## Goal

1. Có test chứng minh `acceptCoupleInvite` (src/pairing.js) idempotent: gọi lại sau khi đã chấp nhận thì không ghi gì và trả `alreadyAccepted: true`; hai lần gọi đồng thời chỉ tạo đúng một relationship.
2. Thêm rate limit theo uid cho cả 5 callable có uid (`acceptCoupleInvite`, `requestAccountDeletion`, `signMemoryUpload`, `confirmMemoryUpload`, `getMemoryMediaUrl`). Vượt giới hạn thì ném `HttpsError('resource-exhausted')`. Có test cho logic và một test tĩnh bảo đảm callable mới thêm sau này không quên rate limit.

## Assumptions

| # | Giả định | Trạng thái |
|---|---|---|
| A1 | Hiện có 53 test, tất cả pass (`npm --prefix firebase/functions test` → `tests 53, pass 53`, chạy 2026-10-10). | VERIFIED |
| A2 | Script test là `node --test "test/**/*.test.js"`, nên `test/helpers/fakeFirestore.js` không bị chạy như một test. | VERIFIED (package.json) |
| A3 | Repo chưa có fake Firestore hay emulator trong test functions; `cloudinary.test.js` chỉ fake Cloudinary SDK. | VERIFIED |
| A4 | Idempotency đã có sẵn: `planAccept` trả `noop` khi invite `ACCEPTED` và relationship cùng id có đủ hai thành viên; nhánh noop trong `acceptCoupleInvite` không ghi gì. Vì vậy test T1 xanh ngay. | VERIFIED (đọc code) |
| A5 | `FieldValue.increment(1).isEqual(FieldValue.increment(1)) === true`, còn so với `increment(2)` hay `serverTimestamp()` là `false` (firebase-admin đã cài). Fake nhận diện increment bằng API công khai này, không đụng field nội bộ. | VERIFIED (chạy `node -e` 2026-10-10) |
| A6a | Trong repo: `mediaQuota` deny-all với client (`firestore.rules`: `allow read, write: if false`) và `firestore.indexes.json` khai báo TTL (`fieldOverride`, `ttl: true`) trên `mediaQuota.expiresAt`. | VERIFIED (đọc file) |
| A6b | Trên project Firebase thật, TTL policy của `mediaQuota.expiresAt` đã được deploy và đang ACTIVE. Khai báo trong repo không chứng minh điều này. | UNVERIFIED (việc của owner, xem T5) |
| A7 | Các key hiện có trong `mediaQuota` là `user_*`, `rel_*`, `global_*`, `storage_*`, `global_storage`. Tiền tố mới `rl_` không đụng key nào. | VERIFIED (src/media.js) |
| A8 | Đọc `index.js` bằng `require` trong test sẽ chạy `initializeApp()` và `defineSecret`, nên test phủ index.js dùng kiểu đọc text như `client-contract.test.js`. | VERIFIED |
| A9 | Counter dùng `runTransaction` (đọc, kiểm tra, increment) vì `index.js` có `maxInstances: 10` và không đặt `concurrency`, nên request cùng uid có thể chạy song song; get rồi increment ngoài transaction cho phép vượt hạn mức. `media.js:200-225` đã dùng cùng mẫu transaction cho quota. Hiệu năng của một doc counter nóng (contention, abort) khi gallery gọi `getMemoryMediaUrl` song song chưa đo. | Quyết định transaction: VERIFIED (đọc code). Hiệu năng dưới tải: UNVERIFIED (cần chạy thử trước production) |
| A11 | Máy local chạy Node v26.10.0, còn runtime của Functions là Node 22 (`package.json` `engines`). Kết quả test local không chứng minh Node 22. | VERIFIED |
| A10 | `docs/architecture/MEDIA_CLOUDINARY.md` mà comment trong code nhắc tới **không tồn tại**. Docs chỉ cập nhật `SECRETS_AND_CONFIG.md`. | VERIFIED (file thiếu) |

## Thiết kế (ngắn)

- **Nơi đếm:** doc `mediaQuota/rl_${name}_${uid}_${windowIndex}`, với `windowIndex = Math.floor(nowMs / windowMs)` (fixed window).
- **Cách đếm:** một `db.runTransaction`: `tx.get(ref)`; nếu `count >= max` thì ném `resource-exhausted` và không ghi gì; ngược lại `tx.set(ref, { count: FieldValue.increment(1), expiresAt: new Date(nowMs + cfg.timing.dailyCounterTtlMs) }, { merge: true })`. Cùng mẫu với `media.js:200-225`.
  - Dùng transaction để cam kết "tối đa `max` lần mỗi cửa sổ" đúng kể cả khi request chạy song song (A9). Get rồi increment ngoài transaction cho phép nhiều request cùng thấy `max-1` và cùng qua.
  - Rủi ro đã biết, chưa đo (UNVERIFIED): doc counter nóng có thể bị contention/abort khi gallery gọi `getMemoryMediaUrl` song song; vì fail-closed nên lỗi đó làm callable lỗi. Cần chạy thử tải gallery trước production, và Decision 2 là đường lùi (fail-open cho `getMemoryMediaUrl`).
  - `ponytail:` fixed window (burst tối đa 2x ở biên cửa sổ), một doc cho mỗi uid mỗi cửa sổ. Cần mượt hơn thì chuyển sliding window hoặc token bucket.
- **Vị trí gọi:** ngay sau `requireUid`, trước khi parse input. Request sai input, request thất bại và cả lần re-accept noop đều tính vào hạn mức.
- **Fail-closed:** không bọc try/catch. Lỗi đọc hoặc ghi counter làm callable lỗi luôn và logic nghiệp vụ không chạy; callable nào cũng phụ thuộc Firestore nên không mất thêm gì. Có test chứng minh (T2).
- **Wiring:** một hàm nhỏ trong `index.js`: `const limit = (name, uid) => rateLimit.enforce({ db: db(), uid, name, nowMs: Date.now() });`. Mỗi callable thêm một dòng `await limit('<tên>', uid);`. Không wrapper hay middleware.
- **Giới hạn đề xuất** (`cfg.rateLimits`, dạng `{ max, windowMs }`):

| callable | max | window |
|---|---|---|
| acceptCoupleInvite | 10 | 1 giờ |
| requestAccountDeletion | 5 | 1 giờ |
| signMemoryUpload | 30 | 1 phút |
| confirmMemoryUpload | 60 | 1 phút |
| getMemoryMediaUrl | 300 | 1 phút |

- **Phương án đã loại:** bộ đếm in-memory theo instance. Với `maxInstances: 10`, giới hạn thực tế có thể gấp 10 lần, và bộ đếm mất khi cold start.

## Tasks

TDD theo thứ tự. Mỗi file chỉ thuộc đúng một task.

| id | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|
| T1 | `firebase/functions/test/helpers/fakeFirestore.js` (mới), `firebase/functions/test/pairing.accept.test.js` (mới), `firebase/functions/src/pairing.js` (**chỉ sửa tạm để mutation check, nội dung cuối cùng phải giống hệt ban đầu**) | Viết fake Firestore in-memory vừa đủ (xem "Yêu cầu fake" bên dưới) và **6 test** cho `pairing.acceptCoupleInvite`: (1) accept lần đầu tạo `relationships/{inviteId}` (partnerAId = sender, partnerBId = caller, status `ACTIVE`), đổi invite sang `ACCEPTED`, trả `alreadyAccepted:false`; (2) gọi lại: `alreadyAccepted:true`, **0 ghi**, snapshot dữ liệu không đổi; (3) `Promise.all` hai lần accept: đúng một `alreadyAccepted:false` và một `true`, chỉ một relationship; (4) invite `ACCEPTED` mà relationship thiếu thì `failed-precondition`; (5) uid không phải người nhận thì `permission-denied`; (6) caller đã có relationship `ACTIVE` khác thì `failed-precondition`. **Test xanh ngay** vì idempotency đã có (A4). **Kiểm tra không rỗng (mutation check):** tạm sửa nhánh noop trong `src/pairing.js` để rơi xuống nhánh tạo mới, chạy test và xác nhận (2) và (3) đỏ. Trước khi sửa, chụp hash: `(Get-FileHash F:\Github_Project\DemNgayYeuAndroid\firebase\functions\src\pairing.js).Hash`. Hoàn tác bằng tay (không dùng lệnh git, vì file có thể đang có thay đổi chưa commit của owner), chạy lại cho xanh, **so hash sau khi hoàn tác phải bằng hash ban đầu**, và ghi kết quả đỏ/xanh cùng hai hash vào báo cáo. | `npm --prefix firebase/functions test` → `tests 59, pass 59`; trong lúc mutation: (2) và (3) fail | — | sonnet |
| T2 | `firebase/functions/test/rateLimit.test.js` (mới) | **12 test** (đỏ): (1) cho phép đúng `max` lần, lần `max+1` ném `HttpsError` code `resource-exhausted`; (2) lần bị từ chối không ghi gì (bộ đếm ghi của fake không tăng); (3) uid khác đếm riêng; (4) `name` khác đếm riêng; (5) sang window mới (`nowMs + windowMs`) thì đếm lại từ đầu; (6) doc ghi `expiresAt` là `Date` lớn hơn `nowMs`; (7) `name` không có trong `cfg.rateLimits` thì ném lỗi (lỗi to, không im lặng cho qua); (8) **đồng thời:** đặt counter ở `max-1`, chạy `Promise.all` N=5 lần `enforce`, đúng 1 lần qua và N-1 lần `resource-exhausted`, counter cuối bằng `max`; (9) **fail-closed đọc:** `get` của counter reject thì `enforce` reject (không nuốt lỗi); (10) **fail-closed ghi:** inject lỗi **lúc commit** (sau khi callback transaction đã chạy xong, giống SDK thật: `tx.set` đồng bộ và chỉ xếp hàng, lỗi ghi nổi lên ở commit), `enforce` phải reject đúng lỗi đã inject, dữ liệu đã lưu và bộ đếm `writes` không đổi; (11) **test tĩnh chặt:** đọc `index.js` như text, với mỗi `exports.X = onCall(` cắt phần thân callable và kiểm có đúng `await limit('X', uid);` nằm **ngay sau** dòng `const uid = requireUid(request);` (trước mọi logic khác), và key `X` có trong `cfg.rateLimits`; (12) **wiring:** nạp text `index.js` bằng `node:vm` với `require` giả (`firebase-functions/v2/https`: `onCall` trả handler, `HttpsError`; `firebase-functions/params`, `firebase-admin/*`, `./src/*` đều giả) trong đó `rateLimit.enforce` luôn reject; gọi từng handler với `{ auth: { uid }, data: {} }` và kiểm handler reject cùng lỗi và các module nghiệp vụ giả (`pairing`, `media`, `accountDeletion`) **không bị gọi**; limiter giả (spy) phải được gọi **đúng 1 lần mỗi handler** với đúng `db` (instance fake), đúng `uid` của request, đúng `name` bằng tên export của handler và `nowMs` là số hợp lệ; chạy với **hai uid khác nhau** để bắt helper dùng uid cố định hoặc name cố định. Test (1)–(10) dùng `cfg.rateLimits` thật hoặc gọi lặp đúng `max` lần. Tái dùng `test/helpers/fakeFirestore.js` của T1. Test (8) chạy trên fake tuần tự hoá transaction nên chỉ chứng minh logic read-check-increment, không chứng minh hành vi lock thật (xem Risks). | `npm --prefix firebase/functions test` → 12 test mới fail (module chưa có), 59 test cũ pass | T1 | sonnet |
| T3 | `firebase/functions/src/rateLimit.js` (mới), `firebase/functions/src/config.js` | Thêm `rateLimits` (bảng trên) vào object frozen trong `config.js`. Tạo `rateLimit.js` export `enforce({ db, uid, name, nowMs })` khoảng 20 dòng theo "Thiết kế" (`runTransaction`, không try/catch): dùng `refs.quota` của `media.js` nếu export được, không thì `db.collection('mediaQuota').doc(key)`; thêm comment `ponytail:` về fixed window (burst tối đa 2x ở biên). | `npm --prefix firebase/functions test` → `tests 71`, pass 69, fail 2 (chỉ còn test tĩnh (11) và wiring (12)) | T2 | sonnet |
| T4 | `firebase/functions/index.js` | `require('./src/rateLimit')`, thêm hàm `limit` một dòng, và `await limit('<tên>', uid);` ngay sau `requireUid` trong 5 callable. Không đổi gì khác. | `npm --prefix firebase/functions test` → `tests 71, pass 71, fail 0`; `node --check firebase/functions/index.js` không lỗi | T3 | sonnet |
| T5 | `docs/release/SECRETS_AND_CONFIG.md` | Dòng 50: `53/53` thành `71/71`. Thêm vào mục 4b: (a) callable có rate limit theo uid (`cfg.rateLimits` trong `firebase/functions/src/config.js`), counter là doc `mediaQuota/rl_*`, vượt hạn mức thì client nhận `resource-exhausted`; (b) **điều kiện trước khi deploy (owner):** vào Firebase Console > Firestore > TTL, kiểm policy trên `mediaQuota.expiresAt` đang ở trạng thái Active (khai báo trong `firestore.indexes.json` chưa chứng minh policy đã chạy trên project); (c) doc `rl_*` hết hạn sau 48 giờ và được TTL xoá bất đồng bộ (thường trong vòng 24 giờ sau hạn), key chứa uid nên ghi vào data retention; job xoá tài khoản không dọn `rl_*`. | Đọc lại diff; `npm --prefix firebase/functions test` vẫn 71/71 | T4 | haiku |

### Yêu cầu fake (T1), giữ tối thiểu

- `collection(name).doc(id)`, có doc con (`doc.collection(...)`), lưu trong một `Map` theo path.
- `doc.get()` trả `{ exists, id, data(), get(field) }`; `doc.set(data, { merge })` và `doc.update(data)`. Giá trị `v` có `v?.isEqual?.(FieldValue.increment(1))` thì cộng 1 (A5). `serverTimestamp()` lưu nguyên sentinel.
- `runTransaction(fn)` chạy tuần tự bằng mutex chuỗi promise. `tx.get(docRef | query)`, còn `tx.set` và `tx.update` là hàm **đồng bộ**, trả về `tx` (không trả Promise), chỉ xếp hàng và áp dụng khi commit sau khi callback xong. Fake cho phép test inject lỗi ở bước commit.
- Query: chuỗi `where(field, '==' | 'in', v)`, snapshot có `docs` và `forEach`.
- Bộ đếm `writes` để test kiểm "0 ghi".
- `ponytail:` fake tuần tự hoá transaction, không mô phỏng lock hay retry thật của Firestore; khi cần thì thêm emulator.

## Lệnh xác minh

```powershell
npm --prefix F:\Github_Project\DemNgayYeuAndroid\firebase\functions test
```

Kỳ vọng cuối cùng: `tests 71`, `pass 71`, `fail 0` (53 cũ + 6 accept + 12 rateLimit). Nếu implementer gộp hay tách test khác đi, số có thể lệch, nhưng phải giải thích trong báo cáo.

Ghi `node --version` vào bằng chứng test. Máy local là Node v26.10.0, runtime Functions là Node 22 (A11). Nếu có Node 22 (ví dụ `npx -y node@22`), chạy lần cuối bằng Node 22; nếu không có, báo rõ khoảng trống này, không ghi "đã xác minh trên Node 22".

## Codex model

`gpt-6.1-sol`, read-only, review plan và diff theo skill `codex-flow`.

## Risks

- **Fake không giống Firestore thật:** không có lock, contention hay retry transaction. Test (3) của T1 và test (8) của T2 chỉ chứng minh logic đúng khi các transaction chạy tuần tự, không chứng minh hành vi dưới contention thật.
- **Thêm 1 transaction (đọc + ghi) mỗi request:** tăng chi phí và độ trễ, rõ nhất ở `getMemoryMediaUrl` (vốn chỉ đọc). Client đã cache URL 15 phút cho mỗi memory, nên tần suất gọi thấp.
- **Contention doc counter nóng (UNVERIFIED):** gallery gọi `getMemoryMediaUrl` song song có thể làm transaction retry/abort; fail-closed khiến URL không được cấp. Chạy thử tải gallery trước production; đường lùi là Decision 2.
- **Fixed window:** ở biên window có thể có burst gấp 2 `max`.
- **Re-accept bị chặn:** client spam re-accept (noop) cũng tiêu hạn mức; sau 10 lần/giờ sẽ nhận `resource-exhausted`.
- **uid nằm trong key doc:** doc `rl_*` chứa uid; `expiresAt` = 48 giờ sau lần ghi, TTL xoá bất đồng bộ (thường trong 24 giờ sau hạn), nên doc có thể tồn tại lâu hơn 48 giờ. Job xoá tài khoản (`accountDeletion.js`) không dọn `rl_*`. Ghi vào data retention (T5).
- **TTL chưa chắc đang chạy (A6b, UNVERIFIED):** nếu policy chưa Active trên project thật, doc `rl_*` tích luỹ mãi. Owner kiểm trước khi deploy (T5).
- **Khoảng trống Node:** test chạy local trên Node v26.10.0, không phải Node 22 (A11).
- **Client chưa xử lý `RESOURCE_EXHAUSTED`:** không có nhánh riêng. Accept hiện thông báo "Chưa kết nối được máy chủ, vui lòng thử lại khi có mạng." (gây hiểu nhầm). Media (`confirmWithRetry` chỉ retry NOT_FOUND/UNAVAILABLE) và xoá tài khoản ném lỗi thô.

## Out of scope

- skipped: map `RESOURCE_EXHAUSTED` sang thông báo thân thiện trên Android, add when có người dùng thật chạm giới hạn hoặc QA báo.
- skipped: rate limit theo IP hay thiết bị, add when thấy lạm dụng bằng nhiều tài khoản.
- skipped: sliding window hoặc token bucket, add when burst 2x ở biên window gây hại thật.
- skipped: counter không transaction (get + increment), add when đo được contention thật làm hỏng gallery (xem Decision 1).
- skipped: App Check limited-use token (chống replay), add when bật enforcement và thấy replay token.
- skipped: rate limit cho trigger và scheduler, add when có trigger do client kích hoạt trực tiếp với tần suất cao.
- skipped: test bằng Firestore emulator, add when cần kiểm hành vi transaction hay contention thật.
- skipped: rules test mới, add when đổi rules (`mediaQuota` đã deny-all và đã có test).
- skipped: viết `docs/architecture/MEDIA_CLOUDINARY.md` mà comment nhắc tới, add when có task dọn docs riêng.

## Decisions needed from user

1. **Counter dùng transaction hay get + increment (không transaction)?** Mặc định đề xuất: **transaction**. Cam kết đúng "tối đa `max` lần mỗi cửa sổ" kể cả khi chạy song song (`maxInstances: 10`); đổi lại doc nóng có thể bị contention khi gallery gọi song song (chưa đo). Get + increment tránh contention nhưng cho phép vượt bằng số request đồng thời.
2. **Fail-closed cho mọi callable, hay fail-open cho `getMemoryMediaUrl` (lỗi counter thì vẫn trả URL và ghi log)?** Mặc định đề xuất: **fail-closed tất cả**. Đơn giản nhất, và callable nào cũng cần Firestore nên lỗi counter thường đi kèm lỗi chính.
3. **Con số giới hạn** (bảng trong "Thiết kế"). Mặc định đề xuất: dùng bảng đó, chỉnh sau trong `cfg.rateLimits` mà không cần sửa logic.

## Attempt records (Step 5, 2026-10-10)

| task | executor | attempts | result |
|---|---|---|---|
| T1 | sonnet/high subagent | 1 | 59/59; mutation check làm (2) và (3) đỏ; hash `pairing.js` không đổi |
| T2 | sonnet/high subagent | 1 | đỏ như kế hoạch: 71 test, 59 pass, 12 fail |
| T3 | sonnet/high subagent | 1 | 71 test, 69 pass, 2 fail (11, 12) đúng kỳ vọng; main session đã đọc lại diff |
| T4 | main session (Sonnet 5.5), không qua subagent | 1 | `node --check index.js` OK; 71/71 |
| T5 | haiku/max subagent | 1 | xem báo cáo cuối |