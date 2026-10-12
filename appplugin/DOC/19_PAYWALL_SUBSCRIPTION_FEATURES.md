# 19 — Bộ tính năng paywall & subscription mà thế giới đang áp dụng

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Toàn bộ feature set của lớp paywall/subscription SDK (RevenueCat, Adapty, Superwall, Apphud, Purchasely, Qonversion, và các vendor nhỏ hơn); platform features của App Store & Google Play 2025–2026 mà các SDK này wrap; góc nhìn gaming-specific (battle pass, VIP, season pass); và checklist tính năng mà SDK của chúng ta cần match hoặc vượt.

## Evidence labels

- **[Primary docs]**: docs/website chính thức của vendor hoặc Apple/Google developer documentation.
- **[Case study — self-reported]**: số liệu do vendor/studio tự công bố (benchmark report, blog post).
- **[Conference]**: talk/deck hội nghị (WWDC, Google I/O, GDC).
- **[Secondary]**: báo chí/analyst/blog bên thứ ba.

---

## A. Bức tranh vendor & mức độ phổ biến

### A.1 Tier 1 — RevenueCat (market leader)

- **Quy mô:** 50,000+ apps đang sử dụng; 115,000+ apps trong dataset benchmark; $16B revenue tracked; 1B+ transactions processed. [Case study — self-reported: RevenueCat State of Subscription Apps 2026]
- **Pricing:** Free tới $2,500 Monthly Tracked Revenue (MTR); trên ngưỡng đó tính 1% của phần vượt. Không có seat license hay per-user fee. [Primary docs — revenuecat.com/pricing]
- **Platform support:** iOS, Android, Web (Stripe), Amazon, Roku — entitlements sync xuyên nền tảng. [Primary docs — revenuecat.com]
- **Tính năng nổi bật:** Paywall Builder v2 (SwiftUI / Jetpack Compose native, WYSIWYG, AI translations, multi-screen flows); Experiments (A/B test tối đa 4 variant, equal weight, 24h data delay); Fraud Protection; Customer Management; 30+ integrations (Amplitude, Braze, Segment, Mixpanel, AppsFlyer, Adjust, Slack…). [Primary docs — revenuecat.com/features; revenuecat.com/changelog]
- **Điểm yếu:** A/B test chỉ 4 variant, equal split, data delay 24h — bị coi là hạn chế so với Adapty/Superwall cho experimentation nâng cao. [Secondary — PkgPulse, BuildMVPFast so sánh 2026]
- **Ai dùng:** Dominant trong giới utility/productivity/fitness/education subscription apps. Game studios lớn ít dùng vì họ đã có backend IAP riêng.

### A.2 Tier 1 — Adapty (fastest growing challenger)

- **Quy mô:** 16,000+ apps trong dataset benchmark; $3B subscription revenue analyzed; 500,000 transaction events; 105,000 paywalls analyzed. [Case study — self-reported: Adapty State of In-App Subscriptions 2026]
- **Pricing (mới 2026):** Free toàn bộ tính năng tới $5,000 monthly revenue; trên ngưỡng đó 1% revenue. Bao gồm tất cả add-ons (Refund Saver, Apple Ads Manager). [Primary docs — adapty.io/blog/adapty-new-pricing-2026]
- **Platform support:** iOS, Android, React Native, Flutter, Unity, Web. [Primary docs — adapty.io]
- **Tính năng nổi bật:** No-code Paywall Builder; A/B testing không giới hạn variant và traffic split; Predictive Analytics (LTV prediction); Paywall Library (10,000+ real-world paywall screens từ top apps); Revenue Analytics chi tiết; Receipt Validation server-side; Rules engine tự động hóa subscription workflows. [Primary docs — adapty.io]
- **Báo cáo benchmark:** "State of In-App Subscriptions 2026" — 95% revenue thuộc top 10% apps; 44.5% users reject paywall ở thời điểm nào đó; European users trả nhiều hơn cho subscription apps. [Case study — self-reported: adapty.io/state-of-in-app-subscriptions]
- **Ai dùng:** Phổ biến cả apps lẫn một số game studios (có Unity SDK). Đang tăng trưởng nhanh trong giới indie và mid-tier.

### A.3 Tier 2 — Superwall (paywall-first experimentation platform)

- **Vị trí:** Tập trung vào lớp presentation/experimentation (paywall builder + A/B test + campaigns), thường được dùng **kết hợp** RevenueCat/Adapty cho subscription infrastructure. [Secondary — buildmvpfast.com, pkgpulse.com]
- **Pricing:** Free tới $10,000 Monthly Attributed Revenue (MAR) — chỉ tính revenue từ paywall, không tính toàn bộ app revenue; 250 conversions/tháng free, $0.20/conversion vượt; subscription infrastructure (entitlements, webhooks, SQL access) free ở mọi scale. Startup $49/mo + 1% MAR; Growth $199/mo + 1% MAR. [Primary docs — superwall.com/pricing; superwall.com/blog/superwalls-new-pricing]
- **Tính năng nổi bật:** Visual Paywall Editor; Campaigns & trigger rules (khi nào hiện paywall cho user nào); A/B testing không giới hạn variant; Surveys; Localization; AI-powered features; SQL data access. [Primary docs — superwall.com/features]
- **Điểm mạnh:** Pricing model chỉ charge trên revenue paywall tạo ra (attributed), không phải toàn bộ MTR. Rất hấp dẫn cho apps đã có RevenueCat. [Secondary]
- **Ai dùng:** Chủ yếu utility/lifestyle/subscription apps; indie devs.

### A.4 Tier 2 — Apphud

