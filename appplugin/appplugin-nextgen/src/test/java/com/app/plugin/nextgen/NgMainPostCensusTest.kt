package com.app.plugin.nextgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 2.4.0 NG1 NM2 (Hợp đồng B, luật 36): trong override nextgen (`src/main/java`, cwd = appplugin-nextgen/), mọi
 * `object : <…Callback|Callbacks|Listener>` nằm trong `ads/MainPost.kt` — cửa duy nhất về main; 0 ở file khác.
 * NG5b (D19/D20 m4) tổng quát cho MỌI override: NM3 lớp `class X … : …Callback(s)/Listener` chỉ được đăng ký qua `MainPost.`
 * (dòng có `MainPost.` và `X(`, hoặc biến `val v = X(` đưa vào `MainPost.…(v)`); NM4 0 SAM-lambda listener (`FooListener {`);
 * NM5 mọi điểm đăng ký (`adEventCallback =`, `bannerAdRefreshCallback =`, `videoLifecycleCallbacks =` khác null, `loader(`,
 * `registerNativeAd(`, `XAd.load(`/`NativeAdLoader.load(`, `.show(activity, …`) đi qua `MainPost.` (trừ dòng seam `loader`).
 */
class NgMainPostCensusTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `NM2 override nextgen 0 object Callback ngoai MainPost`() {
        val root = File(OVERRIDES)
        val kt = root.walkTopDown().count { it.isFile && it.extension == "kt" }
        assertTrue("override nextgen chưa có hoặc sai cwd: ${root.absoluteFile} ($kt kt)", kt >= MIN_OVERRIDES)
        assertTrue("MainPost.kt phải có decorator", NgCallbackCensus.sites(root, includeMainPost = true).isNotEmpty())
        assertEquals(emptyList<String>(), NgCallbackCensus.sites(root, includeMainPost = false))
    }

    @Test
    fun `NM2b regex bat object Callback, Callbacks va Listener co package`() {
        val dir = File(tmp.root, "com/app/plugin/ads").apply { mkdirs() }
        File(dir, "X.kt").writeText(
            "val a = object : InterstitialAdEventCallback {}\nval b = object: VideoController.VideoLifecycleCallbacks {}\n" +
                "val c = object : AdListener() {}\nval d = object : Runnable {}\nval f = object : Base(d), BannerAdEventCallback {}\n"
        )
        File(dir, "MainPost.kt").writeText("val e = object : AdLoadCallback<T> {}\n")
        assertEquals(listOf("X.kt:1", "X.kt:2", "X.kt:3", "X.kt:5"), NgCallbackCensus.sites(tmp.root, includeMainPost = false))
    }

    @Test
    fun `NM3-NM5 moi override nextgen - lop Callback, SAM lambda va diem dang ky deu qua MainPost`() {
        val root = File(OVERRIDES)
        assertTrue("sai cwd: ${root.absoluteFile}", root.isDirectory)
        assertTrue("phải thấy lớp Callback để kiểm (≥ 10)", NgCallbackCensus.callbackClasses(root).size >= 10)
        assertEquals(emptyList<String>(), NgCallbackCensus.unregisteredClasses(root))
        assertEquals(emptyList<String>(), NgCallbackCensus.samLambdas(root))
        assertEquals(emptyList<String>(), NgCallbackCensus.directSites(root))
    }

    @Test
    fun `NM3-NM5 regex bat lop chua dang ky, SAM lambda, diem dang ky truc tiep`() {
        val dir = File(tmp.root, "a").apply { mkdirs() }
        File(dir, "A.kt").writeText(
            "private inner class LoadCb(val e: Int) :\n    AdLoadCallback<X> {}\nprivate inner class Ok(val e: Int) : BannerAdEventCallback {}\n" +
                "private class Ui : VideoController.VideoLifecycleCallbacks {}\nfun f() {\n  loader(req, LoadCb(1))\n  ad.adEventCallback = MainPost.bannerEvents(Ok(1))\n" +
                "  val ui = Ui(); vc.videoLifecycleCallbacks = MainPost.video(ui)\n  vc.videoLifecycleCallbacks = null\n  ad.show(activity, OnUserEarnedRewardListener { })\n" +
                "  BannerAd.load(r, cb)\n  setOnClickListener { }\n}\n@Volatile internal var loader: (R, C) -> Unit = { r, cb -> BannerAd.load(r, cb) }\n"
        )
        assertEquals(listOf("A.kt:LoadCb"), NgCallbackCensus.unregisteredClasses(tmp.root))
        assertEquals(listOf("A.kt:10"), NgCallbackCensus.samLambdas(tmp.root))
        assertEquals(listOf("A.kt:6", "A.kt:10", "A.kt:11"), NgCallbackCensus.directSites(tmp.root))
    }

    private companion object {
        const val OVERRIDES = "src/main/java"
        const val MIN_OVERRIDES = 3
    }
}

