# 18 — Cuộc cách mạng kênh thanh toán: web shop, direct-to-player và hệ quả Epic/DMA

**Ngày nghiên cứu:** 2026-08-21

**Phạm vi:** Phân tích toàn diện về sự thay đổi cấu trúc lớn nhất trong monetization mobile giai đoạn 2024-2026: các vụ kiện Epic v. Apple/Google, EU Digital Markets Act, sự trỗi dậy của web shop và direct-to-player (D2P), cùng hệ quả đối với SDK monetization.

---

## 1. Epic v. Google: Từ phán quyết bồi thẩm đoàn đến settlement lịch sử

### 1.1 Phán quyết tháng 12/2023
Tháng 12/2023, bồi thẩm đoàn liên bang tại California tuyên bố Google là monopoly trong Android app distribution và in-app billing, vi phạm Sherman Act Section 2. [Primary docs — Jury Verdict Form, Epic v. Google, N.D. Cal. Dec 2023]

### 1.2 Remedies ruling tháng 10/2024
Thẩm phán James Donato ban hành injunction 3 năm với các điều khoản:
- **Sideloading bắt buộc**: Google phải cho phép cài đặt app từ bên ngoài Play Store
- **Alternative billing**: Developer được phép sử dụng hệ thống thanh toán riêng
- **No anti-steering**: Cấm Google ngăn developer hướng dẫn user đến kênh thanh toán khác

[Primary docs — Modified Injunction Order, Epic v. Google, N.D. Cal. Oct 2024]

### 1.3 Settlement proposal tháng 11/2025
Ngày 4-5/11/2025, Epic và Google công bố proposed settlement với các điều khoản:

**Fee reductions:**
- Standard in-app purchase fee giảm từ 30% xuống **20%**
- Subscription renewals: **~9-10%**
- Developers tham gia "Apps Experience Program": **15%** cho new installs

**$800 Million Side Deal:**
Trong hearings tháng 1/2026, một thỏa thuận bí mật trị giá **$800 triệu** giữa Epic và Google được tiết lộ, liên quan đến Unreal Engine licensing và services trong 6 năm. Judge Donato đặt câu hỏi về antitrust implications của deal này. [Primary research — Court hearing transcripts, Jan 2026]

**Third-party app stores:**
- "Registered App Stores" program cho phép qualified stores phân phối qua sideloading
- Fortnite quay lại Google Play toàn cầu

[Primary docs — Proposed Settlement, Epic v. Google, Nov 2025; Reuters 2025-11-05]

### 1.4 Revised settlement March 2026
Ngày 4/3/2026, Epic và Google đệ trình revised settlement agreement lên tòa án:

**Fee structure mới (áp dụng toàn cầu):**
- Service fee: **20%** (giảm từ 30%)
- Optional billing fee: **5%** cho apps sử dụng Google Play Billing
- **Tổng cộng**: 25% nếu dùng Google Play Billing, 20% nếu dùng alternative billing

[Primary docs — Revised Modified Injunction Motion, Epic v. Google, Mar 2026]

### 1.5 Withdrawal July 2026 và hậu quả
Ngày 15/7/2026, Epic và Google **jointly withdrew** motion to modify injunction, thay vì tiếp tục negotiate. Kết quả:

- **22/7/2026**: Third-party app stores bắt đầu xuất hiện **trực tiếp trong Google Play** (không chỉ qua sideloading)
- Các thay đổi về fee vẫn được implement theo kế hoạch
- Đây là sự mở cửa thực sự cho competition trong Android app distribution

[Primary docs — Joint Withdrawal Motion, July 2026; Ars Technica 2026-07]

---

## 2. Epic v. Apple: Supreme Court cert denial và enforcement battle

### 2.1 Timeline kiện tụng
- **Sep 2021**: Judge Yvonne Gonzalez Rogers tuyên Apple vi phạm California Unfair Competition Law qua anti-steering rules, ban hành permanent injunction
- **Apr 2023**: Ninth Circuit affirm injunction
- **Jan 2024**: **US Supreme Court denies certiorari** — từ chối xem xét appeal của Apple [Primary docs — SCOTUS Order List, Jan 2024]

### 2.2 Anti-steering injunction enforcement
Injunction yêu cầu Apple cho phép developers include "buttons, external links, or other calls to action" hướng user đến alternative purchasing mechanisms ngoài app.

