# 21 — Đo lường & attribution cho monetization tại US/EU năm 2026

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Bức tranh measurement/attribution thực tế mà các studio mobile game tại US và EU đang vận hành giữa 2026 — sau khi Privacy Sandbox bị khai tử, AdAttributionKit đã trưởng thành, và MMP chuyển vai trò từ attribution sang modeled intelligence layer. Trọng tâm: đo hiệu quả monetization (IAP, subscription, ad revenue, web shop), không chỉ UA.

## Evidence labels

- **[Primary docs]**: tài liệu chính thức từ Apple, Google, MMP.
- **[Primary research]**: báo cáo gốc từ MMP, vendor, hoặc cơ quan quản lý.
- **[Case study — self-reported]**: case study do vendor/studio công bố (có thể bias).
- **[Secondary]**: báo chí, analyst, tổng hợp bên thứ ba.

---

## A. iOS 2026: SKAdNetwork 4.0 ổn định, AdAttributionKit là tương lai được khuyến nghị

### A.1. Trạng thái hiện hành — hai framework chạy song song

Tính đến Q2/2026, iOS có **hai lớp attribution chạy đồng thời** và chưa có timeline SKAN bị tắt hẳn [Secondary — Adjust, Singular, Linkrunner]:

| Framework | Giới thiệu | Tình trạng 8/2026 |
|-----------|-----------|-------------------|
| **SKAdNetwork 4.0** | WWDC 2022, iOS 16.1 | Vẫn hoạt động, vẫn là phương thức attribution chính cho phần lớn ad network |
| **AdAttributionKit (AAK)** | WWDC 2024, iOS 18 | **Framework được Apple khuyến nghị**, đã cập nhật lớn tại WWDC 2025 (iOS 18.4) |
| **SKAdNetwork 5** | Spring 2026 (iOS 19) | Cập nhật minor: thêm postback fields, privacy manifest tightening [Secondary — AuditSocials] |

**Apple Ads đã chuyển sang AdAttributionKit** từ iOS 26.2 — tín hiệu rõ nhất về hướng đi dài hạn [Secondary — AppsFlyer].

### A.2. AdAttributionKit — 6 cải tiến lớn (WWDC 2025)

AAK WWDC 2025 update mang lại 6 thay đổi có ý nghĩa thực tiễn cho monetization [Secondary — Segwise, Adjust, Singular]:

1. **Overlapping re-engagement windows**: đo nhiều lượt re-engage liên tiếp mà không mất tín hiệu. Quan trọng cho subscription apps — biết user quay lại sau win-back campaign nào.
2. **Configurable attribution windows**: tùy chỉnh cửa sổ attribution theo từng loại conversion (không còn cố định 0–2d / 3–7d / 8–35d).
3. **Re-engagement cooldowns**: chặn trùng lặp re-engagement attribution trong khoảng thời gian cấu hình.
4. **Country codes trong postbacks**: phân tích geography-level mà không cần tự enrich.
5. **Developer mode cho testing**: test conversion value trước khi ship production.
6. **Phương thức hiển thị ad mới**: hỗ trợ creative formats bổ sung.

### A.3. Conversion value schema — best practices 2026

Schema SKAN 4.0 vẫn giữ nguyên: **6-bit fine value (0–63)** chỉ ở postback 1 + **coarse value (Low/Medium/High)** ở cả 3 postback. Schema design chuyển sang AAK giữ nguyên logic — không cần rebuild [Secondary — Linkrunner, Adjust].

**Consensus 2026 cho game monetization** [Secondary — AppsFlyer SKAN Conversion Studio, Aarki, Kochava]:

| Postback | Window | Fine value strategy | Coarse value strategy |
|----------|--------|---------------------|----------------------|
| **PB1** | 0–2 ngày | Map 64 bucket thành tiered LTV: 0–15 = no-event, 16–31 = ad-view only, 32–47 = first IAP < $5, 48–55 = first IAP $5–20, 56–63 = whale signal (IAP > $20 hoặc subscription start) | Low = install only, Medium = engagement, High = any revenue |
| **PB2** | 3–7 ngày | (chỉ có coarse) | Low = no repeat, Medium = repeat engagement, High = repeat purchase hoặc subscription active |
| **PB3** | 8–35 ngày | (chỉ có coarse) | Low = churned, Medium = active no-spend, High = LTV growth |

**`lockWindow` strategy**: khóa conversion value sớm khi user đã thể hiện whale signal (e.g., purchase > $20) — tránh bucket bị overwrite bởi event nhỏ hơn sau đó [Secondary — Aarki, Adjust].

### A.4. Crowd Anonymity — rào cản thực tế

Apple áp dụng **crowd anonymity tiers** cho ad source. Nếu campaign không đạt ngưỡng tối thiểu, postback chỉ trả về coarse value hoặc không có conversion value. [Secondary — AppsFlyer, Singular]

**Thực tế 2026**: campaign nhỏ (< ~50 installs/ad source/window) gần như chỉ nhận coarse value. Đây là lý do **modeled attribution** trở thành phần bắt buộc, không phải tùy chọn.

### A.5. Web-to-app attribution — Safari 26 siết chặt

Safari 26 (phát hành cùng iOS 26) tạo thêm rào cản cho web-to-app flow [Secondary — AppsFlyer, TAGGRS, Louder]:

- **Link Tracking Protection**: strip tracking parameters (gclid, fbclid, custom UTM) khỏi URL ở cả regular và Private Browsing.
- **Advanced Fingerprinting Protection (AFP)**: mặc định bật cho tất cả browsing modes, chặn script fingerprinting.
- **User-Agent Freeze**: đóng băng UA string.
- **Hệ quả**: web-to-app attribution truyền thống qua URL parameter gần như vô hiệu. Deferred deep linking phải dựa vào Universal Links + App Clips + server-side click storage.

**App Clips** trở thành cầu nối web-to-app quan trọng: user click Universal Link → App Clip load instant → capture intent/context → khi full app install, context được chuyển qua [Secondary — Branch NativeLink, Kochava].

### A.6. ATT opt-in rate — tín hiệu thực

ATT opt-in trên iOS dao động **25–35%** tùy vertical và geography (US thường thấp hơn EU) [Secondary — Adjust, AppsFlyer Q1 2025 data]. Khoảng **27% iOS users** có IDFA available [Case study — AppsFlyer self-reported]. Con số này ổn định từ 2024, không có dấu hiệu cải thiện.

---

## B. Android 2026: sau Privacy Sandbox — thực tế measurement

### B.1. Privacy Sandbox đã bị khai tử — chi tiết

Ngày **17/10/2025**, Google tuyên bố phase-out phần lớn Privacy Sandbox technologies [Primary docs — Google Privacy Sandbox blog]:

| API | Trạng thái |
|-----|-----------|
| **Topics API** | Deprecated — ngừng triển khai rộng |
| **Protected Audience API (PAAPI/FLEDGE)** | Deprecated — không triển khai rộng |
| **SDK Runtime** | Deprecated |
| **Attribution Reporting API** | Deprecated (Android); Chrome web deprecation bắt đầu tháng 1/2026 |
| **CHIPS (Cookies Having Independent Partitioned State)** | **Giữ lại** |
| **FedCM (Federated Credential Management)** | **Giữ lại** |
| **Private State Tokens** | **Giữ lại** |

**Lý do khai tử**: adoption quá thấp, CMA UK regulatory pressure, và Google từ bỏ kế hoạch deprecate third-party cookies (tháng 4/2025) — làm mất rationale cho Privacy Sandbox [Secondary — Proton, SearchEngineLand, arXiv].

### B.2. Measurement thực tế trên Android 2026

Không giống iOS, Android 2026 vẫn có nhiều tín hiệu deterministic [Secondary — Appvertiser, RocketshipHQ, Adjust]:

| Tín hiệu | Trạng thái | Ý nghĩa |
|----------|-----------|---------|
| **GAID (Google Advertising ID)** | **Vẫn còn**, nhưng opt-in declining ~30–38% ở EU (DMA impact), cao hơn ở US (~50–60%) [Secondary — Appvertiser] | Vẫn là nguồn attribution chính cho Android, nhưng không thể phụ thuộc 100% |
| **Install Referrer API** | **Hoạt động đầy đủ** | Deterministic install attribution từ Play Store — vẫn là gold standard cho Android UA |
| **Google Play Data Safety** | **Bắt buộc** | Mọi data collection/sharing phải khai đúng form — sai lệch = rủi ro gỡ app |
| **Play Integrity API** | **Hoạt động** | Verify app/device authenticity — hỗ trợ fraud detection |
| **First-party server events** | **Bắt buộc tự xây** | Không có Privacy Sandbox replacement, nên mọi post-install attribution phải tự build |

**Kết luận**: Android measurement 2026 = **Install Referrer + GAID (khi có) + first-party server-side events + modeled attribution cho gap**. Đơn giản hơn iOS nhưng đang dịch chuyển nhanh về phía consent-based.

### B.3. Consent-based measurement tại EU (DMA + GDPR)

EU có lớp ràng buộc bổ sung [Primary docs — IAB TCF v2.3, Google EU Consent Policy]:

- **IAB TCF v2.3** (2025–2026): standard consent signal cho programmatic advertising. Google App Attribution yêu cầu developer support TCF v2 message qua UMP SDK [Primary docs — Google].
- **DMA (Digital Markets Act)**: gatekeepers (Google, Meta, Apple) phải xin consent rõ ràng trước khi kết hợp cross-service data. Impact: GAID opt-in rate ở EU thấp hơn US đáng kể.
- **Brussels Court ruling (5/2025)**: upheld sanctions against IAB Europe về TC String, nhưng áp dụng cho TCF v1.0/v2.0 — phiên bản 2.2+ vẫn được triển khai [Secondary — Freshfields, DLA Piper].
- **Thực tế**: chỉ ~12.85% apps analyzed triển khai TCF đầy đủ [Secondary — arXiv 2602.20222]. Compliance gap vẫn rất lớn.

---

## C. MMP landscape 2026: từ attribution engine → modeled intelligence layer

### C.1. Tổng quan positioning

| MMP | Positioning 2026 | Điểm mạnh | Điểm yếu |
|-----|-----------------|-----------|----------|
| **AppsFlyer** | Ecosystem breadth leader | SKAN Conversion Studio, modeled data (Bayesian + ML), single source of truth reconciliation, widest ad network integration | Giá cao nhất ở scale, billing events phức tạp |
| **Adjust** | Developer experience + transparency | SKAN implementation sạch, well-documented, fraud protection tốt, pricing minh bạch hơn | Ít automation hơn AppsFlyer |
| **Singular** | Cross-platform measurement | CTV attribution tốt, incrementality testing built-in, pricing cạnh tranh ($0.05/conversion) | Nhỏ hơn về ecosystem |
| **Kochava** | Omnichannel + fraud focus | AI-powered fraud prevention, CTV-to-mobile attribution, không gated features | Market share nhỏ hơn top 3 |
| **Tenjin** | Gaming-focused, budget-friendly | Ad revenue attribution, gaming benchmarks, pricing thấp | Ít enterprise features |

