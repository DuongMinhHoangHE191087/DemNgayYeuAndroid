# 30 — Design craft kinh tế & monetization: công thức, con số, checklist

**Ngày nghiên cứu:** 2026-08-21
**Phạm vi:** Phần **thủ công thiết kế** (design craft) của game economy & monetization — tức là những con số, công thức và quy tắc mà một economy designer / monetization PM thực sự gõ vào spreadsheet. Không lặp lại phần chiến lược, vendor landscape, attribution hay kiến trúc SDK (đã có ở doc 01–27). Trọng tâm thị trường US/EU: mọi ràng buộc pháp lý nêu trong doc này là ràng buộc của FTC, EU CPC Network và policy của App Store/Google Play, không phải của thị trường châu Á.

> **Cách đọc doc này:** mỗi con số đều có nhãn bằng chứng. Ngành game F2P có một đặc tính khó chịu: rất nhiều "benchmark" được lặp lại hàng nghìn lần trên blog vendor mà không truy được về một primary source nào. Doc này **phân biệt rạch ròi** giữa (a) kết quả đo được có nguồn gốc, (b) rule-of-thumb ngành, và (c) suy dẫn toán học của chính tôi. Khi một con số phổ biến không truy được nguồn, tôi ghi rõ ở mục "Không xác minh được" thay vì trích dẫn nó như sự thật.

### Quy ước nhãn bằng chứng

| Nhãn | Nghĩa |
|---|---|
| `[Primary research]` | Nghiên cứu học thuật / dataset gốc, có peer review hoặc dữ liệu công khai kiểm chứng được |
| `[GDC talk / practitioner]` | Practitioner công bố công khai (GDC, sách, blog của người trong nghề có tên) |
| `[Market data]` | Nhà cung cấp dữ liệu thị trường (GameRefinery, Sensor Tower, Swrve…) — số của họ, phương pháp thường không công khai đầy đủ |
| `[Case study — self-reported]` | Studio/vendor tự báo cáo, không kiểm chứng độc lập |
| `[Design synthesis]` | Suy dẫn / mô hình hóa của chính tôi trong doc này. Tự kiểm chứng được bằng cách chạy lại phép tính |
| `[Rule of thumb]` | Quy tắc ngón tay cái ngành, không có phép đo gốc — dùng làm prior, không dùng làm bằng chứng |

---

## 1. Nền tảng kinh tế: source/sink, velocity, inflation

### 1.1 Từ vựng chuẩn

Bộ từ vựng chuẩn hóa nhất hiện có là của Charlie Czerkawski trong *Game Economy Design: Metagame, Monetization and Live Operations* (CRC Press, 2024) `[GDC talk / practitioner]`:

```text
ENTITIES (cái gì chảy)
  Currency   — soft (kiếm được qua gameplay) / hard (mua bằng tiền thật)
  Resource   — nguyên liệu, tồn tại để phục vụ một hệ thống cụ thể (crafting mats)
  Good       — vật phẩm hữu hình player sở hữu (weapon, armor, skin)

MECHANICS (chảy như thế nào)
  Source / Faucet   — TẠO RA entity từ hư không, đưa vào economy
  Sink   / Drain    — HỦY entity vĩnh viễn, rút khỏi economy
  Converter         — biến entity A thành entity B (crafting) — vừa sink vừa source
  Trader            — trao đổi giữa hai chủ thể, KHÔNG tạo và KHÔNG hủy tổng lượng
```

Phân biệt Converter và Trader là chỗ hay bị nhầm nhất và cũng là chỗ hay gây lỗi cân bằng: một crafting system là **converter** (hủy 10 gỗ, tạo 1 kiếm — tổng giá trị thay đổi), còn player-to-player market là **trader** (tổng lượng vàng trong game không đổi). Nếu spreadsheet của bạn xử lý trading như một sink, bạn đang tính thừa sink và economy sẽ lạm phát trong live.

Taxonomy source/sink hay dùng `[Rule of thumb]`:

```text
5 loại SOURCE                          4 loại SINK
  Capped     — giới hạn cứng/ngày        Fixed        — giá cố định, mua 1 lần
               (daily reward)                          (unlock slot)
  Trickle    — chảy đều theo thời gian   Repeatable   — mua đi mua lại
               (energy regen, idle)                    (consumable, revive)
  Grind      — tỉ lệ thuận effort        Exponential  — giá tăng theo cấp
               (level clear reward)                    (upgrade cost lv^n)
  Investment — trả trước, thu về sau      Competitive — trả để hơn người khác
               (building, staking)                     (bid, tournament entry)
  Random     — drop ngẫu nhiên
               (loot, gacha)
```

Điểm thiết kế quan trọng: **Exponential sink là loại duy nhất tự động chống lạm phát**. Các loại khác đều có trần hấp thụ cố định; khi player mid/late-game giàu lên, chỉ exponential sink mới nuốt kịp. Đó là lý do gần như mọi game F2P có upgrade cost dạng `cost(n) = base · k^n` với `k ∈ [1.15, 1.6]`.

### 1.2 Phương trình cân bằng source–sink

Đây là phương trình cốt lõi. Với một currency `c`, tại thời điểm `t`, cho một segment player `s`:

```text
Stock_c(t+1) = Stock_c(t) + Σ Source_i(t) − Σ Sink_j(t)

Sink Coverage Ratio (SCR):
    SCR_c(t) = Σ Sink_j(t) / Σ Source_i(t)

    SCR ≈ 1.00   → economy ở trạng thái dừng (stationary)
    SCR < 1.00   → tồn kho tăng → lạm phát → currency mất ý nghĩa → mất động lực chi
    SCR > 1.00   → player bị bào mòn → cảm giác starved → churn
```

`[Design synthesis]` Một sai lầm phổ biến là đo SCR trên toàn bộ population. **Phải đo theo cohort tuổi tài khoản.** Lý do: source thường tỉ lệ với hoạt động (gần như hằng số theo thời gian), còn sink dạng exponential tăng theo tiến độ. Nên SCR của cohort D1 và cohort D180 gần như luôn khác nhau, và một SCR tổng thể "khỏe" hoàn toàn có thể che giấu việc cohort mới đang bị bào mòn còn cohort cũ đang bơi trong tiền.

```text
Ma trận chẩn đoán cần dựng cho MỌI currency:

                 D0–D7    D8–D30   D31–D90   D91+
  Source/ngày      S1        S2        S3      S4
  Sink/ngày        K1        K2        K3      K4
  SCR             K1/S1     K2/S2     K3/S3   K4/S4
  Stock trung vị   —         —         —       —
  Stock P90        —         —         —       —

  Cờ đỏ: SCR < 0.8 ở BẤT KỲ ô nào kéo dài > 2 tuần
  Cờ đỏ: Stock P90 / Stock trung vị > 20 (phân hóa giàu nghèo quá nhanh)
```

`[Design synthesis]` Ngưỡng "SCR khỏe 0.95–1.05" xuất hiện trong tài liệu practitioner nhưng tôi không tìm được phép đo gốc; hãy coi nó là **định nghĩa của trạng thái dừng**, không phải một phát hiện thực nghiệm. Cái đúng về mặt toán học là: nếu bạn muốn stock trung vị không đổi, SCR phải bằng 1 khi tính trên cùng cohort.

### 1.3 Currency velocity và cách đo lạm phát

```text
Velocity_c = (tổng lượng c chi tiêu trong kỳ) / (tồn kho trung bình của c)

  Velocity cao  → currency "nóng", chảy nhanh, sink hấp dẫn
  Velocity thấp → player tích trữ (hoarding) → dấu hiệu:
                  (a) không có sink nào đủ hấp dẫn, HOẶC
                  (b) player đang tiết kiệm cho một mục tiêu lớn (lành mạnh, có chủ đích)

Phân biệt (a) và (b): xem phân bố thời gian nắm giữ.
  (b) có đỉnh rõ ràng — mọi người tích rồi xả cùng lúc quanh một event/banner.
  (a) là đuôi dài phẳng — tiền vào rồi nằm im vô thời hạn.
```

Đo lạm phát trong game **không** dùng CPI thật, mà dùng "rổ hàng hóa" nội bộ `[Rule of thumb]`:

```text
Chọn 5–10 item mà giá trị thực với player gần như KHÔNG đổi theo thời gian
(ví dụ: 1 lần hồi energy, 1 lần revive, 1 lần continue)
→ Price Index(t) = Σ w_i · P_i(t) / Σ w_i · P_i(0)

Nếu bạn định giá cứng chúng bằng hard currency, chỉ số này bất động
— và lạm phát thật sẽ xuất hiện ở CHỖ KHÁC: ở lượng soft currency
mà player kiếm được trên mỗi phút chơi. Hãy đo cả hai:
  Real Earning Rate(t) = soft_earned_per_minute(t) / Price Index(t)
```

`[Design synthesis]` Nói cách khác: trong game F2P, "lạm phát" hiếm khi biểu hiện bằng giá tăng (vì bạn kiểm soát giá), mà biểu hiện bằng **giá trị của thời gian player tăng** — cùng một giờ chơi, tháng sau mua được nhiều hơn tháng trước. Đó là biến số cần theo dõi, và nó bị đẩy lên bởi power creep, event lạm phát thưởng và compensation gifts.

### 1.4 Kiến trúc dual / triple currency — và tại sao

Cấu trúc dual currency (soft + hard) là chuẩn ngành từ thời Facebook games `[GDC talk / practitioner]`. Lý do thiết kế thật sự, tách bạch:

```text
Soft currency tồn tại để:
  1. Cho phép thưởng hào phóng mà KHÔNG tốn doanh thu
     (bạn có thể phát vô hạn, chỉ cần sink theo kịp)
  2. Tạo cảm giác tiến bộ mỗi session
  3. Làm đơn vị đo cho balance của gameplay (không dính vào giá tiền)

Hard currency tồn tại để:
  1. Tách BIẾN ĐỘNG GIÁ khỏi BIẾN ĐỘNG BALANCE — bạn có thể buff gameplay
     mà không phải đổi bảng giá IAP
  2. Cho phép sale/bonus mà không phá store front-end
     (đổi tỉ lệ bonus, không đổi price point)
  3. Là "ngăn cách tâm lý" giữa tiền thật và quyết định chi tiêu
     — chính điểm này đang bị FTC và EU CPC tấn công (xem §4.7)

Currency thứ 3 (event / premium-earnable) tồn tại để:
  1. Cô lập lạm phát của một event khỏi economy chính
     (hết event thì currency chết theo, không tồn dư)
  2. Tạo một "hard-currency-lite" mà F2P kiếm được — giữ chân F2P
     mà không làm loãng giá trị hard currency thật
```

**Cảnh báo kiến trúc quan trọng nhất `[Design synthesis]`:** currency thứ 3 chỉ an toàn nếu nó **hết hạn hoặc không quy đổi ngược** về hard currency. Khoảnh khắc tồn tại một đường quy đổi hai chiều event-currency ↔ hard currency, bạn đã tạo ra một arbitrage và toàn bộ tính toán SCR ở §1.2 phải gộp hai currency thành một. Mọi economy nhiều currency mất kiểm soát mà tôi đọc được đều bắt nguồn từ một converter hai chiều được thêm vào sau, vì lý do "tiện cho player".

### 1.5 Tỷ giá hard ↔ soft

```text
Đặt:
  E    = tỉ giá, soft nhận được trên 1 đơn vị hard
  R    = soft player kiếm được trên 1 giờ chơi (median player)
  V$   = giá trị tiền thật của 1 đơn vị hard  (= giá pack / số hard trong pack)

→ "Giá thời gian" ngầm định của game:
     $/giờ = V$ · R / E

Đây là con số quan trọng nhất mà hầu hết design doc KHÔNG viết ra.
Nó trả lời: "player đang trả bao nhiêu USD để tiết kiệm 1 giờ grind?"
```

`[Design synthesis]` Kinh nghiệm đọc các game top-grossing gợi ý một dải hợp lý là **$1–$5 mỗi giờ tiết kiệm được ở early game**, và tăng dần ở late game (vì late game grind chậm hơn nhiều so với tốc độ tăng E). Tôi **không** có phép đo công khai để chốt con số này — coi nó là biến cần calib, không phải hằng số. Điều chắc chắn về mặt cấu trúc: nếu `$/giờ` **giảm** theo tiến độ, bạn đã thiết kế sai, vì player càng chơi lâu càng thấy việc mua rẻ hơn tương đối, dẫn tới whale hóa quá nhanh và mid-spender không còn lối chi tiêu hợp lý.

Với các economy dạng "percentage fill" (mua để lấp đầy phần còn thiếu của một upgrade thay vì mua currency), tỉ giá hard→soft **không cố định** mà thay đổi theo tiến độ và theo dung lượng kho `[GDC talk / practitioner]`. Đây là một cơ chế cực mạnh vì nó cho phép định giá theo giá trị cảm nhận (value-based pricing) thay vì theo đơn vị — nhưng nó cũng chính là loại "che giấu giá thật" mà EU CPC Key Principles 2025 nhắm tới. Xem §4.7.

### 1.6 Mô hình hóa dòng chảy kiểu Machinations

Machinations là ngôn ngữ trực quan + công cụ mô phỏng cho economy: node là `pool / source / drain / converter / trader / gate`, cạnh là dòng chảy resource, và `activator` bật/tắt node theo điều kiện số học `[GDC talk / practitioner]`.

Quy trình dùng thực tế `[Design synthesis]`, rút gọn thành 6 bước có thể chạy trên spreadsheet nếu không mua tool:

```text
1. Liệt kê TẤT CẢ source và sink của mỗi currency. Không bỏ sót
   compensation gift, refund, tutorial grant.
2. Gán mỗi source/sink một RATE theo đơn vị "trên mỗi phút chơi"
   (không phải trên mỗi ngày — vì session length khác nhau giữa persona).
3. Định nghĩa 3–5 PERSONA: (a) F2P grind-only, (b) minnow, (c) dolphin,
   (d) whale, (e) lapsed/casual. Mỗi persona = một bộ (phút chơi/ngày,
   xác suất mua/ngày, ngân sách trần).
4. Chạy Monte Carlo N ≥ 100 player × T bước (1 bước = 1 phút chơi).
5. Đọc PHÂN BỐ, không đọc trung bình. Cụ thể: P10/P50/P90 của
   power score, stock, và thời điểm chạm paywall.
6. Kiểm tra ĐIỀU KIỆN CHỒNG LẤN (xem dưới).
```

