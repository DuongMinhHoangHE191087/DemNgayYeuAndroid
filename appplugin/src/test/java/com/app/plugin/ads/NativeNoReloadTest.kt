package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.ViewGroup
import com.app.plugin.ads.core.UnitGate
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer

/**
 * 2.5.0 PLMN (Hợp đồng A, quyết định (f)): danh sách native không tự nạp lại khi `showNative` chưa có quảng cáo đọc từ
 * `@string/ads_cf_native_no_reload` (CSV): vắng = `nt_language,nt_language_2,nt_intro` (2.4.4), có mặt (kể cả rỗng) =
 * đúng danh sách đó. Harness như `AdsDeliveryRegressionTest` (helper dựng bằng reflection, mạng native giả).
 * 2.5.1 PLMNORM (Hợp đồng B): mục thiếu tiền tố `nt_` được chuẩn hoá thành `nt_<mục>` (như `showNative`) + đúng một `logE`
 * mỗi mục; lỗi đọc resource ⇒ `logE` + 3 placement cũ (PL1d–PL1f).
 */
class NativeNoReloadTest {
    private val dummy = object : AdsLoadInnerCb {
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) = Unit
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
    }

    @After fun tearDown() = UnitGate.resetForTest() // NativeController đăng ký vị từ SHOW vào cổng unit (PRIO)

    private fun field(h: AdsHelper, name: String, value: Any?) =
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(h, value)

    /** [list] `null` = app không khai resource; [readFails] = `getString` ném (resource hỏng). */
    private fun showWithoutAd(list: String?, vararg placements: String, readFails: Boolean = false): AdsBaseNative {
        val h = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        h.currConfig = AdsConfigOb(mock(Context::class.java)).apply { nativeStepList.add(0) }
        h.placementsConfigManager = AdsPlacementConfigManager()
        val network = mock(AdsBaseNative::class.java)
        field(h, "listAdsNative", mutableMapOf(0 to network))
        val ctx = mock(Context::class.java); val res = mock(Resources::class.java)
        `when`(ctx.resources).thenReturn(res); `when`(ctx.packageName).thenReturn("app")
        if (list != null) {
            `when`(res.getIdentifier(eq("ads_cf_native_no_reload") ?: "", eq("string") ?: "", anyString() ?: "")).thenReturn(42)
            if (readFails) `when`(res.getString(42)).thenThrow(RuntimeException("boom"))
            else `when`(res.getString(42)).thenReturn(list)
        }
        val prefs = mock(SharedPreferences::class.java, Answer<Any?> { inv ->
            if (inv.arguments.size == 2) inv.arguments[1] else RETURNS_DEEP_STUBS.answer(inv) })
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(ctx.applicationContext).thenReturn(ctx)
        field(h, "mContext", ctx)
        for (pl in placements) {
            val parent = mock(ViewGroup::class.java); `when`(parent.isAttachedToWindow).thenReturn(true)
            h.showNative(pl, parent, null)
        }
        return network
    }

    private fun loads(network: AdsBaseNative, pl: String, n: Int) =
        verify(network, times(n)).loadAd(eq(pl) ?: pl, any(AdsLoadInnerCb::class.java) ?: dummy)

    @Test fun `PL1 ads_cf_native_no_reload rong thi nt_language tu nap khi showNative`() {
        loads(showWithoutAd("", "language"), "nt_language", 1)
    }

    @Test fun `PL1b vang resource thi ba placement 244 khong tu nap`() {
        val network = showWithoutAd(null, "language", "language_2", "intro", "home")
        verify(network, never()).loadAd(eq("nt_language") ?: "", any(AdsLoadInnerCb::class.java) ?: dummy)
        verify(network, never()).loadAd(eq("nt_language_2") ?: "", any(AdsLoadInnerCb::class.java) ?: dummy)
        verify(network, never()).loadAd(eq("nt_intro") ?: "", any(AdsLoadInnerCb::class.java) ?: dummy)
        loads(network, "nt_home", 1)
    }

    @Test fun `PL1c danh sach nt_home thi nt_language nap, nt_home khong`() {
        val network = showWithoutAd(" nt_home ", "language", "home")
        loads(network, "nt_language", 1)
        verify(network, never()).loadAd(eq("nt_home") ?: "", any(AdsLoadInnerCb::class.java) ?: dummy)
    }

    private fun noLoad(network: AdsBaseNative, pl: String) =
        verify(network, never()).loadAd(eq(pl) ?: pl, any(AdsLoadInnerCb::class.java) ?: dummy)

    @Test fun `PL1d muc thieu tien to nt_ duoc chuan hoa, nt_language khong tu nap`() {
        val network = showWithoutAd("language", "language", "home")
        noLoad(network, "nt_language")
        loads(network, "nt_home", 1)
    }

    @Test fun `PL1e moi muc thieu nt_ dung mot logE, muc co nt_ khong log`() {
        LogUtil.setFlagLog(1)
        mockStatic(Log::class.java).use { log ->
            val network = showWithoutAd("language, nt_intro", "language", "intro", "home", "language")
            noLoad(network, "nt_language"); noLoad(network, "nt_intro")
            log.verify({ Log.e(eq(LogUtil.TAG), contains("thiếu tiền tố nt_")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), eq("ads_cf_native_no_reload: mục 'language' thiếu tiền tố nt_, dùng 'nt_language'")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), contains("nt_intro")) }, never())
        }
    }

    @Test fun `PL1f loi doc resource thi logE va dung ba placement cu`() {
        LogUtil.setFlagLog(1)
        mockStatic(Log::class.java).use { log ->
            val network = showWithoutAd("x", "language", "home", "intro", readFails = true)
            noLoad(network, "nt_language"); noLoad(network, "nt_intro")
            loads(network, "nt_home", 1)
            log.verify({ Log.e(eq(LogUtil.TAG), contains("ads_cf_native_no_reload read failed")) }, times(1))
            log.verify({ Log.e(eq(LogUtil.TAG), eq("ads_cf_native_no_reload read failed: boom")) }, times(1))
        }
    }
}