[Secondary — Vmobify, SplitMetrics, Tenjin blog, BusinessOfApps]

### C.2. SKAN advanced/managed modes

**AppsFlyer SKAN Conversion Studio** [Primary docs — AppsFlyer support]:
- **Custom mode**: advertiser tự map conversion value → revenue/event milestones.
- **Template mode**: dùng pre-built templates cho các vertical (gaming, subscription, ecommerce).
- **SKAN Modeled Data**: Bayesian + ML kết hợp SKAN postbacks + AppsFlyer reported data + probabilistic modeling để recover 30–60% non-organic traffic bị ẩn [Case study — AppsFlyer self-reported].
- **pLTV (Predicted Lifetime Value)**: ML model predict future LTV từ early signals, dùng để optimize campaign khi SKAN data delay 24–48h [Primary docs — AppsFlyer glossary].

**Singular** [Secondary — Singular docs]:
- Managed mode: automatic conversion value updates dựa trên configured models.
- SKAN 4 postbacks + conversion value management tích hợp.

**Adjust** [Secondary — Adjust docs]:
- SKAN 4 implementation với clear developer documentation.
- Conversion value configuration qua SDK hoặc dashboard.

**Kochava** [Secondary — Kochava blog]:
- SKAN 4 prep checklist + conversion value strategy guides.
- Multi-touch attribution + no gated tools/unlock fees.

### C.3. Modeled attribution & pLTV products

Mô hình chung 2026 [Secondary — AppsFlyer, Adjust, Singular, Airbridge]:

```
Input: SKAN postbacks (delayed, aggregated)
       + deterministic events (ATT opt-in users)
       + ad network reported metrics
       + first-party server events

Output: Modeled installs (recovered hidden conversions)
        + pLTV scores (predicted 7d/30d/180d LTV)
        + Blended ROAS (SKAN + deterministic unified view)
```

**AppsFlyer** claim recover 30–60% non-organic traffic qua modeling [Case study — self-reported].

**Airbridge** cung cấp hướng dẫn tính pLTV bằng 2 phương pháp: survival analysis và regression-based prediction [Secondary — Airbridge blog].

### C.4. Fraud protection

Ad fraud projected vượt **$100 tỷ** năm 2026 [Secondary — Improvado]:

| MMP | Fraud approach |
|-----|---------------|
| **Kochava** | AI-powered + ML behavioral analysis + real-time detection, built-in không extra cost [Primary docs — Kochava] |
| **Adjust** | Mobile-specific fraud detection, excels ở install fraud và click spam |
| **AppsFlyer** | Protect360 — integrated fraud prevention layer |
| **Singular** | Fraud prevention tích hợp trong attribution layer |

### C.5. CTV & web-to-app attribution

**CTV** đang trở thành kênh UA quan trọng cho gaming [Secondary — Singular, Kochava, Starti.ai]:

- **Singular**: CTV attribution đo impression trên TV/OTT → kết nối với mobile/web conversions. DraftKings sử dụng ghost ads + CTV attribution của Singular [Case study — Singular interview with DraftKings].
- **Kochava**: CTV analytics platform cho mobile app advertisers, omnichannel attribution CTV → mobile [Primary docs — Kochava]. Partnership với Adomni cho CTV incrementality [Secondary — BusinessWire].
- **Starti.ai (2026)**: xếp Kochava trong top 7 CTV analytics platforms cho attribution [Secondary].

**Web-to-app**: mọi MMP đều hỗ trợ deferred deep linking qua Universal Links/App Links, nhưng Safari 26 Link Tracking Protection làm giảm accuracy đáng kể [Secondary — AppsFlyer, TAGGRS].

### C.6. Pricing reality cho studios

[Secondary — SolarEngine pricing comparison, Linkrunner, Strataigize, CheckThat]

| MMP | Entry | Per-conversion | Scale cost |
|-----|-------|---------------|------------|
| **AppsFlyer** | Free (12K conv/yr) | ~$0.07 (Growth) | $5K–$50K+/tháng ở 500K+ conv |
| **Adjust** | Sales-only | Enterprise negotiated | $5K–$50K+/tháng |
| **Singular** | ~$200/tháng | ~$0.05 | Lower overage ($0.02–0.014) |
| **Kochava** | Sales-only | Competitive | No unlock fees |
| **Tenjin** | Free tier | Gaming-focused | Thấp nhất cho indie studios |
| **Linkrunner** | From $0.01/install | Tiered | Emerging competitor |

**Đối với game studio nhỏ/vừa** (50K–200K installs/tháng): chi phí MMP ~$3.5K–$14K/tháng cho AppsFlyer/Adjust, hoặc $2.5K–$10K cho Singular. Tenjin/free tier phù hợp giai đoạn đầu.

---

## D. Incrementality & experimentation cho monetization

### D.1. Tại sao incrementality quan trọng cho monetization (không chỉ UA)

Attribution truyền thống trả lời "user nào đến từ channel nào." Incrementality trả lời **"user nào đã mua vì campaign, và ai sẽ mua dù không có campaign."** [Secondary — AdLibrary, SegmentStream]

