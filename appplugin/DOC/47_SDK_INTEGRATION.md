# 47 — Tích hợp SDK vào dự án thật: nguyên lý và các bước

**Ngày viết:** 2026-08-24
**Phiên bản:** `com.app.plugin:appplugin:1.3.0`
**Cập nhật:** 2026-08-25 (v1.2) — phủ đủ các bề mặt đã ship sau bản đầu: paywall (doc 49),
purchase router (doc 50), vòng đời subscription (doc 51), battle pass (doc 52), grant định kỳ
(doc 53), offerwall (doc 54), quyết định cross-modal (doc 55), cầu nối CRM (doc 56), bản ghi
phơi nhiễm thí nghiệm (doc 57), và full action space — cổng assist (doc 58).
**Trạng thái:** **508/508 unit test PASS**, backend 146/146, hai AAR build sạch.
**Cập nhật:** 2026-09-23 (1.2.0, bản hợp nhất) — consent một luồng mỗi process, gỡ quảng cáo theo
định dạng, cooldown sau quảng cáo, chuẩn chuyển cảnh full2, consumer rules hẹp. Danh sách đầy đủ
và việc phải làm khi nâng cấp: [`CHANGELOG.md`](../CHANGELOG.md).
**Cập nhật:** 2026-09-23 (1.3.0) — mạng quảng cáo chỉ khởi tạo sau consent, app phải gọi
`startSdkInit` (mục 2b); build lên AGP 9.

> Đây là tài liệu **duy nhất** cần đọc để đưa SDK vào một dự án. Doc 38 (brain), doc 45 (IAP),
> doc 35 (policy artifact) là tài liệu chuyên sâu cho từng lớp; docs 49–58 là chuyên sâu cho
> từng bề mặt monetization.

---

## A. Nguyên lý: SDK làm gì và không làm gì

```text
        ┌─────────────────────────────────────────────┐
        │  APP CỦA STUDIO                              │
        │  quyết định "muốn hiện quảng cáo lúc này"    │
        └───────────────────┬─────────────────────────┘
                            ↓
        ┌─────────────────────────────────────────────┐
        │  LUẬT TĨNH SẴN CÓ (AdsHelper)                │
        │  remove-ads · thời gian tối thiểu · config   │
        └───────────────────┬─────────────────────────┘
                            ↓  (lượt nào đã bị chặn thì vẫn bị chặn)
        ┌─────────────────────────────────────────────┐
        │  BRAIN — chỉ được SIẾT CHẶT THÊM             │
        │  guardrail → Lagrangian → bandit             │
        └───────────────────┬─────────────────────────┘
                            ↓
                     hiện / không hiện
                            ↓
        ┌─────────────────────────────────────────────┐
        │  TÍN HIỆU NGƯỢC: người chơi ở lại hay rời đi │
        └─────────────────────────────────────────────┘
```

**Ba nguyên lý chi phối toàn bộ thiết kế:**

1. **Brain chỉ siết chặt, không bao giờ nới lỏng.** Nó nằm *sau* mọi cổng tĩnh. Hệ quả thực
   tế: bật brain **không thể** làm mật độ quảng cáo tăng lên, chỉ có thể làm giảm. Đó là lý
   do bật nó là một thao tác an toàn để thử.

2. **Reward đến từ điều xảy ra SAU hành động, không phải từ chính hành động.** Hiện quảng cáo
   rồi mất người chơi bị chấm thấp hơn im lặng mà giữ được người. Không có tính chất này,
   bandit chắc chắn hội tụ về "luôn hiện quảng cáo" — vì hành động được thưởng chỉ vì đã xảy ra.

3. **Ràng buộc đạo đức là bộ lọc cứng, không phải điểm trừ.** Điểm trừ luôn có thể bị "mua"
   bằng đủ doanh thu. Ràng buộc tài nguyên thì ngược lại — điều tiết mượt bằng giá bóng
   Lagrangian, vì lọc cứng nhóm này tạo bias hệ thống trong dữ liệu học.

**Một nguyên lý xuyên suốt mọi bề mặt: SDK không vẽ gì cả.** Paywall, thông báo dunning,
nút battle pass, tường offerwall, màn hình web checkout — tất cả là giao diện của app hoặc
của vendor. SDK trả **dữ liệu và quyết định**; app vẽ. Đây là ranh giới giữ SDK thành một
lớp quyết định thuần, không tranh phần trải nghiệm của studio.

