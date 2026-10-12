package com.app.plugin.ads

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Collections

/**
 * 2.0.0 C1c (D1, plan §Hợp đồng C): core gọi MAX chỉ qua seam internal [MaxBridge]; impl
 * `MaxBridgeImpl` (gói `ads.adsmax`) dò bằng tên cố định đúng một lần, chỉ khi [AdsHelper.isMaxSdkAvailable].
 */
class MaxBridgeTest {

    @Before fun setUp() = MaxBridge.resetProbeForTest()

    @After fun tearDown() = MaxBridge.resetProbeForTest()

    /** Nạp `com.app.plugin.*` riêng (ghi lại tên), từ chối `com.applovin.*`: "AppLovin vắng mặt" thật, như
     * `AdsMaxAbsentRegistryTest` — `AdsHelper.isMaxSdkAvailable` tự ra `false` như trên máy, không hook. */
    private class AppLovinlessLoader(parent: ClassLoader) : ClassLoader(parent) {
        val defined: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            findLoadedClass(name)?.let { return it }
            if (name.startsWith("com.applovin.")) throw ClassNotFoundException(name)
            if (!name.startsWith("com.app.plugin.")) return super.loadClass(name, resolve)
            val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
                ?: throw ClassNotFoundException(name)
            defined += name
            defineClass(name, bytes, 0, bytes.size).also { if (resolve) resolveClass(it) }
        }
    }

    @Test
    fun `M2 khong co SDK AppLovin - bridge null va khong nap lop adsmax nao (F2-MAX)`() {
        val loader = AppLovinlessLoader(javaClass.classLoader!!)
        val holder = Class.forName("com.app.plugin.ads.MaxBridge", true, loader).getField("Companion").get(null)
        val get = holder.javaClass.getMethod("get")
        assertSame("seam phải nạp trong loader không có AppLovin", loader, holder.javaClass.classLoader)

        assertNull("không có AppLovin thì bridge phải null", get.invoke(holder))
        assertNull(get.invoke(holder))
        val maxClasses = loader.defined.filter { it.startsWith("com.app.plugin.ads.adsmax.") }
        assertEquals("AppLovin vắng mặt nhưng seam nạp lớp MAX: $maxClasses", emptyList<String>(), maxClasses)
    }

    @Test
    fun `M3 co lop impl - do dung mot lan va giu mot doi tuong, lop vang - null, cung chi do mot lan`() {
        assertTrue("classpath test phải có SDK AppLovin", AdsHelper.isMaxSdkAvailable)
        val probes = mutableListOf<String>()
        MaxBridge.classFor = { probes += it; Class.forName(it) }

        val first = MaxBridge.get()
        repeat(3) { assertSame(first, MaxBridge.get()) }
        assertEquals(MaxBridge.IMPL_CLASS, first?.javaClass?.name)
        assertEquals("dò impl đúng một lần mỗi process", listOf(MaxBridge.IMPL_CLASS), probes)

        MaxBridge.resetProbeForTest()
        val missing = mutableListOf<String>()
        MaxBridge.classFor = { missing += it; throw ClassNotFoundException(it) }
        repeat(3) { assertNull(MaxBridge.get()) }
        assertEquals("lớp vắng: dò một lần, không ném", listOf(MaxBridge.IMPL_CLASS), missing)
        // R8 không thấy Class.forName(tên cố định): thiếu keep rule thì bản release xoá/đổi tên impl, dò ra null.
        val keep = "-keep class ${MaxBridge.IMPL_CLASS} { <init>(); }"
        assertTrue("consumer-rules.pro thiếu đúng dòng '$keep'", File("consumer-rules.pro").readLines().any { it.trim() == keep })
    }

    @Test
    fun `M1 census - ngoai adsmax khong file nao tham chieu goi ads_adsmax tru chuoi do`() {
        val root = File("src/main/java")
        assertTrue("Không thấy ${root.absolutePath}: sai thư mục làm việc, census sẽ xanh rỗng", root.isDirectory)
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/ads/adsmax/") }
            .toList()
        assertTrue("Chỉ quét ${files.size} file .kt — sai gốc?", files.size >= MIN_FILES)
        val hits = files.flatMap { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            f.readLines().mapIndexedNotNull { i, line ->
                val rest = if (rel.endsWith(PROBE_FILE)) line.replace(PROBE_LITERAL, "") else line
                if (rest.contains("ads.adsmax.")) "$rel:${i + 1}" else null
            }
        }
        val offenders = hits.map { it.substringBefore(':') }.distinct()
        assertEquals("file ngoài adsmax/ tham chiếu ads.adsmax. (${offenders.size}): $hits", emptyList<String>(), offenders)
    }

    private companion object {
        const val MIN_FILES = 150
        const val PROBE_FILE = "ads/MaxBridge.kt"
        const val PROBE_LITERAL = "\"com.app.plugin.ads.adsmax.MaxBridgeImpl\""
    }
}
