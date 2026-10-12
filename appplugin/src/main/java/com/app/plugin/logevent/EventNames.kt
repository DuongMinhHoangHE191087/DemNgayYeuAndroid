package com.app.plugin.logevent

/**
 * Tên event SDK gửi, lên server log của SDK (`LogEventServer`) hay lên Firebase Analytics (`FirHelper.logEvent`).
 * Chưa phải mọi tên: tên ghép lúc chạy (dưới đây) còn ở ngoài (follow-up, DOC/65 G6). Nơi `EventNameContractTest`
 * khoá hợp đồng Điều 9.2: tên ≤ 40 ký tự,
 * `^[a-z][a-z0-9_]{0,39}$`, không tiền tố `firebase_`/`google_`/`ga_` (Firebase bỏ event sai tên mà không báo).
 * Param Firebase: snake_case ≤ 40 ký tự, giá trị chuỗi ≤ 100 ký tự.
 *
 * Chỗ gọi `logEvent`/`logEventToServer` dùng hằng ở đây, không viết literal. Hằng ở đây phải là literal chuỗi
 * (test đọc giá trị từ mã nguồn). Tên ghép lúc chạy từ chuỗi của app (`aad_*`, `show_ads_*` trong `AdsHelper`)
 * nằm ngoài tầm kiểm tĩnh.
 *
 * Đổi một giá trị là gãy liên tục báo cáo của event đó: ghi vào CHANGELOG, mục "Phải làm khi nâng cấp".
 */
internal object EventNames {
    // ── Server log của SDK (LogEventServer) ─────────────────────────────────────────────────────
    const val AD_LOAD = "ad_load"
    const val AD_SHOW = "ad_show"
    const val AD_SHOW_FAIL_REASON = "ad_show_fail_reason"
    const val AD_REVENUE = "ad_revenue"
    const val SCREEN_PLAY = "screen_play"
    const val SCREEN_END = "screen_end"
    const val SESSION_START = "session_start"
    const val FIRST_OPEN = "first_open"
    const val BEHAVIOR = "behavior"
    const val BUTTON_CLICK = "button_click"
    const val SDK_INIT_TIME = "sdk_init_time"
    const val FETCH_CF_TIME = "fetch_cf_time"
    const val START_FIRESTORE = "start_firestore"
    const val FIRESTORE_TIME = "firestore_time"
    const val ADMOB_ADAPTER_INIT = "admob_adapter_init"
    const val AD_START_LOAD = "ad_start_load"
    const val AD_START_ACTION = "ad_start_action"
    const val FETCH_CF_TIMEOUT = "fetch_cf_timeout"
    const val FETCH_CF_START = "fetch_cf_start"
    const val CF_CHANGE = "config_change"

    // Schema v2 (1.7.0 Task 1, plan "Hợp đồng event v2"): dựng bởi `AdEventLog`. Tên v1 `ad_start_load`, `ad_load`,
    // `ad_start_action`, `ad_show_fail_reason` giữ ở trên vì dump API, không còn phát. `internal val`, không `const`:
    // `const` là trường public static, làm đổi mặt ngoài EventNames so với v1.5.0 (`tools/api_facade_check.sh` FAIL);
    // getter `internal` bị mangle `…$appplugin` nên façade bỏ qua.
    internal val AD_REQUEST = "ad_request"
    internal val AD_FILL = "ad_fill"
    internal val AD_NO_FILL = "ad_no_fill"
    internal val AD_SHOW_ATTEMPT = "ad_show_attempt"
    internal val AD_SHOW_FAIL = "ad_show_fail"
    internal val AD_CLICK = "ad_click"
    internal val CONSENT_FORM_SHOWN = "consent_form_shown"
    internal val CONSENT_CHOICE = "consent_choice"
    internal val SESSION_END = "session_end"
    // 2.5.1 PLEVT: Firebase, `PlacementPaidEvent` (AdMob đã liên kết, `ads_cf_placement_event` = 1).
    internal val AD_PLACEMENT_IMPRESSION = "ad_placement_impression"

    // Schema v2 IAP/paywall (1.8.0 Task 7, hợp đồng E): dựng bởi `IapEventLog`; `internal val` vì lý do façade như trên.
    internal val IAP_RESULT = "iap_result"
    internal val PAYWALL_SKIP = "paywall_skip"
    internal val PAYWALL_CHOICE = "paywall_choice"