## B. Chạy được ngay, không cần backend

Đây là điều quan trọng nhất khi đưa vào dự án hôm nay. **Không có backend, SDK vẫn hoạt động
đầy đủ:**

| Cơ chế | Hoạt động không backend | Ghi chú |
|---|---|---|
| Guardrail (đạo đức, tài nguyên, chống gian lận) | ✅ đầy đủ | luật cục bộ, không cần mạng |
| Phân khúc theo belief | ✅ đầy đủ | học trên máy, lưu qua `PreferenceHelper` |
| Bandit Thompson Sampling | ✅ nhưng **cục bộ** | mỗi thiết bị học riêng, chưa tổng hợp |
| Kịch bản personalization | ✅ đầy đủ | |
| Holdout + staged rollout | ✅ đầy đủ | phân nhóm tất định theo hash |
| Policy artifact | ✅ qua Firebase Remote Config | kênh có sẵn, không cần server riêng |
| Quyền lợi IAP / tắt quảng cáo | ✅ đầy đủ | Play Billing trực tiếp |
| Paywall (A/B + holdout native) | ✅ đầy đủ | quyết định + cấu hình cục bộ |
| Purchase routing (Play Store / web shop) | ✅ đầy đủ | entitlement web cần server của studio (mục C.5.2) |
| Vòng đời subscription (dunning/win-back) | ✅ đầy đủ | trạng thái suy từ Play Billing + lịch sử cục bộ |
| Battle pass / grant định kỳ | ✅ đầy đủ | luật + chống trùng cục bộ |
| Offerwall (adapter đa vendor) | ✅ đầy đủ | game implement `OfferwallProvider` |
| Cổng assist (quà miễn phí / giảm độ khó) | ✅ đầy đủ | cùng brain |
| Decision log | ✅ ghi + xếp hàng bền vững | tích tại máy, trần 5000 bản ghi |
| **Học toàn cục từ log** | ❌ **cần backend** | đây là thứ duy nhất thiếu |
| **Cầu nối CRM ra Braze/OneSignal** | ⚠️ chạy nhưng **vô nghĩa nếu không có CRM** | game implement `CrmSink`; tín hiệu chuẩn hoá, mặc định TẮT |

Nói thẳng điều cuối: **chưa có backend thì brain không "tự thông minh lên" theo thời gian
trên toàn bộ người dùng.** Mỗi thiết bị là một thí nghiệm cô lập. Cái nó làm được ngay là áp
guardrail đúng, phân khúc người chơi, và điều tiết nhịp quảng cáo/quà/theo trạng thái từng
người. Đó đã là giá trị thật, nhưng nó không phải "AI học từ hàng triệu người dùng" — đừng
bán nó như vậy.

Hàng đợi decision log có trần 5000 bản ghi và **bỏ bản ghi cũ nhất** khi vượt trần, nên máy
offline dài ngày không bao giờ phình vô hạn.

## C. Bốn bước tích hợp

