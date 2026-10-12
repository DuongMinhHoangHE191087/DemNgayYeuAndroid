# 57 — Bản ghi phơi nhiễm thí nghiệm: hook incrementality dùng chung UA + monetization (mục cuối Phase 4, doc 23)

**Ngày viết:** 2026-08-25
**Trạng thái:** 501/501 unit test, backend 105/105, hai AAR build sạch.

---

## A. Bài toán

Holdout (doc 32, HoldoutManager) tồn tại để trả lời câu hỏi quan trọng nhất: *"personalization
có tốt hơn không làm gì không?"*. Nhưng nhóm đối chứng chỉ có giá trị nếu **membership của
họ tồn tại trong dữ liệu** — và đây là chỗ ba doc trước (paywall, cross-modal, CRM) đều
chưa kín:

- Mọi event khác của SDK phát khi có **hành vi** (hiện ads, mở paywall, mua, claim…).
- Người thuộc nhóm đối chứng hoặc ngoài rollout KHÔNG LÀM GÌ → không event nào →
  join với dữ liệu UA/attribution thiếu hàng membership của họ.
- Kết quả: phép so sánh treatment vs holdout chỉ phủ được tập **đã tự chọn lọc vào phễu
  mua** — sai lệch chọn mẫu đúng chỗ phép đo cần sạch nhất.

Paywall đã có nửa giải pháp (`paywall_holdout_skip` + `counterfactual_variant_id`), CRM đã
mang cờ `isHoldout` trên mỗi tín hiệu. Mảnh cuối: **một hàng membership ở cấp phiên cho
MỌI player** — doc 23 Phase 4 gọi là *"Holdout/incrementality hook dùng chung UA + monetization"*.

## B. Thiết kế

### B.1 `ExposureRecord` — ảnh chụp phân nhóm

```kotlin
HoldoutManager.exposureRecord(playerId, rolloutFraction, experimentId = "monetization_brain")
// → ExposureRecord(experimentId, bucket, isHoldout, inRollout, saltVersion)
```

Hàm này KHÔNG đổi trạng thái nào — phân nhóm là hàm tất định của (salt, playerId), chụp
lại chỉ là ghi nhận. `experimentId` đặt tên được vì cùng cơ chế bucket có thể phục vụ thí
nghiệm khác về sau (ví dụ `paywall_pricing`) trên không gian băm khác.

### B.2 Event `experiment_exposure` — mỗi phiên một hàng, cho mọi người

`AdsBrain.onSessionStart` phát qua `LogEventServer.logExperimentExposure` khi `enabled`. 2.2.0 LIC (D30-220): hàng
chỉ phát khi giấy phép `VALID` — phiên bắt đầu lúc verdict chưa có (cold start luôn vậy) giữ hàng trong RAM và phát
nó ở verdict `VALID` đầu tiên của process (dấu thời gian là lúc phát, không phải lúc bắt đầu phiên); hàng giữ bị bỏ khi player id đổi
trước lúc phát; `INVALID` = không hàng:

| Nhóm | Hàng exposure | `is_holdout` | `in_rollout` |
|---|---|---|---|
| Treatment | ✅ | 0 | 1 |
| Đối chứng trong rollout | ✅ | 1 | 1 |
| Ngoài rollout | ✅ | 0 | 0 |

2.3.0 (LIC m17, R-MINR m-6): hàng giữ chờ `VALID` được đóng dấu thời gian (`ts` của body, `System.currentTimeMillis()` lúc dựng body trong `LogEventTransport`) khi hàng được đưa ra hàng đợi gửi — tức lúc verdict `VALID` đầu tiên của process chạy `flushHeld` (`AdsBrain.kt:93-96` gọi lambda `:198-203`) — không phải lúc bắt đầu phiên, và cũng không phải lúc HTTP gửi hay gửi lại; `reconcile`/`calibrate` không dùng mốc thời gian của hàng exposure.

Ba quyết định:

1. **Gate `enabled`, không gate rollout/holdout.** Brain tắt = không thí nghiệm nào đang
   chạy, ghi membership là nhiễu. Nhưng ĐÃ chạy thì ghi cho mọi phía — cờ membership của
   người ngoài rollout chính là dữ liệu.
