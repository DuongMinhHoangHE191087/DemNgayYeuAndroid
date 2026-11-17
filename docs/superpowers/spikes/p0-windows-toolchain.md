# Spike P0: toolchain Windows cho F-01 (Kotlin 2.4.21)

RESULT: S1 WARN

Xanh, nhưng cần một sửa phía test (hai file Migration) vì lỗi của Room 2.8.x trên Windows. Cần người quyết định xem có giữ cách sửa này không.

## Câu hỏi
`:app` có xanh khi dùng Kotlin 2.4.21 + KSP 2.3.12 + Room 2.8.5 + coroutines 1.11.0 trên máy Windows này không?

## Phiên bản trong `gradle/libs.versions.toml` sau khi đổi
- kotlin 2.4.21, googleDevtoolsKsp 2.3.12
- roomRuntime, roomKtx, roomCompiler 2.8.5
- kotlinxCoroutinesTest, kotlinxCoroutinesAndroid, kotlinxCoroutinesCore 1.11.0
- Thêm (chỉ khai báo, `:app` chưa dùng): composeMultiplatform 1.12.1, composeMaterial3Multiplatform 1.12.0-alpha03,
  composeIconsMultiplatform 1.7.3, androidxSqlite 2.7.1, lifecycleMultiplatform 2.11.0, kotlinxDatetime 0.8.0,
  kotlinxSerialization 1.11.0, coil 3.6.3, ktor 3.6.0, cùng các alias thư viện ở mục 4.2.
- Plugin: thêm `kotlin-multiplatform`, `android-kotlin-multiplatform-library`, `jetbrains-compose`, `androidx-room`,
  `kotlin-serialization`; bỏ `kotlin-android`, `android-library`. Root `build.gradle.kts` đổi sang các alias đó (`apply false`).
- Giữ nguyên: agp 9.2.1, composeBom 2024.09.00, ba khóa lifecycle, activityCompose, navigationCompose, coilCompose,
  robolectric 4.16.1, roborazzi 1.59.0. `app/build.gradle.kts` không đổi.

## Kết luận từng giả định
- A18 (KSP 2.3.12 + Kotlin 2.4.21): đo được, không cần phương án dự phòng. `:app:kspDebugKotlin` BUILD SUCCESSFUL.
- A49 (Compose compiler 2.4.21 + Compose runtime 1.7.0 / BOM 2024.09.00): đo được, không cần dự phòng. `:app:assembleDebug` xanh, giữ BOM 2024.09.00.
- A50 (Roborazzi, Robolectric, secrets, google-services, crashlytics, included build `appplugin`): chạy được với Kotlin 2.4.21,
  không phải nâng plugin nào. 267 trên 267 test xanh sau sửa test Migration.

## Lỗi của Room 2.8.5 trên Windows và cách sửa phía test
Trước khi sửa, 3 test fail: `Migration12To13Test.migrate12To13_preservesExistingRows_andAddsSyncColumns`,
`Migration13To14Test.migrate13To14_keepsCoupleProfileRow_andAddsPreferenceColumns`, `Migration13To14Test.migrate12To14_fullChain`.

```
java.lang.IllegalArgumentException: This driver is configured to open a database named 'migration-test'
but 'C:\Users\os\AppData\Local\Temp\robolectric-...\com.aistudio.inlove.kmrv-dataDir\databases\migration-test' was requested.
  at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:48)
  at androidx.room.testing.SupportSQLiteMigrationTestHelper$SupportTestConnectionManager.openConnection(MigrationTestHelper.android.kt:559)
  at androidx.room.testing.MigrationTestHelper.createDatabase(MigrationTestHelper.android.kt:250)
```

