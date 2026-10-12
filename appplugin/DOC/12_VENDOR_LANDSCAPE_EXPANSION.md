# 12 — Vendor Landscape Mở rộng: Ai đang bán "brain", và khoảng trống thật

**Ngày nghiên cứu:** 2026-08-21
**Câu hỏi:** Trên thị trường 2026, vendor nào đang cung cấp năng lực gần với Player Decision Platform (doc 01)? Không ai làm gì? Đâu là white space thật để build?

## Evidence labels

- **[Primary docs]**: docs/website chính thức của vendor.
- **[Primary filing]**: báo cáo công ty niêm yết (SEC/annual report).
- **[Case study — self-reported]**: số uplift do vendor/studio tự công bố.
- **[Conference]**: talk/deck hội nghị (GDC, Ray Summit…).
- **[Secondary]**: báo chí/analyst.

---

## A. Bản đồ vendor theo lớp

### A.1 Lớp "IAP/offer brain" — gần target nhất

| Vendor | Trụ sở | Năng lực công bố | Evidence |
|---|---|---|---|
| **Metica** | Helsinki | Offers, eligibility, contextual bandits, Smart Config, dynamic bundles (đã có trong doc 02/03) | [Primary docs] docs.metica.com |
| **Elevatix** | Dubai | AI engine cá nhân hóa IAP offer theo player behavior; là startup gần nhất với "IAP brain" thuần | [Vendor — self-reported] https://elevatix.com — claim +40% IAP revenue |
| **Balancy** | — | Economy balancing + monetization analytics, mạnh về economy design hơn là real-time decisioning | [Vendor docs] https://balancy.io |
| **Game State Labs** | — | Game analytics + predictive segmentation | [Vendor docs] |
| **Sett AI** | — | AI monetization advisor cho game studio (phân tích + khuyến nghị, không serving real-time) | [Vendor docs] |
| **Xsolla LiveOps Suite** (8/2025) | LA | Tailored offers, live events, CRM; claim +30% revenue từ tailored offers | [Vendor blog — self-reported] https://xsolla.com |

**Nhận xét:** không vendor nào trong nhóm này công bố objective **causal/incremental LTV** — tất cả nói về "personalization" và "conversion/revenue lift". Không ai công bố off-policy evaluation hay guardrail layer.

### A.2 Lớp IAA/mediation với "AI tối ưu"

| Vendor | Năng lực | Evidence |
|---|---|---|
| **AppLovin AXON** | Model học máy trong ad stack MAX; chiếm phần lớn mediation revenue của AppLovin (~73% theo phân tích 2024–2025); Axon self-serve mở 6/2026; là closed-loop pLTV (bidding ↔ monetization ↔ feedback) gần nhất với brain | [Primary filing 10-K] + [Secondary] https://www.klover.ai/applovin-ai-strategy-analysis-of-dominance-in-advertising/ |
| **Unity LevelPlay** | Mediation với "AI ad optimization" (Ad Quality, auto-CPM floor) | [Primary docs] https://docs.unity.com/levelplay/ |
| **Airflux / Airbridge** | IAA-side AI ad optimizer: claim LTV lift 13–55% / 40%; tối ưu frequency + placement | [Vendor — self-reported] |
| **TradPlus VisiM** | Mediation + visibility/measurement layer cho SEA | [Vendor docs] https://tradplus.com |
| **TopOn Smart Manager** | Mediation với auto-optimization rules (không phải per-user ML) | [Vendor docs] https://toponad.com |
| **Admost** | Mediation + waterfall optimization (đã có trong doc 02) | [Primary docs] |

**Nhận xét:** toàn bộ lớp này tối ưu **giá trị của impression cho ad platform/mediator**, không phải LTV của player. Frequency capping ở đây là rule theo segment, không phải decision policy per-user theo state. Đây chính là ranh giới phân biệt "ad optimizer" và "monetization brain".

### A.3 Lớp platform nội bộ của studio lớn (không bán ra ngoài)