Ví dụ công bố của Machinations chạy 100 player × 120 bước cho một game 4X: nhóm hybrid đạt power trung bình cao hơn nhóm F2P thuần **61%**, nhưng **top 2% F2P vượt 19% nhóm Premium Buyers** `[Case study — self-reported]`. Con số cụ thể ít quan trọng; **hình dạng kết luận** mới là thứ cần copy:

> **Điều kiện chồng lấn `[Design synthesis]`:** phân bố power của F2P và của payer **phải chồng lấn ở đuôi**. Nếu P90 của F2P < P10 của payer, game đã trở thành pay-to-win tuyệt đối — F2P không còn lý do ở lại, và population nền (thứ nuôi PvP matchmaking, social proof, và pipeline chuyển đổi payer tương lai) sẽ rỗng dần. Ngược lại, nếu P50 của F2P ≈ P50 của payer, không ai có lý do trả tiền. Mục tiêu thiết kế: **chồng lấn 15–30% diện tích phân bố** — đây là target tôi đề xuất, không phải số đo được.

---

## 2. Progression & difficulty curve

### 2.1 Công thức level curve

`[GDC talk / practitioner]` Bốn họ công thức được dùng phổ biến:

```text
Linear        XP(n) = a·n                    — pacing phẳng, hiếm dùng đơn lẻ
Polynomial    XP(n) = a·n^p                  — MẶC ĐỊNH của ngành
                  p = 2      : cổ điển (D&D-like, JRPG)
                  p = 2.5–3  : endgame grind sâu (Disgaea-like)
Exponential   XP(n) = a·k^(n−1)              — bùng nổ; k=2 → lv30 ≈ 5·10^8 XP
                  chỉ dùng khi max level rất thấp, hoặc k ≈ 1.05–1.15
Logarithmic   XP(n) = a·ln(n+1)·m            — lợi ích giảm dần; hợp cho
                                               mastery/cosmetic, player plateau
Fibonacci-ish XP(n) = XP(n−1) + XP(n−2)      — dễ chịu: dễ đầu, dốc cuối
```

`[Design synthesis]` Quy tắc chọn: **dùng polynomial `p≈2` cho trục progression chính, exponential `k≈1.15–1.25` cho upgrade cost của từng item, và log cho mọi hệ thống mastery không có trần**. Lý do là ba trục này phải có tốc độ khác nhau, nếu không chúng sẽ cộng dồn thành một bức tường duy nhất.

Time-to-level phải tính bằng **median player rate, không phải optimal rate** — thực hành phổ biến là lấy 60–80% tốc độ farm tối ưu `[Rule of thumb]`:

```text
  T(n) = XP(n) / (XP_per_minute_optimal · 0.7)

Nếu bạn tune bằng optimal rate, mọi ước lượng playtime sẽ ngắn hơn thực tế
~30–40%, và bạn sẽ vô tình đặt gate/paywall sớm hơn ý định.
```

### 2.2 Hình dạng đường độ khó

`[Design synthesis]` Đường độ khó không phải một đường đi lên. Nó là **răng cưa có đường bao đi lên**:

```text
  Độ khó
    ▲                                      ╱▔▔╲   ← boss / hard level
    │                          ╱▔╲    ╱▔▔╲╱     ╲
    │              ╱▔╲   ╱▔▔╲╱   ╲╱                 ← đường bao (envelope)
    │      ╱▔╲╱▔╲╱    ╲╱
    │  ╱▔╲╱
    └────────────────────────────────────────────▶ tiến độ

Ba tham số cần chốt bằng số, không bằng cảm giác:
  A  = biên độ răng cưa (chênh lệch win-rate giữa đỉnh và đáy)
  λ  = chu kỳ răng cưa (số level giữa hai đỉnh)
  g  = độ dốc đường bao (win-rate giảm bao nhiêu điểm % mỗi 100 level)

Quy tắc: sau MỖI đỉnh khó phải có ÍT NHẤT 2 level dễ liên tiếp.
Lý do: đỉnh khó tiêu hao energy/lives; nếu không có level dễ để hồi
"cảm giác thắng", player rời đi ngay tại đỉnh chứ không phải sau đó.
```

Trong match-3, biến điều khiển độ khó chủ đạo là **số nước đi**, và quan hệ là **phi tuyến**: nếu level được calib ở 20 nước, cấp cho player 23 nước làm độ khó giảm theo cấp số nhân chứ không tuyến tính `[GDC talk / practitioner]`. Hệ quả thực dụng: **đừng dùng số nước làm núm tinh chỉnh nhỏ** — 1 nước là một bước nhảy lớn. Dùng layout, blocker density và objective count để tinh chỉnh, chỉ dùng move count để đổi bậc độ khó.

Telemetry tối thiểu cho mỗi level `[Rule of thumb]`: win rate, số lần thử trung bình, tỉ lệ dùng booster, và quit point. Nguyên tắc rebalance thường thấy: khi churn của một level vượt phần đóng góp doanh thu của nó thì rebalance.

### 2.3 "Bức tường" trong puzzle game

`[Design synthesis]` Bức tường (the wall) không phải là một level khó. Nó là điểm mà **ba đường cong giao nhau cùng lúc**:

```text
  (1) win rate của level rơi xuống dưới ~0.3 cho lần thử đầu
  (2) trữ lượng booster tích lũy tự nhiên của player = 0
  (3) energy/lives bị tiêu hết trong 1 session

Bất kỳ 1 trong 3 điều kiện là bình thường. Cả 3 cùng lúc = tường.
Khi đó player chỉ còn 3 lựa chọn: trả tiền, đợi, hoặc bỏ.

Test bắt buộc: dựng biểu đồ 3 đường này chồng lên nhau theo level index.
Đánh dấu mọi điểm giao ba. Với mỗi điểm giao, trả lời rõ ràng:
  "đây là tường CÓ CHỦ ĐÍCH hay tường TÌNH CỜ?"
Tường tình cờ là nguyên nhân churn phổ biến nhất mà analytics
không bao giờ chỉ thẳng ra được, vì nó là hiệu ứng giao thoa
giữa ba hệ thống do ba người khác nhau tune.
```

### 2.4 Toán học của energy system (time-gating)

Tham số và phương trình:

```text
  C   = cap (số energy/lives tối đa)
  r   = regen interval (phút / 1 đơn vị)
  c   = chi phí energy cho 1 lần chơi
  L   = độ dài trung bình 1 lần chơi (phút)
  P   = giá refill đầy (USD)

Thời gian hồi đầy từ 0:        T_full = C · r
Số lần chơi/ngày từ regen:     N_day  = (1440 / r) / c
Tổng lần chơi/ngày (từ 0):     N_day + C/c
Độ dài session tự nhiên:       S = (C/c) · L
Giá mỗi lần chơi khi mua:      p_play = P · c / C
```

Cấu hình kinh điển của Candy Crush Saga: **C=5, r=30 phút, c=1, refill $0.99** `[GDC talk / practitioner]`.

```text
[Design synthesis] Thay số:
  T_full   = 5 × 30 = 150 phút = 2.5 giờ
  N_day    = 1440/30 = 48 lần/ngày từ regen (thực tế player không thức 24h)
  S        = 5 lần chơi liên tiếp
  p_play   = $0.99 / 5 = $0.198 ≈ $0.20 mỗi lần chơi

Đọc ngược lại thiết kế: r = 30 phút KHÔNG được chọn để giới hạn tổng
thời gian chơi/ngày (48 lần là quá nhiều). Nó được chọn để ép
NGẮT SESSION: 5 lần thua liên tiếp = hết energy = phải rời đi hoặc trả tiền,
và thời gian chờ 2.5h đủ dài để tạo một lần quay lại vào buổi tối.
Đây là thiết kế NHỊP SESSION, không phải thiết kế TRẦN THỜI GIAN.
```

Rule-of-thumb practitioner: một thanh energy đầy nên cho **15–30 phút gameplay**, và hệ thống nên hỗ trợ **3–4 session/ngày cách quãng** `[GDC talk / practitioner]`.

`[Design synthesis]` **Ba lỗi thiết kế energy hay gặp:**

1. **Cap quá lớn so với `c`** → session dài, player chơi hết hứng trong một lần, không có lý do quay lại. Cap chỉ nên đủ cho 1 session mục tiêu, không nhiều hơn.
2. **Refill giá theo đơn vị thay vì theo cụm** → khi bán lẻ từng energy, player tính được `$/lần chơi` quá dễ và so sánh với giá trị gameplay; bán theo cụm (full refill) làm phép so sánh khó hơn và neo vào "giải quyết vấn đề" thay vì "mua hàng". Đây là lợi thế conversion, nhưng cũng chính là loại che giấu giá mà EU CPC nhắm tới — cân nhắc.
3. **Battle-pass/VIP tăng cap mà không tăng sink** → cấp `C=8` thay vì `5` cho pass holder là buff throughput ~60%; nếu progression sink không được nới tương ứng, pass holder cạn nội dung sớm và churn ngay sau khi trả tiền — kịch bản tệ nhất có thể có.

### 2.5 Vị trí gate và tác động lên retention — bằng chứng đo được

Đây là một trong số rất ít A/B test về gate placement có **dữ liệu công khai kiểm chứng được**: Cookie Cats (Tactile Entertainment), dataset công bố qua DataCamp, **90,189 player**, chia ngẫu nhiên hai nhánh `gate_30` và `gate_40` — gate đầu tiên đặt ở level 30 so với level 40 `[Primary research]`.

```text
Kết quả (nhất quán trên nhiều phân tích độc lập của dataset này):
  Retention D1:  gate_30 cao hơn gate_40 khoảng +0.6 điểm phần trăm
  Retention D7:  gate_30 cao hơn gate_40 khoảng +0.8 điểm phần trăm
                 (≈ −4.3% tương đối khi dời gate ra xa)

Kết luận thống kê: bằng chứng mạnh rằng gate ĐẶT SỚM HƠN cho retention
CAO HƠN ở cả D1 và D7.
```

`[Design synthesis]` Đây là kết quả phản trực giác nhất trong toàn bộ doc này và đáng để nội hóa: **dời gate ra xa hơn — tức cho player chơi tự do lâu hơn — lại làm retention GIẢM.** Cách giải thích nhất quán với lý thuyết pacing: gate không chỉ là công cụ monetization mà còn là **cơ chế ngắt session có kiểm soát**. Nó buộc player dừng lại khi vẫn còn hứng thú, tạo một "vòng lặp mở" để quay lại. Cho phép chơi tới chán rồi mới chặn thì đã quá muộn — thứ được tiêu thụ không phải là level, mà là **sự mới lạ**.

**Cảnh báo về mức độ khái quát hóa:** đây là một game, một cặp vị trí gate, một thời điểm. Đừng suy ra "gate luôn phải sớm". Cái đáng suy ra là **hướng của hiệu ứng có thể ngược với trực giác, nên gate placement bắt buộc phải A/B, không được quyết bằng tranh luận nội bộ.** Ngoài ra, độ lớn hiệu ứng (0.6–0.8 điểm phần trăm) là **nhỏ** — cần cỡ mẫu lớn để phát hiện; nếu game của bạn có ít hơn ~50k user/nhánh thì test này gần như chắc chắn under-powered.

---

## 3. Thiết kế thang giá (pricing ladder)

### 3.1 Các mức giá chuẩn

`[Rule of thumb]` Thang giá kinh điển trên US/EU App Store & Google Play:

```text
  $0.99  →  $4.99  →  $9.99  →  $19.99  →  $49.99  →  $99.99
   (T1)     (T5)     (T10)     (T20)      (T50)     (T100)
```

Lý do sáu bậc chứ không phải bốn hay mười `[Design synthesis]`: mỗi bậc kề nhau chênh **khoảng 2–2.5×**, đủ để tạo bậc thang cảm nhận rõ ràng nhưng không quá xa để tạo khoảng trống. Sáu bậc phủ được dải chi tiêu ~100× (từ $1 đến $100), đúng bằng dải phân hóa giữa minnow và whale nhẹ. Whale nặng cần cơ chế khác (bundle lặp lại, VIP), không phải thêm bậc giá cao hơn.

### 3.2 Đường cong giá trị trên mỗi đô — và tại sao pack lớn PHẢI có bonus

Đây là điểm mà rất nhiều team làm sai vì tưởng bonus là "khuyến mãi". Nó không phải khuyến mãi, nó là **điều kiện cần để thang giá tồn tại**.

```text
[Design synthesis] Một thang giá mẫu và giá trị/đô:

price     gems    gems/$    bonus % so với bậc đáy
$0.99       80      80.8       +0.0%
$4.99      500     100.2      +24.0%
$9.99     1200     120.1      +48.6%
$19.99    2500     125.1      +54.8%
$49.99    6500     130.0      +60.9%
$99.99   14000     140.0      +73.3%
```

Lập luận toán học `[Design synthesis]`: gọi `V(p)` là số hard currency nhận được khi trả `p` USD, và `v(p) = V(p)/p` là giá trị trên mỗi đô.

```text
Nếu v(p) HẰNG SỐ (không bonus):
  player hoàn toàn không có lý do mua bậc cao. Mua $0.99 sáu lần
  bằng hệt mua $4.99 + $0.99. Player sẽ mua bậc THẤP NHẤT vì rủi ro thấp nhất
  → ARPPU bị ép xuống đáy thang, và bạn mất luôn khả năng bán $99.99.

Nếu v(p) TĂNG (có bonus lũy tiến):
  player phải cân giữa "tiết kiệm/đơn vị" và "chi phí trả trước".
  Đây chính là cơ chế đẩy ARPPU lên. Bonus là GIÁ mà bạn trả
  để mua quyền bán bậc cao.

Ràng buộc thiết kế bắt buộc:  v(p) phải ĐƠN ĐIỆU KHÔNG GIẢM theo p.
  Vi phạm ràng buộc này (một pack giữa thang có giá trị/đô thấp hơn
  pack rẻ hơn nó) là lỗi kinh điển và bị player phát hiện trong vài giờ,
  thường thành ảnh chế trên subreddit của game.
```

