package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hợp đồng tên event (Hiến pháp Điều 9.2, DOC/65 G6): tên ≤ 40 ký tự, `^[a-z][a-z0-9_]{0,39}$`, không tiền tố
 * `firebase_`/`google_`/`ga_`. Firebase bỏ event sai tên mà không báo (DUC-RUT §2.1), nên mọi vi phạm được liệt
 * kê theo tên, file và dòng.
 *
 * Tên v2 IAP/paywall của 1.8.0 Task 7 (`iap_result`, `paywall_skip`, `paywall_choice`) cũng là `internal val` trong
 * `EventNames.kt` nên nằm trong tập này; hợp đồng key của chúng: `IapEventContractTest`.
 *
 * Tập được khoá: mọi hằng trong `EventNames.kt`, và tên ghép của placement (`EventNames.adsPlActivityNull`
 * với mọi `eventTag`). Chỗ gọi `logEvent`/`logEventToServer` không được viết literal, để tên mới nào cũng đi
 * qua `EventNames` và rơi vào tập này. Tên ghép lúc chạy từ chuỗi của app (`aad_*`, `show_ads_*` của
 * `AdsHelper`) nằm ngoài tầm kiểm tĩnh. Param của `sdk_health`: `SdkHealthTest`. Giới hạn (follow-up, DOC/65
 * G6): tên đi qua một biến (`val eventName = "…"; logEvent(eventName)`) lọt qua cả hai phép quét chỗ gọi;
 * `fir_rm_fet_*` của `FirHelperConfig` từng như vậy, nay nằm trong `EventNames` (D4) và có phép quét riêng.
 */
class EventNameContractTest {
    private val mainSrc = File("src/main/java")
    private val eventNamesFile = File("src/main/java/com/app/plugin/logevent/EventNames.kt")
    private val adsDir = File("src/main/java/com/app/plugin/ads")

    private val snakeCase = Regex("^[a-z][a-z0-9_]{0,39}$")
    private val bannedPrefixes = listOf("firebase_", "google_", "ga_")

    /**
     * Tên không theo snake_case, **đóng băng**: Google Ads nhập các mốc tROAS này làm conversion theo đúng tên, đổi là
     * tạo conversion action mới không có lịch sử và chiến dịch tROAS phải học lại (quyết định #5, DOC/66,
     * 2026-09-25). Chúng chỉ phải hợp lệ với Firebase (cho chữ hoa). Tập đóng: thêm hay bớt tên ở đây là sửa hợp
     * đồng, phải có quyết định; nếu có ngày phải đổi thì bắn cả tên cũ và mới ít nhất 30 ngày trước khi bỏ tên cũ.
     */
    private val legacyNotSnakeCase = setOf(
        "AdLTV_OneDay_Top50Percent",
        "AdLTV_OneDay_Top40Percent",
        "AdLTV_OneDay_Top30Percent",
        "AdLTV_OneDay_Top20Percent",
        "AdLTV_OneDay_Top10Percent",
    )
    private val firebaseName = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")

    private fun breaksContract(name: String) = !snakeCase.matches(name) || bannedPrefixes.any { name.startsWith(it) }