2. **`holdout_bucket` + `salt_version` đi kèm.** Server tái tạo được membership từ
   (salt, user_id) thay vì tin mù quáng cờ client gửi lên — lệch bucket là biết log hỏng.
3. **Không dedup_key.** Mỗi phiên là một lần phơi nhiễm thật; và đây là hàng duy nhất ghi
   bucket + salt tại thời điểm đó — gộp các phiên bằng khoá tự chế thì mất dấu chu kỳ đo
   khi đổi muối giữa chừng.

### B.3 `salt_version` — ranh giới chu kỳ đo

Đổi muối = phân nhóm lại toàn bộ dân số. Hàng thiếu salt thì không biết thuộc chu kỳ nào,
join với dữ liệu chu kỳ khác cho ra **tăng trưởng giả** (người nhảy nhóm trông như vừa
được "điều trị"). Vì vậy salt đi kèm MỌI hàng exposure, và HoldoutManager đã cảnh báo chỉ
đổi muối khi bắt đầu chu kỳ đo mới.

## C. Backend

- `EXPERIMENT_EXPOSURE` vào `KNOWN_EVENTS`; flatten: `experiment_id`, `holdout_bucket`,
  `is_holdout`, `in_rollout`, `salt_version` (booleans là INT64 — schema không có BOOLEAN).
- Schema thêm 4 cột trước `raw_event_data` (`is_holdout` dùng lại cột sẵn của brain_decision).
- 5 test ingestion mới (backend 94/94): flatten đủ trường, không dedup_key, mọi nhóm đều
  có hàng, đổi muối ghi nhận được qua `salt_version`, thiếu trường → mặc định an toàn
  (thà hàng có giá trị mặc định còn hơn mất hàng membership).
- **Đối soát bucket** (hiện thực hoá quyết định 2 mục B.2): `reconcile.py` + endpoint
  `GET /reconcile/buckets` (backend 105/105). Server KHÔNG tin cờ client khai: tái tính
  bucket từ (salt_version, user_id) bằng bản sao FNV-1a bit-identical của SDK và kiểm ba
  lớp — (1) tái tính bucket, lệch là hỏng dữ liệu tuyệt đối; (2) đơn điệu ngưỡng theo từng
  salt_version, KHÔNG cần biết fraction vẫn bắt được cờ tự mâu thuẫn; (3) cờ chính xác khi
  người vận hành cấp `holdout_fraction`/`rollout_fraction`. Khoá parity chéo ngôn ngữ: bộ
  vector hằng số trong `HoldoutParityVectorsTest` (Kotlin, test 501) trùng từng số với
  `test_reconcile.py` — đổi thuật toán băm ở một bên là bên kia bắt được ngay.

## D. Dùng chung UA + monetization — hook doc 23 gọi tên

Cùng một namespace `brain_holdout_v1` nay phục vụ bốn nơi:

1. **Paywall**: `paywall_holdout_skip` + biến thể counterfactual (doc 49).
2. **Brain**: rollout + holdout của DecisionEngine, holdout nhận baseline khi
   `chooseCrossModal` (doc 55).
3. **CRM**: cờ `isHoldout` trên tín hiệu để studio loại nhóm đối chứng khỏi chiến dịch
   (doc 56) — CRM dội campaign vào holdout là hỏng phép đo incrementality.
4. **UA/attribution**: hàng `experiment_exposure` join với install source / campaign →
   so sánh LTV treatment vs holdout **theo kênh UA**, không chỉ theo IAP — tức trả lời
   "brain đáng giá bao nhiêu trên từng đồng UA" chứ không chỉ "trong app".

## E. Giới hạn — ghi rõ

1. Hàng exposure phát tại `onSessionStart`: phiên dài mà muối đổi giữa phiên thì hàng đó
   mang salt cũ đến hết phiên. Chấp nhận được — đổi muối là sự kiện vận hành hiếm, có chủ
   đích, không xảy ra ngầm.
2. `LogEventServer` gửi-và-quên: mất mạng đúng lúc đó là mất hàng exposure của phiên này;
   tự lành ở phiên kế tiếp (hàng mới thay thế về mặt membership hiện tại, chỉ mất lịch sử
   một phiên).
