package com.app.plugin.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsConfigOb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsKillSwitchTest
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainHandlerTimers
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.logevent.SessionTracker
import com.app.plugin.segment.SegmentTestSupport
import com.app.plugin.until.LogUtil
import com.hostfix.sample.App
import com.hostfix.sample.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 2.5.0 LAM1 (Hợp đồng C, quyết định (d), D1-250): `AdsHelper.setAutoShowOpenAdOnForeground(false)` chỉ tắt lượt SDK tự gọi
 * `showOpenAd(full_resume)` 1 s sau foreground (`AppPluginBase.onStart`, kiểm lúc hẹn và lúc chạy, một dòng `Log.i`
 * `ads open ad auto-show on foreground skipped (app)`); app vẫn gọi `showOpenAd` được (khác `setFlag4ShowResume(false)`).
 * Mặc định `true` = 2.4.4. Dấu hiệu "lượt tự gọi tới showOpenAd": logD `full_resume showOpenAd openFlag4Resume=false`
 * (cờ resume mặc định tắt). Chạm singleton `AdsHelper.instance`: lưu và trả lại field ở [after].
 */
class AutoShowOpenAdTest {
    private val ads = AdsHelper.instance
    private val saved = mutableMapOf<String, Any?>()
    private val owner = mock(LifecycleOwner::class.java)

    private fun adsField(name: String) = AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Before fun before() {
        for (n in FIELDS) saved[n] = adsField(n).get(ads)
        adsField("listAdsAll").set(ads, mutableListOf<AdsBase>())
        adsField("listAdsFull").set(ads, mutableMapOf<Int, AdsBaseFull>())
        adsField("openFlag4Resume").set(ads, false)
        ads.currConfig = AdsConfigOb(mock(Context::class.java))
        ads.placementsConfigManager = AdsPlacementConfigManager()
        LogUtil.setFlagLog(3)
    }

    @After fun after() {
        for (n in FIELDS) adsField(n).set(ads, saved[n])
        ads.setAutoShowOpenAdOnForeground(true)
        LogUtil.setFlagLog(1)
        SessionTracker.resetForTest()
        SegmentTestSupport.reset(tier = null)
    }

    /** App đã qua lần foreground đầu (`isSStart` = false), Activity hiện tại có sẵn: `onStart` = foreground lần 2. */
    private fun appInForeground(): App = App("com.hostfix.sample.debug", mock(PackageManager::class.java)).also { app ->
        AppPluginBase::class.java.getDeclaredField("isSStart").apply { isAccessible = true }.setBoolean(app, false)
        AppPluginBase::class.java.getDeclaredField("currentActivity").apply { isAccessible = true }.set(app, MainActivity())
    }

    private fun verifyAutoShowReached(log: org.mockito.MockedStatic<Log>, reached: Boolean) =
        log.verify({ Log.d(eq(LogUtil.TAG), contains("${PlacementDefault.PLFullResume} showOpenAd")) }, if (reached) atLeastOnce() else never())

    private fun verifySkipped(log: org.mockito.MockedStatic<Log>, n: Int) =
        log.verify({ Log.i(LogUtil.TAG, SKIPPED) }, times(n))

    @Test fun `L1 AdsHelper co setAutoShowOpenAdOnForeground(Boolean) public`() {
        val m = AdsHelper::class.java.methods.filter { it.name == "setAutoShowOpenAdOnForeground" }
        assertEquals("AdsHelper.setAutoShowOpenAdOnForeground(Boolean) (API chỉ thêm)",
            listOf(listOf<Class<*>?>(java.lang.Boolean.TYPE)), m.map { it.parameterTypes.toList() })
    }

    @Test fun `L2 tat thi onStart khong hen showOpenAd full_resume, mot dong skipped (app)`() = MainHandlerTimers.run { timers ->
        mockStatic(Log::class.java).use { log ->
            ads.setAutoShowOpenAdOnForeground(false)
            appInForeground().onStart(owner)
            assertEquals("không hẹn 1 s", emptyList<Long>(), timers.pendingDelays)
            timers.advanceTo(5_000)
            verifyAutoShowReached(log, reached = false)
            verifySkipped(log, 1)
        }
    }

    @Test fun `L3 tat trong 1 s sau khi hen thi luot hen khong hien`() = MainHandlerTimers.run { timers ->
        mockStatic(Log::class.java).use { log ->
            appInForeground().onStart(owner)
            assertEquals("hẹn 1 s như 2.4.4", listOf(1_000L), timers.pendingDelays)
            ads.setAutoShowOpenAdOnForeground(false)
            timers.advanceTo(1_000)
            verifyAutoShowReached(log, reached = false)
            verifySkipped(log, 1)
        }
    }

    @Test fun `L4 mac dinh hen 1 s va goi showOpenAd full_resume nhu 2_4_4`() = MainHandlerTimers.run { timers ->
        assertTrue("AdsHelper mới dựng: tự hiện bật (mặc định 2.4.4)", AdsKillSwitchTest.newAdsHelper().autoShowOpenAdOnForeground())
        mockStatic(Log::class.java).use { log ->
            appInForeground().onStart(owner)
            assertEquals(listOf(1_000L), timers.pendingDelays)
            timers.advanceTo(1_000)
            verifyAutoShowReached(log, reached = true)
            verifySkipped(log, 0)
        }
    }

    /** Mạng app open giả: `showAd` hiện được. */
    private class OpenNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullOpen, context) {
        override val eventTag: String get() = "test_auto_open"
        val shows = mutableListOf<String>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_auto_open_data"
        override fun getKeyForResDf(): String = "test_auto_open_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) = Unit
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            adsShowCb.onAdShowed(placement, placement, "t", flagShow)
            return true
        }
    }

    /** Helper riêng (không singleton) có một mạng app open hiện được; tự hiện lúc foreground tắt, cờ resume = [resumeFlag]. */
    private fun appCallsOpenAd(resumeFlag: Boolean): List<String> {
        val ctx = mock(Context::class.java).also { c ->
            val res = mock(Resources::class.java)
            `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
            `when`(c.resources).thenReturn(res)
            `when`(c.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        }
        val h = AdsKillSwitchTest.newAdsHelper()
        h.currConfig.apply { openAdFirstType = 0; openAdtype = 0; fullStepCicle.clear(); fullStepRe.clear() }
        val open = OpenNetwork(ctx)
        AdsHelper::class.java.getDeclaredField("listAdsFull").apply { isAccessible = true }
            .set(h, mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullOpen to open))
        AdsHelper::class.java.getDeclaredField("listAdsAll").apply { isAccessible = true }.set(h, mutableListOf<AdsBase>())
        h.setAutoShowOpenAdOnForeground(false)
        h.setFlag4ShowResume(resumeFlag)
        assertEquals("showOpenAd trả về (cờ resume $resumeFlag)", resumeFlag, h.showOpenAd("resume", null))
        return open.shows
    }

    @Test fun `L5 tat tu hien van cho app goi showOpenAd full_resume, khac setFlag4ShowResume false`() = MainHandlerTimers.run(honorRemoveAll = true) {
        assertEquals("app tự gọi vẫn hiện", listOf(PlacementDefault.PLFullResume), appCallsOpenAd(resumeFlag = true))
        assertEquals("setFlag4ShowResume(false) chặn cả lượt app gọi", emptyList<String>(), appCallsOpenAd(resumeFlag = false))
    }

    private companion object {
        const val SKIPPED = "ads open ad auto-show on foreground skipped (app)"
        val FIELDS = listOf("listAdsAll", "listAdsFull", "mActivity", "currConfig", "placementsConfigManager", "openFlag4Resume")
    }
}
