package com.app.plugin.ads.core

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mockStatic
import java.util.Collections

/**
 * 1.6.4 F2-MAX: app tiện ích không kèm AppLovin (flavor `utility`) — đường khởi động không được nạp lớp nào của
 * `ads.adsmax` hay `com.applovin`. Cổng thiết bị API 26 bắt được: `AdsNetworkRegistry.build` (inline vào
 * `AdsHelper.initAdsHelper`) đọc `AdsMaxMy.instance` dù `AdsHelper.isMaxSdkAvailable = false` → ART khởi tạo và
 * kiểm lại `AdsMaxMy`, lambda `startNetwork` (implements `AppLovinSdk$SdkInitializationListener`) không phân giải được
 * → "Rejecting re-init on previously-failed class".
 *
 * Mô phỏng "AppLovin vắng mặt" thật: nạp lớp SDK (`com.app.plugin.*`) bằng [AppLovinlessLoader] tự định nghĩa lại
 * từ classpath test và từ chối mọi `com.applovin.*` — nên `AdsHelper.isMaxSdkAvailable` tự ra `false` như trên máy,
 * không cần hook.
 */
class AdsMaxAbsentRegistryTest {

    /** Nạp `com.app.plugin.*` riêng (ghi lại tên), từ chối `com.applovin.*` (ghi lại tên), còn lại nhờ loader cha. */
    private class AppLovinlessLoader(parent: ClassLoader) : ClassLoader(parent) {
        val defined: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val refused: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            findLoadedClass(name)?.let { return it }
            if (name.startsWith("com.applovin.")) {
                refused += name
                throw ClassNotFoundException(name)
            }
            if (!name.startsWith("com.app.plugin.")) return super.loadClass(name, resolve)
            val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
                ?: throw ClassNotFoundException(name)
            defined += name
            defineClass(name, bytes, 0, bytes.size).also { if (resolve) resolveClass(it) }
        }
    }

    private class Run(val loader: AppLovinlessLoader, val registry: Any, val debugLogs: List<String>)

    /** `AdsNetworkRegistry.build()` trong [AppLovinlessLoader], log bật mức debug, ghi mọi `Log.d`. */
    private fun buildWithoutAppLovin(): Run {
        val loader = AppLovinlessLoader(javaClass.classLoader!!)
        val logUtil = Class.forName("com.app.plugin.until.LogUtil", true, loader)
        val logCompanion = logUtil.getField("Companion").get(null)
        logCompanion.javaClass.getMethod("setFlagLog", Int::class.javaPrimitiveType).invoke(logCompanion, 3)
        val logs = Collections.synchronizedList(mutableListOf<String>())
        val registry = mockStatic(Log::class.java) { inv ->
            if (inv.method.name == "d") logs += inv.arguments[1].toString()
            RETURNS_DEFAULTS.answer(inv)
        }.use {
            val cls = Class.forName("com.app.plugin.ads.core.AdsNetworkRegistry", true, loader)
            val companion = cls.getField("Companion").get(null)
            companion.javaClass.getMethod("build").invoke(companion)
        }
        return Run(loader, registry, logs)
    }

    private fun map(registry: Any, getter: String): Map<*, *> = registry.javaClass.getMethod(getter).invoke(registry) as Map<*, *>

    @Test
    fun `AppLovin vang mat - build khong nap lop adsmax nao va khong dung lop AppLovin nao ngoai lan do`() {
        val run = buildWithoutAppLovin()

        val maxClasses = run.loader.defined.filter { it.startsWith("com.app.plugin.ads.adsmax.") }
        assertEquals("AppLovin vắng mặt nhưng đường khởi động nạp lớp MAX: $maxClasses", emptyList<String>(), maxClasses)
        // Chỉ lần dò có mặt (AdsHelper.isMaxSdkAvailable) được hỏi tới AppLovin.
        assertEquals(
            "AppLovin vắng mặt nhưng SDK hỏi lớp AppLovin ngoài lần dò: ${run.loader.refused}",
            setOf("com.applovin.sdk.AppLovinSdk"),
            run.loader.refused.toSet(),
        )
    }

    @Test
    fun `AppLovin vang mat - placement config tro toi MAX bi bo qua co log ly do, khong nem`() {
        val run = buildWithoutAppLovin()

        assertNotNull(run.registry)
        // Loại MAX (AdsIDDef: 6 cho banner/NtCl/rect/native/rewarded/inters, 60 native full, 600 open) không có mạng:
        // controller tra `h.*Networks()[type]` ra null và bỏ qua (không có đối tượng MAX nào để gọi).
        listOf("getBanners", "getNtCls", "getRects", "getNatives", "getGifts").forEach { g ->
            assertTrue("$g có mạng MAX: ${map(run.registry, g)}", 6 !in map(run.registry, g).keys)
        }
        assertTrue("getFulls có mạng MAX", map(run.registry, "getFulls").keys.none { it in setOf(6, 60, 600) })
        val reason = run.debugLogs.filter { it.startsWith("ads max:") }
        assertEquals(
            "thiếu log lý do bỏ mạng MAX khi AppLovin vắng mặt; log debug: ${run.debugLogs}",
            listOf("ads max: không có SDK AppLovin, không đăng ký mạng MAX (loại 6/60/600): placement config trỏ tới MAX bị bỏ qua"),
            reason,
        )
    }
}
