package com.app.plugin.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsBaseFull
import com.app.plugin.ads.AdsConfigOb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementConfigManager
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainHandlerTimers
import com.google.android.gms.ads.HostFixFakeAdActivity
import com.hostfix.other.LaunchActivity
import com.hostfix.samplex.LookalikeActivity
import com.mbridge.hostfix.MediationActivity
import com.mbridge.hostfix.MediationApp
import com.hostfix.sample.App
import com.hostfix.sample.MainActivity
import com.hostfix.sample.OtherActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.1 HOSTFIX (D36, `sdd/im/full2-redmi-analysis.md`): `AppPluginBase.onActivityResumed` nhận host không theo tiền tố
 * `packageName` (bản debug có `applicationIdSuffix` không bao giờ khớp → `onMainActivityResumed` không chạy → Full2 xếp
 * hàng rồi bị bỏ). Activity của app = launcher của `packageName`, HOẶC lớp trong gói của lớp Application, HOẶC (như cũ) tiền tố
 * `packageName`; không bao giờ Activity của SDK quảng cáo/mediation; host = Activity của app resume gần nhất (như 2.4.0).
 * Chạm singleton `AdsHelper.instance`/`AppPluginManager.instance`: lưu và trả lại mọi trường ở [after].
 */
class AppPluginBaseHostTest {
    private val ads = AdsHelper.instance
    private val manager = AppPluginManager.instance
    private val saved = mutableMapOf<String, Any?>()
    private var savedResumed: Any? = null
    private var savedManagerMain: Any? = null

