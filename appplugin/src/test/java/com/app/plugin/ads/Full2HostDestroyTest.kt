package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Handler
import android.util.Log
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * Task 7 (2026-09-24): xoay màn hình khi Full2 đang hiện → Activity chủ bị huỷ, dialog bị hệ thống
 * thu hồi (WindowLeaked) mà không có onAdDismissed, nên flagAdsFullShowing kẹt ở 1 và mọi quảng cáo
 * toàn màn hình sau đó bị từ chối "isAdsFullShowing" cho tới khi process chết.
 * Hợp đồng: sau clearActivity(), mạng đang hiện native full phải phát đúng một onAdDismissed, và
 * AdsHelper phải cho phép hiện tiếp.
 *
 * Fix wave 1 (review Task 7a): dismiss đó chạy giữa vòng `for (ads in listAdsAll) ads.clearActivity()`.
 * Nếu AdsHelper load lại Full2 ngay lúc ấy, mạng kế tiếp trong full2StepCicle (chưa tới lượt
 * clearActivity, còn giữ Activity cũ) dựng quảng cáo trên Activity đang huỷ.
 *
 * Fix wave 2: không chỉ callback của Full2 load lại. Interstitial cha có thể là native full
 * (flagImage 1); dismiss của nó load lại full_default ngay, và mạng đứng sau trong listAdsAll vẫn
 * còn giữ Activity cũ. Bảo đảm phải là cấu trúc: AdsHelper nhả Activity của mọi mạng trước khi mạng
 * nào phát dismiss, và các hàm load mà callback dismiss gọi tới tự bỏ khi không còn host.
 *
 * Fix wave 3: "tự bỏ" chỉ trong cửa sổ clearActivity(). Chưa có host vì Activity đang onCreate (SDK
 * đăng ký host ở onActivityResumed) không phải host đang huỷ: load vẫn đi waterfall như trước.
 *
 * Task F (#6, DOC/66 mục 4): quảng cáo cha bị đóng vì host huỷ (dismiss phát ở lượt 2 của clearActivity) thì
 * không nối Full2: không xếp `pendingFull2` (giữ callback của app, hiện Full2 "bất ngờ" trên màn hình tái tạo),
 * gửi `ad_show_fail_reason` = `host_destroyed`, và app nhận `onTransitionChanged(false)` đúng một lần.
 */
class Full2HostDestroyTest {

    /** [onDismiss] chạy trong onAdDismissed, như app load lại ngay trong callback dismiss. */
    private class RecordingShowCb(private val onDismiss: () -> Unit = {}) : AdsShowCb {
        val dismissed = mutableListOf<String?>()
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
            dismissed += placement
            onDismiss()
        }
    }

    /** Ghi mỗi lần app nhận kết quả load, dạng "<originPl> <error>". */
    private class RecordingLoadCb : AdsLoadCb {
        val results = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { results += "$originPl loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { results += "$originPl $error" }
    }

    /** Native full giả: hiện được ngay; clearActivity() làm đúng điều lớp NtFull thật phải làm. */
    private class DialogNetwork(context: Context, private val loaded: AdsPlBase) :
        AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_net"
        var showing: AdsShowCb? = null
        var lastPlacement: String = ""
        /** Chạy ngay trước khi phát onAdDismissed trong clearActivity(), để chụp trạng thái lúc đó. */
        var beforeDismiss: (() -> Unit)? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ntfull_data"
        override fun getKeyForResDf(): String = "test_ntfull_res"
        // showFull2 chỉ đi tiếp khi có mạng báo đã load (hasLoadedAd), như Full2CallbackParityTest.
        override fun getAdsForPl(placement: String): AdsPlBase = loaded
        /** Mọi placement mạng này được gọi hiện, theo thứ tự (cha `full_*`, Full2 `full_2_*`). */
        val shown = mutableListOf<String>()
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = adsShowCb; lastPlacement = placement
            shown += placement
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        // Cùng thứ tự với lớp NtFull thật: nhả Activity trước, phát dismiss sau.
        override fun clearActivity() {
            super.clearActivity()
            showing?.let { cb ->
                showing = null
                beforeDismiss?.invoke()
                cb.onAdDismissed(lastPlacement, lastPlacement, "test")
            }
        }
    }

    /** Mạng đứng sau trong listAdsAll: chỉ ghi lại mỗi lần bị gọi load và Activity nó giữ lúc đó. */
    private class LoadRecordingNetwork(context: Context, type: Int = AdsIDDef.FBFullNt) : AdsBaseFull(type, context) {
        override val eventTag: String get() = "test_net"
        val loadsWithActivity = mutableListOf<Activity?>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ntfull_next_data"
        override fun getKeyForResDf(): String = "test_ntfull_next_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loadsWithActivity += mActivity }
    }

    /** Rewarded đứng sau trong listAdsAll: ghi lại mỗi lần bị gọi load. */
    private class GiftRecordingNetwork(context: Context) : AdsBaseGift(AdsIDDef.MaxRewarded, context) {
        override val eventTag: String get() = "test_net"
        val loadsWithActivity = mutableListOf<Activity?>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_gift_next_data"
        override fun getKeyForResDf(): String = "test_gift_next_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loadsWithActivity += mActivity }
    }

    /** AdsPlBase thật tối thiểu: cho đọc mActivity ở tầng placement, nơi AdsPlBase.load kiểm host. */
    private class HostProbePl(context: Context) : AdsPlBase(context, mock(AdsBase::class.java), 0, "full_probe") {
        val host: Activity? get() = mActivity
        override val eventTag: String get() = "test_host_probe"
        override fun onInit() = Unit
        override fun tryLoad() = Unit
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
    }

    private lateinit var context: Context
    private lateinit var helper: AdsHelper
    private lateinit var network: DialogNetwork

    private fun <T> field(name: String): T =
        @Suppress("UNCHECKED_CAST")
        (AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.get(helper) as T)

    private fun setField(name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val loaded = mock(AdsPlBase::class.java)
        `when`(loaded.hasLoadedAd()).thenReturn(true)
        network = DialogNetwork(context, loaded)
        helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
            full2StepCicle.add(AdsIDDef.AdmobFullNt)
        }
        helper.placementsConfigManager = AdsPlacementConfigManager()
        setField("listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to network))
        setField("listAdsAll", mutableListOf<AdsBase>(network))
        // Host đang resumed: Full2 hiện ngay thay vì xếp hàng chờ host resume.
        field<FullScreenAdPolicy>("fullScreenAdPolicy").onHostActivityResumed()
    }

    @Test
    fun `Activity chu bi huy khi Full2 dang hien thi co van duoc ha va app nhan dung mot onAdDismissed`() {
        val cb = RecordingShowCb()
        helper.showFull2("default", cb)
        assertTrue("Full2 phải đang hiện", helper.isFullShowing())

        helper.clearActivity()

        assertFalse("flagAdsFullShowing phải về 0 sau khi host bị huỷ", helper.isFullShowing())
        assertEquals(1, cb.dismissed.size)
    }

    @Test
    fun `vong Full2 hai mang - dismiss luc host chet khong load mang ke tiep tren Activity dang huy`() {
        val next = LoadRecordingNetwork(context)
        helper.currConfig.full2StepCicle.add(AdsIDDef.FBFullNt)
        field<MutableMap<Int, AdsBaseFull>>("listAdsFull")[AdsIDDef.FBFullNt] = next
        // Sau mạng đang hiện, như thứ tự listAdsAll thật: tới lúc mạng đang hiện dismiss, mạng này chưa
        // được clearActivity nên còn trỏ vào Activity cũ.
        field<MutableList<AdsBase>>("listAdsAll").add(next)
        helper.setMainActivity(mock(Activity::class.java))
        val cb = RecordingShowCb()
        helper.showFull2("default", cb)
        assertTrue("Full2 phải đang hiện", helper.isFullShowing())

        helper.clearActivity()

        assertEquals("mạng kế tiếp không được load khi host đang huỷ", emptyList<Activity?>(), next.loadsWithActivity)
        assertFalse("flagAdsFullShowing phải về 0 sau khi host bị huỷ", helper.isFullShowing())
        assertEquals(1, cb.dismissed.size)
    }
    @Test
    fun `interstitial cha la native full - dismiss luc host chet khong load mang sau trong listAdsAll`() {
        // Mạng sau trong listAdsAll (MAX interstitial) đứng đầu fullStepCicle: reload full_default
        // trong callback dismiss của interstitial cha đi thẳng vào nó.
        val next = LoadRecordingNetwork(context, AdsIDDef.MaxFullInters)
        helper.currConfig.fullStepCicle.add(AdsIDDef.MaxFullInters)
        field<MutableMap<Int, AdsBaseFull>>("listAdsFull")[AdsIDDef.MaxFullInters] = next
        field<MutableList<AdsBase>>("listAdsAll").add(next)
        helper.setMainActivity(mock(Activity::class.java))
        val cb = RecordingShowCb()
        // flagImage 1: native full AdMob đứng đầu waterfall interstitial.
        assertTrue(helper.showInterstitial("home", 1, cb, isDeltaTime = false))
        assertTrue("interstitial cha phải đang hiện", helper.isFullShowing())
        next.loadsWithActivity.clear()

        helper.clearActivity()

        assertEquals("mạng sau không được load khi host đang huỷ", emptyList<Activity?>(), next.loadsWithActivity)
        assertFalse("flagAdsFullShowing phải về 0 sau khi host bị huỷ", helper.isFullShowing())
        assertEquals(1, cb.dismissed.size)
    }

    @Test
    fun `host chet - moi mang trong listAdsAll da nha Activity truoc khi mang dau phat dismiss`() {
        // Mạng cuối trong listAdsAll giữ một placement thật: load của placement đọc mActivity ở đây.
        val probe = HostProbePl(context)
        val last = LoadRecordingNetwork(context, AdsIDDef.FBFullInters)
        last.mapAdsPl["full_probe"] = probe
        val all = field<MutableList<AdsBase>>("listAdsAll").apply { add(last) }.toList()
        helper.setMainActivity(mock(Activity::class.java))
        assertNotNull("placement của mạng cuối phải giữ host trước khi host chết", probe.host)
        val atDismiss = mutableListOf<List<Activity?>>()
        network.beforeDismiss = { atDismiss += all.map { it.mActivity } + probe.host }
        helper.showFull2("default", RecordingShowCb())
        assertTrue("Full2 phải đang hiện", helper.isFullShowing())

        helper.clearActivity()

        assertEquals("mạng đầu phải phát dismiss đúng một lần", 1, atDismiss.size)
        assertEquals(
            "lúc mạng đầu phát dismiss, mọi mạng và placement của mạng cuối đã nhả Activity",
            listOf<Activity?>(null, null, null), atDismiss.single(),
        )
        assertEquals("sau clearActivity mọi mạng đều không giữ host", listOf<Activity?>(null, null), all.map { it.mActivity })
        assertNull("placement của mạng cuối không giữ host", probe.host)
    }

    /** MAX interstitial và MAX rewarded đứng đầu waterfall của mình, sau mạng đang hiện trong listAdsAll. */
    private fun addIntersAndGift(): Pair<LoadRecordingNetwork, GiftRecordingNetwork> {
        val inters = LoadRecordingNetwork(context, AdsIDDef.MaxFullInters)
        val gift = GiftRecordingNetwork(context)
        helper.currConfig.fullStepCicle.add(AdsIDDef.MaxFullInters)
        helper.currConfig.giftStepCicle.add(AdsIDDef.MaxRewarded)
        field<MutableMap<Int, AdsBaseFull>>("listAdsFull")[AdsIDDef.MaxFullInters] = inters
        setField("listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.MaxRewarded to gift))
        field<MutableList<AdsBase>>("listAdsAll").addAll(listOf(inters, gift))
        return inters to gift
    }

    @Test
    fun `host dang huy - app load trong callback dismiss nhan that bai, khong cham mang nao`() {
        val (inters, gift) = addIntersAndGift()
        helper.setMainActivity(mock(Activity::class.java))
        val loadCb = RecordingLoadCb()
        // App load lại ngay trong onAdDismissed, callback mà mạng native full phát giữa clearActivity().
        val cb = RecordingShowCb {
            helper.loadInterstitialForThisTurn("home", 0, loadCb)
            helper.loadRewardedForThisTurn("home", loadCb)
        }
        helper.showFull2("default", cb)
        assertTrue("Full2 phải đang hiện", helper.isFullShowing())

        helper.clearActivity()

        assertEquals("mạng native full phải phát dismiss đúng một lần", 1, cb.dismissed.size)
        assertEquals(
            "app phải nhận onLoadFailed cho từng lần load phát trong lúc host đang huỷ",
            listOf("full_home host destroyed, load skipped", "gift_home host destroyed, load skipped"),
            loadCb.results,
        )
        assertEquals("interstitial không được load khi host đang huỷ", emptyList<Activity?>(), inters.loadsWithActivity)
        assertEquals("rewarded không được load khi host đang huỷ", emptyList<Activity?>(), gift.loadsWithActivity)

        // clearActivity đã trả về: cửa sổ đóng, load lại đi waterfall như trước setMainActivity đầu tiên.
        helper.loadInterstitialForThisTurn("home", 0, loadCb)
        assertEquals("sau clearActivity load không còn bị bỏ ở guard", listOf<Activity?>(null), inters.loadsWithActivity)
    }

    @Test
    fun `chua co host (load trong onCreate truoc onResume) - load chay waterfall nhu cu, khong bao host destroyed`() {
        val (inters, gift) = addIntersAndGift()
        val loadCb = RecordingLoadCb()
        // Không setMainActivity: SDK chỉ đăng ký host ở onActivityResumed. Hành vi cũ: mạng đầu được gọi
        // load (fail "isActivitynull" ở tầng mạng), các bước waterfall sau được post và chạy sau khi có
        // host. Handler là stub trong unit test JVM nên bước post không chạy ở đây.

        helper.loadInterstitialForThisTurn("home", 0, loadCb)
        helper.loadRewardedForThisTurn("home", loadCb)

        assertEquals("chưa có host không phải host đang huỷ: không báo lỗi đồng bộ", emptyList<String>(), loadCb.results)
        assertEquals("waterfall interstitial phải tới mạng đầu", listOf<Activity?>(null), inters.loadsWithActivity)
        assertEquals("waterfall rewarded phải tới mạng đầu", listOf<Activity?>(null), gift.loadsWithActivity)
    }

    // ═══ Task F (#6): cha đóng vì host huỷ thì không nối Full2 ═══

    /** Interstitial GMA giả: `AdActivity` sống qua xoay, không dismiss trong clearActivity; test tự đóng nó. */
    private class GmaIntersNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "test_net"
        var showing: Pair<String, AdsShowCb>? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_gma_inters_data"
        override fun getKeyForResDf(): String = "test_gma_inters_res"
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            showing = placement to adsShowCb
            adsShowCb.onAdShowed(placement, placement, "test", flagShow)
            return true
        }
        fun dismiss() {
            val (placement, cb) = showing ?: return
            showing = null
            cb.onAdDismissed(placement, placement, "test")
        }
    }

    /** Ghi mọi lần app nhận trạng thái chuyển cảnh (bỏ lần báo lúc đăng ký). */
    private class RecordingTransition : Full2TransitionListener {
        val events = mutableListOf<Boolean>()
        override fun onTransitionChanged(active: Boolean) { events += active }
    }

    /** `full2MainHandler` chạy `post` ngay (main looper của test là stub, không bao giờ chạy); `postDelayed` bỏ. */
    private fun runFull2PostsInline() {
        val handler = mock(Handler::class.java)
        `when`(handler.post(any())).thenAnswer { (it.getArgument<Runnable>(0)).run(); true }
        setField("full2MainHandler", handler)
    }

    private lateinit var transition: RecordingTransition

    /** Host đang resume, app nghe chuyển cảnh, interstitial cha (native full, flagImage 1) đang hiện với chuỗi Full2. */
    private fun parentNtFullShowing(activity: Activity, cb: RecordingShowCb) {
        runFull2PostsInline()
        transition = RecordingTransition()
        helper.setFull2TransitionListener(transition)
        transition.events.clear()
        helper.setMainActivity(activity)
        assertTrue(helper.showInterstitial("home", 1, cb, isDeltaTime = false))
        assertEquals("cha đang hiện, chuỗi Full2 đã bật", listOf(true), transition.events)
    }

    @After
    fun tearDown() {
        LogUtil.setFlagLog(1)
    }

    @Test
    fun `cha native full dong vi host huy - dismiss mot lan, khong xep pendingFull2, host_destroyed, chuyen canh bao false mot lan`() {
        val cb = RecordingShowCb()
        parentNtFullShowing(mock(Activity::class.java), cb)
        LogUtil.setFlagLog(3)

        val fails = mockStatic(Log::class.java).use { log ->
            val fails = ServerEventCapture().use { server ->
                helper.clearActivity()
                server.showFails(1)
            }
            log.verify(
                { Log.d(eq(LogUtil.TAG), contains("full_2_home showFull2 skipped: parent closed by host destroy")) },
                times(1)
            )
            fails
        }

        assertEquals("app nhận đúng một onAdDismissed của cha", listOf<String?>("full_home"), cb.dismissed)
        assertNull("không xếp Full2 chờ host mới", field<Any?>("pendingFull2"))
        assertEquals(listOf("full_2_home" to Constants.AdShowFailReason.HOST_DESTROYED), fails)
        assertEquals("chuyển cảnh: bật lúc cha hiện, tắt đúng một lần", listOf(true, false), transition.events)
        assertFalse("chuỗi Full2 đã kết thúc", field<Boolean>("full2TransitionActive"))
        assertEquals("không Full2 nào được hiện", listOf("full_home"), network.shown)
    }

    @Test
    fun `sau host huy, host moi resume khong hien Full2`() {
        parentNtFullShowing(mock(Activity::class.java), RecordingShowCb())
        helper.clearActivity()

        helper.onMainActivityResumed(mock(Activity::class.java))

        assertEquals("host mới không nhận Full2 nào", listOf("full_home"), network.shown)
        assertEquals("không báo chuyển cảnh thêm", listOf(true, false), transition.events)
    }

    @Test
    fun `luong binh thuong - cha dong khi host tam dung van xep Full2 va hien khi host resume`() {
        val activity = mock(Activity::class.java)
        val cb = RecordingShowCb()
        parentNtFullShowing(activity, cb)
        helper.onMainActivityPaused(activity)

        network.showing?.onAdDismissed("full_home", "full_home", "test")

        assertNotNull("ngoài cửa sổ clearActivity, Full2 vẫn xếp chờ host resume", field<Any?>("pendingFull2"))
        helper.onMainActivityResumed(activity)
        assertEquals("Full2 hiện khi host resume", listOf("full_home", "full_2_home"), network.shown)
        assertNull(field<Any?>("pendingFull2"))
        assertEquals(listOf<String?>("full_home"), cb.dismissed)
    }

    @Test
    fun `cha la interstitial GMA - dismiss den sau khi clearActivity dong cua so thi van noi Full2 nhu cu`() {
        val gma = GmaIntersNetwork(context)
        helper.currConfig.fullStepCicle.add(AdsIDDef.AdmobFullInters)
        field<MutableMap<Int, AdsBaseFull>>("listAdsFull")[AdsIDDef.AdmobFullInters] = gma
        field<MutableList<AdsBase>>("listAdsAll").add(gma)
        runFull2PostsInline()
        helper.setMainActivity(mock(Activity::class.java))
        val cb = RecordingShowCb()
        assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))

        helper.clearActivity()
        assertEquals("AdActivity của GMA sống qua xoay: chưa dismiss", emptyList<String?>(), cb.dismissed)
        gma.dismiss()

        assertEquals(listOf<String?>("full_home"), cb.dismissed)
        assertNotNull("dismiss sau cửa sổ: Full2 xếp chờ host mới như cũ", field<Any?>("pendingFull2"))
        helper.onMainActivityResumed(mock(Activity::class.java))
        assertEquals("Full2 hiện trên host mới", listOf("full_2_home"), network.shown)
    }

    // ═══ Task I5 (G9, review EF Q2): không giữ cbShow của app (Activity cũ) sau khi host bị huỷ ═══

    /** Mọi AdsPlacementConfig đã tạo (map riêng của AdsPlacementConfigManager, đọc qua reflection). */
    private fun allConfigs(): Map<String, AdsPlacementConfig> {
        @Suppress("UNCHECKED_CAST")
        return AdsPlacementConfigManager::class.java.getDeclaredField("plConfigs").apply { isAccessible = true }
            .get(helper.placementsConfigManager) as Map<String, AdsPlacementConfig>
    }

    @Test
    fun `sau clearActivity khong config nao khong dang hien con giu cbShow`() {
        val cb = RecordingShowCb()
        parentNtFullShowing(mock(Activity::class.java), cb)
        helper.getAdsPlacementConfig("bn_home", 1)!!.cbShow = RecordingShowCb()
        helper.getAdsPlacementConfig("gift_home", 1)!!.cbShow = RecordingShowCb()

        helper.clearActivity()

        assertEquals("app vẫn nhận đúng một onAdDismissed của cha", listOf<String?>("full_home"), cb.dismissed)
        val retained = allConfigs().filterValues { !it.isShowing && it.cbShow != null }.keys
        assertEquals("config không đang hiện không còn giữ cbShow", emptySet<String>(), retained)
    }

    @Test
    fun `interstitial GMA dang hien qua xoay - giu cbShow, dismiss van toi app dung mot lan`() {
        val gma = GmaIntersNetwork(context)
        helper.currConfig.fullStepCicle.add(AdsIDDef.AdmobFullInters)
        field<MutableMap<Int, AdsBaseFull>>("listAdsFull")[AdsIDDef.AdmobFullInters] = gma
        field<MutableList<AdsBase>>("listAdsAll").add(gma)
        runFull2PostsInline()
        helper.setMainActivity(mock(Activity::class.java))
        val cb = RecordingShowCb()
        assertTrue(helper.showInterstitial("home", 0, cb, isDeltaTime = false))
        helper.getAdsPlacementConfig("bn_home", 1)!!.cbShow = RecordingShowCb()

        helper.clearActivity()

        val full = helper.getAdsPlacementConfig("full_home", 1)!!
        assertTrue(full.isShowing)
        assertNotNull("quảng cáo đang hiện giữ callback tới khi đóng", full.cbShow)
        assertNull(helper.getAdsPlacementConfig("bn_home", 1)!!.cbShow)
        gma.dismiss()
        assertEquals(listOf<String?>("full_home"), cb.dismissed)
    }
}
