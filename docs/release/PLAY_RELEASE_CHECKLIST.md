# InLove — Checklist phát hành Google Play

Ngày kiểm tra: 26/10/2026. Đây là checklist chuẩn bị, chưa phải hướng dẫn nộp bài. Thay thế bản cũ ngày 10/10/2026.
Nhãn nguồn: [WF] = đã mở trang chính thức bằng WebFetch cùng ngày; [R] = đối chiếu trong repo; [U] = chưa xác minh, owner kiểm tra trước khi nộp.
Không ghi vào tài liệu này: giá trị khoá, mật khẩu, license token, ID AdMob thật, applicationId, Team ID, email. Tên biến và tên file thì được ghi.

## 1. Trạng thái và căn cứ

- Repo đang có thay đổi chưa commit. Checklist mô tả trạng thái làm việc hiện tại, không phải bản đã phát hành. Không commit, push hay tạo PR trong đợt này [R].
- Lượt viết tài liệu này không chạy Gradle hay test. Số liệu build và test phải lấy từ đợt build thật [U].
- Bản build hiện tại: `DataResidencyPolicy.current = LOCAL_ONLY`, là hằng số biên dịch, không phải Remote Config [R].
- Kỷ niệm, ảnh và video không đồng bộ lên server. Ảnh và video không được tải lên vì `cloudContent` = false [R].
- Hồ sơ (`users/{uid}`) và ghép đôi chạy online [R].
- Đã bỏ khỏi checklist cũ vì không còn đúng hoặc chưa xác nhận lại: số test ngày 10/10/2026; "ảnh qua Cloudinary"; "Crashlytics chỉ khi có consent"; "Analytics và FCM tắt"; Room v12 và Migration12To13Test; "phạm vi đồng bộ, kế hoạch bước 4-10"; "JDK 25"; "Gradle 9.4.1"; "config-cache tắt".

## 2. Tài khoản và Play Console

- [ ] Owner tạo tài khoản nhà phát triển Google Play và thanh toán phí đăng ký một lần. Mức phí kiểm trong Play Console [U].
- [ ] Hoàn tất xác minh danh tính theo yêu cầu của Play Console [U].
- [ ] Chọn loại tài khoản, cá nhân hoặc tổ chức. Tên người bán trên store lấy theo tài khoản này [U].
- [ ] Tài khoản cá nhân mới: kiểm yêu cầu thử nghiệm kín (closed test) trước khi lên production. Số người thử và số ngày kiểm trong Play Console [U].
- [ ] Tải AAB lên track kiểm thử nội bộ trước, rồi thêm người thử [U].
- [ ] Không ghi thông tin đăng nhập Play Console vào repo.

## 3. Đầu vào build, ký ứng dụng và bí mật

