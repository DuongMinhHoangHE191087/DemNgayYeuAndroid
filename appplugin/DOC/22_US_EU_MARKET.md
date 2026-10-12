# 22 — Thị trường US & EU 2026: hành vi chi trả, cấu trúc monetization và môi trường pháp lý

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Mobile games/apps US & EU (incl. UK) 2025–2026: (1) quy mô doanh thu & ARPU; (2) hành vi payer & spending concentration; (3) cơ cấu monetization mix; (4) môi trường pháp lý mới (loot box, children's privacy, state privacy laws); (5) implications cho monetization SDK.

---

## 1. Quy mô thị trường & ARPU benchmarks

### 1.1 Global context

Doanh thu mobile game IAP toàn cầu 2025: **$81.75 tỷ** (+1.3% YoY). Tổng chi tiêu iOS + Google Play: **$167 tỷ** (+10.6%). Năm đầu tiên non-game app revenue ($85.6B) vượt game revenue ($81.8B), do AI apps tăng +254% YoY. Downloads giảm ~7% nhưng monetization per user tăng. [Market report — Sensor Tower SOG/SOM 2026]

### 1.2 United States

- **ARPU mobile games (2025):** ~$60.58 — cao nhất global. (2026 projection: $61–$65, có nguồn ước tính đến $94.53 khi tính tổng revenue per user.) [Secondary — Statista / Tekrevol]
- **Market size 2026:** $39.1 tỷ. [Secondary — Statista]
- **212.3 triệu** người Mỹ chơi game hàng tuần; mobile phổ biến nhất (80% gamers). [Primary research — ESA 2026]
- App Store gaming IAP revenue cao hơn Google Play ~75%. [Market report — Sensor Tower]

### 1.3 Europe

- **€7.53 tỷ** doanh thu global từ EU studios trong 2025. **320 triệu** người châu Âu chơi mobile games (~61% dân số). Đóng góp **€5.9 tỷ** GDP; **63,340 jobs**. [Primary research — King/Nordicity via GamesIndustry.biz]
- Dự báo vượt **€8 tỷ** vào 2028. [Primary research — King/Nordicity]
- **EU ARPU (2026):** ~$50–55 Western Europe (thấp hơn US 10–15% do VAT/PPP). [Secondary — Statista]
- Broader EU gaming market (console+PC+mobile): ~$29.3 tỷ. [Secondary — IMARC Group]

### 1.4 So sánh US/EU vs SEA

| Metric | US | Western EU | SEA (VN/TH/PH/ID) |
|--------|-----|-----------|---------------------|
| ARPU (mobile game) | ~$60–65 | ~$50–55 | ~$3–8 |
| Payer conversion | 3–5% | 2–4% | 1–3% |
| eCPM rewarded video | $15–50 | $10–35 | $1–5 |
| IAP vs Ads split (hybrid-casual) | ~50/50 to 60/40 | ~55/45 | ~70/30 to 80/20 |

[Bảng tổng hợp — Market report + Secondary]

Chênh lệch ARPU US vs SEA: **8–20x** — 1 user US = 8–20 user SEA cùng monetization mechanic. [Secondary — Statista, Tenjin, Appodeal]

---

## 2. Hành vi payer & spending concentration

### 2.1 Whale/Dolphin/Minnows

| Segment | % of Payers | Monthly Spend | Revenue Share |
|---------|------------|--------------|---------------|
| **Whales** (>$500/mo) | ~2% payers (<0.1% all) | $500–$5,000+ | ~50%+ revenue |
| **Dolphins** ($20–500) | ~13% payers | $20–$500 | ~30% revenue |
| **Minnows** ($1–20) | ~85% payers | $1–$20 | ~20% revenue |
| **F2P** (non-payers) | ~96–97% all | $0 | 0% (ad value) |

[Secondary — Zendle et al. 2023; Kaggle 2025; Business of Apps]

**Top 1% spenders = ~38% tổng revenue** (Zendle et al., peer-reviewed). [Primary research]

**Nhân khẩu học whale thay đổi:** phụ nữ trên 53 tuổi trở thành nhóm chi tiêu lớn, không phải thanh thiếu niên. [Secondary — RewardEdPlay] SDK segment targeting không nên giả định whale = young male hardcore gamer.

### 2.2 Subscription fatigue — yếu tố then chốt

- **41% consumers** report subscription fatigue; **73%** người Mỹ overwhelmed. [Secondary — Just Pricing 2026, LowerMySubs]
- **47% consumers** hủy ít nhất 1 subscription trong 2026 (tăng từ 31% năm 2024). [Market report — Zuora 2026]
- Hộ gia đình Mỹ giảm từ **4.1 subs (2024) → 2.8 (2025)**. [Secondary — Self Financial]
- Chi tiêu trung bình subscriptions: **$273/tháng**; **89% underestimate** actual spending. [Secondary — West Monroe]
- **65%** coi flexibility (pause/cancel anytime) là quan trọng nhất. **55%** hủy vì giảm chất lượng; chỉ 35.5% hủy vì giá tăng. [Secondary]

**Implication:** Subscription model cần pause/resume, no lock-in, demonstrate value mỗi kỳ billing.

### 2.3 Battle pass / Season pass

- **26% người Mỹ (8+)** đã mua season/battle pass. **58%** đã thực hiện bất kỳ in-game purchase. [Primary research — ESA 2026]
- Battle passes = ~**22% tổng IAP revenue** global; xuất hiện trong ~**60% top-grossing titles**. [Secondary]
- Holders có D30/D60 retention cao hơn non-holders **40–80%**; repurchase rate **50–70%**. [Case study — self-reported]
- Conversion trong active users: **15–20%** khi well-timed. [Case study — self-reported]
- Giá: US ~$9.99; EU ~€11.99 (incl. ~21% VAT — chênh lệch 27%). [Secondary]
- Subscriptions + season passes: **~18.42%** revenue share tại Europe — segment tăng nhanh nhất. [Secondary]

**Monetization mix global (2025):** IAPs 62%, Ads 26%, Subscriptions 12%. [Secondary — Tekrevol]

---

## 3. Cơ cấu monetization: US/EU vs SEA

### 3.1 Hybrid-casual economics

- H1 2025: IAP revenue tăng **100% YoY**; $126M chỉ trong Q2. Tổng 2025: ~$4.2 tỷ. [Market report — AppMagic]
- Revenue split: **~40–50% IAP, ~50–60% ads**. [Case study — self-reported, Verve, Appodeal]

### 3.2 US/EU specifics

- eCPM cao ($15–$50 rewarded video) → ad revenue carry game mà không cần heavy IAP. [Market report — Tenjin 2026]
- Payer rate 3–5% cho hybrid-casual; mỗi payer ARPPU cao. Puzzle có conversion cao nhất iOS; Strategy/RPG chiếm share IAP lớn nhất. [Secondary; Market report — Sensor Tower]
- **Non-game app revenue lần đầu vượt game revenue** 2025 — competition cho user wallet tăng. [Market report — Sensor Tower]

### 3.3 SEA comparison

- eCPM thấp ($1–$5) → IAP phải gánh phần lớn revenue. Cần volume lớn (high DAU). [Market report — Tenjin]
- "APAC skews Mid-core on spend; Europe/North America lean Casual" (adjoe 2025). [Secondary]
- IAP design nặng hơn: gacha, progression gates sâu, competitive spending mechanics. [Case study]

### 3.4 Genre ARPPU benchmarks (US)

| Genre | Primary Revenue | Avg. ARPPU (US) |
|-------|----------------|-----------------|
| Puzzle/Casual | Ads > IAP | $5–$20 |
| Hybrid-casual | Ads ≈ IAP | $15–$50 |
| Strategy/4X | IAP >> Ads | $50–$200+ |
| RPG/Gacha | IAP >> Ads | $30–$500+ |
| Sports (FIFA/Fortnite) | IAP + Battle Pass | $20–$100 |
| Social casino | IAP >> Ads | $50–$500+ |

[Bảng tổng hợp — Secondary từ nhiều nguồn]

---

## 4. Môi trường pháp lý 2025–2026

### 4.1 PEGI loot box age rating (June 2026)

Thay đổi lớn nhất trong lịch sử PEGI:

- Từ **June 2026**, games có loot boxes/paid random items → **PEGI 16 tối thiểu** (toàn châu Âu incl. UK). [Primary docs — PEGI via BBC, Osborne Clarke]
- **Social casino** → PEGI 18. **NFT/blockchain games** → PEGI 18 (July 2026). [Primary docs — PEGI]
- Áp dụng cho **new submissions** từ June 2026; existing titles không bị retroactively re-rated. [Primary docs — Osborne Clarke]
- PEGI giới thiệu **"interactive risk categories"** — đánh giá monetisation mechanics, communication features, engagement design (không chỉ content). [Primary docs — PEGI / Reed Smith]
- Ví dụ: EA Sports FC (trước đây PEGI 3) có thể nâng lên PEGI 16 do loot box system. [Secondary]

### 4.2 UK ASA enforcement notice (February 2026)

- Enforcement Notice yêu cầu disclosure loot boxes trong quảng cáo và app store listings. [Primary docs — ASA]
- Từ **26 May 2026**, ASA actively monitoring compliance. [Primary docs — ASA via Lewis Silkin]
- Loot box info phải **immediately visible** trên app store page; generic "in-app purchases" label **không đủ**. [Primary docs — Lewis Silkin]

### 4.3 EU Digital Fairness Act (DFA) — status August 2026

- DFA trong **Commission preparation phase**; legislative proposal dự kiến **Q4 2026**. Final adoption: late 2027. [Primary docs — European Parliament Legislative Train Schedule]
- Public consultation July–October 2025; responses January 2026; impact assessment Q2 2026. [Primary docs]
- **Dự kiến provisions:** potential ban loot boxes cho minors; disclosure drop rates; real-world cost transparency cho virtual currencies; subscription renewal reminders; restrictions pay-to-win. [Primary docs — Freshfields]

### 4.4 UK ICO — Children's Code enforcement

- **December 2025:** ICO khởi động monitoring **10 popular mobile games** played by children. [Primary docs — ICO]
- **February 2026 — First Children's Code fines:** Reddit **£14.47M**; MediaLab (Imgur) **£247,590** (first financial penalty under Children's Code). [Primary docs — ICO]
- ICO: age self-declaration **không đủ** để protect children. Nếu không xác định chắc chắn user age → **apply Children's Code standards to ALL users**. [Primary docs — ICO]
- **ICO 2026 priorities:** AI/biometrics, Children's Code enforcement guidance, general enforcement. [Primary docs — Bratby Law]

