# Spike P0: Compose Multiplatform trong iosMain (F-05, S3)

RESULT: S3 WARN

Nửa Windows xanh: classpath Android của `:shared` không có chữ `compose` nào, classpath iOS có runtime/foundation/ui/material3, `:app` vẫn dùng `androidx.compose.ui:ui:1.7.0`. Nhưng phải thêm một cách tránh lỗi (loại compiler plugin khỏi classpath Android), và chưa biên dịch được mã Compose thật nào (`iosMain` rỗng, đúng chủ ý; cần Mac/F-06). Vì vậy WARN.

## Câu hỏi
Có thể khai báo plugin `kotlin.compose` + `jetbrains.compose` và các thư viện Compose CHỈ trong `iosMain` của `:shared` mà classpath Android của `:shared` không dính `compose`, `:app` không đổi, và klib iOS vẫn biên dịch trên Windows không?

## Phiên bản
Kotlin 2.4.21, Compose Multiplatform 1.12.1 (runtime/foundation/ui), material3 1.12.0-alpha03, skiko 0.150.1 (kéo theo), AGP 9.2.1, Gradle 9.6.0, JDK 21 (Adoptium). Số phân giải lấy từ `:shared:dependencies --configuration iosSimulatorArm64CompileKlibraries`.

## Đã làm (theo thứ tự)
1. `shared/build.gradle.kts`: thêm `alias(libs.plugins.kotlin.compose)` và `alias(libs.plugins.jetbrains.compose)` vào `plugins {}`; thêm `iosMain.dependencies { implementation(libs.compose.runtime / foundation / ui / material3.multiplatform) }` trong `kotlin { sourceSets {} }`. Chỉ dùng alias có sẵn, 4 thư viện là đủ cho spike (không thêm resources, icons, lifecycle, coil).
   Source set `iosMain` hiện sẵn nhờ hierarchy template mặc định (có `iosMainImplementation`, `kotlinCompilerPluginClasspathMetadataIosMain` trong `:shared:dependencies`), không cần tạo.
2. Chạy nghiệm thu 1: xanh ngay, output `androidCompileClasspath` có 0 chữ `compose`. Plugin `jetbrains.compose` không tự kéo thư viện vào classpath Android.
3. Chạy nghiệm thu 2: ĐỎ. `:shared:compileAndroidMain` FAILED với
   `IrGenerationExtensionException: ... IncompatibleComposeRuntimeVersionException: The Compose Compiler requires the Compose Runtime to be on the class path, but none could be found.`
   Nguyên nhân: plugin `kotlin.compose` gắn compiler plugin vào MỌI compilation; compilation Android (có mã commonMain/spike) không có Compose runtime nên compiler plugin báo lỗi. `compileCommonMainKotlinMetadata` thì không lỗi (không sinh IR).
4. Cách xử lý (không đổi kế hoạch, giữ nghiệm thu 1): loại JAR compiler plugin khỏi các cấu hình plugin của target Android:
   `configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspathAndroid") }.configureEach { exclude(group = "org.jetbrains.kotlin", module = "kotlin-compose-compiler-plugin-embeddable") }`
   (khớp `kotlinCompilerPluginClasspathAndroidMain` và `...AndroidHostTest`). Sau đó `testAndroidHostTest` xanh, không có cảnh báo mới ngoài cảnh báo `-Xexpect-actual-classes` đã biết từ F-04.
5. Kiểm classpath iOS: `iosSimulatorArm64CompileKlibraries` có `compose.runtime:1.12.1`, `compose.foundation:1.12.1`, `compose.ui:1.12.1`, `compose.material3:1.12.0-alpha03` (và `skiko-iossimulatorarm64:0.150.1`); không thấy cảnh báo compileSdk nào ở nửa Windows.

