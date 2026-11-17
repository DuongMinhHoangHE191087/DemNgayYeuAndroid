# U-07 - Spike: dựng InLoveViewModel dưới Robolectric

## Câu hỏi

Có dựng được `InLoveViewModel(application)` dưới Robolectric với `ApplicationProvider` và đọc được giá trị đầu của `selectedTab` mà chỉ cần code phía test (không sửa production, không thêm dependency)? Nếu không, ghi `RESULT: FALLBACK:fake-deps` và dùng Fake sau U-50b.

## Kết quả ngắn

Dựng được ngay lần chạy đầu tiên, không cần vượt chướng ngại nào. Test chỉ cần `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])` (giống `ExampleRobolectricTest`) và `ApplicationProvider.getApplicationContext<Application>()`. Không cần `WorkManagerTestInitHelper`, không cần `FirebaseApp.initializeApp`, không cần Application riêng cho test, không cần `Dispatchers.setMain`.

Lưu ý trung thực: quy trình TDD "viết test, thấy nó đỏ" đã không xảy ra vì test xanh ngay lần đầu. Các chướng ngại bên dưới là chướng ngại ĐÃ PHÂN TÍCH từ code, được giải thích vì sao không xuất hiện, không phải lỗi đã gặp và đã sửa.

## Đã thử

1. Test tối thiểu: `InLoveViewModel(ApplicationProvider.getApplicationContext<Application>())` rồi `assertEquals(0, viewModel.selectedTab.value)`. Xanh.
2. Thêm test thứ hai trong cùng lớp (cùng JVM): dựng VM lần nữa, `shadowOf(Looper.getMainLooper()).idle()` để các `viewModelScope.launch` trong `init` chạy (seed dữ liệu mặc định, `scheduleAllAnniversariesFromDb`, các `collect`), rồi đọc `selectedTab.first()`. Xanh, `system-err` rỗng.

Không thử các cách vượt chướng ngại (test Application, WorkManagerTestInitHelper, FirebaseApp giả) vì không có chướng ngại nào xuất hiện.

## Chướng ngại và cách xử lý

| Chướng ngại | Có xuất hiện? | Lý do / cách vượt |
|---|---|---|
| Firebase init | Không | `InLoveApplication.onCreate()` thoát sớm khi `Class.forName("org.robolectric.Robolectric")` tồn tại (`isRunningInTest`), nên `FirebaseApp.initializeApp` và App Check không chạy. VM không gọi Firebase lúc dựng: `OnlineCoupleRepository` chỉ lấy Firestore khi `FirebaseApp.getApps(context).isNotEmpty()`; `AppServiceLocator.initialize` cũng bọc điều kiện đó (`fs = null`); `FirebaseMemoryMediaRepository.functions` là `by lazy`. Không có lỗi `NameNotFoundException` của Firebase Sessions vì Firebase không bao giờ được khởi tạo. |
| WorkManager | Không | `SyncWorker.enqueuePeriodic` (gọi `WorkManager.getInstance`) chỉ nằm trong `InLoveApplication.onCreate()`, vốn bị bỏ qua trong test. `InLoveViewModel.init` không đụng WorkManager. Không cần `WorkManagerTestInitHelper` (dù `androidx.work.testing` đã là test dependency). |
| AppServiceLocator | Không | `InLoveViewModel.init` tự gọi `AppServiceLocator.initialize(application)`, tạo `BillingManager`, `EntitlementRepository`, `AdsManagerImpl`, `NetworkMonitor`, `FirebaseMemoryMediaRepository`, `SyncCoordinator`. Cả bảy dựng được dưới Robolectric. `BillingManager` gọi `BillingClient.startConnection` nhưng không có Play Store giả nên không có callback, không lỗi. |
| Room | Khác kế hoạch | Kế hoạch nói "Room trong bộ nhớ" nhưng VM luôn dùng `AppDatabase.getDatabase(application)` (singleton `INSTANCE`, `Room.databaseBuilder` ra file). Dưới Robolectric đó là file SQLite trong thư mục tạm của Robolectric, chạy được. Không có cách thay bằng DB in-memory từ code test nếu không sửa production. |
| Main dispatcher | Không | `viewModelScope` dùng `Dispatchers.Main.immediate`; Robolectric có main looper nên không cần `Dispatchers.setMain`. |

## Test và lệnh đã chạy

Test: `app/src/test/java/com/example/ui/viewmodel/InLoveViewModelSmokeTest.kt` (2 test: dựng VM + đọc `selectedTab.value == 0`; dựng VM thứ hai sau `idle()` + đọc `selectedTab.first() == 0`).

Lệnh (từ gốc repo, PowerShell):

```
$env:JAVA_HOME='C:\Users\os\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2'; $env:GRADLE_OPTS='-Dorg.gradle.daemon=false'
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.ui.viewmodel.InLoveViewModelSmokeTest"
```

Đuôi kết quả Gradle:

```
> Task :app:testDebugUnitTest
> Task :app:finalizeTestRoborazziDebug SKIPPED
BUILD SUCCESSFUL in 31s
53 actionable tasks: 3 executed, 50 up-to-date
```

`TEST-com.example.ui.viewmodel.InLoveViewModelSmokeTest.xml`: `tests="2" skipped="0" failures="0" errors="0"`, `system-err` rỗng.

## U-49 (InLoveViewModelHarness) phải copy

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ... {
  private fun newViewModel() =
    InLoveViewModel(ApplicationProvider.getApplicationContext<Application>())
  // ... shadowOf(Looper.getMainLooper()).idle() khi cần cho viewModelScope.launch trong init chạy
}
```

Không thêm Application riêng, không khởi tạo Firebase, không khởi tạo WorkManager. Không gọi `FirebaseApp.initializeApp` trong harness: nó làm xuất hiện lại lỗi Firebase Sessions mà `InLoveApplication.isRunningInTest` đang tránh (xem comment ở đó).

## UNVERIFIED (chưa kiểm chứng)

- Chỉ đọc `selectedTab`. Chưa kiểm chứng các flow phụ thuộc Room/Auth/Billing (`milestones`, `authState`, `isVip`, ...) cho giá trị đúng; chưa chạy một flow có `WhileSubscribed` được subscribe.
- Hai test chạy đúng thứ tự khai báo; chưa thử thứ tự đảo ngược hoặc chạy lẫn với các test khác trong cả bộ `:app:testDebugUnitTest`.
- Singleton `AppDatabase.INSTANCE` và `AppServiceLocator` sống qua các test trong cùng JVM (giữ `Context` của test đầu); test thứ hai vẫn xanh nhưng chưa kiểm tra dữ liệu có rò rỉ giữa các test. Harness U-49 nên cân nhắc việc này nếu cần dữ liệu sạch, nhưng reset chúng cần thay đổi production (ví dụ hàm reset cho test) và đó là quyết định ngoài phạm vi U-07.
- Chưa chạy trên SDK khác 34.

RESULT: PASS
METRIC vm_constructs_in_robolectric 1 >= 1