### 1. Thêm dependency

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/dungduong2899/appplugin")
        credentials { username = gprUser; password = gprKey }
    }
}
dependencies {
    implementation("com.app.plugin:appplugin:1.3.0")
}
```

Yêu cầu: `minSdk 26`, `compileSdk 36` (Play Billing 9 kéo `androidx.core 1.15` cần 35+).
Rule R8 đi kèm trong AAR (`consumer-rules.pro`) nên app tích hợp **không phải tự viết**. Từ 1.2.0
rule chỉ giữ phần SDK dùng qua reflection, phần còn lại được R8 của app rút gọn và đổi tên.

`gprUser`/`gprKey` là tài khoản GitHub và personal access token (classic) có quyền
`read:packages`, đặt trong `~/.gradle/gradle.properties` của từng máy và secret của CI, không
commit vào repo app.

App dùng Dagger/Hilt < 2.60 với kapt phải ghim Meta adapter về 6.21.0.3, xem mục
"Phải làm khi nâng cấp" của [`CHANGELOG.md`](../CHANGELOG.md).

### 2. Kế thừa `AppPluginBase` trong `Application`

```kotlin
class MyApp : AppPluginBase() {
    override fun onCreate() {
        super.onCreate()          // ← bắt buộc gọi trước

        val report = MonetizationSdk.configure(this) {
            brainEnabled = false              // bật là quyết định kinh doanh, xem mục D
            brainRolloutFraction = 0.05
            childDirected = false             // tuyên bố pháp lý của studio (COPPA);
                                              // không khai thì tự kiểm tra cảnh báo (mục 2b)

            inappProducts      = listOf("coin_pack")
            subsProducts       = listOf("premium_monthly")
            consumableProducts = setOf("coin_pack")
            removeAdsProducts  = setOf("premium_monthly")
        }
        Log.i("MonetizationSdk", "\n" + report.describe())
    }
}
```

`super.onCreate()` khởi tạo lưu trữ, pipeline log, hàng đợi decision log, phiên brain và fetch
config. `configure()` áp phần tuỳ chọn **theo đúng thứ tự an toàn** — xem mục E.

**Mọi cấu hình từ xa tự nạp khi Firebase Remote Config fetch** — `brain_policy_android`,
`paywall_config_android`, `purchase_routing_android`, `subscription_lifecycle_android`,
`battle_pass_android`, `recurring_grants_android`, `offerwall_config_android`, `crm_bridge_android`.
App KHÔNG phải gọi `apply()` cho bất kỳ lớp nào; key chưa có hoặc JSON hỏng thì lớp đó giữ
bản đang chạy (fail-safe), không bao giờ crash.

### 2b. Consent và khởi tạo mạng quảng cáo — BẮT BUỘC

Từ 1.3.0 **không mạng nào tự khởi tạo**. App gọi `startSdkInit`, và SDK chỉ khởi tạo MobileAds
(kéo theo mọi adapter mediation), Meta Audience Network và AppLovin MAX khi UMP cho phép
(`ConsentInformation.canRequestAds()`). Trang Get Started của Google Mobile Ads Android:

> "If you need to obtain consent from users in the European Economic Area (EEA), the United
> Kingdom (UK), or Switzerland, set any request-specific flags … or otherwise take action before
> loading ads, ensure you do so before initializing Google Mobile Ads SDK (Legacy)."
>
> "Ads may be preloaded by Google Mobile Ads SDK (Legacy) or mediation partner SDKs upon
> initialization."

("Legacy" là tên Google đặt cho `play-services-ads`, bản SDK này dùng. Trang đọc ngày 2026-09-23:
<https://developers.google.com/admob/android/quick-start>.)

Tức là chỉ khởi tạo thôi đã có thể tải quảng cáo. Không có ngoại lệ "ngoài EEA thì khởi tạo sớm":
trước khi UMP trả lời, app chưa biết người dùng ở vùng nào.

```kotlin
// Trong Activity đầu tiên, KHÔNG phải trong Application:
AdsMobMy.instance.startSdkInit(applicationContext)   // gọi lúc nào cũng được, SDK tự chờ UMP
ConsentManager.request(this) { tier ->
    // tier: GRANTED / DENIED / PARTIAL / UNKNOWN
}
```

- Người dùng đã trả lời ở phiên trước: UMP lưu câu trả lời, nên mạng khởi tạo ngay lúc
  `startSdkInit`, không chờ gì.
- Lần mở đầu tiên: mạng khởi tạo khi UMP cập nhật xong hoặc khi form đóng. Lệnh load đến trước đó
  được giữ lại và chạy sau.
- App không chạy UMP: câu trả lời không bao giờ có, nên **không có quảng cáo nào**.

App muốn tự quyết lúc hiện form thì dùng `ConsentManager.updateConsentInfo(activity) {}` (cập nhật,
không hiện form) và `ConsentManager.canStartAdSdks(context)` (câu trả lời UMP đã lưu). Đừng dùng
`ConsentManager.canRequestAds()` cho việc này: nó trả `true` khi UMP chưa khởi tạo.

SDK **không tự chạy** luồng consent. Nó cần `Activity` đang hiển thị và có thể mở dialog toàn màn
hình — tự bật trong `Application.onCreate()` sẽ khiến form consent nhảy ra đè lên splash của
studio, ở thời điểm họ không kiểm soát được.

Từ 1.2.0 luồng chạy một lần cho mỗi process: gọi lại khi Activity được tạo lại không mở form
thứ hai. Việc chỉ được làm sau consent (thu thập analytics, Install Referrer) đặt trong `onDone`:

```kotlin
ConsentManager.gatherConsent(this) {
    if (ConsentManager.isConsentResolved(applicationContext)) {
        FirebaseAnalytics.getInstance(applicationContext).setAnalyticsCollectionEnabled(true)
    }
    InstallReferrerHelper.captureOnce(applicationContext)
}
```

**Meta SDK (app events, attribution cho chiến dịch Meta):** app tự khai
`com.facebook.android:facebook-core` (SDK chỉ `compileOnly`) và gọi
`(application as AppPluginBase).initMetaSdkAfterConsent()`. Hàm này cũng tự chờ UMP như
`startSdkInit`, và không làm gì khi `facebook_app_id` trống hay còn placeholder.

Nút "Quyền riêng tư" trong Settings chỉ hiện khi `ConsentManager.isPrivacyOptionsRequired(context)`,
và mở form bằng `ConsentManager.showPrivacyOptions(activity)`.

Consent chi phối brain và các bề mặt gửi dữ liệu ra ngoài (CRM, offerwall) theo đúng ranh
giới GDPR:

| Tầng | Tích luỹ hồ sơ | Cá nhân hoá | Guardrail | Decision log |
|---|---|---|---|---|
| `UNKNOWN` (chưa cấu hình) | ✅ | ✅ | ✅ | ✅ |
| `GRANTED` | ✅ | ✅ | ✅ | ✅ |
| `PARTIAL` | ❌ | ✅ | ✅ | ✅ |
| `DENIED` | ❌ | ❌ | ✅ | ✅ |

Hai điều đáng chú ý ở bảng này:

1. **`UNKNOWN` giữ nguyên hành vi cũ.** Một boolean không phân biệt được "đã TỪ CHỐI" với
   "chưa ai hỏi". Coi mọi người là đã từ chối sẽ khiến studio ngoài EEA mất personalization
   khi nâng SDK — một hồi quy im lặng do chính SDK bịa ra.
2. **Guardrail chạy ở mọi tầng.** Nếu consent tắt luôn guardrail thì người từ chối theo dõi
   lại là người bị dội quảng cáo nhiều nhất — ngược hoàn toàn mục đích của quy định.

**COPPA không còn mặc định.** Bản trước hardcode `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` —
SDK tự tuyên bố thay studio. Nay phải khai `childDirected = true/false`; chưa khai thì SDK
không đụng vào cấu hình đó và báo cáo tự kiểm tra sẽ cảnh báo. `childDirected = true` chặn
CẢ tín hiệu CRM lẫn lượt mở offerwall dù consent GRANTED (COPPA).

### 2c. Guardrail theo tiến độ (tuỳ chọn, khuyến nghị)

Chắt lọc từ teardown một game puzzle production — xem [doc 48](48_TEARDOWN_LEARNINGS_PUZZLE_GAME.md).
Mặc định TẮT; bật qua policy artifact khối `progression`, không cần release:

```json
"progression": {
  "min_level_for_interstitial": 10,
  "min_level_for_interstitial_after_loss": 22,
  "min_minutes_after_rewarded": 2,
  "skip_first_interstitials_on_reopen": 1,
  "max_iap_offers_24h": 2
}
```

Muốn nhánh thua có ngưỡng riêng thì phải truyền `DecisionPoint.LEVEL_FAIL` khi hỏi brain —
nếu không, SDK xử lý mọi cơ hội như nhánh thắng.

### 3. Không phải sửa gì ở luồng quảng cáo

`AdsHelper.showInterstitial()` đã tự hỏi brain và tự chốt kết quả. Giữ nguyên lời gọi hiện có;
việc duy nhất phải thêm là `startSdkInit` ở mục 2b.

### 4. Nạp tín hiệu hành vi (tuỳ chọn, nhưng nên có)

```kotlin
AdsBrain.onLevelFail()                    // thua một màn
AdsBrain.onLevelWin()
AdsBrain.onRewardedOffered(accepted = …)  // MỜI rewarded — cả khi bị từ chối
if (AdsBrain.shouldOfferIap(DecisionPoint.SHOP_OPEN)) { … AdsBrain.onIapOfferShown() }
```

Không nạp thì brain vẫn chạy nhưng phân khúc đứng ở mức trung tính — nó không có gì để phân
biệt người chơi này với người chơi khác.

**Lượt mua không cần báo thủ công**: `IapHelper` tự gọi `AdsBrain.onPurchase()`.
**Trạng thái subscription không cần báo thủ công**: `IapHelper` tự nạp snapshot vào
`SubscriptionLifecycle.observe()` mỗi lần truy vấn purchase.

## C.5. Tám bề mặt monetization — mỗi bề mặt một khoá Remote Config

Tất cả đã ship (docs 49–58), đều theo cùng một hình dạng: **khoá JSON tự nạp khi fetch →
cấu hình hỏng giữ bản đang chạy → `enabled=false` là công tắc tắt khẩn cấp → SDK trả dữ
liệu/quyết định, app vẽ và trao.** Phần này chỉ nói app phải gọi gì; thiết kế chi tiết nằm
ở doc riêng của từng bề mặt.

### C.5.1 Paywall — doc 49

```kotlin
val p = PaywallManager.paywallFor(placement = "level_fail", playerId = userId)
if (p != null) {
    // Vẽ paywall với p.products (đúng thứ tự của biến thể), làm nổi bật p.highlight nếu có.
    // Gắn p.variantId vào mọi impression/purchase để đo chuyển đổi theo biến thể.
    PaywallManager.onPaywallShown(p)  // BẮT BUỘC ngay khi paywall hiện — xem dưới
} else {
    // Holdout HOẶC paywall tắt — KHÔNG vẽ gì cả. Chính lượt bỏ qua này là dữ liệu đo
    // "có paywall so với không có paywall" (điều không vendor nào đo được — doc 49).
}
```

`onPaywallShown(p)` thiếu thì hai thứ hỏng cùng lúc: (1) tỉ lệ chuyển đổi mất mẫu số —
con số "bao nhiêu lượt mua" không nói lên điều gì khi không biết bao nhiêu lượt đã hiện;
(2) bộ đếm guardrail của brain (trần mỗi phiên, cooldown, trần 24 giờ) không biết paywall
vừa hiện, và lượt chào bán tiếp theo sẽ lọt qua — paywall do game tự vẽ trở thành cửa sau
lách qua chính bộ bảo vệ vừa chặn nó ở `paywallFor`. Khi brain tắt, lời gọi này là no-op,
vẫn nên gọi.

### C.5.2 Purchase router đa kênh — doc 50

```kotlin
// Thay IapHelper.purchase(...) bằng MỘT lời gọi — kênh do khoá purchase_routing_android chọn:
PurchaseRouter.purchase(activity, "coin_pack") { result ->
    when (result) {
        is IapResult.WebCheckoutOpened -> openBrowser(result.url) // app tự mở, SDK không mở
        is IapResult.Success -> { /* Play Store: entitlement đã cấp tự động */ }
        else -> { /* Pending / UserCanceled / AlreadyOwned / Failed */ }
    }
}
```

**Entitlement web shop KHÔNG tự cấp.** SDK không thể xác thực một lượt mua web từ phía
client — studio phải có server nhận webhook từ vendor thanh toán, xác thực, rồi gọi:

```kotlin
PurchaseRouter.grantFromServer(
    productId = "coin_pack",
    orderId = orderIdFromWebhook,       // dedup an toàn khi webhook retry
    priceMicros = priceMicrosFromVendor, // NGUYÊN BẢN — SDK không bao giờ bịa tỉ giá
    currencyCode = "EUR"
)
```

Hướng an toàn: cấu hình hỏng, URL không https, quốc gia ngoài danh sách — mọi hư hỏng rơi
về Play Store, không bao giờ rơi vào kết quả chết.

### C.5.3 Vòng đời subscription — doc 51

Không phải gọi gì để phân loại trạng thái — `IapHelper` tự observe. App chỉ đọc:

```kotlin
when (SubscriptionLifecycle.stateFor("premium_monthly")) {
    SubState.IN_GRACE_PERIOD -> { /* thanh toán thất bại nhưng Google đang giữ hộ —
                                     vẫn cho dùng, hiện nút sửa phương thức thanh toán */ }
    SubState.LAPSED          -> { /* hết grace thật sự */ }
    ...
}
if (SubscriptionLifecycle.dunningNoticeDue("premium_monthly")) {
    // App TỰ VẼ thông báo theo cadence 1-2-4; SDK chỉ đếm lượt
}
if (SubscriptionLifecycle.isWinBackEligible("premium_monthly")) {
    // Người từng sub nay đã lapse — mời quay lại với ưu đãi (mảnh Google không có native)
}
SubscriptionLifecycle.winBackEligibleProductIds() // cả bộ product id đủ điều kiện win-back —
                                                  // quét một lượt để gắn badge store chẳng hạn
