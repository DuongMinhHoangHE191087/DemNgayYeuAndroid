package com.app.plugin.iap

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.app.plugin.SdkKillSwitch
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never

/**
 * Task 16: kill switch `iap` không kết nối Play Billing. Chỉ tắt (Điều 13): quyền lợi đã lưu giữ nguyên,
 * `restorePurchases` trả bản đã lưu khi billing không sẵn sàng.
 */
class IapHelperKillSwitchTest {
    @Before fun setUp() { SdkKillSwitch.current = SdkKillSwitch.NONE }
    @After fun tearDown() { SdkKillSwitch.current = SdkKillSwitch.NONE }

    /**
     * Đợt sửa cuối Phase B, mục 8: kill tới giữa phiên khi billing đã kết nối. Lần `init`/`configure` kế tiếp
     * từng báo "billing bị kill" (`false` + logE) dù billing vẫn kết nối; giờ kiểm "đã kết nối" trước.
     */
    @Test fun `da ket noi roi kill toi thi init bao true, khong log kill`() {
        IapHelper.replacePort(PlayBillingPort(mock(BillingClient::class.java)))
        try {
            SdkKillSwitch.current = SdkKillSwitch(iap = true)
            mockStatic(Log::class.java).use { log ->
                assertTrue("dang ket noi", IapHelper.init(mock(Context::class.java)))
                log.verify({ Log.e(eq(LogUtil.TAG), contains("kill switch: iap")) }, never())
            }
        } finally {
            IapHelper.replacePort(null)
        }
    }

    @Test fun `kill iap thi init khong dung BillingClient`() {
        SdkKillSwitch.current = SdkKillSwitch(iap = true)

        mockStatic(BillingClient::class.java).use { billing ->
            assertFalse("init báo không kết nối (fix wave 1, Minor 3)", IapHelper.init(mock(Context::class.java)))
            billing.verifyNoInteractions()
        }
        assertFalse(IapHelper.isReady())
    }
}
