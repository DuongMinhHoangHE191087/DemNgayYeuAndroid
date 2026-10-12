# 59 — OPE phía server: reward lên máy chủ, hai trường không tái tạo được có nơi tiêu thụ

**Ngày viết:** 2026-08-25
**Trạng thái:** SDK **508/508** unit test · backend **146/146** test · hai AAR build sạch.

---

## A. Bài toán: log quý nhất hệ thống đang được ghi mà không ai đọc

Doc 46 mục B.1 gọi `chosen_action_probability` (propensity) và `eligible_actions` là hai
trường **không bao giờ tái tạo được** — một khi phiên chơi kết thúc, không mô hình nào tính
lại được xác suất mà bandit đã thực sự gán cho hành động nó đã chọn lúc đó. Vì chúng quý như
vậy, ingestion có cột riêng có kiểu cho chúng, không nằm trong túi JSON.

Nhưng đến trước bản này, một phép kiểm tra đơn giản cho thấy **không có gì tiêu thụ chúng**:

| Mảnh của OPE | Trạng thái trước bản này |
|---|---|
| Propensity π₀(a\|x) | ✓ log trong `brain_decision` |
| Tập ứng viên (support) | ✓ log trong `eligible_actions` |
| Reward quan sát r | ✗ **chỉ tồn tại trên máy** — `RewardModel.estimate()` tính xong đưa vào `bandit.update()` rồi vứt |

Off-policy evaluation cần đủ ba mảnh: ước lượng "policy khác sẽ thu được bao nhiêu reward"
bằng cách đánh trọng số lại chính những mẫu mà policy đang chạy đã thu. Thiếu reward thì
mọi estimator IPS/DR của doc 28 mục H.1 đều vô hiệu — hai trường quý nhất trở thành cột
trang trí trong kho log.

## B. Hai thay đổi

### B.1 SDK: `reward_value` đi cùng `brain_feedback`

`DecisionEngine.feedback()` vốn đã có `outcomeReward01` tại chỗ log — chỉ là chưa gửi đi.
Nay nó chảy qua `DecisionLogger` → `LogEventServer.logBrainFeedback` → event `brain_feedback`
với tên trường `reward_value`.

Hợp đồng nullable là phần đáng nói nhất:

- `null` → trường **vắng mặt** trong payload, không phải số 0. Bản ghi của SDK cũ (chưa có
  trường này) và feedback chưa chốt được reward đều rơi vào nhánh này.
- `0.0` → reward hợp lệ, phải được gửi và được tính. Theo bảng của `RewardModel`, 0.00 là
  "quảng cáo hiện xong người chơi rời game" — hàng tệ nhất trong dữ liệu. Nếu nhầm 0.0 với
  "thiếu", chính những hàng tệ nhất biến mất khỏi ước lượng một cách im lặng.

Hai phía đều có test khoá hợp đồng này: payload vắng mặt lẫn bằng 0
([BrainFeedbackEventTest.kt](../src/test/java/com/app/plugin/logevent/BrainFeedbackEventTest.kt)),
và chuẩn hoá nullable + ước lượng loại hàng thiếu
([test_ope.py](../backend/tests/test_ope.py)).

Đây là mở rộng tương thích ngược: bản SDK cũ vẫn hoạt động với backend mới (hàng của chúng
đơn giản không được tính vào OPE, và endpoint báo đúng điều đó qua
`counts.decisions_without_reward`).

### B.2 Backend: `ope.py` + `GET /ope/policies`

Module thuần `compute(rows)` theo đúng house pattern, ghép `brain_decision` ↔ `brain_feedback`
theo `decision_id` (không phụ thuộc thứ tự — hàng đợi replay có thể đưa feedback tới trước
decision trong cùng một lô). Với mỗi policy tham chiếu, trên mỗi hàng ghép được:

```
w_i = π_e(a_i|x_i) / π_0(a_i|x_i)        π_0 = propensity đã log
IPS   = (1/n) Σ w_i·r_i                  không chệch, phương sai nổ khi π_0 nhỏ
SNIPS = Σ w_i·r_i / Σ w_i                chệch nhẹ, phương sai thấp — con số nên đọc
```

Trọng số kẹp ở `10.0` và số lượt kẹp được báo ra: propensity 0.01 sinh trọng số 100, và nếu
không kẹp thì một hàng duy nhất chi phối toàn bộ ước lượng. Kẹp là đánh đổi chệch lấy
phương sai — chuẩn mực của OPE thực dụng, nhưng con số kẹp phải được nhìn thấy để người đọc
biết ước lượng sạch đến đâu.

