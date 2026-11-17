# Wave 2a: hợp nhất nội dung quà Việt Nam vào một gói dữ liệu (U-19, U-20, U-21, U-22)

Ngày lập: 2026-10-30. Trạng thái: BẢN NHÁP, chờ Codex duyệt kế hoạch (tối đa 3 vòng) rồi chờ chủ sở hữu duyệt. Chưa có dòng mã nào được viết.

Tài liệu nguồn: `docs/research/2026-10-27-gift-occasion-market-research.md` (mục 6.3.2, U-19..U-22 ở dòng 1533-1559). Mẫu cấu trúc: `docs/superpowers/plans/2026-10-27-gift-occasion-wave1.md`. Kế hoạch KMP: `docs/superpowers/plans/2026-10-13-kmp-ios/TASKS.md` (D-13 ở :343, D-15 ở :344, F-17 ở :199).

## 1. Mục tiêu

Đưa 20 món quà gốc ra khỏi mã Kotlin (`GiftIdeasSeed.kt`) vào một gói dữ liệu duy nhất `app/src/main/assets/content/VN.json`, có kiểm hợp lệ, có khoá ổn định `pk:VN:{giftId}` và giá có đơn vị tiền tệ. Gói được đọc mỗi lần khởi động, và có thể được thay bằng bản mới hơn từ Firestore `content_packs/VN` mà không cần phát hành app (sửa F-06 về phía mã).

Bốn việc, đúng phạm vi tối thiểu chỉ cho Việt Nam:

- U-19: mô hình `ContentPack` cùng bộ kiểm hợp lệ.
- U-20: sinh gói VN từ `GiftIdeasSeed` và kiểm khứ hồi từng trường; các golden giữ nguyên từng byte.
- U-21: `PackContentRepository` thay cho seed trong mã; sửa F-06.
- U-22: khoá quà ổn định `pk:{region}:{giftId}` và `Price(currency, min, max)`.

Kết quả phải xong trước D-13 và D-15 (cả hai chưa đánh dấu xong). Room giữ v14; `app/schemas/com.example.data.db.AppDatabase/14.json` giữ nguyên từng byte.

## 2. Ràng buộc chung

- Không thêm thư viện. Mô hình dùng Moshi sẵn có (`@JsonClass(generateAdapter = true)`, mẫu ở `data/media/MemoryBackup.kt:20,27,176`). App không phụ thuộc `:shared` (F-17 chưa làm) nên mã mới nằm ở `app/src/main/java/com/example/data/content/`.
- Gói chỉ là dữ liệu: không công thức, không luật ngày. `CalendarMath` (DEC-59, DECISIONS.md:359) vẫn là nơi duy nhất được phép tính ngày; trong wave này không có mã nào tính ngày.
- Không đổi lược đồ Room. Việc chuyển khoá diễn ra lười khi gieo quà từ gói, cộng MỘT hàm ánh xạ khoá cũ sang khoá mới (`GiftKeys.alias`) dùng ở đúng hai nơi: bộ gieo quà và `AccountDataVault`.
- Không đụng Firestore rules, không tạo hay sửa tài liệu thật trong `content_packs`, không deploy. Tài liệu Firestore `gift_ideas` cũ giữ nguyên; `CloudPresetFetcher.kt` giữ nguyên.
- Golden: 25 file trong `shared/src/commonTest/resources/golden/` phải giữ nguyên từng byte. Wave này KHÔNG chạy `GOLDEN_UPDATE=1` cho golden nào. Nếu một golden lệch thì DỪNG và báo, không sinh lại.
- Giữ kiểu xuống dòng của mọi file sở hữu (bảng ở mục 6). Không BOM. File mới: LF, UTF-8, NFC, đúng một dòng trống cuối.
- Tên test Robolectric trong test Room hoặc vault dài tối đa 60 ký tự (một tên 87 ký tự từng gây `SQLITE_CANTOPEN` trên Windows).
- Bình luận trong mã chứa tên của chế độ phạm vi tối thiểu (ví dụ `GiftCatalog.kt:10`, `GiftCatalog.kt:108`, `InLoveRepository.kt:140`, `GetPersonalizedGiftSuggestionsUseCase.kt:30-32`) được giữ nguyên văn; không task nào sửa, xoá hay chép lại chúng. `GiftCatalog.kt:108` thuộc hàm `seedRemoteIdFor`; U21b đổi thân hàm nhưng giữ dòng bình luận.
- Cây làm việc dùng chung với hai phiên khác:
  - trước mỗi wave gửi `W<n> START: files <danh sách>`, sau cổng gửi `W<n> END: tests <n>, lint <n>`;
  - Gradle chạy tuần tự, một lần một lệnh;
  - đóng băng mã nguồn trong lúc chạy cổng;
  - không `git checkout`, `git stash`, `git reset`;
  - không commit, push hay PR;
  - không adb, không emulator.
- Lệnh git chỉ đọc luôn dùng `git --no-optional-locks`.
- Chạy Gradle bằng `Start-Process` với output ghi ra file và `WaitForExit` (bài học: pipe bắt output từng treo một lần chạy khi daemon mới khởi động).
- Cổng:
  - Cổng A = `.\gradlew.bat :app:testDebugUnitTest` cộng `.\gradlew.bat :shared:testAndroidHostTest`, chạy trên cây CUỐI.
  - Cổng B = `.\gradlew.bat :app:lintDebug` (0 lỗi, tối đa 131 cảnh báo, không còn dư) cộng `.\gradlew.bat :app:assembleDebug`, chạy trên cùng cây đó.
  - Mọi sửa mã nguồn sau một cổng mở lại cả hai cổng.
- Lint không còn dư: không dùng `SharedPreferences` (`commit()` không dùng kết quả và khối `prefs.edit()` từng tốn cảnh báo). Mã mới chỉ ghi file và gọi Firestore `get()`.

## 3. Giả định