    /** Chạy sai thư mục làm việc thì mọi phép quét rỗng và test PASS giả: dừng ngay. */
    private fun kotlinFiles(dir: File): List<File> {
        assertTrue("Không thấy thư mục mã nguồn ${dir.absolutePath} (test phải chạy từ gốc module)", dir.isDirectory)
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun lineOf(text: String, index: Int) = text.substring(0, index).count { it == '\n' } + 1

    private fun where(f: File, text: String, index: Int) = "${f.relativeTo(mainSrc)}:${lineOf(text, index)}"

    /**
     * Tên hằng → giá trị, của mọi `const val` và `internal val` (tên v2 của 1.7.0 Task 1: không `const` vì façade API)
     * trong `EventNames.kt`. Hằng không phải literal chuỗi thì báo lỗi.
     */
    private fun eventNameConstants(): Map<String, String> {
        val text = eventNamesFile.readText()
        val consts = Regex("""(?:const|internal) val (\w+)\s*=\s*(.+)""").findAll(text).toList()
        val notLiteral = consts.filterNot { Regex("^\"[^\"$\\\\]*\"$").matches(it.groupValues[2].trim()) }
        assertTrue(
            "Hằng trong EventNames phải là literal chuỗi (test đọc giá trị từ mã nguồn):\n" +
                notLiteral.joinToString("\n") { "${where(eventNamesFile, text, it.range.first)}: ${it.value.trim()}" },
            notLiteral.isEmpty()
        )
        return consts.associate { it.groupValues[1] to it.groupValues[2].trim().removeSurrounding("\"") }
    }

    /** Tự kiểm của [breaksContract]: nó sai thì mọi test dưới đây PASS giả (Task 17, review Task 14b). */
    @Test
    fun `breaksContract bat dung ten sai hop dong`() {
        val wrong = listOf(
            "reloadSplash_acnull",           // camelCase
            "firebase_custom", "google_x", "ga_x",   // tiền tố cấm
            "a".repeat(41),                  // 41 ký tự
            "1st_open", "_x", "ad-show", "",
        ).filterNot(::breaksContract)
        assertTrue("breaksContract bỏ lọt: $wrong", wrong.isEmpty())
        val right = listOf("ad_show", "a".repeat(40), "reload_splash_ac_null", "x1").filter(::breaksContract)
        assertTrue("breaksContract bắt oan: $right", right.isEmpty())
    }

    @Test
    fun `moi ten trong EventNames dung hop dong`() {
        val names = eventNameConstants()
        assertTrue("EventNames không có hằng nào", names.isNotEmpty())
        val bad = names.filter { (_, v) -> v !in legacyNotSnakeCase && breaksContract(v) }
        assertTrue(
            "Tên event sai hợp đồng Điều 9.2:\n" + bad.entries.joinToString("\n") { "EventNames.${it.key} = \"${it.value}\"" },
            bad.isEmpty()
        )
    }

    @Test
    fun `ngoai le dong bang la tap dong va hop le voi Firebase`() {
        val names = eventNameConstants().values
        assertEquals(legacyNotSnakeCase, names.filterNot { snakeCase.matches(it) }.toSet())
        val bad = legacyNotSnakeCase.filter { !firebaseName.matches(it) || bannedPrefixes.any { p -> it.startsWith(p) } }
        assertTrue("Tên cũ không hợp lệ với Firebase: $bad", bad.isEmpty())
    }

    /**
     * #5: từng hằng giữ đúng giá trị của nó, từng ký tự. Phép so tập ở trên không bắt được hai hằng đổi giá trị cho
     * nhau (mốc 50 % bắn tên của mốc 40 %: conversion Google Ads sai mà không gãy gì).
     */
    @Test
    fun `5 hang AdLTV dong bang dung ten tung ky tu`() {
        val expected = mapOf(
            "AD_LTV_ONE_DAY_TOP_50" to "AdLTV_OneDay_Top50Percent",
            "AD_LTV_ONE_DAY_TOP_40" to "AdLTV_OneDay_Top40Percent",
            "AD_LTV_ONE_DAY_TOP_30" to "AdLTV_OneDay_Top30Percent",
            "AD_LTV_ONE_DAY_TOP_20" to "AdLTV_OneDay_Top20Percent",
            "AD_LTV_ONE_DAY_TOP_10" to "AdLTV_OneDay_Top10Percent",
        )
        assertEquals(expected, eventNameConstants().filterKeys { it.startsWith("AD_LTV_") })
        assertEquals(expected.values.toSet(), legacyNotSnakeCase)
    }

    /**
     * #5: tên `AdLTV_*` chỉ viết trong `EventNames`. `FirHelper.logAdTopRev` đưa tên qua một biến nên hai phép quét
     * chỗ gọi không thấy; literal đúng chữ vẫn lọt test hành vi (`FirHelperAdLtvEventTest`), nên cấm ở đây.
     */
    @Test
    fun `ten AdLTV khong viet literal ngoai EventNames`() {
        val found = kotlinFiles(mainSrc).filter { it.canonicalPath != eventNamesFile.canonicalPath }.flatMap { f ->
            val text = f.readText()
            Regex(""""AdLTV_""").findAll(text).map { where(f, text, it.range.first) }.toList()
        }
        assertEquals("tên AdLTV_ viết literal ngoài EventNames", emptyList<String>(), found)
    }

    @Test
    fun `khong hai hang cung mot ten event`() {
        val dup = eventNameConstants().entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertTrue("Hai hằng cùng một tên event (gộp nhầm số liệu): $dup", dup.isEmpty())
    }

    /**
     * D4 (DOC/66): 8 event thời gian fetch Remote Config (`FirHelperConfig`) nằm trong `EventNames`, nên hợp đồng
     * tên phủ cả chúng. Giữ nguyên tên cũ (dashboard): không có `7s`, `9s` là trên 8 s.
     */
    @Test
    fun `8 ten fir_rm_fet nam trong EventNames`() {
        val expected = listOf("1s", "2s", "3s", "4s", "5s", "6s", "8s", "9s").map { "fir_rm_fet_$it" }.toSet()
        val fetch = eventNameConstants().values.filter { it.startsWith("fir_rm_fet_") }.toSet()
        assertEquals(expected, fetch)
    }

    /** Tên `fir_rm_fet_*` từng đi qua một biến trong `FirHelperConfig`, lọt cả hai phép quét chỗ gọi: không viết lại literal. */
    @Test
    fun `khong con literal fir_rm_fet ngoai EventNames`() {
        val literal = Regex(""""fir_rm_fet_""")
        val found = kotlinFiles(mainSrc).filter { it.canonicalPath != eventNamesFile.canonicalPath }.flatMap { f ->
            val text = f.readText()
            literal.findAll(text).map { where(f, text, it.range.first) }.toList()
        }
        assertTrue("Tên fir_rm_fet_* viết literal, dùng EventNames.remoteConfigFetchBucket:\n" + found.joinToString("\n"), found.isEmpty())
    }

    /**
     * Literal chuỗi đầu tiên của lời gọi `logEvent(`/`logEventToServer(` (kèm dấu nháy), tính từ index của `(`. Bỏ qua
     * xuống dòng, comment `//` và `/* */`, và tên tham số (`name = "…"`) trước literal (1.8.0 Task 15 R4, R-1 m2).
     */
    private val callLiteral = Regex(
        """\b(?:logEvent|logEventToServer)\(\s*(?:(?://[^\n]*\n|/\*[\s\S]*?\*/)\s*)*(?:\w+\s*=\s*)?("(?:[^"\\]|\\.)*")"""
    )

    private fun callLiterals(text: String): List<Pair<Int, String>> =
        callLiteral.findAll(text).map { it.groups[1]!!.range.first to it.groupValues[1] }.toList()

    /** R4 (1.8.0 Task 15, đặc tả cho phép quét): literal ở lời gọi nhiều dòng, có comment, có tên tham số đều bị thấy. */
    @Test
    fun `R4 phep quet literal thay lenh goi nhieu dong co comment va ten tham so`() {
        val fixture = """
            logEvent(
                "multi_line"
            )
            logEventToServer(
                // ghi chú giữa dòng
                "after_comment"
            )
            FirHelper.instance.logEvent(
                /* khối */ "after_block"
            )
            logEvent(
                name = "named_arg",
            )
            logEvent(EventNames.AD_SHOW)
            logEvent(nameFromVariable)
        """.trimIndent()
        assertEquals(
            listOf("\"multi_line\"", "\"after_comment\"", "\"after_block\"", "\"named_arg\""),
            callLiterals(fixture).map { it.second }
        )
    }

    /** Tên literal (không template) ở chỗ gọi, như test gốc của plan: báo tên sai kèm file:dòng. */
    @Test
    fun `ten literal o cho goi dung hop dong`() {
        val bad = kotlinFiles(mainSrc).flatMap { f ->
            val text = f.readText()
            callLiterals(text).filter { '$' !in it.second }.map { it.first to it.second.removeSurrounding("\"") }
                .filter { breaksContract(it.second) }.map { "${where(f, text, it.first)}: ${it.second}" }
        }
        assertTrue("Tên event sai hợp đồng Điều 9.2:\n" + bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun `cho goi logEvent khong viet literal, ten di qua EventNames`() {
        val found = kotlinFiles(mainSrc).flatMap { f ->
            val text = f.readText()
            callLiterals(text).map { "${where(f, text, it.first)}: ${it.second}" }
        }
        assertTrue("Tên event viết literal ở chỗ gọi, chuyển vào EventNames:\n" + found.joinToString("\n"), found.isEmpty())
    }

    /**
     * Mỗi lớp placement cụ thể khai `eventTag` bằng hằng chữ (Task 14b B2): tên lớp bị R8 của app đổi (1.2.0 bỏ
     * keep-all), nên event ghép từ `simpleName` thành `ads_a0`. Tag hợp lệ, không trùng, và tên ghép với
     * `adsType` lớn nhất vẫn trong hợp đồng. Dạng bắt buộc là getter `override val eventTag: String get() = "…"`:
     * `override val eventTag = "…"` có backing field, còn `null` khi constructor của `AdsPlBase` đọc nó.
     */
    @Test
    fun `moi placement khai eventTag hang chu, hop le, khong trung`() {
        val placementClass = Regex(
            """(?m)^(?:internal |open )?class (\w+)\([^)]*\)\s*:\s*(?:AdsPlBase|AdsPlBaseFull|AdsPlBaseBn|AdsPlBaseNative|AdsPlNtClBase)\("""
        )
        val tagDecl = Regex("""override\s+val\s+eventTag\s*:\s*String\s+get\(\)\s*=\s*"([^"]*)"""")
        val tags = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        kotlinFiles(adsDir).forEach { f ->
            val text = f.readText()
            placementClass.findAll(text).forEach { cls ->
                val tag = tagDecl.find(text)?.groupValues?.get(1)
                if (tag == null) missing += "${where(f, text, cls.range.first)}: ${cls.groupValues[1]}" else tags[cls.groupValues[1]] = tag
            }
        }
        assertTrue(
            "Lớp placement thiếu `override val eventTag: String get() = \"…\"` (getter hằng chữ):\n" + missing.joinToString("\n"),
            missing.isEmpty()
        )
        assertTrue("Không tìm thấy lớp placement nào", tags.isNotEmpty())

