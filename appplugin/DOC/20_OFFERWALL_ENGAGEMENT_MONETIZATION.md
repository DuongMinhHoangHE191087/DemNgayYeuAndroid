# 20 — Offerwall, engagement monetization, loyalty và lớp CRM/live-message

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Bức tranh toàn cảnh về các bề mặt monetization nằm ngoài IAP trực tiếp và quảng cáo video truyền thống: offerwall (đang phục hưng trên iOS post-ATT), loyalty/engagement hub, CRM/live-messaging layer đẩy IAP, và gamified monetization infrastructure (battle pass/season pass/event pass). Kèm compliance US/EU và kiến nghị lớp nào thuộc SDK native, lớp nào là integration.

## Evidence labels

- **[Primary docs]**: tài liệu chính thức từ vendor/nền tảng (Unity, Digital Turbine, Apple, FTC, EU).
- **[Primary research]**: paper nghiên cứu peer-reviewed hoặc data report gốc.
- **[Case study — self-reported]**: studio hoặc vendor tự công bố kết quả (cần đọc với skepticism).
- **[Market report]**: báo cáo ngành từ Sensor Tower, Tenjin, data.ai, v.v.
- **[Secondary]**: báo chí, analyst, blog chuyên ngành.

---

## A. Offerwall renaissance (2024–2026) — đặc biệt trên iOS post-ATT

### A.1 Tại sao offerwall quay lại mạnh

Sau khi Apple ATT (App Tracking Transparency) có hiệu lực tháng 6/2021, in-app purchase revenue toàn cầu giảm **~35%** theo MarketingDive. [Secondary — MarketingDive]

Offerwall — bề mặt mà user **tự chọn** hoàn thành task (install app khác, hoàn thành level, trả lời survey) để nhận phần thưởng trong game — trở thành kênh phục hồi vì:

1. **Không phụ thuộc IDFA**: offerwall hoạt động trên mô hình CPA/CPE (cost-per-action / cost-per-engagement), advertiser trả tiền khi user hoàn thành action cụ thể. Attribution diễn ra **server-to-server** giữa offerwall provider và advertiser, không cần device-level tracking. [Secondary — Ciao Games, Remerge]
2. **eCPM vượt trội**: offerwall đạt eCPM trung bình **$350–$530** ở tier-1 markets (US, UK, AU), với peak **$1,500–$2,000+** cho single high-value task completion. So sánh: rewarded video eCPM US iOS khoảng **$19.63** (Q4/2024), US Android **$16.49**. [Market report — Tenjin, Tapjoy data]
3. **Retention multiplier**: user engage với offerwall có **D30 retention cao gấp 7x** so với non-offerwall users, và **10–14x khả năng convert thành payer** theo thời gian. [Case study — self-reported, Kongregate/RevU data]
4. **93% additive revenue**: khoảng 93% người chơi engage offerwall là non-payer và không trở thành payer sau đó — nghĩa là offerwall tạo revenue hoàn toàn bổ sung, không cannibalize IAP. [Case study — self-reported, Kongregate]

### A.2 Multi-reward offerwall — thiết kế hiện đại

Mô hình truyền thống: user hoàn thành 1 task → nhận 1 reward. Mô hình 2024–2026: **multi-reward / multi-milestone**:

- **Digital Turbine** (trước là Fyber): multi-reward CPE campaigns với reward tại các mốc trung gian (level 30, 50, 80) và final (level 100). Giữ user engaged xuyên suốt hành trình thay vì drop-off sau milestone đầu. [Primary docs — Digital Turbine]
- **Unity/Tapjoy** multi-reward ad sets: publisher đặt nhiều reward tier cho cùng một offer entry, tăng completion rate. [Primary docs — Unity LevelPlay]
- **So sánh giá trị reward**: IAP mua 25k coins, rewarded video cho ~30 coins, offerwall cho **~500 coins** — positioning offerwall là kênh giá trị cao nhất cho non-payer. [Secondary — Ciao Games]

### A.3 Bản đồ vendor offerwall hiện tại (2026)

