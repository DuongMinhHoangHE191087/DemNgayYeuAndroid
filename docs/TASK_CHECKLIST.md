# TASK_CHECKLIST.md — InLove: Việc cần làm trước khi lên Google Play

> Danh sách rút gọn, dễ đọc. Chi tiết kỹ thuật đầy đủ + lý do từng quyết định nằm ở
> `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`. Trạng thái cập nhật
> real-time tại `.superpowers/sdd/2026-09-24-master-hardening-and-relaunch/progress.md`.
>
> **Chú thích trạng thái:**
> - ✅ Đã viết code, đã tự rà soát kỹ — **chờ build thật để xác nhận** (máy đang thiếu RAM, xem mục cuối).
> - ✅✅ Đã viết code VÀ đã kiểm chứng thật (chạy thử/parse XML thật, không chỉ đọc bằng mắt).
> - 📝 Đã nghiên cứu/xác nhận, không cần sửa code (kết luận an toàn hoặc quyết định rõ ràng).
> - ⏳ Chưa làm.
> - ❓ Cần bạn quyết định trước khi làm tiếp.

## 🔴 Ưu tiên cao nhất — Bảo mật & việc có thể chặn duyệt Google Play

- [x] ✅ Sửa thứ tự khởi tạo Firebase App Check (trước đây cài đặt trễ, có khoảng hở bảo mật)
- [x] ✅ Sửa lỗi VIP bị "hạ cấp giả" xuống miễn phí trong vài trăm mili-giây mỗi lần mở app
- [x] 📝 Thống nhất mã sản phẩm VIP giữa 2 hệ thống thanh toán (trước đây lệch nhau, quảng cáo không tắt đúng cho subscriber)
- [x] ✅ Sửa "Đổi mật khẩu" và "Quên mật khẩu" — trước đây chỉ đổi bản sao lưu cục bộ, mật khẩu đăng nhập thật KHÔNG đổi (có thể tự khoá tài khoản)
- [x] ✅✅ Xoá 5 mật khẩu tài khoản test (gồm 1 tài khoản admin) khỏi mã nguồn đã lên git — đã kiểm chứng chạy thật
- [x] ✅ Vá 3 lỗ hổng trong luật bảo mật Firestore (một người có thể chiếm quyền relationship của người khác, tự duyệt lời mời ghép đôi của chính mình)
- [x] ✅ Sửa lỗi xoá tài khoản: trước đây xoá dữ liệu đám mây trước rồi mới xoá tài khoản đăng nhập, nếu bước sau lỗi thì mất dữ liệu mà tài khoản vẫn còn
- [x] ✅✅ Xoá 3 lời quảng cáo VIP không đúng sự thật ("AI không giới hạn", "Cloud lưu trữ không giới hạn", và đặc biệt "Khoá vân tay/FaceID" — hoàn toàn không có trong app, chỉ dùng mã PIN) — rủi ro Google Play từ chối/gỡ app vì quảng cáo sai sự thật
- [x] ✅ Bật rút gọn mã (R8) + shrink resources cho bản release (`isMinifyEnabled = true`, `isShrinkResources = true`) — theo `docs/superpowers/plans/2026-09-30-ads-monetization-sdk-compliance.md` Task 5. **Chưa build/ký thật** (sandbox không có mạng để chạy Gradle, không có keystore thật) — cần bạn tự chạy `./gradlew :app:assembleRelease` và kiểm tra `mapping.txt` trước khi đổi thành ✅✅.
- [x] ✅ Room database đã có `MIGRATION_12_13` (version 12→13, table-rebuild cho các cột sync mới, backfill tránh crash UNIQUE INDEX) — theo `docs/superpowers/plans/2026-09-29-data-sync-and-real-pairing.md` Task 1/6. Có `Migration12To13Test.kt` nhưng chưa chạy được (không có mạng để resolve Gradle trong sandbox này) — cần bạn tự chạy `./gradlew :app:testDebugUnitTest` để xác nhận.
- [ ] ⏳ Sửa lỗi Cloudinary: khi tải ảnh lên thất bại, app vẫn báo "đã lưu thành công" (người dùng tưởng ảnh an toàn nhưng thực ra mất)
- [x] 📝 **Đã quyết định (bạn chọn "Tắt hẳn"):** `brainEnabled = false` — AdsBrain tắt hoàn toàn thay vì rollout theo giai đoạn, vì InLove không phải game và không có pipeline BigQuery cho brain này. Theo `docs/superpowers/plans/2026-09-30-ads-monetization-sdk-compliance.md` Task 4.

## 🟠 Chức năng lõi — làm cho đúng như quảng cáo