- **Vị trí:** All-in-one growth infrastructure cho subscription apps; open-source SDKs. [Primary docs — apphud.com]
- **Pricing:** Free $0/mo với $10,000 MTR; Pro $49/mo; Expert $59/mo; Enterprise custom. [Primary docs — apphud.com/pricing]
- **Platform support:** iOS/macOS (Swift), Android (Kotlin), React Native, Flutter, Unity. [Primary docs — docs.apphud.com]
- **Tính năng:** Subscription management (trials, conversions, cancellations, renewals); Real-time analytics (ARPU/ARPPU); Pricing A/B testing; Rules engine; 20+ integrations (AppsFlyer, Adjust, Facebook, Mixpanel); Custom User Properties; Apple Receipt Checker. [Primary docs — docs.apphud.com]
- **Điểm mạnh:** Free tier hào phóng nhất ($10K MTR); open-source SDKs; 99.9% uptime claim. [Primary docs]

### A.5 Tier 2 — Purchasely

- **Vị trí:** "App experience solution" — tập trung vào no-code paywall builder và dynamic paywall rendering. [Primary docs — purchasely.com]
- **Tình trạng M&A:** **Không tìm thấy bằng chứng** Purchasely bị Airbridge mua lại. Hai công ty vẫn độc lập. Airbridge là MMP (attribution/measurement), Purchasely là paywall/subscription SDK — hai lớp khác nhau. [Nghiên cứu này xác minh qua StartupIntros, Tracxn, LinkedIn Purchasely — không có record acquisition nào.]
- **Tính năng:** No-Code Paywall Builder (customizable templates); A/B & multivariate testing; Dynamic paywall via metadata variables; Custom paywall rendering; Subscription analytics; Promotion rules. [Primary docs — purchasely.com/product; docs.purchasely.com]
- **Platform:** iOS, Android, Flutter. [Primary docs]

### A.6 Tier 3 — Qonversion, Appflow.ai, IAPHUB, MavenHandle

| Vendor | Đặc điểm | Pricing | Evidence |
|---|---|---|---|
| **Qonversion** | Subscription Management SDK; No-Code Builder 2.0 (2025); A/B Experiments với confidence stats; cross-platform (iOS/Android/Web JS SDK) | Custom | [Primary docs — qonversion.io] |
| **Appflow.ai** | No-code A/B testing cho paywall; newer entrant | — | [Secondary — appflow.ai] |
| **IAPHUB** | Lightweight subscription SDK; Flutter support | — | [Primary docs — iaphub.com] |
| **MavenHandle** | Unify iOS & Android entitlements on one backend | — | [Secondary — mavenhandle.com] |

### A.7 Gaming-specific: Xsolla, PlayFab, Unity IAP

- **Xsolla:** Full monetization suite (payments, subscriptions, battle pass, login, catalog, offerwall); 1,000+ local payment methods; headless checkout SDK; Subscription Management; LiveOps Suite (tailored offers, live events, CRM — claim +30% revenue từ tailored offers); "Payment Service Provider of the Year" 2025. [Primary docs — xsolla.com; Case study — self-reported]
- **Azure PlayFab:** Backend cho live games (user accounts, inventories, virtual currency, economy, progression); không phải IAP processor nhưng manage game economy & battle pass tiers. [Primary docs]
- **Unity IAP:** Native Unity monetization; consumables, non-consumables, subscriptions; had compatibility issues with Google Play Billing Library v7+ (deadline August 2025, extended to August 2026). [Primary docs — docs.unity.com; Secondary — Unity Discussions]

---

## B. Bộ tính năng chuẩn mà thế giới đã standardize

### B.1 No-code / Remote-configured Paywall Builder

Tất cả Tier 1 & Tier 2 vendors đều cung cấp:
- **Visual/WYSIWYG editor** cho paywall screens (Adapty, Superwall, RevenueCat v2, Purchasely, Qonversion 2.0). [Primary docs]
- **Remote configuration:** thay đổi paywall content, pricing display, copy, images mà không cần app release. [Primary docs]
- **Multi-screen flows:** onboarding → feature intro → social proof → paywall (Blinkist-style). [Secondary — RevenueCat blog]
- **Native rendering:** SwiftUI (iOS) + Jetpack Compose (Android) — không phải WebView. [Primary docs — RevenueCat Paywall Builder v2]
- **AI-powered translations:** RevenueCat v2 có built-in AI translation. [Primary docs]
- **Template libraries:** Adapty có 10,000+ real-world paywall screens; Superwall có curated templates. [Primary docs]

**Status:** Đây là table stakes 2026. Không có vendor nào thiếu tính năng này.

### B.2 Paywall Placement & Trigger Rules

- **Campaign-based triggers:** Superwall cho phép define "campaigns" — rules xác định khi nào và cho ai hiện paywall (ví dụ: sau 3 sessions, sau khi dùng feature X, sau N ngày). [Primary docs — superwall.com/features/campaigns]
- **Rules engine:** Adapty và Apphud có rules engine tự động hóa workflows dựa trên subscriber events. [Primary docs]
- **Placement flexibility:** paywall có thể được trigger từ nhiều điểm trong app (onboarding completion, feature gate, settings, inline prompt). [Primary docs]

### B.3 A/B Testing & Multivariate Testing

| Vendor | Variant limit | Traffic split | Data delay | Statistical engine |
|---|---|---|---|---|
| **RevenueCat** | 4 variants | Equal only | 24h | Built-in confidence |
| **Adapty** | Unlimited | Flexible % | Real-time | Built-in |
| **Superwall** | Unlimited | Flexible | Real-time | Built-in |
| **Qonversion** | Unlimited | Flexible | Real-time | Confidence stats built-in |
| **Apphud** | Multiple | — | — | — |

