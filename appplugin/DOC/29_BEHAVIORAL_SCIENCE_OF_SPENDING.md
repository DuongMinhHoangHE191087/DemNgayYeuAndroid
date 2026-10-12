# 29 — Behavioral science của hành vi chi tiêu trong game: nền tảng học thuật đầy đủ

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Nền tảng **khoa học hành vi** đằng sau quyết định chi tiêu của người chơi F2P, ở mức sâu học thuật: decision theory (prospect theory, mental accounting), pain of paying & payment decoupling, reinforcement schedules và neuroscience của reward prediction error, khối bằng chứng loot box ↔ problem gambling (kèm effect size), nghiên cứu về người chơi dễ tổn thương, tâm lý động cơ (SDT), habit formation, social influence, pricing psychology, và một rubric phân định ranh giới đạo đức. Thị trường mục tiêu: **US/EU** — mọi tham chiếu pháp lý là FTC, EU CPC Network, EU Digital Fairness Act, không phải khung pháp lý châu Á (trừ khi dùng làm đối chứng compliance).

> **Doc này KHÔNG lặp lại:** flow theory và EOMM/DDA ở mức tổng quan (doc 14), near-miss anti-pattern (doc 14/30), design craft economy (doc 30), pricing/LTV mechanics (doc 11), ràng buộc privacy (doc 13). Doc này là **tầng nền lý thuyết** mà các doc kia đứng lên.

> **Cảnh báo phương pháp luận đọc trước:** Behavioral economics đang trải qua một cuộc khủng hoảng replication nghiêm trọng. Rất nhiều "hiệu ứng" được các blog monetization trích dẫn như định luật vật lý thực ra là kết quả của một thí nghiệm lab n=40 năm 1982 chưa từng replicate thành công ở quy mô lớn. Doc này **gắn nhãn `[Contested]` cho mọi hiệu ứng như vậy** thay vì giấu đi. Nếu bạn xây feature SDK dựa trên một hiệu ứng `[Contested]`, hãy coi nó là **giả thuyết cần A/B test trên chính traffic của bạn**, không phải kiến thức đã biết.

### Quy ước nhãn bằng chứng

| Nhãn | Nghĩa |
|---|---|
| `[Primary research — peer-reviewed]` | Nghiên cứu gốc, đã peer review, có effect size hoặc dữ liệu kiểm chứng được |
| `[Primary research — preprint]` | Nghiên cứu gốc chưa peer review (arXiv, SSRN, PsyArXiv) |
| `[Secondary]` | Review, tổng hợp, báo cáo ngành, tài liệu pháp lý, tường thuật lại nghiên cứu gốc |
| `[Contested]` | Hiệu ứng có vấn đề replication nghiêm trọng hoặc effect size bị thổi phồng — **không dùng làm cơ sở thiết kế mà không tự test** |
| `[Design synthesis]` | Suy dẫn / khung hóa của chính tôi trong doc này |

---

## 1. Decision theory áp dụng vào quyết định mua trong game

### 1.1 Prospect theory: ba trụ cột và ánh xạ vào store design

Kahneman & Tversky (1979, *Econometrica*) — "Prospect Theory: An Analysis of Decision under Risk" — thay hàm utility của expected utility theory bằng ba thành phần. Đây là nền tảng **được replicate tốt nhất** trong toàn bộ behavioral economics; ba trụ cột dưới đây đều đứng vững qua 45 năm `[Primary research — peer-reviewed]`.

**(a) Reference dependence.** Con người không đánh giá trạng thái tuyệt đối mà đánh giá **thay đổi so với một điểm tham chiếu**. Trong game store, điểm tham chiếu không phải "0 đồng" mà là **cái mà player tin mình đáng lẽ có**:

```text
Reference point được set bởi:
  - Giá "gốc" gạch ngang trong offer          → reference = giá cao
  - Số gem player từng có trước khi tiêu       → reference = balance cũ
  - Rank/tier trong leaderboard tuần trước     → reference = vị trí cũ
  - Streak day count                           → reference = streak hiện tại
  - Item mà bạn bè trong guild đang có         → reference = social baseline
```

Hệ quả thiết kế quan trọng nhất: **mọi cơ chế tạo ra một reference point mới cao hơn trạng thái hiện tại đều chuyển quyết định "có mua không" thành quyết định "có chấp nhận mất không"** — và mất thì đau gấp đôi (xem (b)).

**(b) Loss aversion.** Hàm value dốc hơn ở phía mất. Hệ số λ ≈ 2.25 trong ước lượng gốc của Tversky & Kahneman (1992). **Cảnh báo hiệu chỉnh:** con số 2.25 bị lạm dụng khủng khiếp. Các ước lượng sau này cho λ dao động rộng theo domain, kích thước tiền cược và cách hỏi; với các khoản tiền nhỏ (đúng phạm vi IAP $0.99–$9.99) loss aversion **yếu hơn đáng kể** so với khoản lớn, và một số meta-analysis tìm thấy λ gần 1.3–1.6 ở stakes thấp. Hãy dùng "mất đau hơn được" như một **hướng định tính**, đừng dùng 2.25 như một hằng số để nhân vào mô hình `[Contested]` — cụ thể là contested về *độ lớn*, không contested về *dấu*.

**(c) Probability weighting.** Con người **overweight xác suất nhỏ** và **underweight xác suất trung bình-cao**. Hàm weighting π(p) có dạng chữ S ngược: π(0.01) có thể hành xử như 0.05.

Đây là điểm cắt trực tiếp nhất vào gacha/loot box và cũng là điểm **quan trọng nhất về mặt đạo đức** của cả doc này:

```text
Gacha rate 0.6% cho SSR:
  Xác suất khách quan               p = 0.006
  Trọng số quyết định (π(p))        ≈ 0.03–0.05  (overweight 5–8×)

⇒ Việc disclose rate KHÔNG tự động sửa được sai lệch này.
  Player nhìn thấy "0.6%" và vẫn ra quyết định như thể là "3%".
```

Hệ quả: **probability disclosure là điều kiện cần nhưng KHÔNG đủ** để coi một hệ thống random là "fair". Đây là lý do các đề xuất pháp lý ở EU đang chuyển từ "disclose the odds" sang "disclose expected spend to obtain" — vì expected spend không bị probability weighting bóp méo theo cách xác suất bị bóp `[Design synthesis]`.

### 1.2 Mental accounting (Thaler)

Thaler (1985, *Marketing Science*; 1999, *Journal of Behavioral Decision Making* — "Mental Accounting Matters") mô tả cách con người phân loại tiền vào các "tài khoản tinh thần" riêng biệt, vi phạm tính fungibility của tiền `[Primary research — peer-reviewed]`.

Ba cơ chế Thaler mô tả, ánh xạ vào game:

| Cơ chế Thaler | Nội dung | Ánh xạ game |
|---|---|---|
| **Framing of gains/losses** | Tách các khoản được ra (segregate gains), gộp các khoản mất lại (integrate losses) | Bundle nhiều item vào 1 lần trả tiền (integrate loss); trao thưởng lẻ tẻ nhiều lần (segregate gains) — chính là cấu trúc battle pass |
| **Topical accounting** | Đánh giá giao dịch trong "khung chủ đề" hẹp chứ không trên tổng tài sản | Gem đã mua nằm trong "tài khoản giải trí game X", không cạnh tranh với tiền ăn uống |
| **Budgeting & self-control** | Người ta tự đặt ngân sách theo category | Player tự đặt "tôi tiêu $20/tháng cho game này" — và **hard currency balance làm ngân sách đó biến mất khỏi tầm nhìn** |

**Điểm then chốt cho SDK:** một giao dịch bằng tiền thật ($9.99) được ghi vào tài khoản tinh thần "chi tiêu"; một giao dịch bằng gem đã mua trước đó được ghi vào tài khoản "sử dụng tài sản đã sở hữu". Đây không phải lỗi nhận thức ngẫu nhiên — đó là hệ quả trực tiếp của cấu trúc mental accounting. Currency layering **thiết kế ra sự nhầm lẫn này một cách có chủ đích**.

### 1.3 Endowment effect

Kahneman, Knetsch & Thaler (1990, *JPE*) — người sở hữu một vật định giá nó cao hơn người chưa sở hữu, cùng một vật `[Primary research — peer-reviewed]`. Tồn tại thật, nhưng **effect size nhỏ hơn nhiều so với mô tả phổ thông** và phụ thuộc mạnh vào procedure; một dòng nghiên cứu đáng kể (Plott & Zeiler) cho rằng phần lớn hiệu ứng trong các thí nghiệm gốc là artifact của cách hướng dẫn người tham gia. Xếp: **có thật, độ lớn `[Contested]`**.

