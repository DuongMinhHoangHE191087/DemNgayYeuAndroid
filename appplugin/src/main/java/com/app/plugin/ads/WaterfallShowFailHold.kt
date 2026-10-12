package com.app.plugin.ads

import com.app.plugin.until.LogUtil

/**
 * Giữ `onAdShowFail` mà một mạng báo ĐỒNG BỘ trong `showAd()` cho tới khi vòng show (app open,
 * interstitial, rewarded) biết còn mạng nào hiện được không.
 *
 * Mạng có quảng cáo mà không hiện được (native full trên host đã huỷ, `Dialog.show()` ném
 * BadToken, rewarded khi chưa có Activity...) gọi `onAdShowFail` rồi trả `false`, và vòng đi tiếp.
 * Chuyển lỗi đó tới app ngay thì app nhận fail rồi mạng sau vẫn hiện: fail → showed → dismissed
 * (doc 64 D). Với lớp này:
 * - một mạng sau hiện được: lỗi đã giữ bị bỏ (mạng tự gửi lý do qua `LogEventServer`);
 * - không mạng nào hiện: đúng một lỗi, lỗi đầu tiên, chạy ở [settle];
 * - mạng báo lỗi đồng bộ mà vẫn trả `true`: lỗi đó chạy ở [settle], sau phần ghi sổ "đã hiện" của
 *   vòng, nên cờ full-screen và bộ đếm trở về như một lần fail thường thay vì kẹt.
 * Lỗi tới sau khi `showAd()` đã trả về (lỗi bất đồng bộ của mạng) không bị giữ.
 *
 * Mạng show trực tiếp, ngoài vòng, không đi qua lớp này: vẫn báo đúng một `onAdShowFail`.
 * Mỗi lần chạy vòng một instance; chỉ dùng trên main thread như vòng show.
 */
internal class WaterfallShowFailHold(private val placement: String) {
    private var inShowCall = false
    private var failOfCall: (() -> Unit)? = null
    private var firstHeld: (() -> Unit)? = null
    private var failOfShownCall: (() -> Unit)? = null

    /** Gọi `showAd()` của một mạng; lỗi mạng đó báo trong lúc gọi được giữ lại. */
    fun during(showAd: () -> Boolean): Boolean {
        inShowCall = true
        failOfCall = null
        val shown = try {
            showAd()
        } finally {
            inShowCall = false
        }
        val fail = failOfCall
        failOfCall = null
        if (shown) {
            failOfShownCall = fail
        } else if (firstHeld == null) {
            firstHeld = fail
        }
        return shown
    }

    /**
     * Đầu `onAdShowFail` của vòng: `true` là lỗi đã được giữ và callback phải return ngay; [fail]
     * gọi lại chính callback đó khi lỗi được chạy. `false` là không trong lúc `showAd()`: xử lý như cũ.
     */
    fun hold(fail: () -> Unit): Boolean {
        if (!inShowCall) return false
        if (failOfCall == null) failOfCall = fail
        return true
    }

    /** Gọi một lần, sau vòng lặp, với kết quả của vòng. */
    fun settle(shown: Boolean) {
        val fail = if (shown) failOfShownCall else firstHeld
        if (shown && firstHeld != null) {
            LogUtil.logD("ads full helper $placement sync show fail dropped: a later network showed")
        }
        firstHeld = null
        failOfShownCall = null
        fail?.invoke()
    }
}
