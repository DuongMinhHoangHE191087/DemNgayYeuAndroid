# Hướng dẫn tích hợp `appplugin` (một nguồn, có kiểm tĩnh)

Tài liệu này đi từ một app Android trống tới app chạy quảng cáo, consent, IAP, đo lường và brain. Mỗi tên API, toạ độ Gradle, khoá manifest, tài nguyên và khoá Remote Config trong đây được `tools/integration_doc_check.sh` đối chiếu với `api/appplugin.api`, `build.gradle.kts`, `src/main` và `CHANGELOG.md` (bản tiếng Anh ngắn ở cuối). Làm đúng thứ tự các mục; mục 12 là danh sách kiểm trước khi phát hành.

**Tài liệu này mô tả SDK 2.4.1:** dùng `<phiên bản>` = 2.4.1 (các mục ghi "từ 2.4.0", như artifact `appplugin-nextgen`, "từ 2.3.0", như preload và policy brain tuỳ chọn, hoặc "từ 2.2.0", như minSdk 24, segment trong core và brain theo giấy phép, không đúng với bản cũ hơn). Bản của một AAR hay tag: tag `vX.Y.Z` trùng `sdkVersion` trong `build.gradle.kts`, và `MonetizationSdk.VERSION` (cũng in ở `report.describe()`) cho biết bản đang chạy. Bốn artifact luôn cùng một số phiên bản.

## 1. Yêu cầu

- compileSdk 36, AGP 9.2.1 trở lên (bản SDK được dựng và kiểm; AGP 9 biên dịch Kotlin sẵn nên **không cần** plugin `org.jetbrains.kotlin.android`; app còn ở AGP 8.x tự áp `kotlin-android` ≥ 2.2, và AGP 8 không phải bản SDK được kiểm), JDK 17 trở lên cho Gradle (CI của SDK dùng 21; đặt `JAVA_HOME` trỏ tới JDK đó, ví dụ JBR của Android Studio, vì lệnh dòng lệnh không tự tìm); SDK xuất bytecode Java 8 nên app nào cũng đọc được.
- App dùng Dagger/Hilt trước 2.60 với kapt (thường ở AGP 8.x; đo ở LAM2026: AGP 8.10, Hilt 2.57.2 kapt, Kotlin 2.2.x) vỡ `kaptDebugKotlin` ("Provided Metadata instance has version 2.3.0…"), vì adapter Meta `com.google.ads.mediation:facebook` bản 6.21.0.4 (phụ thuộc runtime của SDK từ 1.2.0) khai kotlin-stdlib 2.3.0. Cách đã kiểm, giữ nguyên adapter như SDK đã đo: ép `kotlin-metadata-jvm` lên 2.3.0 cho các cấu hình `kapt*`: `val kmd = "org.jetbrains.kotlin:kotlin-metadata-jvm"; configurations.matching { it.name.startsWith("kapt") }.configureEach { resolutionStrategy.force("$kmd:2.3.0") }`. Cách khác: ghim adapter 6.21.0.3 như mục "Phải làm khi nâng cấp" của [1.2.0]. Hilt 2.60 trở lên hoặc KSP: không cần ép.
- SDK minSdk 24 (từ 2.2.0; trước đó 26): app có `minSdk` 24 trở lên dùng được SDK, không cần `tools:overrideLibrary`. Nếu app từng thêm `tools:overrideLibrary` để ép merge với SDK 26 thì bỏ đi. App `minSdk` 26 trở lên: không phải làm gì. Giá trị nằm ở `build.gradle.kts` (`minSdk = 24`), `gradle/module-defaults.gradle` (cho `-game` và `-max`), `appplugin-nextgen/build.gradle.kts` (cùng giá trị) và demo `app/build.gradle.kts`; manifest của cả bốn AAR mang `minSdkVersion="24"`.
- Một app Firebase (mục 7), một tài khoản AdMob, và token giấy phép cho từng `applicationId` (mục 5). Không có token thì không có quảng cáo và không có brain.
- Máy dựng cần GitHub PAT có quyền `read:packages` (mục 2).

## 2. Repository và khoá GitHub Packages

SDK publish lên GitHub Packages `https://maven.pkg.github.com/dungduong2899/appplugin` (publish bằng `tools/release_local.sh` (không CI), đọc credential như `gradle/publish.gradle`). Repository cần khai **theo artifact**:

| Artifact | Repository cần |
|---|---|
| `com.app.plugin:appplugin` | `google()`, `mavenCentral()`, GitHub Packages |
| `com.app.plugin:appplugin-nextgen` | như `appplugin` (GitHub Packages vẫn cần; `ads-mobile-sdk` ở Google Maven nên không cần repository thêm so với `appplugin`) |
| `com.app.plugin:appplugin-game` | như `appplugin` |
| `com.app.plugin:appplugin-max` | như `appplugin`, **thêm** `https://artifacts.applovin.com/android` (AppLovin SDK và adapter MAX không ở Maven Central) |

