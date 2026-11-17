# Spike P0: Room KMP trong `:shared` (F-04, S4, A43)

RESULT: S4 WARN

Nửa Windows xanh nhưng phải dùng `AndroidSQLiteDriver` trong test, vì `BundledSQLiteDriver` không nạp được thư viện native trên host Windows. Vì vậy chưa có bằng chứng `BundledSQLiteDriver` chạy trên Windows JVM; chưa chạy gì trên iOS.

## Câu hỏi
Room 2.8.5 trong `commonMain` của `:shared` (`@ConstructedBy`, `expect object ... : RoomDatabaseConstructor`, `BundledSQLiteDriver`) có biên dịch cho target `android` và klib iOS, và test DB có chạy được trên Windows không? Nếu host test của target `android` không nạp được thư viện native của `BundledSQLiteDriver` thì dùng phương án dự phòng nào của A43?

## Phiên bản
Kotlin 2.4.21, KSP 2.3.12, Room 2.8.5 (`room-runtime`, `room-compiler`), `androidx.sqlite:sqlite-bundled` 2.7.1, coroutines 1.11.0, Robolectric 4.16.1, `androidx.test.ext:junit` 1.3.0, JUnit 4.13.2, AGP 9.2.1 (plugin `com.android.kotlin.multiplatform.library`). JDK 21 (Adoptium) cho Gradle.

## Đã làm (theo thứ tự)
1. TDD: viết trước `commonTest/.../SpikeDbScenario.kt` (`runSpikeScenario(db)`: chèn chuỗi `tôi yêu em`, đọc lại bằng `all().first()`, `assertEquals(1, rows.size)` và `assertEquals("tôi yêu em", rows.single().text)`) và `androidHostTest/.../SpikeDbAndroidTest.kt` (`insertAndReadBack`).
   Chạy lần 1: `:shared:compileAndroidHostTest` FAILED `Cannot infer type for type parameter 'T'` / `Unresolved reference 'size'` (thiếu `SpikeDatabase`): fail đúng lý do.
2. Viết `commonMain/.../SpikeDatabase.kt`: `SpikeRow` (`spike_row`), `SpikeDao` (`insert`, `all(): Flow`), `SpikeDatabase` (version 1, `exportSchema = true`, `@ConstructedBy(SpikeDatabaseConstructor::class)`),
   `expect object SpikeDatabaseConstructor : RoomDatabaseConstructor<SpikeDatabase>` và hàm `withBundledDriver()` (`setDriver(BundledSQLiteDriver())`) để mã commonMain giữ `BundledSQLiteDriver` cho cả hai nền tảng.
   `shared/build.gradle.kts`: thêm `androidHostTest` deps (junit, androidx.junit, robolectric); `kspIosArm64` và `kspIosSimulatorArm64` thêm qua `configurations.matching { it.name == name }.configureEach { ... }` nên thiếu cấu hình thì bỏ qua, không lỗi.
3. Chạy lần 2 với `Room.inMemoryDatabaseBuilder<SpikeDatabase>(ApplicationProvider...)` + `withBundledDriver()`: test FAILED (thực sự chạy, `tests=1 failures=1`):
   `java.lang.UnsatisfiedLinkError: no sqliteJni in java.library.path` tại `androidx.sqlite.driver.bundled.NativeLibraryLoader.loadLibrary(NativeLibraryLoader.android.kt:44)`.
   Nguyên nhân: biến thể Android của `sqlite-bundled` chỉ chứa `.so` cho ABI Android; host test là JVM Windows. Giả định A43a (jar JVM có sẵn dll cho Windows) đúng cho biến thể `jvm`, không đúng cho biến thể `android` mà host test của target `android` dùng.
