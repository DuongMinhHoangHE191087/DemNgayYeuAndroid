package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import com.app.plugin.ads.adsfb.AdsFbMy
import com.app.plugin.ads.adsfb.AdsFbPlBanner
import com.app.plugin.ads.adsfb.AdsFbPlNt
import com.app.plugin.ads.adsfb.AdsFbPlNtCl
import com.app.plugin.ads.adsmax.AdsMaxMy
import com.app.plugin.ads.adsmax.AdsMaxPlBanner
import com.app.plugin.ads.adsmax.AdsMaxPlNt
import com.app.plugin.ads.adsmax.AdsMaxPlNtCl
import com.app.plugin.ads.adsmob.AdsMobPlNt
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
 * 2.4.3 NATAGE (Hợp đồng D): native inline ([AdsPlBaseNative]: AdMob, Meta, MAX) nạp sẵn quá
 * [AdsPolicy.fullscreenMaxAgeMillis] (mặc định 60 phút, RC `fullscreen_max_age_minutes`) là chưa load: `hasLoadedAd`
 * false, `getAdForShow` trả `null` + `ad_expired`, quảng cáo bị bỏ. Banner, NtCl không đổi (không hạn tuổi). Lỗi liệt
 * kê theo tên lớp, để một lớp còn đọc `getAdLoaded()` thô không trốn sau lớp khác. Không đọc nguồn và không chạm lớp
 * GMA nên chạy cả ở appplugin-nextgen (dựng `AdsMobPlNt` Next-Gen cùng chữ ký).
 */
class AdsNativeExpiryTest {
    private lateinit var context: Context
    private val clock = LoadedAdFixture.Clock()
    private val activity = mock(Activity::class.java)
    private val minute = 60_000L

    private class NoShowCb : AdsShowCb

    /** Hàng chờ Meta/MAX (test không load; AdMob khác nhau Legacy/Next-Gen và không bị chạm ở đây). */
    private fun resetQueues() {
        AdsMaxMy.instance.readyQueue.resetForTest()
        AdsFbMy.instance.readyQueue.resetForTest()
    }

    @Before fun setUp() {
        context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        resetQueues()
    }

    @After fun tearDown() {
        AdsPolicy.current = AdsPolicy.DEFAULT
        resetQueues()
    }

    private fun base() = mock(AdsBase::class.java)

    private val natives: List<Pair<String, (Context) -> AdsPlBase>> = listOf(
        "AdsMobPlNt" to { c -> AdsMobPlNt(c, base(), AdsIDDef.AdmobNative, "nt_default") },
        "AdsFbPlNt" to { c -> AdsFbPlNt(c, base(), AdsIDDef.FBNative, "nt_default") },
        "AdsMaxPlNt" to { c -> AdsMaxPlNt(c, base(), AdsIDDef.MaxNative, "nt_default") },
    )

    private val notNative: List<Pair<String, (Context) -> AdsPlBase>> = listOf(
        "AdsFbPlBanner" to { c -> AdsFbPlBanner(c, base(), AdsIDDef.FBBanner, "bn_default") },
        "AdsMaxPlBanner" to { c -> AdsMaxPlBanner(c, base(), AdsIDDef.MaxBanner, "bn_default") },
        "AdsFbPlNtCl" to { c -> AdsFbPlNtCl(c, base(), AdsIDDef.FBNtCl, "cl_default") },
        "AdsMaxPlNtCl" to { c -> AdsMaxPlNtCl(c, base(), AdsIDDef.MaxNtCl, "cl_default") },
    )

    /** Mỗi lớp một placement mới có Activity và một quảng cáo giả; trả "lớp: lỗi" (rỗng là đạt). */
    private fun each(list: List<Pair<String, (Context) -> AdsPlBase>>, check: (AdsPlBase, AdsEcmp) -> String?): List<String> =
        list.mapNotNull { (name, make) ->
            val pl = make(context).also { it.setActivity(activity) }
            val item = LoadedAdFixture.giveLoadedAd(pl, Any(), clock)
            check(pl, item)?.let { "$name: $it" }
        }

