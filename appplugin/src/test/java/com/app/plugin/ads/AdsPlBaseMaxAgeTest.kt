package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.ads.LoadedAdFixture.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Task 11: quảng cáo quá tuổi tối đa của placement ([AdsPlBase.loadedAdMaxAgeMs], app open:
 * [APP_OPEN_MAX_AGE_MS]) được coi là chưa load ở mọi cửa của [AdsPlBase]: [AdsPlBase.load] tải mới thay
 * vì báo "has loaded", [AdsPlBase.hasLoadedAd] là `false`, [AdsPlBase.hasFreshAdForReuse] là `false`.
 * Placement không khai tuổi tối đa (mọi định dạng khác) giữ hành vi cũ, gồm cửa sổ tái dùng 1 giờ.
 */
class AdsPlBaseMaxAgeTest {

    /** Placement tối thiểu: [tryLoad] chỉ đếm (không có SDK mạng trong JVM test). */
    private class Pl(private val maxAgeMs: Long?) :
        AdsPlBase(mock(Context::class.java), mock(AdsBase::class.java), 0, "full_test") {
        var tryLoads = 0
        override val eventTag: String get() = "test_pl"
        override val loadedAdMaxAgeMs: Long? get() = maxAgeMs
        override fun onInit() = Unit
        override fun tryLoad() {
            tryLoads++
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int): Boolean = false
        fun setShowing(showing: Boolean) {
            adsPlacementEcmp.isShowing = showing
        }
    }

    private class RecordingLoadCb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) {
            events += "loaded:$adId"
        }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "failed:$error"
        }
    }

    private val clock = Clock()

    private fun appOpenLike() = Pl(APP_OPEN_MAX_AGE_MS).apply { setActivity(mock(Activity::class.java)) }

    // ═══ load() ═══

    @Test
    fun `load - app open qua 4 gio thi tai moi, khong bao onLoaded`() {
        val pl = appOpenLike()
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        val cb = RecordingLoadCb()

        pl.load("full_test", cb)

        assertEquals("quảng cáo hết hạn không được báo là đã load", emptyList<String>(), cb.events)
        assertEquals("phải tải mới đúng một lần", 1, pl.tryLoads)
        assertFalse(item.isLoaded)
        assertNull(item.adObject)
    }

    @Test
    fun `load - app open con han thi bao onLoaded mot lan, khong tai lai`() {
        val pl = appOpenLike()
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS - 1
        val cb = RecordingLoadCb()

        pl.load("full_test", cb)

        assertEquals(listOf("loaded:${item.adsId}"), cb.events)
        assertEquals(0, pl.tryLoads)
    }

    // ═══ hasLoadedAd() ═══

    @Test
    fun `hasLoadedAd - app open qua 4 gio la false`() {
        val pl = appOpenLike()
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)

        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS - 1
        assertTrue(pl.hasLoadedAd())

        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        assertFalse(pl.hasLoadedAd())
        assertFalse(item.isLoaded)
    }

    // ═══ hasFreshAdForReuse() ═══

    @Test
    fun `hasFreshAdForReuse - app open qua 4 gio la false va quang cao bi bo`() {
        val pl = appOpenLike()
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1

        assertFalse(pl.hasFreshAdForReuse())
        assertFalse("hết hạn phải được bỏ ngay ở cửa này, không chờ show", item.isLoaded)
        assertNull(item.adObject)
    }

    @Test
    fun `hasFreshAdForReuse - cua so tai dung 1 gio giu nguyen, theo dong ho cua placement`() {
        val pl = Pl(maxAgeMs = null)
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)

        clock.nowMs = item.loadedAtMillis + 30 * 60 * 1000L
        assertTrue(pl.hasFreshAdForReuse())

        clock.nowMs = item.loadedAtMillis + 60 * 60 * 1000L
        assertFalse("quá 1 giờ thì không tái dùng", pl.hasFreshAdForReuse())
        assertTrue("nhưng vẫn là quảng cáo đã load, không bị bỏ", item.isLoaded)
    }

    // ═══ Định dạng khác, và quảng cáo đang hiện ═══

    @Test
    fun `placement khong khai tuoi toi da - quang cao cu van la da load`() {
        val pl = Pl(maxAgeMs = null).apply { setActivity(mock(Activity::class.java)) }
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
        clock.nowMs = item.loadedAtMillis + 10 * APP_OPEN_MAX_AGE_MS
        val cb = RecordingLoadCb()

        assertTrue(pl.hasLoadedAd())
        pl.load("full_test", cb)

        assertEquals(listOf("loaded:${item.adsId}"), cb.events)
        assertEquals(0, pl.tryLoads)
        assertTrue(item.isLoaded)
    }

    @Test
    fun `quang cao dang hien khong bi bo du da qua 4 gio`() {
        // Bỏ (và với MAX, destroy) quảng cáo đang trên màn hình làm mất onAdHidden: app không nhận
        // onAdDismissed, cờ full-screen kẹt.
        val pl = appOpenLike()
        val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
        pl.setShowing(true)
        clock.nowMs = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1

        pl.hasLoadedAd()

        assertTrue(item.isLoaded)
        assertNotNull(item.adObject)
    }
}
