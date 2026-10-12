package com.app.plugin.ads

import android.content.Context
import com.app.plugin.consent.GppSignals
import com.app.plugin.consent.contextWithTcf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * 2.1.0 P2 (M14, R-C1c m1): mỗi trong ba lời gọi core → MAX qua seam [MaxBridge] có một test đỏ khi bị xoá (trước đây
 * xoá bất kỳ lời gọi nào cũng sống sót): `followGpp` (`AdSdkStartup`), lambda start mặc định (`NetworkStarters.max`),
 * `initAd` (`AdsHelper.initAdsHelper`). Impl MAX thật được thay bằng [FakeBridge] qua `MaxBridge.classFor`.
 */
class MaxBridgeCallsTest {
    internal class FakeBridge : MaxBridge {
        override fun initAd(context: Context) { calls += "initAd" }
        override fun startNetwork(context: Context) { calls += "startNetwork" }
        override fun followGpp(context: Context, gpp: GppSignals?) { calls += "followGpp" }
        override fun resetForTest() {}
        override fun getAdnetName(adnet: String?): String = adnet.orEmpty()
        override fun networks(): MaxBridge.Networks = mock(MaxBridge.Networks::class.java)
    }

    @Before fun setUp() {
        MaxBridge.resetProbeForTest()
        calls.clear()
        MaxBridge.classFor = { FakeBridge::class.java }
    }

    @After fun tearDown() = MaxBridge.resetProbeForTest()

    @Test
    fun `MX1 GPP giua phien toi MAX qua followGpp`() {
        val gpp = GppSignals(listOf(7), true, true, true, null, "raw")
        AdSdkStartup.javaClass.getDeclaredMethod("applyGppToStartedNetworks", Context::class.java, GppSignals::class.java)
            .apply { isAccessible = true }.invoke(AdSdkStartup, mock(Context::class.java), gpp)
        assertEquals(listOf("followGpp"), calls)
    }

    @Test
    fun `MX2 lambda start mac dinh cua MAX goi startNetwork qua seam`() {
        AdSdkStartup.NetworkStarters().max(mock(Context::class.java))
        assertEquals(listOf("startNetwork"), calls)
    }

    /** Nạp riêng mọi `com.app.plugin.*` (kể cả lớp test này): `initAdsHelper` gán singleton toàn cục, không để lọt sang test khác. */
    private class IsolatedLoader(parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            findLoadedClass(name)?.let { return it }
            if (!name.startsWith("com.app.plugin.")) return super.loadClass(name, resolve)
            val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
                ?: throw ClassNotFoundException(name)
            defineClass(name, bytes, 0, bytes.size).also { if (resolve) resolveClass(it) }
        }
    }

    /** Chạy trong [IsolatedLoader]: cài [FakeBridge], gọi `initAdsHelper`, trả các lời gọi tới seam. */
    class Mx3Probe : java.util.function.Supplier<List<String>> {
        override fun get(): List<String> {
            MaxBridge.resetProbeForTest()
            MaxBridge.classFor = { FakeBridge::class.java }
            AdsHelper.instance.initAdsHelper(contextWithTcf(gdpr = 0, purposes = "1111111111"))
            return calls.toList()
        }
    }

    @Test
    fun `MX3 initAdsHelper dung doi tuong MAX qua initAd`() {
        val loader = IsolatedLoader(javaClass.classLoader!!)
        @Suppress("UNCHECKED_CAST")
        val probe = Class.forName(Mx3Probe::class.java.name, true, loader).getDeclaredConstructor().newInstance() as java.util.function.Supplier<List<String>>
        assertEquals(listOf("initAd"), probe.get())
    }

    private companion object {
        val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
    }
}