internal object NgCallbackCensus {
    private val SITE = Regex("""object\s*:[^{\n]*?(Callback|Callbacks|Listener)\b""")
    private val CLASS = Regex("""class\s+(\w+)(?:\([^)]*\))?\s*:([^{]*)\{""")
    private val CB_TYPE = Regex("""\w*(Callback|Callbacks|Listener)\b""")
    private val SAM = Regex("""(?<![\w.])[A-Z]\w*(Callback|Callbacks|Listener)\s*\{""")
    private val REG = Regex(
        """adEventCallback\s*=|bannerAdRefreshCallback\s*=|videoLifecycleCallbacks\s*=(?!\s*null\b)|\bloader\(|registerNativeAd\(|""" +
            """\b(InterstitialAd|AppOpenAd|RewardedAd|RewardedInterstitialAd|BannerAd|NativeAdLoader)\.load\(|\.show\(activity,"""
    )

    private fun files(root: File) = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "MainPost.kt" }.sortedBy { it.path }

    /** Dòng mã (bỏ chú thích `//`, KDoc `*`) kèm số dòng. */
    private fun code(f: File) = f.readLines().withIndex().map { it.index + 1 to it.value.substringBefore("//").trim() }
        .filter { (_, l) -> l.isNotEmpty() && !l.startsWith("*") && !l.startsWith("/*") }

    /** `<file>:<Tên>` của mọi lớp (ngoài MainPost) kế thừa …Callback(s)/Listener. */
    fun callbackClasses(root: File): List<String> = files(root).flatMap { f ->
        CLASS.findAll(f.readText()).filter { CB_TYPE.containsMatchIn(it.groupValues[2]) }.map { "${f.name}:${it.groupValues[1]}" }.toList()
    }.toList()

    /** Lớp Callback không có dòng `MainPost.` nhận `X(` hoặc biến `val v = X(`. */
    fun unregisteredClasses(root: File): List<String> = files(root).flatMap { f ->
        val lines = code(f).map { it.second }
        CLASS.findAll(f.readText()).filter { CB_TYPE.containsMatchIn(it.groupValues[2]) }.map { it.groupValues[1] }.filter { x ->
            val vars = lines.mapNotNull { Regex("""val\s+(\w+)\s*=\s*$x\(""").find(it)?.groupValues?.get(1) }
            lines.none { l -> "MainPost." in l && ("$x(" in l || vars.any { v -> Regex("""\b$v\b""").containsMatchIn(l.substringAfter("MainPost.")) }) }
        }.map { "${f.name}:$it" }.toList()
    }.toList()

    /** Số dòng (1-based) thuộc phần khai báo lớp `class X(…) : … {` (đầu lớp có thể trải nhiều dòng). */
    private fun headerLines(text: String): Set<Int> = CLASS.findAll(text).flatMap { m ->
        val first = text.substring(0, m.range.first).count { it == '\n' } + 1
        (first..first + m.value.count { it == '\n' }).asSequence()
    }.toSet()

    /** `<file>:<dòng>` SAM-lambda listener (`FooListener {`) không bọc `MainPost.` (bỏ dòng khai báo lớp). */
    fun samLambdas(root: File): List<String> = files(root).flatMap { f ->
        val header = headerLines(f.readText())
        code(f).filter { (n, l) -> n !in header && "MainPost." !in l && SAM.containsMatchIn(l) }.map { (n, _) -> "${f.name}:$n" }
    }.toList()

    /** `<file>:<dòng>` điểm đăng ký không đi qua `MainPost.` (trừ dòng seam `@Volatile internal var loader`). */
    fun directSites(root: File): List<String> = files(root).flatMap { f ->
        code(f).filter { (_, l) -> REG.containsMatchIn(l) && "MainPost." !in l && !l.startsWith("@Volatile internal var loader") }
            .map { (n, _) -> "${f.name}:$n" }
    }.toList()

    /** `<tên file>:<dòng>` của mọi `object : …Callback(s)/Listener` dưới [root]; bỏ `MainPost.kt` trừ khi [includeMainPost]. */
    fun sites(root: File, includeMainPost: Boolean): List<String> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" && (includeMainPost || it.name != "MainPost.kt") }
            .sortedBy { it.path }
            .flatMap { f -> f.readLines().withIndex().filter { SITE.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}" } }
            .toList()
}