| id | giả định | trạng thái |
|---|---|---|
| A1 | App không có `implementation(project(":shared"))`; F-17 chưa làm (TASKS.md:199 `[ ]`). Vì vậy mã mới không vào được `shared/src/commonMain`. | VERIFIED (đọc `app/build.gradle.kts`, TASKS.md:199) |
| A2 | App dùng Moshi (codegen qua ksp), không có kotlinx-serialization. Moshi bỏ qua khoá lạ theo mặc định. | VERIFIED (`MemoryBackup.kt:20,27,176`) |
| A3 | `buildConfig = true` (`app/build.gradle.kts:110`), `versionCode = 1` (:37), nên `BuildConfig.VERSION_CODE` dùng được làm phiên bản app. | VERIFIED |
| A4 | Unit test chạy với thư mục làm việc `app/` và `isIncludeAndroidResources = true` (:112): test JVM đọc được `File("src/main/assets/content/VN.json")`, test Robolectric đọc được asset. | VERIFIED (`GoldenWriter.kt:11` dùng `../shared/...`) |
| A5 | `gift_ideas.remoteId` có unique index (`Entities.kt:34`). Hàng `pk:VN:*` không trùng hàng cloud `gift_*` (`scripts/seed_firestore.js`), hàng `tpl_*` (`GiftSuggestionGenerator.kt:40-98`) hay `local_*`/`legacy_*`. | VERIFIED |
| A6 | `upsertGiftIdeaByRemoteId` (`InLoveDao.kt:80-89`) giữ `id` và `isFavorited`, bỏ qua lần ghi khi không có gì đổi. DAO không đổi trong wave này. | VERIFIED |
| A7 | Lời nhắc quà (`gift_reminders`, `Entities.kt:177`) không tham chiếu `gift_ideas.remoteId`, nên đổi khoá không ảnh hưởng chúng. | VERIFIED (grep `remoteId` ngoài `data/db`) |
| A8 | Overlay yêu thích của vault khớp theo `remoteId` (`AccountDataVault.kt:301-304`); câu UPDATE khôi phục ở :201. Hàng `pk:` không khớp `PERSONAL_GIFT` (:284-285) nên vẫn là hàng catalog có overlay. | VERIFIED |
| A9 | Khoá `reminder_settings` là `7_days`, `3_days`, `1_day`, `exact_day` (`OfflineSeeder.kt:23-26`); không khoá nào bắt đầu bằng `seed_` hay `pk:`, nên `GiftKeys.alias` áp lên overlay đó không đổi gì. | VERIFIED |
| A10 | Không golden nào chứa `seed_`; chỉ GV-029 có `priceRange` (dạng nhãn cũ `<200k`, `200k-500k`, `500k-1tr`, `>1tr`, `""`, `100k-200k`). `Price.parse` nhận nhãn cũ nên 25 golden không đổi. | VERIFIED bằng grep; byte-identity kiểm lại ở WF |
| A11 | 20 `imageSeed` đều khớp `^[a-z0-9][a-z0-9-]*$` (từ `gift-rose-bouquet` đến `gift-playlist`); 19 món có `curatedImageUrl` Pexels, `gift-photo-lamp` dùng URL picsum. Cả hai đều https và không phải Cloudinary, nên qua được `MemorySyncAdapter.safeSharedPhotoUri` (:76-81). | VERIFIED |
| A12 | `ContentRepository.kt` và `ContentModels.kt` không có bản cài và không có chỗ gọi; DEC-69 và `p2-data-domain.md:92,124` vẫn liệt kê chúng cho D-13. `gift_catalog.json` và `holiday_calendar.json` không được mã nào đọc. | VERIFIED |
| A13 | `OfflineSeeder` được tạo ở `InLoveRepository.kt:45` và qua ba lời gọi ở hai file test (`HolidaySeedGoldenTest.kt:48`; `OfflineSeederNotificationTest.kt:34` và `:46`); cả ba truyền `today` THEO TÊN (`today = { ... }`), nên một tham số cuối có giá trị mặc định vẫn biên dịch; hai file test này không kiểm quà. | VERIFIED |
| A14 | Lời gọi `initializeDefaultDataIfEmpty` chạy trong một `viewModelScope.launch` (`InLoveViewModel.kt:628-632`), ngay sau đó là `AlarmNotificationScheduler.scheduleAllAnniversariesFromDb`. Lời gọi không chặn luồng giao diện, nhưng mọi thời gian chờ thêm đều làm chậm việc đặt báo thức. Lần làm mới gói chạy Ở CUỐI hàm với hạn 2000 ms, nối sau lần chờ có hạn 2000 ms sẵn có của `CloudPresetFetcher` (`:153`, `:181`), nên thời gian chờ xấu nhất tăng từ 2000 ms lên 4000 ms. | UNVERIFIED (chỉ đọc mã; cảm giác trên máy thật là việc của chủ sở hữu, O3) |
| A15 | Tài liệu `content_packs/VN` chưa tồn tại và rules chưa cho đọc; khi đó `get()` lỗi và app dùng gói trong APK. F-06 chỉ có hiệu lực đầu cuối sau khi chủ sở hữu xuất bản gói (U-25). | UNVERIFIED (chưa xem rules thật) |
| A16 | Số liệu cổng gần nhất: 172 suite / 1375 test / 0 lỗi; lint 0 lỗi / 131 cảnh báo. T0 đo lại; nếu T0 cho số khác thì T0 thắng. | UNVERIFIED (lấy từ ghi chú phiên trước) |
| A17 | `CalendarMath.kt` không tồn tại trong cây (TASKS.md:593 nói D-09 đã chuyển nó). Wave này không cần nó. | VERIFIED (Glob) |

## 4. Câu hỏi thiết kế và mặc định

| câu | mặc định | lý do |
|---|---|---|
| (a) Mô hình và bộ kiểm đặt ở đâu, viết bằng gì? | Một file mới `data/content/ContentPack.kt`: lớp Moshi, `ContentPack.parse(json, appVersion)` trả gói đã làm sạch hoặc `null`, `GiftKeys`, `Price`. SHA-256 dùng `java.security.MessageDigest` trực tiếp trong `PackContentRepository`. | Không có `:shared` (A1). Một file, không giao diện, không truyền hàm băm từ ngoài vì chỉ có một nơi băm. D-13 chuyển file này sang commonMain sau F-17. Bộ kiểm Node để U-25. |
| (b) Gói nằm ở đâu? | `app/src/main/assets/content/VN.json`. Bản tốt cuối: `filesDir/content/VN.json`. Không có `INTL.json`. | Chỉ một thị trường. INTL chưa có bản gói nào để so. |
| (c) Hình dạng `PackContentRepository`? | Lớp cụ thể, tạo trong `InLoveRepository` từ `appContext`. Tham số: `readBundled: () -> String?`, `dir: File`, `appVersion: Int = BuildConfig.VERSION_CODE`, `fetchRemote: suspend () -> Map<String, Any?>?` (mặc định: một `get()` Firestore `content_packs/VN` qua `FirebaseFirestore.getInstance()` và `.await()`, không thêm thư viện; nuốt mọi ngoại lệ TRỪ `CancellationException` (ném lại); hạn 2000 ms nằm trong `refreshFromRemote`; khi không có FirebaseApp thì `getInstance()` ném lỗi, bị nuốt thành `null` và rơi về gói APK, cùng cách `CloudPresetFetcher` đang chạy trong các test Robolectric hiện có). Hàm `local()` và `refreshFromRemote()`. | Hai lambda là đủ để test không cần Firebase. Không cài `ContentRepository` vì giao diện đó đòi phần lễ (ngoài phạm vi). |
| (d) Chuyển khoá thế nào mà không đổi lược đồ? | `GiftKeys.alias`: `seed_X` thành `pk:VN:X`, `pk:VN:X` thành `seed_X`, khoá khác giữ nguyên. Bộ gieo đổi `remoteId` của hàng `seed_X` sang `pk:VN:X` bằng `updateGiftIdea` (giữ `id`, giữ yêu thích). Vault khôi phục overlay bằng `IN (?, ?)` với khoá và alias của nó. | Một hàm, hai nơi dùng. Lười, không cần migration, chạy lại không đổi gì. Stash cũ hay mới đều khôi phục được. |
| (e) Số phận các file seed? | Xoá `GiftIdeasSeed.kt` (sau khi khứ hồi qua), test sinh gói và `gift_catalog.json`. GIỮ `ContentRepository.kt`, `ContentModels.kt`, `holiday_calendar.json`, `VietnameseHolidays.kt`, `WesternHolidays.kt`, `HolidayDates.kt`. | Gói mới thay đúng phần quà. Phần lễ chưa vào gói (DEC-59, U-26, U-27). DEC-69 vẫn ghim `ContentRepository` cho D-13; xoá nó sẽ làm sai đầu vào của kế hoạch KMP. |
| (f) Finding nào được đóng? | Mục 13. | |
| (g) Test và fixture đặt ở đâu? | `app/src/test/java/com/example/data/content/`; fixture JSON nhỏ viết thẳng trong test dưới dạng chuỗi, không thêm thư mục `resources`. Trợ giúp `TestPacks.kt` đọc `src/main/assets/content/VN.json`. | Ít file nhất. |
| (h) Có cần nâng Room, thêm thư viện hay thêm thị trường không? | Không. Phạm vi không tăng. | Khoá cũ sống được nhờ alias; giá mã hoá trong cột `priceRange` sẵn có. Nếu khi làm phát hiện cần một trong ba thứ đó thì DỪNG và báo, không mở rộng kế hoạch. |

## 5. Hình dạng gói VN (phiên bản lược đồ 1)

```json
{
  "schemaVersion": 1,
  "packVersion": 1,
  "minAppVersion": 1,
  "region": "VN",
  "sourceLanguage": "vi",
  "gifts": [
    {
      "id": "gift-rose-bouquet",
      "title": { "vi": "Bó Hoa Hồng Sáp", "en": "Everlasting Rose Bouquet" },
      "badge": { "vi": "...", "en": "..." },
      "description": { "vi": "...", "en": "..." },
      "details": { "vi": "...", "en": "..." },
      "action": { "vi": "...", "en": "..." },
      "category": "Quà lãng mạn",
      "tag": "Kỷ niệm",
      "occasion": "Valentine",
      "price": { "currency": "VND", "min": 200000, "max": 500000 },
      "interests": [],
      "imageUrl": "https://images.pexels.com/..."
    }
  ]
}
```

