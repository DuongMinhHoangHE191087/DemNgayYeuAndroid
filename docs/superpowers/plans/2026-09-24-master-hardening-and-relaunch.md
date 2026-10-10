# InLove — Master Hardening, UI Rebuild & Play Store Readiness Plan

> Nguồn: khảo sát toàn bộ `:app` (58 file Kotlin, ~36k dòng) + `:appplugin` (141 file, SDK ads/IAP/brain nội bộ) + rà bảo mật (`firestore.rules`, `AndroidManifest`, billing, network, storage) qua 3 agent song song ngày 2026-09-24, cộng với `docs/superpowers/plans/2026-03-29-billing-upgrade.md` (đã complete) và `2026-03-31-ads-firebase-android16-hardening.md` (đã complete theo `.superpowers/sdd/.../progress.md`, dù checkbox trong file chưa tick).
>
> **Cách dùng:** mỗi Task có checkbox `- [ ]`. Superpower `executing-plans` / `subagent-driven-development` chạy tuần tự theo Task, cập nhật ledger tại `.superpowers/sdd/2026-09-24-master-hardening-and-relaunch/progress.md`. KHÔNG đổi Product ID billing thật hoặc xoá dữ liệu người dùng thật mà không xác nhận với chủ dự án.

> **Đối soát 2026-10-08** (chi tiết: `docs/superpowers/plans/2026-10-08-release-execution-plan.md`). Chỉ tick khi có bằng chứng.
> - A1: code xong, chưa xác minh runtime. A2: có test repo, thiếu test ViewModel. A3: đã dùng `VipProductIds` (tick). A4: `changePassword` đã cập nhật Firebase; OTP/security-answer local còn. A5: mở. A6: `exportSchema` và `MIGRATION_12_13` có; test `@Ignore`. A7: ký và R8 bật; keep rules còn rộng. A8: code dùng `requireEnv`; rotate là việc owner. A9: `firestore.rules` có `hasOnly`; test emulator chưa chạy. A10: chưa xác minh. A11: fraction 0.05 nhưng brain tắt; owner xác nhận.
> - B1: một phần (invite listener có; tìm kiếm còn local). B2: một phần. B3: chưa xác minh. B4: mở (`upgradeSubscription` còn). B5: quyết định (light cố định). B6: một phần. B7: client xong; xoá Cloudinary mở.
> - C1: một phần (`Shapes` có, chưa có `Shape.kt`). C2–C6: mở hoặc chưa xác minh. D3: mở (20 seed). D1, D2, D4, E1: chưa xác minh.
> - F3: xong (tick). Data Safety, domain, trang xoá tài khoản web: mở.
> - Đính chính: minSdk của app là 24 (không phải 26, xem `AGENTS.md`); appplugin: catalog 2.3.0, sibling 2.4.2 (P1-2).

## Global Constraints
- Target SDK 36, Min SDK 24 (app; đính chính 2026-10-08) / 26 (appplugin), Java 17.
- `appplugin/` là **git repo lồng riêng** (`appplugin/.git`), không được `git add -A` từ root cuốn theo — luôn `git status` trước khi add.
- Máy build hiện dùng Gradle daemon JDK 25 (`eclipse_adoptium-25-amd64-windows.2`) — đã từng OOM crash (`app/hs_err_pid15228.log`). Cân nhắc pin daemon về JDK 17 (`org.gradle.java.home`) nếu OOM lặp lại.
- Mọi thay đổi billing/entitlement phải giữ nguyên tắc: **Entitlements.recompute() là nguồn sự thật duy nhất nối quyền lợi → quảng cáo** (đã đúng thiết kế trong appplugin) — không tạo thêm đường tắt thứ hai.
- Không đổi Firebase Product/Project ID, không xoá `google-services.json` (public-by-design).

---

## PHẦN A — P0: Bảo mật & Crash chặn Google Play (làm trước)

### Task A1: Sửa thứ tự khởi tạo Firebase App Check (chạy trước khi plugin fetch Firestore/RemoteConfig)
**File:** `app/src/main/java/com/example/InLoveApplication.kt`
- [ ] Chuyển `FirebaseApp.initializeApp(this)` + cài `AppCheckProviderFactory` lên **trước** `super.onCreate()` (hiện tại App Check được cài sau khi `AppPluginBase.onCreate()` → `AppPluginManager.initPlugin()` đã tự gọi `FirebaseApp.initializeApp` + bắn `FirHelper.fetchRemoteCf`/`fetchFireStore`). `FirebaseApp.initializeApp` gọi 2 lần là an toàn (plugin đã bọc `Throwable`).
- [ ] Giữ nguyên nhánh test Robolectric (`isRunningInTest`).
- [ ] Test: `AuthSecurityTest`, `ExampleRobolectricTest` vẫn pass.

