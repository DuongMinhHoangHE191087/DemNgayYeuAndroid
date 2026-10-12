package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.app.plugin.ads.adsfb.AdsFbNtFullView
import com.app.plugin.ads.adsfb.AdsFbPlNtFull
import com.app.plugin.ads.adsmax.AdsMaxNtFullView
import com.app.plugin.ads.adsmax.AdsMaxPlNtFull
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Task 7a, vòng 2 (đo 2026-09-24 trên emulator API 37):
 * - onAdDismissed khi host chết kích hoạt load lại Full2 (AdsHelper.loadFull2ForThisTurn). Nếu
 *   mActivity còn trỏ vào Activity đang huỷ, AdMob dựng AdLoader trên Activity đó. mActivity phải
 *   về null TRƯỚC khi phát dismiss.
 * - parentBlackSafeAreaView (gắn bằng addContentView vào host) giữ Activity đầu tiên cho tới khi
 *   process chết (heap: AdsMobPlNtFull.parentBlackSafeAreaView → RelativeLayout.mContext). Phải
 *   được gỡ khỏi host và bỏ tham chiếu mỗi khi host chết, kể cả lúc không có dialog nào đang hiện.
 * Cùng một hợp đồng cho AdMob, Meta và MAX.
 *
 * Fix wave 1 (review Task 7a):
 * - Dialog.dismiss() gửi message DISMISS mang theo listener đang gắn; setOnDismissListener(null) sau
 *   đó không thu hồi message đã gửi. Người dùng bấm đóng rồi host bị huỷ trước khi message chạy thì
 *   listener cũ vẫn tới SAU clearActivity: vẫn chỉ được có một onAdDismissed cho lượt show đó.
 * - Dialog dựng sẵn (prepareChainedShow) giữ Activity đã tạo nó: phải được nhả khi host chết kể cả
 *   lúc không có dialog nào đang hiện.
 */
class NtFullHostDestroyTest {

    private class Case(
        val name: String, val pl: AdsPlBase, val dialog: Dialog, val ad: Any,
        val setDialog: (Dialog?) -> Unit, val getDialog: () -> Dialog?, val cancelCountdown: (Dialog) -> Unit,
    )

    private fun cases(): List<Case> {
        val context = mock(Context::class.java)
        val base = mock(AdsBase::class.java)
        val mob = AdsMobPlNtFull(context, base, AdsIDDef.AdmobFullNt, "full_2_default")
        val fb = AdsFbPlNtFull(context, base, AdsIDDef.FBFullNt, "full_2_default")
        val max = AdsMaxPlNtFull(context, base, AdsIDDef.MaxFullNt, "full_2_default")
        return listOf(
            Case("admob", mob, mock(AdsMobNtFullView::class.java), mock(com.google.android.gms.ads.nativead.NativeAd::class.java),
                { mob.mDialogNativeFull = it as AdsMobNtFullView? }, { mob.mDialogNativeFull },
                { (it as AdsMobNtFullView).cancelCountdown() }),
            Case("meta", fb, mock(AdsFbNtFullView::class.java), mock(com.facebook.ads.NativeAd::class.java),
                { fb.mDialogNativeFull = it as AdsFbNtFullView? }, { fb.mDialogNativeFull },
                { (it as AdsFbNtFullView).cancelCountdown() }),
            Case("max", max, mock(AdsMaxNtFullView::class.java), mock(com.applovin.mediation.MaxAd::class.java),
                { max.mDialogNativeFull = it as AdsMaxNtFullView? }, { max.mDialogNativeFull },
                { (it as AdsMaxNtFullView).cancelCountdown() }),
        )
    }