- Mọi trường có giá trị mặc định: một trường THIẾU nhận giá trị mặc định và bộ kiểm chạy SAU khi parse. Một giá trị `null` tường minh hoặc sai kiểu cho trường không null (ví dụ `"price": null`, `"min": "abc"`) làm Moshi ném lỗi dù trường có mặc định; `parse` bắt mọi `Exception` và coi đó là lỗi cấu trúc (bỏ cả gói).
- `max` vắng mặt nghĩa là không có trần (bậc `>1tr` cũ).
- `category`, `tag`, `occasion` giữ đúng chuỗi tiếng Việt hiện dùng làm khoá logic (F-03 để sau).
- Chữ hiển thị chọn theo thứ tự: ngôn ngữ người dùng, rồi `sourceLanguage`, rồi `en`.
- Lỗi cấu trúc (bỏ cả gói):
  - JSON hỏng;
  - `schemaVersion` khác 1;
  - kích thước lớn hơn 256 KB;
  - `minAppVersion` lớn hơn `appVersion`;
  - `region` rỗng;
  - sai kiểu hoặc `null` tường minh ở một trường không null (Moshi ném lỗi; `parse` bắt mọi ngoại lệ).
- Lỗi bản ghi (chỉ bỏ món đó):
  - `id` không khớp `^[a-z0-9][a-z0-9-]{0,63}$` (quy tắc này tự loại `:` và các tiền tố `seed_`, `local_`, `tpl_`, `legacy_`);
  - `id` trùng (giữ món đầu);
  - thiếu `title` ở `sourceLanguage`;
  - giá hỏng: `currency` không phải 3 chữ hoa, `min` âm, hoặc `max` nhỏ hơn `min`.
- `imageUrl` không qua `safeSharedPhotoUri` thì chỉ bỏ ảnh (thành chuỗi rỗng), giữ món.
- Kiểm `sha256` nằm ở `PackContentRepository` (chỉ áp cho gói từ Firestore).
- Tài liệu Firestore `content_packs/VN` (do U-25 tạo, ngoài wave này) có hai trường: `packJson` (chuỗi) và `sha256` (hex chữ thường của byte UTF-8 của `packJson`).
- `Price.encode()` ghi vào cột `priceRange` dạng `CUR:min-max`, ví dụ `VND:200000-500000`; không trần thì `VND:1000000-`. `Price.parse` nhận dạng đó và bốn nhãn cũ:
  - `<200k` = `VND:0-200000`;
  - `200k-500k` = `VND:200000-500000`;
  - `500k-1tr` = `VND:500000-1000000`;
  - `>1tr` = `VND:1000000-`.
- Chuỗi khác (`""`, `100k-200k`, giá chữ tự do từ cloud) trả `null`, đúng như hành vi hiện tại.
- Bỏ qua trong lược đồ 1: occasions, milestones, taboos, luật ngày, tierBands, languages, calendars, `currency` cấp gói.

## 6. File sở hữu (đo 2026-10-30 trên cây làm việc, chưa commit)

| viết tắt | đường dẫn | byte | xuống dòng | task |
|---|---|---|---|---|
| CP | app/src/main/java/com/example/data/content/ContentPack.kt | mới | LF | U19 |
| CPT | app/src/test/java/com/example/data/content/ContentPackTest.kt | mới | LF | U19 |
| ADV | app/src/main/java/com/example/data/db/AccountDataVault.kt | 12778 (306 dòng, SHA-256 bắt đầu `1312ED69B35F`) | LF | U22b |
| AIT | app/src/test/java/com/example/data/db/AccountIsolationTest.kt | 10195 | LF | U22b |
| VNJ | app/src/main/assets/content/VN.json | mới | LF | U20 |
| GEN | app/src/test/java/com/example/data/content/VnPackGeneratorTest.kt | mới, xoá ở U21c | LF | U20, U21c |
| GC | app/src/main/java/com/example/domain/content/GiftCatalog.kt | 4664 | LF | U22a, U21b |
| UC | app/src/main/java/com/example/domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt | 9807 | LF | U22a |
| PLT | app/src/test/java/com/example/domain/content/PriceLabelTest.kt | mới | LF | U22a |
| PCR | app/src/main/java/com/example/data/content/PackContentRepository.kt | mới | LF | U21a |
| PCRT | app/src/test/java/com/example/data/content/PackContentRepositoryTest.kt | mới | LF | U21a |
| OS | app/src/main/java/com/example/data/repository/OfflineSeeder.kt | 10511 | CRLF | U21b |
| IR | app/src/main/java/com/example/data/repository/InLoveRepository.kt | 17347 | CRLF | U21b |
| GCT | app/src/test/java/com/example/domain/content/GiftCatalogTest.kt | 7411 | LF | U21b |
| CED | app/src/test/java/com/example/CloudEnrichmentDataTest.kt | 7120 | CRLF | U21b |
| TP | app/src/test/java/com/example/data/content/TestPacks.kt | mới | LF | U21b |
| PST | app/src/test/java/com/example/data/content/PackSeedingTest.kt | mới | LF | U21b |
| GIS | app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt | 26491 | LF | U21c (xoá) |
| GCJ | app/src/main/assets/content/gift_catalog.json | 17760 | CRLF | U21c (xoá) |
| UCT | app/src/test/java/com/example/domain/usecase/GetPersonalizedGiftSuggestionsUseCaseTest.kt | 8191 | LF | U21c |
| PDT | app/src/test/java/com/example/data/repository/PresetDedupeTest.kt | 3391 | LF | U21c |
| NNI | app/src/test/java/com/example/ui/testing/NoNetworkImagesRule.kt | 1176 | LF | U21c (chỉ bình luận :15) |

File chỉ đọc, phải giữ nguyên từng byte:

- `InLoveDao.kt` (20391, CRLF);
- `CloudPresetFetcher.kt` (10352, CRLF);
- `MemorySyncAdapter.kt`;
- `ContentRepository.kt` (1511, CRLF);
- `ContentModels.kt` (2935, CRLF);
- `holiday_calendar.json` (11769, CRLF);
- `VietnameseHolidays.kt` (10435, CRLF);
- `WesternHolidays.kt` (4750, CRLF);
- `HolidayDates.kt` (2260, LF);
- `Entities.kt` (11858, LF);
- `AppDatabase.kt` (2152, LF);
- `app/schemas/com.example.data.db.AppDatabase/14.json` (46415, LF);
- 25 golden;
- mọi test bảo vệ ở mục 9 không có tên trong bảng trên.

## 7. Bảng task