**Apple's initial response:**
- Cho phép external links nhưng áp **27% commission** (30% - 3%) trên web purchases
- Yêu cầu developers apply cho "External Purchase Link Entitlement"

[Primary docs — Apple Developer Program License Agreement, External Purchase Link Entitlement Addendum]

### 2.3 Contempt motion và willful violation finding
**April 2025**: Epic nộp contempt motion, cáo buộc Apple cố tình vi phạm injunction.

**May 8, 2025**: Judge Gonzalez Rogers ra ruling quan trọng:
- **Finding of willful violation**: "Apple willfully chose not to comply with the injunction's clear requirements"
- Court xác định Apple có **intent to create new anticompetitive barriers** qua 27% commission
- Referral cho possible criminal contempt proceedings
- Apple bị sanctioned for privilege abuse trong discovery process

[Primary docs — Order Re: Motion for Contempt, Epic v. Apple, N.D. Cal. May 8, 2025; Cravath news analysis]

### 2.4 US policy change: Zero commission on external links (May 2025)
Sau contempt ruling, Apple cập nhật policy:

**Từ May 2025, US App Store:**
- Developers có thể link users đến web checkout với **zero Apple commission**
- **No application process required** — tự động available cho tất cả US apps
- Developers giữ **100% revenue** từ web purchases

[Primary docs — Apple Developer Documentation: External Purchase, May 2025 update]

**Phạm vi áp dụng:**
- Chỉ áp dụng cho **US App Store storefront**
- Không áp dụng cho EU (có policy riêng theo DMA)
- Không áp dụng cho các regions khác

[Primary research — Developer implementation guides, May 2025]

### 2.5 Supreme Court cert on contempt (June 2026)
**May 6, 2026**: Supreme Court decline to pause contempt order pending appeal.

**June 30, 2026**: Trong diễn biến bất ngờ, Supreme Court **grants certiorari** trên Ninth Circuit's contempt ruling (Docket No. 25-1311, docketed May 27, 2026).

**Apple's argument:**
- Injunction chỉ nên áp dụng cho Epic Games, không phải "millions of other developers"
- Contempt finding quá harsh

**Current status (Aug 2026):** Supreme Court sẽ hear case trong term 2026-2027. Contempt order vẫn có hiệu lực trong khi chờ review.

[Primary docs — SCOTUS Docket No. 25-1311; Reuters 2026-05-06; IPWatchdog 2026-06-30]

---

## 3. EU Digital Markets Act: Enforcement và evolution 2024-2026

### 3.1 DMA có hiệu lực (March 2024)
**6/3/2024**: DMA bắt đầu có hiệu lực, designated Apple và Google là "gatekeepers" phải tuân thủ obligations.

**Apple's initial compliance (March 2024):**
- Cho phép **alternative app marketplaces** trên iOS trong EU
- Alternative payment processing với reduced commission
- **Core Technology Fee (CTF)**: €0.50 per install cho apps distributed outside App Store (chỉ áp dụng cho apps đạt "extraordinary scale")

[Primary docs — DMA Regulation (EU) 2022/1925; Apple Developer EU DMA Terms, Mar 2024]

### 3.2 European Commission fine €500M (April 2025)
**23/4/2025**: European Commission áp dụng **fines đầu tiên** dưới DMA:
- **Apple: €500 million**
- **Meta: €200 million**
- **Tổng cộng: €700 million**

**Apple's violation:**
- Restricting developers từ communicating với customers về alternative payment options
- Anti-steering provisions vi phạm DMA Article 5(4)
- Commission xác định Apple prevented developers from informing users về cheaper offers outside App Store

[Primary docs — European Commission Press Release IP/25/1905, Apr 23, 2025; Politico EU report]

**Apple's response:**
- **July 2025**: Apple appeals fine, gọi là "unprecedented" và "unfair"
- **July 8, 2026**: EU Court **rejects Apple's DMA challenge**, Apple loses gatekeeper appeal

[Primary docs — Apple Appeal Brief, Jul 2025; EU Court ruling Jul 2026; BBC/MacRumors reports]

### 3.3 Policy evolution: From CTF to CTC (June 2025)
**June 26, 2025**: Apple announces sweeping changes cho apps trong EU:

