# Nguồn chuỗi chuẩn (canonical) - V-12

## Quyết định

File resource Android là nguồn chuẩn cho mọi trường của `AppStrings`:
EN là `res/values/strings.xml`, VI là `res/values-vi/strings.xml`.
Các bảng tĩnh trong `LocalizedStrings` (`vietnameseStatic`, `englishStatic`) chạy theo resource.
Bảng tĩnh chỉ là tạm thời và sẽ bị xoá ở task U-66d.

Các test sau giữ cho các nguồn luôn bằng nhau: `StringSourceReconcileTest`, `StaticVsResourceStringsTest`,
`StringPlaceholderParityTest`, `LocaleStringsGoldenTest`.

## Trường đã đổi giá trị tĩnh ở U-63b

Tổng 25 chuỗi: 12 tiếng Việt, 13 tiếng Anh. Giá trị chuẩn là giá trị resource (dạng chạy thật, `%1$d`).

| field | language | old static value | canonical value (resource) | kind |
|---|---|---|---|---|
| `nextMilestoneTitle` | VI | `Cột Mốc %d Ngày Yêu` | `Cột Mốc %1$d Ngày Yêu` | placeholder position |
| `daysRemainingFormat` | VI | `Còn %d ngày` | `Còn %1$d ngày` | placeholder position |
| `romanticAdviceFormat` | VI | `Gợi ý lãng mạn: Chỉ còn %d ngày nữa là chạm mốc %d ngày yêu! Hãy lên kế hoạch hẹn hò đặc biệt hoặc chuẩn bị món quà bất ngờ dành tặng cho %s nhé!` | `Gợi ý lãng mạn: Chỉ còn %1$d ngày nữa là chạm mốc %2$d ngày yêu! Hãy lên kế hoạch hẹn hò đặc biệt hoặc chuẩn bị món quà bất ngờ dành tặng cho %3$s nhé!` | placeholder position |
| `countingDaysNotice` | VI | `InLove: Đang đếm %d ngày yêu nhau` | `InLove: Đang đếm %1$d ngày yêu nhau` | placeholder position |
| `settingsSweetDaysFormat` | VI | `Đã đồng hành %d ngày ngọt ngào` | `Đã đồng hành %1$d ngày ngọt ngào` | placeholder position |
| `memoryNotePlaceholder` | VI | `Nhập dòng nhật ký hoặc lời nhắn gửi ngọt ngào...` | `Nhập dòng nhật ký hoặc lời nhắn gửi ngọt ngào…` | ellipsis |
| `memoryUrlPlaceholder` | VI | `Dán liên kết hình ảnh kỷ niệm (URL)...` | `Dán liên kết hình ảnh kỷ niệm (URL)…` | ellipsis |
| `btnSaveMemory` | VI | `Lưu Kỷ Niệm` | `Lưu Vào Kỷ Niệm` | wording |
| `memoryLoadingPhotos` | VI | `Đang xử lý ảnh...` | `Đang xử lý ảnh…` | ellipsis |
| `pairingTitle` | VI | `Ghép Đôi` | `Kết Nối Đôi Bạn 1-1` | wording |
| `pairingSub` | VI | `Đồng bộ đếm ngày và kỷ niệm thời gian thực` | `Ghép đôi cùng người ấy để đồng bộ đếm ngày và kỷ niệm thời gian thực` | wording |
| `paywallBtnTrial` | VI | `BẮT ĐẦU DÙNG THỬ 3 NGÀY MIỄN PHÍ` | `BẮT ĐẦU DÙNG THỬ MIỄN PHÍ` | wording |
| `nextMilestoneTitle` | EN | `Milestone %d Days` | `Milestone %1$d Days` | placeholder position |
| `daysRemainingFormat` | EN | `%d days left` | `%1$d days left` | placeholder position |
| `romanticAdviceFormat` | EN | `Romantic hint: Only %d days until reaching %d days of love! Plan a special dinner date or surprise %s with a heartfelt gift!` | `Romantic hint: Only %1$d days until reaching %2$d days of love! Plan a special dinner date or surprise %3$s with a heartfelt gift!` | placeholder position |
| `countingDaysNotice` | EN | `InLove: Counting %d days together` | `InLove: Counting %1$d days of romance` | wording + placeholder position |
| `settingsSweetDaysFormat` | EN | `%d sweet days together` | `%1$d sweet days together` | placeholder position |
| `settingsDbSecured` | EN | `Room Database secured • Tap to edit` | `Room Database secured (Tap to edit)` | wording |
| `memoryNotePlaceholder` | EN | `Write a sweet diary note or loving thought...` | `Write a sweet diary note or loving thought…` | ellipsis |
| `memoryUrlPlaceholder` | EN | `Paste photo memory link (URL)...` | `Paste photo memory link (URL)…` | ellipsis |
| `btnSaveMemory` | EN | `Save Memory` | `Save to Memories` | wording |
| `memoryLoadingPhotos` | EN | `Processing photos...` | `Processing photos…` | ellipsis |
| `pairingTitle` | EN | `Pairing` | `Couple Connection 1-1` | wording |
| `pairingSub` | EN | `Sync love counter and memories in real time` | `Pair with your loved one to synchronize day counter and memories in real time` | wording |
| `paywallBtnTrial` | EN | `START 3-DAY FREE TRIAL` | `START FREE TRIAL` | wording |

## Trường chưa có key resource, U-63b đã thêm

Bốn trường `memoryPickMultiple`, `memoryReorderTip`, `memoryCoverBadge`, `memoryLoadingPhotos` chưa có key resource.
U-63b thêm các key `memory_pick_multiple`, `memory_reorder_tip`, `memory_cover_badge`, `memory_loading_photos`
vào cả hai file `strings.xml`, với nội dung là giá trị tĩnh cũ (riêng `memoryLoadingPhotos` còn đổi `...` thành `…`, xem bảng trên).

## Lưu ý hiển thị cho chủ sản phẩm

`paywallBtnTrial` không còn hứa "3 ngày": VI `BẮT ĐẦU DÙNG THỬ MIỄN PHÍ`, EN `START FREE TRIAL`.
Thời lượng dùng thử thật lấy từ offer của Google Play, không lấy từ chuỗi này.

## Quy tắc cho việc sau

- Chuỗi mới phải thêm vào cả hai file `strings.xml` VÀ cả hai bảng tĩnh với cùng một giá trị (cho đến khi U-66d xoá bảng tĩnh).
- Số lượng và kiểu placeholder phải khớp giữa VI và EN.
