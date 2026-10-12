package com.app.plugin.brain

import com.app.plugin.until.LogUtil

/**
 * Player id đang có hiệu lực: id app truyền qua `MonetizationSdk.configure { playerId }` hoặc thẳng qua
 * `AdsBrain.onSessionStart(id)` trong process này nếu có; nếu không thì id app đã lưu từ process trước
 * (`BrainStorage.appPlayerId()`, review cuối I2); nếu không nữa thì `BrainStorage.playerId()` (UUID sinh một lần mỗi
 * lần cài).
 * Một nguồn duy nhất: brain phân nhóm theo đúng id này (`AdsBrain.onSessionStart()` không tham số cũng đọc nó).
 *
 * Telemetry server lấy `user_id` từ đây (`LogEventServerHelper`), và web shop (`PurchaseRouter`) theo đó. Backend
 * giả định `user_id == playerId`: `reconcile.py` lớp 1 tính lại bucket theo `user_id`, `metrics.py` nhóm holdout
 * theo `user_id` (#13, DOC/66 mục 4). Không dùng định danh thiết bị như ANDROID_ID: lý do như KDoc của
 * `BrainStorage.playerId()`.
 *
 * Đặt trong `brain` chứ không trong `MonetizationSdk`: `logevent` đã phụ thuộc `brain` (`DecisionLogger`), nên đọc
 * id ở đây không tạo phụ thuộc mới từ `logevent` lên lớp tích hợp.
 */
internal object PlayerIdSource {

    /**
     * Id app truyền vào trong process này; `null` = dùng id app đã lưu, hoặc id của SDK. Ghi qua [setFromApp] (bộ nhớ
     * và prefs); test ghi thẳng khi chỉ cần bộ nhớ.
     */
    @Volatile
    var override: String? = null

    /**
     * Id app truyền (`MonetizationSdk.configure`, `AdsBrain.onSessionStart(id)`): ghi [override] và lưu cho process sau
     * (`BrainStorage.saveAppPlayerId`), để `session_start`/`first_open` của lần mở kế — gửi trong `super.onCreate()`,
     * trước `configure` — mang đúng id này (review cuối I2). Id khác ghi đè id đã lưu. Id không qua [isUsable] không
     * được ghi (người gọi đã từ chối và log; ở đây log lỗi phòng người gọi mới quên).
     */
    fun setFromApp(id: String) {
        if (!isUsable(id)) {
            LogUtil.logE("PlayerIdSource.setFromApp: playerId rỗng hoặc chứa U+001F/xuống dòng — bỏ qua")
            return
        }
        override = id
        BrainStorage.saveAppPlayerId(id)
    }

    /**
     * Id app truyền vào dùng được không: không rỗng, không chứa U+001F (phân tách trường của decision log), `\n`, `\r`.
     * Id như vậy làm hỏng dòng hàng đợi decision log qua `decision_id = "d_<id>_…"` (review N2), nên cả `configure` và
     * `AdsBrain.onSessionStart(id)` từ chối nó ở cửa vào.
     */
    fun isUsable(id: String): Boolean = id.isNotBlank() && id.none { it == '\u001F' || it == '\n' || it == '\r' }

    /**
     * Id app truyền trong process này, nếu không thì id app đã lưu (`BrainStorage.appPlayerId()`), nếu không thì
     * `BrainStorage.playerId()`. Đọc mỗi lần gọi, không đệm. Lần mở đầu tiên sau khi nâng lên 1.4.0 (chưa có id lưu)
     * `session_start`/`first_open` vẫn có thể mang UUID; từ lần sau là id app.
     */
    fun current(): String = override ?: BrainStorage.appPlayerId() ?: BrainStorage.playerId()

    /**
     * Như process mới: chưa có id app trong bộ nhớ (id đã lưu ở prefs giữ nguyên). Chỉ cho test
     * (`MonetizationSdk.resetForTest`).
     */
    @JvmSynthetic
    internal fun resetForTest() {
        override = null
    }
}
