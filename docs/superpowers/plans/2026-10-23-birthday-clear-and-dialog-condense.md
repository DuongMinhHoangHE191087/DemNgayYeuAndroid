# Kế hoạch: nút X xoá ngày sinh + thu gọn 2 dialog hồ sơ (2026-10-23)

Yêu cầu của chủ app (nguyên văn): "xoá ngày sinh thì dấu x nhỏ bên trong khu nhập tôi và nhập vào đều có định dạng thực hiện xử lý thu gọn lại các giao diện cho tôi hiện tịa bị thừa quá".
Codex được miễn, nên khâu review là Claude review. Chưa commit gì cả.

## 1. Mục tiêu
Việc xoá ngày sinh chuyển thành một dấu X nhỏ nằm trong ô nhập. Dấu X đứng cạnh icon lịch và chỉ hiện khi ô đã có ngày. Cả hai dialog dùng chung một định dạng ô: dialog của tôi (`EditMyProfileDialogContent`) và dialog của người ấy (`EditCoupleDialogContent`). Hai dialog được thu gọn như sau:
- Bỏ nút "Xóa ngày sinh" dạng chữ.
- Bỏ hàng chip/pill tuổi và cung. Thông tin này chuyển thành một dòng helper nhỏ dưới ô, ví dụ "21 tuổi • Cung Cự Giải ♋".
- Bỏ nút "Đóng"/"Hủy" ở đáy. Header đã có X đóng, nên nút Lưu chiếm cả chiều ngang.
- Thẻ "Ngày yêu" bỏ hàng tiêu đề và banner. Số ngày chuyển vào helper của ô.

Thêm một tham số mặc định `clearBirthday = false` để repo thực sự lưu được việc xoá ngày sinh của chính tôi. Hiện nay chuỗi rỗng có nghĩa là "giữ nguyên".

## 2. Giả định
- VERIFIED: `InLoveDatePickerField` (Material3DatePicker.kt :1058-1151) tự quản `trailingIcon`. Đó là một `IconButton` duy nhất, tag `${testTag}_calendar_btn`, cỡ `MinTouchBox`. File này đã import `Icons.Filled.Clear` và `Row`, nên không cần import mới.
- VERIFIED: `BirthdayField` (31 dòng) chỉ bọc `InLoveDatePickerField(pickerOnly = true, …)`. Tham số `helperText` đã có sẵn và được render 11sp.
- VERIFIED: `OnlineCoupleRepository.updateMyProfile` (:264-300) tính `effective = Birthday.parse(birthDate)?.text ?: me.birthDate`, tức là chuỗi rỗng thì giữ giá trị cũ. Các test `BirthdayBoundaryTest.rename_storedBlankOrGarbageBirthday_keeps…` và `OnlineCoupleRepositoryAvatarMergeTest` cố ý ghim hành vi này.
- VERIFIED: phía người ấy đã lưu được ngày sinh rỗng (`profileTab_clearingTheStoredPartnerBirthday_isSavedBlank`). Chỉ phía "tôi" cần cờ mới.
- VERIFIED: `formatZodiacDisplay("Chưa rõ") == ""` và `formatAgeDisplay(0) == ""`. Vì vậy sau khi xoá, tuổi = 0 và cung = "Chưa rõ" tự ẩn trên Home (LoveHomeScreen :775-812). Không có báo thức sinh nhật nào: grep `com/example/alarm` không ra kết quả.
- VERIFIED: firestore.rules :67 cho phép `birthDate` rỗng (`^$|…`). `applyRemoteProfile` áp `""` từ xa như một lần xoá.
- VERIFIED: `btn_close_pairing_tab` là mốc chờ của helper `openPairingTab`, nên giữ nguyên. `coupled_showsTheReadOnlySummaryAndNoSaveButton` (:341) đang assert text "Close" và phải sửa.
- VERIFIED: `btn_save_my_profile` hiện thiếu `heightIn(min = MinTouchBox)`. Đây là lỗi touch target có sẵn, sửa luôn.
- VERIFIED: cả hai dialog dùng chuỗi inline `if (isEnglish) … else …`, không dùng `stringResource`/`R.string` (grep = 0 trong `EditMyProfileDialog.kt`, Dialogs.kt chỉ có dòng khai báo hàm). Xoá UI vì vậy không làm mồ côi tài nguyên nào, không sinh cảnh báo lint `UnusedResources`.
- VERIFIED: chữ "Xóa ngày sinh"/"Clear birthday" chỉ có ở Dialogs.kt:1401 (chính nút sẽ xoá). Không test nào tìm ô ngày yêu theo nhãn "Bắt đầu yêu" hay tìm nút theo chữ, nên đổi nhãn sang "Ngày bắt đầu yêu" an toàn với test.
- VERIFIED: chỉ có 1 nơi gọi production của `EditMyProfileDialog` (MainActivity :628) và `viewModel.updateMyProfile` (MainActivity :637); `androidTest` không có. Mọi test gọi `updateMyProfile` đều truyền 5 đối số theo vị trí, nên tham số cuối có mặc định tương thích.
- VERIFIED: `node scripts/qa/test-inventory.mjs --check` chỉ báo lỗi khi test đã ghi bị mất hoặc đổi tên ("test mới chưa ghi luôn OK"). T3/T4 chỉ sửa assert trong test sẵn có, không đổi tên hay xoá test.
- UNVERIFIED: khi bấm `IconButton` trong `trailingIcon`, sự kiện có thể lọt vào `interactionSource` của ô (`PressInteraction.Release`) và mở luôn picker. Lý thuyết Compose: con `clickable` consume `down` ở pass Main trước cha, nên ô không thấy Press, nhưng chưa chứng minh trong Robolectric. T1 phải có test ghim việc này bằng pointer thật (`performTouchInput { click() }`). Nếu nó lọt thật, X vẫn gọi `onClear`, rồi listener bỏ qua khi `value` vừa rỗng; cách an toàn nhất là bọc X trong một `Box` có `pointerInput` riêng.
- UNVERIFIED: các import Dialogs/EditMyProfile như `CalendarMonth`, `Face`, `FilterChip`, `TextAlign` có thể đã thừa từ trước. KHÔNG đụng vào chúng, vì kotlinc không cảnh báo import thừa.