[Primary docs từ mỗi vendor; Secondary — pkgpulse.com, buildmvpfast.com so sánh 2026]

**Testing scope:** Không chỉ paywall design mà còn: pricing tiers, copy/CTA, images, offer types (free trial vs. discount vs. hard paywall), onboarding flows, placement timing. [Primary docs]

### B.4 Onboarding & Trial-flow Optimization

- **Onboarding-to-paywall flow:** Multi-screen sequence giới thiệu app value trước khi hiện paywall. RevenueCat v2 hỗ trợ native multi-screen flows. [Primary docs]
- **Trial eligibility detection:** SDK tự động detect user có eligible cho introductory offer / free trial hay không (dựa trên subscription history của Apple/Google). [Primary docs — Apple StoreKit, Google Play Billing]
- **Trial conversion tracking:** Tất cả vendors track trial → paid conversion rate. [Primary docs]
- **Adapty finding:** 44.5% users reject paywall — nhấn mạnh cần optimize onboarding flow trước paywall. [Case study — self-reported: Adapty 2026]

### B.5 Introductory Offers & Free Trial Logic

**App Store (iOS):**
- **Introductory Offers:** free trial hoặc discounted price cho new/first-time subscribers; configure trong App Store Connect; StoreKit API `isEligibleForIntroOffer` tự động check eligibility. [Primary docs — developer.apple.com/documentation/storekit/implementing-introductory-offers-in-your-app]
- **Promotional Offers:** discounted price cho existing active subscribers (để reduce churn). [Primary docs]
- **Offer Codes:** single-use (18-char unique) hoặc reusable; max 10 active offers/subscription; 1,000,000 redemptions/app/quarter. Apple đã **khai tử traditional promo codes cho IAP từ 26/03/2026**, chuyển hoàn toàn sang offer code system. [Primary docs — developer.apple.com; Secondary — super-apps.ai]
- **Win-Back Offers (WWDC 2024):** free/discounted offer cho churned/lapsed subscribers — configure trong App Store Connect; chỉ hiện cho users đã cancel subscription. [Primary docs — developer.apple.com/documentation/storekit/supporting-win-back-offers-in-your-app; WWDC24 session 10110]

**Google Play:**
- **Free Trial:** configure trên base plan hoặc offer; Google tự track eligibility. [Primary docs — developer.android.com/google/play/billing/subscriptions]
- **Introductory Pricing:** discounted price cho first billing period. [Primary docs]
- **Promotions:** configure trong Play Console. [Primary docs]

**SDK wrapping:** Tất cả Tier 1 SDKs wrap logic này — SDK tự detect eligibility, hiển thị đúng offer cho đúng user, handle redemption flow. [Primary docs]

### B.6 Price Localization & Elasticity Experiments

- **Apple Price Tiers:** App Store hỗ trợ 900+ price points across 175 regions; auto-convert từ base price. [Primary docs]
- **PPP (Purchasing Power Parity) pricing:** Xu hướng mạnh 2025–2026 — điều chỉnh giá theo sức mua địa phương. Mirava.io report: PPP adjustments có thể tăng revenue 15–122% trong 1 tháng cho subscription apps. PPP Price Planner app (Apple App Store) tự động localize cho 175+ countries. [Secondary — mirava.io; subscriptionindex.com]
- **Elasticity experiments:** RevenueCat blog có hướng dẫn chi tiết "A/B Price Testing Guide for Mobile Apps" — test nhiều price points đồng thời để tìm optimal pricing. Adapty cũng hỗ trợ price A/B testing. [Primary docs — revenuecat.com/blog/engineering/mobile-app-a-b-price-testing-guide]
- **Regional pricing benchmarks:** Adapty State of In-App Subscriptions 2026 report có LTV data theo category và region. [Case study — self-reported]

### B.7 Billing Failure Recovery / Dunning

**Apple App Store:**
- **Billing Grace Period:** configurable period (developer set) mà subscriber vẫn access content khi payment fail; Apple retry charge trong thời gian này. [Primary docs — developer.apple.com/help/app-store-connect/manage-subscriptions/enable-billing-grace-period]
- **Billing Retry Period:** Apple tự động retry payment trong **60 ngày** kể từ failure date. [Primary docs — developer.apple.com/documentation/storekit/reducing-involuntary-subscriber-churn]

**Google Play:**
- **Grace Period:** lên tới 30 ngày; **enabled by default** trên tất cả auto-renewing base plans; user vẫn access subscription. Google retry payment trong grace period. [Primary docs — developer.android.com/google/play/billing/lifecycle/subscriptions]
- **Account Hold:** sau grace period nếu payment vẫn fail → subscription vào account hold → user **mất entitlement**; từ **12/2025**, default duration = 60 ngày trừ grace period length (ví dụ grace 7 ngày → account hold ~53 ngày). Developer có thể adjust/disable từ Play Console. [Primary docs — support.google.com/googleplay/android-developer/answer/16631229]
- **Real-time Developer Notifications (RTDN):** recommended để detect grace period & account hold transitions. [Primary docs]

**SDK dunning features:**
- RevenueCat, Adapty track billing retry status và expose qua webhook/API. [Primary docs]
- Best practice industry: variable retry schedule (3→5→7 ngày), 4–8 attempts trong 15–30 ngày, smart dunning (proactively refresh expiring cards). 58% failed payments recovered qua smart retry scheduling. Subscription businesses mất ~9% MRR vì failed payments. [Secondary — Recurly 2025, Chargebee, Solidgate]

### B.8 Win-back Offers & Churned-subscriber Discounts

