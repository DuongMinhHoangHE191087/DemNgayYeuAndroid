package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * Lưới cho rủi ro R-6 của plan 1.5.0 (review Task 1, I1): móc Full2 trong [AdsHelper.onAdLoaded] — `native_full` về
 * khi chuỗi Full2 đang chuyển cảnh (`full2TransitionActive`) thì `full2MainHandler.post { prepareChainedFull2() }` để
 * quảng cáo native-full tải muộn vẫn được dựng sẵn sau quảng cáo cha. Đi qua hàm public; quan sát bằng
 * `prepareChainedShow()` của mạng Full2 giả (bước duy nhất trong `full2StepCicle`) và hàng đợi Handler ảo
 * ([MainHandlerTimers]), không nới quyền truy cập mã sản phẩm. Móc mất, đặt sau return, hay sai điều kiện → đỏ.
 */
class Full2LoadHookTest {
    private class Fixture(val helper: AdsHelper, val network: AdsBaseFull, val timers: MainHandlerTimers)

    /** `AdsHelper` dựng trong [MainHandlerTimers.run] (để `full2MainHandler` là Handler ảo), Full2 một bước. */
    private fun withHelper(transitionActive: Boolean, check: Fixture.() -> Unit) = MainHandlerTimers.run { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.full2StepCicle.add(AdsIDDef.MaxFullNt)
        val network = mock(AdsBaseFull::class.java)
        setField(helper, "listAdsFull", mutableMapOf(AdsIDDef.MaxFullNt to network))
        setField(helper, "full2TransitionActive", transitionActive)
        Fixture(helper, network, timers).check()
    }

    private fun setField(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    private fun AdsHelper.loaded(adFormat: String) =
        onAdLoaded("ntfull_home", "ntfull_home", adFormat, "applovin", "id-ntfull", "APPLOVIN_NETWORK")

    @Test fun `native_full ve khi dang chuyen canh Full2 - post dung mot runnable goi prepareChainedFull2`() =
        withHelper(transitionActive = true) {
            helper.loaded("native_full")

            assertEquals("đúng một runnable được post", 1, timers.pendingCount)
            verify(network, never()).prepareChainedShow() // post, không chạy đồng bộ
            timers.runPosted()
            verify(network, times(1)).prepareChainedShow()
            assertEquals(0, timers.pendingCount)
        }

    @Test fun `interstitial ve khi dang chuyen canh - khong post`() = withHelper(transitionActive = true) {
        helper.loaded("interstitial")

        assertEquals(0, timers.pendingCount)
        timers.runPosted()
        verify(network, never()).prepareChainedShow()
    }

    @Test fun `native_full ve khi khong chuyen canh - khong post`() = withHelper(transitionActive = false) {
        helper.loaded("native_full")

        assertEquals(0, timers.pendingCount)
        timers.runPosted()
        verify(network, never()).prepareChainedShow()
    }
}