| Vendor | Trạng thái sở hữu | Platforms | Ghi chú |
|---|---|---|---|
| **Tapjoy** | Thuộc **AppLovin** (mua 2021) | iOS + Android | AppLovin layoffs 2024–2025 (hàng trăm người, bao gồm Adjust ~120, Machine Zone ~97); Tapjoy vẫn hoạt động nhưng team bị ảnh hưởng. [Secondary — TechCrunch, GamesIndustry.biz] |
| **Fyber / DT Exchange** | Thuộc **Digital Turbine** (mua 2021, ~$600M) | iOS + Android | DT giảm nhân sự ~3.5–4%/năm (2024–2026), nhưng revenue phục hồi +15% YoY (FY2026 $565.3M). Offerwall SDK vẫn maintained (v9.2.2 trên Maven). Revenue loss FY2025 ~$92.1M (GAAP). [Secondary — DT IR filings] |
| **ironSource Offerwall** | Thuộc **Unity** (merger 2022, $4.4B) | iOS + Android | **ironSource Ads direct demand network đóng cửa 30/4/2026**. LevelPlay mediation vẫn hoạt động và vẫn hỗ trợ offerwall format qua third-party networks. Unity đã explore bán Supersonic. [Primary docs — Unity sunset FAQ] |
| **adjoe Playtime** | Độc lập (Hamburg) | iOS + Android | Mô hình retention-driven: thưởng theo thời gian sử dụng app. eCPM Android reported $240–$1,670 tùy vertical. SDK nhẹ trên Google Play. [Secondary — adjoe blog, Google Play SDK Index] |
| **Torox** | Độc lập | iOS + Android | 600+ publishers, 9M+ MAU, 350+ advertisers. Task-based truyền thống. Reviews mixed về offer tracking reliability. [Secondary — G2, Capterra, Trustpilot] |
| **Mistplay** | Độc lập (Montreal) | **Android only** | Mô hình loyalty/play-to-earn; eCPM thấp hơn task-based ($1–$3/hour cho end-user). Strong discovery platform nhưng không có iOS. [Secondary — Mistplay business blog] |
| **RevU** | Độc lập | iOS + Android | Case study Kongregate: **650% increase iOS offerwall revenue** khi thêm RevU alongside incumbent. ~93% additive (non-cannibalizing). [Case study — self-reported, RevU] |
| **Xsolla Offerwall** | Xsolla (payment platform) | Web + Mobile | Quest-based reward system; kết hợp Web Shop + Offerwall trong SDK 3 (ra mắt 2025). [Primary docs — Xsolla] |
| **Timeplay, JustCash, Appmonet** | Độc lập/nhỏ | Khác nhau | Niche players; Timeplay focused Canadian market, Appmonet focused push-notification monetization. [Secondary] |

### A.4 ironSource shutdown — tác động cụ thể

**Unity thông báo 26/3/2026**: ironSource Ads direct demand network ngừng hoạt động **30/4/2026**. [Primary docs — Unity]

- **Bị ảnh hưởng**: direct demand inventory từ ironSource advertisers (rewarded video, interstitial, banner, offerwall fill trực tiếp từ ironSource demand).
- **KHÔNG bị ảnh hưởng**: LevelPlay mediation platform vẫn hoạt động bình thường; publisher vẫn integrate third-party ad networks (Unity Ads, AdMob, Meta Audience Network, v.v.) qua LevelPlay.
- **Supersonic**: Unity explore bán.
- **Bài học kiến trúc**: nếu SDK integrate ironSource direct, cần migrate sang mediation-only model. LevelPlay SDK changelog vẫn active (v8.8+).

### A.5 So sánh eCPM: Offerwall vs Rewarded Video (US & EU, 2024–2025)

| Format | US iOS | US Android | EU (ví dụ Germany) | Fill rate |
|---|---|---|---|---|
| **Offerwall** | $350–$2,000+ | $240–$1,670 | $200–$800 | Thấp (5–15%) |
| **Rewarded Video** | ~$19.63 | ~$16.49 | ~$3.80–$4.50 | Cao (60–85%) |
| **Interstitial** | $9–$14 | $8–$12 | $4–$8 | Trung bình (30–50%) |
| **Banner** | $0.50–$2.80 | $0.30–$1.50 | $0.20–$0.80 | Rất cao |

**Lưu ý**: eCPM offerwall cao nhưng fill rate thấp — nên **luôn chạy multi-offerwall** (2–3 providers đồng thời) qua mediation. Kongregate case study chứng minh multi-offerwall không cannibalize lẫn nhau. [Market report — Tenjin 2025/2026, Mistplay, PlayIO]

### A.6 Tại sao advertiser thích offerwall (high-intent CPA)

- Advertiser trả **CPA $2–$50+** tùy action depth (install → tutorial → level 10 → purchase). Chỉ trả khi action hoàn thành — risk thấp hơn CPM video.
- User từ offerwall có **intent cao hơn** vì họ tự chọn engage (không bị forced view).
- Multi-reward campaigns cho advertiser **nhiều touchpoint** để đánh giá quality user qua progression.
- Post-ATT, advertiser dịch chuyển budget từ targeted UA sang performance-based channels — offerwall hưởng lợi trực tiếp. [Secondary — Remerge, Ciao Games]

---

## B. Loyalty & Rewards programs — biến engagement thành currency

### B.1 Mô hình loyalty trong mobile games/apps

Loyalty program trong gaming đã evolve từ "login bonus" đơn giản sang **hệ thống điểm/currency phức tạp redeem được real rewards**:

1. **Points-based loyalty**: user tích điểm qua gameplay, daily login, missions → redeem gift card, premium currency, real items.
2. **Play-to-earn hybrid**: Mistplay, adjoe Playtime — user được thưởng cho thời gian chơi, điểm redeem qua PayPal/gift card.
3. **Engagement-as-currency**: game tạo internal currency riêng (gem, crystal, star) kiếm được qua engagement, spend trong game economy.

**Deconstructor of Fun (12/2025)**: "Loyalty Platforms 101" mô tả đây là **UA channel tăng trưởng nhanh nhất** của gaming — players earn loyalty points qua progression, spend trên virtual + real prizes. [Secondary — Deconstructor of Fun]

### B.2 Vendors và SDK cho loyalty