Ánh xạ game — ba pattern khai thác endowment, xếp theo mức độ chấp nhận được:

```text
CHẤP NHẬN ĐƯỢC   Free trial của skin trong 1 trận đấu, kết thúc rõ ràng
                 → player trải nghiệm giá trị, biết chính xác trial kết thúc khi nào

GREY             "Bạn đã mở khóa tạm thời X, giữ nó bằng cách mua trong 24h"
                 → tạo sở hữu giả rồi đe dọa lấy đi

MANIPULATION     Trao item vào inventory, hiển thị nó như đã sở hữu, rồi
                 khóa lại sau đó và đòi tiền để "khôi phục"
                 → FTC coi loại này là false belief creation (xem §10)
```

### 1.4 Sunk cost trong progression system

Arkes & Blumer (1985) — con người tiếp tục đầu tư vào một lựa chọn đã tiêu tốn nguồn lực, dù quyết định hợp lý là bỏ `[Primary research — peer-reviewed]`. Trong game, sunk cost không chỉ là tiền mà là **thời gian, tiến độ và bản sắc**:

- **Pity counter** là cỗ máy sunk cost thuần túy: mỗi lần pull không trúng làm tăng giá trị kỳ vọng của lần pull tiếp theo *một cách khách quan có thật* (pity là thật), nhưng đồng thời tạo cảm giác "bỏ bây giờ là phí 60 lần vừa rồi". Đây là trường hợp hiếm mà **sunk cost fallacy trùng với tính toán đúng** — và chính điều đó khiến pity vừa là feature pro-consumer vừa là công cụ giữ chân mạnh nhất trong gacha.
- **Battle pass đã mua** biến mọi phiên chơi còn lại trong season thành "bảo vệ khoản đầu tư".
- **Streak** (xem §7) là sunk cost + loss aversion cộng dồn.

**Ranh giới thực dụng `[Design synthesis]`:** sunk cost trở thành có hại khi hệ thống **chủ động nhắc lại khoản đã đầu tư trong bối cảnh bán hàng**. "Bạn đã pull 68/90" hiển thị trong màn hình gacha là thông tin minh bạch. Cùng thông tin đó gửi qua push notification lúc 22:30 kèm nút mua gem là một hành vi khác về chất.

---

## 2. Pain of paying & payment decoupling

### 2.1 Cơ sở lý thuyết

Khái niệm "pain of paying" do Ofer Zellermayer (1996) đặt tên, được Prelec & Loewenstein (1998, *Marketing Science* — "The Red and the Black: Mental Accounting of Savings and Debt") hình thức hóa thành mô hình **double-entry mental accounting** `[Primary research — peer-reviewed]`.

Ý tưởng trung tâm: khi tiêu dùng và thanh toán gắn chặt về mặt thời gian và nhận thức, mỗi hành động **gợi nhớ** hành động kia:
- Thanh toán *trước* tiêu dùng → payment bị "khấu hao" (prospective accounting), pain giảm dần → tiêu dùng sướng hơn.
- Thanh toán *sau* tiêu dùng → mỗi lần trả gợi nhớ lại niềm vui đã hết → pain tăng.

Prelec & Loewenstein gọi hiện tượng làm đứt liên kết này là **decoupling**. Thẻ tín dụng là ví dụ kinh điển: nó vừa trì hoãn, vừa gộp chung, vừa làm mờ ranh giới giữa từng giao dịch.

### 2.2 Currency layering là decoupling engine

Kiến trúc chuẩn của F2P là một chuỗi decoupling ba tầng:

```text
TẦNG 0  Tiền thật (USD/EUR)
          ↓  [decoupling #1: đổi sang đơn vị lạ, tỉ giá không tròn]
TẦNG 1  Hard currency (gem, V-Bucks, crystal)
          ↓  [decoupling #2: gem không mua trực tiếp item, mua "lượt"/"box"]
TẦNG 2  Randomiser / soft currency / energy
          ↓
TẦNG 3  Kết quả thực (item, tiến độ)
```

Mỗi mũi tên làm ba việc: (i) **xóa mất neo giá trị real-world**, (ii) **chuyển quyết định chi tiêu ra khỏi thời điểm đau**, (iii) **tạo residual balance** — số dư lẻ không mua nổi thứ gì, buộc phải nạp thêm (pack size không bao giờ chia hết cho giá item là một pattern cố ý, xem doc 30).

### 2.3 Bằng chứng thực nghiệm về elasticity ở từng tầng

Đây là chỗ tôi phải trung thực: **bằng chứng học thuật định lượng cho từng tầng riêng biệt rất mỏng**. Cái có được:

- Một thí nghiệm đăng trên *Experimental Economics* (Cambridge University Press) cho thấy **việc dùng virtual currency thay vì tiền thật làm TĂNG willingness-to-pay cho loot box** trong một thiết kế thí nghiệm có kiểm soát `[Primary research — peer-reviewed]`. Đây là bằng chứng nhân quả trực tiếp nhất hiện có cho decoupling #1 trong bối cảnh game. Hướng hiệu ứng rõ ràng; độ lớn thì áp dụng cho lab setting, không nên ngoại suy thành hệ số nhân doanh thu.
- Chuỗi nghiên cứu về cashless payment (thẻ, ví điện tử) cho thấy chi tiêu tăng và pain giảm một cách nhất quán qua nhiều paradigm; một số nghiên cứu neuroimaging cho thấy trả tiền mặt kích hoạt vùng liên quan xử lý đau mạnh hơn trả thẻ `[Secondary]`. Lưu ý: dòng neuroimaging này **nhỏ, ít replicate**, đừng trích như bằng chứng mạnh.
- Hoàn toàn **không có** nghiên cứu công khai peer-reviewed nào tôi tìm được đo được elasticity riêng của tầng 2 (gem → box) tách khỏi tầng 1 trong dữ liệu game thật. Bất kỳ con số nào bạn thấy trên blog vendor kiểu "currency layering tăng ARPPU 30%" đều là self-reported không kiểm chứng.

**Hàm ý cho SDK `[Design synthesis]`:**

| Quyết định thiết kế | Hướng của bằng chứng | Độ tin cậy |
|---|---|---|
| Hiển thị real-money equivalent bên cạnh giá gem | Giảm chi tiêu impulsive, tăng chi tiêu có chủ đích | Cao (đúng hướng lý thuyết + là hướng của EU DFA) |
| Pack size chia hết cho giá item phổ biến | Giảm residual-balance coercion | Trung bình |
| Đơn vị hard currency có tỉ giá tròn (100 gem = $1) | Giảm decoupling #1 | Trung bình |
| Bỏ hoàn toàn hard currency, bán item trực tiếp bằng tiền thật | Giảm mạnh spending, tăng trust | Cao về hướng, chi phí doanh thu thật |

Chú ý: bảng này liệt kê các can thiệp **giảm** decoupling. Đó là lựa chọn có chủ ý — vì hướng ngược lại (tăng decoupling) là hướng mà EU DFA đang nhắm vào (§10) và không nên được đưa vào một SDK bán cho studio US/EU.

---

## 3. Reinforcement schedules và neuroscience của reward

### 3.1 Dòng dõi Skinner

Ferster & Skinner (1957, *Schedules of Reinforcement*) phân loại bốn lịch trình cơ bản `[Primary research — peer-reviewed]`:

| Schedule | Định nghĩa | Đặc tính response | Ví dụ game |
|---|---|---|---|
| **Fixed Ratio (FR)** | Thưởng sau N hành động cố định | Burst nhanh, nghỉ sau thưởng (post-reinforcement pause) | "Chơi 5 trận nhận 1 chest" |
| **Variable Ratio (VR)** | Thưởng sau trung bình N hành động, ngẫu nhiên | **Tốc độ cao nhất, kháng extinction mạnh nhất** | Gacha, loot box, drop rate |
| **Fixed Interval (FI)** | Thưởng cho hành động đầu sau T thời gian | Scalloping — dồn hoạt động về cuối interval | Daily reward, energy regen |
| **Variable Interval (VI)** | Thưởng sau trung bình T thời gian | Đều, bền | Random world event spawn |

Kết luận kinh điển và **được replicate rất rộng** (từ pigeon đến người): **VR tạo response rate cao nhất và kháng extinction mạnh nhất**. Đó chính xác là lý do mọi hệ thống random reward thương mại đều là VR, và tại sao máy slot là VR.

**Cảnh báo ngoại suy `[Contested]`:** dòng nghiên cứu gốc là operant conditioning trên động vật với reinforcer sinh học (thức ăn) trên sinh vật đói. Việc ngoại suy thẳng sang human purchasing decision với reinforcer là pixel là một bước nhảy lớn. Cấu trúc schedule ánh xạ được; **độ mạnh của conditioning thì không có bằng chứng ngoại suy trực tiếp**. Đừng viết trong pitch deck rằng "VR schedule tạo compulsion loop không thể cưỡng lại" — đó là overclaim.

