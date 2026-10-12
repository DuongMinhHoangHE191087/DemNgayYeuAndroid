package com.app.plugin.iap

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Billing 9 với `enableAutoServiceReconnection()` gửi mọi kết quả kết nối lại về đúng listener
 * của `startConnection`. Một lời gọi API trong lúc lần kết nối đầu chưa xong sinh ra kết quả
 * `DEVELOPER_ERROR` ("already in the process of connecting"), rồi kết quả OK thật tới sau.
 * Đo trên emulator 2026-09-23 (doc 64 mục D).
 */
class BillingSetupOutcomeTest {

    @Test
    fun `OK la da ket noi`() {
        assertEquals(
            BillingSetupOutcome.CONNECTED,
            BillingSetupOutcome.of(BillingResponseCode.OK, ConnectionState.CONNECTED)
        )
    }

    @Test
    fun `DEVELOPER_ERROR khi client van dang ket noi la cho ket qua that, khong phai loi`() {
        assertEquals(
            BillingSetupOutcome.STILL_CONNECTING,
            BillingSetupOutcome.of(BillingResponseCode.DEVELOPER_ERROR, ConnectionState.CONNECTING)
        )
    }

    /**
     * F2B (Samsung A25 2026-09-30): kết quả "already connecting" của lần thử kết nối lại được thư viện
     * post về main; main bận thì nó tới SAU khi kết nối đầu đã xong. Trạng thái lúc giao là CONNECTED —
     * đó là kết quả cũ, không phải lỗi (từng log `E setup failed: 5` ngay sau `connected`).
     */
    @Test
    fun `DEVELOPER_ERROR toi sau khi da ket noi la ket qua cu, khong phai loi`() {
        assertEquals(
            BillingSetupOutcome.STILL_CONNECTING,
            BillingSetupOutcome.of(BillingResponseCode.DEVELOPER_ERROR, ConnectionState.CONNECTED)
        )
    }

    @Test
    fun `DEVELOPER_ERROR khi client khong con ket noi la loi that`() {
        assertEquals(
            BillingSetupOutcome.FAILED,
            BillingSetupOutcome.of(BillingResponseCode.DEVELOPER_ERROR, ConnectionState.DISCONNECTED)
        )
    }

    @Test
    fun `ma loi khac trong luc dang ket noi van la loi`() {
        assertEquals(
            BillingSetupOutcome.FAILED,
            BillingSetupOutcome.of(BillingResponseCode.BILLING_UNAVAILABLE, ConnectionState.CONNECTING)
        )
    }
}
