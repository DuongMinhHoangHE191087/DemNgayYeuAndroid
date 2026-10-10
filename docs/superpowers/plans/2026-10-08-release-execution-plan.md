# Kế hoạch thực thi phát hành InLove — chốt ngày 2026-10-08

Trạng thái: **bản nháp thực thi**. Chưa commit, chưa push, chưa publish. Kế hoạch này gom ba nguồn:
- `docs/superpowers/plans/2026-10-07-release-readiness-plan.md` (Steps 1–31)
- `docs/superpowers/plans/2026-10-06-release-hardening.md` (T1–T9)
- `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md` (Phần A–F)

Mọi trạng thái dưới đây dựa trên đọc mã và kết quả chạy thực tế trong ngày. Không có gì được suy ra từ ghi chú cũ mà chưa đối chiếu lại.

Ký hiệu trạng thái:
- **XONG (code + bằng chứng)**: có mã và có test/lệnh đã chạy.
- **XONG (code, thiếu bằng chứng)**: có mã, chưa có test hoặc lệnh chứng minh.
- **MỘT PHẦN**: có một phần mã, còn thiếu phần quan trọng.
- **MỞ**: chưa có mã đáng kể.
- **QUYẾT ĐỊNH**: chờ chủ dự án chọn.
- **CHẶN**: thiếu đầu vào không nằm trong tầm kiểm soát của repo.

---

## 0. Tóm tắt

1. Unit test: `:app:testDebugUnitTest` **PASS** — 30 file XML, 112 test, 0 failure, 0 error, 1 skipped (`Migration12To13Test`, `@Ignore`). `GRADLE_EXIT=0`, chạy `--rerun` ngày 2026-10-08.
2. Chưa có bằng chứng cho: `:app:lintRelease`, `:app:assembleRelease` (chặn bởi thiếu keystore/ENV), `npm run test:rules` (chưa cài Firebase emulator), Roborazzi verify, clean clone build, thiết bị thật.
3. Rủi ro P1 nặng nhất: `.env` có SMTP credential đã nằm trong HEAD (`9bccfdd`). Cần rotate ở nhà cung cấp; việc này là việc của chủ dự án.
4. Hai nhóm việc lớn chưa làm: (a) server-side (pairing atomic, xoá tài khoản bền, entitlement) cần Blaze và Cloud Functions; (b) ownership dữ liệu cục bộ theo tài khoản và guest adoption có lựa chọn.
5. `appplugin` tồn tại dưới dạng sibling `F:\Github_Project\appplugin` (2.4.2), còn catalog ghim 2.3.0. Không có bản sao trong repo nên clean clone chưa thể build.

---

## 1. Ma trận trạng thái

### 1.1 Steps 1–31 (`2026-10-07-release-readiness-plan.md`)

