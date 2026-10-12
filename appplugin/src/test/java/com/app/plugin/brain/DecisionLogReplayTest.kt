package com.app.plugin.brain

import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.HeldCall
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.LogEventTestIsolation
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.logevent.LogService
import com.app.plugin.until.LogUtil
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import retrofit2.Call
import retrofit2.Response
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fix wave 1 của Task 14a (I2): flush không được đổ cả backlog decision log (tới 5000) vào hàng đợi event
 * có trần 500 — mỗi bản thừa đẩy event cũ nhất đang chờ ra ngoài, mà event quảng cáo không có bản trên
 * đĩa. Mỗi lượt chỉ replay `min(100, chỗ trống của hàng)` bản cũ nhất. Lượt kế chỉ nối khi mọi bản của lượt
 * này có kết quả và đĩa thật sự bớt (có bản được xoá, không bản nào xoá hỏng), tối đa 10 lượt mỗi lần
 * flush; không thì chờ lần flush sau. Bản bị server từ chối (4xx) xếp sau mọi bản khác và không bao giờ bị
 * xoá. `logService` giả qua reflection như `LogEventServerQueueTest`; luồng nền của DecisionLogger là
 * [HeldExecutor] chạy trên luồng test.
 */
class DecisionLogReplayTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val realExecutor = DecisionLogger.ioExecutor
    private val logServiceField = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
    private val calls = CopyOnWriteArrayList<HeldCall>()
    private val io = HeldExecutor()

    private val heldService = object : LogService {
        override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> =
            HeldCall(Buffer().also { requestBody.writeTo(it) }.readUtf8()).also { calls += it }
    }

    @Before fun setUp() {
        PlayerIdSource.resetForTest()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogEventServer.resetSenderForTest()
        logServiceField.set(null, heldService)
        DecisionLogger.ioExecutor = io
    }

    @After fun tearDown() {
        drainPending()
        LogEventTestIsolation.cancelScheduledWork()
        PlayerIdSource.resetForTest()
        logServiceField.set(null, null)   // lateinit trở về chưa init, như các test khác mong đợi
        LogEventServer.resetSenderForTest()
        DecisionLogger.ioExecutor = realExecutor
        DecisionLogger.detachQueueForTest()
    }

    /**
     * Trả lời (200) mọi call còn treo và đợi hàng yên trước khi gỡ `heldService` (1.7.0 TF, R-TF residual): vài test kết thúc
     * khi còn call đang bay hay item đang chờ; luồng IO có thể còn giữa `offer` và `pump` và gửi nó qua `logService` tĩnh
     * vào test kế tiếp. Yên = 100 ms liền không call mới, không item chờ. Tối đa 5 s, không làm test đỏ.
     */
    private fun drainPending() {
        val deadline = System.currentTimeMillis() + 5_000
        var quietSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            var progressed = false
            while (answered < calls.size && calls[answered].isExecuted) {
                calls[answered].let { answered++; it.respond(Response.success("ok".toResponseBody())) }
                progressed = true
            }
            val now = System.currentTimeMillis()
            if (progressed || answered < calls.size || LogEventServer.pendingEventsForTest() > 0) quietSince = now
            else if (now - quietSince >= 100) return
            Thread.sleep(5)
        }
    }

    private fun queueFile() = File(tmp.root, "brain_decision_queue.log")   // DecisionLogQueue.FILE_NAME

    /** Bản ghi đủ 12 trường như sink mặc định ghi (phân tách U+001F). */
    private fun record(i: Int) = listOf(
        "d_replay_$i", "INTERSTITIAL_OPPORTUNITY", "inter", "INTERSTITIAL", "0.5", "none,inter", "",
        "UNKNOWN", "0.0", "UNKNOWN", "0", "p1"
    ).joinToString("\u001F")

    private fun writeBacklog(from: Int, to: Int) =
        queueFile().writeText((from..to).joinToString("") { record(it) + "\n" })

    private fun adEvent(i: Int) = LogEventServer.logAdShowFailWithReason("pl_$i", "no_fill")

    /**
     * Chờ [condition] tối đa [AWAIT_MS]: hạn rộng vì luồng nền của `LogEventServer` (Dispatchers.IO) có thể bị hệ điều hành
     * bỏ đói lâu dưới tải; chỉ trạng thái kẹt thật mới hết hạn, và thông báo mang trạng thái hàng để đọc được nguyên nhân.
     */
    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_MS
        while (!condition()) {
            assertTrue(
                "het ${AWAIT_MS / 1000} s ma chua: $what [calls=${calls.size} answered=$answered " +
                    "pending=${LogEventServer.pendingEventsForTest()} ioSubmitted=${io.submitted} ioHeld=${io.size}]",
                System.currentTimeMillis() < deadline
            )
            Thread.sleep(5)
        }
    }

    /** Số thứ tự (`d_replay_<n>`) của mọi decision event đã tới server (call đã tạo), theo thứ tự gửi. */
    private fun replayedNumbers(): List<Int> = calls
        .map { JsonParser.parseString(it.body).asJsonObject }
        .filter { it.get("eventName").asString == "brain_decision" }
        .map { it.getAsJsonObject("eventData").get("decision_id").asString.removePrefix("d_replay_").toInt() }

    /** Số call đã trả lời; call mới hơn là đang bay. */
    private var answered = 0

    /**
     * Trả lời đúng [n] call của một lượt, chờ call đến muộn. `unanswered() == n` chỉ nói event đã vào hàng, không nói
     * sender đã gửi xong: luồng IO có thể còn giữa "lấy item khỏi hàng" và "call vào `calls`" hay "call vào `calls`" và
     * `enqueue` (call chưa có callback). vòng "trả lời tới khi hết call thấy được" thoát sớm và bỏ sót call đến sau; kết quả cuối của
     * lượt không bao giờ tới, "replay next" không được đẩy, và test chờ "luot N vao hang" tới hết hạn (1.7.0 TF).
     * Ở đây chỉ trả lời call đã `enqueue`, theo thứ tự, tới khi đủ [n]; mỗi kết quả vẫn bơm tiếp phần còn lại của hàng.
     * Mọi test dùng hàm này với [n] biết trước; [response] quyết định kết quả theo từng call (mặc định 200).
     */
    private fun answerRound(n: Int, response: (HeldCall) -> Response<ResponseBody> = { Response.success("ok".toResponseBody()) }) {
        val target = answered + n
        awaitTrue("$n call cua luot deu da gui va tra loi") {
            while (answered < target && answered < calls.size && calls[answered].isExecuted) {
                calls[answered].let { answered++; it.respond(response(it)) }
            }
            answered >= target
        }
    }

    private fun http(code: Int): Response<ResponseBody> = Response.error(code, "err".toResponseBody())

    /** Decision/ad event đã vào hàng mà chưa có trả lời: đang bay + đang chờ. */
    private fun unanswered() = calls.size - answered + LogEventServer.pendingEventsForTest()

    private fun lineCount() = queueFile().readLines().count { it.isNotBlank() }

    /** Số thứ tự `d_replay_<n>` trong body của một call decision. */
    private fun numberOf(call: HeldCall): Int = JsonParser.parseString(call.body).asJsonObject
        .getAsJsonObject("eventData").get("decision_id").asString.removePrefix("d_replay_").toInt()

    // ═══ Review D I3 (FD3): bản ghi mang user_id lúc ghi ═══

    /** `userProperty.user_id` của call thứ [i]. */
    private fun userIdOfCall(i: Int): String = JsonParser.parseString(calls[i].body).asJsonObject
        .getAsJsonObject("userProperty").get("user_id").asString

    /**
     * Bản ghi xếp hàng khi app đã truyền id "app-3", replay ở lần mở kế tiếp tại `LogEventServer.init`, trước
     * `configure` (override còn null): body mang id đã lưu, khớp `is_holdout` tính theo chính id đó.
     */
    @Test fun `ban ghi co user_id luc ghi thi replay truoc configure mang dung id do`() {
        queueFile().writeText(record(1) + "\u001F" + "app-3" + "\n")

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("1 ban ghi toi server") { calls.size == 1 }

        assertEquals("app-3", userIdOfCall(0))
        assertEquals(1, replayedNumbers().single())
    }

    /** Dòng của bản trước (12 trường, không có user_id) đọc tương thích: như hôm nay, id hiện hành. */
    @Test fun `dong cu 12 truong thi replay dung id hien hanh`() {
        PlayerIdSource.override = "app-now"
        writeBacklog(1, 1)

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("1 ban ghi toi server") { calls.size == 1 }

        assertEquals("app-now", userIdOfCall(0))
    }

    /** Trường user_id rỗng (id không lưu được lúc ghi) thì như dòng cũ: id hiện hành. */
    @Test fun `truong user_id rong thi replay dung id hien hanh`() {
        queueFile().writeText(record(1) + "\u001F" + "\n")

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("1 ban ghi toi server") { calls.size == 1 }

        assertEquals(BrainStorage.playerId(), userIdOfCall(0))
    }

    // ═══ 1.7.0 Task 6 (F2): trường 14 = `ts_ms` lúc ghi ═══

    private fun tsOfCall(i: Int): Long = JsonParser.parseString(calls[i].body).asJsonObject.get("ts").asLong

    /** Replay ở lần mở sau mang `ts` lúc ghi (ngày phát, G11 (iv)), không phải giờ replay. */
    @Test fun `B1 replay brain_decision mang ts luc ghi (truong 14)`() {
        queueFile().writeText(record(1) + "\u001F" + "app-3" + "\u001F" + "1790703000000" + "\n")

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("1 ban ghi toi server") { calls.size == 1 }

        assertEquals("ts cua body replay = truong 14", 1790703000000L, tsOfCall(0))
        assertEquals("app-3", userIdOfCall(0))
    }

    /** Dòng 13 trường của bản trước (chưa có `ts_ms`) vẫn replay, `ts` hiện hành như hành vi cũ. */
    @Test fun `B2 dong 13 truong cu van replay (ts hien hanh)`() {
        val before = System.currentTimeMillis()
        queueFile().writeText(record(1) + "\u001F" + "app-3" + "\n")

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("1 ban ghi toi server") { calls.size == 1 }

        assertTrue("ts hien hanh", tsOfCall(0) >= before)
        assertEquals("app-3", userIdOfCall(0))
        assertEquals(1, replayedNumbers().single())
    }

    @Test fun `backlog 600 thi mot luot replay toi da 100 ban cu nhat, event quang cao dang cho khong bi day ra`() {
        (1..5).forEach(::adEvent)
        awaitTrue("4 event quang cao dang bay, 1 cho") { calls.size == 4 && LogEventServer.pendingEventsForTest() == 1 }
        writeBacklog(1, 600)

        DecisionLogger.attachQueue(tmp.root)   // đếm rồi flush, trên io
        io.runAll()

        awaitTrue("100 ban ghi vao hang") { LogEventServer.pendingEventsForTest() == 101 }
        Thread.sleep(200)   // replay thừa (nếu có) dựng trên Dispatchers.IO: cho nó thời gian lộ ra
        assertEquals("chi 100 ban ghi duoc replay", 101, LogEventServer.pendingEventsForTest())
        assertEquals("khong event nao bi day ra", 0L, LogEventServer.droppedEventsForTest())

        answerRound(105)

        assertEquals("5 event quang cao + 100 ban ghi", 105, calls.size)
        assertTrue("event quang cao dang cho toi duoc server", calls.any { it.body.contains("pl_5") })
        assertEquals("100 ban cu nhat, moi ban mot lan", (1..100).toList(), replayedNumbers().sorted())

        io.runAll()   // xoá 100 bản đã tới server, rồi lượt kế (lượt này đã có kết quả)

        assertEquals("500 ban con lai nam tren dia", 500, queueFile().readLines().count { it.isNotBlank() })
        awaitTrue("luot ke replay 100 ban tiep theo") { calls.size == 109 && LogEventServer.pendingEventsForTest() == 96 }
        assertTrue("luot ke lay tiep cac ban cu nhat con lai", replayedNumbers().drop(100).all { it in 101..200 })
    }

    @Test fun `hang event con it cho trong thi replay chi bang cho trong`() {
        (1..454).forEach(::adEvent)
        awaitTrue("4 dang bay, 450 cho") { calls.size == 4 && LogEventServer.pendingEventsForTest() == 450 }
        writeBacklog(1, 600)

        DecisionLogger.attachQueue(tmp.root)
        io.runAll()

        awaitTrue("50 ban ghi lap cho trong") { LogEventServer.pendingEventsForTest() == 500 }
        Thread.sleep(200)
        assertEquals(500, LogEventServer.pendingEventsForTest())
        assertEquals("khong event quang cao nao bi day ra", 0L, LogEventServer.droppedEventsForTest())
    }

    @Test fun `luot truoc chua co ket qua thi flush khong replay trung`() {
        writeBacklog(1, 150)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("luot dau: 4 dang bay, 96 cho") { calls.size == 4 && LogEventServer.pendingEventsForTest() == 96 }

        DecisionLogger.flushQueue()
        io.runAll()

        Thread.sleep(200)
        assertEquals("khong gui them", 4, calls.size)
        assertEquals("khong replay lai 100 ban dang bay/cho", 96, LogEventServer.pendingEventsForTest())
    }

    @Test fun `server nhan ma xoa khoi dia that bai thi khong chay luot ke, log mot lan`() {
        // Fix wave 2 (Important 1): đĩa đầy → "remove failed, records kept" → lượt kế chọn lại đúng 100 bản
        // vừa gửi, server nhận lại, lặp mãi.
        writeBacklog(1, 150)
        val q = spy(DecisionLogQueue(tmp.root))
        doAnswer { q.peekAll()?.size ?: -1 }.`when`(q).removeAll(anyCollection())   // không ghi được, giữ nguyên
        DecisionLogger.attachQueue(q)
        io.runAll()
        awaitTrue("luot dau vao hang") { unanswered() == 100 }
        answerRound(100)

        mockStatic(Log::class.java).use { log ->
            io.runAll()   // các lần xoá (thất bại), rồi quyết định lượt kế
            log.verify({ Log.e(eq("AppPlugin"), contains("could not be removed from disk")) }, times(1))
        }

        Thread.sleep(200)
        assertEquals("khong gui lai cung ban ghi: 100 submit, khong phai 200", 100, calls.size + LogEventServer.pendingEventsForTest())
        assertEquals(150, lineCount())
    }

    @Test fun `ban bi server tu choi khong chan replay, luot ke lay ban 101 tro di`() {
        // Fix wave 2 (M3): bản luôn bị 4xx nằm đầu hàng thì mỗi lượt chọn lại đúng chúng, các bản sau không
        // bao giờ tới lượt.
        writeBacklog(1, 250)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("luot dau vao hang") { unanswered() == 100 }
        answerRound(100) { http(400) }
        io.runAll()

        DecisionLogger.flushQueue()
        io.runAll()
        awaitTrue("luot ke vao hang") { unanswered() == 100 }
        answerRound(100)

        assertEquals((101..200).toList(), replayedNumbers().drop(100).sorted())
        io.runAll()   // xoá 101..200 (server đã nhận), rồi lượt kế
        assertEquals("100 ban bi tu choi mot lan van con tren dia", 150, lineCount())

        // Lượt kế: 50 bản chưa gửi (201..250) trước, rồi 50 bản đã bị từ chối một lần (thử lần hai).
        // Chờ lượt này vào hàng hết để không còn event nào đang dựng khi test kết thúc.
        awaitTrue("luot thu ba vao hang") { unanswered() == 100 }
    }

    /**
     * Đợt sửa cuối Phase B, mục 3: trong một lô hỗn hợp, bản chưa bị từ chối lấp chỗ trước, bản đã bị từ
     * chối lấy phần còn lại theo thứ tự cũ nhất. Lượt 1 (1..100): 1..60 bị 400, 61..100 server nhận → đĩa
     * bớt nên nối lượt 2 = 101..150 (50 bản mới) + 1..50 (bản bị từ chối cũ nhất); 51..60 hết chỗ. Chọn
     * theo thứ tự trên đĩa thì lượt 2 sẽ là 1..60 + 101..140.
     */
    /**
     * Task 17 (review đợt sửa cuối Phase B, Minor 5): `AppPluginManager.initPlugin` gắn hàng đợi (replay trên
     * luồng nền) TRƯỚC `LogEventServer.init` (`AppPluginBase.initPlugin`). Replay chạy trước init không được
     * gửi vào một LogEventServer chưa init (logE "not initialized" giả mỗi lần mở app) mà chờ init.
     */
    @Test fun `replay truoc LogEventServer init thi cho, khong logE not initialized`() {
        logServiceField.set(null, null)
        writeBacklog(1, 3)
        mockStatic(Log::class.java).use { log ->
            DecisionLogger.attachQueue(tmp.root)
            io.runAll()
            log.verify({ Log.e(eq("AppPlugin"), contains("not initialized")) }, never())
        }
        assertEquals(0, calls.size)
        assertEquals(3, lineCount())
    }

    /** Task 17: `LogEventServer.init` xong thì replay các bản đang chờ ngay trong lần mở app này. */
    @Test fun `LogEventServer init xong thi replay ban ghi dang cho`() {
        logServiceField.set(null, null)
        writeBacklog(1, 3)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        // init dựng service thật; thay bằng service giả trước khi luồng nền (HeldExecutor) chạy lần flush.
        LogEventServer.init("http://127.0.0.1:9/", "test-key")
        logServiceField.set(null, heldService)
        io.runAll()
        awaitTrue("3 ban vao hang") { unanswered() == 3 }
        answerRound(3)
        assertEquals((1..3).toList(), replayedNumbers().sorted())
    }

    /**
     * Task 17 (review đợt sửa cuối Phase B, Minor 1): bản không dựng được body (ở đây probability `NaN`, Gson
     * từ chối) hỏng mãi. Nó phải xếp cuối như bản bị từ chối; không thì luôn là bản cũ nhất, bị chọn đầu mọi
     * lượt, và 100 bản như thế ở đầu hàng chặn replay tới khi trần 5000 đẩy chúng ra.
     */
    @Test fun `ban khong dung duoc body xep cuoi, lan flush sau gui phan con lai`() {
        val broken = (1..100).joinToString("") { record(it).replace("\u001F0.5\u001F", "\u001FNaN\u001F") + "\n" }
        queueFile().writeText(broken + (101..150).joinToString("") { record(it) + "\n" })
        DecisionLogger.attachQueue(tmp.root)
        // 100 kết quả REJECTED tới trên Dispatchers.IO; kết quả cuối đẩy việc 'replay next' vào io, có khi ngay
        // trong lúc runAll dưới còn chạy, và runAll chạy luôn nó (CI run 36593118072: chờ io.size > 0 thì treo
        // 5 s). Tạm dừng sau mỗi việc để luôn gặp thứ tự đó; chờ theo số việc đã nhận (attach + replay next).
        io.runAll(pauseAfterEachMs = 300)
        awaitTrue("100 ban hong co ket qua (viec 'replay next' vao luong nen)") { io.submitted >= 2 }
        io.runAll()
        assertEquals("ban hong khong toi server", 0, calls.size)

        DecisionLogger.flushQueue()
        io.runAll()
        awaitTrue("lan flush 2: 50 ban con lai vao hang") { unanswered() == 50 }
        answerRound(50)
        assertEquals((101..150).toList(), replayedNumbers().sorted())
        awaitTrue("50 viec xoa vao luong nen") { io.size >= 50 }
        io.runAll()
        assertEquals("chi con 100 ban hong tren dia", 100, lineCount())
    }

    @Test fun `lo hon hop thi ban moi lap truoc, ban bi tu choi lay phan con lai`() {
        writeBacklog(1, 150)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        awaitTrue("luot 1 vao hang") { unanswered() == 100 }
        answerRound(100) { c -> if (numberOf(c) <= 60) http(400) else Response.success("ok".toResponseBody()) }
        io.runAll()   // xoá 61..100, rồi nối lượt 2

        awaitTrue("luot 2 vao hang") { unanswered() == 100 }
        answerRound(100)   // để lượt 2 có kết quả trước khi test kết thúc
        assertEquals(
            "50 ban moi truoc, roi 50 ban bi tu choi cu nhat",
            (1..50).toList() + (101..150).toList(),
            replayedNumbers().drop(100).sorted()
        )
        io.runAll()   // xoá lượt 2; còn 51..60 → lượt 3 (10 bản)
        awaitTrue("luot 3 vao hang") { unanswered() == 10 }
        answerRound(10)
        assertEquals((51..60).toList(), replayedNumbers().drop(200).sorted())
        io.runAll()
        assertEquals(0, lineCount())
    }

    /**
     * Đợt sửa cuối Phase B, mục 3: một lô có bản xoá được lẫn bản xoá hỏng thì vẫn dừng chuỗi (một dòng log),
     * không nối lượt dù đĩa có bớt — lượt kế sẽ chọn lại đúng các bản xoá hỏng mà server đã nhận.
     */
    @Test fun `lo co ban xoa duoc va ban xoa hong thi van dung, log mot lan`() {
        writeBacklog(1, 250)
        val q = spy(DecisionLogQueue(tmp.root))
        // Xoá bản 1..50 thì không ghi được (giữ nguyên); các bản khác xoá thật.
        doAnswer { inv ->
            val sent = inv.getArgument<Collection<String>>(0)
            if (sent.any { it.startsWith("d_replay_") && it.substringBefore("\u001F").removePrefix("d_replay_").toInt() <= 50 }) {
                q.peekAll()?.size ?: -1
            } else {
                inv.callRealMethod()
            }
        }.`when`(q).removeAll(anyCollection())
        DecisionLogger.attachQueue(q)
        io.runAll()
        awaitTrue("luot dau vao hang") { unanswered() == 100 }
        answerRound(100)

        mockStatic(Log::class.java).use { log ->
            io.runAll()   // 50 lần xoá được, 50 lần hỏng, rồi quyết định lượt kế
            log.verify({ Log.e(eq("AppPlugin"), contains("50 sent record(s) could not be removed from disk")) }, times(1))
        }

        Thread.sleep(200)
        assertEquals("khong noi luot: van 100 submit", 100, calls.size + LogEventServer.pendingEventsForTest())
        assertEquals("51..100 da roi dia, 1..50 con", 200, lineCount())
    }

    /**
     * Re-review 5a18948 (đợt sửa cuối, mục 1): 4xx toàn cục trong một phiên (khoá API sai, WAF, deploy hỏng
     * trả 401/403/404/400 cho MỌI request) không nói gì về từng bản ghi. Bản bị từ chối chỉ xếp cuối, không
     * bao giờ bị xoá: qua 3 lần flush, bản 1..50 bị từ chối lần hai mà vẫn nằm trên đĩa.
     */
    @Test fun `403 toan cuc qua 3 lan flush thi khong ban nao bi xoa khoi dia`() {
        writeBacklog(1, 250)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        mockStatic(Log::class.java).use { log ->
            for (flush in 1..3) {
                if (flush > 1) {
                    DecisionLogger.flushQueue()
                    io.runAll()
                }
                awaitTrue("lan flush $flush vao hang") { unanswered() == 100 }
                answerRound(100) { http(403) }
                io.runAll()   // các lần xoá (nếu có), rồi quyết định lượt kế
                assertEquals("sau lan flush $flush khong ban nao roi dia", 250, lineCount())
            }
            log.verify({ Log.e(eq("AppPlugin"), contains("rejected twice")) }, never())
        }
        assertEquals(
            "lan 3: 50 ban chua gui truoc, roi 50 ban da bi tu choi mot lan",
            (1..50).toList() + (201..250).toList(),
            replayedNumbers().drop(200).sorted()
        )
        Thread.sleep(200)
        assertEquals("khong luot nao tu chay them", 0, unanswered())
    }

    @Test fun `chuoi luot dung sau 10 luot moi lan flush`() {
        writeBacklog(1, 1_100)
        DecisionLogger.attachQueue(tmp.root)
        io.runAll()
        for (round in 1..10) {
            awaitTrue("luot $round vao hang") { unanswered() == 100 }
            answerRound(100)
            io.runAll()   // xoá 100 bản, rồi lượt kế
        }

        Thread.sleep(200)
        assertEquals("10 luot x 100 ban", 1_000, replayedNumbers().size)
        assertEquals(0, unanswered())
        assertEquals("100 ban cho lan flush sau", 100, lineCount())
    }

    @Test fun `hang event day thi hoan replay, log`() {
        (1..504).forEach(::adEvent)
        awaitTrue("4 dang bay, 500 cho") { calls.size == 4 && LogEventServer.pendingEventsForTest() == 500 }
        writeBacklog(1, 10)
        LogUtil.setFlagLog(3)   // logD chỉ in khi flagLog > 2
        try {
            mockStatic(Log::class.java).use { log ->
                DecisionLogger.attachQueue(tmp.root)
                io.runAll()
                log.verify { Log.d(eq("AppPlugin"), contains("replay deferred: event queue full")) }
            }
        } finally {
            LogUtil.setFlagLog(1)
        }
        assertEquals(0L, LogEventServer.droppedEventsForTest())
    }

    @Test fun `server chua san sang thi moi luot van chi 100 ban va khong tu chay luot ke`() {
        // Kill switch `logevent`: mỗi event bị bỏ ngay, onResult(false). Trước Task 17 test này dùng
        // LogEventServer chưa init; giờ replay chờ init (hai test "LogEventServer init" ở trên).
        SdkKillSwitch.current = SdkKillSwitch(logevent = true)
        writeBacklog(1, 250)
        LogUtil.setFlagLog(3)   // bật logD để đếm lượt
        try {
            mockStatic(Log::class.java).use { log ->
                DecisionLogger.attachQueue(tmp.root)
                io.runAll()
                log.verify({ Log.d(eq("AppPlugin"), contains("replay round 1: 100 of 250")) }, times(1))
                log.verify({ Log.d(eq("AppPlugin"), contains("replay round 2")) }, never())
            }
        } finally {
            LogUtil.setFlagLog(1)
            SdkKillSwitch.current = SdkKillSwitch.NONE
        }
        assertEquals("khong request nao", 0, calls.size)
        assertEquals("khong co ban nao bi xoa", 250, queueFile().readLines().count { it.isNotBlank() })
    }

    private companion object {
        const val AWAIT_MS = 30_000L
    }
}
