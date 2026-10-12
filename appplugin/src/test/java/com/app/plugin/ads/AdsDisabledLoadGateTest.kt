package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.ViewGroup
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Đợt sửa cuối Phase B, mục 5 (review toàn nhánh, Important 1): bốn hàm load có `AdsLoadCb` và `showBanner`
 * từng `return` im lặng ở cổng `isRemoveAds(flag) || isDisableAds(flag)`. Từ khi có kill switch, MỌI người
 * dùng gặp cổng này mỗi phiên, và app chờ `onLoadFailed` (spinner của nút rewarded) chờ mãi (Điều 13:
 * callback đúng một lần). Mỗi cổng giờ báo đúng một lần, với nhãn của [AdsHelper] `disabledReason`: kill
 * switch → `show_blocked_kill_switch`, còn remove-ads hay định dạng bị tắt trong cấu hình → nhãn cũ.
 */
class AdsDisabledLoadGateTest {

    private enum class Cause { REMOVE_ADS, CONFIG_DISABLED, KILL }

    /** Một cổng: tên hàm (nhãn cũ là "<tên> isremoveads or disable"), placement thật, và cách gọi. */
    private class Gate(val name: String, val realPl: String, val call: (AdsHelper, String, AdsLoadCb) -> Unit)

    private val gates = listOf(
        Gate("loadRect4Show", "rect_home") { h, pl, cb -> h.loadRect4Show(pl, cb) },
        Gate("loadNtClForThisTurn", "cl_home") { h, pl, cb -> h.loadNtClForThisTurn(pl, cb) },
        Gate("loadNativeFullForThisTurn", "full_home") { h, pl, cb -> h.loadNativeFullForThisTurn(pl, cb) },
        Gate("loadRewardedForThisTurn", "gift_home") { h, pl, cb -> h.loadRewardedForThisTurn(pl, cb) },
        // Task 17: hai cổng đã báo lại từ trước nhưng với nhãn lệch ("remove ads", "isremoveads"), khác lời
        // khuyên của CHANGELOG (bỏ qua khi `err` là "… isremoveads or disable").
        Gate("showRect", "rect_home") { h, pl, cb -> h.showRect(pl, mock(ViewGroup::class.java), cb) },
        Gate("loadOpenAdForThisTurn", "full_home") { h, pl, cb -> h.loadOpenAdForThisTurn(pl, cb) },
    )