        val badTag = tags.filterValues { !Regex("^[a-z][a-z0-9_]*$").matches(it) }
        assertTrue("eventTag sai dạng snake_case: $badTag", badTag.isEmpty())
        val dup = tags.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertTrue("Hai lớp placement cùng eventTag (gộp nhầm số liệu): $dup", dup.isEmpty())
        val badName = tags.values.map { EventNames.adsPlActivityNull(it, Int.MAX_VALUE) }.filter(::breaksContract)
        assertTrue("Tên event ghép từ eventTag sai hợp đồng: $badName", badName.isEmpty())
    }

    /**
     * Mỗi lớp mạng cụ thể (lớp con của `AdsBase`) khai `eventTag` bằng hằng chữ (D3, DOC/66): nhãn
     * `ad_show_fail_reason` `not_plm_cf_<eventTag>_<adsType>` và log của `AdsBase` dùng nó thay cho tên lớp, mà
     * R8 của app đổi (`not_plm_cf_z71`). Cùng dạng getter với placement; tag không trùng giữa các lớp mạng (cùng
     * `adsType` 0 cho banner, rect… của AdMob), được trùng với tag placement cùng mạng và định dạng.
     */
    @Test
    fun `moi lop mang AdsBase khai eventTag hang chu, hop le, khong trung`() {
        val networkClass = Regex(
            """(?m)^(?:internal |open )?class (\w+)\([^)]*\)\s*:\s*(?:AdsBase|AdsBaseBanner|AdsBaseBannerCl|AdsBaseFull|AdsBaseGift|AdsBaseNative|AdsBaseRect)\("""
        )
        val tagDecl = Regex("""override\s+val\s+eventTag\s*:\s*String\s+get\(\)\s*=\s*"([^"]*)"""")
        val tags = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        kotlinFiles(adsDir).forEach { f ->
            val text = f.readText()
            networkClass.findAll(text).forEach { cls ->
                val tag = tagDecl.find(text)?.groupValues?.get(1)
                if (tag == null) missing += "${where(f, text, cls.range.first)}: ${cls.groupValues[1]}" else tags[cls.groupValues[1]] = tag
            }
        }
        assertTrue(
            "Lớp mạng thiếu `override val eventTag: String get() = \"…\"` (getter hằng chữ):\n" + missing.joinToString("\n"),
            missing.isEmpty()
        )
        assertTrue("Không tìm thấy lớp mạng nào", tags.isNotEmpty())
        val badTag = tags.filterValues { !snakeCase.matches(it) }
        assertTrue("eventTag của lớp mạng sai dạng ^[a-z][a-z0-9_]{0,39}$: $badTag", badTag.isEmpty())
        val dup = tags.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        assertTrue("Hai lớp mạng cùng eventTag (nhãn not_plm_cf gộp nhầm): $dup", dup.isEmpty())
        val abstractTag = Regex("""protected\s+abstract\s+val\s+eventTag\s*:\s*String""")
        assertTrue(
            "AdsBase phải khai `protected abstract val eventTag: String`",
            abstractTag.containsMatchIn(File(adsDir, "AdsBase.kt").readText())
        )
    }

    /**
     * Không ghép tên event hay nhãn từ tên lớp của SDK (reflection, R8 đổi tên). Số chỗ còn lại được ghim theo
     * file: không thêm chỗ mới, sửa chỗ cũ thì hạ số ở đây. `AdsBase.kt` đã sạch (D3: log và nhãn `not_plm_cf` dùng
     * `eventTag`), `AdsEcmps.kt` cũng vậy (review E M8: log `dropExpired` dùng `AdsPlBase.logTag`); không được có lại.
     */
    @Test
    fun `khong ghep ten event hay nhan tu ten lop`() {
        val reflection = Regex("""::class\.(?:java\.)?simpleName|(?<![\w.?])javaClass\??\.simpleName|\bthis\.javaClass\b""")
        val known = emptyMap<String, Int>()
        val found = kotlinFiles(adsDir).associate { f ->
            val text = f.readText()
            f.relativeTo(adsDir).path to reflection.findAll(text).map { where(f, text, it.range.first) }.toList()
        }.filterValues { it.isNotEmpty() }
        assertEquals(
            "Tên lớp dùng làm tên event hay nhãn:\n" + found.values.flatten().joinToString("\n"),
            known,
            found.mapValues { it.value.size }
        )
    }
}
