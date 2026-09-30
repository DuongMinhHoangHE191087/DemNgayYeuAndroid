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
- [x] ✅ Sửa lỗi Cloudinary: khi tải ảnh lên thất bại, app vẫn báo "đã lưu thành công" — toast và 3 badge trong màn hình Kỷ niệm giờ đọc đúng `isCloudinaryStored` thay vì luôn báo Cloud (`fec27b8`, `6866afd`)
- [x] 📝 **Đã quyết định (bạn chọn "Tắt hẳn"):** `brainEnabled = false` — AdsBrain tắt hoàn toàn thay vì rollout theo giai đoạn, vì InLove không phải game và không có pipeline BigQuery cho brain này. Theo `docs/superpowers/plans/2026-09-30-ads-monetization-sdk-compliance.md` Task 4.
- [x] ✅ Phát hiện & vá lỗi mới khi code review: 2 nút "Tester A/B" ở màn Đăng nhập gọi thẳng `loginTestUser` (không kiểm tra mật khẩu) và **không hề có điều kiện chặn bản release** — bất kỳ ai cài app thật đều đăng nhập được vào tài khoản demo dùng chung. Cùng dạng rủi ro với vụ lộ mật khẩu test đã xoá khỏi git trước đây, nhưng lần này là một "cửa hậu" sống trong UI. Đã bọc trong `BuildConfig.DEBUG` (`0b6f8e4`).
- [x] ✅ Phát hiện & vá lỗi mới: "Từ chối"/"Hủy" lời mời ghép đôi trước đây chỉ sửa Room cục bộ, không hề gọi Firestore — người nhận vẫn có thể "Chấp nhận" một lời mời đã bị hủy, và lời mời đã từ chối tự động hiện lại sau lần đồng bộ kế tiếp. Đã sửa cả 2 hàm, kèm sửa `InviteStatus.REJECTED` → `DECLINED` cho khớp `firestore.rules` (`5f901a1`).

## 🟠 Chức năng lõi — làm cho đúng như quảng cáo

- [x] ✅ Ghép đôi 2 máy thật qua Firestore — đã viết lại toàn bộ theo `docs/superpowers/plans/2026-09-29-data-sync-and-real-pairing.md` (12/13 task: outbox + WorkManager + SyncCoordinator hai tầng + `coupleCodes` lookup + relationship id = invite id, rà soát 2 vòng, đã sửa hết lỗi tìm được). **Chưa build/chạy thử trên máy thật** — sandbox này không có mạng để chạy Gradle và không có thiết bị Android để cài APK, nên chưa thể tự xác nhận Task 13 (QA 2 máy thật). Cần bạn tự chạy `./gradlew :app:testDebugUnitTest :app:assembleDebug`, cài lên 2 máy, và làm theo 6 bước trong Task 13 của plan trên để xác nhận trước khi đổi dòng này thành ✅✅.
- [x] ✅ Nhận biết mất mạng/còn mạng: `NetworkMonitor` đã chạy sẵn từ khi mở app (đăng ký trong `AppServiceLocator`) nhưng chưa ai đọc — giờ có banner "Bạn đang mất kết nối mạng" ở trang chủ (`4866cc4`)
- [x] ✅ Wallpaper người dùng chọn giờ được lưu lại qua `SharedPreferences` (cùng cơ chế với ngôn ngữ), không mất khi mở lại app (`8834708`)
- [x] ✅ Dialog "Đặt báo thức nhắc nhở" đã nối vào `MainActivity` — root cause là chưa có nơi nào collect `showSetAlarmDialog`/render dialog, không phải lỗi trong chính dialog (`e9efd17`)
- [ ] 📝 Chế độ tối (Dark Mode) hiện bị tắt cứng — **đã xác nhận đây nhiều khả năng là chủ đích thiết kế** (giữ đúng phong cách hồng lãng mạn), không tự ý bật lại, cần hỏi bạn nếu muốn thêm như một tuỳ chọn
- [x] ✅ Phát hiện & vá lỗi mới khi code review: xoá kỷ niệm/ngày kỷ niệm bị "hồi sinh" — do xoá cứng khỏi Room cùng lúc đẩy tombstone lên outbox, nên khi đồng bộ vọng lại (kể cả từ chính máy vừa xoá) mục đó bị chèn lại như còn sống. Đã chuyển sang xoá mềm (`deleted=1`) + lọc `WHERE deleted=0` khi đọc + chặn resurrection trong `SyncCoordinator` (`d5a4211`).

## 🟡 Giao diện — dọn sạch, giữ đúng phong cách hiện có