### Task A2: Fix race "VIP bị thu hồi khi mở app" (billing chưa kịp trả lời)
**File:** `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` (khu vực dòng ~331-389)
- [ ] Không hạ cấp xuống FREE ngay khi `billingManager.isVipUser` khởi tạo `false`; chỉ hạ cấp sau khi nhận được **ít nhất một** giá trị thật từ `queryExistingPurchases()`/`purchaseEvent`, hoặc cache `isVipUser` cuối cùng vào Room/Prefs và chỉ ghi đè khi billing đã "settled" (thêm cờ `hasSyncedOnce` trong `BillingManager`).
- [ ] Test mới: giả lập `BillingManager` chưa emit gì → ViewModel không được gọi `updateUserSubscription(FREE)`.

### Task A3: Thống nhất Product ID billing (đơn nguồn sự thật)
**Files:** `app/src/main/java/com/example/billing/BillingManager.kt`, `app/src/main/java/com/example/InLoveApplication.kt`
- [x] Tạo `object VipProductIds` (trong `com.example.billing`) — đối soát 2026-10-08: dùng ở `InLoveApplication.kt:79–81` chứa 3 hằng số hiện dùng thật trong `BillingManager` (`vip_monthly`, `vip_yearly`, `vip_lifetime` — đây là ID đã cấu hình Paywall/Play Console thật). **Không đổi giá trị này** — chỉ trỏ `MonetizationSdk.configure { inappProducts / subsProducts / removeAdsProducts }` trong `InLoveApplication.kt` về cùng `VipProductIds` thay vì hardcode `inlove_vip_*` (ID chưa từng khớp Play Console).
- [ ] Ghi rõ trong code comment: BillingManager là billing client THẬT duy nhất chạy purchase flow; `IapHelper`/`MonetizationSdk` phía appplugin chỉ dùng để đồng bộ `Entitlements` (tắt quảng cáo), không tự chạy song song một luồng mua hàng khác. Xem xét: có nên tắt `IapHelper.init()` (đặt `startBilling=false` hoặc không khai `inappProducts/subsProducts` trong `configure`) để tránh 2 `BillingClient` cùng lúc — **cần xác nhận với chủ dự án trước khi tắt**, ghi vào ledger nếu chưa quyết được, chuyển sang Task A3b.
- [x] Sau khi PURCHASED, gọi `Entitlements.grant(productId)`/`sync(...)` từ `BillingManager` để cầu nối quyền lợi sang appplugin's `AdsHelper.setRemoveAds` — đã code (xem ledger).
- [x] **QUYẾT ĐỊNH (đã đọc toàn bộ `IapHelper.kt` 516 dòng để trả lời câu hỏi treo ở trên):** GIỮ NGUYÊN 2 `BillingClient`, không tắt `IapHelper`. Lý do:
  - `IapHelper.purchase()` KHÔNG được gọi ở đâu trong `:app` (chỉ `BillingManager.launchPurchaseFlow` được UI gọi) → không có 2 client cùng tranh giành mở luồng mua, callback `onPurchasesUpdated` của `IapHelper` không nhận sự kiện mua thật, chỉ `restorePurchases()` định kỳ mới thấy giao dịch (độ trễ nhỏ, chấp nhận được).
  - `IapHelper` tự làm được nhiều việc `BillingManager` KHÔNG có: phân loại vòng đời subscription ACTIVE/CANCELLED/IN_GRACE_PERIOD/LAPSED (`SubscriptionLifecycle`), giữ quyền lợi đúng trong grace period (payment fail nhưng Play đang retry), báo doanh thu về AppsFlyer, và tín hiệu mua hàng cho `AdsBrain`. Tắt `IapHelper` sẽ mất hết các việc này mà `BillingManager` chưa làm.
  - Rủi ro thật duy nhất trước đây (2 bộ Product ID khác nhau) đã hết sau khi thống nhất qua `VipProductIds`. Gọi trùng `acknowledgePurchase`/`consumeAsync` từ 2 client là an toàn (Play Billing API idempotent, trả OK hoặc lỗi rõ ràng, không lỗi kép).

### Task A4: `changePassword()` / OTP reset phải đổi mật khẩu Firebase thật
**File:** `app/src/main/java/com/example/data/repository/AuthRepository.kt` (~L593-737)
- [ ] Sau khi xác thực lại (`reauthenticate`), gọi `FirebaseUser.updatePassword(newPassword)` thay vì chỉ cập nhật hash Room.
- [ ] OTP / security-answer reset: hoặc bỏ hẳn (route sang `sendPasswordResetEmail`), hoặc giữ OTP nhưng chốt bằng `updatePassword` sau khi xác minh — **không để trạng thái "đã đổi" mà Firebase vẫn mật khẩu cũ**.
- [ ] `RealSmtpEmailSender.kt` đang là stub (log + return success) — nếu giữ luồng OTP, phải nối SMTP thật (`.env` đã có field `SMTP_*`) hoặc chuyển hẳn sang Firebase's built-in reset email.
- [ ] Test: `AuthRepositoryTest`, `EmailQueueServiceTest` cập nhật theo hành vi mới.

