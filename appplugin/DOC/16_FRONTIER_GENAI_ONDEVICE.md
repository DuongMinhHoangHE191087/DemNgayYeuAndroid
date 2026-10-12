# 16 — Frontier: GenAI, on-device ML và kiến trúc hot-path/cold-path

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** LLM/GenAI và on-device ML áp dụng được gì cho monetization brain — và quan trọng hơn, **không** áp dụng vào đâu. Nguyên tắc xuyên suốt: *hot path thuộc về bandit/GBDT nhỏ (<10ms), LLM và agent chỉ sống ở cold path.*

## Evidence labels

- **[Primary research]**: paper peer-reviewed/arXiv.
- **[Primary docs]**: docs chính thức của Google/Apple/vendor.
- **[Case study — self-reported]**: số do vendor tự công bố.
- **[Secondary]**: tổng hợp.

---

## A. GenAI cho brain — 4 slot dùng được, 1 slot cấm

### A.1 Sinh creative/offer copy (cold path) — slot chín nhất

- **HLLM-Creator (ByteDance, arXiv:2508.18118):** LLM được tinh chỉnh để sinh nội dung quảng cáo/campaign cho hệ thống marketing nội bộ; pipeline sinh → đánh giá → lọc. [Primary research]
- **Layer.ai × Gamegos:** vendor GenAI chuyên creative game (sinh artwork/copy quảng cáo), công bố case studio casual. [Vendor — self-reported]
- **Hệ quả:** brain không sinh creative tại request time; thay vào đó, **thư viện creative được sinh offline theo segment**, brain chỉ chọn (arm của bandit bao gồm cả creative variant). Điều này biến "creative" thành dimension mới của action space mà không cần LLM ở hot path.

### A.2 Synthetic players — dùng làm red-team/tester, KHÔNG làm predictor

- **Orak benchmark (arXiv:2506.03610v3):** đánh giá khả năng LLM đóng vai agent trong môi trường game; kết quả thận trọng. [Primary research]
- **MDPI (10.3390/info16040329):** nghiên cứu LLM làm player mô phỏng; phát hiện tiêu cực đáng chú ý — **LLM không hành xử như expert player thật**, thiên kiến hệ thống rõ rệt. [Primary research]
- **Cách dùng đúng:** synthetic players để stress-test guardrail, sinh edge case cho decision policy, fuzz-test economy — không bao giờ dùng số của chúng thay cho A/B thật hoặc làm reward signal.

### A.3 Mô phỏng kinh tế game (cold path)

- **RuleSmith** và các hệ agent mô phỏng kinh tế: sinh/cân bằng rule kinh tế bằng LLM agent.
- **Mô phỏng kinh tế MMO bằng LLM agents (arXiv:2506.04699):** phát hiện quan trọng — nếu không tiêm behavioral bias vào agent, **mô phỏng quá duy lý** so với player thật và kết quả sai. [Primary research]
- **MALLES / ECONAI** (kinh tế học + LLM): cùng cảnh báo về giả định rationality.
- **Cách dùng đúng:** dùng mô phỏng làm "dry run" trước khi đưa chính sách giá/offer mới vào experiment thật — như tiền-kiểm thử, không thay thế experiment.

### A.4 Phân tích nguyên nhân và vận hành (cold path)

- LLM agent đọc decision log + metric để đề xuất giả thích vì sao một policy giảm hiệu quả; output là giả thuyết cho analyst, không phải hành động tự động.

### A.5 Slot cấm: LLM trên hot path DECIDE

- DECIDE API (doc 06) phải trả lời trong ngân sách vài ms của game frame / ad request. Không LLM nào hiện nay đáp ứng được với chi phí chấp nhận được ở quy mô hàng triệu request/ngày, kể cả model nhỏ on-device.
- **FunctionGemma (270M, 12/2025)** cho on-device function calling — thú vị cho "warm path" (chuẩn bị context, routing), nhưng không phải cho quyết định monetization latency-critical. [Primary docs — Google Developers]

## B. On-device ML — stack 2026

### B.1 Android: LiteRT là trung tâm

- **TensorFlow Lite đã đổi tên thành LiteRT (9/2024)**, phản ánh chuyển dịch sang runtime tổng quát hơn TFLite; định dạng `.litertlm` cho LLM on-device; GPU delegate mới nhanh hơn ~1.4×. [Primary docs — Google]
- **Stack 2026 khuyến nghị:** LiteRT (inference) + MediaPipe (pipeline cảm biến/xử lý) + ML Kit (tác vụ dựng sẵn) + NNAPI/GPU delegate (acceleration) + Gemini Nano (LLM nhỏ on-device qua AICore). [Primary docs]
- **Với brain:** model bandit/GBDT xuất ra LiteRT đủ nhỏ để chạy <5ms trên máy trung bình; đây là lựa chọn mặc định cho decision inference phía client (doc 15, MVP wedge).

