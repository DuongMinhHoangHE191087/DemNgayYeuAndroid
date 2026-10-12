# 34 — Dataset công khai, benchmark và môi trường mô phỏng: thử thuật toán TRƯỚC khi có dữ liệu thật

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Toàn bộ KB (docs 01–33) mô tả *nên xây gì*, nhưng có một vấn đề thực tế chưa doc nào giải: **trước khi SDK có user thật, lấy dữ liệu đâu để kiểm chứng thuật toán?** Tài liệu này liệt kê nguồn dữ liệu công khai, benchmark chuẩn và môi trường mô phỏng có thể dùng ngay — kèm đánh giá thẳng thắn cái nào dùng được cho bài toán monetization game, cái nào không.

> **Vì sao doc này quan trọng hơn vẻ ngoài của nó:** một bandit/uplift model không thể "review bằng mắt" mà biết đúng sai. Không có dataset để backtest, mọi lựa chọn thuật toán ở doc 25/28 chỉ là niềm tin. Đây là hạ tầng kiểm chứng, không phải phụ lục.

## Evidence labels

- **[Primary dataset]**: dataset công khai, tải được, có paper mô tả.
- **[Primary docs]**: tài liệu chính thức của thư viện/nền tảng.
- **[Primary research]**: paper peer-reviewed/arXiv.
- **[Đánh giá]**: nhận định của tài liệu này về mức độ phù hợp — không phải tuyên bố của tác giả dataset.

---

## A. Nhóm 1 — Off-policy evaluation: dataset chuẩn vàng

### A.1 Open Bandit Dataset (ZOZO) — quan trọng nhất cho kiến trúc của chúng ta

Dataset logged-bandit công khai từ ZOZOTOWN (nền tảng thời trang e-commerce lớn nhất Nhật Bản). **26 triệu dòng**, mỗi dòng là một impression gồm feature, action được chọn, **propensity score thật**, và click outcome. [Primary dataset]

Điểm khiến nó độc nhất: dữ liệu được thu bằng **hai policy khác nhau chạy song song** (Bernoulli Thompson Sampling và Uniform Random) trên cùng một nền tảng, chia theo 3 campaign (all/men/women). Đây là lý do nó là dataset đầu tiên cho phép **so sánh thực nghiệm các OPE estimator** — vì có ground truth của policy đích.

```text
Vì sao khớp trực tiếp với kiến trúc của chúng ta:
  - DecisionLogger ghi chosen_action_probability  ←→  propensity score trong OBD
  - BanditPolicy dùng Thompson Sampling            ←→  behavior policy "bts" trong OBD
  - Bài toán OPE ở doc 24 mục K                    ←→  đúng mục đích thiết kế của OBD
```

**Cách dùng cụ thể:** dùng Open Bandit Pipeline (thư viện Python `obp`) để backtest logic OPE — verify rằng công thức IPS/DR mình định dùng cho decision log thật sự cho ước lượng không lệch, TRƯỚC khi tin vào số liệu từ decision log của chính mình.

**Giới hạn** [Đánh giá]: đây là e-commerce thời trang, không phải game. Action là "recommend item", không phải "show ad vs offer vs no-action". Dùng nó để **kiểm chứng phương pháp OPE**, không dùng để học policy monetization.

### A.2 Open Bandit Pipeline (`obp`) — thư viện đi kèm

Thư viện Python chuẩn hóa: dataset preprocessing, bandit policy interface, và một loạt OPE estimator (IPS, SNIPS, DM, DR, switch-DR, DRos...). [Primary docs] Đây là reference implementation nên đọc trước khi tự viết estimator — tránh lỗi công thức tinh vi (đặc biệt là clipping/self-normalizing).

## B. Nhóm 2 — Uplift/CATE: dataset để kiểm chứng mô hình causal

Đây là nhóm khớp trực tiếp với doc 05 (uplift/CATE) và phần "incremental LTV" ở doc 11 — bài toán trung tâm của toàn bộ luận điểm sản phẩm.

### B.1 Criteo Uplift Prediction Dataset