| Vendor/SDK | Mô hình | Ghi chú |
|---|---|---|
| **Salesforce Gamification SDK** | Enterprise loyalty management | Android + iOS SDK cho custom loyalty apps; tích hợp Salesforce Loyalty Management backend. [Primary docs — Salesforce] |
| **adjoe** | Playtime + Offerwall hybrid | Publisher tích hợp SDK → user earn rewards qua time-spent; advertiser trả cho engagement. [Primary docs — adjoe] |
| **Mistplay** | B2C loyalty app (Android) | Không SDK cho publisher; là standalone app recommend games → reward users. [Secondary] |
| **Unity Rewards** | Community recognition | Google Play app; reward users qua gameplay community participation. [Primary docs — Unity] |
| **Xsolla** | Payment + loyalty combined | SDK 3 có offerwall + payment + catalog; publisher tạo loyalty program trên web shop. [Primary docs — Xsolla] |

### B.3 Engagement Hub — daily events, streaks, collections, missions

Engagement hub là **bề mặt UI tập trung** nơi player thấy mọi cách để earn/spend/engage. Các thành phần chuẩn:

1. **Daily login calendar / streak**: thưởng tăng dần theo số ngày liên tiếp (day 1: 10 gems, day 7: 100 gems + rare item). Loss-aversion psychology — mất streak tạo urgency quay lại. [Secondary — Gianty 2025]
2. **Daily/weekly missions**: "Win 3 battles", "Play 5 matches", "Use 10 abilities" — short-term goals tạo session structure.
3. **Collections / albums**: thu thập items/cards/stickers → complete set → bonus reward. Tạo long-term engagement loop.
4. **Limited-time events**: holiday events, tournament, special challenges — urgency + FOMO.
5. **Mini-games / engagement activities**: spin wheel, lucky draw, scratch card — dopamine loop giữ player giữa sessions.

**Mỗi thành phần trên đều là bề mặt monetization**: missions có thể include "watch rewarded video for 2x reward", events có thể gate premium tier sau battle pass, collections có thể accelerate bằng IAP. [Secondary — GameRefinery, Studio Krew]

---

## C. CRM / Live Messaging layer — push, in-app message, email driving IAP

### C.1 Vai trò của CRM trong monetization

CRM/messaging layer không trực tiếp tạo revenue nhưng là **delivery mechanism** đưa đúng offer đến đúng user đúng lúc:

- **Push notification**: re-engage dormant user, remind about expiring offer, announce new event.
- **In-app message (IAM)**: contextual offer hiển thị khi user đang trong app — đúng moment of engagement.
- **Email**: nurture lapsed users, communicate subscription benefits, deliver personalized offers.
- **Content Cards / persistent IAM**: message sống trong app (không dismiss), user quay lại thấy offer vẫn chờ.

### C.2 Platform comparison — studios thực sự dùng gì

| Platform | Strengths | Gaming case study | Pricing model |
|---|---|---|---|
| **Braze** | Deep CRM segmentation, cross-channel journeys, Content Cards, Canvas flow builder | **Etermax (Trivia Crack)**: IAM campaign → **87% tăng gameplay weekend**, 50% tăng single-mode. **Second Dinner (Marvel Snap)**: Content Cards amplify player engagement. Self-reported: 31% YoY increase sessions/user, 82% uplift user lifetime cross-channel, 32% YoY conversion rate increase. [Case study — self-reported, Braze] | Enterprise — contact sales; MAU-based |
| **OneSignal** | Developer-first, generous free tier (1M+ users free), easy SDK integration, push + IAM + email | Phổ biến cho indie/mid-size studios. So sánh Braze: ít CRM depth hơn nhưng cost-effective. [Secondary — OneSignal blog] | Free tier + growth/enterprise plans |
| **Airship** | Enterprise push reliability, scale cho airlines/banks/retailers, real-time personalization | Dùng bởi large publishers; ít public gaming case study. [Secondary — Airship comparisons] | Enterprise — contact sales |
| **CleverTap** | Mobile-first, real-time analytics, RFM segmentation, strong APAC presence | **300B push notification report** — benchmark CTR theo industry/segmentation. Gaming-specific growth platform. [Secondary — CleverTap] | MAU-based; có free tier |
| **Insider** | Web + mobile personalization, AI-powered journey orchestration, strong e-commerce DNA | So sánh CleverTap: mạnh hơn về web/e-commerce, weaker về mobile gaming. [Secondary — Sequenzy, G2] | Enterprise — contact sales |
| **Firebase In-App Messaging** | Free (included trong Firebase), kết hợp Remote Config personalization, lightweight | Phổ biến cho indie studios dùng Firebase stack. Remote Config cho phép A/B test offer variants real-time. Không có CRM depth. [Primary docs — Firebase] | Free |
| **Pushwoosh** | Gaming-focused push, omnichannel, journey automation | Claim giúp gaming apps **triple DAU & MAU**. [Secondary — Pushwoosh blog] | MAU-based |

### C.3 Segmentation-to-offer workflow (pattern chuẩn)

```
User behavior data → Segment definition → Trigger condition → Offer selection → Channel delivery → Conversion measurement
```

Ví dụ cụ thể:

1. **Segment**: "Active player, haven't purchased, Day 7+, completed tutorial" (non-payer high-engagement).
2. **Trigger**: User opens app lần thứ 15.
3. **Offer**: First-time buyer bundle — $0.99 starter pack (personalized giá trị).
4. **Channel**: In-app message (modal) khi user vào main menu.
5. **Measurement**: Conversion rate, revenue per message, unsubscribe rate.