SubscriptionLifecycle.managementUriFor("premium_monthly", packageName) // URI mở trang quản lý sub
```

### C.5.4 Battle pass — doc 52

```kotlin
BattlePass.addXp(120)                                  // game sở hữu XP — tự gọi khi người chơi kiếm XP
val tier = BattlePass.currentTier()
val phase = BattlePass.phase()                         // NOT_STARTED / ACTIVE / ENDED / NO_SEASON —
                                                       // game dựa vào đây để đổi UI theo giai đoạn mùa
if (BattlePass.isPremiumActive()) { /* premium pass mùa này còn hiệu lực */ }
for ((t, track) in BattlePass.claimable()) {
    val r = BattlePass.claim(t, track)                 // chống trùng theo (mùa, bậc, track)
    if (r.claimed) grantItems(r.rewards)               // SDK trả dữ liệu thưởng, GAME trao vật phẩm
}
```

Premium là IAP non-renewing theo mùa (mỗi mùa một product id trong cấu hình) — entitlement mùa
cũ tự hết giá trị, không cần code xoá quyền. Cấu hình hỏng/bậc sai thứ tự → từ chối giữ mùa
đang chạy.

### C.5.5 Grant định kỳ (VIP ↔ vật phẩm) — doc 53

```kotlin
for (due in RecurringGrants.dueGrants()) {
    val r = RecurringGrants.claim(due.grant.productId, due.grant.rewardType)
    if (r.claimed) grant(r.rewardType, r.rewardAmount) // KHÔNG truy lĩnh: vắng 10 ngày vẫn chỉ 1 chu kỳ
}
```

Chu kỳ tính từ lần claim trước (không theo ngày dương lịch); sở hữu = `Entitlements` HOẶC
subscription ACTIVE — sub lapse là tắt đặc quyền ngay.

### C.5.6 Offerwall — doc 54

SDK giữ interface, game (hoặc một module adapter riêng) implement vendor cụ thể — SDK không
chứa dependency vendor nào (bài học ironSource đóng direct demand 30/4/2026):

```kotlin
OfferwallManager.register(object : OfferwallManager.OfferwallProvider {
    override val providerId = "tapjoy"
    override fun isAvailable() = tapjoySdk.isOfferwallReady
    override fun show(onWallClosed: () -> Unit) { tapjoySdk.showWall { onWallClosed() } }
})

