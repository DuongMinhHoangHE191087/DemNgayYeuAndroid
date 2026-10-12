package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlInters
import com.app.plugin.ads.adsfb.AdsFbPlNtFull
import com.app.plugin.ads.adsfb.AdsFbPlrewarded
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlInters
import com.app.plugin.ads.adsmax.AdsMaxPlNtFull
import com.app.plugin.ads.adsmax.AdsMaxPlrewarded
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlInters
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.app.plugin.ads.adsmob.AdsMobPlrewarded
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * D5 (DOC/66): cùng cơ chế tuổi tối đa của [AdsPlBase] cho 7 placement toàn màn hình (AdMob, Meta × interstitial,
 * rewarded, native full; MAX native full): quá 60 phút ([AdsPolicy.fullscreenMaxAgeMillis]) là chưa load ở
 * `hasLoadedAd`, `load` (tải mới) và `show` (không hiện, gửi `ad_expired`); native full thêm `getAdForShow`. Mỗi lớp
 * được kiểm riêng và lỗi liệt kê theo tên lớp, để một lớp quên đọc tuổi ở `show` không trốn sau lớp khác.
 *
 * MAX interstitial và rewarded miễn (review E I1): MAX tự hết hạn và nạp lại trong cùng đối tượng
 * (`MaxFullscreenAdImpl.onAdExpired`), nên mốc load của SDK không bao giờ đặt lại và hạn 60 phút chỉ từ chối nhầm.
 */
class AdsFullscreenExpiryTest {

    private lateinit var context: Context
    private val clock = LoadedAdFixture.Clock()
    private val activity = mock(Activity::class.java)
    private val sixtyMin = 60 * 60_000L

    private class NoShowCb : AdsShowCb

