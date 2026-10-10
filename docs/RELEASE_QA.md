# InLove — Cổng QA phát hành

**Trạng thái: NOT STARTED** (cập nhật 2026-10-08). Chỉ ghi kết quả khi đã chạy thật; không đánh dấu PASS theo suy đoán.

Kế hoạch đầy đủ: `docs/superpowers/plans/2026-10-08-release-execution-plan.md`.

## 1. Cổng lệnh (T9)

Chạy theo thứ tự từ thư mục gốc repo. Ghi lại exit code và số liệu.

| # | Lệnh | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | `.\gradlew.bat :app:testDebugUnitTest` | PASS (2026-10-08, `--rerun`, GRADLE_EXIT=0) | 30 file XML, 112 test, 0 fail, 0 error, 1 skipped (`Migration12To13Test`, `@Ignore`) |
| 2 | `npm --prefix scripts run test:rules` | CHƯA CHẠY | Cần `npm install` trong `scripts/` và Firebase CLI + emulator Firestore. Lệnh thực tế: `firebase emulators:exec --only firestore "mocha rules-tests/**/*.test.js --timeout 20000"` |
| 3 | `npm --prefix firebase/functions test` | N/A | Thư mục `firebase/functions` chưa tồn tại. Chỉ chạy sau khi T3 (Cloud Functions) tạo thư mục |
| 4 | `.\gradlew.bat :app:lintRelease` | CHƯA CHẠY | Chạy được khi có đủ đầu vào release (xem mục 2) |
| 5 | `.\gradlew.bat :app:assembleRelease` | CHẶN | Gate `app/build.gradle.kts:250–257` chặn đúng khi thiếu keystore, `STORE_PASSWORD`, `KEY_PASSWORD`, `ADMOB_APP_ID_RELEASE`, `ADMOB_BANNER_ID_RELEASE` |
| 6 | `git status --short` | CHƯA CHẠY trong vòng này | Repo có nhiều thay đổi chưa commit; không `git add -A` từ root (có sibling `appplugin`) |
| 7 | `git diff --check` | CHƯA CHẠY | |

Đầu vào release (chỉ tên, không ghi giá trị): `KEYSTORE_PATH` hoặc `my-upload-key.jks` ở root; `STORE_PASSWORD`; `KEY_PASSWORD`; `ADMOB_APP_ID_RELEASE`; `ADMOB_BANNER_ID_RELEASE`; `APPPLUGIN_DIR` nếu appplugin không nằm cạnh repo.

## 2. Điều kiện trước khi chạy cổng

- [x] Có appplugin đúng phiên bản đã chốt (D2 = 2.4.2) và build được từ clean clone (2026-10-10; cần `APPPLUGIN_DIR` + `ANDROID_HOME`).
- [ ] Có keystore upload và các biến môi trường release.
- [ ] Có AdMob App ID và banner ID release thật (định dạng `ca-app-pub-\d{16}[~/]\d{10}`).
- [ ] `.env` đã rotate SMTP credential (D1) và không còn được track.

## 3. QA thủ công trên thiết bị (Step 18)

Chạy trên bản **signed + minified** (không phải debug). Ghi kết quả, thiết bị, phiên bản Android, và build number.

**Ma trận thiết bị:** API 24, API 25, API 26, API 33, API 35, API 36. Thêm: điều hướng cử chỉ và 3 nút; font lớn; bàn phím (inset); màn hình lớn.

| Nhóm | Kiểm tra | API | Kết quả |
|---|---|---|---|
| Cài đặt | Cài mới (uninstall → install) | | |
| Cài đặt | Nâng cấp từ bản có dữ liệu, schema v12 → v13 | | |
| Ngôn ngữ | Chuyển VI/EN, khởi động lại vẫn giữ | | |
| Offline | Dùng ở chế độ khách khi bật máy bay | | |
| Tài khoản | Adopt dữ liệu khách khi đăng ký; đăng xuất; đổi tài khoản trên cùng máy; không lộ dữ liệu | | |
| Ghép đôi | Hai máy thật: gửi lời mời, chấp nhận, từ chối, huỷ | | |
| Đồng bộ | Tạo kỷ niệm offline → có mạng → xuất hiện ở máy kia | | |
| Media | Ảnh/video riêng tư và chung; lỗi upload hiện đúng trạng thái; thử lại | | |
| Quyền | Từ chối và thu hồi quyền thông báo, quyền exact alarm | | |
| Nhắc hẹn | Báo thức sau khi khởi động lại máy và khi đổi múi giờ | | |
| Thông báo | Bấm thông báo mở đúng màn hình | | |
| Consent | Rút consent: quảng cáo ngừng hiển thị; mục Privacy options hoạt động | | |
| VIP | Cold start khi đã VIP (không bị hạ cấp giả); mua; khôi phục; huỷ; giao dịch đang chờ | | |
| Paywall | Mở paywall, nút mua bật khi có giá từ Play; khi không có sản phẩm thì nút tắt | | |
| Xoá tài khoản | Xoá, đăng nhập lại; link `/delete-account` mở được | | |
| Pháp lý | Link `/privacy`, `/terms` mở được trên trình duyệt di động | | |
| Giao diện | Không cuộn ngang; lề 16dp; touch target ≥ 48dp; status bar sáng đúng | | |

**Bằng chứng cần lưu:** ảnh màn hình hoặc video ngắn cho mỗi nhóm; output của `adb logcat` cho lỗi; số build và SHA của AAB.

## 4. Đã biết, chưa có bằng chứng

- Migration 12 → 13 trên dữ liệu thật: chưa chứng minh (test `@Ignore`, thiếu `12.json`).
- Ghép đôi hai máy: chưa chứng minh.
- `GreetingScreenshotTest` một mình không đủ coverage hình ảnh; cần Roborazzi cho các màn hình chính.
- Telemetry trước consent (A10) và databuckets trong SDK: chưa xác minh trên appplugin.
- 16 KB page size cho thư viện native: chưa kiểm tra.

## 5. Ghi chú

- Không đánh dấu PASS khi chưa chạy lệnh thật. Không sửa kết quả cũ; thêm dòng mới theo ngày.
- Mọi thay đổi đối với mục nào phải ghi vào `docs/superpowers/reviews/2026-10-07-release-readiness-log.md`.
