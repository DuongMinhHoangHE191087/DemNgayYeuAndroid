# 37 — Lagrangian Resource Pacing: trả nợ kỹ thuật cuối cùng của guardrail

**Ngày viết:** 2026-08-22
**Trạng thái:** đã implement (`LagrangianBudget.kt`), **14/14 test riêng PASS**, tổng **112/112 PASS**, AAR release + APK debug build sạch.

---

## A. Nợ kỹ thuật này từ đâu ra

Doc 28 mục E (nghiên cứu thuật toán bandit) chỉ ra một lỗi thiết kế trong code tôi đã viết: `GuardrailEngine` gộp **hai loại ràng buộc khác hẳn nhau về bản chất** vào cùng một cơ chế lọc cứng.

Doc 25 mục B.4 ghi nhận và đính chính, nhưng để lại nợ:

> *"Nhóm `RESOURCE` hiện vẫn chạy như hard filter. An toàn ở Phase 0–1 vì policy còn là rule-based — chưa học từ log thì bias chưa phát sinh. **Phải chuyển sang Lagrangian TRƯỚC khi bật bandit learning trên traffic thật.**"*

Tài liệu này trả nợ đó.

## B. Vì sao lọc cứng là sai với ràng buộc ngân sách

Với ngân sách 6 quảng cáo/phiên, lọc cứng cho hành vi **giật cục**: bandit chọn arm tốt nhất liên tục cho tới khi đụng trần rồi im bặt. Người chơi có thể nhận cả 6 quảng cáo trong 5 phút đầu, sau đó không còn gì — trải nghiệm tệ mà doanh thu cũng không tối ưu.

Lý thuyết Bandits-with-Knapsacks (Badanidiyuru–Kleinberg–Slivkins) chỉ ra: khi ràng buộc là *ngân sách*, lời giải tối ưu **không phải** "luôn chọn arm tốt nhất" mà là một **phân phối trộn** trên các arm. Lọc cứng không bao giờ sinh ra phân phối trộn — nó sinh ra "tất cả rồi không gì cả".

## C. Cơ chế

```text
reward hiệu chỉnh:  r~(a) = r(a) − Σ_j lambda_j · c_j(a)
cập nhật dual:      lambda_j ← lambda_j · exp( eta · (c_quan_sát − B_j/T) )
```

Tiêu nhanh hơn ngân sách → `lambda` tăng → mọi action tốn tài nguyên bị phạt nặng dần, **trơn tru**. Tiêu chậm hơn → `lambda` giảm → hệ thống tự nới.

Điểm triển khai quan trọng: penalty được trừ **trước khi so sánh** trong vòng Monte Carlo của Thompson Sampling, nên giá bóng thực sự tham gia vào lựa chọn — không phải chặn sau khi đã chọn.

### Bảng chi phí

| Action | Chi phí | Lý do |
|---|---|---|
| `INTERSTITIAL` | 1.0 | cưỡng chế, gây gián đoạn nhiều nhất |
| `REWARDED_AD` | 0.3 | người chơi **chủ động** chọn xem — khác về bản chất, không chỉ mức độ |
| `IAP_OFFER` | 0.2 | gián đoạn nhẹ |
| `NO_ACTION` / `FREE_ACTION` / `GAMEPLAY_CONFIG` | 0.0 | không làm phiền |

Đây là **giá trị thiết kế, chưa hiệu chỉnh từ dữ liệu thật**. Ý nghĩa là "mức khó chịu tương đối", không phải đơn vị đo được.

## D. Hai lớp bổ sung nhau, không thay thế

Lagrangian **không** thay thế trần cứng. Phạt là mềm — về nguyên tắc một arm có reward đủ lớn vẫn vượt qua được. Với an toàn người dùng thì không chấp nhận *"đủ tiền thì mua được quyền spam"*.

| Lớp | Vai trò | Tính chất |
|---|---|---|
| `LagrangianBudget` | điều tiết nhịp **bên trong** ngân sách | mượt, tối ưu, có thể thương lượng |
| `GuardrailEngine` (RESOURCE) | chặn tuyệt đối **ở biên** | cứng, không thương lượng |
| `GuardrailEngine` (HARD) | đạo đức, chống gian lận | cứng, không bao giờ thành penalty |

Nguyên tắc giữ nguyên từ doc 28 mục E.4: **ràng buộc đạo đức không bao giờ được biểu diễn dưới dạng penalty trong reward.**

## E. Ba chi tiết dễ làm sai đã xử lý

**1. Khởi tạo lambda phải dương.** Cập nhật là phép nhân; bắt đầu từ 0 thì lambda mãi mãi bằng 0 và cơ chế không bao giờ hoạt động. Đây là lỗi kinh điển khi triển khai multiplicative weights. Khởi tạo 0.1, có test khẳng định.

**2. Ngân sách riêng cho từng player.** Áp lực quảng cáo là trải nghiệm cá nhân. Dùng chung một lambda toàn cục sẽ khiến hành vi người này ảnh hưởng người kia — đúng lỗi share-state đã tránh ở doc 27 mục B.2.

**3. Holdout không dùng Lagrangian.** Nhóm đối chứng phải giữ nguyên hành vi cũ để phép so sánh có nghĩa. Có test khẳng định lambda của họ không đổi.

## F. Quan sát

`DecisionEngine.budgetFor(playerId).lambdaAdPressure` là **chỉ số chẩn đoán rất tốt**: nhìn giá bóng theo thời gian biết ngay áp lực quảng cáo đang căng hay chùng, mà không cần đọc log. Doc 28 mục E.1 gọi đây là dashboard tự nhiên của guardrail engine.

## Không xác minh được (ghi rõ)

1. Bảng chi phí (1.0 / 0.3 / 0.2) chưa hiệu chỉnh từ tác động thực tế lên retention.
2. `learningRate = 0.15` và `maxLambda = 5.0` là giá trị thiết kế, chưa tối ưu.
3. `estimatedDecisionsPerSession = 30` là ước lượng thô để quy đổi B/T — nên đo từ decision log thật rồi chỉnh lại.
4. Chưa chứng minh được bằng thực nghiệm rằng Lagrangian cho ARPDAU tốt hơn lọc cứng trên dữ liệu thật; lập luận hiện tại thuần lý thuyết (BwK) cộng với test hành vi.