- Keystore upload nằm ngoài git. `.gitignore` chặn `*.jks`, `*.keystore`, `debug.keystore`, `.env`, `.env.*` (trừ `.env.example`) và `local.properties` [R].
- Release guard (`app/build.gradle.kts`, dòng 265-281) chỉ chạy khi task graph có `bundleRelease`, `assembleRelease` hoặc `packageRelease`; lint và test không bị chặn [R]. Guard kiểm: `ADMOB_APP_ID_RELEASE` và `ADMOB_BANNER_ID_RELEASE` là thuộc tính Gradle (`-P`), đúng định dạng ID AdMob; biến môi trường `STORE_PASSWORD` và `KEY_PASSWORD` không rỗng; file keystore tồn tại, đường dẫn lấy từ biến môi trường `KEYSTORE_PATH`, mặc định `my-upload-key.jks` ở thư mục gốc repo [R].
- Guard KHÔNG kiểm `APPPLUGIN_LICENSE` và `LEGAL_BASE_URL` [R]. Owner kiểm tay trước khi build, hoặc bổ sung kiểm tra vào guard [U].
- SDK nội bộ appplugin 2.4.2 không nằm trong repo. Build cần bản clone appplugin, tìm theo thứ tự: biến `APPPLUGIN_DIR`, rồi `./appplugin`, rồi `../appplugin` [R: `settings.gradle.kts` dòng 50-53]. Máy build phải có bản clone này.
- `APPPLUGIN_LICENSE`: để trống thì bản build không hợp lệ nhưng không crash [R]. Chú thích trong build script ghi license gắn với applicationId và chữ ký [R]. Chữ ký Play App Signing khác khoá upload. Nhà cung cấp phải xác nhận license chạy đúng với bản do Play phân phối [U]. Đây là điểm chặn.
- `LEGAL_BASE_URL`: đọc qua `getEnv` (`app/build.gradle.kts` dòng 18-31). `.env.example` nạp trước, `.env` (nếu có) ghi đè [R]. `.env.example` đã có một URL HTTPS không rỗng, nên giá trị mặc định ở dòng 47 không được dùng [R]. Giá trị thực tế không ghi trong tài liệu này. Trước khi build release, owner xác nhận giá trị đó là domain do owner kiểm soát và đã host ba trang ở mục 11 [U]. Nếu `.env` đặt `LEGAL_BASE_URL` rỗng, ba đường dẫn thành `/privacy`, `/terms`, `/delete-account` không có domain, vì `LegalLinks` không có phương án dự phòng [R].
- Play App Signing: sau khi tạo app trong Play Console, copy SHA-1 và SHA-256 của chứng chỉ ký vào giới hạn khoá Google API và cấu hình Firebase (`docs/release/SECRETS_AND_CONFIG.md` dòng 29). Không ghi giá trị vào tài liệu [R].
- `app/google-services.json` đang được git theo dõi, và `.gitignore` không chặn file này [R]. Đây là cấu hình client Firebase, không phải khoá bí mật. Owner quyết định có giữ trong repo không, và kiểm khoá API đã giới hạn đúng chưa [U]. Không in nội dung file.
- `.env.example` có các biến mật khẩu seed với giá trị rỗng [R]. Giữ rỗng.
- Tài khoản test: nút đăng nhập thử nằm trong `if (BuildConfig.DEBUG)` (`AuthScreen.kt`, quanh dòng 594), và `fetchTestFixtureByDocId` ném lỗi khi không phải bản debug [R]. Bản debug vẫn chứa tài khoản mẫu có mật khẩu viết sẵn trong mã. Chú thích trong `AuthRepository.kt` ghi không có thông tin đăng nhập cứng trong APK, nên chú thích hoặc mã phải được sửa [R]. Chú thích trong `AuthScreen.kt` nói mật khẩu test từng bị xoá khỏi lịch sử git. Chưa kiểm chứng; nếu đúng, đổi mật khẩu tài khoản test trên Firebase [U].

## 4. Khai báo an toàn dữ liệu (Data safety)

Bảy nhóm dưới đây cũng có trong `IOS_RELEASE_PREP.md` mục 3, để hai bản khớp nhau. Theo định nghĩa của Play, dữ liệu chỉ xử lý trên máy không tính là thu thập [U: đối chiếu trang Data safety]. Vẫn phải mô tả trong chính sách quyền riêng tư (mục 11).