    /**
     * Chạy cho cả ba mạng, gom lỗi của từng mạng rồi fail một lần: mạng đầu lỗi không che mạng sau.
     * Throwable gốc của từng mạng đi kèm dưới dạng suppressed, nên stack trace không mất.
     */
    private fun forAllNetworks(body: (Case) -> Unit) {
        val failures = cases().mapNotNull { c ->
            try {
                body(c)
                null
            } catch (e: Throwable) {
                c.name to ((e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e)
            }
        }
        if (failures.isEmpty()) return
        val message = failures.joinToString("\n", prefix = "${failures.size} mạng lỗi:\n") { (name, cause) ->
            "$name: ${cause.javaClass.simpleName}: ${cause.message}"
        }
        throw AssertionError(message).apply { failures.forEach { (_, cause) -> addSuppressed(cause) } }
    }

    /** Đếm onAdDismissed của lượt show hiện tại. */
    private fun recordDismisses(pl: AdsPlBase): MutableList<String?> {
        val dismissed = mutableListOf<String?>()
        showCbSlot(pl).adsShowCb = object : AdsShowCb {
            override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { dismissed += placement }
        }
        return dismissed
    }

    /**
     * Đưa placement vào trạng thái "dialog đang hiện" qua đúng đường show thật (showWidthDialog dùng
     * lại dialog đã dựng sẵn cho cùng quảng cáo và cùng Activity), trả về listener dismiss mà production
     * gắn vào dialog.
     */
    private fun showViaProduction(c: Case): DialogInterface.OnDismissListener {
        val host = mock(Activity::class.java)
        c.pl.setActivity(host)
        ownField(c.pl, "nativeAdShow").set(c.pl, c.ad)
        ownField(c.pl, "preparedDialog").set(c.pl, c.dialog)
        ownField(c.pl, "preparedNativeAd").set(c.pl, c.ad)
        ownField(c.pl, "preparedActivity").set(c.pl, host)
        showCbSlot(c.pl).isShowing = true
        val shown = c.pl.javaClass.getDeclaredMethod("showWidthDialog").apply { isAccessible = true }.invoke(c.pl)
        assertEquals("dialog phải hiện", true, shown)
        val listener = ArgumentCaptor.forClass(DialogInterface.OnDismissListener::class.java)
        verify(c.dialog).setOnDismissListener(listener.capture())
        return listener.value
    }

    private fun baseField(name: String) =
        AdsPlBase::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun ownField(pl: AdsPlBase, name: String) =
        pl.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private fun showCbSlot(pl: AdsPlBase) = baseField("adsPlacementEcmp").get(pl) as AdsPlacementEcmpBase

    /** Trường lớp phủ + chủ của nó: AdMob giữ trong `background` (AdsMobNtFullBackground, 2.0.0 C1a), Meta/MAX trên placement. */
    private fun overlaySlot(pl: AdsPlBase): Pair<java.lang.reflect.Field, Any> {
        val owner = pl.javaClass.declaredFields.firstOrNull { it.name == "background" }?.apply { isAccessible = true }?.get(pl) ?: pl
        return owner.javaClass.getDeclaredField("parentBlackSafeAreaView").apply { isAccessible = true } to owner
    }

    /** Lớp phủ safe-area đang gắn trong content của host, như sau addBg4Ad. */
    private fun attachOverlay(pl: AdsPlBase): Pair<RelativeLayout, ViewGroup> {
        val overlay = mock(RelativeLayout::class.java)
        val content = mock(ViewGroup::class.java)
        `when`(overlay.parent).thenReturn(content)
        overlaySlot(pl).let { (f, o) -> f.set(o, overlay) }
        return overlay to content
    }

    @Test
    fun `host chet khi dialog dang hien - mActivity ve null truoc dismiss, dung mot dismiss, nha lop phu safe-area`() {
        forAllNetworks { c ->
            c.pl.setActivity(mock(Activity::class.java))
            c.setDialog(c.dialog)
            val (overlay, content) = attachOverlay(c.pl)
            val activityAtDismiss = mutableListOf<Any?>()
            showCbSlot(c.pl).isShowing = true
            showCbSlot(c.pl).adsShowCb = object : AdsShowCb {
                override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) {
                    activityAtDismiss += baseField("mActivity").get(c.pl)
                }
            }

            c.pl.clearActivity()

            assertEquals("${c.name}: đúng một dismiss, phát khi mActivity đã null", listOf<Any?>(null), activityAtDismiss)
            verify(c.dialog).setOnDismissListener(null)
            verify(c.dialog).dismiss()
            assertNull("${c.name}: dialog phải được bỏ", c.getDialog())
            assertEquals("${c.name}: isShowing phải về false", false, showCbSlot(c.pl).isShowing)
            verify(content).removeView(overlay)
            assertNull("${c.name}: lớp phủ safe-area phải được bỏ tham chiếu", overlaySlot(c.pl).let { (f, o) -> f.get(o) })
        }
    }