- **Apple Win-Back Offers (WWDC 2024):** targeted free/discounted offers cho lapsed subscribers; configure trong App Store Connect; chỉ hiện cho users đã churn. [Primary docs — WWDC24 session 10110; developer.apple.com]
- **Google Play:** không có native "win-back offer" tương đương; developer phải tự implement logic (detect churned user, show custom offer). [Primary docs]
- **RevenueCat & Adapty:** track win-back offer performance; RevenueCat có beginner's guide chi tiết. [Primary docs — revenuecat.com/blog/growth/guide-to-apple-win-back-offers]
- **Email/push win-back sequences:** Airbridge blog hướng dẫn build win-back email sequences kết hợp subscription data. [Secondary — airbridge.io]

### B.9 Refund & Chargeback Handling

- **Adapty Refund Saver:** feature đặc biệt giúp retain subscriber sau refund bằng cách offer alternative plan/discount. [Primary docs — adapty.io]
- **Apple/Google refund notifications:** Cả hai platform gửi server notification khi refund xảy ra; SDK wrap logic này để update entitlement. [Primary docs]
- **Chargeback tracking:** RevenueCat fraud protection tự động detect suspicious refund patterns. [Primary docs]

### B.10 Subscription Groups / Upgrades / Downgrades

**App Store:**
- **Subscription Groups:** organize related subscriptions; ranking order determines upgrade/downgrade/cross-grade paths. Upgrade = billed immediately + prorated refund; Downgrade = stay on current plan until next billing cycle; Cross-grade = equal-tier seamless switch. Apple tự động manage billing adjustments. [Primary docs — developer.apple.com/app-store/subscriptions; adapty.io/blog/app-store-subscription-groups]

**Google Play:**
- **Base plans + product change config:** không dùng "groups" giống iOS; developer configure "update policy" cho mỗi plan change (immediate charge vs. at renewal). [Primary docs — developer.android.com/google/play/billing/subscriptions]
- **I/O 2026:** Subscription Management API mới cho phép offer cheaper alternatives để retain users thay vì cancel — aimed at reducing cancellations. [Conference — Google I/O 2026; Secondary — androidheadlines.com 05/2026]

**SDK wrapping:** RevenueCat, Adapty, Qonversion đều expose API cho upgrade/downgrade flow, tự động handle proration logic. [Primary docs]

### B.11 Family Sharing

- **Apple Family Sharing:** up to 6 people share subscriptions (developer opt-in). [Primary docs — support.apple.com/en-us/105062]
- **WWDC 2026 — Group Subscriptions (Multi-Seat Plans):** một user invite others share access — mở rộng hơn Family Sharing truyền thống (không giới hạn family group). [Conference — WWDC26 session 210; Secondary — macrumors.com 06/2026]
- **Google Play:** không có native family sharing cho third-party subscriptions. [Primary docs]

### B.12 Cross-platform Entitlement Sync (iOS/Android/Web)

Đây là **pain point lớn nhất** mà tất cả Tier 1 vendors giải quyết:

- **RevenueCat:** unified entitlement layer across iOS, Android, Web (Stripe), Amazon, Roku. Single customer profile sync subscription state xuyên platform. Blog chi tiết "Cross-platform subscription state" (02/2026). [Primary docs — revenuecat.com/blog/engineering/cross-platform-subscription]
- **Adapty:** Data Synchronization Across Platforms — sync subscription state across iOS, Android, Stripe, any provider via web API. [Primary docs — adapty.io/subscription-sync]
- **Qonversion:** cross-platform SDK (iOS/Android/Web JS) — integrate once, entitlement sync tự động. [Primary docs — qonversion.io/subscription-management]
- **Challenge:** StoreKit không biết gì về Android/web; mỗi platform có purchase model riêng; user có thể double-pay hoặc churn nếu subscription không được recognize khi đổi device. [Secondary — appsops.store, auditbuffet.com]

### B.13 Subscriber Analytics, Cohorts, LTV/Retention Dashboards

| Vendor | Analytics depth |
|---|---|
| **RevenueCat** | Charts, MRR, subscriber counts, retention curves, cohort analysis; App Store Revenue Calculator powered by 115K+ apps dataset |
| **Adapty** | Real-time analytics; ARPU/ARPPU; LTV by category/region; predictive LTV; pricing benchmarks |
| **Apphud** | Real-time ARPU/ARPPU; trial conversion; churn analysis; data export |
| **Qonversion** | Real-time metrics; trial-to-paid conversions; revenue tracking |
| **Superwall** | Paywall-specific analytics (conversion per variant, placement performance) |

[Primary docs từ mỗi vendor]

### B.14 Holdout & Incrementality Measurement

- **Gap đã xác nhận:** Không vendor nào trong nhóm RevenueCat/Adapty/Superwall/Apphud có **built-in holdout group** cho incrementality measurement. A/B testing của họ so sánh variant-vs-variant, không phải treatment-vs-no-exposure. [Nghiên cứu này xác minh qua docs chính thức của RevenueCat Experiments, Adapty A/B testing, Superwall Experiments — không có "holdout" feature]
- **Workaround:** Apps layer external MMPs (AppsFlyer Incrementality, Tenjin, Haus.io, Measured) hoặc tự build holdout mechanism. [Secondary — appsflyer.com/products/measurement/incrementality, haus.io]
- **Amplitude Holdout Groups:** Amplitude Experiment có built-in holdout groups feature. [Primary docs — amplitude.com/docs/feature-experiment/advanced-techniques/holdout-groups]
- **Opportunity:** Đây là white space — SDK nào tích hợp native holdout/incrementality measurement sẽ có lợi thế rõ ràng.

### B.15 Server-side Webhook/Event APIs

