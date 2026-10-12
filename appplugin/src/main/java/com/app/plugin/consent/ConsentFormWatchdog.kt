package com.app.plugin.consent

/**
 * Quyết định khi nào luồng consent được mở cổng, tách khỏi UMP để kiểm chứng bằng unit test.
 *
 * Gộp hai bài học trên máy thật của hai app fork, vốn mỗi bên chỉ sửa được một nửa:
 * - Speaker Clean: timeout vô điều kiện mở cổng sau 5 giây dù form còn trên màn hình, nên app
 *   vẽ paywall và quảng cáo đè lên form, và ở EEA là trước khi có consent. Timeout chỉ được
 *   canh cuộc gọi mạng `requestConsentInfoUpdate`.
 * - Invoice Maker (BUG-07): ở cold start, form có thể tải xong mà không bao giờ hiện (không có
 *   WebView nào), hoặc callback không bao giờ về. Chỉ chờ callback thì cổng treo vĩnh viễn.
 *
 * Tín hiệu phân biệt "form đang hiện" với "form hỏng im lặng": form là một Dialog giữ window
 * focus, nên Activity mất focus chừng nào form còn trên màn hình.
 */
internal object ConsentFormWatchdog {

    /** Thời gian chờ tối đa cho `requestConsentInfoUpdate` (mất mạng, lỗi SDK). */
    const val INFO_UPDATE_TIMEOUT_MS = 5_000L

    /** Chu kỳ kiểm tra khi form đã được yêu cầu hiện mà callback chưa về. */
    const val FORM_CHECK_INTERVAL_MS = 5_000L

    /** Người dùng thật cần vài giây để đọc và chọn; callback về sớm hơn là form chưa hiện. */
    const val FORM_MIN_INTERACTION_MS = 4_000L

    /** Chờ Activity render ổn định rồi mới thử hiện lại form. */
    const val FORM_RETRY_DELAY_MS = 3_000L

    enum class Action {
        /** Callback đã về: callback tự mở cổng, ngừng kiểm tra. */
        STOP,

        /** Activity đã mất: mở cổng để app không treo, không thử lại trên Activity chết. */
        RELEASE,
        /**
         * Activity mang form đã mất (xoay màn hình, đổi dark mode, đổi ngôn ngữ) mà người dùng chưa
         * trả lời, và app đã có Activity mới: hiện form trên Activity mới. Mở cổng ở đây thì ở EEA
         * cả phiên không có quảng cáo, vì mạng chỉ khởi tạo sau consent (1.3.0).
         */
        SHOW_ON_NEWER_ACTIVITY,

        /** Form đang trên màn hình: chờ người dùng đóng nó, kiểm tra lại sau. */
        KEEP_WAITING,

        /**
         * Lần đầu thấy không có form mà callback chưa về. Có thể form hỏng im lặng, cũng có thể
         * form còn đang tải trên mạng chậm: mở cổng cho app chạy tiếp, nhưng chưa thử hiện lại,
         * vì gọi lần hai khi lần một còn đang tải sẽ ra hai form chồng nhau.
         */
        RELEASE_AND_WAIT,

        /** Lần thứ hai vẫn không có form và không có callback: form hỏng im lặng, thử lại. */
        RETRY,
    }

    /**
     * @param silentChecks số lần kiểm tra trước đó đã thấy "không có form, không có callback".
     * @param newerActivityAlive app đã gọi luồng consent từ một Activity khác, còn sống.
     * @param stillRequired UMP vẫn báo REQUIRED (người dùng chưa trả lời).
     */
    fun onFormCheck(
        callbackFired: Boolean,
        activityGone: Boolean,
        activityHasFocus: Boolean,
        silentChecks: Int,
        newerActivityAlive: Boolean = false,
        stillRequired: Boolean = false,
    ): Action = when {
        callbackFired -> Action.STOP
        activityGone && newerActivityAlive && stillRequired -> Action.SHOW_ON_NEWER_ACTIVITY
        activityGone -> Action.RELEASE
        !activityHasFocus -> Action.KEEP_WAITING
        silentChecks == 0 -> Action.RELEASE_AND_WAIT
        else -> Action.RETRY
    }

    /** Callback về quá nhanh mà consent vẫn REQUIRED nghĩa là người dùng chưa từng thấy form. */
    fun shouldRetryAfterCallback(elapsedMs: Long, stillRequired: Boolean): Boolean =
        stillRequired && elapsedMs < FORM_MIN_INTERACTION_MS
}