Bằng chứng cấu trúc từ một game thật: trong Clash of Clans, pack 14.000 gem cho giá trị tốt nhất ở khoảng **$0.71 / 100 gem**, còn pack 80 gem là kém hiệu quả nhất `[Market data]` — đúng hình dạng đơn điệu tăng nêu trên.

`[Design synthesis]` **Độ dốc bonus nên là bao nhiêu?** Không có phép đo công khai. Cấu trúc hợp lý: bonus tăng theo log của giá, tức `v(p) = v₀ · (1 + β·ln(p/p₀))`. Với `β ≈ 0.13` ta được đúng dải +0% → +73% ở bảng trên. Điều quan trọng hơn con số: **độ dốc phải giảm dần** ở đầu cao của thang, nếu không pack $99.99 sẽ nuốt hết doanh thu của các bậc giữa và bạn mất khả năng bán leo thang.

### 3.3 First-time purchase (FTP) offer và starter pack

Đây là chỗ có dữ liệu khảo sát practitioner tốt nhất — và một phát hiện đáng chú ý.

Khảo sát 54 practitioner F2P do Deconstructor of Fun thực hiện `[Market data]`:

```text
Sở thích định giá starter pack:
  96.3%  đặt dưới $10
  59.2%  đặt dưới $5
   3.7%  đặt trên $10

Ưu tiên chiến lược:
  85.2%  ưu tiên "offer hấp dẫn để user mua NHANH"
  61.1%  chủ động định giá THẤP HƠN mức user sẵn sàng trả
  <40%   coi "không định giá quá thấp" là điều quan trọng

Thiên lệch né rủi ro — chọn giữa hai cấu hình có LTV trung bình BẰNG NHAU ($10):
  81.5%  chọn: conversion 10%, $100/payer
   5.5%  chọn: conversion  1%, $1.000/payer
  13.0%  không phân biệt

Khoảng cách personalization:
  70.3%  cho rằng price personalization là "thiết yếu"
  53.7%  thực sự đã triển khai
```

Luận điểm của DoF: ngành **định giá starter pack quá thấp một cách hệ thống**, vì conversion rate là chỉ số dễ nhìn và dễ báo cáo hơn LTV, và vì pack giá thấp tạo cảm giác doanh thu "chắc chắn" hơn — kể cả khi nó làm hỏng repeat purchase ở nhóm giá trị cao.

`[Design synthesis]` Cơ chế gây hại cụ thể là **price anchoring**: nếu offer đầu tiên player nhìn thấy là 1.000 gem giá $0.99, thì gói mặc định 100 gem giá $1.99 trở nên vô lý trong một thời gian rất dài `[GDC talk / practitioner]`. Neo giá không hết hạn cùng offer. Hệ quả thực dụng:

> **Starter pack không nên là pack có giá trị/đô cao nhất trong game.** Nó nên là pack có **rào cản tâm lý thấp nhất** — thứ khác hoàn toàn. Cách đạt được cả hai: giữ giá trị/đô của starter pack **ở mức của bậc kề trên nó** (tức nó "tốt như một pack đắt hơn"), chứ không phải tốt hơn mọi pack trong game. Bổ sung giá trị bằng **thứ không định giá được** — một skin độc quyền, một hero, một cosmetic — thay vì bằng lượng hard currency, vì hard currency là thứ trực tiếp phá neo giá.

Về conversion: các benchmark first-purchase conversion 2–5% (top title 5–8%) lưu hành rộng rãi `[Market data]`; con số "≈1.8% player F2P từng mua" bị lặp lại khắp nơi nhưng **không truy được về primary source nào** — ghi vào mục không xác minh được. Số duy nhất có nguồn rõ ràng: AppsFlyer đo install-to-purchase **2.6% trong 30 ngày** (Q1 2022, tính theo install chứ không theo DAU) `[Market data]`.

### 3.4 "$1 wedge" và bậc thang chuyển đổi

`[Design synthesis]` Lập luận đằng sau offer $0.99 là lập luận về **rào cản, không phải về giá**:

```text
Rào cản của lần mua ĐẦU TIÊN gồm 3 thành phần:
  (a) rào cản thiết lập thanh toán  (nhập thẻ, xác thực) — MỘT LẦN duy nhất
  (b) rào cản tâm lý "tôi là người trả tiền cho game này" — MỘT LẦN duy nhất
  (c) rào cản giá trị "món này có đáng tiền không"       — MỖI LẦN

Offer $1 tồn tại để triệt tiêu (c) xuống gần 0, nhằm thanh toán DỨT ĐIỂM
chi phí (a) và (b). Sau lần đầu, chỉ còn (c) cho mọi giao dịch sau.
Đó là toàn bộ giá trị của wedge — không phải doanh thu $1.
```

Vì thế **cách đo đúng của một $1 offer không phải conversion rate của chính nó, mà là chênh lệch doanh thu D30/D60 giữa nhóm được thấy và nhóm không**. Một $1 offer có conversion 8% nhưng không nâng doanh thu D60 là một offer **thất bại** — nó chỉ chuyển doanh thu tương lai về hiện tại và đồng thời hạ neo giá. Tôi **không tìm được** một phép đo lift công khai, có đối chứng, cho $1 offer; mọi con số lưu hành đều là self-reported không đối chứng. Đây là hạng mục bắt buộc phải tự đo bằng holdout.

### 3.5 Neo giá và decoy — dùng, nhưng biết giới hạn

Decoy effect (asymmetric dominance) là công cụ được viện dẫn nhiều nhất trong thiết kế store F2P: thêm một lựa chọn thứ ba kém hơn rõ rệt so với một trong hai lựa chọn gốc để đẩy lựa chọn về phía option bạn muốn bán. Ví dụ kinh điển của Dan Ariely với The Economist: tỉ lệ chọn gói combo nhảy từ 32% lên 84% khi thêm option print-only cùng giá `[Primary research]`.

`[Design synthesis]` **Cảnh báo bắt buộc:** hiệu ứng decoy là một trong những phát hiện có vấn đề replication rõ nhất trong tài liệu pricing — anchoring, social proof và default effect có bằng chứng thực nghiệm vững hơn nhiều và nhiều khả năng đang gánh phần lớn hiệu ứng mà người ta gán cho decoy trên các trang pricing thật. Khuyến nghị thực dụng: **dùng default/pre-selected option và anchoring làm cơ chế chính; coi decoy là biến A/B chứ không phải nguyên tắc thiết kế.**

### 3.6 Regional pricing

Định giá theo tier quốc gia là cấu trúc chuẩn (xem doc 27 §A.4 cho bằng chứng production trực tiếp). Ở US/EU cần thêm hai lưu ý `[Design synthesis]`:

- **EU không phải một tier.** Sức mua Đức/Bắc Âu và Đông Âu (Romania, Bulgaria, Ba Lan) chênh nhau đủ lớn để cần ít nhất 2–3 tier trong nội bộ EU. Nhưng phải cân nhắc rủi ro pháp lý về phân biệt đối xử theo địa lý trong thị trường chung EU — đây là câu hỏi cho legal, không phải cho economy designer.
- **Cách bù chênh lệch sức mua nên là "nhiều hàng hơn cùng giá", không phải "giá thấp hơn".** Cùng price point local, thêm bonus currency ở thị trường sức mua thấp `[Market data]`. Lý do kỹ thuật: giữ nguyên price tier giúp store front-end, receipt validation và báo cáo doanh thu đồng nhất; lý do thiết kế: nó không phá neo giá toàn cầu khi player so sánh chéo trên Discord/Reddit.

---

## 4. Toán học gacha / loot box

### 4.1 Trường hợp cơ sở: không pity

Với xác suất `p` cố định mỗi lần quay, số lần quay tới lần trúng đầu tiên tuân theo **phân phối hình học**:

```text
  P(trúng lần đầu tại lượt n) = (1−p)^(n−1) · p
  E[số lượt]                  = 1/p
  P(chưa trúng sau n lượt)    = (1−p)^n
  Số lượt để đạt 90% chắc chắn: n₉₀ = ln(0.10)/ln(1−p)
```

`[Design synthesis]` Với `p = 0.006` (0.6%): `E = 166.7 lượt`, và `n₉₀ = 383 lượt`. Con số 383 chính là lý do pity system tồn tại. Không có pity, **10% player xui nhất phải quay hơn 383 lần** — trải nghiệm này phá hủy niềm tin nhanh hơn bất kỳ mức giá nào.

### 4.2 Công thức hóa pity

Dạng tổng quát của hàm xác suất theo lượt `[Design synthesis]`, khớp với các mô tả công khai về hệ thống của Genshin và Arknights:

```text
  p(i) = p_base                                   nếu  i < S     (trước soft pity)
  p(i) = min(1, p_base + Δ·(i − S + 1))           nếu  S ≤ i < H  (soft pity, tuyến tính)
  p(i) = 1                                        nếu  i = H     (hard pity)

  S = lượt bắt đầu soft pity
  H = hard pity (bảo đảm)
  Δ = bước tăng mỗi lượt trong vùng soft pity

Phân phối số lượt tới lần trúng đầu:
  P(n) = p(n) · Π_{k=1}^{n−1} (1 − p(k))

Kỳ vọng:
  E[lượt] = Σ_{n=1}^{H}  n · P(n)
```

Tham số Genshin (banner nhân vật) được cộng đồng mô tả nhất quán: `p_base = 0.6%`, soft pity từ lượt **74**, tăng ~**6 điểm phần trăm** mỗi lượt, hard pity **90** `[Market data]` — lưu ý đây là **mô hình cộng đồng dựng từ dữ liệu quan sát**, không phải thông số do HoYoverse công bố.

### 4.3 Kết quả tính toán

`[Design synthesis]` Chạy công thức trên với `p_base=0.006, S=74, H=90, Δ=0.06`:

```text
  Kiểm tra tổng xác suất       = 1.000000  ✓
  E[lượt tới 5★ bất kỳ]        = 62.30 lượt
  Trung vị                     = lượt 76
  P(không trúng trong 73 lượt) = 64.45%
  P(trúng trong 74–80)         = 56.29%
  P(trúng ≤ 80 lượt)           = 91.85%
  So sánh không pity           = 166.7 lượt

→ Pity kéo kỳ vọng từ 166.7 xuống 62.3 lượt, tức GIẢM 62.6%.
```

Bốn nhận xét thiết kế `[Design synthesis]`:

1. **Pity không chỉ giảm kỳ vọng, nó CẮT ĐUÔI.** 100% player trúng trong 90 lượt so với 383 lượt cho 90% player. Giá trị thật của pity nằm ở phương sai, không ở kỳ vọng.
2. **64% player chạm soft pity.** Nghĩa là "soft pity" là **trải nghiệm điển hình**, không phải ngoại lệ. Nếu bạn thiết kế UX của gacha quanh việc "trúng sớm", bạn đang thiết kế cho 36% thiểu số.
3. **Trung vị (76) > kỳ vọng ÷ 1** và cụm 74–80 chiếm 56% khối lượng xác suất. Phân phối gần như **hai mốt** (bimodal): một cụm nhỏ trúng sớm và một cụm lớn dồn quanh soft pity. Điều này giải thích tại sao cảm nhận cộng đồng về gacha luôn là "quay mãi không ra rồi ra một lúc".
4. Con số cụ thể **rất nhạy với `Δ`.** Với `Δ` nhỏ hơn, khối lượng dồn sát hard pity hơn và E tăng. Nếu bạn copy tham số Genshin, hãy **chạy lại phép tính cho bộ tham số của mình**, đừng copy con số 62.3.

### 4.4 Rate-up 50/50 và chi phí kỳ vọng để sở hữu

Cơ chế phổ biến: khi trúng 5★, có xác suất `q` đó là nhân vật rate-up; nếu trượt thì lần 5★ **kế tiếp được bảo đảm** là rate-up.

```text
  E[lượt để có nhân vật rate-up] = E[lượt tới 5★] · (1 + (1−q))
                                 = E · (2 − q)

  Với q = 0.5:  E_featured = 1.5 · E
```

`[Design synthesis]` Áp vào số đã tính:

```text
  E_featured = 1.5 × 62.30 = 93.45 lượt

  Quy ra tiền, giả định $2.00/lượt (giá điển hình sau bonus của pack lớn):
    Chi phí kỳ vọng cho 1 nhân vật rate-up ≈ $186.89
    Chi phí kỳ vọng cho 7 bản sao (max constellation) ≈ 654 lượt ≈ $1.308

  → Đây là "giá thật" của một nhân vật. Con số này PHẢI xuất hiện
    trong design doc của bạn. Nếu team không biết nó, team không biết
    mình đang bán gì với giá bao nhiêu.
```

### 4.5 Dupe / shard / conversion

`[Design synthesis]` Mọi hệ gacha đều cần đường xử lý bản trùng, và lựa chọn ở đây quyết định hình dạng chi tiêu:

```text
Ba kiến trúc:
  A. Dupe → sức mạnh (constellation/eidolon)
     → tạo đuôi chi tiêu VÔ HẠN cho whale.
     → rủi ro: power gap F2P/whale phình ra, vi phạm điều kiện chồng lấn §1.6.

  B. Dupe → shard/mảnh của một currency mua-được-thứ-mình-muốn
     → tạo "pity xuyên banner": mọi lần quay đều tiến về một mục tiêu chắc chắn.
     → giảm phương sai mạnh nhất, tốt nhất cho niềm tin, nhưng
       cắt bớt đuôi chi tiêu.

  C. Dupe → cosmetic / prestige
     → không đụng vào balance; đuôi chi tiêu tồn tại nhưng không gây pay-to-win.
     → an toàn nhất cho US/EU, nơi áp lực pháp lý và dư luận cao nhất.

Công thức "pity xuyên banner" (kiến trúc B):
  Mỗi lượt quay tặng s shard. Item mục tiêu giá M shard.
  → Trần chi phí tuyệt đối = M/s lượt.
  → Đặt M/s ≈ 1.5–2.0 × E_featured để trần này là mạng lưới an toàn
    thật sự (thỉnh thoảng chạm), chứ không phải con đường chính.
```