### B.2 iOS: Apple Foundation Models & Private Cloud Compute

- **Apple Foundation Models (AFM):** mô hình on-device ~3B tham số làm nền cho Apple Intelligence; các tác vụ vượt khả năng on-device được chuyển sang **Private Cloud Compute (PCC)** với cam kết không lưu dữ liệu và xử lý kiểm toán được. [Primary docs — Apple ML Research / Security]
- **WWDC 2026 (6/2026):** Apple công bố thế hệ AFM thứ ba, gồm nhiều biến thể server-side cho PCC. [Primary docs]
- **Kiến trúc "on-device nhỏ + private cloud cho phần nặng" của Apple chính là reference design** cho cách một monetization brain nên phân tầng trên iOS.
- **Ràng buộc kỹ thuật:** iOS không hỗ trợ LiteRT native cho production app theo cách Android làm — mô hình on-device trên iOS phải qua **Core ML**. Pipeline huấn luyện phải xuất **hai định dạng** (.tflite cho Android, Core ML cho iOS) từ cùng một kiến trúc; Apple quản lý việc chọn accelerator (Neural Engine/GPU/CPU), app chỉ đặt preference `computeUnits`. [Primary docs — Apple Core ML]
- Nghiên cứu 2024–2025 cho thấy nhiều app iOS vẫn bundle TFLite song song Core ML để giữ parity đa nền tảng — nhưng đường "chính chủ" vẫn là Core ML.

### B.3 On-device personalization (không cần FL đầy đủ)

- Kỹ thuật "local adapter / transfer learning 8-bit lượng tử hóa" trên thiết bị: giữ global model cố định, chỉ huấn luyện adapter nhỏ local theo user — cá nhân hóa on-device với chi phí rất thấp. [Primary research — JISEM 9/2025]
- Phù hợp cho giai đoạn sau của brain khi muốn cá nhân hóa sâu hơn mà không đưa thêm data về server.

## C. Federated Learning — đã production, nhưng chưa ai dùng cho monetization

### C.1 Hai deployment FL lớn nhất thế giới đều của OS vendor

- **Google Gboard:** FL + differential privacy cho language model của bàn phím, chạy production nhiều năm; paper chính thức "Federated Learning of Gboard Language Models with Differential Privacy" (ACL 2023 Industry, arXiv:2305.18465); cập nhật 2/2024 xác nhận tiếp tục đầu tư ("Advances in Private Training for Production On-Device Language Models"). [Primary research + Primary docs]
- **Apple Private Federated Learning (PFL):** dùng cho Siri, QuickType, Face ID, app-selection; framework nghiên cứu **pfl-research** đã open-source; case study "Private Federated Learning in Real World Application" (arXiv:2502.04565). [Primary research + Primary docs]
- **Android Privacy Sandbox** từng có API "On-Device Personalization / federated learning job" cho developer — nhưng với việc Privacy Sandbox bị khai tử 10/2025 (doc 13), tương lai API này không chắc chắn; phải coi FL là thứ **tự xây** trên nền LiteRT/Core ML, không phải thứ được platform cho sẵn.

### C.2 Gap quan trọng

- **Không tìm thấy production example nào của FL cho monetization/offer bandit.** Cả Gboard lẫn Apple PFL đều là prediction task giám sát (next-word, emoji…), không phải bài toán bandit với feedback phản thực.
- **Hệ quả:** FL là hướng đáng theo dõi cho giai đoạn privacy-siết-chặt, nhưng ở hiện tại (giữa 2026), **first-party server-side learning + on-device inference** là con đường thực dụng. Đưa FL vào roadmap Phase 4+ (doc 07), không đưa vào MVP.

## D. Kiến trúc hybrid: đâu on-device, đâu server

### D.1 Tiền lệ production: Adobe Target on-device decisioning

- Adobe Target có mode **on-device decisioning**: tải "rule artifact" (JSON chứa toàn bộ rule A/B/targeting) về cache, quyết định chạy in-memory gần 0-latency; mode **hybrid** (at.js 2.5+) quyết định tại device khi artifact đã cache, fallback về edge khi chưa. [Primary docs — Adobe Experience League]
- Đây là bằng chứng production rằng mô hình **"policy artifact + local evaluation + server fallback"** chạy được ở quy mô lớn — đúng pattern brain cần.

### D.2 Phân tầng đề xuất cho brain

```text
HOT PATH (tại decision point, <10ms, không network):
  - On-device bandit/GBDT (LiteRT / Core ML) chọn action
  - Guardrail engine chạy local (hard rules: cap, age-gate, doc 14)
  - Policy artifact cache (pattern Adobe Target)

WARM PATH (background, seconds–minutes):
  - Đồng bộ policy artifact mới; cập nhật feature local
  - On-device adapter update (mục B.3) khi đủ điều kiện sạc/idle

COLD PATH (server, offline):
  - Train lại policy; LTV model (doc 11)
  - LLM: sinh creative (A.1), mô phỏng kinh tế (A.3), phân tích (A.4)
  - A/B analysis, OPE (doc 05)
```

