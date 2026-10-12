package com.app.plugin.brain

import android.content.Context
import android.content.pm.ApplicationInfo
import org.mockito.Mockito.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Đo 2026-09-24: `attachQueue` đọc file hàng đợi ~129 ms trên main thread lúc mở app (StrictMode
 * bắt được trong frame của SDK). Đếm và flush phải chạy trên luồng nền của `DecisionLogger`.
 */
class DecisionLoggerAttachTest {
    @get:Rule val license = LicenseTestRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val realExecutor = DecisionLogger.ioExecutor

    @After
    fun tearDown() {
        PlayerIdSource.resetForTest()
        DecisionLogger.ioExecutor = realExecutor
        DecisionLogger.detachQueueForTest()
    }

    private fun queueFile() = File(tmp.root, "brain_decision_queue.log")   // DecisionLogQueue.FILE_NAME

    private fun decideTimes(n: Int) {
        val engine = DecisionEngine()
        val id = "p_attach_${System.nanoTime()}"
        val cands = listOf(
            ActionCandidate(ActionMode.NO_ACTION, "none"),
            ActionCandidate(ActionMode.INTERSTITIAL, "inter")
        )
        repeat(n) {
            engine.decide(DecisionRequest(id, DecisionPoint.INTERSTITIAL_OPPORTUNITY), PlayerState(id), cands)
        }
    }

    @Test
    fun `attachQueue tra ve truoc khi doc file hang doi`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        var countedValue: Int? = null

        DecisionLogger.attachQueue(tmp.root, onCounted = { countedValue = DecisionLogger.queuedCount() })

        assertEquals("chua dem xong thi queuedCount phai la -1", -1, DecisionLogger.queuedCount())
        assertEquals("onCounted chua duoc goi khi luong nen chua chay", null, countedValue)
        assertTrue("dem phai duoc day sang luong nen", io.size > 0)

        // File xuất hiện SAU khi attachQueue trả về: attachQueue đọc file trên luồng gọi thì thấy
        // hàng đợi rỗng và đếm ra 0, không phải 3.
        queueFile().writeText("a\nb\nc\n")
        io.runAll()