### 4.5 FTC Click-to-Cancel Rule

- Late 2024: FTC finalizes Negative Option Rule.
- **July 8, 2025:** Eighth Circuit **vacates rule**. [Primary docs — Jones Day, Goodwin Law]
- **Jan 30, 2026:** FTC issues ANPRM để restart rulemaking. Comments due April 13, 2026. [Primary docs — Goodwin Law]
- **Status August 2026:** Không có active federal rule, nhưng FTC **vẫn aggressively enforce** qua ROSCA + FTC Act Section 5 (kiện Uber, LA Fitness, Chegg). ~**30 states** có auto-renewal laws riêng. [Primary docs; Secondary — Jones Day]

**Implication cho SDK:** Subscription cancellation phải dễ như sign-up — de facto standard dù federal rule bị vacated.

### 4.6 COPPA Amended Rule

- **Jan 16, 2025:** FTC finalizes amendments. **Published April 22, 2025.** Takes effect June 23, 2025. **Full compliance: April 22, 2026.** [Primary docs — FTC.gov]
- **Feb 25, 2026:** FTC issues policy statement on age verification technologies. [Primary docs — FTC]
- **Key changes:** Parents phải **opt-in** cho third-party advertising targeting children; expanded personal information definition; new biometric data provisions; enhanced retention requirements; limiting monetization of kids' data. [Primary docs — FTC]