## Lệnh và kết quả
Env: `JAVA_HOME=C:\Users\os\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2`, `GRADLE_OPTS=-Dorg.gradle.daemon=false`.
1. `. scripts/kmp/gate-tasks.env; d=$(./gradlew :shared:dependencies --configuration "$ANDROID_CLASSPATH_CONFIG") && ! echo "$d" | grep -qi compose` -> exit 0 (0 dòng chứa `compose`).
2. `. scripts/kmp/gate-tasks.env; ./gradlew :shared:compileCommonMainKotlinMetadata ":shared:${HOST_TEST_TASK}" --rerun` -> `BUILD SUCCESSFUL in 18s`, `25 actionable tasks: 2 executed, 23 up-to-date`. (Lần chạy không có exclude: `BUILD FAILED`, như mục 3 ở trên.)
3. `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -q "androidx.compose.ui:ui:1.7.0"` -> exit 0.
4. `bash scripts/kmp/android-green.sh` -> `BUILD SUCCESSFUL in 20s`, `74 actionable tasks: 74 up-to-date`, `schema-check OK`, exit 0 (`:app` không phụ thuộc `:shared`, kết quả dựa trên bộ nhớ đệm).
5. `grep -Eq` kiểm dòng kết quả S3 (PASS hoặc WARN) ở đầu doc -> khớp.
Tự kiểm: `./gradlew :shared:compileKotlinIosSimulatorArm64 --rerun-tasks` -> `BUILD SUCCESSFUL in 34s` (chạy thật `kspKotlinIosSimulatorArm64`, `compileKotlinIosSimulatorArm64`). `SpikeDbAndroidTest`: `tests="1" skipped="0" failures="0" errors="0"` (XML mới sau `--rerun`).

## UNVERIFIED (không chạy được)
- Biên dịch mã `iosMain` thật dùng Compose + `platform.UIKit` (`MainViewController.kt`/`ComposeUIViewController`): chưa có file nào, thuộc F-06, cần Mac.
- Link framework `Shared` có Compose/skiko, chạy simulator, hiển thị `Text("InLove")`.
- `iosArm64` chưa chạy (chỉ `iosSimulatorArm64`).
- Compose compiler plugin thực sự chạy trên iOS với mã có `@Composable` (iosMain rỗng nên chưa có mã nào để biến đổi).
- Không biết `exclude` plugin có còn đủ khi thêm mã `@Composable` vào source set mà target Android cũng biên dịch (xem cảnh báo F-24).
- Hành vi compileSdk 37 của Compose 1.12.1: không bị kích hoạt ở đây vì module Android không có dependency Compose; chưa thử đưa Compose vào Android.

## Cảnh báo cho task sau
- F-06 (Mac): tạo `iosMain/.../MainViewController.kt` với `ComposeUIViewController { Text("InLove") }`; các dep cần (`runtime`, `foundation`, `ui`, `material3`) đã khai báo trong `iosMain`. Nếu dùng `@Composable` trong iosMain thì compiler plugin còn gắn ở iOS nên ổn; không dùng `@Composable` trong `commonMain`/`androidMain` khi chưa sửa mục dưới.
- F-22 và các task kiểm classpath: dùng `androidCompileClasspath`; nó phải sạch `compose` tới khi F-24. Khai báo Compose bằng `iosMain.dependencies { ... }` (không dùng `commonMain` trước F-24).
- F-24 (đưa Compose sang `commonMain`): phải BỎ khối `exclude(... kotlin-compose-compiler-plugin-embeddable)` trong `shared/build.gradle.kts`, đồng thời Android sẽ nhận Compose, nên: gate `FORBID_COMPOSE_ON_ANDROID_CLASSPATH=1` phải đổi (quyết định của kế hoạch, không phải của task này) và cần quyết định compileSdk. Cố định `compileSdk 36.1` hiện tại của `:shared` có thể bị Compose 1.12.1 (yêu cầu compileSdk 37, máy chưa cài) từ chối; U-08 đã ghi `FALLBACK:cmp-1.9` (material3 1.9.0 là bản stable cuối), việc đổi sang 1.9 là quyết định sau.
- U-63 (và mọi nơi khai báo compose): gồm hai plugin `kotlin.compose` + `jetbrains.compose` trong `shared/plugins {}`; không khai báo lại thư viện Compose ở `androidMain`/`commonMain` nếu muốn giữ nghiệm thu classpath sạch.
- Giữ cảnh báo `-Xexpect-actual-classes` (Beta) như F-04, chưa xử lý.

## F-07: chuyển :app sang Compose 1.12.1 + :shared (nửa còn lại, bản sao tạm)

RESULT: S3 PASS