### 3.2 Reward prediction error (Schultz)

Wolfram Schultz và cộng sự (Schultz, Dayan & Montague, 1997, *Science* — "A Neural Substrate of Prediction and Reward") ghi được hoạt động của dopamine neuron ở primate và cho thấy chúng **không mã hóa phần thưởng, mà mã hóa sai số dự đoán phần thưởng (RPE)** `[Primary research — peer-reviewed]`:

```text
RPE = R_thực_tế − R_dự_đoán

R đúng như dự đoán   → RPE ≈ 0     → dopamine không burst
R tốt hơn dự đoán    → RPE > 0     → dopamine burst
R tệ hơn dự đoán     → RPE < 0     → dopamine dip dưới baseline
Cue dự báo R         → burst dịch chuyển VỀ PHÍA CUE
```

Đây là một trong những phát hiện vững nhất của neuroscience hệ thống, và trùng khớp toán học với thuật toán temporal-difference learning trong RL.

**Ba hệ quả thiết kế trực tiếp `[Design synthesis]`:**

1. **Phần thưởng có thể dự đoán hoàn toàn thì gần như không tạo tín hiệu học tập.** Daily reward cố định ngày 1–7 giá trị biết trước tạo RPE ≈ 0 sau vài chu kỳ. Nó duy trì thói quen (§7) chứ không tạo hưng phấn.
2. **Bất định là nguồn của RPE, không phải độ lớn.** Một reward trung bình nhưng bất định tạo tín hiệu mạnh hơn một reward lớn nhưng chắc chắn. Đây là cơ sở neuroscience của loot box — và cũng là chỗ ranh giới đạo đức nằm.
3. **Dopamine burst dịch về cue** giải thích vì sao animation mở box (ánh sáng, tiếng, delay) tự nó trở thành reinforcer. Kéo dài animation reveal **không phải trang trí** — nó là kỹ thuật khuếch đại cue.

### 3.3 Ranh giới giữa engaging và harmful

Không có ngưỡng do khoa học cung cấp. Cái tồn tại là một tập **chiều** mà khi tất cả cùng đẩy về một phía thì hệ thống rơi vào vùng harmful `[Design synthesis]`:

| Chiều | Phía engaging | Phía harmful |
|---|---|---|
| Chi phí biên của một lần thử | Bằng 0 (thời gian) | Tiền thật, không giới hạn |
| Giới hạn tần suất | Có cap tự nhiên (energy, cooldown) | Không cap, chỉ giới hạn bởi ví |
| Tính minh bạch của phân phối | Rate công khai, kiểm chứng được | Ẩn, hoặc rate động theo profile chi tiêu |
| Near-miss | Không có, hoặc là ngẫu nhiên thật | Được dàn dựng có chủ đích |
| Nhắm mục tiêu | Đồng nhất cho mọi player | Cường độ tăng theo lịch sử chi tiêu |
| Có sàn kết quả | Pity/ceiling rõ ràng | Không có, tail vô hạn |

**Quy tắc thực dụng:** một hệ thống VR mà chi phí biên bằng 0 và có cap tần suất là **game design**. Một hệ thống VR mà chi phí biên là tiền thật, không cap, và cường độ được cá nhân hóa theo lịch sử chi tiêu là **gambling design**, bất kể nó được gọi tên là gì.

---

## 4. Khối bằng chứng loot box ↔ gambling

Đây là literature được nghiên cứu kỹ nhất trong toàn bộ doc này, nhưng cũng bị **overclaim nhiều nhất** theo cả hai hướng.

### 4.1 Cái mà bằng chứng CÓ hỗ trợ

**Zendle & Cairns (2018), PLOS ONE, n = 7,422** `[Primary research — peer-reviewed]`
Khảo sát quy mô lớn, tìm thấy liên hệ giữa chi tiêu loot box và mức độ nghiêm trọng của problem gambling: **η² = 0.054**. Các tác giả lưu ý effect size này lớn hơn liên hệ giữa problem gambling và một số risk factor kinh điển trong literature cờ bạc (như lạm dụng chất).

**Zendle & Cairns (2019), PLOS ONE — replication study** `[Primary research — peer-reviewed]`
Replicate thành công: **η² = 0.051** (gốc 0.054). Subgroup analysis: Cohen's d giữa non-problem gambler và các nhóm khác dao động **d = 0.277–0.368** ở nghiên cứu gốc, **d = 0.429–0.568** ở replication.

**Hai meta-analysis độc lập, 2021** `[Primary research — peer-reviewed]`
- Spicer et al. (2021), 13 studies: **r = 0.27**
- Garea, Drummond, Sauer, Hall & Williams (2021), 15 studies: **r = 0.26**

Hai con số này hội tụ đáng kể. Diễn giải đúng: **small-to-moderate positive correlation, được replicate tốt**. Trong tâm lý học lâm sàng, r ≈ 0.26 là mức có ý nghĩa lâm sàng thật ở quy mô dân số, dù giải thích được khoảng 7% phương sai.

**Drummond & Sauer (2018), Nature Human Behaviour** `[Primary research — peer-reviewed]`
Phân tích cấu trúc 22 game có loot box đối chiếu với các tiêu chí cấu trúc của gambling. Kết luận: loot box chia sẻ đặc tính cấu trúc và tâm lý quan trọng với gambling — phân phối phần thưởng ngẫu nhiên, giá trị phần thưởng biến thiên, near-miss, cue thị giác/âm thanh gắn với tham gia và phần thưởng. Lưu ý đây là **phân tích cấu trúc, không phải nghiên cứu tác động lên người chơi**.

**King & Delfabbro (2018), Addiction** `[Primary research — peer-reviewed]`
Đặt định nghĩa được trích dẫn nhiều nhất cho **predatory monetisation**: các hệ thống mua bán **che giấu hoặc trì hoãn tiết lộ chi phí dài hạn** cho đến khi người chơi đã cam kết về tài chính và tâm lý. Ba đặc trưng: intrusive solicitation, limited disclosure, manipulation of reward outcomes. Đây là định nghĩa hữu ích nhất cho mục đích kỹ thuật vì nó **không dựa vào việc có randomness hay không** — nó dựa vào tính minh bạch của tổng chi phí.

**Phân bố chi tiêu (phân tích thứ cấp dữ liệu Zendle, n = 7,767 người mua loot box)** `[Primary research — peer-reviewed]`
Khoảng **5% người mua loot box (chi > ~£70/tháng) tạo ra khoảng một nửa doanh thu loot box**. Quan trọng hơn: phân tích cho thấy nhóm này có xu hướng là người **có vấn đề cờ bạc**, chứ không đơn giản là người **có thu nhập cao**. Đây là phát hiện có sức nặng đạo đức lớn nhất trong toàn bộ literature: mô hình doanh thu whale không phải trung lập về mặt phúc lợi.

**Xiao và cộng sự — compliance với probability disclosure** `[Primary research — peer-reviewed]`
- Trung Quốc (quy định pháp luật): tỉ lệ disclose ~**95.6%**, nhưng tuân thủ ở mức "suboptimal" — nhiều công ty diễn giải quy định theo hướng tối thiểu hóa.
- Nhật Bản: trong 91 game có loot box, **90.1%** disclose ít nhất một loot box, nhưng chỉ **41.8%** disclose đầy đủ mọi loot box trong một giờ chơi đầu.
- UK (self-regulation của ngành): **64.0%** trong 100 game iPhone doanh thu cao nhất.

Kết luận rút ra rất rõ: **legal regulation đạt compliance cao hơn hẳn industry self-regulation**. Với studio US/EU, đây là tín hiệu dự báo — self-regulation đang thất bại một cách đo được, nên regulation cứng sẽ đến.

### 4.2 Cái mà bằng chứng KHÔNG hỗ trợ (chống overclaim)

Cần nói thẳng, vì đây là nơi phần lớn diễn ngôn công chúng sai:

1. **"Loot box gây ra problem gambling" — KHÔNG được chứng minh.** Gần như toàn bộ literature là **cross-sectional correlational**. Chiều nhân quả không xác định được: có thể loot box đẩy người ta vào gambling, có thể người sẵn có xu hướng gambling bị hút vào loot box, hoặc cả hai do một biến thứ ba. Nghiên cứu về "gateway hypothesis" tồn tại nhưng chưa kết luận được.
2. **"r = 0.26 là effect size lớn" — sai diễn giải.** Đó là small-to-moderate. Nó có ý nghĩa ở quy mô dân số, không có nghĩa là một người mua loot box thì có khả năng cao là problem gambler.
3. **"Loot box về mặt pháp lý là cờ bạc" — không đúng ở hầu hết tài phán quyền.** Bỉ và Hà Lan có động thái mạnh; hầu hết nơi khác thì loot box không thỏa mãn định nghĩa pháp lý của gambling do thiếu yếu tố **cash-out**. Sự tương đồng tâm lý (Drummond & Sauer) khác với sự tương đồng pháp lý.
4. **Self-report spending không đáng tin.** Có nghiên cứu so sánh phương pháp đo và chỉ ra rằng cách hỏi (screen trước hay không) thay đổi tương quan đo được — một dạng methodological artifact chưa được giải quyết trong literature.
5. **Đa số người mua loot box không gặp vấn đề gì.** Bất kỳ chính sách hay feature nào coi mọi người mua là nạn nhân đều sai về mặt thực nghiệm và sẽ bị player phản ứng.

**Vị thế trung thực để xây SDK trên đó `[Design synthesis]`:** liên hệ có thật, replicate tốt, độ lớn nhỏ-vừa, nhân quả chưa biết, **và tập trung rủi ro ở nhóm top-spender là điều đã đo được**. Điều đó đủ để biện minh cho duty of care nhắm vào nhóm chi tiêu cực đoan, và **không** đủ để biện minh cho việc coi mọi random reward là có hại.

---

## 5. Người chơi dễ tổn thương: nhận diện và duty of care

### 5.1 Công cụ đo chuẩn

**PGSI (Problem Gambling Severity Index)** — Ferris & Wynne (2001), thành phần của Canadian Problem Gambling Index. 9 item, thang 0–3, tổng 0–27 `[Primary research — peer-reviewed]`:

```text
0        Non-problem gambling
1–2      Low risk
3–7      Moderate risk
8+       Problem gambling
```

PGSI là thang được dùng trong hầu hết nghiên cứu loot box nêu ở §4, nên nó là ngôn ngữ chung để so sánh kết quả. Các thang liên quan: SOGS (cũ hơn), DSM-5 Gambling Disorder criteria, IGDS9-SF cho gaming disorder.

**Giới hạn quan trọng:** PGSI được thiết kế cho **cờ bạc bằng tiền có cash-out**. Việc áp thẳng vào chi tiêu game là một extension chưa được validate đầy đủ. Một số item (ví dụ về vay mượn để chơi) chuyển ngữ cảnh kém.

### 5.2 Nhận diện at-risk spender từ dữ liệu hành vi

Ngành gambling trực tuyến đã làm việc này hơn 15 năm và literature về **behavioural markers** khá phong phú. Các marker được dùng phổ biến trong hệ thống thương mại như Playscan (Svenska Spel) và Mentor (Neccton/bwin) `[Secondary]`:

| Marker | Nội dung | Có chuyển sang F2P không? |
|---|---|---|
| **Chasing losses** | Tăng cược ngay sau chuỗi thua | Có, dạng biến thể: tăng pull ngay sau chuỗi pull không trúng |
| **Night owling** | Chơi/nạp vào giờ đêm khuya bất thường | Có, chuyển thẳng, đo dễ |
| **Escalating deposits** | Xu hướng tăng đơn điệu của deposit size | Có, chuyển thẳng |
| **Session duration extremes** | Phiên dài bất thường so với baseline cá nhân | Có |
| **Deposit frequency spike** | Nhiều lần nạp trong một phiên | Có — đây là marker mạnh nhất và dễ đo nhất trong IAP |
| **Cancelled withdrawals** | Rút tiền rồi hủy để chơi tiếp | Không — F2P không có withdrawal |
| **Multiple payment methods** | Đổi phương thức thanh toán khi bị từ chối | Một phần — có thể quan sát qua failed purchase rồi retry |

**Về hiệu quả của can thiệp — phải trung thực:**
- Nghiên cứu đánh giá công cụ **Mentor** trên >1,000 online gambler cho thấy nhóm nhận personalised feedback chi tiêu **ít tiền và ít thời gian hơn** nhóm đối chứng, có ý nghĩa thống kê `[Primary research — peer-reviewed]`.
- Nghiên cứu định tính về **Playscan** (rating 3 màu xanh/vàng/đỏ dựa trên thuật toán độc quyền) cho thấy nhận thức của người dùng phức tạp: một số thấy hữu ích, một số không tin vào rating hoặc không hiểu nó dựa trên gì `[Primary research — peer-reviewed]`.
- Tổng quan literature: **nhiều feature responsible-gambling KHÔNG làm giảm chi tiêu hay giảm harm một cách đo được** `[Secondary]`. Đây là điểm cần nhắc lại với bất kỳ ai định bán "responsible monetization module" như một giải pháp đã được chứng minh.

### 5.3 Cái gì thực sự dịch được sang F2P

Xếp theo **bằng chứng hiệu quả × khả thi kỹ thuật** `[Design synthesis]`:

```text
DỊCH TỐT (nên đưa vào SDK)
  1. Spend limit tự đặt (self-imposed), có cooling-off khi nâng limit
     → cơ chế commitment device, cơ sở lý thuyết vững, chi phí thấp
  2. Personalised feedback định kỳ ("tháng này bạn đã chi $X, cao hơn 92%
     người chơi") — bản dịch trực tiếp của Mentor, có RCT hỗ trợ
  3. Hiển thị real-money equivalent tại điểm tiêu gem
  4. Session/spend velocity circuit breaker: tạm dừng offer targeting khi
     phát hiện deposit-frequency spike trong phiên

DỊCH KÉM / CẦN THẬN TRỌNG
  5. Risk scoring 3 màu kiểu Playscan → rủi ro false positive cao, player
     phản ứng tiêu cực khi bị gán nhãn, và không có bằng chứng giảm harm
  6. Self-exclusion → trong gambling là hạ tầng cấp quốc gia có hiệu lực
     pháp lý; trong F2P chỉ là setting trong 1 app, dễ vòng qua

KHÔNG DỊCH ĐƯỢC
  7. Affordability check (kiểm tra thu nhập) → không có cơ sở pháp lý,
     vi phạm privacy, và ở EU gần như chắc chắn vướng GDPR (xem doc 13)
```

### 5.4 Lập luận duty of care

Ba lập luận độc lập, mỗi cái đủ mạnh để đứng riêng `[Design synthesis]`:

1. **Lập luận thực nghiệm:** nếu ~5% top spender tạo ~50% doanh thu loot box và nhóm đó tập trung người có vấn đề cờ bạc (§4.1), thì mô hình doanh thu **về mặt cấu trúc phụ thuộc vào nhóm dễ tổn thương**. Không thể tuyên bố trung lập.
2. **Lập luận pháp lý dự phòng:** hướng đi của EU DFA và FTC (§10) làm cho các thực hành hiện tại có xác suất cao trở thành bất hợp pháp trong 2–4 năm. Xây duty-of-care sớm là quản trị rủi ro, không phải từ thiện.
3. **Lập luận LTV:** một whale bị burn out và churn vĩnh viễn (kèm refund, chargeback, review 1 sao) có LTV thấp hơn một whale chi tiêu ổn định 3 năm. Đây là lập luận yếu nhất về mặt bằng chứng công khai nhưng là lập luận dễ bán nhất nội bộ.

---

## 6. Tâm lý động cơ: Self-Determination Theory

### 6.1 SDT nền tảng

Deci & Ryan (1985; 2000, *Psychological Inquiry*) — con người có ba **nhu cầu tâm lý cơ bản** mà việc thỏa mãn chúng tạo ra động cơ nội tại và phúc lợi `[Primary research — peer-reviewed]`:

| Nhu cầu | Nội dung | Bị phá bởi |
|---|---|---|
| **Autonomy** | Cảm giác hành động xuất phát từ chính mình | Ép buộc, deadline nhân tạo, đường tiến độ duy nhất |
| **Competence** | Cảm giác hiệu quả, làm chủ | Độ khó sai, RNG lấn át kỹ năng, paywall giữa skill và kết quả |
| **Relatedness** | Cảm giác kết nối, được quan tâm | Cạnh tranh thuần túy, cô lập, guild toxic |

SDT phân biệt động cơ theo mức độ nội hóa, không phải nhị phân intrinsic/extrinsic: external regulation → introjected → identified → integrated → intrinsic.

### 6.2 SDT trong game (Rigby & Ryan)

Ryan, Rigby & Przybylski (2006, *Motivation and Emotion* — "The Motivational Pull of Video Games: A Self-Determination Theory Approach") mở rộng SDT sang game và tạo ra thang đo **PENS (Player Experience of Need Satisfaction)** `[Primary research — peer-reviewed]`. Phát hiện cốt lõi: autonomy và competence cảm nhận được trong game dự báo độc lập enjoyment, ý định chơi tiếp, và **thay đổi well-being trước–sau phiên chơi**. Rigby & Ryan sau đó viết *Glued to Games* (2011) `[Secondary]`.

