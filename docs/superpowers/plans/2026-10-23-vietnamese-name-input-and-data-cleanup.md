# Gõ tên tiếng Việt + làm sạch dữ liệu (emoji tự sinh)

Ngày: 2026-10-23. Tier L. Trạng thái: ĐÃ DUYỆT và ĐÃ BUILD 2026-10-23 (xem Execution log cuối file) (chưa có dòng code nào).

## Goal
1. Ô nhập tên (Tôi, Người ấy, Onboarding x2, Đăng ký) gõ Telex/VNI không vỡ dấu; tên lưu ra luôn ở dạng chuẩn.
2. Dữ liệu lưu của người dùng không còn emoji do app tự sinh; dữ liệu cũ đã lưu được làm sạch một lần.

## Findings (đọc code, VERIFIED)
- Mọi ô tên là `OutlinedTextField(value: String, onValueChange = { x = it })`, không lọc, không `keyboardOptions`: `EditMyProfileDialog.kt:476`, `Dialogs.kt:1280`, `OnboardingScreen.kt:678/692`, `AuthScreen.kt:684`. Không có font tuỳ biến, không `windowSoftInputMode`.
- Nên lỗi KHÔNG nằm ở code lọc/đổi chữ trong app. Nghi phạm còn lại: (a) Compose BOM `2024.09.00` đã cũ (IME composing đã được sửa ở bản sau); (b) `Dialog` window + `AppDensity`; (c) Unicode dạng tổ hợp (NFD) từ bàn phím làm dấu hiển thị vỡ; (d) `remember(girlName)` ở `Dialogs.kt:1011` reset ô khi flow đồng bộ đẩy tên mới đúng lúc đang gõ.
- UNVERIFIED: chưa tái hiện được, vì `adb input text` chỉ ASCII, không mô phỏng được Telex. Cần bạn thử trên bàn phím thật sau khi build.
- Emoji tự sinh vào dữ liệu lưu: `AuthRepository.kt:589,1130` (bio mặc định "… InLove ✨"), `OnlineCoupleRepository.kt:94` (bio mặc định 💕), `:488` (loveNote mặc định ❤️). Toast/thông báo, catalog quà-lễ (`GiftIdeasSeed`, holidays) là nội dung chủ ý: GIỮ.

## Tasks
| id | files | change | test | depends | model |
|---|---|---|---|---|---|
| N1 | `ui/util/NameText.kt` (mới) | `cleanName(s)`: NFC, bỏ emoji/ký tự điều khiển/zero-width, gộp khoảng trắng, trim, tối đa 50 | `NameTextTest`: "Nguyễn" NFD→NFC, "Hoàng 💕"→"Hoàng", "  An   Bình "→"An Bình", chuỗi Telex đã ghép | - | haiku |
| N2 | `EditMyProfileDialog.kt`, `Dialogs.kt`, `OnboardingScreen.kt`, `AuthScreen.kt` | ô tên thêm `KeyboardOptions(capitalization = Words, autoCorrectEnabled = false, imeAction = Next)`; KHÔNG lọc trong `onValueChange` (lọc khi đang ghép làm vỡ IME); `Dialogs.kt:1011` đổi `remember(girlName)` thành `remember` + chỉ nạp lại khi mở hộp thoại | test cũ vẫn xanh + 1 test gõ chuỗi có dấu rồi Save | N1 | haiku |
| N3 | `OnlineCoupleRepository.updateMyProfile`, `InLoveViewModel.saveCoupleProfile`, `completeOnboardingProfile`, `AuthRepository` (đăng ký) | gọi `cleanName` khi lưu (một điểm chặn, không lọc lúc gõ) | thêm case vào `ProfileSyncViewModelTest`/repo test | N1 | sonnet |
| N4 | `AuthRepository.kt:589,1130`, `OnlineCoupleRepository.kt:94,488` | bỏ emoji khỏi bio/loveNote mặc định (giữ chữ) | test chuỗi mặc định không chứa emoji | - | haiku |
| N5 | `data/repository/…` + `InLoveApplication.kt` (hoặc nơi khởi tạo repo) | dọn một lần, idempotent, cờ SharedPreferences, KHÔNG đổi schema Room: tên (me/partner/couple) qua `cleanName`; bio chỉ khi đúng bằng chuỗi mặc định cũ (có ✨/💕) thì đổi sang chuỗi mới; bio người dùng tự gõ giữ nguyên. Firestore `users/{uid}` cập nhật qua lần đồng bộ hồ sơ kế tiếp, không script admin | test: dữ liệu bẩn→sạch, chạy 2 lần không đổi, bio tự gõ không bị đụng | N1,N3,N4 | sonnet |
| N6 | build + LDPlayer | full test, lint ≤128, `assembleDebug`, chụp màn hình; bạn tự thử Telex bằng bàn phím thật | lệnh thật | tất cả | - |

## Risks
- Lint đang đúng trần 128: mọi cảnh báo mới làm đỏ.
- N2 chưa chắc chữa gốc lỗi Telex nếu nguyên nhân là phiên bản Compose (xem quyết định 1).
- `cleanName` bỏ emoji khỏi TÊN: nếu bạn muốn tên có emoji thì nói.

## Out of scope
- Nâng Compose BOM (cần duyệt riêng, rủi ro lớn lên 1874 test).
- Emoji trong toast, catalog quà/lễ, nhãn sở thích (`OnlineCoupleRepository.kt:67-76`).
- Đổi schema Room; script dọn Firestore phía server.


## Execution log
- N1-N5 built (cleanName NFC + bỏ emoji lúc LƯU; keyboardOptions Words + autoCorrect off ở 5 ô tên; bio/love note mặc định không emoji; cleanStoredText bằng compare-and-set DAO, chỉ refreshState khi có đổi).
- Tests mới: NameTextTest (7), CleanStoredTextTest (2); OnlineProfileApplyTest đổi bio mặc định sang không emoji (tránh đua với cleanup lúc khởi tạo).
- Verify 2026-10-23: 191 suites / 1883 tests / 0 failures, lint 0 errors / 128 warnings, inventory OK, assembleDebug OK.
- Chưa làm: cài LDPlayer chụp ảnh; Telex trên bàn phím thật chỉ owner thử được; không đổi remember(girlName) ở Dialogs.kt; chưa nâng Compose BOM.
