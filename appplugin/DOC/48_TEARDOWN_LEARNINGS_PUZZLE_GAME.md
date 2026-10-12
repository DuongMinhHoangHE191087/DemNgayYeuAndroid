# 48 — Chắt lọc từ teardown một game puzzle production (Ghidra/IL2CPP)

**Ngày viết:** 2026-08-24
**Nguồn:** `~/ghidra_export` — 142 MB decompile, 320.081 symbol, Unity IL2CPP arm64, cộng **28 key Firebase Remote Config giá trị production thật**. Hai báo cáo phân tích đi kèm: `Bao-cao-Monetization-Teardown.docx`, `Kich-ban-IAA-IAP-LiveOps.docx`.
**Trạng thái:** 5/6 pattern đã code hoá và có test. **275/275 unit test PASS.**

---

## A. Vì sao nguồn này đáng giá hơn tài liệu ngành

Đây không phải "best practice" ai đó viết ra. Đây là **cấu hình đang chạy trên dân số thật**,
đọc trực tiếp từ `FetchAndActivate()`. Mỗi con số là một quyết định đã được ai đó chịu trách
nhiệm và giữ lại vì nó hoạt động.

Một giới hạn phải nói trước, lấy từ chính báo cáo nguồn: bản dump chạy trong Unity Editor nên
**không thuộc chiến dịch UA nào**, và Firebase Remote Config phân khúc bằng Conditions phía
server. Vì vậy 28 giá trị này là **nhánh mặc định của template** — người chơi thật ở quốc gia
khác hoặc đến từ chiến dịch tROAS có thể đang nhận bộ khác. Không suy ra được từ dữ liệu này.

## B. Sáu pattern, và ta làm gì với chúng

| # | Pattern ở game tham chiếu | Ta làm gì |
|---|---|---|
| 1 | `inter_show_level = 10` — 9 màn đầu sạch quảng cáo | ✅ `minLevelForInterstitial` |
| 2 | `inter_show_lose = {Level: 22}` — nhánh thua tha thêm 12 màn | ✅ `minLevelForInterstitialAfterLoss` |
| 3 | `inter_capping_after_reward = 30` tách khỏi `inter_capping = 130` | ✅ `minMinutesAfterRewarded` |
| 4 | `SkipFirstInterCountOnReopen` | ✅ `skipFirstInterstitialsOnReopen` |
| 5 | `noads_popup_config = {ShowPerDay: 2, GapSeconds: 300}` | ✅ `maxIapOffers24h` |
| 6 | `inter_ads_capping_config` — cooldown theo dải level (L1-35: 170s, L36+: 120s) | ❌ **cố ý không copy** — xem mục D |

Điểm chung của năm cái đầu: **bảo vệ khoảnh khắc mong manh**. Người mới chưa gắn bó, người vừa
thua đang bực, người vừa mở lại app chưa vào nhịp, người vừa tự nguyện xem một quảng cáo dài.
Ở những thời điểm đó một impression có giá trị tức thì gần như nhau nhưng **chi phí retention
chênh nhau rất xa**.

Pattern số 2 đáng chú ý nhất. Báo cáo nguồn giải thích lý do bằng tâm lý chứ không bằng kỹ
thuật: *"người vừa thua đã bực, chồng thêm quảng cáo là đẩy họ rời game"*. Đó chính xác là
tính chất mà `RewardModel` của ta được thiết kế để học — nhưng họ **hardcode** nó vào config
sau khi đã biết câu trả lời, còn ta để bandit tự tìm. Hai cách đều hợp lệ; cái của họ chạy
được ngay, cái của ta thích nghi được theo từng người.

## C. Ba con số đáng suy nghĩ

**Vàng gần như không kiếm được bằng cách chơi.** Thắng một màn = 40 gold, người mới bắt đầu
với **0** gold, một lần hồi sinh = 900, một booster = 1.400–1.800. Phải thắng liên tiếp
**22–45 màn** mới đủ cho một lần dùng. Báo cáo nguồn kết luận thẳng: con số này tồn tại để
biến "xem quảng cáo" thành lối thoát duy nhất hợp lý.