### Task A5: Cloudinary — preset không chữ ký & lỗi upload báo "thành công" giả
**Files:** `app/src/main/java/com/example/data/cloudinary/CloudinaryStorageService.kt`, `CloudinaryMediaService.kt` (trùng lặp — xoá 1 trong 2, giữ `CloudinaryStorageService` vì UI đang dùng)
- [ ] Xoá `CloudinaryMediaService.kt` (dead code trùng lặp) sau khi xác nhận không còn tham chiếu.
- [ ] Khi upload lỗi: KHÔNG trả `isSuccess = true` với URL giả — trả lỗi thật, để UI hiển thị "Chưa đồng bộ, thử lại" thay vì âm thầm mất ảnh.
- [ ] Đóng `Response` OkHttp (`use { }`) để tránh leak connection.
- [ ] Ghi rõ hạn chế preset unsigned trong `docs/superpowers/plans/...`: khuyến nghị dài hạn cần backend ký upload (Cloud Function) — out of scope sửa ngay vì cần hạ tầng mới; tạm thời narrow preset permissions trên Cloudinary Console (giới hạn định dạng/kích thước) — việc này làm ở Cloudinary Console, không phải code.

### Task A6: Room không có migration ở version 12
**File:** `app/src/main/java/com/example/data/db/AppDatabase.kt`
- [ ] Thêm `exportSchema = true` + thư mục `schemas/`, viết `Migration` rỗng-an toàn (hoặc tối thiểu `fallbackToDestructiveMigration()` **có chủ đích + log cảnh báo** thay vì OnDowngrade-only) cho version 13 trở đi. Ưu tiên: bắt đầu export schema từ version hiện tại để version sau có migration thật.
- [ ] Test: cài version cũ → build DB giả version 11 → migrate lên 12 không mất dữ liệu (test Room migration chuẩn).

### Task A7: Release build — bật R8 có kiểm soát + gán signingConfig cho `release`
**File:** `app/build.gradle.kts`, `app/proguard-rules.pro`
- [ ] Gán `signingConfig = signingConfigs.getByName("release")` cho buildType `release` (hiện thiếu dòng này — release build hiện tại **không ký**).
- [ ] Bật `isMinifyEnabled = true`, `isShrinkResources = true`; thu hẹp keep rules (hiện keep `com.google.firebase.**`, `com.google.android.gms.**` toàn bộ — quá rộng, cản trở shrink). Thêm `-assumenosideeffects` cho `android.util.Log.*`.
- [ ] Giữ nguyên keep rule cho `com.google.firebase.appcheck.**` (reflection) đã có từ hardening plan trước.
- [ ] Build thử `assembleRelease` (cần `KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD` env hoặc dùng debug keystore tạm để verify build không vỡ do R8) — nếu thiếu keystore thật, chỉ verify bằng `:app:assembleDebug` với `isMinifyEnabled=true` tạm trên debug buildType để bắt lỗi ProGuard sớm, rồi revert cấu hình test đó.
- [ ] Chặn build khi thiếu `ADMOB_*_RELEASE` (hiện default `""` → AdMob App ID rỗng → crash khởi động): thêm `check()` fail-fast trong `build.gradle.kts` khi build variant `release` và property rỗng.

### Task A8: `seed_firestore.js` — xoá mật khẩu test khỏi repo
**File:** `scripts/seed_firestore.js`
- [ ] Chuyển danh sách tài khoản test + mật khẩu sang file `.env`-style KHÔNG commit (hoặc đọc từ biến môi trường), xoá literal khỏi source đã track.
- [ ] Xác nhận với chủ dự án: các tài khoản `admin@inlove.app` v.v. có tồn tại thật trên Firebase Auth production không → nếu có, cần đổi mật khẩu ngay (việc này làm trên Firebase Console, ghi chú lại, không phải code).
- [ ] `git log` đã có các literal này ở các commit trước — cân nhắc rotate thay vì rewrite history (rewrite history rủi ro cao, cần hỏi trước khi làm).

### Task A9: firestore.rules — chặn ghi field nhạy cảm & khớp thành viên relationship
**File:** `firestore.rules`
- [ ] `users/{uid}`, `users_3nf/{uid}`: thêm `request.resource.data.keys().hasOnly([...])` + khoá field `role`/`tier`/`isVip` khỏi client write.
- [ ] `memories`/`memories_3nf`/`anniversaries` create: bắt buộc kiểm tra `relationshipId` (nếu có) thuộc về `auth.uid` qua `get()` trên `relationships/{relationshipId}`.
- [ ] `relationships` create/update: khoá `partnerAId`/`partnerBId` khỏi update tuỳ tiện; cân nhắc đổi bước tạo sang random ID thay vì `rel_<millis>` (đổi ở `OnlineCoupleRepository.kt:374` phía client tương ứng).
- [ ] `invites`: chỉ `targetUid` được đổi `status`, chỉ PENDING→ACCEPTED/REJECTED.
- [ ] Test bằng Firebase Emulator Rules Unit Test nếu có sẵn hạ tầng test; nếu chưa có, ghi rõ cần thêm `firestore.rules.test.ts` như một sub-task riêng (không blocking P0 khác).