`settings.gradle.kts` (bắt buộc khi app đặt `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, như app mẫu; app không đặt thì khai trong `build.gradle.kts` cũng được):

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // com.app.plugin chỉ lấy từ repository này, và repository này chỉ được hỏi cho nhóm đó.
        val appPluginLocalRepo = providers.gradleProperty("appplugin.localRepo").orNull
        exclusiveContent {
            forRepository {
                if (appPluginLocalRepo != null) {
                    maven { url = uri(appPluginLocalRepo) }   // chỉ để kiểm cục bộ (phụ lục A)
                } else {
                    maven {
                        url = uri("https://maven.pkg.github.com/dungduong2899/appplugin")
                        credentials {
                            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }
            filter { includeGroup("com.app.plugin") }
        }
        // Chỉ khi dùng appplugin-max:
        maven { url = uri("https://artifacts.applovin.com/android") }
    }
}
```

- **Nên dùng `exclusiveContent` + `includeGroup("com.app.plugin")`** (checker của SDK đòi khối mã này trong tài liệu): nhóm `com.app.plugin` chỉ được tìm ở repository trong khối, và repository đó chỉ được hỏi cho nhóm đó. Thiếu khối, Gradle tìm `com.app.plugin` ở `google()`, `mavenCentral()` và mọi repository khác trước, còn mọi artifact không tìm thấy ở nơi khác bị gửi kèm thông tin đăng nhập tới GitHub Packages (mở đường cho dependency confusion).
- Property `appplugin.localRepo`: `-Pappplugin.localRepo=<SDK>/build/repo` đổi repository trong khối `exclusiveContent` sang repo cục bộ (phụ lục A) mà không sửa file; bỏ property thì dùng GitHub Packages.
- Máy dev: `~/.gradle/gradle.properties` có `gpr.user=<tên GitHub>` và `gpr.key=<PAT read:packages>`. Không commit PAT.
- CI: đặt secret (ví dụ `GPR_USER`, `GPR_KEY`) và truyền `-Pgpr.user=… -Pgpr.key=…` hoặc biến môi trường `GITHUB_ACTOR` + `GITHUB_TOKEN` (token mặc định của workflow trong cùng tổ chức không đọc được gói của repo khác: dùng PAT).
- Một repository cho cả bốn artifact; GitHub Packages không có bản ghi đè, nên `<phiên bản>` đã publish là bất biến.
- Không có mạng tới GitHub Packages (kiểm cục bộ, môi trường cách ly): phụ lục A.

Khai dependency (cùng `<phiên bản>`):

```kotlin
dependencies {
    implementation("com.app.plugin:appplugin:<phiên bản>")   // GMA Legacy (mặc định)
    // HOẶC, thay cho dòng trên, khi chọn GMA Next-Gen (từ 2.4.0, mục 3 và 16):
    // implementation("com.app.plugin:appplugin-nextgen:<phiên bản>")
    // Chỉ app game / app dùng RC game hay tín hiệu CRM:
    implementation("com.app.plugin:appplugin-game:<phiên bản>")
    // Chỉ app dùng AppLovin MAX (chỉ với `appplugin`, không với `appplugin-nextgen`):
    implementation("com.app.plugin:appplugin-max:<phiên bản>")
}
```

## 3. Bốn artifact và khi nào cần

SDK publish bốn artifact cùng một số phiên bản. Chọn **một** trong `appplugin` (GMA Legacy) hoặc `appplugin-nextgen` (GMA Next-Gen, từ 2.4.0), rồi thêm `-game` khi cần; mục 16 so sánh hai bản.

| Artifact | Cần khi | Mang gì |
|---|---|---|
| `com.app.plugin:appplugin` | mọi app chọn GMA Legacy (mặc định) | toàn bộ mã SDK: AdMob (GMA Legacy `play-services-ads` 25.4.0) và Meta, consent, đo lường, billing/paywall, brain, Remote Config, AppsFlyer |
| `com.app.plugin:appplugin-nextgen` | app chọn GMA Next-Gen, **thay cho** `appplugin` (không khai cả hai) | cùng mã SDK và cùng API công khai, GMA Next-Gen `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0` và adapter Meta `com.google.ads.mediation:facebook:6.22.0.1`; POM không kéo `play-services-ads` **Bản 2.4.0: preview, chưa dùng production** (mục 16: OOM của `ads-mobile-sdk` 1.5.0) |
| `com.app.plugin:appplugin-game` | app game, hoặc app dùng 5 khoá RC game / CRM | `PurchaseRouter`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`, `GameModule`; kéo `appplugin` cùng bản. Chỉ chạy đúng với `appplugin` hoặc `appplugin-nextgen` cùng bản; với Next-Gen cần quy tắc thay phụ thuộc ở mục 17 |
| `com.app.plugin:appplugin-max` | app dùng AppLovin MAX (chỉ với `appplugin`) | 0 mã: AppLovin SDK 13.6.2, 8 adapter `com.applovin.mediation:*`, `com.bigossp:admob-mediation:5.7.0.0`, `com.google.ads.mediation:chartboost:9.12.1.1`, `com.google.ads.mediation:fyber:8.4.5.1`, `com.unity3d.ads:unity-ads:4.19.0` |

- App tiện ích (Speaker Clean, Invoice Maker): chỉ `appplugin` hoặc chỉ `appplugin-nextgen`.
- `appplugin-max` + `appplugin-nextgen`: không hỗ trợ. Next-Gen buộc exclude `play-services-ads` toàn cục, mà `google-adapter` của MAX cần nó; chưa có adapter MAX cho Next-Gen. Bigo cũng chỉ có qua `-max`. App dùng MAX ở lại `appplugin`.
- `appplugin` và `appplugin-nextgen` không đặt cùng classpath: `ads-mobile-sdk` 1.5.0 mang 92 lớp trùng tên `com.google.android.gms.ads.*`, nên trong cùng một app chỉ một trong hai bản được khai.
- Năm adapter GMA bidding tuỳ chọn (`pangle`, `mintegral`, `unity`, `vungle`, `applovin`) là `compileOnly` trong SDK: app muốn partner nào thì tự khai adapter đó cùng bản **và** liệt kê tên partner ở `@array/ads_bidding_partners`.

## 4. `Application`

Kế thừa `AppPluginBase` và khai trong manifest `android:name`. Hilt dùng được: `@HiltAndroidApp class App : AppPluginBase()`.

```kotlin
import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.app.AppPluginBase   // gói com.app.plugin.app, KHÔNG phải com.app.plugin

class App : AppPluginBase() {
    override fun onCreate() {
        super.onCreate()
        val report = MonetizationSdk.configure(this) {
            brainEnabled = false               // bật bằng quyết định kinh doanh có ý thức (mục 9)
            inappProducts = listOf("coin_pack")
            subsProducts = listOf("premium_monthly")
            removeAdsProducts = setOf("premium_monthly")
        }
        Log.i("App", report.describe())        // báo cáo tự kiểm tra: bắt cấu hình lặng lẽ không làm gì
    }
}
```

Mẫu đầy đủ chạy được: `app/src/main/java/com/app/plugin/demo/DemoApplication.kt` và `app/src/main/AndroidManifest.xml` (trong repo SDK; app ngoài làm theo các đoạn mã của tài liệu này). Bảng gói (import) của mọi lớp được nhắc ở đầu mục 9.

**Activity chủ (host):** SDK tự nhận Activity chủ khi resume (Full2 nối, `isInterstitialReadyToShow`, lớp phủ chuyển cảnh đều cần host). Từ 2.4.1 host là Activity không thuộc 11 tiền tố SDK quảng cáo/mediation (danh sách ở CHANGELOG [2.4.1]) và (tên lớp bắt đầu bằng `packageName` (luật cũ, giữ) hoặc là Activity launcher của `packageName` hoặc cùng package với lớp `Application`), nên bản `.debug` (`applicationIdSuffix`) và flavor `applicationId` được nhận đúng (launcher khai bằng `activity-alias` không khớp luật launcher; luật tiền tố/gói app thường vẫn phủ). Trên mọi bản từ 8fcc3c1 (2026-09-11), gồm 2.3.0 và 2.4.0, host chỉ được nhận khi tên lớp Activity bắt đầu bằng `packageName`: app có suffix/flavor `applicationId` phải gọi `AppPluginManager.instance.setMainActivity(this)` trong `onCreate` của Activity chính (trước lần resume đầu), nếu không Full2 nối bị huỷ sau 3 s và `isInterstitialReadyToShow` luôn `false`. Từ 2.4.1 không cần, giữ lại vẫn vô hại.

**App open khi quay lại app (full_resume):** mỗi lần vào foreground (trừ lần đầu của process, `isSStart`), `AppPluginBase.onStart` hẹn `showOpenAd("full_resume")` **1 s** sau (`AppPluginBase.kt:226-227`); cờ `setFlag4ShowResume` chỉ chặn bên trong `showOpenAd` (cờ tắt ⇒ lượt này bị chặn `open_flag_resume` và vẫn ghi event show-fail). `AppResumeIF.onActivityResume` chạy ở **mọi** resume của host, sau `onMainActivityResumed`; gọi `AdsHelper.instance.suppressNextOpenAd(ms)` trong callback này chặn cả open ad lẫn Full2 đang chờ (Full2 được post sau). Đăng ký callback theo Activity (Compose effect) sẽ lỡ lần resume của Activity được tạo lại; nên đăng ký một lần ở `Application`. Từ 2.5.0 `AdsHelper.instance.setAutoShowOpenAdOnForeground(false)` chỉ tắt lượt **SDK tự hẹn** (kiểm lúc hẹn và lúc chạy: tắt trong 1 s cũng không hiện; bỏ lượt ghi `ads open ad auto-show on foreground skipped (app)`, và bỏ cả event show-fail nói trên); app vẫn gọi `showOpenAd` theo luật riêng. Khác `setFlag4ShowResume(false)`, vốn chặn **mọi** open ad không phải `full_splash`, cả lượt app tự gọi (`open_flag_resume`). Mặc định `true` (như 2.4.4). Giá trị chỉ nằm trong RAM, không lưu qua process: gọi mỗi lần app khởi động (ví dụ `Application.onCreate`), trước lần foreground thứ hai.

## 5. Token giấy phép

Từ 2.1.0 quảng cáo và brain chỉ chạy khi có token hợp lệ. Token gắn `applicationId` + SHA-256 của chứng chỉ ký + hạn + tính năng (`ads`, `brain`). IAP, consent và logevent không phụ thuộc token.

1. **Việc của chủ SDK (app tích hợp chỉ nhận token):** khoá ký nằm ngoài repo: `python3 tools/license_issue.py keygen --out ~/.appplugin/license_<kid>.alpk` (mode 600; công cụ từ chối đường dẫn trong repo). Khoá riêng (chuỗi bắt đầu bằng `ALPK1`) không bao giờ vào repo, log hay chat. SDK hiện chỉ biết khoá công khai của kid `dev1` (xoay khoá = thêm kid mới ở một bản SDK sau); token phát bằng kid khác `dev1` sẽ ra `INVALID (kid)`.
2. **SHA-256 chứng chỉ ký** (hex thường, 64 ký tự, không dấu hai chấm):
   - debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | sed -n 's/.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'`
   - upload key: cùng lệnh với keystore upload của app;
   - **Play App Signing:** bản cài từ Play được Google ký lại bằng khoá của Google, nên lấy SHA-256 ở Play Console → App integrity → App signing → "App signing key certificate" (tên menu có thể đổi) và thêm cert đó. Quên cert này là lỗi `cert` ở người dùng thật, không thấy được ở bản dev.
3. **Phát token** cho từng `applicationId`; `--cert` lặp được:
   `python3 tools/license_issue.py issue --key ~/.appplugin/license_<kid>.alpk --kid <kid> --pkg <applicationId> --cert <sha256 debug> --cert <sha256 upload> --cert <sha256 Play> --days 365 --feat ads,brain`
4. **Một token cho mỗi `applicationId`:** bản `.debug` (`applicationIdSuffix`) là một `applicationId` khác, cần token riêng với cert debug.
5. **Khai trong manifest** (meta-data `com.app.plugin.LICENSE`), token theo build type qua `manifestPlaceholders`. **Token không vào file tracked** (kể cả `gradle.properties` của repo): đặt ở `license.properties` ở thư mục gốc dự án, file nằm trong `.gitignore` (cùng mẫu `key.properties`), và commit `license.properties.sample` chỉ có placeholder:

```xml
<meta-data android:name="com.app.plugin.LICENSE" android:value="${appPluginLicense}" />
```
```properties
# license.properties.sample (commit). license.properties thật: sao chép file này rồi điền, KHÔNG commit.
licenseDebug=ALP1.<token của applicationId .debug>
licenseRelease=ALP1.<token của applicationId bản phát hành>
```
```kotlin
// app/build.gradle.kts (import java.util.Properties ở đầu file)
val licenseProperties = Properties().apply {
    val f = rootProject.file("license.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun appPluginLicense(key: String): String =
    licenseProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }   // 1. license.properties
        ?: (findProperty(key) as String?)                                    // 2. -PlicenseDebug=… hoặc gradle.properties của máy
        ?: ""                                                                // 3. thiếu: license: INVALID (missing), không crash
android {
    defaultConfig { manifestPlaceholders["appPluginLicense"] = "" }          // flavor/build type khác không lỗi merge
    buildTypes {
        debug   { manifestPlaceholders["appPluginLicense"] = appPluginLicense("licenseDebug") }
        release { manifestPlaceholders["appPluginLicense"] = appPluginLicense("licenseRelease") }
    }
}
```

**Thử bản release trước khi lên Play:** bản release dựng cục bộ thường ký bằng khoá debug hoặc khoá upload, nên cần một token có cert đó (cùng `applicationId` của bản release, không phải `.debug`): lấy SHA-256 bằng lệnh `keytool` ở trên với keystore đang ký, phát token, đặt `licenseRelease`. Dùng token của `.debug` cho bản release cho ra `INVALID (pkg)` (quảng cáo và brain tắt, không crash).

Token là chuỗi `ALP1.<payload>.<chữ ký>`, không phải bí mật (khoá công khai nằm trong SDK) nhưng gắn chặt `applicationId` và cert nên không dùng lại được ở app khác. Dù vậy đừng ghi nó vào file tracked.

**Kết quả lúc chạy:** đúng một dòng verdict mỗi process: `license: VALID kid=<kid> exp=<ngày>` hoặc `license: INVALID (<lý do>) — quảng cáo và brain tắt` (từ 2.4.3 kèm một dòng `license: source=…` ngay trước, ở cả VALID lẫn INVALID); INVALID thì mạng quảng cáo không khởi tạo, không quảng cáo, không brain, không crash. Kiểm bằng `adb logcat | grep "license:"`. Từ 2.4.3 verdict `VALID` được nhớ bền trong file prefs riêng của SDK (`appplugin_license`): khoá cache gồm token, `applicationId`, tập chứng chỉ ký, tập khoá công khai và bản SDK, nên đổi token, đổi chứng chỉ ký hoặc nâng SDK đều buộc xác minh đầy đủ lại một lần; hạn `exp` vẫn được kiểm bằng đồng hồ thật mỗi lần mở; INVALID không bao giờ được cache; lỗi cache thì xác minh đầy đủ. Cách cấp token không đổi. Từ 2.4.3 có thêm một dòng log `license: source=cache|verify ms=<n> since_start=<n> cert=<n>` ngay trước dòng verdict (`license: VALID …` hoặc `license: INVALID …`).

| Lý do | Nghĩa | Sửa |
|---|---|---|
| `missing` | không có meta-data hoặc placeholder rỗng | khai meta-data, đặt `licenseDebug`/`licenseRelease` trong `license.properties` |
| `malformed` | không phải token `ALP1.…` (cắt, thêm khoảng trắng) | dán lại nguyên chuỗi |
| `kid` | khoá công khai của kid này không có trong SDK | phát lại bằng kid đang dùng (`dev1`) |
| `sig` | chữ ký sai (sửa token, hoặc khoá khác) | phát lại |
| `pkg` | `applicationId` ≠ token | token riêng cho `.debug` và từng flavor |
| `cert` | cert ký của APK không nằm trong token | thêm cert (đặc biệt cert Play App Signing) rồi phát lại |
| `expired` | quá hạn (không có thời gian ân hạn) | phát token mới, phát hành lại app |
| `feat` | thiếu `ads` hoặc `brain` | phát lại với `--feat ads,brain` |
| `timeout`, `error` | xác minh quá 2 giây hoặc ném (fail-closed) | báo lỗi kèm logcat |

**Brain theo giấy phép (từ 2.2.0).** Brain chỉ quyết khi verdict là `VALID`:

- Verdict `INVALID` **hoặc còn `PENDING`** (chưa có verdict, ví dụ ngay sau khi process khởi động): `DecisionEngine.decide` trả hành động `NO_ACTION` đầu tiên với lý do `license_INVALID:<lý do>` hoặc `license_PENDING`, không bandit, không ghi `brain_decision`; quảng cáo quyết bằng luật tĩnh. App **không nên chờ quyết định của brain trước khi có verdict** (thường vài chục ms, tối đa 2 giây rồi `timeout`).
- Phản hồi (`feedback`) cho một quyết định bị giấy phép chặn bị bỏ qua, kể cả khi verdict sau đó thành `VALID`.
- Tín hiệu không phải quyết định (hàng `experiment_exposure` của phiên và tín hiệu lần mua đầu `onFirstPurchase` gửi tới CRM) được giữ trong RAM khi `PENDING` (tối đa 8, bỏ cái cũ nhất), gửi ở verdict `VALID` đầu tiên và bỏ hết khi `INVALID`. Hàng exposure đang giữ mà `playerId` đã đổi trước verdict (ví dụ `configure { playerId = … }`) bị bỏ.
- Khoá giấy phép tự bắt đầu ở `AppPluginBase.onCreate` (đọc meta-data) và ở khởi tạo mạng quảng cáo sau consent (`AdsMobMy.startSdkInit`). Chỉ app **vừa** không kế thừa `AppPluginBase` **vừa** chưa bao giờ tới bước khởi tạo mạng quảng cáo thì verdict ở `PENDING` mãi: brain tắt, không gửi gì.
- Token sai cố ý (kiểm cổng): quảng cáo tắt, brain tắt, `absent-class sdk=0` ở `release_gate.sh`, đúng một dòng `license: INVALID (<lý do>)`; IAP, consent, logevent không đổi.

Việc của app: **không có gì thêm ngoài meta-data token** (mục 5). Dòng `license:` và bảng lý do ở trên không đổi; param `license` của `sdk_health` là `VALID`, `INVALID:<lý do>` hoặc `PENDING`.

## 6. Manifest, resource và xung đột backup-rules của AppsFlyer

**Khoá app-contract** (app khai, SDK đọc bằng tên lúc chạy, tên không có tiền tố `appplugin_`): `admob_*`, `adfb_*`, `admax_*`, `ads_cf_*`, `flagTest`, `admob_test_device`, `fb_test_device`, `max_test_device`, `facebook_app_id`, `af_app_id`, `admax_devKey`, `@array/ads_*`. SDK mang `@raw/appplugin_keep` (`src/main/res/raw/appplugin_keep.xml`, `tools:keep` cho các tên này) để `shrinkResources` của app không xoá chúng. **Đừng** đặt resource tên `appplugin_keep` trong app (bản của app đè file của SDK). Resource SDK tự khai đều có tiền tố `appplugin_` (96 tên); app ghi đè layout, style hay `@xml/appplugin_gma_ad_services_config` phải dùng tên mới (bảng ở `DOC/61_INTERNAL_SDK_THREE_APPS.md` mục G).

**Tập resource tối thiểu app phải khai** (thiếu thì tính năng tương ứng im lặng tắt, không crash; giá trị integer vắng đọc là 0, string vắng đọc là rỗng):

| Resource | Bắt buộc khi | Nội dung |
|---|---|---|
| `admob_*` (`admob_banner`, `admob_full`, `admob_gift`, `admob_native`, `admob_ntcl`, `admob_ntfull`, `admob_openad`, `admob_rwi`, `admob_rect`) | dùng định dạng đó | chuỗi `placement,id;id2` (mẫu bên dưới); định dạng không khai thì không có ad unit |
| AdMob app id (`com.google.android.gms.ads.APPLICATION_ID`) | luôn (mục 7) | meta-data manifest |
| `@integer/ads_admob_firebase_linked` | luôn nên khai | `1` **chỉ khi đã xác nhận ở console** AdMob ↔ Firebase được liên kết (AdMob console, ứng dụng, cài đặt liên kết Firebase; hoặc GA4, Monetization, Publisher ads có doanh thu AdMob): SDK bỏ `ad_impression` trùng của AdMob. Dòng log `origin=am,name=ad_impression(_ai)` (tag `FA-SVC`) chỉ chứng GMA đang ghi `_ai` vào Firebase Analytics trên máy, **không chứng** liên kết console; đặt 1 khi chưa liên kết thì doanh thu AdMob **mất hẳn** khỏi Firebase (`AdRevenueRouting.kt:21-23`). Vắng = 0 |
| `@integer/ads_cf_*` (`ads_cf_open_type`, `ads_cf_open_first_type`, `ads_cf_open_img`, `ads_cf_open_first_img`, `ads_cf_open_full2`, `ads_cf_open_show_at`, `ads_cf_bn_step`, `ads_cf_full_step`, `ads_cf_gift_step`, `ads_cf_native_step`, và từ 2.4.2 `ads_cf_seg_cadence_guardrail_seconds`, và từ 2.4.3 `ads_cf_admob_init_wait_ms`, và từ 2.4.4 `ads_cf_unit_gate_off`, `ads_cf_native_media_ratio`, và từ 2.5.0 `ads_cf_unit_gate_prio_off`, `ads_cf_open_gap_own_only`) | muốn đổi mặc định app open/bước waterfall; `ads_cf_seg_cadence_guardrail_seconds` = mặc định cục bộ của guardrail segment (giây; SC đặt 60; không đặt = 0 = tắt; RC `seg_cadence_guardrail_seconds` thắng); `ads_cf_admob_init_wait_ms` (tuỳ chọn, chỉ GMA Legacy) = thả load AdMob đang giữ N ms (1–8000) sau khi `initialize` của GMA đã được gọi; vắng/0 = chờ callback khởi tạo như cũ; `ads_cf_unit_gate_off` (từ 2.4.4) = 1 tắt cổng một request mỗi ad unit và trả hành vi 2.4.3, vắng/0 = bật (khoá của cổng là từng ad unit id thật đang xin, nên thang sàn eCPM `id_cao;id_all` trong `cf_ids_placement` / `ads_ids` vẫn chuyển sang id kế ngay khi id trước lỗi; chỉ lỗi nạp thật mới tính backoff 2–16 s, timeout không tính; khi mọi id của lượt đang backoff, cùng slot và placement thất bại ngay với 0 request (qua AdsHelper: `list empty`; nên chờ ít nhất 2 s trước khi retry), slot khác chờ tối đa 10 s); `ads_cf_native_media_ratio` (từ 2.4.4) = tỉ lệ media native xin GMA: vắng/0 = ANY, 1 = theo hướng màn hình như 2.4.3, 2 = LANDSCAPE, 3 = PORTRAIT; áp Nt, NtCl, Full2, cả Next-Gen | mặc định mã khi vắng; Remote Config ghi đè |
| `@integer/ads_cf_unit_gate_prio_off`, `@integer/ads_cf_open_gap_own_only` (từ 2.5.0) | muốn lùi hai mặc định mới | `ads_cf_unit_gate_prio_off` = 1: cổng unit không xếp mức SHOW > APP > PRELOAD và không nhường lượt chưa gửi id (FIFO như 2.4.4; đọc một lần mỗi process, `UnitGate.kt:62`); `ads_cf_open_gap_own_only` = 1: open ad chỉ tính khoảng `openAdDeltaTime` từ open ad trước (như 2.4.4), vắng/0 = tính từ mọi quảng cáo toàn màn hình (đọc mỗi lần quyết, `AdsFrequencyGate.kt:119`); RC `ads_cf_overrides` đặt được cả hai (mục 10) |
| `@string/ads_cf_native_no_reload` (từ 2.5.0, tuỳ chọn) | muốn đổi danh sách native không nạp lại khi `showNative` chưa có quảng cáo | CSV tên placement **có tiền tố `nt_`**, so khớp nguyên tên (ví dụ `nt_language,nt_intro`). Từ 2.5.1 mục thiếu `nt_` được chuẩn hoá thành `nt_<mục>` (như `showNative`), mỗi mục một dòng `logE` mỗi process (`language` ⇒ `nt_language`); trước 2.5.1 mục đó không khớp và không log. Lỗi đọc resource ⇒ một dòng `logE` và dùng 3 placement cũ (`NativeController.kt:243-253`). **Vắng = 3 placement cũ** (`nt_language,nt_language_2,nt_intro`); **có mặt (kể cả rỗng) = đúng danh sách đó** (rỗng: mọi native đều nạp lại). Đọc một lần mỗi process (`NativeController.kt:243-253`) |
| `@integer/ads_cf_placement_event` (từ 2.5.1, tuỳ chọn) | app đã liên kết AdMob ↔ Firebase (`ads_admob_firebase_linked` = 1) và muốn doanh thu AdMob theo placement trên Firebase | `1` bật event `ad_placement_impression` (xem mục 7); vắng/khác 1/đọc lỗi = tắt (lỗi đọc có một dòng `logE`). **Chỉ đặt bằng resource; Remote Config `ads_cf_overrides` không đặt được** (allowlist vẫn 4 tên, `AdsCfOverrides.kt:31-36`). Đọc một lần ở `initPlugin` (`AppPluginManager.kt:40`); chỉ có tác dụng khi `ads_admob_firebase_linked` = 1 |
| `flagTest`, `admob_test_device`, `fb_test_device`, `max_test_device` | bản dev/QA | `flagTest` = `1` bật chế độ test; các `*_test_device` là id băm của thiết bị test (phân tách bằng dấu phẩy) |
| `facebook_app_id` | app có `facebook-core` (Meta) | id Meta thật; còn là placeholder thì SDK không khởi tạo Meta |
| `af_app_id` | dùng AppsFlyer | khoá dev AppsFlyer |
| `admax_*`, `admax_devKey`, `adfb_*` | dùng AppLovin MAX / Meta Audience Network | id ad unit và khoá SDK của mạng đó |

Danh sách này khớp `src/main/res/raw/appplugin_keep.xml` (checker đối chiếu từng tên) và README mục 4.

**Layout native của app và `ad_media` (từ 2.5.0):** layout thiếu `@id/ad_media` hoặc khai khác kiểu `MediaView` không còn làm crash: không gắn media, và một dòng `logE` mỗi process nêu layout id và "GMA yêu cầu MediaView" (`NativeMediaRatio.kt:54`; Legacy `AdsMobPlNt.kt:305`, Next-Gen `AdsMobPlNt.kt:215`). Native không có media vẫn có thể bị GMA từ chối: khai `MediaView` trong layout. Binder Full2 dùng layout của SDK nên không bị ảnh hưởng.

**Backend đo lường: meta-data `DATABUCKETS_BASE_URL` và `DATABUCKETS_API_KEY`** (đọc ở `AppPluginBase.onCreate`, `<application>` của app). Đây là phần của hợp đồng app: không khai (hoặc để rỗng) thì SDK ghi `telemetry off: DATABUCKETS_BASE_URL rỗng, không gửi event` và **không gửi bất kỳ event nào** (quảng cáo, IAP, brain, consent) lên backend; quảng cáo, IAP và consent vẫn chạy. App không có backend thì bỏ qua có chủ đích, app có backend thì phải khai:

```xml
<meta-data android:name="DATABUCKETS_BASE_URL" android:value="https://<host backend>/" />
<meta-data android:name="DATABUCKETS_API_KEY" android:value="${databucketsApiKey}" />
```
Khoá API đặt qua `manifestPlaceholders` hoặc `-P`, không commit vào repo.

**Sink cục bộ cho bản debug (`tools/local_sink.sh`).** `DATABUCKETS_BASE_URL=http://127.0.0.1:8089/` (qua `adb reverse tcp:8089 tcp:8089`) cần thêm hai thứ ngoài URL: khoá API khớp sink (sink của SDK dùng `demo-utility-key`) và một network security config cho HTTP thường **chỉ tới `127.0.0.1`** (từ targetSdk 28 Android chặn HTTP thường). Đặt cả hai ở source set `debug` của app để bản phát hành không có ngoại lệ cleartext: `AndroidManifest.xml` của `debug` có `<application android:networkSecurityConfig="@xml/network_security_config_debug" />` và `res/xml/network_security_config_debug.xml` của `debug`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

**Id quảng cáo (`res/values/ads_ids.xml`), dạng `placement,id;id2`** (nhiều placement trong cùng một string ngăn bằng `#`). **Quy tắc tiền tố:** SDK tự thêm tiền tố vào tên placement app truyền vào, và khoá trong resource phải mang đúng tiền tố đó. Gọi `showBanner("home", …)` thì SDK tìm `bn_home`; khoá chỉ là `home` cho ra log `loadAd bn_home not pl config` và không có quảng cáo. Tiền tố do SDK thêm: `bn_`, `rect_`, `nt_`, `cl_`, `full_` (interstitial, app open, native full), `gift_` (rewarded); Full2 dùng `full_2_<tên>` (từ placement `full_` hoặc `gift_` cha). **`rwi_` không được SDK thêm:** rewarded interstitial phải truyền đủ tên `rwi_<tên>`; mọi tên rewarded khác thành `gift_<tên>` (`showRewarded("home", …)` tìm `gift_home`). Placement không có khoá riêng rơi về slot mặc định `*_default` (`bn_default`, `full_default`, `gift_default`…), dùng chung một ad unit; muốn unit riêng cho một placement thì tạo id riêng trong AdMob, khai khoá có tiền tố (`full_<placement>`) trong string và RC `cf_ids_placement`. Tên placement: nên chỉ chữ, số và `_` (`[A-Za-z0-9_]`) và ≤ 20 ký tự: đó là luật đặt tên event của Firebase (tên event `aad_*` tối đa 40 ký tự); SDK không kiểm hai điều này, chỉ cắt phần placement khi tên event dài hơn.

| Định dạng | Resource | Tiền tố khoá | Gọi `"home"` tìm | Hàm |
|---|---|---|---|---|
| Banner | `admob_banner` | `bn_` | `bn_home` | `showBanner` |
| Rect (MREC) | `admob_rect` | `rect_` | `rect_home` | `showRect` |
| Native | `admob_native` | `nt_` | `nt_home` | `showNative` |
| Native collapsible | `admob_ntcl` | `cl_` | `cl_home` | `showNtCl` |
| Interstitial, app open, native full | `admob_full`, `admob_openad`, `admob_ntfull` | `full_` | `full_home` | `showInterstitial`, `showOpenAd` |
| Rewarded | `admob_gift` | `gift_` | `gift_home` | `showRewarded` |
| Rewarded interstitial | `admob_rwi` | `rwi_` (gọi với đủ tiền tố) | `rwi_home` | `showRewarded("rwi_home", …)` |

Mẫu chạy được với id thử của Google (id thử công khai của Google, an toàn để commit):

```xml
<resources>
    <string name="flagTest">1</string>
    <string name="admob_banner">bn_home,ca-app-pub-3940256099942544/6300978111</string>
    <string name="admob_rect">rect_home,ca-app-pub-3940256099942544/6300978111</string>
    <string name="admob_full">full_home,ca-app-pub-3940256099942544/1033173712</string>
    <string name="admob_gift">gift_home,ca-app-pub-3940256099942544/5224354917</string>
    <string name="admob_native">nt_home,ca-app-pub-3940256099942544/2247696110</string>
    <string name="admob_openad">full_splash,ca-app-pub-3940256099942544/9257395921</string>
</resources>
```

AdMob app id thử (cho `com.google.android.gms.ads.APPLICATION_ID`, mục 7): `ca-app-pub-3940256099942544~3347511713`. App thật thay bằng id thật, bỏ `admob_test_device` và đặt `flagTest` = `0` (cả `1` lẫn `90351` bật chế độ test).

**Xung đột backup-rules (bẫy gặp ở lần tích hợp đầu; xảy ra với mọi app dùng `appplugin`).** AppsFlyer (`com.appsflyer:af-android-sdk:6.16.2`) là phụ thuộc runtime của artifact lõi `appplugin`, nên app không gọi AppsFlyer lần nào vẫn nhận manifest của nó, và manifest đó khai `android:dataExtractionRules` và `android:fullBackupContent`. App cũng khai các thuộc tính này (template Android Studio mặc định có) nên manifest merger dừng:

```
Attribute application@dataExtractionRules value=(@xml/data_extraction_rules) from AndroidManifest.xml
is also present at [com.appsflyer:af-android-sdk:6.16.2] AndroidManifest.xml value=(@xml/appsflyer_data_extraction_rules).
Suggestion: add 'tools:replace="android:dataExtractionRules"' to <application> element
```
(và dòng tương tự cho `fullBackupContent`). Khai `xmlns:tools="http://schemas.android.com/tools"` ở `<manifest>` (nếu chưa có) và sửa `<application>` của app:

```xml
<application
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    tools:replace="android:dataExtractionRules,android:fullBackupContent" ...>
```

Rồi gộp luật của AppsFlyer vào `backup_rules.xml` (`fullBackupContent`) và `data_extraction_rules.xml` của app, để không mất ý định của AppsFlyer. Nội dung trong AAR 6.16.2 (`res/xml/appsflyer_backup_rules.xml`, `res/xml/appsflyer_data_extraction_rules.xml`): loại khỏi sao lưu `<exclude domain="sharedpref" path="appsflyer-data"/>`, `<exclude domain="sharedpref" path="appsflyer-purchase-data"/>` và `<exclude domain="database" path="afpurchases.db"/>`; trong `data_extraction_rules.xml` đặt cả ba dòng đó dưới `<cloud-backup>` và `<device-transfer>`. Tương tự, SDK khai `google_analytics_default_allow_*` và các cờ Meta `com.facebook.sdk.*` mặc định tắt cho tới sau consent; app muốn khác phải `tools:replace` có chủ đích.

**Consent Mode v2 từ chối mặc định và FCM auto-init tắt (đổi hành vi so với fork).** SDK merge vào app bốn cờ `google_analytics_default_allow_analytics_storage`, `google_analytics_default_allow_ad_storage`, `google_analytics_default_allow_ad_user_data`, `google_analytics_default_allow_ad_personalization_signals` = `false` và `firebase_messaging_auto_init_enabled` = `false`. Hệ quả cho Analytics và push **của chính app**: Analytics chạy ở trạng thái từ chối cho tới khi SDK áp tầng consent sau UMP (và mãi mãi với tầng `DENIED`/`UNKNOWN`); FCM chỉ mở cho tầng `PARTIAL`/`GRANTED` hoặc người dùng ngoài GDPR chưa `DENIED` (`FcmConsent`), `DENIED` không bao giờ có token và rút consent thì xoá token, nên người dùng EEA từ chối không nhận push của app. Muốn khác thì `tools:replace` có chủ đích và tự chịu trách nhiệm pháp lý.

Property `android.adservices.AD_SERVICES_CONFIG` do AdMob và Firebase Analytics cùng khai: giữ bản của SDK bằng `tools:replace="android:resource"` và `@xml/appplugin_gma_ad_services_config` như `app/src/main/AndroidManifest.xml`.

## 7. Firebase, AdMob, AppsFlyer

**Firebase là bắt buộc** (Remote Config, Analytics, Crashlytics, Messaging là phụ thuộc của SDK):

1. Console Firebase → tạo (hoặc dùng) project → thêm app Android với đúng `applicationId`; thêm **một app cho mỗi `applicationId`** (gồm `.debug`).
2. Tải `google-services.json`, đặt ở `app/google-services.json`. Áp hai plugin với đúng phiên bản SDK đã kiểm (khai ở `settings.gradle.kts` của app trong `pluginManagement.plugins`, hoặc `plugins { … apply false }` ở `build.gradle.kts` gốc), rồi áp trong `app/build.gradle.kts` (app mẫu chỉ áp khi file tồn tại):

```kotlin
// settings.gradle.kts
pluginManagement {
    plugins {
        id("com.google.gms.google-services") version "4.4.2"
        id("com.google.firebase.crashlytics") version "3.0.3"
    }
}
// app/build.gradle.kts
plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
```

Không thêm plugin Kotlin riêng khi app ở AGP 9.2.1 trở lên (Kotlin có sẵn trong AGP); app còn ở AGP 8.x phải tự áp `org.jetbrains.kotlin.android` ≥ 2.2 (cấu hình này SDK chưa kiểm).
3. Bật Analytics; tạo các khoá Remote Config ở mục 10 khi cần ghi đè mặc định (không bắt buộc: khoá vắng thì SDK dùng mặc định trong mã).
4. `google-services.json` phải áp cho **mọi build type, kể cả `.debug`**: nếu app (hoặc template) chỉ áp plugin `com.google.gms.google-services` cho release, debug chạy với `FirebaseApp` chưa khởi tạo.
5. Hành vi khi cấu hình Firebase sai (đã đo trên emulator, bản debug, không crash trong cả hai ca): (a) thiếu plugin `google-services` (`FirebaseApp` không khởi tạo): SDK ghi 4 dòng lỗi (tag Crashlytics `appplugin_version`, giữ auto-init FCM, lấy config Firebase) và tiếp tục chạy, quảng cáo vẫn hoạt động; (b) có plugin nhưng `google-services.json` khoá API giả: `FirebaseApp` khởi tạo bình thường, chỉ một dòng lỗi `Fir Fetching FCM registration token failed: Please set a valid API key`, quảng cáo vẫn hoạt động. Remote Config và Crashlytics không có dữ liệu thật trong cả hai ca.

**AdMob, hai nơi:** (a) manifest: `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-…~…" />` (thiếu thì Google Mobile Ads sập lúc khởi tạo; id thử ở mục 6); (b) id ad unit trong string resource `admob_*` (mục 6). Id thử dùng khi `flagTest` bật.

**Doanh thu và placement trên Firebase (từ 2.5.1):** event `ad_impression` do SDK ghi (mọi mạng, trừ AdMob khi `ads_admob_firebase_linked` = 1) mang thêm param `ad_placement` = placement đang hiện (placement gọi show, lùi về slot khi rỗng; cắt ở 100 ký tự; không đặt khi cả hai rỗng) (`PaidEvent.kt:81,113`; nextgen `:104,136`). Muốn báo cáo eCPM theo placement: đăng ký custom dimension `ad_placement` (phạm vi event) trong GA4. App khai `ads_admob_firebase_linked` = 1 thì SDK **không** ghi `ad_impression` cho AdMob; doanh thu AdMob trên GA4 khi đó chỉ còn ở `_ai` do GMA ghi (không mang placement). Muốn có placement cho AdMob trong trường hợp đó: đặt `@integer/ads_cf_placement_event` = 1 (mục 6, **chỉ resource, RC không đặt được**) ⇒ SDK ghi event `ad_placement_impression` với `ad_placement`, `ad_unit_name`, `ad_format`, `ad_source`, `ad_platform`, `imp_id`, `ad_value`, `currency`, **không có `value`** (GA4 không cộng doanh thu, nên không đếm đôi với `_ai`); đăng ký custom metric `ad_value` và custom dimension `ad_placement` (`PlacementPaidEvent.kt:35-44`). Event chỉ ghi khi `ad_impression` của SDK bị chặn (AdMob và `ads_admob_firebase_linked` = 1); MAX luôn ra `ad_impression`.

**Chỉ đặt `ads_admob_firebase_linked` = 1 khi console xác nhận liên kết.** Dòng `origin=am,name=ad_impression(_ai)` chỉ chứng GMA ghi `_ai`, không chứng liên kết AdMob ↔ Firebase. Khi đã xác nhận: đặt cả `ads_admob_firebase_linked` = 1 và `ads_cf_placement_event` = 1 cùng lúc, đăng ký `ad_placement` và `ad_value` ở GA4. Chưa liên kết: giữ 0 (hoặc liên kết trước rồi mới đặt cờ).

**AppsFlyer:** khoá dev ở string `af_app_id`; không khai thì AppsFlyer không init (SDK vẫn chạy). Dùng khoá giả cho bản debug như `app/src/main/res/values/af_demo.xml`.

## 8. Consent

SDK khởi tạo mạng quảng cáo chỉ sau khi UMP cho phép, và tự canh việc đó. Hai gói cùng có lớp `ConsentManager`: dùng **`com.app.plugin.consent.ConsentManager`** (object, đây là lớp thật); `com.app.plugin.ads.consent.ConsentManager` là lớp chuyển tiếp `@Deprecated` cho fork cũ: vẫn chạy suốt 2.x và **gỡ ở 3.0** (thông điệp `@Deprecated` ghi "2.0" trước 2.3.0 là sai). Di trú: đổi import sang `com.app.plugin.consent.ConsentManager` và bỏ `.instance`; `showPrivacyOptions(activity) { _ -> }` (callback nhận `ConsentTier`, lớp cũ nhận `() -> Unit`); `gatherConsent(activity) { … }` và `isPrivacyOptionsRequired(context)` cùng dạng, không đổi. Thứ tự (mẫu trong repo SDK: `app/src/main/java/com/app/plugin/demo/MainActivity.kt`, `initAppPlugin`):

```kotlin
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.consent.ConsentManager

// Activity đầu (onCreate)
AdsMobMy.instance.startSdkInit(applicationContext)           // 1: luôn gọi, không điều kiện
ConsentManager.gatherConsent(this) { /* 2: form xong, hoặc không cần form */ }
```

1. Trong Activity đầu: `AdsMobMy.instance.startSdkInit(applicationContext)`. **Gọi vô điều kiện, đừng đặt sau cổng consent của app** (ví dụ `if (ConsentManager.canRequestAds())` hay logic tương tự của template): SDK tự hoãn khởi tạo mạng tới khi UMP cho phép (đã đồng ý ở phiên trước thì chạy ngay, chưa thì chờ form) và tự khởi tạo khi câu trả lời về. Cổng của app đặt trước `startSdkInit` làm nó không bao giờ được gọi, nên mạng quảng cáo không khởi tạo và không có quảng cáo. (`ConsentManager.canRequestAds()` trả `true` khi UMP chưa khởi tạo, nên không phải tín hiệu "đã hỏi xong".) Nếu app gọi `startSdkInit` bên trong callback của `gatherConsent` (như Invoice Maker): vẫn được gọi và vẫn chạy, nhưng mạng quảng cáo chỉ bắt đầu khởi tạo sau khi UMP xong thay vì song song với UMP, nên quảng cáo đầu tiên (`full_splash`) có thể trễ hoặc hụt ở lần mở đầu; khuyến nghị giữ "gọi trước, vô điều kiện".
2. `ConsentManager.gatherConsent(activity: Activity, onDone: () -> Unit)`: cập nhật thông tin UMP, hiện form nếu cần, `onDone` được gọi đúng một lần trên main thread (kể cả lỗi hay hết giờ). Muốn chỉ cập nhật thông tin, không hiện form: `ConsentManager.updateConsentInfo(activity: Activity, onDone: () -> Unit)`. Biến thể trả tầng: `ConsentManager.request(activity: Activity, onDone: (ConsentTier) -> Unit)` (`com.app.plugin.consent.ConsentTier`).
3. App có `facebook-core`: gọi `AppPluginBase.initMetaSdkAfterConsent()` sau câu trả lời.
4. Nút "Quyền riêng tư" trong cài đặt: kiểm `ConsentManager.isPrivacyOptionsRequired(context)` rồi `ConsentManager.showPrivacyOptions(activity: Activity, onDone: (ConsentTier) -> Unit)`.
5. **Kiểm thử vùng EEA** (chỉ khi dev/QA):
   - `ConsentManager.debugForceEea = true` (đặt trước `gatherConsent`; là thuộc tính Kotlin `var` của object) **chưa đủ**. UMP chỉ giả lập EEA trên thiết bị đã được khai là thiết bị test: nếu không thì ra `NOT_REQUIRED` (không form) dù cờ bật.
   - Lấy mã băm của thiết bị: chạy app một lần, lọc logcat `adb logcat | grep -i "addTestDeviceHashedId"`; UMP in dòng dạng `Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("<MÃ>")`. Điền `<MÃ>` vào string `admob_test_device` (nhiều mã ngăn bằng dấu phẩy), **và** đặt `flagTest` = `1` (SDK chỉ đọc `admob_test_device` khi `flagTest` bật). Hoặc gán trong mã `ConsentManager.debugTestDeviceHashedIds = listOf("<MÃ>")`.
   - Lúc đó form hiện (nút "Consent", "Do not consent", "Manage options"; tiêu đề thử "Publisher Test Ads"). Chọn "Consent" thì log `consent tier=GRANTED (ump=OBTAINED)` rồi mạng khởi tạo.
   - Cờ `--ez force_eea true` chỉ có ở Activity của app demo trong repo SDK (nó đọc extra rồi gán `debugForceEea`); app của bạn tự đọc extra của mình nếu muốn.
   - Đừng bật `debugForceEea`, `debugTestDeviceHashedIds` hay `flagTest` ở bản phát hành.
   - **Log thoáng qua bình thường trên emulator chậm** (không phải lỗi): `ConsentManager: info update timed out, releasing gate` (sau 5 giây chờ mạng UMP), `ConsentManager: no form on screen yet, releasing gate`, dòng `Web view timed out` của thư viện UMP, và tầng `DENIED` thoáng qua trước khi form hiện (có thể tới ~10 giây). Form vẫn hiện và chọn "Consent" vẫn cho `GRANTED`.

Chưa trả lời, từ chối, hay "không cần" cho ra các tầng khác nhau (`ConsentState.tier()`): quảng cáo, AppsFlyer, push và gửi event theo tầng. App không dùng UMP tự `ConsentState.setTier` rồi `ConsentManager.updateConsentInfo`; app không kế thừa `AppPluginBase` gọi `ConsentState.installStore` + `ConsentState.restore` (xem hàng 1.4.0 ở mục 13).

## 9. API theo thứ tự gọi

Mọi tên dưới đây có trong `api/appplugin.api` (`api/appplugin-game.api` cho phần game). **Gói (import) của các lớp được nhắc:**

| Lớp | Gói |
|---|---|
| `MonetizationSdk` | `com.app.plugin` |
| `AppPluginBase` | `com.app.plugin.app` |
| `AdsHelper`, `AdsShowCb`, `AdsLoadCb`, `AdsGiftShowCb`, `RemoveAdsMask`, `PlacementDefault` | `com.app.plugin.ads` |
| `AdsMobMy` | `com.app.plugin.ads.adsmob` |
| `ConsentManager` (dùng bản này), `ConsentState`, `ConsentTier` | `com.app.plugin.consent` |
| `ConsentManager` (chuyển tiếp, `@Deprecated`) | `com.app.plugin.ads.consent` |
| `IapHelper`, `IapResult` | `com.app.plugin.iap` |
| `PaywallManager` | `com.app.plugin.paywall` |
| `LogEventServer` | `com.app.plugin.logevent` |
| `UserSegmentEngine`, `UserSegment`, `SegmentEvent`, `RevenueTier`, `RevenueTierEvaluator`, `AdCadenceGate`, `SegmentCadenceConfig` | `com.app.plugin.segment` |

Callback: `AdsLoadCb` có hai hàm bắt buộc `onLoaded(placement: String, originPl: String, adId: String)` và `onLoadFailed(placement: String, originPl: String, adId: String, error: String)`; `AdsShowCb` có toàn hàm mặc định (`onAdShowFail`, `onAdShowed(…, flag: Int)`, `onAdImpression`, `onAdClick`, `onAdDismissed`, đều `(placement: String?, originPl: String?, adId: String?, …)`), nên `object : AdsShowCb {}` là hợp lệ; `AdsGiftShowCb : AdsShowCb` thêm `onAdRewarded(placement: String?, originPl: String?, adId: String?)`. `isDeltaTime = true` (mặc định) áp luật khoảng cách thời gian, cooldown màn hình và brain; `false` nghĩa là lượt này do app quyết định (splash, intro). **Từ 2.4.2** `showInterstitial`/`isInterstitialReadyToShow` có thêm tham số `fixedMoment: Boolean` (không mặc định): `true` = thời điểm cố định, hiện mọi lần trừ khi bị chặn cứng (xem bước 5).

1. **Khởi tạo:** `AppPluginBase.onCreate` (qua `super.onCreate()`), `MonetizationSdk.configure(context) { … }` → `report.describe()`.
2. **Consent + mạng:** mục 8 (`AdsMobMy.instance.startSdkInit`, `ConsentManager.gatherConsent`).
3. **Placement đầu và app open:** `AdsHelper.instance.loadOpenAdForThisTurn("full_splash")` rồi `AdsHelper.instance.showOpenAd(placement, cb)` / `showFirstOpen(cb)` (không show được ngay lần đầu nếu chưa preload). **App open khi người dùng quay lại app (resume)** cần app gọi `AdsHelper.instance.setFlag4ShowResume(true)` một lần sau consent (mặc định `false`: không gọi thì `showOpenAd` của placement không phải `full_splash` bị chặn với lý do `open_flag_resume` dù đã khai khoá `full_resume`); `getFlag4ShowResume()` đọc lại. **SDK tự hiện `full_resume` (từ 2.5.0 tắt được):** mỗi lần vào foreground (trừ lần đầu của process) SDK hẹn `showOpenAd("full_resume")` 1 s sau; cờ `setFlag4ShowResume` tắt thì lượt này bị chặn `open_flag_resume`; `AppResumeIF` chạy ở mọi resume; `AdsHelper.instance.setAutoShowOpenAdOnForeground(false)` chỉ tắt lượt tự gọi này (mục 4). **`full_resume` cần khoá riêng** trong `admob_openad`: placement không có khoá thì **không** rơi về `full_splash`; ví dụ `<string name="admob_openad">full_splash,ca-app-pub-xxx/aaa#full_resume,ca-app-pub-xxx/bbb</string>` (placement ngăn bằng `#`; `;` là nhiều id của **một** placement, mục 6). **Khoảng cách (từ 2.5.0):** open ad không phải `full_splash` không hiện trong `openAdDeltaTime` (mặc định 15 s) sau interstitial, Full2, rewarded và rewarded interstitial, không chỉ sau open ad trước; lùi bằng `ads_cf_open_gap_own_only` = 1 (mục 6, 10).
4. **Banner / rect / native:** `AdsHelper.instance.showBanner(placement: String, viewParent: ViewGroup, isCollapse: Boolean, cbLoad: AdsLoadCb?, cbShow: AdsShowCb?, isDeltaTime: Boolean = true)` (`isCollapse = true` thử biến thể native thu gọn của banner (cần thêm khoá `cl_<tên>` trong `admob_ntcl`), `false` là banner thường; placement theo quy tắc tiền tố mục 6: `"home"` → khoá `bn_home`); `hideBanner()`; `showRect(placement: String, viewParent: ViewGroup?, adloadCb: AdsLoadCb? = null)`, `hideRect(container, …)`; `showNative(placement: String, viewParent: ViewGroup?, cbShow: AdsShowCb?, isLoadNShow: Boolean = false)`; `showNtCl(placement: String, cbShow: AdsShowCb?)`. Banner/rect/native đã gắn vào container Activity cũ được gỡ khỏi cha cũ trước khi gắn (từ 1.3.1).
5. **Toàn màn hình:** `AdsHelper.instance.loadInterstitialForThisTurn(placement: String, flagImg: Int, loadCb: AdsLoadCb? = null, isLoadFull2: Boolean = true)` rồi `AdsHelper.instance.showInterstitial(placement: String, flagImage: Int, cbShow: AdsShowCb, isDeltaTime: Boolean = true): Boolean`. **Thời điểm cố định (từ 2.4.2):** overload `showInterstitial(placement, flagImage, cbShow, isDeltaTime = true, fixedMoment = true)` bỏ qua guardrail segment, luật phiên/khoảng cách, cooldown N màn và brain (ví dụ mốc cố định như xong onboarding, xong dọn dẹp); vẫn chặn khi đang có quảng cáo hiện, kill/giấy phép/`RemoveAdsMask`, `flagShow < 0`; lượt `fixedMoment` của `full_intro_3` được nối Full2. `isInterstitialReadyToShow(placement, flagImage, isDeltaTime, fixedMoment)` trả đúng kết luận về các cổng của `showInterstitial` cùng tham số (trừ brain), cộng điều kiện của riêng nó: placement đã nạp và host đã resume. `flagImg`/`flagImage` chọn các nguồn xét theo thứ tự: `0` (hoặc giá trị ngoài 1–5) = waterfall interstitial thường, nên dùng; `1` = chỉ native full; `2` = AdMob interstitial "low"; `3` = native full rồi low; `4` = low rồi native full; `5` = Meta native full trước. **Giá trị phải giống nhau ở lần load và lần show.** `showInterstitial` trả `false` khi không show được (chưa nạp xong, bị chặn bởi cooldown/guardrail/brain/gỡ quảng cáo; không còn `onAdShowFail` giữa waterfall). **SDK theo mẫu show-then-load:** `showInterstitial` khi placement chưa nạp trả `false`, ghi `ad_show_fail` (`show_not_loaded`) và chỉ **bắt đầu** nạp cho lần sau, không hiện gì lượt này. Placement không mặc định (có khoá riêng, bước 14) không được SDK nạp sẵn: app phải gọi `loadInterstitialForThisTurn(placement, flagImg)` trước thời điểm show (ví dụ khi mở màn trước điểm show), nếu không lượt đầu tiên của mỗi process mất. **Gọi `showInterstitial` ngay sau `loadInterstitialForThisTurn` luôn ra `false`** vì việc nạp mất vài giây: nạp sớm (đầu màn hình, hoặc ngay sau lần show trước) và show ở điểm chuyển màn; kiểm `AdsHelper.instance.isInterstitialReadyToShow(placement: String, flagImage: Int = 0)` (từ 2.4.2 tính cả guardrail segment như cổng của `showInterstitial`) hoặc đợi `AdsLoadCb.onLoaded` của lần load rồi mới show. Placement theo quy tắc tiền tố mục 6 (`"home"` → `full_home`); `showFull2(placement, cbShow)`; rewarded: `loadRewardedForThisTurn(placement, cb)` rồi `showRewarded(placement, AdsGiftShowCb)`; rewarded interstitial: placement `rwi_<x>` qua cùng hai hàm rewarded.
6. **Gỡ quảng cáo theo mask:** `RemoveAdsMask.FLAG_BANNER`, `FLAG_RECT`, `FLAG_NATIVE`, `FLAG_NATIVE_COLLAPSIBLE`, `FLAG_FULLSCREEN`, `FLAG_REWARDED`, tổng `RemoveAdsMask.ALL`, `RemoveAdsMask.INTERRUPTIVE`, `RemoveAdsMask.NONE`; `AdsHelper.instance.setRemoveAdsMask(mask)`, `isRemoveAds(flag)`. Gắn mask với sản phẩm: `removeAdsMasks` trong `configure`. Công tắc cũ `setRemoveAds(1)` = `ALL`.
7. **Cooldown giữa màn hình:** `AdsHelper.instance.setPostAdScreenCooldown(n)` (mặc định 0 = tắt) và gọi `AdsHelper.instance.recordScreenChange()` mỗi lần người dùng đổi màn hình (đổi tab/route). `setPostAdScreenCooldown` gọi được bất cứ lúc nào sau `super.onCreate()` của `Application`, kể cả trước consent và trước `startSdkInit`: hàm chỉ lưu giá trị. Khoá `screens_before_interstitial` của `ads_policy_android` thắng giá trị app (áp sau khi Remote Config về).
8. **IAP:** sản phẩm khai ở `configure`; `IapHelper.isReady()`; `IapHelper.queryProducts { list -> … }`; `IapHelper.purchase(activity, productId) { result -> … }` (lượt chồng trả `Failed(-2, …)`); `IapHelper.restorePurchases { ids -> … }`; `IapHelper.purchaseListener`. Quyền lợi chỉ có hiệu lực sau lượt kiểm chữ ký nền.
9. **Paywall:** `PaywallManager.paywallFor(placement, playerId)` (null = không hiện: holdout hoặc chặn) rồi hiện UI của app rồi `PaywallManager.onPaywallShown(paywall)`. Cấu hình: khoá `paywall_config_android` (mục 10).
10. **Brain:** brain được hỏi tự động qua `isDeltaTime` và trong `PaywallManager.paywallFor` (hỏi trước khi chọn biến thể) khi `configure { brainEnabled = true }` (mở dần bằng `brainRolloutFraction`, quy trình bắt buộc ở `DOC/38_INTEGRATION_GUIDE.md`); 2.x không có hook app công khai cho brain (lớp brain là `internal`, không phải API công khai), app không gọi trực tiếp.
11. **Đo lường tự định nghĩa:** `LogEventServer.logEvent(name, json)`, `LogEventServer.logButtonClick(…)`, `LogEventServer.logScreenPlay(…)`; mọi event quảng cáo, IAP và brain SDK tự phát, nhưng chỉ tới backend khi app đã khai meta-data `DATABUCKETS_BASE_URL` và `DATABUCKETS_API_KEY` (mục 6; thiếu = `telemetry off`). Người dùng nhận diện bằng `playerId` trong `configure`.
12. **Game (chỉ khi có `appplugin-game`):** `GameModule` tự đăng ký; app không gọi trực tiếp; khoá RC game ở mục 10.
13. **Segment (từ 2.2.0, không cần cấu hình):** SDK tự khởi động engine từ `initPlugin` và tự ghi `SESSION_START` (mỗi lần foreground), `REWARDED_AD_COMPLETED` và `AD_IMPRESSION` (chỉ cho quảng cáo toàn màn hình: interstitial, app open, rewarded, rewarded interstitial; không cho banner/native). App chỉ gọi, theo thứ tự:
    1. Đọc trạng thái: `UserSegmentEngine.instance.segment` và `UserSegmentEngine.instance.score` là `StateFlow` (thu, đừng đọc một lần lúc khởi động: `segment` = `COLD_START` và `geoTier` = 4 cho tới khi engine nạp xong, vài chục ms sau `initPlugin`; đọc sau khung hình đầu hoặc thu flow). `UserSegmentEngine.instance.geoTier`, `scorerSnapshot()`, `cadenceConfig()`, `revenueTierConfig()` đọc được khi cần.
    2. Báo sự kiện của app: `UserSegmentEngine.instance.recordEvent(event: SegmentEvent, payload: Map<String, Any>? = null)`. `FEATURE_USED` chỉ được tính khi payload có khoá `featureId` kiểu `String` (ví dụ `mapOf("featureId" to "export_pdf")`); `PAYWALL_SHOWN`, `PAYWALL_DISMISSED`, `PAYWALL_PLAN_TAPPED` và `PURCHASE_COMPLETED` không cần payload; `NON_PAYER_DISMISS` mang payload `rung` (Int) và không đổi điểm của scorer. Gọi lúc engine đang nạp: sự kiện được xếp hàng, giữ thời điểm gọi và phát lại đúng thứ tự (tác động hiện sau khi nạp); gọi trước `initPlugin` thì bị bỏ.
    3. Tầng doanh thu: `RevenueTierEvaluator.evaluate(snapshot, lifetimeSessionCount, lastSegmentEventMillis, nonPayerQuiesceUntilMillis, now, config)` trả `RevenueTier` (`lifetimeSessionCount` do app tự đếm; `snapshot` = `UserSegmentEngine.instance.scorerSnapshot()`, `lastSegmentEventMillis` = `UserSegmentEngine.instance.lastSegmentEventMillis`, `config` = `UserSegmentEngine.instance.revenueTierConfig()`); đặt user property bằng `UserSegmentEngine.instance.setRevenueTierUserProperty(tierName: String)` với `tier.name`, không phải enum (cùng quy tắc xếp hàng).
    4. Nhịp interstitial theo tab: `AdCadenceGate.shouldShowInterstitial(segment, geoTier, tabSwitchCount)` với `SegmentCadenceConfig` (mặc định hoặc `UserSegmentEngine.instance.cadenceConfig()`).
    5. QA: `UserSegmentEngine.instance.forceSegment(UserSegment.HIP)` (và `null` để bỏ ép).
    Khoá RC ở mục 10; user property `seg_segment`, `seg_score`, `seg_revenue_tier` không ghi khi consent `DENIED` hoặc `UNKNOWN` (`PARTIAL` và `GRANTED` thì ghi).
14. **Preload (từ 2.3.0, không cần gọi gì):** khi consent cho phép, kill switch không chặn và giấy phép `VALID`, SDK tự nạp sẵn một lần mỗi process (khi mạng quảng cáo sẵn sàng và Activity đầu đã đăng ký) các slot mặc định: interstitial `full_default` (kèm Full2 `full_2_default` khi Full2 bật; rewarded preload cũng kéo Full2 khi `full2FlagShow` ≥ 2), mọi placement app open đã khai, rewarded `gift_default` và rewarded interstitial `rwi_default` khi `admob_rwi` có khoá đó. Sau khi hiện, SDK nạp lại: interstitial `full_default` (không nạp lại gì khi quảng cáo vừa hiện là `full_splash`, `full_2_splash` hoặc `full_intro_3` có khoá riêng: slot mặc định chưa bị dùng; slot đó do app tự nạp nếu muốn hiện lại), Full2 `full_2_<placement cha>` (trừ `full_2_splash`), app open (trừ `full_splash`), rewarded và rewarded interstitial (đúng placement vừa hiện; rewarded từ 2.4.0, trước đó luôn `gift_default`). Hệ quả: `load*ForThisTurn` cho placement mặc định là vô hại nhưng **vẫn nên gọi trước điểm show**: lượt nạp sẵn chỉ chạy một lần mỗi process, không thử lại khi no-fill, và nạp interstitial với `flagImg 0` (app hiện với `flagImage` ≠ 0 không được lợi từ preload) (nếu slot đó đang có một lượt nạp, của SDK hay của app, SDK gắn callback của app vào lượt đang chạy; từ 2.4.0 mọi callback nhận `onLoaded`/`onLoadFailed` thật đúng một lần theo thứ tự gắn, trước đó callback thứ hai nhận `onLoadFailed` "loading"); **placement không mặc định do app nạp trước lần hiện đầu; interstitial không mặc định app còn phải nạp lại sau mỗi lần hiện** (bước 5; SDK nạp lại `full_default`, Full2, rewarded đúng placement vừa hiện từ 2.4.0, và rwi). Preload tôn trọng `RemoveAdsMask`, kill switch và giấy phép (mặt nạ gỡ quảng cáo hoặc định dạng bị tắt: bỏ slot, một dòng `preload: skip <định dạng> <placement> (<lý do>)` (app open chưa cấu hình: placement `-`); kill switch hoặc giấy phép không hợp lệ: preload không chạy, không có dòng nào); interstitial trong cửa sổ `fullTimeStart`/`fullSessionStart` (≥ 120 s) được hoãn tới khi cửa sổ cho phép nạp (1/3 cửa sổ). Chỉ khi preload thấy splash đang nạp ở một nhịp hỏi thì mới có `preload: splash done after <n> ms (settled)` (ngay khi splash hết nạp); splash đã xong từ trước lượt preload thì không được thấy nên preload vẫn chờ ân hạn 1,5 s rồi ghi `(none)`. Tắt hoặc thu hẹp bằng RC `ads_preload_android` (mục 10); RC không được lưu, nên nếu Remote Config về sau lúc preload chạy thì process đó dùng mặc định (bật). Khi app gọi `setFlag4ShowResume(true)`, app open resume (`full_resume`) được nạp sẵn và nạp lại sau mỗi lần dismiss nên có thể hiện ở mọi lần quay lại app đủ điều kiện (giới hạn bởi `openAdDeltaTime`/`ads_policy_android`): tần suất app open resume có thể cao hơn trước 2.3.0. Trong event `ad_request`, gần như chỉ lượt nạp do chính preload mở mới mang `trigger=preload` (nhãn tự hết sau `ad_fill` hoặc 60 s, nên một lượt app gọi trên cùng slot ngay sau preload không đạt có thể mang nhãn). **Nhường splash (từ 2.4.3):** khi RC `ads_preload_android.delay_ms` không đặt hoặc 0, lượt preload chờ trong lúc `full_splash` hoặc `nt_splash` (tên theo quy ước SC/fork) đang nạp; chưa thấy cái nào thì chờ tối đa 1,5 s (app không có splash, vd. Invoice Maker, nên bắt đầu preload chậm khoảng 1,5 s), trần 10 s rồi chạy như cũ; `delay_ms` > 0 thắng và giữ thời điểm 2.4.2. Log `preload: wait splash` và `preload: splash done after <n> ms (settled|cap|none)` (`<n>` đếm theo nhịp hỏi 250 ms, là cận dưới của thời gian thực).

## 10. Khoá Remote Config

Mỗi khoá là một chuỗi JSON trong Firebase Remote Config. Bảng đầy đủ (dạng, version, file đọc) ở `DOC/61_INTERNAL_SDK_THREE_APPS.md` mục G.3. SDK không đăng ký defaults XML: khoá rỗng hoặc vắng thì dùng mặc định trong mã. Tóm tắt:

| Khoá | Schema và version | Nhóm |
|---|---|---|
| `ads_policy_android` | `schema_version` 1; 7 trường v1 + khối tuỳ chọn `by_tier` (`T1`/`T2`/`VN`/`OTHER`); khối sai bị bỏ cả khối; `gma_load_app_context` mặc định tắt; `fullscreen_max_age_minutes` (1–240, mặc định 60) từ 2.4.3 áp cả native nạp sẵn | lõi |
| `ads_bidding_partners_android` | `{"schema_version":1,"partners":[…]}`; chỉ thu hẹp tập partner app đã khai | lõi |
| `ads_preload_android` | `{"schema_version":1,"enabled":true,"formats":["interstitial","app_open","rewarded","rewarded_interstitial"],"delay_ms":0}`; vắng = bật; `enabled:false` = 0 load; `formats` thu hẹp; `delay_ms` kẹp 0..60000; hỏng hoặc `schema_version` ≠ 1 = mặc định + 1 `logE`; áp khi RC fetch xong (không lưu) (từ 2.3.0) | lõi |
| `brain_policy_android` | `version` bắt buộc; `enabled:false` = kill; `experiment.rollout_fraction`; rỗng thì giữ policy đang chạy; bị bỏ qua suốt process khi policy từ `/policy` đã áp (mục 15) | lõi |
| `sdk_kill_android` | `{"ads":bool,"iap":bool,"logevent":bool,"below_version":"x.y.z"}`, không có `schema_version` | lõi |
| `logevent_control` | `version` 1; khác 1 thì dùng mặc định | lõi |
| `paywall_config_android` | `schema_version` vắng = v1, `2` = v2 (holdout mặc định 0); schema khác bị từ chối cả khối | lõi |
| `subscription_lifecycle_android` | `version` bắt buộc (vd `sub_lifecycle_v1`) | lõi |
| `ads_region_android`, `ads_region2_android`, `cf_ids_placement` | bản đồ JSON placement/id (từ 2.4.4: thang sàn eCPM `id_cao;id_all` vẫn chuyển sang `id_all` ngay khi `id_cao` lỗi, vì cổng một request mỗi unit khoá theo từng id; công tắc lùi `ads_cf_unit_gate_off`, mục 6) | lõi |
| `cf_ads_placement` | JSON `{"<placement>":{"flag":-1}}`: `flag < 0` tắt cả nạp lẫn hiện placement đó; khoá `k` áp cho `k` và bí danh `full_x` ↔ `x` (đúng tên thắng); khoá vắng ở lần RC sau = bật lại (kể cả RC rỗng); JSON hỏng = giữ trạng thái cũ + `logE`; `full_2_<x>` hoặc `full_2_default` `flag < 0` tắt Full2 sau placement `x`; đọc bằng `AdsHelper.instance.isPlacementEnabled(placement)` (từ 2.4.2, bảng chuyển ở mục 14); Công tắc đặt bằng cách gọi trực tiếp `AdsHelper.instance.parserAdsConfig(json)` (không qua RC/Firestore) **không bền**: lần fetch RC kế tiếp dựng lại trạng thái từ bản ghi theo nguồn và bật lại nó; app chỉ nên bật/tắt placement qua RC (`cf_ads_placement`) hoặc Firestore.  | lõi |
| `purchase_routing_android`, `battle_pass_android`, `recurring_grants_android`, `offerwall_config_android`, `crm_bridge_android` | `version` bắt buộc | game (chỉ khi có `appplugin-game`) |

**`ads_cf_overrides` (từ 2.5.0)** nằm **trong** chuỗi JSON `ads_policy_android` (cùng JSON, một khoá cấp trên cùng): một đối tượng `{"<tên>": <số nguyên>}` đè `@integer/<tên>` của app khi SDK đọc qua `AppUtil.getResourceInt`, để lùi hành vi bằng Remote Config mà không phát bản app. Allowlist cố định 4 tên (`AdsCfOverrides.kt:27-36`):

| Tên | Giá trị | Hiệu lực | Ý nghĩa |
|---|---|---|---|
| `ads_cf_unit_gate_off` | 0–1 | process sau lần RC fetch xong (hoặc ngay nếu RC về trước lượt load đầu); cổng chốt một lần mỗi process, `UnitGate.kt:63` | 1 = tắt cổng một request mỗi ad unit (như 2.4.3) |
| `ads_cf_unit_gate_prio_off` | 0–1 | như trên (`UnitGate.kt:62`) | 1 = FIFO, không mức/nhường (như 2.4.4) |
| `ads_cf_native_media_ratio` | 0–3 | ngay (đọc mỗi request native) | 0 = ANY; 1 = theo hướng màn hình (2.4.3); 2 = LANDSCAPE; 3 = PORTRAIT |
| `ads_cf_open_gap_own_only` | 0–1 | ngay (đọc mỗi lần quyết) | 1 = open ad chỉ tính khoảng từ open ad trước (như 2.4.4) |

Luật: **khoá RC có mặt thắng resource app (kể cả 0); bỏ khoá để về resource; máy offline giữ bản lưu cuối** (`restorePolicy` áp bản đã lưu lúc mở app, `AdsFrequencyGate.kt`). Tên ngoài allowlist, giá trị không phải số nguyên hay ngoài khoảng bị bỏ riêng tên đó kèm `logE` (resource giữ); `ads_cf_overrides` không phải đối tượng thì bỏ cả khoá; khoá nằm trong một tier của `by_tier` thì cả khối tier bị bỏ kèm `logE`. RC chỉ lùi về hành vi cũ hoặc chọn trong enum có sẵn (`ads_cf_native_media_ratio` 0–3), không bật được tính năng chưa có trong SDK đã cài. Khoá RC có mặt luôn thắng, nên đừng gửi 0 cho bất kỳ khoá nào app đã đặt 1 trong resource. JSON `ads_policy_android` hỏng hoặc `schema_version` lạ ⇒ bỏ mọi override (về resource), kể cả ở lần mở sau, vì bản hỏng vẫn được lưu. Mỗi lần áp khác rỗng có một dòng `ads cf overrides: <tên>=<giá trị>[, …]`. Nếu app đã đặt resource `ads_cf_unit_gate_off` = 1 thì đừng gửi khoá đó với giá trị 0 trong RC.

Khoá segment (SEG, 2.2.0): số nguyên (Long) hoặc CSV mã nước, **không** có JSON/`schema_version`; khoá vắng thì dùng mặc định; đọc trên luồng nền. Tên và mặc định y hệt fork Invoice Maker, **trừ guardrail**.

| Khoá | Mặc định | Ý nghĩa |
|---|---|---|
| `seg_cadence_guardrail_seconds` | **0** (tắt) | khoảng tối thiểu kể từ quảng cáo toàn màn hình gần nhất: interstitial, app open, Full2 và rewarded interstitial đều đóng dấu đồng hồ, nhưng chỉ `showInterstitial` và `showOpenAd` bị chặn (`showOpenAd("full_splash")` được **miễn** từ 2.4.2; `showInterstitial` với `fixedMoment = true` cũng bỏ qua); > 0 bật, bị chặn thì `ad_show_fail reason=seg_guardrail`. Mặc định cục bộ (từ 2.4.2): resource `ads_cf_seg_cadence_guardrail_seconds`; thứ tự RC > resource > 0. Fork: IM 180, SC 60 |
| `seg_weight_geo_tier1`, `seg_weight_geo_tier2` | 40, 20 | điểm theo tier nước |
| `seg_geo_tier1_countries`, `seg_geo_tier2_countries` | danh sách của SDK | CSV mã nước (chữ hoa) |
| `seg_weight_plan_tapped` | 20 | điểm khi chạm gói |
| `seg_weight_bounce`, `seg_bounce_window_millis` | -10, 2000 | điểm và cửa sổ (ms) của thoát nhanh |
| `seg_weight_dismiss_no_tap`, `seg_weight_dismiss_no_tap_cap` | -10, -30 | tắt paywall không chạm và trần |
| `seg_weight_feature_used`, `seg_weight_feature_used_cap` | 5, 25 | dùng tính năng và trần |
| `seg_weight_sessions_3to6`, `seg_weight_sessions_7plus` | 10, 15 | điểm theo số phiên |
| `seg_weight_rewarded_ad`, `seg_weight_rewarded_ad_cap` | 5, 15 | xem rewarded và trần |
| `seg_weight_ecpm_high`, `seg_weight_ecpm_low` | 10, -10 | điểm theo nhóm eCPM |
| `seg_ecpm_min_impressions`, `seg_ecpm_window_size` | 5, 10 | số impression tối thiểu và cỡ cửa sổ eCPM |
| `seg_ecpm_high_threshold_micros` | 500 000 | ngưỡng eCPM cao, micros **mỗi impression** |
| `seg_threshold_hip`, `seg_threshold_psp`, `seg_threshold_power_user` | 70, 40, 90 | ngưỡng điểm HIP, PSP, POWER_USER |
| `seg_cold_start_window_hours` | 48 | cửa sổ COLD_START (giờ) |
| `seg_churn_min_historical_sessions`, `seg_churn_gap_days` | 5, 14 | điều kiện CHURN_RISK |
| `seg_cadence_hip_tab_switch`, `seg_cadence_psp_tab_switch`, `seg_cadence_lia_tab_switch`, `seg_cadence_power_user_tab_switch`, `seg_cadence_churn_risk_tab_switch` | 10, 6, 4, 14, 4 | số lần đổi tab trước khi `AdCadenceGate` cho interstitial |
| `seg_revenue_starved_min_sessions`, `seg_revenue_productive_window_hours`, `seg_revenue_starved_dismiss_kill_switch`, `seg_revenue_starved_kill_switch_cooldown_days` | 10, 336, 3, 30 | ngưỡng tầng doanh thu (`RevenueTier`) |

## 11. R8, quy tắc consumer và lỗi thường gặp

SDK mang `consumer-rules.pro` (áp vào R8 của app, không cần sao chép). Tuỳ chọn: thêm `docs/integration/appplugin-strip-logs.pro` vào `proguardFiles` của release để bỏ chuỗi log `logD`/`logW` của SDK khỏi dex (không bật ở bản chạy cổng thiết bị của SDK, vì cổng đọc các dòng đó).

| Triệu chứng | Nguyên nhân | Sửa |
|---|---|---|
| Merge manifest dừng: `Attribute application@dataExtractionRules … also present at [com.appsflyer:af-android-sdk:6.16.2]` | AppsFlyer và app cùng khai backup-rules | `tools:replace="android:dataExtractionRules,android:fullBackupContent"` + gộp luật (mục 6) |
| Bản release R8 sập lúc khởi động ở `WorkDatabase_Impl` | R8 full mode bỏ constructor không tham số mà Room/WorkManager gọi bằng reflection | đã vá trong `consumer-rules.pro` (từ 1.3.1): dùng bản SDK mới; app tự có `RoomDatabase` hoặc `ListenableWorker` cũng được giữ `<init>` |
| Firebase không khởi tạo ở release, mất component | R8 bỏ `<init>()` của `ComponentRegistrar` | cùng rule trên (từ 1.3.1) |
| Mất ad unit trong APK có `shrinkResources` (0 ad unit) | SDK đọc id bằng `getIdentifier` | SDK mang `appplugin_keep.xml`; đừng đặt resource tên `appplugin_keep`; app có khoá app-contract mới thì thêm vào `tools:keep` của app |
| `NoClassDefFoundError: java.lang.ClassValue` lúc Crashlytics khởi tạo (R8, API < 34) | R8 outline cache `ClassValue` của kotlinx.serialization | đã vá trong `consumer-rules.pro` (1.6.1); `release_gate.sh` FAIL nếu mapping còn `ClassValue` |
| Banner/rect sập "The specified child already has a parent" sau khi Activity tạo lại | view còn nằm trong container cũ | đã vá (`BannerAttach`, từ 1.3.1); dùng bản mới |
| Activity bị giữ sau Full2 / rect (leak) | callback load giữ Activity | từ 1.6.0 SDK nhả callback khi huỷ Activity; cờ `gma_load_app_context` (trong `ads_policy_android`) mặc định **tắt**, chưa đo trên máy khi bật |
| Không quảng cáo, log `license: INVALID (<lý do>)` | mục 5 | bảng lý do ở mục 5 |
| Brain luôn ra `NO_ACTION`, lý do `license_PENDING` hoặc `license_INVALID:<lý do>` | chưa có verdict `VALID` (từ 2.2.0 brain chỉ quyết khi `VALID`) | mục 5: sửa token; `PENDING` mãi chỉ khi app không kế thừa `AppPluginBase` và chưa bao giờ khởi tạo mạng quảng cáo (mục 8) |
| Log `loadAd bn_home not pl config` (hoặc `full_…`, `nt_…`) và không có quảng cáo | khoá trong `ads_ids.xml` thiếu tiền tố (khoá `home` thay vì `bn_home`) | mục 6: khoá = tiền tố + tên app truyền vào |
| `showInterstitial` luôn trả `false` ngay sau `loadInterstitialForThisTurn` | quảng cáo chưa nạp xong (vài giây) | nạp sớm, show ở điểm chuyển màn; mục 9 bước 5 |
| Lượt đầu mỗi process `showInterstitial` ra `false` dù placement có khoá | placement không mặc định không được preload (show-then-load) | gọi `loadInterstitialForThisTurn` trước điểm show: mục 9 bước 5, 14 |
| App open không hiện khi quay lại app (`open_flag_resume`) | app chưa gọi `setFlag4ShowResume(true)` | mục 9 bước 3 |
| Logcat `policy: off (<khoá>)` | policy brain tắt: thiếu hoặc sai một meta-data (chỉ ghi khi giấy phép `VALID`; đúng khi chưa muốn bật) | mục 15 |
| Không quảng cáo dù token `VALID`, không có dòng khởi tạo mạng | `startSdkInit` chưa được gọi (bị đặt sau cổng consent của app) | gọi vô điều kiện, mục 8 bước 1 |
| Form EEA không hiện với `debugForceEea = true` | thiết bị chưa khai là thiết bị test UMP | mục 8 bước 5: mã băm vào `admob_test_device` + `flagTest` = `1` |
| Không quảng cáo, không dòng `license:` | app không kế thừa `AppPluginBase` hoặc chưa qua consent | mục 4, mục 8 |
| Form consent không hiện / sai vùng | tầng consent (`DENIED`/`PARTIAL`/`UNKNOWN`) hay vùng | kiểm `ConsentState.tier()`; QA bằng `ConsentManager.debugForceEea` |
| Mất mạng bidding sau khi nâng 1.9.0 | adapter GMA tuỳ chọn là `compileOnly` | tự khai adapter cùng bản và liệt kê partner ở `ads_bidding_partners` |
| Mất mạng game (Bigo, Chartboost, Fyber, Unity, AppLovin) sau khi nâng 2.0.0 | không còn kéo bắc cầu | thêm `appplugin-max` hoặc tự khai đúng toạ độ (mục 3) |
| Phần game không chạy, log `game: không có com.app.plugin.game.GameModule — 0 section game` | thiếu `appplugin-game` | mục 3 |

**Giới hạn đã biết ảnh hưởng tích hợp** (nguyên văn ở mục "Giới hạn đã biết" từng bản của `CHANGELOG.md`):

- (1.9.0) A/B bidding chỉ làm qua console AdMob hoặc hai bản dựng; opt-out bang Mỹ (GPP) không được chuyển tiếp cho partner bidding (adapter tự đọc TCF).
- (1.9.0) Rewarded interstitial chỉ AdMob (không MAX/Meta), chuỗi intro chỉ tiếng Anh; id chỉ từ resource `admob_rwi`.
- (1.8.0) Grace/hold của subscription chỉ biết khi backend nhận RTDN; không có backend thì nhãn suy từ Play.
- (1.6.x) AppsFlyer cùng process: bộ lọc GPP `"all"` ở lại tới khi process khởi động lại; host tự gọi `setSharingFilterForPartners` phải đặt lại sau khi AppsFlyer start.
- (2.0.0) Kiểu `internal` của Kotlin vẫn gọi được từ Java: không phải API được hỗ trợ.
- (2.1.0) `appplugin-game` luôn cùng bản với `appplugin`; SPI `@AppPluginInternalApi` có thể đổi giữa hai bản không báo.
- (2.2.0, `seg-gioi-han`) Segment: guardrail và `segment.value` chưa đúng trong vài chục ms đầu sau `initPlugin` (engine nạp trên luồng nền; trước đó guardrail không chặn, `segment` = `COLD_START`, `geoTier` = 4). Một ngoại lệ lúc engine init tắt segment cho process đó (ghi `logE`); lỗi đọc Remote Config lùi về mặc định. Ngưỡng `seg_ecpm_high_threshold_micros` mặc định 500 000 micros **mỗi impression** (giá trị fork) nên gần như luôn ra `LOW`: chỉnh bằng RC. Impression Meta direct và impression giá trị 0 được đếm vào `AD_IMPRESSION` (fork chỉ đếm paid event AdMob); `AD_IMPRESSION` chỉ tính cho định dạng toàn màn hình (`interstitial`, `opened`, `rewarded`, `rewarded_interstitial`); banner và native không tính. `LogEventServer.logAdRevenue` (public) của app với một trong bốn định dạng đó cũng nạp segment, với banner/native thì không. User property `seg_segment`, `seg_score`, `seg_revenue_tier` không ghi khi consent `DENIED` hoặc `UNKNOWN` (`PARTIAL` vẫn ghi) và được gửi lại một lần khi tầng chuyển thành `GRANTED`. Hàng đợi khởi động giữ tối đa 100 event, tràn thì bỏ cái cũ nhất.
- (2.2.0, `min24-gioi-han`) API 24–25 chưa đo trên thiết bị hay emulator: host chỉ có image arm64-v8a API 26/28/33/37 (O11, chưa có image arm64-v8a API 24). Cổng của bản là lint `NewApi` 0 và unit; đọc chứng chỉ ký dưới API 28 đi nhánh `GET_SIGNATURES` có sẵn.

## 12. Checklist phát hành

1. Bật R8 và `shrinkResources` ở release; token `licenseRelease` đúng `applicationId` và cert **Play App Signing** (mục 5).
2. Dựng release và chạy `bash tools/release_gate.sh --variant utility` trên bản dựng (app game: `--variant game`; cổng đọc APK + mapping: 0 `ClassValue`, ad unit còn, lớp SDK được đổi tên).
3. Kích thước: so APK với bản trước (`bash tools/bundle_size.sh --variant utility` cho bản demo; với app: `ls -l app-release.apk` và `apkanalyzer apk file-size`); mục tiêu không tăng ngoài dự kiến.
4. Cổng thiết bị trên chính app: `bash tools/device_gate.sh --serial <serial> --apk <app-release.apk> --app <package>/<Activity khởi chạy> [--mapping <mapping.txt>]`. Thêm `--mapping` để kiểm frame SDK (by-sdk); không có thì cổng in `by-sdk: không mapping` và bỏ qua bước đó.
   - Ở chế độ `--app`, cổng chỉ **FAIL cứng** những gì đúng với mọi app: FATAL, ANR, native crash, bốn chuỗi R8 hỏng và thiết bị không dùng được (tiền kiểm, dòng api). Kết luận là dòng `RESULT: PASS`. Cổng **dừng** (không phải FAIL của sản phẩm) khi: Docker Desktop đang chạy (từ chối chạy, hãy tắt Docker trước); không đọc được API của máy (`RESULT: INVALID (api)`, exit 4); hoặc tiền kiểm môi trường không đạt (`RESULT: INVALID (precheck)`, exit 4): máy thật cần Wi-Fi `CONNECTED`, màn hình không khoá, pin đủ, và host không được ngủ (emulator chỉ kiểm host).
   - Các kiểm riêng của demo chỉ để tham khảo, **không làm hỏng lượt chạy**: canary StrictMode (`strictmode armed … n=0 (need >= 0)`), AppsFlyer `af_app_id` có start không, và sink telemetry cục bộ (`sink: không dùng (--app)`: không `reverse` cổng 8089, không kiểm sink tăng dòng).
   - Chỉ truyền `--sink-port <p>` (hoặc `--sink-dir <d>`) khi meta-data `DATABUCKETS_*` của app trỏ tới một sink cục bộ do `tools/local_sink.sh` chạy; khi đó cổng còn đòi sink nhận được dòng. Đặt `STRICT_ARMED_MIN=1` hoặc `AF_STARTED_MIN=1` để hai kiểm tham khảo trên trở lại thành bắt buộc. Sink cục bộ cần network security config cleartext chỉ cho `127.0.0.1` ở source set `debug` và khoá API của sink (`demo-utility-key`, mục 6).
   - Policy brain (mục 15), chỉ với APK utility của SDK: `bash tools/device_gate.sh --serial <serial> --apk <apk utility> --mapping <mapping.txt> --policy` chạy bốn chặng L1–L4 trên sink fake mode cục bộ (L1 áp policy, L2 máy không được chấp nhận ra `policy: default (not_accepted)`, L3 sink dừng ra policy mặc định, L4 không có extra thì policy tắt); chế độ máy bay được khôi phục khi cổng thoát.
   - Giấy phép: kỳ vọng đúng một dòng `license: VALID kid=<kid>` mỗi process. **Cổng không tự kiểm dòng `license:`**: bạn xem nó trong logcat (`adb logcat | grep "license:"`, mục 5). Bản release cần token riêng của nó (mục 5: `applicationId` của release và cert đang ký bản đó), không dùng token của `.debug`.
5. Test EEA: máy thử với mã băm thiết bị trong `admob_test_device`, `flagTest` = `1` và `ConsentManager.debugForceEea = true` (hoặc VPN EEA; chỉ `debugForceEea` thì chưa đủ, mục 8 bước 5): form hiện, từ chối thì không quảng cáo cá nhân hoá, nút Quyền riêng tư mở được. Trên emulator chậm có vài dòng log thoáng qua (`info update timed out`, `Web view timed out`, tầng `DENIED` tạm thời); xem mục 8.
6. `flagTest` = `0`, bỏ `admob_test_device`, id ad unit thật, `google-services.json` đúng `applicationId`.
7. Billing: sản phẩm khai ở Play Console khớp `configure`; thử mua trên track internal testing.
8. minSdk 24: nếu app nhắm API 24–25, kiểm trên thiết bị hoặc emulator API 24 (SDK chưa đo, mục 11); token giấy phép dùng codec base64url Kotlin thuần (`com.app.plugin.license.B64Url`, nội bộ) và ngày `exp=` của dòng `license: VALID` tính bằng `SimpleDateFormat` (không `java.time`), nên không cần desugaring thêm. Lint `NewApi` của app phải sạch.
   Smoke API 26 của SDK 2.2.0 (build minSdk 24, APK utility đã đóng băng, emulator API 26): cổng cơ bản PASS (FATAL, ANR, native 0; by-sdk 0; `license: VALID` 1 dòng mỗi process; `segment: init` 1 dòng mỗi process). `initPlugin` cold start n=5 trung vị 142 ms, p90 212 ms (2.1.0 cùng image: 94 / 119 ms): số tham khảo trên emulator API 26 (host bận, tải 4–9), không phải ngưỡng. Máy thật tham chiếu: Redmi (Android 14) `initPlugin` trung vị 14 ms (2.2.0) so với 16 ms (2.1.0); A/B ghép cặp n=10: 13,5 so với 13,5 ms.

## 13. Phải làm khi nâng cấp (dưới 1.2.0 → 2.5.1)

Đọc từ bản app đang dùng xuống bản mới nhất; mỗi hàng là việc app phải làm (nguyên văn ở mục "Phải làm khi nâng cấp" cùng bản của `CHANGELOG.md`). Bản 1.6.1, 1.6.2, 1.6.3 không có mục riêng.

| Từ bản | Việc phải làm | Nơi tìm |
|---|---|---|
| 2.5.1 | Không bắt buộc. `ad_impression` do SDK ghi có thêm param `ad_placement`; muốn eCPM theo placement: đăng ký custom dimension `ad_placement` (event scope) ở GA4. App đã liên kết AdMob ↔ Firebase (`ads_admob_firebase_linked` = 1, **chỉ đặt khi console xác nhận liên kết**; dòng `origin=am ad_impression(_ai)` không chứng liên kết): đặt thêm `@integer/ads_cf_placement_event` = 1 (chỉ resource, RC không đặt được) để có event `ad_placement_impression`, và đăng ký custom metric `ad_value`. `ads_cf_native_no_reload`: mục thiếu `nt_` được chuẩn hoá thành `nt_<mục>` kèm `logE` (trước đó không có tác dụng) | `rollup` ([2.5.1]) |
| 2.5.0 | Không bắt buộc. Hai mặc định mới có công tắc lùi: `ads_cf_unit_gate_prio_off` = 1 (PRIO: cổng unit xếp lượt SHOW > APP > PRELOAD và nhường lượt chưa gửi id; waiter PRELOAD chạm trần 10 s vẫn đi tiếp như 2.4.4) và `ads_cf_open_gap_own_only` = 1 (LAM2: open ad chờ `openAdDeltaTime` sau mọi quảng cáo toàn màn hình, trước chỉ sau open ad); bốn công tắc `ads_cf_*` (cả hai của 2.4.4) đặt được qua RC `ads_policy_android.ads_cf_overrides` (mục 10; khoá RC có mặt đè resource, nên đừng gửi 0 cho khoá app đã đặt 1; RC chỉ lùi). `AdsHelper.setAutoShowOpenAdOnForeground(false)` tắt lượt SDK tự hiện `full_resume` (mục 4). `ads_cf_native_no_reload`: vắng = 3 placement cũ, có mặt (kể cả rỗng) = đúng danh sách. Layout thiếu `ad_media` không còn crash. NtCl/banner ngoài cổng vẫn tính backoff (GMA throttle theo ad unit); native song song cùng unit hoãn | `rollup` ([2.5.0]) |
| 2.4.4 | Không bắt buộc. Hai mặc định mới có công tắc lùi: `ads_cf_unit_gate_off` = 1 (một request mỗi ad unit: slot thứ hai cùng unit chờ request trước, cộng chờ hết backoff nếu request đó lỗi, tổng tối đa 10 s rồi gửi luôn; retry cùng slot và placement khi mọi id đang backoff thất bại ngay, 0 request, qua AdsHelper là `list empty` nên chờ ít nhất 2 s trước khi retry; timeout không tính backoff) và `ads_cf_native_media_ratio` = 1 (native xin ANY thay vì theo hướng màn hình; ảnh 1.91:1 hoặc 1:1 có thể về, app dùng layout riêng muốn không cắt tự gọi `setImageScaleType(FIT_CENTER)` hoặc `CENTER_INSIDE` trên MediaView của GMA; app dùng layout SDK không gọi được, thấy cắt thì đặt `ads_cf_native_media_ratio` = 1; chỉ màn Full2 dialog tự đặt `CENTER_INSIDE`); hàng load AdMob chạy ngay trong callback init; dòng log mới `load merged into in-flight request`, `load waits unit backoff`, `load skipped unit backoff`, `load released after`, `ads native media ratio` | `CHANGELOG.md` mục 2.4.4 |
| 2.4.3 | Không bắt buộc. Native nạp sẵn quá `fullscreen_max_age_minutes` (60 phút) bị thay bằng lượt nạp mới (`ad_show_fail reason=ad_expired`); preload chờ `full_splash`/`nt_splash` tối đa 10 s khi RC không đặt `delay_ms` (app không có splash, vd. Invoice Maker, preload chậm khoảng 1,5 s; `delay_ms` > 0 khôi phục thời điểm 2.4.2); lần mở đầu sau nâng cấp hoặc `clearLocalData` xác minh giấy phép đầy đủ một lần; dòng log mới `license: source=…`, `preload: wait splash`, `preload: splash done after …`; resource tuỳ chọn `ads_cf_admob_init_wait_ms` (mặc định tắt, chỉ Legacy) | `sc243-app` |
| 2.4.2 | Không bắt buộc. `cf_ads_placement` bật lại khoá đã gỡ theo từng nguồn (nâng từ 2.4.1: khoá RC đã gỡ được bật lại ở lần fetch đầu; Firestore có thể bật lại ngắn lúc khởi động). Placement có slot riêng đang rỗng nay hiện từ slot mặc định của mạng (mọi định dạng; callback có thể nhận `originPl` = tên slot mặc định, đừng dùng `originPl` để chọn placement nạp lại); `showOpenAd("full_splash")` hiện trong cửa sổ guardrail segment. Full2 nay mang tên `full_2_<placement được gọi>` (telemetry, RC, dashboard) và chọn slot `full_2_<pl>` → `full_2_default` → `full_default`; `isInterstitialReadyToShow` tính cả guardrail segment; Install Referrer tự ghi sau consent (app tự đọc thì bỏ, cập nhật khai báo Data safety: dữ liệu attribution cài đặt); Speaker Clean: đổi sang `fixedMoment`, `ads_cf_seg_cadence_guardrail_seconds`, `clearLocalData`, `cf_ads_placement` (mục 14) | `sc242-app` |
| 2.4.1 | Không bắt buộc. Từ 2.4.1 SDK nhận Activity chủ khi là Activity launcher của `packageName` hoặc cùng package với lớp `Application` (luật `startsWith(packageName)` cũ giữ; Activity của SDK quảng cáo bị loại), nên app có `applicationIdSuffix`/flavor `applicationId` (vd. `.debug`) không cần làm gì. Trên mọi bản từ 8fcc3c1 (2026-09-11), gồm 2.3.0 và 2.4.0, app loại này gọi `AppPluginManager.instance.setMainActivity(this)` trong `onCreate` của Activity chính (mục 4); nâng lên 2.4.1 thì bỏ được | `hostfix-app` |
| 2.4.0 (tổng quát) | Đọc mục "Phải làm khi nâng cấp" của bản này ở `CHANGELOG.md` | `rollup` |
| 2.4.0 (`appplugin-nextgen`, tuỳ chọn) | Không bắt buộc: ở `appplugin` (Legacy) không việc. Muốn GMA Next-Gen (mục 16): đổi toạ độ `appplugin` thành `appplugin-nextgen:<phiên bản>`; exclude toàn cục `exclude("com.google.android.gms", "play-services-ads")` và `play-services-ads-lite`; app dùng `-game` thêm luật `dependencySubstitution { substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:2.4.0")) }` (mục 17); bỏ `appplugin-max` (không hỗ trợ cùng Next-Gen); layout của app ghi đè **cùng tên** một trong 7 file layout (6 tên) native của SDK (`appplugin_admob_native_cl`, `appplugin_admob_native_layout_small`, `…_layout_language_2` (cùng tiền tố), `appplugin_layout_native_full_pager`, và của Full2 `appplugin_admob_native_full` (dọc và `layout-land`), `appplugin_admob_native_layout_full`; cả layout đặt qua `setLayoutCustome4Native`) phải đổi lớp gốc `NativeAdView`/`MediaView` sang `com.google.android.libraries.ads.mobile.sdk.nativead.*`: giữ lớp Legacy thì inflate hỏng vì `play-services-ads` bị loại; app biên dịch trên `appplugin-nextgen` nhận `kotlinx-coroutines-android` ≥ 1.9.0 qua `constraints { api(…) }` của `appplugin-nextgen/build.gradle.kts` được publish cùng artifact (POM `<dependencyManagement>` / metadata Gradle; không cần ghim); banner và rect Next-Gen cần Activity đã đăng ký (`setMainActivity`) lúc nạp xong, không thì quảng cáo bị huỷ và nạp lỗi `activity unavailable` (lần `showBanner` kế nạp lại); mã GMA của chính app (import `com.google.android.gms.ads`) phải tự di trú sang Next-Gen | `ng-app` |
| 2.4.0 (policy brain) | `BRAIN_POLICY_URL` phải `https://`; `http://` chỉ nhận đúng host `127.0.0.1` (harness, máy dev), host khác cho `policy: off (com.app.plugin.BRAIN_POLICY_URL)`. `/policy` chỉ chạy khi `brainEnabled = true` trong `configure` và player trong rollout phía client (khác: `policy: off (brain)`, 0 mạng); tối đa một lượt Integrity mỗi 24 h mỗi cài đặt (mục 15) | `carry-pol-app` |
| 2.4.0 (rewarded) | Không việc: sau khi hiện hoặc hiện lỗi, rewarded nạp lại **placement vừa hiện** (vd. `gift_premium`) thay vì luôn `gift_default`; lời gọi nạp lại của app vẫn vô hại | `carry-ads-hanh-vi` |
| 2.3.0 (RemoveAdsMask public) | Đổi hằng cục bộ thay `RemoveAdsMask` về `RemoveAdsMask.ALL/INTERRUPTIVE`; app còn dùng `com.app.plugin.ads.consent.ConsentManager` (`@Deprecated`, gỡ ở 3.0) chuyển sang `com.app.plugin.consent.ConsentManager` (mục 8) | `rob3-app` |
| 2.3.0 (policy brain, tuỳ chọn) | Không bắt buộc. Muốn brain lấy policy từ server: khai ba meta-data (mục 15); thiếu bất kỳ khoá = tắt, hành vi cũ | `secbc-app` |
| 2.3.0 (preload) | Không việc: SDK tự nạp sẵn interstitial, app open, rewarded và rewarded interstitial mặc định sau consent; `load*ForThisTurn` cho placement mặc định là vô hại nhưng vẫn nên gọi (nạp sẵn không thử lại khi no-fill và dùng `flagImg 0`), placement không mặc định và việc nạp lại chúng sau mỗi lần hiện (interstitial và rewarded) vẫn do app; app open khi quay lại app cần `setFlag4ShowResume(true)` (mục 9 bước 3, 14) và khi đã bật thì hiện ở mọi lần quay lại đủ điều kiện; tắt bằng RC `ads_preload_android` | `preload-app`, `preload-hanh-vi` |
| 2.3.0 (tổng quát) | Đọc mục "Phải làm khi nâng cấp" của bản này ở `CHANGELOG.md`. Hướng dẫn mới của tài liệu: token ở `license.properties` (mục 5), `exclusiveContent` cho repo SDK (mục 2), `tools:replace` backup-rules cho mọi app (mục 6), Consent Mode v2 và FCM (mục 6, 8) | `rollup` |
| 2.1.0 (token giấy phép) | Xin token cho `applicationId` + SHA-256 cert ký phát hành, khai meta-data `com.app.plugin.LICENSE`; không khai thì không quảng cáo, không brain (README mục 9, `tools/license_issue.py`) | `sec-a-app`, `sec-a-hanh-vi` |
| 2.1.0 (mã game) | App tiện ích không dùng RC game/CRM/BattlePass/PurchaseRouter/RecurringGrants/Offerwall: 0 việc. App dùng chúng: thêm `appplugin-game:<cùng bản>` (import và FQN không đổi); không có nó thì 5 khoá RC game và tín hiệu brain → CRM không chạy | `g1-app`, `g1-hanh-vi` |
| 2.1.0 (log SDK, tuỳ chọn) | Muốn bỏ chuỗi log SDK khỏi dex: thêm `docs/integration/appplugin-strip-logs.pro` vào `proguardFiles` của release (không bật ở bản chạy cổng thiết bị) | `sec-c-app` |
| 2.1.0 (hành vi) | Không việc: lần mở đầu chậm đặt 9 cờ `Constants.Common.IS_FETCH_FIREBASE`/`Constants.InitAds.*` sau `Application.onCreate` trên luồng nền; `onAdDismissed`/`onAdRewarded` của rwi và rewarded, click/impression của native full theo lượt của chính quảng cáo (ngoại lệ rwi của `c1b` 2.0.0 hết hiệu lực từ 2.1.0) | `p1-hanh-vi`, `p1`, `p3` |
| từ bản < 1.2.0 (ví dụ submodule trước v1.2.0) | Đọc mục "Phải làm khi nâng cấp" của [1.2.0] (R8 keep, `InstallReferrerHelper.captureOnce`, `ConsentManager.request` một lần mỗi process, `gatherConsent`, kapt ở mục 1); gọi `AdsMobMy.instance.startSdkInit(applicationContext)` trong Activity đầu, **vô điều kiện và trước** `ConsentManager.gatherConsent` (mục 8 bước 1): SDK tự chờ câu trả lời UMP rồi mới khởi tạo mạng, không còn khởi tạo mạng ở `AppPluginBase.onCreate`; gọi `AdsHelper.instance.setFlag4ShowResume(true)` một lần sau consent nếu muốn app open lúc quay lại app; khai meta-data `com.app.plugin.LICENSE` (mục 5); rồi đọc các hàng từ 1.3.0 tới 2.5.1 | `CHANGELOG.md` [1.2.0]; các hàng 1.3.0, 2.1.0 (token giấy phép) |
| 1.3.0 | Gọi `AdsMobMy.instance.startSdkInit(context)` (`AppPluginBase.onCreate` không còn khởi tạo mạng); chạy luồng consent UMP (`gatherConsent`/`updateConsentInfo`) vì mạng chỉ khởi tạo sau câu trả lời; app đo Meta tự khai `facebook-core`; DataBucket mặc định tắt (khai `DATABUCKETS_BASE_URL` nếu cần); composite build cần AGP 9.x | mục "Phải làm khi nâng cấp" của [1.3.0] |
| 1.3.1 | Callback Full2 tới `AdsShowCb` cha (phân biệt bằng `placement?.startsWith("full_2_")`); `onAdShowFail` không còn tới giữa waterfall (dựa vào giá trị trả về); `onAdDismissed` có thể tới đồng bộ trong `onActivityDestroyed` (không commit Fragment/điều hướng); không đặt resource trùng `raw/appplugin_keep` | mục "Phải làm khi nâng cấp" của [1.3.1] |
| 1.4.0 | Kiểm lại nhánh `DENIED`/`PARTIAL`/`UNKNOWN` của consent tier; app không UMP tự `ConsentState.setTier` và gọi `updateConsentInfo`; app không kế thừa `AppPluginBase` gọi `ConsentState.installStore`+`restore`; bỏ `setAnalyticsCollectionEnabled(true)` vô điều kiện và `AppsflyerHelper.initHelper`; `tools:replace` cho `google_analytics_default_allow_*`; build lại vì `IapHelper.init` trả `Boolean`; `peekAll()` nullable; `onResult` của `LogEventServer.log…` chạy trên luồng bất kỳ và trễ; backend khử trùng event; `user_id` đổi từ ANDROID_ID sang player id (truyền `configure { playerId }`); lớp con `AdsPlBase`/`AdsBase` thêm `eventTag` (mục Đổi) | mục "Phải làm khi nâng cấp" của [1.4.0] |
| 1.5.0 | Chỉ khi có `admax_*` nhiều id (`pl,idA;idB`): xem lại thứ tự id (mỗi bước waterfall MAX request đúng unit); không để hai placement rewarded dùng chung một ad unit MAX; API không đổi | mục "Phải làm khi nâng cấp" của [1.5.0] |
| 1.6.0 | `cbLoad` của placement không đang load bị bỏ khi Activity chủ bị huỷ: đăng ký lại trong Activity mới (`showBanner`/`showRect`/`load…ForThisTurn`) | mục "Phải làm khi nâng cấp" của [1.6.0] |
| 1.6.4 | App tiện ích muốn bỏ MAX và adapter game: loại khỏi classpath như flavor `utility` (consumer rules đã `-dontwarn com.applovin.**`); app đang dùng đủ adapter: không việc (đã thay bằng artifact `-max` ở 2.0.0) | mục "Phải làm khi nâng cấp" của [1.6.4] |
| 1.7.0 | Event server schema v2 (`ad_request`/`ad_fill`/`ad_no_fill`/`ad_show_attempt`/`ad_show_fail` thay tên v1): đổi truy vấn `events_raw`; DataBucket vẫn tắt tới khi khai `DATABUCKETS_BASE_URL` | mục "Phải làm khi nâng cấp" của [1.7.0] |
| 1.8.0 | Sub `PENDING` không tắt quảng cáo, `IN_GRACE_PERIOD` chỉ từ server; `purchase()` chồng trả `Failed(-2, …)`; quyền lợi chỉ có hiệu lực sau lượt kiểm chữ ký nền; holdout paywall mặc định 0 (muốn holdout thì khai `holdout.fraction` trong `paywall_config_android` v2) | mục "Phải làm khi nâng cấp" của [1.8.0] |
| 1.9.0 | Năm adapter GMA tuỳ chọn (`pangle`, `mintegral`, `unity`, `vungle`, `applovin`) là `compileOnly`: app tự khai adapter cùng bản và liệt kê partner ở `ads_bidding_partners` | `t1-app` ([1.9.0]) |
| 2.0.0 (ba artifact) | App game thêm `appplugin-max` + `appplugin-game` cùng bản; app tiện ích chỉ `appplugin`. App từng dựa vào mạng bắc cầu cũ (Bigo, GMA Chartboost, GMA Fyber, `unity-ads` 4.19.0, `applovin-sdk` 13.6.2 + adapter) tự khai hoặc thêm `appplugin-max`; partner `unity` trên utility thêm `unity-ads:4.19.0` | `c2-app` |
| 2.0.0 (resource) | Tên resource của SDK có tiền tố `appplugin_` (96 tên: layout, style, `@xml/appplugin_gma_ad_services_config`, `appplugin_default_notification_channel_id`, màu, dimen, drawable, font); khoá app-contract và RC không đổi; 4 font không dùng bị xoá | `c3a-app` |
| 2.0.0 (API) | Tham chiếu một trong 25 kiểu/3 thành viên chuyển `internal` thì lỗi biên dịch: dùng API public; fork Speaker Clean và Invoice Maker (∩ SDK) và demo: 0 chỗ tham chiếu | `c3b-app` |
| 2.0.0 (hành vi) | Không việc: HTTP 401 từ server event là tạm thời (giữ và phát lại, trần 2000 bản ghi); billing listener không ghi giá 0; callback GMA vào main qua một poster; seam MAX/game nội bộ; `onAdDismissed`/`onAdRewarded` của rwi (`AdsMobPlRwi:162/164`) vẫn đọc placement hiện hành | `b1-hanh-vi`, `b1`, `c3c`, `c1-seams`, `c1b` |
| 2.2.0 (tổng quát) | Đọc mục "Phải làm khi nâng cấp" của `[2.2.0]` ở `CHANGELOG.md`; hàng chi tiết cho segment và minSdk 24 ở khối dưới bảng. Nếu đang ở 2.1.0: kiểm token giấy phép còn hạn (mục 5) | `rollup` ([2.2.0]) |
| 2.2.0 (minSdk 24) | App `minSdk` 24/25 dùng SDK không cần `tools:overrideLibrary`; nếu đã thêm để ép merge với SDK 26 thì bỏ. App `minSdk` ≥ 26: không phải làm gì | `min24-app` |
| 2.2.0 (segment) | App dùng fork `:adsplugin`: xoá `segment/` của fork và các lời gọi SDK nay tự làm (`UserSegmentEngine.instance.init`, `SESSION_START`, `REWARDED_AD_COMPLETED`, `AD_IMPRESSION`); `UserSegment` có 6 giá trị; guardrail mặc định tắt, bật bằng RC `seg_cadence_guardrail_seconds`; `segment` chưa đúng vài chục ms đầu sau `initPlugin`. App khác: không việc | `seg-app` |
| 2.2.0 (hành vi) | Không việc: brain chỉ quyết khi giấy phép `VALID` (`INVALID`/`PENDING` ra `NO_ACTION`, tín hiệu exposure/lần mua đầu giữ ≤ 8 tới verdict `VALID`; đừng chờ quyết định brain trước verdict); click và impression tới muộn của native full (Meta và AdMob) đi tới callback của chính lượt quảng cáo đó | `lic-hanh-vi`, `lic`, `psh` |


## 14. Di trú từ fork `:adsplugin` (Speaker Clean, Invoice Maker)

1. Xoá module `:adsplugin`, thêm `implementation("com.app.plugin:appplugin:<phiên bản>")` (mục 2) và token giấy phép (mục 5, ở `license.properties`); `@xml/gma_ad_services_config` của Speaker Clean đổi thành `@xml/appplugin_gma_ad_services_config`.
2. Hằng `REMOVE_ADS_*` của `AdsHelper` trong fork nay là `RemoveAdsMask.*` (mục 9, bước 6); công tắc cũ `setRemoveAds(1)` vẫn dùng được.
3. `PlacementSwitches` chỉ có trong fork: bỏ (thay bằng RC `cf_ads_placement`, bảng dưới); `isPlacementEnabled` có ở SDK từ 2.4.2 (`AdsHelper.instance.isPlacementEnabled(placement)`).
4. Segment: xoá thư mục `segment/` của fork (FQN `com.app.plugin.segment.*` giữ nguyên, 0 import đổi). Bỏ các lời gọi SDK nay tự làm: `UserSegmentEngine.instance.init`, `EcpmBucketResolver.init`, `recordEvent(SegmentEvent.SESSION_START)` ở `onStart` (SDK ghi mỗi lần foreground; giữ thì đếm đôi), `REWARDED_AD_COMPLETED` trong `onAdRewarded` và `AD_IMPRESSION` ở `FirHelper` (SDK ghi cả hai). Guardrail toàn màn hình của fork (IM 180 s, SC 60 s) mặc định **tắt** ở SDK: muốn giữ thì đặt RC `seg_cadence_guardrail_seconds` hoặc (từ 2.4.2) resource `ads_cf_seg_cadence_guardrail_seconds` (SC: 60); từ 2.4.2 `showOpenAd("full_splash")` được miễn guardrail như fork (bản 2.2.0 ghi sai là không miễn), Full2 vẫn đóng dấu đồng hồ; lý do chặn đổi tên `global_guardrail` → `seg_guardrail`. `UserSegment` có 6 giá trị (Speaker Clean thiếu `POWER_USER` và `CHURN_RISK`: mọi `when` exhaustive cần thêm hai nhánh). Không port `PlayIntegrityGuard` của Speaker Clean (giữ ở app).
5. Resource SDK có tiền tố `appplugin_` (hàng 2.0.0 ở mục 13): đổi mọi tham chiếu/ghi đè tên cũ.
6. Phụ thuộc bắc cầu: fork khai Firebase, GMS, Facebook, gson và material là `api`, còn SDK publish các thư viện đó ở scope runtime. Module nào của app từng biên dịch nhờ chúng qua `:adsplugin` (hoặc gọi chính SDK, ví dụ module cài đặt gọi `ConsentManager`) phải tự khai `com.app.plugin:appplugin:<phiên bản>` hoặc thư viện đó.
7. Consent Mode v2 từ chối mặc định và FCM auto-init tắt (mục 6) là khác biệt hành vi so với fork: Analytics và push FCM riêng của app bị chặn tới khi tầng consent cho phép; app có dịch vụ push riêng cần kiểm lại số người nhận ở EEA.
8. **Thời điểm cố định (từ 2.4.2):** `bypassFrequencyCaps` của fork thành tham số `fixedMoment` — **luôn truyền theo tên**: `isInterstitialReadyToShow(placement, fixedMoment = true)` và `showInterstitial(placement, flagImage, cb, fixedMoment = true)` (tham số thứ 2 của SDK là `flagImage: Int`, không phải cờ). Ready và show phải nhận **cùng** `flagImage`/`isDeltaTime`/`fixedMoment`. Ready còn đòi placement đã nạp ở lượt này (`loadInterstitialForThisTurn` trước vòng chờ) nên có thể `false` trong khi show (qua slot mặc định) vẫn hiện. Lượt **không** cố định kiểu `tab_switch` của fork (`isDeltaTime = false` + guardrail + cooldown màn): SDK không có tổ hợp bằng đúng — `isDeltaTime = false` bỏ cả cooldown N màn, `true` thêm `fullStartTime`/khoảng cách; app chọn một giá trị, ghi vào bảng chuyển đổi của app và dùng cùng giá trị cho ready và show.
9. **Công tắc placement (từ 2.4.2):** thay ads_placement_android + `PlacementSwitches` bằng RC `cf_ads_placement` (SDK tự đọc, không cần mã app). Bảng chuyển:

   | Fork (ads_placement_android) | SDK 2.4.2 |
   |---|---|
   | `"placements": {"full_resume": false}` (fork coi là tắt: `false` không phân biệt hoa thường, số `0`, chuỗi `"0"`) | `cf_ads_placement`: `{"full_resume":{"flag":-1}}`. Chỉ giá trị dạng đối tượng có `flag` được đọc; `false`/`0` trần bị bỏ qua nên phải đổi sang `{"flag":-1}`. Khoá có dấu phẩy tách nhiều placement (`"a,b"`) |
   | `"placements": {"x": true}` hoặc không liệt kê | bỏ khoá (vắng = bật); khoá đang `flag:-1` mà gỡ đi thì placement bật lại, theo từng nguồn (RC và Firestore mỗi bên chỉ bật lại khoá chính nó đã tắt), kể cả khi RC rỗng |
   | khớp `x` ≡ `full_x` | giữ: khoá `k` áp cho `k` và `full_k`. **Khác fork:** JSON có cả `full_x` và `x` thì khoá đúng tên thắng cho placement đó (fork tắt cả hai nếu một trong hai bị tắt); với một khoá mỗi placement không khác |
   | tắt Full2 sau `x` | `{"full_2_x":{"flag":-1}}`; `{"full_2_default":{"flag":-1}}` tắt Full2 của mọi placement không có khoá riêng |
   | `screen_switch_interval: N` | **không chuyển vào SDK**: ở lại app, đọc từ khoá RC riêng của app (SC: bộ đếm `ScreenSwitchAdCadence`; có thể giữ chính khoá ads_placement_android chỉ để đọc `screen_switch_interval`, SDK không đọc khoá đó); không đặt `screens_before_interstitial` thay nó vì cổng đó dùng chung cho interstitial, app open và ẩn banner. Quyết định (iii) của plan 2.4.2 |
   | rewarded tắt | app tự xử lý (vd cho thưởng miễn phí) qua `AdsHelper.instance.isPlacementEnabled("gift_x")` |
   | đọc trạng thái | `AdsHelper.instance.isPlacementEnabled("x")` |

   Khác fork có chủ ý: JSON hỏng giữ nguyên trạng thái trước + `logE` (fork bật hết). Công tắc đặt bằng cách gọi trực tiếp `AdsHelper.instance.parserAdsConfig(json)` (không qua RC/Firestore) **không bền**: lần fetch RC kế tiếp dựng lại trạng thái từ bản ghi theo nguồn và bật lại nó; app chỉ nên bật/tắt placement qua RC (`cf_ads_placement`) hoặc Firestore.
10. **Install Referrer (từ 2.4.2):** SDK tự ghi sau consent, không như fork (fork gọi trước consent ở `AppPluginBase`): bỏ lời gọi riêng; không đọc khi Consent Mode không cho `analytics_storage`; `gclid`/`raw_referrer` chỉ khi `ad_user_data` được cho.
11. **Xoá dữ liệu (từ 2.4.2):** thay PreferenceHelper.clearAll() của fork bằng `MonetizationSdk.clearLocalData(context)` (import `com.app.plugin.MonetizationSdk`): xoá dữ liệu cục bộ của SDK nhưng **giữ quyền đã mua**, cờ consent, trạng thái công tắc placement (`mem_cf_ads_placement`, `_rc`, `_fs`), bản lưu cấu hình và cờ chống thu lại Install Referrer; **xoá sổ mua** (lần khôi phục mua sau đó phát lại `iap_purchase`, server khử trùng theo token), player id do SDK sinh (id mới, `first_open` gửi lại; app truyền `playerId` qua `configure` thì `user_id` vẫn là id app truyền) và số phiên; làm rỗng hàng đợi sự kiện bền; từ 2.4.3 xoá cả cache giấy phép (`appplugin_license`), nên lần mở sau xác minh giấy phép đầy đủ một lần (theo khả năng tốt nhất: một lần ghi `apply()` đang chạy có thể ghi lại mục sau khi xoá). Còn sót tới khi khởi động lại process: thống kê bandit/belief của brain trong RAM và cửa sổ eCPM của segment, nên app muốn xoá sạch thì khởi động lại process sau khi gọi. Hàm chỉ xoá dữ liệu **trên máy**: dữ liệu đã gửi (telemetry backend theo `user_id`, Firebase Analytics, AppsFlyer) không bị xoá; yêu cầu xoá dữ liệu/tài khoản phía server theo chính sách Play là việc riêng của app. Không chạm file của UMP/Firebase/AppsFlyer/AppLovin và `<pkg>_preferences` (IABTCF/GPP): app tự gọi `resetAnalyticsData` của Firebase và đổi lựa chọn consent qua `showPrivacyOptions`.

## 15. Policy brain qua Play Integrity (tuỳ chọn, từ 2.3.0)

Mặc định **tắt** và app không phải làm gì. Từ 2.4.0 policy chỉ chạy khi app bật brain (`brainEnabled = true` trong `configure`) và player nằm trong rollout phía client (`brainRolloutFraction`; `rollout_fraction` của artifact đã áp không tắt `/policy`); khác thì đúng một dòng `policy: off (brain)` mỗi process (sau verdict giấy phép `VALID` đầu tiên), không đọc meta-data, không mạng. Khi bật, SDK lấy policy của brain từ backend `/policy` (có xác minh Play Integrity) thay cho Remote Config `brain_policy_android`. Ba meta-data trong `<application>`:

| Meta-data | Giá trị |
|---|---|
| `com.app.plugin.BRAIN_POLICY_URL` | URL `https://` của backend, **kết thúc bằng `/`**; `http://` chỉ nhận đúng host `127.0.0.1` (harness, máy dev), mọi host `http://` khác, và URL có user-info, query hay fragment, cho `policy: off (com.app.plugin.BRAIN_POLICY_URL)` (từ 2.4.0) |
| `DATABUCKETS_API_KEY` | khoá API của app trên backend (chính meta-data đo lường ở mục 6; chuỗi không rỗng, khoá toàn chữ số khai qua `@string`) |
| `com.app.plugin.INTEGRITY_CLOUD_PROJECT` | Google Cloud project number, số > 0, **khai qua `@string/...`**, không dùng số trực tiếp (aapt2 có thể lưu số 12 chữ số dạng float, `projectNumber` từ chối → `policy: off`; khuyến nghị, chưa kiểm trên thiết bị) |

- Thiếu hoặc sai một khoá (kiểm theo thứ tự URL, API key, project): đúng một dòng `policy: off (<khoá>)` mỗi process (chỉ ghi sau verdict giấy phép `VALID` đầu tiên; giấy phép `INVALID`/`PENDING` thì không có dòng `policy:` nào), không mạng, brain giữ hành vi cũ.
- Đủ ba khoá: **tối đa một lượt mỗi 24 giờ mỗi cài đặt** (từ 2.4.0, mốc ghi trước lượt mạng và liên process; lượt lỗi cũng tính; process trong 24 h sau đó ghi `policy: skip (backoff <giờ còn lại>h)`, không mạng), ngay sau verdict giấy phép `VALID` đầu tiên của process được phép (không chờ consent), **không thử lại**: `POST <url>policy/nonce`, lấy token Integrity (≤ 10 s), `GET <url>policy`. Mọi lỗi giữ policy đang chạy (RC, bản đã lưu hoặc mặc định) và ghi một dòng `policy: default (<lý do>)`, `policy: rejected (…)` hoặc `policy: error (…)`; thành công ghi `policy: applied version=<v> source=app`. Giấy phép `INVALID` hoặc `PENDING` mãi thì không có mạng.
- Khi policy từ server đã áp, RC `brain_policy_android` bị bỏ qua tới hết process (`policy: rc_skipped version=<v> source=app`); bản server được lưu và cold start khôi phục như bản RC.
- Backend chạy `BRAIN_INTEGRITY_MODE=google` chỉ sau khi chủ dự án hoàn tất bước bật Play Integrity thật cho backend (tài khoản dịch vụ GCP của chủ dự án). **Chưa kiểm với Play Integrity thật**: SDK mới chạy với nguồn token giả và sink fake mode; hạn ngạch và độ trễ thật chưa đo. **Hạn ngạch:** từ 2.4.0 chỉ cài đặt có brain bật và player trong rollout phía client tốn lời gọi Integrity classic, tối đa một lời gọi mỗi 24 giờ mỗi cài đặt (brain tắt: 0 lời gọi); mặc định khoảng 10 nghìn lời gọi/ngày theo tài liệu công khai [P]; chủ dự án phải kiểm tra/nâng hạn ngạch trước khi bật.
- Kiểm thử: `device_gate.sh --policy` (mục 12 bước 4).

## 16. Chọn GMA Legacy hay Next-Gen (từ 2.4.0)

Từ 2.4.0 cùng một mã tích hợp được publish thành hai bản dựng: `appplugin` (GMA Legacy `play-services-ads` 25.4.0) và `appplugin-nextgen` (GMA Next-Gen `ads-mobile-sdk` 1.5.0). API công khai của hai bản giống nhau (`tools/api_parity_check.sh` so hai dump), nên đổi bản không đổi mã app gọi SDK; khác nhau ở bên dưới. Chọn đúng **một** (mục 3).

| Khác biệt | `appplugin` (Legacy) | `appplugin-nextgen` |
|---|---|---|
| Thư viện GMA | `com.google.android.gms:play-services-ads:25.4.0` | `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.5.0`; adapter Meta `com.google.ads.mediation:facebook:6.22.0.1`; `play-services-ads` bị loại |
| Khởi tạo nền | khởi tạo của Legacy (`MobileAds`); quá 8 s chưa xong thì thả các lời gọi đang chờ | khởi tạo trên luồng riêng `appplugin-gma-init` sau consent; load gọi trước khi xong được giữ (tối đa 16, tràn thì từ chối lời gọi mới), quá 8 s vẫn giữ vì Next-Gen ném lỗi nếu load trước khi init xong, chạy đúng một lần trên main khi xong, init lỗi thì bỏ; log `gma: init queue full, drop <n>`, `gma: init done, run <n>`, `gma: ready <ms>ms` |
| Callback | trên main như trước | Next-Gen gọi callback load và sự kiện trên luồng nền; SDK đưa về main qua một cửa (`MainPost`), app không phải làm gì |
| Banner | anchored adaptive theo hướng hiện tại của màn hình | banner large anchored adaptive (chiều cao có thể khác `appplugin`); rect `MEDIUM_RECTANGLE`; banner collapsible gửi `collapsible=bottom`; banner và rect cần Activity đã đăng ký lúc nạp xong (không thì `activity unavailable`); native, native collapsible và Full2 có bản riêng |
| Unity (mediation) | tải bằng context của app hoặc Activity theo cờ `gma_load_app_context` (RC `ads_policy_android`, mặc định tắt) | `load` của Next-Gen không nhận Context nên cờ `gma_load_app_context` không có tác dụng; Unity yêu cầu Activity context khi nạp banner, interstitial và native: **chưa đo** (chưa có driver và mediation Unity thật) |
| `appplugin-max`, Bigo | hỗ trợ | không hỗ trợ (mục 3) |
| Mốc hỗ trợ | Google: GMA Legacy v25.x deprecation 2027-06-30, sunset 2028-06-30; SDK chưa có ngày bỏ `appplugin` (kế hoạch: ≤ 2027-06-30) | bản Google khuyến nghị, nhưng ở 2.4.0 là preview, chưa dùng production (xem dưới); SDK chưa bỏ Legacy ở 2.4.0 |

- **`appplugin-nextgen` 2.4.0 là preview, chưa dùng production.** `ads-mobile-sdk` 1.5.0 chuyển media creative toàn màn hình qua client HTTP riêng (`ads_mobile_sdk.r60`) và đệm cả body trong heap không trần: creative video progressive dài (itag 22, khoảng 34 phút, không `range=`) gây `OutOfMemoryError` 134–268 MB trên Samsung A25 ở process chỉ nạp sẵn; Legacy phát qua WebView nên không bị. Không có công tắc công khai và SDK ta không bắt được lỗi này; chờ Google sửa (theo dõi `ads-mobile-sdk` > 1.5.0). `ads-mobile-sdk` 1.5.0 ghi `NoClassDefFoundError` cho `PackageManager$OnChecksumsReadyListener` (kiểu API 31) ở lớp `am3`; ART từ chối khởi tạo lại lớp, quảng cáo vẫn chạy. Đo được: API 26 không có, API 28 có (8 dòng, không crash), API 33/37 không; API 29–32 chưa đo. Chưa có sàn minSdk được chứng minh; app thử nextgen trên API < 31 có thể thấy dòng này (lỗi trong SDK Google). `appplugin` (Legacy) vẫn là bản mặc định.
- Chọn **Legacy** khi dùng `-max` hoặc Bigo, hoặc muốn giữ đường đã chạy nhiều bản. Chọn **Next-Gen** để thử trước khi rời Legacy (mốc 2027-06-30), không cần MAX; ở 2.4.0 chưa dùng production.
- Giới hạn đã biết (`CHANGELOG.md`, mục "Giới hạn đã biết" của bản 2.4.0): hai bộ nguồn GMA chạy song song tới khi bỏ Legacy; Next-Gen báo mọi callback trên luồng nền nên lỗi hiện không tới đồng bộ trong `show()` (trả `true`, lỗi tới `onAdShowFail` sau) và waterfall không rơi sang mạng kế trong cùng lượt như Legacy; lý do show-fail mang mã lỗi (`fail_to_show_full_content_<mã>` (app open: `open_fail_to_show_full_content_<mã>`)) thay vì thông điệp; Full2 nạp xong khi Activity đã huỷ thì báo nạp lỗi và huỷ quảng cáo, và không còn log `onAdOpened`/`onAdClosed` của Full2; Unity Activity chưa đo.
- Đổi sang Next-Gen: mục 13 (hàng `ng-app`). Kiểm: build release rồi `tools/release_gate.sh --variant utility` như mục 12.

## 17. `-game` + `appplugin-nextgen` (từ 2.4.0)

Ở 2.4.0 **không có artifact thứ năm** cho game + Next-Gen: `appplugin-game` kéo `com.app.plugin:appplugin` (Legacy) cùng bản. App cần game và Next-Gen thay phụ thuộc phía app: thay `appplugin` bằng `appplugin-nextgen` và exclude Legacy toàn cục, trong `build.gradle.kts` của app:

```kotlin
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:2.4.0"))
    }
}
dependencies {
    implementation("com.app.plugin:appplugin-nextgen:2.4.0")
    implementation("com.app.plugin:appplugin-game:2.4.0")
}
```

Thay `2.4.0` bằng `<phiên bản>` đang dùng. SDK kiểm quy tắc này trên dự án tiêu thụ dựng từ `build/repo` (`tools/consumer_check.sh`, phụ lục A): có quy tắc thì cây phụ thuộc chỉ còn `appplugin-nextgen`, bỏ quy tắc thì trùng cả hai artifact. Nếu việc đó lộ lỗi, một artifact riêng cho game + Next-Gen có thể ra ở bản sau. `appplugin-max` vẫn không hỗ trợ (mục 3).

## Phụ lục A. Kiểm chứng cục bộ bằng repo `build/repo` (chỉ để kiểm)

Chỉ dùng khi không tới được GitHub Packages (lane kiểm chứng, môi trường cách ly). Ở cây mã SDK: `./gradlew publishAllPublicationsToLocalCheckRepository` tạo `build/repo` (đủ bốn artifact đúng `<phiên bản>`). Với khối `exclusiveContent` ở mục 2, chỉ cần truyền property, không sửa file (toạ độ không đổi; bỏ property thì lấy lại từ GitHub Packages):

```
./gradlew <tác vụ> -Pappplugin.localRepo=<đường dẫn tuyệt đối>/build/repo
```
App chưa có khối mục 2 mà cần thay tạm thì thay khối GitHub Packages bằng repo cục bộ, vẫn trong `exclusiveContent`:

```kotlin
exclusiveContent {
    forRepository { maven { url = uri("<đường dẫn tuyệt đối>/build/repo") } }
    filter { includeGroup("com.app.plugin") }
}
```

Không dùng cách này cho app thật: phát hành luôn lấy từ GitHub Packages (mục 2).

## English (short)

`appplugin` is an internal Android SDK (ads via AdMob/GMA, consent, IAP, Remote Config, brain). Integration in ten steps:

1. Requirements: compileSdk 36, AGP 9.2.1 or newer (built-in Kotlin: no `kotlin-android` plugin needed; an app on AGP 8.x must apply `kotlin-android` 2.2 or newer itself, which the SDK has not verified), plugins `com.google.gms.google-services` 4.4.2 and `com.google.firebase.crashlytics` 3.0.3, a Firebase project, an AdMob account, a license token per `applicationId`.
   SDK minSdk 24 (from 2.2.0; was 26): apps with `minSdk` 24 or higher can use the SDK; drop any `tools:overrideLibrary` you added for SDK 26. API 24-25 is not yet measured on a device.
2. Repositories: `google()`, `mavenCentral()` and GitHub Packages `https://maven.pkg.github.com/dungduong2899/appplugin` (credentials `gpr.user` / `gpr.key`, a PAT with `read:packages`) wrapped in `exclusiveContent { forRepository { … }; filter { includeGroup("com.app.plugin") } }` so the group resolves only there (dependency-confusion guard; `-Pappplugin.localRepo=<SDK>/build/repo` swaps in a local repo); add `https://artifacts.applovin.com/android` for `appplugin-max`.
3. Dependencies: `com.app.plugin:appplugin:<version>` (GMA Legacy, default) or, instead of it, `com.app.plugin:appplugin-nextgen:<version>` (GMA Next-Gen, from 2.4.0); plus `com.app.plugin:appplugin-game:<version>` (game features) and `com.app.plugin:appplugin-max:<version>` (AppLovin MAX, with `appplugin` only); same version for all four artifacts. `appplugin-max` + `appplugin-nextgen` is not supported (section 3).
4. Make your `Application` extend `AppPluginBase`; call `MonetizationSdk.configure(this) { … }` after `super.onCreate()`.
   Host Activity: from 2.4.1 the SDK recognises as host a non-ad-SDK Activity whose class name starts with `packageName` (old rule, kept) or that is the launcher Activity of `packageName` or lives in the `Application` class package, so `applicationIdSuffix`/flavor `applicationId` builds work; on every version since 8fcc3c1 (2026-09-11), including 2.3.0 and 2.4.0, such apps call `AppPluginManager.instance.setMainActivity(this)` in the main Activity `onCreate`.
5. License: `python3 tools/license_issue.py issue …` with the release, upload and Play App Signing certificate SHA-256; one token per `applicationId` (`.debug` needs its own); manifest meta-data `com.app.plugin.LICENSE`; keep the tokens in a git-ignored `license.properties` (commit only `license.properties.sample`), never in tracked files. Without it there are no ads and no brain (one log line `license: INVALID (<reason>)`). From 2.4.3 a `VALID` verdict is cached in a private prefs file (key = token, `applicationId`, signing certs, SDK public keys and SDK version; `exp` is still checked against the real clock; INVALID is never cached), so the first launch after upgrading or after `clearLocalData` verifies in full once; a new log line `license: source=cache|verify ms=… since_start=… cert=…` precedes `license: VALID`.
6. Manifest: AdMob `com.google.android.gms.ads.APPLICATION_ID`; if the merger reports `dataExtractionRules` also present at the AppsFlyer SDK, add `tools:replace="android:dataExtractionRules,android:fullBackupContent"` and merge AppsFlyer's rules into your own backup rules.
7. Firebase: put `google-services.json` in `app/` (required). Telemetry: declare meta-data `DATABUCKETS_BASE_URL` and `DATABUCKETS_API_KEY` or the SDK logs `telemetry off` and sends no events.
8. Consent: `AdsMobMy.instance.startSdkInit(…)` first and unconditionally, then `ConsentManager.gatherConsent(activity) { … }`; privacy options via `ConsentManager.showPrivacyOptions` (`com.app.plugin.consent`; the `ads.consent` forwarder is deprecated and removed in 3.0). The SDK manifest sets Consent Mode v2 to denied and FCM auto-init off until the consent tier allows them.
9. Ads/IAP/paywall/brain: call order in section 9; Remote Config keys in section 10.
10. Before release: R8 on, `bash tools/release_gate.sh --variant utility`, `bash tools/device_gate.sh --serial <s> --apk <release.apk> --app <applicationId>/<Activity>` (add `--mapping`; in `--app` mode it hard-fails only on FATAL, ANR, native crash, R8 strings and an unusable device, while the demo-only StrictMode, AppsFlyer and local-sink checks are informational), EEA test; upgrade notes in section 13.
11. Placement ids: the key in `ads_ids.xml` is the SDK prefix plus the name you pass (`showBanner("home", …)` reads `bn_home`, `showInterstitial("home", …)` reads `full_home`); a bare `home` gives `not pl config` and no ad.
12. EEA testing: `ConsentManager.debugForceEea = true` is not enough; put the device's UMP hashed id (printed in logcat as `addTestDeviceHashedId(…)`) in the string `admob_test_device` and set `flagTest` to `1`, otherwise no consent form appears.
13. Placement keys: the SDK adds `bn_`/`rect_`/`nt_`/`cl_`/`full_`/`gift_` (not `rwi_`: pass the full `rwi_<name>`; other rewarded names become `gift_<name>`). `setPostAdScreenCooldown` may be called any time after `super.onCreate()`, even before consent. `RemoveAdsMask` is public. A local telemetry sink needs a debug-only cleartext exception for `127.0.0.1` and the key `demo-utility-key`. Fork `api` dependencies are runtime-scope in the SDK, so modules declare their own.
14. Show-then-load and preload: `showInterstitial` on an unloaded placement returns `false` and only starts a load. Since 2.3.0 the SDK preloads the default interstitial, app open, rewarded and rewarded interstitial slots after consent (RC `ads_preload_android`), so non-default placements still need your own `load*ForThisTurn` before their first show and again after each show; calls for default placements are harmless but still recommended (the preload is one shot per process, is not retried after a no-fill, and uses `flagImg 0`) (the SDK reloads `full_default`, Full2, rwi and, since 2.4.0, the rewarded placement just shown instead of always `gift_default`; after a `full_splash`, `full_2_splash` or `full_intro_3` with its own key it reloads nothing). A bare placement name without its own key falls back to `*_default`; use `[A-Za-z0-9_]` and at most 20 characters. `showPrivacyOptions` now takes a `(ConsentTier) -> Unit` callback; read the license token from `license.properties`, then `findProperty`, then empty. Preload attaches your callback to an in-flight load, skips masked/disabled formats, and tags only its own `ad_request` with `trigger=preload`. App open on resume needs `AdsHelper.instance.setFlag4ShowResume(true)`; once set, the preloaded and reloaded `full_resume` can show on every eligible foreground. From 2.4.3, with RC `ads_preload_android.delay_ms` unset or 0, preload yields to `full_splash`/`nt_splash` while they load (1.5 s grace if none is seen, 10 s cap); apps without a splash start preload about 1.5 s later; `delay_ms` > 0 restores the 2.4.2 timing.
15. Optional policy brain (from 2.3.0, default off): meta-data `com.app.plugin.BRAIN_POLICY_URL`, `DATABUCKETS_API_KEY` and `com.app.plugin.INTEGRITY_CLOUD_PROJECT`; at most one `/policy` request per 24 hours per install (cross-process back-off, from 2.4.0) after the first `VALID` license verdict, and only when `brainEnabled` is true and the player is in the client-side rollout (otherwise `policy: off (brain)`); untested with real Play Integrity (section 15); test with `device_gate.sh --serial <s> --apk <utility apk> --mapping <mapping.txt> --policy`; `policy: off (<key>)` is logged once, only after a `VALID` license; the URL must be `https://` (`http://` only for the exact host `127.0.0.1`) and end with `/`, the project number must be > 0 and declared via `@string/...` (not a numeric literal), each install with the brain enabled and in the client-side rollout spends at most one classic Integrity call per 24 hours, none when the brain is off (default quota about 10k/day [P]: check or raise it before enabling), there is no consent wait and no retry within the window, and a server-applied policy makes RC `brain_policy_android` ignored. Calling `startSdkInit` inside the `gatherConsent` callback works but starts the ad network late.
16. Legacy or Next-Gen (from 2.4.0): `appplugin` uses GMA Legacy `play-services-ads` 25.4.0, `appplugin-nextgen` uses GMA Next-Gen `ads-mobile-sdk` 1.5.0 with the Meta adapter `facebook:6.22.0.1`; same public API, pick exactly one. Differences: Next-Gen initialises on its own `appplugin-gma-init` thread (loads issued before init completes are held, up to 16, then refused; held past 8 s; logs `gma: init done, run <n>`, `gma: ready <ms>ms`), uses large anchored adaptive banners, its `load` takes no Context (the `gma_load_app_context` flag has no effect and Unity mediation, which needs an Activity, is not measured), and it supports neither `appplugin-max` nor Bigo (`appplugin-max` + `appplugin-nextgen`: not supported; they need `play-services-ads`). Google's Legacy v25.x deprecation date is 2027-06-30. **`appplugin-nextgen` 2.4.0 is a preview, not for production use:** `ads-mobile-sdk` 1.5.0 proxies full-screen creative media through its own HTTP client (`ads_mobile_sdk.r60`) and buffers the whole body in heap with no cap, so a long progressive video creative (itag 22, about 34 min, no `range=`) caused `OutOfMemoryError` of 134-268 MB on a Samsung A25 in preload-only processes (Legacy streams via WebView and is not affected); there is no public switch and the SDK cannot catch it, so wait for a Google fix (track `ads-mobile-sdk` > 1.5.0); On API < 31, `ads-mobile-sdk` 1.5.0 logs `NoClassDefFoundError` for `PackageManager$OnChecksumsReadyListener` (an API 31 type) in class `am3`; ART rejects re-initialising the class and ads still run. Measured: API 26 none, API 28 yes (8 lines, no crash), API 33/37 none; API 29-32 not measured. No minSdk floor is proven; an app trying nextgen below API 31 may see this line (it comes from Google's SDK). `appplugin` (Legacy) stays the default. To move: swap the coordinate, add the global `exclude` of `play-services-ads` and `-lite`, drop `appplugin-max`, change the root class of any app layout that overrides an SDK native layout by name (`appplugin_admob_native_*` and `appplugin_layout_native_full_pager`) to the Next-Gen `nativead.NativeAdView`/`MediaView` (7 layout files, 6 names: `appplugin_admob_native_full` also has a `layout-land` variant; including the Full2 ones), accept that banner and rect need a registered Activity when the load returns, and migrate the app's own GMA code (section 13, row `ng-app`).
17. Game + Next-Gen (from 2.4.0): there is no fifth artifact; `appplugin-game` pulls `appplugin`, so use `appplugin-nextgen` + `appplugin-game` with a `dependencySubstitution` rule `substitute(module("com.app.plugin:appplugin")).using(module("com.app.plugin:appplugin-nextgen:<version>"))` plus the global `exclude` of `play-services-ads` and `-lite` (section 17); a separate artifact may follow in a later release if the consumer-project check on `build/repo` finds a problem.
18. Fixed moments, placement switches, slots, referrer, data deletion (from 2.4.2): `showInterstitial`/`isInterstitialReadyToShow` take `fixedMoment` (skips segment guardrail, session/interval rules, N-screen cooldown, brain; hard blocks stay); `AdsHelper.instance.isPlacementEnabled(placement)` and RC `cf_ads_placement` `{"x":{"flag":-1}}` replace the fork's ads_placement_android (`screen_switch_interval` stays an app-owned RC key; removing a key re-enables the placement); Full2 is now named `full_2_<called placement>` and picks slot `full_2_<pl>` > `full_2_default` > `full_default`; the guardrail has a local default via integer resource `ads_cf_seg_cadence_guardrail_seconds` and `showOpenAd("full_splash")` is exempt; Install Referrer is read after consent (not before, unlike the fork); `MonetizationSdk.clearLocalData(context)` clears local SDK data but keeps purchased entitlements.
19. 2.4.3 (no required work): faster cold start (license verdict cache, post-verdict rerun at the head of the main queue, preload yields to splash); preloaded native ads older than `fullscreen_max_age_minutes` (60 min default) are replaced and report `ad_show_fail reason=ad_expired`; optional integer resource `ads_cf_admob_init_wait_ms` (GMA Legacy only, default off: absent/0 keeps waiting for the init callback; 1-8000 releases held AdMob loads N ms after GMA `initialize` was called).
20. 2.4.4 (no required work): one request per ad unit, keyed by the unit id actually requested (a load of a unit that another slot has in flight waits for that request, at most 10 s in total, then goes out; an eCPM floor ladder `id_high;id_all` still falls through to `id_all` at once; only real load failures start a 2-16 s backoff, timeouts do not; when every id of a turn is in backoff, a retry of the same slot and placement fails at once with no request (through AdsHelper you get the helper's `list empty`; only rewarded interstitial passes `unit_backoff` through), so delay retries by at least 2 s; rollback: integer resource `ads_cf_unit_gate_off` = 1); native ad requests ask for media aspect ratio ANY (a 1.91:1 or 1:1 image may now arrive; with your own layout call `setImageScaleType(FIT_CENTER)` on the GMA MediaView if you do not want cropping; with the SDK layouts set `ads_cf_native_media_ratio` = 1; rollback or override: integer resource `ads_cf_native_media_ratio` 1 = by screen orientation, as in 2.4.3, 2 = LANDSCAPE, 3 = PORTRAIT); held AdMob loads now run inside the GMA init callback (Next-Gen: posted to the front of the main queue).
21. 2.5.0 (no required work): the per-unit gate orders turns SHOW > APP > PRELOAD, and a higher-priority turn takes the unit from a lower-priority turn of another slot that has not sent its request yet (the displaced turn re-queues; `ads_cf_unit_gate_prio_off` = 1 reverts; a PRELOAD waiter still goes out at the 10 s cap); app open (not `full_splash`) waits `openAdDeltaTime` (default 15 s) after any interstitial, Full2, rewarded or rewarded interstitial (`ads_cf_open_gap_own_only` = 1 reverts); Remote Config `ads_policy_android.ads_cf_overrides` sets the four `ads_cf_*` switches (a present RC key wins over the app resource, even 0; removing the key falls back to the resource; offline keeps the last saved values; do not send 0 for a key your app sets to 1; `unit_gate_off` and `unit_gate_prio_off` take effect from the next process start, `native_media_ratio` and `open_gap_own_only` immediately; RC can only revert or pick the existing media-ratio enum); `AdsHelper.setAutoShowOpenAdOnForeground(false)` stops only the SDK auto-show of `full_resume` (your own `showOpenAd` still works; the value is not persisted, call it on every start); `full_resume` needs its own key in `admob_openad` (placements are separated by `#`); resource `ads_cf_native_no_reload` (absent = the 3 old placements, present even if empty = exactly that list of `nt_*` names); a native layout without `ad_media` no longer crashes. Dagger/Hilt before 2.60 with kapt: force `org.jetbrains.kotlin:kotlin-metadata-jvm` to 2.3.0 on `kapt*` configurations (the Meta adapter `com.google.ads.mediation:facebook` 6.21.0.4 declares kotlin-stdlib 2.3.0).
22. 2.5.1 (no required work): the Firebase `ad_impression` the SDK logs now carries `ad_placement` (the placement that was shown, falling back to the slot); register a GA4 custom dimension `ad_placement` (event scope) for eCPM by placement. Set `ads_admob_firebase_linked` = 1 only after the AdMob console or GA4 Publisher ads confirms the AdMob-Firebase link: a log line `origin=am,name=ad_impression(_ai)` only proves GMA writes `_ai` on the device, not the console link, and setting 1 while unlinked drops AdMob revenue from Firebase entirely. For a linked app, integer resource `ads_cf_placement_event` = 1 (resource only, Remote Config cannot set it) makes the SDK log `ad_placement_impression` (`ad_placement`, `ad_unit_name`, `ad_format`, `ad_source`, `ad_platform`, `imp_id`, `ad_value`, `currency`, never `value`, so GA4 does not double-count); register custom metric `ad_value`. `ads_cf_native_no_reload` entries without the `nt_` prefix are normalised to `nt_<entry>` with one `logE` each (before 2.5.1 they never matched).