**Áp dụng cho monetization**:
- Offer campaign (discount 20% cho returning user): có thực sự tạo incremental revenue, hay user đó sẽ mua anyway?
- Push notification monetization: revenue attributed thực sự incremental, hay user đã định mua?
- Subscription paywall A/B test: conversion tăng thực sự do thiết kế mới, hay do seasonal effect?

### D.2. Phương pháp luận

**Holdout testing** [Secondary — SegmentStream, Customer Science]:
- Chia user thành treatment (nhận campaign) và control/holdout (không nhận).
- So sánh conversion rate giữa hai nhóm.
- **Best practice 2026**: allocate ~10% users vào holdout, chạy tối thiểu 2 tuần, dùng intent-to-treat analysis.
- **Áp dụng monetization**: holdout 10% users khỏi seeing a specific offer/paywall variant, đo incremental ARPU difference.

**Ghost Ads** [Secondary — Remerge, Tinuiti, Singular]:
- Tạo control group gần như miễn phí: ad network log user mà *lẽ ra* sẽ nhận ad nhưng không được serve (do budget/cap).
- So sánh conversion của served users vs ghost users.
- **DraftKings** sử dụng ghost ads để đo TV advertising incrementality [Case study — Singular interview].
- **RTB House (1/2026)**: ghost ads là "next-gen incrementality testing for performance marketing" [Secondary].

**Ghost Bids** [Secondary — Remerge]:
- Tương tự ghost ads nhưng áp dụng cho programmatic bidding — log users mà advertiser *thua* auction.
- Cleaner về mặt methodology vì không có selection bias từ ad serving decisions.

**PSA (Public Service Announcements)** [Secondary — Remerge]:
- Serve PSA ads (không có commercial intent) cho control group thay vì blank.
- Chi phí thấp hơn ghost ads nhưng không zero-cost.
- Useful khi ghost ads không available.

**Geo lift tests** [Secondary — AdLibrary, SegmentStream]:
- Randomize theo geography (DMA/region) thay vì user-level.
- Đo incremental lift bằng difference-in-differences giữa test và control geos.
- **Ưu điểm**: tránh contamination từ user-level spillover.
- **Áp dụng monetization**: test new pricing/offer strategy ở một số geos, so sánh ARPU lift vs control geos.

### D.3. Key finding: retargeting incremental ROAS thấp hơn reported 40–70%

[Secondary — Alex Neiman, Meta Ads 2026 analysis]

Phân tích Meta Ads 2026 cho thấy **retargeting incremental ROAS thực tế thấp hơn 40–70%** so với reported ROAS. Đây là tín hiệu mạnh cho thấy last-click attribution over-credit retargeting campaigns đáng kể — và mọi monetization campaign (win-back, re-engagement, subscription upsell) cần incrementality validation.

---

## E. Marketing Mix Modeling (MMM) revival

### E.1. Bối cảnh open-source MMM 2026

MMM quay lại mạnh mẽ do attribution data ngày càng noisy (ATT, Privacy Sandbox shutdown, cookie deprecation). Hai framework open-source chính [Secondary — Funnel.io, SEIO2026, Springer]:

| Framework | Developer | Language | Methodology | Status |
|-----------|-----------|----------|-------------|--------|
| **Robyn** | Meta Marketing Science | R | Frequentist + ML automation | Stable, widely adopted |
| **Meridian** | Google Ads | Python | Bayesian inference | Launched open-source **29/1/2025**, growing rapidly [Primary docs — Google blog] |
| **PyMC-Marketing** | Community | Python | Probabilistic programming | Flexible, smaller community |

[Primary docs — Google Meridian GitHub, Meta Robyn GitHub, SEIO2026 paper]

### E.2. Google Meridian — chi tiết

**Ra mắt 29/1/2025**, open-source hoàn toàn [Primary docs — Google blog]:
- **Bayesian MMM**: prior distributions cho channel coefficients, posterior inference cho ROI.
- **Reach & frequency modeling**: tích hợp R/F curves vào MMM.
- **Custom KPIs**: optimize cho revenue, installs, hoặc custom metric.
- **2026 updates (Google Marketing Live 2026)**: Meridian GeoX (geo-level MMM), Studio (visual interface), Data Manager (data pipeline integration) [Secondary — Elevarus].

### E.3. Lightweight MMM cho studios

Studio game nhỏ/vừa không cần chạy full Bayesian MMM. Approach thực tế [Secondary — Funnel.io, MarSci]:

1. **Bắt đầu với geo-level holdout**: 2–3 test geos vs control, chạy 4–8 tuần.
2. **Simple regression**: revenue ~ ad_spend_per_channel + seasonality + organic_baseline.
3. **Calibrate với incrementality test**: dùng ghost ads hoặc holdout result để set prior cho Bayesian model.
4. **Tooling**: R (Robyn), Python (Meridian), hoặc spreadsheet-level cho < 5 channels.

### E.4. Bayesian CLV tie-in

MMM + Bayesian CLV prediction tạo thành **closed-loop measurement**:
- MMM trả lời "channel nào tạo incremental revenue."
- Bayesian CLV trả lời "user từ channel nào có LTV cao nhất."
- Kết hợp → budget allocation tối ưu cho monetization, không chỉ UA.

[Secondary — Appier, Airbridge pLTV docs]

---

## F. First-party data & clean rooms

### F.1. Clean room landscape 2026

Data clean rooms đã trưởng thành thành infrastructure chính cho post-IDFA identity [Secondary — Decentriq, MarTech, Guideflow]:

| Platform | Mô hình | Điểm mạnh |
|----------|---------|-----------|
| **LiveRamp Clean Room** | Identity-first, RampID | Identity resolution native, cross-cloud activation (AWS, Snowflake, GCP) |
| **AWS Clean Rooms** | Cloud-native, multi-party | Tích hợp sẵn trong AWS stack, pricing theo usage |
| **Snowflake Data Clean Rooms** | Data warehouse-native | AI templates, native trong Snowflake ecosystem |
| **Decentriq** | Privacy-first, Swiss-based | Strong EU compliance, confidential computing |
| **InfoSum** | On-premise/hybrid | Data never leaves premise |
| **Databricks** | Delta sharing + clean rooms | Lakehouse-native |

### F.2. Identity resolution post-IDFA/cookie

**4 xu hướng chính 2026** [Secondary — MarTech]:

1. **Clean rooms thành core infrastructure**: không còn experimental, đã là production layer cho audience matching và measurement.
2. **ML-based matching**: machine learning thay thế deterministic matching cho fuzzy identity resolution.
3. **Real-time resolution**: thay thế batch processing — identity resolve trong milliseconds tại point of activation.
4. **Multi-ID ecosystems**: không có single universal ID — thay vào đó là identity graph kết nối nhiều signals (hashed email, phone, device graph, publisher IDs).

### F.3. Hashed email match rates

[Secondary — Hellyeah AI, ID5, AUDIENCES, Saber]:

- **Hashed email** (SHA-256) là deterministic ID phổ biến nhất post-IDFA.
- **Match rate thực tế**: biến thiên lớn tùy nguồn data — dưới 50% match rate được xem là insufficient cho server-side collection improvement [Secondary — Hellyeah AI, 8/2026].
- **Best-case scenarios** (first-party CRM data với verified emails): match rates "far in excess" of traditional methods [Secondary — Semcasting, AUDIENCES].
- **Thách thức**: chỉ ~27% iOS users có IDFA, và email availability phụ thuộc vào login/signup rate trong app.

### F.4. Top 10 ID solutions 2026

[Secondary — Adtelligent]:
Privacy-first identity solutions bao gồm: LiveRamp RampID, UID2 (The Trade Desk), ID5, PubMatic Identity, Epsilon CORE ID, BigDBM, FullContact, Neustar/Experian, Tapad, và hashed email direct matching.

### F.5. Ý nghĩa cho SDK sở hữu first-party events

SDK monetization nắm giữ **first-party event data quý giá nhất**: purchase events, subscription lifecycle, engagement signals, LTV trajectories. Đây là tài sản mà clean rooms và identity resolution platforms khao khát.

**Implications**:
- SDK có thể trở thành **data source cho clean rooms** — cung cấp first-party monetization events mà không vi phạm privacy (vì aggregated/anonymized).
- **Competitive moat**: SDK sở hữu first-party events có leverage trong identity resolution ecosystem mà third-party attribution tools không có.
- **Revenue opportunity**: anonymized monetization benchmarks, cohort analytics, cross-app insights.

---

## G. Monetization-specific measurement

### G.1. Revenue attribution across channels

**Thách thức 2026**: revenue đến từ nhiều kênh — app store IAP, subscription, web shop, CTV-driven installs — và attribution phải nối tất cả lại [Secondary — GameAnalytics, Tenjin, Adapty]:

| Revenue source | Attribution method | Challenges |
|---------------|-------------------|-----------|
| **App Store IAP** | SKAN CV + deterministic (ATT users) + MMP modeled | 24–48h delay, coarse-only ở PB2/PB3 |
| **Subscription** | Server-side receipt validation + AAK re-engagement windows | Re-engagement attribution cần AAK (không có ở SKAN thuần) |
| **Ad revenue (IAA)** | Ad revenue attribution qua MMP (Tenjin, AppsFlyer) | eCPM varies, waterfall complexity |
| **Web shop** | First-party server events + UTM/session tracking | Safari 26 strips tracking params, cross-device gap |
| **CTV-driven** | CTV attribution (Singular, Kochava) + lift study | Impression-to-install gap lớn |

### G.2. Web shops — cơ hội post-Epic

**Epic Games Store** tạo tiền lệ mới cho web shop monetization [Secondary — Epic, Reddit, Tech-Insider]:

- **0% revenue share** cho $1M đầu tiên/sản phẩm/năm (từ 2025) [Primary docs — Epic].
- **88/12 split** sau $1M (so với 70/30 truyền thống).
- **Epic First Run Program**: 100% revenue share trong 6 tháng exclusivity.
- **78M MAU** tính đến 12/2025 [Secondary — Tech-Insider].
- **Web shop integration**: AppsFlyer hỗ trợ Epic integration qua S2S và client-side API [Primary docs — AppsFlyer support].

**Cơ hội cho SDK**: web shop bypass app store commission (15–30%), nhưng attribution gap rất lớn — user mua trên web, consumption trên app. SDK có thể bridge gap này bằng first-party event reconciliation.

### G.3. Subscription event pipelines

Subscription monetization cần event pipeline riêng [Secondary — RevenueCat, Adapty, Dataseat]:

```
Events cần track:
  trial_start → trial_convert → subscription_start → renewal → upgrade/downgrade → churn → win_back

Attribution cần trả lời:
  - User đến từ campaign nào?
  - Re-engagement campaign có tạo incremental subscription start?
  - Win-back campaign có giảm churn thực sự?
```

**AdAttributionKit** là framework duy nhất hỗ trợ re-engagement attribution natively — SKAN thuần không có [Secondary — Adjust, RevenueCat].

### G.4. LTV cohort tooling

