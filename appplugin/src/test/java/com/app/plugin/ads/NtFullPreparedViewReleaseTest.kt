package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * 1.6.0 Task 10 (G9 hàng 13, đường A): dialog Full2 dựng sẵn (`prepareChainedShow`) đã gọi
 * `NativeAdView.setNativeAd(ad)` trên view inflate bằng context Activity. Bỏ dialog mà giữ quảng cáo
 * (host chết lúc cha đang hiện, chuỗi không nối, đổi host lúc show) thì GMA vẫn giữ view từ `NativeAd`
 * → Activity cũ sống theo `AdsEcmp.adObject`. Bỏ dialog phải huỷ view (`NativeAdView.destroy()`), không
 * huỷ quảng cáo. AdMob only: Meta đã `unregisterView()`, MAX chỉ `render()` ở `onStart`.
 */
class NtFullPreparedViewReleaseTest {

    private class Fixture {
        val pl = AdsMobPlNtFull(mock(Context::class.java), mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default")
        val hostA: Activity = mock(Activity::class.java)
        val ad: NativeAd = mock(NativeAd::class.java)
        val view: NativeAdView = mock(NativeAdView::class.java)
        val events = mutableListOf<String>()
        val prepared: AdsMobNtFullView = spy(AdsMobNtFullView(hostA, ad, 3, false, true)).also {
            field(AdsMobNtFullView::class.java, "mNativeAdView").set(it, view)
        }

        /** Trạng thái sau `prepareChainedShow` trên hostA: dialog đã populate, chưa hiện. */
        fun prepare() {
            pl.setActivity(hostA)
            own("preparedDialog").set(pl, prepared)
            own("preparedNativeAd").set(pl, ad)
            own("preparedActivity").set(pl, hostA)
            pl.mDialogNativeFull = null
            slot().adsShowCb = object : AdsShowCb {
                override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
                override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail" }
                override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
            }
        }

        fun own(name: String) = field(AdsMobPlNtFull::class.java, name)
        fun slot() = field(AdsPlBase::class.java, "adsPlacementEcmp").get(pl) as AdsPlacementEcmpBase
        fun showWidthDialog(): Any? =
            AdsMobPlNtFull::class.java.getDeclaredMethod("showWidthDialog").apply { isAccessible = true }.invoke(pl)

        fun assertPreparedCleared() {
            assertNull("preparedDialog phải về null", own("preparedDialog").get(pl))
            assertNull("preparedNativeAd phải về null", own("preparedNativeAd").get(pl))
            assertNull("preparedActivity phải về null", own("preparedActivity").get(pl))
        }

        /** Mô phỏng lượt show trên host hiện tại, như `show()` đặt trước `showWidthDialog()`. */
        fun beginShow() {
            pl.nativeAdShow = ad
            slot().isShowing = true
        }
    }

    private companion object {
        fun field(cls: Class<*>, name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
    }

    @Test
    fun `K1 dialog dung san dung lai duoc - khong huy view, dung mot onAdShowed`() {
        val f = Fixture().apply { prepare(); beginShow() }

        assertEquals("dialog dựng sẵn phải hiện", true, f.showWidthDialog())

        verify(f.view, never()).destroy()
        assertEquals(listOf("showed"), f.events)
        assertSame("phải dùng lại đúng dialog dựng sẵn", f.prepared, f.pl.mDialogNativeFull)
    }

    @Test
    fun `K2 sau K1 dong dialog - quang cao huy dung mot lan, dung mot onAdDismissed`() {
        val f = Fixture().apply { prepare(); beginShow() }
        assertEquals(true, f.showWidthDialog())
        val listener = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
        verify(f.prepared).setOnDismissListener(listener.capture())

        listener.value.onDismiss(f.prepared)

        verify(f.ad, times(1)).destroy()
        assertEquals(listOf("showed", "dismissed"), f.events)
    }

    @Test
    fun `T1 Full2 dung san, host bi huy - huy view, giu NativeAd, khong dismiss`() {
        val f = Fixture().apply { prepare() }

        f.pl.clearActivity()

        try {
            verify(f.view).destroy()
        } catch (e: Throwable) {
            throw AssertionError("view của dialog dựng sẵn phải được huỷ khi host chết", e)
        }
        f.assertPreparedCleared()
        try {
            verify(f.ad, never()).destroy()
        } catch (e: Throwable) {
            throw AssertionError("NativeAd phải giữ", e)
        }
        assertEquals("không có dialog đang hiện thì không có callback", emptyList<String>(), f.events)
        assertNull("mActivity phải về null", field(AdsPlBase::class.java, "mActivity").get(f.pl))
    }

    @Test
    fun `T2 chuoi khong noi - huy view, giu NativeAd`() {
        val f = Fixture().apply { prepare() }

        f.pl.cancelChainedShow()

        try {
            verify(f.view).destroy()
        } catch (e: Throwable) {
            throw AssertionError("chuỗi không nối: view của dialog dựng sẵn phải được huỷ", e)
        }
        verify(f.ad, never()).destroy()
        f.assertPreparedCleared()
        assertEquals("isShowing phải false", false, f.slot().isShowing)
    }

    @Test
    fun `T3 hien lan ke tren host moi - huy view cu, dialog moi, dung mot callback, giu NativeAd`() {
        val f = Fixture().apply { prepare() }
        f.pl.setActivity(mock(Activity::class.java))
        f.beginShow()

        assertEquals("Full2 phải hiện trên host mới", true, f.showWidthDialog())

        try {
            verify(f.view).destroy()
        } catch (e: Throwable) {
            throw AssertionError("đổi host: view cũ của dialog dựng sẵn phải được huỷ", e)
        }
        assertNotNull("phải có dialog mới", f.pl.mDialogNativeFull)
        assertNotSame("không được dùng lại dialog của host cũ", f.prepared, f.pl.mDialogNativeFull)
        assertNull(f.own("preparedDialog").get(f.pl))
        assertEquals("đúng một onAdShowed, không onAdShowFail", listOf("showed"), f.events)
        verify(f.ad, never()).destroy()
    }

    @Test
    fun `T4 bo dialog hai lan - view huy dung mot lan`() {
        val a = Fixture().apply { prepare() }
        a.pl.cancelChainedShow()
        a.pl.cancelChainedShow()
        try {
            verify(a.view, times(1)).destroy()
        } catch (e: Throwable) {
            throw AssertionError("cancelChainedShow ×2: view phải huỷ đúng một lần", e)
        }

        val b = Fixture().apply { prepare() }
        b.pl.cancelChainedShow()
        // Cùng dialog còn được tham chiếu nơi khác bị bỏ lần hai (idempotent ở tầng view).
        b.own("preparedDialog").set(b.pl, b.prepared)
        b.pl.clearActivity()
        try {
            verify(b.view, times(1)).destroy()
        } catch (e: Throwable) {
            throw AssertionError("cancelChainedShow rồi clearActivity: view phải huỷ đúng một lần", e)
        }
    }

    @Test
    fun `T5 destroy nem - clearActivity khong nem, field null, cancelCountdown van chay`() {
        // Xanh cả trước và sau sửa: bảo vệ đột biến M5 (bỏ runCatching quanh releaseNativeAdView).
        val f = Fixture()
        doThrow(RuntimeException("boom")).`when`(f.view).destroy()
        val handler = mock(Handler::class.java)
        field(AdsMobNtFullView::class.java, "countTimeHandler").set(f.prepared, handler)
        f.prepare()

        f.pl.clearActivity()

        f.assertPreparedCleared()
        verify(handler).removeCallbacksAndMessages(null)
        verify(f.ad, never()).destroy()
    }
}