Nguyên nhân (đọc mã nguồn `sqlite-framework-android` 2.6.2, giống hệt ở 2.7.1): `SupportSQLiteDriver.open` so tên DB bằng
`substringAfterLast('/')`. Room 2.8 luôn mở qua driver, với đường dẫn tuyệt đối từ `getDatabasePath(name).absolutePath`; trên Windows
đường dẫn dùng dấu `\` nên so khớp sai. Room 2.7.0 không đi qua driver cho `FrameworkSQLiteOpenHelperFactory`, nên trước đây xanh.
Room 2.8.5 là bản mới nhất, chưa có bản vá.

Đã thử và hỏng (không để lại trong cây):
1. Ghim `room-testing` 2.7.2: Gradle vẫn ép lên 2.8.5 (constraint của room-runtime 2.8.5).
2. `resolutionStrategy.force` room-testing 2.7.2 cho classpath test: `NoClassDefFoundError: androidx/room/driver/SupportSQLiteDriver`
   (room-testing 2.7.x không tương thích nhị phân với room-runtime 2.8.5).

Cách sửa đã dùng (phía test, F-01 sở hữu hai file này):
- Constructor dùng `SQLiteDriver` của `MigrationTestHelper` (có `AndroidSQLiteDriver`) trả `SQLiteConnection`, không phải `SupportSQLiteDatabase`,
  nên dùng nó buộc viết lại toàn bộ thân test (`execSQL`, `query`). Vì vậy không dùng, để giữ nguyên thân test.
- Thay vào đó `MigrationTestHelper` vẫn dùng bản legacy, nhưng truyền `WindowsSafeOpenHelperFactory` (định nghĩa `internal` trong
  `Migration12To13Test.kt`, dùng cả ở `Migration13To14Test.kt`). Nó bọc `FrameworkSQLiteOpenHelperFactory` và báo `databaseName`
  là đường dẫn tuyệt đối, nên phép so của `SupportSQLiteDriver` thành công. Delegate vẫn mở đúng file cũ.
- Thân test, schema 12/13/14, các migration và assertion giữ nguyên. Không `@Ignore`, không xóa test, không ghim Room 2.7.x.
- File đổi: `app/src/test/java/com/example/data/db/Migration12To13Test.kt`, `app/src/test/java/com/example/data/db/Migration13To14Test.kt`.

Cảnh báo cho các task sau (KMP/androidHostTest): mọi test migration dùng `FrameworkSQLiteOpenHelperFactory` với
`MigrationTestHelper` sẽ gặp đúng lỗi này trên Windows. Plan mô tả các test đó với factory ấy ở `p2-data-domain.md` dòng 159-160 và
`p7-quality-release.md` dòng 166-167 (không sửa các file plan ở đây). Cần dùng `WindowsSafeOpenHelperFactory`-kiểu bọc, hoặc constructor `SQLiteDriver`.

## Acceptance đã chạy
1. `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`: `BUILD SUCCESSFUL in 3m 6s`, `74 actionable tasks: 74 executed`;
   cộng từ XML kết quả: 267 test, 0 skipped, 0 failed.
2. `^kotlin = "2.4.21"` trong `gradle/libs.versions.toml`: PASS.
3. `buildEnvironment` có `org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21`: PASS.
4. `bash scripts/kmp/schema-check.sh`: `schema-check OK`. Room không sinh lại 14.json khác byte, không phải khôi phục; `git status app/schemas` sạch.
5. `bash scripts/kmp/android-green.sh` (đầy đủ): `BUILD SUCCESSFUL in 20s`, `74 actionable tasks: 74 up-to-date`, rồi `schema-check OK`, exit 0.
6. Đúng một dòng `RESULT:` (S1 WARN).

## Cảnh báo Gradle và compiler (chưa so với trước khi đổi)
- `app/build.gradle.kts:116: 'fun srcDirs(vararg srcDirs: Any)' is deprecated. Use directories mutable set instead.` (có sẵn, không liên quan Kotlin).
- Nhiều `w:` trong test: `This declaration needs opt-in ... ExperimentalCoroutinesApi`; `field operation: PendingIntent? is deprecated`.
  Không biết cái nào mới, vì chưa chạy bản đối chứng với Kotlin 2.2.10.
- D8: `The companion object Companion could not be found` cho `play-services-recaptchabase`, `af-android-sdk`.
- `Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.`

## UNVERIFIED (không chạy)
- Bản legacy `MigrationTestHelper` + `FrameworkSQLiteOpenHelperFactory` không bọc có chạy trên Mac/Linux không (suy từ mã nguồn: dấu `/` khớp, nhưng chưa chạy).
- Bản `androidx.sqlite` mới hơn 2.7.1 có sửa so khớp đường dẫn không (2.7.1 vẫn lỗi; chưa kiểm bản khác).
- Constructor `SQLiteDriver` với `AndroidSQLiteDriver` dưới Robolectric (chưa thử vì buộc viết lại thân test).
- Các mục tiêu iOS, module `:shared`, `:shared:tasks --all`.
- Build release, `lint`, test instrumented, Roborazzi record/verify.
- Các plugin `jetbrains-compose`, `androidx-room`, `kotlin-multiplatform`, `kotlin-serialization`, `android-kotlin-multiplatform-library`
  được root `plugins {}` resolve thành công, nhưng chưa áp dụng vào module nào.

## F-02: module `:shared`, A24, A25 và tên task

Đã làm: `include(":shared")` trong `settings.gradle.kts`; `shared/build.gradle.kts` theo khung mục 4.3 (target `android` qua plugin
`com.android.kotlin.multiplatform.library`, `iosArm64`, `iosSimulatorArm64`; không Compose, không cinterop, không `iosX64`, không `jvm()`);
`kotlin.native.ignoreDisabledTargets=true` trong `gradle.properties`. `compileSdk` 36 minor 1 và `minSdk` 24 giống `:app`; Xmx 3072m giữ nguyên,
cấu hình `:shared` không hết bộ nhớ. Không có file nguồn nào trong `:shared` (task không có nguồn đều NO-SOURCE).

### A24: cờ `kotlin.native.ignoreDisabledTargets=true`
Đo được: chỉ riêng cờ này là đủ. `:shared:tasks --all` trên Windows xanh (`BUILD SUCCESSFUL in 18s`), vẫn liệt kê đủ task iOS
(`compileKotlinIosSimulatorArm64`, `iosSimulatorArm64Test`, `linkDebugFrameworkIosSimulatorArm64`, ...). Không cần bọc
`OperatingSystem.current().isMacOsX`. Biến thể cuối cùng: chỉ cờ, khai báo target Apple vô điều kiện.
Task `link*` và `iosSimulatorArm64Test` bị SKIPPED trên Windows (đã thử `linkDebugFrameworkIosSimulatorArm64`: `SKIPPED`, build vẫn xanh).

### A25: bài thử âm và klib iOS
- (a) Bài thử âm: tạo tạm `shared/src/commonMain/kotlin/com/example/spike/NegProbe.kt` có `import java.time.LocalDate` và dùng `LocalDate.now()`.
  `:shared:compileCommonMainKotlinMetadata` FAILED với `e: ...NegProbe.kt:3:8 Unresolved reference 'java'` và `5:26 Unresolved reference 'LocalDate'`.
  Metadata compile bắt được `java.time` trong `commonMain`. Đã xóa file và cả thư mục `shared/src`; `git status --short -uall shared` chỉ còn `shared/build.gradle.kts`.
- (b) `:shared:compileKotlinIosSimulatorArm64` (klib) CHẠY ĐƯỢC trên Windows: lần đầu tải Kotlin/Native 2.4.21 (`downloadKotlinNativeDistribution`).
  Khi chưa có nguồn thì task là `NO-SOURCE` nên không chứng minh gì; vì vậy đã thêm tạm một file hợp lệ `KlibProbe.kt` (`fun klibProbe(): Int = 1`)
  và task chạy thật cùng `kspKotlinIosSimulatorArm64`, `BUILD SUCCESSFUL in 30s`, resolve được `room-runtime` và `sqlite-bundled` cho target iOS.
  File tạm này cũng đã xóa. `IOS_KLIB_ON_WINDOWS=yes`: klib iOS vào được GATE-A. Vẫn không có binary Apple (link SKIPPED), nên GATE-M trên Mac vẫn cần.

### Tên task thật (từ `:shared:tasks --all`, ghi trong `scripts/kmp/gate-tasks.env`)
- `HOST_TEST_TASK=testAndroidHostTest` (cũng có `testAndroid`, `allTests`); `DB_TEST_TASK` mặc định bằng nó, `DB_TEST_CLASS=com.example.spike.SpikeDbAndroidTest`.
- `IOS_TEST_TASK=iosSimulatorArm64Test`, `IOS_LINK_TASK=linkDebugFrameworkIosSimulatorArm64`, `IOS_KLIB_TASK=compileKotlinIosSimulatorArm64`.
- `ANDROID_CLASSPATH_CONFIG=androidCompileClasspath`. Plan mục 4.5 ghi `androidMainCompileClasspath` là SAI: Gradle báo
  `configuration 'androidMainCompileClasspath' not found`. Trên `androidCompileClasspath`: không có `appplugin`, không có `compose`.
- `:shared:compileCommonMainKotlinMetadata` và `:shared:testAndroidHostTest` xanh (test là `NO-SOURCE`).

### Cảnh báo cho các task sau
- F-04: `testAndroidHostTest` đang `NO-SOURCE`, chưa chạy test thật nào. Cảnh báo lỗi Room 2.8.5 trên Windows ở trên (đường dẫn `\`) áp dụng cho host test của `:shared`.
- F-05, F-22: dùng `androidCompileClasspath` thay cho `androidMainCompileClasspath` khi kiểm classpath; kiểm Compose bằng tên thật.
- `android-green.sh` chạy cache `74 up-to-date` sau khi thêm `:shared`, nên `:app` xanh dựa trên bộ nhớ đệm; `:app` không bị ảnh hưởng vì build file của `:app` không đổi.

negative-probe-java-time: rejected

RESULT: S1 PASS