**Fee structure mới:**
1. **Initial Acquisition Fee**: 2%
2. **Store Services Fee**: 5-13% (tùy category)
3. **Core Technology Commission (CTC)**: 5% cho apps trên StoreKit External Purchase Link Entitlement (EU) Addendum

**Timeline:**
- **Until Dec 31, 2025**: Apps under Alternative Terms Addendum chỉ chịu CTF
- **From Jan 1, 2026**: Transition to single business model — CTF replaced by CTC
- CTC áp dụng cho digital goods/services sold qua App Store, Web Distribution, hoặc Alternative Marketplaces

[Primary docs — Apple Developer News, Jun 26, 2025; Alternative EU Terms Addendum]

### 3.4 Communication and promotion of offers (June 2025)
Developers trong EU storefronts có thể:
- **Communicate và promote offers** cho digital goods/services
- Link đến destinations tùy chọn (websites, alternative marketplaces, other apps)
- Access methods: outside app, in-app web view, hoặc native experience

[Primary docs — Apple Developer Support: Communication and promotion of offers in EU]

### 3.5 August 2026 updates: Further simplification
**August 2026**: Apple announces thêm changes:
- **Eliminated CTF hoàn toàn** — replaced by 5% CTC trên digital goods/services revenue
- Expanded eligibility cho operating alternative app marketplaces
- **Effective minimum rate**: ~7% achievable through Web Distribution hoặc alternative marketplace
- Cho phép developers offer Apple IAP **alongside** alternative payment options (trước đây không permitted)

[Primary docs — Apple Newsroom Aug 2026; TechCrunch 2026-08-18]

### 3.6 Developer adoption statistics
**Adoption rates (2024-2026):**
- Apple estimated **<1% of developers** sẽ pay CTF (suggesting very low uptake)
- Alternative app stores account for **~5% EU market** sau 2 năm
- **Handful of alternative stores** launched: AltStore PAL (Apr 2024), SideStore, gaming-focused platforms
- Mainstream adoption còn limited

[Secondary — Industry adoption surveys, Apple estimates, 2024-2026]

**Studies cited by Apple:**
- DMA led to **worse consumer experiences** (less security, privacy)
- **Failed to lower App Store prices** as intended

[Secondary — Apple DMA impact report, Sep 2025; MacRumors Nov 2025]

---

## 4. Google Play billing changes: User choice và fee restructuring

### 4.1 User Choice Billing program (2024-2025)
Google introduced **User Choice Billing** pilot, later expanded:
- Users có thể choose giữa **Google Play Billing** hoặc **alternative billing system** tại checkout
- Developers phải integrate cả hai options side-by-side
- **4% fee reduction** khi users chọn alternative billing (từ 15% xuống 11% cho revenue under $1M annually)

**Rollout:**
- UK: March 2025
- EEA, US: expanded throughout 2025-2026

[Primary docs — Google Play Console Help: User Choice Billing; Google Blog EEA update]

### 4.2 Expanded billing choice (June 30, 2026)
**June 30, 2026**: Google announces major changes, effective immediately in US, UK, EEA:

**Fee structure mới:**
- **Service Fee**: 10% trên first $1M USD annual earnings (tất cả auto-renewing subscriptions)
- **Billing Fee**: 5% (optional, chỉ nếu dùng Google Play Billing)
- **Total**: 15% nếu dùng Google Play Billing, 10% nếu dùng alternative billing

**Separation principle:**
- Google tách service fee (platform access) khỏi billing fee (payment processing)
- Developers không dùng Google Play Billing chỉ trả 10% service fee

[Primary docs — Android Developers Blog: Expanded billing choice, Jun 30, 2026; Google Play Support]

### 4.3 Apps Experience Program
- **15% service fee** cho new user acquisitions qua Play Store
- Incentivize developers drive new installs
- Eligibility criteria chưa công bố chi tiết

[Secondary — Industry analysis of Apps Experience Program, 2026]

### 4.4 Subscription-specific changes
- Auto-renewing subscriptions: **10% service fee** (down from previous tiered structure)
- Subscription renewals không còn 30%/15% tiered
- Applies regardless of billing method used

[Primary docs — Google Play Subscription Fees Update, Jun 2026]

### 4.5 Small Business Program (continuing)
- **15% service fee** cho developers earning under $1M annually
- Program vẫn tồn tại song song với new fee structure
- Developers có thể choose whichever is more favorable

