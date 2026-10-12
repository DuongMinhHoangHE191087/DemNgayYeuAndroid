# 40 — ComfortIndex: implement một chỉ số mà phần lớn dữ liệu chưa có

**Ngày viết:** 2026-08-22
**Trạng thái:** `ComfortIndex.kt` đã implement, **11/11 test riêng PASS**, tổng **157/157 PASS** ổn định qua 4 lần chạy.

---

## A. Bối cảnh

Doc 26 định nghĩa 4 North Star Metrics và đặt ràng buộc: Control Plane **phải hiển thị cả bốn cùng lúc**, không cho xem riêng ARPDAU mà ẩn chỉ số comfort. Lý do: ba chỉ số kia đều dễ tối ưu bằng cách hy sinh người chơi.

Nhưng ComfortIndex mới chỉ tồn tại dưới dạng công thức trong tài liệu. Tài liệu này implement nó — và xử lý một vấn đề trung thực nghiêm túc.

## B. Vấn đề: 3/5 thành phần không có dữ liệu

Kiểm tra thực tế trong code cho thấy **không một trường latent nào của `PlayerState` được điền** (`purchasePropensity`, `churnRisk`, `adToleranceScore`, `adFatigueScore`, `monetizationReadiness` — tất cả đều 0 nơi gán).

| Thành phần | Trạng thái |
|---|---|
| `ad_fatigue` | ✅ suy từ `adImpressions24h` so với trần |
| `churn_proxy` | ✅ suy từ `daysSinceLastSession` (thô) |
| `organic_purchase_probability` | ❌ cần causal model chưa tồn tại (doc 05) |
| `refund_rate` | ❌ SDK chưa theo dõi refund |
| `post_purchase_retention_delta` | ❌ SDK chưa theo dõi retention sau mua |

Implement ngây thơ sẽ trả về một con số **trông có thẩm quyền nhưng dựa trên 40% dữ liệu**. Với một chỉ số mà cả mục đích là để ngăn đội vận hành tối ưu sai hướng, điều đó còn nguy hiểm hơn không có chỉ số nào — nó tạo cảm giác đã đo được thứ chưa đo được.

## C. Giải pháp: bắt buộc báo cáo độ phủ

```kotlin
data class Result(
    val value: Double,                    // 0..1
    val coverage: Double,                 // tỉ lệ thành phần có dữ liệu
    val componentsAvailable: List<String>,
    val componentsMissing: List<String>   // kèm LÝ DO thiếu
) {
    fun isTrustworthy(): Boolean = coverage >= 0.6
}
```

`describe()` in ra: `ComfortIndex 0.72 (độ phủ 40% — CHƯA ĐỦ TIN CẬY)`.

Hiện tại `isTrustworthy()` **luôn trả về `false`**, và có test khẳng định điều đó. Khi nào thêm được causal model hoặc theo dõi refund/retention thì độ phủ tự tăng và cảnh báo tự tắt — không cần nhớ gỡ thủ công.

## D. Đính chính công thức doc 26

Bản đầu viết `− w5·guardrail_violation_rate_prevented` (dấu **âm**) nhưng mô tả nó là *"proxy cho hệ thống đang bảo vệ user"* (tức **tốt**). Hai điều mâu thuẫn nhau.

Xử lý: **bỏ hẳn khỏi ComfortIndex.**

Lập luận: guardrail nổ nhiều nghĩa là *chính sách bên dưới đang muốn hung hăng và bị chặn lại*. Đó là tín hiệu về **sức khoẻ chính sách**, không phải về trải nghiệm người chơi — người chơi đã được bảo vệ nên comfort của họ không đổi. Gộp hai thứ vào một con số làm cả hai mất ý nghĩa.

Nay tách thành `ComfortIndex.policyAggressiveness(engine)` trả về số lần chặn theo từng mã guardrail. Diễn giải: tăng đột biến ở một mã là dấu hiệu policy hoặc artifact vừa đổi theo hướng cần rà lại. Có test khẳng định guardrail nổ **không** làm giảm ComfortIndex.

## E. Trọng số reward chuyển vào policy artifact

Ở doc 39 mục E tôi khuyến nghị coi `weightImmediateValue`/`weightContinuation` là **tham số quản trị**, không phải hằng số ai cũng sửa. Nay đã thực hiện:

```jsonc
{
  "version": "rw_2026_q3",
  "reward": {
    "weight_immediate_value": 0.4,
    "weight_continuation": 0.6
  }
}
```

Kèm **kiểm tra tự động tính chất chống suy biến**: nếu trọng số làm cho *"hiện ad rồi mất người chơi"* ≥ *"im lặng mà giữ được người"*, artifact vẫn được áp (đó là quyền của người vận hành) nhưng báo cáo ghi cảnh báo rõ:

```text
⚠ trọng số reward làm MẤT tính chất chống suy biến
  (ad+quit=0.95 >= im lặng+ở lại=0.05) → bandit sẽ học cách hung hăng
```

Đây là ví dụ về nguyên tắc mà tôi cố giữ xuyên suốt: **không chặn quyết định của người vận hành, nhưng không để họ đi vào hố mà không biết.**

## Không xác minh được (ghi rõ)

1. `churn_proxy` suy từ `daysSinceLastSession` là ước lượng rất thô — không phải mô hình churn thật (doc 32).
2. Hai thành phần hiện có được cộng với **trọng số bằng nhau**, chưa có cơ sở nào cho rằng mệt mỏi vì quảng cáo và rủi ro rời bỏ đáng cân ngang nhau.
3. Ngưỡng `isTrustworthy() = coverage >= 0.6` là con số chọn, không phải suy ra từ phân tích thống kê.
4. Toàn bộ chỉ số chưa từng được đối chiếu với bất kỳ thước đo hài lòng thật nào (khảo sát, rating, retention dài hạn).