- **Playtika Boost Platform** — documented nhất trong nhóm này: data platform 9TB/ngày, tuyển RL scientists công khai, "AB Boost" framework experimentation nội bộ. [Primary filing 10-K/annual report] https://investors.playtika.com — [Secondary: job postings].
- **King** — end-to-end ML cho IAP (paper đã có trong doc 03, KING_PAPER).
- **Wildlife Studios** — RL cho offer recommendation (deck Ray Summit, doc 03).
- **Tencent** — GDC 2025 talk "Innovation of pLTV in GaaS" (GDC Vault session 1035412): pLTV dùng cho live-ops GaaS, không chỉ UA. [Conference]
- **Fuse Powered** — GDC talk sớm nhất mô tả vision "brain" (session 1022202): dynamic offers theo player value. [Conference]

**Nhận xét:** các studio lớn đã có brain nội bộ từ lâu nhưng **không thương mại hóa** — vì brain gắn với data của họ và là lợi thế cạnh tranh. Điều này xác nhận: thị trường SDK/vendor cho năng lực này vẫn mở.

### A.4 Lớp hạ tầng phụ trợ

- **Tenjin** (pLTV, doc 11), **ThinkingData** (analytics mạnh ở CN/SEA, [Vendor docs] https://thinkingdata.io), **CleverTap/Insider** (predictive segments, đã có trong doc 09), **Countly** (open-source self-hosted, doc 08).

---

## B. Khoảng trống đã xác nhận (white space)

Sau khi quét landscape, 4 khoảng trống sau **không có vendor nào công khai giải quyết**:

1. **Cross-modal decisioning per-user.** Mọi vendor chọn một lane: IAP offer (Elevatix/Metica) HOẶC ad optimization (AXON/Airflux). Không ai chọn hành động **IAA vs IAP vs free vs no-action** cho cùng một player tại cùng một decision point. Đây là action space lõi của doc 01 và chưa có sản phẩm tương đương.

2. **Objective incremental LTV (causal).** Không vendor nào công bố dùng uplift/causal objective hay off-policy evaluation. Tất cả tối ưu conversion/revenue quan sát được — cái mà doc 05 gọi là propensity trap.

3. **Guardrail layer như sản phẩm.** Không vendor nào bán "retention/fairness/eligibility guardrail" như một tầng kiến trúc riêng có thể audit. Guardrail hiện chỉ là frequency cap rule trong mediation. Với EU Digital Fairness Act và luật VN/SEA (doc 13), tầng này sẽ thành yêu cầu bắt buộc — ai có trước sẽ có lợi thế compliance.

4. **SDK trung lập ngồi giữa game và mediation.** AXON chỉ phục vụ hệ sinh thái AppLovin; LevelPlay chỉ phục vụ Unity. Không có decision layer trung lập hoạt động trên nhiều mediation stack (AdMob + MAX + TopOn + TradPlus) — chính xác là vị trí kiến trúc mà repo hiện tại (Android ads plugin) đang đứng.

## C. Hàm ý chiến lược

```text
Định vị khả thi:
  "Cross-modal monetization decision layer, trung lập với mediation stack,
   objective incremental LTV, guardrail-first."

Không nên cạnh tranh:
  - Với AXON về scale data ad.
  - Với Metica/Elevatix về IAP-only SaaS cho studio phương Tây.

Nên cạnh tranh:
  - Ở chỗ chưa ai đứng: decision layer per-user chọn giữa ad/IAP/free,
    cho thị trường hybrid-casual Android-first (doc 15: VN/SEA).
```

## Không xác minh được (ghi rõ)

1. Uplift number của Elevatix, Airflux, Xsolla đều là self-reported, không có replication.
2. Chi tiết thuật toán AXON (proprietary).
3. Kiến trúc Boost Platform của Playtika ngoài những gì filing và job post tiết lộ.
4. Tencent GDC talk: xác nhận tồn tại session, không trích xuất được nội dung chi tiết.

## Nguồn chính

- Elevatix — https://elevatix.com
- Xsolla LiveOps — https://xsolla.com
- Playtika investor — https://investors.playtika.com
- AppLovin 10-K / AXON analysis — https://www.klover.ai/applovin-ai-strategy-analysis-of-dominance-in-advertising/
- Unity LevelPlay — https://docs.unity.com/levelplay/
- TradPlus — https://tradplus.com
- TopOn — https://toponad.com
- ThinkingData — https://thinkingdata.io
- Balancy — https://balancy.io
- GDC Vault pLTV GaaS (Tencent) — session 1035412
- GDC Vault Fuse Powered — session 1022202
- Các nguồn Metica/King/Wildlife: xem doc 03 và doc 09.