Tất cả Tier 1 vendors cung cấp:
- **Webhook endpoints** cho real-time subscription events (new subscription, renewal, cancellation, refund, billing retry, expiration, grace period, account hold). [Primary docs]
- **REST API** cho programmatic access (subscriber lookup, entitlement management, offering configuration). [Primary docs]
- **Server-to-server notifications:** Apple App Store Server Notifications V2; Google RTDN — vendors wrap cả hai. [Primary docs]

### B.16 No-code Integrations

**RevenueCat integrations (30+):** Amplitude, Braze, Segment, Mixpanel, AppsFlyer, Adjust, Slack, Firebase, OneSignal, Airship, Zapier, CleverTap, Insider, Intercom, Customer.io, Iterable, mParticle… [Primary docs — revenuecat.com/docs/integrations]

**Adapty integrations (20+):** Amplitude, Braze, AppsFlyer, Adjust, Mixpanel, Firebase, OneSignal, Segment, Airbridge… [Primary docs — adapty.io]

**Event taxonomy chung:** subscription started, trial started, trial converted, subscription renewed, subscription cancelled, subscription expired, refund issued, billing retry, grace period entered, billing issue resolved. [Primary docs]

---

## C. Platform features 2025–2026 mà SDKs wrap

### C.1 Apple App Store — tính năng mới 2025–2026

| Feature | Năm | Chi tiết |
|---|---|---|
| **Win-Back Offers** | WWDC 2024 | Free/discounted offers cho churned subscribers |
| **Offer Codes thay thế Promo Codes** | 03/2026 | Traditional IAP promo codes retired; chuyển sang offer code system |
| **Price Increase Consent** | 08/2025 | Austria, Germany, Poland: user phải consent nếu price tăng >50% |
| **12-Month Commitment Plans** | WWDC 2026 | Monthly subscription với 12-month commitment → lower monthly price; chưa available tại US/Singapore |
| **Subscription Bundles & Suites** | WWDC 2026 | Cross-developer subscription grouping |
| **Group Purchases (Multi-Seat)** | WWDC 2026 | One user invites others to share subscription |
| **Volume Purchasing** | WWDC 2026 | Enterprise/bulk subscription purchasing |
| **Retention Messaging** | WWDC 2026 | Tools to engage subscribers before cancellation |
| **Billing Grace Period** | Ongoing | Configurable, reduces involuntary churn |
| **Billing Retry** | Ongoing | 60-day automatic retry |

[Primary docs — developer.apple.com; Conference — WWDC25 session 241, WWDC26 session 210; Secondary — macrumors.com 06/2026, techcrunch.com 04/2026]

### C.2 Google Play — tính năng mới 2025–2026

| Feature | Năm | Chi tiết |
|---|---|---|
| **Grace Period default enabled** | 2025 | Auto-enabled trên tất cả auto-renewing base plans; up to 30 ngày |
| **Account Hold auto-calculation** | 12/2025 | Duration = 60 ngày − grace period length |
| **Billing Library v7+ required** | 08/2026 (extended) | Tất cả apps phải upgrade |
| **Subscription Management API enhancements** | I/O 2026 | Offer cheaper alternatives to retain users; downgrade option để prevent cancellation |
| **RTDN (Real-time Developer Notifications)** | Ongoing | Server notifications cho subscription state changes |
| **Base plans & offers model** | Ongoing | Flexible pricing architecture; upgrade/downgrade via product change config |

[Primary docs — developer.android.com/google/play/billing; support.google.com/googleplay/android-developer/answer/16631229; Conference — Google I/O 2025, I/O 2026; Secondary — androidheadlines.com 05/2026]

---

## D. Góc nhìn Gaming-specific

### D.1 Các model subscription trong game

**Battle Pass / Season Pass:**
- Dominant subscription-like model trong mobile gaming. GameRefinery data: creative battle pass implementation found trong 60% top-grossing mobile games. [Secondary — pocketgamer.biz/gamerefinery-battle-pass]
- Battle pass không phải "true subscription" theo nghĩa App Store — thường được implement như **non-renewing IAP** (one-time purchase mỗi season), không phải auto-renewable subscription. [Secondary — gameanalytics.com; adriancrook.com]
- Unity có dedicated Battle Passes service trong Unity Solutions. [Primary docs — docs.unity.com/en-us/services/solutions/battle-passes]

**VIP Subscription / Premium Membership:**
- Recurring QoL/cosmetic benefits (daily rewards, exclusive content, ad-free, boosted progression). [Secondary — naavik.co]
- Naavik analysis: VIP subs drive 2.5–4x more revenue so với non-subscribers; leverage loss aversion để boost LTV. [Secondary — naavik.co/f2p-mobile/vip-subscriptions]
- Tiered pricing (VIP Bronze/Silver/Gold) là pattern phổ biến. [Secondary — adriancrook.com]

**Remove-Ads Subscription:**
- Đơn giản nhất; recurring payment để tắt quảng cáo; phổ biến trong casual/hyper-casual games. [Secondary — appfollow.io/blog/mobile-game-monetization 07/2026]

**Daily Cards / Daily Login Subscription:**
- Model mới: daily reward allocation cho subscribers; tạo habit loop. [Secondary — naavik.co]

**Ecosystem Passes:**
- Cross-game subscription (ví dụ: Xbox Game Pass, Apple Arcade, Netflix Games). [Secondary]

### D.2 SDK nào games đang dùng?

| Layer | SDKs | Ghi chú |
|---|---|---|
| **Platform IAP** | Unity IAP, Apple StoreKit, Google Play Billing | Bắt buộc; games phải dùng native billing |
| **Subscription management** | RevenueCat, Adapty, Xsolla | Chỉ games có subscription component mới dùng |
| **Game economy backend** | PlayFab, Firebase, custom backend | Quản lý virtual currency, inventory, battle pass tiers |
| **LiveOps/CRM** | Xsolla LiveOps, custom CRM | Tailored offers, events, seasonal content |

