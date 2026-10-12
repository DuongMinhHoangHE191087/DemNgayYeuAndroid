package com.app.plugin.nextgen

import android.app.Activity
import android.content.DialogInterface
import android.os.Handler
import com.app.plugin.ads.LoadedAdFixture
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.nextgen.NgNtFullKit.Companion.field
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

/**
 * 2.4.0 NG5b NC5: bản nextgen (Next-Gen `NativeAd`/`NativeAdView`, seam nền: `MainPost.isMain = { false }` + `poster` FIFO)
 * của `NtFullPreparedViewOrderTest` (M1–M3: huỷ view cũ TRƯỚC khi gắn view mới) và `NtFullPreparedViewReleaseTest`
 * (K1–K2, T1–T5: bỏ dialog dựng sẵn thì huỷ view, giữ quảng cáo). `prepareChainedShow` nextgen dựng qua một lần post:
 * test xả hàng main ([NgNtFullKit.drain]) trước khi kiểm.
 */
class NgNtFullOrderTest {
    private val kit = NgNtFullKit().install()
    private val pl = kit.pl
    private val ad: NativeAd = mock(NativeAd::class.java)
    private val view: NativeAdView = mock(NativeAdView::class.java)
    private val prepared: AdsMobNtFullView = spy(AdsMobNtFullView(kit.host, ad, 3, false, true)).also {
        field(AdsMobNtFullView::class.java, "mNativeAdView").set(it, view)
    }

    @After fun tearDown() = kit.uninstall()

    /** Trạng thái sau `prepareChainedShow` trên host: dialog đã populate, chưa hiện. */
    private fun prepare() {
        pl.setActivity(kit.host)
        kit.own("preparedDialog").set(pl, prepared)
        kit.own("preparedNativeAd").set(pl, ad)
        kit.own("preparedActivity").set(pl, kit.host)
        pl.mDialogNativeFull = null
        kit.slot().adsShowCb = kit.showCb
    }

    private fun beginShow() { pl.nativeAdShow = ad; kit.slot().isShowing = true }

    private fun assertPreparedCleared() {
        assertNull(kit.own("preparedDialog").get(pl)); assertNull(kit.own("preparedNativeAd").get(pl)); assertNull(kit.own("preparedActivity").get(pl))
    }

    @Test fun `M1 getAdForShow lay dung ad cua dialog dung san - huy view dung san truoc khi gan vao container`() {
        prepare()
        LoadedAdFixture.giveLoadedAd(pl, ad, LoadedAdFixture.Clock())
        runCatching { pl.getAdForShow("full_2_default", null) } // JVM: inflate ném SAU điểm phải huỷ view cũ
        verify(view).destroy()
        assertNull(kit.own("preparedDialog").get(pl)); assertNull(kit.own("preparedNativeAd").get(pl))
        verify(ad, never()).destroy()
    }

    @Test fun `M1 getAdForShow lay ad khac - khong dung dialog dung san`() {
        prepare()
        LoadedAdFixture.giveLoadedAd(pl, mock(NativeAd::class.java), LoadedAdFixture.Clock())
        runCatching { pl.getAdForShow("full_2_default", null) }
        verify(view, never()).destroy()
        assertEquals(prepared, kit.own("preparedDialog").get(pl))
    }

    @Test fun `M2 prepareChainedShow loi trong create - nha view do dang, khong giu gi`() {
        pl.setActivity(kit.host)
        LoadedAdFixture.giveLoadedAd(pl, ad, LoadedAdFixture.Clock())
        mockConstruction(AdsMobNtFullView::class.java) { d, _ -> doThrow(NullPointerException("mediaContent")).`when`(d).create() }.use { built ->
            pl.prepareChainedShow()
            kit.drain()
            assertEquals(1, built.constructed().size)
            verify(built.constructed()[0]).releaseNativeAdView()
        }
        assertPreparedCleared()
        verify(ad, never()).destroy()
    }

    @Test fun `M3 showWidthDialog doi host - huy view cu truoc khi dialog moi show`() {
        prepare()
        pl.setActivity(mock(Activity::class.java))
        beginShow()
        mockConstruction(AdsMobNtFullView::class.java).use { built ->
            assertEquals(true, kit.showWidthDialog())
            val fresh = built.constructed().single()
            val order = inOrder(view, fresh)
            order.verify(view).destroy()
            order.verify(fresh).setOnDismissListener(any())
            order.verify(fresh).show()
        }
    }

    @Test fun `K1 dialog dung san dung lai duoc - khong huy view, dung mot onAdShowed`() {
        prepare(); beginShow()
        assertEquals(true, kit.showWidthDialog())
        verify(view, never()).destroy()
        assertEquals(listOf("showed"), kit.events)
        assertSame(prepared, pl.mDialogNativeFull)
    }

    @Test fun `K2 sau K1 dong dialog - quang cao huy dung mot lan, dung mot onAdDismissed`() {
        prepare(); beginShow()
        assertEquals(true, kit.showWidthDialog())
        val listener = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
        verify(prepared).setOnDismissListener(listener.capture())
        listener.value.onDismiss(prepared)
        verify(ad, times(1)).destroy()
        assertEquals(listOf("showed", "dismissed"), kit.events)
    }

    @Test fun `T1 Full2 dung san, host bi huy - huy view, giu NativeAd, khong dismiss`() {
        prepare()
        pl.clearActivity()
        verify(view).destroy()
        assertPreparedCleared()
        verify(ad, never()).destroy()
        assertEquals(emptyList<String>(), kit.events)
    }

    @Test fun `T2 chuoi khong noi - huy view, giu NativeAd`() {
        prepare()
        pl.cancelChainedShow()
        verify(view).destroy()
        verify(ad, never()).destroy()
        assertPreparedCleared()
        assertEquals(false, kit.slot().isShowing)
    }

    @Test fun `T3 hien lan ke tren host moi - huy view cu, dialog moi, dung mot callback, giu NativeAd`() {
        prepare()
        pl.setActivity(mock(Activity::class.java))
        beginShow()
        assertEquals(true, kit.showWidthDialog())
        verify(view).destroy()
        assertNotNull(pl.mDialogNativeFull)
        assertNotSame(prepared, pl.mDialogNativeFull)
        assertEquals(listOf("showed"), kit.events)
        verify(ad, never()).destroy()
    }

    @Test fun `T4 bo dialog hai lan - view huy dung mot lan`() {
        prepare()
        pl.cancelChainedShow(); pl.cancelChainedShow()
        verify(view, times(1)).destroy()
        kit.own("preparedDialog").set(pl, prepared)
        pl.clearActivity()
        verify(view, times(1)).destroy()
    }

    @Test fun `T5 destroy nem - clearActivity khong nem, field null, cancelCountdown van chay`() {
        doThrow(RuntimeException("boom")).`when`(view).destroy()
        val handler = mock(Handler::class.java)
        field(AdsMobNtFullView::class.java, "countTimeHandler").set(prepared, handler)
        prepare()
        pl.clearActivity()
        assertPreparedCleared()
        verify(handler).removeCallbacksAndMessages(null)
        verify(ad, never()).destroy()
    }
}
