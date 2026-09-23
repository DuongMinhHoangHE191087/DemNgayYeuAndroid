# APPPLUGIN_SDK_OVERVIEW.md — Hiểu đúng về `:appplugin` (SDK quảng cáo/IAP nội bộ)

> Tóm tắt sau khi đọc trực tiếp `MonetizationSdk.kt`, `AppPluginBase.kt`, `AppPluginManager.kt`, `Entitlements.kt`, cấu trúc 141 file mã nguồn, và `appplugin/DOC/00_INDEX.md` (chỉ mục của một knowledge base nghiên cứu 48.000+ dòng, 65 file). Mục tiêu: giúp hiểu **`:appplugin` thực chất là gì** trước khi quyết định nâng cấp/tắt/bật tính năng nào của nó cho InLove.

## 1. `:appplugin` KHÔNG chỉ là "SDK quảng cáo + IAP"

Theo chính tài liệu của nó (doc 00_INDEX.md dòng 10): mục tiêu thật của hệ thống là một **"Player Decision Platform / Personalized Monetization Brain"** — hệ thống chọn hành động tối ưu (hiện quảng cáo / chào mua / hỗ trợ miễn phí / không làm gì) theo trạng thái từng người dùng, dùng contextual bandit, causal uplift, POMDP belief-state, và tối ưu giá trị dài hạn (LTV) — vốn là công nghệ được thiết kế cho **game** (có battle pass, gacha, difficulty tuning, "player churn", v.v.), nhắm tới thị trường **US & EU** (ghi rõ trong doc, không phải VN/SEA).

InLove chỉ là MỘT trong nhiều app có thể dùng SDK này, và chỉ cần một phần rất nhỏ của nó:
- Lớp thực thi quảng cáo (AdMob/AppLovin MAX mediation, banner/interstitial/app-open) — **InLove dùng thật**.
- Lớp IAP/Entitlements (đồng bộ quyền lợi VIP → tắt quảng cáo) — **InLove dùng thật**, đã nối cầu ở Task A3.
- Lớp "Brain" (contextual bandit quyết định CÓ nên hiện quảng cáo/chào mua hay không, dựa trên learned policy) — **InLove có bật** (`AdsBrain.enabled = true`) nhưng gần như toàn bộ phần "học" phía sau (backend BigQuery, training) **CHƯA deploy** theo chính ghi chú của SDK (doc 46: "Chưa deploy, chưa train").
- Battle pass, offerwall, CRM bridge, recurring grants, cross-modal assist (difficulty tuning cho game) — **InLove hoàn toàn không dùng**, các module này tồn tại trong `:appplugin` nhưng không được cấu hình/gọi từ `InLoveApplication.kt`.

## 2. Phát hiện cần xử lý: `brainRolloutFraction = 1.0`

**File:** `app/src/main/java/com/example/InLoveApplication.kt`

```kotlin
MonetizationSdk.configure(this) {
    brainEnabled = true
    brainRolloutFraction = 1.0   // <-- 100% người dùng
    ...
}
```

Chính KDoc của `MonetizationSdk.Config.brainRolloutFraction` (đọc trực tiếp trong `MonetizationSdk.kt`) ghi rõ: *"quy trình đúng là mở dần từ 0.05 (doc 38 mục F)"*. InLove đang bật thẳng 100% — **trái ngược trực tiếp với khuyến nghị của chính SDK**, cho một hệ thống mà theo tài liệu riêng của nó:
- Chưa có backend deploy để "học" điều gì (`doc 46`: "Chưa deploy, chưa train" — brain học cục bộ trên từng máy, chưa tổng hợp toàn cục, đúng như `MonetizationSdk.Report.describe()` tự in ra).
- Vốn thiết kế/kiểm thử cho ngữ cảnh game (battle pass, difficulty tuning), chưa chắc guardrail của nó phù hợp 100% với ngữ cảnh một app kỷ niệm tình yêu.

**Rủi ro cụ thể:** mọi người dùng InLove hiện đang bị một hệ thống quyết định thử nghiệm (dù có guardrail) chi phối MỘT PHẦN hành vi hiện quảng cáo, thay vì logic tường minh, dễ kiểm chứng trong `AdsManagerImpl.kt` (30 giây interval cap). Nếu guardrail của brain có lỗi hoặc học lệch, ảnh hưởng lan ra 100% người dùng ngay lập tức, không có nhóm đối chứng nào để phát hiện sớm.

**Đề xuất (chưa thực thi — cần xác nhận chủ dự án vì đây là quyết định sản phẩm, không đơn thuần kỹ thuật):**
- Hạ `brainRolloutFraction` xuống một giá trị nhỏ (0.0–0.05) hoặc tắt hẳn `brainEnabled = false` cho tới khi có lý do rõ ràng cần các tính năng nâng cao của brain cho một app không phải game.
- Nếu giữ bật, nên đọc thêm `appplugin/DOC/38_INTEGRATION_GUIDE.md` (quy trình bật an toàn, đường lùi khẩn cấp) trước khi quyết định giữ nguyên 1.0.

## 3. Quảng cáo & Subscription — những gì InLove THỰC SỰ dùng (đã xác minh qua code)

- **Ads:** `AdsManagerImpl.kt` (`:app`) gọi `com.app.plugin.ads.AdsHelper`/`AdsBrain`/`ConsentManager` (`:appplugin`). Mediation nhiều mạng (AdMob, AppLovin MAX, Pangle, Bigo, Chartboost, Fyber, Vungle, Meta, Mintegral, Unity) đã cấu hình trong `appplugin/build.gradle.kts` — quy mô lớn hơn nhiều so với nhu cầu một app tình yêu, nhưng không sai vì đây là SDK dùng chung.
- **Subscription/IAP:** hai billing client cùng tồn tại — `BillingManager` (`:app`, chạy purchase flow thật) và `IapHelper` (`:appplugin`, khởi động qua `MonetizationSdk.configure`). Đã thống nhất Product ID ở Task A3; **quyết định có nên tắt `IapHelper` song song hay không vẫn còn treo** — xem thêm `45_IAP_BILLING_LAYER.md`/`50_PURCHASE_ROUTING.md`/`51_SUBSCRIPTION_LIFECYCLE.md` nếu muốn tận dụng các tính năng dunning/win-back có sẵn trong SDK thay vì tự viết lại ở `:app`.
- **Telemetry:** mọi quyết định của brain + sự kiện mua hàng được gửi tới `https://ingest.databuckets.com/` (đã ghi trong báo cáo bảo mật trước đó) — đây chính là "decision log" mà tài liệu SDK mô tả, KHÔNG có backend BigQuery riêng của InLove nhận (chỉ endpoint chung của nhà cung cấp SDK).

## 4. Kết luận thực dụng cho InLove

`:appplugin` là một SDK CHUNG rất mạnh nhưng được thiết kế cho hệ sinh thái game US/EU, không viết riêng cho InLove. InLove chỉ cần lớp thực thi ads/IAP của nó — phần "Brain" nên được xem là **thử nghiệm, cần thận trọng khi bật 100%** đúng như tài liệu của chính SDK khuyến cáo. Không cần "dựng lại" SDK này (đã có sẵn, tài liệu hoá cực kỳ kỹ) — chỉ cần **cấu hình nó thận trọng hơn** cho đúng quy mô và bối cảnh của InLove.