| Step | Ưu tiên | Nội dung | Trạng thái | Bằng chứng / khoảng trống |
|---|---|---|---|---|
| 1 | P0 | Khôi phục SDK `appplugin` | **CHẶN / MỘT PHẦN** | `settings.gradle.kts:49–54` substitute sang `./appplugin`, `../appplugin` hoặc `APPPLUGIN_DIR`. Hiện chỉ có sibling `F:\Github_Project\appplugin`; `./appplugin` không tồn tại. Dependency tree: `com.app.plugin:appplugin:2.3.0 -> project :appplugin` (`deps-release.txt` L14). Xem P1-2. |
| 2 | P0 | Cấu hình release (ký, versionCode, gate) | **XONG (code) / CHẶN (bằng chứng)** | Ký gán ở `app/build.gradle.kts:80`; minify/shrink `:78–79`; gate `:250–257`. `versionCode = 1` là số nguyên (`:36`), `versionName = "1.0.0"`. Keystore, STORE_PASSWORD, KEY_PASSWORD, ADMOB_*_RELEASE đều thiếu trên máy này. |
| 3 | P0 | Sửa danh tính catalog rỗng | **MỘT PHẦN** | `remoteId = "seed_$imageSeed"` (`GiftIdeasSeed.kt:47`); `newLocalRemoteId()` làm mặc định (ghi nhận I1). 20 seed (`GiftIdeasSeed.kt` L80–403), không phải 30–40. Chưa có test "cả 20 seed sống sót qua reseed" được xác nhận. |
| 4 | P0 | Ranh giới sở hữu dữ liệu cục bộ (`MIGRATION_13_14`) | **MỞ (đổi hướng)** | Không có `MIGRATION_13_14`; DB vẫn ở version 13 (`AppDatabase.kt:43`). Thay vào đó dùng `AccountDataVault` (file stash riêng theo scope, `AccountDataVault.kt:15–52`). Entity chưa có cột owner. Cần quyết định có giữ cách stash không (xem D4). |
| 5 | P0 | Áp dụng ownership khi đọc và đăng xuất | **MỘT PHẦN** | `AccountIsolationTest` 5 test PASS. `enterScope` chạy trước khi `_authState` đổi (`AuthRepository.kt` ghi ở I3). Còn: alarm và reminder gift chưa fence (E2, mục "OPEN"). |
| 6 | P0 | Adoption dữ liệu guest có lựa chọn | **XONG (2026-10-10, D4 = ask-once)** | `register(..., keepGuestData)` (`AuthRepository.kt`, nhánh adopt quanh dòng 618-627) + dialog trong `AuthScreen.kt` (`btn_guest_data_keep` / `btn_guest_data_fresh`). `GuestAdoptionTest` 4/4 xanh, đã kiểm bằng mutation. Chưa commit; chưa Codex review (hết credits). |
| 7 | P0 | Đối chiếu xác thực và khôi phục mật khẩu | **MỘT PHẦN** | `restoreSession` chỉ bật sync khi `FirebaseAuth.currentUser.uid == uid` (ghi nhận I5). Dialog quên mật khẩu chỉ dùng Firebase email (`AuthScreen.kt` L1163). Còn code local reset: `resetPasswordWithOtp` (`AuthRepository.kt:694–760`), `resetPasswordWithSecurityAnswer` (`:766–828`). |
| 8 | P0 | Tìm đôi qua Firestore, profile thành viên | **MỘT PHẦN** | Publish code có caller thật: `AuthRepository.kt:1297`. Invite listener: `SyncCoordinator.kt:70`, xử lý ở `:145`. Tìm kiếm vẫn là local Room: `OnlineCoupleRepository.kt:304`. |
| 9 | P0 | Chuyển đổi quan hệ nguyên tử (server) | **MỞ** | Không có `functions/`; không có `acceptCoupleInvite`. Client có bounded await trả lỗi retry (E1). Cần Blaze (D3). |
| 10 | P0 | Outbox và xung đột | **MỞ** | `SyncWorker.kt:51` batch 20; `:74–75` và `:80–81` bỏ bản ghi có `relationshipId` null (P1-3); `:60` xoá dòng outbox sau `pushOne`; `:103` một Mutex toàn tiến trình. |
| 11 | P0 | Quyền riêng tư media dưới tầng UI | **MỞ** | Gate privacy và gọi Cloudinary nằm trong UI: `MemoriesGridScreen.kt:1339–1344` gọi `CloudinaryStorageService.uploadToCloudinary` (`CloudinaryStorageService.kt:142`). Vi phạm AGENTS.md (P1-5). |
| 12 | P0 | Xoá tài khoản bền (server) | **MỞ** | Chỉ client: `RemoteAccountCleanup.kt:30` (`FirebaseRemoteAccountCleanup`), `AuthRepository.kt:1165` `deleteCurrentAccount`. Cloudinary chưa xoá (P1-4). |
| 13 | P0 | Đồng bộ consent và gate telemetry | **MỘT PHẦN** | `AppPrivacyCoordinator` là publisher duy nhất (I6); `AdPrivacyOptionsRow` `SettingsScreen.kt:681`; `PrivacyOptionsRowTest` 4 test. Chưa thấy gate telemetry theo mục đích trong appplugin (đã đọc tree, chỉ có Analytics, Crashlytics, Messaging đi kèm transitively). |
| 14 | P0 | Đóng race hiển thị quảng cáo và entitlement | **MỘT PHẦN** | `AdsManagerImpl.kt:354–355` interstitial và App Open đều `false`. `AdsEligibilityTest` 6 test. Kiểm tra lại ngay trước khi hiển thị đã có (I6). Chưa có device check cho cold-start VIP. |
| 15 | P0 | Khôi phục Billing đáng tin cậy | **MỘT PHẦN** | `resolveVipEntitlement` thuần, `VipEntitlementResolverTest` (8 test theo I6); query lỗi không thu hồi VIP. `BillingManagerTest` theo tên trong plan chưa có. Acknowledge: `BillingManager.kt:357, 377–404`. |
| 16 | P0 | Host paywall và disclosure | **XONG (code) / MỘT PHẦN (test)** | `resolveHostActivity` (`PlanOffer.kt:40`); `PaywallScreen.kt:118, 123`; `MainActivity.kt:619` truyền Activity thật; `PlanOfferTest`. Thiếu `PaywallScreenTest` theo tên trong plan. |
| 17 | P0 | Trang pháp lý và cấu hình store | **QUYẾT ĐỊNH (D15) / MỘT PHẦN** | `LegalLinks.kt` đọc `LEGAL_BASE_URL` (mặc định `https://inloveapp.com`, `build.gradle.kts:45`). Chưa kiểm tra URL công khai (UNVERIFIED). Data Safety chưa điền. `PLAY_RELEASE_CHECKLIST.md` đã có. |
| 18 | P0 | Cổng xác minh phát hành | **MỘT PHẦN / CHẶN** | Xem mục 5. `testDebugUnitTest` PASS. Lint, rules, assembleRelease, clean clone chưa chạy/chặn. `docs/RELEASE_QA.md` đã tạo (NOT STARTED). |
| 19 | P0 | Phát hành internal testing | **CHẶN (owner)** | Cần Play Console, App Signing, product, tester. Chưa có đầu vào trong repo. |
| 20 | P1 | Theme light có chủ đích, đồng bộ | **MỘT PHẦN** | Light ép cứng `Theme.kt:79–80`; `Shapes` có ở `Theme.kt:65–71`, gắn `:85`; `themes.xml` light (I2). Chưa có `Shape.kt` riêng, `colors.xml` cũ (purple/teal) chưa dọn, chưa có `ReleaseScreensScreenshotTest`. |
| 21 | P1 | Giải thích mode và trạng thái sync | **MỘT PHẦN** | `domain/AppMode.kt` (chưa tracked) + test trong `app/src/test/java/com/example/domain/`. Hiển thị ở Settings theo I2. Tên `AppModeUiStateTest` theo plan chưa có. |
| 22 | P1 | Hoàn thiện đường song ngữ | **MỞ** | TASK_CHECKLIST ghi ~1.500 câu tiếng Việt viết cứng; chưa đo lại. |
| 23 | P1 | Bộ retention tối thiểu | **MỘT PHẦN** | Boot receiver `AndroidManifest.xml:48–58`; exact alarm có guard `AlarmNotificationScheduler.kt:157`; fallback `:164`. Không có widget receiver (D13). `ReminderSchedulingTest` chưa có. Lịch ngày lễ: theo TASK_CHECKLIST, chưa xác minh lại. |
| 24 | P1 | Trình bày quà và chất lượng seed | **MỞ** | Chưa kiểm tra lại. Plan yêu cầu bỏ thay thế tiếng Anh theo Room ID (`GiftScreen.kt:1078`, ghi nhận trong plan). |
| 25 | P1 | Sở thích đối tác tối thiểu | **MỞ** | Không có entity/preferences mới trong repo. |
| 26 | P1 | Gợi ý cá nhân hoá cục bộ | **MỘT PHẦN** | `domain/GiftRanker.kt` (chưa tracked) + test (I1). Chưa có `GetPersonalizedGiftSuggestionsUseCase`. Nhãn "AI" trên template chưa đổi. |
| 27 | P1 | Chia sẻ một gợi ý | **MỞ** | Không có `sendGiftSuggestion`, không có `GiftSuggestionSyncAdapter`. |
| 28 | P1 | Một nguồn cấu hình vận hành | **TỪ CHỐI (có lý do)** | I2: appplugin `FirHelper` đã cung cấp Remote Config. Ghi tại `docs/release/SDK_INVENTORY.md`. Cần chủ dự án xác nhận vì plan ghi là NEW. |
| 29 | P1 | Chẩn đoán Firebase tối thiểu | **QUYẾT ĐỊNH (D9) / MỞ** | Dependency tree: `firebase-analytics:23.2.0`, `firebase-crashlytics:20.1.0`, `firebase-messaging:25.1.1`, `play-services-measurement`, `appsflyer af-android-sdk:6.16.2`, `com.google.ads.mediation:facebook:6.21.0.4` (`deps-release.txt` L390, 577, 659, 386, 884). Không có tham chiếu Analytics/FCM/Crashlytics trong `app/src`. Chưa có ai bật tắt theo consent. |
| 30 | P2 | Dark mode | **QUYẾT ĐỊNH (D8, mặc định hoãn)** | `DarkColorScheme` (`Theme.kt:44–63`) không dùng. |
| 31 | P2 | Giảm bảo trì | **MỞ** | `upgradeSubscription()` còn tại `InLoveViewModel.kt:979`, không có caller (mục P2). Keep rules R8 còn rộng. |