**Hai policy tham chiếu, cố ý tầm thường:**

| Policy | Ý nghĩa |
|---|---|
| `uniform` | Chọn đều trong tập ứng viên — mốc "không học gì" |
| `always_no_action` | Không làm gì cả — mốc đạo đức tối thiểu |

`always_no_action` tồn tại vì câu hỏi doc 29 đặt ra cho mọi cơ chế tối ưu doanh thu: nếu một
policy thua cả việc-ngồi-im về reward tổng hợp, nó đang phá giá trị người chơi. Giờ câu hỏi
đó có thể trả lời bằng dữ liệu thay vì bằng tranh luận.

**Hàng holdout được tính.** Quyết định của nhóm đối chứng là tất định và SDK log propensity
của chúng đúng bằng 1.0 (`DecisionEngine.decide()`: "Propensity = 1.0 vì không có lựa chọn
ngẫu nhiên nào được thực hiện") — chúng là mẫu hợp lệ của policy ghi log, và vốn là nhóm
được thiết kế để so sánh. Số hàng holdout được đếm riêng trong `counts.holdout_rows`.

**Verdict ba bậc:** `no_data` (chưa có decision nào) → `too_few_matches` (< 5 hàng ghép được:
ước lượng lúc đó chỉ là nhiễu, không được đọc như con số thật) → `ok`. Endpoint chạy thuần
trên hàng đã lưu như mọi endpoint đối soát khác, không cần cấu hình thêm, và cũng như chúng:
module này KHÔNG sửa policy thật — nó chỉ đo. Đổi policy là quyết định của studio sau khi đọc.

## C. Giới hạn phải nói rõ

1. **OPE đo reward của RewardModel, không đo LTV.** Reward hiện tại chỉ có hai thành phần
   (giá trị tức thì 0.4 + tiếp tục chơi 0.6 — `RewardModel.kt`); công thức đầy đủ của doc 11
   cần LTV/churn model chưa tồn tại. Vì vậy `GET /ope/policies` trả lời "policy tham chiếu
   đáng giá bao nhiêu theo đúng thước đo bandit đang học", và thước đo đó sẽ thay đổi khi
   LTV model xuất hiện. Kết quả hôm nay so được với nhau theo thời gian, nhưng KHÔNG phải
   là doanh thu kỳ vọng.
2. **Chỉ mới IPS/SNIPS, chưa DR.** Doc 28 mục H.1 liệt kê cả DR/Switch-DR/MRDR/DRos. Với
   hai policy tham chiếu đơn giản và propensity log chuẩn, IPS kẹp trọng số đủ dùng cho câu
   hỏi đầu tiên ("policy đang chạy hơn hay kém ngồi-im?"). DR cần model thưởng dự đoán
   (reward regression) — chưa có dữ liệu để train, nên chưa xây.
3. **Ước lượng chỉ hợp lệ trong support của policy ghi log.** Hàng mà hành động được chọn
   nằm ngoài tập ứng viên (bản ghi lỗi) được xử lý: π_e trả 0, hàng vẫn nằm trong mẫu số
   — đúng toán IPS, nhưng nếu log client hỏng nhiều thì ước lượng lệch; vệ sinh dữ liệu
   được báo qua `counts.unusable_propensity` và `counts.unparseable_eligible`.
4. Kẹp trọng số tạo chệch có chủ đích; khi `weights_clipped` lớn so với `matched`, con số
   SNIPS đáng đọc hơn IPS — và cả hai đều phải đọc kèm diagnostics, không đọc riêng.

## D. Không xác minh được (ghi rõ)

1. Chưa có dữ liệu thật: mọi khẳng định về số học đến từ 15 test backend tính tay trên bộ dữ
   liệu nhỏ (IPS/SNIPS khớp giá trị tính bằng tay từng hàng) và 4 test Kotlin khoá payload.
   Với dữ liệu thật, câu hỏi đầu tiên cần kiểm tra là phân phối propensity — nếu bandit quá
   chắc tay (propensity gần 1 cho một arm), trọng số của mọi arm khác gần 0 và ước lượng
   của các policy "không bao giờ chọn arm đó" chỉ là ngoại suy ngoài vùng dữ liệu.
2. Ngưỡng `too_few_matches = 5` là lựa chọn bảo thủ, chưa calibrate — như mọi ngưỡng khác
   trong hệ thống này.