    @Test
    fun `host chet khi khong co dialog - khong phat dismiss nhung van nha lop phu safe-area va dialog dung san`() {
        forAllNetworks { c ->
            val host = mock(Activity::class.java)
            c.pl.setActivity(host)
            c.setDialog(null)
            val (overlay, content) = attachOverlay(c.pl)
            // Dialog dựng sẵn sau lượt quảng cáo cha, chưa kịp hiện thì host chết.
            val prepared = mock(c.dialog.javaClass)
            ownField(c.pl, "preparedDialog").set(c.pl, prepared)
            ownField(c.pl, "preparedNativeAd").set(c.pl, c.ad)
            ownField(c.pl, "preparedActivity").set(c.pl, host)
            var dismissed = 0
            showCbSlot(c.pl).adsShowCb = object : AdsShowCb {
                override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { dismissed++ }
            }

            c.pl.clearActivity()

            assertEquals("${c.name}: không có dialog thì không có dismiss", 0, dismissed)
            assertNull("${c.name}: mActivity phải về null", baseField("mActivity").get(c.pl))
            verify(content).removeView(overlay)
            assertNull("${c.name}: lớp phủ safe-area phải được bỏ tham chiếu", overlaySlot(c.pl).let { (f, o) -> f.get(o) })
            assertNull("dialog dựng sẵn phải được bỏ", ownField(c.pl, "preparedDialog").get(c.pl))
            assertNull("dialog dựng sẵn không được giữ Activity đã chết", ownField(c.pl, "preparedActivity").get(c.pl))
            assertNull("dialog dựng sẵn không được giữ quảng cáo", ownField(c.pl, "preparedNativeAd").get(c.pl))
            c.cancelCountdown(verify(prepared))
        }
    }

    @Test
    fun `clearActivity goi hai lan - van dung mot onAdDismissed`() {
        forAllNetworks { c ->
            val dismissed = recordDismisses(c.pl)
            showViaProduction(c)

            c.pl.clearActivity()
            c.pl.clearActivity()

            assertEquals("đúng một dismiss", 1, dismissed.size)
            verify(c.dialog).dismiss()
        }
    }

    @Test
    fun `listener dismiss cu toi sau clearActivity - van dung mot onAdDismissed`() {
        forAllNetworks { c ->
            val dismissed = recordDismisses(c.pl)
            // Người dùng bấm đóng: message DISMISS mang listener này đã gửi nhưng chưa chạy.
            val staleListener = showViaProduction(c)

            c.pl.clearActivity()
            staleListener.onDismiss(c.dialog)

            assertEquals("đúng một dismiss", 1, dismissed.size)
            assertNull("dialog phải được bỏ", c.getDialog())
            assertEquals("isShowing phải về false", false, showCbSlot(c.pl).isShowing)
        }
    }

    @Test
    fun `dong binh thuong roi host chet - dung mot onAdDismissed, tu listener`() {
        forAllNetworks { c ->
            val dismissed = recordDismisses(c.pl)
            val listener = showViaProduction(c)

            listener.onDismiss(c.dialog)
            assertEquals("listener phải phát dismiss khi người dùng đóng", 1, dismissed.size)
            assertNull("dialog phải được bỏ", c.getDialog())
            assertEquals("isShowing phải về false", false, showCbSlot(c.pl).isShowing)
            c.cancelCountdown(verify(c.dialog))

            c.pl.clearActivity()

            assertEquals("host chết sau khi đã đóng không phát thêm dismiss", 1, dismissed.size)
        }
    }
}