[Primary docs; Secondary — adriancrook.com, xsolla.com, businessofapps.com]

**Quan trọng:** Top-grossing games (Honor of Kings, Roblox, Last War, Clash Royale — $1B+ annually) thường **tự build** IAP/subscription infrastructure trên nền platform billing, không dùng RevenueCat/Adapty. Lý do: họ cần control sâu game economy (virtual currency conversion, bundle logic, progression-gated offers) mà subscription SDK truyền thống không hỗ trợ. [Secondary — mobilegamer.biz, singular.net]

### D.3 Khoảng trống cho games (gap mà subscription SDK truyền thống không cover)

1. **Virtual currency ↔ subscription bridge:** Subscription SDK hiểu "entitlement = access", không hiểu "entitlement = 500 gems/ngày + battle pass tier unlock". Game cần SDK hiểu cả IAP consumable và subscription entitlement trong cùng one economy.
2. **Season/temporal logic:** Battle pass có mùa (30/60/90 ngày), content reset, progression tiers — không vendor nào trong nhóm RevenueCat/Adapty handle temporal game content.
3. **Progression-gated offers:** Offer unlock khi player đạt level X, complete quest Y — cần game state awareness mà subscription SDK không có.
4. **Hybrid monetization decisioning:** Game cần quyết định per-user: hiện battle pass offer, hay VIP subscription prompt, hay IAP bundle, hay rewarded ad — dựa trên player state. Subscription SDK chỉ handle subscription part.
5. **Cross-game entitlements:** Ecosystem passes (multiple games under one subscription) — chưa vendor nào support.
6. **Server-authoritative game state:** Games cần server validate progression trước khi grant entitlement — khác với app subscription (Apple/Google authorize).

[Design synthesis — tổng hợp từ nhiều nguồn]

---

## E. Implications cho SDK của chúng ta

### E.1 Feature checklist bắt buộc phải match (parity)

| # | Feature | Priority | Notes |
|---|---|---|---|
| 1 | No-code paywall builder (native rendering) | P0 | Table stakes; must be SwiftUI + Compose |
| 2 | Remote configuration (no app release) | P0 | Table stakes |
| 3 | A/B testing (≥4 variants, flexible split) | P0 | Match Adapty/Superwall level |
| 4 | Introductory offer / free trial eligibility | P0 | Wrap StoreKit + Play Billing |
| 5 | Win-back offers (iOS) | P1 | Wrap Apple WWDC24 feature |
| 6 | Offer codes | P1 | Wrap Apple + Google |
| 7 | Billing grace period + retry tracking | P0 | Wrap both platforms |
| 8 | Account hold handling (Android) | P0 | Wrap Google Play lifecycle |
| 9 | Subscription groups / upgrade-downgrade | P1 | Wrap both platforms |
| 10 | Cross-platform entitlement sync | P0 | Core value prop |
| 11 | Webhook/API cho subscription events | P0 | Table stakes |
| 12 | Subscriber analytics + cohorts + LTV | P1 | Match RevenueCat/Adapty dashboards |
| 13 | Integrations (Amplitude, Braze, Segment, AppsFlyer, Adjust) | P1 | Minimum 10 integrations |
| 14 | Price localization / PPP support | P2 | Differentiator nếu built-in |
| 15 | Dunning management / smart retry | P2 | Value-add feature |
| 16 | Holdout & incrementality measurement | P2 | **White space — chưa ai làm** |
| 17 | Family sharing / group subscriptions | P2 | Wrap WWDC26 group purchase khi available |
| 18 | 12-month commitment plans | P2 | Wrap WWDC26 feature |
| 19 | Refund & chargeback handling | P1 | Match Adapty Refund Saver |
| 20 | Price elasticity experiments | P2 | Built-in elasticity testing |

### E.2 2–3 cơ hội differentiation cho games

**1. Game-aware subscription layer:**
- SDK hiểu cả subscription entitlement VÀ game economy state (virtual currency balance, battle pass tier, player level, season timer).
- API: `grantSubscriptionBenefit(type: .dailyGems(500), condition: .levelGte(10))` thay vì chỉ `entitlement.isActive`.
- Không vendor nào trong nhóm RevenueCat/Adapty/Superwall có game-state awareness.

**2. Hybrid monetization decisioning cho subscription context:**
- Khi player xem paywall, SDK quyết định: hiện VIP subscription, hay battle pass offer, hay IAP bundle, hay rewarded ad prompt — dựa trên player state + predicted LTV.
- Tích hợp causal uplift measurement (từ doc 05) vào paywall decisioning — đây là convergence point giữa "subscription brain" và "player decision platform".

**3. Temporal/season-aware subscription management:**
- Native support cho season pass lifecycle (start, mid-season, end-season, transition).
- Auto-renewal logic cho seasonal subscriptions (season 2 auto-unlock nếu season 1 subscriber).
- Progression-gated offers (unlock premium tier khi complete milestone).
- Cross-season retention mechanics (loyalty bonus cho multi-season subscribers).

---

## F. Benchmark data nổi bật (2026)

Từ RevenueCat State of Subscription Apps 2026 (115,000 apps, $16B revenue): [Case study — self-reported]
- Top 10% apps capture 94.5% subscription revenue (tăng từ 92.7% năm 2023).
- Hard paywall convert 10.7% vs. freemium 2.1% — gap 5x (một số nguồn nói 8x).
- Day 0 cancellation rate: 55.4%.
- AI apps churn 36% nhanh hơn non-AI apps.
- Google Play billing failures chiếm 31% cancellations.