3. Đối soát bucket phía server (mục B.2 quyết định 2): job ĐÃ xây (`reconcile.py` +
   `GET /reconcile/buckets`, mục C). Hai phần còn lại thuộc vận hành, không phải thiếu code:
   (a) chạy định kỳ trên kho production thuộc milestone triển khai backend; (b) fraction lấy
   từ cấu hình thí nghiệm phải do người vận hành cấp cho job — hàng exposure cố ý KHÔNG mang
   fraction theo (thiếu nó lớp đơn điệu ngưỡng vẫn chạy, và không để client tự khai ngưỡng
   của chính mình).
4. Không gate consent — exposure không chứa dữ liệu hành vi (không phải profiling); nó chỉ
   mang user_id vốn đã đi kèm mọi event khác của SDK.
5. **`user_id` = playerId từ 1.4.0** (#13, DOC/66 mục 4). Mục B.2 và C giả định server tái tính
   bucket từ `user_id`, nhưng trước 1.4.0 SDK gửi `user_id` = ANDROID_ID trong khi bucket tính
   theo playerId, nên lớp 1 của `reconcile.py` lệch gần như mọi hàng. Từ 1.4.0 `user_id` là
   player id đang có hiệu lực (`PlayerIdSource`): id app truyền qua
   `MonetizationSdk.configure { playerId }` nếu có, nếu không thì `BrainStorage.playerId()`;
   `configure` ghi id trước khi gọi `AdsBrain.onSessionStart(id)`, nên hàng exposure của lần đó
   mang đúng id của bucket. Hàng trước 1.4.0 không đối soát được theo lớp 1. Ba đường khác cũng
   giữ `user_id` = id bucket (review Task D, 2026-09-25): (a) quay lại sau ≥ 30 phút ở nền
   (`AppPluginBase.onStart` → `AdsBrain.onSessionStart()`) đọc `PlayerIdSource`, nên giữ id app đã
   truyền, không về `BrainStorage.playerId()` (lỗi có từ trước #13); (b) app gọi thẳng
   `AdsBrain.onSessionStart(id)` thì brain ghi id đó vào `PlayerIdSource` trước khi phát exposure;
   (c) dòng hàng đợi của `DecisionLogger` lưu `user_id` lúc ghi (trường 13), replay ở lần mở sau
   (tại `LogEventServer.init`, trước `configure`) dùng id đó; dòng của bản trước (12 trường) dùng
   id hiện hành; từ 1.6.0 event `brain_decision` sống cũng mang id ghi ở trường 13 (O1 (b)):
   event sống và replay cùng `user_id` lúc ghi.
   (d) `session_start`/`first_open` và exposure của lần `AdsBrain.onSessionStart()`
   tự động trong `AppPluginManager.initPlugin` chạy trước `configure`: SDK lưu id hợp lệ cuối cùng
   app truyền (`BrainStorage.appPlayerId()`, khoá prefs `brain_app_player_id`, review cuối I2), và
   `PlayerIdSource.current()` = id app của process → id app đã lưu → `BrainStorage.playerId()`;
   id mới ghi đè, id rỗng/không hợp lệ không lưu. **Giới hạn đã biết:** mọi lần mở trước lần đầu
   app truyền id (lần mở đầu tiên sau khi nâng lên 1.4.0 hoặc sau khi cài, hoặc mọi lần mở trước
   lần đăng nhập đầu nếu app chỉ truyền id sau đăng nhập), khi chưa có id lưu, các hàng đó (và
   exposure tự động nếu app bật brain trước `super.onCreate()`) mang UUID của SDK hay id app tuỳ
   race (body dựng trên luồng nền); từ đó là id app. Backend ghép theo `userProperty.session_id`
   (một UUID mỗi process): hàng mang UUID của SDK và hàng mang id app cùng `session_id` là một
   người, ghép được; hàng `brain_decision` replay mang `user_id` lúc ghi của process trước với
   `session_id` của process gửi lại — không tính là ghép theo cặp id app khác nhau (review D M7b,
   review cuối I2).