    private class RecordingLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) {
            events += "loaded:$placement/$originPl"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "failed:$placement/$originPl:$error"
        }
    }

    private class RecordingShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            events += "fail:$placement/$originPl:$err"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) {
            events += "showed"
        }
    }

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
    }

    @After fun tearDown() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        AdSdkStartup.resetForTest()
    }

    /** `AdsHelper` mới với [cause] đang chặn mọi định dạng. */
    private fun blockedHelper(cause: Cause): AdsHelper = AdsKillSwitchTest.newAdsHelper().also { h ->
        when (cause) {
            Cause.REMOVE_ADS -> AdsHelper::class.java.getDeclaredField("pendingRemoveAdsMask")
                .apply { isAccessible = true }.set(h, RemoveAdsMask.ALL)
            Cause.CONFIG_DISABLED -> h.currConfig.maskAdsStatus = 0   // mọi định dạng tắt trong cấu hình
            Cause.KILL -> SdkKillSwitch.current = SdkKillSwitch(ads = true)
        }
    }

    private fun expectedReason(cause: Cause, oldText: String) =
        if (cause == Cause.KILL) Constants.AdShowFailReason.SHOW_BLOCKED_KILL_SWITCH else oldText

    /** Mọi cổng đều được kiểm (không dừng ở cổng sai đầu tiên): lỗi liệt kê theo từng hàm. */
    private fun assertEachLoadGateFailsOnce(cause: Cause) {
        val wrong = gates.mapNotNull { gate ->
            SdkKillSwitch.current = SdkKillSwitch.NONE
            val helper = blockedHelper(cause)
            val cb = RecordingLoadCb()

            gate.call(helper, "home", cb)

            val reason = expectedReason(cause, "${gate.name} isremoveads or disable")
            val expected = listOf("failed:home/${gate.realPl}:$reason")
            if (cb.events == expected) null else "${gate.name}: ${cb.events} (can $expected)"
        }
        assertEquals("cac cong bi chan ($cause) phai bao dung mot lan", emptyList<String>(), wrong)
    }

    @Test fun `remove-ads thi moi ham load bao onLoadFailed dung mot lan voi nhan cu`() =
        assertEachLoadGateFailsOnce(Cause.REMOVE_ADS)

    @Test fun `dinh dang tat trong cau hinh thi moi ham load bao onLoadFailed dung mot lan voi nhan cu`() =
        assertEachLoadGateFailsOnce(Cause.CONFIG_DISABLED)

    @Test fun `kill switch thi moi ham load bao onLoadFailed dung mot lan voi nhan kill switch`() =
        assertEachLoadGateFailsOnce(Cause.KILL)

    @Test fun `showBanner bi chan thi bao onAdShowFail dung mot lan, khong bao load`() {
        for (cause in Cause.values()) {
            SdkKillSwitch.current = SdkKillSwitch.NONE
            val helper = blockedHelper(cause)
            val loadCb = RecordingLoadCb()
            val showCb = RecordingShowCb()

            helper.showBanner("home", mock(ViewGroup::class.java), isCollapse = true, cbLoad = loadCb, cbShow = showCb)

            val reason = expectedReason(cause, "showBanner isremoveads or disable")
            assertEquals("showBanner ($cause)", listOf("fail:home/bn_home:$reason"), showCb.events)
            assertEquals("showBanner ($cause) khong bao load", emptyList<String>(), loadCb.events)
        }
    }

    /**
     * #15: gói rẻ ([RemoveAdsMask.INTERRUPTIVE]) chặn native collapsible ở cổng thật của `loadNtClForThisTurn`
     * (`isRemoveAds(1)`), còn rewarded (cổng `isRemoveAds(5)`) vẫn load được.
     */
    @Test fun `goi re INTERRUPTIVE chan native collapsible, rewarded khong bi cong remove-ads chan`() {
        val helper = AdsKillSwitchTest.newAdsHelper().also { h ->
            AdsHelper::class.java.getDeclaredField("pendingRemoveAdsMask")
                .apply { isAccessible = true }.set(h, RemoveAdsMask.INTERRUPTIVE)
        }
        val ntCl = RecordingLoadCb()
        helper.loadNtClForThisTurn("home", ntCl)
        assertEquals(listOf("failed:home/cl_home:loadNtClForThisTurn isremoveads or disable"), ntCl.events)

        val rewarded = RecordingLoadCb()
        helper.loadRewardedForThisTurn("home", rewarded)
        assertEquals(
            "rewarded không bị cổng remove-ads chặn",
            emptyList<String>(),
            rewarded.events.filter { it.endsWith("isremoveads or disable") }
        )
    }

    /** NtCl giả đã load sẵn (trước khi người dùng mua gói): đếm lần được gọi show. */
    private class LoadedNtCl(context: Context) : AdsBaseBanner(AdsIDDef.AdmobNtCl, context) {
        override val eventTag: String get() = "test_net"
        var shows = 0
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ntcl_gate_data"
        override fun getKeyForResDf(): String = "test_ntcl_gate_res"
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows++
            return true
        }
    }

    /**
     * Review E M3: `showNtCl` có cổng remove-ads như `loadNtClForThisTurn`. NtCl load trước khi gói rẻ
     * ([RemoveAdsMask.INTERRUPTIVE]) được cấp trong cùng phiên không được hiện; gửi đúng một `remove_ad`.
     */
    @Test fun `goi re INTERRUPTIVE chan showNtCl voi NtCl da load san, gui remove_ad`() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val network = LoadedNtCl(context)
        val helper = AdsKillSwitchTest.newAdsHelper().also { h ->
            AdsHelper::class.java.getDeclaredField("pendingRemoveAdsMask")
                .apply { isAccessible = true }.set(h, RemoveAdsMask.INTERRUPTIVE)
            AdsHelper::class.java.getDeclaredField("listAdsNtCl")
                .apply { isAccessible = true }.set(h, mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobNtCl to network))
            h.currConfig.clStepList.add(AdsIDDef.AdmobNtCl)
        }

        val fails = ServerEventCapture().use { server ->
            helper.showNtCl("home", RecordingShowCb())
            server.showFails(1)
        }

        assertEquals("NtCl không được hiện với gói rẻ", 0, network.shows)
        assertEquals(listOf("cl_home" to Constants.AdShowFailReason.REMOVE_AD), fails)
    }

    @Test fun `showBanner bi chan ma khong co cb thi khong nem`() {
        val helper = blockedHelper(Cause.KILL)
        helper.showBanner("home", mock(ViewGroup::class.java), isCollapse = false, cbLoad = null, cbShow = null)
    }
}