**Lưu ý phê phán cần ghi:** có phê bình học thuật đáng kể trong cộng đồng HCI games về việc SDT bị áp dụng lỏng lẻo — dùng PENS như một checklist mà không kiểm định giả định nền, và trích SDT để biện minh cho thiết kế mà bản thân SDT không hỗ trợ. Một bài trên *ACM TOCHI* (2024) đặt vấn đề trực tiếp về việc này `[Primary research — peer-reviewed]`. Đừng dùng SDT như một con dấu đạo đức.

### 6.3 Overjustification effect — khi reward LÀM GIẢM engagement

Đây là phần quan trọng nhất của §6 cho monetization design, và là phần bị bỏ qua nhiều nhất.

**Deci, Koestner & Ryan (1999, *Psychological Bulletin*)** — meta-analysis 128 thí nghiệm về tác động của extrinsic reward lên intrinsic motivation `[Primary research — peer-reviewed]`. Kết quả tổng hợp: **phần thưởng vật chất hữu hình, được kỳ vọng trước, và gắn với việc tham gia hoạt động, làm SUY GIẢM động cơ nội tại** đối với hoạt động đó.

Cấu trúc hiệu ứng (quan trọng — không phải mọi reward đều xấu):

```text
LÀM SUY GIẢM intrinsic motivation:
  - Tangible + expected + engagement-contingent   ← mạnh nhất
  - Tangible + expected + completion-contingent
  - Tangible + expected + performance-contingent (nếu kiểm soát)

KHÔNG suy giảm / có thể tăng:
  - Verbal reward / positive feedback              ← tăng competence
  - Unexpected reward (không được hứa trước)
  - Task-noncontingent reward (không gắn với việc làm)
```

**Ánh xạ vào F2P — hệ quả phản trực giác `[Design synthesis]`:**

| Cơ chế | Dự đoán từ overjustification |
|---|---|
| Daily login reward hứa trước, giá trị cố định | Chuyển động cơ từ "tôi muốn chơi" sang "tôi phải nhận thưởng" — **rủi ro cao nhất** |
| Quest checklist bao phủ toàn bộ hoạt động trong game | Toàn bộ gameplay bị extrinsic-hóa; player chơi checklist, không chơi game |
| Surprise reward không báo trước | An toàn, thậm chí có lợi |
| Feedback về kỹ năng ("bạn đã cải thiện accuracy 12%") | Tăng competence, tăng intrinsic |
| Rút phần thưởng ra khỏi một hoạt động đã reward lâu ngày | **Engagement rơi xuống DƯỚI baseline ban đầu** — đây là cái bẫy |

Điểm cuối cùng đáng nhấn mạnh: overjustification tạo ra một **hiệu ứng bánh cóc (ratchet)**. Một khi bạn extrinsic-hóa một hoạt động, bạn không thể quay lại miễn phí. Đây là lời giải thích lý thuyết cho hiện tượng quen thuộc: game thêm daily quest → DAU tăng → 18 tháng sau bỏ daily quest vì "player mệt mỏi" → DAU sụp xuống dưới mức trước khi thêm.

**Giới hạn honesty:** meta-analysis của Deci et al. gây tranh cãi lớn với Cameron & Pierce, những người re-analysis cùng dữ liệu và tìm thấy hiệu ứng nhỏ hơn nhiều với điều kiện hẹp hơn. Tranh luận này chưa bao giờ được giải quyết dứt điểm. Hiệu ứng **có tồn tại trong điều kiện lab với hoạt động vốn đã thú vị**; mức độ nó chuyển sang môi trường game thương mại thì chưa ai đo được `[Contested]`.

---

## 7. Habit formation

### 7.1 Khoa học habit (Wood & Neal)

Wood & Neal (2007, *Psychological Review* — "A New Look at Habits and the Habit–Goal Interface") `[Primary research — peer-reviewed]`: habit là liên kết **context → response** được học qua lặp lại, hoạt động **bán tự động** và **không cần mục tiêu ý thức** để kích hoạt. Điều then chốt: một khi habit hình thành, thay đổi mục tiêu **không** thay đổi hành vi — chỉ thay đổi **context** mới làm được.

Ba hàm ý:
1. Habit gắn với **cue ngữ cảnh ổn định** (giờ trong ngày, vị trí, hành động trước đó) bền hơn habit gắn với reward.
2. Habit khó phá bằng ý chí — điều này có ý nghĩa đạo đức: một player "muốn dừng" nhưng vẫn mở app hàng ngày không phải là người thiếu ý chí, mà là người có habit mà môi trường vẫn đang trigger.
3. Con số "21 ngày để tạo thói quen" là **huyền thoại** — nghiên cứu của Lally et al. (2010) tìm thấy median ~66 ngày, phân tán từ 18 đến 254 ngày `[Primary research — peer-reviewed]`.

### 7.2 Hooked model (Eyal)

Nir Eyal, *Hooked: How to Build Habit-Forming Products* (2014) `[Secondary]` — mô hình bốn bước: **Trigger → Action → Variable Reward → Investment**.

Đánh giá trung thực: Hooked là **tổng hợp practitioner, không phải nghiên cứu gốc**. Nó lắp ghép Fogg Behavior Model (B = MAP: Motivation, Ability, Prompt), variable ratio schedule của Skinner, và IKEA/endowment effect. Giá trị của nó là ngôn ngữ chung và tính hành động; nó **không mang bằng chứng thực nghiệm riêng**. Trích Hooked như "nghiên cứu cho thấy" là sai.

Điểm đáng chú ý về mặt thiết kế: bước **Investment** (bắt user bỏ công/dữ liệu vào sản phẩm) là bước làm cho trigger vòng sau mạnh hơn — chính là endowment + sunk cost + IKEA effect gộp lại. Đây là bước có sức nặng đạo đức lớn nhất trong bốn bước.

### 7.3 Streak: nơi habit gặp loss aversion

Streak mechanic là hợp lưu của ba lực đã nói ở trên:

```text
Streak day N tạo ra:
  (1) Reference point mới ở mức N            [prospect theory §1.1]
  (2) Mất streak = LOSS, không phải forgone gain  [loss aversion §1.1]
  (3) Sunk cost N ngày đã bỏ ra              [§1.4]
  (4) Cue ngữ cảnh cố định hàng ngày         [habit §7.1]

⇒ Áp lực tâm lý tăng ĐƠN ĐIỆU theo N và không có trần.
```

Đây là điểm phân biệt streak với hầu hết cơ chế khác: **chi phí tâm lý của việc dừng lại tăng theo thời gian tham gia, vô hạn**. Một player ở streak ngày 400 chịu áp lực định tính khác hoàn toàn player ở ngày 4.

**Thang đo đạo đức cho streak `[Design synthesis]`:**

| Thiết kế | Đánh giá |
|---|---|
| Streak thuần cosmetic, không mất tiến độ khi đứt | Chấp nhận được |
| Streak với streak-freeze miễn phí tích lũy được | Chấp nhận được — đây là thiết kế tốt nhất hiện có |
| Streak mà thưởng tăng lũy tiến, mất hết khi đứt | Grey zone — áp lực tăng không trần |
| **Bán "streak restore" bằng tiền thật** | **Vượt lằn ranh** — biến loss aversion trực tiếp thành doanh thu, và mức sẵn lòng trả tỉ lệ thuận với độ dài streak, tức tỉ lệ thuận với mức độ dễ tổn thương |
| Streak reset lúc nửa đêm timezone máy chủ, không cảnh báo | Vượt lằn ranh — cưỡng chế thời điểm |

Dòng "bán streak restore" đáng được nhấn mạnh: đó là một trong số ít cơ chế mà **giá sẵn lòng trả tăng theo chính mức độ ràng buộc tâm lý mà sản phẩm đã tạo ra**. Nó có cấu trúc của một khoản tiền chuộc.

---

## 8. Social influence

Cialdini (*Influence*, 1984/2021) hệ thống hóa sáu nguyên tắc `[Secondary]`. Ba nguyên tắc liên quan trực tiếp đến chi tiêu game:

### 8.1 Social proof

Bằng chứng nền tảng vững (Asch conformity, và quan trọng hơn là các field experiment quy mô lớn về tiết kiệm năng lượng — Schultz et al., 2007 và các RCT của OPOWER trên hàng triệu hộ gia đình, cho thấy descriptive norm messaging có hiệu ứng thật nhưng **nhỏ**, cỡ 1–2% thay đổi hành vi) `[Primary research — peer-reviewed]`.