Từ Adapty State of In-App Subscriptions 2026 (16,000 apps, $3B revenue): [Case study — self-reported]
- 95% revenue thuộc top 10% apps.
- 44.5% users reject paywall tại một thời điểm.
- European users trả nhiều hơn cho subscription apps.
- Subscription fatigue patterns identified ở 3 điểm chính trong user journey.

---

## Không xác minh được (ghi rõ)

1. **Purchasely bị Airbridge mua lại:** Không tìm thấy bất kỳ evidence nào cho acquisition này. Cả hai công ty vẫn hoạt động độc lập tính đến 08/2026. Câu hỏi ban đầu "Purchasely (acquired by Airbridge? verify)" → **không đúng**.

2. **RevenueCat exact revenue processed:** RevenueCat claim "$16B+ tracked" trong benchmark report nhưng đây là aggregate dataset, không phải annual revenue họ process. Con số exact monthly processing volume không được công bố công khai.

3. **Game studios lớn dùng subscription SDK nào:** Top-grossing games (Honor of Kings, PUBG Mobile, etc.) không công bố tech stack cho subscription/IAP layer. Inference dựa trên job postings, GDC talks, và community discussions — không phải confirmed fact.

4. **Naavik "2.5–4x more revenue" VIP subscription claim:** Self-reported analysis, không có independent verification.

5. **Mirava.io "15–122% revenue increase" từ PPP pricing:** Single vendor claim, sample size và methodology không rõ.

6. **Adapty "95% revenue top 10%" vs. RevenueCat "94.5%":** Hai con số gần identical từ hai reports độc lập — có thể phản ánh cùng underlying reality, nhưng mỗi vendor dùng dataset và methodology riêng.

7. **WWDC 2026 features (Group Subscriptions, Bundles & Suites):** Announced nhưng chưa fully available tại thời điểm nghiên cứu (08/2026). Timeline rollout cụ thể chưa rõ.

---

## Nguồn chính