**`revive_config` leo thang theo thất bại.** Hồi sinh lần 1: 900 gold, **có** tuỳ chọn xem
quảng cáo. Lần 2: 1.800 gold, **không** còn tuỳ chọn quảng cáo. Cường độ tăng theo mức độ bế
tắc — đúng thứ mà câu hỏi Q4 trong rubric doc 29 mục 10.2 chỉ ra. Ta **không** code hoá pattern
này; `ETH_FRUSTRATION_EXPLOIT` của ta chặn theo hướng ngược lại.

**`help_with_ads_gold_config.LoseCountToShow = 8`.** Họ chào trợ giúp sau 8 lần thua; ngưỡng
`frustrationStreakThreshold` của ta chặn chào bán từ 5. Đây là một điểm đối chiếu hữu ích cho
việc calibrate sau này — nhưng lưu ý hai thứ khác nhau về bản chất: họ tặng trợ giúp, ta chặn
chào bán. Không phải cùng một trục.

## D. Vì sao KHÔNG copy pattern số 6

`inter_ads_capping_config` đặt cooldown theo dải level: 170s cho L1–35, 120s cho L36+. Tức áp
lực quảng cáo **tăng dần theo tiến độ**.

Ta đã có một cơ chế làm cùng việc đó nhưng tốt hơn: **giá bóng Lagrangian**. Dải level là một
xấp xỉ TĨNH của thứ mà `LagrangianBudget` điều tiết ĐỘNG theo mức tiêu thụ thực tế của từng
người. Thêm dải level lên trên sẽ tạo hai cơ chế cùng điều khiển một đại lượng — và đó chính
là kiểu chồng lấn mà doc 27 mục B.1 cảnh báo (ở app đối thủ, lớp fallback hung hăng hơn lớp
cấu hình chính, im lặng).

Bản thân game tham chiếu cũng đang mắc chính vấn đề đó: `inter_capping = 130` (toàn cục) tồn
tại song song với `inter_ads_capping_config` (170/120 theo dải). Hai nguồn cho cùng một con số.

## E. Điều đáng học nhất không phải một con số

Kiến trúc của họ: **mọi tham số tiền bạc đều nằm ở Remote Config, không có gì trong build**.
28 key điều khiển tần suất quảng cáo, giá booster, phần thưởng, lịch sự kiện, ngưỡng postback.
Đó đúng là nguyên tắc doc 31 mục 6 phát biểu — *bytecode chỉ chứa bộ máy diễn giải; mọi hành
vi thay đổi được phải nằm trong dữ liệu* — và họ thực hiện triệt để hơn ta.

Chỗ ta còn thua: một số ngưỡng của ta vẫn chỉ sửa được bằng release. Đã thu hẹp trong bản này
bằng khối `progression` trong policy artifact.

## Không xác minh được (ghi rõ)

1. Bản dump là **một mẫu duy nhất** từ Unity Editor, không mang attribution/quốc gia/phiên bản
   của một lượt cài thật. Nếu họ đang A/B test tần suất quảng cáo hoặc phân giá theo quốc gia,
   dữ liệu này không phát hiện được.
2. Hai nguồn cấu hình nữa nằm ngoài tầm nhìn: endpoint HTTP của `ABILibsCustomEventConfig`
   (ngưỡng tROAS và bảng tỉ giá) và Google Sheet mà `league_config` trỏ tới.
3. Không có số liệu hiệu quả nào — không biết ARPDAU, retention hay LTV của game này. Các
   pattern được đánh giá bằng lập luận thiết kế, **không** bằng kết quả đo được.
4. Toàn bộ mục C là đọc hiểu từ giá trị config; chưa xác minh bằng cách chơi game thật.