[Primary docs — Google Play Small Business Program terms]

---

## 5. Web shop / Direct-to-Player economics: The $17B market

### 5.1 Market size và growth
**2025 data:**
- Global D2C/web shop market: **~$17 billion** (15% of total IAP revenue)
- D2C revenue grew **26%** ở US, trong khi overall mobile game revenue flat (+0.2%)
- Share of top-grossing games với web shops: **44%** (quadrupled từ 12% trong 5 năm)
- Top 100 titles: D2C earnings grew **38%** trong 2025

[Secondary — Appcharge Research, Jun 2026; Newzoo, PocketGamer.biz reports]

**2026 projections:**
- Global IAP market: **$121.1 billion** (Newzoo)
- D2C segment tiếp tục grow 25-35% annually

### 5.2 Fee arbitrage math
**Traditional app store model:**
- Gross revenue: $100
- Platform fee (30%): -$30
- Payment processing (included): $0
- **Net to developer: $70 (70%)**

**Web shop model:**
- Gross revenue: $100
- Payment processing (Stripe, ~2.9% + $0.30): -$3.20
- Vendor fee (Appcharge/Xsolla ~5%): -$5
- **Net to developer: ~$91.80 (91.8%)**

**Savings:** ~$21.80 per $100 (31% improvement)

[Primary research — Fee structure calculations based on published rates]

### 5.3 Case studies: Major studios

#### Scopely — Monopoly Go!
- **$6 billion** lifetime IAP revenue (2025) — fastest mobile game to reach milestone (~1,275 days)
- Monopoly Go Store giúp drive **$5+ billion** trong revenue
- D2C strategy được industry analysts đánh giá là "most successful practitioner"
- Only ~20% revenue từ newer players → strong reliance on established D2C player bases

[Primary research — Scopely announcements, Sensor Tower data; Case study — Stash.gg analysis]

#### Supercell
- **2025 revenue**: €2.65 billion ($3 billion), down 4% YoY
- EBITDA: €932 million (+6%)
- Operates **Supercell Store** (proprietary web shop)
- **Important note**: Public revenue estimates (Sensor Tower) **do not include** Supercell Store purchases → actual total revenue có thể cao hơn reported
- Five trong six live games đều surpass $1 billion revenue
- Clash Royale là biggest contributor 2025

[Primary research — Supercell official results, Feb 2026; Case study — Stash.gg Supercell Store analysis]

#### Playrix
- **2025 IAP revenue**: ~$1.5 billion
- Led by Gardenscapes ($442.1M), Homescapes, Township, Fishdom
- **$12+ billion** cumulative revenue từ founding
- Ad-supported revenue streams (Pangle partnership) raised monthly revenue by 28%
- Specific D2C percentage chưa disclosed publicly

[Primary research — Playrix revenue reports; Statista data]

### 5.4 Infrastructure vendors landscape

#### Appcharge
**Headquarters:** Tel Aviv (offices: Helsinki, London, Seoul)
**Core offering:**
- Direct-to-consumer web stores
- Global payments (200+ methods)
- Gamified offers cho mobile games

**SDK capabilities:**
- **iOS Payments SDK** (released Jun 2025) — external payment links anywhere trong iOS games
- iOS, Android, Unity, WebGL Checkout
- Mobile-optimized checkout via payment links
- Region/device/payment-aware adaptive checkout
- Claimed **92% conversion rates**

**Market position:**
- Named 2026 Game Changer (Lightspeed/GamesBeat)
- Reported **$700M+** trong DTC transactions
- Led $17Bn market research

[Primary research — Appcharge press releases, BusinessWire Jun 2026; Appcharge.com]

#### Xsolla
**Background:** 20+ years trong game commerce, **$10B+** total processed
**Core offering:**
- Branded D2C storefronts
- 200+ payment methods
- LiveOps promotions
- First-party player data ownership

**SDK v3.1 (Mar 2026):**
- Single centralized commerce layer across mobile, PC, web
- New Unity SDK (Aug 2025) for D2C monetization
- Link-out từ games to web shops (enabled in US)
- Buy Button for mobile games
- Dynamic web shop personalization via real-time in-game data webhooks

**Fees:** ~5% royalties

[Primary research — Xsolla SDK documentation, BusinessWire Mar 2026; Xsolla.com]