**Braze research**: personalized outreach theo segment tăng conversion (bao gồm IAP) **up to 27%**. Cross-channel messaging (push + IAM + email) boost engagement **844%** so với single channel. [Case study — self-reported, Braze]

### C.4 Measured uplift examples (tất cả self-reported)

| Studio/Case | Method | Result | Source |
|---|---|---|---|
| **Etermax (Trivia Crack)** | Braze IAM reward campaign (Triviathon) | **+87% weekend gameplay**, +50% single-mode | Braze case study |
| **Second Dinner (Marvel Snap)** | Braze Content Cards | Amplified player engagement (chi tiết không disclosed) | Braze case study |
| **Braze aggregate gaming** | Cross-channel messaging | +31% sessions/user YoY, +82% user lifetime, +32% conversion YoY | Braze self-reported benchmarks |
| **Generic gamification campaign** | Braze 4-week gamification | **+26% conversion rate** | LinkedIn / Braze partner |
| **amondz (jewelry, not gaming)** | Braze push cart abandonment | +20% conversions | Braze case study |

**Caveat quan trọng**: tất cả numbers trên là **self-reported bởi vendor hoặc partner**. Không có independent third-party verification. Studios nên chạy controlled A/B trước khi trust vendor benchmarks. [Case study — self-reported]

---

## D. Gamified monetization infrastructure — Battle Pass / Season Pass / Event Pass

### D.1 Battle Pass là gì và tại sao quan trọng

Battle Pass (BP) / Season Pass: **tiered reward system** mà player earn qua gameplay progression, thường có free track + premium track ($5–$15/season, season kéo dài 4–8 tuần).

**Revenue impact**:
- **Battle pass chiếm 30–60% total revenue** trong nhiều free-to-play games thành công. [Secondary — Gamemakers.com, 2025]
- **Google Play Developer analysis**: 1–40% revenue trong top-grossing mobile games đến từ premium battle pass purchases. [Primary docs — Google Play]
- **Clash of Clans Gold Pass** (2019): **145% revenue spike** trong 7 ngày đầu; **$27M trong tháng đầu**. [Market report — Sensor Tower, reported by GamesIndustry.biz]
- **Fortnite**: ước tính $400–600M/month, tổng 2023 vượt $6B — phần lớn driven by Battle Pass. [Secondary — NGSSolution]
- **Genshin Impact**: vượt $5B tại China (2024); approaching $10B globally; battle pass + gacha synergy. [Secondary — Niko Partners]

### D.2 Design patterns chuẩn

1. **Free + Premium tracks**: Free track cho small rewards giữ engagement; Premium track ($5–$15) unlock full rewards worth 10–50x giá mua.
2. **Tier progression**: 50–100 tiers per season, mỗi tier yêu cầu XP/points nhất định.
3. **Late-join mechanics**: cho phép mua BP giữa season, catch-up bằng retroactive tier unlock.
4. **Catch-up mechanics**: XP boost events cuối season giúp player chưa xong kịp claim rewards.
5. **Prestige / prestige tiers**: sau khi hoàn thành BP, thêm prestige tiers cho hardcore players.
6. **Social/visual rewards**: exclusive skins, emotes, banners — social signaling tạo purchase pressure lành mạnh.

### D.3 Ai cung cấp Battle Pass infrastructure

| Provider | Type | Chi tiết |
|---|---|---|
| **Unity Battle Passes** | Built-in Unity service | Tiered reward system trong Unity Solutions; integrate trực tiếp vào Unity game. [Primary docs — Unity] |
| **Xsolla** | Payment + LiveOps platform | SDK 3 (ra mắt 8/2025) hỗ trợ battle pass purchases, in-game currency, analytics. LiveOps Management Suite cho D2C channels. Web Shop + Buy Button bypass platform fees. [Primary docs — Xsolla] |
| **Metica** | AI personalization platform | Founded by ex-King + ex-Apple team; raised $9M seed (1/2025). Unity SDK + Android SDK + iOS SDK. SmartConfigs cho personalized offers, SmartFloors cho ad mediation optimization. **IAP personalization +36% revenue** (case study Starberry). Pricing: enterprise custom quote. [Case study — self-reported, Metica] |
| **deltaDNA** | Analytics + LiveOps | Unity SDK trên GitHub; player analytics + live ops management. Được Unity acquire; status sau Unity layoffs không rõ ràng. [Primary docs — GitHub] |
| **Beamable** | Backend-as-a-service | Battle Pass sample implementation cho Unity 5.0; custom content management. [Primary docs — Beamable] |
| **Heroic Labs (Nakama/Hiro)** | Game server | Guide build battle pass qua Achievements + Sub-Achievements system. [Primary docs — Heroic Labs] |
| **In-house** | Tự build | Phần lớn top-grossing games (Supercell, miHoYo, Epic) build in-house vì BP tightly coupled với game economy. [Secondary] |

### D.4 Academic evidence

- **"Battle Pass Capitalism"** (ResearchGate, 2021): phân tích political economy của battle pass — chuyển từ ownership model sang access/subscription model. [Primary research]
- **"The Battle Pass: A Mixed-Methods Investigation"** (OSF Preprints, 2025): phát hiện BP có **minimal effect on player uptake** ở một số titles (Dota 2) — chủ yếu extract thêm value từ existing players chứ không attract new ones. [Primary research]
- **Social value predictor** (IJAEB journal, 2025): social value significantly predicts BP purchase intention trong games như Genshin Impact. [Primary research]
- **Sensor Tower (2021)**: games thêm battle pass/season pass thấy **revenue increase đáng kể**; Clash of Clans là case study chuẩn. [Market report — Sensor Tower]

