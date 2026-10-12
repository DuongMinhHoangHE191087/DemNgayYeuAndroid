package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

/**
 * Review Task 10 (1.6.0), bổ sung cho [NtFullPreparedViewReleaseTest]. `NativeAdView.destroy()` đặt view hiện hành của
 * engine GMA về null **vô điều kiện** (không so view đang huỷ với view đang gắn), nên huỷ view cũ SAU khi cùng quảng cáo
 * đã gắn vào view mới làm view mới mất custom click gesture và policy validator. Thứ tự phải là: huỷ view cũ → gắn view mới.
 * - M1: `getAdForShow` (container) lấy đúng quảng cáo đang có dialog dựng sẵn → bỏ dialog đó TRƯỚC khi populate.
 * - M2: `prepareChainedShow` lỗi sau `setNativeAd` (trong `create()`) → nhả view dở dang (`preparedDialog` chưa gán).
 * - M3: `showWidthDialog` không dùng lại dialog dựng sẵn → huỷ view cũ trước khi dialog mới `show()` (onCreate gắn ad).
 */
class NtFullPreparedViewOrderTest {

    private val pl = AdsMobPlNtFull(mock(Context::class.java), mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default")
    private val hostA: Activity = mock(Activity::class.java)
    private val ad: NativeAd = mock(NativeAd::class.java)
    private val view: NativeAdView = mock(NativeAdView::class.java)

    private fun own(name: String) = AdsMobPlNtFull::class.java.getDeclaredField(name).apply { isAccessible = true }

    /** Dialog dựng sẵn trên hostA cho [ad], view của nó là mock [view]. */
    private fun prepare(): AdsMobNtFullView {
        val prepared = spy(AdsMobNtFullView(hostA, ad, 3, false, true))
        AdsMobNtFullView::class.java.getDeclaredField("mNativeAdView").apply { isAccessible = true }.set(prepared, view)
        pl.setActivity(hostA)
        own("preparedDialog").set(pl, prepared)
        own("preparedNativeAd").set(pl, ad)
        own("preparedActivity").set(pl, hostA)
        return prepared
    }

    @Test
    fun `M1 getAdForShow lay dung ad cua dialog dung san - huy view dung san truoc khi gan vao container`() {
        prepare()
        LoadedAdFixture.giveLoadedAd(pl, ad, LoadedAdFixture.Clock())

        // JVM: LayoutInflater.from(mContext) là null → populate ném ngay ở bước inflate, tức SAU điểm phải huỷ view cũ.
        runCatching { pl.getAdForShow("full_2_default", null) }

        try {
            verify(view).destroy()
        } catch (e: Throwable) {
            throw AssertionError("container lấy ad của dialog dựng sẵn: view dựng sẵn phải huỷ trước khi ad gắn view mới", e)
        }
        assertNull("dialog dựng sẵn phải được bỏ", own("preparedDialog").get(pl))
        assertNull("preparedNativeAd phải về null", own("preparedNativeAd").get(pl))
        verify(ad, never()).destroy()
    }

    @Test
    fun `M1 getAdForShow lay ad khac - khong dung dialog dung san`() {
        val prepared = prepare()
        LoadedAdFixture.giveLoadedAd(pl, mock(NativeAd::class.java), LoadedAdFixture.Clock())

        runCatching { pl.getAdForShow("full_2_default", null) }

        verify(view, never()).destroy()
        assertEquals("dialog dựng sẵn cho ad khác phải giữ", prepared, own("preparedDialog").get(pl))
    }

    @Test
    fun `M2 prepareChainedShow loi trong create - nha view do dang, khong giu gi`() {
        pl.setActivity(hostA)
        LoadedAdFixture.giveLoadedAd(pl, ad, LoadedAdFixture.Clock())

        mockConstruction(AdsMobNtFullView::class.java) { d, _ -> doThrow(NullPointerException("mediaContent")).`when`(d).create() }
            .use { built ->
                pl.prepareChainedShow()

                assertEquals("đúng một dialog được dựng", 1, built.constructed().size)
                try {
                    verify(built.constructed()[0]).releaseNativeAdView()
                } catch (e: Throwable) {
                    throw AssertionError("create() lỗi sau setNativeAd: view dở dang phải được nhả", e)
                }
            }
        assertNull(own("preparedDialog").get(pl))
        assertNull(own("preparedNativeAd").get(pl))
        assertNull(own("preparedActivity").get(pl))
        verify(ad, never()).destroy()
    }

    @Test
    fun `M3 showWidthDialog doi host - huy view cu truoc khi dialog moi show`() {
        prepare()
        pl.setActivity(mock(Activity::class.java))
        pl.nativeAdShow = ad
        LoadedAdFixture.state(pl).isShowing = true

        mockConstruction(AdsMobNtFullView::class.java).use { built ->
            val shown = AdsMobPlNtFull::class.java.getDeclaredMethod("showWidthDialog").apply { isAccessible = true }.invoke(pl)

            assertEquals(true, shown)
            val fresh = built.constructed().single()
            val order = inOrder(view, fresh)
            try {
                order.verify(view).destroy()
                order.verify(fresh).setOnDismissListener(any())
                order.verify(fresh).show()
            } catch (e: Throwable) {
                throw AssertionError("view cũ phải huỷ TRƯỚC khi dialog mới show (onCreate gắn cùng ad vào view mới)", e)
            }
        }
    }
}