| id | files (owned) | change | test or command | depends on | model |
|---|---|---|---|---|---|
| T0 | `<BASELINE_DIR>` trong scratchpad (không file repo nào) | Chụp nền: bản sao mọi file ở mục 6 đang tồn tại, 25 golden, `14.json`, `MANIFEST.txt` (SHA-256), `STATUS-BASELINE.txt` (`git --no-optional-locks status --porcelain`), `EMPTY.txt`; chạy cổng A và B để đo số nền. | `.\gradlew.bat :app:testDebugUnitTest`; `.\gradlew.bat :shared:testAndroidHostTest`; `.\gradlew.bat :app:lintDebug` | - | haiku (effort max) |
| U19 | CP, CPT | Mô hình Moshi `ContentPack`/`PackGift`/`PackPrice`, `ContentPack.parse(json, appVersion)` (bắt mọi ngoại lệ, trả `null`), `PackGift.toEntity(region, language)`, `GiftKeys.pk`/`GiftKeys.alias`, `Price.parse`/`Price.encode`. | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.content.ContentPackTest"` | T0 | sonnet (effort high) |
| U22b | ADV, AIT | Một dòng ở ADV:201: `WHERE "${o.key}" IN (?, ?)` với tham số `(v, k, GiftKeys.alias(k))`. Thêm 3 test vào AIT. | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.db.AccountIsolationTest"` rồi `--tests "com.example.data.db.*"` | U19 | sonnet (effort high) |
| U20 | VNJ, GEN | GEN dựng gói từ `GiftIdeasSeed.all` rồi in bằng adapter Moshi (`indent("  ")`, LF, thêm một `\n` cuối). Có `GOLDEN_UPDATE=1` thì ghi VNJ; không có thì so từng byte. Kiểm khứ hồi từng trường cho cả VI và EN. | `$env:GOLDEN_UPDATE='1'; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.content.VnPackGeneratorTest"; Remove-Item Env:GOLDEN_UPDATE` một lần, rồi chạy lại cùng lệnh KHÔNG có biến | U19 | sonnet (effort high) |
| U22a | GC (chỉ `priceLabel`), UC (chỉ `priceFloorVnd` và import), PLT | `priceLabel` tính từ `Price.parse`; giữ chữ ký và giữ nguyên từng byte nhãn của bốn bậc cũ; tiền khác VND hiện mã ISO. `priceFloorVnd` = `Price.parse(raw)?.takeIf { it.currency == "VND" }?.min`. | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.domain.content.PriceLabelTest" --tests "com.example.domain.content.GiftCatalogTest" --tests "com.example.domain.usecase.GetPersonalizedGiftSuggestionsUseCaseTest" --tests "com.example.golden.PersonalizedGiftGoldenTest"` | U19 | sonnet (effort high) |
| U21a | PCR, PCRT | `local()`: chọn giữa gói APK và bản tốt cuối, `packVersion` cao hơn thắng, bằng nhau thì APK thắng, bản hỏng bị bỏ. `refreshFromRemote()`: một `get()`, hạn 2000 ms; chỉ nhận khi `sha256` khớp, gói hợp lệ và `packVersion` cao hơn bản đang dùng; ghi `.tmp` rồi đổi tên (mẫu `MemoryBackup.kt:219-228`). | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.content.PackContentRepositoryTest"` | U19, U20 | sonnet (effort high) |
| U21b | OS, IR, GC (`install`, `localized`, `seedRemoteIdFor`), GCT, CED, TP, PST | Bộ gieo đọc gói thay cho `GiftIdeasSeed`, chuyển khoá `seed_X` sang `pk:VN:X`, cài gói vào `GiftCatalog`, làm mới từ Firestore mỗi lần khởi động (ở cuối `initializeDefaultDataIfEmpty`). | `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.data.content.PackSeedingTest" --tests "com.example.CloudEnrichmentDataTest" --tests "com.example.domain.content.GiftCatalogTest" --tests "com.example.golden.HolidaySeedGoldenTest" --tests "com.example.data.repository.OfflineSeederNotificationTest"` | U20, U21a, U22a, U22b | sonnet (effort high) |
| U21c | xoá GIS, GEN, GCJ; sửa UCT, PDT, NNI | Chuyển ba test còn lại sang `TestPacks`; xoá seed trong mã, test sinh gói và JSON trùng lặp. | `Select-String` (mục 8, U21c) rồi `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.domain.usecase.*" --tests "com.example.data.repository.PresetDedupeTest" --tests "com.example.domain.content.*"` | U21b | haiku (effort max) |
| WF | không file nào | Cổng A, cổng B, kiểm golden và `14.json`, so `status`, gửi diff cho Codex. | mục 9 | U21c | phiên chính |

Tổng: 8 task có thay đổi (T0, U19, U22b, U20, U22a, U21a, U21b, U21c) cộng WF.

## 8. Chi tiết task và điều kiện chấp nhận

### T0. Ảnh chụp nền

- `<BASELINE_DIR>` = `<scratchpad>\gift-wave2a-baseline`. Chép mọi file ở mục 6 đang tồn tại (giữ đường dẫn tương đối), `shared/src/commonTest/resources/golden/` (25 file), `app/schemas/com.example.data.db.AppDatabase/14.json`.
- `MANIFEST.txt`: SHA-256 và số byte của từng bản chép. `STATUS-BASELINE.txt`: toàn bộ output `git --no-optional-locks status --porcelain`. `EMPTY.txt`: file rỗng.
- Ghi vào execution log: số suite, test, lỗi (đọc `app/build/test-results/testDebugUnitTest/*.xml`), kết quả `:shared:testAndroidHostTest`, số lỗi và cảnh báo lint (`app/build/reports/lint-results-debug.txt`).
- Chấp nhận: MANIFEST có đúng 25 dòng golden và 1 dòng `14.json`; ADV trong MANIFEST bắt đầu bằng `1312ED69B35F` và dài 12778 byte. Nếu ADV khác thì DỪNG: vault đã bị sửa sau lần Codex duyệt.

### U19. `ContentPack` và bộ kiểm

- CP chứa:
  - các lớp `@JsonClass(generateAdapter = true)` `ContentPack`, `PackGift`, `PackPrice`, mọi trường có mặc định;
  - `ContentPack.parse(json: String, appVersion: Int): ContentPack?`: bọc parse và làm sạch trong `try`, bắt mọi `Exception` (hàm không `suspend` nên không có vấn đề huỷ coroutine) và trả `null`;
  - `PackGift.text(field, language, source)`, hoặc một hàm chọn chữ dùng chung cho năm trường chữ;
  - `PackGift.toEntity(region: String, language: AppLanguage): GiftIdeaEntity`, trong đó `remoteId = GiftKeys.pk(region, id)`, `priceRange = Price(...).encode()`, `targetInterests = interests.joinToString(",")`, `isAiGenerated = false`;
  - `object GiftKeys`;
  - `data class Price(currency: String, min: Long, max: Long?)` cùng `parse` và `encode`.
- Lọc URL gọi thẳng `com.example.data.sync.safeSharedPhotoUri` (internal, cùng module), không chép lại quy tắc.
- Test trong CPT (JVM thuần, không Robolectric), mỗi test một đường:
  - `validPackParses`;
  - `badJsonDropsPack`;
  - `wrongTypeDropsPack`, phủ `"price": null`, `"min": "abc"`, `"gifts": {}`, `"packVersion": "x"` (mỗi biến thể bỏ cả gói, `parse` trả `null` và không ném);
  - `newerSchemaDropsPack`;
  - `oversizePackDropsPack`;
  - `appTooOldDropsPack`;
  - `blankRegionDropsPack`;
  - `reservedOrColonIdDropsGift`, phủ `seed_a`, `local_a`, `tpl_a`, `legacy_a`, `a:b`, `A`;
  - `duplicateIdKeepsFirst`;
  - `missingSourceTitleDropsGift`;
  - `badPriceDropsGift`, phủ `currency` sai, `min` âm, `max` nhỏ hơn `min`;
  - `unsafeImageDropsOnlyImage`, phủ http, Cloudinary, chuỗi rác;
  - `safeImageIsKept` (đối chứng dương);
  - `textFallsBackToSourceThenEnglish`;
  - `unknownFieldsAreIgnored`;
  - `aliasMapsSeedAndPackKeysBothWays`;
  - `aliasLeavesOtherKeysAlone`, phủ `gift_x`, `tpl_x`, `local_x`, `7_days`, `""`;
  - `priceParsesLegacyLabels`;
  - `priceRejectsUnknownLabels`, phủ `""`, `100k-200k`, `150.000đ - 350.000đ`;
  - `priceEncodeRoundTrips`.
- Chấp nhận: lệnh ở bảng cho 0 lỗi; CP không import gì từ `com.google.firebase` hay `android.content`; không thêm dòng nào vào `app/build.gradle.kts`.

### U22b. Vault khôi phục yêu thích qua alias (task riêng)

