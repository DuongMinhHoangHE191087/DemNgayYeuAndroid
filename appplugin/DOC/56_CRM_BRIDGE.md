# 56 — Cầu nối CRM: phát tín hiệu engagement ra Braze/OneSignal qua adapter (doc 23 mục E.3, Phase 4)

**Ngày viết:** 2026-08-25
**Trạng thái:** 472/472 unit test, backend 89/89, hai AAR build sạch.

---

## A. Bài toán

Brain hiểu người chơi (segment, khuynh hướng chi tiêu), còn CRM (Braze, OneSignal,
Clevertap…) là kênh nói chuyện với họ. Không có cầu nối, hai hệ thống mù nhau: CRM gửi
cùng một chiến dịch cho người sắp rời bỏ và người sắp trả tiền. Doc 23 Phase 4 gọi tên
mục này: **"Webhook ra Braze/OneSignal"**.

Hai cám dỗ thiết kế phải tránh:

1. **Nhúng vendor CRM SDK vào thư viện** — bài học ironSource (doc 20 A.4): biến SDK thành
   nô lệ lịch release vendor. Giải pháp: adapter do game implement.
2. **Phát tín hiệu suy đoán** — ví dụ "churn risk theo ngày vắng mặt". `daysSinceLastSession`
   hiện chưa được persist qua phiên nên không có dữ liệu thật. Phát tín hiệu bịa còn tệ
   hơn không phát: CRM sẽ hành động trên dữ liệu sai.

## B. Thiết kế

### B.1 Sink là interface do game implement

```kotlin
interface CrmSink {
    val sinkId: String
    fun deliver(signal: EngagementSignal): Boolean
}
```

SDK không chứa dependency CRM nào. Game bọc Braze/OneSignal SDK trong adapter mỏng; cùng
`sinkId` đăng ký lại sẽ THAY adapter cũ (hot-swap). Id rỗng bị từ chối. Sink ném lỗi sẽ
bị bridge bọc try/catch — vendor SDK hỏng không được làm hỏng luồng game.

### B.2 Chỉ hai tín hiệu — cả hai đều có dữ liệu thật

| Tín hiệu | Phát khi | Detail |
|---|---|---|
| `SEGMENT_CHANGE` | `DecisionEngine.observe` thực sự làm latent type dịch chuyển (so argmax trước/sau, không đoán) | `CASUAL_STEADY->LOW_SKILL_HIGH_CHURN_RISK` |
| `FIRST_PURCHASE` | lượt mua đầu đời (`purchaseCountLifetime` chuyển 0→1 — phát ĐÚNG MỘT LẦN) | `usd=4.99` |

Cả hai phát qua đường ống hành vi sẵn có của `AdsBrain` (`observe()` và `onPurchase()`) —
không có đường phát riêng, không có trạng thái riêng phải đồng bộ.

### B.3 Mỗi tín hiệu mang cờ holdout

`EngagementSignal.isHoldout` lấy từ `HoldoutManager` tại thời điểm phát. Nhóm đối chứng
tồn tại để đo giá trị của cá nhân hoá; nếu CRM vô tình dội chiến dịch vào họ, phép đo
incrementality hỏng. Cờ này để studio LOẠI nhóm đối chứng khỏi chiến dịch — cùng một hook
dùng chung cho UA lẫn monetization (mục 3 của Phase 4).

### B.4 Mặc định TẮT — dữ liệu chảy RA phải là quyết định có chủ đích

Chưa nạp cấu hình hoặc `enabled=false` → không phát gì. Đây là bất đối xứng cố ý với các
lớp khác (offerwall cho phép mọi provider khi allowlist rỗng): với CRM, im lặng là trạng
thái an toàn vì mỗi tín hiệu là dữ liệu cá nhân hoá rời khỏi SDK.

## C. Gate consent

Tín hiệu engagement là dữ liệu cá nhân hoá — GDPR coi là profiling:

- Consent **DENIED** → chặn phát.
- Consent **UNKNOWN** KHÔNG chặn — chưa hỏi khác với từ chối (quy tắc nhà, doc 13).
- `childDirected == true` → chặn luôn kể cả GRANTED: COPPA, và CRM cho trẻ em bị cấm ở
  hầu hết khu vực pháp lý.

Lưu ý: `FIRST_PURCHASE` vẫn phát khi **brain tắt** (nếu CRM bật) — lượt mua là sự thật,
không phải sản phẩm của profiling. Chỉ `SEGMENT_CHANGE` là phụ thuộc brain vì nó cần
belief dịch chuyển thật.

## D. Cấu hình từ xa

Khoá `crm_bridge_android`, nạp qua `FirHelperConfig.parserCfWithRemoteCf()`:

```json
{
  "version": "crm_v1",
  "enabled": true,
  "sinks": ["braze"]
}
```

`sinks` vắng mặt hoặc rỗng = cho phép mọi sink đã đăng ký. JSON hỏng → giữ bản đang chạy;
thiếu `version` → từ chối.

## E. Tích hợp (drop-in)

```kotlin
// 1. Adapter mỏng bọc vendor CRM (mỗi vendor một class):
class BrazeSink(ctx: Context) : CrmBridge.CrmSink {
    override val sinkId = "braze"
    override fun deliver(signal: CrmBridge.EngagementSignal): Boolean {
        // Loại nhóm đối chứng khỏi chiến dịch để giữ phép đo incrementality sạch:
        if (signal.isHoldout) return true
        Braze.getInstance(ctx).logCustomEvent(
            "sdk_${signal.kind.name.lowercase()}",
            Properties().put("detail", signal.detail).put("player_id", signal.playerId)
        )
        return true
    }
}
CrmBridge.register(BrazeSink(this))
// Cấu hình crm_bridge_android tự nạp qua FirHelperConfig.

// 2. Game có nguồn tín hiệu riêng? Phát qua CÙNG một cổng gate consent:
CrmBridge.emit(CrmBridge.EngagementSignal(
    CrmBridge.SignalKind.FIRST_PURCHASE, playerId, "usd=4.99", isHoldout = false
))
// Nhưng hai tín hiệu chuẩn do SDK tự phát — game không cần gọi.
```

## F. Giới hạn — ghi rõ

1. Chưa có tín hiệu churn/inactivity — `daysSinceLastSession` chưa persist qua phiên;
   thêm tín hiệu đó đòi hỏi persistence thật trước (không bịa).
2. Bridge phát tại thời điểm sự kiện — không có hàng đợi retry phía SDK. Vendor adapter
   tự lo lưu-lại-gửi-lại nếu cần (Braze/OneSignal SDK vốn đã có queue riêng).
3. SDK không biết chiến dịch CRM nào đang chạy — việc loại holdout khỏi chiến dịch là
   quyết định của studio dựa trên cờ `isHoldout`.
