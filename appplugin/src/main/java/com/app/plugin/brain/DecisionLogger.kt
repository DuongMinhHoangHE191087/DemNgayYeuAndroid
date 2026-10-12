package com.app.plugin.brain

import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.EventOutcome
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.until.LogUtil
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Decision log theo schema doc 06 mục 8 / doc 24 mục K.
 *
 * Dùng event name RIÊNG (`brain_decision`, `brain_feedback`) trên chính pipeline
 * `LogEventServer` sẵn có, thay vì dựng đường truyền mới. Tách event name là cần thiết vì
 * schema khác hẳn các event ads: có propensity, candidate set, segment, cờ holdout — trộn
 * chung sẽ khiến phân tích OPE rất khó tách.
 *
 * ═══ ĐỘ BỀN: có hàng đợi riêng ═══
 *
 * `LogEventServer` gửi rồi quên — request thất bại là mất bản ghi. Với event ads thì mất vài
 * bản ghi chỉ làm nhiễu thống kê; với decision log thì **mất bản ghi làm lệch suy luận nhân
 * quả**, vì các lần mất không ngẫu nhiên (mạng kém tương quan với thiết bị yếu, vùng địa lý,
 * hành vi người chơi).
 *
 * Vì yêu cầu khác nhau nên cơ chế khác nhau: brain có `DecisionLogQueue` riêng — file
 * append-only, không thêm phụ thuộc. Bản ghi xuống đĩa TRƯỚC khi thử gửi, và chỉ bị xoá khi
 * server xác nhận đã nhận. Còn tồn thì được replay ở lần flush sau hoặc lần mở app kế tiếp.
 *
 * Cần gọi `attachQueue(dir)` một lần lúc khởi tạo SDK để bật độ bền; không gọi thì hệ thống
 * vẫn chạy bình thường nhưng quay về chế độ gửi-rồi-quên.
 *
 * ═══ LUỒNG ═══
 *
 * Mọi truy cập file hàng đợi — ghi (kèm cắt trần), đếm, flush, xoá bản đã gửi — chạy trên MỘT luồng nền
 * (`appplugin-brain-log`, FIFO): đo 2026-09-24, `attachQueue` đọc file ~129 ms trên main lúc mở app; 1.8.0 F-P1 (D31):
 * `PaywallManager.paywallFor` gọi `log()` trên main, ghi ngay trên luồng gọi là DiskRead/DiskWrite của SDK trên main.
 * `log()` chỉ chụp `user_id`/`ts` rồi hẹn việc; trên luồng nền bản ghi xuống đĩa TRƯỚC, rồi mới gửi sống.
 *
 * ═══ DÒNG HÀNG ĐỢI ═══
 *
 * 12 trường của `brain_decision` (phân tách [FIELD_SEP]), rồi trường 13: `user_id` lúc ghi (`PlayerIdSource.current()`,
 * review D I3). Replay chạy ở `LogEventServer.init`, trước khi app `configure { playerId }`: dựng body với id hiện
 * hành thì hàng mang UUID của SDK trong khi `is_holdout` tính theo id app. Replay ưu tiên id đã lưu. Dòng của bản trước
 * (12 trường) hoặc trường 13 rỗng thì dùng id hiện hành, như trước. Trường 14 (1.7.0 T6, F2): `ts_ms` lúc ghi — event sống
 * và replay mang cùng `ts` (ngày phát); dòng 13 trường của bản trước → `ts` lúc dựng, như cũ.
 */
internal object DecisionLogger {

    private val forwarders: MutableList<(DecisionRequest, DecisionResponse) -> Unit> = mutableListOf()
    private val feedbackForwarders: MutableList<(String, String, Long, Double?) -> Unit> = mutableListOf()

    @Volatile
    private var defaultSinkInstalled = false

    @Volatile
    private var queue: DecisionLogQueue? = null

    /** Số bản ghi đang chờ; [UNCOUNTED] khi hàng đợi vừa gắn mà luồng nền chưa đếm xong. */
    private val cachedQueued = AtomicInteger(0)

