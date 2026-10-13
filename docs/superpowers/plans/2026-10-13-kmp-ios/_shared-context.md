# Ngữ cảnh chung cho kế hoạch KMP + iOS (đọc file này trước)

Ngày lập: 2026-10-13. Mục tiêu: đưa InLove (hiện Android-only) sang **Kotlin/Compose Multiplatform**, ra bản **iOS đầy đủ tính năng**, animation mượt, widget (WidgetKit) và các phần native chuẩn. Người dùng đã có máy Mac. Chưa viết code nào; đây là giai đoạn lập kế hoạch.

Ngôn ngữ: văn xuôi tiếng Việt; tên file, class, API, lệnh giữ nguyên tiếng Anh.

## 1. Sự thật đã xác minh (VERIFIED, đọc từ repo)

- Repo `F:\Github_Project\DemNgayYeuAndroid`, nhánh `main`, không có `.github/` (chưa có CI).
- `:app` = Android application, namespace `com.example`, applicationId `com.aistudio.inlove.kmrv`, minSdk 24, targetSdk/compileSdk 36, 85 file Kotlin / 36.5k dòng, package gốc `com.example`.
- Build: AGP 9.4.1, Kotlin 2.2.10, KSP 2.3.6, Compose BOM 2024.09.00 (Material 3), Room 2.7.0 (KSP), WorkManager 2.10.1, Coil 2.7.0 (Android-only), Moshi 1.15.2, OkHttp 4.10.0, Firebase BoM 34.17.0 (ai, firestore, auth, functions, crashlytics, appcheck playintegrity/recaptcha/debug), androidx.credentials 1.5.0 + googleid 1.1.1, play-services-ads 25.4.0, UMP 3.1.0, Play Billing ktx 8.1.0, core library desugaring (java.time cho API 24-25). DataStore, CameraX, navigation-compose, accompanist đang bị comment-out trong build.
- Secrets/config: `.env` + `.env.example`, secrets-gradle-plugin, `manifestPlaceholders` (admobAppId, appPluginLicense), `buildConfigField` (LEGAL_BASE_URL, ADMOB_*), google-services.json, signing release qua biến môi trường.
- Bố cục `app/src/main/java/com/example` (số dòng):
  - gốc: `InLoveApplication.kt` 118, `MainActivity.kt` 645
  - `ads/` 2 file 431 · `alarm/` 4 file 1078 (AlarmNotificationScheduler 500, LoveAnniversaryMilestoneScheduler 269, ReminderAlarmReceiver 257, BootCompletedReceiver 52) · `billing/` 5 file 605 (BillingManager 443) · `config/` 1 · `privacy/` 1
  - `data/auth` 2 (FacebookAuthGateway 99, UnlockAttemptStore 70) · `data/db` 4 (InLoveDao 420, AccountDataVault 263, Migrations 171, AppDatabase 68) · `data/email` 1 (EmailQueueService 245) · `data/media` 2 (FirebaseMemoryMediaRepository 142, LocalMediaStore 111) · `data/model` 2 (Entities 346, RbacModels 66)
  - `data/repository` 4 (AuthRepository 1168, InLoveRepository 900, OnlineCoupleRepository 725, RemoteAccountCleanup 55) · `data/seed` 4 (GiftIdeasSeed 418, VietnameseHolidays 129, WesternHolidays 97, HolidayDates 49) · `data/sync` 6 (SyncCoordinator 239, SyncWorker 149, MemorySyncAdapter 79, NetworkMonitor 53, AnniversarySyncAdapter 46, EntitySyncAdapter 24)
  - `di/AppServiceLocator.kt` 141 · `domain/` 8 file (AppMode, GiftRanker, content/*, media/*, usecase/GetPersonalizedGiftSuggestionsUseCase 182)
  - `ui/screens` 16 file / 17956 dòng (Dialogs 2581, GiftScreen 1735, LoveHomeScreen 1652, MemoriesGridScreen 1549, CalendarScreen 1527, AuthScreen 1218, ReminderScreen 1188, PairingScreen 1095, AppModals 1013, SettingsScreen 1009, PaywallScreen 784, OnboardingScreen 771, AccountSecurityDialogs 527, EditMyProfileDialog 469, AppPinLockScreen 446, VerifyInviteDialog 392)
  - `ui/components` 10 file / 5132 dòng (MilestoneBadgeTracker 1519, AnniversaryMemoriesWidget 1364 [widget TRONG app, không phải home-screen widget], LoveDaysCalculatorWidget 763, Material3DatePicker 488, CommonComponents 402, FloatingHeartsOverlay 256, NotificationsSheet 169, AdBannerPlaceholder 103, PressScale 46, MemoryMediaModel 22)
  - `ui/theme` 4 (Color, Motion, Theme, Type) · `ui/util` 5 (Localization 921, AuthSecurityManager 253, ProfileUtils 182, AuthMessages 36, OnlineProfileRules 14) · `ui/viewmodel/InLoveViewModel.kt` 1978
- Tài nguyên: `res/values` + `res/values-vi` (strings.xml en/vi, colors, themes), 3 drawable vector (ic_launcher_background/foreground, ic_stat_heart), mipmap launcher, `res/xml` (backup_rules, data_extraction_rules, file_paths). Tài liệu thiết kế: `DESIGN.md`, `PRODUCT.md` ở gốc repo.
- Manifest: quyền INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, VIBRATE, RECEIVE_BOOT_COMPLETED, BILLING; 1 Activity; 2 BroadcastReceiver (Boot, ReminderAlarm với action REMINDER/ANNIVERSARY/DAILY_CHECK/SNOOZE); FileProvider; WorkManager khởi tạo tay trong InLoveApplication. KHÔNG có AppWidget/Glance (Android hiện chưa có home-screen widget — các phần cần tự grep lại để chắc).
- Test: 49 file (`app/src/test` JUnit + Robolectric + Roborazzi, 1 file androidTest). Nhóm: ads, alarm, billing, data/db (migration 12→13, 13→14), data/repository, data/sync, domain, ui.
- `:appplugin` = SDK nội bộ (included build, substitute `com.app.plugin:appplugin` 2.4.2), 39.3k dòng Kotlin, **Android-only**: ads (AdMob/MAX/FB mediation, ~100 file), brain (bandit/guardrail), consent (UMP), iap, paywall, logevent, segment, license, firbase, appsflyer, attribution. Có `appplugin/backend` (Python/Cloud Run) và `docs/APPPLUGIN_SDK_OVERVIEW.md`.
- `monetization-brain-ios/` = Swift Package (115 file Swift, README ghi 869 test) là bản port iOS của SDK: targets MonetizationBrainCore, MonetizationBrainStoreKit (StoreKit 2), UMP (UIKit), Firebase; README tuyên bố đã port đủ các subsystem (brain, IAP, paywall, offerwall, CRM, consent). **Chưa rõ** phần hiển thị quảng cáo (AdMob/MAX) trên iOS đã có chưa — phần P6 phải kiểm chứng.
- Backend: `firebase/functions` (Cloud Functions), `firestore.rules`, `firestore.indexes.json`, `firebase.json`.
- Tài liệu release Android có thể làm khuôn cho iOS: `docs/release/PLAY_RELEASE_CHECKLIST.md`, `SDK_INVENTORY.md`, `SECRETS_AND_CONFIG.md`, `docs/RELEASE_QA.md`.
- Quy ước kế hoạch cũ: `docs/superpowers/plans/*.md` (xem 2026-10-12-inlove-polish-fixes.md để biết giọng văn và cấu trúc).

## 2. Ràng buộc từ người dùng

- iOS phải **đầy đủ tính năng** như Android, animation đẹp mượt, widget và các phần native làm chuẩn, đầy đủ nhất có thể.
- Có máy Mac. Code hiện nằm trên Windows; mọi việc cần Xcode/Simulator/Swift chạy trên Mac (`host = mac`). Việc chỉ cần Gradle/JVM/Android chạy được trên Windows (`host = win`). Với Kotlin/Native, target Apple chỉ biên dịch/link được trên macOS, nên `iosMain` chỉ kiểm chứng trên Mac.
- Quy tắc AGENTS.md của repo: UI Compose + Material 3, chạm >= 48dp, UI không gọi thẳng Room/Network (qua UseCase/Repository), state là data class bất biến qua StateFlow, có @Preview.
- Không commit/push/merge/PR trừ khi người dùng yêu cầu. Không đụng code nguồn ở giai đoạn lập kế hoạch.

## 3. Quyết định mặc định cần kiểm chứng (nếu bạn không đồng ý, ghi `DECISION-CONFLICT:` ở đầu file của bạn kèm lý do + bằng chứng)

- D1 Cấu trúc: `:shared` (KMP: commonMain/androidMain/iosMain) + `:app` (Android mỏng) + `iosApp/` (Xcode, SwiftUI host nhúng `ComposeUIViewController`) + widget extension. Tên cuối cùng do P1 chốt.
- D2 UI: Compose Multiplatform + Material 3 + Compose Resources (`Res.string`, `Res.drawable`, `values-vi`).
- D3 Persistence: Room KMP (BundledSQLiteDriver, KSP) giữ nguyên schema để Android không phải migrate; file DB iOS nằm trong App Group container để widget đọc được.
- D4 Network/serialization: Ktor client (OkHttp trên Android, Darwin trên iOS) + kotlinx.serialization thay Retrofit/Moshi (nếu còn dùng).
- D5 Thời gian: kotlinx-datetime thay java.time trong common (đếm số chỗ dùng).
- D6 Ảnh: Coil 3 (KMP). Video: cần player KMP/UIKitView interop.
- D7 Lifecycle/ViewModel: androidx.lifecycle ViewModel KMP + StateFlow.
- D8 SDK monetization KHÔNG port sang KMP: giao diện (`AdsGateway`, `BillingGateway`, `ConsentGateway`, `AnalyticsGateway`…) khai báo trong commonMain, Android dùng `appplugin` hiện có, iOS dùng Swift package `monetization-brain-ios` do iosApp truyền vào lúc khởi động.
- D9 Firebase iOS: ứng viên (a) GitLive firebase-kotlin-sdk, (b) interface trong commonMain + triển khai Swift gọi Firebase iOS SDK, tiêm lúc khởi động, (c) cinterop. P3 so sánh bằng bằng chứng và đề xuất một hướng.
- D10 Phần native bắt buộc Swift: WidgetKit, thông báo cục bộ/UNUserNotificationCenter, StoreKit/ads SDK, Sign in with Apple, Keychain, PHPicker/camera, BGTaskScheduler.
- D11 Invariant: **Android luôn xanh** sau mỗi task (build + test). Di chuyển code kiểu strangler theo từng lớp: domain → model/db → repository → viewmodel → UI.

## 4. Yêu cầu định dạng file kế hoạch của bạn

Mỗi phần là MỘT file markdown, ghi đúng đường dẫn được giao. Cấu trúc:

1. `# <Tên phần>` + dòng "Phụ trách: Pn · Ngày 2026-10-13".
2. **Goal**: một đoạn.
3. **Assumptions**: bảng `# | khẳng định | VERIFIED/UNVERIFIED | bằng chứng (file:dòng hoặc URL+ngày)`.
4. **Inventory**: kết quả khảo sát (API Android-only theo file, số chỗ dùng; thứ gì đã KMP-ready).
5. **Design / decisions**: quyết định kỹ thuật, so sánh phương án (có bằng chứng), chọn một.
6. **Platform interfaces introduced**: bảng `tên | vị trí commonMain | impl androidMain | impl iosMain/Swift | người dùng`.
7. **Tasks**: bảng `id | phase | files (owned) | change | test or command | depends on | model | host (win/mac) | size (S<=0.5d, M<=2d, L<=5d)`.
   - id có tiền tố riêng của phần (F-, D-, B-, U-, N-, M-, Q-) + số thứ tự.
   - phase: P0 spike · P1 refactor-trên-Android · P2 chuyển data · P3 chuyển UI · P4 native · P5 monetization · P6 hardening/release.
   - Một task = một thay đổi review được. Quyền sở hữu file loại trừ nhau trong cùng một wave. Nếu phải sửa file thuộc phần khác (Info.plist, libs.versions.toml, settings.gradle.kts, entitlements…), thêm nhãn `shared-file:<đường dẫn>` để file chính điều phối thứ tự.
   - model: `haiku` (cơ học, effort max) hoặc `sonnet` (logic khó/đồng thời/bảo mật/dữ liệu; effort high). Opus không viết code.
8. **Definition of Done** cho phần (kiểm chứng được bằng lệnh/kịch bản).
9. **Risks** (xếp theo mức độ, có cách giảm) và **Out of scope** theo dạng `skipped: X, add when Y`.
10. **Open questions for the user** (tối đa 5, chỉ câu hỏi làm đổi kế hoạch).
11. **Ước lượng**: tổng ngày công theo S/M/L, tách win/mac.

## 5. Cách đọc để không tràn context

- Không Read nguyên file > 400 dòng. Dùng Grep để đếm/liệt kê API Android-only (`android\.`, `Context`, `Activity`, `java\.time`, `SharedPreferences`, `Toast`, `Intent`, `Log\.`, `Locale`, `Bitmap`…), rồi Read theo đoạn (offset/limit) quanh các chỗ cần.
- Ưu tiên `codegraph_explore` (nếu có) với tên symbol/tên file để lấy nguồn + call path trong một lần.
- Tạo file kế hoạch NGAY với khung các mục, rồi điền dần từng mục bằng Edit để tiến độ không mất nếu bị cắt. Đừng ôm toàn bộ nội dung vào một lần ghi dài.
- Hôm nay là 2026-10-13, kiến thức của bạn về phiên bản thư viện có thể cũ. Mọi khẳng định về phiên bản/tương thích (Compose Multiplatform, Kotlin, AGP 9 + KMP, Room KMP, Coil 3, Ktor, GitLive Firebase, Firebase iOS SDK, Xcode/App Store yêu cầu…) phải kiểm bằng WebSearch/WebFetch (ghi URL + ngày) hoặc gắn UNVERIFIED. Nếu công cụ web là deferred, nạp bằng ToolSearch `select:WebSearch,WebFetch`.
- Chỉ ghi vào file kế hoạch của bạn. Không sửa mã nguồn, không chạy git commit/push, không chạy build dài.
- Báo cáo cuối tối đa 10 dòng: STATUS (DONE/PARTIAL/BLOCKED), đường dẫn file, số task theo host (win/mac), 3 rủi ro lớn nhất, mọi `DECISION-CONFLICT`, câu hỏi mở.