Ánh xạ game: "87% người chơi ở level của bạn đã mua pack này". Vấn đề: **con số này thường bịa**. Nếu bịa, đó là false belief creation theo phân loại FTC (§10) và là vi phạm rõ ràng. Nếu thật, nó là social proof hợp pháp — nhưng effect size thực tế nhỏ hơn nhiều so với kỳ vọng.

Đáng chú ý: nghiên cứu norm messaging cũng phát hiện **boomerang effect** — người đang ở dưới mức trung bình sẽ tăng lên, nhưng người đang ở trên trung bình sẽ **giảm xuống** về phía norm. Áp dụng vào chi tiêu: hiển thị "chi tiêu trung bình" cho whale có thể **giảm** chi tiêu của họ. Đây chính là cơ sở của can thiệp personalised feedback ở §5.3 — và giải thích vì sao không studio nào tự nguyện làm.

### 8.2 Competitive comparison & guild pressure

Leaderboard và guild tạo ra một dạng áp lực khác về chất so với social proof: **relatedness need (§6.1) bị biến thành đòn bẩy**.

```text
Cơ chế: guild yêu cầu đóng góp tối thiểu → thành viên yếu bị đẩy vào lựa chọn
        (a) chi tiền để đạt ngưỡng, hoặc
        (b) mất quan hệ xã hội đã đầu tư nhiều tháng

⇒ Cost of not paying = social loss, không phải game loss.
```

Đây là cơ chế monetization mạnh nhất tồn tại trong mobile, và cũng là cơ chế **ít bị điều tiết nhất** vì nó không có giao dịch nào trực tiếp mang tính lừa dối. Không có nghiên cứu học thuật định lượng nào tôi tìm được đo trực tiếp guild pressure → spending trong game thương mại.

**Đánh giá `[Design synthesis]`:** guild content mà tiến độ tập thể **cộng dồn theo effort** (thời gian chơi) là fair. Guild content mà tiến độ tập thể **cộng dồn theo chi tiêu** biến mỗi thành viên thành nhân viên bán hàng cho studio đối với chính bạn bè mình. Đó là ranh giới.

### 8.3 Gifting & reciprocity

Reciprocity norm (Gouldner, 1960) — nhận quà tạo nghĩa vụ đáp lại `[Primary research — peer-reviewed]`. Trong game:
- **Gift giữa player** (năng lượng, vật phẩm) tăng relatedness, chi phí bằng 0 → tích cực rõ ràng.
- **Gift từ studio cho player** ("chúng tôi tặng bạn 100 gem") tạo nghĩa vụ tâm lý một chiều. Nếu 100 gem đó vừa đủ để **thiếu** cho một món giá 300 gem đang giảm giá, cấu trúc này là một dạng partitioned coercion — quà là mồi cho residual balance (§2.2).
- **Cho phép player mua quà tặng người khác** — nhìn bề ngoài tích cực, nhưng chuyển áp lực chi tiêu sang mạng xã hội của player.

---

## 9. Pricing psychology — và sự thật về replication

Đây là phần mà tôi phải phá vỡ nhiều niềm tin nhất. Bối cảnh chung: Open Science Collaboration (2015, *Science*) replicate 100 nghiên cứu tâm lý, **chỉ ~1/3 replicate thành công**, effect size trung bình bằng **một nửa** bản gốc. Camerer et al. (2016, *Science*) replicate 18 thí nghiệm kinh tế học đăng trên AER/QJE: **12/18 replicate**, effect size trung bình ~66% bản gốc `[Primary research — peer-reviewed]`.

Không có lý do gì để tin rằng các hiệu ứng pricing được ngành marketing yêu thích lại miễn nhiễm.

### 9.1 Bảng đánh giá từng hiệu ứng

| Hiệu ứng | Nội dung | Trạng thái bằng chứng | Dùng được không? |
|---|---|---|---|
| **Charm pricing** ($9.99 vs $10) | Left-digit effect | Có bằng chứng field thật (Anderson & Simester 1998 field experiment với catalogue), nhưng **hiệu ứng không nhất quán**, đôi khi đảo dấu ở sản phẩm cao cấp | Dùng được, kỳ vọng khiêm tốn, **phải A/B test** |
| **Anchoring** | Số đầu tiên nhìn thấy neo phán đoán sau | **`[Contested]` về độ lớn.** Replication 2025 trên *Economic Inquiry* chỉ ra thiết kế gốc thường có power < 30%; khi tăng power lên 96%, effect size ước lượng gốc bị bác bỏ. Hiệu ứng **tồn tại** nhưng nhỏ hơn nhiều | Dùng như prior yếu, không xây chiến lược pricing lên trên |
| **Decoy effect / asymmetric dominance** | Thêm option bị trội để đẩy lựa chọn sang option đích | **`[Contested]` nặng.** Huber, Payne & Puto (1982) là gốc; hiệu ứng **phần lớn biến mất hoặc đảo chiều** khi ra ngoài paradigm lab hai thuộc tính trừu tượng. Với sản phẩm thật, nhiều thuộc tính, người thật — không replicate ổn định | **Không nên** xây pricing page dựa vào decoy như một cơ chế đã biết |
| **Compromise / middle-option effect** | Người chọn option giữa | Simonson (1989). Bằng chứng khá hơn decoy, nhưng vẫn phụ thuộc mạnh context và phạm vi giá | Dùng được thận trọng, test |
| **Bundle framing** | Gộp nhiều item vào một giá | Nền lý thuyết mental accounting vững (integrate losses, §1.2). Hiệu ứng thực tế phụ thuộc rất mạnh vào việc player có muốn TẤT CẢ item trong bundle không | Dùng được; nền lý thuyết vững hơn bằng chứng thực nghiệm cụ thể |
| **Partitioned pricing** | Tách giá thành nhiều phần ($8 + $2 ship vs $10) | Có bằng chứng, nhưng **hiệu ứng phụ thuộc mạnh vào việc phần phụ có được xử lý cẩn thận không**; ở EU, che phí bắt buộc là **vi phạm pháp luật** bất kể hiệu quả | **Không dùng** ở EU nếu phần tách ra là phí bắt buộc |
| **Scarcity / time pressure** | Countdown, "chỉ còn 3" | Hiệu ứng có thật. **Vấn đề không phải hiệu quả mà là tính chân thực**: countdown giả reset khi mở lại app là false belief creation, đã bị FTC và CPC Network nhắm tới trực tiếp | Chỉ dùng khi khan hiếm là THẬT |
| **Social priming** | Cue môi trường tinh vi thay đổi hành vi | **`[Contested]` — đây là poster child của replication crisis.** Không dùng | Không |
| **Ego depletion** | Ý chí là nguồn lực cạn kiệt | **`[Contested]`.** Multi-lab replication 23 phòng thí nghiệm không tìm thấy hiệu ứng cốt lõi. Mọi lập luận kiểu "bán vào cuối phiên khi player đã cạn ý chí" **không có cơ sở** | Không |

### 9.2 Quy tắc thực dụng rút ra `[Design synthesis]`

1. **Hiệu ứng có nền lý thuyết vững (prospect theory, mental accounting, reinforcement schedule) đáng tin hơn hiệu ứng chỉ có một thí nghiệm lab nổi tiếng.**
2. **Mọi hiệu ứng pricing phải được A/B test trên chính traffic của bạn.** Điều này không phải lời khuyên chung chung — nó là hệ quả trực tiếp của việc effect size trung bình sau replication chỉ bằng một nửa. Prior của bạn về bất kỳ hiệu ứng pricing nào nên là "một nửa những gì bài báo nói, có thể bằng 0".
3. **Nếu một hiệu ứng chỉ hoạt động khi player hiểu sai điều gì đó, nó vừa là rủi ro replication vừa là rủi ro pháp lý.** Hai loại rủi ro này tương quan cao — không phải ngẫu nhiên.

---

## 10. Rubric phân định ranh giới đạo đức

### 10.1 Neo pháp lý

**FTC — "Bringing Dark Patterns to Light" (tháng 9/2022)** `[Secondary]` phân loại bốn nhóm dark pattern bất hợp pháp:
1. Tạo niềm tin sai (false belief creation)
2. Che giấu thông tin trọng yếu (hiding material information)
3. Gây ra khoản phí không được ủy quyền (unauthorized charges)
4. Lừa lấy dữ liệu (tricking data sharing)

**FTC v. Epic Games** `[Secondary]`: dàn xếp **$245 triệu** vì dùng dark pattern khiến người chơi phát sinh giao dịch ngoài ý muốn — cấu hình nút bấm phản trực giác, không nhất quán, gây mua nhầm chỉ bằng một lần nhấn; và cho phép trẻ em mua V-Bucks mà không cần xác nhận của cha mẹ. Tính đến 6/2025 FTC đã hoàn gần **$200 triệu** cho gần một triệu người chơi. Vụ này là **tiền lệ hoạt động**: nó không phạt vì bán đồ đắt, mà phạt vì **kiến trúc lựa chọn**.