- ADV: 12778 byte, LF, 306 dòng, đã qua Codex ở wave 1. Chỉ đổi đúng dòng :201 (câu UPDATE khôi phục overlay) thành dạng `IN (?, ?)` với mảng tham số `arrayOf<Any?>(c.getLong(1), c.getString(0), GiftKeys.alias(c.getString(0)))`, cộng một dòng `import com.example.data.content.GiftKeys`.
- Không dòng nào khác của ADV được đổi; `writeStash` (:156-166) giữ nguyên, tức stash luôn lưu khoá hiện có của hàng.
- Ba test mới trong AIT, tên tối đa 60 ký tự, dùng lại trợ giúp `favorited` (:222):
  - `oldSeedKeyStashRestoresOntoPackRow`: hàng `seed_rose` được yêu thích dưới tài khoản A; chuyển sang khách; đổi hàng thành `pk:VN:rose` (mô phỏng bộ gieo); quay lại A thì `pk:VN:rose` được yêu thích.
  - `packKeyStashRestoresOntoOldSeedRow`: chiều ngược lại.
  - `unrelatedKeyIsNotAliased`: stash có `catalog_rose` thì hàng `pk:VN:catalog_rose` KHÔNG đổi. Đây là đối chứng âm.
- Đối chứng dương: test cũ `favoritesOnCatalogRowsFollowTheAccount` (:237-251) vẫn qua.
- Chấp nhận:
  - `git diff --no-index <BASELINE_DIR>\...\AccountDataVault.kt app\...\AccountDataVault.kt` chỉ cho đúng 1 dòng import thêm và 1 dòng :201 đổi;
  - ADV vẫn LF, không CRLF;
  - toàn bộ `com.example.data.db.*` cho 0 lỗi;
  - ghi số byte mới và SHA-256 mới vào execution log.

### U20. Sinh `VN.json` và kiểm khứ hồi

- GEN dựng `ContentPack(schemaVersion = 1, packVersion = 1, minAppVersion = 1, region = "VN", sourceLanguage = "vi", gifts = ...)` từ `GiftIdeasSeed.all` theo đúng thứ tự:
  - `id = imageSeed`;
  - `price` lấy từ `Price.parse(seed.priceRange)`;
  - `interests` lấy từ `interestKeys.split(",")`, chuỗi rỗng cho danh sách rỗng;
  - `imageUrl = seed.imageUrl`, tức URL picsum được ghi rõ cho `gift-photo-lamp`.
- In bằng `adapter.indent("  ").toJson(pack) + "\n"`.
- Chọn Moshi thay cho `GoldenWriter`, vì `GoldenWriter` thoát mọi ký tự ngoài ASCII thành `\uXXXX`, làm tiếng Việt trong gói không đọc được khi sửa tay.
- Test trong GEN:
  - `vnPackFileMatchesGenerator`;
  - `everySeedRoundTripsInBothLanguages`: với mỗi seed và mỗi `AppLanguage`, `gift.toEntity("VN", lang) == seed.toEntity(lang).copy(remoteId = "pk:VN:" + seed.imageSeed, priceRange = Price.parse(seed.priceRange)!!.encode())`, so bằng data class nên phủ mọi cột;
  - `vnPackIsValidNfcAndSmall`: `ContentPack.parse(text, 1)` giữ đủ 20 món, mọi chuỗi qua `Normalizer.isNormalized(s, NFC)`, kích thước dưới 256 KB, không có `\r`, không BOM, đúng một `\n` cuối.
- Chấp nhận: lần chạy thứ hai (không có biến môi trường) cho 0 lỗi; VNJ có 20 món; `git --no-optional-locks status --porcelain` chỉ thêm VNJ và GEN, không có golden nào trong danh sách.

### U22a. Giá có đơn vị tiền tệ

- GC: chỉ thân `priceLabel` (:96-105) đổi. Nhãn VND:
  - có `min` 0 thì `under`/`dưới` + trần;
  - không có trần thì `over`/`trên` + sàn;
  - còn lại `sàn–trần`;
  - phân nhóm nghìn bằng `,` (EN) hoặc `.` (VI), hậu tố ` ₫`;
  - tiền tố `Indicative price: ` / `Giá tham khảo: ` giữ nguyên.
- Tiền khác VND: `USD 10–20` (mã ISO đứng trước, cùng cách phân nhóm).
- Import `GiftIdeasSeed` trong GC CHƯA bỏ ở task này (`seedsByRemoteId` còn dùng; U21b bỏ).
- UC: `priceFloorVnd` như ở bảng; bỏ `import com.example.data.seed.GiftIdeasSeed` (:4). KDoc :30-32 giữ nguyên.
- PLT:
  - `legacyLabelsAreByteIdentical`: bốn bậc × hai ngôn ngữ, so với tám chuỗi chép từ GC:98-101 trước khi sửa;
  - `encodedVndMatchesLegacyLabel`;
  - `foreignCurrencyShowsIsoCode`;
  - `unknownPriceGivesNoLabel`;
  - `usdRowIsKeptForAnyVndBudget`: gọi `GetPersonalizedGiftSuggestionsUseCase` với một hàng `USD:10-20` và ngân sách 100000 VND; hàng vẫn còn vì sàn là `null`;
  - `vndFloorFiltersAsBefore`.
- Chấp nhận: lệnh ở bảng cho 0 lỗi; `PersonalizedGiftGoldenTest` qua mà KHÔNG cần `GOLDEN_UPDATE`.

### U21a. `PackContentRepository`

- PCR như mục 4 (c). Gói từ Firestore:
  - đọc `packJson` và `sha256`;
  - so `sha256` với hex chữ thường của `MessageDigest.getInstance("SHA-256")` trên byte UTF-8;
  - chỉ nhận khi `ContentPack.parse` khác `null` và `packVersion` lớn hơn của `local()`.
- Ghi gói: `dir.mkdirs()`, ghi `VN.json.tmp`, rồi `renameTo`; trên Windows đổi tên thất bại thì xoá đích rồi đổi tên lại.
- Mọi ngoại lệ trong `fetchRemote` và mọi lỗi I/O khi đọc bản tốt cuối đều thành `null` hoặc rơi về gói APK, không bao giờ ném ra ngoài; riêng `CancellationException` được ném lại (hạn 2000 ms nằm trong `refreshFromRemote` qua `withTimeoutOrNull`, nên hết hạn vẫn cho `null`).
- `fetchRemote` mặc định: `FirebaseFirestore.getInstance().collection("content_packs").document("VN").get().await()`, đọc `packJson` và `sha256`; dùng lại `kotlinx.coroutines.tasks.await` đã import ở `CloudPresetFetcher.kt:15`, cùng cách lấy Firestore như `CloudPresetFetcher.kt:154`, không thêm thư viện; nuốt mọi ngoại lệ trừ `CancellationException`. PCRT và PST luôn truyền `fetchRemote` riêng cho các test cần kết quả xác định; các test Robolectric dựng `InLoveRepository` dùng mặc định này, nơi `getInstance()` ném lỗi khi không có FirebaseApp, bị nuốt thành `null` và rơi về gói APK.
- Test trong PCRT (JVM thuần, `TemporaryFolder`, `runBlocking`):
  - `bundledPackIsUsedWhenNoLastGood`;
  - `newerLastGoodBeatsBundled`;
  - `equalLastGoodLosesToBundled`;
  - `corruptLastGoodFallsBackToBundled`;
  - `leftoverTempFileIsIgnored`;
  - `remoteNewerValidPackIsSaved`;
  - `remoteShaMismatchIsIgnored`;
  - `remoteOlderPackIsIgnored`;
  - `remoteNeedsNewerAppIsIgnored`;
  - `remoteInvalidPackIsIgnored`;
  - `remoteFetchErrorReturnsNull`.
- Hai test thời gian dùng đồng hồ thật, có đối chứng dương:
  - `remoteSlowFetchTimesOut`: `fetchRemote` gọi `delay(10_000)`, kết quả `null` trong 2000-5000 ms;
  - `remoteFastFetchBeatsTimeout`: cùng khung đo, gói trả về ngay thì được nhận.
- Sau mỗi test bị từ chối, file `VN.json` trong `dir` phải còn nguyên từng byte như trước.
- Chấp nhận: lệnh ở bảng cho 0 lỗi; PCR không dùng `SharedPreferences`.