---

## E. Compliance cho US/EU — offerwall, loyalty, battle pass

### E.1 FTC guidance — virtual currency & loot disclosures

- **FTC workshop 2019 + Staff perspective paper 2020** ("Loot boxes: What's in play?"): FTC scrutinize loot box mechanics từ 2019. Yêu cầu: disclose loot box presence, list probability/chances, show real-money price alongside virtual currency, không obscure actual costs qua multiple currency exchanges. [Primary docs — FTC]
- **2025 enforcement focus**: FTC ưu tiên safeguard kids — loot box mechanics **không được misrepresent drop rates** hoặc obscure real-money costs, đặc biệt cho games accessible to children/teens. [Secondary — Reed Smith LLP]
- **Chưa có federal statute**: không US state nào có loot box statute cụ thể (tính đến 2025). ESA (2019): Microsoft, Nintendo, Sony yêu cầu disclose loot box odds trên platform họ. FTC dùng existing consumer protection authority. [Secondary — blog.promise.legal]
- **Virtual currency**: CPCN (Conference of Commissioners of Uniform State Laws) announced virtual currency consumer protection guidelines — virtual currency trong game phải có disclosure rõ ràng về giá trị, hạn sử dụng, transferability. [Secondary — ZwillGen]

### E.2 EU: DSA, Digital Fairness Act, dark patterns

- **Digital Services Act (DSA) Art. 25**: cấm dark patterns trên online platforms. EGDF (European Games Developer Federation, 2025) phê bình điều khoản **vague và khó interpret** cho gaming context. [Primary law — DSA; Secondary — EGDF]
- **Digital Fairness Act (đề xuất, 2025)**: thảo luận cấm loot box cho <16, cấm pay-to-win nhắm trẻ em, minh bạch dynamic/personalized pricing, drip pricing. **Chưa thành luật** — nhưng là hướng 2026–2027. [Secondary — Osborne Clarke, Digital Fairness Act]
- **Dark patterns đã illegal trong EU** (2025): consent banner designs, confirmshaming, forced continuity, hidden costs. Meta buộc remove dark patterns khỏi "pay-or-consent" flow (12/2025 BEUC statement). [Primary enforcement — BEUC/DPA]
- **PEGI (6/2026)**: game có paid random items tự động nhận PEGI 16 rating. [Primary — PEGI; cũng xem doc 13]

### E.3 Offerwall-specific compliance

**Post-ATT / GDPR considerations**:
- Offerwall hoạt động trên server-to-server attribution — **không cần IDFA cho attribution**. Tuy nhiên, nếu offerwall SDK thu thập device data cho fraud prevention hoặc analytics, cần GDPR consent (legitimate interest không đủ cho behavioral data).
- **Transparency**: user phải biết rõ họ đang rời app để complete offer (install another app, take survey). In-app browser hoặc explicit disclosure trước khi redirect.
- **Data sharing disclosure**: nếu offerwall provider share user completion data với advertiser, phải disclose trong privacy policy.
- **Children**: COPPA/age-gate áp dụng — offerwall targeting children <13 cần parental consent cho data collection. Nhiều offerwall providers tự restrict khỏi apps directed at children.

**Dark pattern lines không được vượt qua**:
- Không disguise offerwall offers như game content/UI elements (deceptive design).
- Không create false urgency ("offer expires in 5 minutes!" khi thực tế không expire).
- Không hide exit/close button trên offerwall modal.
- Không auto-redirect sang offerwall mà không có user action.
- Không pre-check consent boxes cho data sharing với third-party advertisers.

### E.4 Battle Pass / Season Pass compliance

- **Price transparency**: giá premium track phải rõ ràng (real money, không chỉ virtual currency amount).
- **Odds disclosure**: nếu BP có random reward elements (loot crate tiers), phải disclose odds (PEGI 16 rule).
- **No forced continuity**: nếu season pass auto-renew (subscription model), FTC Click-to-Cancel rule yêu cầu cancel dễ bằng mua. [Primary law — FTC]
- **Late-join / catch-up**: không bị regulate trực tiếp, nhưng nếu sell "catch-up packs" cuối season tạo FOMO quá mức → risk dark pattern classification.

---

## F. Implications — lớp nào thuộc SDK native, lớp nào là integration

### F.1 Ma trận quyết định