val walls = OfferwallManager.availableProviders()      // provider mở được NGAY (qua mọi gate) —
                                                       // game dựa vào đây quyết định có vẽ nút không
val r = OfferwallManager.show("tapjoy") { /* onWallClosed: tường đã đóng */ }
// ghi impression; consent DENIED / childDirected bị chặn — r.reason nói rõ lý do từ chối
// Thưởng về SAU (vendor xác nhận S2S kể cả khi tường đã đóng) — qua server của studio:
OfferwallManager.reportCompletion("tapjoy", currencyEarned = 500, transactionId = txFromServer)
// SDK chống trùng theo (provider, transaction) rồi trả dữ liệu — game tự trao, không trao hai lần
```

### C.5.7 Quyết định cross-modal — doc 55

Tại CÙNG một điểm quyết định, hỏi brain nên mời quảng cáo thưởng hay chào bán:

```kotlin
val d = AdsBrain.chooseCrossModal(
    decisionPoint = DecisionPoint.LEVEL_COMPLETE,
    allowRewarded = true, allowIapOffer = true,
    baseline = ActionMode.REWARDED_AD   // hành vi của game khi CHƯA có brain
)
when (d.action) {
    ActionMode.REWARDED_AD -> showRewardedPrompt()
    ActionMode.IAP_OFFER   -> showShopPrompt()
    else                   -> {}        // NO_ACTION
}
if (d.isHoldout) logExperimentSkip()   // nhóm đối chứng — nhận baseline xác suất 1.0
```

Bế tắc không chào bán, rewarded chỉ sau fail thật, circuit breaker 3 lượt mua/phiên — guardrail
đạo đức là filter CỨNG trong không gian đa arm, kể cả với holdout.

### C.5.8 Cổng assist — full action space — doc 58

```kotlin
val d = AdsBrain.chooseAssist(
    decisionPoint = DecisionPoint.LEVEL_FAIL,
    allowFreeGift = true, allowDifficultyTuning = true,
    baseline = ActionMode.NO_ACTION
)
when (d.action) {
    ActionMode.FREE_ACTION     -> showFreeHintButton()
    ActionMode.GAMEPLAY_CONFIG -> applyEasedDifficulty()
    else                       -> {}
}
// Nếu thật sự đưa ra:
AdsBrain.onAssistShown()      // ghi bộ đếm trần
AdsBrain.onAssistUsed(didUse) // chốt giá trị
```

Cố ý KHÔNG có arm IAP_OFFER ở cổng này — cứu người bế tắc bằng thứ phải trả tiền là khai
thác frustration. Trần 3 hỗ trợ/phiên (khoá `gameplay.max_assists_per_session`, kẹp ≤ 8).

### C.5.9 Cầu nối CRM — doc 56 (mặc định TẮT)

Dữ liệu engagement chảy RA NGOÀI phải là quyết định có chủ đích — không tự bật:

```kotlin
// Khoá crm_bridge_android phải có enabled=true, rồi:
CrmBridge.register(object : CrmBridge.CrmSink {
    override val sinkId = "braze"
    override fun deliver(signal: CrmBridge.EngagementSignal): Boolean {
        // Hai tín hiệu ĐÃ CHUẨN HOÁ — ánh xạ sang khái niệm của CRM nào là việc của adapter:
        return when (signal.kind) {
            CrmBridge.SignalKind.SEGMENT_CHANGE -> braze.log("segment_change", signal.playerId, signal.detail)
            CrmBridge.SignalKind.FIRST_PURCHASE -> braze.log("first_purchase", signal.playerId, signal.detail)
        }
    }
})
```

Chỉ hai tín hiệu có dữ liệu thật: `SEGMENT_CHANGE` (latent type thật sự dịch chuyển) và
`FIRST_PURCHASE` (đúng một lần). Mỗi tín hiệu mang cờ `isHoldout` để studio loại nhóm đối
chứng khỏi chiến dịch — hook incrementality dùng chung UA + monetization (doc 57).

## D. Báo cáo tự kiểm tra — nhìn cái này đầu tiên

`configure()` trả về một `Report` bắt những cấu hình sai mà **hệ thống không tự báo lỗi** —
loại nguy hiểm nhất, vì mọi thứ trông như đang chạy:

| Cảnh báo | Vì sao nó nguy hiểm |
|---|---|
| `brainEnabled` nhưng `rolloutFraction = 0` | no-op im lặng, không dấu hiệu nào để chẩn đoán |
| id trong `consumableProducts` không có trong danh sách bán | gõ nhầm → không bao giờ consume → người dùng không mua lại được |
| id trong `removeAdsProducts` không có trong danh sách bán | subscriber vẫn thấy quảng cáo dù đã trả tiền |
| có subscription mà không khai `removeAdsProducts` | như trên |
| có in-app mà không khai `consumableProducts` | gói tiêu hao chỉ mua được một lần |

Báo cáo cũng nói rõ **chưa có training service**, để không ai tưởng brain đang tự cải thiện.

## E. Thứ tự áp dụng — vì sao nó thuộc về code

Hai ràng buộc thứ tự, cả hai đều **im lặng khi sai**:

1. **`Entitlements` trước `IapHelper.init()`.** `init()` kết nối rồi tự khôi phục giao dịch
   ngay. Nếu danh sách sản phẩm tắt quảng cáo chưa khai báo lúc đó, lượt khôi phục đầu tiên
   tính ra `hasRemoveAds() = false` cho một subscriber thật.

2. **`rolloutFraction` trước `enabled`.** Ngược lại sẽ có một khoảng brain chạy ở 100% dân số.
   Trên app thật, khoảng đó đủ phục vụ hàng nghìn quyết định.

Không ai đọc tài liệu đủ kỹ để nhớ hai điều này, và sai thì không có gì báo. Vì vậy
`MonetizationSdk` áp chúng theo thứ tự cứng, và có unit test khoá lại thứ tự đó.

## F. Quy trình bật brain trên dân số thật

```text
1. Để brainEnabled = false. Ship. Xác nhận không có gì đổi.
2. brainEnabled = true, rolloutFraction = 0.05. Ship.
3. Theo dõi ≥ 2 tuần, so sánh nhóm treatment với holdout (mặc định 5%).
   Xem CẢ BỐN North Star, đặc biệt ComfortIndex — không chỉ ARPDAU.