~25 triệu dòng (một số bản mô tả ~14 triệu — cần kiểm tra bản tải về), 12 feature, 1 treatment indicator, 2 label (visit và conversion). Xây từ **incrementality test thật**: một phần dân số được ngẫu nhiên loại khỏi việc bị target quảng cáo. [Primary dataset]

Đây là benchmark uplift lớn nhất công khai, dùng rộng rãi để đánh giá phương pháp ITE/uplift.

### B.2 Hillstrom MineThatData Email dataset

~64 nghìn dòng, 8 feature (recency, history, mens, womens, zip_code, newbie, channel, history_segment), treatment là email marketing được gán ngẫu nhiên. [Primary dataset] Nhỏ, sạch, kinh điển — dataset lý tưởng để **viết test đơn vị cho uplift model**: đủ nhỏ để chạy trong CI, đủ chuẩn để có kết quả tham chiếu so sánh.

### B.3 Benchmark tổng hợp

Có repo benchmark công khai chạy 7 phương pháp uplift trên 5 dataset (Hillstrom, Criteo, Lenta, RetailHero, MegaFon) kèm bootstrap CI. [Primary docs] Đây là điểm khởi đầu tốt hơn tự dựng benchmark từ đầu.

**Đánh giá phù hợp:** cao. Bài toán "ai sẽ đổi hành vi vì intervention" ở đây **giống hệt** bài toán "player nào persuadable với offer" (doc 01 mục 7). Domain khác (retail/email vs game) nhưng cấu trúc bài toán trùng khớp — dùng để chọn và calibrate phương pháp (S/T/X/R-learner), sau đó mới áp lên dữ liệu game.

## C. Nhóm 3 — Dataset game thật: hiếm và nhỏ

Đây là chỗ thực tế phũ phàng: **dữ liệu hành vi game công khai cực kỳ khan hiếm**. Chính giới nghiên cứu game analytics ghi nhận sự khan hiếm dataset hành vi mã nguồn mở là rào cản cho nghiên cứu tái lập được. [Primary research]

### C.1 Cookie Cats A/B (Tactile Entertainment) — nhỏ nhưng đúng bài toán

90.189 người chơi, A/B test dời cổng thời gian (gate) từ level 30 sang level 40, đo `retention_1`, `retention_7`, `sum_gamerounds` (14 ngày đầu). [Primary dataset]

**Đây là dataset game công khai giá trị nhất cho chúng ta** — vì nó chính xác là một quyết định monetization/progression thật (đặt gate ở đâu), có kết quả retention thật, randomize thật. Doc 14 đã trích kết luận của nó; giờ có thể **tự chạy lại phân tích** thay vì chỉ trích dẫn.

Dùng để: kiểm chứng pipeline phân tích A/B của mình (tính lift, CI, bootstrap) cho ra đúng kết quả đã công bố — một bài test end-to-end tốt cho Control Plane's Experiment module (doc 26).

**Giới hạn:** chỉ 1 quyết định, 2 nhánh, không có propensity, không có context per-user phong phú. Không dùng để train bandit được.

### C.2 Open Play (2026) — dataset dài hạn đa nền tảng, mới và đáng chú ý

2.238 người tham gia (18–40 tuổi, US/UK), **1,5 triệu giờ chơi**, 10.475 game, trên Nintendo Switch (42 tháng), Steam (3–12 tháng), Xbox (42 tháng, US), iOS và Android (12 tuần). Kèm **đo lường tâm lý**: trầm cảm, lo âu, hài lòng cuộc sống, chất lượng giấc ngủ, động cơ chơi game, nhật ký sử dụng thời gian. Tải tự do dưới CC0 (có điều khoản chống tái định danh) qua Zenodo. [Primary dataset]

**Vì sao đáng chú ý** [Đánh giá]: đây là dataset hiếm hoi nối **telemetry hành vi với trạng thái tâm lý thật của người chơi** — chính xác là loại dữ liệu cần để kiểm chứng ý tưởng `ComfortIndex` ở doc 26 mục C.4. Không có dữ liệu chi tiêu, nên không thay thế được dữ liệu monetization; nhưng để nghiên cứu quan hệ giữa cường độ chơi và tình trạng tâm lý thì đây là nguồn tốt nhất hiện có công khai.