## 3. Phác thảo trước/sau
```
NGƯỜI ẤY — TRƯỚC                    NGƯỜI ẤY — SAU
[Hồ sơ][Ghép đôi]             (X)   [Hồ sơ][Ghép đôi]             (X)
┌ Người ấy ───────────────┐         ┌ Người ấy ───────────────┐
│ (ảnh) [Tên........]     │         │ (ảnh) [Tên........]     │
│ [15/10/2004      📅]    │         │ [15/10/2004    ✕ 📅]    │
│      Xóa ngày sinh      │         │ 21 tuổi • Cung Thiên Bình│
│ (21 tuổi) (Thiên Bình)  │         └─────────────────────────┘
└─────────────────────────┘         ┌─────────────────────────┐
┌ 📅 Ngày yêu ────────────┐         │ [Ngày bắt đầu yêu   📅] │
│ [Bắt đầu yêu      📅]   │         │ ✨ 812 ngày bên nhau 💕 │
│ ♥ ✨ 812 ngày bên nhau  │         └─────────────────────────┘
└─────────────────────────┘         [          Lưu          ]
[ Đóng ] [   Lưu   ]

CỦA TÔI — TRƯỚC                     CỦA TÔI — SAU
[Tên] [Giới tính] [Bio]             [Tên] [Giới tính] [Bio]
[15/10/2004      📅]                [15/10/2004    ✕ 📅]
( ✦ 21 tuổi • Cung Thiên Bình )     21 tuổi • Cung Thiên Bình
[ Hủy ] [ Lưu ]                     [          Lưu          ]
```

## 4. Tasks
Wave 1: T1 và T2 chạy song song vì không chung file. Wave 2: T3 và T4 chạy song song vì không chung file.

