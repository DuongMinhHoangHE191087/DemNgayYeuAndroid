package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.ViewGroup
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * Task 16, addendum A3: show bị kill switch `ads` chặn mang nhãn riêng
 * [Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH], không rơi vào nhãn remove-ads. Đi đúng đường
 * của lượt bị chặn đồng bộ: interstitial / app open / rewarded trả `false` và không callback (hợp đồng
 * 1.3.1), native báo đúng một `onAdShowFail`. Người dùng đã trả tiền gỡ định dạng đó thì giữ nhãn
 * remove-ads (lý do của họ, không phải của ops).
 *
 * Telemetry đọc ở body mà Retrofit nhận ([ServerEventCapture]): đúng nhãn, đúng số event (fix wave 1,
 * Minor 6: không còn dựa vào dòng log "dropping event").
 */
class AdsKillSwitchTest {

    private class RecordingShowCb : AdsGiftShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            events += "dismissed"
        }
    }

    private lateinit var helper: AdsHelper

    @Before
    fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
        helper = newAdsHelper()
    }

    @After
    fun tearDown() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
        LogUtil.setFlagLog(1)
    }

    private fun removeAdsBought() {
        AdsHelper::class.java.getDeclaredField("pendingRemoveAdsMask").apply { isAccessible = true }
            .set(helper, RemoveAdsMask.ALL)
    }

    private val kill = Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH

    @Test fun `kill ads tat moi dinh dang, ke ca theo phien ban`() {
        for (flag in 0..6) assertFalse("không kill: flag $flag", helper.isDisableAds(flag))
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        for (flag in 0..6) assertTrue("kill ads: flag $flag", helper.isDisableAds(flag))
        SdkKillSwitch.current = SdkKillSwitch(belowVersion = "999.0.0")
        for (flag in 0..6) assertTrue("kill theo phiên bản: flag $flag", helper.isDisableAds(flag))
        SdkKillSwitch.current = SdkKillSwitch(iap = true, logevent = true)
        for (flag in 0..6) assertFalse("kill iap/logevent không tắt quảng cáo: flag $flag", helper.isDisableAds(flag))
    }

    @Test fun `interstitial bi kill - tra false, khong callback, dung mot event mang nhan kill switch`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        LogUtil.setFlagLog(3)
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            mockStatic(Log::class.java).use { log ->
                val shown = helper.showInterstitial("home", 0, cb, isDeltaTime = false)

                assertFalse(shown)
                assertEquals(emptyList<String>(), cb.events)
                // Minor 7: một dòng debug cho lượt bị chặn, không thêm dòng "isremoveads or disable".
                log.verify({ Log.d(eq(LogUtil.TAG), eq("ads helper full_home show blocked: $kill")) }, times(1))
                log.verify({ Log.d(eq(LogUtil.TAG), contains("isremoveads or disable")) }, never())
            }
            assertEquals(listOf("full_home" to kill), server.showFails(1))
        }
    }

    @Test fun `app open bi kill - tra false, khong callback, dung mot event mang nhan kill switch`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            assertFalse(helper.showOpenAd(PlacementDefault.PLFullSplash, cb))

            assertEquals(emptyList<String>(), cb.events)
            assertEquals(listOf(PlacementDefault.PLFullSplash to kill), server.showFails(1))
        }
    }

    @Test fun `native bi kill - dung mot onAdShowFail va mot event voi nhan kill switch`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            helper.showNative("home", mock(ViewGroup::class.java), cb)

            assertEquals(listOf("fail:$kill"), cb.events)
            assertEquals(listOf("nt_home" to kill), server.showFails(1))
        }
    }

    /** Fix wave 1, Minor 8: rewarded bị chặn cũng gửi `ad_show_fail_reason`, trước đây im lặng. */
    @Test fun `rewarded bi kill - tra false, khong callback, dung mot event mang nhan kill switch`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        val cb = RecordingShowCb()
        ServerEventCapture().use { server ->
            assertFalse(helper.showRewarded("home", cb))

            assertEquals(emptyList<String>(), cb.events)
            assertEquals(listOf("gift_home" to kill), server.showFails(1))
        }
    }

    @Test fun `da mua go quang cao thi giu nhan remove-ads du kill dang bat`() {
        SdkKillSwitch.current = SdkKillSwitch(ads = true)
        removeAdsBought()
        val cb = RecordingShowCb()
        // Mỗi lượt một capture: từ 1.7.0 Task 2 mỗi lượt gọi còn phát `ad_show_attempt`, 3 lượt = 6 event > trần 4 request
        // đang bay của hàng đợi (HeldCall không trả lời), event thứ 5–6 kẹt lại, không tới capture.
        ServerEventCapture().use { server ->
            assertFalse(helper.showInterstitial("home", 0, cb, isDeltaTime = false))
            assertEquals(listOf("full_home" to Constants.AdShowFailReason.SHOW_FULL_WHEN_REMOVE_ADS), server.showFails(1))
        }
        ServerEventCapture().use { server ->
            helper.showNative("home", mock(ViewGroup::class.java), cb)
            assertEquals(listOf("nt_home" to Constants.AdShowFailReason.REMOVE_AD), server.showFails(1))
        }
        ServerEventCapture().use { server ->
            assertFalse(helper.showRewarded("home", cb))
            assertEquals(listOf("gift_home" to Constants.AdShowFailReason.REMOVE_AD), server.showFails(1))
        }
        assertEquals(listOf("fail:showNative isremoveads or disable"), cb.events)
    }

    internal companion object {
        /** `AdsHelper` mới (constructor private), không có mạng nào, cấu hình mặc định: mọi định dạng bật. */
        fun newAdsHelper(): AdsHelper {
            val context = mock(Context::class.java)
            val resources = mock(Resources::class.java)
            `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
            `when`(context.resources).thenReturn(resources)
            `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
            return AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().apply {
                currConfig = AdsConfigOb(mock(Context::class.java))
                placementsConfigManager = AdsPlacementConfigManager()
            }
        }
    }
}