[Secondary — Tenjin, GameAnalytics, Bruin, Campaignswell, devtodev]:

| Tool | Mô hình | Điểm mạnh cho monetization |
|------|---------|---------------------------|
| **Tenjin** | Attribution + ad revenue benchmarks | Ad monetization benchmark report 2026, IAA vs IAP analysis, eCPM by country/format |
| **GameAnalytics** | Game analytics + monetization | Unified monetization dashboards (IAP + IAA + webshop), retention/LTV cohorts |
| **devtodev** | Product analytics | In-app goods optimization, revenue funnel analysis |
| **Campaignswell** | UA intelligence | Predicted LTV, cohort quality scoring, ROAS benchmarks |
| **Amplitude / Firebase / GA4** | General analytics | Custom event pipelines, cần tự build monetization layer |

**Tenjin Ad Monetization Benchmark 2026** [Primary research — Tenjin]:
- Android chiếm 55% ad revenue, iOS 45% (Q2/2026).
- eCPM varies significantly by country và ad format.

**GameAnalytics** [Primary research — GameAnalytics 2026 benchmarks]:
- 2026 Mobile & PC Gaming Benchmarks report.
- Unified monetization tracking across IAP, rewarded ads, và webshop.

**Lưu ý**: Playtomic trong câu hỏi nghiên cứu là công ty padel technology (thể thao), không phải game analytics tool [Secondary — Playtomic Global Padel Report 2026].

---

## H. Implications: measurement feature set cho SDK

### H.1. Decision log — ghi nhận attribution decision

SDK nên export **structured decision log** cho mỗi monetization decision:

```json
{
  "event_id": "uuid",
  "timestamp": "ISO8601",
  "decision_type": "offer_presented | paywall_shown | ad_served",
  "context": {
    "user_segment": "high_engager_no_spend",
    "offer_type": "introductory_subscription",
    "sku": "sub_monthly_499",
    "variant": "A",
    "source": "ml_model_v3 | rule_based | a_b_test"
  },
  "outcome": {
    "converted": true,
    "revenue_usd": 4.99,
    "attribution_window_days": 1
  },
  "attribution": {
    "install_source": "meta_ads | organic | tiktok",
    "campaign_id": "camp_123",
    "skan_cv_bucket": 52,
    "deterministic_available": false
  }
}
```

**Tại sao**: đây là first-party data mà MMP không có — MMP chỉ thấy install + SKAN postback, không thấy decision context. SDK sở hữu cả decision lẫn outcome = unique data advantage.

### H.2. First-party event ownership

**Nguyên tắc**: SDK phải là nguồn truth cho mọi monetization events, trước khi MMP/ad network claim attribution.

```
Priority chain:
  SDK event (first-party, real-time, full context)
    → server-side event API (aggregated, enriched)
      → MMP postback (delayed, modeled)
        → ad network claim (most delayed, most noisy)
```

**Events SDK phải capture natively**:
- Purchase/subscription lifecycle (start, renew, churn, win-back)
- Ad impression/revenue (với eCPM metadata)
- Offer presentation + conversion (variant, timing, price)
- Engagement signals (session depth, feature usage, retention milestones)

### H.3. SKAN conversion-value strategy

SDK nên ship **configurable CV schema** với opinionated defaults cho gaming monetization:

| Bucket range | Signal | Rationale |
|-------------|--------|-----------|
| 0–7 | No meaningful event | Noise floor — installs that never engaged |
| 8–15 | Ad-view only (IAA user) | Monetization via ads, no IAP signal |
| 16–31 | First IAP < $5 | Entry-level monetization |
| 32–47 | First IAP $5–20 | Mid-tier |
| 48–55 | First IAP > $20 hoặc subscription start | High-value signal |
| 56–62 | Whale (>$50 first 48h) | Lock window immediately |
| 63 | Reserved — multi-event combo | E.g., IAP + subscription + high engagement |

**`lockWindow` defaults**:
- Lock ở PB1 nếu fine value ≥ 48 (whale/subscription signal).
- Không lock ở PB1 cho values < 48 (cho phép PB2 cập nhật nếu user convert sau).

**Coarse value mapping** (PB2/PB3):
- Low: no revenue event in window.
- Medium: any non-whale revenue event.
- High: whale signal hoặc active subscription.

### H.4. Incrementality hooks

SDK nên expose **APIs cho incrementality testing**:

1. **Holdout group assignment**: SDK nhận flag từ backend — user thuộc holdout group → không thấy specific offer/campaign. SDK log both treatment and holdout exposure.

2. **Ghost event logging**: SDK log khi user *would have* seen an offer nhưng không được serve (do holdout/cap) → tạo ghost ads equivalent cho owned media.

3. **Experiment metadata**: mỗi monetization event tagged với experiment_id, variant, và treatment_timestamp → cho phép downstream analysis bằng difference-in-differences hoặc Bayesian lift.

4. **Geo-level assignment**: SDK support geo-based treatment assignment cho geo lift tests (DMA-level hoặc country-level).

### H.5. Clean room data export

SDK nên cung cấp **anonymized data export** cho clean room integration:

- Aggregated monetization events (không PII).
- Cohort-level LTV curves by install source.
- Conversion rate benchmarks by segment.
- Hashed user IDs (opt-in, consent-gated) cho identity resolution.

**Revenue model**: anonymized benchmark data có thể monetize như industry intelligence (tương tự Tenjin ad monetization benchmarks, GameAnalytics gaming benchmarks).

---