| id | files owned (exclusive) | change | test or command | depends on | model |
|---|---|---|---|---|---|
| T1 | `ui/components/Material3DatePicker.kt`, `ui/components/BirthdayField.kt`, `test/.../ui/components/BirthdayFieldTest.kt` | (a) Thêm `onClear: (() -> Unit)? = null` vào `InLoveDatePickerField`. Khi `onClear != null && value.isNotBlank()`, `trailingIcon` thành `Row { X; lịch }`. X là `IconButton(Modifier.size(MinTouchBox).testTag("${testTag}_clear_btn"))` với `Icons.Filled.Clear`, icon 18dp, tint `OnSurfaceVariant`, cd `if (isEnglish) "Clear date" else "Xóa ngày"` (theo mẫu của nút lịch ở :1101; `InLoveDatePickerField` đã có tham số `isEnglish`; component dùng chung nên không ghi chết chữ "ngày sinh"). (b) `BirthdayField` truyền `onClear = { onValueChange("") }` và `helperText`. helperText là `Birthday.parse(value)?.let { listOf(formatAgeDisplay(it.age, isEnglish), formatZodiacDisplay(it.zodiac, isEnglish)).filter(String::isNotBlank).joinToString(" • ") }?.ifBlank { null }`; dùng đúng chữ ký thật của 2 hàm format trong ProfileUtils. BirthdayField không thêm tham số mới. | Test mới trong `BirthdayFieldTest` (dùng `useUnmergedTree = true`): X ẩn khi value rỗng; X hiện khi value = "15/10/2004"; bấm X gọi `onValueChange("")`; bấm X KHÔNG mở picker; X ≥48dp; helper chứa "tuổi" và "•". Hai test bấm X PHẢI bấm bằng `performTouchInput { click() }` (đường pointer thật mà `pickerOnly` lắng nghe), KHÔNG dùng `performClick()` (đường semantics, mọi test cũ đều dùng đường này, nên test sẽ đúng một cách vô nghĩa). Test "bấm X gọi onClear": assert `changes == listOf("")`. Test "bấm X không mở picker": assert `changes == listOf("")` VÀ `onNodeWithTag("btn_select_year_month").assertDoesNotExist()` sau `waitForIdle()`. | — | sonnet/high |
| T2 | `data/repository/OnlineCoupleRepository.kt`, `ui/viewmodel/InLoveViewModel.kt`, `test/.../data/repository/BirthdayBoundaryTest.kt` | Thêm `clearBirthday: Boolean = false` (tham số cuối) vào `OnlineCoupleRepository.updateMyProfile`, với `effective = if (clearBirthday) "" else Birthday.parse(birthDate)?.text ?: me.birthDate`. Thêm `clearBirthday: Boolean = false` vào `InLoveViewModel.updateMyProfile` (:1881) và truyền tiếp xuống. Đường rename (:1169) giữ nguyên, vẫn dùng mặc định false. | Test mới: `updateMyProfile(..., clearBirthday = true)` thì me.birthDate = "", age = 0 và cặp đôi có partner1Birthday = "" (qua `mirrorMe`). Test cũ blank = keep phải vẫn xanh. | — | sonnet/high |
| T3 | `ui/screens/Dialogs.kt`, `test/.../ui/screens/EditCoupleDialogTest.kt` | Trong `EditCoupleDialogContent`: xoá TextButton `btn_clear_partner_birth` (:1396-1403); xoá Row chip tuổi/cung (:1405-1436); xoá hàng tiêu đề thẻ Ngày yêu (icon CalendarMonth và "Ngày yêu"); xoá banner Surface (:1469-1487); `InLoveDatePickerField(input_anniversary_date)` nhận label "Ngày bắt đầu yêu"/"Love start date" và `helperText = if (currentAnniversary.isNotBlank()) "✨ $calculatedOfflineDays ngày bên nhau 💕" else null` (bản EN là "days together"); xoá cả `Row` hàng nút (:1491-1532) và đặt thẳng `if (!isCoupled) Button(...)` vào Column (không để một Row rỗng khi đã ghép đôi, vì lúc đó Row chỉ có nút "Đóng"); Save `btn_save_couple_offline` dùng `fillMaxWidth()` thay cho `weight(1.2f)` và giữ `heightIn(MinTouchBox)`. `girlAge`/`girlZodiac` VẪN dùng cho `saveCoupleProfile`, nên giữ lại. Không đụng :2576, cũng không đụng `btn_close_pairing_tab`. | Sửa :221-222 thành `onNodeWithTag("input_partner_birth_clear_btn", useUnmergedTree = true)`. Sửa :317 (blank thì không có X) sang tag mới. Sửa :341 thành assert `btn_close_edit_couple` tồn tại. Thêm assert: không còn text "Đóng"/"Close" ở tab hồ sơ khi chưa ghép đôi. | T1 | sonnet/high |
| T4 | `ui/screens/EditMyProfileDialog.kt`, `MainActivity.kt`, `test/.../ui/screens/MyProfileBirthdayTest.kt`, `test/.../ui/screens/EditProfileDialogTargetsTest.kt` | `onSave` thêm tham số thứ 6 là `Boolean`, tên `clearBirthday`, ở CẢ HAI chữ ký (`EditMyProfileDialog` :117 và `EditMyProfileDialogContent` :174). Dùng `val openedWithBirthday = remember { birthDate.isNotBlank() }` (`birthDate` là `String` ủy quyền bằng `by`, KHÔNG có `.value`; đặt ngay sau state `birthDate`; `remember` KHÔNG key) và gửi `openedWithBirthday && birthDate.trim().isBlank()`. Xoá Spacer :547 và pill :550-574. Xoá `calculatedAge`/`calculatedZodiacPair` (:208-213), vì chúng thành val chết và sinh warning. Xoá OutlinedButton "Hủy"; Save `btn_save_my_profile` dùng `fillMaxWidth().heightIn(min = MinTouchBox)`. MainActivity :628-639 truyền `clearBirthday` sang `viewModel.updateMyProfile`. | 7 lambda `onSave` trong test thêm `, _` (MyProfileBirthday :42; Targets :40, 65, 94, 117, 136, 154). Test mới 1: mở với "15/10/2004", bấm X rồi Lưu, thì birth = "" và clear = true. Test mới 2: ngày sinh đọc không được, mở ra trống, Lưu thì clear = false. Test mới 3: Save ≥48dp. | T1, T2 | sonnet/high |