**EU Digital Fairness Act** `[Secondary]`: đề xuất chính thức của Ủy ban châu Âu **dự kiến nửa cuối 2026**, thông qua khó trước 2027. Phạm vi dự kiến gộp dark pattern, addictive design, loot box, virtual currency, influencer marketing và personalised pricing bất công vào một khung. Các nội dung được thảo luận: **buộc hiển thị giá trị tiền thật bên cạnh virtual currency**, hạn chế loot box (từ yêu cầu parental consent cho trẻ vị thành niên đến cấm hoàn toàn ở phương án cực đoan nhất mà nhóm bảo vệ người tiêu dùng như BEUC đề xuất). **Cảnh báo:** đây là quá trình lập pháp đang diễn ra; đừng thiết kế theo một dự thảo cụ thể, hãy thiết kế theo **hướng** của nó.

### 10.2 Rubric ba vùng

Không có tiêu chí đơn lẻ nào phân định được. Rubric dưới đây dùng **năm câu hỏi chẩn đoán**; câu trả lời quyết định vùng `[Design synthesis]`:

```text
CÂU HỎI CHẨN ĐOÁN

Q1. TRANSPARENCY — Người chơi có biết TỔNG chi phí kỳ vọng để đạt được
    kết quả họ đang theo đuổi không, TRƯỚC khi bắt đầu?
    (Đây chính là tiêu chí King & Delfabbro 2018)

Q2. REVERSIBILITY — Nếu người chơi dừng lại và suy nghĩ 24 giờ, họ có
    mất gì không? Nếu có, cái mất đó do khan hiếm THẬT hay do thiết kế?

Q3. SYMMETRY — Cơ chế này có hoạt động tốt như nhau với player hiểu rõ
    nó và player không hiểu không? Nếu hiệu quả của nó PHỤ THUỘC vào
    việc player hiểu sai, đó là manipulation theo định nghĩa.

Q4. TARGETING — Cường độ của cơ chế có tăng theo mức độ dễ tổn thương
    của người chơi (lịch sử chi tiêu, thời điểm, độ dài streak) không?

Q5. STATED-PREFERENCE ALIGNMENT — Nếu hỏi thẳng người chơi "bạn có muốn
    hệ thống làm việc này với bạn không", đa số trả lời có?
```

**Vùng A — Legitimate persuasion.** Q1 = có, Q2 = khan hiếm thật hoặc không mất gì, Q3 = đối xứng, Q4 = không, Q5 = có.

```text
Ví dụ:
  - Battle pass giá cố định, nội dung công khai đầy đủ trước khi mua
  - Skin bán giá cố định, không random
  - Starter pack một lần cho người mới, giá trị hiển thị minh bạch
  - Giảm giá theo mùa có thật, hết hạn thật
  - Rewarded ad: đổi thời gian lấy giá trị, tỉ giá rõ ràng
```

**Vùng B — Grey zone.** Ít nhất một câu hỏi trả lời không thuận lợi, nhưng cơ chế vẫn cung cấp giá trị thật và không dựa vào hiểu lầm.

```text
Ví dụ:
  - Gacha có rate disclose đầy đủ + pity ceiling rõ
    (Q1 một phần: expected cost tính được, nhưng probability weighting
     §1.1 nghĩa là player vẫn hiểu sai — chưa vượt lằn ranh nhưng
     không phải Vùng A)
  - Limited-time offer với khan hiếm thật (Q2 căng)
  - Streak với reward lũy tiến, không bán restore (Q2, Q4 căng)
  - Currency layering có hiển thị real-money equivalent
  - Guild event mà tiến độ tập thể phụ thuộc một phần vào chi tiêu

Nguyên tắc xử lý Vùng B: được phép, nhưng phải kèm biện pháp bù —
disclosure mạnh hơn, spend cap, hoặc cả hai. Và phải được review
định kỳ vì hành lang pháp lý đang siết.
```

**Vùng C — Manipulation. Không dùng.**

```text
  - Rate ẩn, hoặc rate thay đổi theo profile chi tiêu của player
  - Countdown giả / reset khi mở lại app
  - Social proof với số liệu bịa
  - Bán "streak restore" hoặc "progress restore" bằng tiền thật
  - Offer targeting tăng cường độ khi phát hiện dấu hiệu at-risk
    (đây là EOMM áp dụng vào vulnerability — xem doc 14)
  - Kiến trúc nút bấm gây mua nhầm  → tiền lệ Epic, bất hợp pháp
  - Che chi phí bắt buộc khỏi giá hiển thị → bất hợp pháp ở EU
  - Trao item rồi thu hồi và bán lại quyền dùng
  - Loot box cho tài khoản đã xác định là trẻ vị thành niên
```

### 10.3 Kiểm tra thực dụng cuối cùng

Ba phép thử nhanh dùng khi rubric không dứt điểm `[Design synthesis]`:

1. **Publication test.** Nếu cơ chế này được mô tả đầy đủ và chính xác trong một bài báo của Eurogamer/The Verge, phản ứng sẽ là gì? Đây là phép thử tốt vì nó đo được đúng thứ mà Q3 đo: liệu hiệu quả có phụ thuộc vào việc người chơi không biết.
2. **Whale composition test.** Nhìn vào top 1% người chi tiêu tháng trước. Nếu tỉ lệ đáng kể trong số họ có mẫu hành vi khớp với behavioural marker ở §5.2 (chasing, night owling, deposit spike), thì doanh thu của bạn đang **phụ thuộc vào harm**, bất kể từng cơ chế riêng lẻ có nằm ở Vùng A hay không. Đây là phép thử ở tầng hệ thống, không phải tầng feature.
3. **Regulatory horizon test.** Cơ chế này có xuất hiện trong danh mục thảo luận của EU DFA hoặc trong bốn nhóm của FTC không? Nếu có, thời hạn sử dụng của nó được đo bằng năm, không phải bằng thập kỷ.

---

## Không xác minh được (ghi rõ)

Những điều tôi **không** xác minh được và do đó **không** đưa ra như sự thật trong doc này:

1. **Elasticity định lượng riêng cho từng tầng currency layering.** Không tìm thấy nghiên cứu peer-reviewed công khai nào đo được co giãn chi tiêu ở tầng gem→box tách khỏi tầng tiền thật→gem trong dữ liệu game thương mại. Các con số kiểu "currency layering tăng ARPPU X%" đều là self-reported của vendor.
2. **Hệ số loss aversion λ áp dụng cho khoản chi tiêu nhỏ trong game.** λ = 2.25 là ước lượng cho một paradigm cụ thể với stakes cụ thể. Không có ước lượng nào cho microtransaction $0.99–$9.99.
3. **Effect size định lượng của guild pressure lên chi tiêu.** Không tìm thấy nghiên cứu học thuật nào đo trực tiếp. Đây là một trong những lỗ hổng lớn nhất của literature so với tầm quan trọng thực tế của cơ chế.
4. **Tỉ lệ chuyển đổi của các can thiệp responsible-monetization sang F2P.** Bằng chứng RCT tồn tại cho online gambling (Mentor), không tồn tại cho F2P game. Việc dịch sang F2P là suy luận, không phải bằng chứng.
5. **Meta-analysis effect size cho SDT/PENS trong game.** Tôi tìm được nghiên cứu nền tảng và thang đo, nhưng không xác minh được một con số meta-analytic tổng hợp; do đó không trích số nào.
6. **Nội dung chính xác của EU Digital Fairness Act.** Tính đến 2026-08-21, đề xuất chính thức chưa được công bố (dự kiến nửa cuối 2026). Mọi mô tả trong §10.1 là về **phạm vi đang được thảo luận** dựa trên tường thuật của các hãng luật và position paper của BEUC, không phải văn bản luật.
7. **Tình trạng giải quyết của tranh luận Deci et al. vs Cameron & Pierce về overjustification.** Tôi mô tả tranh luận tồn tại nhưng không xác minh được có consensus hiện tại nào.
8. **Trạng thái peer-review của một số nguồn ở phần Nguồn chính** được đánh dấu SSRN/ResearchGate — các bản này có thể là preprint hoặc bản đã đăng; tôi không xác minh từng cái.

---

## Nguồn chính