### 4.7 California AADC — Ninth Circuit March 2026

- **March 12, 2026:** Ninth Circuit mixed ruling (No. 25-2366). [Primary docs — HK Law]
- **Enforced (from April 3, 2026):** age estimation requirements, default privacy settings for minors. [Primary docs — HK Law]
- **Still enjoined:** DPIA requirements, data-use restrictions, dark-pattern restrictions. [Primary docs — HK Law, Wiley Law]
- Case ongoing; further litigation expected. [Primary docs]

### 4.8 US State privacy laws — wave 2025–2026

**20 states** có comprehensive data privacy laws effective tính đến 2026. [Primary docs — Osano, MultiState]

Key states for gaming profiling:

| State | Law | Effective | Profiling provisions |
|-------|-----|-----------|---------------------|
| **Minnesota** | MCDPA | July 31, 2025 | **Strong** — opt out profiling, question results, review data, correct inaccuracies |
| **Maryland** | MODPA | Oct 1, 2025 (enforce April 1, 2026) | Strict data minimization; sensitive data "strictly necessary" |
| Texas | TDPSA | July 1, 2024 | Opt-out targeted ads, data sale, profiling |
| Indiana/Kentucky/Rhode Island | Various | Jan 1, 2026 | Standard profiling opt-out |

[Primary docs — Osano, MultiState, Mintz]

