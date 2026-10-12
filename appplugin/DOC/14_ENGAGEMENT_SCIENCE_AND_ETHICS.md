# 14 — Engagement Science & Ethics: Ranh giới giữa personalization và manipulation

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Khoa học về engagement/difficulty/churn đứng sau mọi monetization decision, và bộ nguyên tắc đạo đức để brain không vượt qua ranh giới manipulation — đặc biệt quan trọng khi hệ thống tối ưu hành vi chứ không chỉ hiển thị quảng cáo.

## Evidence labels

- **[Primary research]**: paper peer-reviewed/arXiv.
- **[Primary patent]**: bằng sáng chế công bố công khai (tồn tại của patent ≠ đang được dùng).
- **[Primary lawsuit record]**: hồ sơ tòa án.
- **[Case study — self-reported]**: A/B do studio tự công bố.

---

## A. Khoa học engagement — nền tảng để đặt reward đúng

### A.1 Flow theory

- Csikszentmihalyi: trạng thái flow khi challenge ≈ skill; lệch về hai phía gây boredom hoặc anxiety. Luận văn của **Jenova Chen** ("Flow in Games", 2007) là bản áp dụng trực tiếp vào game design.
- **Hệ quả cho brain:** difficulty/timing của mọi action (kể cả offer) phải tính đến trạng thái flow hiện tại — chèn interstitial giữa flow state có chi phí retention lớn hơn nhiều so với chèn ở "safe point".

### A.2 Dynamic Difficulty Adjustment (DDA) và vụ kiện EA

- **EA CSCW 2017** — "EOMM" và DDA research papers; các patent liên quan: US20170259177A1 và US10,357,718 (dynamic difficulty). [Primary research + patent]
- **Vụ kiện Zajonc v. EA (2020):** cáo buộc EA dùng DDA để thao túng kết quả nhằm ép mua pack trong game thể thao. **Tòa bác đơn tháng 3/2021** (dismissed; EA phủ nhận sử dụng). [Primary lawsuit record]
- **EA EOMM** (arXiv:1702.06820): matchmaking tối ưu engagement trên 36.9M trận; phát hiện đáng chú ý: thua 3 trận liên tiếp làm tăng xác suất quit ~5.1%; EOMM cải thiện retention đáng kể so với skill-based matchmaking trong mô phỏng. Patent: US20170259178A1. [Primary research]
- **Bài học kiến trúc:** engagement optimizer mạnh thật, nhưng **nếu reward là revenue thay vì engagement, hệ thống tự động trượt sang manipulation**. Đây là lý do doc này tách riêng reward contract cho difficulty loop và monetization loop.

### A.3 Activision matchmaking-to-monetize patent — ranh giới đỏ

- **US9,789,406 B2** (2017): hệ thống matchmaking điều khiển người chơi vào trận mà họ **có khả năng muốn mua item hơn** (ownership-gap: ghép với player sở hữu item hiếm; skill-pressure: ghép newbie với expert dùng vũ khí premium). Activision tuyên bố **chưa triển khai trong game**. [Primary patent + company statement]
- Đây là **ví dụ chuẩn về thiết kế không được làm**: dùng gameplay experience làm công cụ tạo áp lực mua sắm. Brain phải có rule cấm tuyệt đối pattern này (xem mục D).

### A.4 Churn prediction và can thiệp đúng thời điểm

- **DAF (KDD 2021)** — Difficulty-Aware Framework: mô hình churn nhận diện **nguyên nhân churn theo difficulty** và gợi ý can thiệp; gần với blueprint của intervention pipeline trong brain. https://dl.acm.org/doi/10.1145/3447548.3467277 [Primary research]
- Survey churn prediction trong game (arXiv:2104.05554): **LSTM/sequence models thống trị** về performance so với model cổ điển; feature quan trọng nhất là chuỗi hành vi gần đây và tín hiệu frustration.
- **Liên kết doc 11:** survival ensembles cho biết **khi nào** churn — kết hợp DAF cho biết **can thiệp gì**.
- **Khảo sát hành vi Hàn Quốc** (arXiv:2504.10714): tương tác giữa difficulty, monetization pressure và quit behavior trong mobile game Hàn. [Primary research]

### A.5 Near-miss effect — anti-pattern nếu cố tình tạo ra

- Nghiên cứu thần kinh học (Neuron, 2009): near-miss kích hoạt hệ thống reward tương tự thắng thật — nền tảng tâm lý của slot machine.
- **Nếu brain cố tình thiết kế near-miss để tăng purchase intent, đó là manipulation cờ bạc** — vi phạm tinh thần của PEGI 16 loot box rule (doc 13) và là red line đạo đức. Near-miss chỉ chấp nhận được như sản phẩm phụ tự nhiên của gameplay, không bao giờ là output thiết kế.

### A.6 Các kỹ thuật DDA hiện đại (nếu brain mở rộng sang gameplay action)

