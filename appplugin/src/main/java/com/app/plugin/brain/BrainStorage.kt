package com.app.plugin.brain

import com.app.plugin.until.PreferenceHelper

/**
 * Lưu trữ bền vững cho brain, đặt trên `PreferenceHelper` đã có sẵn trong repo.
 *
 * Vì sao cần: `DecisionEngine.beliefStates` là map in-memory — mất sạch khi process bị kill.
 * Không có lớp này, mỗi lần mở app player lại quay về prior đồng đều, và cá nhân hóa không
 * bao giờ tích lũy qua nhiều phiên. Đây là lỗi im lặng rất khó phát hiện khi test thủ công
 * (trong một phiên thì mọi thứ trông vẫn chạy đúng).
 *
 * Ghi chú kỹ thuật: SharedPreferences chỉ phù hợp ở quy mô này (một chuỗi ngắn cho mỗi player,
 * thường chỉ 1 player trên mỗi thiết bị). Nếu sau này cần lưu decision log cục bộ hoặc lịch sử
 * dài, phải chuyển sang Room/SQLite — SharedPreferences không dành cho dữ liệu tăng trưởng.
 */
internal object BrainStorage {

    private const val KEY_BELIEF_PREFIX = "brain_belief_"
    private const val KEY_BANDIT_PREFIX = "brain_bandit_"
    private const val KEY_PLAYER_ID = "brain_player_id"

    /**
     * Bộ nhớ đệm trong process cho player id.
     *
     * Vì sao cần: `PreferenceHelper` thoái hoá thành no-op khi chưa `init()` (và trong unit
     * test thì luôn như vậy). Nếu chỉ đọc từ đĩa, mỗi lần gọi sẽ sinh một UUID mới — belief
     * không bao giờ tích luỹ và phân nhóm holdout/rollout nhảy loạn. Đệm ở đây giữ id ổn
     * định ít nhất trong vòng đời process, kể cả khi ghi đĩa thất bại.
     */
    @Volatile
    private var cachedPlayerId: String? = null

    /** Id đã quên bởi `clearLocalData` (2.4.2 CLEAR I2): belief của chúng không được ghi lại (không khoá mồ côi). */
    private val forgottenIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun loadBelief(playerId: String): String? {
        val raw = PreferenceHelper.getInstance().get(KEY_BELIEF_PREFIX + playerId, "")
        return if (raw.isBlank()) null else raw
    }

    fun saveBelief(playerId: String, serialized: String) {
        if (playerId in forgottenIds) return   // state trong RAM của id đã xoá: không tạo lại `brain_belief_<id cũ>`
        PreferenceHelper.getInstance().setValue(KEY_BELIEF_PREFIX + playerId, serialized)
    }

    fun loadBanditStats(): String? {
        val raw = PreferenceHelper.getInstance().get(KEY_BANDIT_PREFIX + "stats", "")
        return if (raw.isBlank()) null else raw
    }

    fun saveBanditStats(serialized: String) {
        PreferenceHelper.getInstance().setValue(KEY_BANDIT_PREFIX + "stats", serialized)
    }

    /**
     * Player id ổn định, phạm vi thiết bị. Sinh một lần rồi giữ nguyên qua mọi phiên.
     *
     * KHÔNG dùng advertising id / android id: brain chỉ cần một khoá ổn định để tích luỹ
     * belief và phân nhóm thí nghiệm, không cần định danh xuyên ứng dụng. Dùng định danh
     * nền tảng ở đây sẽ kéo theo nghĩa vụ consent (doc 13) mà không đổi lại lợi ích gì.
     *
     * Nếu app đã có id người dùng riêng, hãy truyền vào `MonetizationSdk.configure { playerId }` hoặc thẳng
     * `AdsBrain.onSessionStart(id)` — id đó tốt hơn vì sống qua cả lần cài lại app. Id truyền vào cũng thay id
     * này làm `user_id` telemetry và id của web shop (`PlayerIdSource`): dùng id nội bộ mờ, không email/số điện thoại.
     */
    @Synchronized
    fun playerId(): String {
        cachedPlayerId?.let { return it }
        val stored = PreferenceHelper.getInstance().get(KEY_PLAYER_ID, "")
        val id = if (stored.isNotBlank()) stored else {
            java.util.UUID.randomUUID().toString().also {
                PreferenceHelper.getInstance().setValue(KEY_PLAYER_ID, it)
            }
        }
        cachedPlayerId = id
        return id
    }