| Bề mặt | Build native trong SDK? | Rationale |
|---|---|---|
| **Offerwall mediation layer** | **NATIVE** (adapter) | SDK cần abstraction layer để plug 2–3 offerwall providers (RevU, adjoe, Torox, DT) qua unified API. Không build offerwall content — chỉ build adapter + reward delivery + S2S callback verification. |
| **Offerwall UI (wall display)** | **NATIVE** (templated) | Cung cấp templated UI (native Android view) publisher customize branding. Provider SDKs (Torox, adjoe) đã có UI — SDK wrap thành consistent experience. |
| **Reward delivery system** | **NATIVE (core)** | Khi user complete offer, SDK nhận S2S callback → credit reward cho user. Đây là logic mọi offerwall cần, không nên duplicate per provider. |
| **Engagement Hub UI** | **NATIVE** | Daily login, missions, streaks, collections — đây là bề mặt monetization SDK cần control. Build component library publisher configure. |
| **Battle Pass / Season Pass** | **INTEGRATION** (Xsolla/Unity) | BP quá tightly coupled với game economy để SDK generic được. Cung cấp integration hooks cho Xsolla SDK hoặc Unity Battle Passes; SDK chỉ handle reward delivery + analytics. |
| **CRM / Push / IAM** | **INTEGRATION** (adapter) | Braze, OneSignal, Firebase đã mature. SDK cung cấp event forwarding (user segments, conversion events) cho CRM platform; không rebuild CRM. |
| **Loyalty points system** | **NATIVE (optional module)** | Nếu game muốn internal loyalty currency, SDK cung cấp points ledger + redemption engine. Nếu dùng Mistplay/adjoe loyalty, integrate qua adapter. |
| **Live messaging (IAM)** | **INTEGRATION** | Firebase In-App Messaging hoặc Braze Content Cards — SDK forward events, CRM platform render messages. |

### F.2 Kiến trúc đề xuất cho SDK