### 1.2 T1–T9 (`2026-10-06-release-hardening.md`)

| Task | Nội dung | Trạng thái | Ghi chú |
|---|---|---|---|
| T6 | Secrets/config và minSdk | **MỘT PHẦN** | `.gitignore` đã có `.env` và `.env.*` (`.gitignore:25–27`, chưa commit). `.env` vẫn là **staged deletion** (`D  .env`), HEAD vẫn có `.env`. minSdk giữ 24 (`app/build.gradle.kts:34`). `.env.example` nằm ở root, không phải `app/`. |
| T4 | Nâng cấp dependency và toolchain | **MỘT PHẦN** | Đang dùng AGP 9.2.1 (`libs.versions.toml` L2), Kotlin 2.2.10 (L12), Gradle 9.4.1, Compose BOM 2024.09.00 (L13, cũ). Build và test chạy được. Chưa có bảng nâng cấp. |
| T1 | Tích hợp appplugin 2.3.0 | **MỞ / CHẶN** | Xem Step 1 và P1-2. Catalog 2.3.0 (L3), sibling 2.4.2 (`SDK_INVENTORY.md` L5). |
| T2 | Rules: client không tự cấp VIP/role | **MỘT PHẦN** | `firestore.rules` L59–79: `userProfileFields()` và `hasOnly`; `invites` chỉ đổi `status` (L181–182). Chưa chạy emulator (P1-6). |
| T3 | Entitlement server và đồng bộ Play | **MỞ / QUYẾT ĐỊNH (D3)** | Không có thư mục `functions/`. VIP cache là SharedPreferences (`EntitlementRepository.kt:28`). |
| T5 | Thu hẹp R8 và kiểm tra release | **MỘT PHẦN** | `isMinifyEnabled` và `isShrinkResources` đã bật (`build.gradle.kts:78–79`). Keep rules còn rộng. Chưa có `-printmapping` (P2). Chưa có `lintRelease`/`assembleRelease`. |
| T7 | Theme M3, touch target, Roborazzi | **MỘT PHẦN** | Light ép cứng, Shapes có. Màu cứng `SettingsScreen.kt` L701–756. Touch target đã sửa theo TASK_CHECKLIST (chưa xác minh lại). Roborazzi verify chưa chạy. |
| T8 | Chính sách Play, Data Safety, quyền riêng tư | **MỞ** | `LegalLinks.kt` có. Data Safety chưa điền. AppsFlyer và Meta mediation nằm trong tree, cần khai báo. `docs/privacy/` không tồn tại. |
| T9 | Xác minh cuối | **MỘT PHẦN / CHẶN** | Unit test PASS. Các lệnh còn lại: xem mục 5. |

### 1.3 Master plan 2026-09-24 (Phần A–F)

| Task | Trạng thái | Bằng chứng / khoảng trống |
|---|---|---|
| A1 App Check trước plugin | **XONG (code) / thiếu runtime** | `InLoveApplication.kt:48–49` cài trước `super.onCreate()` L59. Test `AuthSecurityTest` theo tên không tìm thấy. |
| A2 Race VIP | **MỘT PHẦN** | `EntitlementRepositoryTest` 2 test PASS (`TEST-...EntitlementRepositoryTest.xml`). Chưa có test ở mức ViewModel. `InLoveViewModel.kt:386–422` có `hasSyncedOnce`. |
| A3 Product ID thống nhất | **XONG** | `InLoveApplication.kt:79–81` dùng `VipProductIds`; comment ở L64–67. Giữ hai BillingClient theo quyết định trong plan. |
| A4 Đổi mật khẩu thật | **MỘT PHẦN** | `changePassword` cập nhật Firebase (`AuthRepository.kt:871`). OTP và security-answer vẫn còn code local (`:694–828`). `RealSmtpEmailSender` đã bỏ theo SECRETS L8. |
| A5 Cloudinary | **MỞ** | `CloudinaryMediaService.kt` L26–42 vẫn trùng, `CloudinaryStorageService.kt:43–44` vẫn delegate. |
| A6 Room migration v12 | **MỘT PHẦN** | `exportSchema = true` (`AppDatabase.kt:44`); `MIGRATION_12_13` (`Migrations.kt:16`, `AppDatabase.kt:60`). `Migration12To13Test` `@Ignore` (L25), thiếu `12.json`. |
| A7 Release signing + R8 | **MỘT PHẦN** | Ký và R8 bật; gate `:250–257`. Chưa có `-assumenosideeffects` cho Log, keep rules còn rộng. |
| A8 Mật khẩu seed | **CODE XONG / QUYẾT ĐỊNH** | Xem mục 2.6. Năm `password` đều `requireEnv(...)`, không có literal. Lịch sử git vẫn có literal ở commit cũ; rotate là việc của owner. |
| A9 Rules nhạy cảm | **MỘT PHẦN** | Xem T2. `users` có `hasOnly` (L72–76). Rules tests chưa chạy. |
| A10 Telemetry trước consent | **CHƯA XÁC MINH** | Chưa đọc `LogEventServer` trong appplugin, vì sibling không nằm trong repo. |
| A11 brainRolloutFraction | **XONG (code) / QUYẾT ĐỊNH** | `brainEnabled = false` (`InLoveApplication.kt:75`), `brainRolloutFraction = 0.05` (`:76`). Fraction không có tác dụng khi brain tắt. Chủ dự án xác nhận. |
| B1 Pairing 2 máy | **MỘT PHẦN** | Invite listener có; `acceptSetLoveInvite` có Firestore (E1); tìm kiếm local (`OnlineCoupleRepository.kt:304`). Chưa có thử nghiệm 2 máy. |
| B2 Connectivity | **MỘT PHẦN** | `NetworkMonitor` đã có và được dùng (TASK_CHECKLIST). Chưa có `data/network/ConnectivityObserver.kt`. |
| B3 Sync memories 2 chiều | **CHƯA XÁC MINH** | Chưa đọc `Firebase3NFService`; plan ghi là chết. |
| B4 Dialog và dead code | **MỞ** | `upgradeSubscription` còn (`InLoveViewModel.kt:979`). Dialog alarm đã nối (TASK_CHECKLIST). |
| B5 Dark theme | **QUYẾT ĐỊNH (D8)** | Light ép cứng. |
| B6 Wallpaper | **MỘT PHẦN** | TASK_CHECKLIST: lưu SharedPreferences. Plan yêu cầu Room; chưa xác minh. |
| B7 Xoá tài khoản | **XONG (client) / MỞ (Cloudinary, server)** | Thứ tự Auth trước ghi trong plan; `AccountDeletionOrderTest` 3 test PASS. Xoá media chưa làm. |
| C1 Shapes | **MỘT PHẦN** | `Theme.kt:65–71`, gắn ở `:85`. Chưa có `Shape.kt`. |
| C2 Token màu thay hex | **MỞ** | `SettingsScreen.kt` L701–756 còn hex. |
| C3 Tách file lớn | **MỞ** | Chưa đo lại kích thước. |
| C4 Tách ViewModel | **MỞ** | `InLoveViewModel.kt` vẫn một file. |
| C5 Localization | **MỞ** | Chưa đo lại. |
| C6 MainActivity remember | **CHƯA XÁC MINH** | |
| D1 Ngày lễ VN | **CHƯA XÁC MINH** | TASK_CHECKLIST ghi xong; chưa đọc lại file. |
| D2 Ngày lễ phương Tây | **CHƯA XÁC MINH** | Tương tự D1. |
| D3 Seed quà 30–40 | **MỞ** | 20 seed (`GiftIdeasSeed.kt`). |
| D4 Nối lịch lễ | **CHƯA XÁC MINH** | |
| E1 PRODUCT_RESEARCH | **CHƯA XÁC MINH** | Không có trong danh sách tài liệu đã đọc. |
| F (Data Safety và các mục khác) | **MỘT PHẦN** | F3 UMP privacy options: **XONG** (`SettingsScreen.kt:681`, `PrivacyOptionsRowTest`). F6 `PurchaseRouter` không được gọi: **XONG** (đã kiểm tra). Data Safety, domain, deletion web, dọn AI claim, nâng cấp migration: **MỞ**. |