    private class RecordingLoadCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded:$adId"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "failed:$error"
        }
    }

    /** Hàng chờ của ba mạng: load chờ SDK trong test bị bỏ trước và sau mỗi test, không chạy nhầm ở test khác. */
    private fun resetQueues() {
        (AdsMobMy::class.java.getDeclaredField("readyQueue").apply { isAccessible = true }.get(AdsMobMy.instance)
            as SdkReadyQueue).resetForTest()
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        resetQueues()
    }

    @After
    fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        resetQueues()
    }

    private fun base() = mock(AdsBase::class.java)

    /** 7 placement toàn màn hình có hạn tuổi, dựng như `getNewAdsPl` của lớp mạng (MAX interstitial/rewarded miễn). */
    private val placements: List<Pair<String, (Context) -> AdsPlBase>> = listOf(
        "AdsMobPlInters" to { c -> AdsMobPlInters(c, base(), AdsIDDef.AdmobFullInters, "full_default") },
        "AdsMobPlrewarded" to { c -> AdsMobPlrewarded(c, base(), AdsIDDef.AdmobRewarded, "gift_default") },
        "AdsMobPlNtFull" to { c -> AdsMobPlNtFull(c, base(), AdsIDDef.AdmobFullNt, "full_default") },
        "AdsMaxPlNtFull" to { c -> AdsMaxPlNtFull(c, base(), AdsIDDef.MaxFullNt, "full_default") },
        "AdsFbPlInters" to { c -> AdsFbPlInters(c, base(), AdsIDDef.FBFullInters, "full_default") },
        "AdsFbPlrewarded" to { c -> AdsFbPlrewarded(c, base(), AdsIDDef.FBRewarded, "gift_default") },
        "AdsFbPlNtFull" to { c -> AdsFbPlNtFull(c, base(), AdsIDDef.FBFullNt, "full_default") },
    )

    /** Placement có Activity và một quảng cáo (đối tượng giả: quá tuổi thì bị bỏ trước khi ai đọc kiểu của nó). */
    private fun withAd(make: (Context) -> AdsPlBase): Pair<AdsPlBase, AdsEcmp> {
        val pl = make(context)
        pl.setActivity(activity)
        return pl to LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
    }

    /** Chạy [check] cho từng placement; trả danh sách "lớp: lỗi" (rỗng là đạt). */
    private fun eachPlacement(check: (AdsPlBase, AdsEcmp) -> String?): List<String> =
        placements.mapNotNull { (name, make) ->
            val (pl, item) = withAd(make)
            check(pl, item)?.let { "$name: $it" }
        }

    private fun reasonsOf(count: Int = 1, action: () -> Unit): List<String> = ServerEventCapture().use { server ->
        action()
        server.await("ad_show_fail", count).map { it.get("reason").asString }
    }

    /**
     * Review E M1: danh sách lý do chính xác của một lượt show hết hạn. AdMob interstitial và native full gửi thêm
     * lý do "không có quảng cáo" vốn có của nhánh not-loaded; các lớp khác chỉ `ad_expired`.
     */
    private val expiredShowReasons: Map<String, List<String>> = mapOf(
        "AdsMobPlInters" to listOf(Constants.AdShowFailReason.AD_EXPIRED, Constants.AdShowFailReason.SHOW_NOT_LOADED),
        "AdsMobPlNtFull" to listOf(
            Constants.AdShowFailReason.AD_EXPIRED, Constants.AdShowFailReason.SHOW_NOT_LOADED_ON_SHOW
        ),
    ).withDefault { listOf(Constants.AdShowFailReason.AD_EXPIRED) }

    @Test
    fun `hasLoadedAd - 59 phut true, du 60 phut false voi moi placement toan man hinh`() {
        val wrong = eachPlacement { pl, item ->
            clock.nowMs = item.loadedAtMillis + sixtyMin - 1
            if (!pl.hasLoadedAd()) return@eachPlacement "59 phút phải còn hạn"
            clock.nowMs = item.loadedAtMillis + sixtyMin
            if (pl.hasLoadedAd()) "60 phút phải hết hạn" else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `show - qua 60 phut khong hien, gui ad_expired, bo quang cao voi moi placement toan man hinh`() {
        val wrong = eachPlacement { pl, item ->
            clock.nowMs = item.loadedAtMillis + sixtyMin + 1
            var shown = true
            val expected = expiredShowReasons.getValue(pl.javaClass.simpleName)
            val reasons = reasonsOf(expected.size) { shown = pl.show("full_home", NoShowCb(), 0) }
            when {
                shown -> "show trả true"
                reasons.sorted() != expected.sorted() -> "lý do $reasons, cần đúng $expected (show không đọc tuổi?)"
                item.isLoaded || item.adObject != null -> "quảng cáo hết hạn không bị bỏ"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `load - qua 60 phut tai moi, 59 phut bao da load, voi moi placement toan man hinh`() {
        val wrong = eachPlacement { pl, item ->
            clock.nowMs = item.loadedAtMillis + sixtyMin - 60_000L
            val fresh = RecordingLoadCb()
            pl.load("full_home", fresh)
            if (fresh.events != listOf("loaded:${item.adsId}")) return@eachPlacement "59 phút: ${fresh.events}"

            clock.nowMs = item.loadedAtMillis + sixtyMin + 1
            val stale = RecordingLoadCb()
            pl.load("full_home", stale)
            when {
                stale.events.isNotEmpty() -> "quá 60 phút vẫn báo: ${stale.events}"
                !LoadedAdFixture.state(pl).isLoading -> "quá 60 phút không vào tryLoad"
                item.isLoaded || item.adObject != null -> "quảng cáo hết hạn không bị bỏ"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** Native full còn một cửa lấy quảng cáo ra khỏi placement: `getAdForShow` (app tự gắn view). */
    @Test
    fun `native full getAdForShow - qua 60 phut tra null va gui ad_expired`() {
        val wrong = eachPlacement { pl, item ->
            if (!pl.javaClass.simpleName.endsWith("NtFull")) return@eachPlacement null
            clock.nowMs = item.loadedAtMillis + sixtyMin + 1
            var got: Any? = Unit
            val reasons = reasonsOf { got = pl.getAdForShow("full_home", NoShowCb()) }
            when {
                got != null -> "getAdForShow trả quảng cáo hết hạn"
                Constants.AdShowFailReason.AD_EXPIRED !in reasons -> "không gửi ad_expired: $reasons"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** Ngưỡng đọc từ `fullscreen_max_age_minutes` mỗi lần xét tuổi (Remote Config về sau khi load vẫn áp). */
    @Test
    fun `tuoi toi da doc tu fullscreen_max_age_minutes voi moi placement toan man hinh`() {
        val wrong = eachPlacement { pl, item ->
            AdsPolicy.current = AdsPolicy.parse("""{"fullscreen_max_age_minutes":15}""")
            clock.nowMs = item.loadedAtMillis + 15 * 60_000L - 1
            if (!pl.hasLoadedAd()) return@eachPlacement "15 phút trừ 1 ms phải còn hạn"
            clock.nowMs = item.loadedAtMillis + 15 * 60_000L
            if (pl.hasLoadedAd()) "15 phút phải hết hạn" else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** MAX interstitial còn hạn vẫn hiện: `MaxInterstitialAd` dùng lại cho mọi lượt load, hết hạn không `destroy()` nó. */
    @Test
    fun `MAX interstitial 59 phut van hien`() {
        val maxAd = mock(MaxInterstitialAd::class.java).also { `when`(it.isReady).thenReturn(true) }
        val pl = AdsMaxPlInters(context, base(), AdsIDDef.MaxFullInters, "full_default")
        pl.setActivity(activity)
        pl.interstitialAd = maxAd
        val item = LoadedAdFixture.giveLoadedAd(pl, maxAd, clock)
        clock.nowMs = item.loadedAtMillis + 59 * 60_000L

        assertTrue(pl.show("full_home", NoShowCb(), 0))
        verify(maxAd).showAd(activity)
    }

    /**
     * Review E I1: MAX interstitial và rewarded không áp hạn tuổi của SDK. MAX tự hết hạn và nạp lại trong cùng đối
     * tượng `MaxInterstitialAd`/`MaxRewardedAd`, nên `loadedAtMillis` không đặt lại; ở phút 61 quảng cáo MAX đang giữ
     * vẫn tươi và phải hiện, không `ad_expired` nào.
     */
    @Test
    fun `MAX interstitial va rewarded 61 phut van hien, khong gui ad_expired`() {
        val maxInters = mock(MaxInterstitialAd::class.java).also { `when`(it.isReady).thenReturn(true) }
        val inters = AdsMaxPlInters(context, base(), AdsIDDef.MaxFullInters, "full_default")
        inters.setActivity(activity)
        inters.interstitialAd = maxInters
        val maxRewarded = mock(MaxRewardedAd::class.java).also { `when`(it.isReady).thenReturn(true) }
        val rewarded = AdsMaxPlrewarded(context, base(), AdsIDDef.MaxRewarded, "gift_default")
        rewarded.setActivity(activity)
        AdsMaxPlrewarded::class.java.getDeclaredField("rewardedAd").apply { isAccessible = true }.set(rewarded, maxRewarded)
        val intersItem = LoadedAdFixture.giveLoadedAd(inters, maxInters, clock)
        LoadedAdFixture.giveLoadedAd(rewarded, maxRewarded, clock)
        clock.nowMs = intersItem.loadedAtMillis + 61 * 60_000L

        val shown = mutableListOf<Boolean>()
        val expired = ServerEventCapture().use { server ->
            shown += inters.hasLoadedAd()
            shown += rewarded.hasLoadedAd()
            shown += inters.show("full_home", NoShowCb(), 0)
            shown += rewarded.show("gift_home", NoShowCb(), 0)
            server.await("ad_show_fail", 0).map { it.get("reason").asString }
                .filter { it == Constants.AdShowFailReason.AD_EXPIRED }
        }

        assertEquals("hasLoadedAd inters, rewarded; show inters, rewarded", listOf(true, true, true, true), shown)
        assertEquals(emptyList<String>(), expired)
        verify(maxInters).showAd(activity)
        verify(maxRewarded).showAd(activity)
    }

    /**
     * Review E M2 (FE3): Meta interstitial và rewarded quá tuổi bị bỏ thì `destroy()` đúng một lần (cùng đường
     * `destroyAd` mà `AdsEcmps.dropExpired` gọi, như native full), không chỉ bỏ tham chiếu.
     */
    @Test
    fun `Meta interstitial va rewarded qua 60 phut bi destroy dung mot lan`() {
        val fbInters = mock(com.facebook.ads.InterstitialAd::class.java)
        val inters = AdsFbPlInters(context, base(), AdsIDDef.FBFullInters, "full_default")
        inters.setActivity(activity)
        val intersItem = LoadedAdFixture.giveLoadedAd(inters, fbInters, clock)
        val fbRewarded = mock(com.facebook.ads.RewardedVideoAd::class.java)
        val rewarded = AdsFbPlrewarded(context, base(), AdsIDDef.FBRewarded, "gift_default")
        rewarded.setActivity(activity)
        LoadedAdFixture.giveLoadedAd(rewarded, fbRewarded, clock)
        clock.nowMs = intersItem.loadedAtMillis + sixtyMin + 1

        reasonsOf {
            repeat(2) {
                assertFalse(inters.hasLoadedAd())
                assertFalse(rewarded.hasLoadedAd())
            }
        }

        verify(fbInters, times(1)).destroy()
        verify(fbRewarded, times(1)).destroy()
    }

    /** Meta interstitial còn hạn vẫn hiện. */
    @Test
    fun `Meta interstitial 59 phut van hien`() {
        val fbAd = mock(com.facebook.ads.InterstitialAd::class.java)
        val pl = AdsFbPlInters(context, base(), AdsIDDef.FBFullInters, "full_default")
        pl.setActivity(activity)
        val item = LoadedAdFixture.giveLoadedAd(pl, fbAd, clock)
        clock.nowMs = item.loadedAtMillis + 59 * 60_000L

        assertTrue(pl.show("full_home", NoShowCb(), 0))
        verify(fbAd).show()
    }

    /**
     * Cấu trúc: trong 7 lớp có hạn tuổi, mọi chỗ lấy quảng cáo đã load (show, getAdForShow, dựng sẵn dialog Full2)
     * đi qua `loadedAdWithinMaxAge()`; gọi thẳng `adEcmps?.getAdLoaded()` là bỏ qua tuổi tối đa.
     */
    @Test
    fun `7 lop toan man hinh co han tuoi khong goi getAdLoaded bo qua tuoi`() {
        val dir = File("src/main/java/com/app/plugin/ads")
        assertTrue("chạy từ gốc module", dir.isDirectory)
        val direct = Regex("""adEcmps\??\.getAdLoaded\(""")
        val found = placements.map { it.first }.flatMap { name ->
            val f = dir.walkTopDown().first { it.name == "$name.kt" }
            val text = f.readText()
            check(text.contains("override val loadedAdMaxAgeMs")) { "$name không khai loadedAdMaxAgeMs" }
            direct.findAll(text).map { m -> "$name:${text.substring(0, m.range.first).count { it == '\n' } + 1}" }.toList()
        }
        assertEquals(emptyList<String>(), found)
    }
}