Danh sách import và biến chết T3/T4 PHẢI xoá. Cách kiểm: grep từng tên trong file sau khi sửa; còn 0 lần dùng thì xoá import.
- EditMyProfileDialog.kt: `calculatedAge`, `calculatedZodiacPair`, import `Icons.Filled.AutoAwesome`, `derivedStateOf`, `OutlinedButton`, `formatAgeDisplay`/`formatZodiacDisplay`/`calculateAge`/`calculateZodiac` (import ProfileUtils nào hết dùng). `Surface` vẫn còn dùng, GIỮ.
- Dialogs.kt: import nào chỉ phục vụ chip, banner hoặc tiêu đề (có thể là `Icons.Filled.Favorite`, `CalendarMonth`, `formatAgeDisplay`, `formatZodiacDisplay`, `TextButton`, `OutlinedButton`). Dialogs.kt rất lớn nên phần lớn các import này còn dùng ở chỗ khác; chỉ xoá khi grep = 0.
- Material3DatePicker.kt/BirthdayField.kt: chỉ thêm import (`Birthday`, các hàm format nếu chưa có). Không có gì chết.

## 5. Verification (chỉ liệt kê, main session chạy, vì một slot Gradle)
```
.\gradlew :app:testDebugUnitTest --tests com.example.ui.components.BirthdayFieldTest --tests com.example.ui.screens.EditCoupleDialogTest --tests com.example.ui.screens.MyProfileBirthdayTest --tests com.example.ui.screens.EditProfileDialogTargetsTest --tests com.example.data.repository.BirthdayBoundaryTest --tests com.example.data.repository.OnlineCoupleRepositoryAvatarMergeTest --tests com.example.ui.viewmodel.ProfileSyncViewModelTest
.\gradlew :app:lintDebug   # gate: 0 errors, <=128 warnings (hiện đúng 128, KHÔNG còn dư)
node scripts/qa/test-inventory.mjs --check   # không test nào bị mất hoặc đổi tên
```
- Xem log compile của `compileDebugKotlin`: không được có warning mới, nhất là "Variable '…' is never used".
- Sau cùng chạy full `:app:testDebugUnitTest` nếu slot rảnh, vì các test khác cũng gọi `onSave` hoặc `updateMyProfile`. Grep `updateMyProfile(` và `onSave = {` trong `app/src/test` trước khi chạy.

## 6. Rủi ro
1. Bấm X có thể mở picker vì `pickerOnly` nghe `PressInteraction.Release`. T1 có test ghim; nếu đỏ, chặn bằng `pointerInput` riêng cho X.
2. Ngữ nghĩa xoá: nếu suy ra "xoá" trong ViewModel (chuỗi rỗng trong khi đang lưu ngày hợp lệ), sẽ xoá nhầm khi dialog mở lúc còn trống rồi sync/Room đổ ngày về. Vì vậy dùng cờ tường minh, chụp lúc mở dialog, mặc định false.
3. Xoá ngày sinh mà bio cũng trống/chào mừng thì hồ sơ thành "pristine" (`ProfileProjection.isPristine`). Khi đó một doc Legacy từ xa có thể khôi phục ngày cũ. Khả năng thấp, vì Lưu đẩy doc schema-1 ngay. Chấp nhận, ghi lại ở đây.
4. Lint không còn dư: một import hoặc val chết hay một `contentDescription` hardcode mới cũng làm gate đỏ. contentDescription phải theo đúng mẫu VI/EN có sẵn trong file.
5. Dòng helper 11sp thay cho chip làm tuổi/cung kém nổi hơn. Đó là chủ ý thu gọn; chủ app có thể muốn đậm hơn, và nếu vậy chỉ cần đổi màu, không đổi layout.
6. Khi đã ghép đôi, tab Hồ sơ của dialog "Cặp đôi" không còn nút nào ở đáy (trước đây chỉ có "Đóng"); đóng bằng X ở header (`btn_close_edit_couple`, ≥48dp, đã có test :152/:182). Thẻ "Người ấy" và thẻ "Ngày yêu" chỉ hiện khi CHƯA ghép đôi, nên X xoá ngày sinh không bao giờ hiện ở chế độ chỉ đọc.
7. Hai X (xoá + lịch) trong ô ngày sinh chiếm 2 × `MinTouchBox` bề ngang. Với dialog 320 dp vẫn còn chỗ cho "dd/MM/yyyy" (ước tính ~180 dp còn lại cho chữ trước khi thu 0,85); nếu lệch thì T1/T3/T4 báo, không tự co touch target dưới 48 dp.
8. Giữ tiêu đề thẻ "Người ấy" (cho biết đây là hồ sơ của ai); chỉ bỏ tiêu đề thẻ "Ngày yêu" vì nhãn ô đã nói rõ. Chủ app muốn bỏ nốt thì sửa sau, không đổi logic.