---

## 2. Bằng chứng theo file:line (điểm cần để kiểm tra lại)

### 2.1 Build và release
- `app/build.gradle.kts:34` minSdk 24; `:36` versionCode 1; `:37` versionName "1.0.0"; `:45` LEGAL_BASE_URL.
- `:57–65` signingConfigs `release` đọc `KEYSTORE_PATH` (mặc định `${rootDir}/my-upload-key.jks`), `STORE_PASSWORD`, `KEY_PASSWORD`.
- `:78–80` release: minify, shrink, signingConfig release.
- `:82–90` AdMob release lấy từ property; mặc định rỗng.
- `:250–257` gate chặn release tasks khi thiếu ADMOB_APP_ID_RELEASE, ADMOB_BANNER_ID_RELEASE, STORE_PASSWORD, KEY_PASSWORD hoặc keystore.
- `settings.gradle.kts:48–54` include `:app` và substitute appplugin.
- `gradle/libs.versions.toml`: `:2` AGP 9.2.1; `:3` appplugin 2.3.0; `:12` Kotlin 2.2.10; `:13` Compose BOM 2024.09.00.

### 2.2 Đầu vào release trên máy này (chỉ tên và sự có mặt)
- `my-upload-key.jks`: không có. `app\debug.keystore`: không có. `~/.gradle/gradle.properties`: không có.
- `gradle.properties` của dự án: không có KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD, ADMOB_*_RELEASE.
- Biến môi trường không đặt: KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD, APPPLUGIN_DIR, ADMOB_APP_ID_RELEASE, ADMOB_BANNER_ID_RELEASE.
- `app/google-services.json`: có (không in nội dung). `firebase.json`: có. `.env`: có trong working tree, không đọc giá trị.
- Công cụ: node v26.10.0, java có trên PATH. `scripts/node_modules`: chưa có. `scripts/package-lock.json`: chưa có. `firebase/functions`: chưa có.