| Nhóm | Dữ liệu trong bản hiện tại | Nguồn | Trạng thái |
|---|---|---|---|
| Tài khoản | Email, uid. Email và mật khẩu chỉ dùng để đăng nhập và khôi phục. Xoá tài khoản trong app | Firebase Auth | [R] thu thập khi có tài khoản. Chế độ khách có tồn tại; xác nhận không tạo tài khoản [U] |
| Hồ sơ, ghép đôi | `users/{uid}`: tên, ngày sinh, giới tính, tiểu sử, sở thích. Qua lời mời ghép đôi: tên, ngày sinh, ngày kỷ niệm, lời nhắn, tiểu sử. Liên kết `relationships`, `invites`, `coupleCodes` | Firestore | [R] |
| Chẩn đoán lỗi | Nhật ký crash, định danh cài đặt | Crashlytics, khai báo trong appplugin | [R] thu thập theo mặc định, không có cổng consent. Khai báo, hoặc thêm cổng consent rồi khai báo theo cổng [U] |
| Analytics, messaging | Sự kiện trong app, định danh | appplugin khai báo Firebase Analytics, FCM và AppsFlyer | [R] khai báo trong build. [U] SDK có vào APK không, có tự khởi tạo không, có cổng consent không. Cùng câu hỏi với `IOS_RELEASE_PREP.md` mục 3. |
| Quảng cáo | Advertising ID, dữ liệu quảng cáo, chia sẻ với đối tác | AdMob, UMP; appplugin khai báo adapter Meta và Meta Audience Network | [R] Meta khởi tạo sau consent, theo chú thích build. [U] quyền AD_ID và danh sách đối tác nhận dữ liệu |
| Mua hàng | Trạng thái mua, purchase token, xác minh qua Cloud Functions | Google Play Billing, Cloud Functions | [R] luồng. [U] cách lưu trữ và deploy |
| Kỷ niệm, ảnh, video, ghi chú kỷ niệm | Ảnh và video không gửi lên server của InLove. Auto Backup của Android sao lưu thư mục media, có thể lên tài khoản Google; DB và khoá đăng nhập bị loại trừ. Lời nhắn trong lời mời ghép đôi không thuộc dòng này, xem Hồ sơ, ghép đôi | Android Auto Backup | [R] ảnh và video không tải lên (`cloudContent` = false). [U] ghi chú kỷ niệm nằm trong DB Room (không sao lưu) hay trong thư mục media (có sao lưu). [U] cách mô tả Auto Backup trong Data safety |

Lưu ý thêm:
- Firebase AI được khai báo nhưng app không gọi [R]. Nếu bỏ phụ thuộc này, không ảnh hưởng Data safety.
- Lời mời ghép đôi chứa ngày kỷ niệm và lời nhắn, tức là có dữ liệu gửi lên server. Phải khai báo trong dòng Hồ sơ, ghép đôi [R].

Checklist:
- [ ] Rà từng dòng với mã thật và merged manifest của bản release trước khi khai báo.
- [ ] Xác nhận dữ liệu truyền qua HTTPS [U: chưa rà].
- [ ] Đối chiếu với chính sách quyền riêng tư và copy (mục 7.4).

## 5. Quyền và khai báo nhạy cảm

- Quyền trong manifest [R]: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `VIBRATE`, `RECEIVE_BOOT_COMPLETED`, `com.android.vending.BILLING`.
- `POST_NOTIFICATIONS` được xin lúc chạy trong `MainActivity` (dòng 240-243) [R].
- `RECEIVE_BOOT_COMPLETED` dùng cho `BootCompletedReceiver` trong gói alarm [R].
- Không có quyền `READ_MEDIA_*`. Chọn ảnh qua `PickVisualMedia` [R].
- `USE_EXACT_ALARM` đang bị comment trong manifest [R]. Quyền này chỉ dành cho app báo thức và đồng hồ [WF]. Không bật.
- `com.google.android.gms.permission.AD_ID` không được khai báo trong manifest của app [R]. AdMob có thể thêm quyền này khi hợp nhất manifest. Kiểm trong merged manifest bản release. Nếu có, khai báo sử dụng Advertising ID trong Play Console [U].
- Nếu Play Console có mục khai báo riêng cho quyền báo thức chính xác, điền theo mục 6 [U].

## 6. Báo thức chính xác

- App khai báo `SCHEDULE_EXACT_ALARM`. Từ Android 13, quyền này không được cấp sẵn cho đa số app mới cài [WF]. App chỉ đặt báo thức chính xác khi `canScheduleExactAlarms()` trả về true (`AlarmNotificationScheduler.kt` dòng 233), và có đường dự phòng khi bị từ chối (dòng 202) [R].
- App không có luồng xin quyền `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` [R].
- Quyết định của owner. Mặc định của checklist này: giữ quyền, khai báo lý do nếu Play hỏi, và chấp nhận nhắc nhở có thể trễ khi quyền bị tắt [U]. Lựa chọn khác: thêm luồng xin quyền, hoặc bỏ báo thức chính xác.
- [ ] Kiểm trên máy: tắt quyền báo thức chính xác, xác nhận nhắc nhở vẫn đến (có thể trễ) và app không crash [U: chưa chạy].