**Minnesota MCDPA đáng chú ý nhất cho gaming:** consumers có quyền question profiling results, review data, opt-out automated decisions. Không có gaming-specific exemption. [Primary docs — Mintz]

**Implication:** Personalization engine (churn prediction, LTV scoring, offer targeting) phải comply với profiling opt-out tại nhiều states.

### 4.9 App store fee landscape 2026

**Apple:** Standard 30%; Small Business Program **15%** (< $1M/yr). EU alternative billing: 20% commission + layered fees; EU alternative stores: 5%.

**EU layered fee model (post-DMA, August 2026):** 2% Initial Acquisition Fee (new users, 6-month window) + Store Services 5%/13% (Tier 1/2) + 5% Core Technology Commission. Tổng effective: ~7–18%. [Primary docs — Apple Developer, FunnelFox]

**Google Play:** Service fee 10% (first $1M); billing fee **5% IAP / 15% subs day-one** (US/EEA/UK). Third-party billing allowed.

**Effective June 30, 2026:** Google decouples fees — 5% billing + 20% IAP / 10% subs. Registered App Stores introduced (global rollout through Sept 2027). [Primary docs — Android Developers Blog]

---

## 5. Implications cho Monetization SDK targeting US/EU

### 5.1 Feature priorities

| Priority | Feature | Lý do |
|----------|---------|-------|
| P0 | **Battle pass / season pass engine** | 26% US players bought; 22% IAP revenue; retention +40–80% |
| P0 | **Hybrid monetization orchestrator** | Cân bằng IAP + ads theo geography (US: high eCPM → ad-heavy viable) |
| P0 | **Subscription management (pause/resume/cancel)** | Subscription fatigue 41%; click-to-cancel de facto standard |
| P0 | **Loot box compliance module** | PEGI 16, ASA disclosure, potential DFA ban |
| P1 | **Cosmetic-first economy design** | US/EU anti-pay-to-win sentiment |
| P1 | **Geo-segmented pricing** | VAT-inclusive EU; PPP-adjusted |
| P1 | **Profiling opt-out compliance** | Minnesota MCDPA + 20 state laws |
| P1 | **Children's privacy gate** | COPPA amended rule in force; ICO enforcement |
| P2 | **Alternative payment routing** | EU DMA external payments; web-to-app billing |
| P2 | **Whale identification & personalization** | 2% payers = 50%+ revenue; demographic thay đổi |

### 5.2 Compliance must-haves

1. **Loot box transparency:** PEGI disclosure (June 2026+); ASA pre-download disclosure (May 2026+); drop rate display bắt buộc; prepare cho potential EU-wide ban (DFA Q4 2026).