    // Monetization Brain — decision log phục vụ off-policy evaluation (doc 06, doc 28).
    // Tách riêng khỏi các event ads vì schema khác hẳn: có propensity, candidate set,
    // segment và cờ holdout. Trộn chung sẽ làm phân tích OPE rất khó tách.
    const val BRAIN_DECISION = "brain_decision"
    const val BRAIN_FEEDBACK = "brain_feedback"

    // Doanh thu IAP theo kênh (doc 26 mục C.2). Cùng với event ads, đây là vế còn lại
    // của ARPDAU — trước khi có nó, SDK chỉ đo được nửa doanh thu.
    const val IAP_PURCHASE = "iap_purchase"

    // North Star Metric thứ tư (doc 26 mục C.4). Gửi kèm `coverage` vì một chỉ số độ phủ
    // 40% mà trình bày như con số chắc chắn còn nguy hiểm hơn không có chỉ số nào.
    const val BRAIN_COMFORT = "brain_comfort"

    // Mẫu số của tỉ lệ chuyển đổi paywall. Thiếu nó thì số lượt mua không so sánh được
    // giữa các biến thể — biến thể được hiện nhiều hơn tất nhiên bán được nhiều hơn.
    const val PAYWALL_IMPRESSION = "paywall_impression"

    // Lượt bỏ qua của nhóm đối chứng paywall — mẫu số của phép đo "có paywall vs KHÔNG có
    // paywall". Gửi kèm biến thể LẼ RA đã hiện (counterfactual) để so sánh từng biến thể
    // giữa nhóm được hiện và nhóm bị giữ lại. Không có event này thì nhóm đối chứng chỉ
    // tồn tại trong bộ đếm trên máy, tức không tồn tại trong dữ liệu.
    const val PAYWALL_HOLDOUT_SKIP = "paywall_holdout_skip"

    // Tử số của phễu routing đa kênh (doc 18 mục 6.7): người được dẫn ra web shop. Ghép với
    // `iap_purchase` channel WEB_SHOP (xác nhận phía server) để đo tỉ lệ chuyển đổi theo kênh.
    const val WEB_CHECKOUT_OPENED = "web_checkout_opened"

    // Chuyển đổi trạng thái vòng đời subscription (doc 23 mục C.3): một event với cặp
    // from/to phủ toàn bộ taxonomy doc 19 (vào grace, hồi phục thanh toán, huỷ, churn).
    // Chỉ phát khi trạng thái THẬT SỰ đổi — dedup nằm ở nguồn, không cần dedup key.
    const val SUBSCRIPTION_STATE = "subscription_state"

    // Battle pass (doc 23 mục C.3): vượt bậc và claim phần thưởng. Hai event riêng vì
    // chúng trả lời hai câu hỏi khác nhau — tiến độ (funnel giữ chân) và nhận thưởng
    // (funnel chuyển đổi premium).
    const val BATTLE_PASS_TIER = "battle_pass_tier"
    const val BATTLE_PASS_CLAIM = "battle_pass_claim"

    // Cầu nối VIP ↔ virtual currency (doc 19): mỗi lần người sở hữu nhận dòng phát
    // định kỳ. Đo retention của đặc quyền VIP — lý do người ta GIỮ subscription.
    const val RECURRING_GRANT_CLAIM = "recurring_grant_claim"

    // Offerwall (doc 20, doc 23 C.4): impression là lượt mở tường — mẫu số để đo
    // chuyển đổi sang completion; completion là offer được xác nhận hoàn thành.
    const val OFFERWALL_IMPRESSION = "offerwall_impression"
    const val OFFERWALL_COMPLETED = "offerwall_completed"

    // Bản ghi phân nhóm thí nghiệm (doc 23 Phase 4 mục 3): mỗi phiên một hàng cho MỌI
    // player trong không gian thí nghiệm — mẫu số chung của cả hai phép đo incrementality
    // (treatment vs không treatment, holdout vs không holdout). Thiếu nó thì join với dữ
    // liệu UA/attribution không có hàng membership cho những player không bao giờ thấy
    // paywall — phép đo chỉ còn phủ được người đã vào phễu mua.
    const val EXPERIMENT_EXPOSURE = "experiment_exposure"

    // ── Firebase Analytics ──────────────────────────────────────────────────────────────────────
    /** Sức khoẻ SDK, một lần mỗi process (`SdkHealth`, doc 60 G). Param và dạng `kill`: `SdkHealth.params`. */
    const val SDK_HEALTH = "sdk_health"
    const val SHOW_ADS_TOTAL = "show_ads_total"
    const val APP_INSTALL_REFERRER = "app_install_referrer"
    const val DAILY_ADREVENUE = "daily_adrevenue"