4. A43 phương án (1) biến thể: trong TEST thay driver bằng `androidx.sqlite.driver.AndroidSQLiteDriver` (Robolectric, `inMemoryDatabaseBuilder` nên không có đường dẫn, không dính lỗi `\` của Room 2.8.x trên Windows ở `p0-windows-toolchain.md`). Mã `commonMain` vẫn dùng `BundledSQLiteDriver`. Test xanh.

## Phương án A43 cuối cùng
(1) với driver khác trong test (`AndroidSQLiteDriver`). KHÔNG thêm `jvm()`; `gate-tasks.env` không đổi: `DB_TEST_TASK=testAndroidHostTest`, `DB_TEST_CLASS=com.example.spike.SpikeDbAndroidTest`.
Lý do: phương án (1) chạy được và đúng chỉ dẫn "ít thay đổi nhất". Đổi lại, test này không chạy `BundledSQLiteDriver`, nên không chứng minh driver production chạy được trên Windows.
Nếu muốn có bằng chứng đó trên Windows, phương án (2) (`jvm()` + `kspJvm` + `SpikeDbJvmTest`, `BundledSQLiteDriver` với dll Windows trong jar jvm) vẫn là lựa chọn, chưa làm vì (1) đã chạy.

## Lệnh và kết quả
- `.\gradlew.bat :shared:testAndroidHostTest --tests "com.example.spike.SpikeDbAndroidTest.insertAndReadBack" --rerun` (chạy qua `scripts/kmp/gate-tasks.env`):
  `BUILD SUCCESSFUL in 21s`; XML `shared/build/test-results/testAndroidHostTest/...SpikeDbAndroidTest.xml`: `tests="1" skipped="0" failures="0" errors="0"`.
- `shared/schemas/com.example.spike.SpikeDatabase/1.json` do Room sinh (tồn tại).
- `.\gradlew.bat :shared:compileCommonMainKotlinMetadata :shared:compileKotlinIosSimulatorArm64`: `BUILD SUCCESSFUL in 19s`, chạy thật `kspCommonMainKotlinMetadata`, `kspKotlinIosSimulatorArm64`, `compileKotlinIosSimulatorArm64`; Room KSP sinh `SpikeDatabaseConstructor` actual cho iOS và klib biên dịch được trên Windows.
- `bash scripts/kmp/android-green.sh`: `BUILD SUCCESSFUL`, `74 actionable tasks: 74 up-to-date`, `schema-check OK`, exit 0. `:app` không phụ thuộc `:shared` và không bị sửa, nên kết quả này dựa trên bộ nhớ đệm; không chạy lại `--rerun-tasks` vì không đụng thứ gì ảnh hưởng `:app`.
- Cảnh báo mới: `'expect'/'actual' classes ... are in Beta. Consider using the '-Xexpect-actual-classes' flag` (ở `SpikeDatabase.kt` và `SpikeDatabaseConstructor.kt` sinh ra). Chưa thêm cờ.

## UNVERIFIED (không chạy)
- Chạy DB này trên iOS simulator với `BundledSQLiteDriver` (`iosSimulatorArm64Test`, cần Mac, GATE-M).
- `BundledSQLiteDriver` chạy được trên Windows JVM (host test Android không nạp được; chưa thử `jvm()`).
- `1.json` sinh từ KSP của iOS giống hệt từ KSP của Android (ở đây chỉ có KSP Android ghi ra file; KSP iOS biên dịch nhưng `copyRoomSchemas` báo `NO-SOURCE`, chưa so byte). Cần so trên Mac.
- KSP iOS (`kspIosArm64`, link, binary Apple) trên Mac; trên Windows chỉ `kspKotlinIosSimulatorArm64` và klib simulator được chạy, `iosArm64` chưa chạy.
- Tên bảng/kiểu cột trên SQLite iOS và nạp thư viện native bundled trong framework tĩnh `Shared`.

## Cảnh báo cho các task sau
- D-* (tầng dữ liệu): đừng giả định test DB dùng `BundledSQLiteDriver` trên host Windows; dùng `AndroidSQLiteDriver` (Robolectric) trong `androidHostTest` hoặc quyết định thêm `jvm()` (A43 phương án 2) rồi báo P2/P7. Mọi test DB nên dùng `Room.inMemoryDatabaseBuilder` và hàm kịch bản trong `commonTest` để chạy chung ở iOS.
- Test dùng file DB (kể cả migration) trên host Windows với Room 2.8.x gặp lỗi so tên DB của `SupportSQLiteDriver` (xem `p0-windows-toolchain.md`).
- `commonTest` có `runSpikeScenario` đang được `SpikeDbAndroidTest` dùng; khi F-15 xóa spike phải xóa cả `SpikeDatabase.kt`, `SpikeDbScenario.kt`, `SpikeDbAndroidTest.kt`, `shared/schemas/com.example.spike.SpikeDatabase/` và trả `DB_TEST_CLASS`/`DB_TEST_TASK` trong `gate-tasks.env` về test thật.
- F-22 (và task kiểm classpath): `androidHostTest` giờ có deps robolectric/junit/androidx.junit; kiểm classpath dùng `androidCompileClasspath`, không dính các deps test này.
- Cấu hình `kspIos*` dùng `configurations.matching`; khi F-15/F-24 sửa `dependencies {}` giữ cách này nếu còn build trên Windows.