### U21b. Gieo quà từ gói, sửa F-06

- OS:
  - thêm tham số CUỐI `packs: PackContentRepository? = null`, đặt sau `today`, để ba lời gọi ở hai file test, truyền `today` theo tên (A13), vẫn biên dịch. Khi `null`, `ensureSeedGiftCatalog` không làm gì; hai file test đó không kiểm quà.
  - `ensureSeedGiftCatalog(language)`: `val pack = packs?.local() ?: return`; `GiftCatalog.install(pack)`; với mỗi món, theo thứ tự:
    1. Hàng `pk:VN:id` chưa có mà hàng `seed_id` có: `updateGiftIdea(entity.copy(id = old.id, isFavorited = old.isFavorited))`.
    2. Cả hai đều có: `setGiftFavorite(pk.id, pk.isFavorited || old.isFavorited)`, SAU ĐÓ `upsertGiftIdeaByRemoteId(entity)` để chữ của hàng `pk:` luôn mới (upsert giữ nguyên `isFavorited`, `InLoveDao.kt:84`); hàng `seed_` để nguyên vì DAO không có lệnh xoá và DAO không thuộc wave này.
    3. Chỉ khi CHƯA có hàng `pk:VN:id` (nếu không sẽ vi phạm chỉ mục UNIQUE của `remoteId`): hàng cũ có `remoteId` rỗng khớp `GiftCatalog.seedRemoteIdFor` được nhận lại như logic hiện tại ở :57-61.
    4. Ngược lại: `upsertGiftIdeaByRemoteId(entity)`.
  - `initializeDefaultDataIfEmpty`: chuyển lần làm mới xuống CUỐI hàm, sau nhánh đám mây hoặc gieo ngoại tuyến (tuần tự, không song song, để việc "làm mới chạy sau mọi bước gieo cục bộ" kiểm được bằng test); `packs?.refreshFromRemote()` trả khác `null` thì gọi lại `ensureSeedGiftCatalog(language)`, hàm này đọc lại `local()` và chọn gói có `packVersion` cao hơn.
  - Bình luận :173 đổi chữ `GiftIdeasSeed` thành `VN.json`; KDoc ở `:48-51` (câu "Cài đủ 20 ý tưởng quà ...") sửa để số món do gói quyết định; giữ CRLF.
- IR: `:45` truyền `PackContentRepository(readBundled = { appContext.assets.open("content/VN.json").bufferedReader().use { it.readText() } }, dir = File(appContext.filesDir, "content"))` vào `OfflineSeeder` qua tham số có tên `packs =`. Giữ CRLF. Dòng :140 không đổi.
- GC:
  - `@Volatile private var packGifts: Map<String, PackGift>` cùng `fun install(pack: ContentPack)`;
  - `localized` tra `packGifts[remoteId] ?: packGifts[GiftKeys.alias(remoteId)]`; chưa cài thì rơi về chữ của entity, đúng hành vi hiện tại cho hàng không phải seed;
  - `seedRemoteIdFor` duyệt `packGifts` (khớp `imageUrl` trước, rồi `title` ở mọi ngôn ngữ) và trả khoá `pk:`;
  - bỏ `seedsByRemoteId` và import `GiftIdeasSeed`;
  - KDoc :7-8 đổi chữ `[GiftIdeasSeed]` thành `VN.json`; dòng :10 và :108 giữ nguyên.
- TP:
  - `fun vnPack(): ContentPack` đọc `File("src/main/assets/content/VN.json")`, parse với `appVersion = Int.MAX_VALUE`;
  - `fun installVnPack()` gọi `GiftCatalog.install(vnPack())`;
  - `fun vnEntities(lang): List<GiftIdeaEntity>`.
- GCT: thay mọi `GiftIdeasSeed` bằng `TestPacks`; khoá mong đợi chuyển sang `pk:VN:...`; mỗi lớp test gọi `installVnPack()` trong `@Before`. Giữ đủ số test hiện có.
- CED:
  - test 20 món, tiêu đề EN, nhận hàng cũ và yêu thích (:74-143) dùng `TestPacks` thay `GiftIdeasSeed`;
  - mong đợi `remoteId` là `pk:VN:...`;
  - bình luận cũ "15 fixed" (:145-164) KHÔNG sửa vì phần lễ ngoài phạm vi;
  - giữ CRLF.
- PST (Robolectric sdk 34, Room in-memory, `PackContentRepository` với `readBundled` đọc VNJ và `fetchRemote = { null }`, trừ `newerRemotePackIsSeededOnStart` và `refreshRunsAfterAllLocalSeeding` truyền `fetchRemote` riêng), tên tối đa 60 ký tự:
  - `freshInstallSeedsTwentyPackRows`;
  - `seedRowBecomesPackRowKeepingIdAndFav`;
  - `blankLegacyRowIsAdoptedByImage`;
  - `bothKeysPresentMergesFavorite` (hàng `pk:` nhận chữ mới, `isFavorited` được gộp, hàng `seed_` giữ nguyên);
  - `secondRunChangesNothing` (so toàn bộ danh sách hàng trước và sau);
  - `personalAndCloudRowsAreUntouched` (`local_`, `tpl_`, `legacy_`, `gift_`);
  - `giftRemindersSurviveMigration`;
  - `oldBuildVaultStashRestoresFavorite` (stash viết với `seed_X`, gieo, rồi khôi phục);
  - `englishLanguageWritesEnglishText`;
  - `newerRemotePackIsSeededOnStart`;
  - `noPacksMeansNoGiftWrites` (đối chứng cho tham số `null`);
  - `pkRowBlocksLegacyAdoption` (có sẵn hàng `pk:` và một hàng cũ có `remoteId` rỗng khớp: không ném lỗi, hàng rỗng không đổi, đúng một hàng `pk:` cho món đó);
  - `refreshRunsAfterAllLocalSeeding` (một `fetchRemote` ghi lại thời điểm bị gọi và xác nhận, khi gọi trong lần `initializeDefaultDataIfEmpty` đầu tiên, rằng mốc kỷ niệm và đủ 20 hàng `pk:` đã có sẵn).
- Chấp nhận:
  - lệnh ở bảng cho 0 lỗi;
  - `Select-String -Path app\src\main\java\com\example\domain\content\GiftCatalog.kt -Pattern 'GiftIdeasSeed'` không in gì;
  - OS và IR vẫn CRLF, không có LF trần (đếm `\r\n` bằng số `\n`);
  - bình luận ở `GiftCatalog.kt:10`, `:108` và `InLoveRepository.kt:140` khớp bản nền theo chữ (`(Get-Content -Encoding UTF8 <file>) -contains <dòng nền>` in `True`).

### U21c. Xoá seed trong mã và chuyển test còn lại

- UCT:
  - `GiftIdeasSeed.PriceRange.OVER_1M` (:148) thành `">1tr"`;
  - `GiftIdeasSeed.all.map { it.toEntity(...) }` (:173) thành `TestPacks.vnEntities(...)`;
  - `"seed_gift-camping-trip"` (:185) thành `"pk:VN:gift-camping-trip"`;
  - `imageSeed` (:191) thành `id`;
  - gọi `installVnPack()` nơi cần chữ dịch.
- PDT:42: thay bằng `TestPacks.vnEntities(AppLanguage.VI)`.
- NNI:15: bình luận đổi `GiftIdeasSeed` thành `VN.json`.
- Xoá GIS, GEN, GCJ.
- Kiểm trước khi chạy Gradle: `Select-String -Path app\src\main\java\*,app\src\test\java\* -Pattern 'GiftIdeasSeed|gift_catalog\.json|"seed_' ` (đệ quy qua `Get-ChildItem -Recurse -Filter *.kt`) không in dòng nào.
- Chấp nhận:
  - lệnh ở bảng cho 0 lỗi;
  - `holiday_calendar.json`, `ContentRepository.kt`, `ContentModels.kt` còn nguyên từng byte so với MANIFEST;
  - số test mất đi đúng bằng số test của GEN (ghi vào log).

## 9. Waves và cổng

