package com.app.plugin.consent

import com.app.plugin.until.LogUtil

/**
 * Ba tầng phục vụ theo consent (doc 13 mục F), cộng một tầng thứ tư mà tài liệu không có:
 * **chưa biết**.
 *
 * Tầng `UNKNOWN` là điểm quan trọng nhất của thiết kế này. Một biến boolean không phân biệt
 * được "người dùng đã TỪ CHỐI" với "chưa ai từng hỏi", và gộp hai thứ đó lại buộc ta chọn
 * một trong hai điều tệ:
 *
 * - Coi mọi người là đã từ chối → studio ngoài EEA chưa từng cần consent tự nhiên mất
 *   personalization khi nâng phiên bản SDK. Một hồi quy im lặng do chính ta bịa ra.
 * - Coi mọi người là đã đồng ý → làm chính xác điều GDPR cấm.
 *
 * Cách đúng: SDK tôn trọng một lời từ chối THẬT, và không tự phát minh ra lời từ chối ở nơi
 * chưa ai đo được gì.
 */
public enum class ConsentTier {
    /**
     * **Chưa có câu trả lời dùng được**: chưa chạy luồng consent, hoặc UMP `OBTAINED` mà không đọc
     * được IABTCF (xem `TcfConsent.resolve`). Báo cáo tự kiểm tra của `MonetizationSdk` cảnh báo.
     *
     * UNKNOWN **không bao giờ là đồng ý**. Mọi consumer về quảng cáo, analytics, FCM, AppsFlyer, brain và CRM
     * phải coi nó là chưa được cấp: Task 9 siết cho Analytics/FCM/AppsFlyer (`AnalyticsConsent`, `FcmConsent`,
     * `AppsflyerHelper.consentFor`), quyết định #7 (DOC/66) siết [ConsentState.allowsProfiling] và
     * [ConsentState.allowsPersonalization]. Ngoại lệ duy nhất: `IABTCF_gdprApplies = 0` đọc được rõ ràng.
     */
    UNKNOWN,

    /** Người dùng từ chối, hoặc bắt buộc phải có consent mà chưa lấy được. */
    DENIED,

    /** Đồng ý một phần. Chưa có per-feature masking nên xử lý bảo thủ — xem `allowsProfiling`. */
    PARTIAL,

    /** Đồng ý đầy đủ. */
    GRANTED
}

/**
 * Trạng thái consent hiện tại và các quyền nó mở ra.
 *
 * Tách khỏi `ConsentManager` (lớp bọc UMP cần `Activity`) để phần luật — thứ thực sự chi phối
 * hành vi — kiểm chứng được bằng unit test thuần.
 */
public object ConsentState {

    @Volatile
    private var current: ConsentTier = ConsentTier.UNKNOWN

    private val listeners = mutableListOf<(ConsentTier) -> Unit>()

    /**
     * App có hướng tới trẻ em không (COPPA). `null` = **studio chưa khai báo**, và khi đó SDK
     * KHÔNG đụng vào cấu hình tương ứng của mạng quảng cáo.
     *
     * Trước bản này, `AdsMobMy.initAd()` hardcode `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` —
     * tức SDK tự tuyên bố thay studio rằng app của họ không hướng tới trẻ em. Đó là một tuyên
     * bố pháp lý, không phải một giá trị mặc định kỹ thuật, và nó không thuộc thẩm quyền của
     * một thư viện.
     */
    @Volatile
    public var childDirected: Boolean? = null

    /** Người dùng dưới tuổi đồng ý (GDPR). `null` = chưa khai báo, xem `childDirected`. */
    @Volatile
    public var underAgeOfConsent: Boolean? = null

    public fun tier(): ConsentTier = current

    /**
     * Đổi tầng consent. Đổi consent là **sự kiện hạng nhất** (doc 13 mục F): người nghe phải
     * được báo để huỷ hồ sơ đã cache và đổi tầng phục vụ ngay. Không có móc này thì hồ sơ cũ
     * tiếp tục được dùng sau khi người dùng đã rút lại sự đồng ý.
     */
    @Synchronized
    public fun setTier(tier: ConsentTier) {
        if (tier == current) return
        current = tier
        try {
            store?.save(tier.name)
        } catch (e: Exception) {
            // Không lưu được thì phiên sau mở ở UNKNOWN tới khi UMP trả lời; tầng phiên này vẫn đổi.
            LogUtil.logE("ConsentState: cannot save tier $tier: ${e.message}")
        }
        for (l in listeners.toList()) {
            try {
                l(tier)
            } catch (e: Exception) {
                // Người nghe hỏng không được chặn luồng, nhưng phải để lại dấu vết (Điều 7).
                LogUtil.logE("ConsentState: listener crashed on tier $tier: ${e.message}")
            }
        }
    }

    @Synchronized
    public fun onChange(listener: (ConsentTier) -> Unit) {
        listeners.add(listener)
    }

    /** Gỡ đúng người nghe đã đăng ký bằng [onChange] (so theo instance). Cho `resetForTest` của SDK. */
    @JvmSynthetic
    @Synchronized
    internal fun removeOnChange(listener: (ConsentTier) -> Unit) {
        listeners.remove(listener)
    }

    /** [listener] (so theo instance) còn đăng ký không. `AdSdkStartup` dùng để không đăng ký trùng. */
    @JvmSynthetic
    @Synchronized
    internal fun hasOnChange(listener: (ConsentTier) -> Unit): Boolean = listener in listeners