Thử trên bản sao tạm của snapshot `C:\kmpf07`. CHƯA XÓA được: công cụ chặn lệnh xóa đường dẫn đó, nên thư mục (bản sao mã nguồn, log, gói `platform-37.0_r02.zip`) còn nằm ngoài repo và chủ máy cần xóa tay. Repo thật không bị đổi gì ngoài tài liệu này và `p0-compose-switch.patch`.

compile-errors: 0
roborazzi-diffs: 4
duplicate-classes: 0
time-spent-days: 2
m3-alpha-fallback: no
compile-sdk-required: 37

Ghi chú số đo:
- compile-errors: số lỗi biên dịch Kotlin sau khi đổi phiên bản và thêm `implementation(project(":shared"))`. Chỉ có cảnh báo deprecation (`TabRow`, `tabIndicatorOffset`) và một cảnh báo cũ `Unnecessary safe call` ở SettingsScreen.kt.
- roborazzi-diffs: `compareRoborazziDebug` báo 4/4 ảnh khác golden. Trên bản gốc (trước khi đổi) compare đã báo 3 ảnh khác: `greeting` và hai ảnh topbar (topbar không có `diff_percentage`, tức kích thước ảnh khác golden), còn `paywall_footer` khi đó không đổi. Vậy hai ảnh topbar đã cũ từ trước, `greeting` lệch 0.0057% y như bản gốc, `paywall_footer` lệch 0.7442% là lệch mới. Ảnh diff cho thấy nền đổi từ xám nhạt sang gần trắng, nút/chữ/bố cục không đổi: khớp với thay đổi màu surface của Material3 1.5.0-alpha22. Không xóa test hay golden nào; chưa ghi lại (record) golden, cần người duyệt.
- duplicate-classes: `:app:checkDebugDuplicateClasses --rerun` BUILD SUCCESSFUL, không có "Duplicate class".
- time-spent-days: ước lượng cho việc chuyển cả ứng dụng (0 lỗi biên dịch, nhưng phải nâng compileSdk ở `:app`, `:shared` và CI, duyệt 4 golden, giữ Coil 2). Công sức thực tế của spike nhỏ hơn nhiều.
- m3-alpha-fallback: không phải dùng phương án dự phòng CMP 1.9.x / M3 1.9.0.

## Đã làm (theo thứ tự)
1. Nâng `gradle/libs.versions.toml`: lifecycle*, activityCompose, composeBom, navigationCompose; thêm `androidxMaterial3` và gắn version tường minh cho `androidx-compose-material3`. `coilCompose` giữ 2.7.0.
2. `app/build.gradle.kts`: thêm `implementation(project(":shared"))`.
3. `shared/build.gradle.kts`: chuyển 4 dependency Compose từ `iosMain` sang `commonMain`; bỏ khối `iosMain.dependencies`; bỏ khối `exclude(... kotlin-compose-compiler-plugin-embeddable)` của F-05 (không còn cần vì Android có Compose runtime).
4. Lỗi gặp: `:app:checkDebugAarMetadata` FAILED (14 mục, cùng một nguyên nhân): AAR Compose 1.12.1 và material3 1.5.0-alpha22 yêu cầu compileSdk 37. Sửa: cài platform 37 (xem dưới) rồi đặt `compileSdk { version = release(37) }` ở `:app` và `:shared`. `targetSdk` giữ 36.
5. Lỗi gặp: `:app:validateSigningDebug` FAILED vì thiếu `debug.keystore` và `appplugin/local.properties` trong bản sao tạm (chỉ là vấn đề của bản sao, repo thật có đủ).
6. Không phải sửa mã nguồn Kotlin nào.

## Phiên bản đã dùng
- Androidx Compose BOM: 2026.09.00 (Compose 1.12.1).
- material3 (androidx, `:app`): 1.5.0-alpha22 (khai báo tường minh, BOM chỉ cho 1.4.0). Quyết định: dùng bản alpha, không lùi về 1.9.x.
- Compose Multiplatform (`:shared`): 1.12.1; `material3` CMP 1.12.0-alpha03 (trên Android là redirect sang androidx material3 1.5.0-alpha22).
- lifecycle*: 2.11.0; activityCompose: 1.13.0; navigationCompose: 2.10.2.
- coilCompose: 2.7.0, không đổi (Coil 2 vẫn biên dịch được; chưa đo việc chuyển sang Coil 3).
- Kotlin 2.4.21, AGP 9.2.1, Gradle 9.6.0, JDK 21 giữ nguyên.