## 7. Store listing

### 7.1 Tiêu đề và mô tả ngắn
- Tiêu đề EN: 25 ký tự. VI: 21 ký tự. Guide ghi 27 và 22, sai [R]. Đếm từ file guide bằng PowerShell.
- Giới hạn tiêu đề và mô tả ngắn theo guide: 30 và 80 [U: kiểm trong Play Console].
- Mô tả ngắn EN: 77 ký tự. Có cụm "shared memories", sửa theo mục 7.4.
- Mô tả ngắn VI: 74 ký tự. Guide ghi 75, sai [R]. Có cụm "lưu ảnh kỷ niệm cùng người ấy", sửa theo mục 7.4.
- Tên App Store (dùng khi làm iOS): 25 và 21 ký tự. Phụ đề: 27 và 27. Số trong guide đúng ở phần phụ đề [R].
- Đếm lại sau khi sửa copy, trước khi nộp.

### 7.2 Ảnh chụp và video
- [ ] Ảnh chụp lấy từ bản build thật. Không dựng cảnh tính năng chưa có, như widget hay album đồng bộ.
- [ ] Ảnh 1 có chú thích "ngay trên màn hình chính" (EN và VI). Bỏ hoặc sửa, vì không có widget màn hình chính [R].
- [ ] Ảnh 3 (album) có chú thích "đồng bộ cho cả hai" (EN và VI). Sửa theo LOCAL_ONLY.
- [ ] Video demo chỉ quay màn hình có thật trong bản build.
- [ ] Icon 512 x 512, feature graphic 1024 x 500, tối thiểu hai ảnh chụp điện thoại: yêu cầu của Play Console [U: kiểm thông số hiện hành].
- [ ] Ảnh chụp khớp ngôn ngữ của listing (EN và VI).

### 7.3 URL và liên hệ
- Chính sách quyền riêng tư: `LEGAL_BASE_URL` + `/privacy` [R: `LegalLinks.kt`].
- Điều khoản: `LEGAL_BASE_URL` + `/terms` [R].
- Xoá tài khoản: `LEGAL_BASE_URL` + `/delete-account` [R]. Khai báo URL này trong Play Console, mục xoá tài khoản [U: vị trí mục trong Console].
- Cả ba trang phải mở được qua HTTPS, không cần đăng nhập [U: chưa kiểm trang đã host].
- Quyết định domain pháp lý (D15) nằm trong `docs/superpowers/plans/2026-10-08-release-execution-plan.md`, dòng 52 và 207; trạng thái ghi là một phần [R].
- Email hỗ trợ: owner điền trong Play Console. Không ghi trong tài liệu [U].

