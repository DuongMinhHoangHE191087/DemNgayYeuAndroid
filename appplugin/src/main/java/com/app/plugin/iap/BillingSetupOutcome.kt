package com.app.plugin.iap

import com.android.billingclient.api.BillingClient

/**
 * Kết quả `onBillingSetupFinished`, đã tính tới cách Billing 9 kết nối lại.
 *
 * Với `enableAutoServiceReconnection()`, mọi lần thư viện tự kết nối lại đều báo kết quả về
 * listener của `startConnection`. Một lời gọi API trong lúc lần kết nối đầu chưa xong cho ra
 * `DEVELOPER_ERROR` ("already in the process of connecting") trong khi client vẫn đang kết nối,
 * rồi kết quả thật tới sau. Coi đó là lỗi thì mỗi lần mở app đều ghi một lỗi giả (doc 64 mục D).
 * Kết quả đó được post về main nên có thể tới SAU khi kết nối đã xong (CONNECTED): vẫn là kết quả cũ (F2B).
 *
 * Hành vi này là nội bộ của thư viện, đọc từ bytecode `billing:9.0.0` (listener nội bộ `zzbs`
 * chuyển kết quả kết nối lại về listener của app). Nâng phiên bản billing thì kiểm lại.
 */
internal enum class BillingSetupOutcome {
    CONNECTED, STILL_CONNECTING, FAILED;

    companion object {
        fun of(responseCode: Int, connectionState: Int): BillingSetupOutcome = when {
            responseCode == BillingClient.BillingResponseCode.OK -> CONNECTED
            responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR && (
                connectionState == BillingClient.ConnectionState.CONNECTING ||
                    connectionState == BillingClient.ConnectionState.CONNECTED
                ) -> STILL_CONNECTING
            else -> FAILED
        }
    }
}