        assertEquals("dem tren luong nen phai thay 3 ban ghi", 3, countedValue)
        // Flush chạy ngay sau khi đếm: ba dòng không đủ trường bị bỏ, bộ đếm theo đúng file.
        assertEquals("sau flush bo ban ghi hong, bo dem phai ve 0", 0, DecisionLogger.queuedCount())
        assertEquals("", queueFile().readText())
    }

    /** Context của app giả: `applicationInfo.dataDir` = [dataDir]; `getFilesDir()` có stub để đếm lời gọi. */
    private fun appContext(dataDir: File): Context {
        val info = ApplicationInfo().apply { this.dataDir = dataDir.absolutePath }
        val ctx = mock(Context::class.java)
        doReturn(info).`when`(ctx).applicationInfo
        doReturn(File(dataDir, "files")).`when`(ctx).filesDir
        return ctx
    }

    /**
     * Task 17, fix wave 1 (review I2): `initPlugin` gắn hàng đợi NGAY, trên luồng gọi, bằng đường dẫn dựng từ
     * `applicationInfo.dataDir` (đọc field, không đụng đĩa) — không gọi `getFilesDir()`, lần đầu nó kiểm/tạo thư
     * mục (StrictMode DiskRead trên main, cổng G2). Gắn ngay thì `queuedCount()` là `-1` ("đang đếm") tới khi
     * luồng nền đếm xong, như `attachQueue`.
     */
    @Test
    fun `attachQueueForApp gan ngay khong goi getFilesDir, queuedCount la -1 toi khi dem xong`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        val data = tmp.newFolder("data")
        File(data, "files").mkdirs()
        File(data, "files/brain_decision_queue.log").writeText("a\nb\n")
        val ctx = appContext(data)

        DecisionLogger.attachQueueForApp(ctx)

        assertEquals("gan ngay: dang dem", -1, DecisionLogger.queuedCount())
        io.runAll()
        verify(ctx, never()).filesDir
        assertEquals("flush sau khi dem bo hai dong hong", "", File(data, "files/brain_decision_queue.log").readText())
        assertEquals(0, DecisionLogger.queuedCount())
    }

    /**
     * Task 17, fix wave 1 (review I2): bản ghi brain ngay sau khi gắn (máy cài mới, thư mục `files` chưa có) xuống
     * đĩa TRƯỚC khi gửi — sink ghi rồi mới gửi (1.8.0 F-P1: cả hai trên luồng nền, theo thứ tự). Gắn trên luồng nền
     * (05ca0bf) thì trong khoảng chờ `queue` là null và bản ghi chỉ được gửi thẳng: mất khi process chết hay gửi lỗi.
     */
    @Test
    fun `log ngay sau attachQueueForApp xuong dia truoc khi gui, ke ca khi thu muc files chua co`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        val data = tmp.newFolder("data-fresh")
        DecisionLogger.attachQueueForApp(appContext(data))
        DecisionLogger.installDefaultSink()

        decideTimes(1)   // LogEventServer chưa init: lần gửi bị bỏ, bản ghi phải còn trên đĩa
        io.runAll()   // F-P1: ghi rồi gửi trên luồng nền

        val file = File(data, "files/brain_decision_queue.log")
        assertTrue("ban ghi phai co tren dia", file.isFile)
        assertEquals(1, file.readLines().count { it.isNotBlank() })
    }

    /** Review M8: `initPlugin` phải gắn qua [DecisionLogger.attachQueueForApp], không qua `context.filesDir`. */
    @Test
    fun `initPlugin gan hang doi qua attachQueueForApp, khong doc filesDir`() {
        val src = File("src/main/java/com/app/plugin/app/AppPluginManager.kt")
        assertTrue("khong thay ${src.absolutePath}", src.isFile)
        val text = src.readText()
        assertTrue("initPlugin phai goi DecisionLogger.attachQueueForApp(context)", text.contains("DecisionLogger.attachQueueForApp(context)"))
        assertTrue("AppPluginManager khong duoc doc filesDir (dia tren main)", !Regex("""\.filesDir\b|getFilesDir\(""").containsMatchIn(text))
    }

    @Test
    fun `dem hang doi chay tren luong appplugin-brain-log`() {
        queueFile().writeText("a\nb\n")
        val latch = CountDownLatch(1)
        var threadName: String? = null
        var countedValue = Int.MIN_VALUE

        DecisionLogger.attachQueue(tmp.root, onCounted = {
            threadName = Thread.currentThread().name
            countedValue = DecisionLogger.queuedCount()
            latch.countDown()
        })

        assertTrue("dem nen phai xong", latch.await(5, TimeUnit.SECONDS))
        assertEquals("1.8.0 F-P1 (D31): mot luong nen appplugin-brain-log", "appplugin-brain-log", threadName)
        assertEquals(2, countedValue)
    }

    @Test
    fun `flushQueue tra ve truoc khi doc hay xoa file hang doi`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        // Spy đếm lời gọi: file không đổi chưa đủ chứng minh không có peekAll đồng bộ (đọc không để dấu).
        val q = spy(DecisionLogQueue(tmp.root))
        DecisionLogger.attachQueue(q)
        io.runAll()
        DecisionLogger.installDefaultSink()
        decideTimes(3)   // LogEventServer chưa init -> gửi thất bại -> 3 bản ghi nằm lại
        io.runAll()   // F-P1: ghi rồi gửi trên luồng nền
        assertEquals(3, DecisionLogger.queuedCount())
        // Thay bằng 3 dòng hỏng: flush chạy thật thì bỏ hết, nên thấy ngay flush đã chạy hay chưa.
        queueFile().writeText("a\nb\nc\n")
        clearInvocations(q)

        DecisionLogger.flushQueue()

        assertEquals("flush phai day sang luong nen", 1, io.size)
        verify(q, never()).peekAll()
        verify(q, never()).removeAll(anyCollection())
        verify(q, never()).size()
        assertEquals("file chua duoc doc/xoa tren luong goi", "a\nb\nc\n", queueFile().readText())
        assertEquals(3, DecisionLogger.queuedCount())

        io.runAll()

        verify(q, atLeastOnce()).peekAll()
        verify(q, times(3)).removeAll(anyCollection())
        assertEquals("", queueFile().readText())
        assertEquals("bo dem theo so ban ghi con lai sau flush", 0, DecisionLogger.queuedCount())
    }

    @Test
    fun `xoa ban ghi ma khong doc duoc hang doi thi giu bo dem cu`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        queueFile().writeText("a\nb\nc\n")   // ba dòng hỏng: flush xoá từng dòng qua removeSent
        val q = spy(DecisionLogQueue(tmp.root))
        // DecisionLogQueue.removeAll trả -1 khi không đọc được file (không xoá gì).
        doReturn(-1).`when`(q).removeAll(anyCollection())
        var counted = Int.MIN_VALUE
        DecisionLogger.attachQueue(q, onCounted = { counted = DecisionLogger.queuedCount() })

        io.runAll()

        assertEquals(3, counted)
        verify(q, times(3)).removeAll(anyCollection())
        assertEquals("-1 cua removeAll khong duoc ghi vao bo dem", 3, DecisionLogger.queuedCount())
    }

    @Test
    fun `attachQueue cong khai giu chu ky nhi phan cu`() {
        // App/thư viện đã biên dịch với bản 1.3.x gọi attachQueue(File, int); hook onCounted chỉ cho test.
        val public = DecisionLogger::class.java.methods
            .filter { it.name == "attachQueue" }
            .map { it.parameterTypes.toList() }
        assertEquals(listOf(listOf(File::class.java, Int::class.javaPrimitiveType)), public)
    }

    /**
     * Review D I3 (FD3): sink mặc định lưu `user_id` hiện hành (`PlayerIdSource.current()`) làm trường thứ 13 của dòng
     * hàng đợi, để replay ở lần mở sau (trước `configure`) mang đúng id lúc ghi. 12 trường đầu không đổi.
     */
    @Test
    fun `sink mac dinh luu user_id hien hanh vao truong thu 13`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        DecisionLogger.installDefaultSink()
        PlayerIdSource.override = "app-3"

        decideTimes(1)
        io.runAll()

        val parts = queueFile().readLines().single { it.isNotBlank() }.split("\u001F")
        assertEquals("13 truong + ts_ms (truong 14, 1.7.0 T6)", 14, parts.size)
        assertEquals("app-3", parts[12])
    }

    /**
     * Id có xuống dòng/ký tự phân tách sẽ làm hỏng dòng hàng đợi: không lưu (trường rỗng), dòng vẫn đủ 13 trường, và
     * lỗi được log (không câm). Chỉ phòng thủ cho trường 13: dòng nguyên vẹn ở đây vì engine của test phân nhóm theo id
     * khác override; ở production `decision_id` (trường 0, `d_<playerId>_…`) mang cùng id, nên id như vậy bị chặn ngay
     * ở `configure`/`AdsBrain.onSessionStart(id)` (`PlayerIdSource.isUsable`, review N2; `SessionPlayerIdTest`).
     */
    @Test
    fun `user_id co xuong dong thi khong luu, dong van nguyen ven, logE`() =
        org.mockito.Mockito.mockStatic(android.util.Log::class.java).use { log ->
            val io = HeldExecutor()
            DecisionLogger.ioExecutor = io
            DecisionLogger.attachQueue(tmp.root)
            io.runAll()
            DecisionLogger.installDefaultSink()
            PlayerIdSource.override = "acct\n7\u001Fx"

            decideTimes(1)
            io.runAll()

            val lines = queueFile().readLines().filter { it.isNotBlank() }
            assertEquals(1, lines.size)
            val parts = lines.single().split("\u001F")
            assertEquals(14, parts.size)
            assertEquals("", parts[12])
            log.verify {
                android.util.Log.e(
                    org.mockito.ArgumentMatchers.eq(com.app.plugin.until.LogUtil.TAG),
                    org.mockito.ArgumentMatchers.contains("bản ghi không lưu user_id"),
                )
            }
        }

    @Test
    fun `ghi them ban ghi thi bo dem tang va khong vuot tran`() {
        val io = HeldExecutor()
        DecisionLogger.ioExecutor = io
        DecisionLogger.attachQueue(tmp.root, maxRecords = 2)
        io.runAll()
        assertEquals(0, DecisionLogger.queuedCount())

        DecisionLogger.installDefaultSink()
        decideTimes(3)
        io.runAll()

        // LogEventServer chưa init trong unit test -> gửi thất bại -> ba bản ghi nằm lại, file cắt còn 2.
        assertEquals("bo dem phai theo tran cua hang doi", 2, DecisionLogger.queuedCount())
        assertEquals(2, queueFile().readLines().count { it.isNotBlank() })
    }
}