### 2.3 `.env` và `.gitignore`
- `.gitignore:24–27`: `.env`, `.env.*`, `!.env.example` (đã thêm, chưa commit). `git check-ignore -v .env` → `.gitignore:25`. `git check-ignore .env.example` → exit 1 (không bị ignore, đúng).
- `git log -- .env` → `9bccfdd ip`. HEAD có `.env`.
- Khoá có trong HEAD `.env` (chỉ tên, không có giá trị): `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_UPLOAD_PRESET`, `CLOUDINARY_FOLDER`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_SENDER_EMAIL`, `SMTP_SENDER_PASSWORD`, `SMTP_SENDER_NAME`.
- Index: `D  .env` (đã `git rm --cached`, chưa commit).
- `git ls-files`: `.env.example` và `app/google-services.json` được track. `app/.env.example` không tồn tại.

### 2.4 Dependency release runtime (`scratchpad\deps-release.txt`, Gradle exit 0)
- `com.app.plugin:appplugin:2.3.0 -> project :appplugin` (L14).
- `firebase-analytics:23.2.0` (L390, 403), `play-services-measurement:23.2.0` (L404).
- `firebase-crashlytics:20.1.0` (L577), `firebase-messaging:25.1.1` (L659), `firebase-firestore:26.5.0` (L686), `firebase-auth:24.2.0` (L1459), `firebase-ai:17.15.0` (L1306).
- `com.appsflyer:af-android-sdk:6.16.2` (L386).
- `com.google.ads.mediation:facebook:6.21.0.4` (L884).
- `com.android.billingclient:billing:9.0.0` (L745), `billing-ktx:8.1.0` (L1578).
- `com.google.android.gms:play-services-ads:25.4.0` (L764), `user-messaging-platform:4.0.0` (L866).
- Không tìm thấy `databuckets` trong release runtime classpath. Endpoint databuckets có thể nằm trong code của appplugin; cần đọc sibling để xác nhận.

### 2.5 Mã nguồn chính
- `InLoveApplication.kt:26–32` `isRunningInTest`; `:39` return sớm khi test; `:48–49` App Check trước; `:55–56` locator và sync; `:68–82` MonetizationSdk; `:75–76` brain tắt, fraction 0.05.
- `Theme.kt:44–63` DarkColorScheme không dùng; `:65–71` AppShapes; `:79–80` ép light.
- `AccountDataVault.kt:15–30` header và ponytail; `:44–49` adoptCurrentInto; `:52` switchTo.
- `AuthRepository.kt:618` adopt khi register; `:871` changePassword; `:1165` deleteCurrentAccount; `:1297` publishCoupleCode caller.
- `OnlineCoupleRepository.kt:304` tìm kiếm local; `:326` sendSetLoveInvite; `:398` acceptSetLoveInvite.
- `SyncWorker.kt:51` batch 20; `:60` xoá outbox; `:74–75` và `:80–81` drop null relationshipId; `:103` Mutex.
- `SyncCoordinator.kt:60` return nếu firestore null; `:70` snapshot invites; `:145` handleInviteSnapshot.
- `AdsManagerImpl.kt:354–355` cờ interstitial và App Open đều false.
- `EntitlementRepository.kt:28` cache SharedPreferences.
- `BillingManager.kt:357` acknowledge retry; `:377–404` helper; `:415` offer null.
- `PaywallScreen.kt:118, 123` host activity; `PlanOffer.kt:40` resolveHostActivity.
- `MemoriesGridScreen.kt:1339–1344` gọi upload từ UI.
- `GiftIdeasSeed.kt:47` `seed_$imageSeed`; `:80–403` 20 seed.
- `InLoveViewModel.kt:979` upgradeSubscription (không caller); `:1783–1785` deleteAccountAndData → authRepo.deleteCurrentAccount; `:1813–1815` onCleared dừng coordinator.
- `SettingsScreen.kt:681` AdPrivacyOptionsRow; `:701–756` màu cứng; `:1590` deleteAccountAndData.
- `RemoteAccountCleanup.kt:25` Noop; `:30` Firebase client-only.
- `config/LegalLinks.kt`: privacy/terms/delete-account dựa trên `LEGAL_BASE_URL`.
- `AndroidManifest.xml:8` SCHEDULE_EXACT_ALARM; `:11` USE_EXACT_ALARM bị comment; `:13` RECEIVE_BOOT_COMPLETED; `:16` BILLING; `:48–58` BootCompletedReceiver; `:60–70` ReminderAlarmReceiver `exported=false`; `:72–78` WorkManager initializer. Không có widget receiver.
- `AlarmNotificationScheduler.kt:157` guard canScheduleExactAlarms; `:158` exact; `:164` fallback không exact; `:171` exact (chưa rõ guard); `:347` non-exact.
- `AppDatabase.kt:43` version 13; `:44` exportSchema; `:60` addMigrations. `Migrations.kt:16` MIGRATION_12_13.
- `firestore.rules`: `:59–79` user fields; `:85–93` coupleCodes; `:98–128` relationships; `:145–163` memories/anniversaries subcollections; `:168–192` invites; `:197–232` memories/anniversaries; `:237–260` catalog read-only; `:265–267` test_fixtures khoá.

### 2.6 Seed Firestore (`scripts/seed_firestore.js`)
- `:47–` `requireEnv(name)` fail-fast khi thiếu ENV, không có giá trị mặc định.
- `:67, :77, :87, :97, :107`: `password: requireEnv(...)`. Đã phân loại: **env-based**. Không có literal (A8 đóng ở mức code).
- ENV dùng: `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY`, `GOOGLE_APPLICATION_CREDENTIALS` (`:37–39`), và các biến `requireEnv`.

### 2.7 Test (trong `app/build/test-results/testDebugUnitTest`)
- `EntitlementRepositoryTest`: 2 test PASS (`isVipUser_updatesAndPersists_onlyAfterHasSyncedOnceBecomesTrue`, `isVipUser_seedsFromPersistedCache_beforeAnyBillingResponseThisSession`).
- `PinAndFacebookAuthTest`: 11 test (`AuthRepository` PIN, lockout, Facebook). `tearDown` L67–71 có `cancelAndJoin` trước `db.close()`.
- `Migration12To13Test`: `@Ignore` tại `app/src/test/java/com/example/data/db/Migration12To13Test.kt:25`.

---

## 3. Quyết định cho chủ dự án (kèm khuyến nghị)

| ID | Vấn đề | Khuyến nghị | Ghi chú |
|---|---|---|---|
| **D1** | `.env` đã có SMTP credential trong HEAD (`9bccfdd`) | **Rotate `SMTP_SENDER_PASSWORD` ngay ở nhà cung cấp.** Commit `.gitignore` và `git rm --cached .env` khi chủ dự án đồng ý. Không viết lại lịch sử trừ khi repo đã hoặc sẽ public. | Việc rotate là việc của owner; agent không làm. |
| **D2** | `appplugin`: catalog 2.3.0, sibling 2.4.2 | Ghim **2.4.2** nếu CHANGELOG xác nhận tương thích AGP 9.2.1; đưa nguồn vào repo (submodule ghim commit hoặc Maven). Chứng minh bằng clean clone. | `libs.versions.toml:3`, `settings.gradle.kts:49–54`. |
| **D3** | Server (Blaze) cho pairing, xoá tài khoản, entitlement | Bật Blaze; duyệt một callable xoá tài khoản và dọn media trước. Secret Cloudinary chỉ ở server. T3 đầy đủ hoãn nếu lợi ích VIP chỉ là bỏ quảng cáo; ghi rủi ro. | Không có `functions/`. |
| **D4** | Guest adoption | Hỏi một lần khi register nếu có dữ liệu guest: "giữ cho tài khoản mới" / "bắt đầu mới". Dữ liệu mơ hồ vào vùng giữ, không gán ngầm. | **Chốt 2026-10-10: ask-once** (owner nêu cả hai, giao toàn quyền; chọn mặc định của plan). Đã cài đặt, xem step 6. |
| **D5** | Khôi phục mật khẩu | Chỉ dùng Firebase reset email. Gỡ OTP và security-answer khỏi đường production; giữ seam cho test. | Dialog đã làm theo (I5). |
| **D6** | Xoá tài khoản | Job bền, idempotent, retry, có audit. Xoá nội dung và media của người yêu cầu, gỡ liên kết couple, giữ dữ liệu độc lập của đối tác, khai báo lưu giữ. Hủy subscription tách riêng, làm trên Play. | Cần D3. |
| **D7** | Quảng cáo | Chỉ banner v1; interstitial và App Open tắt. | `AdsManagerImpl.kt:354–355`. |
| **D8** | Theme | Light thương hiệu, dynamic color tắt, dark hoãn. Giữ `DarkColorScheme` và đánh dấu deferred. | `Theme.kt:79–80`. |
| **D9** | Chẩn đoán Firebase | Crashlytics có consent, upload mapping. Không bật Analytics/FCM nếu chưa có yêu cầu. Xác nhận transitive AppsFlyer và Meta mediation trong Data Safety. | Deps L386, 884. |
| **D10** | minSdk | Giữ 24 (`build.gradle.kts:34`, AGENTS.md). QA thêm API 24 và 25. Kiểm tra manifest merge bằng `processDebugManifest`. | Plan cũ ghi 26 là sai. |
| **D11** | Phiên bản | `versionCode` số nguyên: lần đầu 1, tăng mỗi lần upload; `versionName` "1.0.0". | Đã đúng trong code. |
| **D12** | Nghiên cứu consent | Tắt trong v1. Không có đường thu thập đến khi có thiết kế riêng. | T8. |
| **D13** | Phạm vi retention | Milestones/day counter, reminders/calendar, ảnh kỷ niệm tin cậy. Hoãn widget (không có receiver). | Step 23. |
| **D14** | Quyền riêng tư media | "Riêng tư" chỉ ở máy. "Chung đôi" chỉ upload sau khi đã ghép đôi và được cấp quyền. Chuyển gate và điều phối Cloudinary khỏi UI sang use case. | AGENTS.md. |
| **D15** | Domain pháp lý | Chọn một domain sở hữu được; xác nhận `/privacy`, `/terms`, `/delete-account` live. Mặc định hiện tại `https://inloveapp.com`. | Plan cũ nhắc `inlove.app` và `inlove-app.web.app`. |
| **D16** | Cấu hình console | Cloudinary preset unsigned giới hạn định dạng, kích thước, thư mục. Google API key giới hạn package và SHA-1. Không có secret ở client. | SECRETS §1–2. |
| **D17** | JDK cho Gradle daemon | Chỉ ghim JDK 17 nếu OOM lặp lại. Log `hs_err` đã git-ignore. | |
| **D18** | Supabase | Dự án không dùng Supabase. Chỉ cấp quyền nếu muốn. | Không cần cho phát hành này. |

