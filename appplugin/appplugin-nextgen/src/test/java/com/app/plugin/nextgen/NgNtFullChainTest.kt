package com.app.plugin.nextgen

import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobNtFullView
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.File

/**
 * 2.4.0 NG5b (Hợp đồng B "NG5b", luật 36, R3): chuỗi Full2 AdMob trên GMA Next-Gen với callback nền. Mọi callback của
 * SDK vào hàng main qua [MainPost]; `prepareChainedShow` dựng dialog bằng MỘT lần post nữa ([MainPost.post]) nên chạy SAU
 * mọi callback nền đã xếp trước nó (FIFO) và xét lại trạng thái lúc dựng. NC1 dismiss cha (nền) → dialog Full2 tạo sau
 * handler dismiss, đúng một; NC2 0 chạm quảng cáo/view trước khi hàng main xả; NC3 host huỷ giữa dismiss và prepare →
 * không dialog, không gì giữ host/quảng cáo. NC4 (census = 0, 0 play-services-ads) ở NgLegacyCensusTest + lệnh trong report.
 */
class NgNtFullChainTest {
    private val kit = NgNtFullKit().install()

    @After fun tearDown() = kit.uninstall()

    private fun <T> dialogs(block: (MockedConstruction<AdsMobNtFullView>) -> T): T =
        mockConstruction(AdsMobNtFullView::class.java) { _, _ -> kit.events += "dialog" }.use(block)

    /** Callback sự kiện của quảng cáo cha (interstitial Next-Gen) đã bọc [MainPost]; handler dismiss = [onDismiss]. */
    private fun parent(onDismiss: () -> Unit): InterstitialAdEventCallback =
        MainPost.interstitialEvents(object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() { kit.events += "dismiss"; onDismiss() }
        })

    @Test fun `NC1 dismiss cha tu luong nen - dialog Full2 tao sau handler dismiss tren main, FIFO, dung mot`() = dialogs { built ->
        kit.loaded()
        parent { kit.pl.show("full_2_default", kit.showCb, 0) }.onAdDismissedFullScreenContent() // luồng nền: xếp hàng
        kit.pl.prepareChainedShow() // handler prepareChainedFull2 của Full2Chain chạy trên main trong lúc dismiss đang chờ
        assertEquals("chưa xả: chưa dismiss, chưa dialog", emptyList<String>(), kit.events)
        kit.drain()
        assertEquals(listOf("dismiss", "dialog", "showed"), kit.events)
        assertEquals("đúng một dialog", 1, built.constructed().size)
        verify(built.constructed()[0], times(1)).show()
        assertNull("prepare sau show không dựng gì thêm", kit.own("preparedDialog").get(kit.pl))
    }

    @Test fun `NC2 load nen va prepare - 0 cham quang cao hay view truoc khi poster xa, roi dung mot dialog dung san`() = dialogs { built ->
        val ad = kit.loadFromBackground()
        kit.pl.prepareChainedShow()
        assertEquals("0 lời gọi trên quảng cáo trước khi main chạy", 0, mockingDetails(ad).invocations.size)
        assertEquals("0 dialog trước khi main chạy", 0, built.constructed().size)
        assertTrue("chưa nạp trước khi main chạy", kit.slot().adEcmps!!.list.none { it.isLoaded })
        kit.drain()
        assertEquals(1, built.constructed().size)
        verify(built.constructed()[0]).create()
        assertEquals(built.constructed()[0], kit.own("preparedDialog").get(kit.pl))
    }

    @Test fun `NC3 host huy giua dismiss va prepare - khong dialog, khong giu host hay quang cao`() = dialogs { built ->
        val ad: NativeAd = kit.loaded()
        parent { /* showFull2 xếp chờ host resume: không dùng quảng cáo */ }.onAdDismissedFullScreenContent()
        kit.pl.prepareChainedShow()
        `when`(kit.host.isDestroyed).thenReturn(true) // host chết sau khi dismiss/prepare đã xếp hàng
        kit.drain()
        assertEquals(listOf("dismiss"), kit.events)
        assertEquals("không dựng dialog trên host đã huỷ", 0, built.constructed().size)
        assertNull(kit.own("preparedDialog").get(kit.pl))
        assertNull(kit.own("preparedActivity").get(kit.pl))
        assertNull(kit.own("preparedNativeAd").get(kit.pl))
        verify(ad, never()).destroy() // quảng cáo giữ cho host kế (như T1 Legacy)
    }

    @Test fun `NC3b clearActivity truoc khi prepare chay - khong dialog, prepare cu bi bo`() = dialogs { built ->
        kit.loaded()
        kit.pl.prepareChainedShow()
        kit.pl.clearActivity()
        kit.drain()
        assertEquals(0, built.constructed().size)
        assertNull(kit.own("preparedDialog").get(kit.pl))
    }

    @Test fun `NC3c cancelChainedShow truoc khi prepare chay - prepare cu khong dung dialog`() = dialogs { built ->
        kit.loaded()
        kit.pl.prepareChainedShow()
        kit.pl.cancelChainedShow()
        kit.drain()
        assertEquals("chuỗi đã huỷ: không dựng dialog muộn", 0, built.constructed().size)
    }

    @Test fun `NC4 0 file Legacy trong nguon nextgen, build het chuyen tiep Legacy, loai play-services-ads tren cau hinh consumer`() {
        assertEquals(emptySet<String>(), NgCensus.expected(File("legacy-remaining.txt")))
        assertEquals(emptySet<String>(), NgCensus.legacyFiles(listOf(File("src/main/java"), File("build/generated/appplugin-shared/java"))))
        val gradle = File("build.gradle.kts")
        val legacyDeps = gradle.readLines().map { it.substringBefore("//").trim() }
            .filter { Regex("""^\w+\("com\.google\.android\.gms:play-services-ads""").containsMatchIn(it) }
        assertEquals("không còn phụ thuộc GMA Legacy nào (compileOnly/testImplementation chuyển tiếp đã cắt)", emptyList<String>(), legacyDeps)
        val text = gradle.readText()
        assertTrue("loại play-services-ads(-lite) trên api/implementation/runtimeOnly (D11)",
            "listOf(\"api\", \"implementation\", \"runtimeOnly\")" in text && "module = \"play-services-ads-lite\")" in text)
    }

    @Test fun `nap Full2 qua NativeAdLoader voi VideoOptions dieu khien rieng, co tieng (NG5a) va host chet khi nap thi huy`() {
        val ad = kit.loadFromBackground()
        val req = kit.requests.single().first
        assertEquals(listOf(NativeAd.NativeAdType.NATIVE), req.nativeAdTypes)
        assertTrue(req.videoOptions?.customControlsRequested == true && req.videoOptions?.startMuted == false)
        `when`(kit.host.isDestroyed).thenReturn(true)
        kit.drain()
        verify(ad).destroy()
        assertTrue(kit.slot().adEcmps!!.list.none { it.isLoaded || it.adObject != null })
    }
}
