# 36 — Holdout & Exploration Floor: hai thứ không thể bổ sung hồi tố

**Ngày viết:** 2026-08-22
**Trạng thái:** đã implement (`HoldoutManager.kt`, `BanditPolicy.explorationFloor`), **18/18 unit test PASS**, tổng bộ test **92/92 PASS**, AAR release + APK debug build sạch.

---

## A. Vì sao hai thứ này phải có từ ngày đầu

Đây là điểm mà gần như mọi tài liệu trong KB đều độc lập nhấn mạnh:

- Doc 32: *"permanent holdout 1-5% đo incrementality toàn engine"* — và ghi rõ **rẻ nhất khi làm sớm**.
- Doc 34: *"vẫn phải có holdout ngay từ ngày đầu"*.
- Doc 31 mục 5: *"Epsilon exploration floor (2–5%) là hạ tầng bắt buộc, không phải tính năng thuật toán"*.
- Doc 19: **holdout-native là white space đã xác minh** — không vendor paywall lớn nào (RevenueCat, Adapty, Superwall, Apphud, Qonversion) có sẵn. Đây là điểm khác biệt hoá cạnh tranh, không chỉ là vệ sinh kỹ thuật.

Lý do "không thể hồi tố" khác nhau ở hai thứ:

| | Vì sao không hồi tố được |
|---|---|
| **Holdout** | So sánh "trước khi bật brain / sau khi bật" luôn lẫn với mùa vụ, bản cập nhật game, đổi chiến dịch UA. Chỉ nhóm đối chứng chạy **song song** mới cho so sánh sạch. |
| **Exploration floor** | Khi bandit hội tụ, nó chọn cùng một arm ~100% và propensity log ra ≈ 1.0. Lúc đó **mọi off-policy evaluation trở nên vô dụng** — không có overlap thì importance weight bằng 0 hoặc vô cực. Log đã ghi thì không sửa lại được. |

## B. Holdout — thiết kế

```text
bucket(playerId) = FNV-1a("salt|playerId") mod 10000
isHoldout        = bucket < holdoutFraction * 10000
```

Bốn quyết định thiết kế đáng nói:

**1. Tất định theo hash, không random.** Cùng `playerId` luôn rơi vào cùng nhóm qua mọi phiên, mọi lần cài lại, **không cần lưu trữ gì**. Random-per-call sẽ khiến một player lúc treatment lúc control — làm hỏng hoàn toàn phép đo.

**2. FNV-1a chứ không phải `String.hashCode()`.** `hashCode` của JVM không được đảm bảo ổn định giữa các phiên bản/nền tảng. Phân nhóm phải giống hệt nhau ở client và server khi đối soát log.

**3. Guardrail VẪN áp cho nhóm holdout.** Holdout là để đo giá trị của *personalization*, không phải để bỏ an toàn. Nếu nhóm control bị hại vì thiếu guardrail, phép so sánh sẽ nghiêng về phía brain một cách giả tạo.

**4. Outcome của holdout KHÔNG nạp vào bandit.** Quyết định của họ do luật tất định sinh ra, không phải do bandit; nạp vào sẽ làm lệch posterior bằng chính dữ liệu lẽ ra phải độc lập. Vẫn ghi log đầy đủ để backend phân tích incrementality.

### `isBaseline` — điều khiến so sánh có nghĩa

`ActionCandidate` có thêm cờ `isBaseline`. Nhóm holdout luôn nhận action này. Không có nó thì "control" là một thứ mơ hồ; có nó thì control = **đúng hành vi hệ thống làm trước khi có brain**, và hiệu số treatment − control mới đọc được.

## C. Exploration floor — thiết kế

```text
p_final(a) = (1 − eps) · p_TS(a) + eps / n
```

Chi tiết dễ làm sai và đã được xử lý: **rút thăm theo chính phân phối đã trộn**, không phải chọn theo Thompson rồi mới log xác suất trộn. Nếu làm cách sau, action thực tế và propensity đã log không khớp nhau — và OPE dựa trên propensity sai còn tệ hơn không có OPE.

Mặc định `0.03`. Đặt `0.0` sẽ tắt OPE; artifact có ghi cảnh báo rõ khi ai đó làm vậy.

## D. Một lỗi thiết kế bị unit test bắt được

Bản đầu của `isInRollout` (staged rollout) dùng phép dịch pha để "tách" khỏi bucket holdout:

```kotlin
val shifted = (bucket(playerId) + BUCKET_COUNT / 2) % BUCKET_COUNT   // SAI
```

Test `rollout va holdout khong tuong quan voi nhau` fail ngay. Lý do: dịch pha chỉ là **một hàm tất định của chính bucket đó**, nên hai nhóm vẫn tương quan hoàn toàn. Với holdout 10% (bucket 0–999) và rollout 50%, mọi player holdout đều dịch thành 5000–5999 — tức **luôn nằm ngoài rollout**. Hai thí nghiệm không thể chạy đồng thời.

Cách đúng: băm bằng **không gian tên riêng**, không phải biến đổi bucket cũ.

```kotlin
fun bucketWith(namespace: String, playerId: String): Int   // ĐÚNG
isInRollout → bucketWith("$salt|rollout", playerId)
```

Đây là ví dụ rõ nhất trong cả dự án về việc test bắt lỗi mà đọc code không thấy — logic "dịch pha cho khác nhau" nghe rất hợp lý cho tới khi đo thực tế.

## E. Cấu hình từ policy artifact

```jsonc
{
  "version": "exp_2026_q3",
  "experiment": {
    "holdout_fraction": 0.05,        // kẹp [0, 0.5]
    "holdout_salt": "cycle_2",       // đổi = phân nhóm lại toàn bộ dân số
    "exploration_floor": 0.03        // kẹp [0, 0.5]; =0 sẽ có cảnh báo
  }
}
```

`holdout_fraction` bị kẹp trần 0.5 — giữ đối chứng quá nửa dân số gần như chắc chắn là lỗi cấu hình.

**Cảnh báo vận hành:** chỉ đổi `holdout_salt` khi bắt đầu một chu kỳ đo mới. Đổi giữa chừng làm một số player nhảy nhóm và mất tính so sánh của dữ liệu đang thu.

## F. Quan sát khi test

Dòng thứ ba của debug view hiển thị trực tiếp:

```text
║ nhóm  : HOLDOUT (đối chứng — KHÔNG personalize)  [bucket 342/10000, explore 3%]
║ nhóm  : treatment                                [bucket 7781/10000, explore 3%]
```

## Không xác minh được (ghi rõ)

1. Chưa có dữ liệu thật để biết `holdoutFraction = 0.05` có đủ công suất thống kê cho quy mô traffic thực tế hay không — doc 28 mục J cho công thức cỡ mẫu, cần tính lại theo DAU thật.
2. Phía backend chưa tồn tại, nên chưa có ai thực sự *đọc* cờ `isHoldout` trong log để tính incrementality. Cơ chế mới chỉ đảm bảo dữ liệu được sinh ra đúng.
3. Giá trị `0.03` cho exploration floor là chọn theo khoảng thông dụng trong tài liệu (2–5%), chưa tối ưu theo chi phí khám phá thực tế của sản phẩm này.