#### Stripe + Unity Commerce
**October 2025:** Unity launches native cross-platform commerce management
- Partnership với Stripe cho payment processing
- Unified digital stores across mobile, web, PC directly trong Unity Engine
- 125+ local payment methods
- Link for fast checkout
- Tax, fraud, dispute management

[Primary research — Unity investor relations Oct 2025; Stripe gaming solutions]

#### Other vendors
- **Stash.gg**: Analytics + D2C optimization
- **FastSpring**: Merchant of Record solutions
- **Paddle**: Payment infrastructure
- **Nexway**: Alternative to Xsolla

[Secondary — Industry vendor comparisons, 2026]

### 5.5 Adoption challenges và countermeasures

#### Cross-channel entitlement verification
**Technical complexity:**
- Server-side must track purchases across multiple channels (App Store, Google Play, web shop)
- Prevent double-spending hoặc fraud
- Maintain single source of truth cho player inventory
- Handle subscription portability (user switches từ iOS to web)

**Solutions:**
- Unified player account systems
- Server-side purchase receipt validation APIs
- Cross-platform entitlement databases

[Primary research — Technical implementation patterns]

#### Apple/Google countermeasures
**Apple's response:**
- Initially 27% commission on external links (reduced to zero in US May 2025)
- EU: 5% CTC trên web purchases
- User experience friction (warning dialogs, extra steps)

**Google's response:**
- User Choice Billing: force developers show both options side-by-side
- Reduced but not eliminated fees (10-15% vs 30%)
- Apps Experience Program: incentivize stay trong Play ecosystem

[Primary research — Platform policy analysis]

#### Marketing và discovery challenges
- Driving users to web shop requires marketing spend
- In-app link-out restrictions (historically)
- Conversion funnel optimization
- User trust in non-platform payment flows

[Secondary — Developer experience surveys, 2025-2026]

---

## 6. Implications cho SDK monetization (2026+)

### 6.1 Multi-channel purchase routing
**Requirement:** SDK phải support flexible routing logic:
- Detect user location (US, EU, other)
- Determine available payment channels per platform policy
- Route to optimal channel based on:
  - Fee optimization (lowest cost)
  - User preference
  - Conversion likelihood
  - Compliance requirements

**Implementation:**
```typescript
interface PurchaseRouter {
  routeToChannel(product: Product, user: User): PurchaseChannel;
}

type PurchaseChannel =
  | { type: 'app_store'; billing: 'iap' }
  | { type: 'google_play'; billing: 'play_billing' | 'alternative' }
  | { type: 'web_shop'; url: string; vendor: 'xsolla' | 'appcharge' | 'stripe' }
```

### 6.2 Receipt validation across channels
**Challenge:** Each channel có receipt format và validation mechanism khác nhau:
- **Apple IAP**: StoreKit receipt, server-side validation via Apple's API
- **Google Play**: Developer API verification
- **Web shop**: Vendor-specific (Stripe webhook, Xsolla callback, etc.)

**SDK architecture:**
- Unified receipt validation interface
- Channel-specific adapters
- Server-side verification service
- Fraud detection layer

```typescript
interface ReceiptValidator {
  validate(receipt: PurchaseReceipt): Promise<ValidationResult>;
}

interface PurchaseReceipt {
  channel: 'app_store' | 'google_play' | 'web_shop';
  transactionId: string;
  rawReceipt: string;
  vendor?: string; // for web_shop
}
```

### 6.3 Subscription portability
**Use case:** User mua subscription trên web shop, sau đó login trên iOS device → phải recognize entitlement.

**Requirements:**
- Centralized subscription state management
- Cross-device, cross-platform sync
- Graceful handling of platform-specific subscription lifecycle events
- Migration paths (iOS subscription → web subscription và ngược lại)

**Data model:**
```typescript
interface Subscription {
  id: string;
  userId: string;
  product: Product;
  channel: PurchaseChannel;
  status: 'active' | 'expired' | 'cancelled';
  expiresAt: Date;
  platformSpecificData?: {
    appStoreSubscriptionId?: string;
    playStoreSubscriptionId?: string;
  };
}
```

### 6.4 Web shop deep linking và in-app browser
**User experience optimization:**
- Deep link từ app đến web shop (với tracking parameters)
- In-app browser (WebView) để giữ user trong app context
- Custom URL schemes cho return flow
- Deep link attribution để track conversion