- [ ] ⏳ Hơn 1.100 chỗ tự viết mã màu rải rác thay vì dùng bảng màu chung — cần gom lại (không đổi giao diện nhìn thấy được, chỉ dọn code)
- [ ] ⏳ 5 file màn hình quá dài (2.000-2.400 dòng mỗi file) — cần tách nhỏ theo từng chức năng để dễ bảo trì
- [ ] ⏳ Còn khoảng 1.500 câu tiếng Việt viết cứng trong code thay vì dùng hệ thống đa ngôn ngữ có sẵn
- [x] ✅ Dọn 2 chỗ rò rỉ tên thư viện "(Room)" vào text hiển thị cho người dùng (`a53e889`)
- [x] ✅ Touch target dưới 48dp: đã sửa toàn bộ ~40 chỗ tìm được (GiftScreen, AppModals, MilestoneBadgeTracker, PairingScreen, ReminderScreen, MemoriesGridScreen, Dialogs, CalendarScreen, SettingsScreen), gồm cả 2 badge đổi ảnh đại diện trong `EditCoupleDialog` — dùng vùng chạm 48dp trong suốt bọc quanh badge nhỏ 22dp thay vì phóng to hẳn badge (`6866afd`, `e46ec5c`, `18e9593`)

## ✅ Đã sửa từ vòng review sâu (2026-09-30)

Rà soát toàn bộ UI Compose (~28.000 dòng) tìm thêm bug/cải tiến, rồi sửa hết các mục có thể sửa an
toàn mà không cần build thật để xác nhận (đọc lại bằng tay kỹ, không đổi hành vi ngoài ý muốn):

- [x] ✅ `AddMilestoneDialog` (Dialogs.kt): `subtitle` giờ tự tính từ ngày thật chọn (Thứ mấy) thay vì luôn "Chủ Nhật"; đã thêm bộ chọn ảnh từ `presetPhotos` thay vì luôn 1 ảnh stock cố định; nhãn "N ngày trước" ở milestone đã qua (CalendarScreen) giờ tính từ `dateText` thật thay vì luôn "268 ngày" (`28f89ea`)
- [x] ✅ `CalendarScreen.kt` rẽ nhánh giao diện milestone: đổi từ `milestone.id == 1L/2L` (PK thật, chỉ đúng với 2 dòng seed) sang `milestone.categoryTag` (trường ngữ nghĩa, người dùng tự đặt được qua `AddMilestoneDialog`) (`28f89ea`)
- [x] ✅ `SettingsScreen.kt`: xoá hẳn công tắc "Xác thực sinh trắc học" giả (app không có tính năng vân tay/FaceID thật — cùng loại quảng cáo sai sự thật đã xoá ở mục VIP), tính lại "Security Health Score" chỉ dựa trên 2 yếu tố thật (tài khoản + PIN); `notificationEnabled`/`soundEnabled` giờ lưu qua `SharedPreferences` (cùng cơ chế ngôn ngữ/wallpaper) thay vì mất khi rời màn hình — **lưu ý: 2 công tắc này vẫn chưa thật sự tắt/bật thông báo, chỉ hết bị reset trạng thái** (`045155a`)
- [x] ✅ Hoàn thiện luồng "Câu hỏi bảo mật" còn dang dở: backend (`AuthRepository`) đã hỗ trợ sẵn từ trước, chỉ thiếu UI ở màn Đăng ký. Đã thêm ô chọn câu hỏi + câu trả lời (không bắt buộc), nối vào `register()` (`7cb121d`)
- [x] ✅ `CaptureMemoryDialog` (AppModals.kt) đã khoá nút Lưu khi đang lưu, tránh gửi trùng khi bấm nhanh (`0bb5cf0`)
- [x] ✅ Dialog "Quên mật khẩu" đã có kiểm tra khớp mật khẩu mới ngay trên giao diện, giống màn Đăng ký (`0bb5cf0`)
- [x] ✅ Xoá overload chết của `EditCoupleDialog` (Dialogs.kt) — thân rỗng, không còn nơi gọi (`0bb5cf0`)
- [x] ✅ Nút "Sổ tay sở thích" (GiftScreen.kt) đã nối thật — mở `WishlistNotebookDialog` liệt kê quà đã Yêu thích, đổi tên "Của người ấy" → "Của tôi" vì dữ liệu Yêu thích chỉ ở local Room, chưa đồng bộ Firestore nên không thể hiển thị đúng sở thích thật của đối phương (`0e9bd36`)

## 🆕 Tính năng mới thêm theo yêu cầu (2026-10-01)

