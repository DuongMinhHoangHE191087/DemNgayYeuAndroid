# 02 — Turkey Ecosystem: Monetization, Personalization và User Intelligence

## Vì sao Turkey đáng nghiên cứu?

Turkey có hệ sinh thái mobile game/app rất mạnh và nhiều công ty công khai từng mảnh của “Monetization Brain”:

```text
FOMO          → IAA vs IAP policy + Remote Config
Peak Games    → real-time segmentation
Good Job      → player segmentation + personalized offers
Dream Games   → inferred interests + customized events/offers/promotions
ADMOST        → per-user ad waterfall + IAP/IAA LTV
Insider       → predictive user segments / affinity
Visilabs      → behavior + targeting + recommendation
Segmentify    → micro-segmentation + real-time recommendation
Storyly       → personalized presentation / placement
Countly       → event/user/control-plane, partly open-source
```

## 1. FOMO Games — personalized IAA vs IAP policy

Firebase's FOMO case study mô tả studio Turkey có hơn 10M MAU và nhận ra cohort acquisition khác nhau có behavior khác nhau: có nhóm thiên IAP, có nhóm responsive với ads.

Họ segment theo acquisition channel, platform, geography, rồi dùng Remote Config + targeted A/B testing để thay đổi gameplay/monetization.

Case nổi bật:

```text
Default Out-of-Moves:
Rewarded Ad OR Standard IAP

Variant:
Rewarded Ad removed
Higher-value IAP shown
```

Firebase báo cáo **20% increase in overall LTV** sau khi triển khai framework này. Đây là self-reported case result nhưng nó là bằng chứng rất sát về việc studio thực tế đang chọn `IAA vs IAP` theo group. [FOMO_FIREBASE]

### Copyable insight

```text
Action space should contain:
NO_ACTION
IAA
IAP
BOTH
```

Không giả định IAA và IAP luôn song song.

## 2. Peak Games — real-time segmentation từ 2016

Peak Games đã dùng VoltDB cho real-time user segmentation/personalization. Announcement mô tả ingest → analyze → act on incoming streams in milliseconds, với mục tiêu đưa personalized content đúng thời điểm. [PEAK_VOLTD]

Điểm đáng học:

```text
Event stream
   ↓
Real-time segmentation
   ↓
Immediate decision
   ↓
Offer / gift / content
```

## 3. Good Job Games — segmentation là capability nội bộ

Job description chính thức của Good Job Games cho `Data & Monetization Analyst` liệt kê:

- thiết kế/chạy/phân tích A/B tests cho gameplay, UX, monetization;
- develop và maintain **player segmentation tools**;
- strategies cho **personalized offers and engagement**;
- theo dõi DAU, retention, ARPU, LTV;
- predictive analytics/modeling;
- ability to distinguish correlation from causation. [GOODJOB_ROLE]

## 4. Dream Games — inference về sở thích và customized offers

Dream Games privacy disclosures nói họ có thể xử lý analytics data, commercial information, ad interaction data và inferred interests/preferences; họ dùng dữ liệu để optimize experience và deliver **customized in-game events, offers and promotions**. [DREAM_PRIVACY] [DREAM_KR]

Đây không cho biết algorithm cụ thể, nhưng xác nhận production practice: profile/inference → customized monetization/content.

## 5. ADMOST — per-user ad waterfall

ADMOST tự mô tả là mobile monetization platform với mediation, revenue analytics và cross promotion. [ADMOST_OVERVIEW]

Điểm đặc biệt nhất: Personalized Waterfall.

Thay vì GEO-level waterfall, ADMOST mô tả unique waterfall cho từng user dựa trên behavior, dynamic regeneration per request và previous request conversion/analytics variables. [ADMOST_WATERFALL]

Analytics kết hợp **ad revenue + IAP revenue theo từng user** để tính LTV/ARPU. [ADMOST_ANALYTICS]

### Ý nghĩa với Monetization Brain

ADMOST giải downstream problem:

> Nếu đã quyết định show ad, route demand thế nào?

Brain của mình giải upstream problem:

> Có nên show ad không, loại nào, hay protect user cho IAP?

## 6. Insider — predictive segments và affinity

Insider public các predictive segments:

- Likelihood to Purchase,
- User Engagement,
- Customer Lifecycle Status,
- Discount Affinity,
- Attribute Affinity. [INSIDER_PRED]

`Likelihood to Purchase` nhắm khả năng mua trong 7 ngày; `Discount Affinity` phân biệt discount seekers/premium shoppers; `Attribute Affinity` học category/product/custom-attribute preference.

FAQ public data requirements/time windows cho các algorithms. [INSIDER_FAQ]

### Mapping sang game

```text
Likelihood to Purchase → P(IAP)
Discount Affinity      → price/discount sensitivity
Attribute Affinity     → power/cosmetic/time-saving affinity
Engagement             → activity propensity
Lifecycle              → new/engaged/slipping/churning
```

## 7. Related Digital / Visilabs — behavior → target/recommend

Visilabs mô tả personalization bằng cách kết hợp customer behavior, customer data và insights để cá nhân hóa real time. Data có thể gửi qua SDK/REST rồi dùng `Target` và `Recommend`. [VISILABS_PERSONAL]