### 7.4 Copy phải sửa trước khi nộp
Các câu sau không khớp với bản build hiện tại (`LOCAL_ONLY`, không có widget, nhắc nhở theo mốc thật):
- "updated live on your home screen" (EN) và "cập nhật ngay trên màn hình chính" (VI) trong mô tả đầy đủ; chú thích ảnh 1. Không có widget [R].
- "7 ngày trước" áp cho mọi mốc, kể cả 100 ngày. Thực tế: 7 ngày chỉ có cho ngày kỷ niệm và ngày tự thêm (công tắc 7, 3, 1 ngày). Mốc 100 ngày chỉ có 3 ngày, 1 ngày và đúng ngày [R: `UserItemRules.kt`, `ReminderModels.kt`]. Valentine's Day có trong lịch ngày lễ [R], nhưng mức nhắc của ngày lễ cố định chưa xác nhận là 7 ngày [U].
- "cloud-synced album", "synced across both your devices", "album đồng bộ trên đám mây", "album kỷ niệm chung", "shared memory album" trong mô tả đầy đủ EN và VI. Kỷ niệm lưu trên máy [R].
- "keep your shared timeline, memories, and gift plans in sync on both phones" (EN) và "giữ dòng thời gian, kỷ niệm, kế hoạch quà tặng luôn đồng bộ trên cả hai điện thoại" (VI). Chỉ hồ sơ và ghép đôi chạy online [R]. Viết lại đúng phạm vi.
- Mô tả ngắn EN "shared memories" và VI "lưu ảnh kỷ niệm cùng người ấy": sửa theo cùng nguyên tắc.
- "shared gift checklist" (EN) và "checklist quà tặng chung" (VI): chưa kiểm xem có đồng bộ giữa hai người không [U].
- Promo text App Store, guide dòng 156-157: "shared memory album" và "album kỷ niệm chung" [R]. Sửa khi làm iOS (`IOS_RELEASE_PREP.md` mục 7).
- Guide mục 2.1, ảnh 4, tham chiếu "Section 3" nhưng guide không có mục 3 [R]. Sửa tham chiếu.
- Sau khi sửa, đối chiếu lại với mục 4 và chính sách quyền riêng tư.

## 8. Đối tượng, nội dung và quảng cáo

- [ ] Đối tượng mục tiêu: người từ 18 tuổi trở lên [U: owner quyết định]. Không chọn nhóm có trẻ em, trừ khi tuân thủ chính sách Gia đình [U].
- [ ] Phân loại nội dung: trả lời bảng câu hỏi IARC trong Play Console [U].
- [ ] Khai báo app có quảng cáo. Bản hiện tại chỉ có banner, và banner chỉ hiện khi không phải VIP, có `canRequestAds` và có ID quảng cáo [R: `AdsManagerImpl.kt`]. Interstitial và app-open tắt bằng hằng số [R].
- [ ] EEA và UK: cấu hình thông báo consent trong AdMob (Privacy & messaging) [U].
- [ ] Quyền truy cập ứng dụng cho người review: dùng tài khoản thật do owner quản lý, không dùng tài khoản mẫu. Collection `test_fixtures` bị khoá trong `firestore.rules` [R]. Ghi cách vào tính năng ghép đôi và VIP trong phần ghi chú [U]. Không đưa mật khẩu vào repo hay tài liệu.
- [ ] Nếu người review cần thử mua, thêm tài khoản vào license testers [U].

## 9. Billing và sản phẩm

- Billing Library 8.1.0 [R]. Hạn dùng Billing Library 8 trở lên đã qua (31/8/2026) [WF].
- ID sản phẩm phải khớp chính xác `VipProductIds` [R]: `vip_monthly` (đăng ký), `vip_yearly` (đăng ký), `vip_lifetime` (sản phẩm một lần). Checklist cũ ghi sản phẩm thiếu thì nút mua bị tắt; chưa kiểm lại [U].
- Ưu đãi dùng thử: chú thích trong code ghi `vip_yearly` có dùng thử 3 ngày [R]. Ưu đãi phải được tạo trong Play Console và khớp với code [U].
- Xác nhận giao dịch: `BillingManager` thử xác nhận tối đa 3 lần [R]. Chú thích ghi giao dịch chưa xác nhận sẽ được hoàn trong 3 ngày [R].
- Xác minh phía server: Cloud Functions gọi `androidpublisher` để cập nhật quyền VIP [R]. Đã deploy chưa: [U].
- [ ] Mua thử bằng license tester cho ba sản phẩm, kiểm mua, khôi phục và huỷ đăng ký [U: chưa chạy].
- Liên kết quản lý đăng ký có trong `LegalLinks` [R].

## 10. Android 16 và yêu cầu kỹ thuật