### 4.6 Multi-pull và box gacha

```text
Multi-pull có bảo đảm (ví dụ: 10-pull đảm bảo ít nhất 1 item ≥4★):
  Bảo đảm KHÔNG làm thay đổi kỳ vọng của bậc 5★ nếu nó chỉ áp cho bậc 4★.
  Nó chỉ giảm phương sai cảm nhận. Đừng nhầm hai thứ này khi tính chi phí.

Box gacha (rút không hoàn lại — hypergeometric, không phải geometric):
  Hộp N item, trong đó K là item mục tiêu.
  P(trúng ít nhất 1 trong n lần rút) = 1 − C(N−K, n)/C(N, n)
  E[số lần rút tới lần trúng đầu]    = (N+1)/(K+1)

  → Box gacha có TRẦN CỨNG tự nhiên (rút hết hộp là chắc chắn có).
    Đây là hình thức MINH BẠCH NHẤT về mặt toán học và là lựa chọn
    an toàn nhất về pháp lý ở US/EU — nhưng nó cũng làm chi phí trở nên
    dễ tính với player, nên doanh thu đuôi thấp hơn.
```

### 4.7 Yêu cầu công bố xác suất — theo từng khu vực pháp lý

`[Primary research]` / `[Market data]` Bảng dưới là ràng buộc cứng, không phải best practice:

```text
Trung Quốc     Bắt buộc theo luật từ 01/05/2017 (quy định của Bộ Văn hóa,
               ban hành 12/2016). Quốc gia đầu tiên bắt buộc bằng luật.
               Nghiên cứu học thuật ghi nhận nhiều công ty tuân thủ ở mức
               TỐI THIỂU — công bố ở nơi khó tìm, không nổi bật.

Hàn Quốc       Luật công bố xác suất loot box có hiệu lực 2024, ĐƯỢC THỰC THI
               CHỦ ĐỘNG. Nghiên cứu so sánh kết luận mức tuân thủ TỐT HƠN
               rõ rệt so với tự điều chỉnh của ngành.

Đài Loan       Có yêu cầu công bố xác suất.

Nhật Bản       Không bắt buộc bằng luật; các công ty lớn ủng hộ TỰ ĐIỀU CHỈNH.
               (Riêng "complete gacha"/kompu gacha bị cấm từ 2012.)

Apple App Store  Bắt buộc công bố odds cho MỌI game trên iOS TOÀN CẦU
                 từ 12/2017. Nghiên cứu ghi nhận Apple KHÔNG thực thi tích cực.
Google Play / Xbox / PlayStation / Nintendo eShop
                 Đều yêu cầu công bố odds như điều kiện niêm yết game có
                 cơ chế loot box.

Hoa Kỳ (FTC)   Không có luật loot box riêng, nhưng ÁN LỆ THỰC THI đã có:
               01/2025, Cognosphere (HoYoverse, Genshin Impact) nộp phạt
               $20 triệu và chịu các ràng buộc:
                 • PHẢI công bố odds loot box VÀ tỉ giá quy đổi virtual currency
                 • CẤM bán loot box cho user dưới 16 tuổi nếu không có
                   đồng thuận xác thực của cha mẹ
                 • CẤM bán loot box CHỈ bằng virtual currency — phải luôn có
                   lựa chọn mua trực tiếp bằng tiền thật
                 • Cấm xuyên tạc odds, giá, tính năng

EU (CPC Network)  03/2025: 7 "Key Principles on In-Game Virtual Currencies"
                  — hướng dẫn, KHÔNG ràng buộc pháp lý, nhưng thể hiện cách
                  cơ quan bảo vệ người tiêu dùng EU sẽ diễn giải luật hiện hành.
                  Yêu cầu tối thiểu: giá và thông tin tiền hợp đồng rõ ràng,
                  minh bạch; cấm che giấu chi phí thật của nội dung số;
                  cấm ép buộc phải mua virtual currency; kỳ vọng game
                  hiển thị GIÁ TRỊ TIỀN THẬT của mọi giao dịch trong game.
                  06/2025 Ủy ban châu Âu tổ chức đối thoại với các bên
                  về việc áp dụng.
```

`[Design synthesis]` **Ba hệ quả kiến trúc trực tiếp cho SDK/game nhắm US/EU** — cần thiết kế từ đầu, không thể vá sau:

1. **Mọi loot box phải mua được trực tiếp bằng tiền thật, song song với đường virtual currency.** Đây là ràng buộc từ án FTC. Nghĩa là data model của offer cần đồng thời hai `price` (real + virtual), không phải một.
2. **Hiển thị giá trị tiền thật kèm mọi giá virtual currency.** Đây là kỳ vọng của EU CPC. Nghĩa là mọi màn hình có giá phải truy được về một tỉ giá quy chiếu — và tỉ giá đó phải ổn định, có thể giải thích. Kiến trúc "percentage fill" ở §1.5, vốn cố tình làm tỉ giá biến động, **xung đột trực tiếp** với nguyên tắc này.
3. **Odds phải nổi bật và dễ tiếp cận, không chỉ tồn tại.** Bài học từ nghiên cứu tuân thủ ở Trung Quốc: chôn odds trong một trang settings sâu là hình thức tuân thủ mà cơ quan quản lý đã học được cách nhận diện. Đặt odds ở màn hình quay, một chạm.

---

## 5. Toán học battle pass

### 5.1 Tham số cấu trúc

`[Market data]` Cấu hình chuẩn ngành 2026:

```text
  Độ dài mùa:   4–8 tuần   (casual: ~1 tháng; mid-core/cosmetic: có thể 3 tháng)
  Số tier:      50–100
  Giá:          $5–$15
  Loại thưởng:  chủ đạo là cosmetic
  Độ phủ:       ~60% game top-grossing có battle pass
```

### 5.2 Đường cong XP và mục tiêu completion

Nguyên tắc thiết kế do practitioner nêu: đường cong **tăng dần** về effort và về giá trị tier, có **các khoảnh khắc giảm tải đều đặn** sau mỗi đỉnh, và **kết thúc bằng một đỉnh lớn** `[GDC talk / practitioner]`. Thưởng nhỏ giữ nhịp tiến bộ; thưởng lớn đóng vai trò cột mốc.

`[Design synthesis]` Cụ thể hóa bằng số, so sánh curve phẳng và curve dốc dần cho pass 50 tier / mùa 30 ngày:

```text
  Phẳng:     XP(t) = 1000                → tổng 50.000 XP → 1.667 XP/ngày
  Dốc dần:   XP(t) = 800 + 40·t          → tổng 91.000 XP → 3.033 XP/ngày

  Nhưng player KHÔNG chơi 30/30 ngày. Với 21/30 ngày hoạt động (70%):
  Dốc dần cần 4.333 XP/ngày hoạt động — gấp 1.43× ước lượng ngây thơ.
```

> **Sai lầm phổ biến nhất của battle pass là chia tổng XP cho số ngày trong mùa.** Phải chia cho **số ngày HOẠT ĐỘNG kỳ vọng của segment mục tiêu**. Với D30 retention điển hình của casual game, tỉ lệ ngày hoạt động của một player "tích cực vừa phải" nằm quanh 50–70%, nghĩa là yêu cầu XP thực tế mỗi ngày hoạt động cao hơn con số ngây thơ **1.4–2×**.

Thất bại phổ biến nhất được practitioner chỉ ra là **đường độ khó**: thiết kế một mùa mà nhịp chơi trung bình không thể hoàn thành khiến player bỏ pass **và bỏ luôn game**; ngược lại quá dễ thì giữa mùa không còn gì để làm `[GDC talk / practitioner]`.

`[Design synthesis]` Cách chốt target completion rate theo từng segment, thay vì một con số duy nhất:

```text
Đặt target theo PHÂN VỊ ENGAGEMENT, không theo trung bình:
  P90 engagement (top 10% chăm nhất)  → hoàn thành TRƯỚC hạn 5–10 ngày
                                        (cần nội dung "sau tier 50" cho họ)
  P50 (trung vị player MUA pass)      → hoàn thành ĐÚNG hoặc SÁT hạn
                                        ← đây là ràng buộc thiết kế trung tâm
  P25 người mua pass                  → đạt ~70–85% số tier
                                        (đủ để thấy đáng tiền, đủ để tiếc
                                         mà mua booster/tier skip)

Quy trình: lấy dữ liệu engagement thật của người ĐÃ MUA pass mùa trước
(không phải toàn bộ DAU — hai phân phối này khác nhau đáng kể),
tính XP/ngày ở từng phân vị, rồi GIẢI NGƯỢC ra tổng XP của pass.
Đây là cách duy nhất đúng: tổng XP là ĐẦU RA, không phải đầu vào.
```

### 5.3 Chia giá trị free track vs premium track

`[Market data]` Các mô hình thực tế công bố được:

```text
  Hearthstone     — "nhiều phần thưởng free cho mỗi phần thưởng premium"
  CoD Mobile      — người mua premium được +25% XP boost
  Wild Rift       — người mua premium có thêm 25 level vượt mức 50
  Brawl Stars     — quest giới hạn thời gian làm mới hằng ngày + quest tuần vĩnh viễn
```

`[Design synthesis]` Ba nguyên tắc chia:

1. **Free track phải có nhịp thưởng ĐỀU HƠN, premium track phải có ĐỈNH CAO HƠN.** Free track làm nhiệm vụ retention (player không mua vẫn thấy tiến bộ mỗi ngày); premium track làm nhiệm vụ chuyển đổi (đỉnh nhìn thấy được nhưng không chạm được là động cơ mua).
2. **Free track nên trả về ĐỦ hard currency cho khoảng 60–90% giá pass, không bao giờ 100%+.** Practitioner mô tả nguyên tắc này rõ: pass được thiết kế để player kiếm đủ cho "một pass tiếp theo", chứ không tạo vòng lặp pass miễn phí vô hạn `[GDC talk / practitioner]`. Ở mức 60–90%, mùa sau player vẫn cần trả thêm một khoản nhỏ — giữ được conversion mà vẫn tạo cảm giác "pass tự trả tiền cho nó".
3. **Không đặt bất kỳ item ảnh hưởng balance nào chỉ có ở premium track** nếu game có PvP. Đây là ranh giới pay-to-win rõ nhất mà cộng đồng US/EU phản ứng mạnh nhất.

### 5.4 Toán học catch-up cho người tham gia muộn

`[Design synthesis]` Vấn đề: player mua pass ở ngày `d` của mùa `D` ngày chỉ còn `D−d` ngày để kiếm tổng XP `X`.

```text
  Yêu cầu XP/ngày của người vào muộn:  X / (D − d)
  Yêu cầu của người vào ngày đầu:      X / D
  Hệ số bất lợi:                       D / (D − d)

  Với D=30:  vào ngày 15 → hệ số 2.0×   (bất khả thi với đa số)
             vào ngày 20 → hệ số 3.0×
             vào ngày 25 → hệ số 6.0×

→ Nếu KHÔNG có cơ chế catch-up, doanh thu pass thực tế bị KHÓA
  trong 3–5 ngày đầu mùa. Đó là một lỗ hổng doanh thu rất lớn,
  và nó biến mọi ngày sau ngày ~10 thành ngày chết cho sản phẩm này.

Ba cơ chế catch-up, theo thứ tự an toàn thiết kế:
  1. Truy lĩnh (retroactive): mua pass ở bất kỳ lúc nào cũng nhận NGAY
     mọi premium reward của các tier đã đạt. An toàn nhất — công bằng,
     dễ hiểu, không phá đường cong. NÊN LÀ MẶC ĐỊNH.
  2. Quest tuần tích lũy không hết hạn (kiểu Brawl Stars):
     tự động tạo một "kho XP" cho người vào muộn.
  3. Tier skip bán bằng hard currency: doanh thu cao nhất nhưng
     rủi ro cao nhất — biến pass từ "thưởng cho engagement"
     thành "thưởng cho chi tiêu", làm hỏng chính lý do pass giữ chân player.
     Nếu dùng, hãy đặt TRẦN số tier mua được (ví dụ ≤ 30% tổng tier).
```

### 5.5 Giá so với giá trị cảm nhận

`[Market data]` Practitioner mô tả tỉ lệ giá trị của pass là **10×–20× so với giá bán** — tương đương một mức giảm giá ~90% nếu mua trực tiếp từng món.

`[Design synthesis]` Con số này chỉ có nghĩa nếu **giá niêm yết của từng món trong pass là giá thật, thực sự bán được ở store**. Nếu bạn định giá món chỉ để làm phép nhân cho đẹp, con số 10–20× là hư cấu và player biết. Ràng buộc: **mọi item trong pass phải hoặc (a) từng được bán riêng ở mức giá đó, hoặc (b) không được tính vào phép nhân giá trị.**

Nghịch lý cần ý thức: tỉ lệ 10–20× nghĩa là pass là **món hời nhất trong game một cách áp đảo**. Đó là chủ ý — pass là công cụ retention trước, doanh thu sau. Nhưng nó cũng nghĩa là pass **cạnh tranh trực tiếp với chính store của bạn**. Xem §5.6.

### 5.6 Đóng góp doanh thu và rủi ro ăn thịt lẫn nhau

`[Market data]` Các con số công bố, có độ tin cậy khác nhau — đọc kèm cảnh báo:

```text
  ~60%      game top-grossing có battle pass  (GameRefinery, độ tin cậy khá)
  ~22%      tỉ trọng battle pass trong tổng doanh thu IAP của nhóm top-grossing
  10–40%    dải đóng góp doanh thu trong các title F2P top-grossing
  1–40%     dải rộng hơn được nêu khi phân tích từng game cụ thể
```

Dải 1–40% quá rộng để dùng làm benchmark; nó nói lên điều đúng hơn: **đóng góp của pass phụ thuộc gần như hoàn toàn vào việc economy còn CHỖ SÂU để chi tiêu hay không.**