    /**
     * 2.4.2 CLEAR (`LocalDataReset`, sau khi xoá prefs): quên id đệm và id đang lưu, xoá belief của chúng; lần
     * [playerId] sau sinh UUID mới. Belief brain còn giữ trong RAM cho id cũ không bao giờ được ghi lại ([saveBelief]).
     */
    @Synchronized
    fun forgetPlayerId() {
        val prefs = PreferenceHelper.getInstance()
        listOfNotNull(cachedPlayerId, prefs.get(KEY_PLAYER_ID, "").takeIf { it.isNotBlank() }).forEach { id ->
            forgottenIds += id
            prefs.remove(KEY_BELIEF_PREFIX + id)
        }
        prefs.remove(KEY_PLAYER_ID)
        cachedPlayerId = null
    }

    private const val KEY_APP_PLAYER_ID = "brain_app_player_id"

    /**
     * Id hợp lệ cuối cùng app truyền (`MonetizationSdk.configure { playerId }` hoặc `AdsBrain.onSessionStart(id)`), đã
     * lưu từ process trước; `null` nếu app chưa từng truyền (hoặc giá trị lưu không qua [PlayerIdSource.isUsable]).
     *
     * Vì sao lưu (review cuối I2): `AppPluginBase.initPlugin` gửi `session_start`/`first_open` trong `super.onCreate()`,
     * trước khi app kịp `configure`, và body dựng bất đồng bộ; override chỉ ở bộ nhớ làm các event đó mang UUID cài đặt
     * hay id app tuỳ race, backend thấy hai user mỗi máy. Đọc từ `PreferenceHelper` (file `XgameHCM-BaseAndroid` đã
     * ấm hoá off main bởi `PrefsWarmup`): tra bảng trong bộ nhớ, không đọc thêm đĩa.
     */
    fun appPlayerId(): String? =
        PreferenceHelper.getInstance().get(KEY_APP_PLAYER_ID, "").takeIf { PlayerIdSource.isUsable(it) }

    /**
     * Lưu id app vừa truyền (đã qua [PlayerIdSource.isUsable]); id khác ghi đè (đăng nhập tài khoản khác), trùng id đã
     * lưu thì không ghi. `apply()`: không chặn luồng gọi. Không có đường xoá: id app sống tới khi app truyền id mới.
     */
    fun saveAppPlayerId(id: String) {
        val prefs = PreferenceHelper.getInstance()
        if (prefs.get(KEY_APP_PLAYER_ID, "") == id) return
        prefs.setValue(KEY_APP_PLAYER_ID, id)
    }

    private const val KEY_OFFER_COUNT = "brain_offer_count_24h"
    private const val KEY_OFFER_DAY = "brain_offer_day"

    private fun today(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    /**
     * Số lần chào bán trong ngày hiện tại.
     *
     * Cửa sổ theo NGÀY (mốc UTC) chứ không phải 24 giờ trượt: cửa sổ trượt cần lưu dấu thời
     * gian của từng lần chào bán, còn cửa sổ theo ngày chỉ cần một bộ đếm và một mốc ngày.
     * Đánh đổi đã biết: người chơi ngay sau nửa đêm được cấp lại hạn mức. Chấp nhận được với
     * một trần chống làm phiền; không chấp nhận được nếu sau này dùng cho hạn mức chi tiêu.
     */
    @Synchronized
    fun loadOfferCount24h(): Int {
        val day = PreferenceHelper.getInstance().get(KEY_OFFER_DAY, 0)
        if (day != today()) return 0
        return PreferenceHelper.getInstance().get(KEY_OFFER_COUNT, 0)
    }

    @Synchronized
    fun bumpOfferCount24h(): Int {
        val n = loadOfferCount24h() + 1
        PreferenceHelper.getInstance().setValue(KEY_OFFER_DAY, today())
        PreferenceHelper.getInstance().setValue(KEY_OFFER_COUNT, n)
        return n
    }

    /** Xoá toàn bộ trạng thái học được của một player — dùng cho nút reset khi test. */
    fun clearBelief(playerId: String) {
        PreferenceHelper.getInstance().remove(KEY_BELIEF_PREFIX + playerId)
    }
}