    /**
     * Thời gian fetch Remote Config (`FirHelperConfig`), một event mỗi lần fetch, theo mốc giây: [remoteConfigFetchBucket].
     * Tên giữ nguyên như trước 1.4.0 để không gãy dashboard, kể cả hai chỗ lệch: không có `7s` (dải 6–8 s mang tên
     * `8s`), và `9s` nghĩa là **trên 8 s**, không phải 9 s. Server đã có số ms thật ở `fetch_cf_time`.
     */
    const val FIR_RM_FET_1S = "fir_rm_fet_1s"
    const val FIR_RM_FET_2S = "fir_rm_fet_2s"
    const val FIR_RM_FET_3S = "fir_rm_fet_3s"
    const val FIR_RM_FET_4S = "fir_rm_fet_4s"
    const val FIR_RM_FET_5S = "fir_rm_fet_5s"
    const val FIR_RM_FET_6S = "fir_rm_fet_6s"
    const val FIR_RM_FET_8S = "fir_rm_fet_8s"
    const val FIR_RM_FET_9S = "fir_rm_fet_9s"

    /**
     * Event của một lần fetch Remote Config mất [ms] mili giây: mỗi mốc gồm biên trên (`1000` → `1s`, `1001` → `2s`),
     * `6001..8000` → `8s`, trên `8000` → `9s`. Thời gian âm (giờ hệ thống bị lùi) vào `1s`, như chuỗi if/else cũ.
     */
    fun remoteConfigFetchBucket(ms: Long): String = when {
        ms <= 1_000L -> FIR_RM_FET_1S
        ms <= 2_000L -> FIR_RM_FET_2S
        ms <= 3_000L -> FIR_RM_FET_3S
        ms <= 4_000L -> FIR_RM_FET_4S
        ms <= 5_000L -> FIR_RM_FET_5S
        ms <= 6_000L -> FIR_RM_FET_6S
        ms <= 8_000L -> FIR_RM_FET_8S
        else -> FIR_RM_FET_9S
    }

    /** Mở lại app open khi chưa có Activity chính. Trước 1.4.0: `reloadSplash_acnull`. */
    const val RELOAD_SPLASH_AC_NULL = "reload_splash_ac_null"

    /** Activity chính tới, chạy lần mở lại app open đã hoãn. Trước 1.4.0: `setAc_waitreloadsplash`. */
    const val SET_AC_WAIT_RELOAD_SPLASH = "set_ac_wait_reload_splash"

    /**
     * Placement được yêu cầu load khi chưa có Activity: `ads_pl_<eventTag>_<adsType>`, ví dụ `ads_pl_mob_full_0`
     * (`eventTag` là hằng chữ của lớp placement, xem `AdsPlBase.eventTag`). Trước 1.4.0: `ads_<tên lớp><adsType>`
     * (`ads_AdsMobPlInters0`), mà dưới R8 của app là tên lớp đã đổi (`ads_a0`).
     */
    fun adsPlActivityNull(eventTag: String, adsType: Int): String = "ads_pl_${eventTag}_$adsType"

    // ── Ngoại lệ đóng băng: tên cũ không theo snake_case (quyết định #5, DOC/66, 2026-09-25) ────────
    // Mốc doanh thu quảng cáo cộng dồn (tROAS, `FirHelper.logAdTopRev`). Đóng băng: Google Ads nhập các event này
    // làm conversion theo đúng tên, đổi tên là tạo conversion mới không lịch sử và chiến dịch tROAS học lại. Hợp lệ
    // với Firebase (chữ hoa được phép). `EventNameContractTest` khoá đúng năm tên, từng ký tự, và cấm literal ngoài
    // file này; `FirHelperAdLtvEventTest` khoá mốc → tên. Nếu phải đổi: bắn cả tên cũ và mới ≥ 30 ngày trước.
    const val AD_LTV_ONE_DAY_TOP_50 = "AdLTV_OneDay_Top50Percent"
    const val AD_LTV_ONE_DAY_TOP_40 = "AdLTV_OneDay_Top40Percent"
    const val AD_LTV_ONE_DAY_TOP_30 = "AdLTV_OneDay_Top30Percent"
    const val AD_LTV_ONE_DAY_TOP_20 = "AdLTV_OneDay_Top20Percent"
    const val AD_LTV_ONE_DAY_TOP_10 = "AdLTV_OneDay_Top10Percent"
}