```
┌──────────────────────────────────────────────────────────────────────┐
│                    MONETIZATION SDK CORE                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────────────┐  │
│  │ Offerwall    │  │ Engagement   │  │ Reward Delivery Engine    │  │
│  │ Adapter Layer│  │ Hub Module   │  │ (S2S callback + ledger)   │  │
│  │              │  │              │  │                           │  │
│  │ • RevU       │  │ • Daily login│  │ • Credit/debit currency   │  │
│  │ • adjoe      │  │ • Missions   │  │ • Fraud verification      │  │
│  │ • Torox      │  │ • Streaks    │  │ • Multi-currency support  │  │
│  │ • DT/Fyber   │  │ • Collections│  │                           │  │
│  └──────┬───────┘  └──────┬───────┘  └─────────────┬─────────────┘  │
│         │                 │                        │                  │
│  ┌──────┴─────────────────┴────────────────────────┴──────────────┐  │
│  │              Event Bus (unified events)                         │  │
│  │  offer_completed | mission_done | reward_earned | bp_tier_up   │  │
│  └──────┬─────────────────┬────────────────────────┬──────────────┘  │
│         │                 │                        │                  │
│  ┌──────┴───────┐  ┌──────┴───────┐  ┌────────────┴────────────┐   │
│  │ CRM Adapter  │  │ Battle Pass  │  │ Analytics Forwarding    │   │
│  │              │  │ Integration  │  │                         │   │
│  │ • Braze      │  │              │  │ • Firebase Analytics    │   │
│  │ • OneSignal  │  │ • Xsolla SDK │  │ • Adjust/AppsFlyer     │   │
│  │ • Firebase   │  │ • Unity BP   │  │ • Custom webhook       │   │
│  │ • CleverTap  │  │ • Custom     │  │                         │   │
│  └──────────────┘  └──────────────┘  └─────────────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### F.3 Ưu tiên build theo phase

**Phase 1 (MVP)**:
- Offerwall adapter layer (2 providers: Torox + adjoe, thêm RevU nếu US focus).
- Reward delivery engine (S2S callback, single currency).
- Basic engagement hub (daily login + 1 mission type).

**Phase 2**:
- Multi-currency reward ledger.
- CRM event forwarding (Firebase IAM + OneSignal push).
- Battle Pass integration hooks (Xsolla).
- Additional offerwall adapters (DT Exchange, Mistplay Android).

**Phase 3**:
- Advanced engagement hub (collections, events, streaks configurable).
- Loyalty points module (optional).
- Advanced CRM integrations (Braze Canvas, CleverTap journeys).
- AI-personalized offer curation (Metica-style).

### F.4 Revenue model cho SDK từ các bề mặt này

1. **Offerwall revenue share**: SDK lấy 5–15% cut trên offerwall revenue flowing qua mediation. (Industry standard: mediation platforms lấy 0–10%, nhưng value-added SDK có thể charge cao hơn.)
2. **Engagement Hub**: premium feature trong SDK subscription (doc 07 pricing).
3. **CRM integration**: free cho basic event forwarding; premium cho advanced segmentation sync.
4. **Battle Pass hooks**: free — khuyến khích adoption, revenue đến từ offerwall/IAP flow qua SDK.

---

## Không xác minh được (ghi rõ)

1. **Mistplay exact eCPM cho publisher**: Mistplay là B2C app, không public publisher-facing eCPM data. Con số $1–$3/hour là từ user reports, không từ Mistplay.
2. **adjoe Playtime eCPM $240–$1,670**: con số này xuất hiện trên adjoe blog và third-party comparisons nhưng không có independent verification; có thể là cherry-picked best cases.
3. **Metica pricing**: không public. Contact-required model. Con số +36% IAP revenue là từ self-reported case study (Starberry).
4. **Braze gaming benchmarks (+31% sessions, +82% lifetime, +32% conversion)**: self-reported aggregate bởi Braze, không có methodology disclosure đầy đủ.
5. **Battle pass chiếm 30–60% revenue**: con số widely cited nhưng sources là industry analysis/blogs, không phải audited financial data từ studios.
6. **Digital Turbine FY2025 net loss $92.1M**: từ IR filings, nhưng phân bổ bao nhiêu cho offerwall vs các segment khác không rõ.
7. **ironSource shutdown impact thực tế**: announced 3/2026, effective 4/2026 — chưa đủ thời gian để đo actual impact lên publisher revenue.
8. **Offerwall D30 retention 7x**: Kongregate/RevU data — self-reported, có thể selection bias (engaged users tự chọn vào offerwall).
9. **Torox 9M+ MAU, 600+ publishers**: từ Torox marketing materials, chưa verified bởi third party.

---

## Nguồn chính

**Offerwall & Vendor Status:**
- RevU — "RevU drives 650% increase in iOS Offerwall Revenue" (Kongregate case study) — https://revu.co/blog/kongregate
- Unity — "ironSource Ads direct demand sunset FAQ" — https://unity.com/products/ironsource-ads-sunset
- Unity — "4 Offerwall Myths, Debunked" — https://unity.com/blog/4-offerwall-myths-debunked
- Digital Turbine — "Multi-Reward Campaigns" — https://docs.digitalturbine.com/offerwall-advertisers/advanced-campaign-setup/multi-reward-campaigns
- adjoe — "Playtime vs Offerwall Campaigns" — https://adjoe.io/blog/playtime-vs-offerwall-campaigns/
- Ciao Games — "Offerwall Explained: Boost Your Mobile Game's Revenue" — https://ciao.games/offerwall/
- Mistplay — "20+ best offerwalls to choose from" — https://business.mistplay.com/resources/best-offerwalls
- Mistplay — "Mobile ads eCPM: Basics and latest data" — https://business.mistplay.com/resources/mobile-ads-ecpm
- Remerge — "From Installs to Engagement: Mobile Offerwalls Guide" — https://www.remerge.io/blog-post/from-installs-to-engagement-the-growing-influence-of-mobile-offerwalls
- Xsolla — "Offerwall boosts game monetization" — https://xsolla.com/newsroom/xsolla-offerwall-boosts-game-monetization-and-enhances-non-paying-gamers-engagement
- GamesIndustry.biz — "Unity shuts down IronSource ad network" — https://www.gamesindustry.biz/unity-sees-growth-in-engine-revenue-shuts-down-ironsource-ad-network-in-favour-of-vector-ai-user-acquisition-platform
- Digital Turbine IR — Fiscal 2025 Fourth Quarter Results — https://ir.digitalturbine.com/news-events/press-releases/detail/682
- Digital Turbine — "Completion of Acquisition of Fyber" — https://ir.digitalturbine.com/news-events/press-releases/detail/610
- UndrAds — "Tapjoy vs Revu vs Adjoe vs ironSource Offerwall" — https://undrads.com/blogs/tapjoy-vs-revu-vs-adjoe-vs-ironsource-offerwall
- GamesIndustry.biz — "AppLovin cuts another 97 jobs" — https://www.gamesindustry.biz/applovin-cuts-another-97-jobs-including-ceo-of-subsidiary-studio-machine-zone

**eCPM Benchmarks:**
- Tenjin — "Ad Monetization Benchmark Report 2026" — https://tenjin.com/blog/ad-mon-gaming-2026/
- Tenjin — "Ad Monetization Benchmark Report 2025" — https://tenjin.com/blog/ad-monetization-benchmark-report-2025-ecpm-ad-revenue/
- PlayIO — "Rewarded Ad Benchmarks for 2026" — https://blog.playio.co/rewarded-ad-benchmarks-2026
- Coinis — "Rewarded Video Ads: 2026 eCPMs" — https://coinis.com/glossary/rewarded-video
- Klink Labs — "Offerwall vs Rewarded Video Ads" — https://klinklabs.com/blog/blog-offerwall-vs-rewarded-video-ads
- PubFuture — "Rewarded Video Ads vs Offerwalls" — https://blog.pubfuture.com/rewarded-video-ads-vs-offerwalls/47422/

**Loyalty & Engagement:**
- Deconstructor of Fun — "Loyalty Platforms 101" (12/2025) — https://www.deconstructoroffun.com/blog/2025/12/1/loyalty-platforms-101-the-complete-guide-to-gamings-fastest-growing-ua-channel
- adjoe — "How-To: Build Loyalty Program that Sticks" — https://adjoe.io/blog/why-loyalty-programs-need-mobile-games/
- Salesforce — "Gamification Mobile SDK" — https://developer.salesforce.com/docs/atlas.en-us.loyalty.meta/loyalty/gamification_sdk.htm
- Gianty — "How Gamification Helps Boost User Engagement 2025" — https://www.gianty.com/gamification-boost-user-engagement-in-2025/
- Global Games Forum — "Evolution of Loyalty Programmes in Mobile Gaming 2025" — https://www.globalgamesforum.com/news-media/the-evolution-of-loyalty-programmes-in-mobile-gaming-a-2025-perspective

**CRM & Messaging:**
- Braze — "Etermax Used In-App Messages to Increase Gameplay by 87%" — https://www.braze.com/customers/etermax-case-study
- Braze — "Second Dinner Amplifies Player Engagement" — https://www.braze.com/customers/second-dinner-case-study
- Braze — "Growth for Gaming" — https://www.braze.com/resources/articles/growth-for-gaming
- Braze — "30 Essential Stats on In-App Purchases" — https://www.braze.com/resources/articles/in-app-purchase-stats
- Braze — "In-App Purchase Tips" — https://www.braze.com/resources/articles/in-app-purchases-tips
- Firebase — "Remote Config for Unity" — https://firebase.google.com/docs/remote-config/unity/real-time
- Google Cloud — "Firebase Unity Solutions: Remote Config" — https://cloud.google.com/blog/products/gaming/firebase-unity-solutions-update-game-behavior-without-deploying-with-remote-config
- OneSignal — "Top Braze Alternatives for Email & Push 2026" — https://onesignal.com/blog/top-braze-alternatives-for-email-push-in-2026/
- CleverTap — "The Complete Growth Platform for Gaming Apps" — https://clevertap.com/gaming/
- CleverTap — "Advanced Mobile Push Tactics" — https://clevertap.com/blog/mobile-push-notifications-advanced-tactics/

**Battle Pass & LiveOps:**
- Gamemakers.com — "The Complete Guide to Battle Pass Design & Monetization" — https://www.gamemakers.com/p/understanding-battle-pass-game-design
- Google Play — "How battle passes can boost engagement and monetization" — https://medium.com/googleplaydev/how-battle-passes-can-boost-engagement-and-monetization-in-your-game-d296dee6ddf8
- GameRefinery — "Battle Pass is a hot trend in mobile games" — https://www.gamerefinery.com/battle-pass-trend-mobile-games/
- Deconstructor of Fun — "Battle Passes — Everything You Ought to Know" — https://www.deconstructoroffun.com/blog/2022/6/4/battle-passes-analysis
- GameAnalytics — "Designing battle passes in mobile games" — https://www.gameanalytics.com/blog/designing-battle-passes-in-mobile-games-the-whats-whys-and-hows
- Sensor Tower — "Revenue increases following battle pass implementation" — https://gameworldobserver.com/2021/04/15/sensor-tower-on-revenue-increases-in-mobile-games-following-battle-pass-season-pass-implementation
- Xsolla — "LiveOps Management Suite" — https://xsolla.com/newsroom/xsolla-unveils-liveops-management-suite-to-boost-d2-c-monetization-for-game-developers
- Xsolla — "SDK now available for game developers globally" — https://xsolla.com/newsroom/xsolla-sdk-now-available-for-game-developers-globally
- Xsolla — "Game Subscriptions Redefining Mobile Game Monetization" — https://xsolla.com/blog/how-game-subscriptions-are-redefining-mobile-game-monetization
- Metica — "+36% IAP Revenue case study (Starberry)" — https://www.metica.com/case-studies/metica-iap-revenue-boost-merge-mayor-starberry
- Metica — "Ex-King and Ex-Apple Team Raise $9M" — https://www.metica.com/blog/ex-king-ex-apple-team-9million-seed-round
- Metica — "5 Reasons Why Games Need A Personalization Strategy" — https://www.metica.com/blog/win-in-2025
- Unity Docs — "Battle Passes" — https://docs.unity.com/en-us/services/solutions/battle-passes
- Heroic Labs — "How to build a battle pass" — https://heroiclabs.com/docs/hiro/guides/gameplay-mechanics/battle-pass/
- Beamable — "Building a battle pass" — https://help.beamable.com/Unity-5.0/unity/samples/battle-pass/

**Compliance:**
- FTC — "Loot boxes: What's in play?" (2020) — https://www.ftc.gov/business-guidance/blog/2020/08/loot-boxes-whats-play
- Reed Smith LLP — "FTC starts 2025 with a focus on safeguarding kids" — https://www.reedsmith.com/articles/ftc-starts-2025-with-a-focus-on-safeguarding-kids/
- blog.promise.legal — "Loot Box Laws by Jurisdiction 2025" — https://blog.promise.legal/loot-box-laws-game-developers/
- EGDF — "Dark Patterns (2025)" — https://www.egdf.eu/documentation/7-balanced-protection-of-vulnerable-players/consumer-protection/dark-patterns-2025/
- EGDF — "Random elements / loot boxes (2025)" — https://www.egdf.eu/documentation/7-balanced-protection-of-vulnerable-players/consumer-protection/lootboxes/
- EP Think Tank — "Regulating dark patterns in the EU" — https://epthinktank.eu/2025/01/14/regulating-dark-patterns-in-the-eu-towards-digital-fairness/
- Osborne Clarke — "Digital Fairness Act Unpacked: Dark Patterns" — https://www.osborneclarke.com/insights/digital-fairness-act-unpacked-dark-patterns
- BEUC — "Meta changes to pay-or-consent" — https://www.beuc.eu/news/meta-changes-pay-or-consent-user-flow-must-remove-all-dark-patterns-and-provide-less
- CBTW — "Dark Patterns Are Now Illegal in the EU" — https://cbtw.tech/insights/illegal-dark-patterns-europe
- ZwillGen — "CPCN Virtual Currency Consumer Protection Guidelines" — https://www.zwillgen.com/gaming/cpcn-announces-virtual-currency-consumer-protection-guidelines/

**Academic / Research:**
- ResearchGate — "Battle pass capitalism" (2021) — https://www.researchgate.net/publication/350320553_Battle_pass_capitalism
- OSF Preprints — "The Battle Pass: a Mixed-Methods Investigation" — https://osf.io/preprints/osf/vnmeq_v2