    private fun adsField(name: String) = AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }
    private val policyResumed get() =
        ads.fullscreenPolicy().javaClass.getDeclaredField("isHostActivityResumed").apply { isAccessible = true }
    private val managerMain get() = AppPluginManager::class.java.getDeclaredField("mainActivity").apply { isAccessible = true }

    @Before fun before() {
        for (n in FIELDS) saved[n] = adsField(n).get(ads)
        savedResumed = policyResumed.get(ads.fullscreenPolicy())
        savedManagerMain = managerMain.get(manager)
        adsField("listAdsAll").set(ads, mutableListOf<AdsBase>())
        adsField("listAdsFull").set(ads, mutableMapOf<Int, AdsBaseFull>())
        adsField("mActivity").set(ads, null)
        adsField("pendingFull2").set(ads, null)
        ads.currConfig = AdsConfigOb(mock(Context::class.java))
        ads.placementsConfigManager = AdsPlacementConfigManager()
        policyResumed.set(ads.fullscreenPolicy(), false)
        managerMain.set(manager, null)
    }

    @After fun after() {
        for (n in FIELDS) adsField(n).set(ads, saved[n])
        policyResumed.set(ads.fullscreenPolicy(), savedResumed)
        managerMain.set(manager, savedManagerMain)
    }

    private fun pm(launcher: String?): PackageManager = mock(PackageManager::class.java).also { pm ->
        val intent = launcher?.let { cls ->
            val c = mock(ComponentName::class.java).also { `when`(it.className).thenReturn(cls) }
            mock(Intent::class.java).also { `when`(it.component).thenReturn(c) }
        }
        `when`(pm.getLaunchIntentForPackage(anyString())).thenReturn(intent)
    }

    private fun isHost(a: Activity) = manager.checkMainActivity(a)

    @Test fun `T1 ban debug co applicationIdSuffix - launcher MainActivity resume la host, Full2 khong con bi hoan`() {
        val app = App("com.hostfix.sample.debug", pm("com.hostfix.sample.MainActivity"))
        val main = MainActivity()

        app.onActivityResumed(main)

        assertTrue("host = MainActivity dù tên lớp không bắt đầu bằng applicationId", isHost(main))
        assertFalse("onMainActivityResumed đã chạy", ads.fullscreenPolicy().shouldDeferFull2UntilHostResumed())
    }

    @Test fun `T1b launcher ngoai goi cua lop Application van la host`() {
        val app = App("com.hostfix.sample.debug", pm("com.hostfix.other.LaunchActivity"))
        val launch = LaunchActivity()

        app.onActivityResumed(launch)

        assertTrue(isHost(launch))
    }

    @Test fun `T1c khong co launcher - Activity trong goi cua lop Application la host`() {
        val app = App("com.hostfix.sample.debug", pm(null))
        val other = OtherActivity()

        app.onActivityResumed(other)

        assertTrue(isHost(other))
        assertFalse("ngoài gói app, không launcher, không tiền tố applicationId → không host", run {
            val launch = LaunchActivity(); app.onActivityResumed(launch); isHost(launch)
        })
    }

    @Test fun `T2 Full2 xep hang cho host resume duoc hien khi launcher resume (khong bi bo)`() = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        adsField("full2MainHandler").set(ads, android.os.Handler(android.os.Looper.getMainLooper()))   // handler ảo của timers
        ads.currConfig.apply { full2FlagShow = 1; full2StepCicle.clear(); full2StepCicle.add(AdsIDDef.AdmobFullNt); full2StepRe.clear() }
        val net = Full2Net(context())
        adsField("listAdsFull").set(ads, mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to net))
        adsField("listAdsAll").set(ads, mutableListOf<AdsBase>(net))
        val app = App("com.hostfix.sample.debug", pm("com.hostfix.sample.MainActivity"))
        val main = MainActivity()
        ads.setMainActivity(main)   // host có sẵn (AppPluginBase.onStart), nhưng chưa resume: Full2 xếp hàng

        ads.showFull2("home", object : AdsShowCb {})
        assertNotNull("xếp chờ host resume", adsField("pendingFull2").get(ads))

        app.onActivityResumed(main)
        timers.runPosted()

        assertEquals("Full2 hiện đúng một lần", listOf("full_2_home"), net.shows)
        assertNull(adsField("pendingFull2").get(ads))
    }

    @Test fun `T3 Activity cua SDK quang cao resume truoc khong bao gio la host`() {
        val app = App("com.google.android.gms.ads", pm("com.google.android.gms.ads.HostFixFakeAdActivity"))
        val ad = HostFixFakeAdActivity()

        app.onActivityResumed(ad)

        assertFalse("tiền tố SDK quảng cáo bị loại kể cả khi khớp applicationId/launcher", isHost(ad))
        val main = MainActivity()
        App("com.hostfix.sample.debug", pm("com.hostfix.sample.MainActivity")).onActivityResumed(main)
        assertTrue(isHost(main))
    }

    @Test fun `T4 app khong co hau to - nhu cu, tien to applicationId khop la host, Activity sau doi host`() {
        val app = App("com.hostfix.sample", pm(null))
        val main = MainActivity()
        val other = OtherActivity()

        app.onActivityResumed(main)
        assertTrue(isHost(main))
        app.onActivityResumed(other)
        assertTrue("host tự nhận đổi theo Activity resume (hành vi cũ)", isHost(other))
    }

    @Test fun `T5 host app tu dang ky (setMainActivity) bi thay boi Activity cua app resume ke (nhu 2_4_0)`() {
        val app = App("com.hostfix.sample.debug", pm("com.hostfix.sample.MainActivity"))
        val manual = mock(Activity::class.java).also { `when`(it.localClassName).thenReturn("ManualHostActivity") }
        manager.setMainActivity(manual)
        val other = OtherActivity()

        app.onActivityResumed(other)

        assertTrue("host = Activity của app resume gần nhất", isHost(other))
        assertFalse(isHost(manual))
    }

    @Test fun `T6 goi chi trung tien to chu voi goi app (samplex vs sample) khong la host`() {
        val app = App("com.hostfix.sample.debug", pm(null))
        val look = LookalikeActivity()

        app.onActivityResumed(look)

        assertFalse("luật gói app cần dấu chấm: com.hostfix.samplex không thuộc com.hostfix.sample", isHost(look))
    }

    @Test fun `T7 Activity mediation (com_mbridge) bi loai ca khi nam trong goi lop Application`() {
        val app = MediationApp("com.example.store.debug", pm(null))
        val med = MediationActivity()

        app.onActivityResumed(med)

        assertFalse("tiền tố mediation loại trước luật gói app", isHost(med))
    }

    private fun context(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    /** Full2 một bước luôn có quảng cáo; ghi show. */
    private class Full2Net(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_hostfix_full2"
        private val pl = mock(AdsPlBase::class.java).also { `when`(it.hasLoadedAd()).thenReturn(true) }
        val shows = mutableListOf<String>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_hostfix_full2_data"
        override fun getKeyForResDf(): String = "test_hostfix_full2_res"
        override fun getAdsForPl(placement: String): AdsPlBase = pl
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) {}
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows += placement
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
    }

    private companion object {
        val FIELDS = listOf("listAdsAll", "listAdsFull", "mActivity", "pendingFull2", "currConfig", "placementsConfigManager", "full2MainHandler")
    }
}
