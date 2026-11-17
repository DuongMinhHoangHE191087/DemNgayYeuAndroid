# Rà soát quà tặng, gợi ý kỷ niệm và nghiên cứu thị trường cho InLove

- **Ngày:** 2026-10-27 (báo cáo của các sub-agent ghi ngày 2026-10-26)
- **Trạng thái:** bản nháp để chủ app duyệt; chưa đổi dòng code, seed hay schema nào của app
- **Phạm vi:** quà tặng, gợi ý dịp lễ, kỷ niệm và cột mốc; 35 mã thị trường chia thành 5 gói (7.1)
- **Dữ liệu kèm theo:** thư mục `docs/research/data/` (10.4)

## Mục lục

- [1. Tóm tắt điều hành](#1-tóm-tắt-điều-hành)
  - [1.1 Kết luận chính](#11-kết-luận-chính)
  - [1.2 Điểm mạnh đã có](#12-điểm-mạnh-đã-có)
  - [1.3 Làm ngay](#13-làm-ngay)
  - [1.4 Chiến lược nội dung](#14-chiến-lược-nội-dung)
  - [1.5 Thị trường](#15-thị-trường)
  - [1.6 Dữ liệu và độ tin cậy](#16-dữ-liệu-và-độ-tin-cậy)
  - [1.7 Quyết định cần chốt](#17-quyết-định-cần-chốt)
  - [1.8 Chưa được kiểm chứng](#18-chưa-được-kiểm-chứng)
  - [1.9 Bản đồ đọc](#19-bản-đồ-đọc)
- [2. Phương pháp, độ tin cậy và giới hạn](#2-phương-pháp-độ-tin-cậy-và-giới-hạn)
  - [2.1 Các sub-agent và phần việc](#21-các-sub-agent-và-phần-việc)
  - [2.2 Các loại bằng chứng](#22-các-loại-bằng-chứng)
  - [2.3 Mã số và nhãn dùng trong tài liệu](#23-mã-số-và-nhãn-dùng-trong-tài-liệu)
  - [2.4 Giới hạn](#24-giới-hạn)
  - [2.5 Quy ước trình bày](#25-quy-ước-trình-bày)
- [3. Rà soát code](#3-rà-soát-code)
  - [3.0 Cách đọc mục này](#30-cách-đọc-mục-này)
  - [3.1 Quà tặng (Gift)](#31-quà-tặng-gift)
  - [3.2 Dịp lễ, kỷ niệm, cột mốc và nhắc hẹn](#32-dịp-lễ-kỷ-niệm-cột-mốc-và-nhắc-hẹn)
  - [3.3 Đường ống nội dung: có mở rộng ra nhiều thị trường mà không cần release không?](#33-đường-ống-nội-dung-có-mở-rộng-ra-nhiều-thị-trường-mà-không-cần-release-không)
- [4. Thị trường](#4-thị-trường)
  - [4.0 Cách đọc](#40-cách-đọc)
  - [4.1 Việt Nam (thị trường gốc)](#41-việt-nam-thị-trường-gốc)
  - [4.2 Đông Á (Hàn Quốc, Nhật Bản, Trung Quốc, Đài Loan, Hồng Kông)](#42-đông-á-hàn-quốc-nhật-bản-trung-quốc-đài-loan-hồng-kông)
  - [4.3 Phương Tây (Mỹ, Canada, Anh, Úc, Đức, Pháp, Tây Ban Nha, Ý)](#43-phương-tây-mỹ-canada-anh-úc-đức-pháp-tây-ban-nha-ý)
  - [4.4 Đông Nam Á và Ấn Độ (Philippines, Thái Lan, Indonesia, Malaysia, Singapore, Ấn Độ)](#44-đông-nam-á-và-ấn-độ-philippines-thái-lan-indonesia-malaysia-singapore-ấn-độ)
  - [4.5 Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS (kèm Ba Lan và vùng Vịnh)](#45-mỹ-latinh-thổ-nhĩ-kỳ-nga-và-cis-kèm-ba-lan-và-vùng-vịnh)
- [5. App nên xử lý thói quen và kỷ niệm tặng quà thế nào](#5-app-nên-xử-lý-thói-quen-và-kỷ-niệm-tặng-quà-thế-nào)
  - [5.1 Ba chỉ số khác nhau, không gộp làm một](#51-ba-chỉ-số-khác-nhau-không-gộp-làm-một)
  - [5.2 Bảy thói quen và cách app nên xử lý](#52-bảy-thói-quen-và-cách-app-nên-xử-lý)
  - [5.3 Luật chọn "dịp nào bật mặc định"](#53-luật-chọn-dịp-nào-bật-mặc-định)
  - [5.4 Hai cơ chế "vùng" trong code và hậu quả cho người dùng quốc tế](#54-hai-cơ-chế-vùng-trong-code-và-hậu-quả-cho-người-dùng-quốc-tế)
  - [5.5 Quà: người nhận, mức giá, hình thức](#55-quà-người-nhận-mức-giá-hình-thức)
  - [5.6 Vòng đời một món quà: app đang dừng ở đâu](#56-vòng-đời-một-món-quà-app-đang-dừng-ở-đâu)
- [6. Nâng cấp có ưu tiên](#6-nâng-cấp-có-ưu-tiên)
  - [6.0 Nguyên tắc](#60-nguyên-tắc)
  - [6.1 Thang đo, bảng việc và thứ tự](#61-thang-đo-bảng-việc-và-thứ-tự)
  - [6.2 Đợt 1: sửa nhanh và độ tin cậy](#62-đợt-1-sửa-nhanh-và-độ-tin-cậy)
  - [6.3 Đợt 2: gói nội dung theo thị trường](#63-đợt-2-gói-nội-dung-theo-thị-trường)
  - [6.4 Đợt 3: tính năng sau khi có gói nội dung](#64-đợt-3-tính-năng-sau-khi-có-gói-nội-dung)
  - [6.5 Những thay đổi cần cờ riêng](#65-những-thay-đổi-cần-cờ-riêng)
  - [6.6 Bảng đối chiếu: từ mục thị trường và từ phát hiện đến việc cần làm](#66-bảng-đối-chiếu-từ-mục-thị-trường-và-từ-phát-hiện-đến-việc-cần-làm)
- [7. Dữ liệu đã làm giàu và bảng âm lịch](#7-dữ-liệu-đã-làm-giàu-và-bảng-âm-lịch)
  - [7.1 Số lượng theo gói thị trường](#71-số-lượng-theo-gói-thị-trường)
  - [7.2 Trường dữ liệu của từng loại bản ghi](#72-trường-dữ-liệu-của-từng-loại-bản-ghi)
  - [7.3 Độ tin cậy và độ phủ](#73-độ-tin-cậy-và-độ-phủ)
  - [7.4 Từ vựng và phân bố giá trị](#74-từ-vựng-và-phân-bố-giá-trị)
  - [7.5 Kiểm tra chất lượng dữ liệu](#75-kiểm-tra-chất-lượng-dữ-liệu)
  - [7.6 Âm lịch 2025-2040](#76-âm-lịch-2025-2040)
  - [7.7 Vị trí tệp và cách dùng](#77-vị-trí-tệp-và-cách-dùng)
- [8. Thị trường kế tiếp, kiếm tiền và AI](#8-thị-trường-kế-tiếp-kiếm-tiền-và-ai)
  - [8.1 Cách đọc mục này](#81-cách-đọc-mục-này)
  - [8.2 Đối thủ ở phần quà, dịp và cột mốc](#82-đối-thủ-ở-phần-quà-dịp-và-cột-mốc)
  - [8.3 Xếp hạng thị trường](#83-xếp-hạng-thị-trường)
  - [8.4 Kiếm tiền](#84-kiếm-tiền)
  - [8.5 AI và LLM](#85-ai-và-llm)
  - [8.6 Giới hạn](#86-giới-hạn)
- [9. Quyết định cần chủ app chốt, rủi ro và việc chưa kiểm chứng](#9-quyết-định-cần-chủ-app-chốt-rủi-ro-và-việc-chưa-kiểm-chứng)
  - [9.1 Bảng tóm tắt](#91-bảng-tóm-tắt)
  - [9.2 Chi tiết từng quyết định](#92-chi-tiết-từng-quyết-định)
  - [9.3 Danh sách mong muốn cho hai người (đề xuất, chưa đánh số)](#93-danh-sách-mong-muốn-cho-hai-người-đề-xuất-chưa-đánh-số)
  - [9.4 Điểm duyệt đi kèm từng việc (không đánh số QĐ)](#94-điểm-duyệt-đi-kèm-từng-việc-không-đánh-số-qđ)
  - [9.5 Rủi ro chung](#95-rủi-ro-chung)
  - [9.6 Việc chưa kiểm chứng](#96-việc-chưa-kiểm-chứng)
- [10. Phụ lục](#10-phụ-lục)
  - [10.1 Hai mươi quà gốc trong mã](#101-hai-mươi-quà-gốc-trong-mã)
  - [10.2 Lịch lễ trong mã](#102-lịch-lễ-trong-mã)
  - [10.3 Đường dẫn đầy đủ của các file được nhắc tới](#103-đường-dẫn-đầy-đủ-của-các-file-được-nhắc-tới)
  - [10.4 Dữ liệu thô và nguồn](#104-dữ-liệu-thô-và-nguồn)

## 1. Tóm tắt điều hành

**Ý chính.** Tài liệu rà soát phần quà tặng, gợi ý dịp lễ và cột mốc, cùng nhắc hẹn của InLove, rồi đối chiếu với thói quen tặng quà ở năm nhóm thị trường: Việt Nam, Đông Á, phương Tây, Đông Nam Á cùng Ấn Độ, và nhóm Mỹ Latinh kèm Thổ Nhĩ Kỳ, Nga, CIS, Ba Lan và vùng Vịnh. Mười một sub-agent chạy song song, mỗi agent một mảng độc lập (2.1). Kết quả gồm 63 phát hiện về code (25 mức P1, 38 mức P2/P3, chưa có P0 nào được xác nhận), 38 việc nâng cấp xếp thành ba đợt 18/10/10, 10 quyết định đều có mặc định, và 354 bản ghi dữ liệu nháp (87 dịp, 55 cột mốc, 142 quà, 70 kiêng kỵ) chia thành năm gói theo thị trường, kèm bảng âm lịch 2025-2040. **Đợt 1 không phụ thuộc quyết định nào và có thể duyệt ngay.** Dữ liệu nháp chưa nối vào app, và chưa có dòng code, seed hay schema nào của app bị đổi. Giới hạn chính: chỉ đọc code (không chạy test hay emulator), dữ liệu chưa có người bản ngữ rà soát, và người thứ hai mới đọc lại phần trích dẫn code có số dòng, chưa đọc toàn bộ tài liệu (1.8, 2.4).

### 1.1 Kết luận chính

Đường dẫn code theo quy ước ở 3.0; số dòng lấy từ cây làm việc chưa commit (1.8).

1. **Thẻ gợi ý quà không nói lý do và không bấm được.** Thẻ `PersonalizedGiftCard` (`ui/screens/GiftScreen.kt:1215-1260`) chỉ vẽ biểu tượng tim, tên quà, nút chia sẻ và nhãn giá. Hai trường `reason` và `preparation` đã được tính sẵn ở `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:157-165` nhưng không bao giờ hiện ra, và bấm vào thẻ không mở chi tiết (G-08). Hai thứ giúp người dùng quyết định ("vì sao tặng món này", "nên chuẩn bị gì") đã tính xong nhưng bị giấu.
2. **Một ngày lễ sai, và bảng âm lịch sắp hết hạn.** Rằm tháng Giêng 2027 trong repo là 2027-02-19 (`data/seed/VietnameseHolidays.kt:86`); đúng là 2027-02-20 (mùng 1 Tết 06/02 cộng 14 ngày). Đây là lỗi duy nhất trong 15 ngày so được với bảng tham chiếu (F07). Bảng của repo còn hết hạn sớm: Tết tới 2028, Rằm tháng Giêng tới 2027, Vu Lan chỉ tới 2026; khi hết dữ liệu, lễ đó thành "đã qua" vĩnh viễn và không còn nhắc (F06). Thuật toán âm lịch ở 7.6 khớp bảng của Đài Thiên văn Hồng Kông (HKO) ở 96/96 ô (6 dịp, 16 năm 2025-2040); riêng múi giờ Việt Nam (UTC+7) không có nguồn độc lập để đối chiếu.
3. **Nhắc mặc định quá rộng.** Mọi ngày lễ khi gieo đều bật nhắc, trước 3 ngày, kể cả những ngày không liên quan cặp đôi như Ngày Thương binh liệt sĩ hay Quốc khánh, và nhắc bằng giọng cặp đôi (`data/repository/OfflineSeeder.kt:193-203,212-227`; F04). Cờ `isCoupleRelevant` có trong file seed nhưng không được dùng khi gieo.
4. **Nhắc cột mốc có thể sai ngày, sai ngôn ngữ hoặc không đến.** Trên đường nhắc cũ, mốc 100 ngày được báo vào ngày thứ 101 vì tính `start + N×24h`, trong khi bộ lập lịch mới tính `start + (N−1)` ngày, khớp cách app đếm ngày bắt đầu là ngày 1 (F01; `alarm/LoveAnniversaryMilestoneScheduler.kt:96` so với `alarm/plan/LoveMilestoneRules.kt:54`). Chữ thông báo cột mốc cứng tiếng Việt, kể cả cho người dùng giao diện EN (F02). Sau khi khởi động lại máy, bộ nhận boot không dựng lại alarm cột mốc cho tới khi mở app (F03; `alarm/BootCompletedReceiver.kt:20-52`). App khai báo quyền báo thức chính xác nhưng không có lối đưa người dùng tới màn cấp quyền, nên nhắc có thể trễ mà không ai biết (F09). Các mốc 100, 200, 365, 1000 ngày là khoảnh khắc cảm xúc nhất của app; cả bốn lỗi đều ở mức P1.
5. **Nội dung gắn chặt với một thị trường.** Lịch lễ chọn theo ngôn ngữ giao diện chứ không theo quốc gia (F08, F-01); ngôn ngữ chỉ có hai giá trị VI và EN, máy không phải tiếng Việt bị ép sang EN (F-02); giá chỉ có VND (G-01, F-04); khoá logic của quà là chuỗi hiển thị tiếng Việt (F-03). Nội dung trên cloud không có phiên bản, ngôn ngữ, vùng hay schema (F-05), và bản cập nhật không tới người dùng đã cài vì app chỉ kéo lại khi một bảng rỗng hoặc người dùng bấm đồng bộ (F-06, suy ra từ đọc mã). Câu hỏi "mở rộng ra nhiều thị trường mà không cần release được không?" được trả lời ở 3.3: chưa.
6. **Nhãn "AI" không có mô hình phía sau.** "Gợi ý AI" là các mẫu câu cố định chỉ cho Việt Nam (`data/repository/GiftSuggestionGenerator.kt:15-106`) và còn được cộng 1 điểm khi xếp hạng (`domain/GiftRanker.kt:35-36`) (G-14, G-07). Phụ thuộc `firebase-ai` có khai báo nhưng không dòng nào gọi (F-13). Script seed Firestore chứa huy hiệu "98% Cặp đôi mê mẩn", một số liệu xã hội không có nguồn (`scripts/seed_firestore.js:123`; F-15, cần xác nhận pháp lý; chưa biết bản này đã lên production chưa).
7. **Hai bảng cột mốc song song, và danh sách mốc còn thưa.** Bảng cũ ở `alarm/LoveAnniversaryMilestoneScheduler.kt:46-59` chỉ có tiếng Việt, bảng mới ở `alarm/plan/LoveMilestoneRules.kt` có hai ngôn ngữ (F-07). Danh sách mốc ngày thiếu những mốc như 22, 52, 520, 1314 ngày và không có mốc theo tháng; các lựa chọn nhắc trước chỉ là 0, 1, 3 hoặc 7 ngày, giờ nhắc cố định (F10). Dữ liệu nháp cho thấy nhiều dịp cần báo sớm hơn thế: `leadTimeDays` của dịp nằm trong khoảng 0 đến 30 ngày (14 dịp ở mức 14 ngày, 9 dịp từ 21 ngày trở lên) và 33 trên 87 dịp có `bookingAdvised` (7.4).
8. **Thói quen tặng quà khác nhau theo ba trục mà app chưa mô hình hoá.** Dữ liệu nháp mô tả mỗi dịp bằng ba chỉ số tách rời: mức liên quan cặp đôi (`coupleRelevance`), mức kỳ vọng tặng quà (`giftExpectation`) và có nên bật nhắc mặc định hay không (`defaultOn`), ví dụ Loy Krathong và White Day (5.1). App hiện chỉ có một cờ `isCoupleRelevant` nhị phân và cờ đó chưa được dùng (F04).

### 1.2 Điểm mạnh đã có

Chỉ liệt kê những gì đã đọc trực tiếp trong code:

- **Test golden cho lịch lễ và gợi ý quà:** `golden/HolidayGoldenTest.kt`, `golden/HolidaySeedGoldenTest.kt`, `golden/HolidayRefreshGoldenTest.kt`, `golden/PersonalizedGiftGoldenTest.kt`, với dữ liệu mẫu trong `shared/src/commonTest/resources/golden/`. Mỗi lần đổi hành vi hiện thành một diff cần đọc; một số việc ở mục 6 (U-01, U-09) đã ghi sẵn golden nào bị ảnh hưởng.
- **Bộ lập lịch thuần ở `alarm/plan/`:** `ReminderTexts` có sẵn hai ngôn ngữ, `LoveMilestoneRules` tính `start + (N−1)` đúng. Phần việc chủ yếu là chuyển đường cũ sang bộ này (U-04, U-05).
- **Cổng quyền VIP phía server:** `firebase/functions/src/entitlement/gatePolicy.js` mặc định từ chối, mỗi callable mới phải có một dòng trong `GATES` và có test kiểm. Nếu sau này có gọi LLM, chỗ đặt cổng đã rõ (QĐ-7).
- **Luồng đồng ý quảng cáo (UMP)** đã có ở `ads/AdsManagerImpl.kt`.
- **Điểm gắn cho gói nội dung đã được khai báo:** `domain/content/ContentRepository.kt:15-31` có interface nhưng chưa có cài đặt (F-09; phần KDoc `:12` nhắc một `FirebaseContentRepository` không có trong cây); U-21 dùng đúng chỗ này.
- **20 quà gốc có tên và mô tả hai ngôn ngữ** trong `data/seed/GiftIdeasSeed.kt`.

### 1.3 Làm ngay

Đợt 1 gồm 18 việc (U-01 đến U-18), không phụ thuộc quyết định nào. Bảng dưới là thứ tự đề xuất ở 6.1: sáu việc đầu là việc nên làm trước, hai việc cuối là đề xuất cân nhắc thêm. Việc nào cũng đi qua kế hoạch và duyệt trước khi đổi code.

| Thứ tự | Việc | Cỡ | Vì sao làm trước | Phát hiện |
|---|---|---|---|---|
| 1 | U-01: sửa ngày Rằm tháng Giêng 2027 | S | Sai ngày, nhỏ, và nên làm trước khi D-13 và DEC-64 khoá hành vi hiện tại | F07 |
| 2 | U-04 cùng U-05: một bảng cột mốc, chữ thông báo đi qua `ReminderTexts` | S + S | Cùng một file; sửa lệch ngày và chữ một lần | F01, F11, F-07 / F02, F12 |
| 3 | U-06: gán khoá sở thích chuẩn cho 20 quà gốc | S | Làm cho "gợi ý theo sở thích" có tác dụng với 20 quà gốc | G-02, G-17 |
| 4 | U-03 kèm U-11: gỡ nhãn và điểm "AI", dọn quà `tpl_*` | M + S | Gỡ nhãn "AI" khi app không gọi mô hình nào; làm trước D-13 | G-14, G-07, G-20, F-15, F-13 / G-13, G-07 |
| 5 | U-07: cố định locale khi đọc và lưu ngày | M | Ngày theo locale máy, chưa kiểm chứng trên máy thật; phải xong trước khi mở Thái Lan hay Ả Rập | F-08 |
| 6 | U-02 kèm U-09: nhắc mặc định chỉ cho lễ liên quan cặp đôi, thêm Noel 24/12 | S + S | Chặn nhắc không liên quan cặp đôi | F04 / F08 |
| 7 | Cân nhắc U-13: nhắc không mất sau khởi động lại, có lối cấp quyền báo thức chính xác | M | Độ tin cậy của nhắc, tác động lên mọi người dùng | F03, F09, F18, F19 |
| 8 | Cân nhắc phần thẻ của U-30: thẻ bấm được, hiện lý do và cách chuẩn bị, nút chia sẻ 48 dp | S | Không cần U-21. Đây là đề xuất của tài liệu: 6.1 vẫn xếp U-30 ở đợt 3 | G-08 |

D-13 chuyển nguyên văn code quà sang `shared/`; D-nn và DEC-nn là mã quyết định của kế hoạch KMP/iOS (`docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md`). Tám việc còn lại của đợt 1 (U-08, U-10, U-12, U-14, U-15, U-16, U-17, U-18) nhặt dần, không có thứ tự cứng.

### 1.4 Chiến lược nội dung

Nội dung hiện nằm ở ba nơi không đồng bộ: code Kotlin (20 quà gốc và lịch lễ); Firestore `gift_ideas` cùng `scripts/seed_firestore.js` (15 quà khác không khớp bản trong code); và hai file `app/src/main/assets/content/gift_catalog.json`, `app/src/main/assets/content/holiday_calendar.json` mà không dòng code nào đọc (6.3.1). Đề xuất đưa về một nguồn duy nhất:

- **Một gói JSON có phiên bản cho mỗi thị trường**, chỉ chứa dữ liệu. Tính ngày (âm lịch, thứ trong tuần, quy tắc tương đối) vẫn nằm trong Kotlin (`CalendarMath`, DEC-59).
- **Ba lớp phân phối:** gói đi kèm app trong assets, bản lưu nguyên tử trong `filesDir`, và tài liệu Firestore `content_packs/{region}` có `sha256`; không dùng Remote Config (6.3).
- **Khoá ổn định cho quà:** `remoteId = "pk:{region}:{giftId}"` kèm `legacyRemoteId = seed_<imageSeed>`, để quà đã lưu của người dùng cũ không mất.
- **Một lần nâng Room từ v14 lên v15**, gộp mọi cột mới (U-27, U-28, U-29, U-37), làm sau D-15 (D-15 chuyển DAO và database sang `shared/`, và bước kiểm của nó đòi tệp lược đồ `14.json` không đổi; chi tiết ở 6.3.5). Ngược lại U-19 đến U-22 phải làm trước D-13 (QĐ-2).
- **Đường tối thiểu cho riêng Việt Nam:** chỉ U-19 đến U-22. Bốn việc này gộp ba nguồn thành một và xử lý G-10, F-05, F-06, F-09 cùng một phần F05 mà không đổi schema Room. U-23 trở đi chỉ làm khi chủ app chọn thị trường thứ hai (QĐ-1). Thứ tự 12 bước ở 6.3.9.

### 1.5 Thị trường

- **Thị trường thứ hai (QĐ-1).** Mặc định: Philippines, rồi Brazil, rồi Thái Lan. Philippines là lựa chọn rẻ nhất vì máy không phải tiếng Việt đã bị ép sang EN (`ui/util/Localization.kt:29-32`); Brazil và Thái Lan cần bỏ `isEnglish` và `tr()` để dùng mã ngôn ngữ BCP-47 (U-24, cỡ L), Thái Lan còn cần rà soát font (U-38). Mỹ và Hàn Quốc nằm trong danh sách theo dõi (cạnh tranh, tỉ lệ iOS).
- **Xếp hạng là ước lượng của một sub-agent** (2026-10-26), không phải số đo: năm thị trường đầu nằm trong khoảng 0,35 điểm, và cột "nhu cầu" yếu nhất vì chưa mở dữ liệu Sensor Tower hay AppMagic (8.1, 8.3, 8.6).
- **Khoảng trống rõ nhất so với đối thủ là danh sách quà cho hai người** (món có ghi chú, "ẩn với người ấy", "giữ chỗ"). Đề xuất riêng ở 8.2.2, cỡ L; mặc định là làm U-29 (lịch sử quà "đã tặng") và dùng `mutualInterests` có sẵn trước, chỉ làm danh sách chung khi có bằng chứng đo được. Thiết kế đồng bộ và riêng tư phải quyết riêng, đối chiếu với kế hoạch local-first.
- **Chưa có liên kết tiếp thị (QĐ-8):** `whereToBuy` là chữ thường; nút "tìm quà" (U-35) chỉ mở tìm kiếm web.
- **Chưa dùng LLM (QĐ-7).** Nếu dùng: tuỳ chọn cho VIP qua một callable phía server với cổng `vip` (`firebase/functions/src/entitlement/gatePolicy.js`, dòng 5 ghi proxy Gemini phải dùng cổng `vip`), làm sau ba bước đầu ở 8.5.4.
- **Không có chỗ đo hành vi trong app.** App không có SDK phân tích (`privacy/AppPrivacyCoordinator.kt:15`), chỉ có Crashlytics (`app/build.gradle.kts:206`). Các chỉ số mà 8.2.2 đặt làm điều kiện, như tỉ lệ mở màn quà, vì thế chưa có chỗ đo trong app (suy luận từ việc không có SDK phân tích).

### 1.6 Dữ liệu và độ tin cậy

Dữ liệu nháp nằm ở `docs/research/data/gift-occasion-draft.json` (7.7). Số bản ghi theo gói:

| Gói | Mục | Thị trường (mã vùng) | Dịp | Cột mốc | Quà | Kiêng kỵ | Tổng |
|---|---|---|---:|---:|---:|---:|---:|
| VN | 4.1 | Việt Nam | 15 | 11 | 32 | 15 | 73 |
| EA | 4.2 | Đông Á (CN, HK, JP, KR, TW) | 18 | 10 | 24 | 14 | 66 |
| WEST | 4.3 | Phương Tây (AU, CA, DE, ES, FR, GB, IT, US) | 19 | 19 | 30 | 14 | 82 |
| SEA_SA | 4.4 | Đông Nam Á và Ấn Độ (ID, IN, MY, PH, SG, TH) | 19 | 7 | 28 | 13 | 67 |
| LATAM_TR_CIS | 4.5 | Mỹ Latinh, Thổ Nhĩ Kỳ, Nga, CIS, Ba Lan, vùng Vịnh (15 mã) | 16 | 8 | 28 | 14 | 66 |
| **Cộng** | | 35 mã vùng | 87 | 55 | 142 | 70 | 354 |

Đây là hạt giống để người bản ngữ rà soát, chưa phải nội dung phát hành. Mức tin cậy do chính bản ghi tự gắn: 34 `high`, 170 `med`, 150 `low`.

- **Nguồn:** 78 bản ghi mang nhãn nguồn dạng chữ, trong đó 77 là nhãn "from memory" (không có URL, viết từ kiến thức sẵn có của agent); 66 bản ghi không có URL nào, tất cả đều `low`; 12 bản ghi `high` chỉ dựa vào một URL.
- **Nhãn không đồng nhất:** chú giải mức tin cậy khác nhau giữa năm báo cáo vùng, nên `high` không có nghĩa là "hai nguồn khớp nhau", "nguồn chính thức" hay "người bản ngữ đã duyệt" (7.5).
- **Trùng với quà gốc:** theo tên, ít nhất 3 trong 142 quà nháp gần trùng một quà gốc (`ru_g_004` và `cn_g_003` với `gift-weekend-trip`, `pl_g_002` với `gift-board-game`), và còn năm quà cùng nhóm ý tưởng. Quy tắc ở 6.3.7: nhập vào bản ghi của quà gốc (giữ `legacyRemoteId`) hoặc bỏ quà nháp.
- **Âm lịch:** bảng 2025-2040 trong `docs/research/data/lunar-2025-2040.json` do thuật toán Hồ Ngọc Đức tính, khớp HKO ở 96/96 ô nhưng ở UTC+8; múi giờ Việt Nam chưa có nguồn độc lập (7.6).

### 1.7 Quyết định cần chốt

Mười quyết định, mỗi quyết định có mặc định để chủ app nhận bằng một câu. Đợt 1 không cần quyết định nào; QĐ-1 và QĐ-2 phải chốt trước đợt 2; tám quyết định còn lại chỉ cần chốt khi việc bị chặn tới lượt (9.1).

| QĐ | Câu hỏi | Mặc định | Chi tiết |
|---|---|---|---|
| QĐ-1 | Thị trường thứ hai là nước nào? | Philippines, rồi Brazil, rồi Thái Lan | 9.2.1 |
| QĐ-2 | Nâng Room lên v15 vào lúc nào so với D-15? | Một lần nâng v15 gộp các cột mới (U-27, U-28, U-29, U-37), làm SAU D-15; U-19 đến U-22 xong TRƯỚC D-13 | 9.2.2 |
| QĐ-3 | Nhắc theo tháng bật hay tắt mặc định? | TẮT; người dùng tự bật (U-33); không thêm cột | 9.2.3 |
| QĐ-4 | Thêm ngôn ngữ giao diện nào ngoài VI và EN? | Chưa thêm ngôn ngữ nào; U-24 chỉ dựng hạ tầng mã ngôn ngữ BCP-47 | 9.2.4 |
| QĐ-5 | Có hỗ trợ nhắc hẹn lãng mạn cho Ả Rập Xê Út và Kuwait không? | Không; chưa liệt kê hai nước là thị trường hỗ trợ cho đến khi có rà soát pháp lý và văn hoá tại chỗ | 9.2.5 |
| QĐ-6 | Có suy ra hay lưu tôn giáo của người dùng không? | Không bao giờ; gói dịp tôn giáo tuỳ chọn chỉ bật khi người dùng chọn, chỉ lưu trên máy, không đồng bộ | 9.2.6 |
| QĐ-7 | Có dùng LLM để gợi ý quà không? | Chưa; nếu làm thì là tính năng VIP tuỳ chọn, qua callable phía máy chủ có cổng `vip` | 9.2.7 |
| QĐ-8 | Có gắn link affiliate không? | Chưa; `whereToBuy` giữ là chữ thường; U-35 chỉ mở tìm kiếm web | 9.2.8 |
| QĐ-9 | Dữ liệu nháp có cần người bản địa duyệt trước khi hiện không? | Có: phần `low` không tự hiện; điều kiêng kỵ chỉ từ `med` trở lên; thị trường chưa duyệt mang nhãn "bản nháp" | 9.2.9 |
| QĐ-10 | Phụ thuộc `firebase-ai` chưa có lệnh gọi nào xử lý ra sao? | Giữ nguyên đến khi QĐ-7 chốt; chỉ gỡ nếu QĐ-7 là callable phía máy chủ (SUY LUẬN) | 9.2.10 |

**Trả lời nhanh.** Một câu "đồng ý mặc định" nghĩa là: Philippines là thị trường thứ hai (rồi Brazil, Thái Lan); một lần nâng v15 sau D-15; nhắc theo tháng tắt mặc định; chưa thêm ngôn ngữ nào; Ả Rập Xê Út và Kuwait chưa hỗ trợ; không bao giờ suy ra tôn giáo; chưa dùng LLM; chưa affiliate; không tự hiện dữ liệu `low`; giữ `firebase-ai` đến khi QĐ-7 chốt.

### 1.8 Chưa được kiểm chứng

Danh sách đầy đủ ở 2.4 và 9.6. Những điều chủ app nên biết trước khi quyết:

- Chưa chạy Gradle, test hay emulator, chưa quan sát một thông báo nào thật; mọi kết luận về code là từ đọc mã.
- Số dòng trích từ cây làm việc chưa commit (372 mục trước khi thêm tài liệu này; các phiên khác dùng chung cây), nên có thể lệch sau khi code đổi.
- Chưa biết Firestore production có dữ liệu `gift_ideas` thật hay không. F-08 (định dạng ngày theo locale máy) chưa kiểm trên máy thật; nếu xác nhận sẽ nâng lên P0.
- Ngày âm lịch Việt Nam không có nguồn độc lập: HKO khớp 96/96 ở UTC+8, còn hai trang tiếng Việt có thể chạy cùng thuật toán. Hai giới hạn chưa kiểm: sai số tuyệt đối của chuỗi Meeus rút gọn và cách xếp tháng nhuận (7.6).
- Dữ liệu nháp chưa có người bản ngữ rà soát, không có script nào mở từng URL để kiểm, và 77 bản ghi mang nhãn "from memory". Việc tìm quà nháp trùng quà gốc chỉ dựa vào tên.
- Xếp hạng thị trường là ước lượng của một sub-agent; trang Play Store không tải được nên mô tả đối thủ lấy từ App Store, báo chí và trang tổng hợp (8.1).
- Một sub-agent (kiểm kê) chỉ trả báo cáo một phần (2.1) và một sub-agent (đối thủ) vượt ngân sách tìm kiếm được giao (8.6).
- Số cảnh báo lint (128 đến 131) là số ghi trong các kế hoạch trước, chưa chạy lại hôm nay.
- Người thứ hai mới đọc lại phần trích dẫn code có số dòng (bảy sub-agent, 564 cụm: 546 đúng, 18 sai và đã sửa; 2.2). Chưa ai đọc lại toàn bộ tài liệu: câu khẳng định sự vắng mặt, chỗ nhắc file không kèm số dòng và nhận định thị trường chưa qua người đọc thứ hai.

### 1.9 Bản đồ đọc

| Muốn biết | Đọc mục |
|---|---|
| Tài liệu được làm thế nào, tin được tới đâu | 2 Phương pháp, độ tin cậy và giới hạn |
| App hiện có lỗi gì, ở file nào | 3 Rà soát code (3.1 quà, 3.2 dịp lễ, cột mốc và nhắc hẹn, 3.3 đường ống nội dung) |
| Từng thị trường tặng quà và kỷ niệm ra sao | 4 Thị trường (4.1 Việt Nam, 4.2 Đông Á, 4.3 phương Tây, 4.4 Đông Nam Á và Ấn Độ, 4.5 Mỹ Latinh, Thổ Nhĩ Kỳ, Nga, CIS) |
| App nên xử lý các thói quen đó thế nào | 5 App nên xử lý thói quen và kỷ niệm tặng quà thế nào |
| Làm gì trước, làm gì sau, mỗi việc ra sao | 6 Nâng cấp có ưu tiên (6.1 bảng 38 việc, 6.2 đến 6.4 chi tiết từng đợt, 6.6 bảng đối chiếu phát hiện và mục thị trường với việc) |
| Dữ liệu nháp và bảng âm lịch | 7 Dữ liệu đã làm giàu và bảng âm lịch |
| Mở thị trường nào tiếp, kiếm tiền, AI | 8 Thị trường kế tiếp, kiếm tiền và AI |
| Cần quyết gì, rủi ro gì, còn chưa kiểm gì | 9 Quyết định cần chủ app chốt, rủi ro và việc chưa kiểm chứng |
| File nào nằm ở đâu, dữ liệu thô và nguồn | 10 Phụ lục (file dữ liệu trong `docs/research/data/`) |

**Bước tiếp theo.** Chủ app duyệt Đợt 1 (cả đợt, hoặc chọn việc trong đó) và trả lời QĐ-1, QĐ-2 nếu muốn mở đợt 2. Mỗi việc được duyệt sẽ đi qua kế hoạch, duyệt, build và kiểm trước khi đổi code; hiện chưa có gì được code.

## 2. Phương pháp, độ tin cậy và giới hạn

**Ý chính.** Tài liệu dựa trên 11 sub-agent, mỗi sub-agent một mảng độc lập, chạy song song: ba lượt rà soát code, một lượt kiểm kê nội dung trong code, một lượt kiểm chứng lịch âm, năm lượt nghiên cứu thị trường theo vùng và một lượt về đối thủ, kiếm tiền và AI. Phiên chính đối chiếu kết quả với code rồi viết lại thành các mục 1, 2, 5, 6, 7, 9 và 10. Bằng chứng gồm code đọc từ cây làm việc chưa commit (không chạy Gradle, không dùng máy giả lập), script đếm trên dữ liệu, trang web (một phần chỉ thấy qua bản tóm tắt của công cụ tìm kiếm) và thuật toán âm lịch đối chiếu với Đài thiên văn Hồng Kông cùng hai trang Việt Nam. Dữ liệu nháp (354 bản ghi) là hạt giống có nhãn tin cậy, chưa có người bản ngữ rà soát, chưa phải nội dung phát hành. Một lượt đọc lần hai (bảy sub-agent) đã đối chiếu 564 cụm trích code có số dòng với cây làm việc, nhưng chưa có người thứ hai đọc lại toàn bộ tài liệu (2.2, 2.4). **Không có dòng code, seed hay schema nào của app bị đổi.**

### 2.1 Các sub-agent và phần việc

| # | Phạm vi | Kết quả nằm ở | Tình trạng |
|---|---|---|---|
| 1 | Review code tính năng quà tặng | 3.1 | Hoàn tất |
| 2 | Review code kỷ niệm và nhắc hẹn | 3.2 | Hoàn tất |
| 3 | Đánh giá kiến trúc nội dung đa thị trường | 3.3 | Hoàn tất |
| 4 | Kiểm kê nội dung quà và dịp lễ trong code | 10.1, 10.2 và các số đếm trong mục 3 | Lần chạy đầu thất bại, sau đó trả về một báo cáo một phần. Số đếm lấy từ một script kiểm kê riêng của phiên chính và khớp với báo cáo một phần đó |
| 5 | Bảng lịch âm kiểm chứng 2025-2040 | 7.6 | Hoàn tất |
| 6 | Việt Nam (VN) | 4.1 | Hoàn tất, kèm gói dữ liệu nháp VN (73 bản ghi) |
| 7 | Đông Á (CN, HK, JP, KR, TW) | 4.2 | Hoàn tất, kèm gói EA (66 bản ghi) |
| 8 | Đông Nam Á và Ấn Độ (ID, IN, MY, PH, SG, TH) | 4.4 | Hoàn tất, kèm gói SEA_SA (67 bản ghi) |
| 9 | Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS, Ba Lan, vùng Vịnh (15 mã vùng) | 4.5 | Hoàn tất, kèm gói LATAM_TR_CIS (66 bản ghi) |
| 10 | Phương Tây (AU, CA, DE, ES, FR, GB, IT, US) | 4.3 | Hoàn tất, kèm gói WEST (82 bản ghi) |
| 11 | Đối thủ, xếp hạng thị trường, kiếm tiền, AI | 8 | Hoàn tất, nhưng vượt ngân sách tìm kiếm đã giao (8.6) |

Phiên chính viết các mục 1, 2, 5, 6, 7 (trừ 7.6, lấy từ sub-agent 5), 9, 10 và phần mở đầu của mục 3 và 4. Mục 8 lấy từ sub-agent 11, rồi phiên chính đối chiếu với code: chỗ sửa so với báo cáo gốc ghi HIỆU CHỈNH, chỗ thêm suy luận ghi SUY LUẬN (2.3). Dữ liệu nháp đã qua 12 sửa lỗi sau khi kiểm tra lại; danh sách nằm ở trường `meta.qaFixes` của `gift-occasion-draft.json` (7.7).

### 2.2 Các loại bằng chứng

| Loại bằng chứng | Dùng cho | Độ tin và giới hạn |
|---|---|---|
| Code đọc từ cây làm việc | Mục 3, phần hiện trạng của 4, 5 và 6, 10.1, 10.2 | Ghi dạng `tệp:dòng` theo quy ước đường dẫn ở 3.0. Cây chưa commit và còn đổi (372 mục thay đổi khi kiểm lại 2026-10-27, đếm trước khi thêm tài liệu này), nên số dòng có thể trôi. Không chạy Gradle, test hay máy giả lập, không quan sát một thông báo thật; suy luận chưa chứng minh mang nhãn `[CHƯA CHẮC]` |
| Script chạy trên code và dữ liệu | Số đếm ở 3.x, 4.0, 7 và 10; kiểm kê 20 quà gốc, 20 lễ Việt Nam và 13 lễ phương Tây | Script chỉ đếm và đối chiếu, không sinh nội dung. Script là tạm thời, không đưa vào repo, trừ `lunar_reference.py` (7.7). Mọi số đếm về dữ liệu nháp lấy từ `gift-occasion-draft.json` |
| Trang web đã mở | Mục 4 và 8 | Công cụ web trả về bản tóm tắt, không phải HTML thô; nhiều trang chỉ thấy qua bản tóm tắt của công cụ tìm kiếm. Các trang Play Store không tải được (404 hoặc bị cắt), nên mô tả app của đối thủ lấy từ App Store, báo chí và trang tổng hợp, tức bằng chứng gián tiếp (8.1). Không script nào mở từng URL (7.5) |
| Thuật toán âm lịch đối chiếu với bảng ngoài | 7.6, U-01, U-17 | Ở UTC+8 khớp Đài thiên văn Hồng Kông 96/96 ngày và 112/112 ngày; ở UTC+7 khớp ngaydep.com 30/30 ngày và baolamdong.vn 21/21 ngày. Hai trang Việt Nam nhiều khả năng chạy chính thuật toán này nên chỉ xác nhận phần cài đặt; nguồn độc lập thật sự là Hồng Kông. Ngày âm lịch Hàn Quốc chỉ do thuật toán tính, chưa mở nguồn Hàn Quốc nào |
| Nhãn nguồn dạng chữ trong dữ liệu nháp | 4 và 7 | 78 bản ghi có nhãn chữ trong `sources`, trong đó 77 bản ghi mang nhãn "from memory" (agent tự ghi là viết theo trí nhớ): EA 15 trên 66 bản ghi, LATAM_TR_CIS 15 trên 66, SEA_SA 19 trên 67, VN 13 trên 73, WEST 15 trên 82; thêm 1 bản ghi VN mang một nhãn chữ khác. 66 bản ghi không có URL nào, tất cả `low` |
| Ước lượng của agent | 8.2.2, 8.3 (xếp hạng thị trường), 8.5.2 (chi phí AI) | Gắn nhãn ƯỚC LƯỢNG: là phép tính hoặc nhận định của agent, không phải số đo. Phần kỹ thuật về AI (8.5.3) là kiến thức không kèm nguồn web |

**Kiểm tự động.** Ngày 2026-10-27, một script (không đưa vào repo) rút 748 cụm nêu tên file trong ngoặc ngược, trong đó 512 cụm kèm số dòng, thêm 59 cụm chỉ có số dòng, gắn với file nêu trước nó khi cùng ô bảng và cùng câu; tổng cộng 93 file khác nhau, ở các mục viết tay (không tính ba bảng do script sinh ra ở 1.7, 6.6 và 10.3). Đối chiếu với cây làm việc: 735 trên 748 cụm nêu tên file trỏ tới file có thật (583 cụm theo bốn mục đầu của quy ước ở 3.0, 152 cụm là tên đứng một mình tìm theo tên duy nhất ở mục 5 của quy ước); 512 trên 512 cụm kèm số dòng còn nằm trong độ dài file, 59 trên 59 cụm chỉ có số dòng cũng vậy; 13 cụm nêu tên file và 0 cụm chỉ có số dòng không đạt, ghi ở 10.3 (đó là tên file đề xuất chưa có trong cây hoặc file đã bị xoá). Phép kiểm này **chỉ xác nhận file có thật và đủ dòng, không xác nhận dòng đó nói đúng điều tài liệu viết**. Một phép kiểm phụ, gần đúng và chỉ để tham khảo, tìm tên hàm, lớp hoặc trường viết trong cùng ô bảng hoặc cùng câu với cụm trích, trong khoảng dòng trích cộng trừ 3 dòng: 160 cụm thấy, 83 cụm không thấy, 311 cụm không có tên để so, 17 cụm bỏ qua vì khoảng dòng dài hơn 40 dòng.

**Đối chiếu nội dung bằng cách đọc.** Sau phép kiểm trên, bảy sub-agent (mô hình Sonnet) đọc lần hai: mỗi agent nhận một phần trong 564 cụm trích có số dòng của bản nháp lúc đó, đọc các dòng được trích (cộng trừ 2 dòng) trong cây làm việc rồi so với câu của tài liệu quanh cụm. Kết quả: 546 cụm đúng, 16 cụm sai khoảng dòng và 2 cụm sai nội dung; cả 18 chỗ được phiên chính đọc lại code rồi sửa. Bản cuối có 571 cụm trích có số dòng: 544 cụm giống hệt bản các agent đã đọc, còn 27 cụm là cụm mới hoặc cụm đã sửa sau lượt đó, do phiên chính tự đọc lại code và chưa qua người đọc thứ hai. Đây là kiểm bằng cách đọc, không phải chứng minh đầy đủ: lượt đọc chỉ xem câu quanh cụm trích, không phủ câu khẳng định sự vắng mặt (ví dụ "không có chỗ nào gọi"), các chỗ nhắc file không kèm số dòng, và các nhận định thị trường ở mục 4 và 8, vốn dựa vào trang web chứ không phải code.

### 2.3 Mã số và nhãn dùng trong tài liệu

**Mã số**

| Mã | Nghĩa | Nằm ở |
|---|---|---|
| G-01 đến G-24 | Phát hiện về quà tặng | 3.1 |
| F01 đến F21 | Phát hiện về kỷ niệm và nhắc hẹn | 3.2 |
| F-01 đến F-18 | Phát hiện về đường ống nội dung đa thị trường | 3.3 |
| U-01 đến U-38 | Việc nâng cấp: Đợt 1 là U-01 đến U-18, Đợt 2 là U-19 đến U-28, Đợt 3 là U-29 đến U-38 | Bảng ở 6.1, chi tiết ở 6.2 đến 6.4 |
| QĐ-1 đến QĐ-10 | Quyết định chủ app cần chốt, mỗi quyết định có mặc định | 9.1 |
| D-nn, DEC-nn | Quyết định của kế hoạch KMP/iOS | `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md`, ngoài tài liệu này |
| GV-nnn | File golden JSON của test (`GV-013` là lịch âm, `GV-204` là hàng lễ gieo) | `shared/src/commonTest/resources/golden/` |
| `vn_g_015`, `us_g_012`... | Mã bản ghi trong dữ liệu nháp | `docs/research/data/gift-occasion-draft.json` |

`F18` (3.2, dẫn tới U-13) khác `F-18` (3.3, dẫn tới U-35): mã có dấu gạch ngang thuộc 3.3.

**Nhãn**

| Nhãn | Nghĩa |
|---|---|
| `high` / `med` / `low` | Độ tin cậy ghi trên từng bản ghi của dữ liệu nháp. Chú giải không giống nhau giữa năm báo cáo vùng (7.3, 7.5), nên `high` không có nghĩa là "hai nguồn khớp", "nguồn chính thức" hay "người bản ngữ đã duyệt" |
| (H) / (M) / (L) | Nhãn của agent trong bảng thị trường và đối thủ; không thống nhất giữa các bảng (4.0, 8.1) |
| ƯỚC LƯỢNG | Con số hay nhận định do agent ước tính, không phải số đo |
| SUY LUẬN | Điều suy ra từ code hoặc lập luận, do tài liệu này thêm vào, không phải kết luận của agent |
| HIỆU CHỈNH | Chỗ tài liệu sửa một nhận định của agent sau khi đối chiếu với code |
| `[CHƯA CHẮC]` | Suy luận chưa được chứng minh (3.0) |
| "?" trong ma trận đối thủ | Trang đã đọc không nói đến mục đó (8.2) |

**Thang**

| Thang | Dùng cho | Nằm ở |
|---|---|---|
| P0, P1, P2, P3 | Độ nghiêm trọng của phát hiện trong code | 3.0 |
| S, M, L | Cỡ công sức của việc U-nn (không đo độ nghiêm trọng) | 6.1 |
| Làm trước, Làm sau, Làm sau nữa | Nhãn ưu tiên của nhóm nghiên cứu thị trường, đổi từ P0, P1, P2 của họ để khỏi lẫn với thang P0-P3 | 4.0 và 8.5.5 |

### 2.4 Giới hạn

**Rà soát code**

- Rà soát chỉ đọc code: không chạy Gradle, test hay máy giả lập; chưa quan sát thông báo, quyền báo thức chính xác hay chuyển giờ mùa hè (DST) thực tế. Phát hiện nào phụ thuộc hành vi khi chạy thật mang nhãn `[CHƯA CHẮC]`; F-08 sẽ lên P0 nếu được xác nhận trên máy thật (3.0).
- Số dòng lấy từ cây làm việc chưa commit và đang đổi (3.0). Khi số dòng lệch, nên tìm theo tên hàm hoặc tên lớp đi kèm.
- Không script nào mở từng URL hay so câu chữ của bản ghi với trang nguồn. Việc đối chiếu nội dung do các agent làm khi thu thập, cộng một số lần kiểm tay (ba chỗ sai tìm được và đã sửa, 7.5), nên lỗi cùng loại vẫn có thể còn ở chỗ chưa kiểm.
- Chưa có người thứ hai đọc lại toàn bộ tài liệu. Lượt đọc lần hai (2.2) chỉ phủ các cụm trích code có số dòng và chỉ xem câu quanh cụm trích; chưa phủ câu khẳng định sự vắng mặt (ví dụ "không có chỗ nào gọi"), chỗ nhắc file không kèm số dòng, nhận định thị trường (mục 4 và 8) và 27 cụm mới hoặc đã sửa sau lượt đó. Các phép kiểm tự động ở 2.2 không thay được việc đọc.

**Dữ liệu thị trường**

- 354 bản ghi (87 dịp, 55 cột mốc, 142 quà, 70 kiêng kỵ) là hạt giống để rà soát, chưa phải nội dung phát hành; chưa có người bản ngữ nào xem (QĐ-9).
- Độ sâu không đều: Việt Nam sâu nhất; Mexico, Peru, Chile, Colombia, Argentina, Ba Lan và Thổ Nhĩ Kỳ còn mỏng; UAE và Ai Cập chỉ có một mục kiêng kỵ chung; Ả Rập Xê Út và Kuwait có một dịp (tắt) và hai mục kiêng kỵ, không có quà.
- Độ tin cậy: 34 bản ghi `high`, 170 `med`, 150 `low`. 66 bản ghi chỉ có nhãn chữ, không có URL, tất cả `low`. 12 bản ghi `high` chỉ có một URL, và không URL nào là của cơ quan thống kê (trang báo, một hãng khảo sát, một hiệp hội thương mại; 7.5). Chú giải `high`, `med`, `low` không thống nhất giữa năm báo cáo vùng (7.5).
- Nhãn "from memory" có ở 77 bản ghi (2.2), nên các con số và tên riêng ở những bản ghi đó cần người bản ngữ hoặc một nguồn thứ hai xác nhận trước khi phát hành.
- Từ vựng sở thích còn trùng nghĩa (`jewellery` 3 và `jewelry` 3; `home` 3 và `home_decor` 8) và cần chuẩn hóa theo từ vựng ở 6.3.6 trước khi nạp.
- Mã quà phương Tây trong dữ liệu nháp có tiền tố `us_g_`; gói thật dùng `west_g_` (6.3.7).
- Chỉ 4 trong 5 khoá chip danh mục hiện có trùng nguyên văn với 10 danh mục của bản nháp; việc ánh xạ nằm ở 6.3.6.
- Tính theo tên (chưa so mô tả), ít nhất 3 trong 142 quà nháp gần trùng một quà gốc: `ru_g_004` và `cn_g_003` với `gift-weekend-trip`, `pl_g_002` với `gift-board-game`; năm quà nữa cùng nhóm ý tưởng với một quà gốc. Quy tắc xử lý nằm ở 6.3.7.
- Chỉ 1 trong 142 quà có người nhận `him` (109 `any`, 32 `her`), nên bộ quà cho "anh ấy" còn mỏng (U-37).
- Ngày âm lịch Hàn Quốc chỉ do thuật toán tính, không có nguồn Hàn Quốc nào (7.6).

**Thị trường và đối thủ**

- Bảng xếp hạng thị trường (8.3) là ƯỚC LƯỢNG của một sub-agent. Năm nước đầu (Mỹ, Hàn Quốc, Brazil, Philippines, Thái Lan) nằm trong khoảng 0,35 điểm, và cột "nhu cầu" yếu nhất vì thiếu số tải app cặp đôi theo từng nước. Thứ tự đề xuất ở QĐ-1 khác thứ tự điểm thô (9.2.1).
- Các trang Play Store không tải được; mô tả app của đối thủ lấy từ App Store, báo chí và trang tổng hợp (8.1). Số lượt tải của Between và Been Together do chính nhà phát hành công bố.
- Phần AI (8.5.3) là kiến thức kỹ thuật không kèm nguồn web, còn các con số chi phí ở 8.5.2 là phép tính của agent.
- Sub-agent 11 vượt ngân sách tìm kiếm đã giao (8.6).
- App không có công cụ phân tích dùng riêng (`privacy/AppPrivacyCoordinator.kt:15`), chỉ có Crashlytics (`app/build.gradle.kts:206`), nên không đo được tính năng nào được dùng; Play Console là nguồn số liệu duy nhất hiện có.
- Tài liệu không phải tư vấn pháp lý: các điểm về dữ liệu cá nhân, quảng cáo và bán hàng theo từng nước cần người am hiểu luật địa phương xem lại.

### 2.5 Quy ước trình bày

- Số thập phân dùng dấu phẩy, hàng nghìn dùng dấu chấm (0,35 điểm; 361.489 byte).
- Tham chiếu chéo dùng dấu chấm (`7.1`, `6.3`); "mục 3" là cả mục.
- Mã định danh, tên file, tên hàm và tên trường nằm trong dấu huyền.
- Đường dẫn trong bảng viết rút gọn theo quy ước ở 3.0; mục 10.3 liệt kê đường dẫn đầy đủ từ gốc repo.
- "Kiêng kỵ" là các điều nên tránh khi tặng quà hoặc ăn mừng (taboo trong dữ liệu).
- Ngày viết tài liệu là 2026-10-27; các báo cáo của sub-agent đề ngày 2026-10-26.
- File dữ liệu nằm trong `docs/research/data/` (tên và cách dùng ở 7.7).

## 3. Rà soát code

**Ý chính.** Ba lượt rà soát (3.1 quà tặng, 3.2 kỷ niệm và nhắc hẹn, 3.3 đường ống nội dung đa thị trường) ghi **63 phát hiện: 25 mức P1 và 38 mức P2/P3, không có P0 nào được xác nhận**. Ba điểm nổi nhất: (1) thẻ gợi ý quà cá nhân hóa đã tính sẵn `reason` và `preparation` nhưng không vẽ chúng và cũng không bấm được (G-08); (2) đường nhắc cột mốc cũ trên Android báo mốc N ngày chậm một ngày, cứng chữ tiếng Việt trong thông báo và không được dựng lại sau khi khởi động máy (F01, F02, F03); (3) mọi ngày lễ gieo sẵn đều bật nhắc trước 3 ngày dù không liên quan cặp đôi, và Rằm tháng Giêng 2027 sai một ngày (F04, F07). F-08 (định dạng ngày LƯU theo `Locale.getDefault()`) sẽ lên P0 nếu được xác nhận trên máy thật. Rà soát chỉ đọc code: không chạy Gradle, không dùng máy giả lập, không quan sát một thông báo thật nào, nên suy luận chưa chứng minh được mang nhãn `[CHƯA CHẮC]`.

### 3.0 Cách đọc mục này

**Quy ước đường dẫn.** Mọi bảng trong tài liệu ghi đường dẫn dạng rút gọn, hiểu theo thứ tự sau (các mục khác gọi đây là "quy ước ở đầu mục 3"):

1. Tên bắt đầu bằng `res/`, `assets/` hoặc đúng `AndroidManifest.xml`: tính từ `app/src/main/`.
2. Tên bắt đầu bằng `app/`, `docs/`, `scripts/`, `firebase/`, `gradle/`, `shared/` hoặc đúng `firestore.rules`: tính từ gốc repo.
3. File test (như `golden/HolidayGoldenTest.kt`, `alarm/ReminderSchedulingTest.kt`, `alarm/plan/LoveMilestoneRulesTest.kt`, `CloudEnrichmentDataTest.kt`, `data/media/MemoryRestoreTest.kt`): tính từ `app/src/test/java/com/example/`.
4. Mọi tên còn lại có thư mục (như `data/seed/VietnameseHolidays.kt`, `ui/util/Localization.kt`): tính từ `app/src/main/java/com/example/`.
5. Tên không khớp bốn mục trên, đứng một mình hoặc chỉ kèm một thư mục cuối (như `InLoveViewModel.kt`, `GiftScreen.kt`, `Dialogs.kt`, `OfflineSeeder.kt`, `gatePolicy.js`, `holiday_calendar.json`, `spike/SpikeDatabase.kt`): tìm theo tên file duy nhất trong cả repo, không tính `docs/` và `spikes/`; mục 10.3 cho đường dẫn đầy đủ của từng tên. Một số tên hay gặp: `InLoveViewModel.kt` ở `ui/viewmodel/`; `GiftScreen.kt`, `Dialogs.kt`, `CalendarScreen.kt` ở `ui/screens/`; `OfflineSeeder.kt` ở `data/repository/`. Mọi đường dẫn vào `docs/` trong tài liệu này đều ghi đủ từ `docs/`.

Mục 10.3 liệt kê đường dẫn đầy đủ từ gốc repo của mọi file được nhắc. **Số dòng lấy từ cây làm việc chưa commit**, kiểm lại ngày 2026-10-27 khi cây có 372 mục thay đổi (126 sửa, 244 chưa theo dõi, 2 xoá; đếm trước khi thêm tài liệu này), nên số dòng có thể trôi khi cây còn đổi. Khi đối chiếu, nên tìm theo tên hàm hoặc tên lớp đi kèm trước.

**Mức nghiêm trọng** (thang giao cho cả ba lượt rà soát):

| Mức | Nghĩa |
|---|---|
| P0 | Mất hoặc làm hỏng dữ liệu, sập app, lỗi bảo mật, sai ngày hoặc sai tiền |
| P1 | Sai hành vi gặp thường xuyên, hoặc thiếu lớn so với nhu cầu của thị trường đích |
| P2 | Chất lượng, độ nhất quán, bảo trì |
| P3 | Lặt vặt |

Các bảng gộp P2 và P3 thành một khối. **Không có P0 nào được xác nhận.** Hai lỗi lệch ngày đã thấy rõ trong code (F01: thông báo mốc chậm một ngày; F07: Rằm tháng Giêng 2027) được xếp P1, vì ngày do người dùng nhập vẫn được lưu và đếm đúng; chỉ giờ báo (F01) hoặc một dòng dữ liệu gieo sẵn (F07) sai. F-08 chạm đúng ngày do người dùng nhập nên sẽ lên P0 nếu được xác nhận trên máy thật. Thang mức này độc lập với cỡ S/M/L của mục 6 (cỡ đo công sức, không đo độ nghiêm trọng).

**Số phát hiện theo mục**

| Mục | Mảng | P1 | P2/P3 | Tổng | Mã |
|---|---|---:|---:|---:|---|
| 3.1 | Quà tặng | 8 | 16 | 24 | G-01 đến G-24 |
| 3.2 | Kỷ niệm và nhắc hẹn | 9 | 12 | 21 | F01 đến F21 |
| 3.3 | Đường ống nội dung đa thị trường | 8 | 10 | 18 | F-01 đến F-18 |
| | **Tổng** | **25** | **38** | **63** | |

Ghi chú đọc mã: G-21 đến G-24 chiếm một hàng trong bảng 3.1 nhưng tính bốn phát hiện. `F18` (mục 3.2, dẫn tới U-13) khác `F-18` (mục 3.3, dẫn tới U-35): mã có dấu gạch ngang thuộc 3.3. Bảng từ phát hiện sang việc nâng cấp nằm ở 6.6.2.

### 3.1 Quà tặng (Gift)

**Luồng hiện tại.** `data/seed/GiftIdeasSeed.kt` (20 seed song ngữ) → `OfflineSeeder.ensureSeedGiftCatalog` (upsert cả 20 hàng MỖI lần mở app) → Room `gift_ideas` → `InLoveRepository.giftIdeas` → `InLoveViewModel.personalizedGifts` → `ui/screens/GiftScreen.kt`. Lúc hiển thị, `domain/content/GiftCatalog.localized` thay chữ theo `remoteId` dạng `seed_*`; hàng Room vẫn chỉ một ngôn ngữ. Gợi ý cá nhân hóa do `GetPersonalizedGiftSuggestionsUseCase` (dịp gần nhất trong 30 ngày, từ khóa sở thích, sàn ngân sách VND, vùng `VN`/`INTL`, `take(3)`) và `GiftRanker` (so khớp chuỗi con) đảm nhiệm. Bộ "AI" thực chất là template tiếng Việt cứng (`data/repository/GiftSuggestionGenerator.kt:15-106`), **không có lời gọi mô hình nào** (đã grep: Firebase AI chỉ có phụ thuộc, không có call).

**P1 (sai hành vi hoặc thiếu lớn cho thị trường đích)**

| ID | file:line | Vấn đề | Hướng sửa |
|---|---|---|---|
| G-01 | `data/seed/GiftIdeasSeed.kt:69-74`; `domain/content/GiftCatalog.kt:96-105`; `ui/screens/GiftScreen.kt:1323` | Giá và ngân sách chỉ có VND (`PriceRange` là hằng số VND, bộ chọn ngân sách VND). Người dùng USD/EUR/JPY/INR không có thang giá. | Lưu `priceTier` 1-4 trên quà; mỗi thị trường có `tierBands` bằng tiền địa phương (xem U-22 và U-28, mục 6.3). |
| G-02 | `GiftIdeasSeed.kt:52-65` (`toEntity`); `domain/GiftRanker.kt:18-22,33` | `toEntity` không bao giờ gán `targetInterests` → 20 seed không có nhãn sở thích; cá nhân hóa chỉ dựa so khớp chuỗi con (+1) thay vì nhãn (+3). Từ khóa EN/VI lệch nhau nên dễ trượt. | Gán `interestKeys` chuẩn hóa cho mọi seed, ranker so khớp theo key. Chỉ cần dữ liệu: `GiftIdeaEntity` đã có cột. |
| G-03 | `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:116` vs `domain/GiftRanker.kt:14-15,25` | Khi dịp sắp tới là kỷ niệm tròn 1 năm, tên dịp là "Kỷ niệm 1 năm" (`GetPersonalizedGiftSuggestionsUseCase.kt:116`), còn `occasionFits` chỉ so khớp chính xác (không phân biệt hoa thường) hoặc khi `suggestedOccasion` chứa "bất kỳ"/"any". Trong 20 seed chỉ 1 quà ghi "Kỷ niệm 1 năm" (`GiftIdeasSeed.kt:414`) và 2 quà ghi "Bất kỳ dịp nào"; 10 quà "Kỷ niệm ngày yêu" không được cộng điểm dịp (+2). Khi chưa có sở thích chung, bộ lọc ở `invoke` (`GetPersonalizedGiftSuggestionsUseCase.kt:152-155`) chỉ giữ quà khớp dịp, nên chỉ 3 quà đủ điều kiện (thay vì 12 ở các kỷ niệm sau). Suy ra từ code, chưa chạy. | Khóa dịp chuẩn (`anniversary`, `valentine`, `birthday_partner`...) thay cho chuỗi tự do; kỷ niệm 1 năm chỉ là biến thể của `anniversary`. |
| G-04 | `GetPersonalizedGiftSuggestionsUseCase.kt:113`; `InLoveViewModel.kt:588-598` | Sinh nhật của CẢ HAI người đều thành dịp "tặng quà cho đối phương"; không phân biệt sinh nhật mình với sinh nhật người kia. | Dịp có thuộc tính `forPartner`; sinh nhật mình chỉ bật nhắc hoặc ý tưởng "mong nhận". |
| G-05 | `GetPersonalizedGiftSuggestionsUseCase.kt:46-54,119,127-131` | Bảng dịp cố định (`fixedOccasions`, `GetPersonalizedGiftSuggestionsUseCase.kt:46-51`) gồm Valentine, 8/3, 20/10, Giáng Sinh; thêm sinh nhật, kỷ niệm ngày yêu và Tết (`:127-131`, chỉ khi vùng không phải `INTL`). 8/3 và 20/10 bị bỏ khi vùng là `INTL` (`vietnamOnly`, `GetPersonalizedGiftSuggestionsUseCase.kt:54`); vùng chỉ phân biệt `INTL` với mọi giá trị còn lại (`:119`). Còn thiếu: 14/3, Thất Tịch, Chuseok, Diwali và các dịp theo thị trường ở mục 4. Tết không khớp seed nào (không seed nào ghi "Tết Nguyên Đán"), nên chỉ được điểm "bất kỳ dịp nào". | Bảng dịp theo thị trường, data-driven (JSON hoặc Firestore) + `regionKeys[]`. |
| G-06 | `GetPersonalizedGiftSuggestionsUseCase.kt:135-167`; `GiftRanker.kt:38` | Khởi đầu nguội (chưa có sở thích chung, không có dịp trong 30 ngày tới) trả danh sách RỖNG: bộ lọc ở `GetPersonalizedGiftSuggestionsUseCase.kt:152-155` chỉ giữ quà khớp sở thích hoặc khớp dịp; `MAX_RESULTS = 3`. Người mới thấy chữ hướng dẫn thay cho khối gợi ý (`GiftScreen.kt:344-349`). | Fallback: 2 quà "Bất kỳ dịp nào" hiện có, hoặc thêm trường xếp hạng độ phổ biến (chưa có trong code) rồi lấy top N. |
| G-07 | `GiftSuggestionGenerator.kt:15-106`; `InLoveViewModel.kt:1267-1288` | Bộ sinh "AI" chỉ tiếng Việt, cứng "Kỷ niệm ngày yêu", "người ấy"; chỉ 4 chủ đề; người dùng EN thấy chữ Việt. Toast "thêm n" dù upsert idempotent. | Đa ngôn ngữ hóa template hoặc đổi tên "gợi ý nhanh" và bỏ nhãn AI (xem G-14). |
| G-08 | `ui/screens/GiftScreen.kt:331-343,1215-1260`; `ui/screens/Dialogs.kt:581-763` | Thẻ gợi ý cá nhân hóa nhận `reason` và `preparation` (do use case tính, `GetPersonalizedGiftSuggestionsUseCase.kt:157-165`) nhưng chỉ vẽ tên, giá và nút chia sẻ: không ảnh, không bấm xem chi tiết, nên lý do và cách chuẩn bị không bao giờ hiện ra. `GiftDetailDialog` có ảnh, mô tả và "CHI TIẾT THỰC HIỆN" nhưng không có chia sẻ, nơi mua, "đã tặng" hay hẹn nhắc; chia sẻ chỉ có tên quà và giá. Hẹn nhắc quà có riêng (`GiftReminderEntity`, `Entities.kt:177-190`) nhưng không liên kết với quà trong catalog, và `isCompleted` nghĩa là "đã chuẩn bị" chứ không phải "đã tặng". Luồng chọn quà → mua → hẹn nhắc → đánh dấu đã tặng vì vậy chưa liền mạch. | Hiện `reason`/`preparation` và mở chi tiết từ thẻ, thêm cảnh báo mềm và nơi mua (U-30, U-35); lịch sử "đã tặng" (U-29). |

**P2/P3 (chất lượng)**

| ID | file:line | Vấn đề → hướng sửa (một dòng) |
|---|---|---|
| G-09 | `data/repository/OfflineSeeder.kt:52-66` | 20 upsert MỖI lần mở app làm Flow bảng re-emit, tính lại `personalizedGifts` → so sánh hàng đang lưu với seed trước khi ghi (hoặc thêm phiên bản nội dung, chưa có trong code) để chỉ ghi khi seed đổi. |
| G-10 | `OfflineSeeder.kt:17-46` | `hasGifts` tính TRƯỚC khi seed; quà cloud chỉ lấy ở lần đầu nên sửa/bổ sung Firestore không đến máy đã cài; seed local còn ghi đè bản Firestore trùng remoteId `seed_*` → đồng bộ theo version, ưu tiên cloud. |
| G-11 | `data/repository/CloudPresetFetcher.kt:179-241` | Timeout 2 s, tải tuần tự cả collection, KHÔNG validate (scheme/host ảnh, độ dài, id trùng `seed_*`), mặc định tiếng Việt → lược đồ + whitelist https, cấm tiền tố `seed_`/`local_`/`tpl_`. |
| G-12 | `OfflineSeeder.kt:58` vs `data/db/Migrations.kt:132` | Nhánh nhận hàng legacy đòi `remoteId.isBlank()` nhưng migration backfill `'legacy_'||id` → nhánh gần như mã chết; người nâng cấp từ <v12 có thể có thẻ trùng `[CHƯA CHẮC]`. |
| G-13 | `data/repository/GiftSuggestionGenerator.kt:37,56,75,95` | Hàng `tpl_*` sinh theo tên người yêu + dịp, không dọn → tăng không giới hạn khi đổi tên → xoá `tpl_` cũ khi sinh lại. |
| G-14 | `GiftRanker.kt:35`; `res/values/strings.xml:165-176` | +1 điểm cho `isAiGenerated` và chuỗi "AI Gifts" quảng bá AI khi không có model → rủi ro quảng cáo sai sự thật (nhất là store listing). |
| G-15 | `data/repository/InLoveRepository.kt:108-110` | `toggleGiftFavorite` ghi cả hàng cũ → có thể ghi đè trường vừa đổi → `UPDATE ... SET isFavorite` theo remoteId. |
| G-16 | `GiftIdeasSeed.kt` (Pexels 19/20), `:50` picsum | Ảnh hot-link bên thứ ba: điểm hỏng đơn, bản quyền chưa rõ → tự host (R2/Firebase Storage), lưu `imageLicense`. |
| G-17 | `InLoveViewModel.kt:821-823` | `parseLikes` không fold dấu/hoa thường ("Âm nhạc" ≠ "am nhac") `[CHƯA CHẮC trên bàn phím thật]`. |
| G-18 | `GiftScreen.kt:376-430,1163-1189,1342-1352,528` | Nhiều mục chạm dưới 48dp, trái AGENTS.md. |
| G-19 | `GiftScreen.kt:433-553` | Thẻ tĩnh "1,000-Day" mở VIP paywall (`GiftScreen.kt:528`) dưới nhãn "Xem lịch trình" → nhãn không báo trước paywall. |
| G-20 | `scripts/seed_firestore.js:331-342` | Doc mẫu có `category: 'AI Đề Xuất ✨'`, `priceRange: 'Miễn phí với VIP'` → tách cờ `vipOnly` khỏi chuỗi giá. |
| G-21..24 | `GiftScreen.kt:1087`, `Dialogs.kt:639`; `res/values/strings.xml:197-203`, `ui/util/AppStrings.kt:165-171`, `ui/util/Localization.kt:237-243`; `InLoveViewModel.kt:606`; `GiftCatalog.kt:35,37-54,110-112` | contentDescription cứng tiếng Anh ("Favorite", "Close"); 7 chuỗi `gift_cat_*` chỉ nạp vào các trường `AppStrings.giftCat*` (`Localization.kt:237-243`, dự phòng `:463-469` và `:680-686`) mà không màn nào đọc (đã grep `app/src`), còn chip thật lấy nhãn từ `GiftCatalog.chips` (`GiftCatalog.kt:35,58`) với bộ khoá khác → dọn (U-18); `today` cố định lúc tính (`LocalDate.now()`); chip danh mục và bản dịch tag cứng trong code; `seedRemoteIdFor` khớp dòng cũ theo URL ảnh rồi theo tiêu đề nên dòng đã đổi cả hai thì mất bản địa hóa. |

**Hai catalog song song.** `scripts/seed_firestore.js:118-342` có thêm 15 doc với id khác hẳn (`gift_album_diy`...), không đồng bộ với 20 seed trong app, và có cả huy hiệu bịa "98% Cặp đôi mê mẩn" → phải hợp nhất thành MỘT nguồn (U-19, U-21 và U-25, mục 6.3).

**Chưa kiểm chứng.** Không chạy Gradle/test/emulator; hành vi nêu ở trên suy ra từ code. Chưa biết Firestore production có `gift_ideas` thật hay không (nếu trống thì chỉ dùng 20 seed, G-10 và G-11 ít ảnh hưởng). Chưa kiểm: URL ảnh Pexels chỉ dựa vào chú thích trong file seed ("đã kiểm 2026-10-26").

### 3.2 Dịp lễ, kỷ niệm, cột mốc và nhắc hẹn

**Hiện trạng.**

- **Dữ liệu lễ gieo sẵn:** `data/seed/VietnameseHolidays.kt` (15 ngày dương cố định :35-51 và 5 lễ âm lịch tra bảng `datesByYear` :64-125, chỉ năm 2025-2028; Vu Lan chỉ 2025-2026, Trung Thu 2025-2027), `data/seed/WesternHolidays.kt` (9 ngày cố định :34-43 và 4 quy tắc nth-weekday; Easter bị bỏ có chủ đích), `data/seed/HolidayDates.kt` (`nextLunar`, `nextRuleBased`, `refreshExisting` :30-48).
- **Gieo vào máy:** `data/repository/OfflineSeeder.kt` (`buildHolidayAnniversaries` :188-229). Vùng chọn **chỉ theo ngôn ngữ giao diện** (:211): VI = lễ Việt Nam, còn lại = lễ phương Tây.
- **Hai bộ máy nhắc chạy song song:**
  - Đường cũ, là đường Android production qua `AndroidReminderScheduler.resyncNow`: `alarm/AlarmNotificationScheduler.kt`, `alarm/LoveAnniversaryMilestoneScheduler.kt`, `alarm/ReminderAlarmReceiver.kt`, `alarm/BootCompletedReceiver.kt`.
  - Planner thuần dùng kotlinx.datetime, sẵn sàng cho KMP/iOS: `alarm/plan/` (`LoveMilestoneRules`, `ReminderTexts` song ngữ, `GatewayReminderScheduler`).
- **Hook dữ liệu chưa dùng:** `domain/content/ContentModels.kt:32-63` (`HolidayKind`, `HolidayCalendarItem`) và `ContentRepository.getHolidayCalendar` chỉ được khai báo, không có implementation hay nơi gọi trong `app/src`.
- **Test có sẵn:** `HolidayGoldenTest` (GV-012/013/014), `HolidaySeedGoldenTest` (GV-204), `HolidayRefreshGoldenTest` (GV-015), `ReminderSchedulingTest`, `LoveMilestoneRulesTest`. Android là đặc tả; golden JSON nằm ở `shared/src/commonTest/resources/golden`.

**P1**

| ID | file:line | Vấn đề | Hướng sửa |
|---|---|---|---|
| F01 | `alarm/LoveAnniversaryMilestoneScheduler.kt:96` so với `alarm/plan/LoveMilestoneRules.kt:54` và `ui/util/ProfileUtils.kt` (`loveDaysOn` = diff + 1) | **Lệch 1 ngày.** Bộ đếm coi ngày bắt đầu là ngày 1, đường cũ tính mốc N ngày = `start + N×24h` nên báo mốc 100 ngày vào ngày thứ 101. Planner tính `start + (N−1)` là đúng. Cộng 24h còn lệch khi qua DST. Mốc 100/200/365/1000 ngày là khoảnh khắc cảm xúc nhất của app. | Dùng `LocalDate.plusDays(N−1)` trên múi giờ hiện tại, tốt nhất bỏ đường cũ và dùng planner; thêm test khớp `loveDaysOn`. |
| F02 | `alarm/LoveAnniversaryMilestoneScheduler.kt:109,140,194-195,215-216,236-237`; `alarm/AlarmNotificationScheduler.kt:333,359-360` | Nội dung thông báo kỷ niệm và cột mốc **cứng tiếng Việt** trên đường production (vd. `"Cột mốc $days ngày bên nhau đong đầy kỷ niệm"`), dù `ReminderTexts.kt` đã có bản EN. Người dùng giao diện EN nhận thông báo tiếng Việt. | Dựng chữ qua `ReminderTexts` ở mọi nơi, hoặc chuyển Android sang planner. |
| F03 | `alarm/BootCompletedReceiver.kt:20-52` | Sau khởi động lại hoặc đổi múi giờ chỉ gọi `scheduleAllAnniversariesFromDb`; alarm cột mốc yêu không được dựng lại tới khi mở app (`resyncNow`). | Gọi `AndroidReminderScheduler.resyncNow` từ boot receiver. |
| F04 | `data/repository/OfflineSeeder.kt:193-203,212-227` | Mọi ngày lễ gieo đều `notificationEnabled=true`, `reminderDaysBefore=3` bất kể `isCoupleRelevant`: người dùng bị nhắc "Ngày Thương binh liệt sĩ", Quốc khánh... bằng giọng cặp đôi ("Chúc hai bạn..."). `isCoupleRelevant` chỉ nằm trong file seed, không được dùng khi gieo. | Chỉ bật nhắc cho lễ cặp đôi (`coupleRelevance ≥ 2`); lễ khác mặc định tắt, hiển thị dạng thông tin. Không đổi schema. |
| F05 | `OfflineSeeder.kt:39,175-177` | Lễ chỉ gieo khi bảng anniversary rỗng hoặc thiếu milestone/gift/badge; người dùng cũ có đủ dữ liệu có thể không bao giờ nhận lễ mới `[CHƯA CHẮC, chưa dựng luồng nâng cấp để thử]`. | Gieo theo cờ riêng `holidaySeedVersion`. |
| F06 | `data/seed/HolidayDates.kt:16,30-48`; `VietnameseHolidays.kt:64-125` | **Bảng âm lịch hết hạn.** Tết dòng cuối 2028-01-26; Vu Lan chỉ tới 2026-08-27 (nay là 2026-10-26, đã qua và không có năm sau); `refreshExisting` giữ ngày cũ và đặt `isAnnual=false` nên hàng thành lễ đã qua vĩnh viễn, không nhắc nữa. | Tính âm lịch bằng thuật toán (xem mục 7, đã kiểm 96/96 mốc) hoặc bảng 2025-2040; có thông báo "hết dữ liệu" khi thiếu. |
| F07 | `VietnameseHolidays.kt:86` | **Rằm tháng Giêng 2027 sai 1 ngày:** repo ghi 2027-02-19, đúng là **2027-02-20** (Tết 2027 là 6/2, mùng 1 + 14 ngày). Đây là lỗi duy nhất trong bảng của repo theo đối chiếu với bảng HKO (xem mục 7). | Sửa bảng và golden GV-013. |
| F08 | `OfflineSeeder.kt:211`; `VietnameseHolidays.kt:35-51`; `WesternHolidays.kt:34-84` | Vùng chọn chỉ theo ngôn ngữ UI, không có quốc gia. Danh sách VN thiếu Giáng sinh, White Day, Ngày của Mẹ/Cha, Thất Tịch; danh sách phương Tây thiếu White Day, Pepero Day, 520, Dia dos Namorados (12/6), Karwa Chauth, Easter. Người dùng EN ở châu Á nhận lịch Mỹ. | `region` theo quốc gia và luật dữ liệu (U-23, mục 6.3). |
| F09 | `AndroidManifest.xml:8-11`; `ui/viewmodel/InLoveViewModel.kt:911` | Khai báo `SCHEDULE_EXACT_ALARM`, có fallback inexact và một cảnh báo trạng thái, nhưng **không có lối đưa người dùng tới màn cấp quyền** (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM` không được dùng). Từ Android 14 quyền này không mặc định cấp cho app mới: nhắc rơi về inexact (trễ vài phút đến vài chục phút) mà người dùng không biết. | Màn hướng dẫn cấp quyền khi có nhắc đầu tiên và hiển thị trạng thái "nhắc gần đúng giờ". |

**P2/P3**

- **F10** `ui/screens/Dialogs.kt:2550-2552`, `LoveMilestoneRules.kt:13-14,25-38,67`: mốc ngày chỉ có 30, 50, 100, 150, 200, 300, 365, 500, 730, 1000, 1500, 2000 (thiếu 22, 52, 520, 1314, 400-900, 3000), không có mốc tháng ("monthsary"); lead time cố định {0,1,3,7}, giờ nhắc 09:00/08:30 cứng, người dùng không chọn được.
- **F11** `LoveMilestoneRules.kt:57-63` + alarm couple 400001: mốc 365/730 trùng mốc năm (cùng rơi ngày gần nhau); 29/2 khác nhau giữa đường cũ (lenient → 1/3) và planner (kẹp 28/2).
- **F12** `alarm/plan/ReminderTexts.kt:62,99`: chữ "24 giờ" trong khi báo lúc 09:00 hôm trước; nhắc 3 ngày không có nút/deep link tới quà.
- **F13** `alarm/ReminderAlarmReceiver.kt:162-256`: thông báo chỉ mở tab, không mở dịp hay màn quà; snooze cố định +1h.
- **F14** `ui/screens/CalendarScreen.kt:941,1385-1412`: nút "Gợi ý quà" gọi `onNavigateToGifts()` **không truyền dịp**; gợi ý chỉ có 4 lễ cố định + Tết + sinh nhật + kỷ niệm, tên dịp khớp bằng chuỗi tiếng Việt.
- **F15** `CalendarScreen.kt:142-160`: `matchesCalendarDay` bỏ qua năm nên lễ đổi ngày hằng năm (`isAnnual=false`) hiện ở mọi năm `[CHƯA CHẮC]`.
- **F16** `CalendarScreen.kt:298-312`: chỉ xoá mềm từng lễ, không có "ẩn lễ" hay bật/tắt theo nhóm.
- **F17** `data/sync/AnniversarySyncAdapter.kt`: trường đồng bộ thiếu lunar/rule/source/giờ nhắc; đổi mô hình phải cập nhật adapter và golden GV-026.
- **F18-F21** (P3): `checkAndNotifyTodayAnniversaries` so ngày/tháng và có thể báo song song; nhãn "00:00 Ngày lễ" nhưng bắn 09:00; `refreshExisting` ghép hàng bằng chuỗi emoji + tiêu đề (đổi tên hàng gieo là mất cập nhật ngày); không có test cho F01/F02/F03 và hết hạn bảng âm lịch sau 2028.

**Parity KMP/iOS.** Planner dùng chung quy tắc cho Android và iOS (F01 đúng ở planner), nhưng Android production vẫn đi đường cũ ⇒ hai nền tảng sẽ báo mốc lệch 1 ngày, khác ngôn ngữ và khác 29/2. Lịch lễ chưa có bản port trong `shared/src/commonMain` (golden JSON đã sẵn làm đặc tả).

**Chưa kiểm chứng.** Review chỉ đọc code; chưa quan sát thông báo, quyền exact alarm hay DST thực tế. Chưa đọc kỹ `GatewayReminderScheduler`, `ReminderPlanner`, `BackgroundRefresh`, `SyncCoordinator`, `MilestoneBadgeTracker`. F05, F16, F18 phụ thuộc hành vi khởi động và xoá mềm khi chạy thật.

### 3.3 Đường ống nội dung: có mở rộng ra nhiều thị trường mà không cần release không?

**Kết luận ngắn: chưa.** Hạ tầng "đẩy nội dung không cần release" mới có một nửa (Firestore đọc công khai + script seed). Phần còn lại bị khoá cứng vào VI/EN, VND và ba bảng dữ liệu trùng nhau.

**Bằng chứng chính** (đường dẫn tính từ `app/src/main/java/com/example/`):

- **Ngôn ngữ chỉ có hai giá trị.** `AppLanguage { VI, EN }` (`ui/util/Localization.kt:8-10`); máy không phải tiếng Việt bị ép về EN (`:29-32`). Có 678 lần xuất hiện `isEnglish` trong 30 file và 56 lời gọi `tr("vi","en")` trong `InLoveViewModel.kt` (đếm bằng grep trên cây làm việc ngày 2026-10-27). Thêm KO/JA/TH/ID/PT/ES nghĩa là sửa enum, các `when` và hàng trăm chỗ rẽ nhánh. Manifest không có `locales_config`.
- **Khoá nghiệp vụ là chuỗi tiếng Việt.** `category` = "Quà lãng mạn" (`domain/content/GiftCatalog.kt:16-20`), `suggestedOccasion` = "Valentine"/"Kỷ niệm ngày yêu" (`data/seed/GiftIdeasSeed.kt:91,109`), `priceRange` = "500k-1tr" (`:69-74`), và `GiftRanker.occasionFits` so chuỗi (`domain/GiftRanker.kt:15`). Dịch hoặc đổi chữ là vỡ lọc và xếp hạng.
- **Lịch lễ chọn theo ngôn ngữ, không theo thị trường.** VI → `VietnameseHolidays`, còn lại → `WesternHolidays` (`OfflineSeeder.kt:211`), chỉ gieo một lần khi `anniversary_dates` rỗng. Người Hàn/Nhật/Thái dùng EN nhận lịch Mỹ (St. Patrick, Boyfriend Day).
- **Vùng chỉ có `VN`/`INTL`** (`GetPersonalizedGiftSuggestionsUseCase.kt:37-38`), lưu ở `couple_profile.occasionRegion`, người dùng phải tự chọn ở `GiftScreen.kt:1338-1339`. Không nối với ngôn ngữ, không suy ra từ locale.
- **Tiền chỉ có VND.** `budgetMaxVnd` (`data/model/Entities.kt:100`), nhãn "₫" cứng (`GiftCatalog.kt:96-107`), bộ chọn ngân sách `GiftScreen.kt:1323`.
- **Cột mốc có hai bảng song song** (`LoveAnniversaryMilestoneScheduler.kt:46-59` chỉ tiếng Việt và `alarm/plan/LoveMilestoneRules.kt:23-38` song ngữ) lệch quy ước ngày (xem F01 ở mục 3.2). Cả hai đều là danh sách kiểu Việt (30/50/100...), không có mốc theo thị trường (22 ngày, 100 ngày Hàn, Thất Tịch...).
- **Pipeline Firestore.** `CloudPresetFetcher.fetchDynamicGiftIdeasFromFirestore` lấy CẢ collection `gift_ideas`, không lọc vùng/ngôn ngữ, không version, không validate, upsert theo `doc.id` (`data/repository/CloudPresetFetcher.kt:198-241`), timeout 2 giây (`:179-193`). Luật `firestore.rules:310-313`: read public, write false. 13-15 món trong `scripts/seed_firestore.js` khác hẳn 20 seed trong app.
- **Seam `ContentRepository` là xác chết:** `domain/content/ContentRepository.kt:15-31` + `domain/content/ContentModels.kt` (có `HolidayCalendarItem` FIXED/LUNAR/RULE_BASED) nhắc `FirebaseContentRepository` (`ContentRepository.kt:12`) và `ContentMappers` (`ContentModels.kt:12`) nhưng thư mục `data/content/` **không tồn tại** và grep không thấy nơi dùng. Đây là chỗ gắn sẵn, chỉ thiếu cài đặt.
- **Ảnh:** Pexels hot-link đã kiểm tay cho 19/20 món, còn lại picsum; template dùng Unsplash; R2/Cloudinary chỉ phục vụ avatar và ảnh kỷ niệm, chưa có pipeline ảnh quà.
- **Link ngoài/affiliate: không có.** `GiftDetailDialog` (`ui/screens/Dialogs.kt:582`) không mở URL; UMP đã có cho quảng cáo (`ads/AdsManagerImpl.kt`) nhưng chưa gắn gì vào gợi ý quà.
- **KMP/iOS:** `shared/src/commonMain` hiện chỉ có `spike/SpikeDatabase.kt`; kế hoạch (D-13, D-11) chuyển `GiftIdeasSeed/GiftCatalog/ContentModels/ContentRepository` nguyên văn và ngày lễ sang kotlinx-datetime. Đã có kotlinx-serialization-json và kotlinx-datetime ở commonMain, cùng golden JSON ghim xếp hạng quà (GV-016, GV-029) và ngày lễ (GV-012..015, 204). **Mọi đề xuất phải nằm trong commonMain, không dùng `java.time`.**

**Phát hiện bổ sung (P1)**

| ID | file:line | Vấn đề |
|---|---|---|
| F-01 | `OfflineSeeder.kt:211,171-177` | Bộ lịch lễ gắn với ngôn ngữ; đổi ngôn ngữ/vùng sau lần gieo đầu không đổi lịch (chỉ gieo khi bảng rỗng). |
| F-02 | `Localization.kt:8-10,29-32,74-77,303-308` | Enum 2 giá trị, ép EN cho mọi máy không-vi, không có fallback chain; nội dung dạng `titleVi/titleEn` không mở rộng được. |
| F-03 | `GiftIdeasSeed.kt:83,91,109`; `GiftCatalog.kt:16-20,45-54`; `GiftRanker.kt:15` | Khoá logic là chuỗi hiển thị tiếng Việt (category, tag, occasion); pack đa ngôn ngữ cần khoá ổn định tách khỏi nhãn. |
| F-04 | `GetPersonalizedGiftSuggestionsUseCase.kt:80-86,138`; `Entities.kt:100`; `GiftCatalog.kt:96-107` | Giá chỉ VND (4 bậc "k/tr"); cột `budgetMaxVnd` mang tên đơn vị. |
| F-05 | `CloudPresetFetcher.kt:198-241`; `scripts/seed_firestore.js:118-134` | Nội dung cloud không có version/ngôn ngữ/vùng/schema; `priceRange` và `suggestedOccasion` của món cloud là chữ tự do ("150.000đ - 350.000đ") nên không khớp bộ lọc giá/dịp; doc xấu vẫn được upsert (chỉ bỏ doc thiếu `title`). |
| F-06 | `OfflineSeeder.kt:17-45` | Sau lần cài đầu, khởi động chỉ gọi `fetchPresetAssetsFromFirestore`; gift/milestone cloud chỉ kéo lại khi một trong ba bảng rỗng hoặc người dùng bấm `syncCloudData` (`InLoveViewModel.kt:1740`). **Cập nhật nội dung không tới người dùng hiện hữu** (suy ra từ đọc mã). |
| F-07 | `LoveAnniversaryMilestoneScheduler.kt:46-59,96,109` | Hai bảng cột mốc song song, bảng cũ chỉ tiếng Việt và lệch 1 ngày; `×24h` còn sai khi qua DST (thị trường Âu/Mỹ). |
| F-08 | `ui/util/ProfileUtils.kt:13` và 14 vị trí khác (bảng ngay dưới) | `SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())` dùng để parse/format ngày LƯU. Trên máy có lịch hoặc chữ số khác (th-TH Phật lịch, ar/hi chữ số bản địa) có thể ra năm 2569 hoặc chữ số không phải ASCII, và hai người dùng có locale máy khác nhau có thể đọc sai ngày của nhau. **CHƯA kiểm chứng trên máy thật** (kế hoạch KMP cũng gắn UNVERIFIED). Sửa rẻ (S, U-07): `Locale.ROOT` cho định dạng lưu, `Locale.getDefault()` chỉ cho hiển thị. Nếu xác nhận thì nâng lên P0 (sai ngày). |

**Vị trí `SimpleDateFormat(` của F-08.** Đếm bằng grep trên cây làm việc ngày 2026-10-27: 15 lần trong 10 file của `app/src/main` (đường dẫn tính từ `app/src/main/java/com/example/`). 14 lần dùng `Locale.getDefault()`; chỉ `ui/util/ProfileUtils.kt:214` đã dùng `Locale.ROOT`. Chưa phân loại từng vị trí là lưu hay chỉ hiển thị (cột cuối ghi những gì thấy ở chính dòng đó); cách sửa và kiểm ở U-07.

| File | Dòng | Locale | Mẫu ở dòng đó |
|---|---|---|---|
| `ui/util/ProfileUtils.kt` | 13 | `getDefault()` | `"dd/MM/yyyy"` |
| `ui/util/ProfileUtils.kt` | 214 | `ROOT` | `pattern` (biến) |
| `alarm/AlarmNotificationScheduler.kt` | 81 | `getDefault()` | `fmt` (biến), `isLenient = false` |
| `alarm/AlarmNotificationScheduler.kt` | 503 | `getDefault()` | `"HH:mm - dd/MM/yyyy"` |
| `alarm/LoveAnniversaryMilestoneScheduler.kt` | 73 | `getDefault()` | `fmt` lần lượt `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy-MM-dd`, `d/M/yyyy`; `isLenient = false`; đọc ngày bắt đầu |
| `alarm/LoveAnniversaryMilestoneScheduler.kt` | 90 | `getDefault()` | `"dd/MM/yyyy"`, định dạng `formattedDate` |
| `ui/screens/AccountSecurityDialogs.kt` | 364 | `getDefault()` | `"HH:mm dd/MM/yyyy"` |
| `ui/screens/Dialogs.kt` | 1003 | `getDefault()` | `"dd/MM/yyyy"`, ngày hôm nay (`todayFormatted`) |
| `ui/screens/Dialogs.kt` | 2368 | `getDefault()` | `"dd/MM/yyyy"`, ngày hôm nay (`todayFormatted`) |
| `ui/screens/MemoriesGridScreen.kt` | 628 | `getDefault()` | `"dd/MM/yyyy"`, ngày hôm nay |
| `ui/components/AnniversaryMemoriesWidget.kt` | 858 | `getDefault()` | `"dd/MM/yyyy"`, ngày hôm nay |
| `ui/screens/PairingScreen.kt` | 143 | `getDefault()` | `"dd/MM/yyyy"`, ngày hôm nay (`todayFormatted`) |
| `ui/components/LoveDaysCalculatorWidget.kt` | 110 | `getDefault()` | `pattern` (biến) |
| `ui/components/Material3DatePicker.kt` | 99 | `getDefault()` | `pattern` (biến) |
| `ui/components/Material3DatePicker.kt` | 109 | `getDefault()` | `fmt` (biến) |

**P2/P3:** `ContentRepository` + `HolidayCalendarItem` chưa có cài đặt (F-09: dùng hoặc xoá, đừng để hai thiết kế); vùng và ngôn ngữ là hai trục rời, không suy ra từ locale/SIM (F-10); không dùng per-app language/`LocaleConfig` (F-11); `FontFamily.Default` và `lineHeight` cố định có thể cắt chữ Thái/Devanagari, chưa audit RTL (F-12, suy luận); dependency `firebase-ai` chưa dùng (F-13); ảnh hot-link (F-14); `badgeText: '98% Cặp đôi mê mẩn'` trong `scripts/seed_firestore.js:123` là số liệu xã hội bịa, rủi ro chính sách quảng cáo/Store (F-15, cần xác nhận pháp lý); bảng âm lịch hết 2028 và thiếu quy trình cập nhật hằng năm (F-16); `suggestedGiftCategory` của ngày lễ ("Lãng Mạn") không trùng khoá category của catalog ("Quà lãng mạn") nên dữ liệu chết hoặc nối sai (F-17); không có đường mua/tìm quà (F-18, là cơ hội chứ không phải lỗi).

**Rủi ro khi đổi.** Cờ yêu thích `isFavorited` được giữ theo `remoteId` (`InLoveDao.kt:73-85`) nên đổi khoá phải có bảng `legacyRemoteId` và test (`gift_reminders` không có khoá quà nên không bị ảnh hưởng, `data/model/Entities.kt:177-190`); golden iOS (GV-016/029/204) ghim hành vi hiện tại nên đổi `GiftRanker`/dịp phải sinh lại golden có kiểm soát; pack Firestore hỏng hoặc sai ngày lễ là lỗi mất uy tín nên cần validate phía client, `minAppVersion` và bản last-good; dịch máy dễ gợi quà sai văn hoá nên mỗi pack cần người bản địa duyệt.

## 4. Thị trường

**Ý chính.** Năm nhóm thị trường (4.1 Việt Nam, 4.2 Đông Á, 4.3 phương Tây, 4.4 Đông Nam Á và Ấn Độ, 4.5 Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS kèm Ba Lan và vùng Vịnh) được nghiên cứu song song, mỗi nhóm kèm một gói dữ liệu nháp. Độ sâu không đều: Việt Nam có nhiều nguồn nhất; Mỹ có khảo sát NRF đã mở trang gốc; Mexico, Peru, Chile, Colombia, Argentina, Ba Lan và Thổ Nhĩ Kỳ còn mỏng; UAE và Ai Cập chỉ có một mục kiêng kỵ chung; Ả Rập Xê Út và Kuwait có một dịp (tắt) và hai mục kiêng kỵ, không có quà. Mỗi mục có thêm khối "Cách InLove nên xử lý" gồm 48 điểm (7 + 9 + 9 + 13 + 10), được ánh xạ sang việc nâng cấp ở 6.6.1.

### 4.0 Cách đọc

- **Dữ liệu nháp.** Mỗi mục 4.1 đến 4.5 mở bằng dòng "Dữ liệu nháp" ghi số dịp, cột mốc, quà và kiêng kỵ của gói tương ứng (VN 15/11/32/15; EA 18/10/24/14; WEST 19/19/30/14; SEA_SA 19/7/28/13; LATAM_TR_CIS 16/8/28/14; cộng lại 87 dịp, 55 cột mốc, 142 quà, 70 kiêng kỵ, tức 354 bản ghi). Đây là hạt giống để người bản ngữ rà soát, chưa phải nội dung phát hành (mục 7).
- **Cột "Liên quan cặp đôi".** Là trường `coupleRelevance` từ 0 đến 3, số càng cao thì dịp càng quan trọng với cặp đôi; không có tiêu chí riêng cho từng mức (3: 28 dịp, 2: 31, 1: 25, 0: 3) và cột này độc lập với `giftExpectation` (mức kỳ vọng tặng quà: major, expected, small, none). Báo cáo thô của các nhóm nghiên cứu gọi cột này là "Kỳ vọng" (0-3, "độ bắt buộc" với cặp đôi); tài liệu đổi tên để khỏi lẫn với `giftExpectation`. Ví dụ đối chiếu Loy Krathong và White Day ở 5.1.
- **Nhãn ưu tiên của nhóm nghiên cứu.** Các nhóm nghiên cứu thị trường tự gắn P0/P1/P2 cho đề xuất của họ. Để không lẫn với thang P0-P3 ở mục 3, tài liệu đổi thành "Làm trước / Làm sau / Làm sau nữa" (4.3) và "(làm sau nữa)" (4.5), cùng từ vựng với 8.5.5. Thứ tự làm thật nằm ở mục 6.
- **Số khảo sát không so và không cộng.** Các khảo sát khác nhau về mẫu (toàn dân, người ăn mừng, người mua quà), về năm và về câu hỏi; số tiền của nước này không so được với nước khác và không nên cộng lại.
- **Nhãn tin cậy.** Các nhóm dùng nhãn H/M/L (cao/vừa/thấp) nhưng chú giải không giống nhau giữa năm báo cáo (7.5): `high` không có nghĩa là "hai nguồn khớp nhau", "nguồn chính thức" hay "người bản ngữ đã duyệt".
- **Cách InLove nên xử lý.** Cuối mỗi mục có một khối cùng tên, tổng cộng 48 điểm (4.1: 7; 4.2: 9; 4.3: 9; 4.4: 13; 4.5: 10). Bảng ánh xạ từng điểm sang mã việc nâng cấp U-nn nằm ở 6.6.1; điểm nào chưa có việc riêng thì ghi rõ "Không làm" hoặc "hoãn". Thị trường nên mở tiếp theo và cách xếp hạng nằm ở mục 8; quyết định liên quan là QĐ-1 (mục 9).

### 4.1 Việt Nam (thị trường gốc)

**Dữ liệu nháp:** 15 dịp, 11 cột mốc, 32 quà, 15 kiêng kỵ (`pack = VN` trong file JSON nháp). Đây là thị trường có nhiều nguồn nhất, nhưng **không có khảo sát quốc gia 2025-26 về chi tiêu quà đôi**; mọi ngân sách bên dưới là ước lượng bậc độ lớn từ giá lẻ báo chí (tin cậy thấp, trừ Valentine ở mức vừa).

**Tín hiệu thị trường (định tính, có nguồn báo chí)**

- **Valentine 2026 rơi vào 27 Tết** nên thị trường "kém sôi động": hồng 30-40k/bông, hộp chocolate 95-300k, mỹ phẩm và trang sức được tìm nhiều hơn, một số cặp chọn đi chơi xuân hoặc chuyển khoản thay vì quà (baonghean.vn). **Valentine 2027 (14/2/2027, Chủ nhật) rơi vào mùng 9 Tết:** đang nghỉ lễ, hợp với trải nghiệm hơn quà vật chất (suy luận, tin cậy thấp).
- **Valentine 2025** (vneconomy.vn): hồng Đà Lạt 15-20k/bông, hồng nhập 50-60k, chocolate hộp nhỏ 150-500k, cao cấp 800k-1M, workshop làm quà 100-500k; xu hướng tulip, chocolate nội, đồ sưu tầm (Labubu, Baby Three), cá nhân hóa, spa đôi, dinner cruise sông Hồng.
- **20/10/2026** (tienphong.vn, bài đăng 19/10/2026): quà cá nhân hóa 100-400k, hoa len/kẽm 90-250k, hoa sáp 300-500k. Người trẻ chuộng quà **thiết thực, bền** (mỹ phẩm, nước hoa, máy massage mini, bình giữ nhiệt, nến thơm) và chọn theo nhu cầu người nhận hơn là theo trend.
- **Kênh mua:** 6 tuần trước Tết 2026, người Việt chi khoảng 67.700 tỷ đồng trên Shopee và TikTok Shop (doanhnhan.baophapluat.vn, từ đoạn trích tìm kiếm, tin cậy vừa). Điều này khớp với đề xuất "tìm quà" ở U-35 (mục 6.4), nhưng **chưa có bằng chứng** rằng đây là kênh mua quà đôi.
- **Không dùng được:** "Statista 42% / 28 USD" và "Picodi 31% phụ nữ coi rượu là món quà tệ nhất" chỉ có trong tóm tắt tìm kiếm, không mở được nguồn gốc nên **không đưa vào dữ liệu**.

**Lịch dịp đôi (liên quan cặp đôi 0-3; ngân sách VND ước lượng thấp/vừa/cao; ngày dương tính cho 10/2026-10/2027)**

| Dịp | Quy tắc ngày | Liên quan cặp đôi | Ngân sách | Báo trước | Bật mặc định |
|---|---|---|---|---|---|
| Valentine 14/2 | dương cố định | 3 | 200k / 900k / 3M | 7 ngày (đặt bàn 10-14) | Có |
| Quốc tế Phụ nữ 8/3 | dương cố định | 2 | 150k / 500k / 1,5M | 5 | Có |
| Phụ nữ Việt Nam 20/10 | dương cố định | 3 | 150k / 500k / 1,5M | 5 | Có |
| Đêm Noel 24/12 | dương cố định | 3 (nhận định) | 100k / 500k / 2M | 7 | Có |
| Tết Nguyên Đán (biếu nhà người yêu) | âm 1/1; mùng 1 năm 2027 = 6/2/2027 | 2 | 300k / 1M / 3M | 21 | Có |
| Đi chơi xuân đầu năm | sau mùng 2 Tết | 2 | 500k / 2M / 6M | 21 (đặt phòng) | Không |
| Valentine Trắng 14/3; Ngày 520 (20/5); Thất Tịch (âm 7/7) | dương/âm | 1-2 | 50k-300k trung bình | 3 | Không |
| Giao thừa dương 31/12; Trung Thu (âm 15/8) | dương/âm | 1-2 | 100k-300k trung bình | 7 | Không |
| Rằm tháng Giêng, Vu Lan | âm | 0-1 | không có | 0 | Không |
| Sinh nhật, kỷ niệm ngày yêu, ngày cưới | người dùng nhập | 3 | 200k-1,5M trung bình | 10-14 | Có |
| Ra mắt gia đình người yêu | người dùng nhập | 3 | 500k / 1,5M / 5M | 7 | Không |

Ngày dương và thứ trong tuần là tính số học (tin cậy cao). Ngày âm 2027 do agent tự tính bằng thuật toán Hồ Ngọc Đức và đối chiếu bảng HKO (xem mục 7); Tết 6/2/2027 và Trung Thu 15/9/2027 còn có nguồn web. Lịch nghỉ Tết 2027 chính thức **chưa chốt** trong các nguồn đã đọc.

**Thói quen đếm và kỷ niệm**

- Mốc **1 năm** là mốc quan trọng nhất, quà thường là đồ đôi (áo, nhẫn, vòng, đồng hồ đôi), lịch ảnh, album, chuyến đi (shopee.vn/blog, có yếu tố thương mại).
- **"1001 ngày yêu"** là mốc dân gian có thật (một clip viral 2016, vietnamnet.vn) nhưng không có số liệu độ phổ biến; **mốc tròn tháng và phong tục 100 ngày không tìm được bằng chứng** nên chỉ nên là tùy chọn mặc định tắt.
- Ngày cưới có tên theo chất liệu (Giấy 1, Bông 2, Da 3, Gỗ 5, Đồng 8, Thiếc 10, Thủy tinh 15, Sứ 20, Bạc 25, Ngọc trai 30, Vàng 50, Kim cương 60); các nguồn không thống nhất hoàn toàn (voh.com.vn).
- Đối thủ tại VN (Been Love Memory, Been Together, Lovedays, LoveStorii...): chỉ Been Together 2021 nêu mốc ngày cụ thể; **chưa thấy đối thủ nào gợi ý quà theo dịp và ngân sách VND** (chưa mở trang cửa hàng từng app, nên đây là khoảng trống có điều kiện).

**Kiêng kỵ (tín ngưỡng dân gian từ blog bán hàng, KHÔNG phải số liệu; chỉ cảnh báo mềm, không chặn)**

- Ly/cốc đôi (đồng âm "ly" là chia ly), đồng hồ (hết thời gian), khăn tay/khăn quàng (nước mắt), giày dép (bỏ đi), dao kéo (cắt đứt): tin cậy vừa; ô/dù: thấp.
- Tiền mặt hoặc trang sức bạc cho người yêu (tình phai, mua tình): vừa; nên lì xì cho gia đình người yêu thay vì tiền cho người yêu.
- Hoa cúc trắng (tang lễ), hoa vàng, số bông lẻ: tin cậy thấp (nguồn là blog bán hoa).
- Biếu Tết: tránh giấy gói đen hoặc xanh đậm, mực, hạt tiêu, nước, lửa.
- **Tác động lên seed hiện tại:** "Bộ Cốc Đôi In Tên", "Đồng Hồ Đôi", "Vòng Tay Khắc Tên Đôi" nằm trong nhóm kiêng nhẹ. Đề xuất: gắn `tabooRefs` và hiện một dòng nhỏ "một số người ngại món này", **không xoá quà**. Không có nguồn so sánh Bắc/Nam.

**Cách InLove nên xử lý (chi tiết ưu tiên ở mục 6)**

1. **Sửa Rằm tháng Giêng 2027** (repo 2027-02-19, đúng là 2027-02-20) và gia hạn bảng âm lịch (Vu Lan, Thất Tịch, Rằm tháng 10 hiện hết hạn hoặc chưa có).
2. **Đêm Noel 24/12** chỉ nằm trong `WesternHolidays` nên người dùng bản VN không thấy: thêm vào bộ lịch VN, bật mặc định cùng Valentine, 8/3, 20/10, Tết, sinh nhật, kỷ niệm.
3. **Báo trước dài hơn cho dịp phải đặt chỗ hoặc làm đồ cá nhân hóa** (Valentine, Noel, 8/3, 20/10: 7-14 ngày; Tết: 21 ngày) vì giá tăng 3-4 lần ở đỉnh, nhà hàng kín chỗ, đồ cá nhân hóa mất 5-10 ngày (báo chí; "7-14 ngày" là khuyến nghị thận trọng, chưa có dữ liệu thời gian đặt bàn).
4. **Giữ nguyên bốn bậc giá hiện tại** (<200k, 200-500k, 500k-1tr, >1tr): khớp mặt bằng giá VN. Chỉ bổ sung khoảng giá theo dịp.
5. **Bổ sung danh mục và nhãn:** "Làm đẹp & Mỹ phẩm", "Sưu tầm & Trend", "Quà ra mắt & Biếu gia đình", "Sức khỏe & Chăm sóc"; nhãn "Biếu bố mẹ", "Cá nhân hóa", "Thiết thực", "Đặt trước". 32 quà nháp phủ hoa tulip/len/sáp, túi mù, nến thơm, workshop, dinner cruise, giỏ Tết, trà, yến, bàn chải điện.
6. **Quà số miễn phí** (video hành trình yêu, thư tay, ảnh Polaroid) cho ngân sách thấp, nối với kho ảnh trong app (mức quan tâm: thấp, chưa kiểm chứng).
7. **Không đổi copy 8/3 và 20/10 thành "chỉ cho mẹ"**: cả hai dịp đều có người yêu tặng quà, dịp 20/10 thiên về mẹ, vợ, con gái.

**Chưa kiểm chứng:** ngân sách các dịp; "tròn tháng" và phong tục 100 ngày; ngân sách biếu bố mẹ người yêu; thời gian đặt bàn; khác biệt kiêng kỵ Bắc/Nam; giá quà lẻ (nước hoa, son, bàn chải điện, du thuyền) phần lớn tin cậy thấp. Nhiều trang nguồn là blog bán hàng (Shopee, mytour, toplist).

### 4.2 Đông Á (Hàn Quốc, Nhật Bản, Trung Quốc, Đài Loan, Hồng Kông)

**Dữ liệu nháp:** 18 dịp, 10 cột mốc, 24 quà, 14 kiêng kỵ (`pack = EA`). **Lưu ý độ tin cậy:** nhiều khảo sát không ghi năm hoặc cỡ mẫu; **không có nguồn mở được cho ngân sách TWD** (mọi số TWD là "from memory, unverified" hoặc để trống); không tìm được khảo sát "tỷ lệ cặp ăn mừng 100/200/300 ngày". Phần "ngân sách điển hình" trong bảng dưới là khoảng thận trọng cho một quà, không phải trung bình thống kê, trừ khi ghi nguồn.

**Tín hiệu thị trường (đã đối chiếu nguồn; nhãn H/M/L = cao/vừa/thấp)**

- **Hàn Quốc.** Embrain (n=1.000, tuổi 13-59, công bố 11/2024, Korea Herald): dịp quan trọng nhất là **Pepero Day 11/11 (80,7%)**, rồi Valentine (74,1%), White Day (68,9%) [H]. KakaoTalk Gift 2025: khoảng 189,5 triệu giao dịch trong năm (khoảng 540.000/ngày), ngày bận nhất là Pepero Day, rồi Valentine, Teacher's Day, White Day; thẻ Starbucks phổ biến nhất [M, báo chí tường thuật số của Kakao]. Một khảo sát Duo (năm không rõ): khoảng 60% cặp 20-30 tuổi tặng quà "mức 3 vạn won", trên 70% cặp có lo các "day" [M]. Nữ muốn **nhận** tiền mặt xếp số 1 (PMI, năm không rõ) [L-M].
- **Nhật Bản.** Intage (đầu 2026, khoảng 2.500 phụ nữ): ngân sách Valentine trung bình 4.943 yen (chủ yếu tặng gia đình hoặc chính mình), **42,8% không định tặng chocolate** (+4,0 điểm so với năm trước) [H cho số]. Matsuya Ginza (12/2025, 1.328 người, mẫu không đại diện): honmei-choco trung bình 5.573 yen, bằng nửa mức tự thưởng 10.662 yen. White Day 2026: chỉ 9,8% "có kế hoạch" (nguồn thứ cấp, chưa mở trực tiếp) [L].
- **Trung Quốc.** Hơn 30% nam giới **không tặng quà ngày 520** từ 2024 (Jing Daily, trích lại) [M]. WeChat nâng hạn mức lì xì cá nhân 200 → 520 NDT trong một ngày vào Thất Tịch 29/08/2025 [H]. Lì xì 520/1314 thường bị coi là tặng cho, khó đòi lại sau chia tay [M].
- **Đài Loan.** yes123 (28/08/2025, nhân viên văn phòng, cỡ mẫu không nêu): 77,7% có kế hoạch Thất Tịch. Nam muốn đồ tự làm 37,2% và thư tay 34,7%; nữ muốn tiền mặt/lì xì 37,7% và đồ tự làm 36,4%. Quà "dễ dẫm mìn": nam ghét gấu bông, hoa, nhẫn kim cương; nữ ghét chocolate, hoa, gấu bông [M-H]. Chia đôi hóa đơn phổ biến (50,6% từng làm), nên không mặc định "nam trả".
- **Hồng Kông.** HK Romance Dating 2026 (n=517): 54% nữ muốn chi dưới HK$500; 36% nam chi HK$501-1.000; năm 2025 có 27% nam không định mua quà [M]. Bữa tối ngoài và du lịch ngắn qua đại lục là lựa chọn chính, ngân sách đang giảm.
- **Xu hướng chung:** ngày lễ thương mại hóa mờ dần, quà thực dụng/tự làm/trải nghiệm tăng, ngân sách giảm (JP, CN, HK). **Hàn Quốc vẫn mạnh nhất về văn hóa "day" và tặng quà qua app.**

**Lịch dịp đôi (liên quan cặp đôi 0-3)**

| Dịp | Nước | Quy tắc ngày | Liên quan cặp đôi | Ngân sách điển hình | Báo trước | Bật mặc định |
|---|---|---|---|---|---|---|
| Valentine, White Day | KR | 14/2, 14/3 | 3 | 20-50k KRW; White Day 30-100k KRW | 7 | Có |
| Pepero Day | KR | 11/11 | 2 (rất phổ biến nhưng nhỏ) | 3-30k KRW | 5 | Có |
| Giáng sinh | KR | 24-25/12 | 3 | 50-200k KRW | 21 (đặt chỗ) | Có |
| Rose, Silver/Ring, Black Day | KR | 14/5, 14/7, 14/4 | 0-1 | nhỏ | 3-14 | Không |
| Valentine (honmei), White Day | JP | 14/2, 14/3 | 2 | 2-6k JPY; White Day 3-5k JPY | 14 | Có |
| **Giáng sinh Eve** | JP | 24/12 | **3** (là "Valentine thứ hai") | 5-30k JPY | **30** (nhà hàng, khách sạn) | Có |
| Tanabata | JP | 7/7 dương | 1 | 0-5k JPY | 3 | Không |
| 520 | CN | 20/5 | 2 | lì xì 520/1.314 NDT; quà 300-1.500 NDT | 7 | Có |
| **Thất Tịch** | CN, TW | **âm lịch 7/7** | 3 | 500-2.000 NDT | 10 | Có |
| 214, 314 | CN, TW, HK | 14/2, 14/3 | 2 và 1 | HK$500-2.000 (214) | 7 và 3 | 214 Có; 314 Không |
| Bình An Đêm (táo) | CN, TW, HK | 24/12 | 2 | 10-100 NDT/táo gói | 2 | Có (HK, TW) |
| Nguyên Tiêu | CN, TW, HK | âm 1/15 | 1 | nhỏ (từ trí nhớ, chưa kiểm chứng) | 3 | Không |
| Quang Côn Tiết 11/11 | CN | 11/11 | 0-1 (không phải ngày đôi) | không có | 0 | Không |

**Thói quen đếm và kỷ niệm**

- **Hàn Quốc:** mốc **22 ngày** ("투투데이", chủ yếu học sinh, sinh viên), **100 ngày** (rất phổ biến, "마의 100일"), 200, 300, 1 năm, 1000 ngày [M]. Nghi thức "14 hằng tháng" (Diary, Silver, Photo, Hug...) phần lớn đã mờ, chỉ còn Valentine, White Day, Pepero.
- **Nhật Bản:** nhiều cặp ăn **mốc theo tháng** (1 tháng, nửa năm, 1 năm); kiểu "100 ngày" không phải chuẩn ở JP nên ưu tiên tháng và năm [L-M, không có khảo sát tỷ lệ].
- **Trung Quốc:** app đếm ngày thường có preset **100 ngày, 1 năm, 520 ngày, 1314 ngày** [M]. Thất Tịch và Nguyên Tiêu theo âm lịch; ngày yêu thường tính dương lịch (từ trí nhớ, chưa kiểm chứng).
- **Đài Loan, Hồng Kông:** dùng bộ ngày của Trung Quốc (214, Thất Tịch âm lịch, 520) cộng Giáng sinh [L].
- **Mốc InLove đang thiếu:** 22, 520, 1314 ngày và mốc tháng 1-12 (xem `LoveMilestoneRules.kt`).

**Thói quen quà tặng**

- **Hàn Quốc:** kênh KakaoTalk Gift (gifticon, thẻ quà), Olive Young (mỹ phẩm), Coupang [kênh: vừa; mức độ phổ biến từng kênh: chưa kiểm chứng]. Quà: thẻ Starbucks, thẻ quà làm đẹp, son bóng, bình giữ nhiệt, nước hoa.
- **Nhật Bản:** chocolate thương hiệu ở depachika; **tự thưởng (jibun-choco) đang vượt honmei** [M-H]; White Day 3.000-5.000 yen là phổ biến nhất; Giáng sinh Eve đặt bàn nhiều tuần trước.
- **Trung Quốc:** son môi, hoa hồng, trang sức, hộp quà xa xỉ vẫn là truyền thống nhưng sức nóng giảm [M]; lì xì WeChat 520/1314.
- **Đài Loan:** đồ tự làm, thư tay, du lịch; nam ít thích gấu bông, hoa, nhẫn kim cương.
- **Hồng Kông:** ăn tối ngoài, du lịch ngắn, ngân sách thắt chặt.

**Kiêng kỵ (blog, du lịch, tín ngưỡng; chỉ cảnh báo mềm)**

- **CN, TW, HK:** đồng hồ (送钟 đồng âm 送终), lê (离), ô (散), giày, hoa trắng hoặc cúc vàng (tang), số 4 (死) [M, nguồn không học thuật].
- **KR:** tặng giày cho người yêu = "chạy mất" (mê tín, hay nói đùa); viết tên bằng mực đỏ (chỉ dành cho người đã mất) [M]. **Số 4 ở KR: các nguồn tìm được không nhắc**, từ trí nhớ, chưa kiểm chứng.
- **JP:** số 4 (shi) và 9 (ku), lược (kushi); không gắn nhãn "giri" cho người yêu (từ trí nhớ, chưa kiểm chứng).
- **Tiền mặt** là quà mong muốn của nữ KR và TW nhưng nam TW không thích (30,5%): đừng đặt "tiền mặt" làm quà mặc định.
- **Tác động lên seed:** "Đồng Hồ Đôi" (Minimalist Couple Watch Set) cần cờ nhạy cảm theo vùng CN/TW/HK.

**Đối thủ trong khu vực.** Between (VCNC, Hàn) khoảng 10 triệu tải tính đến cuối 2014 (khoảng 50% từ nước ngoài, chủ yếu JP/TW/ĐNA), vẫn là app được nêu đầu tiên ở Nhật cùng Pairy và "The Couple" [M cho lịch sử; L cho hiện tại, không có số 2025]. Trung Quốc có ngách "恋爱纪念日" dạng widget, phân mảnh, không có kẻ thống trị rõ (một app Android chỉ 3.170 tải) [L-M]. **Chưa thấy app nào gắn "gợi ý quà theo dịp địa phương + nhắc theo lead time"** (chỉ một vòng tìm kiếm mỗi nước, chưa kiểm chứng thấu đáo).

**Cách InLove nên xử lý (chi tiết ở mục 6)**

1. **Thêm mốc 22, 520, 1314 ngày và mốc tháng 1-12;** mốc 22 chỉ bật mặc định khi region = KR.
2. **Thêm dịp bật mặc định theo vùng:** KR (White Day, Pepero Day, Giáng sinh), JP (Giáng sinh Eve, White Day), CN/TW/HK (214, 520, Thất Tịch âm lịch). Valentine và Giáng sinh Eve đã có trong `WesternHolidays`: chỉ cần thêm biến thể theo vùng, `whoGives` và lead time.
3. **Cờ nhạy cảm theo vùng** cho đồng hồ, giày, ô, lê, hoa trắng; hiển thị cảnh báo mềm cho "Đồng Hồ Đôi" ở CN/TW/HK.
4. **Lead time theo dịp:** JP Giáng sinh Eve 30 ngày, KR White Day và Valentine 7 ngày, CN 520 7 ngày, Thất Tịch 10 ngày.
5. **Thang giá theo tiền địa phương** (KRW/JPY/CNY/TWD/HKD) bằng `priceTier` 1-4 + `tierBands`; thêm danh mục "Thẻ quà & Voucher", "Quà số/Lì xì", "Quà đồ ăn mùa lễ".
6. **Bỏ giả định "nam tặng nữ"** và có lựa chọn "chúng mình không tặng quà lễ" (hơn 30% nam CN bỏ 520; Đài Loan chia đôi hóa đơn phổ biến).
7. **Region mặc định suy từ locale** (ko, ja, zh-TW, zh-HK, zh-CN) nhưng cho người dùng sửa; chỉ bật dịp có liên quan cặp đôi ≥ 2.
8. **Nền tảng:** nếu nhắm Nhật thì iOS là điều kiện (hai nguồn thị phần mâu thuẫn: StatCounter iOS khoảng 60-64% theo traffic web, Statista Android 58,1%); Hàn Quốc Android khoảng 62-66%; Trung Quốc không dùng được Google Play và Firebase (từ trí nhớ), nên nằm ngoài phạm vi hiện tại.
9. **Hoãn:** Black Day, Rose Day, Silver Day, Quang Côn Tiết (không phải dịp đôi), 314.

**Chưa kiểm chứng:** ngân sách TWD; tỷ lệ cặp lo 100/200/300 ngày; thị phần iOS/Android của TW, HK; xếp hạng app hiện tại; kênh mua nào thực sự dùng cho quà đôi (chỉ Kakao Gift có số). Khảo sát Duo, PMI, Smartlog, Showcase, Hunel, iiMedia, yes123 thiếu năm hoặc cỡ mẫu. Số OS đo traffic web chứ không đo số người dùng. Ngày âm lịch cụ thể 2026 trở đi do agent âm lịch tính (mục 7).

### 4.3 Phương Tây (Mỹ, Canada, Anh, Úc, Đức, Pháp, Tây Ban Nha, Ý)

**Dữ liệu nháp:** 19 dịp, 19 cột mốc, 30 quà, 14 kiêng kỵ (`pack = WEST` trong file JSON nháp). Cả 19 cột mốc đều là mốc **năm cưới** (có tên theo chất liệu); không có mốc đếm ngày vì không tìm được bằng chứng người phương Tây đếm "100 ngày". Chưa phủ New Zealand, Hà Lan, Bắc Âu (không tìm được nguồn); bản ghi "Úc/NZ" đã đổi thành chỉ Úc, kèm ghi chú NZ. **Lưu ý đọc số:** các khảo sát dưới đây khác nhau về mẫu (toàn dân, người ăn mừng, người mua quà) và về câu hỏi, nên **không so và không cộng số tiền giữa các nước**.

**Tín hiệu thị trường (nhãn tin cậy: cao / vừa / thấp)**

- **Mỹ** (NRF/Prosper, 2-8/1/2026, n=7.791): 55% ăn mừng Valentine, 83% trong số đó mua quà cho người yêu; chi trung bình $199,78 mỗi người mua (2025: $188,81), tổng $29,1 tỷ, mức kỷ lục. Theo hạng mục: kẹo 56%, hoa 41%, hẹn ngoài 39% ($6,3 tỷ), trang sức 25% ($7,0 tỷ). Tin cậy cao (đã mở trang NRF).
- **Mỹ, xu hướng "trải nghiệm hơn vật"** (nguồn thứ cấp): Trustpilot 1/2025 (n=1.115, qua Fortune) 42% định đi nhà hàng so với 28% tặng quà; Savings.com 1/2026 thời gian ở nhà bên nhau 48%, trải nghiệm 43%; YPulse 2/2026 có 64% người 18-36 tuổi coi Valentine là "overrated". Tin cậy vừa.
- **Anh:** £61 (người ăn mừng, Confused.com 9/2/2026), £49 (toàn dân, Finder), £107,52 (người mua quà, Parcelhero); 32% chi 0 đồng. Ba mẫu khác nhau nên không so được. Tin cậy vừa.
- **Pháp** (YouGov 2025, n=1.032): 36% ăn mừng, ngân sách 154 EUR; hoa 25%, nước hoa 21%, socola 17%, trang sức 16%; nhà hàng 45% (giảm từ 52%). **Ý** (YouGov 2026, n=1.039): 31% ăn mừng, 49% không làm gì, ngân sách phổ biến 70-149 EUR; Confesercenti-Ipsos 2026 (qua LaPresse) cho 58% và trung bình 152 EUR, lệch xa YouGov vì mẫu khác. Tin cậy cao (YouGov), vừa (Ipsos).
- **Đức, Tây Ban Nha yếu:** HDE/IFH Köln 1/2026 (n=500) chỉ 20% định mua quà Valentine, giảm 8 điểm, và 14/2/2026 trùng thứ Bảy Karneval (một năm đơn lẻ). Tây Ban Nha (Gelt 2/2026, n>1.200, **fetch lỗi, chỉ có tóm tắt tìm kiếm**): 57% mua quà nhỏ, hơn nửa dưới 50 EUR, đồ ngọt và trải nghiệm dẫn đầu. Tin cậy vừa (DE), vừa-thấp (ES).
- **Úc, Canada:** Roy Morgan 2/2026: 12% người lớn Úc định mua quà, trung bình A$152 (nam A$171, nữ A$123). CIBC/Ipsos 2/2026: 56% người Canada định chi, trung bình C$179, nhóm 18-34 tuổi C$224, 8/10 ưu tiên cửa hàng địa phương. Hai con số 12% và 56% **không so được** (câu hỏi khác). Tin cậy vừa.
- **Đặt chỗ nhà hàng** (OpenTable 1/2026): 86% đặt trước ít nhất 1 ngày, 56% trước một tuần, 24% trong vòng 48 giờ; 84% là bàn hai người; lượt đặt Valentine tăng 43% (2024 sang 2025). Ủng hộ mốc nhắc trước 14-21 ngày. Tin cậy vừa.
- **Ý (Confcommercio-SWG, qua AGI):** 76% muốn nhất một "cuối tuần văn hóa", 71% bữa tối, 65% vé biểu diễn; 32% muốn gói trọn gói, 24% muốn được hủy miễn phí sát ngày. Tin cậy vừa.
- **Thú cưng:** 35% người mua Valentine ở Mỹ mua quà cho thú cưng, kỷ lục $2,1 tỷ (NRF). Tin cậy cao.
- **Thiết bị (StatCounter):** iOS chiếm Mỹ 58,35% (6/2026), Anh 51,4% (8/2026), Úc khoảng 64-65%; **Pháp chỉ 36,7%** (Android 63,3%). Canada, Đức, Tây Ban Nha, Ý, NZ: không tìm thấy. Tin cậy vừa. Hệ quả: kế hoạch KMP/iOS có giá trị cao nhất ở phương Tây (Mỹ, Anh, Úc); Pháp là ngoại lệ.

**Lịch dịp đôi (liên quan cặp đôi 0-3; số tiền chỉ ghi khi đã có nguồn, "-" là không có số đã kiểm chứng)**

| Dịp | Nước | Quy tắc ngày | Liên quan cặp đôi | Số tiền đã nguồn | Báo trước | Bật mặc định |
|---|---|---|---|---|---|---|
| Valentine | Mỹ, Canada | 14/2 | 3 | ~$200 (Mỹ), C$179 (CA) | 21 ngày | Có |
| Valentine | Anh | 14/2 | 3 | £49-61 (mẫu khác nhau) | 21 | Có |
| Valentine | Úc | 14/2 | 2 | A$152 | 14 | Có |
| Saint-Valentin / San Valentino | Pháp, Ý | 14/2 | 2 | 154 EUR (FR); 70-149 EUR phổ biến (IT) | 14 | Có |
| Valentinstag / San Valentín | Đức, Tây Ban Nha | 14/2 | 1 | ES phần lớn dưới 50 EUR; DE: không có số | 7 | Không (*) |
| Giáng Sinh | tất cả | 25/12 | 2 | quà cho bạn đời ~£106 (Anh, không rõ năm, thấp) | 28 | Có |
| Giao thừa dương lịch | tất cả | 31/12 | 2 | - | 30 (đặt chỗ) | Có |
| Kỷ niệm ngày yêu | tất cả | người dùng nhập | 3 | - | 14 | Có |
| Kỷ niệm ngày cưới | tất cả | ngày cưới | 3 | - | 21 | Có |
| Galentine's Day | Mỹ, Canada, Anh, Úc | 13/2 | 1 | - | 7 (đặt bàn) | Không |
| Sant Jordi (hoa hồng và sách) | Tây Ban Nha (Catalonia) | 23/4 | 3 | - | 3 | Không (bật theo vùng) |
| Sweetest Day | Mỹ (Đông Bắc, vùng Great Lakes) | thứ Bảy thứ 3 tháng 10 | 1 | - | 3 | Không |
| National Boyfriend Day / Girlfriend Day | Mỹ | 3/10 và 1/8 | 1 | - | 3 | Không (ngày marketing, thấp) |
| Monthiversary | Mỹ, Canada, Anh, Úc | hằng tháng | 1 | - | 1 | Không |
| Easter | tất cả | Computus | 1 | - | 7 | Không |
| Mothering Sunday | Anh | Easter trừ 21 ngày | 1 | - | 10 | Không |
| Reyes Magos / Epifania | Tây Ban Nha, Ý | 6/1 | 1 | - | 14 | Không |

(*) Báo cáo gốc ghi "Có (nhẹ)" cho Đức/Tây Ban Nha; đã sửa thành "Không" ở bước gộp vì quy tắc `defaultOn` chỉ bật khi liên quan cặp đôi ≥ 2 (một trong 12 sửa lỗi của bước gộp, xem mục 7).

Ngày dương và thứ trong tuần là tính số học (tin cậy cao): 14/2/2027 là **Chủ nhật**; Sweetest Day 2026 rơi 17/10/2026 (đã qua), 2027 rơi 16/10/2027; Thanksgiving Mỹ 2026 là 26/11/2026. **Easter 2027 = 28/3/2027 và Mothering Sunday 2027 = 7/3/2027:** đã chạy lại thuật toán Computus (Anonymous Gregorian) cho 2024-2030, khớp 7/7 năm với `dateutil.easter` và với bảng ngày Easter đã biết; golden test thật vẫn phải lấy từ một bảng đã xuất bản độc lập. Quy tắc Mothering Sunday = Easter − 21 ngày là kiến thức chung (chưa có nguồn), báo cáo thị trường gắn "unverified" và nguồn chưa được mở.

**Hiện trạng trong app (người dùng chọn tiếng Anh, ở bất cứ nước nào, nhận bộ lịch này)**

- `data/seed/WesternHolidays.kt` có 9 ngày cố định (New Year's Day, Valentine, St. Patrick, National Boyfriend Day, Independence Day, Halloween, Đêm Giáng Sinh, Giáng Sinh, Giao thừa dương lịch) và 4 ngày theo quy tắc (Mother's Day = Chủ nhật thứ 2 tháng 5, Father's Day = Chủ nhật thứ 3 tháng 6, Thanksgiving = thứ Năm thứ 4 tháng 11, Sweetest Day). Chủ nhật thứ 2 tháng 5 và Thanksgiving là quy tắc Mỹ; **Anh dùng Mothering Sunday**, còn Canada, Úc và các nước châu Âu có ngày khác (kiến thức chung, chưa kiểm chứng trong đợt này), nên các lễ này sai ngày với người dùng ngoài Mỹ.
- **Easter bị bỏ có chủ đích**: chú thích đầu file ghi chưa kiểm chứng được Computus bằng test. Kết quả đối chiếu ở trên gỡ được lý do đó, miễn là có golden test.
- Chỉ 6 trong 13 lễ gắn `isCoupleRelevant=true` (Valentine, Boyfriend Day, Đêm Giáng Sinh, Giáng Sinh, Giao thừa dương lịch, Sweetest Day), nhưng theo F04 ở mục 3.2 **mọi** lễ gieo đều bật nhắc trước 3 ngày. Người dùng EN ở Hàn, Nhật, Thái, Việt Nam vẫn nhận nhắc "Independence Day", "Thanksgiving", "St. Patrick" bằng giọng cặp đôi. Đây là vấn đề dữ liệu theo vùng (F08 ở mục 3.2, F-01 ở mục 3.3), không riêng gì Tây.
- National Boyfriend Day đang là lễ cặp đôi bật sẵn nhưng là ngày marketing, không có nguồn cho độ phổ biến.

**Thói quen đếm và kỷ niệm**

- **Cặp chưa cưới: không có chuẩn chung.** Mỗi cặp chọn ngày hẹn đầu tiên hoặc ngày "chính thức" (Elite Daily, ý kiến chuyên gia, thấp). YouGov Úc 2024: 24% sẽ ăn mừng Valentine với người mới hẹn dưới 1 tháng (vừa; phần còn lại của phân bố không đưa vào).
- **Monthiversary: không có khảo sát;** bình luận cho rằng ăn mừng hằng tháng dễ gây áp lực, thường chỉ đánh dấu 6 tháng và 1 năm. Giữ là tùy chọn, tắt mặc định (thấp).
- **"100 ngày" không phải thói quen phương Tây;** dùng mốc theo năm: 1, 5, 10, 25... (kinh nghiệm chung, chưa kiểm chứng).
- **Cặp đã cưới: bảng chủ đề theo năm.** Danh sách truyền thống gốc Victoria (Anh); danh sách hiện đại do hiệp hội kim hoàn Mỹ lập năm 1947 (nguồn thấp). Năm 25 = bạc, 50 = vàng ở cả Mỹ và Anh; năm 60 = kim cương (một số truyền thống để 75). **Các nguồn lệch nhau ở năm 1, 2, 4, 6, 8, 9, 14, 18, 24**, nên chỉ hiện làm gợi ý và gắn nhãn "truyền thống / hiện đại". Tin cậy vừa cho năm 1-10, 15, 20, 25 trở lên; thấp cho 11-14, 16-19, 21-24. Đức, Pháp, Tây Ban Nha, Ý cũng gọi 25 năm là "bạc", 50 năm là "vàng" (từ trí nhớ, chưa kiểm chứng).

**Thói quen quà tặng**

- Theo tiền: trang sức (Mỹ $7,0 tỷ) và bữa tối ($6,3 tỷ). Theo số người: kẹo, thiệp, hoa. Pháp: hoa, nước hoa, socola. Ý: bữa tối đứng đầu, cuối tuần đi chơi 45%, spa 29%, massage đôi 18%. Đức: hoa và cây, đồ ăn, voucher. Tây Ban Nha: đồ ngọt và trải nghiệm.
- **Cá nhân hóa** (tên, ngày, tọa độ, tranh vẽ, bộ DIY) là chủ đề nổi trội trong blog người bán; không có số bán hàng (thấp).
- Tặng lại quà đã nhận (regift) là điều tối kỵ với 25% người được hỏi (ConsumerAffairs 2025, vừa).
- **Ngôn ngữ tình yêu:** tổng quan thứ cấp (Impett, Park, Muise 2023-2024) không thấy bằng chứng ổn định rằng "khớp ngôn ngữ" làm tăng hài lòng; chỉ nên là thẻ gợi ý mềm (vừa; bài gốc PsychologicalScience fetch lỗi, chỉ có tóm tắt).
- 30 quà nháp phủ bữa tối đặt trước, cuối tuần văn hóa, spa đôi, vé biểu diễn, trang sức cá nhân hóa, photo book, lớp làm gốm, bộ thư "Open when...", afternoon tea (Anh), sách kèm hoa hồng (Sant Jordi), chợ Giáng Sinh và lịch Advent, quà theo năm cưới (giấy, bông, da, gỗ, 10 năm, bạc). Báo cáo cho biết không trùng với 20 seed hiện có (danh sách 20 seed ở 10.1). So khớp tên trên cả 142 quà nháp của năm gói cho thấy ý đó chỉ đúng với phần lớn quà phương Tây: ít nhất 3 quà ở gói khác gần trùng seed (xem 7.5).

**Kiêng kỵ và nhạy cảm (cảnh báo mềm, không chặn)**

- **Hoa cúc:** gắn với Toussaint (1/11) và tang lễ ở Pháp (vừa); Ý tương tự nhưng chưa mở nguồn (thấp).
- **Hoa hồng đỏ chỉ dành cho tình yêu lãng mạn** (Đức, vừa), không tặng chủ nhà hay bạn bè. Hoa hồng vàng: nguồn mâu thuẫn (thấp). Số bông lẻ được ưu tiên, tránh 13; "số chẵn là tang" chủ yếu là tập quán Đông Âu (thấp). Cẩm chướng ở Pháp (thấp).
- **Tây Ban Nha:** Thứ Ba ngày 13 xui, không phải Thứ Sáu 13 (từ trí nhớ, chưa kiểm chứng).
- **Nhẫn** có thể bị hiểu là lời cầu hôn; **dao hoặc kéo** bị coi là "cắt đứt" (cả hai từ trí nhớ, chưa kiểm chứng).
- **Lông thú và ngà** xuất hiện trong danh sách năm 13-14: nhạy cảm đạo đức và pháp lý; đề xuất thay bằng ren hoặc vàng (phán đoán biên tập, thấp).
- **Đừng giả định người tặng là nam:** nam chi nhiều hơn (Anh £57 so với £41; Úc A$171 so với A$123) nhưng chữ trong app phải trung tính (vừa).
- Giáng Sinh, Easter, Reyes có yếu tố tôn giáo: không gửi thông báo "bắt buộc".

**Đối thủ trong khu vực (mỏng, tin cậy thấp)**

- Paired (trả phí): trang App Store ghi 8 triệu lượt tải, đây là tuyên bố của nhà phát triển (thấp). Lovewick (miễn phí): vượt 100.000 người dùng năm 2022 và gọi vốn pre-seed 1 triệu USD (vừa).
- Không tìm thấy dữ liệu Sensor Tower hay Appfigures cho Between, Couple Widget và các app đếm ngày kiểu Đông Á ở phương Tây, cũng không thấy app đếm ngày chuyên biệt nào chiếm lĩnh; nhóm chính là app hỏi đáp về quan hệ. Đây là kết luận hạn chế, không chứng minh được quy mô thị trường.

**Cách InLove nên xử lý (chi tiết ưu tiên ở mục 6)**

1. **Làm trước: thêm Easter và sửa Mothering Sunday cho Anh.** Thêm loại quy tắc `relative(anchor = easter)` bằng Computus trong commonMain (không dùng `java.time`), kèm golden test lấy từ bảng đã xuất bản (2027: Easter 28/3, Mothering Sunday 7/3). Cỡ S-M, không đổi schema Room.
2. **Làm trước: mặc định theo quốc gia, không theo ngôn ngữ giao diện.** Valentine bật ở mọi vùng nhưng nhấn nhẹ ở Đức, Úc, Tây Ban Nha (20% và 12% có kế hoạch mua quà); National Boyfriend Day tắt mặc định và thêm Girlfriend Day đối xứng; Sweetest Day và Sant Jordi chỉ bật theo vùng; lễ không phải cặp đôi (Independence Day, Thanksgiving, St. Patrick) chỉ hiện dạng thông tin, không nhắc (F04 ở mục 3.2).
3. **Làm trước: báo trước đúng mức đặt chỗ.** Nhà hàng 14-21 ngày, đơn cá nhân hóa 14 ngày, Giao thừa dương lịch 30 ngày, Giáng Sinh 28 ngày. Bộ chọn hiện tại {0, 1, 3, 7} không phủ các mức này (F10 ở mục 3.2).
4. **Làm trước: thay "N-Year Love Anniversary" chung bằng tên theo năm** (giấy, bông, da, gỗ, thiếc, pha lê, sứ, bạc, ngọc trai, hồng ngọc, vàng, kim cương), hiện cả bản truyền thống và hiện đại, ghi rõ là gợi ý.
5. **Làm sau: giá nhiều tiền tệ.** Bậc giá 1-4 độc lập tiền tệ cộng `tierBands` theo thị trường (G-01 ở mục 3.1). JSON nháp để `priceBand = null` vì chưa có số kiểm chứng; chỉ điền sau khi người bản địa duyệt, không bịa số.
6. **Làm sau: tùy chọn "ngày hẹn đầu / ngày chính thức"** cho ngày kỷ niệm; monthiversary là tùy chọn, tắt mặc định.
7. **Làm sau: văn phong trung tính giới tính;** `whoGives` do người dùng chọn; ngôn ngữ tình yêu chỉ là thẻ mềm, không tuyên bố "khớp" làm tăng hài lòng.
8. **Làm sau nữa:** tab "Hoa" cảnh báo số bông lẻ và hoa cúc theo vùng (Pháp, Ý, Đức); gợi ý "lên kế hoạch chợ Giáng Sinh" và lịch Advent đôi cho Đức, Pháp.
9. **Nền tảng:** ưu tiên iOS cho Mỹ, Anh, Úc; chờ chủ app quyết định lịch KMP/iOS (mục 9).

**Chưa kiểm chứng:** New Zealand, Hà Lan, Bắc Âu; tỷ lệ thẻ quà tặng của cặp đôi; thói quen cặp đôi dịp Easter và Giáng Sinh (chỉ có chi tiêu quà bạn đời của Anh, nguồn không rõ năm); tỷ lệ iOS/Android của Canada, Đức, Tây Ban Nha, Ý, NZ; nguồn Gelt (ES) và PsychologicalScience (chỉ có tóm tắt tìm kiếm). `mid` của Ý (110) và Đức/Tây Ban Nha (50) là mốc của khoảng đa số, **không phải trung bình đã công bố**. Số Gen Z so với Millennial ở Mỹ mâu thuẫn giữa các nguồn nên không đưa vào. Không có nguồn đã mở cho: nguồn gốc và mức dùng của National Boyfriend/Girlfriend Day, Reyes Magos, Mothering Sunday = Easter − 21, Thứ Ba ngày 13, kiêng kỵ nhẫn và dao, hoa cúc ở Ý (đều gắn "từ trí nhớ, chưa kiểm chứng" trong dữ liệu). Tên sản phẩm và giá trong danh sách quà (Lego Botanical, Virgin Experience Days, Tinggly, Red Letter Days, TheFork) lấy từ trí nhớ hoặc kết quả tìm kiếm, **chưa kiểm giá**. Bảng năm cưới 11-24 là nguồn thứ cấp và mâu thuẫn nhau. Về dữ liệu: id quà đang có tiền tố `us_g_` dù nhiều món phục vụ cả châu Âu; nên đổi thành tiền tố trung tính (ví dụ `west_g_`) trước khi chốt pack.

### 4.4 Đông Nam Á và Ấn Độ (Philippines, Thái Lan, Indonesia, Malaysia, Singapore, Ấn Độ)

**Dữ liệu nháp:** 19 dịp, 7 cột mốc, 28 quà, 13 kiêng kỵ (`pack = SEA_SA` trong file JSON nháp). Đây là gói **mỏng nhất và nhiều ước lượng nhất**: trong 28 quà có 15 món tin cậy thấp và 13 món tin cậy vừa (không món nào cao), ngân sách dịp gần như toàn là ước lượng bậc độ lớn, và nhiều số khảo sát lấy từ đoạn trích tìm kiếm chứ không phải trang gốc. Có 8 quà gắn với Indonesia hoặc Malaysia, trong đó 7 món tin cậy thấp. **Không có số liệu quốc gia về thói quen tặng quà đôi ở Philippines, Indonesia, Malaysia**; chỉ có số về Valentine ở Thái Lan, Singapore và Ấn Độ. Số nào cũng khác mẫu, khác năm nên **không so và không cộng giữa các nước**.

**Tín hiệu thị trường (nhãn tin cậy: cao / vừa / thấp)**

- **Thái Lan:** UTCC dự báo chi tiêu Valentine 2026 là 2,9 tỷ baht (+7,4%, cao nhất sáu năm). Gen Z chi khoảng 1.200-1.600 baht, nghiêng về quà handmade và hoạt động số; Gen Y khoảng 2.500-2.800 baht, chủ yếu cho bữa tối. **Đi ăn ngoài là hoạt động số 1 (46%)**; quần áo và túi khoảng một phần ba, socola 25%, hoa 12%, 57% mua online (nhóm số sau tin cậy vừa-thấp). Nguồn: Thairath 27/01/2026, trang không mở được (hết thời gian chờ) nên số lấy từ đoạn trích, và bài không nêu khảo sát gốc. Tin cậy vừa.
- **Singapore:** YouGov 2019 (n=1.041): 34% người Singapore đón Valentine, trong nhóm đó 81% tặng quà, trung bình **SGD 123 một món**. Tỷ lệ đón giảm theo thời gian yêu: **76% dưới 1 năm, 48% từ 5 đến 10 năm, 27% từ 20 năm trở lên**. Số đã 7 năm tuổi; YouGov 2023 chỉ cho thấy 24% coi đây là dịp đặc biệt chính đáng. Tin cậy vừa-thấp. Ý nghĩa cho InLove: với người yêu lâu, **mốc kỷ niệm năm quan trọng hơn Valentine**.
- **Philippines:** Visa 2024: người dùng chi "thông minh hơn", ưu tiên quà có ý nghĩa và cá nhân hóa; hoa giảm, sách, trang sức, giày, đồ thủ công tăng; chi tiêu dồn vào **hai cuối tuần quanh 14/2** chứ không chỉ đúng ngày. Mức giữa PHP 2.000 lấy từ ước tính ABS-CBN 2018 (cũ). Tin cậy vừa. "Monthsary" là từ thật và có thị trường quà thương mại (PhilFlower, PhilFlora, Gifty PH), nhưng **không có khảo sát về mức phổ biến** (tin cậy thấp).
- **Ấn Độ:** Hansa Research qua Social Samosa (trang trả 403 khi mở, chưa xác nhận ngày xuất bản): 71% định chi; nhóm lớn nhất chi INR 1.000-3.000 (33%), 18% chi 3.000-5.000, 15% trên 5.000; hoa 54%, ăn ngoài hoặc quán cà phê 53%, socola và kẹo 52%. Tin cậy thấp-vừa. **Diwali 2026:** chi tiêu mua sắm lễ hội đô thị dự kiến giảm khoảng 6% (LocalCircles qua Outlook Business, tin cậy vừa), nên cần một bậc giá thấp. Diwali 2024: 53% chọn bánh kẹo hoặc socola làm quà (LocalCircles qua BusinessToday).
- **Thiết bị (StatCounter, tháng 9/2026; chưa ghi lại URL nên tin cậy vừa):** Android chiếm Ấn Độ 93,89%, Philippines 86,53%, Indonesia 79,16%, Thái Lan 73,96%, Malaysia 55,23%, Singapore 56,25%. Nghĩa là **ở Malaysia và Singapore có khoảng 44-45% người dùng không dùng Android**, nên kế hoạch KMP/iOS có giá trị rõ ở hai nước này; Ấn Độ, Philippines, Indonesia gần như thuần Android.
- **Kênh mua:** thương mại điện tử Đông Nam Á khoảng 157,6 tỷ USD GMV năm 2025 (Momentum Works; chưa mở nguồn, tin cậy vừa). Chưa có bằng chứng đây là kênh mua quà đôi. Tên kênh (Shopee, Lazada, TikTok Shop, Tokopedia, Amazon.in, Flipkart, FNP, IGP) chỉ nên là **nhãn tham khảo**, không gắn link cứng.
- **Nhạy cảm tôn giáo (đã có nguồn báo chí):** **Indonesia không có lệnh cấm toàn quốc**; Aceh Besar, Aceh Barat, Banda Aceh cấm hoặc yêu cầu không tổ chức, Bekasi cấm học sinh (2020), Bộ Tôn giáo không chính thức ủng hộ lệnh cấm. **Malaysia:** JAKIM nhắc lại (2/2023) rằng Valentine chưa bao giờ được Hồi giáo công nhận, kèm phán quyết của Hội đồng Fatwa Quốc gia năm 2005 (theo báo chí); đây là lời khuyên tôn giáo, không phải luật, và người Hoa, Ấn, Cơ đốc vẫn đón. **Ấn Độ:** nhóm cực hữu quấy rối cặp đôi ở công viên quanh 14/2 (Ghaziabad, 2/2025); nên khuyến khích địa điểm riêng tư. Có báo chí nói cảnh sát Hyderabad từng cấm cặp chưa cưới ở nơi công cộng, nguồn chưa được mở nên không dùng làm căn cứ.
- **Pakistan, Bangladesh:** chỉ ghi chú nhạy cảm, **không làm nội dung** (lệnh cấm của toà Pakistan năm 2017 là nhớ lại, chưa kiểm chứng; Bangladesh có Pohela Falgun gần 14/2).
- **Không dùng được:** các số "Valentine Indonesia/Malaysia chi bao nhiêu" không tìm được nguồn nào; ô ngân sách của hai nước này là ước lượng, ghi "est" trong file JSON.

**Đối chiếu bằng tìm kiếm web ngày 2026-10-26 (chỉ đọc đoạn trích kết quả, chưa mở trang gốc; mọi ngày dưới đây là "dự kiến ±1 ngày")**

- **Karwa Chauth 2026:** thứ Năm **29/10** ở Ấn Độ (đa số nguồn); ở Mỹ là 28/10 (lý do có thể là múi giờ và giờ trăng mọc; SUY LUẬN, chưa kiểm chứng); riêng SmartPuja ghi 28/10 cho cả Ấn Độ. **Ngày phụ thuộc vị trí người dùng**, nên quy tắc `table` phải khoá theo thị trường và cho người dùng chỉnh ±1 ngày.
- **Ramadan 2027:** dự kiến bắt đầu thứ Hai **8/2/2027** (Trung tâm Thiên văn UAE; một nguồn tiếng Pháp ghi Ramadan bắt đầu từ tối 7/2). **Eid al-Fitr** 9/3 (dự báo Ả Rập Xê Út) hoặc 10/3 (nếu Ramadan đủ 30 ngày). Tất cả phụ thuộc quan sát trăng.
- **Diwali 2026:** Chủ nhật **8/11/2026** (Dhanteras 6/11, Govardhan Puja 9/11). Một trang (BankBazaar) ghi 20-21/10/2026, trông như lịch của năm 2025 còn sót lại.
- **Loy Krathong 2026:** **24/11** (Phuket101, HappyFares, Wat Tha Khanun dẫn Bộ Văn hóa) so với 25/11 (ItsBetterInThailand, Outside.so); **chưa chốt**.
- **Độ trễ thời gian:** tại ngày lập tài liệu (26/10/2026), Karwa Chauth cách 3 ngày, Diwali 13 ngày, Loy Krathong khoảng 4 tuần. Nghĩa là nội dung này **không kịp phát hành trước ba dịp đó trong năm 2026**; nó chỉ có ích cho chu kỳ 2027, và nhắc rằng quy trình cập nhật bảng ngày hằng năm (U-17 và U-25) phải chạy trước mùa lễ ít nhất 4-6 tuần.

**Lịch dịp đôi (liên quan cặp đôi = `coupleRelevance` 0-3; ngân sách nội tệ thấp / vừa / cao, **đều là ước lượng trừ khi có ghi nguồn**; ngày tính cho 10/2026-10/2027)**

| Dịp | Nước | Quy tắc ngày | Liên quan cặp đôi | Ngân sách | Báo trước | Bật mặc định |
|---|---|---|---|---|---|---|
| Valentine | Philippines | dương 14/2 | 3 | PHP 500 / 2.000 / 5.000 | 5 ngày (đặt chỗ) | Có |
| Giáng Sinh, mùa "Ber months" | Philippines | dương 25/12 (mùa bắt đầu từ 1/9) | 2 | PHP 500 / 1.500 / 4.000 (không có nguồn) | 14 | Có (tin cậy thấp) |
| Valentine | Thái Lan | dương 14/2 | 3 | THB 1.200 / 1.600 / 2.800 | 7 (đặt bàn) | Có |
| Loy Krathong | Thái Lan | bảng: 24 hoặc 25/11/2026 | 2 | THB 0 / 300 / 1.500 | 3 | Không |
| Valentine | Singapore | dương 14/2 | 2 | SGD 40 / 123 / 300 | 7 (đặt bàn) | Có |
| Valentine | Indonesia | dương 14/2 | 2 | IDR 50.000 / 200.000 / 750.000 | 5 | **Không** (nhạy cảm, bật theo ý người dùng) |
| Valentine | Malaysia | dương 14/2 | 1 | MYR 30 / 120 / 400 | 5 | **Không** (nhạy cảm, bật theo ý người dùng) |
| Ramadan 2027 (cờ ngữ cảnh, không phải dịp quà) | Indonesia, Malaysia | bảng: dự kiến 8/2/2027 | 1 | không có | 14 | Không |
| Eid al-Fitr / Lebaran / Hari Raya Aidilfitri 2027 | Indonesia, Malaysia | bảng: dự kiến 9 hoặc 10/3/2027 | 1 | IDR 100.000 / 300.000 / 1.000.000 | 14 | Không |
| Chap Goh Mei (rằm tháng Giêng của người Hoa) | Malaysia, Singapore | âm lịch Trung Quốc 15/1 → 20/2/2027 | 2 | MYR 0 / 50 / 200 | 3 | Không (chọn cho người Hoa) |
| Thất Tịch (Qixi) | Malaysia, Singapore | âm lịch Trung Quốc 7/7 → 8/8/2027 | 2 | SGD 20 / 80 / 250 | 5 | Không (chọn cho người Hoa; chưa có nguồn riêng cho hai nước) |
| Valentine | Ấn Độ | dương 14/2 | 3 | INR 1.000 / 3.000 / 5.000 (Hansa Research) | 7 (đặt bàn) | Có, kèm ghi chú địa điểm |
| Tuần lễ Valentine 7-14/2 (Rose 7/2, Chocolate 9/2, Teddy 10/2, Promise 11/2) | Ấn Độ | tuần và các ngày dương cố định | 2 | tuần INR 200 / 800 / 3.000; Rose 100 / 500 / 1.500; Chocolate 100 / 500 / 2.000; Teddy 300 / 1.000 / 3.000; Promise 0 / 300 / 3.000 | 3-5 | Không (nhóm tuỳ chọn) |
| Karwa Chauth | Ấn Độ | bảng: 29/10/2026 (28 hoặc 29 tuỳ vị trí) | 3 | INR 1.000 / 5.000 / 25.000 | 10 | Không (chọn theo người dùng) |
| Diwali | Ấn Độ | bảng: 8/11/2026 | 2 | INR 500 / 2.000 / 10.000 | 14 | Không (chọn theo người dùng) |

**Quy tắc `defaultOn`:** liên quan cặp đôi ≥ 2 là điều kiện cần, chưa đủ. Dịp còn phải áp dụng cho số đông người dùng của thị trường đó và không bị cổng tôn giáo hay thể chế chặn. Indonesia (liên quan cặp đôi 2) bị tắt bởi cổng tôn giáo, Malaysia (liên quan cặp đôi 1) bị tắt bởi cả hai điều kiện, còn Ấn Độ **vẫn bật** nhưng kèm cảnh báo an toàn vì rủi ro là quấy rối ngoài đường chứ không phải phản đối từ cơ quan nhà nước. Các dịp của một cộng đồng nhỏ (Chap Goh Mei, Qixi, Karwa Chauth, Diwali) để opt-in dù liên quan cặp đôi 2-3. `idmy_ramadan_2027` bị đổi sang tắt ở bước gộp (một trong 12 sửa lỗi, xem mục 7) vì là cờ ngữ cảnh chứ không phải dịp quà. Ba dịp dạng bảng (Loy Krathong, Karwa Chauth, Diwali) thuộc loại quy tắc `table`: **phải có quy trình cập nhật hằng năm và cảnh báo hết hạn**, giống rủi ro F06 ở mục 3.2 (bảng âm lịch hết hạn). Gói chỉ tách riêng 4 trong 8 ngày của Valentine Week (Rose, Chocolate, Teddy, Promise); Propose 8/2, Hug 12/2 và Kiss 13/2 chỉ nằm trong dòng "tuần" (báo cáo gốc không nêu lý do loại; SUY LUẬN: ngày đó không gắn với một món quà cụ thể).

**Thói quen đếm và kỷ niệm**

- **Monthsary (Philippines)** là từ thật; nhóm tiệm hoa và quà bán gói "monthsary" nhưng không có khảo sát nào cho biết bao nhiêu cặp đôi làm. Giữ mốc 1, 3, 6, 9 tháng ở dạng **lặp hằng tháng, tuỳ chọn**; ngày 29-31 lùi về ngày cuối tháng. Theo nguồn tiệm quà, mốc 1 và 6 tháng thường được làm lớn hơn. Đây là **quyết định của chủ app** (hỏi ở onboarding, xem mục 9).
- **Kỷ niệm năm (Philippines, Thái Lan, Indonesia):** mốc 1, 2, 3, 5, 10 năm. Thái Lan có "วันครบรอบ" là dịp quà riêng ngoài Valentine (trang gợi ý quà của LG Thái Lan và Shopee Thái Lan). Indonesia: "anniversary" thường dùng cho hari jadian (ngày bắt đầu hẹn hò), không chỉ cho ngày cưới (Popbela).
- **Hari jadian hằng tháng (Indonesia):** không tìm được nguồn xác nhận; giữ ở dạng tuỳ chọn, **mặc định tắt**.
- **100 ngày (Thái Lan):** tìm bằng tiếng Thái không xác nhận tục này (kết quả "100 วัน" chủ yếu là lễ cúng 100 ngày sau tang lễ). **Không đưa vào mặc định.**
- **Singapore, Malaysia:** kỷ niệm năm là mốc nặng nhất, nhất là năm đầu, vì tỷ lệ đón Valentine giảm theo thời gian yêu (số của YouGov Singapore). Không có nguồn riêng về tục lệ hai nước.
- **Ấn Độ:** nhóm dùng "cặp đôi" nhiều khả năng là **vợ chồng**, nên mốc ngày cưới (1, 5, 10, 15, 20, 25, 50 năm) quan trọng; dùng chuẩn phương Tây (bạc 25, vàng 50), khớp bán lẻ Ấn Độ. Quà 25 năm: đồng xu bạc có dấu BIS hallmark, khung ảnh khắc tên, tranh vẽ cặp đôi; 50 năm: vàng hoặc đồ mạ vàng (nguồn là blog bán lẻ hoặc bài SEO chất lượng thấp, nên tin cậy thấp).
- **Đối thủ:** Been Love Memory, Day Together, Between (mạnh ở Singapore từ khoảng 2016, nhớ lại), Coupled, My Love, Been Together; **không có số tải theo nước** nên phần này chỉ định tính (xem mục 8). Chưa thấy ứng dụng nào có kịch bản monthsary hoặc Valentine Week (khoảng trống chưa kiểm chứng).
- **Cụm tháng 2/2027 cho người dùng Hoa ở Malaysia:** Tết 6/2, Ramadan khoảng 8/2, Valentine 14/2 (Chủ nhật, khoảng ngày thứ 7 của Ramadan), Chap Goh Mei 20/2. Đây là tuần mà gợi ý "bữa tối lãng mạn" dễ đụng độ lịch ăn chay, nên cần cờ ngữ cảnh.

**Kiêng kỵ (cảnh báo mềm, không chặn; 4 mục "mạnh" và 9 mục "mềm")**

- **Mạnh (nhưng vẫn chỉ cảnh báo):** tổ chức hoặc quảng bá Valentine ở nơi có lệnh địa phương (Indonesia); đẩy Valentine làm mặc định cho người dùng Hồi giáo Malaysia; **rượu và đồ ăn có thịt heo** (Indonesia, Malaysia); khuyến khích thể hiện tình cảm nơi công cộng quanh 14/2 (Ấn Độ). Tin cậy vừa.
- **Mềm, người gốc Hoa ở Malaysia và Singapore:** đồng hồ treo hoặc để bàn (送钟 gần âm với "tiễn chung"), dao kéo, khăn tay, giấy gói màu trắng, đen, xanh dương. Tin cậy vừa.
- **Mềm, tin cậy thấp:** đồ riêng tư gợi cảm (Indonesia, Malaysia); giày dép tặng người yêu (Thái Lan, Philippines, mê tín "người ấy sẽ bỏ đi"); đồ da bò, thực phẩm có thịt bò cho người Hindu; màu đen trong Karwa Chauth.
- **Tác động lên seed hiện tại:** "Đồng Hồ Đôi Phong Cách Tối Giản" / "Minimalist Couple Watch Set" (`GiftIdeasSeed.kt:204-205`) nên gắn `tabooRefs` theo **khái niệm** `clock_watch`, vì bốn gói đã dùng chung khái niệm này (`vn_clock` ở Việt Nam, `sgmy_clock` ở Malaysia và Singapore, `cn_clock` ở Trung Quốc, Đài Loan, Hồng Kông, `ru_watch_knife` ở Nga, Ukraine, Kazakhstan). Mỗi thị trường tự chọn câu cảnh báo của mình, hiện một dòng nhỏ "một số người ngại món này". **Không xoá quà.**

**Cách InLove nên xử lý (chi tiết ưu tiên ở mục 6)**

1. **Tách "thị trường" khỏi "ngôn ngữ giao diện".** Hiện bộ lịch lễ chọn theo ngôn ngữ (`OfflineSeeder.kt:211`: VI → lịch Việt, còn lại → lịch Mỹ) và chỉ gieo một lần khi bảng rỗng. Người dùng Thái, Indonesia, Philippines dùng giao diện tiếng Anh sẽ nhận lịch Mỹ (St. Patrick, Boyfriend Day) mà không có Loy Krathong, Ramadan hay Diwali. Cần `MarketProfile` suy từ locale máy hoặc một thẻ chọn ở onboarding, **không bao giờ suy ra hay lưu tôn giáo của người dùng**; chỉ dùng các bộ lọc cục bộ do chính người dùng bật.
2. **Valentine tắt mặc định cho Indonesia và Malaysia, bật cho Philippines, Thái Lan, Singapore, Ấn Độ.** Đổi cờ trong seed là chưa đủ: `GetPersonalizedGiftSuggestionsUseCase.kt:46-51` **cứng bốn dịp** (Valentine, 8/3, 20/10, Giáng Sinh) và `nearestUpcomingOccasion` không đọc `defaultOn` hay danh sách dịp của người dùng, nên Valentine sẽ vẫn là "dịp gần nhất" ở Indonesia. Phải truyền bộ dịp theo thị trường vào `nearestUpcomingOccasion` và `invoke`, nếu không gợi ý quà sẽ đẩy Valentine bất chấp cờ trong seed.
3. **Ramadan 2027 là cờ ngữ cảnh, không phải ngày tặng quà:** hiện một banner gợi ý "buka puasa" thay cho bữa tối lãng mạn, không tạo nhắc nhở. Eid al-Fitr là nhắc tuỳ chọn, tránh gợi ý tình nhân. Cả hai chỉ hiện cho người dùng đã tự bật gói dịp "Ramadan và Eid" (một lựa chọn dịp lưu cục bộ, không phải khai báo tôn giáo, không lưu vào hồ sơ và không đồng bộ lên máy chủ).
4. **Ấn Độ: nhóm "Valentine Week" opt-in, Karwa Chauth opt-in, Diwali nhắc trước 10-14 ngày.** Thêm một dòng an toàn ở Valentine ("ưu tiên địa điểm riêng tư"). Mốc ngày cưới nhấn mạnh 25 và 50 năm. Karwa Chauth phụ thuộc vị trí, nên là quy tắc `table` theo thị trường với nút chỉnh ±1 ngày.
5. **Thời gian báo trước theo dịp**, không cố định 3 ngày: Valentine 5-7 ngày (đặt bàn 7-10 ngày ở Thái Lan, Singapore, Ấn Độ), Karwa Chauth 10, Diwali 14, Ramadan và Eid 14, monthsary 1-2, quà làm riêng 7+. Hiện **mọi ngày lễ được gieo đều nhắc trước 3 ngày** (F04 ở mục 3.2).
6. **Giá bằng nội tệ, không quy đổi sang VND:** mỗi quà có bậc `priceTier` 1-4 và mỗi thị trường có `tierBands` bằng tiền địa phương; cột `budgetMaxVnd` (`Entities.kt:100`) cần đổi thành `budgetMax` + `budgetCurrency` (**có thể cần migration Room**, xem U-28 và 6.3.5). Ấn Độ cần một bậc thấp vì Diwali giảm khoảng 6%. Nhãn tiền dùng `NumberFormat.getCurrencyInstance`.
7. **Danh mục và nhãn mới:** `halal_safe`, `regional_festival`, `monthsary`, `buka_puasa`, `private_venue`. Quà "telekung hoặc sejadah" (`my_g_002`) là đồ tôn giáo nên chỉ hiện sau khi người dùng bật bộ lọc "halal-safe / trang nhã" cục bộ.
8. **Cân bằng loại quà:** với Gen Z Thái Lan và Philippines, ưu tiên trải nghiệm, cá nhân hóa, quà số (thư tay, photo booth, bài hát riêng); với Thái Lan, Singapore, Ấn Độ ưu tiên **bữa tối đặt trước** (số 1 ở Thái Lan: 46%). **Không để "gấu bông, hoa, socola" là mặc định duy nhất** ở Philippines, vì Visa 2024 ghi hoa giảm.
9. **Cảnh báo kiêng kỵ mềm:** đồng hồ, dao kéo, khăn tay cho người dùng Hoa; rượu, thịt heo cho Indonesia và Malaysia; gắn `tabooRefs` cho seed đồng hồ (`GiftIdeasSeed.kt:204-205`). **Không chặn nội dung**, và không gán nhãn tôn giáo cho người dùng.
10. **Cách viết:** không dùng "Ngày Tình Nhân" làm tiêu đề dẫn đầu ở Indonesia và Malaysia; tên dịp song ngữ VI/EN kèm tên bản địa (ví dụ "วันครบรอบ", "Hari jadian", "शादी की सालगिरह").
11. **Sửa định dạng ngày trước khi mở Thái Lan:** `SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())` được dùng để parse và định dạng ngày **lưu** (F-08 ở mục 3.3: `ProfileUtils.kt:13`, `Material3DatePicker.kt:99,109`, `AlarmNotificationScheduler.kt:81`...). Trên máy locale `th-TH` (lịch Phật, năm 2569) hoặc chữ số bản địa (`ar`, `hi`) có thể ra sai năm hoặc chữ số không phải ASCII. **Chưa kiểm chứng trên máy thật**; sửa rẻ bằng `Locale.ROOT` cho định dạng lưu, `Locale.getDefault()` chỉ cho hiển thị.
12. **Ngôn ngữ thêm (id, th, hi, fil)** nên quyết định bằng **số liệu quốc gia trong Play Console của chủ app**, không bằng nghiên cứu này (số thiết bị chỉ cho biết nền tảng, không cho biết ngôn ngữ). Mỗi gói cần người bản địa duyệt, vì dịch máy dễ gợi quà sai văn hoá.
13. **Một dòng về quyền riêng tư:** mỗi nước trong nhóm này có luật bảo vệ dữ liệu cá nhân riêng; **chưa kiểm chứng nội dung các luật đó, cần tư vấn pháp lý** trước khi mở thị trường. Thiết kế đề xuất chủ ý không thu thêm dữ liệu nào (không lưu tôn giáo, không gửi lựa chọn dịp lên máy chủ), nên rủi ro thấp hơn so với một hồ sơ có thuộc tính nhạy cảm.

**Chưa kiểm chứng:** ngân sách các dịp (trừ ba số có nguồn là Valentine ở Thái Lan, Singapore, Ấn Độ; mức giữa của Philippines lấy từ ước tính năm 2018); mọi ngày dạng bảng (Karwa Chauth, Diwali, Loy Krathong, Ramadan, Eid) mới chỉ được đối chiếu qua đoạn trích tìm kiếm; mức phổ biến của monthsary, hari jadian và 100 ngày Thái Lan; Qixi ở Malaysia và Singapore (không có nguồn); `whoGives` của Teddy Day (nam tặng nữ, chưa có nguồn) và Karwa Chauth, và `whoGives` chỉ là gợi ý mềm, **không dùng để lọc theo giới**; thị phần thiết bị chưa ghi lại URL; số StatCounter, Momentum Works và Thairath lấy từ đoạn trích; các lệnh cấm Pakistan và Hyderabad nhắc lại từ báo chí hoặc trí nhớ; vài món quà Indonesia, Malaysia (7 trên 8 món tin cậy thấp); khuyến nghị lead time 5-7 ngày là thận trọng, chưa có dữ liệu thời gian đặt bàn ở các nước này.

### 4.5 Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS (kèm Ba Lan và vùng Vịnh)

**Dữ liệu nháp:** 16 dịp, 8 cột mốc, 28 quà, 14 kiêng kỵ (`pack = LATAM_TR_CIS`). Độ tin cậy: quà 1 cao / 20 vừa / 7 thấp; dịp 4 cao / 9 vừa / 3 thấp; cột mốc 1 vừa / 7 thấp; kiêng kỵ 3 cao / 2 vừa / 9 thấp. **Độ phủ mỏng và không đều:** mỗi nước chỉ có 2-5 quà (Brazil và Nga nhiều nhất); Chile không có số liệu chi tiêu; Thổ Nhĩ Kỳ không có khoảng giá (để trống chủ ý vì lạm phát); vùng Vịnh chỉ có một dịp bị tắt và ba mục kiêng kỵ, **không có quà**; UAE và Ai Cập không có dịp hay quà nào (chỉ xuất hiện trong một mục kiêng kỵ chung). Vì vậy nhóm này là **hạt giống để người bản ngữ rà soát**, chưa phải nội dung phát hành. Thêm một điều kiện cứng: giao diện app hiện chỉ có tiếng Việt và tiếng Anh (`AppLanguage { VI, EN }`), dù dữ liệu nháp đã có tên bản ngữ (`local`).

**Tín hiệu thị trường** (các mục có nhãn "đối chiếu web" được kiểm lại bằng tìm kiếm ngày 2026-10-26, chỉ đọc đoạn trích; còn lại theo báo cáo gốc)

- **Brazil, Dia dos Namorados (12/6):** CNDL/SPC 2026 cho 61% người dự định tặng quà, vé trung bình R$264 (R$295 ở nhóm A-B), tổng khoảng R$26,4 tỷ (đối chiếu web, Folha PE). Một số bài khác vẫn dùng bản cũ hơn: R$238 và R$22,14 tỷ, và các nguồn không nói rõ bản nào là chính thức. Top quà theo báo cáo gốc: quần áo 52%, nước hoa và mỹ phẩm 34%, bữa tối khoảng 20%. Mức giữa trong JSON nháp ban đầu là R$250, không trùng cả hai con số nguồn; đã sửa thành R$264 ở bước QA (`meta.qaFixes`).
- **Colombia, Amor y Amistad (thứ Bảy thứ 3 tháng 9):** Fenalco 2026 cho 78% dự định tổ chức, trong số đó 73% chơi "amigo secreto"; 48% dự chi từ 100.000 COP trở lên (34% năm 2025) (đối chiếu web). Người yêu là nhóm người nhận được nhắc nhiều nhất (35%) nhưng dưới một nửa, amigo secreto sát ngay sau (32%); cả hai số lấy qua RTVC, chưa xác nhận là số chính thức của Fenalco. Vì vậy đây **không phải dịp riêng của cặp đôi**. Dữ liệu nháp ban đầu ghi lý do của mục kiêng kỵ `co_not_couple_only` là "chỉ 35% ... đồng nghiệp là nhóm chính", sai nghĩa; đã sửa ở bước QA.
- **Mexico (14/2):** hai nguồn lệch nhau gần 3 lần vì đơn vị tính khác nhau: Concanaco MXN 1.100 mỗi người, ANPEC MXN 3.037 mỗi lần tổ chức gồm cả bữa tối; ANPEC cảnh báo giá tăng đến 15%. **Argentina:** Focus Market 2/2026, bình quân ARS 42.700; trải nghiệm chiếm khoảng 38% nhu cầu, nhà hàng và bar khoảng 31%, sô-cô-la chỉ 7%; số ARS lỗi thời rất nhanh. **Peru:** CCL 2026, chi tối đa S/300 mỗi người, xu hướng quà công năng và công nghệ (Mercado Libre).
- **Nga, 14/2 đang suy giảm nhưng con số tuyệt đối không thống nhất.** Một bài của dp.ru (13/2/2026) dẫn VCIOM 2026: 18% dự định tặng quà (39% năm 2019), 58% thờ ơ. Trang VCIOM năm 2025 lại ghi 34% (từ 51% xuống 34% trong 20 năm) và không tìm thấy trang VCIOM 2026. Avito 2026: khoảng 33% sẽ tổ chức; mức trung bình 8.000 RUB cho quà 14/2 của Avito chỉ thấy trong tiêu đề một bài báo (đối chiếu web). Báo cáo gốc liệt kê quà cho vợ 4.500, bạn gái 5.000, bạn trai 4.000 RUB nhưng nguồn không rõ, và các mức này chưa hòa giải với 8.000 RUB của Avito. **Chỉ kết luận được:** dưới một nửa dân số tham gia và xu hướng giảm, vì vậy Valentine để tắt mặc định. **8/3 mới là ngày lớn:** SuperJob 3/2026 cho quà khoảng 5.700 RUB cộng hoa khoảng 4.000 RUB.
- **Kazakhstan, 8/3:** số của sàn Wildberries cho 83% tham gia và 76% chi tối đa 40.000 KZT (số của sàn bán lẻ, không rõ phương pháp); Deloitte (qua báo thứ cấp) nói du lịch là quà mong muốn nhất (37%).
- **Ukraine, 8/3 đang bất định:** 8/3 vẫn nằm trong lịch chính thức nhưng năm 2026 không được nghỉ vì thiết quân luật; dự luật số 9009 (2023) đề xuất thay bằng "Ngày Phụ nữ Ukraine" 25/2, và một dự thảo nghị quyết số 15052 đăng ký ngày 2/3/2026 đề xuất điều tương tự; chưa rõ kết quả (đối chiếu web). Không có khảo sát chi tiêu cho Ukraine.
- **Ba Lan:** nhóm giá PLN 101-300 chiếm 41% ở Walentynki (wiadomoscihandlowe.pl); Allegro 2026 nêu sách, trò chơi đôi, nước hoa Ả Rập, đồ chơi "kidults". **Thổ Nhĩ Kỳ:** không có ngân sách TL đáng tin cho 2025-2026; Tamindir 2025 (không nêu khảo sát gốc) nói 38% tặng quà làm tay hoặc cá nhân hóa.
- **Android chiếm 62-88% thị phần ở 11 nước khảo sát** (AR 87%, PE 86%, CL 84%, BR 79%); nguồn là một trang thống kê tổng hợp mà báo cáo gốc không giữ URL, nên chỉ coi là hướng chung. Không có số liệu: Chile 2026, chi tiêu Ukraine, ngân sách TL, hiện trạng vùng Vịnh.

**Lịch dịp đôi (liên quan cặp đôi `coupleRelevance` 0-3; ngân sách nội tệ thấp / vừa / cao, chỉ "vừa" và số có ghi nguồn là số đo, còn lại là ước lượng; ngày tính cho 10/2026-10/2027)**

| Dịp | Nước | Quy tắc ngày | Liên quan cặp đôi | Ngân sách | Báo trước | Bật mặc định |
|---|---|---|---|---|---|---|
| Dia dos Namorados | BR | 12/6 cố định (2027: Thứ Bảy) | 3 | R$80 / 264 / 600 | 14 ngày | Có |
| Santo Antônio (thánh bà mối) | BR | 13/6 cố định | 1 | không có | 0 | Không, gộp vào lời nhắc 12/6 |
| Día del Amor y la Amistad | MX | 14/2 | 3 | MXN 300 / 1.100 / 3.000 | 7 | Có |
| San Valentín | CL | 14/2 | 3 | không có số | 7 | Có (tin cậy thấp, không có nguồn riêng cho Chile) |
| San Valentín | PE | 14/2 | 3 | PEN 80 / 200 / 300 | 7 | Có |
| Amor y Amistad | CO | thứ Bảy thứ 3 tháng 9 (19/9/2026; 18/9/2027) | 2 | COP 50k / 100k / 200k | 7 | Có, lời văn phải có bạn bè |
| Día de los Enamorados | AR | 14/2 | 3 | ARS 20k / 42,7k / 100k | 7 | Có |
| Semana de la Dulzura | AR | tuần 1-7/7 | 1 | kẹo, alfajor nhỏ | 0 | Không |
| Sevgililer Günü | TR | 14/2 | 3 | để trống (lạm phát) | 7 | Có |
| Dünya Kadınlar Günü | TR | 8/3 | 1 | không có | 3 | Không (tin cậy thấp) |
| Valentine | RU, UA, KZ | 14/2 | 1 | RUB 300 / 4.500 / 8.000 | 5 | Không |
| 8 Марта | RU, UA, KZ | 8/3 | 3 | RUB 1.000 / 5.700 / 10.000 (cộng hoa ~4.000) | 7 | Có (Ukraine cần rà soát, xem trên) |
| 23 февраля (nữ tặng nam) | RU | 23/2 | 2 | không có số | 5 | Có |
| День семьи, любви и верности | RU, KZ | 8/7 | 1 | không có | 0 | Không, chỉ lời chúc |
| Walentynki | PL | 14/2 | 2 | PLN 50 / 200 / 300 | 5 | Có |
| Valentine vùng Vịnh | SA, KW | 14/2 | 0 | không có | 0 | Không (tắt) |

Quy tắc ngày dương là tính số học; thứ trong tuần 2027 đã được tính lại (14/2 Chủ nhật, 23/2 Thứ Ba, 8/3 Thứ Hai, 12/6 Thứ Bảy). **Lỗ hổng dữ liệu:** dịp 23/2 bật mặc định nhưng chỉ có đúng một quà gắn vào (đồng hồ thể thao `ru_g_003`, chính là món mang cảnh báo kiêng nhẹ `ru_watch_knife`); trong 28 quà của nhóm này có 22 quà dành cho "bất kỳ" và 6 quà dành cho "cô ấy", **không quà nào dành cho "anh ấy"**.

**Thói quen đếm và kỷ niệm**

- **Brazil, mesversário** (kỷ niệm hằng tháng, cùng ngày trong tháng, mỗi tháng tính khoảng 30 ngày): có thật nhưng chỉ có bằng chứng ở mức blog quà tặng, không có khảo sát (tin cậy vừa). Dữ liệu nháp để các mốc 1-11 tháng, trọng số văn hóa 2. Tên vui "bodas de namoro" (1 tháng = beijinho, 3 tháng = algodão-doce, 6 tháng = pluminhas) khác nhau giữa các trang, **chỉ dùng làm lời văn** (tin cậy thấp).
- **Tây Ban Nha ngữ:** mesiversario nhẹ hơn Brazil, chủ yếu ở người trẻ; từ khóa và mức quan trọng là ký ức, chưa có nguồn (tin cậy thấp). Mốc 100 ngày chỉ là xu hướng mạng xã hội, không có bằng chứng. Mốc theo năm (1, 2, 3, 5, 10) là dịp đôi cốt lõi ở mọi nước trong nhóm nhưng cũng không có khảo sát (tin cậy thấp).
- **Thổ Nhĩ Kỳ:** cặp đôi thích "ngày đặc biệt" kiểu 06.06.2026 (một bài báo địa phương ở Antalya đếm 71 cặp cưới cùng ngày); gợi ý cho người dùng **tự chọn** ngày đặc biệt, không tự sinh. **Nga và CIS:** có cả "годовщина знакомства" (kỷ niệm quen nhau) và kỷ niệm cưới có tên theo năm (năm đầu "ситцевая" là ký ức); tên gọi khác nhau theo nước nên chỉ hiện đúng ngôn ngữ của người dùng.
- **Argentina và Colombia** có hai dịp tách rời (ngày Valentine hoặc Amor y Amistad, và kỷ niệm quen nhau); app **không nên gộp**.

**Thói quen quà tặng (nguồn có thể là số của sàn bán lẻ, phương pháp không công bố)**

- Brazil: quần áo, nước hoa và mỹ phẩm, bữa tối, sô-cô-la, giày; khoảng 85% định có hoạt động đôi (Alqia, qua trang tin, không rõ cỡ mẫu). Mexico: hoa hồng, sô-cô-la, gấu bông cỡ vừa và bữa tối (khoảng MXN 1.600) là combo kinh điển. Colombia (Fenalco): sô-cô-la và kẹo 18%, lời mời đi ăn 16%, quần áo 14%, mỹ phẩm và nước hoa 14%, thẻ quà 12%, trang sức 11%. Argentina: trải nghiệm (spa, khách sạn, đi chơi ngoại ô) tăng, sô-cô-la thấp. Peru: hoa, sô-cô-la, trang sức dẫn đầu nhưng đang dịch sang quà công năng, công nghệ, sức khỏe.
- Thổ Nhĩ Kỳ: trang sức, hoa, nước hoa, mỹ phẩm, quần áo, sô-cô-la, trải nghiệm. Nga: hoa (số lẻ), trang sức (Wildberries khoảng +60% trước 8/3), đồng hồ thể thao (Ozon tăng 12 lần cùng kỳ), nước hoa, thiết bị; thiệp "valentinka" và thú bông nhẹ cho 14/2 (thiệp +310% so với tháng 1). Kazakhstan: bó hoa 10.000-15.000 KZT (Flowwow). Ba Lan: sách, trò chơi đôi, nước hoa Ả Rập, "kidults".

**Kiêng kỵ và nhạy cảm (14 mục: 4 mạnh, 10 nhẹ; chỉ cảnh báo mềm, không chặn)**

- **Mạnh:** bó hoa số chẵn dành cho tang lễ ở Nga (tin cậy cao, nhiều nguồn) và Ukraine (vừa, chỉ thấy đoạn trích); ở Kazakhstan chỉ là nhẹ và tin cậy thấp. Brazil: **không nên lấy 14/2 làm dịp chính** (tin cậy cao). Ả Rập Xê Út: không quảng bá Valentine hay đồ đỏ (tin cậy thấp, xem dưới).
- **Nhẹ:** hoa vàng gợi chia tay (Nga, Ukraine; nguồn tự gọi là còn tranh cãi); dao, kéo, đồng hồ, khăn tay là mê tín ở Nga, Ukraine, Kazakhstan (ký ức; đồng hồ thông minh vẫn bán chạy nên chỉ ghi chú nhỏ, cùng khái niệm `clock_watch` với `vn_clock`, `sgmy_clock`, `cn_clock`); dao, kéo "cắt" tình cảm ở Thổ Nhĩ Kỳ và Brazil (ký ức); hoa cúc và loa kèn gắn với tang lễ ở nhiều nền văn hóa, riêng hoa cúc còn gắn với Lễ Các Thánh ở Ba Lan (từ ký ức, chưa có nguồn); Colombia: coi Amor y Amistad chỉ dành cho cặp đôi (tin cậy cao).
- **Vùng Vịnh:** báo cáo 2008-2009 nói cảnh sát tôn giáo Ả Rập Xê Út thu đồ đỏ trước 14/2 và một nghị sĩ Kuwait đề nghị cấm; **hiện trạng 2025-2026 chưa kiểm chứng**. Cách xử lý an toàn: tắt mọi nhắc dịp lãng mạn mặc định, dùng từ trung tính ("bạn đời"), không chia sẻ ảnh đôi công khai mặc định.
- **Điểm về mô hình dữ liệu:** 5 trong 14 mục (khái niệm `valentine_sensitive`: `br_not_feb14`, `co_not_couple_only`, `mena_sa_valentine`, `mena_kw_valentine`, `mena_unmarried_couples`) **không phải kiêng kỵ quà** mà là cảnh báo về cách nói về dịp. Đề xuất đặt thành `copyNotes` của dịp thay vì `tabooRefs` của quà (xem 6.3.7).

**Cách InLove nên xử lý (chi tiết ưu tiên ở mục 6)**

1. **Bộ dịp theo thị trường thay vì chỉ "Valentine 14/2":** BR 12/6; CO thứ Bảy thứ 3 tháng 9; RU, KZ 8/3 (RU thêm 23/2); các nước còn lại 14/2. Hiện app chưa biết quốc gia của người dùng ở hai chỗ: (a) lịch dịp lễ được gieo theo **ngôn ngữ giao diện** (VI: lễ Việt Nam, còn lại: lễ phương Tây, `OfflineSeeder.kt:188-229`); (b) gợi ý quà theo dịp chỉ có chip "Vùng dịp lễ" VN/INTL do người dùng tự chọn (mặc định trống) và `GetPersonalizedGiftSuggestionsUseCase.kt:46-51` cứng bốn dịp (Valentine, 8/3, 20/10, Giáng Sinh). Vì vậy **người dùng Brazil dùng giao diện tiếng Anh nhận lịch có Valentine 14/2 mà không có 12/6, và gợi ý quà vẫn có thể lấy Valentine 14/2 làm dịp gần nhất (trong 30 ngày trước đó) dù chọn vùng nào, đúng điều mục kiêng kỵ mạnh `br_not_feb14` cảnh báo**. Cần thêm khái niệm thị trường và truyền bộ dịp theo thị trường vào hàm này (mục 5.4 và U-23, mục 6.3).
2. **Nga và Ukraine:** tắt Valentine 14/2 mặc định, bật 8/3 và 23/2 (Nga). Với Ukraine, giữ 8/3 nhưng đánh dấu **cần người bản ngữ rà soát** vì dự luật đổi sang 25/2 chưa rõ kết quả.
3. **Vùng Vịnh (SA, KW):** tắt mọi nhắc dịp lãng mạn, dùng từ trung tính; **không liệt kê vùng này là thị trường được hỗ trợ** cho đến khi có kiểm tra pháp lý và văn hóa tại chỗ (QĐ-5, mục 9). Dữ liệu hiện chưa có quà, dịp cho UAE hay Ai Cập.
4. **Mốc theo tháng:** Brazil gợi ý bật mesversário (1-11 tháng) và để người dùng tắt, các nước Tây Ban Nha ngữ chỉ 1/3/6/9 tháng và mặc định tắt (quyết định mặc định chung: QĐ-3, mục 9).
5. **Cảnh báo hoa:** Nga và Ukraine hiện nhắc "số lẻ"; tránh gợi ý hoa cúc và loa kèn như quà lãng mạn.
6. **Báo trước theo dịp:** Brazil 14 ngày cho 12/6; 7 ngày cho 14/2 và 8/3; 3-5 ngày cho 23/2 và Walentynki.
7. **Nhóm quà mới và lấp lỗ hổng:** "Trải nghiệm đôi" cho AR, MX, CL (bữa tối, spa, đi trong ngày), "Quà làm tay" cho TR, BR; **bổ sung quà cho nam** vì nhóm này không có món nào (cũng là lỗ hổng của dịp 23/2).
8. **Hạng giá theo nội tệ** (BRL, MXN, COP, ARS, PEN, TRY, RUB, KZT, PLN) bằng bậc tương đối `priceTier` 1-4 chứ không quy đổi từ VND; **không hiện mức giá cố định cho ARS, TRY, RUB, UAH** khi chưa có số liệu mới, rà lại mỗi 6 tháng vì lạm phát.
9. **Lời văn Colombia:** "Amor y Amistad" gồm cả bạn bè, thêm lời nhắc "amigo secreto".
10. **Ngôn ngữ và kênh mua (làm sau nữa):** muốn phục vụ thật sự cần thêm tiếng Bồ Đào Nha (Brazil), Tây Ban Nha, Thổ Nhĩ Kỳ, Nga, Ba Lan; chỉ làm khi có dữ liệu Play Console (QĐ-4, mục 9). Link "nơi mua" (Mercado Libre, Trendyol, Hepsiburada, Allegro) là tùy chọn, kèm nhãn affiliate nếu có hoa hồng (U-35, QĐ-8, mục 8). Marketplace là đối thủ thật của gợi ý quà vì họ giữ phần mua hàng, nên InLove chỉ nên đưa ý tưởng và link.

**Chưa kiểm chứng:** ngân sách "thấp" và "cao" của mọi dịp (là ước lượng quanh mức giữa của nguồn); số trái nhau chưa hòa giải (MX: 1.100 và 3.037; RU: 4.500 và 8.000 cho quà 14/2; VCIOM 18%, 34%, 33% giữa các nguồn); mọi kiêng kỵ ở Thổ Nhĩ Kỳ, Brazil, Kazakhstan, hoa cúc, loa kèn, dao, kéo, đồng hồ (từ ký ức); mesversário, "bodas de namoro", mesiversario và mốc 100 ngày (blog hoặc ký ức); **tác động của lệnh trừng phạt, thanh toán và Google Play đối với Nga: không tìm được nguồn, không nên suy diễn về khả năng phân phối app tại Nga**; tình trạng các luật thời chiến của Ukraine đối với dịp lễ; Ba Lan là phần thêm tùy chọn trong yêu cầu giao cho agent nghiên cứu (không nằm trong yêu cầu của chủ app) nên nghiên cứu nông; một số trang chỉ thấy đoạn trích (24tv.ua, Serasa trả HTTP 403).

## 5. App nên xử lý thói quen và kỷ niệm tặng quà thế nào

**Ý chính.** Bộ dữ liệu nháp (87 dịp, 55 cột mốc, 142 quà, 70 kiêng kỵ) cho thấy thói quen tặng quà khác nhau theo ba trục mà app chưa có: *ai tặng ai*, *chuẩn bị trước bao lâu*, và *dịp này có nên đẩy chuyện tặng quà hay không*. Cả ba chỉ cần thêm dữ liệu vào gói nội dung (U-19 đến U-21, mục 6.3) cộng vài điều kiện nhỏ trong code, không cần đổi giao diện lớn. Mọi con số dưới đây đếm từ `docs/research/data/gift-occasion-draft.json` (mục 7); độ tin cậy của chính dữ liệu: 16 dịp `high`, 42 `med`, 29 `low`.

### 5.1 Ba chỉ số khác nhau, không gộp làm một

| Chỉ số | Giá trị | Dùng để làm gì | Phân bố trong 87 dịp |
|---|---|---|---|
| `coupleRelevance` (liên quan cặp đôi) | 0-3 | Quyết định dịp có hiện trong lịch và có bật nhắc mặc định không (`defaultOn`) | 3: 28, 2: 31, 1: 25, 0: 3 |
| `giftExpectation` (kỳ vọng tặng quà) | `none` / `small` / `expected` / `major` | Quyết định giọng nhắc và độ nhấn: có nút "Gợi ý quà" hay chỉ là một ngày để nhớ | expected 36, small 31, none 14, major 6 |
| `defaultOn` | đúng/sai | Dịp có được bật nhắc cho người dùng mới ở thị trường đó | bật 43, tắt 44 |

Hai chỉ số đầu độc lập nhau. Ví dụ: Loy Krathong (Thái) có `coupleRelevance` 2 nhưng `giftExpectation` là `none`, nghĩa là ngày để đi chơi cùng nhau, không phải ngày tặng quà. White Day (Hàn) có `giftExpectation` là `major`: nam giới được kỳ vọng đáp lễ rõ ràng.

### 5.2 Bảy thói quen và cách app nên xử lý

| # | Thói quen | Dữ liệu có sẵn (bản nháp) | Cách app nên xử lý | Code hôm nay |
|---|---|---|---|---|
| 1 | **Ai tặng ai** | `whoGives`: `both` (hai bên cùng tặng) 30, `either` (không có quy ước, bên nào cũng được) 25, `male_to_female` 18, `varies` (tùy cặp) 10, `female_to_male` 4. Ví dụ: Hàn và Nhật, Valentine truyền thống nữ tặng nam (nay nhiều cặp hai chiều), White Day nam tặng nữ; Nga, 23/2 (Ngày Bảo vệ Tổ quốc) nữ tặng nam, 8/3 nam tặng nữ | Hiện thành **mẹo** trên thẻ dịp ("Ở Hàn Quốc, White Day là ngày nam đáp lễ"). Không thêm trường giới tính vào hồ sơ, không dùng như luật lọc. Chỉ 22/87 dịp có chiều một phía (18 nam tặng nữ, 4 nữ tặng nam); 65 dịp còn lại hai chiều hoặc không có quy ước | `GetPersonalizedGiftSuggestionsUseCase` không phân biệt; sinh nhật của cả hai người đều thành "dịp tặng đối phương" (G-04) |
| 2 | **Chuẩn bị trước bao lâu** | `leadTimeDays`: 7 ngày (22 dịp), 3 (17), 14 (14), 5 (11), 21 (6), 0 (6), 10 (5), 30 (2); các mức 1, 2, 4 và 28 ngày mỗi mức một dịp. `bookingAdvised` = đúng ở 33/87 dịp. Ví dụ: Valentine Mỹ, Canada, Anh 21 ngày (đặt nhà hàng), Giáng sinh phương Tây 28, Giáng sinh Nhật 30, Tết 21 | Lấy giờ nhắc mặc định từ dữ liệu của dịp; thêm lựa chọn 14/21/30 ngày; nếu `bookingAdvised` thì lời nhắc ghi thêm "đặt chỗ sớm" | Mọi lễ gieo đều `reminderDaysBefore=3`; lựa chọn chỉ {0,1,3,7} (F04, F10) |
| 3 | **Có nên đẩy chuyện tặng quà** | `giftExpectation`: `none` ở 14 dịp (Loy Krathong, Lantern Festival, Singles Day, đêm giao thừa Tây...), `major` ở 6 (White Day, Thất Tịch, 8/3 vùng Nga-CIS, sinh nhật người yêu ở Việt Nam, Giáng sinh Tây, kỷ niệm cưới Tây) | `none`: chỉ hiện thông tin, ẩn nút "Gợi ý quà". `major`: nhắc sớm hơn một bậc và nút gợi ý nổi bật | Nút "Gợi ý quà" gọi `onNavigateToGifts()` **không truyền dịp** (F14) |
| 4 | **Cách đếm cột mốc** | 55 cột mốc: tính theo năm 35, ngày 12, tháng 8. Ba phong cách: đếm ngày (Hàn 22/100/200/300/1000, Trung 520/1314, Việt 100/1000/1001), đếm tháng (Nhật hằng tháng và nửa năm, Brazil *mesversário*, Philippines *monthsary*, Mỹ Latinh 1/3/6/9, Việt 1/3/6/9), đếm năm (Âu-Mỹ theo tên truyền thống, Ấn, Nga-CIS, Thổ). Hai mốc lặp hằng tháng (Philippines, Indonesia tùy chọn) | Một bảng mốc duy nhất theo thị trường, có đơn vị ngày/tháng/năm và cờ lặp hằng tháng. Mốc N ngày = ngày bắt đầu + (N-1) | Hai bảng mốc, đường production lệch 1 ngày và dùng `×24h` (F01, F-07); không có mốc tháng (F10). Cách đếm "ngày bắt đầu là ngày 1" ở Hàn là **từ trí nhớ, chưa kiểm** |
| 5 | **Kiêng kỵ khi chọn quà** | 70 mục: 15 mạnh, 55 mềm; loại quà 33, hành vi 14, hoa 13, số 6, màu 4. Nhóm khái niệm lặp nhiều nhất: Valentine nhạy cảm (8), hoa trắng/cúc (6), số bông chẵn (5), dao kéo (5), giày (5), đồng hồ (4), số 4/9/13 (4); 20 mục không thuộc nhóm nào. Độ tin cậy: `med` 34, `low` 33, `high` 3 | **Cảnh báo mềm, không ẩn quà.** Gắn theo khái niệm (`tabooRefs`, ví dụ `clock_watch`) chứ không theo từng chữ; hiện thẻ nhỏ "Lưu ý văn hóa" trên chi tiết quà cho người dùng thuộc thị trường đó. Chỉ hiện mục từ `med` trở lên; mục `low` giữ trong dữ liệu, chưa hiện đến khi có người bản địa duyệt (khuyên nhầm cũng là thiếu tôn trọng) | App chưa có khái niệm kiêng kỵ. Seed "Đồng Hồ Đôi" của app chạm đúng `cn_clock` (tặng đồng hồ nghe như "đưa tang") ở Trung Quốc, Đài Loan, Hồng Kông, mức mạnh, tin cậy trung bình |
| 6 | **Tôn giáo và quy định địa phương** | `sensitivity`: không 68, nhạy cảm tôn giáo 11, hạn chế theo nơi 8. Tám mục `valentine_sensitive`: mạnh ở Brazil, Ả Rập Xê Út, Indonesia, Malaysia, Ấn Độ; mềm ở Colombia, Kuwait, và khung "bạn trai/bạn gái" công khai ở vùng Ả Rập | Valentine mặc định **tắt** ở Indonesia, Malaysia, Ả Rập Xê Út, Kuwait; người dùng bật lại được. Brazil: dịp chính là 12/6, không đẩy 14/2. Ấn Độ: Valentine bật kèm ghi chú tránh thể hiện tình cảm nơi công cộng. Lời nhắc không dùng khung "bạn trai/bạn gái" công khai cho Ả Rập (cần trường `copyNotes` của dịp, xem 6.3.7) | Lịch lễ chọn theo ngôn ngữ giao diện, không theo quốc gia (mục 5.4) |
| 7 | **Ngày không cố định** | Quy tắc ngày: cố định dương 61, người dùng tự nhập 7, âm lịch 7, tra bảng theo năm 5, tương đối 3, thứ-trong-tháng 2, theo tuần 2 | Âm lịch: tính bằng thuật toán (mục 7). Tra bảng (Ấn, Hồi giáo, Thái): bảng theo năm, nhãn "dự kiến ±1 ngày", cho người dùng chỉnh 1 ngày, cảnh báo khi bảng sắp hết. Tự nhập: dùng cho ngày riêng của cặp (7 dịp: sinh nhật người yêu, ngày yêu, kỷ niệm cưới, ngày ra mắt gia đình, *monthiversary*) | Bảng âm lịch chỉ tới 2028, Vu Lan đã hết (F06); Rằm tháng Giêng 2027 sai 1 ngày (F07) |

*Lưu ý về mẫu số:* các con số `whoGives`, `leadTimeDays`, `giftExpectation` đếm trên 87 dịp nên 22 dịp "một chiều" gồm cả dịp của một thị trường nhỏ (Ấn, Thổ); đừng đọc thành "22% cặp đôi".

### 5.3 Luật chọn "dịp nào bật mặc định"

**Luật:** `defaultOn = coupleRelevance ≥ 2` **và** áp dụng cho đa số người dùng thị trường đó **và** không bị cổng tôn giáo hay thể chế. Kết quả kiểm bằng script: 43 dịp bật, 44 tắt, và không dịp nào bật mà `coupleRelevance` dưới 2.

**16 dịp có `coupleRelevance ≥ 2` nhưng tắt mặc định**, chia theo lý do:

- *Cổng tôn giáo hoặc quy định địa phương (6):* Valentine Indonesia, và năm ngày của Valentine Week Ấn Độ (Rose, Chocolate, Teddy, Promise, bản thân tuần lễ).
- *Cộng đồng nhỏ hoặc tùy chọn (5):* Chap Goh Mei và Thất Tịch của người Hoa ở Malaysia-Singapore, Diwali, Karwa Chauth (dành cho phụ nữ Hindu đã kết hôn, ngày phụ thuộc thành phố), Loy Krathong (không phải ngày tặng quà).
- *Tình huống cá nhân hoặc theo vùng (5):* 520 và Giao thừa Dương lịch và "đi chơi xuân" ở Việt Nam, ra mắt gia đình (Việt Nam, `coupleRelevance` 3 nhưng chỉ có ý nghĩa khi cặp thực sự ở giai đoạn đó), Sant Jordi (Catalunya).

**5 dịp bật dù mang nhãn nhạy cảm:** Giáng sinh Hàn Quốc, Giáng sinh Trung Quốc-Đài Loan-Hồng Kông (Tặng táo đêm Giáng sinh), đêm Giáng sinh Việt Nam, Giáng sinh phương Tây, và Valentine Ấn Độ. **Mặc định đề xuất: giữ bật** vì ở Hàn, Trung, Việt đây là ngày hẹn hò thế tục; ở phương Tây là lễ phổ thông; ở Ấn Độ Valentine phổ biến ở đô thị nhưng có nơi phản đối nên bật kèm ghi chú an toàn. Lời nhắc dùng chữ trung tính ("Đêm Giáng Sinh", không giảng giải tôn giáo), và người dùng tắt được bằng một chạm trong màn "Dịp của bạn" (một màn duy nhất liệt kê các dịp của thị trường kèm công tắc, thay cho việc xoá từng lễ như `CalendarScreen.kt:298-312`, F16). Đổi sang "mọi dịp nhạy cảm là opt-in" nếu chủ app muốn an toàn hơn và chấp nhận ít nhắc hơn.

### 5.4 Hai cơ chế "vùng" trong code và hậu quả cho người dùng quốc tế

Code hiện có **hai** cơ chế chọn vùng, cả hai đều không biết quốc gia:

| Cơ chế | Chọn bằng gì | Ở đâu | Ghi chú |
|---|---|---|---|
| Lịch lễ gieo vào `anniversary_dates` | **Ngôn ngữ giao diện**: VI = lễ Việt Nam, còn lại = lễ phương Tây | `data/repository/OfflineSeeder.kt:188-229` (dòng 211) | Gieo **một lần** khi bảng rỗng; đổi ngôn ngữ sau đó không đổi lịch (F-01) |
| Dịp của gợi ý quà | Chip "Vùng dịp lễ" (Việt Nam / Quốc tế) do **người dùng tự chọn**, mặc định rỗng | `GiftScreen.kt:1281-1388`; cột `couple_profile.occasionRegion` (`Entities.kt:102`, migration v13→v14 `Migrations.kt:159-169`) | Bộ dịp là 4 dịp cố định viết cứng trong `GetPersonalizedGiftSuggestionsUseCase.kt:46-51`; Quốc tế chỉ bỏ 8/3, 20/10 và Tết |

**Ví dụ hỏng:** một người Brazil dùng giao diện tiếng Anh nhận lịch phương Tây (có Valentine 14/2, không có Dia dos Namorados 12/6). Trong gợi ý quà, dịp gần nhất vẫn có thể là Valentine 14/2 bất kể chọn vùng nào, trái với kiêng kỵ mạnh `br_not_feb14` trong dữ liệu.

**Hướng xử lý (chi tiết ở mục 6):** một `MarketProfile` duy nhất (quốc gia, ngôn ngữ, tiền tệ, lịch, bộ dịp mặc định), suy ra từ vùng của máy và cho người dùng sửa, dùng cho cả gieo lịch, gợi ý quà, nhãn giá và lời nhắc. Cột `occasionRegion` giữ nguyên (giá trị cũ `VN`/`INTL` ánh xạ trực tiếp; mã quốc gia mới ghi vào cùng cột văn bản), nên bước này không cần migration. Đã kiểm trong mã: cột là `TEXT NOT NULL DEFAULT ''`, không ràng buộc giá trị (`Migrations.kt:169`, `Entities.kt:102`), và ba cột sở thích đối phương (`likesCsv`, `budgetMaxVnd`, `occasionRegion`) chỉ nằm ở lớp lưu trữ cục bộ, `OnlineCoupleRepository.kt:679-681` chỉ giữ nguyên giá trị cục bộ khi áp hồ sơ từ xa. Việc phải sửa là chỗ đọc: `InLoveViewModel.kt:608` và `GetPersonalizedGiftSuggestionsUseCase` hiện chỉ hiểu rỗng, `VN` và `INTL`.

### 5.5 Quà: người nhận, mức giá, hình thức

- **Người nhận lệch một phía.** 142 quà: `any` 109, `her` 32, `him` **1** (chocolate honmei của Nhật). Trong khi dữ liệu có 4 dịp "nữ tặng nam" (Valentine Hàn, Valentine Nhật, 23/2 Nga, Boyfriend Day Mỹ). 20 seed của app không có trường người nhận (`GiftSeed` chỉ có tiêu đề, nhãn, mô tả, dịp, giá, ảnh), nên không lọc được theo người nhận. Cách xử lý: dùng trường `recipient` (đã có trong dữ liệu) **chỉ để xếp hạng** ở dịp một chiều, không hỏi giới tính; bổ sung khoảng 15 quà "him" ở vòng dữ liệu kế tiếp (U-37, chỉ là dữ liệu).
- **Mức giá.** 142 quà: bậc 1 là 37, bậc 2 là 58, bậc 3 là 35, bậc 4 là 12. 20 seed: dưới 200k có 7, 200-500k có 4, 500k-1tr có 4, trên 1tr có 5 (bậc trên cùng không có giới hạn trên, nên không tách được quà cao cấp). Luật: lọc theo **bậc** rồi hiển thị khoảng giá bằng tiền địa phương (`tierBands`), không quy đổi tỷ giá.
- **Hình thức.** `buy_ready` 59, `experience` 36, `personalized_order` 26, `diy` 13, `digital` 8. Trục "ngôn ngữ tình yêu" (mỗi quà gắn một hoặc nhiều nhãn, nên tổng lớn hơn 142): quà vật chất 104, thời gian bên nhau 49, lời nói 31, hành động phục vụ 8, chạm 3. Tức là thiên về mua đồ; ghi nhận để bổ sung quà trải nghiệm và hành động ở vòng sau. **Không** dựng tính năng "ngôn ngữ tình yêu" riêng (YAGNI; chính dữ liệu có cảnh báo `west_love_language_overclaim` rằng cách nói này bị thổi phồng).
- **Từ vựng.** Danh mục trong dữ liệu mới có 10 nhóm, chỉ 4 trùng 5 nhóm của app; sở thích có cặp lặp (`jewellery`/`jewelry`, `home`/`home_decor`). Phải chuẩn hóa thành khóa ổn định khi nạp vào gói (U-06, U-20 và 6.3.6), nếu không lại lặp lỗi F-03 (khóa nghiệp vụ là chữ hiển thị tiếng Việt).

### 5.6 Vòng đời một món quà: app đang dừng ở đâu

| Bước | Hôm nay | Đề xuất |
|---|---|---|
| 1. Nhắc dịp | Có (đường cũ và planner mới) | Sửa F01-F04 (U-04, U-05, U-13, U-02; cả bốn mức P1) |
| 2. Gợi ý quà cho dịp đó | Một phần: nút không truyền dịp (F14), dịp hard-code (G-05) | Truyền khóa dịp sang màn quà (S) |
| 3. Lưu yêu thích, hẹn nhắc mua | Có (`toggleGiftFavorite`, `gift_reminders`) | Sửa G-15 (ghi đè trường) |
| 4. Mua hoặc đặt | **Không có** link, không có thông tin "mua ở đâu" (G-08, F-18) | Hiện `whereToBuy` từ dữ liệu như chữ thuần, **chưa** gắn affiliate (mục 8) |
| 5. Đánh dấu "đã tặng" và ghi phản ứng | **Không có** | U-29 (mục 6.4), cỡ M: `GiftReminderEntity` (`Entities.kt:177-190`) không có khoá quà trong catalog và `isCompleted` nghĩa là "đã chuẩn bị", nên cần cột hoặc bảng mới (một migration cộng thêm, gộp vào lần nâng v15, xem 6.5) |
| 6. Năm sau không tặng trùng | **Không có** | Hệ quả của bước 5 |

Hướng đi tối thiểu: làm bước 2 và hiện `whereToBuy` trước (không đổi schema), bước 5 chỉ làm khi chủ app duyệt.

## 6. Nâng cấp có ưu tiên

Mục này biến các phát hiện ở mục 3 và nhu cầu thị trường ở mục 4-5 thành 38 việc có số hiệu (U-01 đến U-38). **Chưa việc nào được làm trong app.** Mỗi việc cần chủ app duyệt rồi đi qua quy trình L (kế hoạch, review, build, kiểm). Đường dẫn trong mục này theo quy ước ở đầu mục 3.

### 6.0 Nguyên tắc

1. **Dữ liệu trước, mã sau.** Nội dung (quà, dịp lễ, cột mốc) đi ra khỏi mã biên dịch thành gói nội dung có phiên bản; mã chỉ đọc gói. Mỗi thị trường mới là một gói dữ liệu, không phải một đợt sửa mã.
2. **Đổi khoá và mô hình dữ liệu TRƯỚC khi chuyển nguyên văn sang `shared` (D-13).** Kế hoạch KMP/iOS (`docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md`, D-13) chuyển `GiftIdeasSeed`, `GiftRanker`, `GiftCatalog`, `ContentModels`, `ContentRepository` sang commonMain "nguyên văn". Đổi khoá hay điểm số sau bước đó nghĩa là cập nhật golden hai lần, và bước chuyển nguyên văn không được trộn thay đổi hành vi. Vì vậy U-03 và U-22 đứng trước D-13.
3. **Không đổi schema Room trừ khi nêu ở 6.5.** Nếu cần, gộp thành MỘT lần nâng v14 lên v15 (có tương tác với D-15, xem QĐ-2).
4. **Chưa có liên kết tiếp thị (affiliate).** `whereToBuy` giữ là chữ thường. Lý do ở mục 8 và QĐ-8.
5. **Luật trước, LLM sau.** Gợi ý quà hiện chạy bằng luật và mẫu có sẵn (app có phụ thuộc `firebase-ai` nhưng không có lời gọi mô hình nào, xem F-13). Nếu sau này có LLM thì là tuỳ chọn cho gói VIP (QĐ-7, QĐ-10).
6. **Lint không được tăng cảnh báo.** Các kế hoạch trước ghi nhận 0 lỗi và 128-131 cảnh báo (số ghi trong kế hoạch trước, **chưa chạy lại hôm nay**). Mọi việc dưới đây phải giữ nguyên hoặc giảm số này.
7. **Golden là hợp đồng.** Việc nào đổi hành vi có golden (6.5) phải chạy lại bằng `GOLDEN_UPDATE=1`, đọc từng dòng khác biệt và ghi lý do. Không sửa golden để cho test xanh.

### 6.1 Thang đo, bảng việc và thứ tự

**Cỡ việc** (định nghĩa một lần, theo PHẠM VI, không theo thời gian; không có ước lượng giờ và không trùng với mức P0-P3 của phát hiện):

- **S**: 1-3 file, không đổi schema hay API, một test đi kèm.
- **M**: 4-10 file hoặc một thành phần mới.
- **L**: xuyên suốt nhiều chỗ, hoặc cần việc bên ngoài (bản địa hoá có người bản xứ, rà soát pháp lý).

| Việc | Nội dung | Phát hiện | Ở đâu | Đợt | Cỡ | Phụ thuộc |
|---|---|---|---|---|---|---|
| U-01 | Sửa ngày Rằm Giêng 2027 (19/2 thành 20/2) | F07 | `data/seed/VietnameseHolidays.kt:86`, `assets/content/holiday_calendar.json:163`, GV-013, GV-015 | 1 | S | — |
| U-02 | Chỉ bật nhắc mặc định cho lễ liên quan cặp đôi (cài mới) | F04 | `data/repository/OfflineSeeder.kt:193-203,212-227`, `data/model/Entities.kt:166-167` | 1 | S | — |
| U-03 | Gỡ nhãn và điểm "AI" khỏi gợi ý quà | G-14, G-07, G-20, F-15, F-13 | `domain/GiftRanker.kt:35`, `res/values/strings.xml:165,166,176`, `ui/util/Localization.kt:436,446,652,653,663`, `scripts/seed_firestore.js:123,331-342`, GV-016, GV-029 | 1 | M | — (trước D-13) |
| U-04 | Một bảng cột mốc duy nhất, lập lịch theo `LoveMilestoneRules` | F01, F11, F-07 | `alarm/LoveAnniversaryMilestoneScheduler.kt:46-59,96`, `alarm/plan/LoveMilestoneRules.kt:25-38,51-55` | 1 | S | — |
| U-05 | Chữ thông báo cột mốc đi qua `ReminderTexts` (VI/EN), sửa "24 giờ" lệch với 09:00 | F02, F12 | `alarm/LoveAnniversaryMilestoneScheduler.kt:109,194-195,215-216,236-237`, `alarm/AlarmNotificationScheduler.kt:333,359-360`, `alarm/plan/ReminderTexts.kt:62,99` | 1 | S | U-04 |
| U-06 | Gán khoá sở thích chuẩn cho 20 quà gốc | G-02, G-17 | `data/seed/GiftIdeasSeed.kt:52-65`, `ui/viewmodel/InLoveViewModel.kt:822` | 1 | S | — |
| U-07 | Cố định locale khi đọc và lưu ngày | F-08 | 15 chỗ `SimpleDateFormat(` (bảng ở mục 3.3) | 1 | M | — |
| U-08 | Đánh dấu "thích" quà bằng một câu UPDATE riêng | G-15 | `data/db/InLoveDao.kt:73-85`, `data/repository/InLoveRepository.kt:108-110` | 1 | S | — |
| U-09 | Thêm Noel 24/12 vào lịch Việt Nam, bật mặc định | F08 | `data/seed/VietnameseHolidays.kt:35-51`, `data/repository/OfflineSeeder.kt:211-219` | 1 | S | U-02 |
| U-10 | Gợi ý dự phòng khi chưa có sở thích hay dịp gần | G-06, G-04 | `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:135-167` | 1 | S | — |
| U-11 | Dọn quà `tpl_*`, bỏ nhãn "AI" của bộ mẫu, dịch EN | G-13, G-07 | `data/repository/GiftSuggestionGenerator.kt:15-106`, `ui/viewmodel/InLoveViewModel.kt:1270,1283` | 1 | S | U-03 |
| U-12 | Seed quà chỉ ghi hàng khác bản đang có; sửa thứ tự `hasGifts` | G-09, G-10, G-12 | `data/repository/OfflineSeeder.kt:32-45,52-66` | 1 | M | — |
| U-13 | Nhắc không mất sau khởi động lại, có lối cấp quyền báo thức chính xác | F03, F09, F18, F19 | `alarm/BootCompletedReceiver.kt:43`, `AndroidManifest.xml:8-11`, `ui/viewmodel/InLoveViewModel.kt:911`, `alarm/AlarmNotificationScheduler.kt:222-233`, `alarm/AndroidReminderScheduler.kt:122`, `alarm/ReminderAlarmReceiver.kt:52,116`, `data/repository/OfflineSeeder.kt:26` | 1 | M | — |
| U-14 | `today` tính một lần và theo thay đổi; lịch tôn trọng năm | G-23, F15 | `ui/viewmodel/InLoveViewModel.kt:587,606`, `ui/screens/CalendarScreen.kt:142` | 1 | S | — |
| U-15 | Nút quà ở lịch mang theo dịp; sửa cách chọn dịp gần nhất | G-03, G-05, F14 | `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:106-133`, `ui/screens/CalendarScreen.kt:266,941` | 1 | M | — |
| U-16 | Thêm mốc nhắc sớm và mặc định theo dịp; thêm cột mốc ngày bằng dữ liệu | F10 | `ui/screens/Dialogs.kt:2374,2550,2559`, `alarm/plan/LoveMilestoneRules.kt:25-38` | 1 | S | U-04 |
| U-17 | Bảng âm lịch quá 2028 và cảnh báo hết dữ liệu | F06, F20, F-16 | `data/seed/VietnameseHolidays.kt:64-125`, `data/seed/HolidayDates.kt:16,30-48` | 1 | M | U-01 |
| U-18 | Vùng chạm 48 dp (gồm nút chia sẻ của thẻ cá nhân hóa và bộ chọn vùng, cả hai đang 40 dp), nhãn VIP, mô tả EN, dọn 7 chuỗi `gift_cat_*` cùng 7 trường `AppStrings.giftCat*` không màn nào đọc | G-18, G-19, G-21, G-22 | `ui/screens/GiftScreen.kt:401,528,537,1087,1163-1189,1244,1346`, `ui/screens/Dialogs.kt:639,825`, `res/values/strings.xml:197-203` (và `values-vi`), `ui/util/AppStrings.kt:165-171`, `ui/util/Localization.kt:237-243,463-469,680-686` | 1 | M | — |
| U-19 | `ContentPack` (mô hình) và bộ kiểm hợp lệ | G-11, F-05 | mới (commonMain, commonTest, `scripts/`) | 2 | M | — |
| U-20 | Bộ sinh gói từ dữ liệu hiện có, kiểm khứ hồi với golden | F-09, G-02 | mới; nguồn `GiftIdeasSeed.kt`, `VietnameseHolidays.kt`, `WesternHolidays.kt`, `LoveMilestoneRules.kt` | 2 | M | U-19 |
| U-21 | `PackContentRepository` thay cho seed trong mã | F05, F-01, F-05, F-06, F-09, G-10 | `domain/content/ContentRepository.kt:15-31`, `data/repository/CloudPresetFetcher.kt:179-241` | 2 | M | U-19, U-20 |
| U-22 | Khoá ổn định cho quà và `Price(currency,min,max)` | G-01, G-24, F-03, F-04, F-17 | `data/seed/GiftIdeasSeed.kt:49-50`, `domain/content/GiftCatalog.kt:96-112`, `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:80-86`, `data/db/AccountDataVault.kt:241-260` | 2 | M | U-03 |
| U-23 | `MarketProfile` thay cho `occasionRegion` | G-05, F08, F-01, F-10 | `ui/screens/GiftScreen.kt:1281-1388`, `ui/viewmodel/InLoveViewModel.kt:608,826`, `data/repository/InLoveRepository.kt:92-93`, `data/model/Entities.kt:98-102` | 2 | M | U-21 |
| U-24 | Bỏ `isEnglish` (678 chỗ) và `tr()` (56 chỗ trong `InLoveViewModel.kt`), dùng mã ngôn ngữ BCP-47 | F-02, F-11, F-12 | 30 file, nhiều nhất `ui/screens/Dialogs.kt`; `ui/util/Localization.kt:8-10` | 2 | L | U-23 |
| U-25 | Script đẩy gói lên Firestore, luật đọc, test luật | G-11, G-20, F-05 | `firestore.rules:310-313` (mẫu), `scripts/seed_firestore.js` | 2 | S | U-19 |
| U-26 | Thị trường thứ hai thật sự (dữ liệu, bản địa hoá, rà soát) | F08 | gói dữ liệu mới | 2 | L | U-20, U-21, U-23, U-24 |
| U-27 | Tuỳ chọn: cột `contentKey` cho `anniversary_dates` | F17, F20 | `data/db/Migrations.kt`, `data/model/Entities.kt`, `data/sync/AnniversarySyncAdapter.kt`, `firestore.rules:183-191` | 2 | M | U-21 |
| U-28 | Nhiều đơn vị tiền: `budgetMax` và `budgetCurrency` | G-01, F-04 | `data/model/Entities.kt:100`, `data/db/Migrations.kt:165-171`, `domain/content/GiftCatalog.kt:96-105` | 2 | M | U-22 |
| U-29 | Lịch sử quà "đã tặng" và phản ứng | G-08 | `data/model/Entities.kt:177-190`, `ui/viewmodel/InLoveViewModel.kt:1797`, `ui/screens/GiftScreen.kt:699-805` | 3 | M | U-22 |
| U-30 | Mở rộng hộp thoại chi tiết quà (`GiftDetailDialog` đã có): cảnh báo mềm, nơi mua, thời gian chuẩn bị; làm thẻ gợi ý cá nhân hóa bấm được và hiện `reason`/`preparation` | G-08 | `ui/screens/Dialogs.kt:581-763`, `ui/screens/GiftScreen.kt:331-343,1215-1260` | 3 | M | U-21 (phần thẻ không cần) |
| U-31 | Bấm thông báo mở đúng dịp hoặc đúng màn quà | F12, F13, F14 | `alarm/ReminderAlarmReceiver.kt:207,215-251` | 3 | M | U-15 |
| U-32 | Màn "Dịp của bạn" có công tắc thay cho xoá từng lễ | F16 | `ui/screens/CalendarScreen.kt:298-312` | 3 | M | U-02, U-21 |
| U-33 | Cột mốc theo tháng và lựa chọn "ngày đầu hẹn hò hay ngày chính thức" | — | `alarm/plan/LoveMilestoneRules.kt` | 3 | M | U-04 |
| U-34 | Cho chọn giờ nhắc | F10 | `alarm/plan/LoveMilestoneRules.kt:94` | 3 | M | U-13 |
| U-35 | Nút "tìm quà" và nơi mua (chưa có affiliate) | G-08, F-18 | `ui/screens/GiftScreen.kt` | 3 | M | U-21 |
| U-36 | Ảnh quà tự lưu trữ | G-16, F-14 | `data/seed/GiftIdeasSeed.kt:50` | 3 | L | U-21 |
| U-37 | Giọng trung tính, vai "mình / người ấy", bộ quà "anh ấy", "chúng mình không tặng quà lễ" | G-04 | `ui/viewmodel/InLoveViewModel.kt:268,271,283,286,588-598` | 3 | L | U-23 |
| U-38 | Rà soát font và chữ phải-sang-trái | F-12 | toàn bộ UI | 3 | M | U-24 |

**Thứ tự đề xuất.** Mười tám việc đợt 1 độc lập với nhau, trừ các phụ thuộc ghi trong bảng.

1. ★ **U-01**: sai ngày, nhỏ, và nên làm trước khi D-13 và DEC-64 khoá hành vi hiện tại.
2. ★ **U-04 cùng U-05**: cùng một file; sửa lệch ngày và chữ một lần.
3. ★ **U-06**: làm cho "gợi ý theo sở thích" có tác dụng với 20 quà gốc.
4. ★ **U-03 (kèm U-11)**: gỡ nhãn "AI" khi app không gọi mô hình AI nào; làm trước D-13.
5. ★ **U-07**: ngày theo locale máy, chưa kiểm chứng trên máy thật; phải xong trước khi mở Thái Lan hay Ả Rập.
6. ★ **U-02 (kèm U-09)**: nhắc mặc định chỉ cho lễ liên quan cặp đôi.
7. **Cân nhắc đưa U-13 lên cùng nhóm**: đây là việc về độ tin cậy (nhắc không đến sau khi khởi động lại hoặc khi thiếu quyền), tác động lên mọi người dùng.
8. **Cân nhắc làm cùng nhóm: phần thẻ của U-30** (cỡ S, không cần U-21, xem 6.4): làm cho thẻ gợi ý cá nhân hóa bấm được và hiện `reason`/`preparation` (G-08). Tách phần này khỏi U-30 để làm trong đợt 1 là đề xuất của tài liệu; 6.1 vẫn xếp U-30 ở đợt 3.
9. Còn lại của đợt 1 (U-08, U-10, U-12, U-14, U-15, U-16, U-17, U-18): nhặt dần, không có thứ tự cứng.
10. **Đợt 2** (U-19 đến U-28) đi theo 12 bước ở 6.3; bắt đầu khi chủ app chốt QĐ-1 (thị trường thứ hai) và QĐ-2 (thời điểm KMP/iOS).
11. **Đợt 3** (U-29 đến U-38) làm sau khi có gói nội dung.

U-15 và phần khoá sở thích của U-06 là **cầu nối tạm**: gói nội dung (U-20) thay thế cả hai, nên không nên đầu tư thêm vào chúng ngoài phần tối thiểu.

**Không làm hoặc hoãn** (đã cân nhắc, có lý do):

- Đổi lời 8/3 và 20/10 thành "chỉ dành cho mẹ" (mục 4.1, mục 7).
- Black Day, Rose Day, Silver Day, ngày 3/14: hoãn (mục 4.2).
- Liên kết tiếp thị (affiliate): chờ rà soát UMP, Data Safety của Google Play và ATT của Apple (QĐ-8).
- Gọi LLM và dùng `firebase-ai`: chờ quyết định gói VIP (QĐ-7, QĐ-10).
- Nhắc lãng mạn cho Ả Rập Xê Út và Kuwait: chưa hỗ trợ cho tới khi có rà soát pháp lý và văn hoá (QĐ-5).
- Tính năng "ngôn ngữ tình yêu" riêng: chưa cần (YAGNI); dữ liệu nháp đã có thẻ `loveLanguages` để dùng sau.
- Quy đổi tỷ giá và giá cố định cho ARS, TRY, RUB, UAH: dùng bậc giá theo từng thị trường, rà soát mỗi 6 tháng.
- Migration dùng `RENAME COLUMN`: SQLite trên Android 7-10 cũ hơn 3.25 (chưa kiểm chứng); dùng cột mới và sao chép giá trị.
- Remote Config cho gói nội dung: dùng tài liệu Firestore có `sha256` (6.3).
- Suy đoán hay lưu tôn giáo của người dùng; thêm thư viện mới.

### 6.2 Đợt 1: sửa nhanh và độ tin cậy

Mười tám việc dưới đây thuộc đợt 1, mỗi việc có bốn mục: **Việc** (làm gì), **Ở đâu** (file và dòng, đối chiếu với cây mã ngày 2026-10-26), **Kiểm** (test tối thiểu phải đi kèm) và **Rủi ro** (điều có thể hỏng). Quy ước đường dẫn ngoài quy ước ở đầu mục 3: test tính từ `app/src/test/java/com/example/`; golden nằm ở `shared/src/commonTest/resources/golden/`, tạo lại bằng `GOLDEN_UPDATE=1` rồi đọc từng dòng khác biệt (6.0, nguyên tắc 7); `res/`, `assets/` và `AndroidManifest.xml` tính từ `app/src/main/`; `scripts/` và `firestore.rules` tính từ gốc repo.

#### U-01 — Sửa ngày Rằm Giêng 2027 (F07, cỡ S)

- **Việc:** đổi `2027 to LocalDate.of(2027, 2, 19)` thành `(2027, 2, 20)`. Tết 2027 mùng 1 là 06/02 nên mùng 15 là 20/02; HKO, ngaydep.com, baolamdong.vn và thuật toán Hồ Ngọc Đức cùng cho 20/02 (mục 7). Sửa cùng lỗi ở `assets/content/holiday_calendar.json:163` (chưa mã nào đọc file này).
- **Ở đâu:** `data/seed/VietnameseHolidays.kt:86`. Golden bị ảnh hưởng:
  - **GV-013**: 10 dòng `2027-02-19` trong 5 ca `table-dump-5-entries`, `vu-lan-no-upcoming-date-2026-10-13`, `trung-thu-after-date`, `ram-thang-gieng-2026-2027`, `sweep-today-2026-12-31`, mỗi ca ở cả khối `expected` lẫn `androidLegacy`. Riêng ca `ram-thang-gieng-2026-2027` (`golden/HolidayGoldenTest.kt:164`), giá trị của ngày `2027-02-20` đổi từ `null` thành `"2027-02-20"` ở cả hai khối.
  - **GV-015**: ca `multiple-rows-one-pass`, 2 dòng `19/02/2027` đổi thành `20/02/2027`; `updateCalls` vẫn là 4.
  - **GV-204** không đổi.
- **Kiểm:** chạy `GOLDEN_UPDATE=1`, xác nhận chỉ các dòng trên đổi; thêm một ca đơn vị "Rằm Giêng 2027 = 20/02/2027".
- **Rủi ro:** thấp. Hàng đã gieo tự sửa nhờ `HolidayDates.refreshExisting` (chạy lúc mở app ở `data/repository/OfflineSeeder.kt:179` và trong kiểm tra buổi sáng ở `alarm/ReminderAlarmReceiver.kt:51`), trừ hàng người dùng đã đổi tên (F20). Làm trước khi D-13 và DEC-64 khoá hành vi hiện tại.

#### U-02 — Chỉ bật nhắc mặc định cho lễ liên quan cặp đôi (F04, cỡ S)

- **Việc:** truyền `notificationEnabled = h.isCoupleRelevant` vào `toEntity`. Hiện cả 20 lễ Việt Nam và 13 lễ phương Tây đều bật nhắc, vì entity có mặc định `notificationEnabled = true`, `reminderDaysBefore = 3` còn `toEntity` không truyền cờ nào. Sau sửa: Việt Nam 7 bật / 13 tắt, phương Tây 6 bật / 7 tắt.
- **Ở đâu:** `data/repository/OfflineSeeder.kt:193-203` (hàm `toEntity`), nơi gọi `:212-227`; `data/model/Entities.kt:166-167`; cờ `isCoupleRelevant` ở `data/seed/VietnameseHolidays.kt:32,59` và `data/seed/WesternHolidays.kt:31,52`.
- **Kiểm:** test MỚI cho cờ. Không test nào gọi `buildHolidayAnniversaries` trực tiếp hay ghim cờ thông báo; `CloudEnrichmentDataTest.kt:146-164` chỉ chạm gián tiếp (kiểm số lượng, ngày parse được, tiêu đề "Tết Dương Lịch"). Không golden nào đổi (GV-204 không ghim cờ thông báo).
- **Rủi ro:** chỉ áp dụng cho cài mới vì bảng lễ chỉ gieo khi rỗng (`OfflineSeeder.kt:175-177`, xem F05). Không ai mất nhắc vì nút chuông ở `ui/screens/CalendarScreen.kt:1055-1067` (`ui/viewmodel/InLoveViewModel.kt:1674`, hàm `toggleAnniversaryNotification`) bật lại được từng lễ. Màn "Dịp của bạn" đầy đủ là U-32.

#### U-03 — Gỡ nhãn và điểm "AI" khỏi gợi ý quà (G-14, G-07, G-20, F-15, F-13 tuỳ chọn, cỡ M)

- **Việc:** app không gọi mô hình AI nào (F-13) nhưng đang quảng bá "AI" và cộng điểm cho hàng `isAiGenerated`. Bỏ +1 điểm cho `isAiGenerated` (hàng `tpl_*` hiện xếp cao hơn đúng 1 điểm); bỏ các câu quảng bá "AI" trong chuỗi giao diện; trong `scripts/seed_firestore.js` bỏ `badgeText: '98% Cặp đôi mê mẩn'` (huy hiệu bịa), `category: 'AI Đề Xuất ✨'` và `priceRange: 'Miễn phí với VIP'`. **Giữ** khoá nội bộ `AI_KEY` (kèm ánh xạ biểu tượng ở `ui/screens/GiftScreen.kt:1196`) và nhãn chip "Cá nhân hóa ✨" (đã không có chữ AI, kiểm bởi `domain/content/GiftCatalogTest.kt:46,106,109`) và các chỗ dùng `isAiGenerated` khác (`domain/content/ContentModels.kt:24`, `data/model/Entities.kt:46`, `data/seed/GiftIdeasSeed.kt:62`, `data/repository/CloudPresetFetcher.kt:212,227`, `ui/screens/GiftScreen.kt:1012,1112,1118`).
- **Ở đâu:** `domain/GiftRanker.kt:35`; `res/values/strings.xml` và `res/values-vi/strings.xml` dòng 165, 166, 176; `ui/util/Localization.kt:436,446,652,653,663` (chỗ đọc `:209-210,220`); comment `ui/screens/SettingsScreen.kt:267`; `scripts/seed_firestore.js:123,331-342`.
- **Kiểm:** test ranker "hai quà giống nhau cùng điểm dù một cái `isAiGenerated`". Golden GV-016 (30 mục `isAiGenerated: true`) và GV-029 (5 mục) coi đó là đầu vào, chỉ thứ hạng có thể đổi: tạo lại và đọc từng diff.
- **Rủi ro:** thấp về hành vi nhưng phải làm TRƯỚC D-13 (6.0, nguyên tắc 2). Phụ thuộc `firebase-ai` không có lời gọi (F-13) là QĐ-10, không làm ở đây.

#### U-04 — Một bảng cột mốc duy nhất, lập lịch theo `LoveMilestoneRules` (F01, F11, F-07, cỡ S)

- **Việc:** xoá `STANDARD_DAY_MILESTONES` (12 mốc, chỉ tiếng Việt) và dựng lịch từ `alarm/plan/LoveMilestoneRules.kt` (`DAY_MILESTONES`, cùng 12 giá trị, có VI và EN). Đường cũ tính `start + N × 24 giờ` (`alarm/LoveAnniversaryMilestoneScheduler.kt:96`) nên báo mốc 100 ngày vào ngày thứ 101 so với `loveDaysOn` (`ui/util/ProfileUtils.kt:229`, ngày bắt đầu là ngày 1) và lệch khi qua giờ mùa hè; planner tính `start + (N − 1)` ngày (`alarm/plan/LoveMilestoneRules.kt:51-55`).
- **Ở đâu:** `alarm/LoveAnniversaryMilestoneScheduler.kt:46-59,96`; `alarm/plan/LoveMilestoneRules.kt:25-38,51-55`.
- **Kiểm:** test khớp `loveDaysOn` cho 100, 365, 1000 và ngày bắt đầu 29/2; mở rộng `alarm/plan/LoveMilestoneRulesTest.kt`. Với 29/2 năm không nhuận, planner kẹp về cuối tháng qua `occurrenceDate` (`alarm/plan/LoveMilestoneRules.kt:47`) còn đường cũ (lenient) tràn sang 1/3: chốt theo planner.
- **Rủi ro:** báo thức đã đặt dời 1 ngày sau lần đồng bộ lại kế tiếp (U-13 giúp đồng bộ sau khởi động). Mốc 365 và 730 trùng mốc năm (F11) nên giữ một thông báo mỗi ngày.

#### U-05 — Chữ thông báo cột mốc đi qua `ReminderTexts` (F02, F12, cỡ S; sau U-04)

- **Việc:** thay chữ tiếng Việt viết cứng bằng `ReminderTexts` (đã có VI và EN). Sửa "Chỉ còn 24 giờ nữa": thông báo bắn lúc 09:00 hôm trước, không phải 24 giờ; đổi thành "Ngày mai là ..." / "Tomorrow is ...". Người dùng EN thôi nhận thông báo tiếng Việt.
- **Ở đâu:** chữ cứng ở `alarm/LoveAnniversaryMilestoneScheduler.kt:109,194-195,215-216,236-237` và `alarm/AlarmNotificationScheduler.kt:333,359-360`; bản dịch có sẵn ở `alarm/plan/ReminderTexts.kt` (VI `:42,47,57,62`, EN `:83,87,95,99`; chữ "24 giờ" ở `:62` và `:99`).
- **Kiểm:** test chuỗi VI và EN cho ngày-đúng, 1 ngày, 3 ngày (ví dụ trong `alarm/ReminderSchedulingTest.kt`).
- **Rủi ro:** thấp; làm cùng U-04 vì cùng file.

#### U-06 — Gán khoá sở thích chuẩn cho 20 quà gốc (G-02, G-17, cỡ S)

- **Việc:** thêm `interestKeys` chuẩn hoá vào 20 seed và ghi vào cột `targetInterests` (chuỗi dấu phẩy, đã có sẵn nên không đổi schema), dùng 10 khoá giao diện. Hiện `GiftSeed` không có trường này nên cột luôn rỗng, sở thích chỉ ăn +1 qua so khớp chữ và không bao giờ ăn +3. Cho `parseLikes` bỏ dấu và không phân biệt hoa thường (G-17).
- **Ở đâu:** `data/seed/GiftIdeasSeed.kt:52-65` (`toEntity`); `ui/viewmodel/InLoveViewModel.kt:821-823`; điểm ở `domain/GiftRanker.kt:30-37`.
- **Kiểm:** test ranker "cà phê"/"coffee" đẩy đúng quà lên đầu. Giữ xanh `domain/content/GiftCatalogTest.kt`, `data/repository/PresetDedupeTest.kt`, `CloudEnrichmentDataTest.kt` (20 hàng, `remoteId` không đổi) và `ui/testing/NoNetworkImagesRule.kt`. GV-016 và GV-029 không đổi vì bộ sinh golden không dùng `GiftIdeasSeed` thật.
- **Rủi ro:** thấp. Đây là cầu nối tạm: gói nội dung (U-20) thay bằng 23 khoá chuẩn (6.3), nên chỉ làm phần tối thiểu.

#### U-07 — Cố định locale khi đọc và lưu ngày (F-08, cỡ M)

- **Việc:** có 15 chỗ `SimpleDateFormat(` trong 10 file (đếm 2026-10-26); chỉ `ui/util/ProfileUtils.kt:214` đã dùng `Locale.ROOT`. 6 chỗ đọc/lưu (trong 5 file) đổi `Locale.getDefault()` thành `Locale.ROOT` để chuỗi lưu luôn là lịch Gregory, chữ số ASCII (DEC-75, GV-002). 8 chỗ hiển thị giữ locale người dùng nhưng ép lịch Gregory và chữ số Latin.
- **Ở đâu:**
  - Đọc/lưu: `alarm/AlarmNotificationScheduler.kt:81`, `alarm/LoveAnniversaryMilestoneScheduler.kt:73`, `ui/util/ProfileUtils.kt:13`, `ui/components/Material3DatePicker.kt:99,109`, `ui/components/LoveDaysCalculatorWidget.kt:110`.
  - Hiển thị: `alarm/AlarmNotificationScheduler.kt:503`, `alarm/LoveAnniversaryMilestoneScheduler.kt:90`, `ui/components/AnniversaryMemoriesWidget.kt:858`, `ui/screens/MemoriesGridScreen.kt:628`, `ui/screens/Dialogs.kt:1003,2368`, `ui/screens/AccountSecurityDialogs.kt:364`, `ui/screens/PairingScreen.kt:143`.
- **Kiểm:** test Robolectric đặt th-TH, ar, ja-JP, xác nhận chuỗi lưu giống hệt và đọc lại được. Hành vi thật trên máy **chưa kiểm chứng**.
- **Rủi ro:** nếu xác nhận có lỗi thì nâng lên P0 trước khi mở Thái Lan (U-26). Lỗi có thể đã xảy ra với người dùng máy Thái hay Ả Rập: giao diện bị ép EN nhưng `Locale.getDefault()` vẫn là locale của máy.

#### U-08 — Đánh dấu "thích" quà bằng một câu UPDATE riêng (G-15, cỡ S)

- **Việc:** thêm `@Query("UPDATE gift_ideas SET isFavorited = :fav WHERE remoteId = :remoteId")` vào DAO và cho `toggleGiftFavorite` dùng nó, thay cho đọc-sửa-ghi cả hàng (`dao.updateGiftIdea(item.copy(isFavorited = !item.isFavorited))`).
- **Ở đâu:** `data/db/InLoveDao.kt:73-85` (upsert giữ `isFavorited` ở `:81`); `data/repository/InLoveRepository.kt:108-110`.
- **Kiểm:** test DAO (đổi cờ giữ nguyên các cột khác; seed upsert ngay sau khi đổi không làm mất cờ); `CloudEnrichmentDataTest.kt:131-143` phải xanh.
- **Rủi ro:** thấp, không đổi schema. `remoteId` có chỉ mục UNIQUE (`data/model/Entities.kt:34`) nên UPDATE chỉ chạm đúng một hàng.

#### U-09 — Thêm Noel 24/12 vào lịch Việt Nam (F08 một phần, cỡ S; sau U-02)

- **Việc:** thêm "Đêm Noel (24/12)" vào `VietnameseHolidays.fixedHolidays` với `isCoupleRelevant = true` (bản nháp `vn_noel_eve`: liên quan 3, bật mặc định, nhắc trước 7 ngày, độ tin cậy thấp). Hàng mới có `notificationEnabled = true` nhờ U-02.
- **Ở đâu:** `data/seed/VietnameseHolidays.kt:35-51`; `data/repository/OfflineSeeder.kt:211-219`.
- **Kiểm:** golden đổi CHẮC CHẮN:
  - **GV-012**: ca `table-dump-15-entries` thành 16 mục (đổi tên ca); ca `title-nfc-emoji-prefix` thêm 2 hàng.
  - **GV-204**: thêm một hàng Noel trong MỖI ca trong 9 ca `-vi` (`2024-01-01-vi`, `2027-12-31-vi`, `2028-01-01-vi`, `2028-02-14-vi`, `2028-02-28-vi`, `2028-02-29-vi`, `2028-03-01-vi`, `2028-07-15-vi`, `2030-12-31-vi`), ở cả hai khối; 9 ca `-en` không đổi.
  - **GV-015** và **GV-014** không đổi; `CloudEnrichmentDataTest.kt:152-155` vẫn qua.
- **Rủi ro:** chỉ áp dụng cho cài mới (F05). Noel không phải lễ truyền thống Việt Nam nhưng là dịp hẹn hò phổ biến ở đô thị (độ tin cậy thấp, mục 4.1), nên chủ app xác nhận trước khi bật mặc định.

#### U-10 — Gợi ý dự phòng khi chưa có sở thích hay dịp gần (G-06, G-04 chỉ ghi chú, cỡ S)

- **Việc:** khi không có sở thích và không có dịp trong 30 ngày, use case hiện trả danh sách RỖNG (`.filter { (_, fit) -> fit.first || fit.second }.take(MAX_RESULTS)`). Cho trả dự phòng theo tín hiệu có sẵn: quà có `suggestedOccasion` chứa "bất kỳ" hoặc "any" (cùng luật `occasionFits`), giữ thứ tự seed. KHÔNG dùng `popularityWeight` (trường này không tồn tại). Chỉ 2/20 quà gốc là "Bất kỳ dịp nào" nên tối đa 2 quà, cho đến khi U-20 bổ sung dữ liệu.
- **Ở đâu:** `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:135-167` (lọc ở `:152-155`); `domain/GiftRanker.kt:14-15`.
- **Kiểm:** test "không sở thích, không dịp" trả ít nhất 1 gợi ý "bất kỳ dịp"; GV-029 có thể đổi ở ca đang trả rỗng, đọc diff.
- **Rủi ro:** G-04 (`GetPersonalizedGiftSuggestionsUseCase.kt:113` coi sinh nhật của hai người như nhau) KHÔNG sửa được trong use case vì ViewModel không có vai "mình / người ấy" (`InLoveViewModel.kt:268,271,283,286`); chuyển sang U-37.

#### U-11 — Dọn quà `tpl_*`, bỏ nhãn "AI" của bộ mẫu, dịch EN (G-13, G-07, cỡ S; sau U-03)

- **Việc:** `GiftSuggestionGenerator` là 4 mẫu tiếng Việt gắn nhãn AI (`isAiGenerated = true` ở `data/repository/GiftSuggestionGenerator.kt:35,54,73,93`, `remoteId` dạng `tpl_*`). Dịch mẫu sang EN hoặc đổi tên thành "gợi ý nhanh". Khi sinh lại, xoá hàng `tpl_*` cũ (G-13) nhưng CHỈ hàng chưa `isFavorited`, để không mất dấu "thích". Toast đếm đúng số hàng mới hoặc đổi.
- **Ở đâu:** `data/repository/GiftSuggestionGenerator.kt:15-106`; `ui/viewmodel/InLoveViewModel.kt:1270,1273,1283`.
- **Kiểm:** test VI và EN; sinh lặp lại không làm tăng số hàng `tpl_*`.
- **Rủi ro:** hàng `tpl_*` là quà cá nhân trong `AccountDataVault` (`data/db/AccountDataVault.kt:241-242`): việc xoá phải khớp luật cất và khôi phục.

#### U-12 — Seed quà chỉ ghi hàng khác bản đang có; sửa thứ tự `hasGifts` (G-09, G-10, G-12, cỡ M)

- **Việc:** không có `contentVersion` để dựa vào. Tối thiểu, trong `ensureSeedGiftCatalog` chỉ ghi hàng khác hàng đang có để Room không phát lại cả bảng mỗi lần mở app (hiện 20 upsert mỗi lần khiến `personalizedGifts` tính lại); dấu phiên bản là tuỳ chọn. Tính `hasGifts` đúng thứ tự (hiện ở `data/repository/OfflineSeeder.kt:33`, TRƯỚC khi seed ở `OfflineSeeder.kt:37`) và nói rõ cloud hay seed: seed cục bộ ghi đè bản Firestore trùng `remoteId` `seed_*`. Nhánh nhận hàng legacy (`OfflineSeeder.kt:57-59`) đòi `remoteId.isBlank()` trong khi migration điền `'legacy_'||id` (`data/db/Migrations.kt:132`) nên gần như là mã chết (G-12): ghi chú hoặc dọn, kèm test.
- **Ở đâu:** `data/repository/OfflineSeeder.kt:32-45,52-66`.
- **Kiểm:** giữ xanh `CloudEnrichmentDataTest.kt:106-143`; thêm test chạy seed hai lần không phát lại Flow.
- **Rủi ro:** trung bình vì đụng luồng khởi động; người nâng cấp từ bản rất cũ là trường hợp **chưa chắc**.

#### U-13 — Nhắc không mất sau khởi động lại, có lối cấp quyền báo thức chính xác (F03, F09, F18, F19, cỡ M; có thể tách 13a và 13b)

- **Việc:**
  - **13a**: `BootCompletedReceiver` gọi `AndroidReminderScheduler.resyncNow`; hiện chỉ gọi `AlarmNotificationScheduler.scheduleAllAnniversariesFromDb` (`alarm/BootCompletedReceiver.kt:43`).
  - **13b**: lối đưa người dùng tới màn cấp quyền báo thức chính xác (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM` có khai báo nhưng không dùng) và trạng thái "nhắc gần đúng giờ".
  - **13c**: tránh thông báo trùng giữa `checkAndNotifyTodayAnniversaries` và alarm.
  - **13d**: nhãn "00:00 Ngày lễ" nhưng thực tế bắn 09:00.
- **Ở đâu:** `alarm/BootCompletedReceiver.kt:33-51` (gọi ở `:43`); `AndroidManifest.xml:8-11`; `ui/viewmodel/InLoveViewModel.kt:911`; `alarm/AlarmNotificationScheduler.kt:222-233`; `alarm/AndroidReminderScheduler.kt:122`; `alarm/ReminderAlarmReceiver.kt:52,116`; `data/repository/OfflineSeeder.kt:26`.
- **Kiểm:** test với `alarm/plan/fakes/FakeReminderScheduler.kt` (file này nằm trong `main`; hợp đồng ở `alarm/plan/ReminderScheduler.kt:7-8,20`): khởi động lại phải gọi resync. Quyền báo thức chính xác chỉ kiểm được trên máy hay emulator Android 14 trở lên (**chưa kiểm chứng**).
- **Rủi ro:** thông báo trùng. Từ Android 14, quyền này không còn được cấp mặc định cho app mới (theo tài liệu Android, chưa thử trên máy).

#### U-14 — `today` tính theo thay đổi; lịch tôn trọng năm (G-23, F15, cỡ S)

- **Việc:** `personalizedGifts` đọc `LocalDate.now()` lúc phát ra nên app mở qua nửa đêm giữ ngày cũ (mã đã ghi chú giới hạn này bằng một dòng chú thích ở `InLoveViewModel.kt:585-587`). `matchesCalendarDay` bỏ qua năm nên lễ đổi ngày hằng năm (`isAnnual = false`) có thể hiện ở mọi năm (**chưa chắc**).
- **Ở đâu:** `ui/viewmodel/InLoveViewModel.kt:587,606`; `ui/screens/CalendarScreen.kt:142`.
- **Kiểm:** test ViewModel với đồng hồ giả qua nửa đêm; test `matchesCalendarDay` cho hàng `isAnnual = false` ở năm khác.
- **Rủi ro:** thấp.

#### U-15 — Nút quà ở lịch mang theo dịp; sửa cách chọn dịp gần nhất (G-03, G-05, F14, cỡ M)

- **Việc:**
  - (a) Dùng khoá dịp chuẩn (`anniversary`, `valentine`, `birthday_partner`...) thay cho chuỗi tự do. Hiện năm đầu tạo tên "Kỷ niệm 1 năm" (`GetPersonalizedGiftSuggestionsUseCase.kt:41,116`) chỉ khớp 1 seed; 10 seed "Kỷ niệm ngày yêu" không được +2.
  - (b) `nearestUpcomingOccasion` (`GetPersonalizedGiftSuggestionsUseCase.kt:106-133`) chỉ có 4 dịp cố định (Valentine, 8/3, 20/10, Giáng Sinh) cộng Tết (chỉ khi vùng không phải INTL), sinh nhật và kỷ niệm. Nối bảng dịp theo thị trường là việc của U-23; ở đây chỉ làm khoá.
  - (c) Nút "Gợi ý quà" ở lịch truyền dịp được chọn sang màn quà (hiện `onNavigateToGifts: () -> Unit` không có tham số).
- **Ở đâu:** `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:106-133`; `ui/screens/CalendarScreen.kt:266,941`.
- **Kiểm:** test use case năm đầu kỷ niệm ăn điểm dịp; GV-029 có thể đổi.
- **Rủi ro:** đây là cầu nối tạm, U-20 thay thế.

#### U-16 — Thêm mốc nhắc sớm, mặc định theo dịp, cột mốc ngày bằng dữ liệu (F10, cỡ S; sau U-04)

- **Việc:** mở rộng lựa chọn nhắc trước ngoài {0, 1, 3, 7} ngày (thêm 14 và 21 ngày cho dịp cần đặt trước hay làm riêng; dữ liệu nháp ghi `leadTimeDays` từ 0 đến 30 ngày, phổ biến nhất 7 ngày (22 dịp) và 14 ngày (14 dịp), Tết 21 ngày, chưa kiểm chứng; xem 7.4) và mặc định theo dịp thay cho 3 ngày cố định. Thêm các mốc ngày 22, 52, 520, 1314 (mục 4.2) làm dòng dữ liệu trong `DAY_MILESTONES`; mỗi mốc thêm tạo tới 3 báo thức (trước 3 ngày, trước 1 ngày, ngày đúng; `LoveMilestoneRules.kt:68-94`).
- **Ở đâu:** `ui/screens/Dialogs.kt:2374,2550,2559`; `alarm/plan/LoveMilestoneRules.kt:25-38`.
- **Kiểm:** test `plan` sinh đúng số báo thức; test các lựa chọn nhắc trước.
- **Rủi ro:** thấp. Chip 10.sp ở `Dialogs.kt:2559` cần vùng chạm 48 dp (liên quan U-18).

#### U-17 — Bảng âm lịch quá 2028 và cảnh báo hết dữ liệu (F06, F20, F-16, cỡ M; sau U-01)

- **Việc:** bảng phủ không đều: Tết 2025-2028; Rằm Giêng chỉ 2026-2027; Giỗ Tổ 2025-2028; Vu Lan chỉ 2025-2026 (đã hết hạn tính đến 2026-10-26); Trung Thu 2025-2027. Khi hết bảng, `refreshExisting` giữ ngày cũ và đặt `isAnnual = false` nên hàng thành lễ đã qua vĩnh viễn (F06). Mở rộng bảng đến 2040 (bảng và 15 vector ở mục 7; thuật toán khớp 96/96 mốc HKO với múi giờ +8), hoặc dùng thuật toán chung (khoảng 120 dòng, có tham số múi giờ), cộng cảnh báo "hết dữ liệu" khi `next == null`.
- **Ở đâu:** `data/seed/VietnameseHolidays.kt:64-125`; `data/seed/HolidayDates.kt:16,30-48`.
- **Kiểm:** golden GV-013, GV-015, GV-204 đổi ở các ca "hết hạn thì null": đọc từng diff. Test 15/15 vector.
- **Rủi ro:** F20: `refreshExisting` ghép hàng bằng chuỗi `"${emoji} ${titleVi}"`, đổi tên là mất cập nhật (U-27 tuỳ chọn xử lý). Độ tin cậy theo từng năm ở mục 7. Việt Nam và Trung Quốc lệch nhau 5 ngày trong giai đoạn 2025-2040.

#### U-18 — Vùng chạm 48 dp, nhãn VIP, mô tả EN, dọn chuỗi chết (G-18, G-19, G-21, G-22, cỡ M)

- **Việc:**
  - (a) Chip danh mục (`Surface` clickable, cao khoảng 36 dp), nút hành động và các nút 36, 32, 40 dp lên 48 dp theo AGENTS.md; dùng lại hằng `MinTouchBox`.
  - (b) Thẻ "1,000-Day": nút "Xem lịch trình" / "View plan" thực ra mở paywall VIP; đổi nhãn để báo trước (đề xuất "Xem gói VIP" / "View VIP plan").
  - (c) `contentDescription` "Favorite" và "Close" đang viết cứng tiếng Anh: chuyển sang chuỗi bản địa hoá.
  - (d) Xoá chuỗi chết `gift_cat_*` sau khi chạy lint.
- **Ở đâu:** `ui/screens/GiftScreen.kt:401,528,537,630,732,795,1087,1163-1189,1244,1331-1344`; `ui/screens/Dialogs.kt:639,825`; `res/values/strings.xml:197-203` (cả hai locale); `ui/util/Localization.kt:237-243,463-469,680-686`; `ui/util/AppStrings.kt:165-171`.
- **Kiểm:** test Compose kích thước tối thiểu 48 dp; chạy `:app:lintDebug` không thêm cảnh báo; nếu có ảnh Roborazzi cho màn này thì phải chụp lại.
- **Rủi ro:** thấp. `gift_cat_*` nối qua ba chỗ, xoá cả ba cùng lúc.

### 6.3 Đợt 2: gói nội dung theo thị trường

Đợt 2 là bước chiến lược của cả bản nghiên cứu: thay ba nơi đang chứa nội dung (6.3.1) bằng **một gói JSON có phiên bản cho mỗi thị trường**. Gói chỉ là dữ liệu; phép tính ngày vẫn nằm trong Kotlin (`CalendarMath`, DEC-59). **Đường tối thiểu:** chỉ làm U-19 đến U-22 cho thị trường Việt Nam. Bốn việc này gộp ba catalog làm một, xử lý G-10, F-05, F-06, F-09 và một phần F05 (phần còn lại ở U-27) mà chưa đổi lược đồ Room. U-23 trở đi chỉ làm khi chủ app đã chọn thị trường thứ hai (QĐ-1, mục 9). Các đầu việc U-19 đến U-28 viết ngay sau 6.3.9, cùng bốn gạch đầu dòng (**Việc**, **Ở đâu**, **Kiểm**, **Rủi ro**) và cùng quy ước đường dẫn như 6.2 (ghi ở đầu mục 3).

#### 6.3.1 Ba nơi chứa nội dung hôm nay

| Nơi | Chứa gì | Ai đọc | Vấn đề |
|---|---|---|---|
| Mã Kotlin | `data/seed/GiftIdeasSeed.kt` (20 quà song ngữ); `data/seed/VietnameseHolidays.kt` và `data/seed/WesternHolidays.kt` (bảng lễ); `alarm/plan/LoveMilestoneRules.kt` cùng một bảng mốc thứ hai ở `alarm/LoveAnniversaryMilestoneScheduler.kt:46-59` | `data/repository/OfflineSeeder.kt`: quà nạp lại mỗi lần mở app (`:37`); lễ chỉ nạp khi bảng `anniversary_dates` trống (`:175-177`) | Đổi nội dung phải phát hành app mới; lễ chỉ seed một lần theo ngôn ngữ lúc đó (F05, F-01); bảng âm lịch hết hạn (F06); khoá logic là chữ hiển thị tiếng Việt (F-03); hai bảng mốc (F-07) |
| Firestore `gift_ideas` và `scripts/seed_firestore.js` | Collection đọc công khai, không ghi (`firestore.rules:310-313`); script có 13-15 quà, một catalog thứ hai không đồng bộ với mã | `data/repository/CloudPresetFetcher.kt:179-241`: đọc cả collection, giới hạn 2 giây, không phiên bản, không kiểm tra | Không schema, không phiên bản, không kiểm ảnh hay id (G-11, F-05); quà cloud chỉ được lấy ở lần chạy đầu (G-10); `badgeText` bịa "98% Cặp đôi mê mẩn" (F-15) |
| `app/src/main/assets/content/gift_catalog.json` và `holiday_calendar.json` | Phiên bản 1; 20 quà; lễ VN 15 cố định + 5 âm lịch, lễ Tây 9 cố định + 4 theo luật | **Không ai.** `ContentRepository.getHolidayCalendar` (`domain/content/ContentRepository.kt:30`) và `HolidayCalendarItem` (`domain/content/ContentModels.kt:47-63`) chỉ được khai báo, không có triển khai, không có chỗ gọi; thư mục `data/content/` không tồn tại | Mã chết (F-09); `holiday_calendar.json:163` lặp lại ngày Rằm Giêng 2027 sai (F07) |

Ba nơi này không biết nhau. Vì thế cùng một lỗi (ngày Rằm Giêng 2027) nằm ở hai nơi, và cùng một thứ (danh sách quà) có ba bản khác nhau. Mục tiêu của Đợt 2 là **một nguồn duy nhất**.

#### 6.3.2 Hình dạng gói

Mỗi thị trường một tệp JSON gồm phần đầu và bốn mảng `occasions` (dịp), `milestones` (mốc), `gifts` (quà), `taboos` (cấm kỵ). Phần đầu gồm `schemaVersion`, `packVersion`, `minAppVersion`, `region`, `languages`, `sourceLanguage`, `currency`, `calendars`, `defaultOccasionSet` và `tierBands` (khoảng giá của bốn bậc giá theo tiền tệ thị trường); tên trường chốt khi làm U-19. Chữ hiển thị là từ điển theo mã ngôn ngữ BCP-47; thiếu ngôn ngữ người dùng thì dùng ngôn ngữ nguồn của gói, rồi `en`. Id ổn định và không bao giờ đổi nghĩa.

Bốn ví dụ dưới đây là bản ghi thật lấy từ bản nháp `docs/research/data/gift-occasion-draft.json` (87 dịp, 55 mốc, 142 quà, 70 cấm kỵ). Mỗi ví dụ **đã bỏ trường `sources`** (danh sách đường dẫn nguồn) cho gọn. Đây là dạng dùng khi nghiên cứu; bảng ngay sau các ví dụ nói trường nào đi vào gói giao cho app.

Dịp lễ (`vn_valentine`):

```json
{
  "id": "vn_valentine",
  "names": { "vi": "Valentine (14/2)", "en": "Valentine's Day", "local": "Valentine (14/2)", "localLang": "vi" },
  "regions": ["VN"],
  "kind": "romantic_holiday",
  "dateRule": { "type": "fixed_solar", "month": 2, "day": 14 },
  "coupleRelevance": 3,
  "giftExpectation": "expected",
  "whoGives": "male_to_female",
  "budget": { "currency": "VND", "low": 200000, "mid": 900000, "high": 3000000 },
  "leadTimeDays": 7,
  "bookingAdvised": true,
  "customs": "Hoa hồng, chocolate và bữa tối là bộ ba kinh điển; nam chi nhiều hơn nữ (khảo sát 2019 không rõ nguồn: nam 1,4 triệu, nữ ước ~0,9 triệu). Năm 2026 trùng 27 Tết nên sức mua trầm, nhiều cặp chọn đi chơi xuân thay vì quà; năm 2027 rơi vào mùng 9 Tết (Chủ nhật 14/2/2027).",
  "sensitivity": "none",
  "defaultOn": true,
  "confidence": "med",
  "pack": "VN"
}
```

Mốc (`vn_100_days`; bản ghi thật không có trường `recurrence`):

```json
{
  "id": "vn_100_days",
  "names": { "vi": "Bách nhật yêu (100 ngày)", "en": "100 days in love", "local": "Bách nhật yêu (100 ngày)" },
  "regions": ["VN"],
  "unit": "day",
  "values": [100],
  "culturalWeight": 2,
  "customs": "Các app đếm ngày yêu ở VN (vd. Been Together 2021) liệt kê 100 ngày cùng 1 năm, sinh nhật, ngày cưới là mốc nhắc nhở. Chưa có nguồn về phong tục kỷ niệm 100 ngày hay giá trị của nó; có thể kế thừa từ 'trăm ngày tuổi' của trẻ sơ sinh.",
  "confidence": "low",
  "pack": "VN"
}
```

Quà (`vn_g_001`):

```json
{
  "id": "vn_g_001",
  "title": { "vi": "Bó tulip 10 bông kèm thiệp viết tay", "en": "Tulip bouquet (10 stems) with a handwritten card" },
  "category": "Quà lãng mạn",
  "description": {
    "vi": "Tulip đang được người trẻ chọn nhờ màu tươi và cắm lâu hơn hồng; mua ở chợ hoa hoặc shop hoa rồi tự viết thiệp ngắn.",
    "en": "Tulips are a young-buyer favourite for bright colours and longer vase life; buy at a flower market or shop and add a short handwritten card."
  },
  "regions": ["VN"],
  "occasions": ["vn_valentine", "vn_womens_day_0803", "vn_womens_day_2010"],
  "priceTier": 2,
  "priceBand": { "currency": "VND", "min": 200000, "max": 300000 },
  "interests": ["flowers"],
  "loveLanguages": ["gifts", "words"],
  "effort": "buy_ready",
  "leadTimeDays": 2,
  "whereToBuy": ["chợ hoa (Quảng Bá, Hồ Thị Kỷ)", "shop hoa", "app giao hàng"],
  "recipient": "her",
  "trendNote": "Tulip 200-300k/10 bông (Hà Nội, 19/10/2026); 20-50k/bông dịp Valentine 2025.",
  "confidence": "high",
  "pack": "VN"
}
```

Cấm kỵ (`vn_clock`):

```json
{
  "id": "vn_clock",
  "regions": ["VN"],
  "type": "gift",
  "item": { "vi": "Đồng hồ (nhất là dịp Tết)", "en": "Clocks and watches" },
  "reason": "Gợi ý thời gian sắp hết, âm Hán gần chữ 'chung' (kết thúc/tang). Phương Tây coi đồng hồ là đúng hẹn, nên nhiều bài vẫn đề xuất đồng hồ đôi.",
  "severity": "soft",
  "confidence": "med",
  "concept": "clock_watch",
  "pack": "VN"
}
```

| Trường trong bản nháp | Trong gói giao cho app | Vì sao |
|---|---|---|
| `names.local`, `names.localLang` | Bỏ; dùng từ điển BCP-47 (`vi`, `en`, các ngôn ngữ khác) | Gói giao chỉ có một cơ chế dịch |
| `sources` | Bỏ khỏi gói; giữ ở bản nháp nghiên cứu | Tốn chỗ, người dùng không đọc |
| `pack` | Bỏ | Chỉ là nhóm nghiên cứu (VN, WEST, EA, SEA_SA, LATAM_TR_CIS), không phải thị trường |
| `confidence` | **Giữ** | 64 trong 142 quà ở mức `low`; bỏ thì app mất cờ "chưa chắc" (U-30); tốn thêm vài KB (ước tính, chưa đo) |
| `customs`, `trendNote`, `reason` | Giữ | Hiển thị ở màn chi tiết quà (U-30) |
| `imageUrl`, `legacyRemoteId` | Thêm, tuỳ chọn | Quà gốc giữ ảnh và khoá cũ (6.3.5, 6.3.8) |

**Kích thước đo được** (script Python chạy một lần trên bản nháp; JSON gọn, UTF-8, tính byte; năm nhóm nghiên cứu, một gói thị trường thật chỉ chứa phần của thị trường đó nên nhỏ hơn):

| Nhóm | Dịp / mốc / quà / cấm kỵ | Bỏ `sources` và `confidence` | Bỏ thêm `customs`, `trendNote`, `reason`, `pack` |
|---|---|---|---|
| VN | 15 / 11 / 32 / 15 | 51.721 | 37.941 |
| WEST | 19 / 19 / 30 / 14 | 56.092 | 42.004 |
| EA | 18 / 10 / 24 / 14 | 38.656 | 28.446 |
| SEA_SA | 19 / 7 / 28 / 13 | 51.151 | 37.509 |
| LATAM_TR_CIS | 16 / 8 / 28 / 14 | 46.436 | 33.152 |

Gói giao (bỏ `sources` và `pack`, giữ `confidence`) nằm quanh cột thứ ba, lệch vài KB. Nếu giữ mọi trường thì mỗi nhóm nặng 46.855 đến 68.932 byte. Cả bản nháp là 298.276 byte (gọn) hoặc 361.489 byte (thụt lề). Giới hạn một tài liệu Firestore là 1 MiB (theo tài liệu Firebase, chưa kiểm lại hôm nay); mức trần đề xuất cho một gói là 256 KB (**đề xuất**, chưa có số liệu hỗ trợ). Gộp cả bản nháp vào một tệp sẽ vượt mức trần này, nên chia theo thị trường.

#### 6.3.3 Thị trường (`MarketProfile`) và mã vùng

`MarketProfile = {region, languages[], currency, calendars[], defaultOccasionSet}` **không lưu**: app suy ra từ mã vùng và phần đầu của gói. Thứ được lưu là **mã vùng** (`VN`, `INTL`, `PH`, `BR`...), trong cột `couple_profile.occasionRegion` đã có. Cột này là `TEXT NOT NULL DEFAULT ''` (`data/db/Migrations.kt:165-171`) nên nhận mã mới mà không cần migration; test `data/repository/OnlineCoupleProfileSlotTest.kt:179,211` đã lưu giá trị tuỳ ý `"HN"`.

| `occasionRegion` hôm nay | Người dùng nhận gì hôm nay | Sau U-23 |
|---|---|---|
| rỗng (mặc định) | Gợi ý theo kiểu Việt Nam (Tết chỉ bị loại khi vùng là INTL) | Vẫn là Việt Nam cho tới khi app có từ hai gói thật; từ đó suy từ vùng của thiết bị, người dùng chỉnh lại được |
| `VN` | Việt Nam | `VN` dùng gói `VN.json` |
| `INTL` | Phương Tây, không Tết | `INTL` dùng gói `INTL.json`, sinh từ `WesternHolidays` (6.3.8) |
| Mã nước mới (`PH`, `BR`, `TH`, `US`...) | Chưa có | Lưu thẳng vào cột cũ; nước chưa có gói thì rơi về `INTL` |

Nơi đọc cột này hôm nay chỉ hiểu rỗng, `VN`, `INTL`: `ui/viewmodel/InLoveViewModel.kt:608`, `ui/screens/GiftScreen.kt:368`, `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:35-38` (danh sách đủ 7 file ở U-23). Quy tắc kèm theo:

- Vùng và ngôn ngữ là hai trục riêng (F-10): không suy vùng từ ngôn ngữ giao diện (đây là lỗi F08 hôm nay).
- Tôn giáo **không bao giờ** được suy ra hay lưu. Gói dịp tuỳ chọn (ví dụ lễ tôn giáo) chỉ bật khi người dùng chủ động chọn, chỉ lưu trên máy, không đồng bộ (QĐ-6).
- Một nước chưa có gói không được để trống danh sách: rơi về `INTL`.

#### 6.3.4 Lưu trữ, ghi đè, bản dự phòng, kiểm hợp lệ, phiên bản

```text
Mở app (gói đang dùng cũ hơn 24 giờ thì tải lại)

  (1) gói trong APK        assets/content/{region}.json      luôn có
  (2) bản dự phòng         filesDir/content/{region}.json    gói hợp lệ gần nhất đã tải
  (3) gói trên Firestore   content_packs/{region}            một lần get(), hết giờ sớm
        gồm: packJson (chuỗi JSON), sha256, packVersion, minAppVersion

  (3) --> kiểm: sha256 -> schemaVersion, minAppVersion -> kích thước -> từng bản ghi
            lỗi cấu trúc:      bỏ cả gói, giữ (2)
            lỗi từng bản ghi:  bỏ riêng bản ghi đó
            |
            v
          packVersion cao hơn bản đang dùng?  --> ghi tạm rồi đổi tên thành (2)

  Bản dùng = gói có packVersion cao nhất trong (1), (2) và (3) vừa qua kiểm.
  Không mạng, hết giờ, hoặc luật chưa phát hành: dùng (2) hoặc (1), không báo lỗi.
```

- **Vì sao Firestore, không Remote Config:** giới hạn kích thước tham số Remote Config **chưa kiểm chứng**; Firestore đã dùng sẵn qua `CloudPresetFetcher`.
- **Tài liệu `content_packs/{region}`:** `packJson` là một **chuỗi** JSON để `sha256` phủ đúng từng byte (không cần chuẩn hoá JSON khi kiểm); script đẩy gói (U-25) tạo chuỗi này ở dạng gọn, khoá sắp xếp, nên cùng một gói luôn cho cùng `sha256`. Một lần `get()`, hết giờ ngắn (đề xuất dùng lại mức 2 giây của `CloudPresetFetcher`).
- **Luật đọc:** thêm một khối `match` chỉ đọc, theo mẫu `firestore.rules:310-313`; trước khi luật được phát hành, app chỉ việc dùng gói trong APK.

```text
match /content_packs/{region} {
  allow read: if true;
  allow write: if false;
}
```

- **Bản dự phòng:** ghi vào tệp tạm rồi đổi tên (không bao giờ để lại tệp ghi dở).
- **Phiên bản:** `schemaVersion` (app từ chối nếu số lớn cao hơn số nó hiểu), `packVersion` tăng đều (hoàn tác = phát hành số cao hơn mang nội dung cũ), `minAppVersion` (gói cần mã mới hơn thì app cũ giữ bản dự phòng), đọc JSON với `ignoreUnknownKeys` để thêm trường không làm vỡ app cũ.
- **Bộ kiểm hợp lệ:** một bộ thuần Kotlin trong commonTest và một script Node trong `scripts/`, **dùng chung một bộ gói mẫu tốt và xấu** để hai bộ không lệch nhau. Dùng lại luật an toàn URL của GV-202. Cấm `:` trong id và cấm tiền tố `seed_`, `local_`, `tpl_`, `legacy_` (các tiền tố hàng cũ và hàng người dùng dùng hôm nay).
- **Hai loại lỗi:**

| Loại lỗi | Ví dụ | Xử lý |
|---|---|---|
| Cấu trúc (cả gói) | JSON hỏng; `sha256` không khớp; `schemaVersion` hoặc `minAppVersion` cao hơn app; vượt mức trần kích thước (đề xuất 256 KB) | Bỏ cả gói, giữ bản dự phòng gần nhất |
| Từng bản ghi | `imageUrl` không phải https hoặc host ngoài danh sách cho phép; id chứa `:` hoặc có tiền tố cấm; thiếu trường bắt buộc; luật ngày lạ | Bỏ bản ghi đó; ảnh sai chỉ bỏ ảnh, không bỏ quà |

`sha256` trong cùng tài liệu **chỉ chống hỏng hoặc cắt cụt**, không chống kẻ có quyền ghi. Quyền ghi bị chặn bằng `write: false` (chỉ admin SDK ghi được).

#### 6.3.5 Room, khoá ổn định, lễ, tiền tệ, ngôn ngữ

| Phần | Thay đổi Room | Làm ở |
|---|---|---|
| Quà (`gift_ideas`) | Không đổi lược đồ: `remoteId` thành `pk:{region}:{giftId}`; `suggestedOccasion`, `targetInterests`, `priceRange` chứa chuỗi phẳng sinh từ gói | U-21, U-22 |
| Lễ (`anniversary_dates`) | Giai đoạn 1 không đổi; giai đoạn 2 (tuỳ chọn) thêm `contentKey TEXT NOT NULL DEFAULT ''` | U-21; U-27 |
| Vùng (`couple_profile.occasionRegion`) | Không đổi: cột đã nhận mã bất kỳ | U-23 |
| Ngân sách (`couple_profile`) | Thêm `budgetMax` và `budgetCurrency`, sao chép từ `budgetMaxVnd`; **không** RENAME COLUMN | U-28 |

- **Quà và khoá cũ.** Mỗi quà sinh ra từ một seed cũ mang `legacyRemoteId = seed_<imageSeed>`. **Một** bảng ghép cũ sang mới dùng ở hai nơi: giao dịch upsert `InLoveDao.upsertGiftIdeaByRemoteId` (`data/db/InLoveDao.kt:73-85`, giữ `isFavorited` ở `:81`) và lớp phủ yêu thích của két dữ liệu `data/db/AccountDataVault.kt:260`. Hàng `pk:%` là hàng **catalog** (két chỉ bỏ qua hàng cá nhân `local_%`, `tpl_%`, `legacy_%` ở `data/db/AccountDataVault.kt:241-242`), nên yêu thích của chúng được cất theo `remoteId` và đổi khoá thì phải đổi cả ở két. `gift_reminders` không bị ảnh hưởng: nó lưu chữ tự do, không có khoá tới quà (`data/model/Entities.kt:177-190`). Test theo mẫu `CloudEnrichmentDataTest.kt:115-145` (yêu thích còn nguyên sau khi seed lại).
- **Cách gói vào hàng Room.** Gói trải `occasions[]` của quà thành các token cách nhau bằng dấu phẩy trong `suggestedOccasion`, `interests` chuẩn vào `targetInterests`, giá vào `priceRange` ở dạng máy đọc được như `CUR:min-max` (mặc định đề xuất, chốt khi làm U-22).
- **Lễ.** Giai đoạn 1: không migration; gói quyết định lễ nào được seed theo vùng, khớp hàng cũ theo tiêu đề như `HolidayDates.refreshExisting` (`data/seed/HolidayDates.kt:30`). Giai đoạn 2 (U-27, tuỳ chọn): một migration cộng thêm `contentKey` (v14 lên v15; cần test migration và tệp lược đồ JSON); cột này **chỉ ở máy** cho tới khi đã kiểm `firestore.rules:183-191` và `data/sync/AnniversarySyncAdapter.kt`. F05 chỉ sửa trọn khi có U-27 cộng một dấu "đã seed khoá này" để lễ người dùng đã xoá không quay lại (F16 nói xoá lễ là xoá mềm, nên dấu này có thể không cần: kiểm khi làm).
- **Tiền tệ.** So `price.min` của quà với ngân sách **cùng đơn vị tiền** (không quy đổi tỷ giá); đổi thị trường thì đặt lại và hỏi lại ngân sách. Định dạng bằng `NumberFormat.getCurrencyInstance` qua một lớp expect/actual mỏng. Số chữ số thập phân mặc định của JPY, KRW, IDR (`Currency.getDefaultFractionDigits`): **chưa kiểm chứng** (`java.util.Currency` theo ISO, ICU theo CLDR, có thể lệch ở IDR).
- **Một lần nâng v15, sau D-15.** Mặc định đề xuất: gộp U-27 và U-28 (cộng cột của U-29 và U-37 nếu hai việc này được duyệt, xem 6.5) vào **một** lần nâng lược đồ lên v15 và làm **sau** khi D-15 (chuyển DAO và DB sang `:shared`) xong. Lý do: bước kiểm của D-15 chạy `git diff --exit-code -- shared/schemas` (`docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:328`) và DEC-22 (`docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:39`) đòi tệp `14.json` không đổi. Hôm nay tệp ấy nằm ở `app/schemas/com.example.data.db.AppDatabase/14.json`; tác vụ F-16 của kế hoạch KMP (`docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:551`; không phải phát hiện F-16 của tài liệu này) chuyển nó sang `shared/schemas/com.example.data.db.AppDatabase/14.json`, còn `shared/schemas/` hiện chỉ chứa lược đồ của spike. Kế hoạch KMP cũng tự chừa một lần nâng v15: nếu D-06 cho thấy nâng Room lên 2.8.5 làm đổi `identityHash`, kế hoạch thêm `Migration(14, 15)` rỗng (giả định A12, chưa kiểm chứng, `docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:30`; cổng P2 ở `docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:411`). Khi đó nên gộp các cột mới vào đúng lần nâng ấy để vẫn chỉ có một v15 (SUY LUẬN). Nếu quyết thị trường thứ hai (QĐ-1) trước D-15 thì nâng trước và sửa bước ghim băm của D-06 (`docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:318`) cùng bước kiểm của D-15 cho khớp v15.
- **Ngôn ngữ.** `AppLanguage { VI, EN }` (`ui/util/Localization.kt:8-10`) thành mã BCP-47 cộng `LanguageResolver`; bỏ `isEnglish` (678 chỗ, 30 file) và `tr()` (56 chỗ trong `InLoveViewModel.kt`); thêm `values-xx` và `locales_config` (manifest chưa có), dạng số nhiều, dự phòng font và `lineHeight` (F-12). Đây là U-24, cỡ L.
- **Gói là dữ liệu, không có logic ngày.** `CalendarMath` là nơi tính ngày duy nhất (DEC-59). Luật `nth_weekday` và Phục Sinh (`relative`) tính trong Kotlin; gói chỉ mô tả luật và tham số.

#### 6.3.6 Từ vựng chuẩn: sở thích, loại quà, dịp

**Sở thích: 38 khoá trong gói nháp, 23 khoá chuẩn.** Giao diện hôm nay chỉ có 10 khoá (`data/repository/OnlineCoupleRepository.kt:70-81`); 28 trong 38 khoá của gói nằm ngoài 10 khoá đó, và 62 trong 142 quà có **toàn bộ** sở thích nằm ngoài (1 quà không có sở thích nào). Bảng ghép 38 xuống 23:

| Khoá chuẩn | Giao diện có sẵn? | Khoá trong gói nháp gộp vào |
|---|---|---|
| `coffee` | có | `coffee` |
| `travel` | có | `travel`, `adventure` |
| `technology` | có | `technology` |
| `cycling` | có | `cycling` |
| `fashion` | có | `fashion` |
| `music` | có | `music` |
| `cinema` | có | `cinema` |
| `cooking` | có | `cooking` |
| `books` | có | `books` |
| `gaming` | có | `gaming` |
| `food` | mới | `food`, `dining`, `sweets`, `wine`, `tea` |
| `photography` | mới | `photography` |
| `flowers` | mới | `flowers` |
| `handmade` | mới | `handmade`, `crafts` |
| `wellness` | mới | `wellness`, `fitness` |
| `beauty` | mới | `beauty`, `fragrance` |
| `home_decor` | mới | `home_decor`, `home` |
| `jewelry` | mới | `jewelry`, `jewellery` |
| `art_culture` | mới | `art`, `culture`, `theatre` |
| `writing` | mới | `writing`, `journaling` |
| `collectibles` | mới | `collectibles`, `toys` |
| `family` | mới | `family` |
| `pets` | mới | `pets` |

Đếm: 11 khoá của gói rơi vào 10 khoá giao diện có sẵn, 26 khoá rơi vào 13 khoá mới, cộng `faith` = 38. `faith` chỉ là **thuộc tính của quà**, không cho người dùng chọn làm sở thích (QĐ-6).

**Loại quà: 10 loại trong gói nháp (142 quà), 5 khoá chip hiện có, thêm 1 chip.** Giữ nguyên 5 khoá chip hiện có để không vỡ dữ liệu; chỉ đổi nhãn khi gộp:

| Loại trong gói nháp (số quà) | Khoá chip trong app | Số quà |
|---|---|---|
| Quà lãng mạn (39), Quà ra mắt và biếu gia đình (4), Sức khoẻ và chăm sóc (2) | Quà lãng mạn | 45 |
| Kỷ vật Handmade (31) | Kỷ vật Handmade | 31 |
| Trải nghiệm và thưởng thức (21), Địa điểm hẹn hò (19) | khoá "Địa điểm hẹn hò" giữ nguyên, nhãn đổi thành "Hẹn hò và trải nghiệm" | 40 |
| Công nghệ và cảm xúc (13) | chip thứ 6 (mới) | 13 |
| Làm đẹp và mỹ phẩm (2), Trang sức và nước hoa (9) | khoá "Trang sức & Nước hoa" giữ nguyên, nhãn đổi thành "Làm đẹp và trang sức" | 11 |
| Sưu tầm và trend (2) | Bất ngờ bí mật (cộng 3 quà gốc) | 2 |

45 + 31 + 40 + 13 + 11 + 2 = 142. Quy tắc đề xuất: một chip chỉ hiện khi **thị trường** có từ 8 quà trở lên trong loại đó; 5 chip hiện có vẫn luôn hiện ở Việt Nam để không đổi hành vi. "Quà lãng mạn" gồm cả hai loại nhỏ cho tới khi mỗi loại có từ 8 đến 10 quà.

**Token chung của dịp.** Mảng `occasions[]` của quà ngoài 87 id dịp còn có ba token chung: `anniversary` (51 quà), `birthday` (41), `any` (19). Chúng thành token **dành riêng** `@anniversary`, `@birthday`, `@any`, khớp theo loại dịp (`dateRule.type = user_defined`) và theo token mà use case sinh ra, **không** khớp theo tên tiếng Việt như hôm nay (`domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:40-43`). Các từ vựng còn lại (người nhận, hình thức, ngôn ngữ yêu) và số đếm ở mục 7.

#### 6.3.7 Quy tắc của gói

- **Id**: ổn định, không đổi nghĩa; không chứa `:`; không mang tiền tố `seed_`, `local_`, `tpl_`, `legacy_`; tiền tố quà phương Tây là `west_g_` (không dùng `us_g_`).
- **Quà nháp gần trùng quà seed**: so khớp tên (7.5) thấy ít nhất 3 quà nháp gần trùng một trong 20 quà gốc. Khi nạp bản nháp vào gói (sau U-20 và U-21), mỗi cặp phải được gộp vào bản ghi của quà gốc (giữ `legacyRemoteId = seed_<imageSeed>` để không mất yêu thích) hoặc bỏ quà nháp; không để hai bản ghi cùng ý tưởng cùng hiện trong một danh sách.
- **Ngày lưu** luôn là lịch dương ASCII (DEC-75, GV-002); chữ số Ả Rập hay Thái chỉ ở lớp hiển thị.
- **Giá**: `priceTier` 1-4 cộng khoảng giá thật `priceBand` theo tiền tệ thị trường; không quy đổi tỷ giá. Phân bố bậc giá hiện có: bậc 1 là 37 quà, bậc 2 là 58, bậc 3 là 35, bậc 4 là 12.
- **Cấm kỵ là cảnh báo mềm**, gắn ở mức khái niệm (`concept`, ví dụ `clock_watch`, `cash_money`), không chặn quà. Mới có 4 quà mang `tabooRefs`: `vn_g_030` và `vn_g_024` (`vn_cash`), `cn_g_001` (`cn_red_packet_breakup`), `ru_g_003` (`ru_watch_knife`). Ba quà gốc cũ dính cấm kỵ mềm (Bộ Cốc Đôi In Tên, Đồng Hồ Đôi ở `data/seed/GiftIdeasSeed.kt:204-205`, Vòng Tay Khắc Tên Đôi) cũng gắn `tabooRefs` khi sinh gói (U-20) và hiển thị ở U-30. Trong 14 cấm kỵ của nhóm LATAM/TR/CIS có 5 mục thật ra là ghi chú về cách nói (`br_not_feb14`, `co_not_couple_only`, `mena_sa_valentine`, `mena_kw_valentine`, `mena_unmarried_couples`): nên chuyển thành `copyNotes` của dịp.
- **`defaultOn`**: bật mặc định khi `coupleRelevance >= 2` **và** áp dụng cho đa số thị trường **và** không có cổng tôn giáo hay tổ chức. 16 dịp có `coupleRelevance >= 2` đang tắt và 5 dịp bị cổng nhạy cảm đang bật; đây là mục chủ app duyệt (5.3).
- **Hai điểm riêng**: `coupleRelevance` (0-3, quyết định `defaultOn`) khác `giftExpectation` (none, small, expected, major). `whereToBuy` không bao giờ rỗng, chỉ là chữ thuần (chưa có liên kết affiliate, QĐ-8).
- **Luật ngày** (87 dịp). Bộ kiểm hợp lệ của nghiên cứu thiếu ba loại `week`, `table`, `user_defined` nên báo 14 trường hợp sai; schema chuẩn phải có đủ bảy loại:

| Loại luật | Số dịp | Ví dụ | Ai tính |
|---|---|---|---|
| `fixed_solar` | 61 | Valentine 14/2 | Kotlin |
| `lunar` | 7 | `vn_tet`, `vn_trung_thu`, `vn_that_tich`, `cn_qixi`, `cn_lantern_festival`, `cn_chap_goh_mei`, `cn_qixi_sea` | Bảng âm lịch (F06, U-17) và `CalendarMath` |
| `nth_weekday` | 2 | `co_amor_y_amistad`, `us_sweetest_day` | Kotlin |
| `relative` | 3 | `vn_di_choi_xuan`, `west_easter`, `gb_mothering_sunday` | Kotlin |
| `table` | 5 | `th_loy_krathong`, `idmy_ramadan_2027`, `idmy_eid_al_fitr_2027`, `in_karwa_chauth`, `in_diwali` | Bảng ngày theo năm nằm trong gói |
| `user_defined` | 7 | `vn_partner_birthday`, `vn_love_anniversary`, `vn_wedding_anniversary`, `vn_meet_parents`, `west_dating_anniversary`, `west_monthiversary`, `west_wedding_anniversary` | Ngày người dùng nhập |
| `week` | 2 | `ar_semana_dulzura`, `in_valentines_week` | Khoảng tuần quanh một ngày |

- **Luật `table` cần quy trình cập nhật hằng năm** (chạy 4 đến 6 tuần trước mùa lễ), cảnh báo hết hạn như F06, khoá theo thị trường **và địa điểm** (Karwa Chauth phụ thuộc địa điểm), và cho người dùng nhích ±1 ngày (ngày dự kiến của lễ theo trăng có thể lệch 1 ngày, mục 7).

#### 6.3.8 Bộ sinh gói và kiểm khứ hồi

**Sinh gói tương đương trước, làm giàu sau.** Một lớp test Kotlin dùng một lần đọc `GiftIdeasSeed.all` (đúng như lúc chạy, đã có sửa của Đợt 1 như U-06), `VietnameseHolidays`, `WesternHolidays` và `LoveMilestoneRules`, rồi ghi `VN.json` và `INTL.json` vào `app/src/main/assets/content/` khi đặt một biến môi trường (giống cách `GOLDEN_UPDATE` được đọc ở `app/src/test/java/com/example/golden/GoldenWriter.kt:12`; tên biến chốt khi làm). Chứng minh app **không đổi hành vi** với gói tương đương, rồi mới thêm nội dung làm giàu từ bản nháp nghiên cứu.

**Bằng chứng:**

- Lễ: GV-204 dùng bảng lễ thật nên là bằng chứng trực tiếp (hàng seed dựng từ gói phải bằng hàng trong GV-204).
- Quà: golden quà hiện có (GV-016, GV-029) dùng dữ liệu giả, **không** dùng `GiftIdeasSeed` thật nên không chứng minh được gì. Cần một phép so **từng trường** mới giữa gói và `GiftIdeasSeed.all`.
- Phép kiểm khứ hồi phải chạy **trước** khi U-21 xoá seed Kotlin.

**Điểm xuất phát đã kiểm (20 quà gốc, chi tiết ở mục 10):** 5 loại (Địa điểm hẹn hò 6, Kỷ vật Handmade 5, Quà lãng mạn 3, Trang sức và nước hoa 3, Bất ngờ bí mật 3); dịp gợi ý (Kỷ niệm ngày yêu 10, Sinh nhật 2, Bất kỳ dịp nào 2, mỗi dịp Valentine, 8/3, 20/10, Giáng Sinh, Kỷ niệm 1 năm, Hẹn hò cuối tuần 1); giá (dưới 200k 7, 200 đến 500k 4, 500k đến 1 triệu 4, trên 1 triệu 5); 19 trong 20 ảnh là Pexels, 1 ảnh dùng picsum. Chưa có quà nào cho Tết, mốc 100 ngày và dịp của nam.

**Việc phải quyết khi làm U-20:**

- **Hai nguồn lệch nhau.** Mã có 20 lễ Việt Nam (15 cố định, 5 âm lịch); bản nháp nghiên cứu có 15 dịp, chỉ trùng một phần. Cần một bảng ghép tay (lễ trong mã với id dịp), và giữ cả lễ không phải dịp cặp đôi (`coupleRelevance` thấp, `defaultOn` tắt) để gói tương đương vẫn seed đúng như cũ.
- **Một catalog quà duy nhất** cho Việt Nam: 20 quà gốc cộng quà nghiên cứu sau khi khử trùng (số quà sau khử trùng: **chưa đếm**).
- **13 quà trong `scripts/seed_firestore.js`**: mặc định bỏ; chỉ giữ quà không trùng sau khi xem tay; không bao giờ nhập `badgeText` bịa.
- **`assets/content/*.json` cũ** (6.3.1): mặc định xoá sau khi gói thay thế. **Không xoá** tài liệu Firestore `gift_ideas` khi app cũ còn đọc.
- **Khoảng trống ảnh.** Quà gốc giữ ảnh qua `imageUrl` tuỳ chọn (https và host trong danh sách cho phép); quà nghiên cứu mới chưa có ảnh nên dùng biểu tượng hoặc nền màu cho tới khi có U-36 (ảnh tự lưu trữ).
- **Tìm và mua quà** (nút "tìm quà", nơi mua; chưa có affiliate) là U-35, Đợt 3.

#### 6.3.9 Lộ trình 12 bước và rủi ro

| Bước | Việc | Cỡ | U | Phụ thuộc |
|---|---|---|---|---|
| 1 | Cố định locale khi đọc và lưu ngày (F-08) | M | U-07 | Không |
| 2 | Gộp hai bảng mốc | S | U-04 | Không |
| 3 | `ContentPack` và bộ kiểm hợp lệ | M | U-19 | Không |
| 4 | Bộ sinh gói và kiểm khứ hồi | M | U-20 | U-19 |
| 5 | `PackContentRepository`; sửa F-06 | M | U-21 | U-19, U-20 |
| 6 | Khoá ổn định và `Price(currency,min,max)`, **trước** khi D-13 chuyển seed | M | U-22 | U-03 |
| 7 | `MarketProfile` | M | U-23 | U-21 |
| 8 | Bỏ `isEnglish` và `tr()` | L | U-24 | U-23 |
| 9 | Script đẩy gói, luật đọc, test luật | S | U-25 | U-19 |
| 10 | Thị trường thứ hai thật sự | L | U-26 | U-20, U-21, U-23, U-24 |
| 11 | Tuỳ chọn: cột `contentKey` | M | U-27 | U-21 |
| 12 | Nhiều đơn vị tiền (kiểu `Price` ở bước 6; cột Room ở đây, cùng lần nâng v15 với bước 11 nếu làm cả hai) | M | U-28 | U-22 |

**Thứ tự với KMP và iOS.** Mã U-19 viết thẳng vào `shared` (commonMain); `shared/src/commonMain` hiện chưa có tệp quà, lễ, nội dung hay seed nào (đã kiểm 2026-10-26). Nếu U-19 đến U-21 xong trước D-13 (chuyển `GiftIdeasSeed`, `GiftRanker`, `GiftCatalog`, `ContentModels`, `ContentRepository` sang commonMain, DEC-58) thì D-13 chuyển `PackContentRepository` thay vì `GiftIdeasSeed` (QĐ-2).

**Đường tối thiểu:** U-19 đến U-22 chỉ cho Việt Nam đã gộp ba catalog làm một và sửa F-06; U-23 trở đi chỉ làm khi chủ app đã chọn thị trường thứ hai.

| Rủi ro | Giảm bằng |
|---|---|
| Đổi khoá logic làm mất yêu thích | Bảng ghép cũ sang mới dùng ở upsert **và** ở két, kèm test (U-22); nhắc quà không bị ảnh hưởng |
| Golden iOS ghim hành vi hiện tại | Làm U-22 **trước** khi D-13 chuyển seed sang commonMain |
| Gói Firestore hỏng hoặc độc | Kiểm phía app (https và danh sách host cho ảnh, loại bản ghi, giữ bản hợp lệ gần nhất) và `write: false`; `sha256` chỉ chống hỏng |
| Chất lượng bản dịch và độ đúng văn hoá | Người bản ngữ rà (QĐ-9); gắn nhãn "bản nháp" cho thị trường chưa rà |
| Ảnh liên kết nóng (hot-link) | Chép về R2 trước khi có nhiều thị trường (U-36) |
| Liên kết affiliate về sau | Cần UMP, Data Safety, ATT và nhãn công bố; chưa làm (QĐ-8) |
| Dung lượng APK | Năm nhóm nghiên cứu cộng lại khoảng 244.056 byte (bỏ `sources`, `confidence`): chưa là vấn đề; chỉ đưa vào APK các thị trường đang hỗ trợ |
| Hai nơi tính ngày | Gói chỉ là dữ liệu; `CalendarMath` là nơi duy nhất (DEC-59) |
| Mất cờ `confidence` | Giữ `confidence` trong gói giao |

**Các đầu việc của Đợt 2 (U-19 đến U-28).** Bốn gạch đầu dòng và quy ước đường dẫn giống 6.2; thứ tự làm theo lộ trình 12 bước ở 6.3.9. Mọi việc dưới đây đều cần chủ app duyệt trước khi viết mã (cổng duyệt của quy trình L). Đường tối thiểu cho riêng thị trường Việt Nam là U-19 đến U-22; từ U-23 trở đi chỉ làm khi QĐ-1 (mục 9) chọn thị trường thứ hai.

#### U-19 — `ContentPack` (mô hình) và bộ kiểm hợp lệ (G-11, F-05, cỡ M)

- **Việc:** viết mô hình `ContentPack` (kotlinx-serialization, `ignoreUnknownKeys = true` để app cũ đọc được gói có trường mới) theo các trường ở 6.3.2, và một bộ kiểm hợp lệ thuần Kotlin. Lỗi cấu trúc thì bỏ cả gói và giữ bản tốt cuối: JSON hỏng, `schemaVersion` lớn hơn bản app hiểu, kích thước quá 256 KB (con số đề xuất), `sha256` không khớp. Lỗi từng bản ghi thì chỉ bỏ bản ghi đó: id chứa `:` hoặc bắt đầu bằng `seed_`, `local_`, `tpl_`, `legacy_`; kiểu luật ngày lạ; `imageUrl` không phải https hoặc ngoài danh sách host cho phép (chỉ bỏ ảnh, giữ quà). Thêm ba kiểu luật ngày mà bộ kiểm nghiên cứu còn thiếu (`week`, `table`, `user_defined`; thiếu chúng gây 14 báo sai). Dùng lại quy tắc an toàn URL mà GV-202 đã ghim (`golden/OutboxGoldenTest.kt:362`).
- **Ở đâu:** mới, trong `shared/src/commonMain` (mô hình, bộ kiểm), `shared/src/commonTest` (test, fixture) và `scripts/` (bộ kiểm Node dùng ở U-25). `shared/src/commonMain` hiện chưa có file nội dung nào (D-13 chưa làm) nên không đụng mã cũ. SHA-256: `shared/src` chưa có hàm băm; app chỉ gọi `java.security.MessageDigest` trực tiếp ở `data/db/AccountDataVault.kt:232`, `data/sync/ProfileProjection.kt:59` và `ui/util/AuthSecurityManager.kt:79`. Mặc định đề xuất: bộ kiểm nhận hàm băm `(ByteArray) -> String` từ ngoài truyền vào, để U-19 không phụ thuộc cách băm trên iOS (`expect/actual` với CommonCrypto là phương án khác, **chưa kiểm chứng**).
- **Kiểm:** một bộ fixture tốt/xấu dùng chung cho bộ kiểm Kotlin và bộ kiểm Node (cùng danh sách, cùng kết quả mong đợi, để hai bên không lệch). Chạy cả hai trên `docs/research/data/gift-occasion-draft.json`: kỳ vọng không có lỗi cấu trúc, và có cảnh báo cho các `priceBand.min` còn trống (WEST 30/30, LATAM_TR_CIS 4 quà) để U-26 điền trước khi phát hành.
- **Rủi ro:** hai bộ kiểm trôi lệch nhau (giảm bằng fixture chung); danh sách host ảnh phải khớp với U-36; quy tắc "id không chứa `:`" phải có TRƯỚC id `pk:` đầu tiên vì khoá `pk:{region}:{giftId}` dùng `:` làm dấu phân cách; test commonTest dùng hàm băm giả nên cần thêm một test nền Android băm thật.

#### U-20 — Bộ sinh gói từ dữ liệu hiện có, kiểm khứ hồi với golden (F-09, G-02, cỡ M; sau U-19)

- **Việc:** một lớp test chạy một lần (bật bằng biến môi trường, theo mẫu `GOLDEN_UPDATE`; tên chọn lúc làm) đọc `GiftIdeasSeed.all` (đã có khoá sở thích nhờ U-06), `VietnameseHolidays`, `WesternHolidays` và `LoveMilestoneRules`, rồi ghi `VN.json` và `INTL.json` vào `assets/content/`. Nguyên tắc: gói tương đương trước, làm giàu sau; hai việc này không trộn vào một lần đổi. Hai nguồn lễ không khớp nhau (mã có 20 lễ Việt Nam, nghiên cứu có 15 dịp, trùng một phần) nên cần một bảng ghép tay; giữ các lễ không liên quan cặp đôi (`coupleRelevance` thấp, `defaultOn = false`). Ảnh: giữ `imageUrl` của 20 quà cũ làm trường tuỳ chọn của gói.
- **Ở đâu:** nguồn `data/seed/GiftIdeasSeed.kt`, `data/seed/VietnameseHolidays.kt`, `data/seed/WesternHolidays.kt`, `alarm/plan/LoveMilestoneRules.kt`; đích `assets/content/VN.json` và `assets/content/INTL.json`; mẫu biến môi trường `golden/GoldenWriter.kt:12`.
- **Kiểm:** bằng chứng trực tiếp cho lễ là GV-204 (bảng hàng lễ thật): gói sinh ra rồi nạp lại phải cho đúng các hàng đó. Với quà, GV-016 và GV-029 dùng đầu vào giả nên KHÔNG chứng minh được gì về 20 quà thật; cần một test MỚI so từng trường giữa `GiftIdeasSeed.all` và gói đã nạp. Chạy khứ hồi TRƯỚC khi U-21 xoá seed Kotlin; sau U-21 đóng băng hoặc xoá bộ sinh.
- **Rủi ro:** bảng ghép lễ sai thì mất lễ hoặc nhân đôi hàng (khoá khớp hiện tại là `"${emoji} ${titleVi}"` hoặc `titleEn` ở `data/seed/HolidayDates.kt:32-34,42-44`); làm U-01 trước để gói không mang ngày Rằm Giêng 2027 sai.

#### U-21 — `PackContentRepository` thay cho seed trong mã (F05, F-01, F-05, F-06, F-09, G-10, cỡ M; sau U-19, U-20)

- **Việc:** cài đặt `ContentRepository` (giao diện đã khai báo, chưa có bản cài) bằng `PackContentRepository`. Chọn gói tốt nhất trong ba nơi (APK, bản tốt cuối trong `filesDir`, Firestore) theo `packVersion` cao hơn, kiểm hợp lệ (U-19), rồi: ghi quà vào Room CHỈ khi hàng khác bản đang có (G-09; thay `ensureSeedGiftCatalog`), gieo lễ từ gói thay cho `buildHolidayAnniversaries`, cột mốc giữ `LoveMilestoneRules` (gói chỉ cho tên và `customs`; cột mốc theo tháng ở U-33). Khi gói đã thay thế được: xoá hai file `assets/content/*.json` không ai đọc và phần `HolidayCalendarItem` không dùng. KHÔNG xoá tài liệu Firestore `gift_ideas` khi app cũ còn đọc.
- **Ở đâu:** `domain/content/ContentRepository.kt:15-31` (giao diện); `data/repository/CloudPresetFetcher.kt:179-241` (tải Firestore; thay bằng một `get()` cho `content_packs/{region}`); `data/repository/OfflineSeeder.kt:37,52-66` (gieo quà) và `:175-229` (gieo lễ).
- **Kiểm:** bài khứ hồi của U-20 vẫn xanh; gói Firestore hỏng thì giữ bản tốt cuối; `packVersion` thấp hơn bị bỏ qua; yêu thích còn sau khi nạp lại (mẫu `CloudEnrichmentDataTest.kt:115-145`); chạy cả bộ test.
- **Rủi ro:** F05 mới sửa một phần: lễ vẫn chỉ gieo khi bảng rỗng cho tới U-27 (có thể kèm một dấu "khoá đã thấy"); app cũ vẫn đọc `gift_ideas` nên giữ nguyên bộ tài liệu cũ.

#### U-22 — Khoá ổn định cho quà và `Price(currency, min, max)` (G-01, G-24, F-03, F-04, F-17, cỡ M; sau U-03)

- **Việc:** (1) khoá quà `pk:{region}:{giftId}` kèm `legacyRemoteId` (`seed_<imageSeed>` cho 20 quà cũ) và MỘT bảng ánh xạ cũ → mới dùng chung cho upsert và két dữ liệu; (2) logic dùng khoá, không dùng chuỗi hiển thị tiếng Việt (G-24: `seedRemoteIdFor` khớp theo ảnh hoặc tiêu đề; F-17: danh mục của lễ "Lãng Mạn" khác khoá danh mục "Quà lãng mạn"); (3) thay `priceLabel` (chỉ VND) và `priceFloorVnd` bằng `Price(currency, min, max)`, cột Room `priceRange` giữ dạng chuỗi máy `CUR:min-max` (mặc định). PHẢI làm TRƯỚC khi D-13 chuyển file sang commonMain.
- **Ở đâu:** `data/seed/GiftIdeasSeed.kt:49-50`; `domain/content/GiftCatalog.kt:96-112`; `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:80-86`; `data/db/AccountDataVault.kt:241-260`; `data/db/InLoveDao.kt:73-85`.
- **Kiểm:** yêu thích còn sau nâng cấp (hàng cũ `seed_*` được ánh xạ sang khoá mới); két cất và khôi phục theo `remoteId` mới; GV-016 và GV-029 tạo lại bằng `GOLDEN_UPDATE=1` rồi đọc từng dòng; thêm test lọc giá với hai đơn vị tiền.
- **Rủi ro:** quên bảng ánh xạ thì người dùng mất yêu thích; hàng `local_`, `tpl_`, `legacy_` giữ nguyên; `gift_reminders` không bị ảnh hưởng (chữ tự do, không có khoá tới quà: `data/model/Entities.kt:177-190`).

#### U-23 — `MarketProfile` thay cho `occasionRegion` (G-05, F08, F-01, F-10, cỡ M; sau U-21)

- **Việc:** lưu MÃ vùng (VN, JP, ...) vào cột `occasionRegion` sẵn có (`TEXT NOT NULL DEFAULT ''`, không cần migration); cập nhật các nơi đọc (hiện chỉ hiểu rỗng, VN và INTL); thay `fixedOccasions` bằng bảng dịp của gói kèm `regions[]`. Giá trị rỗng giữ nghĩa VN cho tới khi có từ 2 gói thật; nước chưa có gói rơi về INTL.
- **Ở đâu:** `ui/viewmodel/InLoveViewModel.kt:608,826,828,1110`; `ui/screens/GiftScreen.kt:368` và `:1281-1388` (chip vùng `:1331-1344`); `data/repository/InLoveRepository.kt:92-93`; `data/db/InLoveDao.kt:137`; `data/db/Migrations.kt:165-171`; `data/model/Entities.kt:98-102`; `data/repository/OnlineCoupleRepository.kt:681`; use case `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt` (hằng số `:35-38,54`, `fixedOccasions` `:46-51`, chọn dịp `:113-133`).
- **Kiểm:** `ui/screens/GiftBudgetRegionControlsTest.kt` (VN, INTL, rỗng), `data/repository/OnlineCoupleProfileSlotTest.kt:179,211` (đã lưu giá trị tuỳ ý `"HN"`), `data/db/Migration13To14Test.kt:39`; thêm test "dịp chỉ thuộc vùng A không hiện ở vùng B".
- **Rủi ro:** `OnlineCoupleRepository.kt:681` chép vùng khi ghép đôi (`insertCoupleProfile` là REPLACE, `:678`) nên hai người khác thị trường sẽ bị một bên ghi đè; app chưa có vai "mình / người ấy" (G-04, làm ở U-37). Không cần nâng v15.

#### U-24 — Bỏ `isEnglish` và `tr()`, dùng mã ngôn ngữ BCP-47 (F-02, F-11, F-12, cỡ L; sau U-23)

- **Việc:** thay `AppLanguage { VI, EN }` bằng mã BCP-47 và một `LanguageResolver` (thứ tự: ngôn ngữ người dùng chọn, ngôn ngữ nguồn của gói, en). Bỏ 678 chỗ `isEnglish` (30 file; 15 file đầu chiếm 623: `ui/screens/Dialogs.kt` 167, `ui/screens/GiftScreen.kt` 83, `ui/screens/CalendarScreen.kt` 59, `ui/screens/PairingScreen.kt` 56, `ui/screens/MemoriesGridScreen.kt` 45, `ui/screens/SettingsScreen.kt` 42, `ui/components/Material3DatePicker.kt` 38, ...) và 56 chỗ `tr("vi","en")` (tất cả trong `ui/viewmodel/InLoveViewModel.kt`; `alarm/ReminderAlarmReceiver.kt` còn một hàm `tr` riêng với 12 lời gọi nữa). Thêm `values-xx` và `locales_config` (manifest chưa có). Chuỗi giao diện sửa ở ba nơi (`res/values*/strings.xml`, `ui/util/Localization.kt`, `ui/util/AppStrings.kt`); xoá luôn chuỗi chết `gift_cat_*` (G-22).
- **Ở đâu:** `ui/util/Localization.kt:8-10` (enum) và `:29-32` (máy không dùng tiếng Việt bị ép sang EN); 30 file nói trên; `AndroidManifest.xml` (thêm `localeConfig`).
- **Kiểm:** `:app:lintDebug` không tăng cảnh báo (trần hiện hành **chưa kiểm chứng**: các kế hoạch cũ ghi ≤ 128 và không còn dư địa, ghi chú gần nhất ghi 131 cảnh báo / 0 lỗi); golden GV-023 (chuỗi theo locale); chạy cả bộ test sau từng nhóm file.
- **Rủi ro:** động tới 30 file đang dùng chung với phiên làm việc khác (xung đột hợp nhất), nên làm từng file nhỏ và chạy test sau mỗi bước; ngôn ngữ mới chưa có người dịch (QĐ-4, QĐ-9).

#### U-25 — Script đẩy gói lên Firestore, luật đọc, test luật (G-11, G-20, F-05, cỡ S; sau U-19)

- **Việc:** script Node trong `scripts/` (dùng `firebase-admin` và service account như `scripts/seed_firestore.js:735-740`): đọc gói, chạy bộ kiểm Node, tạo `packJson` gọn có khoá sắp xếp, tính `sha256` trên đúng chuỗi byte đó, tăng `packVersion`, ghi `content_packs/{region}`. Luật đọc theo mẫu `firestore.rules:310-313`: `match /content_packs/{region} { allow read: if true; allow write: if false; }`. Dọn `scripts/seed_firestore.js`: bỏ `badgeText` bịa ở `:123`, tách cờ `vipOnly` khỏi chuỗi giá ở tài liệu mẫu `:331-342`.
- **Ở đâu:** `scripts/` (file mới); `firestore.rules` (cạnh `:310-325`); `scripts/seed_firestore.js:123,331-342`; `scripts/rules-tests/system-collections.rules.test.js:109`.
- **Kiểm:** thêm `'content_packs'` vào mảng `CATALOGS` (`scripts/rules-tests/system-collections.rules.test.js:109`): bộ test sẵn có tự kiểm đọc được khi đăng nhập và ghi bị từ chối với cả khách; chạy `npm run test:rules` trong `scripts/`. Kiểm đột biến: trỏ `RULES_PATH` (cùng file, `scripts/rules-tests/system-collections.rules.test.js:9-10`) sang bản luật thiếu khối `match` mới, test phải ĐỎ. Thử trên emulator trước khi chạm dự án thật.
- **Rủi ro:** triển khai luật và đẩy gói lên dự án thật là việc của chủ app (nghiên cứu này không làm); `sha256` chỉ chống hỏng hoặc cụt gói, không chống người có quyền ghi (ghi chỉ qua admin SDK); không xoá tài liệu `gift_ideas` cũ khi app cũ còn đọc.

#### U-26 — Thị trường thứ hai thật sự (F08, cỡ L; sau U-20, U-21, U-23, U-24)

- **Việc:** đưa MỘT thị trường mới lên thật; mặc định đề xuất theo mục 8 là Philippines, rồi Brazil, rồi Thái Lan (QĐ-1). Điền trước các band giá còn trống (mọi quà WEST có `min = null`, 4 quà LATAM_TR_CIS; 8/16 dịp LATAM_TR_CIS chưa có `budget`, 12/19 dịp WEST chưa có `budget.mid`), mở rộng độ phủ (hiện PH 5 quà / 2 dịp, BR 5 / 2, TH 4 / 2, đều mỏng), bản địa hoá, nhờ người bản xứ rà (QĐ-9) và rà pháp lý (QĐ-5). Thị trường chưa rà mang nhãn "bản nháp".
- **Ở đâu:** gói dữ liệu mới (nguồn `docs/research/data/gift-occasion-draft.json`), UI chọn thị trường (U-23), ngôn ngữ (U-24).
- **Kiểm:** bộ kiểm U-19 trên gói mới; đọc tay mọi taboo `severity = strong`; test "chọn vùng thì chỉ thấy dịp và quà của vùng đó".
- **Rủi ro:** độ phủ mỏng và sai văn hoá là rủi ro chính (64/142 quà ở mức `confidence = low`); ảnh hot-link (U-36); đổi thị trường làm đổi đơn vị tiền (U-28).

#### U-27 — Tuỳ chọn: cột `contentKey` cho `anniversary_dates` (F17, F20, cỡ M; sau U-21)

- **Việc:** thêm `anniversary_dates.contentKey TEXT NOT NULL DEFAULT ''` (v14 sang v15) để `HolidayDates.refreshExisting` khớp theo khoá thay vì `"${emoji} ${titleVi}"` hoặc `titleEn`, và để lễ mới của gói đến được người dùng cũ mà không nhân đôi hàng đã đổi tên (F20). Dấu "khoá đã thấy" có thể không cần, vì xoá lễ chỉ là xoá mềm (F16).
- **Ở đâu:** `data/db/Migrations.kt`; `data/model/Entities.kt`; `data/sync/AnniversarySyncAdapter.kt`; `firestore.rules:183-191`; `data/repository/OfflineSeeder.kt:175-229`; `data/seed/HolidayDates.kt:30`; `shared/schemas/`.
- **Kiểm:** test migration MỚI (mẫu `Migration13To14Test`), file schema JSON mới, GV-015; GV-026 nếu cột được đồng bộ.
- **Rủi ro:** cột chỉ ở máy cho tới khi `firestore.rules:183-191` và `AnniversarySyncAdapter` được kiểm; MỘT lần nâng v15 gộp với U-28 và làm SAU D-15 (bước kiểm của D-15 đòi tệp `14.json` không đổi; đường dẫn và số dòng ở 6.3.5); nếu QĐ-1 quyết trước D-15 thì nâng trước và sửa bước ghim băm của D-06 cùng bước kiểm của D-15 cho khớp v15.

#### U-28 — Nhiều đơn vị tiền: `budgetMax` và `budgetCurrency` (G-01, F-04, cỡ M; sau U-22)

- **Việc:** thêm hai cột `budgetMax` và `budgetCurrency`, chép từ `budgetMaxVnd` (không RENAME); so `price.min` với ngân sách CÙNG đơn vị tiền; xoá ngân sách khi đổi thị trường; định dạng tiền bằng `NumberFormat.getCurrencyInstance` (cần `expect/actual` cho commonMain). Số chữ số thập phân của JPY, KRW, IDR **chưa kiểm chứng**.
- **Ở đâu:** 8 file cộng 1 (danh sách đầy đủ của `budgetMaxVnd` và chỗ hiển thị giá): `ui/viewmodel/InLoveViewModel.kt:603`; `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:80-86`; `ui/screens/GiftScreen.kt:1323`; `data/model/Entities.kt:100`; `data/db/InLoveDao.kt:137`; `data/db/Migrations.kt:165-171`; `data/repository/InLoveRepository.kt:92-93`; `data/repository/OnlineCoupleRepository.kt:680`; `domain/content/GiftCatalog.kt:96-105`.
- **Kiểm:** test migration (500000 VND giữ nguyên nghĩa), `ui/screens/GiftBudgetRegionControlsTest.kt`, test use case với hai đơn vị tiền.
- **Rủi ro:** MỘT lần nâng v15 cùng U-27, sau D-15; hai người ở hai thị trường: hồ sơ cặp đôi bị chép khi ghép đôi, ngân sách theo người đặt (cần U-37).

### 6.4 Đợt 3: tính năng sau khi có gói nội dung

Mười việc dưới đây (U-29 đến U-38) dùng khoá quà ổn định (U-22), `MarketProfile` (U-23) hoặc `PackContentRepository` (U-21), nên chỉ bắt đầu khi phần đó đã xong. Mỗi việc có bốn mục giống 6.2 (**Việc**, **Ở đâu**, **Kiểm**, **Rủi ro**) và dùng cùng quy ước đường dẫn. Thứ tự đề xuất: U-30 rồi U-35 (cùng sửa `GiftDetailDialog`, nên làm liền nhau hoặc gộp một lần giao), sau đó U-31, U-29, U-32, U-33, U-34. U-36, U-37 và U-38 chỉ làm khi QĐ-1 (mục 9) chọn thị trường thứ hai; riêng U-36 còn cần chủ app chọn nơi lưu ảnh công khai. Mọi việc đều cần chủ app duyệt trước khi viết mã (cổng duyệt của quy trình L).

#### U-29 — Lịch sử quà "đã tặng" và phản ứng (G-08, cỡ M; sau U-22)

- **Việc:** cho ghi lại món quà đã tặng, ngày tặng và phản ứng, rồi dùng lịch sử đó khi gợi ý. Hai cách. (A) Không đổi schema, ghi vào cột `notes` hiện có: dễ vỡ và không xếp hạng được. (B, đề xuất) Thêm cột có giá trị mặc định vào `gift_reminders`: ngày tặng (chữ), phản ứng và `giftKey` (khoá quà trong gói, rỗng nếu là quà tự nhập), nằm trong MỘT lần nâng v14 sang v15 chung (6.5). Không đổi nghĩa của `isCompleted`: hôm nay nó là "đã chuẩn bị", nên "đã tặng" là bước thứ hai. Ngày lưu theo định dạng cố định của U-07 (lịch dương, chữ số ASCII). "Năm sau không tặng trùng" làm bằng cách hạ điểm (không ẩn) quà đã tặng trong 12 tháng qua: `GiftRanker.rank` nhận thêm tham số tập khoá đã tặng, mặc định rỗng để golden GV-016 không đổi (chưa kiểm chứng). Nếu đợt nâng v15 chung không xảy ra (U-27 và U-28 chưa làm), hoãn U-29 thay vì nâng riêng một bản.
- **Ở đâu:** `data/model/Entities.kt:177-190` (`GiftReminderEntity`: chữ tự do, không có khoá tới quà, không có adapter đồng bộ); `ui/viewmodel/InLoveViewModel.kt:1786-1805` (thêm, sửa, đánh dấu "đã chuẩn bị" ở `:1797-1803`, xoá); `ui/screens/GiftScreen.kt:699-807` (mục "Lời Nhắc Quà Tặng Đã Lưu"); `domain/GiftRanker.kt:30-38`. Két dữ liệu tài khoản không cần sửa mã: `data/db/AccountDataVault.kt:117-154` cất bằng `SELECT *`, `:156-197` khôi phục theo giao của hai tập cột (nên bản cất cũ vẫn khôi phục được sau khi thêm cột), và `hasPersonalData` ở `:56-62` đã đếm `gift_reminders`.
- **Kiểm:** test ViewModel (đánh dấu đã tặng, bỏ đánh dấu, ghi phản ứng); test két: cất rồi khôi phục với cột mới, và khôi phục từ một bản cất cũ chưa có cột; `Migration14To15Test` theo mẫu `data/db/Migration13To14Test.kt`; test xếp hạng (quà đã tặng bị hạ, quà chưa tặng giữ thứ tự).
- **Rủi ro:** nâng v15 riêng lẻ thành bản v15 thứ hai, va chạm bước kiểm lược đồ của D-15 (6.5); nhắc cũ dạng chữ tự do không nối được với quà trong gói, nên lịch sử chỉ bắt đầu từ sau khi nâng cấp; nhãn phải tách rõ "đã chuẩn bị" và "đã tặng" để người dùng không nhầm.

#### U-30 — Mở rộng hộp thoại chi tiết quà: cảnh báo mềm, nơi mua, thời gian chuẩn bị (G-08, cỡ M; sau U-21)

- **Việc:** mở rộng `GiftDetailDialog` hiện có (đã có ảnh, nhãn danh mục, mô tả, giá tham khảo, mục "CHI TIẾT THỰC HIỆN", nút yêu thích và "Đã rõ"), không viết màn mới. Thêm ba thứ: (1) ghi chú mềm khi quà có `tabooRefs` (bản nháp có 4 quà; `copyNotes` là trường ĐỀ XUẤT, chưa có trong bản nháp); (2) "nên chuẩn bị trước N ngày" từ `leadTimeDays` (70 trong 142 quà từ 7 ngày trở lên, 31 trong 142 từ 14 ngày trở lên, đủ 142 trên 142); (3) danh sách `whereToBuy` dạng chữ thường (0 trong 142 quà rỗng; không đường dẫn, không affiliate). Các trường này chỉ có trong gói, không có trong Room: lấy `giftId` từ `remoteId = pk:{region}:{giftId}` rồi đọc gói qua kho nội dung (không đổi schema); hàng `seed_`, `local_`, `tpl_` không có trong gói hiển thị như cũ. Thêm một phần không phụ thuộc dữ liệu mới (không cần U-21): làm thẻ gợi ý cá nhân hóa (`PersonalizedGiftCard`) bấm được và vẽ `reason`/`preparation` mà use case đã tính nhưng thẻ hiện không hiển thị; bấm thẻ mở `openGiftDetail(s.idea)`; nút chia sẻ của thẻ tăng từ 40 dp lên 48 dp (`GiftScreen.kt:1244`). Phần thẻ cỡ S và có thể làm ngay ở đợt 1. Chia sẻ đã có (`GiftScreen.kt:161-196,339`, chỉ gồm tên quà và giá tham khảo), không làm lại. Sửa luôn chữ "Close" cứng tiếng Anh ở `Dialogs.kt:639` (trùng với U-18). Ba quà gốc của app chạm điều kiêng kỵ mềm hoặc cần người rà tay: "Đồng Hồ Đôi Phong Cách Tối Giản" ↔ nhóm `clock_watch` (với thị trường Việt Nam chỉ `vn_clock` áp dụng), "Bộ Cốc Đôi In Tên Theo Yêu Cầu" ↔ `vn_cups`, và "Vòng Tay Khắc Tên Đôi" chưa thấy điều kiêng kỵ nào khớp (cần người rà tay, chưa kiểm chứng); U-20 ghi `tabooRefs` cho chúng khi sinh gói VN.
- **Ở đâu:** `ui/screens/Dialogs.kt:581-763` (`GiftDetailDialog`; chỗ rơi về mặc định của "CHI TIẾT THỰC HIỆN" ở `:706-708`), mở từ `MainActivity.kt:510-517`; `ui/screens/GiftScreen.kt:161-196,339` (chia sẻ); `ui/screens/GiftScreen.kt:331-343,1215-1260` (thẻ cá nhân hóa); `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:157-165` (nơi tính `reason`/`preparation`); `data/seed/GiftIdeasSeed.kt:132-133,204-205,383-384` (ba quà gốc nói trên).
- **Kiểm:** test Compose: ghi chú chỉ hiện với quà có `tabooRefs`; hàng không thuộc gói không đổi so với hôm nay; thứ tự `whereToBuy` giữ nguyên; nội dung chia sẻ không đổi; thẻ cá nhân hóa hiện `reason` và `preparation`, bấm thẻ mở `openGiftDetail(s.idea)`, nút chia sẻ của thẻ cao tối thiểu 48 dp.
- **Rủi ro:** cảnh báo văn hoá sai còn tệ hơn không có (64 trong 142 quà có `confidence = low`), nên chỉ là ghi chú nhẹ, không ẩn hay chặn quà; điều kiêng kỵ mức `strong` phải để người bản ngữ xem trước (QĐ-9); hộp thoại đã dài (`ui/screens/Dialogs.kt:581-763`), cần kiểm cuộn và vùng chạm 48 dp (U-18).

#### U-31 — Bấm thông báo mở đúng dịp hoặc đúng màn quà (F12, F13, F14, cỡ M; sau U-15)

- **Việc:** hôm nay thông báo chỉ mở tab "calendar" hoặc "home". Thêm vào `launchIntent` một extra "dịp" (dùng cùng cơ chế mang dịp mà U-15 làm cho nút quà ở lịch) và một đích "gifts" (tab 3), rồi đọc chúng ở chỗ `MainActivity` đang đọc `target_tab`; đưa phép ánh xạ đích sang tab ra thành hàm thuần để kiểm. Hai nút hiện tại là "Mở InLove ❤️" (dùng lại đúng `pendingIntent` của việc bấm thân thông báo, nên trùng) và "Nhắc lại sau 1h ⏰": thay nút đầu bằng "Xem gợi ý quà", giữ nút nhắc lại sau 1 giờ (có thể thêm "mai 9h"). Tuỳ chọn: cảnh báo trước 3 ngày của cột mốc (hiện mở lịch) mở thẳng màn quà.
- **Ở đâu:** `alarm/ReminderAlarmReceiver.kt:150-156` (hằng số extras; lưu ý `EXTRA_TARGET_TAB = "extra_target_tab"` của intent báo thức khác khoá `"target_tab"` của intent mở app), `:200-212` (`launchIntent` và `PendingIntent`), `:243-252` (hai nút), `:63,67,215-227` (nhắc lại sau 1 giờ); `MainActivity.kt:247-256` (chỉ đọc `target_tab`: "calendar" sang tab 2, "home" sang tab 0; tab 3 là quà, `:263-269`, chưa là đích của thông báo nào); `from_notification` và `notification_id` được ghi nhưng không nơi nào trong `app/src/main` đọc; `alarm/plan/LoveMilestoneRules.kt:86-89` (cảnh báo 3 ngày, đích hiện là lịch).
- **Kiểm:** test hàm ánh xạ đích sang tab (hiện không test nào ghim `target_tab`: 0 kết quả); thêm ca vào `alarm/plan/LoveMilestoneRulesTest.kt` nếu đổi đích của cảnh báo 3 ngày.
- **Rủi ro:** `PendingIntent` dùng `notificationId` làm request code nên extras đã tách theo từng thông báo (không phải rủi ro). Rủi ro thật: `launchIntent` dùng `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP` mà `MainActivity` không có `onNewIntent` hay `launchMode`; theo tài liệu Android, `CLEAR_TOP` không kèm `SINGLE_TOP` trên activity chế độ chuẩn sẽ tạo lại activity, nên trạng thái màn hình có thể mất (chưa kiểm chứng trên máy). Thêm `onNewIntent` hoặc `singleTop` đổi hành vi của mọi điểm vào, cần thử trên máy ảo trước.

#### U-32 — Màn "Dịp của bạn" có công tắc thay cho xoá từng lễ (F16, cỡ M; sau U-02, U-21)

- **Việc:** một màn liệt kê mọi dịp (lễ trong gói và dịp tự thêm), mỗi dịp một công tắc bật/tắt, thay cho nút xoá từng lễ. Mặc định đề xuất: chỉ giữ "xoá" cho kỷ niệm do người dùng tự tạo; lễ gieo sẵn chỉ bật/tắt, và lễ đã bị xoá mềm hiện ở trạng thái "tắt" để bật lại được.
- **Ở đâu:** `ui/screens/CalendarScreen.kt:295-312` (trạng thái xoá và `DeleteConfirmationDialog`), `:1055-1067` (công tắc thông báo từng lễ đã có); `ui/viewmodel/InLoveViewModel.kt:1666-1672` (`deleteAnniversaryDate`: xoá mềm, huỷ báo thức, toast "Đã xóa ngày kỷ niệm khỏi thiết bị."), `:1674-1686` (`toggleAnniversaryNotification`). Hàng lễ gieo sẵn có `syncId` rỗng nên không nằm trong phần két tài khoản cất (`data/db/AccountDataVault.kt:253`); `HolidayDates.refreshExisting` khớp hàng theo `"${emoji} ${titleVi}"` hoặc `titleEn` (`data/seed/HolidayDates.kt:32-34,42-44`) cho tới khi có `contentKey` của U-27.
- **Kiểm:** test ViewModel (tắt thì không còn báo thức, bật lại thì có lại, kỷ niệm tự tạo vẫn xoá được); test Compose cho màn mới.
- **Rủi ro:** đổi thói quen của người đang dùng (nút xoá biến mất với lễ gieo sẵn); F20: đổi tên lễ trong gói làm lệch việc khớp theo chữ, nên U-27 nên đi trước nếu tên lễ sẽ đổi.

#### U-33 — Cột mốc theo tháng và lựa chọn "ngày đầu hẹn hò hay ngày chính thức" (cỡ M; sau U-04)

- **Việc:** thêm cột mốc theo tháng và cho người dùng chọn ngày nào làm gốc đếm. Dữ liệu: gói VN có `vn_monthsary` (1, 3, 6, 9 tháng; `confidence = low`, chưa xác nhận người Việt đếm theo tháng lịch), gói PH có `ph_monthsary` (trọng số văn hoá 3, mạnh nhất). QĐ-3 mặc định: nhắc theo tháng TẮT, người dùng tự bật. Giữ MỘT ngày bắt đầu đã lưu làm gốc (`CoupleProfileEntity.anniversaryDate`, đọc qua `loveDaysOn`); lựa chọn "ngày đầu hẹn hò hay ngày chính thức" chỉ là cách người dùng nhập vào ô hiện có, màn hình ghi chú rõ hai cách hiểu, không thêm cột. Dùng lại cách xử lý cuối tháng của `occurrenceDate` (ngày không tồn tại như 31/04 rơi vào ngày cuối tháng) và hai vòng lặp ngày và năm trong `LoveMilestoneRules.plan`.
- **Ở đâu:** `alarm/plan/LoveMilestoneRules.kt:25-38` (`DAY_MILESTONES`, đã có mốc 30 ngày), `:46-47` và `:52-63` (cuối tháng, vòng lặp ngày và năm), `:86-94` (ba cảnh báo cho mỗi cột mốc); `data/model/Entities.kt:95` (`anniversaryDate`); `ui/util/ProfileUtils.kt:229` (`loveDaysOn`).
- **Kiểm:** ca mới trong `alarm/plan/LoveMilestoneRulesTest.kt` (ngày 29, 30, 31, năm nhuận, mặc định tắt, đã bật).
- **Rủi ro:** thêm thông báo (vì thế mặc định tắt); giới hạn báo thức chính xác (F09, làm ở U-13); cách người Việt đếm "tháng" (đủ 30 ngày hay đúng ngày mỗi tháng) chưa xác nhận, và app hôm nay chỉ có mốc 30 ngày trong `DAY_MILESTONES`.

#### U-34 — Cho chọn giờ nhắc (F10, cỡ M; sau U-13)

- **Việc:** giờ nhắc hiện là hằng số cố định: 09:00 cho hai cảnh báo trước (3 ngày và 1 ngày), 08:30 cho ngày chính. `reminder_settings` (`ReminderCadenceEntity`) chỉ có `key`, `label`, `isEnabled`, không có cột giờ. Mặc định đề xuất: lưu giờ trong SharedPreferences theo mẫu đã có (`platform/SharedPrefsAppSettings.kt`, file `inlove_notifications`, giao diện `AppSettings`), KHÔNG thêm cột Room để khỏi thêm một lần nâng schema. `plan(input, nowMillis, zone)` đã nhận múi giờ; khi đổi giờ gọi `requestResync()` (`alarm/AndroidReminderScheduler.kt:49`) để đặt lại báo thức. Nhãn "00:00 Ngày lễ" (`data/repository/OfflineSeeder.kt:26`, F19) sửa ở U-13.
- **Ở đâu:** `alarm/plan/LoveMilestoneRules.kt:14,86-94`; `platform/SharedPrefsAppSettings.kt` (khoá mới cạnh `KEY_ANNIVERSARY_ENABLED`); `ui/settings/AppSettings` (thêm phương thức); `ui/screens/SettingsScreen.kt` hoặc màn nhắc hẹn cho ô chọn giờ (chỗ đặt chưa xem).
- **Kiểm:** ca mới trong `alarm/plan/LoveMilestoneRulesTest.kt` (giờ tuỳ chọn cho ra đúng thời điểm báo thức); test đọc và ghi giá trị mặc định của cài đặt giờ.
- **Rủi ro:** báo thức chính xác có thể không khả dụng (F09) nên giờ nhắc có thể lệch vài phút, vì vậy việc này đi sau U-13; thêm phương thức vào `AppSettings` buộc mọi lớp cài đặt cùng giao diện phải sửa theo (chưa đếm có bao nhiêu).

#### U-35 — Nút "tìm quà" và nơi mua (chưa có affiliate) (G-08, F-18, cỡ M; sau U-21)

- **Việc:** thêm lối "Tìm quà này" trong hộp thoại chi tiết (tuỳ chọn thêm trên thẻ): mở tìm kiếm web của hệ thống với tên quà, không affiliate, không gửi dữ liệu cá nhân (QĐ-8: chưa làm affiliate). Phần HIỂN THỊ danh sách `whereToBuy` thuộc U-30. Nút hành động trên thẻ hiện đã gọi `openGiftDetail` (`GiftScreen.kt:593,903,994`), giữ nguyên; nút mới nằm trong hộp thoại. Cơ chế (`ACTION_WEB_SEARCH` hay `ACTION_VIEW` với địa chỉ tìm kiếm) và việc có cần khai báo `<queries>` hay không: chưa kiểm chứng. Tác động lên mục Data Safety của Google Play: cần kiểm khi làm.
- **Ở đâu:** `ui/screens/Dialogs.kt:581-763`; `ui/screens/GiftScreen.kt:593,903,994`; `ui/viewmodel/InLoveViewModel.kt:1368` (`openGiftDetail`); `AndroidManifest.xml` (chỉ nếu cần `<queries>`).
- **Kiểm:** test Compose: nút có mặt; intent tìm kiếm chỉ chứa tên quà (mô phỏng, không mở trình duyệt thật).
- **Rủi ro:** tìm kiếm web có thể dẫn tới bên bán không đáng tin; nhãn nút cần ở cả hai ngôn ngữ (3 nơi chuỗi, 6.5); vùng chạm 48 dp.

#### U-36 — Ảnh quà tự lưu trữ (G-16, F-14, cỡ L; sau U-21)

- **Việc:** hôm nay ảnh quà là liên kết nóng (`imageUrl = curatedImageUrl ?: picsum`; 19 trong 20 quà gốc dùng Pexels, 1 dùng picsum). Gói nội dung (U-19) cho phép trường `imageUrl` tuỳ chọn (https và danh sách host cho phép), nên quà trong gói chưa có ảnh cho tới U-36. Ba phương án: nhét ảnh vào APK (kích thước chưa đo), bucket công khai trên R2 hoặc Cloudinary, hoặc giữ liên kết nóng cho tới khi có thị trường thứ hai. Kế hoạch R2 hiện có chỉ dành cho ảnh và video RIÊNG của người dùng (bucket riêng, DEC-267 ở `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:284`; các bước phía chủ app trên Cloudflare chưa làm), không dùng cho ảnh danh mục công khai, nên chủ app phải chọn nơi lưu riêng.
- **Ở đâu:** `data/seed/GiftIdeasSeed.kt:49-50`; `ui/testing/NoNetworkImagesRule.kt` (luật test chặn tải ảnh mạng); `docs/superpowers/plans/2026-10-21-r2-media-provider.md`.
- **Kiểm:** test host cho phép trong bộ kiểm gói (U-19); test Compose cho thẻ quà KHÔNG có ảnh (chưa đọc cách thẻ hiển thị khi thiếu `imageUrl`: chưa kiểm chứng); `NoNetworkImagesRule` vẫn xanh.
- **Rủi ro:** điều khoản dùng ảnh Pexels qua liên kết nóng chưa kiểm; kích thước APK; chi phí băng thông của ảnh công khai (chưa ước lượng); cần chủ app chọn nơi lưu.

#### U-37 — Giọng trung tính, vai "mình / người ấy", bộ quà "anh ấy", "chúng mình không tặng quà lễ" (G-04, cỡ L; sau U-23)

- **Việc:** (1) bỏ cách coi cả hai ngày sinh là "quà cho người kia": thêm vai "mình / người ấy" và logic `forPartner` cho sinh nhật; (2) viết lại chuỗi theo giọng trung tính, không giả định giới tính; (3) bổ sung bộ quà "anh ấy": bản nháp chỉ có 1 trong 142 quà dành cho `him` (`jp_g_001`), 32 cho `her`, 109 cho `any`, cần thêm khoảng 15 quà "anh ấy" (ước lượng); (4) cờ "chúng mình không tặng quà lễ" để tắt gợi ý quà theo lễ. Có thể cần cột mới trên `couple_profile` (vai, cờ), nằm trong lần nâng v15 chung (6.5).
- **Ở đâu:** `ui/viewmodel/InLoveViewModel.kt:268,271,283,286` (hiện chỉ có `boyName`, `boyBirthDate`, `girlName`, `girlBirthDate`, không có vai "mình / người ấy") và `:588-598`; `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt:113`; `data/model/Entities.kt:80-103` (entity dùng `partner1*` và `partner2*`, `:83-92`); `data/repository/OnlineCoupleRepository.kt:678-681` (chép `likesCsv`, `budgetMaxVnd`, `occasionRegion` khi ghi đè hồ sơ).
- **Kiểm:** `data/repository/OnlineCoupleProfileSlotTest.kt` (đã có kiểm ghép khe với hồ sơ); test use case: sinh nhật của "mình" không còn tính là quà cho chính mình; test cột mới sống sót qua đường REPLACE.
- **Rủi ro:** `insertCoupleProfile` là REPLACE nên mọi cột mới trên `couple_profile` phải được chép ở `OnlineCoupleRepository.kt:679-681`, nếu không sẽ bị đặt lại; việc ánh xạ "khe" khi ghép đôi trực tuyến sang "mình" chưa kiểm; đụng nhiều chuỗi (678 điểm `isEnglish` trong 30 file, 56 chỗ `tr(...)` trong `InLoveViewModel.kt`) nên cỡ L; không ép giới tính.

#### U-38 — Rà soát font và chữ phải-sang-trái (F-12, cỡ M; sau U-24)

- **Việc:** chạy trước một phép thử rẻ trên máy ảo ngôn ngữ Ả Rập, Thái, Hindi: giao diện hiện chỉ có VI và EN, nhưng `Locale.getDefault()` vẫn là ngôn ngữ của máy, nên người dùng máy Thái hay Ả Rập có thể đã gặp ngày bị định dạng lệch (F-08) hoặc chữ bị cắt (chưa kiểm chứng). Sau đó rà `FontFamily.Default`, các `lineHeight` cố định và RTL (`supportsRtl="true"` nhưng chưa ai kiểm).
- **Ở đâu:** `ui/theme/Type.kt` (một `FontFamily.Default` và 9 dòng `lineHeight =`); 36 dòng `lineHeight =` trong 12 file Kotlin; `AndroidManifest.xml:26` (`android:supportsRtl="true"`); `res/xml` chưa có `locales_config`.
- **Kiểm:** ảnh chụp Roborazzi với qualifier `ar` và `th` (chưa kiểm chứng chạy được trong cấu hình hiện tại); kiểm tay trên máy ảo.
- **Rủi ro:** chỉ đáng làm khi thật sự thêm ngôn ngữ (U-24); sửa `lineHeight` đổi hình thức cả app; cần người bản ngữ xem (QĐ-9).

### 6.5 Những thay đổi cần cờ riêng

Bảng này gom các thay đổi có tác động rộng hơn một file, để chủ app thấy chúng ở một chỗ trước khi duyệt.

| Loại thay đổi | Việc liên quan | Cần làm kèm |
|---|---|---|
| Schema Room v14 sang v15 (MỘT lần nâng) | U-27 (`contentKey`), U-28 (`budgetMax` và `budgetCurrency`, thêm cột, không RENAME), U-29 (cột trên `gift_reminders`), U-37 (cột trên `couple_profile`) | U-34 mặc định dùng SharedPreferences nên KHÔNG cần cột. Va chạm với D-15 (bước kiểm của D-15 chạy `git diff --exit-code -- shared/schemas`, nên tệp `14.json` không được đổi): mặc định nâng SAU D-15; nếu quyết thị trường thứ hai (QĐ-1) trước D-15 thì nâng trước và sửa bước ghim băm của D-06 cùng bước kiểm của D-15 (6.3.5; QĐ-1 và QĐ-2 ở mục 9). Kế hoạch KMP cũng nêu tên `Migration14To15Test` cho trường hợp D-06 buộc `Migration(14, 15)` rỗng (`docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:411`): dùng chung MỘT test. Test `Migration14To15Test` theo mẫu `data/db/Migration13To14Test.kt` |
| Quy tắc Firestore | U-25 | thêm khối chỉ-đọc `content_packs/{region}` (mẫu `firestore.rules:310-313`) và thêm `'content_packs'` vào `scripts/rules-tests/system-collections.rules.test.js:109`; kiểm đột biến: trỏ `RULES_PATH` vào bản sao thiếu khối `match`, test phải ĐỎ |
| Chữ ký API | U-19 đến U-23, U-28, U-29, U-34 | `ContentPack`, `Price`, `PackContentRepository`, `MarketProfile`, `LanguageResolver`, `invoke` của `GetPersonalizedGiftSuggestionsUseCase`, `LoveMilestoneRules.plan`, có thể `AppSettings` (U-34) và `GiftRanker.rank` (U-29) |
| Golden (hợp đồng, `GOLDEN_UPDATE=1`, đọc từng dòng khác biệt) | U-01: GV-013 và GV-015 (đã đối chiếu); U-09: GV-012 và GV-204 (đã đối chiếu) | các việc khác (U-03, U-06, U-22, U-28...) chỉ là DỰ ĐOÁN; chỉ chạy `GOLDEN_UPDATE=1` mới biết; không sửa golden cho xanh |
| Bảng khoá `remoteId` cũ sang mới | U-22 | MỘT bảng `legacyRemoteId` dùng chung cho upsert `data/db/InLoveDao.kt:73-85` (giữ `isFavorited`, `:81`) và két tài khoản `data/db/AccountDataVault.kt:260`; người dùng đã bấm yêu thích không được mất; mẫu test: `CloudEnrichmentDataTest.kt:115-145` |
| Chuỗi ở 3 nơi | U-03, U-05, U-18, U-30, U-35, U-37 | `res/values/strings.xml` (hai locale), `ui/util/Localization.kt`, `ui/util/AppStrings.kt` |
| Mức lint | mọi việc | kế hoạch cũ ghi "0 lỗi, 128 cảnh báo" và ghi chú mới nhất ghi 131 cảnh báo; CHƯA chạy lại hôm nay. Chạy `:app:lintDebug` trước và sau mỗi đợt; số cảnh báo không được tăng |
| Không đổi | không | tệp lược đồ `14.json` (`app/schemas/com.example.data.db.AppDatabase/14.json`; F-16 của kế hoạch KMP chuyển nó sang `shared/schemas/`), payload Moshi của outbox (GV-201), các adapter đồng bộ (trừ U-27 và F17), thanh toán (billing), quảng cáo |

### 6.6 Bảng đối chiếu: từ mục thị trường và từ phát hiện đến việc cần làm

Hai bảng dưới đây giúp đi ngược từ một mục ở mục 4 hoặc một phát hiện ở mục 3 đến mã việc U-xx ở 6.1. "QĐ-n" là quyết định ở mục 9; "Không làm" nghĩa là chủ ý không làm (danh sách cuối 6.1). Lưu ý `F18` (không có dấu gạch: `checkAndNotifyTodayAnniversaries` so ngày và tháng, làm ở U-13) khác `F-18` (có dấu gạch: chưa có cách tìm hay mua quà, làm ở U-35).

#### 6.6.1 Từ mục thị trường (mục 4) đến việc

**4.1 Việt Nam (thị trường gốc)**

| Điểm | Nội dung (rút gọn) | Việc |
|---|---|---|
| 1 | Sửa Rằm tháng Giêng 2027 | U-01, U-17 |
| 2 | Đêm Noel 24/12: chỉ nằm trong `WesternHolidays` nên người dùng bản VN không thấy | U-09 |
| 3 | Báo trước dài hơn cho dịp phải đặt chỗ hoặc làm đồ cá nhân hóa | U-16 (dữ liệu ở U-20) |
| 4 | Giữ nguyên bốn bậc giá hiện tại | U-22, U-28 |
| 5 | Bổ sung danh mục và nhãn | U-20, U-22 |
| 6 | Quà số miễn phí | U-20 (chỉ phần dữ liệu, chưa kiểm chứng) |
| 7 | Không đổi copy 8/3 và 20/10 thành "chỉ cho mẹ" | Không làm hoặc hoãn (6.1) |

**4.2 Đông Á (Hàn Quốc, Nhật Bản, Trung Quốc, Đài Loan, Hồng Kông)**

| Điểm | Nội dung (rút gọn) | Việc |
|---|---|---|
| 1 | Thêm mốc 22, 520, 1314 ngày và mốc tháng 1-12 | U-16 (dữ liệu), U-33 |
| 2 | Thêm dịp bật mặc định theo vùng | U-23, U-26 |
| 3 | Cờ nhạy cảm theo vùng | U-20, U-30 |
| 4 | Lead time theo dịp | U-16 |
| 5 | Thang giá theo tiền địa phương | U-22, U-28 |
| 6 | Bỏ giả định "nam tặng nữ" | U-37 |
| 7 | Region mặc định suy từ locale | U-23 |
| 8 | Nền tảng: nếu nhắm Nhật thì iOS là điều kiện (hai nguồn thị phần mâu thuẫn | QĐ-2 |
| 9 | Hoãn: Black Day, Rose Day, Silver Day, Quang Côn Tiết (không phải dịp đôi), 314 | Không làm hoặc hoãn (6.1) |

**4.3 Phương Tây (Mỹ, Canada, Anh, Úc, Đức, Pháp, Tây Ban Nha, Ý)**

| Điểm | Nội dung (rút gọn) | Việc |
|---|---|---|
| 1 | Làm trước: thêm Easter và sửa Mothering Sunday cho Anh | U-19 (lược đồ), U-26 |
| 2 | Làm trước: mặc định theo quốc gia, không theo ngôn ngữ giao diện | U-23 |
| 3 | Làm trước: báo trước đúng mức đặt chỗ | U-16 |
| 4 | Làm trước: thay "N-Year Love Anniversary" chung bằng tên theo năm | U-20 |
| 5 | Làm sau: giá nhiều tiền tệ | U-22, U-28 |
| 6 | Làm sau: tùy chọn "ngày hẹn đầu / ngày chính thức" | U-33 |
| 7 | Làm sau: văn phong trung tính giới tính | U-37 |
| 8 | Làm sau nữa: tab "Hoa" cảnh báo số bông lẻ và hoa cúc theo vùng (Pháp, Ý, Đức) | U-20, U-30 |
| 9 | Nền tảng: ưu tiên iOS cho Mỹ, Anh, Úc | QĐ-2 |

**4.4 Đông Nam Á và Ấn Độ (Philippines, Thái Lan, Indonesia, Malaysia, Singapore, Ấn Độ)**

| Điểm | Nội dung (rút gọn) | Việc |
|---|---|---|
| 1 | Tách "thị trường" khỏi "ngôn ngữ giao diện" | U-23 |
| 2 | Valentine tắt mặc định cho Indonesia và Malaysia, bật cho Philippines, Thái Lan,… | U-15, U-23 |
| 3 | Ramadan 2027 là cờ ngữ cảnh, không phải ngày tặng quà | Hoãn: chỉ khi chọn ID hoặc MY (U-26) |
| 4 | Ấn Độ: nhóm "Valentine Week" opt-in, Karwa Chauth opt-in, Diwali nhắc trước 10-14 ngày | U-19 (quy tắc `table`), U-26 |
| 5 | Thời gian báo trước theo dịp | U-16 |
| 6 | Giá bằng nội tệ, không quy đổi sang VND | U-28 |
| 7 | Danh mục và nhãn mới | U-20, U-22 |
| 8 | Cân bằng loại quà | U-20 |
| 9 | Cảnh báo kiêng kỵ mềm | U-22, U-30 |
| 10 | Cách viết: không dùng "Ngày Tình Nhân" làm tiêu đề dẫn đầu ở Indonesia và Malaysia | U-24, QĐ-9 |
| 11 | Sửa định dạng ngày trước khi mở Thái Lan | U-07 |
| 12 | Ngôn ngữ thêm (id, th, hi, fil) | U-24, QĐ-4 |
| 13 | Một dòng về quyền riêng tư | QĐ-6 |

**4.5 Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS (kèm Ba Lan và vùng Vịnh)**

| Điểm | Nội dung (rút gọn) | Việc |
|---|---|---|
| 1 | Bộ dịp theo thị trường thay vì chỉ "Valentine 14/2" | U-23, U-26 |
| 2 | Nga và Ukraine: tắt Valentine 14/2 mặc định, bật 8/3 và 23/2 (Nga) | U-23, QĐ-5, QĐ-9 |
| 3 | Vùng Vịnh (SA, KW) | QĐ-5 |
| 4 | Mốc theo tháng: Brazil gợi ý bật mesversário (1-11 tháng) và để người dùng tắt, các… | U-33 (và QĐ-3) |
| 5 | Cảnh báo hoa: Nga và Ukraine hiện nhắc "số lẻ" | U-20, U-30 |
| 6 | Báo trước theo dịp | U-16 |
| 7 | Nhóm quà mới và lấp lỗ hổng | U-37, U-20 |
| 8 | Hạng giá theo nội tệ | U-28 |
| 9 | Lời văn Colombia | U-20, QĐ-9 |
| 10 | Ngôn ngữ và kênh mua (làm sau nữa) | U-24, U-35, QĐ-4, QĐ-8 |

#### 6.6.2 Từ phát hiện (mục 3) đến việc

| Phát hiện | Mục | Việc | Đợt |
|---|---|---|---|
| G-01 | 3.1 | U-22, U-28 | 2 |
| G-02 | 3.1 | U-06, U-20 | 1, 2 |
| G-03 | 3.1 | U-15 | 1 |
| G-04 | 3.1 | U-10, U-37 | 1, 3 |
| G-05 | 3.1 | U-15, U-23 | 1, 2 |
| G-06 | 3.1 | U-10 | 1 |
| G-07 | 3.1 | U-03, U-11 | 1 |
| G-08 | 3.1 | U-29, U-30, U-35 | 3 |
| G-09 | 3.1 | U-12 | 1 |
| G-10 | 3.1 | U-12, U-21 | 1, 2 |
| G-11 | 3.1 | U-19, U-25 | 2 |
| G-12 | 3.1 | U-12 | 1 |
| G-13 | 3.1 | U-11 | 1 |
| G-14 | 3.1 | U-03 | 1 |
| G-15 | 3.1 | U-08 | 1 |
| G-16 | 3.1 | U-36 | 3 |
| G-17 | 3.1 | U-06 | 1 |
| G-18 | 3.1 | U-18 | 1 |
| G-19 | 3.1 | U-18 | 1 |
| G-20 | 3.1 | U-03, U-25 | 1, 2 |
| G-21 | 3.1 | U-18 | 1 |
| G-22 | 3.1 | U-18 | 1 |
| G-23 | 3.1 | U-14 | 1 |
| G-24 | 3.1 | U-22 | 2 |
| F01 | 3.2 | U-04 | 1 |
| F02 | 3.2 | U-05 | 1 |
| F03 | 3.2 | U-13 | 1 |
| F04 | 3.2 | U-02 | 1 |
| F05 | 3.2 | U-21 | 2 |
| F06 | 3.2 | U-17 | 1 |
| F07 | 3.2 | U-01 | 1 |
| F08 | 3.2 | U-09, U-23, U-26 | 1, 2 |
| F09 | 3.2 | U-13 | 1 |
| F10 | 3.2 | U-16, U-34 | 1, 3 |
| F11 | 3.2 | U-04 | 1 |
| F12 | 3.2 | U-05, U-31 | 1, 3 |
| F13 | 3.2 | U-31 | 3 |
| F14 | 3.2 | U-15, U-31 | 1, 3 |
| F15 | 3.2 | U-14 | 1 |
| F16 | 3.2 | U-32 | 3 |
| F17 | 3.2 | U-27 | 2 |
| F18 | 3.2 | U-13 | 1 |
| F19 | 3.2 | U-13 | 1 |
| F20 | 3.2 | U-17, U-27 | 1, 2 |
| F21 | 3.2 | không có việc riêng | — |
| F-01 | 3.3 | U-21, U-23 | 2 |
| F-02 | 3.3 | U-24 | 2 |
| F-03 | 3.3 | U-22 | 2 |
| F-04 | 3.3 | U-22, U-28 | 2 |
| F-05 | 3.3 | U-19, U-21, U-25 | 2 |
| F-06 | 3.3 | U-21 | 2 |
| F-07 | 3.3 | U-04 | 1 |
| F-08 | 3.3 | U-07 | 1 |
| F-09 | 3.3 | U-20, U-21 | 2 |
| F-10 | 3.3 | U-23 | 2 |
| F-11 | 3.3 | U-24 | 2 |
| F-12 | 3.3 | U-24, U-38 | 2, 3 |
| F-13 | 3.3 | U-03 | 1 |
| F-14 | 3.3 | U-36 | 3 |
| F-15 | 3.3 | U-03 | 1 |
| F-16 | 3.3 | U-17 | 1 |
| F-17 | 3.3 | U-22 | 2 |
| F-18 | 3.3 | U-35 | 3 |

F21 (không có test cho F01, F02, F03 và cho việc bảng âm lịch hết hạn sau 2028) không có việc riêng trong bảng 6.1. Các test đó nằm ở phần "Kiểm" của U-04 (F01), U-05 (F02), U-13 (F03) và U-17 (bảng âm lịch hết hạn). F18 dẫn tới U-13 và F-18 dẫn tới U-35.

## 7. Dữ liệu đã làm giàu và bảng âm lịch

**Ý chính.** Nghiên cứu để lại một bộ dữ liệu **nháp** gồm 87 dịp, 55 cột mốc, 142 món quà và 70 điều kiêng kỵ cho 5 thị trường (35 mã quốc gia hoặc vùng), kèm bảng âm lịch Việt Nam và Trung Quốc 2025-2040 đã đối chiếu với nguồn. Bộ dữ liệu **chưa nối vào app**: `meta.status` ghi rõ "bản nháp nghiên cứu", và 128 trên 142 quà, 71 trên 87 dịp, 54 trên 55 cột mốc, 67 trên 70 điều kiêng đang ở độ tin cậy `low` hoặc `med`. Mọi bản ghi đều có `confidence` và `sources`, nhưng `sources` **không phải lúc nào cũng là đường dẫn**: 78 trên 354 bản ghi mang ít nhất một nhãn văn bản ("from memory, unverified" hoặc "computed"), và 66 bản ghi trong số đó không có đường dẫn nào (độ tin cậy của 66 bản ghi này: `low` 66). Toàn bộ dữ liệu có 455 đường dẫn, 196 đường dẫn khác nhau. Mọi con số trong mục này do script đếm lại từ chính file dữ liệu, không gõ tay.

Các tệp đi kèm (đều nằm trong `docs/research/data/`, xem 7.7): `gift-occasion-draft.json` (dữ liệu), `lunar-2025-2040.json` (bảng âm lịch), `lunar_reference.py` (cài đặt tham chiếu của thuật toán), `sources.md` (nguồn).

### 7.1 Số lượng theo gói thị trường

| Gói | Dịp | Cột mốc | Quà | Kiêng kỵ | Tổng bản ghi | Mã vùng |
|---|---:|---:|---:|---:|---:|---:|
| VN | 15 | 11 | 32 | 15 | 73 | 1 |
| EA | 18 | 10 | 24 | 14 | 66 | 5 |
| WEST | 19 | 19 | 30 | 14 | 82 | 8 |
| SEA_SA | 19 | 7 | 28 | 13 | 67 | 6 |
| LATAM_TR_CIS | 16 | 8 | 28 | 14 | 66 | 15 |
| **Tổng** | 87 | 55 | 142 | 70 | 354 | 35 |

Gói và các mã vùng bên trong (mã nằm trong một gói nếu có ít nhất một bản ghi của gói đó gắn mã; trong dữ liệu hiện tại không mã nào thuộc hai gói, nên tổng cột "Mã vùng" bằng số mã khác nhau):

- **VN**: VN
- **EA (Đông Á)**: CN, HK, JP, KR, TW
- **WEST (Phương Tây)**: AU, CA, DE, ES, FR, GB, IT, US
- **SEA_SA (Đông Nam Á, Nam Á)**: ID, IN, MY, PH, SG, TH
- **LATAM_TR_CIS (Mỹ Latinh, Thổ Nhĩ Kỳ, Nga và CIS, Ba Lan, vùng Vịnh)**: AE, AR, BR, CL, CO, EG, KW, KZ, MX, PE, PL, RU, SA, TR, UA

Kích thước từng gói khi đóng gói (byte, bản có và không có `sources`, `confidence`) đã đo ở 6.3.2; không lặp lại ở đây.

### 7.2 Trường dữ liệu của từng loại bản ghi

Cột "Có" đếm bản ghi có khoá đó và giá trị khác rỗng. Cột "Giá trị" là phân bố của trường dạng liệt kê.

**Dịp (`occasions`): 87 bản ghi**

| Trường | Có | Ý nghĩa | Giá trị |
|---|---:|---|---|
| `id` | 87 | Khoá ổn định, dạng `<mã vùng>_<tên>`; làm khoá nghiệp vụ thay cho chữ hiển thị |  |
| `names` | 87 | `vi`, `en`, `local` (tên bản địa), `localLang` (mã ngôn ngữ của `local`) |  |
| `regions` | 87 | Mã quốc gia hoặc vùng áp dụng |  |
| `kind` | 87 | Loại dịp | romantic_holiday 40, couple_custom 17, cultural_festival 16, family_holiday 8, national_holiday 6 |
| `dateRule` | 87 | Cách tính ngày; `type` là một trong 7 loại (xem 7.4) | fixed_solar 61, lunar 7, user_defined 7, table 5, relative 3, nth_weekday 2, week 2 |
| `coupleRelevance` | 87 | Mức liên quan cặp đôi 0-3 (5.1) | 3 28, 2 31, 1 25, 0 3 |
| `giftExpectation` | 87 | Kỳ vọng tặng quà (5.1) | expected 36, small 31, none 14, major 6 |
| `whoGives` | 87 | Ai tặng ai (5.2, thói quen 1) | both 30, either 25, male_to_female 18, varies 10, female_to_male 4 |
| `budget` | 79 | `currency`, `low`, `mid`, `high`: mức chi tiêu điển hình bằng tiền địa phương |  |
| `leadTimeDays` | 87 | Số ngày nên chuẩn bị trước |  |
| `bookingAdvised` | 87 | Nên đặt chỗ trước (nhà hàng, khách sạn) | False 54, True 33 |
| `customs` | 87 | Phong tục, một đoạn văn ngắn |  |
| `sensitivity` | 87 | Cổng tôn giáo hoặc quy định địa phương | none 68, religious_sensitive 11, restricted_in_places 8 |
| `defaultOn` | 87 | Bật nhắc mặc định cho người dùng mới của thị trường (5.3) | False 44, True 43 |
| `confidence` | 87 | Độ tin cậy `high`, `med`, `low` | med 42, low 29, high 16 |
| `sources` | 87 | Danh sách nguồn: đường dẫn `http(s)` hoặc nhãn văn bản (xem 7.5) |  |
| `pack` | 87 | Gói thị trường | SEA_SA 19, WEST 19, EA 18, LATAM_TR_CIS 16, VN 15 |
| `nzNote` | 1 | Ghi chú riêng (chỉ ở dịp Valentine Úc: không có nguồn cho New Zealand) |  |

**Cột mốc (`milestones`): 55 bản ghi**

| Trường | Có | Ý nghĩa | Giá trị |
|---|---:|---|---|
| `id` | 55 | Khoá ổn định |  |
| `regions` | 55 | Mã quốc gia hoặc vùng áp dụng |  |
| `unit` | 55 | Đơn vị đếm | year 35, day 12, month 8 |
| `values` | 55 | Các mốc, ví dụ `[100, 365]` |  |
| `names` | 55 | `vi`, `en`, `local` |  |
| `culturalWeight` | 55 | Trọng số văn hoá 1-3: mốc đó quan trọng cỡ nào ở thị trường | 3 20, 2 19, 1 16 |
| `customs` | 55 | Phong tục, một đoạn văn ngắn |  |
| `confidence` | 55 | Độ tin cậy | med 30, low 24, high 1 |
| `sources` | 55 | Danh sách nguồn: đường dẫn `http(s)` hoặc nhãn văn bản (xem 7.5) |  |
| `pack` | 55 | Gói thị trường | WEST 19, VN 11, EA 10, LATAM_TR_CIS 8, SEA_SA 7 |
| `recurrence` | 2 | Mốc lặp lại (chỉ `ph_monthsary` hằng tháng và `id_hari_jadian_monthly` tuỳ chọn) |  |

**Quà (`gifts`): 142 bản ghi**

| Trường | Có | Ý nghĩa | Giá trị |
|---|---:|---|---|
| `id` | 142 | Khoá ổn định, dạng `<mã>_g_<số>` |  |
| `title` | 142 | `vi`, `en` |  |
| `category` | 142 | Nhóm quà (10 nhóm, xem 7.4) |  |
| `description` | 142 | `vi`, `en` |  |
| `regions` | 142 | Mã quốc gia hoặc vùng |  |
| `occasions` | 142 | Id dịp, hoặc token chung `anniversary`, `birthday`, `any` |  |
| `priceTier` | 142 | Bậc giá 1-4, tương đối trong thị trường | 4 12, 3 35, 2 58, 1 37 |
| `priceBand` | 142 | `currency`, `min`, `max`: khoảng giá bằng tiền địa phương |  |
| `interests` | 141 | Khoá sở thích (38 khoá, có cặp lặp, xem 7.4) |  |
| `loveLanguages` | 142 | Nhãn "ngôn ngữ tình yêu" (5.5: chỉ để ghi nhận, không dựng tính năng riêng) |  |
| `effort` | 142 | Hình thức | buy_ready 59, experience 36, personalized_order 26, diy 13, digital 8 |
| `leadTimeDays` | 142 | Số ngày nên chuẩn bị trước |  |
| `whereToBuy` | 142 | Nơi mua, chữ thuần (7.4) |  |
| `recipient` | 142 | Người nhận | any 109, her 32, him 1 |
| `trendNote` | 142 | Ghi chú xu hướng |  |
| `confidence` | 142 | Độ tin cậy | med 64, low 64, high 14 |
| `sources` | 142 | Danh sách nguồn: đường dẫn `http(s)` hoặc nhãn văn bản (xem 7.5) |  |
| `pack` | 142 | Gói thị trường | VN 32, WEST 30, LATAM_TR_CIS 28, SEA_SA 28, EA 24 |
| `tabooRefs` | 4 | Id điều kiêng liên quan (cảnh báo mềm); QA đã thêm cho 4 quà |  |

**Kiêng kỵ (`taboos`): 70 bản ghi**

| Trường | Có | Ý nghĩa | Giá trị |
|---|---:|---|---|
| `id` | 70 | Khoá ổn định |  |
| `regions` | 70 | Mã quốc gia hoặc vùng |  |
| `type` | 70 | Loại kiêng | gift 33, behavior 14, flower 13, number 6, color 4 |
| `item` | 70 | `vi`, `en`: món hoặc hành vi bị kiêng |  |
| `reason` | 70 | Lý do văn hoá |  |
| `severity` | 70 | Mức độ: `strong` hoặc `soft` | soft 55, strong 15 |
| `confidence` | 70 | Độ tin cậy | med 34, low 33, high 3 |
| `sources` | 70 | Danh sách nguồn: đường dẫn `http(s)` hoặc nhãn văn bản (xem 7.5) |  |
| `pack` | 70 | Gói thị trường | VN 15, EA 14, LATAM_TR_CIS 14, WEST 14, SEA_SA 13 |
| `concept` | 70 | Khái niệm nhóm (ví dụ `clock_watch`); cảnh báo gắn theo khái niệm, không theo từng chữ | other 20, valentine_sensitive 8, chrysanthemum_white_flowers 6, shoes 5, even_flowers 5, knife_sharp 5, clock_watch 4, number_4_9_13 4, handkerchief_scarf 3, yellow_flowers 3, umbrella 2, cash_money 2, alcohol_pork 2, cups 1 |

### 7.3 Độ tin cậy và độ phủ

**Độ tin cậy theo loại bản ghi** (nhãn do agent thu thập tự gán và không có một định nghĩa chung, xem 7.5; riêng `low` gồm cả 22 bản ghi có từ hai đường dẫn trở lên, nên `low` không có nghĩa là "chưa có nguồn"):

| Loại | Tổng | high | med | low | Tỷ lệ low |
|---|---:|---:|---:|---:|---:|
| Dịp | 87 | 16 | 42 | 29 | 33% |
| Cột mốc | 55 | 1 | 30 | 24 | 44% |
| Quà | 142 | 14 | 64 | 64 | 45% |
| Kiêng kỵ | 70 | 3 | 34 | 33 | 47% |
| **Tất cả** | 354 | 34 | 170 | 150 | 42% |

Hệ quả cho cách dùng: phần `low` **không được tự động hiện cho người dùng** khi chưa có người bản địa duyệt (QĐ-9 ở mục 9). Riêng điều kiêng kỵ chỉ hiện từ `med` trở lên (5.2, thói quen 5).

**Độ tin cậy theo gói:**

| Gói | Tổng bản ghi | high | med | low | Tỷ lệ low |
|---|---:|---:|---:|---:|---:|
| VN | 73 | 5 | 30 | 38 | 52% |
| EA | 66 | 6 | 34 | 26 | 39% |
| WEST | 82 | 10 | 39 | 33 | 40% |
| SEA_SA | 67 | 5 | 35 | 27 | 40% |
| LATAM_TR_CIS | 66 | 8 | 32 | 26 | 39% |

**Độ phủ theo mã vùng** (số bản ghi có gắn mã đó; một bản ghi nhiều mã được đếm ở mỗi mã; xếp theo tổng giảm dần):

| Mã | Dịp | Dịp bật mặc định | Cột mốc | Quà | Kiêng kỵ | Tổng |
|---|---:|---:|---:|---:|---:|---:|
| VN | 15 | 8 | 11 | 32 | 15 | 73 |
| GB | 9 | 5 | 19 | 28 | 4 | 60 |
| US | 11 | 5 | 19 | 25 | 5 | 60 |
| AU | 8 | 5 | 19 | 21 | 3 | 51 |
| CA | 8 | 5 | 19 | 17 | 3 | 47 |
| FR | 6 | 5 | 2 | 18 | 8 | 34 |
| DE | 6 | 4 | 2 | 14 | 9 | 31 |
| CN | 7 | 4 | 4 | 8 | 9 | 28 |
| IT | 7 | 5 | 2 | 12 | 6 | 27 |
| ES | 8 | 4 | 2 | 10 | 5 | 25 |
| TW | 6 | 4 | 4 | 7 | 8 | 25 |
| IN | 8 | 1 | 2 | 9 | 3 | 22 |
| KR | 7 | 4 | 5 | 8 | 2 | 22 |
| MY | 5 | 0 | 1 | 7 | 7 | 20 |
| HK | 5 | 3 | 2 | 4 | 7 | 18 |
| JP | 4 | 3 | 3 | 7 | 2 | 16 |
| BR | 2 | 1 | 5 | 5 | 3 | 15 |
| ID | 3 | 0 | 2 | 6 | 3 | 14 |
| RU | 4 | 2 | 2 | 5 | 3 | 14 |
| SG | 3 | 1 | 1 | 4 | 4 | 12 |
| AR | 2 | 1 | 3 | 4 | 1 | 10 |
| CO | 1 | 1 | 3 | 4 | 2 | 10 |
| KZ | 3 | 1 | 2 | 3 | 2 | 10 |
| PH | 2 | 2 | 2 | 5 | 1 | 10 |
| TH | 2 | 1 | 2 | 4 | 1 | 9 |
| UA | 2 | 1 | 2 | 2 | 3 | 9 |
| MX | 1 | 1 | 3 | 3 | 1 | 8 |
| PE | 1 | 1 | 3 | 3 | 1 | 8 |
| TR | 2 | 1 | 1 | 4 | 1 | 8 |
| CL | 1 | 1 | 3 | 2 | 1 | 7 |
| PL | 1 | 1 | 1 | 3 | 1 | 6 |
| KW | 1 | 0 | 0 | 0 | 2 | 3 |
| SA | 1 | 0 | 0 | 0 | 2 | 3 |
| AE | 0 | 0 | 0 | 0 | 1 | 1 |
| EG | 0 | 0 | 0 | 0 | 1 | 1 |

Điểm cần biết khi đọc bảng:

- **Việt Nam sâu nhất**: 15 dịp, 11 cột mốc (gồm cả mốc năm cưới), 32 quà, 15 điều kiêng. Phần lớn các thị trường còn lại chỉ có lõi (Valentine, Giáng sinh, vài dịp bản địa).
- **Mã có dịp và kiêng kỵ nhưng không có quà**: KW, SA (mỗi mã 1 dịp `mena_valentine_restricted` và 2 kiêng kỵ). **Mã chỉ có một điều kiêng, không có dịp, cột mốc hay quà**: AE, EG (cùng bản ghi `mena_unmarried_couples`).
- **Mã có từ 1 đến 3 món quà** (mỏng, quà đủ để thử nghiệm chứ chưa đủ để chạy một thị trường): KZ (3), UA (2), MX (3), PE (3), CL (2), PL (3).
- Số quà của một mã không quyết định chất lượng gợi ý: gói `WEST` và `LATAM_TR_CIS` gộp nhiều quốc gia dưới một gói, nên quà gắn nhiều mã được đếm nhiều lần.

**Hai danh sách cần chủ app duyệt (luật ở 5.3).** Dịp có `coupleRelevance ≥ 2` nhưng đang **tắt** mặc định (16): `th_loy_krathong`, `id_valentines`, `cn_chap_goh_mei`, `cn_qixi_sea`, `in_valentines_week`, `in_rose_day`, `in_chocolate_day`, `in_teddy_day`, `in_promise_day`, `in_karwa_chauth`, `in_diwali`, `vn_520`, `vn_new_year_eve`, `vn_di_choi_xuan`, `vn_meet_parents`, `es_sant_jordi`.

Dịp có nhãn nhạy cảm nhưng đang **bật** mặc định (5): `kr_christmas`, `cn_christmas_eve_apple`, `in_valentines_day`, `vn_noel_eve`, `west_christmas`.

Kiểm tra luật: số dịp bật mặc định mà `coupleRelevance` dưới 2 là 0; số dịp bật là 43, tắt là 44.

**Khoảng trống về giá** (cố ý, không phải lỗi nhập): ở Tây và Thổ Nhĩ Kỳ nguồn không cho khoảng giá sát từng món, nên `priceBand.min` để `null`; chỉ có `priceTier`.

| Gói | Quà | Có `min` | `min` null | Không `priceBand` | Tiền tệ | Dịp | Dịp không `budget` | Dịp `budget.mid` null |
|---|---:|---:|---:|---:|---|---:|---:|---:|
| VN | 32 | 32 | 0 | 0 | VND | 15 | 0 | 0 |
| EA | 24 | 24 | 0 | 0 | CNY, HKD, JPY, KRW, TWD | 18 | 0 | 0 |
| WEST | 30 | 0 | 30 | 0 | EUR, GBP, USD | 19 | 0 | 12 |
| SEA_SA | 28 | 28 | 0 | 0 | IDR, INR, MYR, PHP, SGD, THB | 19 | 0 | 0 |
| LATAM_TR_CIS | 28 | 24 | 4 | 0 | ARS, BRL, COP, MXN, PEN, PLN, RUB, TRY | 16 | 8 | 0 |

### 7.4 Từ vựng và phân bố giá trị

**Nhóm quà (`category`): 10 nhóm trong dữ liệu, 5 nhóm trong app.**

| Nhóm trong dữ liệu | Số quà | Trùng nhóm của app |
|---|---:|---|
| Quà lãng mạn | 39 | Có |
| Kỷ vật Handmade | 31 | Có |
| Trải nghiệm & Thưởng thức | 21 | Không |
| Địa điểm hẹn hò | 19 | Có |
| Công nghệ & Cảm xúc | 13 | Không |
| Trang sức & Nước hoa | 9 | Có |
| Quà ra mắt & Biếu gia đình | 4 | Không |
| Làm đẹp & Mỹ phẩm | 2 | Không |
| Sưu tầm & Trend | 2 | Không |
| Sức khỏe & Chăm sóc | 2 | Không |

Trùng 4 trên 5 nhóm của app. Nhóm của app **không có** trong dữ liệu: "Bất ngờ bí mật". Cách ánh xạ sang chip của app và lý do chỉ hiện chip khi thị trường có từ 8 quà trở lên: 6.3.6.

**Sở thích (`interests`): 38 khoá, tổng 229 lượt gắn.** App hiện chỉ có 10 khoá sở thích để người dùng chọn (`data/repository/OnlineCoupleRepository.kt:70-81`); cách ánh xạ 38 khoá về 23 khoá chuẩn nằm ở 6.3.6. Khoá và số quà:

`fashion` 26, `food` 24, `travel` 22, `photography` 12, `flowers` 12, `handmade` 11, `cooking` 10, `wellness` 10, `beauty` 9, `technology` 8, `home_decor` 8, `coffee` 6, `dining` 6, `music` 5, `sweets` 5, `art` 5, `books` 4, `wine` 4, `crafts` 4, `family` 4, `cinema` 3, `journaling` 3, `jewellery` 3, `home` 3, `jewelry` 3, `writing` 3, `fitness` 2, `gaming` 2, `collectibles` 2, `pets` 2, `cycling` 1, `fragrance` 1, `culture` 1, `faith` 1, `toys` 1, `theatre` 1, `adventure` 1, `tea` 1.

Các cặp khoá trùng nghĩa còn trong dữ liệu (phải gộp khi nạp vào gói, nếu không người dùng chọn một khoá sẽ chỉ khớp một nửa số quà):

| Khoá A | Số quà A | Khoá B | Số quà B |
|---|---:|---|---:|
| jewellery | 3 | jewelry | 3 |
| home | 3 | home_decor | 8 |
| crafts | 4 | handmade | 11 |

**Người nhận, hình thức, ngôn ngữ tình yêu:**

| Trường | Phân bố (142 quà) |
|---|---|
| `recipient` | any 109, her 32, him 1 |
| `effort` | buy_ready 59, experience 36, personalized_order 26, diy 13, digital 8 |
| `loveLanguages` (mỗi quà một hoặc nhiều nhãn) | gifts 104, quality_time 49, words 31, acts 8, touch 3 |
| `priceTier` | 1 37, 2 58, 3 35, 4 12 |

Chỉ có 1 quà dành cho "anh ấy" (`jp_g_001`), trong khi 4 dịp có chiều "nữ tặng nam" (5.5).

**Thời gian chuẩn bị (`leadTimeDays`).** Quà: 0 ngày: 7, 1 ngày: 3, 2 ngày: 11, 3 ngày: 31, 4 ngày: 4, 5 ngày: 16, 7 ngày: 26, 10 ngày: 13, 14 ngày: 15, 21 ngày: 9, 30 ngày: 6, 45 ngày: 1. Có 70 trên 142 quà cần từ 7 ngày trở lên, 31 quà cần từ 14 ngày trở lên. Dịp: 0 ngày: 6, 1 ngày: 1, 2 ngày: 1, 3 ngày: 17, 4 ngày: 1, 5 ngày: 11, 7 ngày: 22, 10 ngày: 5, 14 ngày: 14, 21 ngày: 6, 28 ngày: 1, 30 ngày: 2; 23 dịp cần từ 14 ngày trở lên, 33 dịp `bookingAdvised`.

So với app: mọi lễ gieo sẵn đều nhắc trước 3 ngày, và các lựa chọn nhắc chỉ có 0, 1, 3, 7 ngày (F04, F10 ở 3.2). Tức là với 31 quà cần chuẩn bị từ 14 ngày trở lên, lời nhắc 3 ngày đến quá muộn.

**Nơi mua (`whereToBuy`).** Danh sách chữ thuần, 216 chuỗi khác nhau trên 347 lượt; chưa chuẩn hoá (ví dụ "siêu thị" và "supermarket", "florist" và "local florist", "Shopee" và "Shopee Mall" là các chuỗi khác nhau). Nên giữ chữ thuần khi hiển thị và **không** gắn affiliate (QĐ-8). Các chuỗi hay gặp nhất:

Shopee 23, TikTok Shop 14, Amazon.in 7, siêu thị 6, Etsy 6, florist 5, supermarket 5, restaurant direct 5, FNP 5, Tokopedia 4, Shopee Mall 4, KakaoTalk Gift 3, Takashimaya 3, Amazon.jp 3, restaurant booking 3, Trendyol 3, DIY 3, Wildberries 3, Ozon 3, Allegro 3, Lazada 3, department store 3, local florist 3, Klook 3, Flipkart 3.

**Token chung trong `occasions` của quà** (ngoài id dịp): `anniversary` 51 quà, `birthday` 41 quà, `any` 19 quà; 0 quà không gắn dịp nào ngoài `any`. Trong gói nội dung các token này thành `@anniversary`, `@birthday`, `@any` (6.3.6).

**Quy tắc ngày (`dateRule.type`)**: `fixed_solar` 61, `lunar` 7, `user_defined` 7, `table` 5, `relative` 3, `nth_weekday` 2, `week` 2. Danh sách id của các loại không cố định và các quy tắc cần quy trình cập nhật hằng năm: 6.3.7.

**Nhóm khái niệm của điều kiêng (`concept`)**: `valentine_sensitive` 8, `chrysanthemum_white_flowers` 6, `shoes` 5, `even_flowers` 5, `knife_sharp` 5, `clock_watch` 4, `number_4_9_13` 4, `handkerchief_scarf` 3, `yellow_flowers` 3, `umbrella` 2, `cash_money` 2, `alcohol_pork` 2; `other` 20; 1 khái niệm chỉ có một mục.

Mức độ: soft 55, strong 15. Loại: gift 33, behavior 14, flower 13, number 6, color 4. Điều kiêng `strong` (15): `cn_clock`, `cn_white_flowers`, `cn_green_hat`, `ru_even_flowers`, `ua_even_flowers`, `br_not_feb14`, `mena_sa_valentine`, `id_valentine_local_bans`, `my_valentine_muslim`, `idmy_alcohol`, `in_public_displays_valentine`, `vn_white_chrysanthemum`, `fr_chrysanthemum`, `it_chrysanthemum`, `west_fur_ivory_items`.

**Quà gắn điều kiêng (`tabooRefs`)**: chỉ 4 quà (`cn_g_001` → `cn_red_packet_breakup`; `ru_g_003` → `ru_watch_knife`; `vn_g_024` → `vn_cash`; `vn_g_030` → `vn_cash`). Với hơn 2 trên 3 điều kiêng là `med` hoặc `low`, phần nối quà với điều kiêng là việc của U-20 và U-30 (6.2, 6.4), không phải của riêng dữ liệu.

### 7.5 Kiểm tra chất lượng dữ liệu

**12 sửa lỗi của vòng QA** (nguyên văn từ `meta.qaFixes`, theo thứ tự đã làm):

1. west_valentines_de_es: defaultOn true -> false (coupleRelevance=1, không đáng bật mặc định)
2. idmy_ramadan_2027: defaultOn true -> false (coupleRelevance=1, không đáng bật mặc định)
3. west_valentines_au_nz: đổi tên thành '(Úc)', giữ regions=[AU], thêm nzNote (không có nguồn cho NZ)
4. vn_g_030: tabooRefs=[vn_cash] (cảnh báo mềm, không chặn; quà vẫn giữ trong catalogue)
5. vn_g_024: tabooRefs=[vn_cash] (cảnh báo mềm, không chặn; quà vẫn giữ trong catalogue)
6. cn_g_001: tabooRefs=[cn_red_packet_breakup] (cảnh báo mềm, không chặn; quà vẫn giữ trong catalogue)
7. ru_g_003: tabooRefs=[ru_watch_knife] (cảnh báo mềm, không chặn; quà vẫn giữ trong catalogue)
8. co_not_couple_only/co_amor_y_amistad: bỏ câu 'chỉ 35%' và 'coworkers là nhóm chính' (sai nghĩa); người yêu là nhóm nhận lớn nhất nhưng dưới một nửa (35%, qua RTVC)
9. br_dia_dos_namorados: budget.mid 250 -> 264 (đúng mức trung bình CNDL/SPC 2026; bản cũ ghi R$238)
10. cis_valentine_14feb: thêm ghi chú lệch số VCIOM (18% qua dp.ru vs 34% trang VCIOM 2025) và Avito; mid 4.500 RUB chưa hòa giải với 8.000 RUB của Avito
11. gói VN: 26 mục customs (15 dịp + 11 cột mốc) đã viết lại có dấu tiếng Việt (bản gốc của agent viết không dấu); kiểm bằng script: bỏ dấu bản mới == bản cũ, không đổi chữ nào, trừ 2 chỗ sửa lỗi gõ "phoi truong" thành "phô trương"
12. vn_520: tên thiếu dấu 'Ngay 520 (20/5)' sửa thành 'Ngày 520 (20/5)' (names.vi và names.local); quét lại mọi trường tiếng Việt bằng script, không còn trường thiếu dấu nào khác

**Kiểm tra lại trên file cuối cùng** (script viết cho mục này, không phải bộ kiểm cũ):

- Cấu trúc: id không trùng trong từng loại; `confidence` thuộc `high`/`med`/`low`; `sources` là danh sách không rỗng gồm chuỗi (đường dẫn `http(s)` hoặc nhãn văn bản), và mọi bản ghi `high` hoặc `med` có ít nhất một đường dẫn; `pack` và `regions` hợp lệ; `dateRule.type` thuộc 7 loại; `coupleRelevance` 0-3; `giftExpectation`, `sensitivity`, `unit`, `severity`, `priceTier`, `culturalWeight` thuộc tập cho phép; mọi `tabooRefs` và mọi id dịp trong `occasions` của quà trỏ về bản ghi có thật. Kết quả: **0 lỗi**.
- Chữ Việt có dấu: quét `names.vi`, `title.vi`, `item.vi`, `description.vi`, `customs`, `reason`, `trendNote` bằng cùng phép thử đã dùng ở vòng QA (đoạn dài từ 25 ký tự, không có chữ có dấu, từ 3 từ phổ biến không dấu trở lên): **0** trường nghi vấn trên 354 bản ghi. Quét riêng tên ngắn có từ không dấu phổ biến (`Ngay`, `Tang`, `Qua`...): **0** trường.
- Phép thử này là heuristic: nó bắt được đoạn dài viết không dấu (đã bắt 20 bản ghi ở vòng đầu, đều là phần phong tục tiếng Việt của gói VN, nay đã viết lại có dấu), không bắt được lỗi dấu một chữ.
- Trùng với 20 quà gốc của app: so khớp tên (chưa rà từng mô tả) giữa 142 quà nháp và 20 seed cho thấy ít nhất 3 quà nháp gần trùng một seed: `ru_g_004` ("Chuyến đi cuối tuần cho hai người") và `cn_g_003` ("Chuyến đi ngắn hai người") với `gift-weekend-trip` ("Short Weekend Trip for Two"), `pl_g_002` ("Trò chơi bàn cho cặp đôi") với `gift-board-game` ("Bộ Board Game Cho Cặp Đôi"). Thêm 5 quà cùng nhóm ý tưởng với một seed nhưng khác chi tiết: `us_g_012` (tranh chân dung đôi có thú cưng, so với `gift-couple-portrait`), `jp_g_004` (trang sức đôi tối giản, so với `gift-couple-bracelet`), `pl_g_003` (nước hoa kiểu Ả Rập, so với `gift-perfume-set`), `vn_g_015` (chuyến đi chơi xuân, so với `gift-weekend-trip`), `ru_g_003` (đồng hồ thể thao thông minh, so với `gift-couple-watch`). Bản nháp chưa gộp các cặp này; quy tắc xử lý ở 6.3.7.

**Nguồn.** Mỗi phần tử của `sources` là một chuỗi, và có hai loại. Loại thứ nhất là đường dẫn `http(s)`: 455 đường dẫn, 196 đường dẫn khác nhau, 176 tên miền khác nhau; tên miền hay gặp nhất: leahingram.com 22, yougov.com 16, asianews.network 10, vneconomy.vn 10, baonghean.vn 9, shopee.vn 9, voh.com.vn 9, vietnamnet.vn 9, worldjournal.com 8, thequint.com 8, mytour.vn 8, tienphong.vn 8. Loại thứ hai là nhãn văn bản mà agent thu thập ghi khi không có trang nào để dẫn: 77 nhãn "from memory, unverified" (kiến thức ngoài, chưa kiểm chứng; 10 biến thể, thường kèm một cụm trong ngoặc nói phần nào chưa kiểm chứng, ví dụ `(budget)`, `(Noche Buena)`, `(couple-date custom)`) và 1 nhãn `computed` (một ngày âm lịch tính bằng thuật toán, ở dịp `vn_that_tich`). Nhãn văn bản không phải lỗi dữ liệu, nhưng nó không cho người đọc một trang để kiểm lại.

Số bản ghi theo cách dẫn nguồn:

| Loại | Bản ghi | Có ít nhất 1 đường dẫn | Không có đường dẫn (chỉ nhãn) | Có cả đường dẫn lẫn nhãn | Đúng 1 đường dẫn |
|---|---:|---:|---:|---:|---:|
| Dịp | 87 | 72 | 15 | 5 | 19 |
| Cột mốc | 55 | 47 | 8 | 3 | 30 |
| Quà | 142 | 119 | 23 | 4 | 83 |
| Kiêng kỵ | 70 | 50 | 20 | 0 | 32 |
| **Tổng** | 354 | 288 | 66 | 12 | 164 |

Độ tin cậy theo số đường dẫn của bản ghi (cả bốn loại, 354 bản ghi):

| `confidence` | 2 đường dẫn trở lên | Đúng 1 đường dẫn | Không có đường dẫn | Tổng |
|---|---:|---:|---:|---:|
| `high` | 22 | 12 | 0 | 34 |
| `med` | 80 | 90 | 0 | 170 |
| `low` | 22 | 62 | 66 | 150 |
| **Tổng** | 124 | 164 | 66 | 354 |

Kiểm nhất quán: không bản ghi `high` hay `med` nào thiếu đường dẫn, và cả 66 bản ghi không có đường dẫn đều là `low`.

**Mức `high` mà chỉ có một đường dẫn**: 12 trên 34 bản ghi `high` (dịp `kr_black_day`, `jp_valentine`, `in_rose_day`, `in_teddy_day`, `in_promise_day`, `west_valentines_fr`; quà `kr_g_001`, `cn_g_001`, `vn_g_003`, `us_g_029` (id `us_g_*` là id của bản nháp; gói thật dùng tiền tố `west_g_`, xem 6.3.7); kiêng kỵ `br_not_feb14`, `co_not_couple_only`). `in_rose_day`, `in_teddy_day`, `in_promise_day` dùng chung một đường dẫn (thequint.com); `west_valentines_fr`, `us_g_029` dùng chung một đường dẫn (yougov.com). Tên miền của các nguồn đó (tính theo tên miền, chưa mở lại từng trang): thequint.com 3, yougov.com 2, asianews.network 1, english.adnkronos.com 1, view.asiae.co.kr 1, m.gmw.cn 1, tienphong.vn 1, gazetasp.com.br 1, fenalco.com.co 1. Đó là các trang báo, một hãng khảo sát và một hiệp hội thương mại; không có cơ quan thống kê nào. Nên cho người bản địa xem các bản ghi này trước (QĐ-9, mục 9).

**Điều chưa được kiểm.** Không có script nào mở từng đường dẫn để xem còn sống không, cũng không có script nào so nội dung bản ghi với nội dung nguồn. Việc đối chiếu nội dung với nguồn do các agent làm lúc thu thập, và một phần được kiểm tay (ba chỗ sai tìm được ở sửa lỗi 8, 9, 10).

**`confidence` không có một định nghĩa chung.** Năm báo cáo thị trường dùng nhãn `high`/`med`/`low` (báo cáo Đông Á viết `[H]`/`[M]`/`[L]`) nhưng chỉ một báo cáo định nghĩa cả ba mức: báo cáo Mỹ Latinh, Thổ Nhĩ Kỳ và Nga viết "cao = có nguồn đã mở và số liệu khớp; vừa = có nguồn nhưng thiếu phương pháp hoặc chỉ thấy snippet; thấp = ký ức/không kiểm chứng". Báo cáo Đông Nam Á và Nam Á chỉ định nghĩa hai nhãn phụ ("est" = ước lượng của tác giả, không có nguồn; "unverified" = kiến thức ngoài, chưa kiểm chứng) và cho biết phần lớn trang chỉ thấy qua tóm tắt của công cụ tìm kiếm nên rất ít mục được xếp `high`. Báo cáo Đông Á và báo cáo Phương Tây chỉ định nghĩa nhãn "unverified" (chưa mở được nguồn nào). Phần đầu báo cáo Việt Nam chỉ liệt kê tên nhãn, và ở một dòng ghi "Tin cậy: high là bài nói vậy, định tính". Không báo cáo nào đặt quy tắc "ít nhất hai nguồn". Vì vậy `high` **không** có nghĩa là "hai nguồn khớp" hay "nguồn chính thức", và càng không phải "đã được người bản địa duyệt".

### 7.6 Âm lịch 2025-2040

**Kết luận ngắn.**

1. **Thuật toán âm lịch của Hồ Ngọc Đức (bản cài đặt tham chiếu bằng Python ở `lunar_reference.py`) đã qua kiểm chứng** ở mức đáng tin để dùng: tính với UTC+8 trùng 96 trên 96 ngày của Đài Thiên văn Hồng Kông (6 dịp x 16 năm, 2025-2040), và 112 trên 112 ngày của 7 dịp Việt Nam (cùng ngày âm lịch, vẫn tính ở UTC+8) so với cùng bảng Hồng Kông; ở hai trang Việt Nam (so với kết quả UTC+7) thì trùng 30 trên 30 ngày ở ngaydep.com (2026-2031) và 21 trên 21 ngày ở baolamdong.vn. Câu chú thích hiện tại trong `data/seed/VietnameseHolidays.kt:15-17` ("thuật toán viết tay không kiểm chứng được") không còn đúng; xem giới hạn bên dưới.
2. **Bảng trong repo có đúng một lỗi** trên 15 ngày so được: Rằm tháng Giêng 2027 ghi 2027-02-19 (`data/seed/VietnameseHolidays.kt:86`), đúng là **2027-02-20** (Tết 06/02, cộng 14 ngày; Hồng Kông, ngaydep.com và baolamdong.vn cùng ra 20/02). `assets/content/holiday_calendar.json:163` chép lại cùng ngày sai. Hàng đã gieo trên máy người dùng tự sửa nhờ `HolidayDates.refreshExisting` (U-01 ở 6.2 ghi đủ các golden test bị ảnh hưởng).
3. **Việt Nam và Trung Quốc lệch nhau đúng 5 ngày trong 2025-2040**, nguyên nhân duy nhất là múi giờ (UTC+7 so với UTC+8): điểm sóc rơi vào khoảng 16:00-17:00 UTC, tức sát nửa đêm. Hai bảng dưới đây không có cùng các cột (Giỗ Tổ, Đoan Ngọ, Vu Lan và 23 Chạp chỉ có trong bảng Việt Nam; Trùng Cửu chỉ có trong bảng Trung Quốc), nên script còn tính lại từng dịp với cả hai múi giờ: Đoan Ngọ, Thất Tịch, Vu Lan, Trùng Cửu, 23 Chạp không lệch năm nào trong 16 năm, nên năm ngày nói trên là toàn bộ chênh lệch. Tháng nhuận cũng trùng nhau.
4. **Bảng cho 16 năm có sẵn, thuật toán không hết hạn.** Bảng trong repo hết hạn rất sớm (Tết chỉ tới 2028, Vu Lan chỉ tới 2026, Rằm Giêng chỉ tới 2027). Khuyến nghị: ngắn hạn dùng bảng này kèm sửa Rằm 2027; dài hạn dùng thuật toán trong module chung Kotlin, giữ bảng làm golden test.

Độ tin cậy từng ô: bảng Việt Nam 128 ô, trong đó 44 ô có từ hai nguồn trở lên (dấu `*`), 84 ô một nguồn; bảng Trung Quốc 80 ô, 24 ô có từ hai nguồn trở lên, 56 ô một nguồn. **Không có ô nào chỉ dựa vào thuật toán.** Một nguồn nghĩa là chỉ một nguồn ngoài, kèm thuật toán.

**Bảng Việt Nam (UTC+7).** Ngày/tháng dương lịch của năm ở cột đầu. `*` = có từ hai nguồn trở lên; **đậm** = khác bảng Trung Quốc. Cột "23 Chạp" là 23/12 âm lịch của năm âm trước, ghi theo ngày dương lịch của năm ở cột đầu.

| Năm | Tết 1/1 | Rằm Giêng 15/1 | Giỗ Tổ 10/3 | Đoan Ngọ 5/5 | Thất Tịch 7/7 | Vu Lan 15/7 | Trung Thu 15/8 | 23 Chạp (ông Táo) |
|---|---|---|---|---|---|---|---|---|
| 2025 | 29/01\* | 12/02 | 07/04\* | 31/05 | 29/08\* | 06/09\* | 06/10\* | 22/01 |
| 2026 | 17/02\* | 03/03\* | 26/04\* | 19/06\* | 19/08 | 27/08\* | 25/09\* | 10/02 |
| 2027 | 06/02\* | 20/02\* | 16/04\* | 09/06\* | 08/08 | 16/08 | 15/09\* | 30/01 |
| 2028 | 26/01\* | 09/02\* | 04/04\* | 28/05\* | 26/08 | 03/09 | 03/10\* | 19/01 |
| 2029 | 13/02\* | 27/02\* | 23/04\* | 16/06\* | 16/08 | 24/08 | 22/09\* | 06/02 |
| 2030 | **02/02\*** | **16/02\*** | 12/04\* | 05/06\* | 05/08 | 13/08 | 12/09\* | 26/01 |
| 2031 | 23/01\* | 06/02\* | 01/04\* | 24/06\* | 24/08 | 01/09\* | 01/10\* | 16/01 |
| 2032 | 11/02\* | 25/02 | 19/04 | 12/06 | 12/08 | 20/08 | 19/09 | 04/02 |
| 2033 | 31/01\* | 14/02 | 09/04 | 01/06 | 01/08 | 09/08 | 08/09 | 23/01 |
| 2034 | 19/02\* | 05/03 | 28/04 | 20/06 | 20/08 | 28/08 | **26/09** | 11/02 |
| 2035 | 08/02\* | 22/02 | 17/04 | 10/06 | 10/08 | 18/08 | 16/09 | 31/01 |
| 2036 | 28/01\* | 11/02 | 06/04 | 30/05 | 28/08 | 05/09 | 04/10 | 20/01 |
| 2037 | 15/02\* | 01/03 | **24/04** | 18/06 | 17/08 | 25/08 | 24/09 | 07/02 |
| 2038 | 04/02 | 18/02 | **13/04** | 07/06 | 07/08 | 15/08 | 13/09 | 27/01 |
| 2039 | 24/01 | 07/02 | 03/04 | 27/05 | 26/08 | 03/09 | 02/10 | 17/01 |
| 2040 | 12/02 | 26/02 | 20/04 | 14/06 | 14/08 | 22/08 | 20/09\* | 05/02 |

**Bảng Trung Quốc (UTC+8).** Ký hiệu như trên (đậm = khác bảng Việt Nam).

| Năm | Tết Âm lịch 1/1 | Nguyên Tiêu 15/1 | Thất Tịch 7/7 | Trung Thu 15/8 | Trùng Cửu 9/9 |
|---|---|---|---|---|---|
| 2025 | 29/01\* | 12/02 | 29/08 | 06/10 | 29/10 |
| 2026 | 17/02\* | 03/03\* | 19/08 | 25/09\* | 18/10 |
| 2027 | 06/02\* | 20/02\* | 08/08 | 15/09\* | 08/10 |
| 2028 | 26/01\* | 09/02\* | 26/08 | 03/10\* | 26/10 |
| 2029 | 13/02\* | 27/02\* | 16/08 | 22/09\* | 16/10 |
| 2030 | **03/02\*** | **17/02** | 05/08 | 12/09\* | 05/10 |
| 2031 | 23/01\* | 06/02\* | 24/08 | 01/10\* | 24/10 |
| 2032 | 11/02\* | 25/02 | 12/08 | 19/09 | 12/10 |
| 2033 | 31/01\* | 14/02 | 01/08 | 08/09 | 01/10 |
| 2034 | 19/02\* | 05/03 | 20/08 | 27/09 | 20/10 |
| 2035 | 08/02\* | 22/02 | 10/08 | 16/09 | 09/10 |
| 2036 | 28/01\* | 11/02 | 28/08 | 04/10 | 27/10 |
| 2037 | 15/02\* | 01/03 | 17/08 | 24/09 | 17/10 |
| 2038 | 04/02 | 18/02 | 07/08 | 13/09 | 07/10 |
| 2039 | 24/01 | 07/02 | 26/08 | 02/10 | 26/10 |
| 2040 | 12/02 | 26/02 | 14/08 | 20/09 | 14/10 |

**Năm ngày lệch giữa Việt Nam và Trung Quốc, và vì sao.** Cột "Điểm sóc (UTC)" tính bằng chính thuật toán (xem giới hạn về sai số bên dưới).

| Năm | Dịp | Việt Nam | Trung Quốc | Điểm sóc mở đầu tháng (UTC) |
|---|---|---|---|---|
| 2030 | Rằm Giêng 15/1 | 16/02 | 17/02 | 2030-02-02 16:09 |
| 2030 | Tết 1/1 | 02/02 | 03/02 | 2030-02-02 16:09 |
| 2034 | Trung Thu 15/8 | 26/09 | 27/09 | 2034-09-12 16:14 |
| 2037 | Giỗ Tổ 10/3 | 24/04 | 25/04 | 2037-04-15 16:10 |
| 2038 | Giỗ Tổ 10/3 | 13/04 | 14/04 | 2038-04-04 16:45 |

Cả năm ngày đều có điểm sóc lúc 16:09-16:45 UTC, tức 23:09-23:45 giờ Việt Nam và 00:09-00:45 giờ Trung Quốc: cùng một khoảnh khắc thiên văn, hai ngày dương lịch khác nhau. Đây là chênh lệch theo định nghĩa múi giờ, không phải lỗi của một bên.

**Ngày nhạy cảm: điểm sóc sát nửa đêm.** Nếu điểm sóc cách nửa đêm địa phương chỉ vài phút, một sai số nhỏ của thuật toán (cỡ vài phút) có thể đổi cả ngày. Script duyệt mọi điểm sóc từ 2024 đến 2041 và liệt kê những điểm cách nửa đêm địa phương dưới 30 phút:

| Múi giờ | Ngày mồng 1 tính được | Điểm sóc (UTC) | Cách nửa đêm địa phương | Tháng âm bắt đầu | Dịp theo dõi trong tháng |
|---|---|---|---|---|---|
| UTC+7 | 15/01/2029 | 2029-01-14 17:25 | 25 phút sau nửa đêm | tháng 12 | 23 Chạp |
| UTC+7 | 21/04/2031 | 2031-04-21 16:59 | 1 phút trước nửa đêm | tháng nhuận 3 | (không có) |
| UTC+7 | 04/08/2035 | 2035-08-03 17:12 | 12 phút sau nửa đêm | tháng 7 | Thất Tịch, Vu Lan |
| UTC+7 | 14/06/2037 | 2037-06-13 17:11 | 11 phút sau nửa đêm | tháng 5 | Đoan Ngọ |
| UTC+7 | 04/04/2038 | 2038-04-04 16:45 | 15 phút trước nửa đêm | tháng 3 | Giỗ Tổ |
| UTC+7 | 22/06/2039 | 2039-06-21 17:22 | 22 phút sau nửa đêm | tháng nhuận 5 | (không có) |
| UTC+7 | 15/12/2039 | 2039-12-15 16:32 | 28 phút trước nửa đêm | tháng 11 | (không có) |
| UTC+8 | 10/10/2026 | 2026-10-10 15:50 | 10 phút trước nửa đêm | tháng 9 | Trùng Cửu |
| UTC+8 | 06/02/2027 | 2027-02-06 15:57 | 3 phút trước nửa đêm | tháng 1 | Tết, Nguyên Tiêu |
| UTC+8 | 11/07/2029 | 2029-07-11 15:51 | 9 phút trước nửa đêm | tháng 6 | (không có) |
| UTC+8 | 03/02/2030 | 2030-02-02 16:09 | 9 phút sau nửa đêm | tháng 1 | Tết, Nguyên Tiêu |
| UTC+8 | 21/02/2031 | 2031-02-21 15:51 | 9 phút trước nửa đêm | tháng 2 | (không có) |
| UTC+8 | 12/03/2032 | 2032-03-11 16:26 | 26 phút sau nửa đêm | tháng 2 | (không có) |
| UTC+8 | 13/09/2034 | 2034-09-12 16:14 | 14 phút sau nửa đêm | tháng 8 | Trung Thu |
| UTC+8 | 17/12/2036 | 2036-12-17 15:35 | 25 phút trước nửa đêm | tháng 11 | (không có) |
| UTC+8 | 16/04/2037 | 2037-04-15 16:10 | 10 phút sau nửa đêm | tháng 3 | (không có) |

Cách đọc: trong 2025-2040 có 7 tháng âm có mồng 1 sát nửa đêm ở UTC+7 và 9 ở UTC+8 (chỉ một số tháng chứa dịp đang theo dõi, xem cột cuối). Sát nửa đêm nhất trong bảng: 21/04/2031 (1 phút trước nửa đêm; không có dịp theo dõi). Với Trung Quốc (UTC+8), các dịp theo dõi sát nhất: 06/02/2027 (3 phút trước nửa đêm; Tết, Nguyên Tiêu); 03/02/2030 (9 phút sau nửa đêm; Tết, Nguyên Tiêu); 10/10/2026 (10 phút trước nửa đêm; Trùng Cửu); Hồng Kông là nguồn độc lập và khớp thuật toán ở cả 96 ô (6 dịp x 16 năm), trong đó có mọi ô Trung Quốc thuộc các tháng có dịp theo dõi ở bảng trên. Với Việt Nam (UTC+7), các dịp theo dõi sát nhất: 14/06/2037 (11 phút sau nửa đêm; Đoan Ngọ); 04/08/2035 (12 phút sau nửa đêm; Thất Tịch, Vu Lan); 04/04/2038 (15 phút trước nửa đêm; Giỗ Tổ). Việt Nam không có nguồn độc lập tương đương (hai trang Việt Nam nhiều khả năng chạy cùng thuật toán), nên khoảng cách 11-15 phút ở đây là biên của thuật toán, chưa phải biên đã được nguồn khác kiểm. Hai giới hạn chưa kiểm: (1) sai số tuyệt đối của chuỗi Meeus rút gọn trong thuật toán chưa được đo riêng, chỉ có kết quả khớp 96/96 và 112/112 làm bằng chứng gián tiếp; (2) việc đặt tháng nhuận phụ thuộc vào thời điểm "trung khí" (kinh độ Mặt trời), và thời điểm này sát nửa đêm cũng có thể đổi tháng; script này **không** duyệt trung khí.

**Hàn Quốc (UTC+9) khác Việt Nam ở 10 ngày** (chỉ do thuật toán tính, **không mở nguồn Hàn nào**, nên chỉ đưa ra để cảnh báo, không đưa vào dữ liệu):

| Năm | Dịp | Việt Nam | Hàn Quốc (tính) |
|---|---|---|---|
| 2027 | Rằm Giêng 15/1 | 20/02 | 21/02 |
| 2027 | Tết 1/1 | 06/02 | 07/02 |
| 2028 | Rằm Giêng 15/1 | 09/02 | 10/02 |
| 2028 | Tết 1/1 | 26/01 | 27/01 |
| 2030 | Rằm Giêng 15/1 | 16/02 | 17/02 |
| 2030 | Tết 1/1 | 02/02 | 03/02 |
| 2034 | Trung Thu 15/8 | 26/09 | 27/09 |
| 2037 | Giỗ Tổ 10/3 | 24/04 | 25/04 |
| 2038 | Giỗ Tổ 10/3 | 13/04 | 14/04 |
| 2040 | Trung Thu 15/8 | 20/09 | 21/09 |

Hệ quả: nếu mở thị trường Hàn, Chuseok và Seollal không được lấy từ bảng Việt Nam; phải có nguồn Hàn riêng (QĐ-1 ở mục 9). Nhật và Đài Loan chưa kiểm.

**Tháng nhuận 2025-2040** (năm dương lịch của tháng nhuận → tháng âm nhuận; Việt Nam và Trung Quốc giống nhau): 2025 → tháng 6, 2028 → tháng 5, 2031 → tháng 3, 2034 → tháng 11, 2036 → tháng 6, 2039 → tháng 5.

**15 vector kiểm thử** cho cài đặt Kotlin (cũng nằm trong `lunar_reference.py` dưới tên `VECTORS`; script này đã chạy cả 15 và cả chiều ngược lại `lunar_to_solar`, đều khớp lúc sinh tài liệu). Cột "Nguồn đối chiếu" ghi nơi ngày được xác nhận độc lập:

| Dương lịch | Múi giờ | Âm lịch (ngày/tháng/năm) | Nguồn đối chiếu |
|---|---|---|---|
| 29/01/2025 | UTC+7 | 1/1/2025 | Hồng Kông (Tết 2025) |
| 17/02/2026 | UTC+7 | 1/1/2026 | Hồng Kông, ngaydep.com |
| 06/02/2027 | UTC+7 | 1/1/2027 | Hồng Kông; điểm sóc sát nửa đêm UTC+8 |
| 26/01/2028 | UTC+7 | 1/1/2028 | Hồng Kông, ngaydep.com |
| 02/02/2030 | UTC+7 | 1/1/2030 | baolamdong.vn, ngaydep.com, xemlicham.com (Tết Việt Nam 2030) |
| 03/02/2030 | UTC+8 | 1/1/2030 | Hồng Kông (Tết Trung Quốc 2030) |
| 02/02/2030 | UTC+8 | 30/12/2029 | Cùng ngày: Trung Quốc còn là 30 tháng Chạp |
| 25/07/2025 | UTC+7 | 1/6/2025 (nhuận) | Hồng Kông (tháng 6 nhuận 2025) |
| 06/10/2025 | UTC+7 | 15/8/2025 | Trung Thu 2025, nhiều nguồn |
| 26/09/2034 | UTC+7 | 15/8/2034 | baolamdong.vn (Trung Thu Việt Nam 2034) |
| 26/09/2034 | UTC+8 | 14/8/2034 | Hồng Kông: Trung Quốc còn là 14/8 |
| 24/04/2037 | UTC+7 | 10/3/2037 | baolamdong.vn (Giỗ Tổ 2037) |
| 24/04/2037 | UTC+8 | 9/3/2037 | Hồng Kông: Trung Quốc còn là 9/3 |
| 13/04/2038 | UTC+7 | 10/3/2038 | baolamdong.vn (Giỗ Tổ 2038) |
| 20/02/2027 | UTC+7 | 15/1/2027 | Hồng Kông, ngaydep.com, baolamdong.vn (sửa ngày 19/2 sai của repo) |

Nên kiểm thêm toàn bộ 15 ngày của bảng hiện tại (với Rằm 2027 sửa thành 20/02) và 128 ô của bảng Việt Nam ở trên làm golden test: bảng này là chuẩn chặn lỗi hồi quy khi viết lại thuật toán trong Kotlin (Python `//` làm tròn xuống, còn Kotlin `/` trên hai `Int` cắt về 0, nên chỉ giống nhau với toán hạng không âm; Python `int()` cắt về 0 như `toInt()` của Kotlin; dùng `Double`).

**Nguồn đã mở thật** (tài liệu chỉ tính nguồn đã mở): Hong Kong Observatory, 16 tệp `T2025e.txt` đến `T2040e.txt` (tải bằng `curl`, múi giờ UTC+8); ngaydep.com 2026-2031 (30 ngày); baolamdong.vn (21 ngày); Wikipedia "Chinese New Year" (2025-2037) và "Mid-Autumn Festival" (2026-2030); xemlicham.com năm 2030 (chỉ dùng các dòng Tết, Rằm Giêng, Vu Lan, Trung Thu; dòng Giỗ Tổ và Đoan Ngọ bị công cụ đọc sai nên không dùng). **Không mở được**: timeanddate.com và holidayapi.com (HTTP 403). Trang chỉ thấy qua bản tóm tắt của công cụ tìm kiếm (ví dụ Thất Tịch 2025-2030) không được tính là nguồn. **Chưa kiểm**: lịch Hàn, Nhật, Đài Loan; mọi năm sau 2040. **Chỉ có một nguồn ngoài**: 23 Chạp (16 ô Việt Nam; 16 ô Trung Quốc, chỉ có trong tệp JSON), Thất Tịch (15 trên 16 ô Việt Nam, 16 trên 16 ô Trung Quốc; ô Việt Nam còn lại, năm 2025, có thêm baolamdong.vn) và Trùng Cửu (16 ô Trung Quốc) chỉ có Hồng Kông cộng thuật toán.

**Giới hạn trung thực của bằng chứng.** Hai trang Việt Nam (ngaydep.com, baolamdong.vn) nhiều khả năng cũng chạy chính thuật toán Hồ Ngọc Đức, nên chúng xác nhận phần cài đặt, không phải thiên văn độc lập. Nguồn độc lập thật sự là Đài Thiên văn Hồng Kông (cho múi giờ UTC+8). Với năm 2038-2040, nguồn Việt Nam chỉ có thuật toán cộng baolamdong.vn cho vài ô.

**Khuyến nghị (đã chọn mặc định):**

| Bước | Làm gì | Lý do | Mục liên quan |
|---|---|---|---|
| Ngay | Sửa Rằm tháng Giêng 2027 thành 2027-02-20 ở `VietnameseHolidays.kt:86` và `holiday_calendar.json:163`; mở rộng bảng sang 2029-2040 bằng bảng ở trên | Rủi ro thấp, không cần thuật toán, người dùng không còn gặp ngày sai | U-01 (6.2) |
| Sau đó | Cài đặt thuật toán trong Kotlin dùng chung (ước tính khoảng 120 dòng, chỉ `kotlin.math`, chạy được trên iOS), múi giờ là tham số; **mặc định UTC+7 cho Việt Nam** | Bảng không bao giờ hết hạn; một mã cho Android và iOS (DEC-59: `CalendarMath` là cài đặt ngày duy nhất) | U-01, 6.3.7 |
| Cùng lúc | Bảng 2025-2040 làm golden test (128 ô Việt Nam, 96 ô Trung Quốc, 15 vector); thêm một bản đồ ngoại lệ nhỏ cho những năm lịch chính thức khác thuật toán | Chặn hồi quy; bắt được năm sát nửa đêm (2027, 2030, 2038) | GV-013, GV-015 (6.5) |
| Ghi nhãn | Với ngày dựa vào bảng hoặc sát nửa đêm: nhãn "dự kiến ±1 ngày" và cho người dùng chỉnh 1 ngày | Ngày dựa vào trăng quan sát hoặc lịch từng vùng (Hồi giáo, Ấn, Thái) có thể lệch 1 ngày giữa các nơi; ngày âm lịch Việt Nam và Trung Quốc lệch nhau theo múi giờ (bảng ở trên) | 6.3.7, 5.2 (thói quen 7) |

Các điểm trái chiều đã cân nhắc: sai số vài phút ở những tháng sóc sát nửa đêm (nhưng đã biết trước, đã liệt kê ở trên); độ chính xác của chuỗi rút gọn chưa được đo ngoài 2025-2040 (ngoài phạm vi); và người dùng nước ngoài có thể mong lịch âm của nước họ chứ không phải của Việt Nam (QĐ-1).

### 7.7 Vị trí tệp và cách dùng

| Tệp | Nội dung | Dùng để làm gì |
|---|---|---|
| `docs/research/data/gift-occasion-draft.json` | Dữ liệu nháp (361.489 byte, 87 dịp, 55 cột mốc, 142 quà, 70 kiêng kỵ, khoá `meta` có `qaFixes` và `status`) | Đầu vào của U-20; mọi số đếm trong tài liệu lấy từ đây |
| `docs/research/data/lunar-2025-2040.json` | Bảng âm lịch Việt Nam và Trung Quốc, độ tin cậy từng ô, 5 ngày lệch, 10 ngày lệch Hàn (chỉ do tính), tháng nhuận, nguồn | Đầu vào của golden test U-01; chuẩn so khi cài thuật toán |
| `docs/research/data/lunar_reference.py` | Cài đặt tham chiếu (Hồ Ngọc Đức, Meeus) và 15 vector | Bản mẫu để dịch sang Kotlin; chạy `python lunar_reference.py` sẽ in kết quả tự kiểm |
| `docs/research/data/sources.md` | Nguồn theo thị trường, gom từ các báo cáo nghiên cứu | Truy nguồn khi duyệt từng bản ghi |

Dữ liệu này là **hạt giống để duyệt**, không phải nội dung cuối. Thứ tự dùng được đề xuất ở 6.3.9: kiểm định cấu trúc (U-19), nạp thử một gói (U-20), người bản địa duyệt phần `low` (QĐ-9) rồi mới bật cho người dùng.

## 8. Thị trường kế tiếp, kiếm tiền và AI

**Ý chính.** Bảng xếp hạng thị trường ở mục này là ƯỚC LƯỢNG của một sub-agent (2026-10-26), không phải số đo. Năm thị trường đầu (Mỹ, Hàn Quốc, Brazil, Philippines, Thái Lan) chỉ cách nhau 0,35 điểm, và cột "nhu cầu" là cột yếu nhất vì không có số tải ứng dụng đôi theo từng nước. Thứ tự chiến lược đề xuất là Philippines, rồi Brazil, rồi Thái Lan; Mỹ và Hàn Quốc nằm trong danh sách theo dõi vì cạnh tranh và tỷ lệ iOS. Philippines rẻ nhất vì không cần ngôn ngữ mới: máy không dùng tiếng Việt đã bị ép sang tiếng Anh (`ui/util/Localization.kt:29-32`). Brazil và Thái Lan phải qua U-24 (cỡ L), riêng Thái Lan còn cần U-38. Chưa làm affiliate (QĐ-8) và chưa gọi LLM. Nếu sau này thêm LLM thì phải là callable phía máy chủ có cổng `vip` (QĐ-7, QĐ-10), vì `firebase/functions/src/entitlement/gatePolicy.js:5` đã quy định như vậy. Khoảng trống rõ nhất ở phần quà là danh sách quà cho hai người, thứ mà WishSync, Wishpicks, Lovewick và Cupla có; InLove chỉ có mục "Yêu thích" trên máy của từng người.

### 8.1 Cách đọc mục này

- Phần thị trường và đối thủ lấy từ MỘT báo cáo của sub-agent, ghi ngày 2026-10-26. Công cụ web trả về bản tóm tắt, không phải HTML gốc. Các trang Play Store không tải được (lỗi 404 hoặc bị cắt), nên mô tả ứng dụng lấy từ App Store, báo chí và trang tổng hợp: đó là bằng chứng gián tiếp.
- Điểm và trọng số ở 8.3 là ƯỚC LƯỢNG của agent.
- Nhãn (H), (M), (L) là nhãn của agent và KHÔNG đồng nhất giữa các bảng (số giao dịch của Kakao Gift là (M) ở ma trận, (H) ở bảng thị trường). Vì vậy cạnh mỗi con số có thêm loại nguồn (App Store, báo chí, trang tổng hợp, nhà cung cấp).
- Dấu `?` trong ma trận nghĩa là nguồn đã đọc không nói đến, không có nghĩa là ứng dụng không có tính năng đó.
- Paired, Agapé, Love Nudge, Amora, Flamme, Waffle và Couple Widget không có trong ma trận: tên của chúng chỉ xuất hiện trong `docs/PRODUCT_RESEARCH.md` hoặc một bài của AARP, và agent không xác minh được là còn hoạt động. Riêng Amora: `docs/PRODUCT_RESEARCH.md` ghi là đối thủ đang hoạt động, agent không xác minh độc lập (nên không đưa vào ma trận).
- Bảng đối thủ tổng quát nằm ở `docs/PRODUCT_RESEARCH.md`; mục này chỉ bàn phần quà, dịp và cột mốc.
- Hàng InLove trong ma trận đã đối chiếu với mã (3.1, 3.2).
- Phần AI ở 8.5.3 là kiến thức kỹ thuật từ trí nhớ, không có nguồn web. Con số chi phí ở 8.5.2 là phép tính của agent, không phải số đo.

### 8.2 Đối thủ ở phần quà, dịp và cột mốc

| Ứng dụng | Lịch, cột mốc, nhắc | Danh sách quà | Gợi ý quà, ngân sách, mua | Nguồn và độ tin |
|---|---|---|---|---|
| Between (VCNC, Hàn Quốc) | Lịch, kỷ niệm, sinh nhật, nhắc mốc 100 ngày | ? | ? | 5 triệu và 10 triệu lượt tải theo vulcanpost và besuccess (báo chí); xếp hạng 3 trong một bài so sánh 02/2026 (M) |
| Been Together (Hàn Quốc) | Hơn 10 loại ngày kỷ niệm, 100 ngày, 1 năm, thông báo | ? | ? | "20 triệu người dùng từ 2012" theo trang App Store id539392422 (lời nhà phát hành); "5 triệu+ lượt tải, 3,6 sao" chỉ có từ trang tổng hợp, chưa kiểm chứng (L) |
| Lovedays, Day Together, The Couple | ? | ? | ? | Chỉ có nguồn thứ cấp trong repo; agent chưa mở trang ứng dụng (L) |
| Cupla (Hàn Quốc; iOS và Android; miễn phí kèm mua trong ứng dụng) | Ngày quan trọng có đếm ngược; "nhắc thông minh" cho buổi hẹn | Wishlist có nhắc, giao việc cho người kia | ? | App Store id1557764033; phần nào bị khoá phí thì chưa rõ (M) |
| Lovewick (iOS) | Cột mốc, sinh nhật, đếm ngược buổi hẹn | Lưu ý tưởng hẹn hò vào wishlist ẩn với người kia để giữ bất ngờ | Ghi sở thích (cà phê, hoa, cỡ); bộ lọc chi phí cho ý tưởng hẹn hò | App Store id1516199115 và datingnews.com; nguồn không nhắc "ngôn ngữ tình yêu" (M) |
| WishSync (mới, trang 05/2026) | Sinh nhật, kỷ niệm, có widget | Wishlist hai người, thấy ý tưởng của nhau | ? | Chỉ một nguồn: appshunter.io id6760728166 (L); là đối thủ trực tiếp nhất cho "wishlist cặp đôi kèm nhắc" |
| tended | Gợi ý trước các dịp kỷ niệm, nhắc theo giờ | ? | Gợi ý quà bằng AI; học từ hội thoại và nhật ký | Product Hunt (L) |
| Wishpicks "Wishlist for Two" | ? | Soạn chung; giữ chỗ (reservation) ẩn với người nhận | ? | Trang wishpicks.com, có trang theo nước us, au, mx (L) |
| Elfster | ? | Wishlist từ trang web bất kỳ qua tiện ích trình duyệt; link chia sẻ | Có cửa hàng riêng; mạnh ở trao đổi quà nhóm và Secret Santa | App Store 4,8 sao, 199 đánh giá qua appfollow (M/L) |
| Giftster | ? | Nhóm gia đình riêng tư, có giữ chỗ | ? | (L) |
| Giftful | Nhiều dịp | Có danh sách | Theo dõi giá; 6 ngôn ngữ | (L) |
| GiftList | ? | Wishlist liên kết cửa hàng, có người đồng tổ chức | "AI gift finder" theo lời quảng cáo | Trang của nhà cung cấp (L) |
| KakaoTalk Gift (không phải ứng dụng đôi) | không áp dụng | không áp dụng | Quà e-voucher gửi qua chat: 189,5 triệu giao dịch từ 01/01 đến 17/12/2025 (khoảng 540 nghìn quà mỗi ngày, tự tính 189,5 triệu chia 351 ngày), khoảng 8.700 thương hiệu, hơn 640 nghìn sản phẩm; thẻ Starbucks đứng đầu | asiae 12/2025 (M) |
| **InLove (hiện tại)** | Lịch có âm lịch Việt Nam; cột mốc ngày ở `alarm/plan/LoveMilestoneRules.kt`; nhắc cố định 3 ngày, 1 ngày và trong ngày | Không có danh sách quà từ người kia; chỉ có mục "Yêu thích" trên máy (8.2.1) | Ngân sách theo nhóm VND (`budgetMaxVnd`); không affiliate; không gọi LLM; chỉ có VI và EN | Đã đối chiếu với mã (3.1, 3.2) |

**Quan sát (M).** Các ứng dụng đôi lâu năm (Between, Been Together, Cupla) mạnh ở lịch, cột mốc và nhắc; phần wishlist và quà nằm ở các ứng dụng nhỏ và mới (WishSync, Wishpicks, Lovewick, tended). Agent không tìm thấy ứng dụng nào nối cả chuỗi "cột mốc tự động, gợi ý quà theo sở thích người kia, ngân sách, mua và lịch sử quà" kèm nội dung theo vùng. Đây là một khoảng trống, nhưng bằng chứng là "không tìm thấy", không phải "không tồn tại". Số lượt tải của Between và Been Together là lời tự công bố hoặc lời của báo chí, không phải số đo độc lập.

#### 8.2.1 "Wishlist" của InLove hiện nay

- Nút ở `ui/screens/GiftScreen.kt:864-893` (testTag `btn_preferences_notebook`, nhãn `if (isEnglish) "Wishlist" else "Yêu thích"`) mở `showWishlistDialog`. `GiftScreen.kt:898-906` mở `WishlistNotebookDialog` (truyền `giftIdeas` và `isEnglish`; `onFavoriteToggle` gọi `viewModel.toggleGiftFavorite`, `onItemClick` gọi `viewModel.openGiftDetail`); composable nằm ở `:913` và chỉ hiển thị `giftIdeas.filter { it.isFavorited }` (`:920`).
- Chú thích mã ở `GiftScreen.kt:909-911` nói rõ: quà ý tưởng và mục yêu thích là trạng thái Room cục bộ, không bao giờ đồng bộ lên Firestore, nên không thể phản ánh món người kia đã thích trên máy của họ. Vì vậy nút được đặt tên trung thực là "wishlist của tôi", không phải "của người ấy".
- Cặp đã ghép online có một tập sở thích chung: `data/repository/OnlineCoupleRepository.kt:127-128` khai báo `_mutualInterests` và `mutualInterests: StateFlow<Set<String>>`; `:259` lấy giao `myInterests.intersect(partnerInterests)`; `:857` lấy giao `currentSet.intersect(partner.interests.toSet())`; `:261` và `:809` đặt lại về rỗng. Tập này chỉ có 10 khoá sở thích (`data/repository/OnlineCoupleRepository.kt:70-81`).
- `ui/viewmodel/InLoveViewModel.kt:1274` gộp tập sở thích chung với `parseLikes(coupleProfile.value?.likesCsv)`; `likesCsv` là văn bản sở thích của người ấy do người dùng tự gõ (`CoupleProfileEntity.likesCsv`, `data/model/Entities.kt:98`). Riêng bộ sinh gợi ý mẫu `data/repository/GiftSuggestionGenerator.kt:17-62` dùng `mutualInterests`.
- Kết luận: có tập sở thích chung (cho cặp online), nhưng không có danh sách món kèm ghi chú, không có "ẩn với người ấy", không có "giữ chỗ".

#### 8.2.2 Đề xuất bổ sung: danh sách quà cho hai người (chưa có trong bảng 6.1)

Đây là một đề xuất riêng, không mang số U hay QĐ.

- Nội dung: mỗi món có ghi chú và khoảng giá; cờ "ẩn với người ấy" để giữ bất ngờ; cờ "giữ chỗ" để người kia thấy món đã có người lo mà không biết ai lo. Mô hình tham khảo: Lovewick (ẩn), Wishpicks (giữ chỗ ẩn với người nhận), WishSync (hai người thấy ý tưởng của nhau).
- Cỡ: L (ƯỚC LƯỢNG). Điều kiện trước: U-29 (lịch sử quà "đã tặng") và U-04 (một bảng cột mốc duy nhất).
- Thiết kế đồng bộ và quyền riêng tư phải quyết riêng và đối chiếu với kế hoạch local-first `docs/superpowers/plans/2026-10-21-local-first-media-profile-ux.md`, kế hoạch đã chọn kỷ niệm chỉ lưu cục bộ, cộng với sao lưu media và manifest.
- Mặc định theo hướng phạm vi nhỏ nhất: làm U-29 và tận dụng `mutualInterests` có sẵn trước; chỉ làm danh sách chung khi có bằng chứng đo được (tỷ lệ mở màn quà, mức dùng mục "Yêu thích").

### 8.3 Xếp hạng thị trường

#### 8.3.1 Cách chấm và bảng điểm

Trọng số do agent chọn: nhu cầu ứng dụng đôi 25%, cường độ và chi tiêu quà 20%, Android nhân sức mua 15%, dễ bản địa hoá 15%, rủi ro pháp lý và văn hoá 10% (5 là rủi ro thấp), cạnh tranh 15% (5 là ít cạnh tranh); tổng 100%. Điểm 0-5 là ƯỚC LƯỢNG. Tỷ lệ Android lấy từ StatCounter, tháng 09/2026; mỗi trang đều được mở (H).

| Thị trường | Nhu cầu | Chi tiêu quà | Android × sức mua | Bản địa hoá | Rủi ro | Cạnh tranh | Tổng | Android 09/2026 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Mỹ | 4 | 5 | 3 | 5 | 3 | 1 | 3,65 | 46,4% |
| Hàn Quốc | 5 | 5 | 3 | 3 | 3 | 1 | 3,60 | 62,2% |
| Brazil | 3 | 5 | 4 | 3 | 3 | 3 | 3,55 | 78,8% |
| Philippines | 3 | 3 | 3 | 5 | 4 | 3 | 3,40 | 86,5% |
| Thái Lan | 3 | 4 | 3 | 3 | 4 | 3 | 3,30 | 74,0% |
| Indonesia | 3 | 3 | 3 | 4 | 3 | 3 | 3,15 | 79,2% |
| Mexico | 2 | 3 | 3 | 4 | 4 | 4 | 3,15 | 72,0% |
| Ấn Độ | 3 | 3 | 3 | 3 | 2 | 3 | 2,90 | 93,9% |
| Thổ Nhĩ Kỳ | 2 | 3 | 3 | 3 | 3 | 3 | 2,75 | 77,0% |
| Nhật Bản | 3 | 3 | 2 | 2 | 3 | 2 | 2,55 | 39,5% |

**Độ nhạy.** Năm thị trường đầu chỉ cách nhau 0,35 điểm (3,65 trừ 3,30), nằm trong sai số của ước lượng. Cột "nhu cầu" yếu nhất: không có số tải ứng dụng đôi theo từng nước (Sensor Tower và AppMagic không mở được). Không nên coi thứ tự này là chính xác.

#### 8.3.2 Bằng chứng theo thị trường

- **Mỹ.** NRF dự báo chi tiêu Valentine 2026 đạt kỷ lục 29,1 tỷ USD, 199,78 USD mỗi người; quà cho bạn đời 14,5 tỷ USD, trang sức 7 tỷ USD, ăn tối ngoài 6,3 tỷ USD (khảo sát NRF và Prosper trên 7.800 người, 01/2026; thông cáo của NRF; H). iOS chiếm 53,6%. Rủi ro FTC 16 CFR 255 (8.4.2). Cạnh tranh điểm 1: WishSync, tended, GiftList; `docs/PRODUCT_RESEARCH.md` còn ghi Amora là đối thủ đang hoạt động, agent không xác minh độc lập (nên không đưa vào ma trận).
- **Hàn Quốc.** Kakao Gift có 189,5 triệu giao dịch năm 2025 (asiae; nhãn (H) ở bảng thị trường, (M) ở ma trận). Chi tiêu kỷ niệm trung bình khoảng 267-293 nghìn KRW mỗi cặp (Asiae 2023; M). Văn hoá "ngày" (100 ngày, ngày 14 hằng tháng, Pepero Day 11/11; M, asianews và Korea Herald). Between có 5-10 triệu lượt tải theo báo chí, Been Together tự nhận 20 triệu (M-L). Đây là quê hương của nhóm ứng dụng, nên nhu cầu cao nhưng cạnh tranh tối đa. Khảo sát Embrain: 51% cho rằng "văn hoá ngày" do doanh nghiệp tạo ra (2024, giảm từ 60,2%; M).
- **Brazil.** CNDL và SPC: Dia dos Namorados 2025 đạt R$22,14 tỷ, khoảng 93 triệu người dự định mua, trung bình R$238 mỗi quà; Fecomércio-SC nêu R$190,5 và Fecomércio-MA nêu R$242 (báo địa phương; M). Ngày này là 12/6 chứ không phải 14/2 (từ trí nhớ, chưa kiểm chứng; dữ liệu nháp có bản ghi `br_dia_dos_namorados`). Mercado Livre có chương trình affiliate. Không có số tải ứng dụng đôi, nên điểm nhu cầu là (L).
- **Philippines.** Android 86,5%. Tiếng Anh đã có trong ứng dụng nên điểm bản địa hoá là 5. Visa Philippines: chi tiêu quà Valentine tăng (02/2024; M). Không có dữ liệu về ứng dụng đôi.
- **Thái Lan.** UTCC 2024: 2,52 tỷ baht, trung bình 2.125 baht mỗi người, tăng 5,4% so với 2023 (thaipbsworld; M). Dự báo UTCC cho 2026 mới hơn: 2,9 tỷ baht, tăng 7,4% (4.4; M). Chữ Thái là chi phí bản địa hoá thêm.
- **Indonesia.** Android 79,2%. Jakpat: chi tiêu Valentine thường từ 100 đến 300 nghìn IDR (không rõ năm; L). Aceh từng cấm quà lưu niệm Valentine; không có lệnh cấm toàn quốc (nguồn cũ; M).
- **Mexico.** Android 72%. Chương trình affiliate của Mercado Libre khởi động năm 2024 ở mức 12-22% (M). Không có dữ liệu nhu cầu (L). Một gói tiếng Tây Ban Nha có thể phục vụ nhiều nước (từ trí nhớ).
- **Ấn Độ.** Android 93,9% nhưng sức mua thấp; rủi ro pháp lý và văn hoá chưa kiểm chứng (điểm 2 là từ trí nhớ; L).
- **Thổ Nhĩ Kỳ.** Android 77%. Không có dữ liệu nhu cầu hay quà (L), nên điểm là trung lập.
- **Nhật Bản.** iOS chiếm 60,5% (Android chỉ 39,5%). White Day trung bình ¥2.601 mỗi quà năm 2024; thị trường quà White Day giảm từ 73 tỷ yên (2014) xuống 24 tỷ yên (2021) (Hiệp hội Kỷ niệm Nhật Bản qua nguồn thứ cấp; M). Between có khoảng 1 triệu lượt tải ở Nhật (nguồn cũ, khoảng 2015; L).

#### 8.3.3 Độ phủ dữ liệu bản nháp theo mã thị trường

Số bản ghi trong bản nháp dữ liệu (mục 7) cho các thị trường ở trên. Một bản ghi có thể liệt kê nhiều mã vùng, nên các hàng chồng lên nhau và KHÔNG được cộng lại (gói WEST liệt kê US, GB, CA và AU).

| Mã | Dịp | Cột mốc | Quà | Kiêng kỵ | Tổng bản ghi |
|---|---:|---:|---:|---:|---:|
| PH | 2 | 2 | 5 | 1 | 10 |
| BR | 2 | 5 | 5 | 3 | 15 |
| TH | 2 | 2 | 4 | 1 | 9 |
| US | 11 | 19 | 25 | 5 | 60 |
| KR | 7 | 5 | 8 | 2 | 22 |
| JP | 4 | 3 | 7 | 2 | 16 |
| ID | 3 | 2 | 6 | 3 | 14 |
| IN | 8 | 2 | 9 | 3 | 22 |
| MX | 1 | 3 | 3 | 1 | 8 |
| TR | 2 | 1 | 4 | 1 | 8 |
| GB | 9 | 19 | 28 | 4 | 60 |
| CN | 7 | 4 | 8 | 9 | 28 |

Ba thị trường được đề xuất (PH 10, BR 15, TH 9 bản ghi) mỏng hơn nhiều so với Mỹ và Anh (60 bản ghi mỗi mã). Việc làm đầy nằm ở U-26 (PH 5 quà và 2 dịp, BR 5 và 2, TH 4 và 2).

#### 8.3.4 Ba thị trường kế tiếp

Đề xuất của agent là một điều chỉnh chiến lược, KHÁC thứ tự điểm thô: Mỹ và Hàn Quốc đứng đầu về điểm nhưng bị đưa vào danh sách theo dõi (8.3.5). Quyết định thuộc về chủ app (QĐ-1, mục 9).

| Thị trường | Lý do | Bước rẻ nhất theo agent | Cần gì từ mục 6 | Khoảng trống dữ liệu |
|---|---|---|---|---|
| Philippines | Tiếng Anh đã có trong ứng dụng; Android 86,5%; văn hoá Đông Nam Á gần Việt Nam | Bật phát hành Play ở Philippines với trang cửa hàng tiếng Anh; thêm một gói dịp (Valentine, kỷ niệm tháng "monthsary", sinh nhật); đo giữ chân D7 và tỷ lệ mở màn quà; không cần affiliate | U-23, U-26, U-33 (cột mốc theo tháng vì "monthsary"; QĐ-3 mặc định tắt, người dùng tự bật). Không cần ngôn ngữ mới: máy không dùng tiếng Việt đã bị ép sang tiếng Anh (`ui/util/Localization.kt:29-32`). SUY LUẬN: nhu cầu thật của U-24 vì thế thấp hơn với Philippines, dù bảng 6.1 xếp U-26 sau U-24; cần xác nhận khi lập kế hoạch U-26 | 5 quà, 2 dịp (mỏng) |
| Brazil | Theo nhận định của agent (ƯỚC LƯỢNG), cường độ quà cao nhất trong nhóm nhiều Android; có affiliate Mercado Livre (M; 8.4.1) | Dịch bộ chuỗi quà và dịp sang pt-BR ("ít chuỗi"); thêm Dia dos Namorados 12/6 vào lịch dịp; thử một trang cửa hàng đóng (thử nghiệm mở) trước 12/6/2027 | HIỆU CHỈNH so với agent: bước "ít chuỗi" không đúng với mã này. `AppLanguage` chỉ có VI và EN (`ui/util/Localization.kt:8-10`), có 678 điểm `isEnglish` trong 30 file và 56 lời gọi `tr()` trong `InLoveViewModel.kt`; thêm pt-BR cần U-24 (cỡ L). Cần thêm U-23, U-26, và U-22 cùng U-28 (đơn vị tiền BRL) | 5 quà, 2 dịp, 5 cột mốc |
| Thái Lan | Có số liệu chi tiêu Valentine (2.125 baht mỗi người); cạnh tranh trung bình; rủi ro thấp | Dịch trang cửa hàng và màn quà sang tiếng Thái; không affiliate | Cùng hiệu chỉnh: cần U-24 (cỡ L), không phải "ít chuỗi". Làm U-07 trước (cố định locale khi đọc và lưu ngày): thiết bị Thái có thể dùng lịch Phật lịch hoặc chữ số Thái ở một số thiết lập (chưa kiểm chứng trên thiết bị thật). Cần thêm U-38 (rà soát font: `FontFamily.Default` một chỗ trong `ui/theme/Type.kt`, 36 dòng `lineHeight =` ở 12 file), U-23, U-26, và U-22 cùng U-28 (baht) | 4 quà, 2 dịp |

Các mục từ U-23 trở đi chỉ bắt đầu sau khi chủ app chốt QĐ-1; U-36, U-37 và U-38 chỉ làm khi QĐ-1 chọn một thị trường thứ hai.

#### 8.3.5 Danh sách theo dõi

- **Mỹ.** Chi tiêu quà lớn nhất nhưng cạnh tranh nhiều (WishSync, tended, GiftList; `docs/PRODUCT_RESEARCH.md` còn ghi Amora), có rủi ro FTC (8.4.2), Android chỉ 46,4%, và bản iOS chưa phát hành (KMP/iOS đang làm; QĐ-2).
- **Hàn Quốc.** Quê hương của nhóm ứng dụng (Between, Been Together, Cupla), nên cần một điểm khác biệt rõ và bản địa hoá tiếng Hàn. Đây là PHƯƠNG ÁN THAY THẾ trong QĐ-1, không phải mặc định. Ngày âm lịch Hàn Quốc (UTC+9) khác Việt Nam ở 10 ngày trong bảng 2025-2040 (7.6), chỉ do thuật toán tính, chưa có nguồn Hàn nào được mở, nên Seollal và Chuseok cần nguồn Hàn riêng.
- **Nhật Bản.** iOS chiếm 60,5% và thị trường quà White Day đang co lại (73 tỷ yên năm 2014 xuống 24 tỷ yên năm 2021).
- Ở 4.3, thị trường phương Tây được xếp ưu tiên iOS cho Mỹ, Anh, Úc; lịch KMP/iOS do chủ app quyết (QĐ-2, mục 9).

### 8.4 Kiếm tiền

#### 8.4.1 Chương trình affiliate

| Chương trình | Mức hoa hồng (có nguồn) | Nguồn, độ tin | Ghi chú |
|---|---|---|---|
| Shopee Affiliate (Việt Nam) | Các nguồn thứ cấp mâu thuẫn: khoảng 2,1-7% ở một bài tiếng Việt, trần khoảng 11%; một trang khác nêu 1-2%; thay đổi theo ngành hàng | misaeshop.vn và ghn.vn (thứ cấp); KHÔNG có trang chính thức của Shopee trong kết quả (L) | Chỉ tính đơn giao thành công; cookie khoảng 7 ngày; phải kiểm tra trên bảng điều khiển của chương trình |
| Amazon Associates (Mỹ) | Bảng chính thức: Gift Cards 0,00%; Furniture, Home, Pets 3,00%; Luxury Beauty 4,00%; Canada Gift Cards PC 1,00%; một bài thứ cấp nêu 1-10% | affiliate-program.amazon.com; (H) cho bảng của Mỹ, các nước khác chưa kiểm | Thẻ quà được trả 0% |
| Coupang Partners (Hàn Quốc) | Chuẩn khoảng 3% giá mua; điện tử khoảng 4%; sách 1%; một số ngành 0-1% | Trang tính calcnara.com và blog tiếng Hàn (M-L) | Số liệu từ trang tính và blog, không phải điều khoản chính thức |
| Mercado Libre (AR, MX, BR) | AR tối đa 15%; MX 12-22% lúc ra mắt năm 2024; một phỏng vấn 2025 nêu tối đa 24%; điện tử thấp (1-4%) theo một blog thứ ba | mercado.com.ar, báo và blog (M) | Mức khác nhau theo nước và ngành hàng |
| Flipkart (Ấn Độ) | FAQ chính thức: "tối đa 12%"; nguồn khác nêu 0-8,1% và 3-6% | affiliate.flipkart.com/faqs (M); các nguồn còn lại (L) | |
| Tokopedia, TikTok Shop (Indonesia) | Bisnis.com 2023: tối đa 10%, trần Rp50.000; người bán tự đặt hoa hồng và có thể đổi (nghiên cứu pháp lý 2026) | teknologi.bisnis.com (L, số liệu 2023) | |
| Lazada, TikTok Shop Việt Nam, Rakuten (Nhật) | CHƯA kiểm chứng (mức 1% của Rakuten chưa được xác nhận) | Không có nguồn | Không đưa số vào tài liệu |

**Quan sát (M).** Thẻ quà và e-voucher thường được trả 0% hoặc rất thấp (Amazon Mỹ 0%), nên hoa hồng khá hơn đến từ hàng vật lý giá 100-500 nghìn đồng (ước lượng của agent: hoa, mỹ phẩm, đồ trang trí, quần áo). Bổ sung từ mã (SUY LUẬN): trong 20 quà gốc có 6 quà thuộc nhóm "Địa điểm hẹn hò" (trải nghiệm, không có sản phẩm để gắn link), nên một phần danh mục gốc không có chỗ để gắn affiliate.

#### 8.4.2 Quy tắc công bố quảng cáo

- **Mỹ (FTC, 16 CFR 255, sửa đổi 29/06/2023).** Phải công bố "rõ ràng và dễ thấy", đặt cạnh lời giới thiệu. Theo một bản tóm tắt thứ cấp, riêng từ "affiliate link" hay nút "buy now" chưa đủ, nên dùng cách nói như "paid link"; "commissionable link" bị coi là chưa rõ. Quy tắc về đánh giá và lời chứng, 16 CFR 465, có hiệu lực từ 21/10/2024 (nguồn từ văn phòng luật và nhà cung cấp; M). ftc.gov KHÔNG được mở trực tiếp; phải đối chiếu với 16 CFR 255 trước khi dùng cho mục đích pháp lý.
- **Anh.** ASA coi người làm affiliate là "nhà quảng cáo thứ cấp": nhãn "Ad" phải đặt TRƯỚC nội dung liên quan, không dùng cách viết tắt "aff"; câu chung chung "một số link có hoa hồng" chưa đủ. CMA có quyền phạt mạnh hơn theo DMCC Act (asa.org.uk và RPC; M).
- **Liên minh châu Âu (UCPD 2005/29/EC).** Kết quả tìm kiếm KHÔNG nhắc đến. Từ trí nhớ, quảng cáo trá hình bị cấm nên cũng cần nhãn rõ (chưa kiểm chứng).
- **Việt Nam.** Luật quảng cáo và việc công bố tiếp thị liên kết CHƯA được nghiên cứu: cần hỏi luật sư trước khi chạy thí điểm affiliate ở Việt Nam (chưa kiểm chứng).

**Hệ quả thiết kế.** Mỗi thẻ quà có link affiliate cần một nhãn như "Quảng cáo / link tiếp thị liên kết" cạnh nút, ở mọi ngôn ngữ. Không dùng nhãn "Gợi ý của AI" để che hoa hồng. Câu "vì sao gợi ý quà này" không được che một yếu tố tài trợ trong xếp hạng. Điều này gắn với U-03 (gỡ nhãn và điểm "AI").

#### 8.4.3 Chính sách cửa hàng ứng dụng

- **Google Play** (support.google.com/googleplay/android-developer/answer/10281818, đã mở; H). Hàng vật lý (quần áo, điện tử...) nằm NGOÀI Play Billing; thuê bao nội dung số phải dùng Play Billing; ứng dụng không được hướng người dùng sang phương thức thanh toán khác Play cho hàng số. Hệ quả: link ra Shopee hay Amazon cho hàng vật lý về nguyên tắc được phép; ứng dụng không được bán hay quảng bá gói VIP qua link ngoài. Thẻ quà và e-voucher của bên thứ ba: ranh giới vật lý và số chưa rõ trong kết quả (chưa kiểm chứng), nên tránh bán trong ứng dụng lúc đầu. Một ngoại lệ cho Mỹ theo thông báo 29/10/2025 (có thể đã hết hạn, chưa xác nhận) không liên quan tới Việt Nam.
- **Apple** (developer.apple.com/app-store/review/guidelines, đã mở; H; chỉ đọc 100 nghìn ký tự đầu trong tổng 114 nghìn). Điều 3.1.3(e): hàng tiêu dùng vật lý bán ngoài ứng dụng phải dùng phương thức thanh toán khác IAP. Điều 5.1.2(i): dữ liệu cá nhân chia sẻ cho bên thứ ba, "kể cả AI của bên thứ ba", phải được công bố và xin phép rõ ràng trước. Điều này quan trọng cho bước LLM (8.5) và cho bản iOS.

#### 8.4.4 Thẻ quà và e-voucher

Bằng chứng duy nhất là Kakao Gift (8.2). InLove không có nền tảng thanh toán, nên khuyến nghị của agent là dẫn (deeplink) tới trang e-gift của một đối tác hoặc qua affiliate, không lưu giá trị trong ứng dụng, để tránh yêu cầu giấy phép ví hoặc thanh toán (từ trí nhớ, chưa kiểm chứng). Đối tác ở Việt Nam: chưa nghiên cứu.

#### 8.4.5 Gói VIP "kế hoạch quà"

Phán đoán của agent (M), giữ nguyên nguyên tắc kiểm quyền ở máy chủ:

- **Miễn phí:** bộ gợi ý tĩnh, 3 nhắc cố định, 1 danh sách ý tưởng. Link affiliate KHÔNG đặt sau tường trả phí (đó là doanh thu, cần nhiều lượt xem nhất).
- **VIP:** wishlist hai người không giới hạn; nhắc sớm tuỳ chỉnh (ví dụ 14 và 7 ngày); kế hoạch quà cả năm; ngân sách theo dịp; lịch sử quà; "vì sao quà này" mở rộng; hạn mức AI theo tháng.
- Quyền VIP có chi phí biên (nhất là LLM) phải được kiểm ở máy chủ. Không để Remote Config hay máy khách quyết định ai là VIP. Giữ Play Billing cho thuê bao; không dùng link affiliate để dẫn tới thanh toán gói.

Hạ tầng sẵn có trong repo (đã kiểm):

- `firebase/functions/src/entitlement/gatePolicy.js` (44 dòng) mặc định TỪ CHỐI. Chỉ `entitlement.vip === true` VÀ `environment === 'Production'` mới là VIP thật (Sandbox không bao giờ qua). Mọi callable mới PHẢI có một dòng trong `GATES` (test `gateCoverage.test.js`, M-84).
- `GATES` có 9 dòng: 6 dòng `free` (`acceptCoupleInvite`, `requestAccountDeletion`, `getAccountToken`, `claimPurchase`, `getEntitlement`, `signAvatarUpload`) và 3 dòng `tiered` (`signMemoryUpload`, `confirmMemoryUpload`, `getMemoryMediaUrl`). `MEDIA_FREE_ALLOWED = true` ở `gatePolicy.js:8` (quyết định của chủ app, "p6 §9 mục 17"). Không có dòng nào cho AI.
- Phía ứng dụng: `billing/BillingManager.kt`, `VipEntitlementResolverTest.kt`, `EntitlementRepositoryTest.kt`; golden GV-060, GV-061 và GV-063.

#### 8.4.6 Năm lý do cho mặc định của QĐ-8 (chưa làm affiliate)

1. Chưa có link sản phẩm ổn định: `whereToBuy` trong bản nháp dữ liệu là chuỗi chữ thuần (0 trên 142 bản ghi rỗng), chưa có URL sản phẩm nào để gắn.
2. Phải thiết kế nhãn công bố (VI và EN) trước khi gắn bất kỳ link nào (8.4.2).
3. Phạm vi sự đồng ý UMP và khai báo Data Safety chưa được kiểm chứng; ATT (iOS) cũng phải rà soát. Repo đã có luồng UMP cho quảng cáo (`gradle/libs.versions.toml:62`, `ads/AdsManagerImpl.kt:112-118`), nhưng phạm vi chính xác của sự đồng ý và khai báo Play Data Safety chưa được kiểm.
4. Số hoa hồng mâu thuẫn giữa các nguồn (8.4.1); chưa có nguồn chính thức cho Shopee.
5. Thứ tự gợi ý không được che yếu tố tài trợ: nếu sau này có hoa hồng, xếp hạng không được thiên vị theo hoa hồng mà không nói rõ.

U-35 là bước đệm không có affiliate: nút "tìm quà" mở tìm kiếm web của hệ thống với tên quà, không affiliate, không gửi dữ liệu cá nhân.

### 8.5 AI và LLM

#### 8.5.1 Hiện trạng trong repo

- `firebase-ai` được khai báo ở `gradle/libs.versions.toml:113` và `app/build.gradle.kts:202`, nhưng KHÔNG có `generativeModel`, `GenerativeModel` hay `FirebaseAI` trong bất kỳ file `.kt`, `.kts`, `.toml`, `.js` hay `.ts` nào: tức là không có lời gọi LLM nào.
- `data/repository/GiftSuggestionGenerator.kt:15-106` là bộ sinh theo mẫu chỉ có tiếng Việt, mang nhãn "AI" (`isAiGenerated = true`). `domain/GiftRanker.kt:35` cộng thêm 1 điểm cho quà `isAiGenerated`. U-03 gỡ cả nhãn lẫn điểm này.
- `firebase/functions/src/entitlement/gatePolicy.js:5` ghi: Gemini (firebase-ai) chưa có callable phía máy chủ, và proxy sau này PHẢI đặt cổng `vip`. `GATES` chưa có dòng AI.
- Remote Config KHÔNG được dùng (không có phụ thuộc, không có lời gọi). Chỉ có chú thích ở `domain/DataResidencyPolicy.kt:7`: công tắc là hằng lúc biên dịch, chỉ chuyển sang Remote Config khi chủ dự án cần rollback theo đợt.

#### 8.5.2 Firebase AI Logic: dữ kiện và giá

Các dữ kiện dưới đây lấy từ trang tài liệu chính thức mà agent đã mở (H), trừ chỗ có ghi khác.

- SDK cho Kotlin và Java, Swift, JS, Dart, Unity. Hai nhà cung cấp: Gemini Developer API (có bậc miễn phí) và Gemini API trên Vertex ("Agent Platform"). Đổi nhà cung cấp chỉ đổi vài dòng khởi tạo (firebase.google.com/docs/ai-logic).
- App Check (Play Integrity) giữ khoá API ở phía máy chủ và chặn máy khách giả. Hạn mức mặc định là 100 RPM mỗi người dùng mỗi vùng; chỉnh được nhưng không chỉnh theo từng người hay nhóm (…/ai-logic/quotas).
- Tên mô hình nên lấy từ Remote Config, không viết cứng (trang Android ai/gemini).
- Đầu ra có cấu trúc: `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(...)` với `responseMimeType = "application/json"` và `responseSchema`; `text/x.enum` cho phân loại; các trường bắt buộc theo mặc định. Hỗ trợ `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite` và các bản Flash 3.x; các mô hình 2.5 được ghi là đã ngừng (…/generate-structured-output).
- Giá và quyền riêng tư (ai.google.dev/gemini-api/docs/pricing): bậc miễn phí ghi "nội dung được dùng để cải thiện sản phẩm = Có", bậc trả phí ghi "Không". Giá USD cho mỗi 1 triệu token (vào/ra, bậc Standard): 3.1 Flash-Lite 0,25/1,50; 3.5 Flash-Lite 0,30/2,50; 2.5 Flash-Lite 0,10/0,40; đầu ra gồm cả token suy nghĩ. Trang giá có ghi một đợt tăng giá bản Flash từ 31/12/2026.
- Trang quản trị dữ liệu: nếu dự án ở gói Spark (không có Cloud Billing) thì áp dụng "Unpaid Services terms" (H). Trang KHÔNG nêu thời hạn lưu giữ hay vùng lưu trữ; phải đọc các điều khoản được liên kết (chưa kiểm chứng).
- Ước tính chi phí của agent (phép tính, không đo; L-M; đã tính lại): một yêu cầu khoảng 800 token vào và 400 token ra trên 3.1 Flash-Lite xấp xỉ 0,0008 USD (0,0002 cộng 0,0006); 10.000 yêu cầu xấp xỉ 8 USD. Token suy nghĩ có thể làm con số gấp đôi hoặc hơn, nên phải đo.
- Độ trễ: chưa đo. Phải đo p50 và p95 trên máy thật trước khi đưa vào luồng giao diện (chưa kiểm chứng).

#### 8.5.3 Thiết kế lai và quyền riêng tư

Phần này là kiến thức kỹ thuật từ trí nhớ, không có nguồn web (L-M).

- **Lai.** Luật và danh mục tuyển chọn lọc ứng viên theo ngân sách, dịp, nước, sở thích, trừ các món đã tặng. LLM chỉ xếp lại và viết lời giải thích trên một danh sách ID cố định, bằng một schema trả về mảng gồm `giftId` và `reason`.
- **Khởi đầu lạnh.** Cho chọn 3-5 thẻ sở thích khi thiết lập người ấy, hoặc người kia tự điền wishlist; mặc định theo dịp, ngân sách và giai đoạn quan hệ.
- **Giải thích.** Một câu "vì sao" dựng từ các thuộc tính khớp, bằng luật, và hiện cả khi không có LLM.
- **Quyền riêng tư.** Chỉ gửi thuộc tính đã ẩn danh (dịp, nhóm ngân sách, thẻ sở thích, ngôn ngữ hoặc nước); KHÔNG gửi tên, ảnh, nội dung kỷ niệm hay ghi chú. Dùng một màn đồng ý riêng, mặc định tắt, thu hồi được (điều 5.1.2(i) của Apple). Dùng bậc trả phí hoặc Vertex để dữ liệu không dùng cho huấn luyện.
- **Chi phí.** Đệm kết quả theo (dịp, nhóm ngân sách, thẻ, ngôn ngữ) trong ngày; hạn mức tháng theo gói; dùng luật dự phòng khi gặp lỗi 429 hoặc ngoại tuyến.

#### 8.5.4 Quyết định: CHƯA làm LLM

Agent kết luận: chưa, sau khi sửa luật và nội dung. Lý do: hiện chỉ có 20 quà gốc, một bộ sinh theo mẫu và nhóm ngân sách VND, nên LLM xếp lại 20 món không thêm giá trị mà thêm rủi ro riêng tư (điều 5.1.2(i) của Apple), chi phí và việc kiểm quyền.

Thứ tự của agent, đã gắn với số U của bảng 6.1:

1. Mở rộng danh mục quà có gắn thẻ: U-19 đến U-22 (mô hình gói, bộ sinh gói, repository, khoá ổn định), dữ liệu từ U-20, cộng U-28 (đơn vị tiền) và U-35 (tìm quà).
2. Cải thiện bộ xếp hạng và lời giải thích theo luật: U-06, U-10, U-15; U-03 và U-11 cho nhãn "AI".
3. Thêm lịch sử quà và wishlist của đối tác: U-29 và đề xuất ở 8.2.2.
4. Chỉ sau đó thử LLM như một bước VIP tuỳ chọn: callable phía máy chủ có cổng `vip`.

Ba điểm cần nêu rõ:

- Phương án (a) của agent, gọi Gemini thẳng từ máy khách qua Firebase AI Logic, chỉ có App Check và hạn mức mặc định 100 RPM mỗi người dùng, không kiểm quyền. Phương án này MÂU THUẪN với quy tắc của repo ở `firebase/functions/src/entitlement/gatePolicy.js:5`. Phương án (b), callable phía máy chủ có cổng `vip`, là chính sách trong repo.
- Câu "bật bằng Remote Config" của agent không khớp với repo: repo không có Remote Config, và chính sách là cổng ở máy chủ.
- SUY LUẬN của tài liệu này (không phải kết luận của agent): với phương án (b), tên mô hình nằm ở máy chủ nên lời khuyên "tên mô hình lấy từ Remote Config" không còn cần, và phụ thuộc `firebase-ai` ở máy khách cũng thừa, vì callable dùng Gemini API phía máy chủ, độc lập với SDK `firebase-ai` ở máy khách. Đây là nội dung của QĐ-10.

#### 8.5.5 Đối chiếu danh sách ưu tiên của agent với bảng 6.1

Agent gắn nhãn P0, P1, P2 cho danh sách của mình. Tài liệu này đổi thành "trước", "sau", "sau nữa", vì P0 đến P3 là thang mức độ của các phát hiện ở mục 3.

| Nhóm | # | Việc theo agent | Nằm ở đâu |
|---|---:|---|---|
| Trước | 1 | Mở rộng và gắn thẻ danh mục quà | U-19 đến U-22, U-28, U-35 |
| Trước | 2 | Wishlist hai người, "ẩn với người ấy", giữ chỗ, nối với cột mốc tự động | Đề xuất ở 8.2.2 (cần U-29 và U-04) |
| Trước | 3 | Nhắc ở nhiều mốc (14, 7, 3, 1 ngày), đính gợi ý quà vào thông báo; kiểm tra ngày âm và dương cho Việt Nam | U-16 và U-31; U-01 và U-17 cho ngày âm lịch |
| Trước | 4 | Thiết kế nhãn công bố affiliate (VI, EN) trước khi gắn link | 8.4.2 và QĐ-8; chưa làm |
| Sau | 5 | Thí điểm affiliate Việt Nam: Shopee cho hàng vật lý 100-500 nghìn đồng; đo CTR và hoa hồng thật; kiểm điều khoản chính thức | QĐ-8; chưa làm |
| Sau | 6 | Thêm Philippines (tiếng Anh, không bản địa hoá), rồi Brazil (pt-BR và Dia dos Namorados 12/6), rồi Thái Lan | U-23, U-26 (Philippines trước); Brazil và Thái Lan cần U-24 |
| Sau | 7 | Ngân sách theo dịp và lịch sử quà (VIP), kiểm quyền ở máy chủ | U-28, U-29; cổng VIP ở 8.4.5 |
| Sau nữa | 8 | LLM tuỳ chọn cho VIP, chỉ sau 1-3; Firebase AI Logic, App Check, Remote Config, đồng ý riêng; cần quyết định giữa máy khách và Cloud Function | QĐ-7, QĐ-10 (8.5.4) |
| Sau nữa | 9 | Hàn Quốc chỉ khi có điểm khác biệt (planner theo nhịp 100 ngày và ngày 14 hằng tháng, deeplink Coupang Partners), chấp nhận bản địa hoá tiếng Hàn và cạnh tranh trực tiếp | Phương án thay thế trong QĐ-1, không phải mặc định |
| Sau nữa | 10 | Mỹ và Nhật cùng bản iOS; Mỹ có doanh thu quà lớn nhất nhưng cạnh tranh và FTC; Nhật iOS 60,5%, thị trường White Day đang co | QĐ-2 (KMP/iOS) |

### 8.6 Giới hạn

Các giới hạn do chính agent nêu, cộng với các điểm được thêm khi đối chiếu với mã:

- Trang Play Store của Between, Lovedays và Lovewick không được mở; con số 5 triệu lượt tải của Been Together chỉ có từ trang tổng hợp.
- Không có số tải hay xếp hạng theo từng nước (Sensor Tower và AppMagic không truy cập được), nên cột "nhu cầu" gần như phỏng đoán với Brazil, Philippines, Mexico, Ấn Độ và Thổ Nhĩ Kỳ.
- ftc.gov, CMA và UCPD của EU không được mở. Không có điều khoản chính thức nào được kiểm cho Shopee, Lazada, TikTok Shop, Rakuten hay Amazon ngoài Mỹ.
- Luật quảng cáo và công bố tiếp thị liên kết của Việt Nam chưa được nghiên cứu; phải hỏi luật sư trước khi thí điểm affiliate.
- Ranh giới của Google Play với thẻ quà số của bên thứ ba chưa kiểm chứng, và ngoại lệ cho Mỹ (10/2025) chưa xác nhận.
- Phần "AI thực hành tốt" là kiến thức từ trí nhớ và kinh nghiệm; ước tính chi phí là phép tính của agent, không đo; độ trễ chưa đo.
- Mô hình và giá thay đổi nhanh (trang giá ghi một đợt tăng giá Flash từ 31/12/2026). Gemini 3.5 Flash-Lite chỉ thấy trên trang chính thức.
- Nhãn (H), (M), (L) không đồng nhất giữa các bảng; agent đã vượt ngân sách tìm kiếm của mình.
- Bước "ít chuỗi" cho Brazil và Thái Lan trong báo cáo của agent bị hiệu chỉnh theo mã (8.3.4); báo cáo của agent không có phần đối chiếu mã bản địa hoá.

## 9. Quyết định cần chủ app chốt, rủi ro và việc chưa kiểm chứng

**Ý chính.** Có mười quyết định (QĐ-1 đến QĐ-10) cần chủ app chốt. Mỗi quyết định có một mặc định đã nghiên cứu, nên chủ app có thể trả lời gọn bằng một câu "đồng ý mặc định". Đợt 1 (U-01 đến U-18) không phụ thuộc quyết định nào và có thể duyệt ngay. QĐ-1 (thị trường thứ hai) và QĐ-2 (thời điểm nâng Room lên v15 so với việc chuyển cơ sở dữ liệu sang `:shared`, tức D-15) cần chốt trước khi bắt đầu Đợt 2; tám quyết định còn lại chỉ cần chốt khi việc bị chặn đến lượt. Mục này cũng gom ba điểm duyệt đi kèm từng việc (không đánh số QĐ), bảng rủi ro chung và danh sách việc chưa kiểm chứng kèm cách kiểm. Chưa có mã, dữ liệu seed hay lược đồ nào bị đổi. Đường dẫn trong mục này theo quy ước ở đầu mục 3.

### 9.1 Bảng tóm tắt

| QĐ | Câu hỏi | Mặc định | Cần chốt khi nào | Chặn việc nào |
|---|---|---|---|---|
| QĐ-1 (9.2.1) | Thị trường thứ hai là nước nào? | Philippines, rồi Brazil, rồi Thái Lan | Trước khi bắt đầu Đợt 2 | U-23 đến U-28; U-36 đến U-38 |
| QĐ-2 (9.2.2) | Nâng Room lên v15 vào lúc nào so với D-15? | Một lần nâng v15 gộp các cột mới (U-27, U-28, U-29, U-37), làm SAU D-15; U-19 đến U-22 xong TRƯỚC D-13 | Trước khi bắt đầu Đợt 2 và trước khi D-13 bắt đầu | Thứ tự U-19 đến U-22 so với D-13; các cột mới của U-27, U-28, U-29, U-37 |
| QĐ-3 (9.2.3) | Nhắc theo tháng bật hay tắt mặc định? | TẮT; người dùng tự bật (U-33); không thêm cột | Trước U-33 | U-33 |
| QĐ-4 (9.2.4) | Thêm ngôn ngữ giao diện nào ngoài VI và EN? | Chưa thêm ngôn ngữ nào; U-24 chỉ dựng hạ tầng mã ngôn ngữ BCP-47 | Khi Play Console có số liệu | Việc thêm bất kỳ ngôn ngữ nào (pt-BR, Tây Ban Nha, Thổ Nhĩ Kỳ, Nga, Ba Lan, Thái) |
| QĐ-5 (9.2.5) | Có hỗ trợ nhắc hẹn lãng mạn cho Ả Rập Xê Út và Kuwait không? | Không; chưa liệt kê hai nước là thị trường hỗ trợ cho đến khi có rà soát pháp lý và văn hoá tại chỗ | Chỉ khi chọn một thị trường vùng Vịnh | Việc mở thị trường Ả Rập Xê Út, Kuwait |
| QĐ-6 (9.2.6) | Có suy ra hay lưu tôn giáo của người dùng không? | Không bao giờ; gói dịp tôn giáo tuỳ chọn chỉ bật khi người dùng chọn, chỉ lưu trên máy, không đồng bộ | Trước mọi gói dịp tuỳ chọn (U-20, U-32) | Mọi gói dịp tôn giáo tuỳ chọn |
| QĐ-7 (9.2.7) | Có dùng LLM để gợi ý quà không? | Chưa; nếu làm thì là tính năng VIP tuỳ chọn, qua callable phía máy chủ có cổng `vip` | Sau các bước 1 đến 3 ở 8.5.4 | Mọi lệnh gọi LLM |
| QĐ-8 (9.2.8) | Có gắn link affiliate không? | Chưa; `whereToBuy` giữ là chữ thường; U-35 chỉ mở tìm kiếm web | Trước khi làm nhãn công bố hay link | Mọi link affiliate |
| QĐ-9 (9.2.9) | Dữ liệu nháp có cần người bản địa duyệt trước khi hiện không? | Có: phần `low` không tự hiện; điều kiêng kỵ chỉ từ `med` trở lên; thị trường chưa duyệt mang nhãn "bản nháp" | Trước U-26 | U-26 |
| QĐ-10 (9.2.10) | Phụ thuộc `firebase-ai` chưa có lệnh gọi nào xử lý ra sao? | Giữ nguyên đến khi QĐ-7 chốt; chỉ gỡ nếu QĐ-7 là callable phía máy chủ (SUY LUẬN) | Cùng lúc với QĐ-7 | Việc gỡ phụ thuộc (đổi Gradle) |

**Trả lời nhanh.** Một câu "đồng ý mặc định" nghĩa là: Philippines là thị trường thứ hai (rồi Brazil, Thái Lan); một lần nâng v15 sau D-15; nhắc theo tháng tắt mặc định; chưa thêm ngôn ngữ nào; Ả Rập Xê Út và Kuwait chưa hỗ trợ; không bao giờ suy ra tôn giáo; chưa dùng LLM; chưa affiliate; không tự hiện dữ liệu `low`; giữ `firebase-ai` đến khi QĐ-7 chốt.

### 9.2 Chi tiết từng quyết định

Mỗi khối có năm nhãn: **Câu hỏi**, **Mặc định**, **Bằng chứng**, **Phương án khác** và **Mở khoá** (việc nào được mở hay bị chặn). Nhãn (H), (M), (L) và các chữ SUY LUẬN, ƯỚC LƯỢNG, HIỆU CHỈNH giữ nghĩa như ở mục 8.

#### 9.2.1 QĐ-1: Thị trường thứ hai

**Câu hỏi.** Sau Việt Nam, nên mở thị trường nào trước?

**Mặc định.** Philippines, rồi Brazil, rồi Thái Lan (8.3.4). Đây là điều chỉnh chiến lược, KHÁC thứ tự điểm thô ở 8.3.1 (Mỹ 3,65; Hàn Quốc 3,60; Brazil 3,55; Philippines 3,40; Thái Lan 3,30): Mỹ và Hàn Quốc đứng đầu về điểm nhưng được đưa vào danh sách theo dõi (8.3.5). Philippines đứng trước Brazil dù điểm thô thấp hơn vì là thị trường rẻ nhất để mở: tiếng Anh đã có trong ứng dụng, Android cao nhất trong năm thị trường (86,5%), và theo mã thì chưa cần ngôn ngữ mới (SUY LUẬN, xem dưới); Brazil cần thêm pt-BR (U-24, cỡ L).

**Bằng chứng.** Bảng dưới gom điểm ở 8.3.1, số bản ghi nháp ở 8.3.3 và việc cần thêm trong ứng dụng ở 8.3.4.

| Thị trường | Điểm (8.3.1) | Android | Sáu điểm thành phần | Dữ liệu nháp (dịp / cột mốc / quà / kiêng kỵ) | Việc thêm trong ứng dụng | Vai trò |
|---|---:|---:|---|---|---|---|
| Philippines | 3,40 | 86,5% | 3-3-3-5-4-3 | 2 / 2 / 5 / 1 | U-23, U-26, U-33; chưa cần ngôn ngữ mới (SUY LUẬN) | Mặc định, thứ nhất |
| Brazil | 3,55 | 78,8% | 3-5-4-3-3-3 | 2 / 5 / 5 / 3 | U-23, U-26, U-22 cùng U-28 (BRL); cần U-24 (cỡ L) vì chưa có pt-BR | Mặc định, thứ hai |
| Thái Lan | 3,30 | 74,0% | 3-4-3-3-4-3 | 2 / 2 / 4 / 1 | U-07 trước; U-24 (cỡ L), U-38, U-23, U-26, U-22 cùng U-28 (baht) | Mặc định, thứ ba |
| Mỹ | 3,65 | 46,4% | 4-5-3-5-3-1 | 11 / 19 / 25 / 5 (gói WEST, dùng chung với GB, CA, AU) | Cần bản iOS (QĐ-2); cạnh tranh nhiều; rủi ro FTC | Danh sách theo dõi |
| Hàn Quốc | 3,60 | 62,2% | 5-5-3-3-3-1 | 7 / 5 / 8 / 2 | Bản địa hoá tiếng Hàn (U-24 cộng bản dịch; suy luận từ 8.3.5) và nguồn Hàn riêng cho Seollal, Chuseok | Phương án thay thế |

Thứ tự sáu điểm thành phần: nhu cầu, chi tiêu quà, Android × sức mua, dễ bản địa hoá, rủi ro (5 = ít rủi ro), cạnh tranh (5 = ít cạnh tranh).

- **Điểm là ƯỚC LƯỢNG.** Điểm 0-5 là của agent, không đo. Trọng số do agent chọn: nhu cầu 25%, chi tiêu quà 20%, Android × sức mua 15%, dễ bản địa hoá 15%, rủi ro pháp lý và văn hoá 10%, cạnh tranh 15%.
- **Khoảng cách hẹp.** Năm thị trường đầu cách nhau 0,35 điểm (3,65 so với 3,30), nên thứ tự thô không đủ để quyết một mình. Cột "nhu cầu" yếu nhất vì không có số tải theo từng nước (Sensor Tower và AppMagic không truy cập được).
- **Tỷ lệ Android** lấy từ StatCounter 09/2026 (H; từng trang đã mở).
- **Không cộng các hàng.** Số bản ghi của các hàng chồng nhau (gói WEST chứa US, GB, CA, AU).
- **Quan sát về tiền tệ.** 8.3.4 không nêu U-22 và U-28 cho Philippines và các bản nháp không nhắc PHP, trong khi U-26 nêu rủi ro đổi thị trường làm đổi đơn vị tiền (U-28). Cách xử lý tiền của Philippines cần xác nhận khi lập kế hoạch U-26.

Lý do và việc cần làm theo từng thị trường:

- **Philippines (mặc định, thứ nhất).** Tiếng Anh đã có trong ứng dụng, Android 86,5%, văn hoá Đông Nam Á gần Việt Nam. Bước rẻ nhất theo agent: phát hành Play ở Philippines với trang cửa hàng tiếng Anh, thêm một gói dịp (Valentine, "monthsary", sinh nhật), đo giữ chân D7 và tỷ lệ mở màn quà, không affiliate. SUY LUẬN: máy không dùng tiếng Việt đã bị ép sang tiếng Anh (`ui/util/Localization.kt:29-32`), nên U-24 ít cần hơn với Philippines, dù 6.1 xếp U-26 sau U-24; xác nhận khi lập kế hoạch U-26. Khoảng trống dữ liệu: 5 quà, 2 dịp.
- **Brazil (mặc định, thứ hai).** Theo nhận định của agent (ƯỚC LƯỢNG), cường độ tặng quà cao nhất trong nhóm nhiều Android; có chương trình affiliate Mercado Livre (M; 8.4.1). HIỆU CHỈNH so với agent: bước "ít chuỗi" không đúng với mã này, vì `AppLanguage` chỉ có VI và EN (`ui/util/Localization.kt:8-10`), có 678 điểm `isEnglish` trong 30 tệp và 56 lời gọi `tr()` trong `InLoveViewModel.kt`; thêm pt-BR cần U-24 (cỡ L). Dia dos Namorados là 12/6 (bản ghi nháp `br_dia_dos_namorados`; ngày lấy từ trí nhớ, chưa kiểm chứng), nên có thử nghiệm kín trên Play trước 12/6/2027. Khoảng trống dữ liệu: 5 quà, 2 dịp, 5 cột mốc.
- **Thái Lan (mặc định, thứ ba).** Có số liệu chi tiêu Valentine (2.125 baht mỗi người; khảo sát UTCC 2024, tổng 2,52 tỷ baht, tăng 5,4% so với 2023; M; dự báo UTCC 2026 mới hơn: 2,9 tỷ baht, tăng 7,4%, xem 4.4), cạnh tranh trung bình, rủi ro thấp. Cùng hiệu chỉnh như Brazil: cần U-24 (cỡ L). Làm U-07 trước, vì thiết bị Thái có thể dùng lịch Phật lịch hoặc chữ số Thái ở một số thiết lập (chưa kiểm chứng trên thiết bị thật). Cần U-38 (`FontFamily.Default` một chỗ trong `ui/theme/Type.kt`, 36 dòng `lineHeight =` ở 12 tệp). Khoảng trống dữ liệu: 4 quà, 2 dịp.
- **Mỹ (danh sách theo dõi).** Chi tiêu quà lớn nhất: NRF dự báo chi tiêu Valentine 2026 đạt 29,1 tỷ USD, 199,78 USD mỗi người, quà cho bạn đời 14,5 tỷ USD (khảo sát NRF và Prosper trên 7.800 người, 01/2026; H; xem 8.3.2). Nhưng cạnh tranh nhiều (WishSync, tended, GiftList; `docs/PRODUCT_RESEARCH.md` còn ghi Amora, agent không xác minh độc lập), có rủi ro FTC 16 CFR 255 (8.4.2), Android chỉ 46,4% (iOS 53,6%), và bản iOS chưa phát hành (QĐ-2).
- **Hàn Quốc (phương án thay thế).** Quê hương của nhóm ứng dụng cặp đôi (Between, Been Together, Cupla), nên cần một điểm khác biệt rõ (ví dụ lịch 100 ngày và ngày 14 hằng tháng) cùng bản địa hoá tiếng Hàn. Seollal và Chuseok cần nguồn Hàn riêng: ngày âm lịch Hàn Quốc (UTC+9) khác bảng Việt Nam ở 10 ngày theo thuật toán, chưa mở nguồn Hàn nào (7.6).
- **Nhật Bản (không đưa vào mặc định).** iOS chiếm 60,5% (Android 39,5%) và thị trường quà White Day đang co, từ 73 tỷ yên năm 2014 xuống 24 tỷ yên năm 2021 (8.3.5).

**Phương án khác.** (1) Chọn Brazil trước: điểm cao hơn Philippines 0,15 và cường độ quà cao, nhưng cần U-24 (cỡ L) cùng U-22 và U-28 trước khi phát hành. (2) Chọn Hàn Quốc, nếu chủ app có một điểm khác biệt rõ so với Between, Been Together, Cupla. (3) Chọn Mỹ khi đã có bản iOS (QĐ-2).

**Mở khoá.** Khi QĐ-1 chốt, U-23 đến U-28 được mở; U-36, U-37 và U-38 chỉ mở khi QĐ-1 chọn một thị trường thứ hai. U-26 (cỡ L) làm đầy dữ liệu (khoảng giá còn trống, độ phủ, bản địa hoá), cần người bản địa duyệt (QĐ-9) và rà soát pháp lý (QĐ-5); thị trường chưa duyệt mang nhãn "bản nháp". Nếu QĐ-1 chốt trước D-15, nâng v15 trước rồi sửa bước ghim băm của D-06 và bước kiểm của D-15 (xem QĐ-2).

#### 9.2.2 QĐ-2: Nâng Room lên v15 và thứ tự so với KMP/iOS

**Câu hỏi.** Nâng lược đồ Room lên phiên bản 15 vào lúc nào so với kế hoạch KMP/iOS, nhất là D-13 (chuyển quà, xếp hạng và mô hình nội dung sang `commonMain`) và D-15 (chuyển DAO và cơ sở dữ liệu sang `:shared`)?

**Mặc định.** MỘT lần nâng v15 gộp các cột mới (U-27 và U-28, cộng cột của U-29 và U-37 theo 6.5), làm SAU D-15, vì bước kiểm của D-15 đòi tệp lược đồ `14.json` không đổi (chi tiết ở 6.3.5). U-19 đến U-22 làm TRƯỚC D-13 (nguyên tắc 2 ở 6.0: U-03 và U-22 trước D-13). Nếu U-19 đến U-21 xong trước D-13, D-13 chuyển `PackContentRepository` thay cho `GiftIdeasSeed` (6.3.9). Nếu lần nâng chung không diễn ra, hoãn U-29 thay vì nâng riêng.

**Bằng chứng.** Các số quyết định lấy từ `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md`.

- D-13 chuyển `GiftIdeasSeed`, `GiftRanker`, `GiftCatalog`, `ContentModels`, `ContentRepository` nguyên văn sang `commonMain` (DEC-58); `toNfc()` được áp vào `GiftRanker` trước khi chuyển.
- D-15 chuyển DAO và cơ sở dữ liệu sang `:shared` và kiểm bằng `git diff --exit-code -- shared/schemas` (`docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:328`); DEC-22 (`docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:39`) giữ tệp `14.json` không đổi; F-16 của kế hoạch KMP chuyển nó từ `app/schemas/` sang `shared/schemas/`. DEC-71 (`docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:88`) quy định `InLoveDatabaseAccess.kt` (D-42, chưa có trong cây) là điểm gọi cơ sở dữ liệu duy nhất của `:app`.
- Kế hoạch KMP tự chừa một lần nâng v15 nếu D-06 buộc `Migration(14, 15)` rỗng (giả định A12, chưa kiểm chứng, `docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:30`); khi đó gộp các cột mới vào đúng lần nâng ấy (SUY LUẬN).
- Kiểm 2026-10-26: `shared/src/commonMain` chưa có tệp Kotlin nào về quà, ngày lễ, nội dung hay seed (chỉ có `spike/SpikeDatabase.kt`), nên D-13 chưa làm và D-15 cũng chưa.
- Đổi khoá hay mô hình sau D-13 kéo theo sinh lại golden; xem bảng rủi ro ở 6.3.9.

**Phương án khác.** Nâng v15 ngay, trước D-15, rồi sửa bước ghim băm của D-06 và bước kiểm của D-15 (cách này áp dụng khi QĐ-1 chốt trước D-15). Hoặc không nâng: các việc cần cột mới (U-27, U-28, U-29, U-37) phải hoãn.

**Mở khoá.** Đợt 2 bắt đầu khi QĐ-1 và QĐ-2 cùng chốt (6.1, bước 10 của thứ tự đề xuất). Đó là quy tắc gọn; về kỹ thuật, U-19 đến U-22 (đường tối thiểu cho riêng thị trường Việt Nam, 6.3) không phụ thuộc QĐ-1, chỉ cần QĐ-2 cho thứ tự so với D-13. Về iOS: 4.3 xếp ưu tiên iOS cho Mỹ, Anh, Úc, và 8.5.5 (hàng 10) đặt Mỹ và Nhật cùng lúc với bản iOS.

#### 9.2.3 QĐ-3: Nhắc theo tháng

**Câu hỏi.** Nhắc theo tháng ("tròn tháng", monthsary, mesversário) bật hay tắt mặc định?

**Mặc định.** TẮT; người dùng tự bật trong cài đặt (U-33). Ngày bắt đầu duy nhất `anniversaryDate` vẫn là mốc gốc, không thêm cột; ngày 29 đến 31 lùi về ngày cuối tháng.

**Bằng chứng.**

- Việt Nam: không có bằng chứng cho "tròn tháng" hay mốc 100 ngày (4.1); `vn_monthsary` có độ tin `low` và trọng số văn hoá 2.
- Philippines: "monthsary" là từ thật và có thị trường quà thương mại, nhưng không có khảo sát (4.4); `ph_monthsary` có trọng số văn hoá 3 (cao nhất trong các giá trị đang có: 1, 2, 3) và độ tin `med`.
- Indonesia: hari jadian hằng tháng không có nguồn, mặc định tắt (4.4). Phương Tây: monthiversary không có khảo sát, tuỳ chọn và tắt (4.3).
- Brazil: mesversário có thật ở mức blog, độ tin trung bình; 4.5 gợi ý bật mặc định cho Brazil (tháng 1 đến 11) và để người dùng tắt. Các nước Tây Ban Nha ngữ chỉ có mốc 1, 3, 6, 9 tháng và mặc định tắt. Thái Lan: mốc 100 ngày chưa xác nhận, không đưa vào mặc định.

**Phương án khác.** Hỏi ngay ở onboarding thay cho công tắc trong cài đặt (hợp với Philippines và Brazil, nơi "monthsary" và "mesversário" là từ có thật). Hoặc bật mặc định riêng cho Brazil, để người dùng tắt; đây là ngoại lệ cần xác nhận nếu QĐ-1 chọn Brazil.

**Mở khoá.** U-33 (cần cả U-04).

#### 9.2.4 QĐ-4: Ngôn ngữ giao diện thêm

**Câu hỏi.** Có thêm ngôn ngữ giao diện ngoài tiếng Việt và tiếng Anh (pt-BR, Tây Ban Nha, Thổ Nhĩ Kỳ, Nga, Ba Lan, Thái) không, và khi nào?

**Mặc định.** Chưa thêm ngôn ngữ nào. U-24 chỉ dựng hạ tầng mã ngôn ngữ BCP-47 (cỡ L); ngôn ngữ chỉ thêm khi số liệu Play Console cho thấy cần. Nhất quán với QĐ-1: Philippines không cần ngôn ngữ mới (SUY LUẬN).

**Bằng chứng.**

- `AppLanguage` chỉ có VI và EN (`ui/util/Localization.kt:8-10`); máy không dùng tiếng Việt bị ép sang EN (`ui/util/Localization.kt:29-32`) nhưng locale mặc định của máy vẫn giữ nguyên.
- Có 678 điểm `isEnglish` trong 30 tệp và 56 lời gọi `tr("vi", "en")`, tất cả trong `ui/viewmodel/InLoveViewModel.kt` (đếm 2026-10-26).
- Manifest khai `android:supportsRtl="true"` (`AndroidManifest.xml:26`) nhưng chưa có `locales_config`; chưa có người dịch (QĐ-9).
- Ứng dụng không có phân tích dùng nào (`privacy/AppPrivacyCoordinator.kt:15`), nên nguồn số liệu duy nhất hiện có là Play Console. 4.5 cũng nói chỉ thêm ngôn ngữ khi có số liệu Play Console.

**Phương án khác.** Thêm một ngôn ngữ cùng với thị trường thứ hai (Brazil: pt-BR; Thái Lan: tiếng Thái, kèm U-38). Hoặc thêm công cụ đo trong ứng dụng để có số liệu sớm hơn (kéo theo UMP và Data Safety, xem 9.5).

**Mở khoá.** Việc thêm từng ngôn ngữ; U-38 (rà soát font và chữ phải-sang-trái) làm sau U-24.

#### 9.2.5 QĐ-5: Ả Rập Xê Út và Kuwait

**Câu hỏi.** Có hỗ trợ nhắc hẹn lãng mạn cho Ả Rập Xê Út (SA) và Kuwait (KW) không?

**Mặc định.** Chưa. SA và KW không được liệt kê là thị trường hỗ trợ cho đến khi có rà soát pháp lý và văn hoá tại chỗ. Nếu vẫn có người dùng ở đó: nhắc dịp lãng mạn tắt mặc định, chữ trung tính ("bạn đời"), không công khai ảnh đôi theo mặc định.

**Bằng chứng.**

- 4.5: báo cáo 2008-2009 nói cảnh sát tôn giáo Ả Rập Xê Út tịch thu đồ màu đỏ trước 14/2 và một nghị sĩ Kuwait đề nghị cấm; tình trạng 2025-2026 chưa kiểm chứng.
- Dữ liệu nháp: SA và KW mỗi nước có 1 dịp và 2 điều kiêng, chưa có quà; AE (UAE) và EG (Ai Cập) mỗi nước chỉ có 1 điều kiêng, không có dịp, cột mốc hay quà.
- Gói LATAM_TR_CIS có 14 mục kiêng kỵ và nhạy cảm (4 mạnh, 10 mềm; 4.5, 6.3). Trong đó 5 mục (`br_not_feb14`, `co_not_couple_only`, `mena_sa_valentine`, `mena_kw_valentine`, `mena_unmarried_couples`; khái niệm `valentine_sensitive`) là cảnh báo về cách viết dịp, không phải điều kiêng khi tặng quà. Chúng sẽ thành trường dịp đề xuất `copyNotes` (số nhiều; trường ĐỀ XUẤT, chưa có trong dữ liệu nháp).
- Cùng nhóm nhạy cảm: Nga tắt Valentine, giữ 8/3 và 23/2. Ukraine giữ 8/3 và cần người bản địa xem lại, vì dự luật số 9009 (2023) và dự thảo nghị quyết số 15052 (đăng ký 2/3/2026) đề xuất ngày 25/2, kết cục chưa rõ (4.5). Về trừng phạt, thanh toán và Google Play ở Nga: không có nguồn, không suy đoán.

**Phương án khác.** Thuê rà soát pháp lý và văn hoá tại chỗ rồi mới mở; hoặc không bao giờ mở hai nước này.

**Mở khoá.** Chỉ liên quan khi chọn một thị trường vùng Vịnh (cần cả U-23 và U-26 cho thị trường đó).

#### 9.2.6 QĐ-6: Tôn giáo không bao giờ được suy ra hay lưu

**Câu hỏi.** Ứng dụng có được suy ra hay lưu tôn giáo của người dùng không?

**Mặc định.** Không bao giờ. Gói dịp tuỳ chọn (ví dụ lễ tôn giáo) chỉ bật khi người dùng chủ động chọn, chỉ lưu trên máy, không đồng bộ. `faith` chỉ là thuộc tính của quà, không cho người dùng chọn làm sở thích.

**Bằng chứng.** Nguyên tắc nằm ở 6.3.3 ("Thị trường (`MarketProfile`) và mã vùng") và 6.3.6 ("Từ vựng chuẩn: sở thích, loại quà, dịp"). Dữ liệu nháp có 11 dịp mang nhãn nhạy cảm tôn giáo (`religious_sensitive`) và 8 dịp bị hạn chế ở một số nơi (`restricted_in_places`) trong 87 dịp (7.2).

**Phương án khác.** Cho người dùng tự chọn gói dịp tôn giáo trong màn "Dịp của bạn" (U-32); đặt công tắc ở đó chỉ là đề xuất.

**Mở khoá.** Không có việc U riêng. Cần chốt trước khi U-20 sinh gói có phần dịp tuỳ chọn và trước U-32.

#### 9.2.7 QĐ-7: LLM chỉ là tính năng VIP tuỳ chọn

**Câu hỏi.** Có dùng mô hình ngôn ngữ lớn (LLM) để gợi ý quà không?

**Mặc định.** Chưa (8.5.4). Nếu sau này làm: chỉ là tính năng VIP tuỳ chọn, qua callable phía máy chủ có cổng `vip` (`firebase/functions/src/entitlement/gatePolicy.js:5`).

**Thứ tự trước khi có LLM (8.5.4).**

1. U-19 đến U-22 (mô hình gói, bộ sinh gói, repository, khoá ổn định), cộng dữ liệu từ U-20, U-28 (đơn vị tiền) và U-35 (tìm quà).
2. U-06, U-10, U-15 (xếp hạng và giải thích bằng luật), cộng U-03 và U-11 (nhãn "AI").
3. U-29 và danh sách mong muốn cho hai người (9.3).
4. Chỉ sau đó mới đến LLM, như một bước VIP tuỳ chọn.

**Bằng chứng.**

- Lý do của agent (8.5.4): với 20 quà gốc, một bộ sinh mẫu và các nhóm ngân sách VND, LLM xếp lại 20 món chỉ thêm rủi ro riêng tư (Apple 5.1.2(i)), chi phí và việc kiểm quyền mà không thêm giá trị.
- Trong repo: `firebase-ai` được khai (`gradle/libs.versions.toml:113`, `app/build.gradle.kts:202`) nhưng không có lệnh gọi `generativeModel`, `GenerativeModel` hay `FirebaseAI` nào. `gatePolicy.js` mặc định từ chối; chỉ `entitlement.vip === true` cùng `environment === 'Production'` mới là VIP thật; mọi callable mới phải có một hàng trong `GATES` (test `gateCoverage.test.js`); dòng 5 ghi rằng Gemini chưa có callable phía máy chủ và proxy sau này PHẢI đặt cổng `vip`.
- **Mâu thuẫn và khoảng trống.** Phương án (a) của agent (gọi thẳng từ máy qua Firebase AI Logic, chỉ có App Check và hạn mức mặc định 100 RPM mỗi người dùng, không kiểm quyền VIP) mâu thuẫn với `gatePolicy.js:5`. Phương án (b) (callable có cổng `vip`) là chính sách của repo. Lời khuyên "bật bằng Remote Config" không khớp repo: repo không dùng Remote Config, chính sách là cổng phía máy chủ.
- **Điều kiện nếu làm (8.5.3, 8.5.4).** Màn đồng ý riêng, mặc định tắt, thu hồi được (Apple 5.1.2(i)). Chỉ gửi thuộc tính ẩn danh (dịp, nhóm ngân sách, thẻ sở thích, ngôn ngữ hoặc quốc gia; không bao giờ tên, ảnh, nội dung kỷ niệm hay ghi chú). Dùng bậc trả phí hoặc Vertex để dữ liệu không dùng huấn luyện (bậc miễn phí ghi "content used to improve product = Yes", bậc trả phí ghi "No"). Thiết kế lai (từ trí nhớ của agent; L-M): luật hoặc danh sách tuyển chọn lọc ứng viên (ngân sách, dịp, nước, sở thích, trừ quà đã tặng); LLM chỉ xếp lại và viết lý do trên danh sách `giftId` cố định (mảng `giftId` cùng `reason`); lưu đệm theo (dịp, nhóm ngân sách, thẻ, ngôn ngữ) mỗi ngày; hạn mức tháng theo gói; quay lại luật khi gặp lỗi 429 hoặc ngoại tuyến.
- **Chi phí (ƯỚC LƯỢNG, L-M).** Khoảng 0,0008 USD mỗi yêu cầu (800 token vào, 400 token ra trên 3.1 Flash-Lite: 0,0002 cộng 0,0006), tức khoảng 8 USD cho 10.000 yêu cầu. Đây là phép tính của agent, chưa đo; token suy nghĩ có thể nhân đôi hoặc hơn. Độ trễ chưa đo (cần đo p50 và p95 trên máy thật). Giá Flash tăng từ 31/12/2026. Hạn mức App Check mặc định là 100 RPM mỗi người dùng mỗi vùng. Trang quản trị dữ liệu không nêu thời gian lưu hay vùng lưu (chưa kiểm chứng); gói Spark áp dụng "Unpaid Services terms".

**Phương án khác.** Làm sớm hơn nếu có số đo cho thấy gợi ý bằng luật chưa đủ (cần công cụ đo, xem 9.5).

**Mở khoá.** Chặn mọi lệnh gọi LLM; hiện không có việc U nào cần nó. QĐ-10 đi theo QĐ-7.

#### 9.2.8 QĐ-8: Chưa có affiliate

**Câu hỏi.** Có gắn link affiliate (hoa hồng) vào gợi ý quà không?

**Mặc định.** Chưa. `whereToBuy` giữ là chữ thường và không bao giờ để trống (0 trong 142 bản ghi nháp trống). Nút "tìm quà" của U-35 chỉ mở tìm kiếm web của hệ thống với tên món quà, không affiliate, không dữ liệu cá nhân.

**Bằng chứng (năm lý do, 8.4.6).**

1. Chưa có link sản phẩm ổn định: `whereToBuy` là chữ thường, không có URL sản phẩm.
2. Nhãn công bố tiếng Việt và tiếng Anh phải được thiết kế trước (8.4.2).
3. Phạm vi đồng ý UMP, Data Safety và ATT chưa kiểm chứng (repo đã có UMP cho quảng cáo: `gradle/libs.versions.toml:62`, `ads/AdsManagerImpl.kt:112-118`).
4. Số liệu hoa hồng mâu thuẫn giữa các nguồn và không có trang Shopee chính thức (8.4.1).
5. Xếp hạng không được che yếu tố tài trợ.

**Điều cần làm nếu mở.**

- Luật công bố (8.4.2): Mỹ có FTC 16 CFR 255 (sửa đổi 29/06/2023; chưa mở ftc.gov; 16 CFR 465 có hiệu lực 21/10/2024); Anh có ASA (nhãn "Ad" đặt đầu, không dùng "aff"); EU có UCPD (từ trí nhớ).
- Chính sách cửa hàng (8.4.3): Play yêu cầu hàng hoá vật lý nằm ngoài Play Billing, thẻ quà số bên thứ ba chưa rõ, ngoại lệ ở Mỹ ngày 29/10/2025 chưa xác nhận; Apple có 3.1.3(e) và 5.1.2(i).
- Thẻ quà và e-voucher (8.4.4): chỉ deeplink, không lưu giá trị.
- Luật quảng cáo Việt Nam chưa nghiên cứu, nên hỏi luật sư trước mọi thử nghiệm affiliate ở Việt Nam. Số liệu hoa hồng là thứ cấp và mâu thuẫn (Shopee 2,1-7% so với 1-2%; thẻ quà Amazon Mỹ 0%).

**Phương án khác.** Thử nghiệm Shopee về sau (hàng vật lý 100-500 nghìn đồng; đo CTR và hoa hồng thực; kiểm điều khoản chính thức), chỉ sau khi nhãn công bố đã có (8.5.5, hàng 4 và 5).

**Mở khoá.** Chặn mọi nhãn công bố và link affiliate. U-35 không bị chặn vì chỉ mở tìm kiếm web.

#### 9.2.9 QĐ-9: Người bản địa duyệt dữ liệu

**Câu hỏi.** Dữ liệu nháp có cần người bản địa duyệt trước khi hiện cho người dùng không?

**Mặc định.** Có. Phần `low` không tự hiện khi chưa có người bản địa duyệt; điều kiêng kỵ chỉ hiện từ `med` trở lên; điều kiêng `strong` được duyệt trước và đọc bằng tay; thị trường chưa duyệt mang nhãn "bản nháp". Với Việt Nam, người duyệt có thể là chủ app.

**Bằng chứng.** 150 trong 354 bản ghi (42%) có độ tin `low`. 78 bản ghi mang dấu nguồn không phải URL (66 bản ghi chỉ có dấu, không URL, đều `low`; 12 bản ghi có cả URL và dấu). 12 bản ghi `high` chỉ có 1 URL. 64 trong 142 quà là `low`. Tính cả `med`, có 128 trong 142 quà, 71 trong 87 dịp, 54 trong 55 cột mốc và 67 trong 70 điều kiêng chỉ ở mức `low` hoặc `med` (7.3, 7.5).

**Phương án khác.** Duyệt toàn bộ trước khi mở thị trường (chậm hơn, không cần nhãn "bản nháp"); hoặc hiện mọi thứ kèm nhãn "bản nháp" (nhanh hơn, nhưng rủi ro nội dung sai văn hoá).

**Mở khoá.** U-26 (thị trường thứ hai thật sự). Điều kiện này cũng áp cho bản dịch của mọi ngôn ngữ mới (điểm 12 của 4.4).

#### 9.2.10 QĐ-10: Phụ thuộc `firebase-ai` chưa dùng

**Câu hỏi.** Phụ thuộc `firebase-ai` đã khai nhưng chưa có lệnh gọi nào (F-13) xử lý ra sao?

**Mặc định.** Giữ nguyên cho đến khi QĐ-7 chốt. Nếu QĐ-7 chốt là callable phía máy chủ có cổng `vip`, phụ thuộc phía máy khách không cần nữa và có thể gỡ: callable dùng API Gemini phía máy chủ, không phụ thuộc SDK phía máy khách, và lời khuyên "tên mô hình lấy từ Remote Config" cũng không còn cần. Đây là SUY LUẬN (8.5.4). U-03 không gỡ nó. Gỡ là thay đổi Gradle nên cần duyệt riêng.

**Bằng chứng.** `gradle/libs.versions.toml:113` và `app/build.gradle.kts:202` khai `firebase-ai`; không có tệp `.kt`, `.kts`, `.toml`, `.js` hay `.ts` nào dùng `generativeModel`, `GenerativeModel` hoặc `FirebaseAI` (kiểm 2026-10-26).

**Phương án khác.** Gỡ ngay, để bỏ một phụ thuộc không dùng; đổi lại mất đường gọi thẳng từ máy (phương án (a) của QĐ-7), vốn đã trái `gatePolicy.js:5`.

**Mở khoá.** Cùng QĐ-7.

### 9.3 Danh sách mong muốn cho hai người (đề xuất, chưa đánh số)

**Hiện trạng (8.2.1).** "Yêu thích" chỉ là danh sách ưa thích trên máy của mình. Nút "Yêu thích" (`ui/screens/GiftScreen.kt:864-893`, testTag `btn_preferences_notebook`) mở `WishlistNotebookDialog` (`ui/screens/GiftScreen.kt:913`), hộp thoại này chỉ hiện `giftIdeas.filter { it.isFavorited }` (`ui/screens/GiftScreen.kt:920`). Chú thích ở `ui/screens/GiftScreen.kt:909-911` nói ý tưởng quà và mục yêu thích là trạng thái Room cục bộ, không đồng bộ lên Firestore. Cặp đôi trực tuyến chỉ có `mutualInterests` (giao của hai tập sở thích; `data/repository/OnlineCoupleRepository.kt:127-128,259,857`) cộng văn bản "likes" người dùng tự gõ cho người ấy (`likesCsv`, `ui/viewmodel/InLoveViewModel.kt:1274`). Chưa có danh sách món kèm ghi chú, chưa có "ẩn với người ấy", chưa có "giữ chỗ".

**Đề xuất (8.2.2; cỡ L, ƯỚC LƯỢNG; tiền đề U-29 và U-04).** Mỗi món có ghi chú và khoảng giá. Cờ "ẩn với người ấy" giữ bất ngờ. Cờ "giữ chỗ" cho người kia thấy món đã có người nhận mà không biết ai. Mẫu tham khảo: Lovewick (ẩn), Wishpicks (việc giữ chỗ ẩn với người nhận), WishSync (cả hai thấy ý tưởng của nhau); Cupla cũng có wishlist.

**Mặc định.** Làm U-29 (lịch sử quà "đã tặng") và dùng lại `mutualInterests` trước; chỉ làm danh sách chung khi có số đo (tỷ lệ mở màn quà, mức dùng "Yêu thích"). Đồng bộ và riêng tư quyết riêng, đối chiếu với `docs/superpowers/plans/2026-10-21-local-first-media-profile-ux.md` (kế hoạch ấy chọn kỷ niệm chỉ lưu trên máy cộng sao lưu ảnh và manifest). Điểm mới: việc đo cần công cụ đo mà ứng dụng hiện chưa có (9.5).

### 9.4 Điểm duyệt đi kèm từng việc (không đánh số QĐ)

- **(a) Hai danh sách `defaultOn` (7.3; luật ở 5.3).** Luật: một dịp bật mặc định khi `coupleRelevance` từ 2 trở lên, áp dụng cho phần lớn người dùng của thị trường đó và không có cổng tôn giáo hay thể chế. Có 16 dịp đạt ngưỡng nhưng đang tắt, và 5 dịp có nhãn nhạy cảm nhưng đang bật (danh sách mã ở 7.3). Chủ app duyệt hai danh sách này trước khi U-20 sinh gói.
- **(b) U-09, Noel 24/12 bật mặc định (6.2).** Chỉ áp dụng cho cài mới (F05). Noel không phải ngày lễ truyền thống của Việt Nam nhưng là dịp hẹn hò phổ biến ở đô thị; độ tin `low` (4.1). Chủ app xác nhận trước khi bật mặc định. Ảnh hưởng golden: GV-012 (`table-dump-15-entries` thành 16 mục, đổi tên ca; `title-nfc-emoji-prefix` thêm 2 hàng); GV-204 (thêm một hàng Noel vào mỗi ca trong 9 ca `-vi`, 9 ca `-en` không đổi); GV-015 và GV-014 không đổi.
- **(c) U-36, nơi lưu ảnh quà công khai (6.4).** Chủ app chọn nơi lưu. R2 hiện chỉ dành cho ảnh và video riêng của người dùng (DEC-267, `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:284`); các bước Cloudflare phía chủ app theo kế hoạch R2 (`docs/superpowers/plans/2026-10-21-r2-media-provider.md`) được ghi là chưa làm (chưa kiểm lại hôm nay). Điều khoản liên kết trực tiếp ảnh Pexels chưa kiểm; chi phí băng thông cho ảnh công khai chưa ước lượng. Chỉ cần khi QĐ-1 chọn thị trường thứ hai.

### 9.5 Rủi ro chung

Bảng này không lặp lại bảng rủi ro của 6.3.9 (khoá đổi làm mất mục yêu thích, golden iOS ghim hành vi, gói Firestore hỏng, chất lượng dịch, ảnh liên kết trực tiếp, affiliate về sau, kích thước APK, hai bản cài ngày, mất cờ `confidence`).

| Rủi ro | Mức | Chi tiết | Cách giảm |
|---|---|---|---|
| Cây mã chưa commit, dòng trích dẫn có thể lệch | Trung bình | Kiểm 2026-10-27 bằng `git status --short`: 372 mục (126 sửa, 244 chưa theo dõi, 2 xoá, ví dụ `data/media/FirebaseMemoryMediaRepository.kt`; đếm trước khi thêm tài liệu này). Mọi `đường dẫn:dòng` trong tài liệu lấy từ cây này | Trước khi sửa một việc, mở lại tệp và tra theo tên ký hiệu thay vì số dòng |
| Dữ liệu nháp chưa đủ tin cậy để hiện thẳng cho người dùng | Cao | 150 trong 354 bản ghi (42%) là `low`; 78 bản ghi mang dấu nguồn không phải URL (66 chỉ có dấu, đều `low`); 12 bản ghi `high` chỉ có 1 URL; chưa mở mọi URL (7.5) | QĐ-9; đọc tay 15 điều kiêng `strong` trước; nhãn "bản nháp" |
| Quyết định mù vì chưa có phân tích dùng | Trung bình | Ứng dụng không có SDK phân tích (`privacy/AppPrivacyCoordinator.kt:15`), chỉ có Crashlytics (`app/build.gradle.kts:206`; `gradle/libs.versions.toml:44,120,154`). Cột "nhu cầu" của QĐ-1, QĐ-4 và danh sách mong muốn (9.3) cần số đo mà hiện chưa có. Thêm công cụ đo kéo theo quyết định về UMP, Data Safety và đồng ý | Dùng Play Console trước; nếu thêm công cụ đo, coi là một việc riêng cần duyệt |
| Sai ngày lễ âm lịch | Cao | Rằm Giêng 2027 trong repo là 19/2, đúng là 20/2 theo bảng ở 7.6 (U-01). Bảng Việt Nam và Trung Quốc lệch nhau đúng 5 ngày trong 2025-2040 do múi giờ. Hàn Quốc (UTC+9) lệch bảng Việt Nam 10 ngày, chỉ theo thuật toán, chưa mở nguồn Hàn | U-01 và U-17; mặc định múi giờ UTC+7 cho Việt Nam, nhãn "dự kiến ±1 ngày"; nguồn Hàn riêng nếu mở thị trường Hàn |
| Số liệu thị trường là thứ cấp và phần lớn là ước lượng | Trung bình | Điểm ở 8.3.1 là ƯỚC LƯỢNG; chỉ một phần số liệu mang nhãn (H) (ví dụ tỷ lệ Android từ StatCounter, dự báo NRF); số hoa hồng mâu thuẫn; nhiều số Đông Á lấy từ trí nhớ | Giữ nhãn (H), (M), (L) khi trích; kiểm lại trước khi chi tiền cho một thị trường |
| Khoảng trống pháp lý và công bố | Cao nếu mở affiliate hoặc LLM | FTC 16 CFR 255, UCPD và ASA chưa mở nguồn chính thức (UCPD từ trí nhớ); luật quảng cáo Việt Nam chưa nghiên cứu; Apple 5.1.2(i) đòi màn đồng ý riêng, mặc định tắt, thu hồi được | QĐ-7 và QĐ-8 mặc định là "chưa"; hỏi luật sư trước khi mở |
| Chung cây mã với các phiên làm việc khác | Trung bình | Cây đang dùng chung với phiên khác (cùng tệp, cùng Gradle) và còn nhiều thay đổi chưa commit | Báo trước mọi sửa trong `app/src`; chạy test liên quan sau mỗi việc; không commit khi chủ app chưa yêu cầu |
| Làm cả 38 việc cùng lúc | Trung bình | Phạm vi lan rộng; Đợt 2 và Đợt 3 phụ thuộc quyết định chưa chốt (QĐ-1, QĐ-2) | Mặc định: duyệt Đợt 1 trước, theo thứ tự ở 6.1; Đợt 2 chỉ bắt đầu khi QĐ-1 và QĐ-2 đã chốt |

### 9.6 Việc chưa kiểm chứng

| Việc | Vì sao chưa kiểm | Cách kiểm |
|---|---|---|
| Phạm vi UMP và khai Data Safety hiện có | Chỉ đọc mã (`ads/AdsManagerImpl.kt:112-118`, `gradle/libs.versions.toml:62`); chưa đọc khai báo trên Play Console | Mở Play Console (Data Safety) và đối chiếu với luồng UMP trong mã |
| Ngày Seollal và Chuseok của Hàn Quốc | Chỉ có kết quả thuật toán UTC+9; chưa mở nguồn Hàn nào (7.6) | Đối chiếu với lịch chính thức do cơ quan thiên văn Hàn Quốc công bố |
| Việc cấm hoặc hạn chế Valentine ở Ả Rập Xê Út và Kuwait năm 2025-2026 | Chỉ có báo cáo 2008-2009 (4.5) | Rà soát pháp lý và văn hoá tại chỗ (QĐ-5) |
| Lịch Phật lịch hoặc chữ số Thái trên máy Thái | Suy luận từ cài đặt thiết bị; chưa thử trên máy thật | Sau U-07: đặt máy thử sang Thái Lan với lịch Phật lịch và chữ số Thái, lưu rồi đọc lại một ngày kỷ niệm |
| Philippines không cần ngôn ngữ mới và U-24 ít cần hơn cho Philippines | SUY LUẬN từ `ui/util/Localization.kt:29-32` | Xác nhận khi lập kế hoạch U-26 |
| Cách xử lý tiền Philippines (PHP) | 8.3.4 và dữ liệu nháp không nhắc PHP | Xác nhận khi lập kế hoạch U-26, cùng U-28 |
| Chi phí và độ trễ của LLM | Chi phí là phép tính của agent (800 token vào, 400 token ra), chưa đo; độ trễ chưa đo; giá Flash tăng từ 31/12/2026 | Đo p50 và p95 trên máy thật và đọc bảng giá hiện hành (8.5.4) |
| Hoa hồng affiliate | Nguồn thứ cấp và mâu thuẫn; không có trang Shopee chính thức (8.4.1) | Đọc điều khoản chương trình chính thức trước mọi thử nghiệm |
| Luật công bố quảng cáo | FTC 16 CFR 255, UCPD, ASA chưa mở nguồn chính thức (UCPD từ trí nhớ); luật quảng cáo Việt Nam chưa nghiên cứu | Đọc văn bản chính thức hoặc hỏi luật sư trước khi mở affiliate |
| Nội dung về đối thủ (WishSync, tended, GiftList, Amora, Lovewick, Wishpicks, Cupla) | Chưa mở từng trang Google Play hay App Store; agent về đối thủ đã vượt ngân sách tìm kiếm; Amora chỉ có trong `docs/PRODUCT_RESEARCH.md` | Mở từng trang cửa hàng và ghi ngày kiểm |
| Trọng số và điểm thị trường | Ước lượng của agent (8.3.1) | Chủ app chỉnh trọng số và xem thứ tự có đổi không |
| Hành vi ứng dụng | Kết luận lấy từ đọc mã và test hiện có; chưa chạy ứng dụng, giả lập hay thiết bị | Chạy lại trên LDPlayer hoặc máy thật sau từng việc |
| Trích dẫn code ngoài phạm vi lượt đọc lần hai | Lượt đọc lần hai (2.2) chỉ phủ các cụm trích có số dòng; chưa phủ câu khẳng định sự vắng mặt, chỗ nhắc file không kèm số dòng và 27 cụm mới hoặc đã sửa sau lượt đó | Trước khi lập kế hoạch cho một việc U-nn, mở lại tệp được trích và đọc các dòng liên quan |
| Mọi URL nguồn | Không script nào mở mọi URL (7.5): 455 URL, trong đó 196 URL khác nhau | Duyệt mẫu theo độ tin cậy; ưu tiên 15 điều kiêng `strong` và các bản ghi `high` chỉ có 1 URL |
| Số liệu Đông Á | Nhiều số lấy từ trí nhớ, chưa mở nguồn | Kiểm nguồn trước khi trích ra ngoài tài liệu |
| Đường cơ sở lint | Chưa chạy lại hôm nay; các kế hoạch trước ghi 128 đến 131 cảnh báo | Chạy `:app:lintDebug` trước khi bắt đầu Đợt 1 |
| Số ca golden | Do agent báo cáo, chưa đếm | Đếm lại ca trong các tệp golden ở `shared/src/commonTest/resources/golden/` |
| Nga và Ukraine | Nga: không có nguồn về trừng phạt, thanh toán, Google Play (không suy đoán). Ukraine: kết cục dự luật số 9009 (2023) và dự thảo nghị quyết số 15052 (đăng ký 2/3/2026) về ngày 25/2 chưa rõ (4.5) | Tra cứu trạng thái dự luật; nhờ người bản địa duyệt (QĐ-9) |
| Ngày Dia dos Namorados (12/6) | Lấy từ trí nhớ (8.3.4) | Xác nhận trước khi thử nghiệm kín trước 12/6/2027 |
| Độ phủ mỏng | KZ, UA, MX, PE, CL, PL chỉ có 2 đến 3 quà; AE và EG chỉ có điều kiêng; SA và KW có 1 dịp và 2 điều kiêng, không có quà (7.3) | Bổ sung dữ liệu và nguồn khi một thị trường được chọn |

**Bước tiếp theo đề xuất.** Duyệt Đợt 1 hoặc một phần của nó (ví dụ U-01; U-04 cùng U-05; U-06; U-03 cùng U-11; U-07; U-02 cùng U-09). Trả lời QĐ-1 và QĐ-2, hoặc nói "đồng ý mặc định". Để tám quyết định còn lại đến khi việc bị chặn đến lượt. Cho đến lúc đó chưa có mã, dữ liệu seed hay lược đồ nào bị đổi.

## 10. Phụ lục

**Ý chính.** Phụ lục gom bốn thứ: 20 quà gốc đang nằm trong mã (10.1), hai lịch lễ trong mã (10.2), bảng đường dẫn đầy đủ của các file được nhắc tới (10.3) và nơi để dữ liệu thô cùng nguồn (10.4). Các bảng ở 10.1 và 10.2 được trích trực tiếp từ ba file Kotlin (`GiftIdeasSeed.kt`, `VietnameseHolidays.kt`, `WesternHolidays.kt`) và đối chiếu số lượng trước khi ghi: 20 quà, 20 lễ Việt Nam, 13 lễ phương Tây. Mọi số trong hai mục này vì vậy khớp với mã tại thời điểm trích.

### 10.1 Hai mươi quà gốc trong mã

Nguồn: `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt`, danh sách `GiftIdeasSeed.all` (`:76`), xếp đúng thứ tự trong file. Cột "Mức giá lưu" là chuỗi nhóm giá được lưu trong Room (`PriceRange`, `GiftIdeasSeed.kt:69-74`), không phải số tiền.

| # | imageSeed | Tên VI | Tên EN | Nhóm | Thẻ | Dịp gợi ý | Mức giá lưu | Ảnh |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `gift-rose-bouquet` | Bó Hoa Hồng Sáp | Everlasting Rose Bouquet | Quà lãng mạn | Kỷ niệm | Valentine | `200k-500k` | Pexels 30891127 |
| 2 | `gift-candlelit-dinner` | Bữa Tối Nến Lãng Mạn Tự Nấu | Candlelit Home-Cooked Dinner | Địa điểm hẹn hò | Hẹn hò | Kỷ niệm ngày yêu | `<200k` | Pexels 37731521 |
| 3 | `gift-photo-album` | Album Ảnh Kỷ Niệm In Thủ Công | Handcrafted Memory Photo Album | Kỷ vật Handmade | Kỷ niệm | Kỷ niệm ngày yêu | `200k-500k` | Pexels 8057039 |
| 4 | `gift-couple-bracelet` | Vòng Tay Khắc Tên Đôi | Couple's Engraved Bracelet Set | Trang sức & Nước hoa | Đôi | 20/10 | `200k-500k` | Pexels 38591770 |
| 5 | `gift-camping-trip` | Chuyến Dã Ngoại Cắm Trại Cuối Tuần | Weekend Camping Getaway | Địa điểm hẹn hò | Phiêu lưu | Kỷ niệm ngày yêu | `500k-1tr` | Pexels 6324165 |
| 6 | `gift-perfume-set` | Nước Hoa Đôi Cặp | His & Hers Perfume Set | Trang sức & Nước hoa | Đôi | Sinh nhật | `>1tr` | Pexels 18656841 |
| 7 | `gift-board-game` | Bộ Board Game Cho Cặp Đôi | Couple's Board Game Night Set | Bất ngờ bí mật | Tại nhà | Kỷ niệm ngày yêu | `<200k` | Pexels 8111328 |
| 8 | `gift-couple-watch` | Đồng Hồ Đôi Phong Cách Tối Giản | Minimalist Couple Watch Set | Trang sức & Nước hoa | Đôi | Kỷ niệm ngày yêu | `>1tr` | Pexels 1697570 |
| 9 | `gift-photo-lamp` | Đèn Ngủ Khắc Ảnh 3D | 3D Photo Crystal Night Lamp | Kỷ vật Handmade | Kỷ niệm | Kỷ niệm ngày yêu | `200k-500k` | không có (dùng picsum) |
| 10 | `gift-movie-tickets` | Vé Xem Phim Suất Chiếu Sớm | Early Movie Premiere Tickets | Địa điểm hẹn hò | Hẹn hò | Hẹn hò cuối tuần | `<200k` | Pexels 8261585 |
| 11 | `gift-love-journal` | Sổ Tay Viết Cho Nhau Mỗi Ngày | Daily Love Notes Journal | Kỷ vật Handmade | Tại nhà | Kỷ niệm ngày yêu | `<200k` | Pexels 4639031 |
| 12 | `gift-teddy-bear` | Gấu Bông Ôm Khổng Lồ | Giant Hug Teddy Bear | Quà lãng mạn | Dễ thương | Sinh nhật | `500k-1tr` | Pexels 19849143 |
| 13 | `gift-cooking-class` | Lớp Học Nấu Ăn Cho Cặp Đôi | Couple's Cooking Class | Địa điểm hẹn hò | Phiêu lưu | Kỷ niệm ngày yêu | `500k-1tr` | Pexels 6248808 |
| 14 | `gift-couple-portrait` | Tranh Chân Dung Đôi Vẽ Tay | Custom Hand-Drawn Couple Portrait | Kỷ vật Handmade | Kỷ niệm | Kỷ niệm ngày yêu | `500k-1tr` | Pexels 22690792 |
| 15 | `gift-couple-spa` | Voucher Spa Đôi Thư Giãn | Couple's Spa Retreat Voucher | Quà lãng mạn | Thư giãn | 8/3 | `>1tr` | Pexels 19695981 |
| 16 | `gift-desk-plant` | Cây Cảnh Mini Để Bàn | Mini Desk Plant | Bất ngờ bí mật | Nhẹ nhàng | Bất kỳ dịp nào | `<200k` | Pexels 30682994 |
| 17 | `gift-weekend-trip` | Chuyến Du Lịch Ngắn Ngày Hai Người | Short Weekend Trip for Two | Địa điểm hẹn hò | Phiêu lưu | Kỷ niệm ngày yêu | `>1tr` | Pexels 7446972 |
| 18 | `gift-couple-mugs` | Bộ Cốc Đôi In Tên Theo Yêu Cầu | Personalized Couple Mug Set | Kỷ vật Handmade | Đôi | Giáng Sinh | `<200k` | Pexels 3867002 |
| 19 | `gift-hot-air-balloon` | Trải Nghiệm Khinh Khí Cầu Ngắm Bình Minh | Sunrise Hot Air Balloon Experience | Địa điểm hẹn hò | Phiêu lưu | Kỷ niệm 1 năm | `>1tr` | Pexels 9873509 |
| 20 | `gift-playlist` | Playlist Nhạc Riêng Tặng Người Ấy | Custom Playlist Made For Them | Bất ngờ bí mật | Nhẹ nhàng | Bất kỳ dịp nào | `<200k` | Pexels 7054718 |

Tổng hợp từ bảng trên:

- **Nhóm:** Địa điểm hẹn hò 6, Kỷ vật Handmade 5, Quà lãng mạn 3, Trang sức & Nước hoa 3, Bất ngờ bí mật 3.
- **Thẻ:** Kỷ niệm 4, Đôi 4, Phiêu lưu 4, Hẹn hò 2, Tại nhà 2, Nhẹ nhàng 2, Dễ thương 1, Thư giãn 1.
- **Dịp gợi ý:** Kỷ niệm ngày yêu 10, Sinh nhật 2, Bất kỳ dịp nào 2, 6 mục mỗi mục 1 quà (Valentine, 20/10, Hẹn hò cuối tuần, 8/3, Giáng Sinh, Kỷ niệm 1 năm).
- **Mức giá lưu:** `<200k` 7, `200k-500k` 4, `500k-1tr` 4, `>1tr` 5.
- **Ảnh:** 19 quà có ảnh Pexels. Quà #9 (đèn ngủ khắc ảnh 3D) không có `curatedImageUrl` nên dùng ảnh giữ chỗ `https://picsum.photos/seed/gift-photo-lamp/800/600` (`data/seed/GiftIdeasSeed.kt:50`). KDoc của file (`data/seed/GiftIdeasSeed.kt:20-25`) ghi 19 địa chỉ ảnh đã được kiểm bằng cách tải (HTTP 200, `image/jpeg`) ngày 2026-10-26.

Điều bảng cho thấy:

- Chỉ có 9 giá trị `suggestedOccasion` khác nhau. Không quà nào gắn với Tết, với mốc 100 ngày hay với một dịp dành riêng cho nam; hai quà gắn với ngày của phụ nữ (8/3 là #15, 20/10 là #4).
- Một nửa số quà (10/20) ghi "Kỷ niệm ngày yêu", trong khi mỗi dịp cố định (Valentine, 8/3, 20/10, Giáng Sinh) chỉ có đúng 1 quà. Bảng dịp cố định của use case là G-05 ở 3.1.
- Không quà nào có khoá sở thích: `GiftSeed` không có trường này, `toEntity` không đặt `targetInterests`, nên cả 20 hàng Room có cột này rỗng (`data/model/Entities.kt:47`), trong khi `GiftRanker` đọc đúng cột đó để chấm điểm (`domain/GiftRanker.kt:9`) (U-06).
- Giá là chuỗi nhóm, không có số tiền và không có đơn vị tiền (G-01, U-22).

### 10.2 Lịch lễ trong mã

Nguồn: `app/src/main/java/com/example/data/seed/VietnameseHolidays.kt` (20 lễ: 15 cố định, 5 âm lịch) và `app/src/main/java/com/example/data/seed/WesternHolidays.kt` (13 lễ: 9 cố định, 4 theo quy tắc). Các bảng xếp theo ngày dương lịch; thứ tự trong file khác (ở file phương Tây, 3/10 đứng trước 4/7). Tiêu đề lưu trong app là biểu tượng cộng tên lễ; các bảng bỏ biểu tượng cho gọn. Cột "Nhóm quà" là giá trị `suggestedGiftCategory` đúng như trong mã.

**Lễ cố định của Việt Nam** (15 lễ, 5 liên quan cặp đôi)

| Ngày (d/M) | Tên trong app (VI) | Tên EN | Nhóm quà | Cặp đôi |
| --- | --- | --- | --- | --- |
| 1/1 | Tết Dương Lịch | New Year's Day | Trải Nghiệm | không |
| 14/2 | Lễ Tình Nhân Valentine | Valentine's Day | Lãng Mạn | có |
| 8/3 | Quốc Tế Phụ Nữ | International Women's Day | Lãng Mạn | có |
| 20/3 | Ngày Quốc Tế Hạnh Phúc | International Day of Happiness | Trải Nghiệm | có |
| 21/4 | Ngày Sách Việt Nam | Vietnam Book Day | Ý Nghĩa | không |
| 30/4 | Giải Phóng Miền Nam | Reunification Day | Trải Nghiệm | không |
| 1/5 | Quốc Tế Lao Động | International Workers' Day | Trải Nghiệm | không |
| 15/5 | Ngày Quốc Tế Gia Đình | International Day of Families | Ý Nghĩa | không |
| 1/6 | Quốc Tế Thiếu Nhi | International Children's Day | Ý Nghĩa | không |
| 28/6 | Ngày Gia Đình Việt Nam | Vietnamese Family Day | Ý Nghĩa | có |
| 27/7 | Ngày Thương Binh Liệt Sĩ | War Invalids and Martyrs Day | Ý Nghĩa | không |
| 2/9 | Quốc Khánh | National Day | Trải Nghiệm | không |
| 20/10 | Ngày Phụ Nữ Việt Nam | Vietnamese Women's Day | Lãng Mạn | có |
| 20/11 | Ngày Nhà Giáo Việt Nam | Vietnamese Teachers' Day | Ý Nghĩa | không |
| 22/12 | Ngày Thành Lập QĐND Việt Nam | Vietnam People's Army Day | Ý Nghĩa | không |

**Lễ âm lịch của Việt Nam** (5 lễ, 15 ngày trong bảng, 2 lễ liên quan cặp đôi)

| Lễ | Tên EN | Ngày âm | Cặp đôi | Nhóm quà | Năm trong bảng | Ngày cuối trong bảng |
| --- | --- | --- | --- | --- | --- | --- |
| Tết Nguyên Đán | Lunar New Year (Tết) | 1/1 | có | Ý Nghĩa | 2025, 2026, 2027, 2028 | 2028-01-26 |
| Rằm Tháng Giêng | First Full Moon Festival | 15/1 | không | Ý Nghĩa | 2026, 2027 | 2027-02-19 |
| Giỗ Tổ Hùng Vương | Hùng Kings' Commemoration Day | 10/3 | không | Ý Nghĩa | 2025, 2026, 2027, 2028 | 2028-04-04 |
| Lễ Vu Lan | Vu Lan (Ancestors' Day) | 15/7 | không | Ý Nghĩa | 2025, 2026 | 2026-08-27 |
| Tết Trung Thu | Mid-Autumn Festival | 15/8 | có | Trải Nghiệm | 2025, 2026, 2027 | 2027-09-15 |

**Đối chiếu 15 ngày âm lịch trong mã với thuật toán** (Hồ Ngọc Đức, múi giờ UTC+7; bảng đầy đủ 2025-2040 ở 7.6)

| Lễ | Năm | Ngày trong mã | Thuật toán UTC+7 | Kết quả |
| --- | ---: | --- | --- | --- |
| Tết Nguyên Đán | 2025 | 2025-01-29 | 2025-01-29 | khớp |
| Tết Nguyên Đán | 2026 | 2026-02-17 | 2026-02-17 | khớp |
| Tết Nguyên Đán | 2027 | 2027-02-06 | 2027-02-06 | khớp |
| Tết Nguyên Đán | 2028 | 2028-01-26 | 2028-01-26 | khớp |
| Rằm Tháng Giêng | 2026 | 2026-03-03 | 2026-03-03 | khớp |
| Rằm Tháng Giêng | 2027 | 2027-02-19 | 2027-02-20 | lệch 1 ngày |
| Giỗ Tổ Hùng Vương | 2025 | 2025-04-07 | 2025-04-07 | khớp |
| Giỗ Tổ Hùng Vương | 2026 | 2026-04-26 | 2026-04-26 | khớp |
| Giỗ Tổ Hùng Vương | 2027 | 2027-04-16 | 2027-04-16 | khớp |
| Giỗ Tổ Hùng Vương | 2028 | 2028-04-04 | 2028-04-04 | khớp |
| Lễ Vu Lan | 2025 | 2025-09-06 | 2025-09-06 | khớp |
| Lễ Vu Lan | 2026 | 2026-08-27 | 2026-08-27 | khớp |
| Tết Trung Thu | 2025 | 2025-10-06 | 2025-10-06 | khớp |
| Tết Trung Thu | 2026 | 2026-09-25 | 2026-09-25 | khớp |
| Tết Trung Thu | 2027 | 2027-09-15 | 2027-09-15 | khớp |

**Lễ cố định của phương Tây** (9 lễ, 5 liên quan cặp đôi)

| Ngày (d/M) | Tên VI | Tên EN (tên app lưu) | Nhóm quà | Cặp đôi |
| --- | --- | --- | --- | --- |
| 1/1 | Tết Dương Lịch | New Year's Day | Experience | không |
| 14/2 | Lễ Tình Nhân | Valentine's Day | Romantic | có |
| 17/3 | Ngày Thánh Patrick | St. Patrick's Day | Experience | không |
| 4/7 | Ngày Quốc Khánh Mỹ | Independence Day | Experience | không |
| 3/10 | Ngày Bạn Trai Quốc Gia | National Boyfriend Day | Romantic | có |
| 31/10 | Lễ Hội Ma Halloween | Halloween | Experience | không |
| 24/12 | Đêm Giáng Sinh | Christmas Eve | Romantic | có |
| 25/12 | Giáng Sinh | Christmas Day | Meaningful | có |
| 31/12 | Giao Thừa Dương Lịch | New Year's Eve | Romantic | có |

**Lễ phương Tây tính theo quy tắc** (4 lễ, 1 liên quan cặp đôi; ví dụ năm 2026 do script tính lại)

| Lễ (tên VI) | Tên EN | Quy tắc | Ví dụ 2026 | Cặp đôi | Nhóm quà |
| --- | --- | --- | --- | --- | --- |
| Ngày Của Mẹ | Mother's Day | Chủ nhật lần thứ 2 của tháng 5 | 2026-05-10 | không | Meaningful |
| Ngày Của Cha | Father's Day | Chủ nhật lần thứ 3 của tháng 6 | 2026-06-21 | không | Meaningful |
| Ngày Ngọt Ngào (Sweetest Day) | Sweetest Day | Thứ bảy lần thứ 3 của tháng 10 | 2026-10-17 | có | Romantic |
| Lễ Tạ Ơn | Thanksgiving | Thứ năm lần thứ 4 của tháng 11 | 2026-11-26 | không | Experience |

**Tổng số lễ**

| Danh sách | Cố định | Âm lịch hoặc theo quy tắc | Tổng | Liên quan cặp đôi |
| --- | ---: | ---: | ---: | ---: |
| Việt Nam (dùng khi giao diện là VI) | 15 | 5 | 20 | 7 |
| Phương Tây (dùng khi giao diện là EN) | 9 | 4 | 13 | 6 |

**Ghi chú về cách lịch lễ đi vào app**

- **Seed một lần, làm mới sau đó.** `data/repository/OfflineSeeder.kt` chỉ seed lịch lễ khi bảng `anniversary_dates` còn trống (`:175-177`), rồi gọi `HolidayDates.refreshExisting` để dời ngày cho các hàng đã có (`:179`). Giao diện VI dùng danh sách Việt Nam, giao diện còn lại (EN) dùng danh sách phương Tây với tên EN (`OfflineSeeder.kt:211-228`). Mỗi lễ thành một hàng `type = "CUSTOM"` (`OfflineSeeder.kt:198`) có tiêu đề `"${h.emoji} ${h.titleVi}"` hoặc `"${h.emoji} ${h.titleEn}"` (`:213,217,222,225`).
- **Nhắc mặc định bật cho mọi lễ.** Seeder không đặt cờ nhắc (`data/repository/OfflineSeeder.kt:193-203`): hàng nhận giá trị mặc định của entity, bật nhắc và nhắc trước 3 ngày (`data/model/Entities.kt:166-167`). Trong mã Kotlin, cờ `isCoupleRelevant` chỉ xuất hiện ở hai file lịch lễ, mô hình `domain/content/ContentModels.kt` (`:53`) và test `golden/HolidayGoldenTest.kt`; ngoài Kotlin, cờ còn nằm trong `app/src/main/assets/content/holiday_calendar.json` và ba fixture golden GV-012, GV-013, GV-014. Không đoạn mã nào đọc cờ này để quyết định bật nhắc, nên mọi lễ của danh sách tương ứng (20 lễ Việt Nam hoặc 13 lễ phương Tây) đều có nhắc bật (F04, U-02).
- **Làm mới theo chuỗi tiêu đề.** `HolidayDates.refreshExisting` ghép hàng đã lưu với bảng lễ chỉ bằng chuỗi tiêu đề (`"${emoji} ${titleVi}"` hoặc `"${emoji} ${titleEn}"`, `data/seed/HolidayDates.kt:32-34,42-44`), không lọc theo `type`. Hệ quả: hàng bị người dùng đổi tên không còn được dời ngày, và một sự kiện tự tạo có tiêu đề trùng đúng chuỗi đó (kể cả biểu tượng) sẽ bị ghi đè ngày (F20, U-27, U-17).
- **Hết ngày thì mất lễ.** Bảng âm lịch là bảng tra chỉ gồm các năm đã kiểm (KDoc `data/seed/VietnameseHolidays.kt:13-20`, tìm web 2026-09-24). `HolidayDates.nextLunar` lấy ngày sớm nhất không trước hôm nay và trả về null khi hết (`data/seed/HolidayDates.kt:16-17`). Ngày cuối của từng lễ: Lễ Vu Lan sau 2026-08-27; Rằm Tháng Giêng sau 2027-02-19; Tết Trung Thu sau 2027-09-15; Tết Nguyên Đán sau 2028-01-26; Giỗ Tổ Hùng Vương sau 2028-04-04 (Rằm tháng Giêng tính theo ngày đang sai, xem ghi chú kế). Khi hết, seed mới bỏ lễ đó (`data/repository/OfflineSeeder.kt:215-218`, `mapNotNull`), còn hàng đã có được giữ nguyên ngày cũ và đặt `isAnnual = false` (`data/seed/HolidayDates.kt:36-39`) (F06, U-17).
- **Một ngày sai.** Trong chính bảng này, Tết 2027 là 2027-02-06 nên mồng 15 tháng Giêng phải là 2027-02-20 (14 ngày sau, như năm 2026: 2026-02-17 và 2026-03-03), nhưng bảng ghi 2027-02-19 (`data/seed/VietnameseHolidays.kt:86`). Thuật toán UTC+7 cho 2027-02-20 và khớp 14 trong 15 ngày. Cùng giá trị sai nằm ở `app/src/main/assets/content/holiday_calendar.json:163`. Xem 7.6 và U-01.
- **`suggestedGiftCategory` không nối vào đâu.** Giá trị của trường (Việt Nam: `Lãng Mạn`, `Trải Nghiệm`, `Ý Nghĩa`; phương Tây: `Romantic`, `Experience`, `Meaningful`) không trùng khoá nhóm nào trong 20 quà gốc (10.1). Tìm trong `app/src` và `shared/src`: tên trường chỉ xuất hiện ở bốn file Kotlin (hai file lịch lễ, khai báo mô hình `domain/content/ContentModels.kt` và test `golden/HolidayGoldenTest.kt`) và bốn file JSON (`assets/content/holiday_calendar.json` cùng ba fixture golden GV-012, GV-013, GV-014). Không đoạn mã nào dùng giá trị này để chọn quà, và không file Kotlin nào gọi `holiday_calendar.json` theo tên (F-17).
- **Không có trong danh sách Việt Nam:** Giáng Sinh (U-09), White Day, Ngày của Mẹ, Ngày của Cha và Thất Tịch. **Không có trong danh sách phương Tây:** White Day, Pepero Day, 520, Dia dos Namorados (12/6), Karwa Chauth và Phục Sinh (F08). Phần còn lại theo từng gói thị trường ở 6.3 và U-26.
- **Phục Sinh bị bỏ có chủ đích.** KDoc của `data/seed/WesternHolidays.kt:18-20` ghi lễ này phụ thuộc thuật toán Computus chưa kiểm chứng được bằng test, nên để lại cho lần sau.

### 10.3 Đường dẫn đầy đủ của các file được nhắc tới

Các mục trước ghi đường dẫn rút gọn theo quy ước ở đầu mục 3 (tính từ `app/src/main/java/com/example/` nếu không nói khác). Bảng dưới liệt kê từng file được nhắc tới trong tài liệu, kèm đường dẫn đầy đủ từ gốc repo, số lần nhắc và cột "Trùng tên" đánh dấu tên file có nhiều bản trong repo (khi đó phải đọc đường dẫn để biết đang nói file nào).

| Tên rút gọn | Đường dẫn đầy đủ (từ gốc repo) | Số lần nhắc | Trùng tên |
|---|---|---:|---|
| `app/build.gradle.kts` | `app/build.gradle.kts` | 6 | có (11 bản) |
| `14.json`, `app/schemas/com.example.data.db.AppDatabase/14.json` | `app/schemas/com.example.data.db.AppDatabase/14.json` | 9 | — |
| `AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` | 9 | có (11 bản) |
| `app/src/main/assets/content/gift_catalog.json` | `app/src/main/assets/content/gift_catalog.json` | 2 | — |
| `holiday_calendar.json`, `assets/content/holiday_calendar.json`, `app/src/main/assets/content/holiday_calendar.json` | `app/src/main/assets/content/holiday_calendar.json` | 12 | — |
| `MainActivity.kt` | `app/src/main/java/com/example/MainActivity.kt` | 2 | có (2 bản) |
| `ads/AdsManagerImpl.kt` | `app/src/main/java/com/example/ads/AdsManagerImpl.kt` | 5 | — |
| `alarm/AlarmNotificationScheduler.kt`, `AlarmNotificationScheduler.kt` | `app/src/main/java/com/example/alarm/AlarmNotificationScheduler.kt` | 11 | — |
| `alarm/AndroidReminderScheduler.kt` | `app/src/main/java/com/example/alarm/AndroidReminderScheduler.kt` | 3 | — |
| `alarm/BootCompletedReceiver.kt` | `app/src/main/java/com/example/alarm/BootCompletedReceiver.kt` | 6 | — |
| `alarm/LoveAnniversaryMilestoneScheduler.kt`, `LoveAnniversaryMilestoneScheduler.kt` | `app/src/main/java/com/example/alarm/LoveAnniversaryMilestoneScheduler.kt` | 17 | — |
| `alarm/ReminderAlarmReceiver.kt` | `app/src/main/java/com/example/alarm/ReminderAlarmReceiver.kt` | 8 | — |
| `alarm/plan/LoveMilestoneRules.kt`, `LoveMilestoneRules.kt` | `app/src/main/java/com/example/alarm/plan/LoveMilestoneRules.kt` | 24 | — |
| `alarm/plan/ReminderScheduler.kt` | `app/src/main/java/com/example/alarm/plan/ReminderScheduler.kt` | 1 | — |
| `alarm/plan/ReminderTexts.kt`, `ReminderTexts.kt` | `app/src/main/java/com/example/alarm/plan/ReminderTexts.kt` | 4 | — |
| `alarm/plan/fakes/FakeReminderScheduler.kt` | `app/src/main/java/com/example/alarm/plan/fakes/FakeReminderScheduler.kt` | 1 | — |
| `billing/BillingManager.kt` | `app/src/main/java/com/example/billing/BillingManager.kt` | 1 | — |
| `data/db/AccountDataVault.kt` | `app/src/main/java/com/example/data/db/AccountDataVault.kt` | 9 | — |
| `data/db/InLoveDao.kt`, `InLoveDao.kt` | `app/src/main/java/com/example/data/db/InLoveDao.kt` | 8 | — |
| `data/db/Migrations.kt`, `Migrations.kt` | `app/src/main/java/com/example/data/db/Migrations.kt` | 10 | — |
| `data/model/Entities.kt`, `Entities.kt` | `app/src/main/java/com/example/data/model/Entities.kt` | 27 | — |
| `data/repository/CloudPresetFetcher.kt`, `CloudPresetFetcher.kt` | `app/src/main/java/com/example/data/repository/CloudPresetFetcher.kt` | 7 | — |
| `data/repository/GiftSuggestionGenerator.kt`, `GiftSuggestionGenerator.kt` | `app/src/main/java/com/example/data/repository/GiftSuggestionGenerator.kt` | 9 | — |
| `data/repository/InLoveRepository.kt` | `app/src/main/java/com/example/data/repository/InLoveRepository.kt` | 6 | — |
| `data/repository/OfflineSeeder.kt`, `OfflineSeeder.kt` | `app/src/main/java/com/example/data/repository/OfflineSeeder.kt` | 38 | — |
| `data/repository/OnlineCoupleRepository.kt`, `OnlineCoupleRepository.kt` | `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` | 11 | — |
| `data/seed/GiftIdeasSeed.kt`, `GiftIdeasSeed.kt`, `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt` | `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt` | 27 | — |
| `data/seed/HolidayDates.kt` | `app/src/main/java/com/example/data/seed/HolidayDates.kt` | 11 | — |
| `data/seed/VietnameseHolidays.kt`, `VietnameseHolidays.kt`, `app/src/main/java/com/example/data/seed/VietnameseHolidays.kt` | `app/src/main/java/com/example/data/seed/VietnameseHolidays.kt` | 23 | — |
| `data/seed/WesternHolidays.kt`, `WesternHolidays.kt`, `app/src/main/java/com/example/data/seed/WesternHolidays.kt` | `app/src/main/java/com/example/data/seed/WesternHolidays.kt` | 10 | — |
| `data/sync/AnniversarySyncAdapter.kt` | `app/src/main/java/com/example/data/sync/AnniversarySyncAdapter.kt` | 4 | — |
| `data/sync/ProfileProjection.kt` | `app/src/main/java/com/example/data/sync/ProfileProjection.kt` | 1 | — |
| `domain/DataResidencyPolicy.kt` | `app/src/main/java/com/example/domain/DataResidencyPolicy.kt` | 1 | — |
| `domain/GiftRanker.kt`, `GiftRanker.kt` | `app/src/main/java/com/example/domain/GiftRanker.kt` | 14 | — |
| `domain/content/ContentModels.kt`, `ContentModels.kt` | `app/src/main/java/com/example/domain/content/ContentModels.kt` | 7 | — |
| `domain/content/ContentRepository.kt`, `ContentRepository.kt` | `app/src/main/java/com/example/domain/content/ContentRepository.kt` | 6 | — |
| `domain/content/GiftCatalog.kt`, `GiftCatalog.kt` | `app/src/main/java/com/example/domain/content/GiftCatalog.kt` | 11 | — |
| `GetPersonalizedGiftSuggestionsUseCase.kt`, `domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt` | `app/src/main/java/com/example/domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt` | 31 | — |
| `platform/SharedPrefsAppSettings.kt` | `app/src/main/java/com/example/platform/SharedPrefsAppSettings.kt` | 2 | — |
| `privacy/AppPrivacyCoordinator.kt` | `app/src/main/java/com/example/privacy/AppPrivacyCoordinator.kt` | 4 | — |
| `ui/components/AnniversaryMemoriesWidget.kt` | `app/src/main/java/com/example/ui/components/AnniversaryMemoriesWidget.kt` | 2 | — |
| `ui/components/LoveDaysCalculatorWidget.kt` | `app/src/main/java/com/example/ui/components/LoveDaysCalculatorWidget.kt` | 2 | — |
| `ui/components/Material3DatePicker.kt`, `Material3DatePicker.kt` | `app/src/main/java/com/example/ui/components/Material3DatePicker.kt` | 5 | có (2 bản) |
| `ui/screens/AccountSecurityDialogs.kt` | `app/src/main/java/com/example/ui/screens/AccountSecurityDialogs.kt` | 2 | — |
| `ui/screens/CalendarScreen.kt`, `CalendarScreen.kt` | `app/src/main/java/com/example/ui/screens/CalendarScreen.kt` | 13 | — |
| `ui/screens/Dialogs.kt`, `Dialogs.kt` | `app/src/main/java/com/example/ui/screens/Dialogs.kt` | 21 | — |
| `ui/screens/GiftScreen.kt`, `GiftScreen.kt` | `app/src/main/java/com/example/ui/screens/GiftScreen.kt` | 40 | — |
| `ui/screens/MemoriesGridScreen.kt` | `app/src/main/java/com/example/ui/screens/MemoriesGridScreen.kt` | 3 | — |
| `ui/screens/PairingScreen.kt` | `app/src/main/java/com/example/ui/screens/PairingScreen.kt` | 3 | — |
| `ui/screens/SettingsScreen.kt` | `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | 3 | — |
| `ui/theme/Type.kt` | `app/src/main/java/com/example/ui/theme/Type.kt` | 3 | có (2 bản) |
| `ui/util/AppStrings.kt` | `app/src/main/java/com/example/ui/util/AppStrings.kt` | 5 | — |
| `ui/util/AuthSecurityManager.kt` | `app/src/main/java/com/example/ui/util/AuthSecurityManager.kt` | 1 | — |
| `ui/util/Localization.kt`, `Localization.kt` | `app/src/main/java/com/example/ui/util/Localization.kt` | 23 | — |
| `ui/util/ProfileUtils.kt`, `ProfileUtils.kt` | `app/src/main/java/com/example/ui/util/ProfileUtils.kt` | 10 | — |
| `ui/viewmodel/InLoveViewModel.kt`, `InLoveViewModel.kt` | `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` | 40 | — |
| `res/values-vi/strings.xml` | `app/src/main/res/values-vi/strings.xml` | 1 | có (4 bản) |
| `res/values/strings.xml` | `app/src/main/res/values/strings.xml` | 7 | có (4 bản) |
| `CloudEnrichmentDataTest.kt` | `app/src/test/java/com/example/CloudEnrichmentDataTest.kt` | 9 | — |
| `alarm/ReminderSchedulingTest.kt` | `app/src/test/java/com/example/alarm/ReminderSchedulingTest.kt` | 2 | — |
| `alarm/plan/LoveMilestoneRulesTest.kt` | `app/src/test/java/com/example/alarm/plan/LoveMilestoneRulesTest.kt` | 5 | — |
| `EntitlementRepositoryTest.kt` | `app/src/test/java/com/example/billing/EntitlementRepositoryTest.kt` | 1 | — |
| `VipEntitlementResolverTest.kt` | `app/src/test/java/com/example/billing/VipEntitlementResolverTest.kt` | 1 | — |
| `data/db/Migration13To14Test.kt` | `app/src/test/java/com/example/data/db/Migration13To14Test.kt` | 3 | — |
| `data/media/MemoryRestoreTest.kt` | `app/src/test/java/com/example/data/media/MemoryRestoreTest.kt` | 1 | — |
| `data/repository/OnlineCoupleProfileSlotTest.kt` | `app/src/test/java/com/example/data/repository/OnlineCoupleProfileSlotTest.kt` | 3 | — |
| `data/repository/PresetDedupeTest.kt` | `app/src/test/java/com/example/data/repository/PresetDedupeTest.kt` | 1 | — |
| `domain/content/GiftCatalogTest.kt` | `app/src/test/java/com/example/domain/content/GiftCatalogTest.kt` | 2 | — |
| `app/src/test/java/com/example/golden/GoldenWriter.kt`, `golden/GoldenWriter.kt` | `app/src/test/java/com/example/golden/GoldenWriter.kt` | 2 | — |
| `golden/HolidayGoldenTest.kt` | `app/src/test/java/com/example/golden/HolidayGoldenTest.kt` | 5 | — |
| `golden/HolidayRefreshGoldenTest.kt` | `app/src/test/java/com/example/golden/HolidayRefreshGoldenTest.kt` | 1 | — |
| `golden/HolidaySeedGoldenTest.kt` | `app/src/test/java/com/example/golden/HolidaySeedGoldenTest.kt` | 1 | — |
| `golden/OutboxGoldenTest.kt` | `app/src/test/java/com/example/golden/OutboxGoldenTest.kt` | 1 | — |
| `golden/PersonalizedGiftGoldenTest.kt` | `app/src/test/java/com/example/golden/PersonalizedGiftGoldenTest.kt` | 1 | — |
| `ui/screens/GiftBudgetRegionControlsTest.kt` | `app/src/test/java/com/example/ui/screens/GiftBudgetRegionControlsTest.kt` | 2 | — |
| `ui/testing/NoNetworkImagesRule.kt` | `app/src/test/java/com/example/ui/testing/NoNetworkImagesRule.kt` | 2 | — |
| `docs/PRODUCT_RESEARCH.md` | `docs/PRODUCT_RESEARCH.md` | 7 | — |
| `docs/research/data/gift-occasion-draft.json`, `gift-occasion-draft.json` | `docs/research/data/gift-occasion-draft.json` | 11 | — |
| `docs/research/data/lunar-2025-2040.json`, `lunar-2025-2040.json` | `docs/research/data/lunar-2025-2040.json` | 4 | — |
| `lunar_reference.py`, `docs/research/data/lunar_reference.py` | `docs/research/data/lunar_reference.py` | 6 | — |
| `sources.md`, `docs/research/data/sources.md` | `docs/research/data/sources.md` | 4 | — |
| `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md` | `docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md` | 9 | có (2 bản) |
| `docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md` | `docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md` | 1 | — |
| `docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md` | `docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md` | 7 | — |
| `docs/superpowers/plans/2026-10-21-local-first-media-profile-ux.md` | `docs/superpowers/plans/2026-10-21-local-first-media-profile-ux.md` | 2 | — |
| `docs/superpowers/plans/2026-10-21-r2-media-provider.md` | `docs/superpowers/plans/2026-10-21-r2-media-provider.md` | 2 | — |
| `firebase/functions/src/entitlement/gatePolicy.js`, `gatePolicy.js` | `firebase/functions/src/entitlement/gatePolicy.js` | 12 | — |
| `gateCoverage.test.js` | `firebase/functions/test/entitlement/gateCoverage.test.js` | 2 | — |
| `firestore.rules` | `firestore.rules` | 13 | — |
| `gradle/libs.versions.toml` | `gradle/libs.versions.toml` | 7 | có (3 bản) |
| `scripts/rules-tests/system-collections.rules.test.js` | `scripts/rules-tests/system-collections.rules.test.js` | 4 | — |
| `scripts/seed_firestore.js` | `scripts/seed_firestore.js` | 16 | — |
| `spike/SpikeDatabase.kt` | `shared/src/commonMain/kotlin/com/example/spike/SpikeDatabase.kt` | 3 | — |

Số lần nhắc đếm các cụm có tên file trong ngoặc ngược (có hoặc không kèm số dòng) ở các mục viết tay, không tính bảng này và các bảng 1.7 và 6.6 do script sinh ra; cụm chỉ có số dòng như `:175-177` không tính. Bốn file dữ liệu của tài liệu nằm trong `docs/research/data/` (10.4).

**Cụm nêu tên file không có trong cây làm việc hiện tại**

| Tên | Số lần nhắc | Ghi chú |
|---|---:|---|
| `VN.json` | 3 | tên gói đề xuất (6.3.3 và 6.3.4), chưa có trong cây |
| `INTL.json` | 3 | tên gói đề xuất (6.3.3 và 6.3.4), chưa có trong cây |
| `shared/schemas/com.example.data.db.AppDatabase/14.json` | 1 | file mà tác vụ F-16 của kế hoạch KMP sẽ tạo, chưa có trong cây (hôm nay tệp nằm ở `app/schemas/`) |
| `assets/content/VN.json` | 1 | đường dẫn đích đề xuất (U-20), chưa có trong cây |
| `assets/content/INTL.json` | 1 | đường dẫn đích đề xuất (U-20), chưa có trong cây |
| `T2025e.txt` | 1 | tên file của Đài Thiên văn Hồng Kông (HKO), nguồn ngoài, không nằm trong repo |
| `T2040e.txt` | 1 | tên file của Đài Thiên văn Hồng Kông (HKO), nguồn ngoài, không nằm trong repo |
| `InLoveDatabaseAccess.kt` | 1 | file mà kế hoạch KMP (D-42, DEC-71) sẽ tạo, chưa có trong cây |
| `data/media/FirebaseMemoryMediaRepository.kt` | 1 | đã xoá khỏi cây làm việc hiện tại (git ghi "D"; còn trong commit gần nhất) |

### 10.4 Dữ liệu thô và nguồn

Dữ liệu làm giàu và bảng âm lịch nằm trong `docs/research/data/` (bảng liệt kê ở 7.7): `gift-occasion-draft.json`, `lunar-2025-2040.json`, `lunar_reference.py` và `sources.md`. `sources.md` gom danh sách nguồn theo từng báo cáo thị trường; chưa có script nào mở từng đường dẫn để kiểm (xem mục 2).
