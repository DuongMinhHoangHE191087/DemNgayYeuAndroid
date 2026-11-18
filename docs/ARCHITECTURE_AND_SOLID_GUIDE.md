# Kiến Trúc Tổng Thể & Hướng Dẫn SOLID (InLove KMP Refactor)

> Tài liệu chuẩn hóa ranh giới tầng (Clean Architecture), thiết kế hướng đối tượng SOLID, ngân sách kích thước file, Design System và các cổng kiểm chứng tự động (QA Gates) cho **InLove (Android + iOS + Shared KMP + Firebase Functions)**.

---

## 1. Sơ Đồ Kiến Trúc Đa Nền Tảng (Clean Architecture + KMP)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       PRESENTATION LAYER (UI)                           │
│  Android Compose (app/ui/)                iOS SwiftUI & WidgetKit       │
│  - ui/designsystem/ (Tokens, Inputs)      - iosApp/InLoveIos/Views/     │
│  - ui/dialogs/ (Single-purpose modals)    - iosApp/InLoveWidget/        │
│  - ui/screens/{gift,home,calendar}/       - Theme/InLoveIosTheme.swift  │
│  - ui/viewmodel/controllers/ (SOLID)                                    │
└───────────────┬─────────────────────────────────────────┬───────────────┘
                │ StateFlow / Events                      │ Swift Bridge
┌───────────────▼─────────────────────────────────────────▼───────────────┐
│                    SHARED KMP DOMAIN BRAIN (:shared)                    │
│  shared/src/commonMain/kotlin/com/example/shared/                       │
│  - dossier/      PartnerDossierModels, PartnerDossierStore (+ fakes/)   │
│  - calendar/     LunarCalendar2025To2040, AnniversaryBehaviorEngine     │
│  - ai/           AiGiftAdvisorGateway, SmartRuleGiftAdvisor,            │
│                  AiPromptBuilder (+ fakes/)                             │
│  - monetization/ ProTierCatalog, ProFeature, SacredZone,AdPlacementPolicy│
│  - widget/       CoupleWidgetSnapshot & JSON codec                      │
│                                                                         │
│  iOS Facade: shared/src/iosMain/kotlin/com/example/ios/IosInLoveFacade  │
└───────────────┬─────────────────────────────────────────┬───────────────┘
                │ Repositories / Gateways                 │ HTTPS Callable