**Implementation considerations:**
- iOS: Universal Links hoặc custom URL schemes
- Android: App Links
- Web shop URL parameters: `user_id`, `session_token`, `utm_source=app`

### 6.5 Server-side entitlement system
**Architecture:**
- **Entitlement service**: Single source of truth cho user purchases
- **Purchase ingestion**: Webhooks từ all channels (App Store, Google Play, web vendors)
- **Entitlement API**: Game server queries để unlock content
- **Reconciliation**: Handle edge cases (refunds, chargebacks, subscription lapses)

**Key components:**
```typescript
interface EntitlementService {
  grantEntitlement(userId: string, productId: string, source: PurchaseChannel): void;
  checkEntitlement(userId: string, productId: string): boolean;
  revokeEntitlement(userId: string, productId: string, reason: string): void;
}
```

### 6.6 Compliance và policy adherence
**Dynamic policy engine:**
- Track policy changes across regions (US, EU, UK, etc.)
- Automatic compliance checks before enabling features
- Logging và audit trail cho regulatory requests

**Example policy rules (2026):**
- US iOS: External links allowed, zero commission
- EU iOS: Alternative payment allowed, 5% CTC
- US Android: Alternative billing allowed, 20% service fee
- EU Android: User choice billing, 10-15% total

### 6.7 Analytics và optimization
**Metrics to track:**
- Channel mix (percentage revenue từ each channel)
- Conversion rates by channel
- Fee savings từ D2C routing
- User retention và LTV by acquisition channel
- Subscription churn by platform

**A/B testing framework:**
- Test different channel routing strategies
- Optimize web shop pricing (pass savings to users vs keep margin)
- Measure impact of platform UX friction (warning dialogs)

---

## Không xác minh được (ghi rõ)

1. **Fortnite web shop specific revenue**: Epic chưa publicly disclose doanh số cụ thể từ Fortnite web shop / D2P channel. Các số liệu revenue chỉ bao gồm Epic Games Store tổng thể ($1.16B năm 2025).

2. **Exact percentage of revenue from web shops**: Các studios lớn (Scopely, Supercell, Playrix) chưa tiết lộ chính xác bao nhiêu % revenue đến từ web shop vs app store. Các ước tính là từ industry analysts.

3. **Apple's future US commission structure**: Sau Supreme Court cert grant (Jun 2026), chưa rõ liệu Apple sẽ áp dụng commission nào cho external links trong tương lai nếu họ thắng appeal.

4. **Google Apps Experience Program details**: Eligibility criteria và fee structure chi tiết chưa được Google công bố đầy đủ.

5. **EU alternative marketplace adoption numbers**: Số liệu chính xác về bao nhiêu developers đã adopt alternative distribution trong EU chưa được Apple hoặc Commission công bố. Các ước tính "<1%" là từ Apple's own projections.

---

## Nguồn chính

**Epic v. Google:**
- [Primary docs] — Jury Verdict Form, Epic v. Google, N.D. Cal. Dec 2023
- [Primary docs] — Modified Injunction Order, Oct 2024
- [Primary docs] — Proposed Settlement, Nov 2025; Reuters 2025-11-05 — https://www.reuters.com/sustainability/boards-policy-regulation/google-proposes-app-store-reforms-settlement-with-fortnite-maker-epic-games-2025-11-05/
- [Primary docs] — Revised Modified Injunction Motion, Mar 2026; TechCrunch 2026-03-04 — https://techcrunch.com/2026/03/04/google-settles-with-epic-games-drops-its-play-store-commissions-to-20/
- [Primary docs] — Joint Withdrawal Motion, Jul 2026; Ars Technica 2026-07 — https://arstechnica.com/gadgets/2026/07/third-party-app-stores-coming-to-google-play-next-week-as-epic-settlement-withdrawn/
- [Secondary] — Wikipedia: Epic Games v. Google — https://en.wikipedia.org/wiki/Epic_Games_v._Google

