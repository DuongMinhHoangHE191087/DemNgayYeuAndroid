package com.app.plugin.nextgen

import android.content.Context
import com.app.plugin.ads.MainFront
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobMy
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.concurrent.Executor

/**
 * 2.4.4 INITCB (Hợp đồng C, quyết định (c)) — Next-Gen: callback init đến từ luồng nền ⇒ hàng load đang giữ đi qua
 * `MainFront` (message async đầu hàng main), không `MainPost.poster` (cuối hàng, sau khung hình); chạy đúng một lần.
 */
class NgInitFrontTest {
    private val frontPosted = ArrayList<Runnable>()
    private val tailPosted = ArrayList<Runnable>()
    private val logs = ArrayList<String>()
    private var listener: OnAdapterInitializationCompleteListener? = null

    @After fun tearDown() {
        MainFront.resetForTest()
        MainPost.resetForTest()
    }

    private fun my(onMain: Boolean) = AdsMobMy().apply {
        initExecutor = Executor { it.run() }
        scheduleTimeout = { _, _ -> }
        initializer = { _, _, l -> listener = l }
        appIdReader = { "ca-app-pub-0000000000000000~0000000000" }
        testDeviceIds = { emptyList() }
        logSink = { logs += it }
        MainPost.isMain = { onMain }
        MainPost.poster = { r -> tailPosted += r }
        MainFront.isMain = { onMain }
        MainFront.poster = { r -> frontPosted += r }
    }

    @Test fun `NIC1 init xong tu luong nen khong dung MainPost poster cuoi hang, hang chay dung mot lan qua MainFront`() {
        val m = my(onMain = false)
        val ran = ArrayList<Int>()
        repeat(3) { i -> m.runWhenSdkReady { ran += i } }
        m.startNetwork(mock(Context::class.java))
        listener!!.onAdapterInitializationComplete(mock(InitializationStatus::class.java))
        assertEquals("drain sau init phải đi message async đầu hàng, không MainPost.poster (cuối hàng, sau frame)", 0, tailPosted.size)
        assertEquals("đúng một message đầu hàng", 1, frontPosted.size)
        assertEquals("chưa chạy trên luồng SDK", emptyList<Int>(), ran)
        listener!!.onAdapterInitializationComplete(mock(InitializationStatus::class.java))   // callback lặp: không thêm
        frontPosted.toList().forEach { it.run() }
        assertEquals(listOf(0, 1, 2), ran)
        assertEquals(listOf("gma: init done, run 3"), logs.filter { it.startsWith("gma: init done") })
        assertTrue(m.isSdkInitialized)
    }

    @Test fun `NIC2 callback init dang o main thi chay hang tai cho, 0 post`() {
        val m = my(onMain = true)
        val ran = ArrayList<Int>()
        m.runWhenSdkReady { ran += 7 }
        m.startNetwork(mock(Context::class.java))
        listener!!.onAdapterInitializationComplete(mock(InitializationStatus::class.java))
        assertEquals(listOf(7), ran)
        assertEquals(listOf(0, 0), listOf(frontPosted.size, tailPosted.size))
    }
}