Đây là bài học có bằng chứng đối chứng tốt nhất trong toàn bộ mục này `[Market data]`:

```text
  Clash Royale — doanh thu GIẢM tương quan với thời điểm ra mắt Pass.
                 Nguyên nhân được nêu: economy KHÔNG đủ chiều sâu chi tiêu,
                 nên Pass hút doanh thu từ các kênh đang có thay vì tạo mới.

  Clash of Clans — doanh thu tăng, vì Pass được ra mắt SAU KHI đã mở rộng economy.
```

> `[Design synthesis]` **Quy tắc rút ra: battle pass không tạo ra chiều sâu chi tiêu, nó KHAI THÁC chiều sâu sẵn có.** Trước khi ship pass, phải trả lời được: *"sau khi player mua pass và nhận hết thưởng, họ còn thứ gì đáng chi tiếp?"* Nếu câu trả lời là "không", pass sẽ ăn thịt doanh thu chứ không cộng thêm. Kiểm chứng bằng số trước khi ship: tính tổng giá trị (quy ra hard currency) mà pass phát ra trong một mùa, so với tổng sink hard currency khả dụng của một player trong cùng mùa. Nếu pass phát ra **> 30–40%** tổng sink, bạn đang trên đường ăn thịt chính mình.

---

## 6. Thiết kế bundle và offer

### 6.1 Độ sâu chiết khấu vs cannibalization

`[Design synthesis]` Bài toán cốt lõi: mỗi bundle giảm giá đều bán cho hai nhóm — nhóm **incremental** (đáng lẽ không mua) và nhóm **cannibalized** (đáng lẽ đã mua ở giá đầy đủ).

```text
  Đặt:
    d  = độ sâu chiết khấu (0.3 = giảm 30%)
    N₀ = số người đáng lẽ mua ở giá đầy đủ
    N(d) = tổng số người mua ở mức chiết khấu d
    P  = giá đầy đủ

  Doanh thu không bundle:  R₀ = N₀ · P
  Doanh thu có bundle:     R(d) = N(d) · P · (1−d)

  ĐIỀU KIỆN HÒA VỐN:
     N(d) / N₀  >  1 / (1 − d)

  → d = 20%  cần lift số người mua ≥ 1.25×
  → d = 30%  cần ≥ 1.43×
  → d = 50%  cần ≥ 2.00×
  → d = 70%  cần ≥ 3.33×
```

Ba hệ quả thực dụng `[Design synthesis]`:

- **Chiết khấu sâu chỉ hợp lý khi nhắm vào nhóm gần như chắc chắn KHÔNG mua ở giá đầy đủ** — non-payer, lapsed payer, player mới. Với nhóm đang mua đều, `N(d)/N₀` bị chặn trên bởi mức trần nhu cầu và gần như không bao giờ đạt 2×.
- **Vì thế chiết khấu sâu ⇒ BẮT BUỘC phải target.** Một sale 50% hiển thị cho toàn bộ population gần như chắc chắn lỗ.
- **Đo bằng holdout, không bằng doanh thu tuyệt đối.** Doanh thu ngày sale luôn tăng; câu hỏi duy nhất có nghĩa là doanh thu **7–14 ngày** của nhóm thấy offer so với nhóm không thấy. Sale kéo doanh thu tương lai về hiện tại là hiện tượng mặc định, không phải ngoại lệ.

### 6.2 Decoy bundle

`[Design synthesis]` Áp dụng §3.5 vào bundle, với cảnh báo replication đã nêu:

```text
Cấu trúc 3 lựa chọn, mục tiêu bán option B:
  A  $4.99   — 500 gem                          (neo dưới)
  B  $19.99  — 2.500 gem + skin + 3 booster     ← MỤC TIÊU (mặc định chọn sẵn)
  C  $19.99  — 2.500 gem                        (decoy: cùng giá, ít hơn hẳn B)

C bị B thống trị hoàn toàn → làm B trông như lựa chọn hiển nhiên.

NHƯNG: hiệu ứng thực tế nhiều khả năng đến từ (a) B được PRE-SELECT
và (b) A neo giá thấp, chứ không phải từ bản thân C.
→ Chạy A/B 4 nhánh (có/không C × có/không pre-select) để tách hai hiệu ứng.
   Nếu C không đóng góp, bỏ nó — mỗi option thừa đều tăng tải nhận thức
   và làm chậm quyết định.
```

### 6.3 Ràng buộc cho hệ sinh bundle cá nhân hóa

Đây là phần quan trọng nhất cho SDK. `[Design synthesis]` Một offer engine sinh bundle động **phải** có các ràng buộc cứng sau, nếu không nó sẽ tự tìm ra các cấu hình phá kinh tế:

```text
RÀNG BUỘC BẮT BUỘC cho mọi bundle sinh tự động:

 C1  Đơn điệu giá trị:   value($X) ≥ value($Y) với mọi X > Y
     — không bao giờ được sinh ra bundle đắt hơn mà ít giá trị hơn.

 C2  Trần chiết khấu:    d ≤ d_max theo segment
     — ví dụ d_max = 0.25 cho payer đang hoạt động, 0.60 cho lapsed non-payer.
     Không có trần này, engine sẽ hội tụ về việc giảm giá tối đa cho
     người sẵn sàng trả nhiều nhất, vì đó là nhóm phản hồi mạnh nhất
     trong dữ liệu ngắn hạn.

 C3  Sàn giá:            price ≥ p_min theo segment
     — bảo vệ neo giá (§3.3). Không được sinh $0.99 cho whale.

 C4  Nhất quán nội dung: một item chưa unlock ở progression
     không được xuất hiện trong bundle.

 C5  Trần tần suất:      ≤ K offer/ngày, ≤ M offer/session
     (xem §6.4)

 C6  Trần tổng cung:     tổng hard currency phát qua bundle trong 30 ngày
     ≤ ngưỡng theo player — nếu không, offer engine trở thành
     một SOURCE không kiểm soát và phá phương trình §1.2.

 C7  Không phân biệt giá theo thuộc tính được bảo vệ, và ở EU/UK
     phải kiểm tra lại C2/C3 với legal — personalized pricing là
     vùng xám pháp lý đang siết chặt, không phải vùng tự do kỹ thuật.
```

`[Design synthesis]` **C6 là ràng buộc bị bỏ sót nhiều nhất.** Một offer engine tối ưu conversion sẽ tăng dần độ hào phóng cho tới khi mọi người mua — và khi đó nó đã bơm vào economy một lượng currency vượt xa mọi source được thiết kế. Triệu chứng: soft/hard currency stock P90 tăng vọt, velocity sụt, mọi sink mất ý nghĩa, và doanh thu sụp sau 4–8 tuần **mà không ai liên hệ được với offer engine**, vì nó xảy ra chậm và ở một dashboard khác.

### 6.4 Thiết kế cooldown cho offer

`[Design synthesis]` Không tìm được nghiên cứu công khai về cooldown tối ưu. Khung thiết kế đề xuất, dùng làm điểm khởi đầu để A/B:

```text
Ba tầng cooldown, phải có CẢ BA:
  1. Per-offer:     một offer cụ thể không hiện lại trong T₁ (ví dụ 24–72h)
                    sau khi bị từ chối/hết hạn.
  2. Per-slot:      một VỊ TRÍ hiển thị (ví dụ popup sau khi thua level)
                    có trần tần suất riêng, độc lập với nội dung offer.
  3. Per-player:    trần tổng số offer/ngày, bất kể loại.

Lý do cần cả ba: hai tầng đầu độc lập nhau sẽ vẫn cho phép
"offer khác nhau ở cùng một chỗ, liên tục" — đó chính là trải nghiệm
mà player mô tả là "game này chỉ toàn popup bán hàng".

Backoff sau từ chối liên tiếp (đề xuất):
  T(n) = T₁ · γ^(n−1),   γ ≈ 1.5–2.0,  n = số lần từ chối liên tiếp
  Reset n về 0 sau MỘT lần mua bất kỳ.
```

Trigger phổ biến trong ngành: giảm giá cá nhân hóa kích hoạt sau khi player không mua qua N session (ví dụ 3 session), kèm countdown tạo cảm giác cấp bách `[Market data]`. `[Design synthesis]` Cảnh báo: countdown giả (đếm ngược rồi offer quay lại y hệt) là **dark pattern** và nằm trong phạm vi mà EU CPC Key Principles và FTC đang mở rộng chú ý. Nếu dùng countdown, offer **phải** thực sự biến mất.

### 6.5 "Value stacking" — trình bày

`[Design synthesis]` Cách trình bày một bundle ảnh hưởng đến giá trị cảm nhận độc lập với nội dung. Các quy tắc trình bày, xếp theo mức độ vững chắc của bằng chứng:

```text
VỮNG (anchoring, default effect — bằng chứng thực nghiệm tốt):
  • Hiển thị giá gạch ngang (giá gốc) cạnh giá bán — nhưng giá gốc
    PHẢI là giá thật từng bán. Giá gốc bịa là rủi ro pháp lý ở US/EU.
  • Pre-select option mục tiêu.
  • Hiển thị "% tiết kiệm" thay vì "số tiền tiết kiệm" khi giá thấp,
    và ngược lại khi giá cao (quy tắc 100 của Jonah Berger).

TRUNG BÌNH:
  • Liệt kê 3–5 item, không nhiều hơn — mỗi dòng thêm làm giảm
    mức chú ý dành cho các dòng trước.
  • Đặt item CẢM XÚC (skin, hero) ở đầu danh sách, item SỐ LƯỢNG
    (currency) ở cuối.

YẾU / cần A/B:
  • Decoy option (§6.2).
  • Badge "phổ biến nhất" / "giá trị tốt nhất" — social proof có
    bằng chứng tốt nói chung, nhưng badge sai sự thật là dark pattern.
```

---

## 7. Kinh tế học whale

### 7.1 Hình dạng phân bố chi tiêu — bằng chứng tốt nhất hiện có

Nghiên cứu quy mô lớn nhất công khai được: *The Many Faces of Monetisation* (Games: Research and Practice, ACM, 2023) `[Primary research]`:

```text
  Dữ liệu: $4.7 TỶ chi tiêu in-game
           69.144.363 player
           2.873 game mobile
           624 ngày

Phát hiện chính:
  • KHÔNG có một hình dạng phân bố duy nhất. Các game phân thành nhiều
    cụm, từ cụm "uniform" (mọi spender chi xấp xỉ như nhau) tới cụm
    "hyper-Pareto" — nơi ~38% doanh thu đến từ RIÊNG 1% spender.
  • Game càng phụ thuộc top 1%, các cá nhân đó càng chi nhiều
    (quan hệ củng cố lẫn nhau, không phải tuyến tính).
  • Trường hợp cực đoan nhất: top 1% chi trung bình $66.285/người
    trong 624 ngày.
  • Sản phẩm mô phỏng cờ bạc (social casino) nằm ở đỉnh
    của mức độ tập trung doanh thu.
```

`[Market data]` Con số được trích dẫn nhiều nhất trong ngành — Swrve: **0,15% player tạo ra 50% doanh thu IAP**. Cùng nghiên cứu đó ghi nhận giá trị giao dịch trung bình ~$6, giao dịch $1–$5 chiếm **67% số lượng giao dịch**, và giao dịch $50 chiếm **0,7% số lượng nhưng 9% doanh thu**.

`[Design synthesis]` **Đây là chỗ cần suy nghĩ cẩn thận, không phải chỗ để lặp lại "80/20".**

```text
Ba hiểu lầm cần loại bỏ:

1. "Pareto 80/20 là quy luật của F2P."
   SAI. Nghiên cứu ACM cho thấy phân bố là MỘT LỰA CHỌN THIẾT KẾ,
   không phải một quy luật tự nhiên. Cùng một thị trường, có game
   uniform và có game hyper-Pareto. Bạn CHỌN mình ở đâu, thông qua
   việc có hay không đuôi chi tiêu vô hạn (§4.5 kiến trúc A).

2. "0,15% → 50% nghĩa là hãy tối ưu cho whale."
   SAI VỀ LOGIC. Con số này mô tả PHÂN BỐ ĐÃ XẢY RA, không mô tả
   độ co giãn. Nó không nói rằng đầu tư thêm vào whale sinh lời hơn
   đầu tư vào mid-spender.

3. "Whale là một segment ổn định."
   SAI. Whale là một TRẠNG THÁI, không phải một loại người.
   Player đi vào và đi ra khỏi trạng thái đó. Nguồn cung whale
   tương lai là nhóm mid-spender hôm nay.
```

### 7.2 Rủi ro tập trung và cách lượng hóa

`[Design synthesis]` Hai chỉ số cần đo hằng tuần, không có trong dashboard mặc định của bất kỳ analytics nào:

```text
  Chỉ số tập trung:
     R₁  = tỉ trọng doanh thu từ top 1% spender
     R₁₀ = tỉ trọng doanh thu từ top 10% spender
     Gini của chi tiêu trong nhóm spender

  Chỉ số rủi ro tập trung (đề xuất của tôi):
     Concentration Risk = R₁ / (churn-adjusted headcount của top 1%)

     Diễn giải: nếu top 1% là 500 người tạo 38% doanh thu, và
     churn tháng của nhóm đó là 8%, bạn đang mất ~3% TỔNG doanh thu
     mỗi tháng chỉ do churn tự nhiên của nhóm này — và phải thay thế
     bằng cách "nuôi" whale mới từ pipeline mid-spender.

  → Chỉ số cần theo dõi song song: TỐC ĐỘ THĂNG HẠNG
     (số mid-spender chuyển lên nhóm top 1% mỗi tháng)
     phải ≥ số whale rời đi. Nếu không, doanh thu sẽ suy giảm
     với độ trễ 2–4 tháng, sau khi mọi chỉ số bề mặt vẫn còn đẹp.
```

### 7.3 Thiết kế chương trình VIP

`[Market data]` Cấu trúc chuẩn: tier theo tổng chi tiêu tích lũy, đặc quyền leo thang (tiến độ nhanh hơn, truy cập sớm, event riêng, support riêng), kèm biểu tượng địa vị hiển thị công khai (badge, khung avatar).