## I. Tổng kết: bức tranh measurement 2026 cho monetization SDK

| Dimension | 2024 | 2026 |
|-----------|------|------|
| **iOS attribution** | SKAN 4.0 dominant, AAK mới launch | AAK recommended, SKAN 4 vẫn chạy, Safari 26 siết web-to-app |
| **Android attribution** | Privacy Sandbox đang rollout | Privacy Sandbox dead, GAID declining, Install Referrer + first-party events |
| **MMP role** | Attribution engine | Modeled intelligence + fraud + CTV |
| **Incrementality** | UA-focused | Monetization-focused (offer testing, subscription lift) |
| **MMM** | Expensive consulting | Open-source (Meridian, Robyn) accessible |
| **Identity** | IDFA-dependent | Multi-signal, hashed email, clean rooms |
| **Web shops** | Niche | Growing (Epic precedent), attribution gap lớn |
| **First-party data** | Nice-to-have | Strategic necessity + competitive moat |

**SDK implication**: measurement không còn là "gọi MMP SDK rồi chờ attribution." Measurement 2026 là **first-party event ownership + configurable SKAN/AAK strategy + incrementality hooks + clean room data export** — tất cả built-in, không bolt-on.

---

## Không xác minh được (ghi rõ)

1. **SKAN 5 có 8-bit conversion values (256 values) hay không**: một số nguồn secondary đề cập nhưng không tìm thấy xác nhận từ Apple developer documentation chính thức. AdAttributionKit WWDC 2025 update không đề cập thay đổi bit depth. Cần verify khi Xcode beta mới available.

2. **SKAN modeled data "recover 30–60% non-organic traffic"**: con số do AppsFlyer self-report, không có independent verification. Actual recovery rate phụ thuộc vào campaign volume, vertical, và geography.

3. **GAID opt-in rate "30–38% ở EU"**: số liệu từ Appvertiser (secondary), không tìm thấy nguồn primary từ Google. Google không công bố GAID opt-in rates.

4. **Ad fraud "$100 tỷ" projection 2026**: số từ Improvado (secondary), dựa trên industry estimates. Con số chính xác biến thiên tùy methodology.

5. **Google Meridian "GeoX, Studio, Data Manager" tại Google Marketing Live 2026**: source secondary (Elevarus), chưa verify trực tiếp từ Google blog post.

6. **ATT opt-in "25–35%" range 2026**: tổng hợp từ nhiều secondary sources (Adjust, AppsFlyer) — số liệu gốc từ Q1/2025, chưa có cập nhật Q2/2026 chính thức.

---

## Nguồn chính

### iOS / SKAdNetwork / AdAttributionKit
- Apple SKAdNetwork Documentation — https://developer.apple.com/documentation/storekit/skadnetwork
- Apple Ad Attribution — https://developer.apple.com/app-store/ad-attribution/
- What's new in AdAttributionKit — WWDC25 — https://developer.apple.com/videos/play/wwdc2025/221/
- AdAttributionKit Updates — https://developer.apple.com/documentation/updates/adattributionkit
- Adjust: AdAttributionKit, SKAN, iOS 26, and more — WWDC25 — https://www.adjust.com/blog/wwdc-adattributionkit-2025/
- Singular: WWDC 2025 — major changes to AdAttributionKit — https://www.singular.net/blog/wwdc-2025-aak/
- Segwise: WWDC 2026 AdAttributionKit Update — https://segwise.ai/blog/wwdc-2025-adattributionkit-update-6-improvements-catch
- Linkrunner: AdAttributionKit vs SKAN — https://linkrunner.io/blog/adattributionkit-vs-skan
- AppsFlyer: AdAttributionKit WWDC25 update — https://www.appsflyer.com/blog/mobile-marketing/adattributionkit-wwdc25/
- YouAppi: Everything about SKAN 5 — https://youappi.com/blog/everything-mobile-advertisers-need-to-know-about-skan-5/
- Kochava: SKAN versions 2–5 differences — https://www.kochava.com/blog/the-differences-between-skan-versions-2-5/
- Kochava: iOS Attribution Strategy 2026 — https://www.kochava.com/blog/your-ios-attribution-strategy-2026-reality-check/
- AuditSocials: Apple ATT & SKAdNetwork 5 May 2026 — https://www.auditsocials.com/blog/apple-att-skadnetwork-5-may-2026-privacy-manifests-postback-schema-mobile-advertiser-workflow
- AppsFlyer: Safari iOS 26 user-agent freeze — https://www.appsflyer.com/blog/measurement-analytics/safari-ios26-user-agent/

### Android / Privacy Sandbox
- Google Privacy Sandbox — update on plans — https://privacysandbox.google.com/blog/update-on-plans-for-privacy-sandbox-technologies
- Privacy Sandbox feature status — https://privacysandbox.google.com/overview/status
- Segwise: Google Privacy Sandbox shutdown reason — https://segwise.ai/blog/google-privacy-sandbox-shutdown-reason
- SearchEngineLand: Google officially shuts down Privacy Sandbox — https://searchengineland.com/google-officially-shuts-down-privacy-sandbox-463561
- Appvertiser: Android Privacy Sandbox Mobile Attribution 2026 — https://www.appvertiser.ai/blog/android-privacy-sandbox-mobile-attribution-2026
- arXiv: The Rise and Fall of Google's Privacy Sandbox — https://arxiv.org/html/2607.00693v1