2. **Children's privacy:** Age estimation/verification (CAADCA + ICO); COPPA consent flows (full force April 2026); default high-privacy cho minors; no targeted ads <13 (COPPA) hoặc <16 (một số states).

3. **Subscription:** Cancel as easy as sign-up (FTC standard); renewal reminders; clear pricing; no dark patterns.

4. **Profiling/data:** Opt-out automated decision-making (MN, TX, 18+ states); data minimization (Maryland MODPA); DPIA khi required (CA — hiện bị enjoin).

5. **Store compliance:** IARC/PEGI ratings accuracy; interactive risk categories; specific loot box disclosure (không generic "in-app purchases").

### 5.3 Strategic positioning

**Why US/EU despite regulatory burden:**
- ARPU 8–20x SEA; payer willingness-to-pay cao nhất global
- Battle pass adoption 26%; platform fees giảm (đặc biệt EU)
- Payment infrastructure mature

**Trade-off:**
- Compliance cost cao (multi-jurisdiction); legal risk (ICO fines đến £14.47M; FTC aggressive)
- Subscription fatigue = need superior value proposition
- PEGI 16 loot boxes = limit addressable market cho games targeting <16

**Recommended approach:**
1. Build compliance-first: module architecture enable/disable per jurisdiction
2. Lead với battle pass + hybrid monetization, không loot box
3. Subscription best-in-class (flexible, transparent, easy cancel)
4. Personalization respect profiling opt-out (state laws)
5. Age-gating built-in, không optional add-on
6. Geo-segmented offer engine: US/EU pricing ≠ SEA pricing

---

## Không xác minh được

1. **US ARPU $60.58:** Statista projection; không có primary methodology. Range các nguồn: $60–$95 tùy definition.
2. **Whale = women over 53:** RewardEdPlay/Facebook data — survey nhưng không tìm được original methodology. Có thể self-selected sample.
3. **Battle pass 15–20% conversion:** Industry estimates / self-reported case studies; không có large-scale independent verification.
4. **DFA timeline Q4 2026:** Indicative EC work programme; EU legislation thường trễ 6–12 months.
5. **Hybrid-casual IAP/ads split 40–50/50–60:** Multiple sources nhưng ratio phụ thuộc genre/platform/geography.
6. **EU ARPU $50–55:** Extrapolated từ global figures + VAT adjustment; không có clean EU-specific data point.
7. **47% canceled subscriptions (Zuora 2026):** Widely cited nhưng original report behind paywall.
8. **PEGI 16 enforcement mechanism cho digital-only games:** Chưa rõ PEGI/IARC enforce cho mobile-only games không qua physical retail.

---

## Nguồn chính