┌───────────────▼───────────────────────┐ ┌───────────────▼───────────────┐
│        ANDROID DATA & SERVICES        │ │       FIREBASE BACKEND        │
│  - data/dossier/ (Atomic JSON/Scope)  │ │  - functions/src/aiAdvisor.js │
│  - data/ai/      (Hybrid Gateway)     │ │  - functions/src/entitlement/ │
│  - monetization/ (AppPlugin Facade)   │ │    gatePolicy.js (AppCheck +  │
│  - ads/          (AdPlacementPolicy)  │ │    rateLimit + quota)         │
│  - data/db/      (Room v14 immutable) │ │                               │
└───────────────────────────────────────┘ └───────────────────────────────┘
```

---

## 2. Ranh Giới Tầng Bắt Buộc (Architecture Guardrails)

| Mã Gate | Quy tắc bắt buộc | Công cụ kiểm tra |
|---|---|---|
| **ARCH-01 / GG-01** | `shared/src/commonMain/**` **100% thuần Kotlin**: CẤM `@Composable`, `android.*`, `java.*`, `javax.*`, `com.google.firebase.*`, `com.squareup.moshi.*`, `com.app.plugin.*`, `runBlocking`. Dùng `kotlinx.datetime`, `kotlinx.serialization`, `kotlinx.coroutines`. | `scripts/qa/check-grep-gates.mjs` & `scripts/qa/check-architecture.mjs` |
| **ARCH-01 (UI)** | `app/src/main/java/com/example/ui/**` CẤM import `androidx.room.*` hoặc `com.google.firebase.firestore.*`. Mọi truy cập dữ liệu phải đi qua UseCase / Repository / Controller. | `scripts/qa/check-architecture.mjs` |
| **FAKES-01** | Mọi `interface` trong `shared/src/commonMain` có tên kết thúc bằng `Gateway`, `Api`, `Invoker`, `Storage`, `Store`, `Clock`, hoặc `Engine` PHẢI có `class Fake<Name>` hoặc `object Fake<Name>` trong thư mục `fakes/` hoặc `testing/` (hoặc `commonTest`). | `scripts/qa/check-fakes.mjs` |
| **SCHEMA-01** | KHÔNG chỉnh sửa `Entities.kt` hay `AppDatabase.kt` ngoài kế hoạch migration; hash Room v12/v13/v14 bị khóa cứng. Dữ liệu mới (`PartnerDossier`, AI quota cache) lưu bằng atomic JSON file theo từng `scopeId`. | `scripts/kmp/schema-check.sh` |
| **IOS-BRIDGE-01** | Mọi `public fun` và constructor trong `shared/src/iosMain/kotlin/com/example/ios/` phải có `@Throws(Exception::class)` hoặc comment `// no-throw: <lý do>`; cấm default params, `value class`, `Result<T>`, generic interface, `runBlocking`. | `scripts/kmp/check-ios-bridge.sh` |
| **SKU-PARITY (ARCH-04)** | Tập SKU `["vip_lifetime", "vip_monthly", "vip_yearly"]` phải khớp 100% giữa `VipProductIds.kt`, `MonetizationPolicy.kt`, `iosApp/StoreKit/InLove.storekit`, và `InLoveIosCore`. | `scripts/qa/check-architecture.mjs` |

---

## 3. Áp Dụng 5 Nguyên Lý SOLID Trong Refactor

1. **Single Responsibility Principle (SRP)**:
   - Tách God-ViewModel `InLoveViewModel.kt` thành các controller chuyên biệt trong `ui/viewmodel/controllers/`:
     - `PartnerDossierController`: quản lý tải/lưu hồ sơ người thương, ghi chú bí mật, mức độ hoàn thiện (`completenessPercent`).
     - `AiGiftConciergeController`: điều phối `AiGiftAdvisorGateway`, chọn tone, giữ bộ lọc an toàn và theo dõi hạn mức miễn phí/Pro.
     - `AnniversaryReadinessController`: tính `NextOccasionReadiness` & trạng thái Vùng Thiêng ngày lễ.
     - `DialogUiStateController`: điều phối state đóng/mở dialog thay cho hàng loạt `MutableStateFlow` rời rạc.
   - Tách God-File `Dialogs.kt` thành 5 module trong `ui/dialogs/` (`Dialogs.kt` chỉ còn facade tương thích ngược `<= 350` dòng).
   - Tách `GiftScreen.kt` thành các thành phần con trong `ui/screens/gift/`.
2. **Open/Closed Principle (OCP)**:
   - `AnniversaryBehaviorEngine` và `GiftArchetypeCatalog` dùng mô hình dữ liệu (`OccasionProfile`, `GiftArchetype`) thay vì `if/else` theo chuỗi hiển thị; thêm dịp mới hoặc mẫu quà mới không cần sửa luồng tính điểm.
   - `AdPlacementPolicy` phân loại qua `SacredZone` & `UpsellTrigger` enum.
3. **Liskov Substitution Principle (LSP)**:
   - `SmartRuleGiftAdvisor`, `FakeAiGiftAdvisorGateway`, và `HybridAiGiftAdvisorRepository` đều tuân thủ hợp đồng `AiGiftAdvisorGateway.advise(request)` với cùng ràng buộc lọc dị ứng, ngân sách và không trả null.
   - `LocalJsonPartnerDossierRepository` và `FakePartnerDossierStore` đều tuân thủ `PartnerDossierStore`.
4. **Interface Segregation Principle (ISP)**:
   - Tách nhỏ `AdPolicyGate` (quyết định cho phép quảng cáo / SacredZone) và `MonetizationFeatureGate` (kiểm tra quyền VIP / soft-paywall) thay vì buộc UI phụ thuộc toàn bộ `MonetizationSdk`.
   - Tách `PartnerDossierStore` khỏi `InLoveDao` để lớp UI/iOS không biết đến SQL hay Room.
5. **Dependency Inversion Principle (DIP)**:
   - Domain & ViewModel Controllers chỉ phụ thuộc abstraction (`AiGiftAdvisorGateway`, `PartnerDossierStore`, `AdPolicyGate`), không phụ thuộc trực tiếp Firebase Functions, `:appplugin`, hay Google Play Billing. Wiring thực tế tập trung tại `di/AppServiceLocator.kt`.

---

## 4. Ngân Sách Kích Thước File & Thống Nhất Design System

### 4.1 Giới Hạn Số Dòng Code (ARCH-02)
- **Mọi file mới/refactor** trong `shared/src/**`, `ui/designsystem/**`, `ui/dialogs/**`, `ui/viewmodel/controllers/**`, `ui/screens/{gift,home,calendar}/**`, `data/dossier/**`, `data/ai/**`, `monetization/**`, `iosApp/**`: **tối đa `550` dòng**.
- **Trần file sau refactor**:
  - `Dialogs.kt` `<= 350` dòng
  - `GiftScreen.kt` `<= 650` dòng
  - `InLoveViewModel.kt` `<= 1550` dòng
- **Ratchet cho các file lớn cũ**: không được phình to vượt baseline hiện tại (`LoveHomeScreen.kt`, `CalendarScreen.kt`, `PaywallScreen.kt`, `MemoriesGridScreen.kt`, `OnboardingScreen.kt`, `AuthScreen.kt`, `PairingScreen.kt`, v.v.).

### 4.2 Design System & WCAG AAA Contrast (ARCH-03)
- **Canonical Tokens (`ui/designsystem/`)**:
  - `InLoveTokens.kt`, `InLoveInputs.kt`, `InLoveCards.kt`, `InLoveModalScaffold.kt`.
  - **Bắt buộc**: Toàn bộ `OutlinedTextField` trong `app/src/main/java/com/example/ui/` phải dùng `inLoveOutlinedTextFieldColors()` — **CẤM** viết lại `OutlinedTextFieldDefaults.colors(` ngoài `ui/designsystem/`.
- **Độ tương phản chuẩn `DESIGN.md`**:
  - Chữ chính: `TextDarkPlum` (`#1F0416`, ~15:1 trên nền trắng).
  - Chữ phụ/hint: `TextSubtlePlum` (`#4A1934`, ~9.2:1 trên nền trắng).
  - **CẤM** dùng `Color.Gray` hoặc `Color.LightGray` trong `ui/designsystem/`, `ui/dialogs/`, `ui/screens/gift/`, `ui/screens/home/`, `ui/screens/calendar/`.
  - Mọi thành phần tương tác đạt vùng chạm tối thiểu `48.dp × 48.dp` (`48pt` trên SwiftUI).

---

## 5. Bảo Mật, Quyền Riêng Tư & Bất Biến Backend

1. **Cloud Functions Gate Invariants**:
   - Mọi `exports.<name> = onCall(...)` trong `firebase/functions/index.js` phải bật `enforceAppCheck: true`, khai báo `cfg.rateLimits` (`src/config.js`), khai báo `GATES` (`src/entitlement/gatePolicy.js`), gọi `await limit('<name>', uid);` và `const { tier } = await gateFor('<name>', uid);`.
   - `generateGiftAdvice` dùng cổng `tiered` (`freeAllowed: true`: Free 2 lượt/tháng, VIP 60 lượt/tháng, quota tại `aiAdvisorQuota/{uid}_{YYYYMM}`).
2. **PII Redaction**:
   - `AiPromptBuilder` chỉ gửi `occasionRegion`, `occasionId`, `daysUntilOccasion`, `loveLanguages`, `interestCategories`, size/màu/mùi hương và danh sách dị ứng/kiêng kỵ đã làm sạch email/SĐT/URL. Không gửi `nickname` hay `secretSurpriseNotes`.
3. **Cross-Platform Date Integrity**:
   - Mọi tính toán ngày yêu dùng chuẩn `Day 1 = startDate -> Day N = startDate + (N - 1)` khớp `LoveMilestoneRules` và `AnniversaryBehaviorEngine`.
   - Mọi thao tác lưu/đọc ngày chuẩn dùng ISO `YYYY-MM-DD` hoặc `Locale.ROOT` bất biến với locale máy.

---

## 6. Bộ Lệnh Kiểm Chứng Chuẩn (Verification Gates)

Trước khi chốt PR/merge, toàn bộ 6 cổng sau phải xanh 100%:
```powershell
node scripts/qa/run-all-gates.mjs
& "C:\Program Files\Git\bin\bash.exe" scripts/kmp/schema-check.sh
& "C:\Program Files\Git\bin\bash.exe" scripts/kmp/check-ios-bridge.sh
.\gradlew.bat :shared:compileCommonMainKotlinMetadata :shared:testAndroidHostTest -Dorg.gradle.java.home="C:\Users\os\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2" -Dorg.gradle.jvmargs="-Xmx1792m -XX:ReservedCodeCacheSize=384m -XX:+UseG1GC -Dfile.encoding=UTF-8" --max-workers=1
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest -Dorg.gradle.java.home="C:\Users\os\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2" -Dorg.gradle.jvmargs="-Xmx1792m -XX:ReservedCodeCacheSize=384m -XX:+UseG1GC -Dfile.encoding=UTF-8" --max-workers=1
git diff --check
```
*(Trên macOS CI chạy thêm `swift test --package-path iosApp/Packages/InLoveIosCore` và `:shared:iosSimulatorArm64Test`.)*