- `minSdk` 24, `targetSdk` 36, `compileSdk` 36 (minor 1) [R].
- `versionCode` 1, `versionName` "1.0.0" [R]. Nếu đã có bản tải lên trước đó trong Play Console, phải tăng `versionCode` [U].
- AGP 9.2.1, Kotlin 2.4.21, Gradle wrapper 9.6.0, bytecode Java 17 [R]. Checklist cũ ghi Gradle 9.4.1 và JDK 25, đã thay. JDK đang chạy Gradle [U].
- Hành vi Android 16 [WF]: target 36 bắt buộc edge-to-edge và không còn cờ tắt. Back dự đoán có cờ tắt tạm thời. Hạn chế hướng màn hình và khả năng đổi kích thước bị bỏ qua từ 600 dp trở lên.
- App không khoá hướng màn hình và không tắt back dự đoán [R]. Vẫn cần kiểm giao diện edge-to-edge: nội dung không bị thanh hệ thống che [U: chưa chạy trên Android 16].
- Hạn targetSdk tối thiểu của Play cho bản cập nhật: kiểm trong Play Console [U].
- Căn chỉnh 16 KB: app module không có thư viện native (`jniLibs`) [R]. Thư viện native trong AAR bên thứ ba chưa kiểm [U]. Ghi chú tra cứu trước đó ghi mốc 1/2/2027; kiểm lại trước khi nộp [U].
- Room v14, các schema 12-14 đã xuất [R]. Nếu đã có bản phát hành trước đó, kiểm migration [U].

## 11. Pháp lý và xoá tài khoản

- [ ] Chính sách quyền riêng tư: công khai, HTTPS, không cần đăng nhập [U]. Nội dung khớp mục 4, gồm: kỷ niệm lưu trên máy; Android backup có thể khôi phục kỷ niệm khi đổi máy; thông tin ghép đôi qua lời mời; quảng cáo; crash log; dữ liệu mua hàng [R: chuỗi `settings_privacy_local_body` và mục 4].
- [ ] Điều khoản sử dụng tại `/terms` [U].
- Xoá tài khoản: luồng xoá trong app có sẵn [R: `RemoteAccountCleanup.kt`, hook `onAccountDeleting`]. Trang `/delete-account` phải nêu dữ liệu nào bị xoá, dữ liệu nào giữ lại và thời hạn giữ [U].
- Hàm Cloud Functions cho ghép đôi nguyên tử và xoá tài khoản: trạng thái deploy chưa xác nhận [U]. Checklist cũ ghi cần gói Blaze [U]. Thiếu hàm này thì luồng xoá tài khoản không đủ.
- Đăng nhập Facebook qua OAuth web của Firebase [R]. Nếu giữ, cần URL hướng dẫn xoá dữ liệu theo yêu cầu của Meta [U]. Nếu bỏ, gỡ nút và cấu hình liên quan [U].
- Domain pháp lý (D15) còn ở trạng thái một phần theo plan 2026-10-08 [R]. Không nộp khi ba trang chưa có [U].

## 12. Kiểm thử và build trước khi nộp

Lệnh, chạy ở thư mục repo có bản clone appplugin (mục 3):

```
./gradlew :app:testDebugUnitTest :app:lintRelease
./gradlew :app:bundleRelease -PADMOB_APP_ID_RELEASE=<ID_APP_ADMOB> -PADMOB_BANNER_ID_RELEASE=<ID_BANNER_ADMOB> -PAPPPLUGIN_LICENSE=<LICENSE>
npm --prefix scripts run test:rules
npm --prefix firebase/functions test
```