| wave | task | file (không file nào dùng chung trong một wave) |
|---|---|---|
| W0 | T0 | chỉ `<BASELINE_DIR>` |
| W1 | U19 | CP, CPT |
| W2 | U22b, U20, U22a | ADV+AIT; VNJ+GEN; GC+UC+PLT |
| W3 | U21a | PCR, PCRT |
| W4 | U21b rồi U21c (tuần tự, vì U21c phụ thuộc U21b) | OS, IR, GC, GCT, CED, TP, PST; GIS, GEN, GCJ, UCT, PDT, NNI |
| WF | cổng cuối | không file nào |

- GC được U22a (W2) và U21b (W4) sửa ở hai wave khác nhau. GEN được U20 tạo và U21c xoá ở hai wave khác nhau.
- Trong W2 ba task chạy Gradle tuần tự, không song song.

Cuối mỗi wave W1-W4:

- chạy lệnh test của từng task trong wave;
- chạy `.\gradlew.bat :app:lintDebug`: 0 lỗi và số cảnh báo không vượt số đo ở T0 (tối đa 131);
- ghi vào execution log.

Test bảo vệ, phải qua ở W4 và WF:

- `CloudEnrichmentDataTest`;
- `PresetDedupeTest`;
- `GiftCatalogTest`;
- `NoNetworkImagesRule` (qua các test Compose dùng nó);
- `LoveMilestoneRulesTest`;
- `ReminderSchedulingTest`;
- `HolidayGoldenTest`;
- `HolidaySeedGoldenTest`;
- `HolidayRefreshGoldenTest`;
- `PersonalizedGiftGoldenTest`;
- `GiftAndSyncGoldenTest`;
- `DateFormatGoldenGeneratorTest`;
- `StartDateFallbackGoldenTest`.

Lệnh W4 thêm sau U21c: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.golden.*" --tests "com.example.alarm.*" --tests "com.example.data.*" --tests "com.example.domain.*" --tests "com.example.CloudEnrichmentDataTest"`.

WF, phiên chính, trên cây đã đóng băng:

1. Cổng A:
   - `.\gradlew.bat :app:testDebugUnitTest` cho 0 lỗi, 0 error; số test ít nhất bằng (T0 + test mới − test của GEN);
   - `.\gradlew.bat :shared:testAndroidHostTest` cho 0 lỗi.
2. Cổng B:
   - `.\gradlew.bat :app:lintDebug` cho 0 lỗi và số cảnh báo không vượt T0 (tối đa 131);
   - `.\gradlew.bat :app:assembleDebug` thành công.
3. Golden và lược đồ: SHA-256 của 25 golden và của `14.json` bằng đúng MANIFEST. Lệch một byte là DỪNG.
4. `git --no-optional-locks status --porcelain` so với `STATUS-BASELINE.txt`. Mọi đường dẫn MỚI phải là file sở hữu ở mục 6; đường dẫn khác ghi riêng là thay đổi của phiên khác.
5. Với mỗi file sở hữu có bản nền: `git diff --no-index <BASELINE_DIR>\<path> <path>`. File mới: diff với `EMPTY.txt`. Gửi toàn bộ cho Codex (mục 10).
6. Kiểu xuống dòng: đếm `\r\n` và `\n` cho từng file ở mục 6 và đối chiếu cột "xuống dòng".
7. Không commit.

## 10. Codex

- Model `gpt-6.1-sol`, effort xhigh, chỉ đọc, qua skill `codex-flow`.
- Duyệt kế hoạch: tối đa 3 vòng. Phiên chính kiểm từng phát hiện trên mã thật rồi mới sửa kế hoạch.
- Duyệt diff ở WF bước 5: tối đa 3 vòng; Codex không sửa file nào, phiên chính áp mọi thay đổi.
- Nếu Codex không dùng được (hết hạn mức, lỗi xác thực, thiếu plugin) thì báo và dừng vòng đó, không tự duyệt thay rồi coi như xong.
- Mọi sửa sau một vòng duyệt cần lại: tin nhắn START/END cho phiên khác, test liên quan, lint (không còn dư) và cả hai cổng.

## 11. Rủi ro

| id | rủi ro | cách giảm |
|---|---|---|
| R1 | ADV vừa được làm cứng và qua Codex; sửa sai một dòng làm hỏng khôi phục tài khoản. | Task riêng (U22b), đúng 1 dòng cộng 1 import, diff được kiểm bằng số dòng, 3 test mới cộng toàn bộ `com.example.data.db.*`, SHA-256 trước và sau ghi vào log. |
| R2 | `GiftCatalog` giữ trạng thái toàn cục `@Volatile`; thứ tự test trong cùng JVM có thể làm kết quả khác nhau. | Mọi test cần chữ dịch gọi `installVnPack()` trong `@Before`; gói luôn là cùng VNJ nên cài lại không đổi gì. Test golden dùng hàng không có khoá `pk:` nên không phụ thuộc trạng thái này (A10). |
| R3 | Trước khi `ensureSeedGiftCatalog` chạy lần đầu sau khởi động, `GiftCatalog.localized` hiện chữ đã lưu trong Room thay vì chữ theo ngôn ngữ mới (chỉ ở lần khởi động đầu tiên sau khi đổi ngôn ngữ; chữ trong Room không rỗng nên không hiện ô trống). | `GiftCatalog.install` nằm TRONG `ensureSeedGiftCatalog`, được gọi sớm trong `initializeDefaultDataIfEmpty` (trước nhánh đám mây hoặc ngoại tuyến); chữ trong Room là chữ của lần gieo gần nhất. Cài ở nơi sớm hơn bị loại vì kéo theo đọc asset trên luồng chính. Kiểm trên máy là việc của chủ sở hữu (O3). |
| R4 | Thêm một `get()` Firestore có hạn 2000 ms vào lúc khởi động (A14), đặt ở CUỐI `initializeDefaultDataIfEmpty`: việc đặt báo thức ngay sau đó chậm thêm tối đa 2000 ms; thời gian chờ xấu nhất là 4000 ms (lần chờ có hạn sẵn có của `CloudPresetFetcher` cộng lần làm mới này), chỉ xảy ra khi mạng chậm hoặc mất. | Gọi SAU mọi bước gieo cục bộ (gói APK và mốc kỷ niệm đã có), nên nội dung có ngay; lỗi hoặc hết hạn chỉ trả `null`. Phương án chạy lần làm mới ở một coroutine riêng bị loại vì phải đưa `InLoveViewModel.kt` (84 KB) vào danh sách file sở hữu. |
| R5 | Hai hàng `seed_X` và `pk:VN:X` cùng tồn tại (chỉ xảy ra khi hai lần gieo chạy chồng nhau) thì một món hiện hai lần. | Gộp yêu thích sang hàng `pk:`; hàng `seed_` để lại. Thêm lệnh xoá khi việc này được quan sát thật, ở wave có quyền sửa DAO. |
| R6 | Lint không còn dư: một cảnh báo mới làm cổng B đỏ. | Không dùng `SharedPreferences`; chạy lint cuối mỗi wave để bắt sớm. |
| R7 | `VN.json` là UTF-8 có dấu; trình sửa trên Windows có thể thêm CRLF hoặc BOM. | `vnPackIsValidNfcAndSmall` chỉ sống tới U21c; WF bước 6 kiểm lại xuống dòng và BOM. Sau U21c, sửa gói chỉ qua U-25. |
| R8 | Xoá `GiftIdeasSeed.kt` làm câu chữ D-13 trong TASKS.md:343 (chuyển `GiftIdeasSeed`) lỗi thời. | Ghi vào báo cáo cho chủ sở hữu; tài liệu nghiên cứu (dòng 1515) đã dự trù D-13 chuyển `PackContentRepository` thay thế. Kế hoạch này không sửa TASKS.md. |
| R9 | Hai phiên khác sửa cùng cây trong lúc làm. | START/END, Gradle tuần tự, so `status` với nền ở WF bước 4. |
| R10 | Test thời gian (U21a) chạy chậm hoặc chập chờn trên máy bận. | Khung chấp nhận rộng (2000-5000 ms), cộng đối chứng dương trong cùng khung đo. |

