package com.app.plugin.brain

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DecisionLogQueueTest {
    @get:Rule val license = LicenseTestRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private fun queue(max: Int = 5000) = DecisionLogQueue(tmp.root, max)

    /** 1.8.0 F-P1: sink mặc định ghi trên `DecisionLogger.ioExecutor`; executor trực tiếp để `log()` ghi xong ngay. */
    private var realLogExecutor: java.util.concurrent.Executor? = null

    @Before fun setUp() { realLogExecutor = useDirectDecisionLogExecutor() }

    @After fun tearDown() { realLogExecutor?.let { DecisionLogger.ioExecutor = it } }

    @Test
    fun `ban ghi ton tai qua ca viec tao lai doi tuong`() {
        queue().enqueue("rec-1")
        queue().enqueue("rec-2")
        // Đối tượng mới, cùng thư mục — mô phỏng app khởi động lại.
        assertEquals(listOf("rec-1", "rec-2"), queue().peekAll())
    }

    @Test
    fun `giu dung thu tu ghi vao`() {
        val q = queue()
        (1..20).forEach { q.enqueue("r$it") }
        assertEquals((1..20).map { "r$it" }, q.peekAll())
    }

    @Test
    fun `xoa dung ban ghi da gui`() {
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(it) }
        q.removeAll(listOf("b"))
        assertEquals(listOf("a", "c"), q.peekAll())
    }

    @Test
    fun `xoa nhieu ban ghi cung luc`() {
        val q = queue()
        listOf("a", "b", "c", "d").forEach { q.enqueue(it) }
        q.removeAll(listOf("a", "d"))
        assertEquals(listOf("b", "c"), q.peekAll())
    }

    @Test
    fun `xoa tra ve so ban ghi con lai`() {
        // DecisionLogger đặt bộ đếm từ giá trị này thay vì đọc lại cả file sau mỗi lần xoá.
        val q = queue()
        listOf("a", "b", "c").forEach { q.enqueue(it) }
        assertEquals(2, q.removeAll(listOf("b")))
        assertEquals("xoa ban ghi khong co thi so con lai khong doi", 2, q.removeAll(listOf("khong-co")))
        assertEquals(2, q.removeAll(emptyList()))
        assertEquals(0, q.removeAll(listOf("a", "c")))
    }

    @Test
    fun `xoa ban ghi khong ton tai khong lam hong hang doi`() {
        val q = queue()
        listOf("a", "b").forEach { q.enqueue(it) }
        q.removeAll(listOf("khong-co"))
        assertEquals(listOf("a", "b"), q.peekAll())
    }

    @Test
    fun `ban ghi trung noi dung chi bi xoa mot ban moi lan`() {
        // Hai quyết định khác nhau về lý thuyết không thể trùng chuỗi (có decision_id duy nhất),
        // nhưng nếu có thì xoá phải bỏ đúng MỘT, không phải tất cả.
        val q = queue()
        listOf("x", "x", "y").forEach { q.enqueue(it) }
        q.removeAll(listOf("x"))
        assertEquals(listOf("x", "y"), q.peekAll())
    }

    @Test
    fun `vuot tran thi bo ban ghi cu nhat`() {
        val q = queue(max = 5)
        (1..12).forEach { q.enqueue("r$it") }
        val all = q.peekAll()
        assertEquals(5, all?.size)
        assertEquals("giu lai 5 ban ghi MOI nhat", listOf("r8", "r9", "r10", "r11", "r12"), all)
    }

    @Test
    fun `xuong dong trong ban ghi khong pha vo dinh dang`() {
        val q = queue()
        q.enqueue("dong-1\ndong-2")
        q.enqueue("binh-thuong")
        assertEquals("moi ban ghi van la mot dong", 2, q.peekAll()?.size)
    }

    @Test
    fun `clear xoa sach hang doi`() {
        val q = queue()
        (1..5).forEach { q.enqueue("r$it") }
        q.clear()
        assertEquals(0, q.size())
        assertEquals(emptyList<String>(), q.peekAll())
    }

    @Test
    fun `hang doi rong khong nem loi`() {
        val q = queue()
        assertEquals("chua co file la hang doi rong, khong phai loi", emptyList<String>(), q.peekAll())
        assertEquals(0, q.size())
        q.removeAll(listOf("gi-do"))   // không được ném
    }

    /**
     * File hàng đợi có mà không đọc được: tạo đường dẫn của nó là thư mục (có một file bên trong để kiểm
     * không bị đụng tới). Đọc lỗi cả khi chạy bằng root, khác `setReadable(false)`.
     */
    private fun unreadableQueueFile(): File =
        File(tmp.root, "brain_decision_queue.log").apply {   // DecisionLogQueue.FILE_NAME
            mkdirs()
            File(this, "keep").writeText("x")
        }

    @Test
    fun `doc file loi thi peekAll tra null, khong phai hang doi rong`() {
        val q = queue()
        unreadableQueueFile()
        mockStatic(Log::class.java).use { log ->
            assertNull(q.peekAll())
            log.verify { Log.e(eq("AppPlugin"), contains("decision log read failed")) }
        }
    }

    @Test
    fun `doc file loi thi removeAll khong ghi de, log va tra -1`() {
        // Task 15 review: peekAll nuốt lỗi đọc thành danh sách rỗng, removeAll ghi lại file rỗng —
        // cả hàng đợi mất mà không một dòng log (Điều 7).
        val q = queue()
        val f = unreadableQueueFile()
        mockStatic(Log::class.java).use { log ->
            assertEquals(-1, q.removeAll(listOf("a")))
            log.verify { Log.e(eq("AppPlugin"), contains("decision log remove skipped: queue unreadable")) }
        }
        assertFalse("khong ghi gi: khong co file tam", File(tmp.root, "brain_decision_queue.log.tmp").exists())
        assertTrue("khong bi ghi de", f.isDirectory)
        assertEquals("x", File(f, "keep").readText())
    }

    /**
     * Đợt sửa cuối Phase B, mục 2: ghi lại file hỏng (đường dẫn file tạm là thư mục) rồi đọc lại cũng hỏng
     * thì `removeAll` phải trả `-1`, không phải `size()` = 0 — 0 làm `DecisionLogger.removeSent` tưởng bản
     * ghi đã rời đĩa (0 < số dòng trước) và nối lượt replay gửi trùng.
     */
    @Test
    fun `ghi loi roi doc lai cung loi thi removeAll tra -1, khong phai 0`() {
        val q = spy(queue())
        listOf("a", "b").forEach { q.enqueue(it) }
        File(tmp.root, "brain_decision_queue.log.tmp").apply {   // writeAll ghi vào đây: là thư mục nên lỗi
            mkdirs()
            File(this, "keep").writeText("x")
        }
        doCallRealMethod().doReturn(null).`when`(q).peekAll()   // lần đọc sau khi ghi lỗi cũng hỏng
        mockStatic(Log::class.java).use { log ->
            assertEquals(-1, q.removeAll(listOf("a")))
            log.verify { Log.e(eq("AppPlugin"), contains("decision log remove failed, records kept")) }
        }
        assertEquals("file goc nguyen ven", listOf("a", "b"), File(tmp.root, "brain_decision_queue.log").readLines())
    }

    @Test
    fun `thu muc chua ton tai van ghi duoc`() {
        val nested = java.io.File(tmp.root, "chua/co/thu/muc")
        val q = DecisionLogQueue(nested)
        assertTrue("phai tu tao thu muc", q.enqueue("rec"))
        assertEquals(listOf("rec"), q.peekAll())
    }

    @Test
    fun `DecisionLogger dinh kem hang doi thi ghi lai ban ghi`() {
        // Hàng đợi được đếm trên luồng nền (Task 15): chờ đếm xong rồi mới đọc bộ đếm.
        val counted = CountDownLatch(1)
        DecisionLogger.attachQueue(tmp.root, onCounted = { counted.countDown() })
        try {
            assertTrue("dem hang doi nen phai xong", counted.await(5, TimeUnit.SECONDS))
            val e = DecisionEngine()
            val id = "p_q_${System.nanoTime()}"
            val cands = listOf(
                ActionCandidate(ActionMode.NO_ACTION, "none"),
                ActionCandidate(ActionMode.INTERSTITIAL, "inter")
            )
            e.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), cands)

            // LogEventServer chưa init trong unit test -> gửi thất bại -> bản ghi PHẢI nằm lại.
            assertTrue(
                "gui that bai thi ban ghi phai duoc giu lai de thu lai",
                DecisionLogger.queuedCount() > 0
            )
        } finally {
            // Hàng đợi là process-global: không gỡ thì test sau ghi vào thư mục tạm đã bị xoá.
            DecisionLogger.detachQueueForTest()
        }
    }

    @Test
    fun `khong dinh kem hang doi thi he thong van chay binh thuong`() {
        // Không gọi attachQueue: quay về chế độ gửi-rồi-quên, không được crash.
        val e = DecisionEngine()
        val id = "p_noq_${System.nanoTime()}"
        val d = e.decide(
            DecisionRequest(id, DecisionPoint.SESSION_START), PlayerState(id),
            listOf(ActionCandidate(ActionMode.NO_ACTION, "none"))
        )
        assertFalse(d.decisionId.isBlank())
    }
}