`[Design synthesis]` Bốn nguyên tắc thiết kế VIP, và một cảnh báo:

```text
 1. Tier dựa trên CHI TIÊU TÍCH LŨY, không bao giờ reset về 0.
    Reset tier là cách nhanh nhất để mất một whale — nó biến
    khoản đã chi thành lãng phí hồi tố (sunk-cost bị phản bội).
    Nếu cần cơ chế theo mùa, dùng một lớp "season status" RIÊNG,
    chồng lên tier vĩnh viễn.

 2. Đặc quyền nên nghiêng về TIỆN LỢI và ĐỊA VỊ, hạn chế SỨC MẠNH.
    Lý do: đặc quyền sức mạnh làm phình power gap (vi phạm §1.6)
    và tạo áp lực pay-to-win — nhạy cảm nhất ở US/EU.

 3. Support riêng là đặc quyền có ROI cao nhất và bị đánh giá thấp nhất.
    Các nguyên nhân được ghi nhận làm nhóm LTV cao ngừng chi:
    ticket support không được giải quyết, hoàn tiền chậm,
    giao dịch mua bị thất lạc.

 4. KHÔNG gửi cùng một thông điệp khuyến mãi cho nhóm LTV cao
    và cho non-spender. Nó phát tín hiệu "địa vị thấp" và
    đẩy nhanh churn ở nhóm giá trị cao.
```

### 7.4 Mối nguy: thiết kế tối ưu cho whale làm hỏng mid-spender

`[Design synthesis]` Đây là dạng hỏng hóc chậm và nguy hiểm nhất trong F2P. Cơ chế:

```text
Vòng lặp hỏng:

  Bước 1  Doanh thu tập trung ở whale → team ưu tiên feature cho whale
  Bước 2  Feature cho whale = nội dung đắt, power creep, đuôi chi tiêu sâu
  Bước 3  Power creep làm mid-spender thấy khoản chi của mình vô nghĩa
          (chi $50 vẫn thua người chi $5.000)
  Bước 4  Mid-spender ngừng chi → tỉ trọng whale TĂNG
  Bước 5  Số liệu càng "chứng minh" rằng whale là tất cả → quay lại Bước 1

Sau 3–4 vòng: doanh thu tuyệt đối giảm, tỉ trọng whale tăng lên >60%,
population nền rỗng, PvP matchmaking hỏng, UA không còn hiệu quả
(vì người mới không thấy đường tiến), và game rơi vào vòng xoáy tử vong.

CHỈ SỐ CẢNH BÁO SỚM — theo dõi hằng tháng:
  • R₁ TĂNG trong khi doanh thu tuyệt đối ĐỨNG YÊN hoặc giảm
    → đây là chữ ký của vòng lặp trên. R₁ tăng chỉ lành mạnh khi
      doanh thu tuyệt đối cũng tăng.
  • Tỉ lệ repeat-purchase của nhóm chi $10–$100 giảm
  • Thời gian trung bình từ lần mua 1 → lần mua 2 kéo dài ra
  • Tỉ lệ thăng hạng mid → top giảm (§7.2)

BIỆN PHÁP ĐỐI TRỌNG (phải có, không phải nên có):
  • Đặt TRẦN CỨNG cho power gap: chênh lệch sức mạnh tối đa giữa
    người chi $0 và người chi không giới hạn phải là một CON SỐ
    được viết trong design doc, được review mỗi khi thêm nội dung.
  • Dành một phần ngân sách nội dung mỗi mùa CHỈ cho mid-spender
    (nội dung giá $5–$20 có giá trị cảm nhận cao, không đụng balance).
  • Đưa "doanh thu từ nhóm P50–P90 spender" thành một KPI riêng
    trong báo cáo, ngang hàng với tổng doanh thu.
```

---

## 8. Thiết kế lịch LiveOps

### 8.1 Nhịp và mật độ

`[Market data]` Các con số mật độ được nhà cung cấp dữ liệu nêu (độ tin cậy trung bình, phương pháp không đầy đủ):

```text
  Trung bình event/tháng tăng từ ~73 lên ~89 (theo báo cáo 2025)
  Puzzle/casual top: 80–100 điểm chạm event/tháng qua nhiều lớp chồng nhau
  Mid-core: 20–40 điểm chạm/tháng
  Chuẩn vàng về thời lượng: event cuối tuần 72 giờ (Thứ Sáu → Chủ Nhật)
  Khoảng nghỉ giữa hai event cạnh tranh: 12–24 giờ
```

`[Market data]` GameRefinery theo dõi **35+ loại live event**. Ví dụ độ phủ theo loại: win-streak event xuất hiện ở **45% top puzzle title**.

`[Design synthesis]` Con số 80–100 điểm chạm/tháng dễ bị hiểu sai. Nó **không** có nghĩa là 80–100 event riêng biệt. Nó là tổng của nhiều **lớp** chồng lên nhau, và cách đúng để nghĩ về lịch là theo lớp:

```text
KIẾN TRÚC LỊCH THEO LỚP:

  Lớp 0 — Nền (luôn bật):     daily reward, daily quest, energy regen
                              → không phải "event", là nhịp cơ sở

  Lớp 1 — Tuần (1–2 cái/lúc): tournament, win-streak, team event
                              → trục engagement chính

  Lớp 2 — Mùa (1 cái/lúc):    battle pass, collection album
                              → trục retention dài hạn, KHÔNG bao giờ chồng
                                hai cái cùng lúc

  Lớp 3 — Đỉnh (1–3 cái/tháng): limited-time mode, event theo lễ,
                                banner/gacha giới hạn
                              → trục doanh thu

  Lớp 4 — Offer:               IAP offer gắn với từng lớp trên

Quy tắc: mỗi lớp có TRẦN SỐ LƯỢNG ĐỒNG THỜI riêng.
Số "điểm chạm/tháng" là kết quả của phép nhân giữa các lớp,
không phải một tham số bạn set trực tiếp.
```

### 8.2 Phân loại event theo chức năng

`[Design synthesis]` Phân loại theo **thứ event yêu cầu ở player**, vì đó mới là thứ quyết định xung đột:

```text
  Loại              Yêu cầu chính     Trục         Rủi ro chính
  ────────────────────────────────────────────────────────────────────
  Collection        thời gian + may   retention    tốn thời gian, dễ chán
  Tournament/PvP    kỹ năng + thời gian doanh thu  áp lực, whale thống trị
  Win-streak        kỹ năng           engagement   frustrate player yếu
  Limited-time mode tính mới lạ       engagement   chi phí sản xuất cao
  Co-op / team      LỊCH RẢNH ĐỒNG BỘ retention    xung đột lịch nặng nhất
  Idle/tích lũy     chỉ cần log-in    retention    giá trị cảm nhận thấp
  Banner/gacha giới hạn  TIỀN         doanh thu    ăn thịt mọi thứ khác
```

**Quy tắc xung đột `[Design synthesis]`:** hai event **cùng đòi hỏi một nguồn lực khan hiếm** không được chồng nhau.

```text
Ma trận xung đột — điền cho lịch của bạn trước mỗi mùa:

                    Thời gian  Energy  Soft cur.  Hard cur.  Kỹ năng
  Collection            ●●        ●●       ●          ○         ○
  Tournament            ●●        ●●       ○          ●        ●●
  Co-op                 ●●●       ●●       ○          ●         ●
  Banner giới hạn        ○         ○       ○         ●●●        ○
  Battle pass           ●●        ●●       ○          ●         ●

  ●●● = tiêu thụ nặng   ●● = trung bình   ● = nhẹ   ○ = không

  LUẬT: không chồng hai event có ●●● hoặc ●● trên CÙNG MỘT CỘT.
  Ngoại lệ duy nhất được phép: banner giới hạn (cột hard currency)
  có thể chồng với hầu hết mọi thứ, vì nó không cạnh tranh thời gian.
  Đó chính là lý do banner là công cụ doanh thu linh hoạt nhất.
```

### 8.3 Event fatigue

`[Market data]` Các mô tả practitioner nhất quán về cơ chế:

```text
  • Khi event không bao giờ dừng, tham gia trở thành NGHĨA VỤ,
    và nghĩa vụ cuối cùng dẫn tới churn.
  • Chạy event chu kỳ 3–4 ngày KHÔNG có khoảng nghỉ tạo fatigue.
  • Player cảm thấy "không theo kịp" KHÔNG giảm engagement từ từ —
    họ dừng HOÀN TOÀN.
  • Fatigue là chỉ số TRỄ: khi nó hiện ra trên đường cong retention,
    những player bị ảnh hưởng nặng nhất đã rời đi từ trước.
  • Nguyên nhân nằm ở NHỊP và PHÂN KHÚC, không ở bản thân offer.
```

`[Design synthesis]` Vì fatigue là chỉ số trễ, cần **chỉ số dẫn dắt (leading indicator)**. Bốn đề xuất, đo được từ ngày đầu:

```text
 1. Tỉ lệ HOÀN THÀNH event (không phải tỉ lệ THAM GIA).
    Tham gia cao + hoàn thành thấp = player đang bắt đầu rồi bỏ giữa chừng
    = tín hiệu fatigue sớm nhất và rõ nhất.

 2. Độ trễ tham gia: số giờ từ khi event mở tới khi player tham gia lần đầu.
    Độ trễ TĂNG dần qua các event liên tiếp = mất động lực.

 3. Tỉ lệ opt-out push notification.
    Tăng đột biến trong tuần có ≥3 event đồng thời = quá tải thông báo.

 4. Số event đồng thời mà một player CÁ NHÂN nhìn thấy
    (không phải số event bạn chạy). Với segmentation, hai con số này
    khác nhau rất xa — và chỉ con số cá nhân mới có ý nghĩa.
```

### 8.4 Mật độ lịch vs ARPDAU vs churn

`[Design synthesis]` Không tồn tại một nghiên cứu công khai định lượng đánh đổi này. Khung mô hình để tự đo:

```text
  Với mật độ event d (số event đồng thời trung bình mà một player thấy):

     ARPDAU(d)  — tăng, nhưng lợi ích giảm dần:  a·ln(1+d)
     Churn(d)   — gần như phẳng tới ngưỡng d*, rồi tăng nhanh:
                  c₀ + c₁·max(0, d − d*)^2

     LTV(d) ∝ ARPDAU(d) / Churn(d)

  → LTV có một CỰC ĐẠI NỘI TẠI. Nó gần như luôn nằm ở mật độ THẤP HƠN
    mật độ tối đa hóa ARPDAU. Team nào tối ưu ARPDAU sẽ luôn
    vượt quá điểm tối ưu LTV — đây là lỗi cấu trúc của KPI,
    không phải lỗi phán đoán.

  → d* KHÁC NHAU THEO SEGMENT. Với whale d* cao (họ muốn nhiều nội dung);
    với casual d* thấp. Đây là lập luận mạnh nhất cho việc
    CÁ NHÂN HÓA MẬT ĐỘ LỊCH, không chỉ cá nhân hóa offer.
```

---

## 9. Thiết kế ad monetization trong game hybrid

### 9.1 Rewarded video: thiết kế vị trí

`[Design synthesis]` Nguyên tắc phân biệt phần thưởng "xứng đáng" với phần thưởng "cưỡng ép":

```text
Một rewarded placement là XỨNG ĐÁNG nếu thỏa CẢ BA:
  (a) Player ĐANG ở một điểm quyết định tự nhiên
      (vừa thua, vừa thắng, đang chọn nâng cấp) — không bị ngắt giữa dòng.
  (b) Phần thưởng GIẢI QUYẾT vấn đề mà player VỪA gặp
      (thua → thêm nước; hết energy → thêm energy).
  (c) Từ chối KHÔNG làm player mất thứ gì họ đã có.

Nó là CƯỠNG ÉP nếu:
  • Nó là con đường duy nhất qua một tường (§2.3)
  • Từ chối làm mất streak/tiến độ đã tích được
  • Nó xuất hiện trước khi player kịp hiểu mình đang được đề nghị gì
```

Các vị trí rewarded chuẩn, theo thứ tự "cảm giác xứng đáng" giảm dần `[Design synthesis]`: *continue sau khi thua* → *nhân đôi phần thưởng cuối màn* → *bỏ qua thời gian chờ* → *free spin/box hằng ngày* → *dùng thử booster* → *hồi energy*.

### 9.2 Công thức định cỡ phần thưởng

`[Design synthesis]` Đây là phép tính bắt buộc trước khi chốt bất kỳ reward nào:

```text
  eCPM  = doanh thu trên 1.000 lượt hiển thị
  → Doanh thu trên 1 lượt xem:  R_ad = eCPM / 1000

  Giá trị phần thưởng quy ra tiền:  V_reward
  → ĐIỀU KIỆN KINH TẾ BẮT BUỘC:     V_reward < R_ad

  Ví dụ số học:
    eCPM $15  →  R_ad = $0.015/lượt xem
    Nếu bạn thưởng 100 gem và tỉ giá là $1 = 100 gem,
    thì V_reward = $1.00  >>  $0.015
    → BẠN ĐANG PHÁT TIỀN. Sai lệch 66 LẦN.

  Quy tắc an toàn đề xuất:  V_reward ≤ 0.3 × R_ad
  (biên an toàn cho biến động eCPM theo mùa/theo geo và cho fill rate)

  → Với eCPM $15: trần phần thưởng ≈ $0.0045 ≈ 0.45 gem.
    Con số này nghe "keo kiệt" — và đó chính là lý do
    rewarded video KHÔNG NÊN thưởng hard currency trực tiếp.
```

`[Design synthesis]` **Hệ quả thiết kế quan trọng nhất của mục 9:** phần thưởng rewarded video nên là thứ có **giá trị cảm nhận cao nhưng giá trị kinh tế thấp**:

```text
  TỐT:   thêm 5 nước đi (giá trị cảm nhận: cứu cả level;
                          giá trị kinh tế: gần 0 nếu chỉ dùng một lần)
         mở khóa 1 lần thử nhân vật premium
         nhân đôi phần thưởng của MỘT màn (số tuyệt đối nhỏ)
         một lượt quay vòng quay với phần thưởng phần lớn là soft currency

  XẤU:   X đơn vị hard currency (định lượng được, so sánh trực tiếp
                                  với giá IAP → phá neo giá)
         energy đầy (cạnh tranh trực tiếp với refill $0.99)
         item vĩnh viễn ảnh hưởng balance
```