    private fun expiredEvents(count: Int, action: () -> Unit): List<String> = ServerEventCapture().use { server ->
        action()
        server.await("ad_show_fail", count).map { it.get("reason").asString }.filter { it == Constants.AdShowFailReason.AD_EXPIRED }
    }

    @Test fun `NA1 native 59 phut con, du 60 phut het han - moi lop native`() {
        val wrong = each(natives) { pl, item ->
            clock.nowMs = item.loadedAtMillis + 60 * minute - 1
            if (!pl.hasLoadedAd()) return@each "59 phút phải còn hạn"
            clock.nowMs = item.loadedAtMillis + 60 * minute
            if (pl.hasLoadedAd()) "60 phút phải hết hạn" else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun `NA2 getAdForShow qua 60 phut tra null, gui ad_expired, quang cao bi bo - moi lop native`() {
        val wrong = each(natives) { pl, item ->
            clock.nowMs = item.loadedAtMillis + 60 * minute + 1
            var got: Any? = Unit
            var thrown: Throwable? = null
            val expired = expiredEvents(1) { got = runCatching { pl.getAdForShow("nt_home", NoShowCb()) }.onFailure { thrown = it }.getOrNull() }
            when {
                thrown != null -> "getAdForShow ném ${thrown!!.javaClass.simpleName} (đọc quảng cáo quá tuổi?)"
                got != null -> "getAdForShow trả quảng cáo hết hạn"
                expired.isEmpty() -> "không gửi ad_expired"
                item.isLoaded || item.adObject != null -> "quảng cáo hết hạn không bị bỏ"
                else -> null
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /**
     * Bất biến pool (Fable 2.4.3 §5): native đã lấy qua `getAdForShow` rời pool ngay (`adObject = null`), nên lần xét
     * tuổi sau không bỏ/huỷ nó (0 `ad_expired`). Meta, MAX: quảng cáo giả không đúng kiểu ⇒ lấy ra rồi trả `null`, đủ để
     * kiểm pool mà không dựng view (AdMob ép kiểu cứng `NativeAd` nên không dùng được quảng cáo giả ở đây).
     */
    @Test fun `NA2b native da lay qua getAdForShow khong bi bo o lan xet tuoi sau - Meta, MAX`() {
        val wrong = each(natives.filter { it.first != "AdsMobPlNt" }) { pl, item ->
            clock.nowMs = item.loadedAtMillis + 59 * minute
            pl.getAdForShow("nt_home", NoShowCb())
            if (item.isLoaded || item.adObject != null) return@each "getAdForShow không rút quảng cáo khỏi pool"
            clock.nowMs = item.loadedAtMillis + 61 * minute
            val expired = expiredEvents(0) { pl.hasLoadedAd(); pl.getAdForShow("nt_home", NoShowCb()) }
            if (expired.isNotEmpty()) "quảng cáo đã lấy bị xét tuổi lại: $expired" else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun `NA3 RC 120 phut - native 90 phut con, 120 het - banner va NtCl khong het han o 2 gio`() {
        AdsPolicy.current = AdsPolicy.parse("""{"fullscreen_max_age_minutes":120}""")
        val wrongNative = each(natives) { pl, item ->
            clock.nowMs = item.loadedAtMillis + 90 * minute
            if (!pl.hasLoadedAd()) return@each "RC 120: 90 phút phải còn hạn"
            clock.nowMs = item.loadedAtMillis + 120 * minute
            if (pl.hasLoadedAd()) "RC 120: 120 phút phải hết hạn" else null
        }
        AdsPolicy.current = AdsPolicy.DEFAULT
        val wrongOther = each(notNative) { pl, item ->
            clock.nowMs = item.loadedAtMillis + 120 * minute
            if (!pl.hasLoadedAd()) "2 giờ: banner/NtCl không có hạn tuổi" else null
        }
        assertEquals(emptyList<String>(), wrongNative + wrongOther)
    }
}