- `STORE_PASSWORD` và `KEY_PASSWORD`: biến môi trường của hệ điều hành, không phải `-P` và không nằm trong `.env`. `bundleRelease` cần cả hai [R: `build.gradle.kts` dòng 65, 67, 276]. Không ghi giá trị vào tài liệu hay repo.
- `KEYSTORE_PATH`: chỉ đặt khi keystore không nằm ở `my-upload-key.jks` trong thư mục gốc repo [R: dòng 63, 277].
- `ADMOB_APP_ID_RELEASE`, `ADMOB_BANNER_ID_RELEASE`, `APPPLUGIN_LICENSE`: truyền bằng `-P` như lệnh trên. Guard chỉ kiểm cặp ADMOB, không kiểm `APPPLUGIN_LICENSE` [R].
- `LEGAL_BASE_URL`: kiểm giá trị thực tế trước khi build, xem mục 3 [R].
- [ ] Unit test và lint không có lỗi. Cảnh báo lint không vượt ngưỡng owner chốt [U]. Ghi số liệu của đợt build này.
- [ ] Trong bản release: không có nút đăng nhập thử và không có tài khoản mẫu trong mã đã biên dịch [U: owner tự chọn cách kiểm; không in kết quả có mật khẩu].
- [ ] Smoke test trên bản release đã ký hoặc bản kiểm thử nội bộ: mở app; chế độ khách; đăng ký và đăng nhập email; ghép đôi online (tạo và nhận mã); ghép đôi khi offline báo lỗi rõ; mở màn VIP; banner chỉ hiện với người không VIP và sau consent; khoá PIN; đổi EN/VI; nhắc nhở đến (đúng giờ khi có quyền báo thức chính xác); xoá tài khoản thử trên tài khoản test [U: chưa chạy].

## 13. Việc owner cần làm và chặn

Chặn nộp, phải xong trước khi nộp:
- [ ] Xác nhận license appplugin hợp lệ với chữ ký Play App Signing (mục 3) [U].
- [ ] Host `/privacy`, `/terms`, `/delete-account` trên domain đã chốt, và chốt D15 (mục 7.3, mục 11) [U].
- [ ] Deploy Cloud Functions cần cho xác minh mua và xoá tài khoản (mục 9, mục 11) [U].
- [ ] Sửa copy theo mục 7.4.
- [ ] Hoàn tất Data safety (mục 4) sau khi kiểm merged manifest và SDK trong APK.
- [ ] Kiểm bản release không có tài khoản test (mục 12).
- [ ] Có tài khoản review thật (mục 8).

Quyết định của owner:
- [ ] Crashlytics: khai báo thu thập theo mặc định, hoặc thêm cổng consent (mục 4).
- [ ] Báo thức chính xác: giữ và khai báo, thêm luồng xin quyền, hoặc bỏ (mục 6).
- [ ] Đăng nhập Facebook: giữ (kèm URL xoá dữ liệu) hoặc bỏ (mục 11).
- [ ] Đối tượng từ 18 tuổi và phân loại nội dung (mục 8) [U].
- [ ] `google-services.json` trong git: giữ hay gỡ khỏi repo (mục 3).
- [ ] Ảnh chụp, icon, feature graphic (mục 7.2).
- [ ] Gói đăng ký, ưu đãi dùng thử và license tester trong Play Console (mục 9).
- [ ] Closed test cho tài khoản cá nhân, nếu áp dụng (mục 2).

Sửa mã, ngoài đợt tài liệu này:
- Nút đăng nhập thử và chú thích trong `AuthRepository.kt` (mục 3). Không chặn nếu bản release không có chuỗi tài khoản mẫu.
- `switchDemoUser` và `loginDemoUser`: không tìm thấy caller trong UI [R]. Xoá, hoặc xác nhận không có caller gián tiếp [U].
- `LEGAL_BASE_URL`: `.env.example` đã có URL HTTPS không rỗng (mục 3), nên mặc định trong `build.gradle.kts` dòng 47 không bao giờ được dùng. Guard release không kiểm biến này [R]. Cách sửa do owner chọn [U]: để `LEGAL_BASE_URL` rỗng trong `.env.example` và để guard chặn bản release khi thiếu, hoặc để guard kiểm giá trị là HTTPS hợp lệ.

Mặc định đang dùng (owner xác nhận hoặc đổi):
- Theme chỉ sáng, theo checklist cũ [U: chưa kiểm lại trong code].
- Chỉ banner; interstitial và app-open tắt [R].
- Domain pháp lý theo `LEGAL_BASE_URL` cho tới khi D15 chốt.
- Giữ `SCHEDULE_EXACT_ALARM` và khai báo lý do.