- **Fallback rule:** decision on-device thất bại/thiếu feature → fallback server; server cũng không kịp → default policy an toàn (NO_ACTION hoặc frequency tối thiểu). Không bao giờ block game frame chờ network.
- Firebase IAP optimization codelab (doc 08) là ví dụ mẫu của hướng on-device inference cho IAP — đáng đọc như tutorial nhập môn pattern này.

### D.3 Server-Driven UI — lớp trình diễn cho output của brain

- **Server-Driven UI (SDUI)** đã thành chuẩn production: **Airbnb Ghost Platform** (Sections/Screens, server gửi JSON mô tả UI, ship homepage redesign không qua app review), **Netflix Hawkins**, Lyft, Shopify, Nubank. [Primary engineering blogs / InfoQ]
- **Liên hệ với brain:** output của IAP_OFFER action không chỉ là "offer_id" mà có thể là **UI descriptor** (layout, copy variant, urgency framing) render bởi SDUI layer → thay đổi presentation không cần release app. Đây cũng là cách triển khai paywall A/B nhanh nhất.
- Lưu ý store policy: Apple cho phép SDUI trong phạm vi tính năng đã công bố; dùng SDUI để thay đổi flow thanh toán/giá có rủi ro review — presentation cá nhân hóa nên đi qua offer type hợp lệ (doc 13, Guidelines 3.1.x).

## E. Bảng tổng kết: dùng / không dùng

| Công nghệ | Slot | Hot path? | Ghi chú |
|---|---|---|---|
| Bandit/GBDT nhỏ (LiteRT/Core ML) | Quyết định action | ✅ | Xương sống |
| Policy artifact cache | Quyết định rule-based | ✅ | Pattern Adobe Target |
| LLM sinh creative | Thư viện creative offline | ❌ | HLLM-Creator pattern |
| LLM mô phỏng kinh tế | Dry-run chính sách | ❌ | Phải tiêm bias hành vi |
| Synthetic players | Red-team/guardrail test | ❌ | Không làm predictor |
| Gemini Nano / AFM on-device | Warm path, routing | ❌ | Chưa cho hot path |
| Federated Learning | Privacy-first training | ❌ | Chưa có tiền lệ monetization |
| SDUI | Trình diễn offer | — | Airbnb Ghost pattern |

## Không xác minh được (ghi rõ)

1. Số liệu cụ thể trong case Layer.ai × Gamegos (self-reported).
2. Trạng thái chính xác của On-Device Personalization FL API sau khi Privacy Sandbox dừng (10/2025).
3. Chi tiết kiến trúc PCC thế hệ ba của Apple ngoài công bố chính thức.

## Nguồn chính

- HLLM-Creator — https://arxiv.org/abs/2508.18118
- Orak benchmark — https://arxiv.org/abs/2506.03610
- LLM player sim (MDPI) — https://doi.org/10.3390/info16040329
- MMO economy LLM agents — https://arxiv.org/abs/2506.04699
- LiteRT — https://developers.googleblog.com/ (announcement 9/2024) + https://github.com/google-ai-edge/LiteRT
- FunctionGemma — https://developers.googleblog.com/ (12/2025)
- Apple Foundation Models — https://machinelearning.apple.com/research/introducing-apple-foundation-models
- Private Cloud Compute — https://security.apple.com/blog/private-cloud-compute/
- Core ML — https://developer.apple.com/documentation/coreml
- Gboard FL — https://aclanthology.org/2023.acl-industry.60/ + https://arxiv.org/abs/2305.18465
- Google private training advances — https://research.google/blog/advances-in-private-training-for-production-on-device-language-models/
- Apple PFL case study — https://arxiv.org/abs/2502.04565 ; pfl-research — https://github.com/apple/pfl-research
- Privacy Sandbox on-device personalization — https://privacysandbox.google.com/protections/on-device-personalization
- Adobe Target on-device decisioning — https://experienceleague.adobe.com/en/docs/target-dev/developer/client-side/at-js-implementation/on-device-decisioning/on-device-decisioning
- Firebase Remote Config Personalization — https://firebase.google.com/docs/remote-config/personalization/about
- Airbnb SDUI — https://www.infoq.com/news/2021/07/airbnb-server-driven-ui/
- Netflix Hawkins / SDUI overview — https://www.weweb.io/blog/server-driven-ui-guide-architecture-examples ; https://www.digia.tech/post/server-driven-ui-sdui-necessary-evil-for-scalable-mobile-apps/