### MMP
- AppsFlyer SKAN Conversion Studio — https://support.appsflyer.com/hc/en-us/articles/4403727223185-SKAN-Conversion-Studio
- AppsFlyer SKAN modeled data — https://support.appsflyer.com/hc/en-us/articles/7086372479505-SKAN-modeled-data
- AppsFlyer pLTV glossary — https://www.appsflyer.com/glossary/pltv/
- SolarEngine: MMP pricing comparison — https://blog.solarengine.com/en-blog/docs/mmp-pricing-comparison
- Linkrunner: Mobile Attribution Pricing 2026 — https://linkrunner.io/blog/the-true-cost-of-mobile-attribution-why-affordable-solutions-like-linkrunner-io-are-changing-the-game
- Vmobify: AppsFlyer vs Adjust vs Singular 2026 — https://vmobify.com/blog/appsflyer-vs-adjust-vs-singular
- SplitMetrics: 8 Best MMPs in 2026 — https://splitmetrics.com/blog/best-mobile-measurement-partners-mmps/
- Tenjin: Best AppsFlyer Alternatives 2026 — https://tenjin.com/blog/best-appsflyer-alternatives-in-2026-adjust-vs-singular-vs-tenjin/

### Incrementality
- Remerge: Ghost Ads vs Ghost Bids vs PSA — https://www.remerge.io/blog-post/incrementality-tests-101-intent-to-treat-psa-ghost-ads-and-ghost-bids
- Singular: Incrementality Testing — https://www.singular.net/blog/incrementality-testing/
- RTB House: Ghost Ads next-gen incrementality — https://www.rtbhouse.com/blog/ghost-ads-next-gen-incrementality-testing-for-performance-marketing
- AdLibrary: Incrementality Testing 2026 — https://adlibrary.com/posts/incrementality
- SegmentStream: Incrementality Measurement Guide 2026 — https://segmentstream.com/blog/articles/incrementality-measurement-guide
- Alex Neiman: Measure Incremental Conversions Meta Ads 2026 — https://alexneiman.com/measure-incremental-conversions-meta-ads-2026/

### MMM
- Google Meridian — https://developers.google.com/meridian
- Google Meridian GitHub — https://github.com/google/meridian
- Google blog: Meridian now available to everyone — https://blog.google/products/ads-commerce/meridian-marketing-mix-model-open-to-everyone/
- Meta Robyn — https://facebookexperimental.github.io/Robyn/
- SEIO2026: MMM open-source comparative analysis — https://seio2026.com/en/papers/3668/marketing-mix-modeling-mmm-open-source-solutions-a-comparative-analysis-of-robyn-meridian-and-pymc-marketing-libraries
- Funnel.io: Open-source MMM — https://funnel.io/blog/open-source-marketing-mix-modeling
- Elevarus: MMM Google Marketing Live 2026 — https://elevarus.com/marketing-mix-modeling-meridian-google-marketing-live-2026/

### First-party data & Clean Rooms
- MarTech: 5 trends reshaping identity resolution 2026 — https://martech.org/the-5-trends-reshaping-identity-resolution-in-2026/
- Decentriq: Best data clean room companies 2026 — https://www.decentriq.com/article/data-clean-rooms-compared
- Snowflake Data Clean Rooms — https://www.snowflake.com/en/product/features/data-clean-rooms/
- AWS Clean Rooms partners — https://aws.amazon.com/clean-rooms/partners/
- Adtelligent: Top 10 ID Solutions 2026 — https://adtelligent.com/blog/top-10-id-solutions/
- Hellyeah AI: Server Side Analytics 2026 — https://www.hellyeahai.com/blog/server-side-analytics

### Monetization-specific
- Tenjin: Ad Monetization Benchmark 2026 — https://tenjin.com/blog/ad-mon-gaming-2026/
- GameAnalytics: 2026 Mobile & PC Gaming Benchmarks — https://www.gameanalytics.com/reports/2026-mobile-pc-gaming-benchmarks
- GameAnalytics: Unified monetization — https://www.gameanalytics.com/analyticsiq/monetize
- Bruin: Best Mobile Game Analytics Tools 2026 — https://getbruin.com/blog/best-analytics-tools-mobile-gaming-2026/
- Epic Games Store webshops — https://store.epicgames.com/news/new-epic-games-store-webshops-and-revenue-share-update
- AppsFlyer Epic integration — https://support.appsflyer.com/hc/en-us/articles/14241298075921-Epic-integration-with-AppsFlyer

### EU / Consent / GDPR
- IAB TCF v2.3 guide — https://usercentrics.com/knowledge-hub/iab-tcf-2-3-transparency-consent-framework-quick-guide/
- Google EU consent policy support — https://support.appsflyer.com/hc/en-us/articles/22310731147153-Support-Google-EU-consent-policy
- GDPR Compliance for Mobile Apps 2026 — https://secureprivacy.ai/blog/gdpr-compliance-mobile-apps
- Mobile Attribution After ATT and GDPR 2026 — https://linkrunner.io/blog/understanding-mobile-attribution-in-today-s-privacy-landscape

### CTV & Fraud
- Kochava CTV & OTT Analytics — https://www.kochava.com/product/measurement/connected-tv/
- Kochava AI-Powered Ad Fraud Prevention — https://www.kochava.com/product/ad-fraud-prevention/
- Singular CTV Attribution — https://www.singular.net/ctv/
- Starti.ai: Best CTV Analytics Platforms 2026 — https://starti.ai/blog/best-ctv-analytics-platforms-in-2026-top-7-for-attribution-measurement/
- Improvado: Ad Fraud 2026 — https://improvado.io/blog/ad-fraud
