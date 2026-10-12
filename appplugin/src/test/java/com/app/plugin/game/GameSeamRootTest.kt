package com.app.plugin.game

import android.util.Log
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainSignalListener
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.firbase.RcSectionRegistry
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mockStatic
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.1.0 G1: nửa phía core của `GameSeamTest` (2.0.0 C1d), chạy ở module gốc — nơi `GameModule` KHÔNG có trên classpath.
 * G2: core không gọi lớp game nào (census `src/main/java` của gốc); G3a: vắng `GameModule` → 0 section, 0 lỗi, 0 I/O, dò
 * đúng một lần. Nửa "có GameModule" (G3b, G4, G5) ở `appplugin-game/src/test/.../GameSeamTest.kt`.
 */
class GameSeamRootTest {
    @Before fun setUp() { RcSectionRegistry.resetForTest(); ConsentState.resetForTest(); ConsentState.setTier(ConsentTier.GRANTED) }
    @After fun tearDown() { RcSectionRegistry.resetForTest(); ConsentState.resetForTest() }

    @Test
    fun `G2 census - core ngoai 5 file game va game khong goi lop game nao`() {
        val root = File("src/main/java")
        assertTrue("Không thấy ${root.absolutePath}: sai thư mục làm việc, census sẽ xanh rỗng", root.isDirectory)
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it to it.relativeTo(root).invariantSeparatorsPath }
            .toList()
        assertTrue("Chỉ quét ${files.size} file .kt — sai gốc?", files.size >= MIN_FILES)
        val hits = files.flatMap { (f, rel) ->
            f.readLines().mapIndexedNotNull { i, line ->
                val t = line.trim()
                val comment = t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                if (!comment && (GAME_CALL.containsMatchIn(line) || GAME_PKG.containsMatchIn(line))) "$rel:${i + 1}" else null
            }
        }
        assertEquals("core gọi thẳng lớp game (${hits.size}): $hits", emptyList<String>(), hits)
    }

    @Test
    fun `G3a GameModule vang - 0 section 0 loi 0 IO, do dung mot lan`() {
        val absent = mutableListOf<String>()
        RcSectionRegistry.classFor = { absent += it; throw ClassNotFoundException(it) }
        val errors = CopyOnWriteArrayList<String>()
        ServerEventCapture().use { cap ->
            mockStatic(Log::class.java) { inv -> if (inv.method.name in setOf("e", "w")) errors += "${inv.arguments.toList()}"; 0 }.use {
                repeat(3) { RcSectionRegistry.ensureGameModule() }
                repeat(3) { assertNull("vắng GameModule thì không có listener", BrainSignalListener.current()) }
            }
            Thread.sleep(SETTLE_MS)
            assertEquals("dò GameModule không được gửi gì", 0, cap.allRequests().size)
        }
        assertEquals("vắng: 0 section", 0, RcSectionRegistry.sections().size)
        assertEquals("vắng: 0 lỗi/cảnh báo", emptyList<String>(), errors)
        assertEquals("vắng: dò đúng một lần", listOf(RcSectionRegistry.GAME_MODULE_CLASS), absent)
        // Tín hiệu brain khi vắng mã game: không ném, không ai nhận (phía CRM kiểm ở GameSeamTest của appplugin-game).
        AdsBrain.onSessionStart("g3_${System.nanoTime()}")
        AdsBrain.onPurchase(1.0)
        assertNull("vắng GameModule: tín hiệu brain không có listener", BrainSignalListener.installed)

        // Lỗi khác ClassNotFound (vd NoClassDefFoundError khi R8 cắt một phụ thuộc): không ném ra luồng config/main, vẫn dò một lần.
        RcSectionRegistry.resetForTest()
        val broken = mutableListOf<String>()
        RcSectionRegistry.classFor = { broken += it; throw NoClassDefFoundError(it) }
        repeat(3) { RcSectionRegistry.ensureGameModule() }
        assertNull("lỗi nạp lớp: không có listener", BrainSignalListener.current())
        assertEquals("lỗi nạp lớp: dò đúng một lần", listOf(RcSectionRegistry.GAME_MODULE_CLASS), broken)
        assertEquals("lỗi nạp lớp: 0 section", 0, RcSectionRegistry.sections().size)
    }

    /** Module giả cho G4r: đăng ký 2 section (dò bằng `classFor`, gọi `INSTANCE.install()` qua reflection như GameModule thật). */
    object StubTwoSections {
        fun install() {
            RcSectionRegistry.register("stub_a", "StubA") { it }
            RcSectionRegistry.register("stub_b", "StubB") { it }
        }
    }

    @Test
    fun `G4r do co - dung mot dong installed N section, vang - dung mot dong khong co`() {
        val debug = CopyOnWriteArrayList<String>()
        LogUtil.setFlagLog(3)
        try {
            mockStatic(Log::class.java) { inv -> if (inv.method.name == "d") debug += "${inv.arguments[1]}"; 0 }.use {
                RcSectionRegistry.classFor = { StubTwoSections::class.java }
                repeat(3) { RcSectionRegistry.ensureGameModule() }
                assertEquals(listOf("game: GameModule installed — 2 section"), debug.filter { it.startsWith("game:") })

                debug.clear()
                RcSectionRegistry.resetForTest()
                RcSectionRegistry.classFor = { throw ClassNotFoundException(it) }
                repeat(3) { RcSectionRegistry.ensureGameModule() }
                assertEquals(
                    listOf("game: không có ${RcSectionRegistry.GAME_MODULE_CLASS} — 0 section game"),
                    debug.filter { it.startsWith("game:") }
                )
            }
        } finally {
            LogUtil.setFlagLog(1)
        }
    }

    private companion object {
        const val MIN_FILES = 150
        const val SETTLE_MS = 300L
        /** Tên lớp game rồi `.`/`::` (lời gọi, kể cả không đủ FQN qua import). Chữ trong chuỗi log (`"BattlePass (…"`) không khớp. */
        val GAME_CALL = Regex("""\b(BattlePass|RecurringGrants|PurchaseRouter|OfferwallManager|CrmBridge)\s*(\.|::)""")
        /** Gói game ở dạng FQN/import: `com.app.plugin.iap.(BattlePass|…)`, `com.app.plugin.offerwall.`, `com.app.plugin.crm.`. */
        val GAME_PKG = Regex("""com\.app\.plugin\.(iap\.(BattlePass|RecurringGrants|PurchaseRouter)\b|offerwall\.|crm\.)""")
    }
}