- Video game loot boxes are linked to problem gambling: Results of a large-scale survey (Zendle & Cairns, PLOS ONE 2018) — https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0206767
- Loot boxes are again linked to problem gambling: Results of a replication study (Zendle & Cairns, PLOS ONE 2019) — https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0213194
- Loot boxes are again linked to problem gambling: replication study (PMC full text) — https://pmc.ncbi.nlm.nih.gov/articles/PMC6405116/
- Meta-analysis of the relationship between problem gambling, excessive gaming and loot box spending (Garea, Drummond, Sauer, Hall & Williams, 2021) — https://www.researchgate.net/publication/351107695_Meta-analysis_of_the_relationship_between_problem_gambling_excessive_gaming_and_loot_box_spending
- Video game loot boxes are psychologically akin to gambling (Drummond & Sauer, Nature Human Behaviour 2018) — https://www.nature.com/articles/s41562-018-0360-1
- Predatory monetization schemes in video games (e.g. 'loot boxes') and internet gaming disorder (King & Delfabbro, Addiction 2018) — https://onlinelibrary.wiley.com/doi/full/10.1111/add.14286
- Secondary analysis of loot box data: Are high-spending "whales" wealthy gamers or problem gamblers? (Addictive Behaviors 2021) — https://www.sciencedirect.com/science/article/abs/pii/S0306460321000368
- Secondary analysis of loot box data (bản toàn văn, University of Wolverhampton repository) — https://wlv.openrepository.com/server/api/core/bitstreams/043d3d5c-06a7-4c8c-9ee0-48bb41401317/content
- Loot boxes and problem gambling: Investigating the "gateway hypothesis" (Addictive Behaviors 2022) — https://www.sciencedirect.com/science/article/pii/S0306460322000934
- Adolescents and loot boxes: links with problem gambling and motivations for purchase (Royal Society Open Science 2019) — https://royalsocietypublishing.org/rsos/article/6/6/190049/94826/Adolescents-and-loot-boxes-links-with-problem
- To screen, or not to screen: An experimental comparison of two methods for correlating loot box expenditure and problem gambling severity (Computers in Human Behavior 2023) — https://www.sciencedirect.com/science/article/pii/S0747563223003709
- Lifting the Lid on Loot-Boxes: Chance-Based Purchases in Video Games (GambleAware) — https://www.gambleaware.org/media/egljnu4x/gaming_and_gambling_report_final_0.pdf
- What are the odds? Lower compliance with Western loot box probability disclosure industry self-regulation than Chinese legal regulation (Xiao, Henderson & Newall, SSRN) — https://doi.org/10.2139/ssrn.3934941
- Gaming the system: suboptimal compliance with loot box probability disclosure regulations in China (Xiao et al., Behavioural Public Policy, Cambridge Core) — https://www.cambridge.org/core/journals/behavioural-public-policy/article/gaming-the-system-suboptimal-compliance-with-loot-box-probability-disclosure-regulations-in-china/B2642E2F8B7164236E5477D58D2B26DE
- What are the odds? Poor compliance with UK loot box probability disclosure industry self-regulation (PLOS ONE 2023) — https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0286681
- Incomplete Compliance: Loot Box Prevalence, Probability Disclosure, and Compu Gacha Policy in Japanese Mobile Games (CityUHK Scholars) — https://scholars.cityu.edu.hk/en/publications/incomplete-compliance-loot-box-prevalence-probability-disclosure-/
- Leon Y. Xiao — Working Papers (danh mục nghiên cứu regulatory compliance loot box) — https://sites.google.com/view/leon-xiao/publications/working-papers
- Virtual currencies in online gaming increase the willingness to pay for loot boxes: an experimental analysis (Experimental Economics, Cambridge Core) — https://www.cambridge.org/core/journals/experimental-economics/article/virtual-currencies-in-online-gaming-increase-the-willingness-to-pay-for-loot-boxes-an-experimental-analysis/BA5773D9B866AF75DF978A1410E37B1F
- The affective cost of spending: neuroeconomic and experimental evidence on the pain of paying (Journal of Economic Behavior & Organization) — https://www.sciencedirect.com/science/article/pii/S0167268126002581
- Results of a massive experiment on virtual currency endowments and money demand (PMC) — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5646808/
- Assessing the effectiveness of a responsible gambling behavioural feedback tool for reducing the gambling expenditure of at-risk players (International Gambling Studies — nghiên cứu công cụ Mentor) — https://www.tandfonline.com/doi/full/10.1080/14459795.2015.1049191
- Gamblers' Perception of the Playscan Risk Assessment: A Mixed-Methods Study (PMC) — https://pmc.ncbi.nlm.nih.gov/articles/PMC9119887/
- Experiences of Playscan: Interviews with users of a responsible gambling tool (PubMed) — https://pubmed.ncbi.nlm.nih.gov/30135829/
- Modeling Early Gambling Behavior Using Indicators from Online Lottery Gambling Tracking Data: Longitudinal Analysis (PMC) — https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7450385/
- The Motivational Pull of Video Games: A Self-Determination Theory Approach (Ryan, Rigby & Przybylski, Motivation and Emotion 2006) — https://link.springer.com/article/10.1007/s11031-006-9051-8
- Player Experience of Needs Satisfaction (PENS) — selfdeterminationtheory.org — https://selfdeterminationtheory.org/player-experience-of-needs-satisfaction-pens/
- A Motivational Model of Video Game Engagement (Przybylski, Rigby & Ryan, Review of General Psychology 2010) — https://selfdeterminationtheory.org/SDT/documents/2010_PrzybylskiRigbyRyan_ROGP.pdf
- Self-Determination Theory and HCI Games Research: Unfulfilled Promises and Unquestioned Paradigms (ACM TOCHI 2024 — phê bình việc áp dụng SDT) — https://dl.acm.org/doi/10.1145/3673230
- Underpowered studies and exaggerated effects: A replication and re-evaluation of the magnitude of anchoring effects (Economic Inquiry 2025) — https://onlinelibrary.wiley.com/doi/full/10.1111/ecin.13279
- The Replication Crisis Hub: Famous Behavioral Science Studies and What Actually Survives (Atticus Li) — https://atticusli.com/replication-crisis/
- Behavioral Economics After the Replication Crisis: Trust But Verify (Atticus Li) — https://atticusli.com/blog/posts/behavioral-economics-replication-crisis-trust-verify/
- The Decoy Effect: The Pricing-Page Tactic That Doesn't Replicate (Atticus Li) — https://atticusli.com/replication-crisis/decoy-effect-asymmetric-dominance/
- FTC Finalizes Order Requiring Fortnite maker Epic Games to Pay $245 Million for Tricking Users into Making Unwanted Charges (FTC) — https://www.ftc.gov/news-events/news/press-releases/2023/03/ftc-finalizes-order-requiring-fortnite-maker-epic-games-pay-245-million-tricking-users-making
- $245 million FTC settlement alleges Fortnite owner Epic Games used digital dark patterns (FTC Business Guidance Blog) — https://www.ftc.gov/business-guidance/blog/2022/12/245-million-ftc-settlement-alleges-fortnite-owner-epic-games-used-digital-dark-patterns-charge
- Dark Patterns Defined: Examining FTC Enforcement and Developing Best Practices (NAI — tóm tắt "Bringing Dark Patterns to Light") — https://thenai.org/wp-content/uploads/2023/05/Dark-Patterns-Defined_Examining-FTC-Enforcement-and-Developing-Best-Practices.pdf
- The EU's proposed Digital Fairness Act: A game developer's guide to potential implications (Freshfields) — https://www.freshfields.com/en/our-thinking/blogs/technology-quotient/the-eus-proposed-digital-fairness-act-a-game-developers-guide-to-potential-imp-102ltio
- From Dark Patterns to Fair Play: How the Digital Fairness Act Could Redefine Digital Consumer Protection (Goodwin) — https://www.goodwinlaw.com/en/insights/publications/2025/11/alerts-practices-antc-from-dark-patterns-to-fair-play
- Towards the Digital Fairness Act — Position paper (BEUC, 12/2025) — https://www.beuc.eu/sites/default/files/publications/BEUC-X-2025-110_Towards_the_Digital_Fairness_Act.pdf
- European Commission assesses in-game purchases, loot boxes and engagement techniques as part of 'Digital Fairness Fitness Check' on EU consumer law (Lexology) — https://www.lexology.com/library/detail.aspx?g=2c0c453f-a6fb-4a4a-91f0-39f7613aa380
- Digital Fairness Act (DFA) — Updates tracker — https://www.digital-fairness-act.com/
- Needs, Passions and Loot Boxes — Exploring Reasons for Problem Behaviour in Relation to Loot Box Engagement (arXiv preprint) — https://arxiv.org/pdf/2307.04549
- Using Psychophysiological Insights to Evaluate the Impact of Loot Boxes on Arousal (arXiv preprint) — https://arxiv.org/pdf/2507.04906
- Deceptively Framed Lotteries in Consumer Markets (arXiv preprint) — https://arxiv.org/pdf/2511.01597