4. Nâng dần 0.05 → 0.2 → 1.0 qua policy artifact, KHÔNG cần release app.
```

Bước 3 cần ít nhất 2 tuần vì hiệu ứng lên retention cần thời gian bộc lộ. Kết luận sớm hơn
gần như chắc chắn là đọc nhiễu. Bản ghi phơi nhiễm thí nghiệm (doc 57) phát mỗi phiên một
hàng cho MỌI player khi thí nghiệm chạy — kể cả nhóm đối chứng — nên phép so sánh có đủ dữ
liệu membership ngay khi backend nhập liệu hoạt động.

**Lùi khẩn cấp, không cần release** — đẩy vào Remote Config key `brain_policy_android`:

```json
{"version":"panic","experiment":{"rollout_fraction":0}}
```

Brain thôi điều khiển mọi player ngay lần fetch kế tiếp; luồng quảng cáo về đúng luật tĩnh cũ.
Mỗi bề mặt khác có công tắc riêng trong khoá JSON của nó (`"enabled": false`) — cùng hình dạng.

## G. Giới hạn phải nói với studio

1. **Chưa có training service** — brain học cục bộ trên từng thiết bị; backend ingestion +
   North Star dashboard đã có code (146/146 test) nhưng chưa deploy.
2. **Chưa xác thực biên lai phía server** — quyền lợi IAP chỉ nên dùng cho `removeAds`, không
   dùng cấp vật phẩm có giá trị quy đổi. Biên nhận Play Billing (`purchase_token`) đã chảy trong
   log pipeline nên backend có đầu vào để xác thực; cơ chế xác thực đã xong
   (`play_verify.py` + `GET /verify/purchases` — chưa cấu hình credential thì 503 nói thẳng),
   phần còn lại là deploy với service account thật (đối soát gian lận trên token —
   `GET /reconcile/purchases` — đã chạy được ngay).
   Entitlement web shop bắt buộc qua server của studio (`grantFromServer`).
3. **ComfortIndex mới có 2/5 thành phần** (`isTrustworthy()` luôn false) — theo dõi xu hướng
   được, ra quyết định thì chưa.
4. **Hằng số phân khúc và các trần (3 quảng cáo/phiên, 3 hỗ trợ/phiên…) chưa calibrate** từ
   dữ liệu thật; chúng là giả định bảo thủ từ domain knowledge.
5. **Chưa chạy trên traffic thật** — mọi khẳng định đến từ 508 unit test, không từ production.
6. **Chưa có iOS.**
7. **`PARTIAL` chưa tự phát hiện được.** UMP chỉ cho biết luồng consent đã hoàn tất, không cho
   biết người dùng tick những mục đích nào. Muốn biết phải đọc và phân tích chuỗi TCF
   (`IABTCF_*` trong SharedPreferences) — chưa làm, nên `PARTIAL` hiện chỉ đặt được thủ công.
8. **Chưa có SKAN/AAK conversion value** (doc 23 mục C.5).
9. **Offerwall và CRM cần game implement adapter** (`OfferwallProvider`, `CrmSink`) — SDK cố ý
   không chứa dependency vendor nào.
10. **Chưa có server-side entitlement SSoT thật và chưa có job đối soát bucket** (doc 57 E.3) —
    cả hai thuộc milestone backend.
11. **On-device inference và social prompt chưa có mặt** (doc 58 mục H) — dài hạn, ghi rõ chứ
    không giấu.
12. **Lớp `ads/` mới có test cho phần logic thuần.** Đã phủ waterfall eCPM (`AdsEcmps`) và
    parser cấu hình placement — hai chỗ hỏng im lặng nhất. Phần còn lại (13.400+ dòng adapter
    cho AdMob/MAX/FB, vòng đời view, callback SDK) chưa có, và cần Robolectric hoặc
    instrumentation test trên thiết bị mới phủ được — không phải việc thêm vài test JVM là xong.

## Không xác minh được (ghi rõ)

1. SDK chưa được tích hợp vào một app thật nào ngoài app demo trong repo này. Đường đi
   Firebase Remote Config → `BrainPolicy` và 8 bề mặt chưa chạy trên thiết bị thật với
   Firebase Console thật.
2. Giao dịch IAP chưa test trên Play Console (cần app đã publish + tài khoản tester).
3. Con số "brain chỉ siết chặt, không nới lỏng" là tính chất của **cấu trúc code** (cổng brain
   nằm sau mọi cổng tĩnh) chứ chưa được đo bằng A/B trên traffic thật.