**Giới hạn nghiêm trọng cho mục đích của chúng ta:** không có sự kiện IAP/ad, không có decision log, mẫu tự nguyện (self-selected) nên không đại diện dân số chơi game nói chung.

### C.3 Các dataset học thuật rải rác

- Dataset log giao dịch game social casino (LengBear, Campuchia): ~342.544 bản ghi IAP kèm play log. [Primary dataset] Nhỏ, thị trường hẹp, thể loại casino — [Đánh giá] giá trị hạn chế cho game casual US/EU.
- Nhiều paper churn prediction dùng dữ liệu riêng của studio đối tác, **không công bố dataset** — chỉ đọc phương pháp được, không tái lập được.

## D. Nhóm 4 — Môi trường mô phỏng: khi không có dữ liệu thì tự sinh

Đây là con đường thực tế nhất cho giai đoạn tiền-sản-phẩm.

| Môi trường | Bản chất | Phù hợp cho chúng ta |
|---|---|---|
| **RecoGym** | Môi trường RL (nền OpenAI Gym) cho product recommendation trong quảng cáo online; mô phỏng cả tương tác organic lẫn bandit, chỉnh được tương quan giữa hai loại, số chiều không gian ẩn của sở thích user, và mức ảnh hưởng của phơi nhiễm quảng cáo quá khứ lên CTR. [Primary research] | **Cao** — cấu trúc "organic behavior + ad exposure ảnh hưởng lẫn nhau" chính là mô hình IAA↔engagement của chúng ta |
| **RecSim / RecSim NG** (Google) | Nền tảng mô phỏng cấu hình được cho recommender, hỗ trợ tương tác tuần tự, **latent user state và động lực học của nó**, choice model. [Primary research] | **Cao** — latent state + sequential chính là POMDP ở doc 24; đây là nơi thử belief-state policy mà không cần user thật |
| SOFA, PyRecGym, T-RECS | Các môi trường mô phỏng khác (SOFA hiệu chỉnh sai lệch, T-RECS nghiên cứu tác động xã hội) | Trung bình — hữu ích cho nghiên cứu chuyên sâu |

**Cách dùng đề xuất** [Đánh giá]: cấu hình RecSim với latent user state gồm `ad_tolerance` và `purchase_propensity` (đúng 2 biến ẩn ở `BeliefState.kt`), action space gồm NO_ACTION/AD/OFFER, rồi chạy `DecisionEngine` logic chống lại nó. Đây là cách **duy nhất** kiểm chứng được rằng belief update + bandit hội tụ đúng hướng trước khi có player thật — và cũng là nền cho "synthetic players để red-team guardrail" đã nêu ở doc 16 mục A.2.

Cảnh báo quan trọng (nhất quán với doc 16 mục A.3): mô phỏng chỉ đúng bằng đúng giả định hành vi nạp vào nó. **Không bao giờ dùng số từ mô phỏng làm bằng chứng hiệu quả kinh doanh** — chỉ dùng để bắt lỗi logic, kiểm tra hội tụ, và stress-test guardrail.

## E. Kế hoạch kiểm chứng đề xuất (thứ tự thực tế)

```text
Bước 1 — Kiểm chứng phương pháp OPE (không cần dữ liệu game)
   Open Bandit Dataset + obp → xác nhận công thức IPS/DR mình dùng cho
   decision log là đúng, không lệch. Nếu sai ở đây thì mọi số liệu
   sau này từ production đều vô nghĩa.

Bước 2 — Kiểm chứng uplift model
   Hillstrom (nhỏ, chạy trong CI) → Criteo (quy mô lớn).
   Chọn meta-learner (S/T/X/R) dựa trên kết quả thật, không theo cảm tính.

Bước 3 — Kiểm chứng pipeline phân tích A/B
   Cookie Cats → chạy lại phân tích, phải ra đúng kết luận đã công bố
   (gate ở level 30 tốt hơn level 40 về retention). Đây là test
   end-to-end cho Experiment module của Control Plane (doc 26).

Bước 4 — Kiểm chứng belief-state + bandit hội tụ
   RecSim/RecoGym cấu hình theo latent state của BeliefState.kt.
   Đây là nơi bắt lỗi logic DecisionEngine trước khi chạm user thật.

Bước 5 — Chỉ sau 4 bước trên mới nối vào traffic thật, và vẫn phải
   có holdout ngay từ ngày đầu (doc 05 mục 10).
```