---

## 4. Thứ tự thực hiện

Các bước được sắp theo phụ thuộc. Mỗi bước có cổng (gate) để đi tiếp.

**Pha 0 — Việc của owner, làm ngay (không chờ code)**
1. D1: rotate SMTP credential. Xác nhận đã làm.
2. Quyết định D2 (phiên bản appplugin) và cung cấp nguồn appplugin.
3. Cung cấp đầu vào release: keystore, STORE_PASSWORD, KEY_PASSWORD, ADMOB_APP_ID_RELEASE, ADMOB_BANNER_ID_RELEASE. Gate: `assembleRelease` đi qua gate và tạo ra AAB.

**Pha 1 — Repo, không cần server**
4. Commit `.gitignore` và `git rm --cached .env` khi owner đồng ý (D1). Gate: `git ls-files -- .env` trống; `git check-ignore -v .env` có kết quả.
5. Ghim appplugin trong catalog và settings theo D2; chứng minh bằng clean clone (`git clone` vào thư mục rỗng, `APPPLUGIN_DIR` trỏ đúng). Gate: `:app:compileDebugKotlin` pass trên clone.
6. Step 3: viết test "20 seed sống sót qua reseed" trong `PresetDedupeTest` hoặc `CloudEnrichmentDataTest`. Gate: pass.
7. Step 10: `SyncWorker` không drop bản ghi có `relationshipId` null. Giữ outbox row hoặc đưa vào dead-letter. Test: 21+ write, retry. Gate: pass.
8. Step 11: chuyển gate privacy và upload Cloudinary từ `MemoriesGridScreen` sang use case. Test `MemoryPrivacyTest`. Gate: pass và UI không gọi Cloudinary trực tiếp.
9. Step 7: gỡ OTP và security-answer khỏi production (giữ seam test). Gate: `AuthRepositoryTest` pass.
10. Step 31 và B4: xoá `upgradeSubscription` sau khi xác nhận không caller. Gate: `:app:compileDebugKotlin` pass.
11. Step 20 và T7: xoá `colors.xml` purple/teal sau khi kiểm tra tham chiếu; chuyển `SettingsScreen` màu cứng sang token (không đổi hex). Gate: `testDebugUnitTest` pass.
12. A5: xoá `CloudinaryMediaService.kt` trùng lặp sau khi xác nhận không tham chiếu. Gate: compile pass.
13. Step 4–6: quyết định D4; nếu chọn "hỏi một lần", thêm UI và `GuestAdoptionTest`. Nếu chọn giữ auto-adopt, ghi rõ trong plan và đóng.
14. A6: xuất `12.json` từ commit có version 12 rồi bỏ `@Ignore` trên `Migration12To13Test`. Gate: test pass, không mất dữ liệu.
15. Step 24–27 (quà, sở thích, gợi ý, chia sẻ): làm sau khi có D1–D4 và quyết định về phạm vi. Mỗi mục một commit.

**Pha 2 — Server (cần D3 và Blaze)**
16. Bật Blaze; tạo `functions/` (Cloud Functions). Cài Node và Firebase CLI cho `scripts/`.
17. Step 9: `acceptCoupleInvite` là callable transaction, idempotent, có App Check. Gate: `npm run test:rules` pass và có test emulator cho double accept.
18. Step 12 (D6): `requestAccountDeletion` và `processAccountDeletion` retry, audit; xoá media Cloudinary qua server. Gate: emulator test pass; thử trên tài khoản dùng thử.
19. T3 (D3): entitlement verify trên server nếu owner chọn; nếu không, ghi rủi ro vào `docs/RELEASE_QA.md`.
20. Step 8: tìm đôi qua `coupleCodes` thay vì local search. Gate: `pairing.rules.test.js` pass.

**Pha 3 — Cổng phát hành**
21. Step 13–17: consent, ads, billing, paywall, Data Safety, domain (D15). Gate: `PrivacyOptionsRowTest`, `AdsEligibilityTest`, `VipEntitlementResolverTest`, `PlanOfferTest` pass; `PaywallScreenTest` tạo mới; URL công khai kiểm tra trên trình duyệt di động.
22. Step 18 (`docs/RELEASE_QA.md`): chạy theo thứ tự T9 (dưới đây) và QA thủ công.
23. Step 19: upload AAB lên internal testing (owner).

**Lệnh cổng T9** (từ `2026-10-06-release-hardening.md` L635–641):
```
.\gradlew.bat :app:testDebugUnitTest
npm --prefix scripts run test:rules
npm --prefix firebase/functions test        # N/A cho đến khi T3 tạo thư mục
.\gradlew.bat :app:lintRelease
.\gradlew.bat :app:assembleRelease
git status --short
git diff --check
```