| # | Label | Title | URL |
|---|-------|-------|-----|
| 1 | SENSOR_TOWER_SOG_2026 | State of Gaming 2026 | https://sensortower.com/blog/state-of-gaming-2026 |
| 2 | SENSOR_TOWER_SOM_2026 | State of Mobile 2026 | https://sensortower.com/blog/state-of-mobile-2026 |
| 3 | ESA_2026 | 2026 Essential Facts — ESA | https://www.theesa.com/resources/essential-facts-about-the-us-video-game-industry/2026-data/ |
| 4 | KING_NORDICITY | European mobile games €7.53bn — GamesIndustry.biz | https://www.gamesindustry.biz/european-mobile-games-sector-generates-753bn-globally-in-2025 |
| 5 | PEGI_LOOTBOX | PEGI 16+ for loot box games — BBC | https://www.bbc.com/news/articles/cge84xqjg5lo |
| 6 | ASA_ENFORCEMENT | Loot box disclosure — ASA | https://www.asa.org.uk/resource/enforcement-notice-disclosure-of-loot-boxes-in-app-stores.html |
| 7 | ASA_LEWIS_SILKIN | ASA tough measures on loot boxes — Lewis Silkin | https://www.lewissilkin.com/insights/2026/02/27/new-tough-measures-brought-in-by-the-asa-on-advertising-loot-boxes-in-the-uk-102mknq |
| 8 | EU_DFA_TRACKER | Digital Fairness Act — European Parliament | https://www.europarl.europa.eu/legislative-train/theme-protecting-our-democracy-upholding-our-values/file-digital-fairness-act |
| 9 | DFA_FRESHFIELDS | DFA game developer guide — Freshfields | https://www.freshfields.com/en/our-thinking/blogs/technology-quotient/the-eus-proposed-digital-fairness-act-a-game-developers-guide-to-potential-imp-102ltio |
| 10 | ICO_MOBILE_GAMES | Children's privacy in mobile games — ICO | https://ico.org.uk/about-the-ico/media-centre/news-and-blogs/2025/12/children-s-online-privacy-in-mobile-games-under-spotlight/ |
| 11 | ICO_REDDIT | Reddit £14.47m fine — ICO | https://ico.org.uk/about-the-ico/media-centre/news-and-blogs/2026/02/reddit-issued-with-1447m-fine-for-children-s-privacy-failures/ |
| 12 | ICO_IMGUR | Imgur/MediaLab fine — ICO | https://ico.org.uk/about-the-ico/media-centre/news-and-blogs/2026/02/imgur-owner-medialab-fined-over-children-s-privacy-failures/ |
| 13 | FTC_COPPA | FTC Children's Privacy Rule amendments | https://www.ftc.gov/news-events/news/press-releases/2025/01/ftc-finalizes-changes-childrens-privacy-rule-limiting-companies-ability-monetize-kids-data |
| 14 | FTC_CTC_JONES | FTC Click-to-Cancel revival — Jones Day | https://www.jonesday.com/zh-hans/insights/2026/05/ftc-revives-clicktocancel-rule-new-risks-for-subscription-businesses |
| 15 | FTC_CTC_GOODWIN | FTC Click-to-Cancel new life — Goodwin | https://www.goodwinlaw.com/en/insights/publications/2026/02/alerts-practices-ba-ftcs-click-to-cancel-rule-gets-new-life |
| 16 | CA_AADC | Ninth Circuit CAADCA ruling — HK Law | https://www.hklaw.com/en/insights/publications/2026/03/ninth-circuit-issues-mixed-ruling-on-california-age-appropriate-design |
| 17 | STATE_PRIVACY | U.S. Data Privacy Laws 2026 — Osano | https://www.osano.com/us-data-privacy-laws |
| 18 | STATE_PRIVACY_MS | 20 State Privacy Laws 2026 — MultiState | https://www.multistate.us/insider/2026/2/4/all-of-the-comprehensive-privacy-laws-that-take-effect-in-2026 |
| 19 | MN_PROFILING | Minnesota MCDPA profiling — Mintz | https://www.mintz.com/insights-center/viewpoints/2826/2024-12-30-minnesotas-consumer-privacy-law-takes-aim-profiling-and |
| 20 | APPLE_EU_FEES | App Store Fees 2026 EU — FunnelFox | https://blog.funnelfox.com/apple-app-store-fees-2026-eu-dma/ |
| 21 | GOOGLE_PLAY_2026 | Choice and openness — Android Developers | https://android-developers.googleblog.com/2026/03/a-new-era-for-choice-and-openness.html |
| 22 | TEKREVOL | Mobile Game Revenue Statistics 2026 | https://www.tekrevol.com/blogs/mobile-game-revenue-statistics/ |
| 23 | APPMAGIC | Monetization Report 2025 | https://appmagic.rocks/research/monetization-report-2025/ |
| 24 | SUB_FATIGUE | Subscription Fatigue Stats 2026 | https://www.readless.app/blog/subscription-fatigue-statistics-2026 |
| 25 | TENJIN_AD | Ad Monetization Benchmark 2026 | https://tenjin.com/blog/ad-mon-gaming-2026/ |
| 26 | PEGI_OSBOURNE | Loot boxes 16+ Europe — Osborne Clarke | https://www.osborneclarke.com/insights/games-loot-boxes-get-16-plus-age-rating-europe-under-pegi-overhaul |