### 9.3 Nghiên cứu về cannibalization ads ↔ IAP

`[Market data]` / `[Case study — self-reported]` Bằng chứng hiện có, kèm cảnh báo về chất lượng:

```text
  • Khảo sát ngành: 86% developer có cả ads và IAP báo cáo mức chi IAP
    GIỮ NGUYÊN hoặc TĂNG sau khi thêm rewarded ads.
    → Đây là khảo sát TỰ BÁO CÁO, có thiên lệch sống sót rõ rệt
      (studio bị cannibalize thường đã gỡ ads và không trả lời khảo sát).

  • Tapjoy: user tương tác với rewarded ads có khả năng mua IAP
    cao gấp ~4.5×. Các nguồn khác nêu 4.5×–6×.
    → ĐÂY LÀ TƯƠNG QUAN, KHÔNG PHẢI NHÂN QUẢ. Người tương tác với
      rewarded ads là người engaged; người engaged mua nhiều hơn.
      Con số này KHÔNG chứng minh rewarded ads gây ra việc mua.

  • Unity: thêm ads vào game trước đó chỉ có IAP làm D7 retention
    tăng 2.3% và tỉ lệ người mua ít nhất một lần tăng 1.1%.
    → Có hướng và độ lớn hợp lý, nhưng phương pháp không công khai đủ.
```

`[Design synthesis]` **Cách suy nghĩ đúng về cannibalization**, độc lập với các con số trên:

```text
Cannibalization xảy ra khi phần thưởng của rewarded ad cạnh tranh
TRỰC TIẾP ở CÙNG MỘT LỚP của economy với thứ bạn đang bán.

  Cannibalize:      ad tặng energy, và bạn đang bán energy refill
  KHÔNG cannibalize: ad tặng gợi ý/hint, còn bạn bán gói tiến độ

Lập luận sắc bén nhất tôi gặp được: một player xem ad để đổi lấy hint
vừa TỰ XÁC NHẬN VỚI CHÍNH MÌNH rằng hint có giá trị thật.
Đó không phải cannibalization — đó là priming.

→ QUY TẮC KIẾN TRÚC: lập bản đồ mọi rewarded reward và mọi IAP
  lên một bảng "lớp economy". Bất kỳ ô nào có CẢ HAI = rủi ro
  cannibalization, phải A/B với holdout trước khi ship.
```

### 9.4 Nhịp interstitial

`[Market data]` Các khuyến nghị lưu hành (nguồn phần lớn là blog vendor — độ tin cậy thấp, dùng làm prior):

```text
  • >1 interstitial / 2 phút session → D7 retention giảm tới ~20%
  • Interstitial mỗi màn trong casual puzzle → 15–25% player bỏ
    ngay trong session đầu
  • Trần khuyến nghị: 4–6 interstitial/session (lợi ích giảm mạnh sau cái thứ 5–6)
  • Tối thiểu 1 interstitial trên mỗi 3–5 hành động
  • Cách nhau tối thiểu 3–5 phút hoặc 2–3 màn
  • Lỗi phổ biến nhất: hiện interstitial trong 30 GIÂY ĐẦU của session
    của một user MỚI
```

`[Design synthesis]` Khung thiết kế nhịp interstitial nên là một **hàm của trạng thái**, không phải một hằng số:

```text
  gate(player, t) = TRUE  chỉ khi TẤT CẢ đúng:
     • t − t_last_interstitial  ≥  T_min          (trần thời gian)
     • n_session                <  N_max          (trần số/session)
     • age_days                 ≥  A_min          (bảo vệ user mới)
     • KHÔNG ở trong: tutorial / vừa mua IAP / vừa thua 3 lần liên tiếp
     • đang ở ranh giới tự nhiên (kết thúc màn, quay về menu)

  Ba tham số cần A/B riêng cho từng segment:
     T_min ∈ [90s, 300s]
     N_max ∈ [2, 6]
     A_min ∈ [0, 3 ngày]

  Với A_min: bảo vệ user mới KHÔNG phải là lịch sự, mà là kinh tế.
  Interstitial sớm đánh vào cohort chưa xác định LTV, gồm cả
  những người sẽ trở thành payer. eCPM của một impression là ~$0.01;
  một payer tương lai đáng giá gấp hàng trăm lần.
```

### 9.5 Căng thẳng "mua để tắt quảng cáo"

`[Design synthesis]` Đây là một trong những quyết định khó nhất của hybrid monetization, vì nó có ba lực đối nghịch:

```text
  Lực 1: "Remove ads" là IAP có conversion CAO NHẤT ở nhiều casual game
         — giá trị rõ ràng, không cần hiểu game, một lần trả.
  Lực 2: Nó VĨNH VIỄN giết doanh thu ads của chính người có
         xu hướng chi tiền nhất — tức nhóm giá trị nhất.
  Lực 3: KHÔNG có nó thì người trả tiền vẫn thấy quảng cáo,
         gây hối hận sau mua (xem doc 27 §B.3 — case đối chứng thật).

Giải pháp phân tầng (được practitioner mô tả và tôi ủng hộ):
  Tầng 1  gói rẻ / bất kỳ IAP nào → tắt các format GÂY GIÁN ĐOẠN
                                     (interstitial, app-open)
                                     GIỮ banner/native + GIỮ rewarded
  Tầng 2  gói "remove ads" đắt hơn → tắt toàn bộ format phi-rewarded

  Rewarded video KHÔNG BAO GIỜ bị tắt bởi bất kỳ gói nào.
  Lý do: nó là opt-in, player chủ động chọn, và với payer nó vẫn là
  một công cụ tiện lợi. Tắt nó là lấy đi một lựa chọn, không phải
  gỡ bỏ một phiền toái.

Quy tắc bắt buộc: sau khi mua BẤT KỲ gói nào, trải nghiệm quảng cáo
PHẢI thay đổi một cách người dùng CẢM NHẬN ĐƯỢC NGAY.
Vi phạm quy tắc này là nguyên nhân trực tiếp của post-purchase regret,
và nó không hiện ra trong conversion — nó hiện ra ở
tỉ lệ mua LẦN THỨ HAI, vài tuần sau.
```

---

## 10. Checklist review economy trước khi ship

`[Design synthesis]` Chạy toàn bộ checklist này trước mỗi lần ship economy mới hoặc thay đổi lớn. Mỗi mục phải trả lời được bằng **một con số hoặc một câu khẳng định kiểm chứng được**, không phải bằng ý kiến.

### A. Cân bằng cơ bản

```text
[ ] A1  Mọi currency đã được liệt kê ĐẦY ĐỦ source và sink, kể cả
        compensation gift, refund, và grant từ offer engine?
[ ] A2  SCR (§1.2) đã được tính RIÊNG cho từng cohort tuổi tài khoản?
        Có ô nào < 0.8 hoặc > 1.2 không?
[ ] A3  Mỗi currency có ít nhất MỘT exponential sink không?
        (nếu không, nó sẽ lạm phát ở late game — đây là định lý, không phải rủi ro)
[ ] A4  Có converter HAI CHIỀU nào giữa hai currency không?
        Nếu có: hai currency đó phải được coi là MỘT trong mọi phép tính.
[ ] A5  Stock P90 / Stock trung vị của mỗi currency là bao nhiêu?
        > 20 là cờ đỏ.
[ ] A6  "$/giờ tiết kiệm được" (§1.5) là bao nhiêu ở early / mid / late game?
        Nó có TĂNG theo tiến độ không? (giảm = sai)
```

### B. Progression

```text
[ ] B1  Đường XP dùng công thức gì, tham số bao nhiêu? Ai chốt và dựa trên gì?
[ ] B2  Time-to-level tính bằng median rate (~70% optimal) hay optimal rate?
[ ] B3  Đã vẽ chồng 3 đường (win rate / booster stock / energy) chưa?
        Mọi ĐIỂM GIAO BA đã được đánh dấu và phân loại
        "có chủ đích" / "tình cờ" chưa? (§2.3)
[ ] B4  Sau mỗi đỉnh khó có ít nhất 2 màn dễ không?
[ ] B5  Vị trí gate đầu tiên đã được A/B chưa? Cỡ mẫu ≥ 50k/nhánh?
[ ] B6  Energy: T_full, N_day, S, p_play là bao nhiêu? (§2.4)
        Cap có vượt quá 1 session mục tiêu không?
[ ] B7  Nếu battle pass/VIP tăng energy cap: sink progression
        đã được nới tương ứng chưa?
```

### C. Pricing

```text
[ ] C1  v(p) = value/dollar có ĐƠN ĐIỆU KHÔNG GIẢM trên toàn thang giá không?
        (kiểm tra bằng bảng, không bằng trí nhớ)
[ ] C2  Starter pack có phải pack giá trị/đô cao nhất game không?
        (nếu CÓ → đang phá neo giá, xem §3.3)
[ ] C3  Giá trị "thêm" của starter pack đến từ item độc quyền
        hay từ hard currency? (hard currency = rủi ro neo giá)
[ ] C4  Mọi "giá gốc" gạch ngang có phải giá THẬT từng bán không?
[ ] C5  Đã có ít nhất 2–3 tier giá cho EU chưa? Legal đã review chưa?
[ ] C6  $1 offer (nếu có) được đo bằng doanh thu D30/D60 có holdout,
        hay chỉ bằng conversion rate của chính nó?
```

### D. Randomization (gacha/loot box)

```text
[ ] D1  Đã tính E[lượt], trung vị, P(≤n) cho BỘ THAM SỐ CỦA MÌNH chưa?
        (không copy từ game khác)
[ ] D2  Chi phí kỳ vọng bằng USD để sở hữu item mục tiêu là bao nhiêu?
        Có nằm trong design doc không?
[ ] D3  Có trần chi phí TUYỆT ĐỐI không (hard pity hoặc shard)?
        Trần đó là bao nhiêu USD?
[ ] D4  Odds có hiển thị ở màn hình quay, trong MỘT chạm không?
[ ] D5  Loot box có mua được TRỰC TIẾP bằng tiền thật không?
        (ràng buộc FTC — bắt buộc cho US)
[ ] D6  Có hiển thị giá trị tiền thật kèm giá virtual currency không?
        (kỳ vọng EU CPC)
[ ] D7  Có cơ chế chặn bán loot box cho user < 16 tuổi không có
        đồng thuận phụ huynh không? (ràng buộc FTC)
[ ] D8  Kiến trúc dupe là A, B hay C (§4.5)? Lựa chọn đó có được
        ghi rõ lý do không?
```

### E. Battle pass

```text
[ ] E1  Tổng XP là ĐẦU RA (giải ngược từ dữ liệu engagement)
        hay ĐẦU VÀO (ai đó chọn một con số tròn)?
[ ] E2  Đã chia cho số ngày HOẠT ĐỘNG kỳ vọng, không phải số ngày mùa?
[ ] E3  Target completion đã đặt theo phân vị (P25/P50/P90) chưa?
[ ] E4  Free track trả về bao nhiêu % giá pass? (mục tiêu 60–90%)
[ ] E5  Cơ chế catch-up là gì? Truy lĩnh có phải mặc định không?
[ ] E6  Tổng giá trị pass phát ra so với tổng sink hard currency
        trong mùa là bao nhiêu %? (> 30–40% = rủi ro cannibalization)
[ ] E7  Có item ảnh hưởng balance nào CHỈ có ở premium track không?
        (nếu game có PvP → phải là KHÔNG)
```

### F. Offer & bundle

```text
[ ] F1  Với mỗi mức chiết khấu đang dùng, lift cần thiết để hòa vốn
        là bao nhiêu? (§6.1) Có dữ liệu cho thấy đạt được không?
[ ] F2  Chiết khấu > 30% có được TARGET không, hay hiển thị đại trà?
[ ] F3  Offer engine đã có đủ C1–C7 (§6.3) chưa?
        Đặc biệt C6 (trần tổng cung) — hay bị bỏ sót nhất.
[ ] F4  Có đủ CẢ BA tầng cooldown (per-offer / per-slot / per-player) không?
[ ] F5  Countdown có phải countdown THẬT không (offer thực sự biến mất)?
[ ] F6  Số offer TỐI ĐA mà một player cá nhân có thể thấy trong 1 ngày
        là bao nhiêu? Ai đã thực sự chơi thử ở mức đó?
```

### G. Whale & phân bố

```text
[ ] G1  R₁ và R₁₀ hiện tại là bao nhiêu? Xu hướng 6 tháng?
[ ] G2  R₁ có đang TĂNG trong khi doanh thu tuyệt đối đứng yên không?
        (chữ ký của vòng xoáy §7.4)
[ ] G3  Tốc độ thăng hạng mid → top có ≥ tốc độ churn của top không?
[ ] G4  Power gap tối đa giữa $0 và chi không giới hạn là một CON SỐ
        được viết ra chưa? Ai review nó khi thêm nội dung?
[ ] G5  Phân bố power của F2P và payer có chồng lấn không?
        (P90 F2P vs P10 payer — §1.6)
[ ] G6  "Doanh thu từ nhóm P50–P90 spender" có phải một KPI riêng không?
[ ] G7  VIP tier có bao giờ reset không? (phải là KHÔNG)
```

### H. LiveOps

```text
[ ] H1  Lịch đã được vẽ theo LỚP (§8.1) chưa? Mỗi lớp có trần
        số lượng đồng thời riêng chưa?
[ ] H2  Ma trận xung đột (§8.2) đã điền chưa? Có ô nào vi phạm
        luật "không chồng hai ●● trên cùng một cột" không?
[ ] H3  Số event đồng thời mà MỘT PLAYER CÁ NHÂN thấy (không phải
        số event bạn chạy) là bao nhiêu ở P50 và P90?
[ ] H4  Đang theo dõi tỉ lệ HOÀN THÀNH event, hay chỉ tỉ lệ THAM GIA?
[ ] H5  Tỉ lệ opt-out push có được theo dõi theo tuần, đối chiếu
        với mật độ event tuần đó không?
[ ] H6  Mật độ lịch có được cá nhân hóa theo segment không? (§8.4)
```