### Task A11: `brainRolloutFraction = 1.0` trái khuyến nghị của chính SDK — CẦN QUYẾT ĐỊNH SẢN PHẨM
**File:** `app/src/main/java/com/example/InLoveApplication.kt`. Chi tiết đầy đủ: `docs/APPPLUGIN_SDK_OVERVIEW.md`.
- Đã đọc `appplugin/DOC/00_INDEX.md` (chỉ mục knowledge base 65 file) và `MonetizationSdk.kt` trực tiếp: `:appplugin` không chỉ là SDK ads/IAP mà là một "Player Decision Platform" nghiên cứu sâu (contextual bandit, POMDP), thiết kế cho game, nhắm US/EU, và chính KDoc của SDK khuyến nghị mở dần từ `0.05` — InLove đang bật thẳng `1.0` cho 100% người dùng, trong khi backend học tập trung của SDK "chưa deploy, chưa train" theo tài liệu của chính nó.
- [ ] **Cần chủ dự án xác nhận:** hạ `brainRolloutFraction` xuống 0.0-0.05, hay giữ nguyên 1.0 có chủ đích? Đây là quyết định sản phẩm (ảnh hưởng hành vi hiện quảng cáo cho toàn bộ người dùng), không tự ý đổi.
- [ ] Nếu quyết định hạ: chỉ cần đổi 1 giá trị số trong `InLoveApplication.kt`, rủi ro kỹ thuật thấp — nhưng cần build-verify + theo dõi tỉ lệ hiện quảng cáo trước/sau khi đổi.

### Task A10: Telemetry gửi trước khi có consent
**File:** `appplugin/src/main/java/com/app/plugin/logevent/LogEventServer.kt`, `LogEventServerHelper.kt`, `app/src/main/java/com/example/InLoveApplication.kt`
- [ ] Trì hoãn `LogEventServer.sessionStart()` / các event đầu tiên cho tới khi `ConsentManager` đã có kết quả tier (GRANTED/DENIED/PARTIAL), thay vì bắn ngay trong `AppPluginBase.initPlugin()`.
- [ ] Với DENIED: không gửi ANDROID_ID / SIM country (theo `ConsentTier.DENIED` guardrail đã có ở brain, áp dụng thêm cho log event).

---

## PHẦN B — P1: Đúng chức năng lõi (pairing thật, offline/online mạch lạc)

### Task B1: Pairing 2 máy thật qua Firestore (hiện chỉ hoạt động trong Room 1 máy) — ĐÃ ĐỌC TOÀN BỘ FILE, XÁC NHẬN 100%
**File:** `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` (đã đọc toàn bộ 579 dòng ngày 2026-09-24)
- **Xác nhận trực tiếp (không còn suy đoán):** `acceptSetLoveInvite()` (L355) chỉ đọc `_incomingInvite.value` (in-memory) hoặc `dao.getInviteByIdSync()` (Room) — KHÔNG có bất kỳ đọc Firestore nào trong toàn bộ file. `sendSetLoveInvite()` (L286) là nơi DUY NHẤT chạm Firestore: ghi fire-and-forget vào `invites/{inviteId}` (L346, field `senderUid`) mà không ai từng đọc lại. Kết luận: **2 thiết bị vật lý khác nhau không thể ghép đôi được trong bản hiện tại** — mọi luồng test/demo chỉ hoạt động vì 2 "user" A/B cùng chia sẻ 1 Room DB trên 1 máy.
- [ ] Viết lại `sendSetLoveInvite`/`acceptSetLoveInvite`/`rejectSetLoveInvite`/`requestBreakup`/`confirmBreakup` để đọc/ghi Firestore thật (collection `invites` đã có field `senderUid`/`targetUid` khớp `firestore.rules` đã sửa ở Task A9; cần thêm collection `relationships` ghi thật — hiện `OnlineRelationshipEntity` CHỈ tồn tại trong Room, chưa từng ghi Firestore).
- [ ] Dùng `snapshotListener` cho invite đến, cập nhật Room làm cache offline (kiến trúc offline-first: Room là cache, Firestore là nguồn sự thật khi online).
- [ ] Loại bỏ demo user A/B (`switchDemoUser`, `USER_A_ID`/`USER_B_ID`) khỏi luồng production (giữ lại sau `BuildConfig.DEBUG` nếu cần cho QA).
- **Đây là Task ưu tiên cao nhất của Phần B** — "Set Love 1-1" là lời hứa giá trị cốt lõi của app (xem `docs/PRODUCT_RESEARCH.md`), hiện không hoạt động đúng như quảng cáo giữa 2 người dùng thật.