1. RevenueCat — Pricing & Plans — https://www.revenuecat.com/pricing
2. RevenueCat — Why RevenueCat — https://www.revenuecat.com/why-revenuecat
3. RevenueCat — State of Subscription Apps 2026 — https://www.revenuecat.com/state-of-subscription-apps/
4. RevenueCat — Subscription App Trends & Benchmarks 2026 — https://www.revenuecat.com/blog/growth/subscription-app-trends-benchmarks-2026
5. RevenueCat — Experiments — https://www.revenuecat.com/feature/experiments
6. RevenueCat — Cross-platform Subscription State — https://www.revenuecat.com/blog/engineering/cross-platform-subscription
7. RevenueCat — Beginner's Guide to Apple Win-Back Offers — https://www.revenuecat.com/blog/growth/guide-to-apple-win-back-offers
8. RevenueCat — A/B Price Testing Guide — https://www.revenuecat.com/blog/engineering/mobile-app-a-b-price-testing-guide
9. RevenueCat — Changelog — https://www.revenuecat.com/changelog
10. Adapty — Pricing (2026) — https://adapty.io/blog/adapty-new-pricing-2026/
11. Adapty — Pricing Page — https://adapty.io/pricing/
12. Adapty — State of In-App Subscriptions 2026 — https://adapty.io/state-of-in-app-subscriptions/
13. Adapty — Subscription App Success 2026 — https://adapty.io/blog/subscription-app-success-2026/
14. Adapty — Data Synchronization Across Platforms — https://adapty.io/subscription-sync/
15. Adapty — Apple Subscription Offers Guide — https://adapty.io/blog/apple-subscription-offers-guide/
16. Adapty — App Store Subscription Groups — https://adapty.io/blog/app-store-subscription-groups/
17. Adapty — Braze Integration — https://adapty.io/docs/braze
18. Superwall — Pricing — https://superwall.com/pricing
19. Superwall — New Pricing Blog — https://superwall.com/blog/superwalls-new-pricing-more-aligned-generous-and-transparent
20. Superwall — Features — https://superwall.com/features
21. Superwall — Campaigns — https://superwall.com/features/campaigns
22. Superwall — A/B Testing — https://superwall.com/features/ab-testing
23. Apphud — Pricing — https://apphud.com/pricing
24. Apphud — FAQ — https://docs.apphud.com/docs/faq
25. Apphud — SDK Integration — https://docs.apphud.com/docs/sdk-integration
26. Purchasely — Product — https://www.purchasely.com/product
27. Purchasely — Docs — https://docs.purchasely.com/
28. Qonversion — Subscription Management — https://qonversion.io/subscription-management
29. Qonversion — A/B Experiments — https://qonversion.io/experiments
30. Apple Developer — Auto-renewable Subscriptions — https://developer.apple.com/app-store/subscriptions/
31. Apple Developer — Implementing Introductory Offers — https://developer.apple.com/documentation/storekit/implementing-introductory-offers-in-your-app
32. Apple Developer — Supporting Win-Back Offers — https://developer.apple.com/documentation/storekit/supporting-win-back-offers-in-your-app
33. Apple Developer — Implementing Offer Codes — https://developer.apple.com/documentation/storekit/implementing-offer-codes-in-your-app
34. Apple Developer — Managing Price Increases — https://developer.apple.com/documentation/storekit/managing-price-increases-for-auto-renewable-subscriptions
35. Apple Developer — Billing Grace Period — https://developer.apple.com/help/app-store-connect/manage-subscriptions/enable-billing-grace-period-for-auto-renewable-subscriptions/
36. Apple Developer — Reducing Involuntary Churn — https://developer.apple.com/documentation/storekit/reducing-involuntary-subscriber-churn
37. Apple Developer — WWDC26 What's New in IAP (session 210) — https://developer.apple.com/videos/play/wwdc2026/210/
38. Apple Developer — WWDC25 What's New in StoreKit (session 241) — https://developer.apple.com/videos/play/wwdc2025/241/
39. Apple Developer — Offer Codes Setup — https://developer.apple.com/help/app-store-connect/manage-subscriptions/set-up-subscription-offer-codes/
40. MacRumors — Apple Introduces App Store Subscription Overhaul (06/2026) — https://www.macrumors.com/2026/06/11/apple-introduces-app-store-subscription-overhaul/
41. TechCrunch — Apple Win-Back Offers (06/2024) — https://techcrunch.com/2024/06/11/apple-adds-win-back-subscription-offers-and-improved-search-suggestions-to-the-app-store/
42. TechCrunch — Apple Cheaper Subscription Option (04/2026) — https://techcrunch.com/2026/04/28/apple-introduces-a-cheaper-option-for-app-store-subscriptions/
43. Google Android Developer — About Subscriptions — https://developer.android.com/google/play/billing/subscriptions
44. Google Android Developer — Subscription Lifecycle — https://developer.android.com/google/play/billing/lifecycle/subscriptions
45. Google Play Console — Understanding Subscriptions — https://support.google.com/googleplay/android-developer/answer/12154973
46. Google Play — Changes to Payment Recovery Periods — https://support.google.com/googleplay/android-developer/answer/16631229
47. RevenueCat — Google Play Lifecycle Guide — https://www.revenuecat.com/blog/engineering/google-play-lifecycle
48. RevenueCat — Google Play Billing Error Churn — https://www.revenuecat.com/blog/growth/google-play-billing-error-churn-how-to-fix
49. Naavik — VIP Subscriptions & Recurring Revenue Revolution — https://naavik.co/f2p-mobile/vip-subscriptions-recurring-revenue-revolution/
50. Xsolla — How Game Subscriptions Are Redefining Mobile Monetization — https://xsolla.com/blog/how-game-subscriptions-are-redefining-mobile-game-monetization
51. Adrian Crook — Subscription Models for Mobile Games — https://adriancrook.com/subscription-models-for-mobile-games/
52. GameAnalytics — Designing Battle Passes in Mobile Games — https://www.gameanalytics.com/blog/designing-battle-passes-in-mobile-games-the-whats-whys-and-hows
53. AppFollow — Mobile Game Monetization 2026 — https://appfollow.io/blog/mobile-game-monetization
54. Adapty — Top 7 Mobile Game Monetization Models 2026 — https://adapty.io/blog/mobile-game-monetization/
55. PkgPulse — RevenueCat vs Adapty vs Superwall 2026 — https://www.pkgpulse.com/guides/revenuecat-vs-adapty-vs-superwall-mobile-in-app-2026
56. BuildMVPFast — RevenueCat vs Superwall vs Adapty 2026 — https://www.buildmvpfast.com/blog/revenuecat-vs-superwall-vs-adapty-in-app-subscription-indie-2026
57. sph.sh — RevenueCat Alternatives 2026 — https://sph.sh/en/posts/revenuecat-alternatives-comparison-2026/
58. appsops.store — iOS Subscription SDK Comparison 2026 — https://appsops.store/blog/ios-subscription-sdk-comparison-2026
59. PPC.land — 95% Revenue Top 10% (Adapty 2026) — https://ppc.land/95-of-app-subscription-revenue-goes-to-top-10-adaptys-2026-benchmark-report/
60. Mirava.io — Free Trial Conversion Benchmarks 2026 — https://www.mirava.io/blog/free-trial-conversion-benchmarks-2026
61. Chargebee — Dunning Management — https://www.chargebee.com/blog/dunning-management-for-saas-business/
62. Solidgate — Dunning Management — https://solidgate.com/blog/dunning-management/
63. AndroidHeadlines — Google Play Downgrade Option (05/2026) — https://www.androidheadlines.com/2026/05/google-play-store-cancel-app-subscription-downgrade-options.html
64. FunnelFox — Apple WWDC 2026 Subscription Features — https://blog.funnelfox.com/apple-wwdc-2026/
65. Super-apps.ai — Apple Ends Traditional IAP Promo Codes — https://super-apps.ai/blogs/apple-ends-traditional-iap-promo-codes-transitions-to-offer-code-system
66. Appbot — Apple Offer Codes 2026 — https://appbot.co/blog/apple-offer-code/
67. Amplitude — Holdout Groups — https://amplitude.com/docs/feature-experiment/advanced-techniques/holdout-groups-exclude-users
68. Braze — RevenueCat Integration — https://www.braze.com/docs/partners/ecommerce/ordering_payments_subscription/revenuecat
69. Amplitude — RevenueCat Integration — https://amplitude.com/integrations/revenue-cat
70. PocketGamer — GameRefinery Battle Pass in Top-Grossing Games — https://www.pocketgamer.biz/gamerefinery-battle-pass-implementation-in-top-grossing-mobile-games/
71. Unity Docs — Battle Passes — https://docs.unity.com/en-us/services/solutions/battle-passes
72. Xsolla — Subscriptions — https://xsolla.com/subscriptions
73. Xsolla — SDK — https://xsolla.com/sdk
74. Adapty — Unity SDK Overview — https://adapty.io/docs/unity-sdk-overview
75. Samsung IAP — Subscriptions for Games (Unreal Engine) — https://developer.samsung.com/iap/blog/en/2026/06/30/introduce-subscriptions-for-your-games-using-the-samsung-iap-unreal-engine-plugin
76. Medium/RevenueCat Blog — iOS Subscription Groups Explained — https://medium.com/revenuecat-blog/ios-subscription-groups-explained-8c5b8325d4ab