---

## 5. Bằng chứng và khoảng trống bằng chứng

| Mục | Trạng thái | Lệnh / lý do |
|---|---|---|
| `:app:testDebugUnitTest` | **PASS** | 30 XML, 112 test, 0 fail, 0 error, 1 skipped (Migration12To13Test). `GRADLE_EXIT=0`. `--rerun` ngày 2026-10-08. Log: `scratchpad\gradle-rerun-20261008.log`. |
| `npm --prefix scripts run test:rules` | **CHƯA CHẠY** | Cần `npm install` trong `scripts/` (chưa có `node_modules`), Firebase CLI và emulator Firestore. Lệnh: `firebase emulators:exec --only firestore "mocha rules-tests/**/*.test.js --timeout 20000"`. |
| `npm --prefix firebase/functions test` | **N/A** | Không có thư mục (T3 chưa làm). |
| `:app:lintRelease` | **CHƯA CHẠY** | Chạy được khi có đủ đầu vào release? Lint không cần keystore; có thể chạy ngay. Cần chạy. |
| `:app:assembleRelease` | **CHẶN** | Gate `build.gradle.kts:250–257` chặn đúng: thiếu keystore, STORE_PASSWORD, KEY_PASSWORD, ADMOB_*_RELEASE. Lỗi do đầu vào, không do code. |
| `git status --short`, `git diff --check` | **CHƯA CHẠY** trong bản này | Đã chạy `git status --short` (danh sách có nhiều thay đổi chưa commit). `git diff --check` chưa chạy. |
| Dependency release | **CÓ BẰNG CHỨNG** | `deps-release.txt`, Gradle exit 0 (mục 2.4). |
| appplugin tests | **CHƯA CHẠY** | Sibling không nằm trong repo, cần bước 5. |
| Roborazzi verify | **CHƯA CHẠY** | Cần lệnh Roborazzi cho các screenshot đã có. |
| Clean clone build | **ĐÃ CHẠY 2026-10-10** | Tag v2.4.2 clone mới, `APPPLUGIN_DIR` + `ANDROID_HOME`, `:app:compileDebugKotlin` BUILD SUCCESSFUL. |
| Device QA (Step 18) | **CHƯA BẮT ĐẦU** | Xem `docs/RELEASE_QA.md`. |
| Migration 12 → 13 | **CHƯA CHỨNG MINH** | Test `@Ignore`; thiếu `12.json`. |
| Pairing 2 máy | **CHƯA CHỨNG MINH** | Cần thiết bị. |
| Link pháp lý | **CHƯA XÁC MINH** | Không kiểm tra trên trình duyệt. |
| Play Console | **OWNER** | |
| ViewModel test cho VIP (A2) | **THIẾU** | Chỉ có test repo. |

---

## 6. `ponytail:` — các giới hạn đã biết

Liệt kê từ `grep -rn "ponytail:" app/src` (ngày 2026-10-08):

| Vị trí | Giới hạn | Điều kiện nâng cấp |
|---|---|---|
| `InLoveApplication.kt:38` | Test bỏ qua Firebase và AppServiceLocator | Khi có test Firebase thật, dùng Application riêng cho test. |
| `SyncWorker.kt:102` | Một lock cho cả tiến trình; hai lần chạy có thể đẩy bản UPSERT cũ sau tombstone | Khi có nhiều tiến trình, đánh dấu dòng đang xử lý trong DB. |
| `UnlockAttemptStore.kt:10` | Dùng đồng hồ tường, có guard lùi giờ, không có monotonic clock | Khi cần khoá chặt hơn, dùng `elapsedRealtime` đã lưu. |
| `AccountDataVault.kt:29` | File stash không mã hoá | Khi có rủi ro máy root, dùng SQLCipher. |
| `AuthRepository.kt:1294` | Một lần get mỗi lần đăng nhập; không retry khi offline | Khi có nhu cầu đồng bộ offline. |
| `SyncWorker.kt:51` (batch 20, không đọc hết mục 21+) | Mỗi lần chạy tối đa 20 | Lặp đến khi rỗng hoặc chia theo thời gian. |
| `EntitlementRepository.kt:28` | Cache VIP trong SharedPreferences, không hết hạn | Khi có T3: `cacheValidUntil` từ server. |
| `AdsManagerImpl.kt:354–355` | Interstitial và App Open tắt | Chỉ bật theo hướng dẫn rollout của SDK. |
| `InLoveApplication.kt:75–76` | Brain tắt; fraction 0.05 không có tác dụng | Rollout 5% → 20% → 100%, mỗi bước ít nhất 2 tuần. |
| `OnlineCoupleRepository.kt:304` | Tìm kiếm chỉ local | Khi có Firestore query theo `coupleCodes`. |
| `RemoteAccountCleanup.kt:30` | Xoá tài khoản chỉ ở client | D6 (server callable). |
| `build.gradle.kts:250–257` | Gate chỉ kiểm tra banner ID | Thêm kiểm tra interstitial và AOA khi bật cờ. |
| `EmailQueueService.kt:83` | Scope singleton không bao giờ huỷ | Chấp nhận trong vòng đời tiến trình. |

---

## 7. Khẳng định cũ cần sửa (stale-claim corrections)