    /** Trần của hàng đợi đang gắn: `+1` sau enqueue không vượt quá nó, vì hàng đợi tự cắt bản ghi cũ. */
    @Volatile
    private var queueMaxRecords: Int = 0

    /**
     * Gộp một thao tác trên file hàng đợi với cập nhật [cachedQueued] để hai thứ không lệch nhau
     * khi luồng nền đếm/xoá cùng lúc với luồng khác ghi thêm.
     *
     * Cái giá: khoá được giữ suốt thao tác file. `attachQueue` (luồng gọi) phải chờ nếu luồng nền đang đếm
     * hoặc đang xoá; lúc mở app luồng nền còn rảnh nên không chờ. Ghi bản ghi mới cũng chạy trên luồng nền (F-P1).
     */
    private val countLock = Any()

    /** Luồng nền duy nhất cho ghi, đếm, flush và xoá bản ghi. Test thay bằng executor tự điều khiển hay trực tiếp. */
    internal var ioExecutor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, IO_THREAD_NAME).apply { isDaemon = true }
    }

    /**
     * Bật độ bền cho decision log. `dir` thường là `context.filesDir`.
     * Gọi một lần lúc khởi tạo SDK; gọi lại sẽ thay hàng đợi đang dùng.
     *
     * Trả về ngay, không đọc file: đếm rồi flush chạy trên luồng nền.
     */
    public fun attachQueue(dir: File, maxRecords: Int = 5000) {
        attachQueue(dir, maxRecords, onCounted = null)
    }

    /**
     * Như `attachQueue(dir, maxRecords)`, thêm `onCounted` được gọi trên luồng nền khi đếm xong,
     * trước khi flush. `internal`: chữ ký công khai giữ nguyên như 1.3.x.
     */
    internal fun attachQueue(dir: File, maxRecords: Int = 5000, onCounted: (() -> Unit)?) {
        attachQueue(DecisionLogQueue(dir, maxRecords), onCounted)
    }

    /**
     * Gắn hàng đợi của app lúc `AppPluginManager.initPlugin`, ngay trên luồng gọi, vào thư mục `files` của app.
     * Đường dẫn dựng từ `applicationInfo.dataDir` (đọc field) thay vì `Context.getFilesDir()`: lần gọi đầu của
     * hàm đó kiểm/tạo thư mục trên main (StrictMode DiskRead, cổng G2). Thư mục chưa có thì lần ghi đầu tạo
     * (`DecisionLogQueue.enqueue`). Như [attachQueue]: bản ghi xuống đĩa trước khi gửi ngay từ lúc này,
     * `queuedCount()` là `-1` tới khi luồng nền đếm xong.
     */
    internal fun attachQueueForApp(context: android.content.Context) =
        attachQueue(File(context.applicationInfo.dataDir, "files"))

    /** Gắn một hàng đợi đã dựng sẵn. `internal`: test gắn spy để đếm lời gọi trên file hàng đợi. */
    internal fun attachQueue(q: DecisionLogQueue, onCounted: (() -> Unit)? = null) {
        synchronized(countLock) {
            queue = q
            queueMaxRecords = q.maxRecords
            cachedQueued.set(UNCOUNTED)
        }
        runOnIo("attach") {
            countQueued(q)
            try {
                onCounted?.invoke()
            } catch (e: Exception) {
                LogUtil.logE("decision log onCounted failed: ${e.message}")
            }
            flushNow(q)
        }
    }

    /**
     * Số bản ghi đang chờ gửi. `-1` ngay sau `attachQueue`, tới khi luồng nền đếm xong; `0` khi
     * chưa gắn hàng đợi.
     */
    public fun queuedCount(): Int = cachedQueued.get()

    /**
     * Gửi lại các bản ghi còn tồn. Gọi khi app vào foreground, khi mạng trở lại, hoặc định kỳ.
     * Trả về ngay: việc đọc file và gửi chạy trên luồng nền.
     *
     * Mỗi lượt chỉ gửi tối đa [MAX_REPLAY_PER_FLUSH] bản CŨ NHẤT, và không quá chỗ trống của hàng đợi
     * event (`LogEventServer.eventQueueHeadroom`): hàng đợi đó có trần 500, bản thừa sẽ đẩy event cũ nhất
     * đang chờ ra ngoài — event quảng cáo không có bản trên đĩa nên bị đẩy là mất. Phần còn lại nằm trên
     * đĩa. Lượt kế chạy ngay khi mọi bản của lượt này có kết quả và đĩa thật sự bớt (có bản được xoá, không
     * bản nào xoá hỏng), tối đa [MAX_ROUNDS_PER_FLUSH] lượt; không thì ở lần flush sau. Flush tới khi lượt
     * trước chưa xong thì bỏ qua, để không gửi trùng bản đang bay. Bản bị server từ chối (4xx) xếp sau mọi
     * bản khác trong process này và KHÔNG bao giờ bị xoá vì bị từ chối: 4xx toàn cục (khoá API sai, WAF,
     * deploy hỏng) không nói gì về từng bản ghi; trần [DecisionLogQueue.maxRecords] giới hạn đĩa.
     *
     * Gửi tuần tự từng bản ghi qua đúng endpoint hiện có thay vì gộp batch: mục tiêu của hàng
     * đợi là ĐỘ BỀN, không phải hiệu năng. Gộp batch cần endpoint mới ở server — thứ chưa tồn
     * tại và không kiểm chứng được từ phía client.
     */
    public fun flushQueue() {
        if (queue == null) return   // chưa bật độ bền: không có gì để gửi lại, không dựng luồng nền
        runOnIo("flush") { queue?.let { flushNow(it) } }
    }

    /**
     * Một lượt replay. [round] đếm lượt trong chuỗi của một lần flush (tối đa [MAX_ROUNDS_PER_FLUSH]).
     * [removedOk]/[removeFailed]: số bản server đã nhận (cần xoá khỏi đĩa) đã xoá được / không xoá được.
     */
    private class ReplayBatch(val q: DecisionLogQueue, size: Int, val round: Int) {
        val outstanding = AtomicInteger(size)
        val removedOk = AtomicInteger(0)
        val removeFailed = AtomicInteger(0)
    }

    /** Lượt replay gần nhất; chỉ một lượt chưa xong mỗi hàng đợi. */
    @Volatile
    private var replayBatch: ReplayBatch? = null

    /**
     * Bản ghi (nguyên dòng) bị server từ chối (4xx) trong process này. Khi chọn lượt, các dòng này xếp sau
     * mọi dòng khác, để bản luôn bị từ chối không chặn replay; chúng vẫn nằm trên đĩa.
     */
    private val rejectedInProcess: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Chỉ chạy trên luồng nền. */
    private fun flushNow(q: DecisionLogQueue, round: Int = 1) {
        val active = replayBatch
        if (active != null && active.q === q && active.outstanding.get() > 0) {
            LogUtil.logD("decision log flush skipped: ${active.outstanding.get()} replayed record(s) still pending")
            return
        }
        val lines = q.peekAll() ?: run {
            LogUtil.logE("decision log flush skipped: queue unreadable, records kept")
            return
        }
        if (lines.isEmpty()) return
        val budget = minOf(MAX_REPLAY_PER_FLUSH, LogEventServer.eventQueueHeadroom())
        if (budget <= 0) {
            LogUtil.logD("decision log replay deferred: event queue full, ${lines.size} record(s) stay on disk")
            return
        }
        val picked = pickReplay(q, lines, budget)
        if (picked.isEmpty()) return
        if (!LogEventServer.isInitialized()) {
            LogUtil.logD("decision log replay waits for LogEventServer.init")   // init gọi flushQueue
            return
        }
        LogUtil.logD("decision log replay round $round: ${picked.size} of ${lines.size} record(s), rest stays on disk")
        val batch = ReplayBatch(q, picked.size, round).also { replayBatch = it }
        for ((line, parts) in picked) replayOne(batch, line, parts)
    }

    /**
     * Tối đa [budget] bản cũ nhất; bản đã bị từ chối trong process này xếp sau mọi bản khác. Dòng hỏng thì
     * xoá (có log) và không tính vào [budget].
     */
    private fun pickReplay(q: DecisionLogQueue, lines: List<String>, budget: Int): List<Pair<String, List<String>>> {
        val fresh = mutableListOf<Pair<String, List<String>>>()
        val rejected = mutableListOf<Pair<String, List<String>>>()
        for (line in lines) {
            if (fresh.size >= budget) break
            val parts = line.split(FIELD_SEP)
            if (parts.size < 12) {
                LogUtil.logE("decision log: drop malformed record (${parts.size} fields)")
                removeSent(q, line)
                continue
            }
            if (line in rejectedInProcess) {
                if (rejected.size < budget) rejected += line to parts
            } else {
                fresh += line to parts
            }
        }
        return (fresh + rejected).take(budget)
    }

    private fun replayOne(batch: ReplayBatch, line: String, parts: List<String>) {
        val reported = AtomicBoolean(false)
        val report = { o: EventOutcome ->
            if (reported.compareAndSet(false, true)) onReplayResult(batch, line, parts[0], o)
        }
        try {
            val eventData = LogEventServer.brainDecisionEventData(
                decisionId = parts[0],
                decisionPoint = parts[1],
                chosenActionId = parts[2],
                chosenActionMode = parts[3],
                chosenActionProbability = parts[4].toDoubleOrNull() ?: 0.0,
                eligibleActions = parts[5],
                guardrailsApplied = parts[6],
                segment = parts[7],
                segmentConfidence = parts[8].toDoubleOrNull() ?: 0.0,
                scenario = parts[9],
                isHoldout = parts[10].toIntOrNull() ?: 0,
                policyVersion = parts[11]
            )
            // Trường 13: user_id lúc ghi (review D I3); dòng cũ hoặc trường rỗng → id hiện hành (null).
            val userId = parts.getOrNull(USER_ID_FIELD)?.takeIf { it.isNotEmpty() }
            // Trường 14: ts lúc ghi (F2); dòng 13 trường → null = giờ dựng.
            val tsMs = parts.getOrNull(TS_FIELD)?.toLongOrNull()
            LogEventTransport.sendEventAt(EventNames.BRAIN_DECISION, eventData, report, userId, tsMs)
        } catch (e: Exception) {
            // Giữ lại trong hàng đợi để thử lần sau.
            LogUtil.logE("decision log replay failed, kept for retry: ${e.message}")
            report(EventOutcome.DROPPED)
        }
    }

    /** Chạy trên luồng bất kỳ (callback của `LogEventServer`): việc file đẩy sang luồng nền. */
    private fun onReplayResult(batch: ReplayBatch, line: String, decisionId: String, outcome: EventOutcome) {
        when (outcome) {
            EventOutcome.ACCEPTED -> rejectedInProcess.remove(line)
            EventOutcome.REJECTED -> {
                // Đã logE ("event dropped: http N" hay "… request body not built"). Nằm lại trên đĩa, xếp cuối.
                if (rejectedInProcess.add(line)) LogUtil.logD("decision log: record $decisionId rejected, kept on disk")
            }
            EventOutcome.DROPPED -> Unit   // lý do tạm thời: nằm lại trên đĩa, lần sau gửi lại
        }
        if (outcome == EventOutcome.ACCEPTED) {
            runOnIo("remove") {
                if (removeSent(batch.q, line)) batch.removedOk.incrementAndGet() else batch.removeFailed.incrementAndGet()
            }
        }
        // Quyết định lượt kế trên luồng nền, SAU các lần xoá ở trên (cùng một luồng FIFO).
        if (batch.outstanding.decrementAndGet() == 0) runOnIo("replay next") { finishBatch(batch) }
    }

    /**
     * Chỉ chạy trên luồng nền. Lượt kế chỉ chạy khi đĩa thật sự bớt: có bản được xoá và không bản nào cần
     * xoá mà xoá hỏng — xoá hỏng thì lượt kế chọn lại đúng các bản server vừa nhận, gửi trùng mãi.
     */
    private fun finishBatch(batch: ReplayBatch) {
        val failed = batch.removeFailed.get()
        if (failed > 0) {
            LogUtil.logE(
                "decision log replay stopped: $failed sent record(s) could not be removed from disk; " +
                    "next flushQueue retries"
            )
            return
        }
        if (batch.removedOk.get() == 0) return   // không bản nào rời đĩa (mất mạng, bị từ chối…)
        if (batch.round >= MAX_ROUNDS_PER_FLUSH) {
            LogUtil.logD("decision log replay paused after ${batch.round} rounds; rest on next flushQueue")
            return
        }
        if (queue === batch.q) flushNow(batch.q, batch.round + 1)
    }

    /** Chỉ chạy trên luồng nền. Bộ đếm lấy lại từ file, không đoán theo số dòng định xoá. */
    private fun countQueued(q: DecisionLogQueue) {
        synchronized(countLock) {
            if (queue !== q) return   // đã bị thay bởi một attachQueue sau: không ghi đè bộ đếm của nó
            val n = try {
                q.size()
            } catch (e: Exception) {
                LogUtil.logE("decision log count failed: ${e.message}")
                0
            }
            cachedQueued.set(n)
        }
    }

    /**
     * Chỉ chạy trên luồng nền. @return `true` khi bản ghi không còn trên đĩa: đã xoá (file thật sự ngắn
     * đi), hoặc vốn không có. `false` khi không đọc được file hay xoá không ghi được (đã log).
     */
    private fun removeSent(q: DecisionLogQueue, line: String): Boolean {
        synchronized(countLock) {
            val before = q.peekAll() ?: return false
            if (line !in before) return true
            val remaining = q.removeAll(listOf(line))
            // -1: không đọc được file nên không xoá gì (đã log) — giữ bộ đếm cũ, không ghi -1 ("đang đếm").
            if (remaining >= 0 && queue === q) cachedQueued.set(remaining)
            return remaining in 0 until before.size
        }
    }

    /** Chỉ chạy trên luồng nền, trước lần gửi sống của cùng bản ghi (F-P1): bản ghi xuống đĩa trước khi gửi. */
    private fun enqueueCounted(q: DecisionLogQueue, line: String) {
        synchronized(countLock) {
            if (!q.enqueue(line)) {
                LogUtil.logE("decision log enqueue failed: record not persisted")
                return
            }
            if (queue !== q) return
            val max = queueMaxRecords
            // Chưa đếm xong (-1) thì để nguyên: lượt đếm trên luồng nền đọc file đã có bản ghi này.
            cachedQueued.updateAndGet { n -> if (n < 0) n else minOf(n + 1, max) }
        }
    }

    private fun runOnIo(what: String, task: () -> Unit) {
        try {
            ioExecutor.execute {
                try {
                    task()
                } catch (e: Exception) {
                    LogUtil.logE("decision log $what failed: ${e.message}")
                }
            }
        } catch (e: RejectedExecutionException) {
            LogUtil.logE("decision log $what not scheduled: ${e.message}")
        }
    }

    /**
     * CHỈ DÙNG TRONG TEST. Hàng đợi là process-global: test gắn hàng đợi vào thư mục tạm thì
     * phải gỡ ra, không thì test sau ghi vào thư mục đã bị xoá.
     */
    @JvmSynthetic
    internal fun detachQueueForTest() {
        synchronized(countLock) {
            queue = null
            cachedQueued.set(0)
        }
        replayBatch = null
        rejectedInProcess.clear()
        unsafeUserIdLogged.set(false)
    }

    private const val UNCOUNTED = -1

    /** Trần số bản ghi replay mỗi lượt (fix wave 1 của Task 14a, I2). */
    private const val MAX_REPLAY_PER_FLUSH = 100

    /** Trần số lượt nối nhau của một lần flush (fix wave 2): tối đa 1000 bản mỗi lần `flushQueue`. */
    private const val MAX_ROUNDS_PER_FLUSH = 10
    private const val IO_THREAD_NAME = "appplugin-brain-log"

    /** Ký tự phân tách trường. Chọn ký tự điều khiển vì nó không xuất hiện trong id/tên thật. */
    private const val FIELD_SEP = "\u001F"

    /** Chỉ số của trường `user_id` lúc ghi (trường 13, review D I3); dòng 12 trường của bản trước không có nó. */
    private const val USER_ID_FIELD = 12

    /** Chỉ số của trường `ts_ms` lúc ghi (trường 14, 1.7.0 T6); dòng 13 trường của bản trước không có nó. */
    private const val TS_FIELD = 13

    /** Dòng "player id không lưu được" đã log trong process. */
    private val unsafeUserIdLogged = AtomicBoolean(false)

    /**
     * `user_id` lúc ghi cho dòng hàng đợi: `PlayerIdSource.current()`, trừ khi id chứa [FIELD_SEP] hoặc xuống dòng (làm
     * hỏng dòng của file append-only): khi đó trường rỗng (replay dùng id hiện hành, như dòng cũ) và log lỗi một lần.
     */
    private fun userIdForQueue(): String {
        val id = PlayerIdSource.current()
        if (!id.contains(FIELD_SEP) && !id.contains('\n') && !id.contains('\r')) return id
        if (unsafeUserIdLogged.compareAndSet(false, true)) {
            LogUtil.logE("decision log: player id chứa ký tự phân tách/xuống dòng, bản ghi không lưu user_id (replay dùng id hiện hành)")
        }
        return ""
    }

    /**
     * Nối decision log vào pipeline `LogEventServer` sẵn có của SDK.
     *
     * Được gọi tự động khi `DecisionEngine` khởi tạo. Lý do đặt mặc định thay vì bắt tích hợp
     * viên tự đăng ký: nếu quên, log sẽ **im lặng đi vào hư không** và không ai phát hiện cho
     * tới khi cần phân tích — lúc đó dữ liệu đã mất vĩnh viễn, không tạo lại được.
     *
     * Gọi lại nhiều lần là vô hại (idempotent).
     */
    @Synchronized
    public fun installDefaultSink() {
        if (defaultSinkInstalled) return
        defaultSinkInstalled = true

        registerForwarder { request, response ->
            val q = queue
            // `user_id` lúc ghi, đọc MỘT lần: dòng đĩa (trường 13) và event sống mang cùng giá trị, kể cả khi
            // `configure { playerId }` chạy trước lúc body dựng trên IO (O1 (b)). Rỗng (không lưu được) = id hiện hành.
            val userId = userIdForQueue()
            val tsMs = System.currentTimeMillis()
            if (q == null) {
                sendLive(request, response, null, null, userId, tsMs)   // chưa bật độ bền: không có đĩa, gửi như cũ
                return@registerForwarder
            }
            val line = listOf(
                response.decisionId,
                request.decisionPoint.name,
                response.chosenAction.actionId,
                response.chosenAction.mode.name,
                response.chosenActionProbability.toString(),
                response.eligibleActionIds.joinToString(","),
                response.guardrailsApplied.joinToString(","),
                response.segment?.name ?: "UNKNOWN",
                response.segmentConfidence.toString(),
                response.scenario?.name ?: "UNKNOWN",
                if (response.isHoldout) "1" else "0",
                response.policyVersion,
                userId,
                tsMs.toString()
            ).joinToString(FIELD_SEP)
            // F-P1 (D31): không đụng đĩa trên luồng gọi (main khi từ `paywallFor`). Trên luồng nền, xuống đĩa TRƯỚC
            // rồi mới gửi: app chết sau khi ghi thì bản ghi vẫn còn; FIFO giữ thứ tự các lần `log()`.
            runOnIo("enqueue") {
                enqueueCounted(q, line)
                sendLive(request, response, q, line, userId, tsMs)
            }
        }
        registerFeedbackForwarder { decisionId, event, ts, reward ->
            LogEventServer.logBrainFeedback(decisionId, event, ts, reward)
        }
    }

    /** Gửi sống một quyết định; [q]/[line] khác null thì server nhận (ACCEPTED) → xoá dòng đó trên luồng nền. */
    private fun sendLive(
        request: DecisionRequest,
        response: DecisionResponse,
        q: DecisionLogQueue?,
        line: String?,
        userId: String,
        tsMs: Long
    ) {
        LogEventTransport.sendEventAt(
            EventNames.BRAIN_DECISION,
            LogEventServer.brainDecisionEventData(
                decisionId = response.decisionId,
                decisionPoint = request.decisionPoint.name,
                chosenActionId = response.chosenAction.actionId,
                chosenActionMode = response.chosenAction.mode.name,
                chosenActionProbability = response.chosenActionProbability,
                eligibleActions = response.eligibleActionIds.joinToString(","),
                guardrailsApplied = response.guardrailsApplied.joinToString(","),
                segment = response.segment?.name ?: "UNKNOWN",
                segmentConfidence = response.segmentConfidence,
                scenario = response.scenario?.name ?: "UNKNOWN",
                isHoldout = if (response.isHoldout) 1 else 0,
                policyVersion = response.policyVersion
            ),
            { o ->
                // Callback của Retrofit về main thread: xoá (ghi lại file) trên luồng nền.
                if (o == EventOutcome.ACCEPTED && q != null && line != null) runOnIo("remove") { removeSent(q, line) }
            },
            userId = userId.ifEmpty { null },
            timestampMs = tsMs
        )
    }

    /** Đăng ký thêm nơi nhận decision log (ngoài sink mặc định). */
    public fun registerForwarder(forwarder: (DecisionRequest, DecisionResponse) -> Unit) {
        forwarders.add(forwarder)
    }

    public fun registerFeedbackForwarder(forwarder: (String, String, Long, Double?) -> Unit) {
        feedbackForwarders.add(forwarder)
    }

    /**
     * CHỈ DÙNG TRONG TEST. Danh sách forwarder là process-global: forwarder test này đăng ký
     * sẽ rò sang test khác nếu không xoá. Production không gọi hàm này.
     *
     * Lưu ý: `defaultSinkInstalled` KHÔNG được đặt lại — sink mặc định (nếu đã cài) cũng bị
     * xoá theo, và test nào cần nó thì gọi lại `installDefaultSink()`.
     */
    @JvmSynthetic
    internal fun clearForwardersForTest() {
        forwarders.clear()
        feedbackForwarders.clear()
        defaultSinkInstalled = false
    }

    /**
     * Định danh tiến trình, sinh một lần mỗi lần app khởi động. Phân biệt các lần chạy khác
     * nhau mà không cần đọc/ghi storage.
     */
    private val runId: String by lazy {
        java.lang.Long.toString(System.currentTimeMillis(), 36) +
            "-" + java.lang.Integer.toString((0..0xFFFF).random(), 36)
    }

    private val counter = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Sinh `decision_id` **duy nhất tuyệt đối**.
     *
     * Bản đầu lấy số thứ tự từ `PreferenceHelper`. Đó là lỗi nghiêm trọng bị unit test bắt
     * được: khi `PreferenceHelper` chưa `init(context)` — hoặc khi ghi thất bại âm thầm —
     * `get()` luôn trả về giá trị mặc định, nên **mọi decision_id đều bằng nhau**.
     * Trùng ID nghĩa là không thể khớp outcome với decision, tức toàn bộ off-policy
     * evaluation vô hiệu, mà lại không có dấu hiệu gì lúc chạy.
     *
     * Thiết kế hiện tại không phụ thuộc storage: `runId` phân biệt các lần chạy app,
     * `AtomicLong` đảm bảo duy nhất trong tiến trình kể cả khi gọi từ nhiều luồng.
     */
    public fun nextDecisionId(playerId: String): String =
        "d_${playerId}_${runId}_${counter.incrementAndGet()}"

    public fun log(request: DecisionRequest, response: DecisionResponse) {
        for (forward in forwarders) {
            try {
                forward(request, response)
            } catch (e: Exception) {
                // Lỗi logging không được làm gián đoạn luồng quyết định chính.
            }
        }
    }

    /** FEEDBACK API (doc 06 mục 10): outcome tới sau, khớp lại bằng decisionId.
     * `rewardValue` mang con số bandit dùng để học lên backend cho OPE — null khi chưa có. */
    public fun feedback(
        decisionId: String,
        event: String,
        timestampMs: Long = System.currentTimeMillis(),
        rewardValue: Double? = null
    ) {
        for (forward in feedbackForwarders) {
            try {
                forward(decisionId, event, timestampMs, rewardValue)
            } catch (e: Exception) {
                // Không để lỗi logging làm crash luồng feedback.
            }
        }
    }
}
