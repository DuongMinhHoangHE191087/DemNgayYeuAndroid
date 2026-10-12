# 58 — Full action space: FREE_ACTION và GAMEPLAY_CONFIG được nối dây thật qua `chooseAssist` (doc 23 Phase 5, mục 1)

**Trạng thái:** 500/500 unit test, backend 94/94, hai AAR build sạch.

## A. Bài toán

Action space gốc của brain (doc 01) khai báo 6 mode: `NO_ACTION`, `FREE_ACTION`,
`REWARDED_AD`, `INTERSTITIAL`, `IAP_OFFER`, `GAMEPLAY_CONFIG`. Trước doc này, hai mode
`FREE_ACTION` (tặng hint/boost miễn phí) và `GAMEPLAY_CONFIG` (giảm độ khó màn) chỉ tồn tại
trong enum — không có cổng nào hỏi brain về chúng. Doc 55 mục G.1 ghi thẳng khoảng trống:
"Full action space của Phase 5 (difficulty assist, social prompt…) chưa có mặt".

Vì sao phải nối dây, không phải chuyện riêng của game design:

- **Doc 24 mục C** (AAMAS 2026, "The Monetization Agent: A Deployed POMDP for Maximizing
  Lifetime In-App Advertising Revenue"): dynamic difficulty adjustment là một *candidate
  action* hợp lệ của monetization agent — production RCT đo nó cùng ad frequency, không
  tách sang hệ khác. "Một candidate action có thể là cấu hình gameplay, không chỉ ad hay
  paywall — đúng như action GAMEPLAY_CONFIG đã có trong action space gốc (doc 01)."
- **Doc 26**: SDK quyết định "ad frequency/timing, IAP offer, gameplay config, subscription
  nudge" — gameplay config là lời hứa sản phẩm chưa có code.
- **Doc 16 mục D**: kiến trúc HOT path (<10ms) của brain được thiết kế để ra quyết định ở
  mọi điểm trong phiên chơi, không chỉ điểm quảng cáo.

## B. API

```kotlin
val d = AdsBrain.chooseAssist(
    decisionPoint = DecisionPoint.LEVEL_FAIL,  // học riêng theo từng điểm
    allowFreeGift = true,        // game có quà để tặng ở điểm này?
    allowDifficultyTuning = true, // màn này cho phép giảm độ khó? (boss → false)
    baseline = ActionMode.NO_ACTION // hành vi game sẽ làm nếu KHÔNG có brain
)
when (d.action) {
    ActionMode.FREE_ACTION     -> showFreeHintButton()
    ActionMode.GAMEPLAY_CONFIG -> applyEasedDifficulty()
    else                       -> {} // NO_ACTION: để người chơi tự vượt qua
}
if (d.isHoldout) logExperimentSkip() // nhóm đối chứng — không ghi quyết định này vào brain

// Khi đã đưa hỗ trợ ra:
AdsBrain.onAssistShown()       // hiện nút quà / áp độ khó mới — đầu vào của trần phiên
AdsBrain.onAssistUsed(didUse)  // người chơi có dùng hay không — giá trị chốt ở đây
```

Cùng hình dạng với `chooseCrossModal` (doc 55):

- Tắt brain / ngoài rollout / cả hai loại hỗ trợ bị cấm → trả đúng `baseline`, không ép về
  `NO_ACTION`, không hỏi engine (không ghi quyết định một-arm vào log OPE).
- `NO_ACTION` luôn có mặt trong tập ứng viên; `allowFreeGift`/`allowDifficultyTuning` để
  game loại loại hỗ trợ không tồn tại ở điểm đó.
- Holdout nhận `baseline` với xác suất 1.0 + cờ `isHoldout`, cùng namespace
  `brain_holdout_v1` với paywall và cross-modal.

## C. Ràng buộc đạo đức cốt lõi: KHÔNG có arm IAP_OFFER

Hàm này CỐ Ý không có arm `IAP_OFFER`. Cứu người bế tắc bằng thứ PHẢI TRẢ TIỀN là khai
thác frustration — đúng hành vi guardrail `ETH_FRUSTRATION_EXPLOIT` chặn ở mọi cổng khác
(doc 29 mục 10.2 câu Q4: "mức sẵn lòng trả tăng theo độ dài chuỗi thua — và đó chính xác
là lý do không được làm").

Hỗ trợ ở đây chỉ tồn tại dưới dạng **cho đi** (`FREE_ACTION`) hoặc **chỉnh trải nghiệm**
(`GAMEPLAY_CONFIG`). Bán rescue vẫn là quyết định IAP riêng của game, đi qua
`shouldOfferIap`/`chooseCrossModal` — nơi guardrail từ chối nó khi chuỗi thua đã sâu. Cổng
này không có đường nào để lách: `IAP_OFFER` không phải candidate, không phải flag, không
tồn tại trong enum của hàm.

## D. Guardrail mới: `ASSIST_SESSION_CAP`

Trần tần suất hỗ trợ mỗi phiên (`GuardrailEngine.maxAssistsPerSession`, mặc định 3), chặn
CẢ HAI mode hỗ trợ khi quá ngân sách — nhưng không chặn `NO_ACTION`. Lý do không phải "miễn
phí thì không cần trần":

- Dồn dập quà tặng tạo **helplessness học được** — người chơi ngừng cố vì chờ được cho.
- Phá tín hiệu tiến bộ mà game dựa vào, và làm lệch kinh tế game.
- Quà chỉ có giá trị khi người chơi đang kẹt; nhịp bên trong do chính nhu cầu của họ điều
  tiết, nên trần này là chốt chặn biên (RESOURCE) — không cần Lagrangian shadow price.

Ba nguyên tắc thiết kế giữ nguyên (doc 27):

1. **Cô lập state** — bộ đếm `assistsThisSession` riêng, không share với bộ đếm quảng cáo
   hay chào bán (ba loại action không liên quan không share trạng thái).
2. **Fail-safe** — mặc định 3 là con số bảo thủ; artifact hỏng giữ nguyên.
3. **Click-fraud miễn trừ** — khoá `CLICK_FRAUD_LOCKOUT` bảo vệ kho quảng cáo khỏi click
   gian lận, không phải để cấm game tặng quà hay chỉnh độ khó: `FREE_ACTION` và
   `GAMEPLAY_CONFIG` được miễn trừ cùng `NO_ACTION`.

### Cấu hình từ xa — clamp-only

Khoá `gameplay.max_assists_per_session` trong policy artifact, kẹp cận trên bởi hằng số
biên dịch `BrainPolicy.HARD_MAX_ASSISTS_PER_SESSION = 8` (kèm cảnh báo trong report khi kẹp).
Trần CAO hơn = quà dồn nhịp hơn → chỉ được phép siết xuống, không bao giờ nâng vượt trần
cứng — đúng chiều rủi ro như mọi trần tần suất khác.

## E. Feedback: đưa ra ≠ giá trị

Cùng học thuyết với `onIapOfferShown` (doc 55 B.3):

- `onAssistShown()`: ghi bộ đếm phiên (đầu vào của trần), `pendingHadValue = false` — đưa
  quà ra chưa tạo giá trị gì nếu người chơi nhận rồi vẫn bỏ game.
- `onAssistUsed(true)`: người chơi ĐÃ dùng hỗ trợ — vừa là giá trị trao đi vừa là tín hiệu
  họ tiếp tục chơi → chốt ngay như `onRewardedOffered(true)` chốt lượt nhận quảng cáo thưởng.
- `onAssistUsed(false)`: bị phớt lờ — KHÔNG chốt âm ngay; chờ tín hiệu ở lại thật
  (`onLevelWin`/`onLevelFail`/`onSessionEnd`) như đường `onAdSkipped`, vì người từ chối quà
  vẫn có thể tự vượt qua và ở lại. Bandit vì vậy học "trợ giúp đúng lúc giữ chân người"
  thay vì "cứ tặng là thắng".

## F. Backend: KHÔNG cần thay đổi

`brain_decision` flatten `chosen_action_id`/`chosen_action_mode`/`chosen_action_probability`
dưới dạng chuỗi generic (normalize.py) — `free_assist`/`ease_difficulty`/`FREE_ACTION`/
`GAMEPLAY_CONFIG` tự chảy vào kho log như mọi arm khác. OPE server-side phân tích được ngay
khi có dữ liệu vì propensity đã log đủ.

## G. Tích hợp (drop-in)

1. Tại điểm người chơi kẹt (sau `onLevelFail()`, khi `CURRENCY_LOW`…), gọi
   `AdsBrain.chooseAssist(decisionPoint, allowFreeGift, allowDifficultyTuning, baseline)`.
2. `baseline` = hành vi game đang làm khi CHƯA có brain — thường `NO_ACTION`. Khai sai thì
   phép đo holdout lệch; đây là trách nhiệm tích hợp (giống doc 55 G.2).
3. Nếu `action` khác `NO_ACTION` và game quyết định thật sự đưa ra: gọi `onAssistShown()`,
   rồi `onAssistUsed(đãDùng)` khi biết kết quả. Nếu chọn mà KHÔNG đưa ra (ad không sẵn
   sàng, quà hết…), KHÔNG gọi gì cả — lượt hỏi kế tiếp tự chốt.
4. Bật từ xa: thêm `"gameplay": {"max_assists_per_session": N}` vào policy artifact khi muốn
   nới ngân sách (≤ 8). Mặc định trong code là 3.

## H. Giới hạn — ghi rõ

1. Chưa có `social prompt` (doc 23 Phase 5 nhắc "difficulty assist, social prompt…") — half
   đã nối dây là difficulty assist + free gift; social prompt cần thêm decision point và tín
   hiệu đo riêng, để phase sau.
2. `GAMEPLAY_CONFIG` trả về MODE, không trả cấu hình cụ thể — "giảm độ khó màn này xuống
   bao nhiêu" là dữ liệu của game, SDK chỉ quyết định CÓ hay KHÔNG. Đúng nguyên lý SDK
   không vẽ UI / không sở hữu nội dung game.
3. Trần 3/phiên là lựa chọn thiết kế bảo thủ, chưa calibrate từ dữ liệu thật — như mọi
   ngưỡng khác trong `GuardrailEngine`.
4. On-device inference (Phase 5 mục 3, doc 16) vẫn blocked: chưa có model huấn luyện sẵn và
   pipeline train — dài hạn, ghi nhận không phải nợ giấu.