Điểm mấu chốt: **bước 1–4 làm được ngay hôm nay, không cần chờ SDK có user.** Đây là công việc song song với việc xây SDK, không phải công việc nối tiếp sau nó.

## F. Khoảng trống dữ liệu — và hệ quả chiến lược

Không tồn tại dataset công khai nào có đủ: (a) sự kiện monetization per-user, (b) context phong phú, (c) propensity/randomization, (d) outcome dài hạn (LTV/retention). Ba dataset tốt nhất mỗi cái thiếu một mảnh khác nhau.

**Hệ quả chiến lược** [Đánh giá]: chính sự khan hiếm này là một phần **hào bảo vệ (moat)** của sản phẩm. Nếu ai cũng có dữ liệu decision log chuẩn OPE thì lợi thế thuộc về ai có thuật toán tốt nhất — nhưng vì dữ liệu khan hiếm, lợi thế thuộc về ai **thu thập được dữ liệu đúng chuẩn sớm nhất**. Điều này củng cố quyết định ở doc 23/25: decision log chuẩn OPE là Phase 0, làm trước cả ML — không phải vì ML cần nó, mà vì đó chính là tài sản.

## Không xác minh được (ghi rõ)

1. Số dòng chính xác của Criteo Uplift Dataset — các nguồn mô tả khác nhau (~25M vs ~14M), có thể do phiên bản dataset khác nhau; phải kiểm tra khi tải về thật.
2. Chưa tự tải và chạy bất kỳ dataset nào ở trên trong quá trình viết tài liệu này — toàn bộ mô tả dựa trên tài liệu công bố của tác giả dataset, chưa xác nhận bằng thực nghiệm.
3. Mức độ phù hợp của RecSim/RecoGym cho bài toán monetization là **đánh giá suy luận** từ mô tả kiến trúc, chưa có ai công bố việc dùng chúng cho bài toán IAA↔IAP decisioning cụ thể.
4. Tình trạng giấy phép/điều khoản sử dụng của từng dataset cho mục đích thương mại chưa được rà — phải đọc license trước khi dùng trong sản phẩm bán ra (đặc biệt Open Play có điều khoản chống tái định danh).

## Nguồn chính

- Open Bandit Dataset & Pipeline (arXiv) — https://arxiv.org/abs/2008.07146
- Open Bandit Pipeline (GitHub) — https://github.com/st-tech/zr-obp
- Open Bandit Pipeline docs — https://zr-obp.readthedocs.io/en/latest/
- ZOZO Research Data Release — https://research.zozo.com/data.html
- Criteo Uplift Prediction Dataset — https://ailab.criteo.com/criteo-uplift-prediction-dataset/
- A Large Scale Benchmark for Uplift Modeling (Diemert et al., AdKDD 2018) — http://papers.adkdd.org/2018/papers/adkdd18-diemert-large-scale.pdf
- Uplift benchmark 7 phương pháp × 5 dataset — https://github.com/yablochnikovds/uplift-bench
- Cookie Cats A/B dataset (Kaggle) — https://www.kaggle.com/datasets/yufengsui/mobile-games-ab-testing
- Open Play longitudinal dataset — https://digital-wellbeing.github.io/open-play/ (DOI: https://doi.org/10.5281/zenodo.17536656)
- RecoGym (arXiv) — https://arxiv.org/pdf/1808.00720
- RecSim (arXiv) — https://arxiv.org/abs/1909.04847
- Churn prediction survival ensembles (arXiv) — https://arxiv.org/pdf/1710.02264