## 12. Ngoài phạm vi

- skipped: thị trường thứ hai và `MarketProfile`, add when có gói INTL và có U-28.
- skipped: gói INTL.json, add when có nội dung quà quốc tế đã duyệt.
- skipped: đưa ngày lễ, cột mốc, kiêng kỵ và luật ngày vào gói, add when `CalendarMath` có ở U-26 và có U-27.
- skipped: bỏ `isEnglish`/`tr()` và chuỗi chọn ngôn ngữ đầy đủ, add when làm U-23.
- skipped: script đẩy gói lên Firestore, rules cho `content_packs`, bộ kiểm Node, add when làm U-25.
- skipped: nâng Room v15 hoặc cột giá riêng, add when có lần nâng chung sau D-15.
- skipped: chuyển `ContentPack` sang `shared/src/commonMain`, add when F-17 xong và D-13 bắt đầu.
- skipped: cài `ContentRepository` và xoá `ContentModels.kt`, add when D-13 được lập lại (DEC-69 còn ghim hai file này).
- skipped: xoá `holiday_calendar.json`, add when ngày lễ chuyển vào gói.
- skipped: kiểm hợp lệ và gắn phiên bản cho đường `gift_ideas` cũ trong `CloudPresetFetcher.kt`, add when U-25 thay đường đó.
- skipped: tách `category`, `tag`, `occasion` khỏi chuỗi hiển thị tiếng Việt (F-03), add when làm khoá dịp ổn định.
- skipped: lệnh xoá hàng `seed_` thừa trong DAO, add when R5 xảy ra thật.
- skipped: nút đồng bộ thủ công làm mới gói, add when chủ sở hữu yêu cầu.
- skipped: sửa các con trỏ cũ trong tài liệu nghiên cứu và câu chữ D-13 trong TASKS.md, add when chủ sở hữu duyệt việc sửa tài liệu.

## 13. Trạng thái finding sau wave 2a

| finding | trạng thái sau 2a |
|---|---|
| F-06 | Sửa về mã: gói đọc mỗi lần khởi động và làm mới từ `content_packs/VN`. Hiệu lực đầu cuối chờ chủ sở hữu xuất bản gói (A15, U-25). |
| G-10 | Sửa cho đường gói: gói không còn phụ thuộc `hasGifts` (`OfflineSeeder.kt:33`). Đường `gift_ideas` cũ vẫn như cũ. |
| F-05, G-11 | Sửa cho đường gói: có phiên bản, vùng, lược đồ, giá có cấu trúc, bản ghi xấu bị bỏ. Đường `gift_ideas` cũ còn mở tới U-25. |
| F-09 | Một phần: quà còn một nguồn duy nhất, `gift_catalog.json` bị xoá. `ContentRepository` và phần lễ còn đó (mục 12). |
| F-04 | Một phần: giá có đơn vị tiền tệ; cột `budgetMaxVnd` vẫn mang tên VND. |
| F05, F-01 | Còn mở (U-27, U-23). |

## 14. Con trỏ cũ trong tài liệu nghiên cứu (chỉ ghi lại, không sửa)

- Dòng 1536 trỏ `AccountDataVault.kt:232`; hiện tại hàm băm ở `:274-275`.
- Dòng 1010, 1144, 1399, 1557 trỏ `AccountDataVault.kt:241-260`. Hiện tại:
  - `PERSONAL_GIFT` ở `:284-285`;
  - `PERSONAL` ở `:287-298`;
  - `OVERLAYS` ở `:301-304`;
  - câu khôi phục ở `:196-203`.
- Dòng 501, 996, 1118, 1399, 1557, 1687 trỏ `InLoveDao.kt:73-85`; hiện tại upsert ở `:80-89`.
- Dòng 1010, 1557, 1659 trỏ `GiftIdeasSeed.kt:49-50`; hiện tại `remoteId` và `imageUrl` ở `:51-52`.
- Dòng 473, 1010, 1557, 1599 trỏ `GetPersonalizedGiftSuggestionsUseCase.kt:80-86`; hiện tại `priceFloorVnd` ở `:81-88`.
- Dòng 1216, 1491, 2891 ghi "20 lễ VN, 15 cố định"; hiện tại có 21 lễ: 16 cố định, 5 âm lịch.
- Dòng 1535 dẫn quy tắc URL từ `OutboxGoldenTest.kt:362` (bản sao trong test); quy tắc chạy thật là `MemorySyncAdapter.kt:76-81`. Trong repo, bình luận `OutboxGoldenTest.kt:364` còn nhắc file đã xoá `FirebaseMemoryMediaRepository.kt`.
- Dòng 1515 và 1536 đặt U-19 vào `shared/src/commonMain`; vị trí đó chưa dùng được cho tới khi F-17 xong (A1).
- Dòng 1549: U-21 cài `ContentRepository` và xoá cả hai file JSON. Wave 2a chỉ xoá `gift_catalog.json` và không cài `ContentRepository` (mục 4 (e)).
- TASKS.md:593 nói `CalendarMath.kt` đã do D-09 chuyển; file này không tồn tại (A17).

## 15. Execution log

| task | status | attempts | tests run | notes |
|---|---|---|---|---|
| T0 | TODO | 0 | | |
| U19 | TODO | 0 | | |
| U22b | TODO | 0 | | |
| U20 | TODO | 0 | | |
| U22a | TODO | 0 | | |
| U21a | TODO | 0 | | |
| U21b | TODO | 0 | | |
| U21c | TODO | 0 | | |
| WF | TODO | 0 | | |

## 16. Resume checklist

1. Đọc mục 15 và tìm task đầu tiên chưa DONE.
2. So `git --no-optional-locks status --porcelain` với `<BASELINE_DIR>\STATUS-BASELINE.txt`; ghi riêng mọi đường dẫn lạ.
3. Chạy `.\gradlew.bat --status` để chắc không có lần chạy Gradle của phiên khác đang dở. Không bao giờ dừng lần chạy của phiên khác. Nếu từng phải dừng Gradle giữa chừng thì xoá `app/build/kotlin/<task>` trước lần chạy sau.
4. Với task đang dở: so từng file sở hữu với bản nền bằng `git diff --no-index`. Phần đã sửa mà chưa có test xanh thì chạy lại lệnh test của task.
5. Đo lại byte và kiểu xuống dòng của file sở hữu; khác cột "xuống dòng" ở mục 6 là lỗi.
6. Gửi lại START cho wave đang tiếp tục.
7. Chạy lại test của wave và lint trước khi gửi END.
8. Mọi sửa sau một cổng hay một vòng duyệt mở lại cả cổng A và cổng B.
9. Không commit, push hay PR; không `git checkout`, `git stash`, `git reset`; không adb.

## 17. Bước chỉ chủ sở hữu làm

Agent không bao giờ được báo các bước này là đã xong.

- O1: Duyệt kế hoạch này sau vòng Codex.
- O2: Khi U-25 sẵn sàng:
  - tạo tài liệu Firestore `content_packs/VN` (`packJson`, `sha256`);
  - mở rules cho phép đọc;
  - giữ nguyên `gift_ideas` cho app cũ.
- O3: Kiểm trên máy thật hoặc LDPlayer:
  - cài bản mới đè lên bản cũ có món yêu thích: món yêu thích còn, không có món trùng;
  - đổi ngôn ngữ VI và EN: chữ quà đổi theo;
  - đăng xuất rồi đăng nhập lại: yêu thích theo đúng tài khoản;
  - khởi động khi tắt mạng hoặc mạng chậm: giao diện không treo; việc đặt báo thức sau lần gieo chậm tối đa khoảng 4 giây (A14, R4).
- O4: Quyết định có sửa câu chữ D-13 trong TASKS.md (chuyển `ContentPack` và `PackContentRepository` thay cho `GiftIdeasSeed`) và các con trỏ ở mục 14 hay không.
- O5: Quyết định commit; kế hoạch này không commit.