Mobile SDK event collection bao gồm signup/login, page/product view, add to cart, purchase, search, banner click, favorite. [VISILABS_EVENTS]

SDK expose recommendation call theo `zoneId`, `productCode`, filters và có feedback click tracking. [VISILABS_IOS]

### Mapping sang game

```text
zoneId      → monetization placement / decision point
productCode → current item/context
recommend() → next best offer candidate
click track → feedback
```

## 8. Segmentify — real-time micro-segmentation

Segmentify mô tả ML micro-segmentation dựa trên browse/click/view/add-to-basket/purchase history để tạo recommendation real time cho từng visitor. [SEGMENTIFY_FAQ]

## 9. Storyly — presentation personalization

Storyly định vị như AI-powered content experience platform, với Placements, Widgets, Flows và optional AI personalization. [STORYLY_DOCS]

Đây phù hợp cho **Presentation Engine**:

```text
same offer
→ popup / story / banner / card / feed / full-screen
```

Action hoàn chỉnh nên là:

\[
Action=(Product,Price,Value,Placement,Creative,Timing,Presentation)
\]

## 10. Countly — open-source control-plane candidate

`Countly/countly-server` public source và có session/view/event collection, remote configuration, push, APIs, plugin architecture, dashboards và event segmentation. [COUNTLY_REPO]

Các release hiện tại còn cho thấy journey-engine functionality ở enterprise line. [COUNTLY_RELEASES]

### Tại sao đáng reverse-engineer?

Khác nhiều closed SaaS, Countly cho phép đọc architecture event ingestion / plugins / APIs và có thể dùng như nền:

```text
events
↓
user profile / cohort
↓
remote config / journey
↓
external decision service
```

## 11. Turkey stack ghép lại

```text
COUNTLY / EVENT PIPELINE
↓
Player memory + behavior

INSIDER
↓
Predictive profile + affinity

VISILABS / SEGMENTIFY
↓
Recommendation

FOMO
↓
IAA vs IAP policies

ADMOST
↓
User-level ad monetization

STORYLY
↓
Presentation

PEAK / GOOD JOB / DREAM
↓
Evidence that studios operationalize segmentation,
real-time personalization and personalized offers
```

## Nguồn

- **[FOMO_FIREBASE] Firebase — FOMO Games case study** — Primary platform case study.  
  https://firebase.google.com/case-studies/fomo-games
- **[PEAK_VOLTD] Peak Games / VoltDB — Real-time segmentation and personalization** — Primary/partner announcement.  
  https://www.prnewswire.com/news-releases/peak-games-selects-voltdb-for-real-time-segmentation-and-personalization-300306017.html
- **[GOODJOB_ROLE] Good Job Games — Data & Monetization Analyst** — Primary company job description.  
  https://job-boards.greenhouse.io/goodjobgames/jobs/7661366003
- **[DREAM_PRIVACY] Dream Games — Privacy Policy** — Primary company disclosure.  
  https://www.dreamgames.com/en/privacy
- **[DREAM_KR] Dream Games — Supplemental Korean Privacy Statement** — Primary company disclosure.  
  https://www.dreamgames.com/en/ko-disclosure
- **[ADMOST_OVERVIEW] Admost — Overview** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/overview/
- **[ADMOST_WATERFALL] Admost — How does Waterfall work?** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/how-does-waterfall-work-2/
- **[ADMOST_ANALYTICS] Admost — Analytics Tool & Utilization** — Primary vendor docs.  
  https://resources.admost.com/knowledge-base/analytics-tool/
- **[INSIDER_PRED] Insider — Predictive Segments** — Primary vendor docs.  
  https://academy.useinsider.com/docs/audience-predictive-segments
- **[INSIDER_FAQ] Insider — FAQ about Predictive Segments** — Primary vendor docs.  
  https://academy.useinsider.com/docs/faq-about-predictive-segments-1
- **[VISILABS_PERSONAL] Related Digital / Visilabs — Personalisation overview** — Primary vendor docs.  
  https://relateddigital.atlassian.net/wiki/spaces/RMCKBT/pages/428802372/RMC%2BPersonalisation%2Bvisilabs
- **[VISILABS_EVENTS] Related Digital / Visilabs — iOS SDK Data Collection** — Primary vendor docs.  
  https://relateddigital.atlassian.net/wiki/spaces/KB/pages/1029013526/iOS%2BSDK%2BData%2BCollection
- **[VISILABS_IOS] Related Digital / Visilabs — iOS Targeting Actions** — Primary vendor docs.  
  https://relateddigital.atlassian.net/wiki/spaces/KB/pages/1029275656/iOS%2BTargeting%2BActions
- **[SEGMENTIFY_FAQ] Segmentify — FAQ / real-time personalized recommendations** — Primary vendor docs.  
  https://segmentify.com/faqs/
- **[STORYLY_DOCS] Storyly — Introduction / Placements, Widgets, Flows** — Primary vendor docs.  
  https://docs.storyly.io/docs/introduction
- **[COUNTLY_REPO] Countly — countly-server** — Primary source repository.  
  https://github.com/Countly/countly-server
- **[COUNTLY_RELEASES] Countly — Releases** — Primary source repository.  
  https://github.com/Countly/countly-server/releases
