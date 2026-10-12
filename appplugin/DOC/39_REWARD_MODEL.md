# 39 — Reward Model: sửa một reward suy biến

**Ngày viết:** 2026-08-22
**Trạng thái:** `RewardModel.kt` đã implement, **9/9 test riêng PASS**, tổng **146/146 PASS** ổn định qua 6 lần chạy liên tiếp.

---

## A. Lỗi đang sửa

Ở lượt xây `AdsBrain`, tôi gán reward ngay tại thời điểm hành động:

```kotlin
onInterstitialShown() → reportOutcome(1.0)
onAdSkipped()         → reportOutcome(0.0)
```

Tôi từng mô tả đây là "hạn chế tạm thời". Nhìn kỹ lại thì không phải — đây là **reward tautological**: hành động được thưởng vì **đã xảy ra**, không phải vì tạo ra giá trị.

Hệ quả tất yếu: `INTERSTITIAL` luôn nhận 1.0, `NO_ACTION` luôn nhận 0.0, nên bandit **chắc chắn hội tụ về "luôn hiện quảng cáo"**. Thứ duy nhất cản lại là guardrail và Lagrangian. Nói cách khác, toàn bộ phần học máy trở nên vô nghĩa — nó chỉ học lại chính các luật đã cứng, và học sai hướng so với mục tiêu sản phẩm ở doc 26.

## B. Nguyên tắc: reward đến từ điều xảy ra SAU

Đây là pattern Pinterest đã production hoá (doc 03 mục 8, doc 05 mục 9): retention là tín hiệu thưa và trễ, nên dùng hành vi phiên xuất hiện sớm nhưng dự báo được retention làm proxy.

Tín hiệu proxy chọn ở đây: **người chơi có tiếp tục chơi sau hành động không.**

```text
R = w_value · giá_trị_tức_thì  +  w_continue · tiếp_tục_chơi
```

| Tình huống | Reward | Diễn giải |
|---|---|---|
| Hiện ad, người chơi ở lại | 1.00 | tốt nhất — có doanh thu mà không mất ai |
| Không hiện, người chơi ở lại | 0.60 | an toàn nhưng bỏ lỡ doanh thu |
| **Hiện ad, người chơi bỏ đi** | **0.40** | **tệ hơn không làm gì** |
| Không hiện, vẫn bỏ đi | 0.00 | mất người mà chẳng được gì |

Dòng thứ ba là mấu chốt. Không có tính chất "hiện ad rồi mất người chơi < im lặng" thì bandit **không bao giờ học được cách kiềm chế**. Có một test riêng khẳng định đúng bất đẳng thức này.

## C. Tín hiệu continuation lấy từ đâu

| Sự kiện | Suy ra |
|---|---|
| `onLevelFail` / `onLevelWin` / `onPurchase` | vẫn đang chơi → `continued = true` |
| Chạm tới một decision point mới | vẫn đang chơi → `continued = true` |
| `onSessionEnd()` | đã rời đi → `continued = false` |
| `onSessionStart()` khi còn quyết định treo | phiên trước kết thúc mà không rõ → `false` |

Điểm tinh tế: **chạm tới decision point mới chính là bằng chứng người chơi còn ở lại** — nếu họ rời đi thì đã không có lần hỏi đó. Đây vừa là tín hiệu tốt, vừa sửa một bug: bản đầu ghi đè quyết định treo mà không chốt, làm mất dữ liệu và gán nhầm kết quả của hành động trước sang hành động sau.

## D. Đây vẫn chưa phải đích

Công thức đầy đủ ở doc 11:

```text
R = r_tức_thì + γ·[LTV(s') − LTV(s)] − λ_churn·P(churn|action) − λ_fair·ViPhamCongBang
```

Cần LTV/churn model chưa tồn tại. Cái ở đây là **bậc thang trung gian đúng hướng**: không còn tautological, đã có đánh đổi thật giữa doanh thu và giữ chân. Khi có LTV model, thay `RewardModel.estimate()` — không cần sửa `DecisionEngine` hay `AdsBrain`.

## E. Cảnh báo về hai núm trọng số

`weightImmediateValue` và `weightContinuation` là chỗ **dễ lạm dụng nhất trong toàn hệ thống**. Đẩy trọng số về phía doanh thu đủ mạnh thì thứ tự ở bảng mục B đảo ngược, và hệ thống quay lại đúng hành vi tham lam mà nó được thiết kế để tránh — có một test minh hoạ chính xác điều này.

Khuyến nghị: coi hai giá trị này là **tham số quản trị**, cần review như một thay đổi chính sách chứ không phải tinh chỉnh kỹ thuật. Chúng nên nằm trong policy artifact có kiểm soát (doc 35), không phải hằng số ai cũng sửa được.

## Không xác minh được (ghi rõ)

1. Trọng số 0.4/0.6 là **giá trị thiết kế**, chưa hiệu chỉnh từ dữ liệu thật. Cần A/B để tìm điểm cân bằng đúng cho từng tựa game.
2. "Tiếp tục chơi" là proxy cho retention, **không phải retention**. Nó đo trong phạm vi một phiên; người chơi ở lại hết phiên rồi hôm sau không quay lại thì tín hiệu này không bắt được.
3. Chưa có bằng chứng thực nghiệm rằng reward này cho kết quả kinh doanh tốt hơn reward nhị phân — lập luận hiện tại là **loại bỏ một lỗi logic đã chứng minh được**, không phải tối ưu đã đo.