- [x] ✅ Ghép đôi 2 máy thật qua Firestore — đã viết lại toàn bộ theo `docs/superpowers/plans/2026-09-29-data-sync-and-real-pairing.md` (12/13 task: outbox + WorkManager + SyncCoordinator hai tầng + `coupleCodes` lookup + relationship id = invite id, rà soát 2 vòng, đã sửa hết lỗi tìm được). **Chưa build/chạy thử trên máy thật** — sandbox này không có mạng để chạy Gradle và không có thiết bị Android để cài APK, nên chưa thể tự xác nhận Task 13 (QA 2 máy thật). Cần bạn tự chạy `./gradlew :app:testDebugUnitTest :app:assembleDebug`, cài lên 2 máy, và làm theo 6 bước trong Task 13 của plan trên để xác nhận trước khi đổi dòng này thành ✅✅.
- [ ] ⏳ Không có tính năng nhận biết mất mạng / còn mạng — app không báo cho người dùng biết khi nào offline
- [ ] ⏳ Wallpaper người dùng chọn không được lưu lại, mất khi mở lại app
- [ ] ⏳ Dialog "Đặt báo thức nhắc nhở" đã viết code nhưng quên nối vào màn hình — bấm không có phản ứng gì
- [ ] 📝 Chế độ tối (Dark Mode) hiện bị tắt cứng — **đã xác nhận đây nhiều khả năng là chủ đích thiết kế** (giữ đúng phong cách hồng lãng mạn), không tự ý bật lại, cần hỏi bạn nếu muốn thêm như một tuỳ chọn

## 🟡 Giao diện — dọn sạch, giữ đúng phong cách hiện có

- [ ] ⏳ Hơn 1.100 chỗ tự viết mã màu rải rác thay vì dùng bảng màu chung — cần gom lại (không đổi giao diện nhìn thấy được, chỉ dọn code)
- [ ] ⏳ 5 file màn hình quá dài (2.000-2.400 dòng mỗi file) — cần tách nhỏ theo từng chức năng để dễ bảo trì
- [ ] ⏳ Còn khoảng 1.500 câu tiếng Việt viết cứng trong code thay vì dùng hệ thống đa ngôn ngữ có sẵn

## 🟢 Dữ liệu mẫu — đã làm xong phần lớn

- [x] ✅✅ Lịch ngày lễ Việt Nam: 15 ngày dương lịch cố định + 5 ngày âm lịch (Tết, Trung Thu, Giỗ Tổ, Vu Lan, Rằm tháng Giêng) — **đã tra cứu và xác minh thật qua tìm kiếm web**, không phải đoán, cho các năm 2025-2028
- [x] ✅✅ Lịch ngày lễ Mỹ/phương Tây: 9 ngày cố định + 4 ngày tính theo công thức đúng cho mọi năm (Ngày của Mẹ, Ngày của Cha, Lễ Tạ Ơn...)
- [x] ✅ Mở rộng danh mục gợi ý quà từ 2 lên 18 món, đầy đủ song ngữ Việt/Anh, chia theo 4 khoảng giá và nhiều dịp
- [ ] ⏳ Nối 3 bộ dữ liệu trên vào app thật (hiện mới tạo file dữ liệu, chưa gắn vào màn hình)

## 🔵 Nghiên cứu — đã hoàn thành

- [x] 📝 Nghiên cứu thị trường & đối thủ (Việt Nam và quốc tế) — xem `docs/PRODUCT_RESEARCH.md`. Phát hiện chính: nên làm widget màn hình khoá/chính hiển thị số ngày yêu
- [x] 📝 Tìm hiểu sâu SDK quảng cáo/subscription nội bộ — xem `docs/APPPLUGIN_SDK_OVERVIEW.md`

## Cập nhật xác nhận build

RAM trên máy dao động lên xuống nhiều lần trong lúc làm việc (có lúc chỉ còn ~2GB trống trên tổng 13.9GB, có lúc lên 4.2GB) — nhiều khả năng do các ứng dụng khác đang mở (Chrome nhiều tab). Mỗi lần RAM xuống thấp, hệ thống tự dừng lệnh build để bảo vệ máy; tôi không tự ý chạy lại cho đến khi thấy dấu hiệu RAM đã hồi phục.

**Đã xác nhận được:** lệnh biên dịch (`compileDebugKotlin` và `compileDebugJavaWithJavac`) chạy THÀNH CÔNG — toàn bộ code Kotlin/Java đã sửa đều biên dịch sạch. Trong lúc xác nhận, tôi phát hiện và sửa luôn MỘT lỗi thật do chính việc thêm biến môi trường mới gây ra (làm vỡ phần sinh mã tự động của Android) — đúng là loại lỗi chỉ build thật mới bắt được.

**Chưa xác nhận được:** kết quả đạt/không đạt của các bài kiểm thử tự động (unit test) cho phần đăng nhập và thanh toán — bài test bị dừng giữa chừng (thiếu RAM) ngay trước khi chạy xong, 2 lần liên tiếp.

**Bước tiếp theo đề xuất:** đóng bớt Chrome hoặc các ứng dụng nặng khác, sau đó yêu cầu chạy lại bộ test để có kết quả cuối cùng, rồi mới commit.