    // ═══ Lưu qua phiên ═══

    /** Nơi lưu tên tầng giữa các process. `AppPluginBase` cài bản dùng `PreferenceHelper`. */
    public interface TierStore {
        public fun save(name: String)
        public fun load(): String?
    }

    @Volatile
    private var store: TierStore? = null

    /** Cài nơi lưu. Từ đây mỗi lần [setTier] đổi tầng thì tên tầng được ghi xuống. */
    public fun installStore(s: TierStore) { store = s }

    /**
     * Gọi lúc mở process, trước khi UMP trả lời, để tầng phục vụ của phiên trước có hiệu lực ngay.
     * Chưa lưu gì, hoặc giá trị không phải tên một [ConsentTier], thì giữ nguyên tầng hiện tại.
     *
     * Chỉ áp khi tầng hiện tại còn UNKNOWN: đã có tầng khác nghĩa là UMP trả lời trước, và câu trả
     * lời đó mới hơn tầng đã lưu. Chạy trong lock để không xen giữa một [setTier] khác.
     */
    @Synchronized
    public fun restore() {
        if (current != ConsentTier.UNKNOWN) {
            LogUtil.logD("ConsentState: restore skipped, tier already $current")
            return
        }
        val saved = try {
            store?.load()
        } catch (e: Exception) {
            LogUtil.logE("ConsentState: cannot load saved tier, keeping $current: ${e.message}")
            return
        }
        val name = saved ?: return
        val tier = ConsentTier.values().firstOrNull { it.name == name }
        if (tier == null) {
            LogUtil.logE("ConsentState: saved tier '$name' is not a ConsentTier, keeping $current")
            return
        }
        LogUtil.logD("ConsentState: restore tier=$tier")
        setTier(tier)
    }

    // ═══ Quyền ═══

    /**
     * Được phép **tích luỹ hồ sơ hành vi** (cập nhật belief) không, khi không có `Context` để đọc
     * `IABTCF_gdprApplies`: như [allowsProfiling] với `gdprApplies = null` (không biết), nên `UNKNOWN` trả false.
     */
    public fun allowsProfiling(): Boolean = allowsProfiling(gdprApplies = null)

    /**
     * Được phép **tích luỹ hồ sơ hành vi** (cập nhật belief) không (quyết định #7, DOC/66).
     *
     * - `DENIED` → false, thắng mọi giá trị [gdprApplies].
     * - `GRANTED` → true.
     * - `PARTIAL` → false một cách có chủ đích: doc 13 mục F nói PARTIAL nghĩa là chỉ dùng feature đã được
     *   consent, nhưng ta chưa có per-feature masking. Khi chưa phân biệt được feature nào được phép, lựa chọn
     *   bảo thủ là không tích luỹ thêm gì cả.
     * - `UNKNOWN` → true **chỉ khi** [gdprApplies] `== false`, tức `IABTCF_gdprApplies = 0` đọc được rõ ràng
     *   (`TcfConsent.gdprApplies`); `null` (khoá vắng, đọc lỗi) và `true` → false. Người EEA chưa trả lời form
     *   không bị lập hồ sơ (GDPR Recital 32: im lặng không phải là đồng ý).
     */
    public fun allowsProfiling(gdprApplies: Boolean?): Boolean = when (current) {
        ConsentTier.DENIED -> false
        ConsentTier.GRANTED -> true
        ConsentTier.PARTIAL -> false
        ConsentTier.UNKNOWN -> gdprApplies == false
    }

    /**
     * Được phép dùng hồ sơ ĐÃ CÓ để cá nhân hoá không, khi không có `Context` để đọc `IABTCF_gdprApplies`: như
     * [allowsPersonalization] với `gdprApplies = null` (không biết), nên `UNKNOWN` trả false.
     */
    public fun allowsPersonalization(): Boolean = allowsPersonalization(gdprApplies = null)

    /**
     * Được phép dùng hồ sơ ĐÃ CÓ để cá nhân hoá không (quyết định #7, DOC/66). `DENIED` → false (luôn thắng);
     * `GRANTED` và `PARTIAL` → true; `UNKNOWN` → true **chỉ khi** [gdprApplies] `== false` (`IABTCF_gdprApplies = 0`
     * đọc được rõ ràng), `null` hoặc `true` → false. Xem [allowsProfiling].
     */
    public fun allowsPersonalization(gdprApplies: Boolean?): Boolean = when (current) {
        ConsentTier.DENIED -> false
        ConsentTier.GRANTED, ConsentTier.PARTIAL -> true
        ConsentTier.UNKNOWN -> gdprApplies == false
    }

    /**
     * Guardrail luôn chạy ở mọi tầng. Chúng chỉ BẢO VỆ người chơi — chặn quảng cáo quá dày,
     * chặn chào bán lúc bế tắc. Không có diễn giải nào của GDPR khiến việc bảo vệ người dùng
     * cần sự cho phép của chính họ.
     */
    public fun allowsGuardrails(): Boolean = true

    /**
     * Decision log vẫn được ghi khi bị từ chối — doc 13 mục F nói rõ *"vẫn log decision (log
     * không chứa PII)"*. Log của ta chứa id giả lập, action và propensity; không có PII.
     */
    public fun allowsDecisionLogging(): Boolean = true

    @JvmSynthetic
    internal fun resetForTest() {
        current = ConsentTier.UNKNOWN
        childDirected = null
        underAgeOfConsent = null
        store = null
        synchronized(this) { listeners.clear() }
    }
}