Thay đổi trên máy cục bộ (ngoài repo): cài platform `platforms;android-37.0` rev 2 (API 37.0, extension 22) vào `C:\Users\os\AppData\Local\Android\Sdk\platforms\android-37.0`. Nguồn: `https://dl.google.com/android/repository/platform-37.0_r02.zip`, 67281901 byte, sha1 `ed8ebf7f8822a4de5686d427f237d2fa30ff7410` (khớp `repository2-3.xml`). `package.xml` do tôi viết theo mẫu của `android-36.1`. Không xóa sau spike; máy chạy build/unit test với patch này phải có platform này.

## Lệnh và kết quả
Chạy trong bản sao tạm với `JAVA_HOME` JDK 21 Adoptium, `-Dorg.gradle.daemon=false`:
- `.\gradlew.bat :app:assembleDebug`: BUILD SUCCESSFUL.
- `.\gradlew.bat :app:compileDebugUnitTestKotlin` và `:app:testDebugUnitTest`: 267 test, 0 fail.
- `.\gradlew.bat :shared:compileKotlinIosSimulatorArm64`: BUILD SUCCESSFUL (chỉ biên dịch klib, không link/chạy).
- `.\gradlew.bat :app:compareRoborazziDebug`: 4 ảnh khác golden (xem trên).
- `.\gradlew.bat :app:checkDebugDuplicateClasses --rerun`: BUILD SUCCESSFUL, 0 trùng.
- Patch: `docs/superpowers/spikes/p0-compose-switch.patch` (3 tệp: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `shared/build.gradle.kts`), áp dụng được lên cây làm việc hiện tại (`git apply --check` pass). Phải sinh lại nếu F-22/F-24 sửa `shared/build.gradle.kts`.

## UNVERIFIED
- Chưa chạy gì trên Mac/iOS: chưa link framework, chưa chạy ứng dụng iOS, chưa kiểm tra hiển thị Compose trên iOS.
- Chưa chạy ứng dụng Android trên thiết bị/giả lập; chỉ biên dịch, unit test và Roborazzi.
- Chưa đo Coil 3, chưa đo việc chuyển UI sang `commonMain` (spike chỉ thêm dependency, không di chuyển mã).
- Chưa kiểm CI (GitHub Actions) với compileSdk 37.

## Cảnh báo cho F-22/F-24/U-63
- compileSdk 37 là bắt buộc cho cả `:app` và `:shared` (Compose 1.12.1 và material3 1.5.0-alpha22). Mọi máy dev/CI phải cài `platforms;android-37.0` trước, nếu không `checkDebugAarMetadata` và unit test fail. Giữ `targetSdk` 36.
- Thứ tự gợi ý: nâng phiên bản trong `libs.versions.toml`, nâng compileSdk, thêm `project(":shared")` vào `:app`, đưa Compose sang `commonMain`, bỏ khối exclude F-05. Làm khối exclude sau cùng hoặc cùng lúc với việc đưa Compose sang `commonMain` (nếu bỏ sớm khi Android chưa có Compose runtime sẽ bị `IncompatibleComposeRuntimeVersionException`).
- Gate `FORBID_COMPOSE_ON_ANDROID_CLASSPATH` trong `scripts/kmp/gate-tasks.env` phải đổi từ 1 sang 0 khi áp patch này, vì `androidCompileClasspath` của `:shared` giờ có Compose.
- Giữ `androidx-compose-material3` ở 1.5.0-alpha22 tường minh (BOM chỉ cho 1.4.0, và CMP material3 trỏ tới 1.5.0-alpha22). Rủi ro: là bản alpha; phương án dự phòng (CMP 1.9.x, M3 1.9.0) vẫn còn nhưng không cần tới.
- Golden Roborazzi: duyệt `paywall_footer` (đổi tông nền do M3 alpha), và hai golden topbar đã cũ từ trước. Cần quyết định record lại hay không; spike không record.
- Coil 2.7.0 vẫn chạy với Compose 1.12.1; chuyển sang Coil 3 (đa nền tảng) là việc riêng, chưa đo.
- Không động tới các guard chống bypass đăng ký (subscription) khi áp patch.