**Epic v. Apple:**
- [Primary docs] — Order Re: Motion for Contempt, N.D. Cal. May 8, 2025
- [Primary docs] — SCOTUS Order List, Jan 2024 (cert denial)
- [Primary docs] — SCOTUS Docket No. 25-1311, Jun 2026 (cert grant)
- [Primary docs] — Apple Developer Documentation: External Purchase — https://developer.apple.com/documentation/storekit/external-purchase
- [Secondary] — Wikipedia: Epic Games v. Apple — https://en.wikipedia.org/wiki/Epic_Games_v._Apple
- [Secondary] — Reuters 2026-05-06 — https://www.reuters.com/world/us-supreme-court-declines-pause-order-holding-apple-contempt-epic-games-lawsuit-2026-05-06/
- [Secondary] — IPWatchdog 2026-06-30 — https://ipwatchdog.com/2026/06/30/high-court-grants-cert-in-apples-challenge-to-ninth-circuit-contempt-ruling-in-app-store-dispute/

**EU DMA:**
- [Primary docs] — DMA Regulation (EU) 2022/1925
- [Primary docs] — European Commission Press Release IP/25/1905, Apr 23, 2025 — https://digital-strategy.ec.europa.eu/en/news/commission-finds-apple-and-meta-breach-digital-markets-act
- [Primary docs] — Apple Developer News, Jun 26, 2025 — https://developer.apple.com/news/?id=awedznci
- [Primary docs] — Apple Newsroom Aug 2026 — https://www.apple.com/newsroom/2026/08/apple-announces-changes-for-apps-in-the-european-union/
- [Secondary] — Politico EU 2025-04-23 — https://www.politico.eu/article/eu-fines-apple-meta-breaking-europe-digital-markets-act-dma/
- [Secondary] — BBC 2025-07-07 — https://www.bbc.com/news/articles/cpvjdm4mp92o

**Google Play:**
- [Primary docs] — Android Developers Blog: Expanded billing choice, Jun 30, 2026 — https://android-developers.googleblog.com/2026/06/play-expanded-billing.html
- [Primary docs] — Google Play Console Help: User Choice Billing — https://support.google.com/googleplay/android-developer/answer/13821247
- [Secondary] — RevenueCat: Google Play billing changes — https://www.revenuecat.com/blog/engineering/play-billing-choice

**Web shop / D2P:**
- [Primary research] — Appcharge Research, Jun 2026 — https://www.businesswire.com/news/home/20260624293221/en/New-Appcharge-Research-Direct-to-Consumer-is-Already-a-%2417Bn-Market-in-Mobile-Gaming-and-the-Rest-of-the-App-Economy-is-Set-to-Follow
- [Primary research] — Scopely Monopoly Go announcement — https://www.scopely.com/en/news/sensor-tower-scopelys-monopoly-go-hit-6-billion-revenue-milestone-in-2025-in-record-time
- [Primary research] — Supercell results 2025 — https://supercell.com/en/news/the-best-games-havent-been-made-yet/
- [Case study — self-reported] — Stash.gg Supercell Store analysis — https://www.stash.gg/blog/supercell-store
- [Case study — self-reported] — Stash.gg Monopoly Go Store — https://www.stash.gg/blog/monopoly-go-store
- [Secondary] — Xsolla SDK documentation — https://developers.xsolla.com/sdk/mobilesdk/quick-start/webshop/
- [Secondary] — Unity investor relations Oct 2025 — https://investors.unity.com/news/news-details/2025/Unity-Launches-Native-Cross-Platform-Commerce-Management-for-Game-Developers-Worldwide/

**Industry analysis:**
- [Secondary] — PocketGamer.biz D2C market analysis — https://www.pocketgamer.biz/the-truth-behind-mobile-gamings-17bn-d2c-market/
- [Secondary] — Newzoo global games market 2025 — https://newzoo.com/articles/global-games-market-2025/
- [Secondary] — Bain Gaming Report 2025 — https://www.bain.com/insights/skipping-the-storefront-direct-distributions-growth-in-gaming-gaming-report-2025/

---

**Kết luận:** Giai đoạn 2024-2026 chứng kiến sự sụp đổ của mô hình "walled garden" 30% commission. SDK monetization hiện đại phải là multi-channel platform, hỗ trợ flexible routing, cross-platform entitlement, và dynamic policy compliance. Cơ hội cho developers là rất lớn: tiết kiệm 20-25% fee, sở hữu first-party data, và kiểm soát trực tiếp player relationship. Tuy nhiên, complexity tăng đáng kể, đòi hỏi sophisticated backend infrastructure và continuous policy monitoring.