### I. Quảng cáo (hybrid)

```text
[ ] I1  Với mỗi rewarded placement: V_reward và R_ad là bao nhiêu?
        V_reward ≤ 0.3 × R_ad có đúng không? (§9.2)
[ ] I2  Có rewarded nào thưởng hard currency trực tiếp không?
        (nên là KHÔNG)
[ ] I3  Đã lập bảng ánh xạ rewarded-reward ↔ IAP lên "lớp economy" chưa?
        Ô nào có cả hai? Đã A/B chưa?
[ ] I4  T_min, N_max, A_min của interstitial là bao nhiêu?
        A_min > 0 chứ? (bảo vệ user mới)
[ ] I5  Có interstitial nào có thể xuất hiện trong 30 giây đầu
        của session đầu tiên không? (phải là KHÔNG)
[ ] I6  Sau khi mua BẤT KỲ IAP nào, trải nghiệm quảng cáo có thay đổi
        theo cách người dùng cảm nhận được NGAY không?
[ ] I7  Rewarded video có bị tắt bởi gói "remove ads" không?
        (nên là KHÔNG)
```

### J. Kiểm chứng tổng thể

```text
[ ] J1  Đã chạy mô phỏng Monte Carlo với ≥3 persona chưa? (§1.6)
[ ] J2  Đã đọc PHÂN BỐ (P10/P50/P90) hay chỉ đọc trung bình?
[ ] J3  Với mỗi thay đổi lớn, có holdout group không?
[ ] J4  Có ai trong team thực sự CHƠI game ở trạng thái F2P thuần,
        từ đầu, trong ít nhất 2 tuần, KHÔNG dùng tài khoản admin không?
[ ] J5  Mọi con số benchmark dùng để quyết định có được gắn nhãn
        nguồn không? Có bao nhiêu trong số đó là "ai đó nói thế"?
```

---

## Không xác minh được (ghi rõ)

Các con số/khẳng định sau lưu hành rộng rãi trong ngành nhưng tôi **không** truy được về một primary source kiểm chứng được. Chúng **không** được dùng làm cơ sở quyết định trong doc này:

1. **"~1,8% player F2P từng chi tiền"** — con số bị lặp lại khắp nơi; các phân tích truy nguồn kết luận không có primary source. Số duy nhất có nguồn rõ ràng là AppsFlyer: install-to-purchase 2,6% trong 30 ngày (Q1 2022, tính theo install, không theo DAU).
2. **Win rate mục tiêu cụ thể cho level match-3** (con số kiểu "40% cho lần thử đầu") — không tìm được tài liệu công khai của King hay studio lớn nào công bố ngưỡng cụ thể. Cơ chế tuning (telemetry win rate, attempts, booster usage, quit point) thì có mô tả; ngưỡng thì không.
3. **Lift conversion đo được của "$1 offer"** — mọi con số tìm thấy đều là self-reported, không có nhóm đối chứng. Không có nghiên cứu có holdout công khai.
4. **Ngưỡng SCR "khỏe mạnh 95–105%" và "inflation rate khỏe mạnh 2–5%/tháng"** — xuất hiện trong tài liệu practitioner nhưng không có phép đo gốc. Tôi trình bày chúng như hệ quả của định nghĩa trạng thái dừng, không như phát hiện thực nghiệm.
5. **Tham số pity chính xác của Genshin Impact** (`p_base=0.6%`, soft pity lượt 74, `Δ≈6%`, hard pity 90) — là **mô hình do cộng đồng dựng từ dữ liệu quan sát quy mô lớn**, không phải thông số do HoYoverse công bố. Mọi tính toán ở §4.3 phụ thuộc vào giả định này.
6. **Các con số nhịp interstitial ở §9.4** ("D7 giảm 20% khi >1 ad/2 phút", "15–25% bỏ trong session đầu") — nguồn là blog vendor, không công bố phương pháp hay cỡ mẫu. Dùng làm prior để A/B, không dùng làm bằng chứng.
7. **"86% developer không thấy IAP giảm sau khi thêm rewarded ads"** — khảo sát tự báo cáo, có thiên lệch sống sót rõ rệt.
8. **"User xem rewarded ads có khả năng mua IAP cao gấp 4,5×–6×"** — đây là tương quan, không phải nhân quả; không có nghiên cứu incrementality công khai nào tách được hai chiều.
9. **Mật độ event "trung bình tăng từ 73 lên 89/tháng"** và các con số 80–100 / 20–40 điểm chạm — báo cáo vendor, định nghĩa "event touchpoint" không công khai, nên không so sánh chéo được giữa các nguồn.
10. **Dải "$1–$5 mỗi giờ tiết kiệm được"** ở §1.5 — đây là ước lượng của tôi từ việc đọc cấu trúc giá của các game top-grossing, **không** phải phép đo. Coi là prior để calib.
11. **Tỉ lệ chồng lấn phân bố power "15–30%"** ở §1.6 và các target completion rate theo phân vị ở §5.2 — là **đề xuất thiết kế của tôi**, không phải benchmark ngành.
12. **Ngưỡng "pass phát ra > 30–40% tổng sink = rủi ro"** ở §5.6 — suy dẫn của tôi từ case Clash Royale/Clash of Clans, chưa được kiểm chứng định lượng.

---

## Nguồn chính

- The Many Faces of Monetisation: Understanding the Diversity and Extremity of Player Spending in Mobile Games via Massive-scale Transactional Analysis (Games: Research and Practice, ACM) — https://dl.acm.org/doi/10.1145/3582927
- Mobile Games A/B Testing with Cookie Cats (dataset & project, Tactile Entertainment / DataCamp) — https://www.datacamp.com/projects/184
- Gacha Game: When Prospect Theory Meets Optimal Pricing (Tan Gan, arXiv:2208.03602) — https://arxiv.org/abs/2208.03602
- Gaming the system: suboptimal compliance with loot box probability disclosure regulations in China (Behavioural Public Policy, Cambridge) — https://www.cambridge.org/core/journals/behavioural-public-policy/article/gaming-the-system-suboptimal-compliance-with-loot-box-probability-disclosure-regulations-in-china/B2642E2F8B7164236E5477D58D2B26DE
- Better than industry self-regulation: Compliance of mobile games with newly adopted and actively enforced loot box probability disclosure law in South Korea (ScienceDirect) — https://www.sciencedirect.com/science/article/pii/S0001691825008030
- Loot Box State of Play 2023: Law, Regulation, Policy, and Enforcement around the World (Leon Y. Xiao, Gaming Law Review) — https://journals.sagepub.com/doi/10.1089/glr2.2024.0006
- Genshin Impact Game Developer Will be Banned from Selling Lootboxes to Teens Under 16 without Parental Consent, Pay a $20 Million Fine to Settle FTC Charges (FTC, 01/2025) — https://www.ftc.gov/news-events/news/press-releases/2025/01/genshin-impact-game-developer-will-be-banned-selling-lootboxes-teens-under-16-without-parental
- Key principles on in-game virtual currencies (CPC Network / European Commission, 03/2025) — https://commission.europa.eu/document/download/8af13e88-6540-436c-b137-9853e7fe866a_en?filename=Key+principles+on+in-game+virtual+currencies.pdf
- The European Commission hosts stakeholders' talks on the application of the CPC Network's Key Principles on In-Games Virtual Currencies (06/2025) — https://commission.europa.eu/news-and-media/news/european-commission-hosts-stakeholders-talks-application-cpc-networks-key-principles-games-virtual-2025-06-03_en
- Apple Now Requires Disclosure of Loot Box Odds (Fenwick) — https://www.fenwick.com/insights/publications/apple-now-requires-disclosure-of-loot-box-odds
- Book Excerpt: Game Economy Design: Metagame, Monetization and Live Operations (Charlie Czerkawski, CRC Press 2024 — trích trên Game Developer) — https://www.gamedeveloper.com/design/book-excerpt-game-economy-design-metagame-monetization-and-live-operations
- Economic Balancing and Improved Monetization Through Clever Sink Design (Vili Lehdonvirta, GDC 2014 — GDC Vault) — https://www.gdcvault.com/play/1020085/Economic-Balancing-and-Improved-Monetization
- Value chains — A method for creating and balancing faucet-and-drain game economies (Daniel Cook, Lost Garden) — https://lostgarden.com/2021/12/12/value-chains/
- Battle Passes — Everything You Ought to Know and Then Some (Deconstructor of Fun) — https://www.deconstructoroffun.com/blog/2022/6/4/battle-passes-analysis
- Pricing Starter Packs: When Conversion is King, We May Price Too Low (Deconstructor of Fun, khảo sát 54 practitioner) — https://www.deconstructoroffun.com/blog/2024/4/8/free-to-play-starter-pack-pricing-when-conversion-is-king-we-may-price-too-low
- Hybrid Monetization Buzz Doesn't Quit: Brat or Basic? (Deconstructor of Fun) — https://www.deconstructoroffun.com/blog/2024/12/09/hybrid-monetization-buzz-doesnt-quit-brat-or-basic
- Energy Systems: Lessons from Top Freemium Games (Adrian Crook & Associates) — https://adriancrook.com/energy-systems-lessons-top-freemium-games/
- Balancing F2P Economies: Simulating Player Personas and Progression Curves with Machinations — https://machinations.io/articles/balancing-f2p-economies-simulating-player-personas-and-progression-curves-with-machinations
- The Designer's Notebook: Machinations, A New Way to Design Game Mechanics (Ernest Adams / Joris Dormans, Game Developer) — https://www.gamedeveloper.com/design/the-designer-s-notebook-machinations-a-new-way-to-design-game-mechanics
- Monetizer Special: Analysing hard-to-soft currency conversion rates in F2P games that use a 'percentage fill' IAP economy (PocketGamer.biz) — https://www.pocketgamer.biz/monetizer/57841/monetizer-special-analysing-hard-to-soft-currency-conversion-rates-in-f2p-games-that-use-a-percentage-fill-iap-economy/
- Types of game currencies in mobile free-to-play (Game Developer) — https://www.gamedeveloper.com/business/types-of-game-currencies-in-mobile-free-to-play
- IAP Packs in Mobile F2P: Analysis and Design (Game Developer) — https://www.gamedeveloper.com/business/iap-packs-in-mobile-f2p-analysis-and-design
- Determining your IAP price points (hint: make it expensive) (Alexandre Macmillan) — https://alexandremacmillan.com/2019/01/24/determining-your-iap-price-points-hint-make-it-expensive/
- Why It's Scary When 0.15% Mobile Gamers Bring In 50% Of The Revenue (Forbes, về nghiên cứu Swrve) — https://www.forbes.com/sites/insertcoin/2014/03/01/why-its-scary-when-0-15-mobile-gamers-bring-in-50-of-the-revenue/
- Report: Whales gobble up even more of the F2P mobile game revenue pie (Game Developer) — https://www.gamedeveloper.com/business/report-whales-gobble-up-even-more-of-the-f2p-mobile-game-revenue-pie
- GameRefinery: creative battle pass implementation found in 60% of the top grossing mobile games (PocketGamer.biz) — https://www.pocketgamer.biz/gamerefinery-battle-pass-implementation-in-top-grossing-mobile-games/
- How to Build Better Live Events with the GameRefinery Live Events Tracker — https://www.gamerefinery.com/how-to-build-better-live-events-with-the-gamerefinery-live-events-tracker/
- [Event Spotlight] How Mobile Game Developers Can Boost Engagement In The Casual Puzzle Genre With Competitive 1v1 Events (GameRefinery) — https://www.gamerefinery.com/event-spotlight-how-mobile-game-developers-can-boost-engagement-in-the-casual-puzzle-genre-with-competitive-1v1-events/
- Algorithms for calculating gacha probabilities (Kyle Chen) — https://kylechen.net/writing/gacha-probability/
- The Royal Crush: Analysis of Match-3 Mechanics (Daniel Eckmann, CoG 2024) — https://downloads.hci.informatik.uni-wuerzburg.de/2024-CoG-RoyalCrush.pdf
- Improving Conditional Level Generation using Automated Validation in Match-3 Games (arXiv:2409.06349) — https://arxiv.org/pdf/2409.06349
- Diversify your mobile games' revenue with rewarded ads (Google Play Apps & Games) — https://medium.com/googleplaydev/diversify-your-mobile-games-revenue-with-rewarded-ads-abaeca615040
- How battle passes can boost engagement and monetization in your game (Google Play Apps & Games) — https://medium.com/googleplaydev/how-battle-passes-can-boost-engagement-and-monetization-in-your-game-d296dee6ddf8
- Hybrid monetization for mobile games (Think with Google) — https://www.thinkwithgoogle.com/intl/en-apac/marketing-strategies/app-and-mobile/hybrid-mobile-game-monetization-models/
- Decoy effect (tổng quan + giới hạn replication) — https://en.wikipedia.org/wiki/Decoy_effect
- The Decoy Effect: The Pricing-Page Tactic That Doesn't Replicate (Atticus Li) — https://atticusli.com/replication-crisis/decoy-effect-asymmetric-dominance/
- Monetization mechanisms in gacha games: The behavioral triad of pricing strategies, pity systems, and belief of luck (ScienceDirect) — https://www.sciencedirect.com/science/article/abs/pii/S1875952125001247
- First purchase timing matters: Monetization and churn in Free-to-Play mobile games — A survival-analysis case study (ScienceDirect) — https://www.sciencedirect.com/science/article/pii/S1875952126001175
- Game changer: EU introduces consumer protection guidance for in-game virtual currencies (Linklaters) — https://techinsights.linklaters.com/post/102k6t4/game-changer-eu-introduces-consumer-protection-guidance-for-in-game-virtual-curr
- New Guidelines for In-Game Currencies – Digital Consumer Protection and the Expanding Taboo of 'Dark Patterns' (Gleiss Lutz) — https://www.gleisslutz.com/en/know-how/new-guidelines-game-currencies-digital-consumer-protection-and-expanding-taboo-dark-patterns