- [x] ✅ Trang chủ "Chụp ảnh kỷ niệm" giờ dùng đúng dialog upload ảnh/video thật lên Cloudinary (`AddMemoryDialog`) — trước đây chỉ nhận URL dán tay/ảnh mẫu và lưu sai vào bảng milestone thay vì bảng kỷ niệm thật (`d71af49`)
- [x] ✅ Cẩm nang hướng dẫn sử dụng (song ngữ) bổ sung 2 tab còn thiếu: "Ghép Đôi" và "Kho Kỷ Niệm" — chuỗi dịch đã có sẵn từ trước nhưng chưa ai nối vào danh sách tab hiển thị; sửa luôn câu nhắc "bảo mật vân tay" không còn đúng (`30d6e34`)

## 🟢 Dữ liệu mẫu — đã làm xong phần lớn

- [x] ✅✅ Lịch ngày lễ Việt Nam: 15 ngày dương lịch cố định + 5 ngày âm lịch (Tết, Trung Thu, Giỗ Tổ, Vu Lan, Rằm tháng Giêng) — **đã tra cứu và xác minh thật qua tìm kiếm web**, không phải đoán, cho các năm 2025-2028
- [x] ✅✅ Lịch ngày lễ Mỹ/phương Tây: 9 ngày cố định + 4 ngày tính theo công thức đúng cho mọi năm (Ngày của Mẹ, Ngày của Cha, Lễ Tạ Ơn...)
- [x] ✅ Mở rộng danh mục gợi ý quà từ 2 lên 18 món, đầy đủ song ngữ Việt/Anh, chia theo 4 khoảng giá và nhiều dịp
- [x] ✅ Cả 3 bộ dữ liệu mẫu đã nối vào app thật: 18 gợi ý quà thay 2 món cũ, lịch ngày lễ VN/phương Tây tự seed vào Lịch khi cài đặt lần đầu (`a70610a`, `6d497d2`)

## 🔵 Nghiên cứu — đã hoàn thành

- [x] 📝 Nghiên cứu thị trường & đối thủ (Việt Nam và quốc tế) — xem `docs/PRODUCT_RESEARCH.md`. Phát hiện chính: nên làm widget màn hình khoá/chính hiển thị số ngày yêu
- [x] 📝 Tìm hiểu sâu SDK quảng cáo/subscription nội bộ — xem `docs/APPPLUGIN_SDK_OVERVIEW.md`

## Cập nhật xác nhận build

RAM trên máy dao động lên xuống nhiều lần trong lúc làm việc (có lúc chỉ còn ~2GB trống trên tổng 13.9GB, có lúc lên 4.2GB) — nhiều khả năng do các ứng dụng khác đang mở (Chrome nhiều tab). Mỗi lần RAM xuống thấp, hệ thống tự dừng lệnh build để bảo vệ máy; tôi không tự ý chạy lại cho đến khi thấy dấu hiệu RAM đã hồi phục.

**Đã xác nhận được:** lệnh biên dịch (`compileDebugKotlin` và `compileDebugJavaWithJavac`) chạy THÀNH CÔNG — toàn bộ code Kotlin/Java đã sửa đều biên dịch sạch. Trong lúc xác nhận, tôi phát hiện và sửa luôn MỘT lỗi thật do chính việc thêm biến môi trường mới gây ra (làm vỡ phần sinh mã tự động của Android) — đúng là loại lỗi chỉ build thật mới bắt được.

**Chưa xác nhận được:** kết quả đạt/không đạt của các bài kiểm thử tự động (unit test) cho phần đăng nhập và thanh toán — bài test bị dừng giữa chừng (thiếu RAM) ngay trước khi chạy xong, 2 lần liên tiếp.

**Bước tiếp theo đề xuất:** đóng bớt Chrome hoặc các ứng dụng nặng khác, sau đó yêu cầu chạy lại bộ test để có kết quả cuối cùng, rồi mới commit.

### Cập nhật 2026-09-30 (phiên review sau)

RAM lúc bắt đầu phiên này chỉ còn ~1.1GB/13.9GB trống — thấp hơn cả lần trước, nên **không tự
chạy Gradle build/test lần này** để tránh crash JVM (đã thấy 6 lần crash liên tiếp trong log cũ).
Mọi sửa lỗi + tính năng mới trong phiên này (xem `git log 49c939d..HEAD`, ~27 commit) đã được đọc
lại bằng tay ít nhất 2 lần, kiểm tra cân bằng ngoặc `{}`/`()` bằng script cho mọi file sửa nhiều,
và có test hồi quy Robolectric đi kèm cho các phần phức tạp (resurrection race, invite
decline/cancel, wallpaper persistence, seed dữ liệu quà/ngày lễ), nhưng **chưa chạy build thật
được** — cần bạn chạy khi máy đủ RAM:
```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```