| # | Khẳng định cũ (nguồn) | Thực tế (bằng chứng) |
|---|---|---|
| 1 | minSdk 26 (master L8; 10-06 L14; 10-07 L7) | **24** (`app/build.gradle.kts:34`; AGENTS.md) |
| 2 | appplugin "vắng mặt" (10-07 L8) | Có qua composite build; sibling 2.4.2; catalog 2.3.0 (`settings.gradle.kts:49–54`) |
| 3 | AGP 9.1.1 (10-06 L16) | **9.2.1** (`libs.versions.toml:2`) |
| 4 | `.gitignore` đã ignore `.env` (10-06 L20; SECRETS L9) | Sai trước phiên này. Hiện đã thêm (`.gitignore:25–27`), chưa commit |
| 5 | `app/.env.example` (10-06 T6) | Đường dẫn đúng là `.env.example` ở root |
| 6 | `versionCode` không hợp lệ (10-07 Step 2) | Số nguyên `1` (`build.gradle.kts:36`) |
| 7 | 18 gợi ý quà (TASK_CHECKLIST L71–72) | **20** (`GiftIdeasSeed.kt:80–403`) |
| 8 | `publishCoupleCode` không có caller | Có caller tại `AuthRepository.kt:1297` |
| 9 | SyncWorker "bị kẹt" | **Bị bỏ qua** (drop) âm thầm: `SyncWorker.kt:74–75`, `:80–81`, xoá outbox ở `:60` |
| 10 | `EmailQueueServiceTest` dùng database | Không có DB; tearDown gọi `clearAll()` |
| 11 | `acceptCoupleInvite` | Tên thật: `acceptSetLoveInvite` (`OnlineCoupleRepository.kt:398`) |
| 12 | `sendSetLoveInvite` ở L286 | **L326** |
| 13 | BillingManager thiếu acknowledge | Có (`BillingManager.kt:357`, `:377–404`) |
| 14 | A11 `brainRolloutFraction = 1.0` | **0.05**, brain tắt (`InLoveApplication.kt:75–76`) |
| 15 | B1 "không đọc Firestore ở đâu" | Có invite listener (`SyncCoordinator.kt:70`, `:145`). Tìm kiếm vẫn local |
| 16 | A1 chưa tick | Code đã xong (`InLoveApplication.kt:48–49`). Thiếu bằng chứng runtime |
| 17 | Device matrix API 26/33/35/36 (10-07 Step 18) | Thêm API 24 và 25 |
| 18 | `SDK_INVENTORY.md` L5 "appplugin 2.4.2" | Khác catalog 2.3.0. Đối chiếu sau D2 |
| 19 | PLAY_RELEASE_CHECKLIST L3 "1 test @Ignore" | Đúng nhưng thiếu: 30 XML, 112 test, 0 fail, 0 error, 1 skipped |
| 20 | 10-06 T1 nói AdsManagerImpl preload interstitial và App Open | Cờ đều false (L354–355). Cần xác nhận không có lời gọi preload |
| 21 | A1 nêu `AuthSecurityTest` | Không tìm thấy trong các test đã đếm |
| 22 | A8: seed có literal password | **Không còn**: năm chỗ đều `requireEnv(...)`. Lịch sử git vẫn có |
| 23 | `.env` đã được `git rm --cached` (I4) | Index chỉ có `D  .env` chưa commit; HEAD vẫn có |
| 24 | Gift catalog "đã sửa" theo I1 | Có `seed_$imageSeed`. Test cho "20 seed sống sót" chưa xác nhận |

---

## 8. Vấn đề P1 (cần xử lý trước phát hành)

| ID | Vấn đề | Bằng chứng | Hành động |
|---|---|---|---|
| **P1-1** | `.env` có SMTP credential trong HEAD | `git log -- .env` → `9bccfdd`; khoá: `SMTP_SENDER_PASSWORD` và các khoá SMTP khác | D1. Rotate (owner). Commit xoá index khi đồng ý. |
| **P1-2** | appplugin không tái tạo được: catalog 2.3.0, sibling 2.4.2; không có bản trong repo | `libs.versions.toml:3`; `settings.gradle.kts:49–54`; `SDK_INVENTORY.md:5`; `./appplugin` không tồn tại; `.gitignore` hiện không có rule `appplugin/` (lần kiểm tra trước ghi `.gitignore:35`, nay không còn) | D2; clean clone. Rủi ro lồng repo hiện chưa xảy ra vì thư mục không có trong repo. |
| **P1-3** | SyncWorker bỏ bản ghi có `relationshipId` null | `SyncWorker.kt:74–75`, `:80–81`, `:60` | Step 10. Test trước khi sửa. |
| **P1-4** | Xoá tài khoản chỉ ở client; Cloudinary không xoá | `RemoteAccountCleanup.kt:30`; `AuthRepository.kt:1165`; `SettingsScreen.kt:1590` | D6, D3. Play yêu cầu cả đường xoá trong app và link web. |
| **P1-5** | Gate privacy và upload Cloudinary nằm trong UI | `MemoriesGridScreen.kt:1339–1344`; `CloudinaryStorageService.kt:142` | Step 11, D14. Vi phạm AGENTS.md. |
| **P1-6** | Test rules emulator đã viết, chưa chạy | `scripts/package.json:10`; `scripts/rules-tests/*.test.js` | Cài deps, chạy `test:rules`. |
| **P1-7** | Không có entitlement phía server; VIP cache SharedPreferences | `EntitlementRepository.kt:28`; không có `functions/` | D3; T3. Lợi ích VIP duy nhất thấy được là bỏ quảng cáo. |
| **P1-8** | Ghép đôi 2 máy chưa chứng minh | Tìm kiếm local `OnlineCoupleRepository.kt:304`; listener `SyncCoordinator.kt:70` | Step 8, Step 9, thiết bị thật. |
| **P1-9** | Đầu vào release thiếu; `assembleRelease` bị gate chặn | `build.gradle.kts:250–257`; mục 2.2 | Pha 0 bước 3. |
| **P1-10** | Bằng chứng Data Safety chưa đủ | Deps có AppsFlyer 6.16.2 và Meta mediation 6.21.0.4; Crashlytics, Analytics, FCM đi kèm; `databuckets` chưa thấy trong tree | D9; đọc sibling để xác nhận databuckets; điền Data Safety. |

P2 (ngắn): `Migration12To13Test` `@Ignore`, thiếu `12.json`; chưa có `MIGRATION_13_14`; R8 keep rules rộng, chưa có `-printmapping`; `SettingsScreen` màu cứng; Cloudinary trùng lặp; `upgradeSubscription` không caller; reset local `resetPasswordWithOtp`/`resetPasswordWithSecurityAnswer`; adopt im lặng khi register; `scripts/seed_firestore.js` đã env-based (đóng, lịch sử git vẫn có); fixture loader ở login (`AuthRepository.kt:433`) cần xác nhận chỉ chạy trong test; 20 seed so với 30–40; `EmailQueueService.kt:83`, `EntitlementRepositoryTest` L31, L45 scope rò rỉ tiềm ẩn; alarm sau khi đổi scope (E2); `AlarmNotificationScheduler.kt:171` chưa rõ guard; dark mode bị ép light.

---

## 9. Ghi chú môi trường

- Repo: `F:\Github_Project\DemNgayYeuAndroid`, nhánh `main`. Working tree có nhiều thay đổi chưa commit, gồm cả file chưa tracked; không có gì đã được commit trong phiên này.
- `appplugin/` không tồn tại trong repo. Không `git add -A` từ root khi có sibling.
- Lệnh Gradle chạy được với JDK có sẵn; daemon báo cảnh báo về native access (không ảnh hưởng kết quả).
- Không đọc, không in giá trị `.env`, `google-services.json` hay mật khẩu seed.