- **MAB cho DDA** (Shen et al. 2024): bandit chọn difficulty adjustment theo player state.
- **Imitation-learning DDA** (arXiv:2408.06818): học từ data của level designer thay vì tự khám phá.
- **Continuous RL DDA** (arXiv:2308.12726): action space liên tục cho difficulty parameter.
- **PCGRL**: procedural content generation bằng RL — xa hơn, chỉ ghi nhận.

### A.7 Energy gate / pacing — bằng chứng A/B công khai hiếm hoi

- **Cookie Cats** (case kinh điển của Trefor Williams / Gamasutra): A/B test N>90.000 — dời energy gate từ level 30 xuống level 40 **giảm D7 retention**. Kết luận counter-intuitive: gate sớm hơn giữ chân tốt hơn vì tạo nhịp nghỉ có chủ đích. [Case study — self-reported]
- **Source/sink economy design**: tài liệu thiết kế kinh tế game chuẩn — mọi action phát resource (source) phải cân với action tiêu resource (sink); brain phát FREE_ACTION cũng phải tôn trọng cân bằng này.

---

## B. Hai vòng lặp phải tách rời

```text
VÒNG ENGAGEMENT (difficulty, pacing, content)
  Objective:  maximize engagement/retention metrics
  Reward:     session chất lượng, progression, mastery
  KHÔNG nhận tín hiệu revenue

VÒNG MONETIZATION (offer, ad, price)
  Objective:  incremental LTV
  Reward:     revenue + LTV delta − churn penalty − fairness penalty
  KHÔNG được phép gọi vòng engagement để thay đổi difficulty
  vì mục đích bán hàng
```

Nếu hai vòng này nhập làm một với reward revenue duy nhất, hệ thống tái tạo chính xác pattern của patent Activision — dù không ai lập trình nó cố ý.

## C. Bộ quy tắc đạo đức của brain (bản cứng)

1. **Không manufactured failure:** không thiết kế thất bại nhân tạo để tạo nhu cầu mua giải pháp.
2. **Không matchmaking-for-monetization:** không dùng matching/pairing/đối thủ để tạo áp lực mua (patent US9,789,406).
3. **Không engineered near-miss:** không tối ưu xác suất near-miss.
4. **Không artificial urgency quá mức:** countdown phải gắn với sự kiện thật (event thật kết thúc thật), không phải timer reset ảo.
5. **Difficulty loop độc lập với monetization loop** (mục B).
6. **Reward của gameplay optimizer là engagement, không phải revenue.**
7. **Loss-aversion rescue offer** (offer cứu vãn sau thất bại) chỉ chấp nhận được khi thất bại là thật và offer không phải cách duy nhất để vượt qua.
8. **Minh bạch:** mọi dynamic/personalized price phải hiển thị theo đúng yêu cầu EU DFA (doc 13) khi áp dụng.

## D. Cách encode vào guardrail engine (doc 06)

```text
Guardrail categories thêm cho doc 14:
  ETH_MANUFACTURED_FAIL   : chặn action xuất hiện sau khi game state
                            bị engine cố ý làm khó (kiểm tra source của
                            difficulty change)
  ETH_URGENCY_FAKE        : chặn countdown không gắn event_id thật
  ETH_NEAR_MISS_DESIGN    : chặn config điều khiển xác suất near-miss
  ETH_MONETIZE_FAIL_LOOP  : chặn IAP_OFFER trigger trực tiếp từ sự kiện
                            fail liên tiếp (phải qua cooldown + đánh giá
                            churn intent, không phải purchase intent)
  ETH_CHILD_TARGET        : chặn mọi personalized monetization cho
                            account age-gated <13 (doc 13)
```

## Không xác minh được (ghi rõ)

1. EA có đang dùng EOMM/DDA trong production hay không (EA phủ nhận; tòa bác đơn vì không chứng minh được).
2. Activision có từng thử nghiệm patent US9,789,406 nội bộ hay không.
3. Chi tiết số của Cookie Cats ngoài các số đã công bố rộng rãi (N, retention direction).

## Nguồn chính

- Csikszentmihalyi, Flow; Chen, Flow in Games (USC thesis 2007)
- EA CSCW 2017 / EOMM — https://arxiv.org/abs/1702.06820
- EA patents — US20170259177A1, US10,357,718, US20170259178A1
- Activision patent — US9,789,406 B2
- Zajonc v. EA — hồ sơ tòa C.D. Cal., dismissed 3/2021
- DAF KDD 2021 — https://dl.acm.org/doi/10.1145/3447548.3467277
- Churn survey — https://arxiv.org/abs/2104.05554
- Near-miss Neuron 2009 — https://www.cell.com/neuron/ (Chase & Clark 2009 line of work; tìm qua DOI "near-miss gambling")
- Korean difficulty-monetization — https://arxiv.org/abs/2504.10714
- MAB DDA (Shen 2024); imitation DDA — https://arxiv.org/abs/2408.06818 ; continuous RL DDA — https://arxiv.org/abs/2308.12726
- Cookie Cats case — Gamasutra/Game Developer archive