## 7. Ngoài phạm vi
- skipped: nút Close ở tab Ghép đôi (`btn_close_pairing_tab`); add when sửa `openPairingTab` sang mốc khác.
- skipped: X trên ô ngày yêu; add when chủ app muốn xoá được ngày yêu (hiện ngày yêu là bắt buộc để đếm ngày).
- skipped: golden Roborazzi cho 2 dialog; add when có baseline screenshot cho dialog.
- skipped: dọn các import có thể đã thừa từ trước; add when có task dọn lint riêng.
- skipped: kiểm thử trên LDPlayer; add when emulator-5554 online và chủ app đồng ý.

## 8. Claude review (thay Codex, đã được miễn)
Sau khi T1-T4 xong và xanh, main session đọc diff 6 file main và 5 file test, rồi soát:
- Không còn tham chiếu `btn_clear_partner_birth`.
- `openedWithBirthday` dùng `remember` không key.
- Đường rename vẫn truyền mặc định false.
- Không có val hoặc import chết; touch target ≥48dp.
- Lint ≤128.

Mỗi phát hiện được kiểm lại với code trước khi sửa, tối đa 3 vòng.

### Nhật ký vòng 1 (Claude review kế hoạch, 2026-10-23, đối chiếu từng điểm với code)
| # | phát hiện | kết luận | hành động |
|---|---|---|---|
| 1 | T4 ghi `remember { birthDate.value.isNotBlank() }`, nhưng `birthDate` là `String` ủy quyền (`var birthDate by remember {…}`, EditMyProfileDialog.kt:178), không có `.value` | ĐÚNG, nhận | sửa thành `remember { birthDate.isNotBlank() }`, nhấn mạnh `remember` không key và sửa CẢ 2 chữ ký `onSave` (:117, :174) |
| 2 | Test "bấm X không mở picker" nếu viết bằng `performClick()` thì luôn xanh một cách vô nghĩa (mọi test cũ của `BirthdayFieldTest` dùng đường semantics, không đi qua `PressInteraction.Release`) | ĐÚNG, nhận | T1 bắt buộc `performTouchInput { click() }`, assert `changes == listOf("")` và `btn_select_year_month` không tồn tại |
| 3 | cd của X ghi chết "Xóa ngày sinh" trong component dùng chung | ĐÚNG một phần, nhận | đổi thành `if (isEnglish) "Clear date" else "Xóa ngày"` theo mẫu nút lịch (:1101) |
| 4 | Sau khi xoá "Đóng", `Row` hàng nút còn lại rỗng khi đã ghép đôi (Row chỉ chứa "Đóng" + `if (!isCoupled) Button`) | ĐÚNG, nhận | T3: xoá cả Row, đặt thẳng `if (!isCoupled) Button` |
| 5 | Xoá UI có thể mồ côi tài nguyên chuỗi và sinh lint `UnusedResources` (lint đã sát trần 128) | KHÔNG, bác | grep: cả hai dialog dùng chuỗi inline, 0 `stringResource` |
| 6 | Đổi nhãn "Bắt đầu yêu" có thể làm vỡ test tìm theo nhãn | KHÔNG, bác | grep `app/src/test`: không test nào tìm theo nhãn đó |
| 7 | `scripts/qa/test-inventory.mjs --check` có thể đỏ vì test mới | KHÔNG, bác | code: "test mới chưa ghi luôn OK"; chỉ lỗi khi test cũ mất hoặc đổi tên |
Kết quả vòng 1: 7 phát hiện, nhận 4, bác 3. Chưa cần vòng 2 vì không phát hiện nào còn mở.
