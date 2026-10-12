package com.app.plugin.nextgen

import android.app.Activity
import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.nextgen.NgNtFullKit.Companion.field
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 2.4.0 NG5b NC5: bản nextgen (AdMob Next-Gen, seam nền) của `NtFullHostDestroyTest`: host chết → `mActivity` về null
 * TRƯỚC khi phát dismiss, đúng một onAdDismissed (kể cả listener cũ tới sau `clearActivity`), nhả lớp phủ safe-area và
 * dialog dựng sẵn kể cả khi không có dialog đang hiện.
 */
class NgNtFullHostDestroyTest {
    private val kit = NgNtFullKit().install()
    private val pl = kit.pl
    private val dialog: AdsMobNtFullView = mock(AdsMobNtFullView::class.java)
    private val ad: NativeAd = mock(NativeAd::class.java)

    @After fun tearDown() = kit.uninstall()

    private fun mActivity() = field(AdsPlBase::class.java, "mActivity").get(pl)

    private fun recordDismisses(): MutableList<String?> {
        val dismissed = mutableListOf<String?>()
        kit.slot().adsShowCb = object : AdsShowCb {
            override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { dismissed += placement }
        }
        return dismissed
    }

    /** Dialog đang hiện qua đường show thật (dùng lại dialog dựng sẵn cùng quảng cáo + host); trả listener dismiss đã gắn. */
    private fun showViaProduction(): DialogInterface.OnDismissListener {
        val host = mock(Activity::class.java)
        pl.setActivity(host)
        pl.nativeAdShow = ad
        kit.own("preparedDialog").set(pl, dialog); kit.own("preparedNativeAd").set(pl, ad); kit.own("preparedActivity").set(pl, host)
        kit.slot().isShowing = true
        assertEquals(true, kit.showWidthDialog())
        val listener = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
        verify(dialog).setOnDismissListener(listener.capture())
        return listener.value
    }

    /** Lớp phủ safe-area (trong `background`, AdsMobNtFullBackground) đang gắn vào content của host. */
    private fun attachOverlay(): Pair<RelativeLayout, ViewGroup> {
        val owner = kit.own("background").get(pl)
        val overlay = mock(RelativeLayout::class.java)
        val content = mock(ViewGroup::class.java)
        `when`(overlay.parent).thenReturn(content)
        field(owner.javaClass, "parentBlackSafeAreaView").set(owner, overlay)
        return overlay to content
    }

    private fun overlay(): Any? = kit.own("background").get(pl).let { field(it.javaClass, "parentBlackSafeAreaView").get(it) }

    @Test fun `host chet khi dialog dang hien - mActivity ve null truoc dismiss, dung mot dismiss, nha lop phu`() {
        pl.setActivity(mock(Activity::class.java))
        pl.mDialogNativeFull = dialog
        val (ov, content) = attachOverlay()
        val activityAtDismiss = mutableListOf<Any?>()
        kit.slot().isShowing = true
        kit.slot().adsShowCb = object : AdsShowCb {
            override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { activityAtDismiss += mActivity() }
        }
        pl.clearActivity()
        assertEquals(listOf<Any?>(null), activityAtDismiss)
        verify(dialog).setOnDismissListener(null)
        verify(dialog).dismiss()
        assertNull(pl.mDialogNativeFull)
        assertEquals(false, kit.slot().isShowing)
        verify(content).removeView(ov)
        assertNull(overlay())
    }

    @Test fun `host chet khi khong co dialog - khong dismiss, van nha lop phu va dialog dung san`() {
        val host = mock(Activity::class.java)
        pl.setActivity(host)
        val (ov, content) = attachOverlay()
        val prepared = mock(AdsMobNtFullView::class.java)
        kit.own("preparedDialog").set(pl, prepared); kit.own("preparedNativeAd").set(pl, ad); kit.own("preparedActivity").set(pl, host)
        val dismissed = recordDismisses()
        pl.clearActivity()
        assertEquals(0, dismissed.size)
        assertNull(mActivity())
        verify(content).removeView(ov)
        assertNull(overlay())
        assertNull(kit.own("preparedDialog").get(pl)); assertNull(kit.own("preparedActivity").get(pl)); assertNull(kit.own("preparedNativeAd").get(pl))
        verify(prepared).cancelCountdown()
    }

    @Test fun `clearActivity goi hai lan - van dung mot onAdDismissed`() {
        val dismissed = recordDismisses()
        showViaProduction()
        pl.clearActivity(); pl.clearActivity()
        assertEquals(1, dismissed.size)
        verify(dialog).dismiss()
    }

    @Test fun `listener dismiss cu toi sau clearActivity - van dung mot onAdDismissed`() {
        val dismissed = recordDismisses()
        val stale = showViaProduction()
        pl.clearActivity()
        stale.onDismiss(dialog)
        assertEquals(1, dismissed.size)
        assertNull(pl.mDialogNativeFull)
        assertEquals(false, kit.slot().isShowing)
    }

    @Test fun `dong binh thuong roi host chet - dung mot onAdDismissed, tu listener`() {
        val dismissed = recordDismisses()
        val listener = showViaProduction()
        listener.onDismiss(dialog)
        assertEquals(1, dismissed.size)
        assertNull(pl.mDialogNativeFull)
        verify(dialog).cancelCountdown()
        pl.clearActivity()
        assertEquals(1, dismissed.size)
    }
}