### Task B2: Thêm connectivity detection tập trung
**File mới:** `app/src/main/java/com/example/data/network/ConnectivityObserver.kt`
- [ ] `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>` đơn giản, expose qua `AppServiceLocator`.
- [ ] Dùng nó để: (a) hiện banner "Đang offline — dữ liệu sẽ đồng bộ khi có mạng" ở Settings/Home, (b) gate các fetch Firestore (tránh timeout 2s vô ích khi biết chắc offline), (c) trigger sync lại khi mạng vừa có (online → gọi `syncAllCloudPresets()` / `Firebase3NFService` nếu bật lại).

### Task B3: Kích hoạt đồng bộ Firestore 2 chiều cho memories/anniversaries (hiện `Firebase3NFService` chết, không ai gọi)
**File:** `app/src/main/java/com/example/data/firebase/Firebase3NFService.kt`, `InLoveRepository.kt`
- [ ] Gọi `syncToFirebaseCloud()` sau mỗi thay đổi memories/anniversaries quan trọng, hoặc theo debounce định kỳ khi online (dùng `ConnectivityObserver` từ B2).
- [ ] Sửa lỗi ID: dùng `"${uid}_${localId}"` thay vì auto-increment Room ID làm doc ID (tránh đụng độ giữa nhiều user, khớp Task A9's rule fix).
- [ ] Test round-trip: tạo memory offline → có mạng → xuất hiện ở `memories_3nf`.

### Task B4: Sửa 2 bug "ondemand" dialog/tab chết
- [ ] `SetAlarmReminderDialog` (`Dialogs.kt`) chưa từng được `@Composable` gọi trong `MainActivity.kt` dù `CalendarScreen`/`GiftScreen` gọi `viewModel.openSetAlarmDialog(...)` — nối dây (compose nó trong `InLoveApp()` giống các dialog khác) hoặc xoá nếu không còn cần.
- [ ] `ReminderScreen.kt` (1187 dòng) không nằm trong bất kỳ tab nào dù có `navReminders` string — quyết định: (a) thêm làm tab thứ 6, hoặc (b) gộp chức năng vào Settings/Calendar rồi xoá file. **Cần hỏi chủ dự án nếu muốn giữ làm tab riêng** (ảnh hưởng điều hướng/IA) — mặc định đề xuất: gộp reminder logic hiện có (`CustomReminderEntity`) vào Calendar tab đã có, xoá `ReminderScreen.kt`, tránh phình thêm điều hướng.
- [ ] Xoá dead code còn lại: `VipSubscriptionDialog.kt` (unused), `AdBannerPlaceholder()` alias, `open3NFVisualizerDialog`, `upgradeSubscription()` (cấp VIP không qua thanh toán — nguy hiểm, xoá hẳn).

### Task B5: Dark theme — XÁC NHẬN Ý ĐỊNH TRƯỚC KHI SỬA (không phải bug rõ ràng)
**File:** `app/src/main/java/com/example/ui/theme/Theme.kt`
- [ ] `MyApplicationTheme` hiện luôn ép `LightColorScheme` kèm comment tường minh "Always prioritize a crisp, vibrant, romantic Light Mode UI" — đây nhiều khả năng là QUYẾT ĐỊNH THIẾT KẾ có chủ đích (đúng tinh thần "giữ đúng theme phong cách" người dùng yêu cầu), không phải lỗi quên code. `DarkColorScheme` được định nghĩa sẵn nhưng chưa dùng có thể là chuẩn bị cho tương lai, chưa chắc là thiếu sót cần vá ngay.
- [ ] **Không tự ý bật lại dark mode.** Nếu triển khai, cần hỏi chủ dự án: có muốn thêm dark mode như một TÙY CHỌN (toggle riêng trong Settings, mặc định vẫn Light) hay giữ nguyên luôn-Light là chủ đích sản phẩm.

### Task B6: Wallpaper không được lưu bền
**File:** `InLoveViewModel.kt` (`setWallpaper`, ~L1172)
- [ ] Lưu `selectedWallpaperUrl` vào Room (`couple_profile` hoặc bảng mới) thay vì chỉ `StateFlow` trong bộ nhớ — hiện mất khi restart app.

### Task B7: Xoá tài khoản chưa xoá hết dữ liệu liên quan — CODE DONE (chưa build-verify)
**File:** `AuthRepository.kt` (`deleteCurrentAccount`)
- [x] Đảo thứ tự: xoá Firebase Auth identity TRƯỚC (bắt riêng `FirebaseAuthRecentLoginRequiredException`, trả lỗi rõ ràng yêu cầu đăng nhập lại), chỉ xoá Firestore/Room SAU KHI Auth đã xoá thành công — trước đây xoá Firestore trước nên khi Auth ném lỗi "cần đăng nhập gần đây", dữ liệu đã mất nhưng tài khoản Auth vẫn còn (kẹt trạng thái nửa xoá).
- [x] Thêm xoá `invites` collection nơi `senderUid == uid` (đã xác nhận qua đọc toàn bộ `OnlineCoupleRepository.kt`: đây là collection Firestore DUY NHẤT khác ngoài `users`/`memories` mà app thực sự ghi tới — `relationships`/`relationships_3nf`/`invites_3nf` hiện KHÔNG được client ghi, không cần dọn cho tới khi Task B1 triển khai ghi thật).
- [ ] Xoá media Cloudinary đã upload (cần Cloudinary Admin API + credentials — chưa làm, out of scope tới khi có backend theo Task A5).

---

## PHẦN C — P2: Dọn UI theo đúng theme, giảm code, giữ phong cách "Dreamy Romantic Rose"

> Chuẩn tham chiếu: `DESIGN.md` (đã có token màu, typography, spacing, animation). Mục tiêu: **không đổi phong cách**, chỉ dọn implementation — thay ~1121 chỗ `Color(0x...)` rải rác bằng `MaterialTheme.colorScheme.*` / token trong `Color.kt`, giảm kích thước các file khổng lồ (`Dialogs.kt` 2385 dòng, `CalendarScreen.kt` 2099, `MemoriesGridScreen.kt` 1952...) bằng cách tách composable con theo màn hình, KHÔNG viết lại từ đầu.

### Task C1: Thiết lập `Shapes` Material3 chính thức (hiện thiếu hẳn)
**File:** `app/src/main/java/com/example/ui/theme/Shape.kt` (mới)
- [ ] Định nghĩa `Shapes(extraSmall..extraLarge)` khớp `DESIGN.md` (card 24-28dp, badge 50dp stadium, button 14-50dp), gắn vào `MaterialTheme(shapes = ...)` trong `Theme.kt`.

### Task C2: Thay thế hardcode màu bằng token theme — theo từng file lớn, không đổi giao diện nhìn thấy được
**Files:** `Dialogs.kt`, `CalendarScreen.kt`, `MemoriesGridScreen.kt`, `SettingsScreen.kt`, `LoveHomeScreen.kt`, `PairingScreen.kt`, `AuthScreen.kt`, `MilestoneBadgeTracker.kt` (theo thứ tự nhiều `Color(0x` nhất trước)
- [ ] Với mỗi file: map từng `Color(0xFF....)` sang token `Color.kt` đã có sẵn giá trị trùng khớp (đối chiếu hex) hoặc thêm token mới nếu màu đó lặp lại ≥3 lần nhưng chưa có tên — **giữ nguyên giá trị hex**, chỉ đổi cách tham chiếu.
- [ ] Sau mỗi file: build + Roborazzi screenshot test để đảm bảo pixel không đổi bất ngờ.
- [ ] Track tiến độ: ghi số `Color(0x` còn lại mỗi file vào ledger.

### Task C3: Tách các file Composable khổng lồ thành nhiều file nhỏ theo section (không đổi hành vi)
- [ ] `Dialogs.kt` (2385d) → tách theo nhóm: `ReminderDialogs.kt`, `MilestoneDialogs.kt`, `AnniversaryDialogs.kt`, `GiftDialogs.kt`, `CoupleEditDialogs.kt`.
- [ ] `CalendarScreen.kt` (2099d) → tách `CalendarGrid.kt`, `CalendarEventList.kt`, giữ `CalendarScreen.kt` làm orchestrator mỏng.
- [ ] `MemoriesGridScreen.kt` (1952d), `SettingsScreen.kt` (1886d), `MilestoneBadgeTracker.kt` (1598d) — tương tự, tách theo cụm chức năng rõ ràng (mỗi file con < ~500 dòng).
- [ ] Không gộp logic nghiệp vụ vào Composable khi tách — nếu thấy logic tính toán lẫn trong UI, chuyển sang `ui/viewmodel` hoặc file `*Extensions.kt` (dọn dần theo Clean Architecture, không bắt buộc UseCase layer đầy đủ ngay).

### Task C4: `InLoveViewModel.kt` (1778 dòng) — tách theo domain
- [ ] Tách thành nhiều ViewModel nhỏ theo domain (`HomeViewModel`, `MemoriesViewModel`, `CalendarViewModel`, `GiftViewModel`, `SettingsViewModel`) HOẶC (an toàn hơn, ít rủi ro hồi quy hơn) giữ 1 ViewModel nhưng tách các nhóm hàm sang `*.kt` riêng dùng Kotlin `context receiver`/extension trên private repo refs — **quyết định cụ thể để ở đầu Task khi thực thi**, ưu tiên phương án ít rủi ro (extension file) trước, refactor ViewModel thật sự để sau khi có test coverage tốt hơn (xem Task D-cuối).

### Task C5: Localization — thay literal tiếng Việt cứng bằng `stringResource`
**Files:** 42 file có chuỗi tiếng Việt cứng (ưu tiên `Dialogs.kt` 164, `InLoveViewModel.kt` 105, `SettingsScreen.kt` 91, `InLoveRepository.kt` 79, `CalendarScreen.kt` 75)
- [ ] Thêm string res còn thiếu vào `values/strings.xml` + `values-vi/strings.xml` (đang có 222 mỗi file, `AppStrings` đã có field tương ứng phần lớn — ưu tiên route qua `LocalizedStrings`/`AppStrings` đã có sẵn field thay vì tạo cơ chế mới).
- [ ] Không đổi cơ chế i18n hiện tại (SharedPrefs + `AppStrings` bundle) — chỉ tăng độ phủ, giảm literal.

### Task C6: `MainActivity.kt` — sửa `LocalizedStrings.fromContext` chạy lại mỗi recomposition, sửa shadow biến trùng tên
- [ ] Bọc bằng `remember(appLanguage)`.
- [ ] Đổi tên biến trùng (`loveDays`, `appLanguage` khai 2 lần trong `InLoveApp`).

---

## PHẦN D — P3: Dữ liệu — Mock data quà tặng & lịch lễ VN/US, đầy đủ song ngữ

### Task D1: Bộ dữ liệu ngày lễ/kỷ niệm Việt Nam (âm + dương lịch) — file mới
**File mới:** `app/src/main/java/com/example/data/seed/VietnameseHolidays.kt`
- [ ] Danh sách tối thiểu (dương lịch cố định): 1/1 Tết Dương lịch, 14/2 Valentine, 8/3 Quốc tế Phụ nữ, 20/3 Ngày Hạnh phúc, 26/3, 30/4, 1/5, 7/5, 19/5, 1/6 Quốc tế Thiếu nhi, 28/6 Gia đình Việt Nam, 27/7, 2/9 Quốc khánh, 15/10 Phụ nữ Việt Nam, 20/10 Phụ nữ Việt Nam (đã có), 20/11 Nhà giáo, 22/12.
- [ ] Danh sách âm lịch (cần công thức quy đổi âm-dương hoặc bảng tra theo năm, KHÔNG hardcode ngày dương cố định vì lệch mỗi năm): Tết Nguyên Đán (30 Tết→mùng 3), Rằm tháng Giêng, Giỗ Tổ Hùng Vương (10/3 âm), Tết Trung Thu (15/8 âm), Rằm tháng 7 (Vu Lan). Dùng thư viện quy đổi âm lịch VN có sẵn (kiểm tra `libs.versions.toml` có `nlopez`/`AmLich`/tương tự chưa; nếu chưa, thêm dependency nhẹ hoặc viết thuật toán quy đổi chuẩn — không ước lượng bừa vì sai ngày Tết gây mất uy tín app).
- [ ] Mỗi mục: `title` (VI/EN), `dateRule` (cố định hoặc âm lịch), `emoji`, `suggestedGiftCategory`, `isCoupleRelevant: Boolean` (lọc bớt ngày không liên quan tình yêu nếu cần UI riêng).

### Task D2: Bộ dữ liệu ngày lễ Mỹ/phương Tây (song ngữ, cho người dùng English)
**File mới:** `app/src/main/java/com/example/data/seed/WesternHolidays.kt`
- [ ] New Year's Day, Valentine's Day, St. Patrick's Day, Easter (di động — công thức Computus hoặc bảng tra theo năm), Mother's Day (2nd Sun of May), Father's Day (3rd Sun of June), Independence Day (Jul 4), Halloween, Thanksgiving (4th Thu of Nov), Christmas, New Year's Eve, Anniversary chung (Sweetest Day - 3rd Sat Oct, National Boyfriend/Girlfriend Day...).
- [ ] Cùng cấu trúc dữ liệu với D1 để dùng chung UI hiển thị lịch.

### Task D3: Mở rộng mock data quà tặng — nhiều hơn, đa dạng theo dịp/ngân sách/đối tượng, song ngữ
**File:** `app/src/main/java/com/example/data/repository/InLoveRepository.kt` (`seedOfflineDataIfStillEmpty`) hoặc tách sang `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt` (mới, để giảm size file repository theo Task C3 tinh thần)
- [ ] Mở rộng từ 2 → tối thiểu 30-40 gợi ý quà, phân theo: khoảng giá (`priceRange`: <200k, 200-500k, 500k-1tr, >1tr), dịp (`suggestedOccasion`: Valentine, kỷ niệm, sinh nhật, 20/10, Giáng sinh, Tết...), đối tượng (nam tặng nữ / nữ tặng nam / không phân biệt), có `imageUrl` Unsplash hợp lệ như mẫu hiện tại.
- [ ] Mỗi gift có bản EN song song (hiện chỉ VI) — thêm field hoặc dùng cùng pattern `titleVi/titleEn` như `LoveBadgeEntity` đã làm.
- [ ] Không cần AI thật — vẫn là seed tĩnh nhưng phong phú hơn nhiều, có phân loại để lọc theo tab Gift Screen (Category filter đã có UI, hiện chỉ 2 item nên filter vô nghĩa).

### Task D4: Nối `VietnameseHolidays`/`WesternHolidays` vào Milestone/Calendar hiện có
- [ ] `LoveAnniversaryMilestoneScheduler.kt` hoặc `InLoveRepository` seed thêm các mốc trên vào bảng `milestones`/`anniversary_dates` khi `AppLanguage` tương ứng (VI → seed thêm holidays VN, EN → seed Western), không phá schema hiện có.
- [ ] `CalendarScreen.kt` hiển thị đúng theo ngôn ngữ đang chọn.

---

## PHẦN E — Nghiên cứu hành vi người dùng & thị trường (đầu ra: tài liệu, không phải code)

### Task E1: Viết `docs/PRODUCT_RESEARCH.md`
- [ ] Phân khúc người dùng mục tiêu (đã có trong `PRODUCT.md`) — bổ sung: hành vi mở app hằng ngày để xem "số ngày yêu" (hook chính, giữ đúng như hiện tại — không đổi).
- [ ] Đối thủ tham khảo dạng "ngày yêu" phổ biến trên thị trường Việt & quốc tế (liệt kê tính năng họ có mà InLove còn thiếu: widget màn hình khoá, chia sẻ story lên MXH, lịch âm tích hợp).
- [ ] Đề xuất tính năng ưu tiên theo P1/P2/P3 ở trên đã phản ánh phần lớn — task này chỉ tổng hợp lại dạng tài liệu sản phẩm để chủ dự án dùng làm roadmap sau khi launch.

---

## PHẦN F — Google Play Readiness Checklist (chốt trước khi submit)

- [ ] Data Safety form khai đủ SDK đã liệt kê (Firebase*, AdMob + toàn bộ mediation adapters, AppsFlyer, Cloudinary, databuckets ingest endpoint).
- [ ] Privacy Policy URL thống nhất 1 domain (hiện `inloveapp.com` / `inlove.app` / `inlove-app.web.app` lẫn lộn — chọn 1, sửa tất cả nơi tham chiếu).
- [x] UMP "Privacy options" entry point trong Settings (đối soát 2026-10-08: `SettingsScreen.kt:681`, `PrivacyOptionsRowTest` 4 test) (hiện chưa có, bắt buộc nếu phục vụ EEA/UK).
- [ ] Account deletion hoàn tất (Task B7) + link web deletion hoạt động thật.
- [ ] Release build ký đúng keystore thật (Task A7), R8 bật, version code/name hợp lý cho lần submit đầu.
- [x] **`PurchaseRouter` — đã đọc toàn bộ file (309 dòng) và xác nhận: KHÔNG phải rủi ro thật cho InLove hiện tại.** Mặc định `WEB_SHOP_OFF` (an toàn), chỉ bật khi Firebase Remote Config gửi `web_shop.enabled=true` qua `FirHelperConfig.kt` → `PurchaseRouter.apply()`. Nhưng `:app` KHÔNG hề gọi `PurchaseRouter.purchase()` ở bất kỳ đâu (grep xác nhận 0 kết quả) — `PaywallScreen.kt` gọi thẳng `BillingManager.launchPurchaseFlow()` (Play Billing thuần), router bị bỏ qua hoàn toàn dù Remote Config nói gì. Không cần hành động thêm trừ khi sau này chủ động nối `:app` qua router.
- [ ] Xoá/làm rõ claim "AI" trong `SettingsScreen.kt` nếu không triển khai Firebase AI thật trước khi submit (tránh vi phạm "Misleading claims" của Play Policy).
- [ ] Test cài đặt mới hoàn toàn (uninstall → install) và test nâng cấp (giữ bản cũ có data → cài bản mới) sau khi Task A6 (Room migration) xong.

---

## Thứ tự thực thi đề xuất
1. Phần A (A1→A10) — bảo mật/crash, giá trị cao, rủi ro thấp trừ A3/A7 cần thận trọng build-test.
2. Phần B (B1→B7) — chức năng lõi online/offline mạch lạc.
3. Phần C (C1→C6) — dọn UI/theme, làm song song được với B vì không đụng chung file nhiều.
4. Phần D (D1→D4) — mock data, làm sau khi schema/seed pattern ổn định từ B.
5. Phần E, F — tài liệu + checklist chốt cuối trước khi submit Play Store.
