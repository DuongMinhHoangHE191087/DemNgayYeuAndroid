package com.app.plugin.logevent

import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Phần gửi của [LogEventServer] (1.6.4 Task 2, tách khỏi `LogEventServer.kt`): service Retrofit, hàng đợi có trần +
 * backoff (gate G6), cổng consent và kill switch `logevent`. [LogEventServer] giữ API và các hàm `log…`, uỷ quyền tới đây.
 */
internal object LogEventTransport {
    private val requestScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val sequenceIdMutex = Mutex()
    // @Volatile: main ghi ở `init`, luồng IO của replay đọc trong `sendEvent`; đọc một lần mỗi event (T1b-m1).
    @Volatile
    private var endpoint: String? = null
    // @Volatile: main thread ghi ở `init`, luồng nền của decision log đọc qua `isInitialized()` (replay lúc mở app).
    @Volatile
    private lateinit var logService: LogService
    private lateinit var apiKey: String

    /** Dòng log của kill switch `logevent` chỉ ghi một lần mỗi process (mỗi event đều bị chặn). */
    private val killLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Dòng "chưa init" (logE) chỉ ghi một lần mỗi process: mỗi event trước `init` đều bị bỏ. */
    private val notInitLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val telemetryOffLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Hàng đợi có trần + backoff (gate G6) giữa `logEventToServer` và Retrofit. `logService` đọc lúc gửi,
     * không lúc tạo: `init` có thể chạy sau khi object đã khởi tạo.
     */
    @Volatile
    private var sender: LogEventSender = newSender()

    /**
     * Thế hệ của [sender]; chỉ [resetSenderForTest] tăng nó. Event dựng xong mà thế hệ đã đổi thì bị bỏ, để
     * event của test trước (còn đang dựng trên `Dispatchers.IO`) không rò vào hàng đợi của test sau.
     */
    private val senderGeneration = java.util.concurrent.atomic.AtomicInteger(0)

    private fun newSender() = LogEventSender(
        queue = LogEventQueue(),
        transport = { json -> logService.logActionsToServer(json.toRequestBody(JSON_MEDIA_TYPE)) },
        schedule = { delayMs, block ->
            requestScope.launch {
                delay(delayMs)
                try {
                    block()
                } catch (e: Exception) {
                    LogUtil.logE("event queue: retry failed: ${e.message}")
                }
            }
        }
    ).also { it.allowed = LogEventControl::sendAllowed }   // P-1: kiểm lại lúc gửi, cả lượt gửi lại

    /**
     * CHỈ DÙNG TRONG TEST: hàng đợi và cờ "đã log kill switch"/"đã log chưa init" là process-global, test sau không được thấy
     * event hay dòng log của test trước. Event còn đang dựng lúc gọi thuộc thế hệ cũ: dựng xong thì bị bỏ
     * (DROPPED), không vào sender mới.
     */
    internal fun resetSenderForTest() {
        senderGeneration.incrementAndGet()
        sender = newSender()
        LogEventControl.resetForTest()
        killLogged.set(false)
        notInitLogged.set(false)
        telemetryOffLogged.set(false)
    }

    /** CHỈ DÙNG TRONG TEST. */
    internal fun pendingEventsForTest(): Int = sender.pendingCount

    /**
     * Số event còn đưa vào hàng đợi được mà không đẩy event cũ nhất ra. `DecisionLogger` dùng để giới hạn
     * mỗi lượt replay backlog: event quảng cáo đang chờ không có bản trên đĩa, bị đẩy ra là mất.
     */
    internal fun eventQueueHeadroom(): Int = sender.headroom

    /** CHỈ DÙNG TRONG TEST. */
    internal fun droppedEventsForTest(): Long = sender.droppedCount

    /** Bộ đếm mỗi process cho dòng `logevent stats` (T5): body đã dựng; kết quả cuối server nhận / từ chối / bỏ. */
    internal val builtCount = AtomicLong(0)
    internal val acceptedCount = AtomicLong(0)
    internal val rejectedCount = AtomicLong(0)
    internal val droppedCount = AtomicLong(0)

    internal fun init(baseEndpoint: String, key: String, isDebugMode: Boolean = false) {
        try {
            this.endpoint = baseEndpoint
            this.apiKey = key

            if (baseEndpoint.isEmpty()) return

            val okHttpClient =
                OkHttpClient.Builder().also { client ->
                    client.retryOnConnectionFailure(true)
                    client.connectTimeout(30, TimeUnit.SECONDS)
                    client.readTimeout(30, TimeUnit.SECONDS)
                    client.protocols(Collections.singletonList(Protocol.HTTP_1_1))
                    client.addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .addHeader("X-API-KEY", key)
                            .addHeader("Content-Type", "application/json")
                            .build()
                        chain.proceed(request)
                    }
                    if (isDebugMode) {
                        Logs.d("------------------debug----------------")
                        val loggingInterceptor = HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                        client.addInterceptor(loggingInterceptor)
                    }
                }.build()

            val retrofit = Retrofit.Builder().baseUrl(baseEndpoint).client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(GsonConverterFactory.create()).build()
            logService = retrofit.create(LogService::class.java)
            // Decision log gắn trước init (AppPluginManager.initPlugin) chờ tới đây mới replay.
            com.app.plugin.brain.DecisionLogger.flushQueue()
            // Tập bền (T6): replay khi tầng qua cổng, DENIED → xoá file; chỉ hẹn luồng đĩa.
            DurableEvents.onTransportReady()
        } catch (e: Exception) {
            Logs.d("Error: $e")
        }
    }

    /** `init` đã dựng service gửi event. Replay decision log chờ điều này (không gửi vào hàng chưa init). */
    internal fun isInitialized(): Boolean = ::logService.isInitialized

    /**
     * Như `LogEventServer.logEventToServer`, báo [EventOutcome] (phân biệt bị từ chối với bị bỏ vì lý do tạm thời).
     *
     * @param userId `user_id` của body thay cho id hiện hành (`LogEventServerHelper.generateGlobalPropertiesData`);
     *   `null` = id hiện hành. Chỉ replay decision log dùng (id lúc ghi, review D I3).
     */
    internal fun sendEvent(
        eventName: String,
        eventData: Map<String, Any>,
        onOutcome: ((EventOutcome) -> Unit)?,
        userId: String? = null
    ) = sendEventAt(eventName, eventData, onOutcome, userId, null)

    /**
     * Như [sendEvent], [timestampMs] = `ts` của body thay giờ dựng (`null` = giờ dựng). Replay decision log dùng `ts_ms`
     * lúc ghi (T6, F2): hàng replay giữ ngày phát.
     */
    internal fun sendEventAt(
        eventName: String,
        eventData: Map<String, Any>,
        onOutcome: ((EventOutcome) -> Unit)?,
        userId: String? = null,
        timestampMs: Long? = null
    ) {
        // Mọi kết quả cuối đi qua bộ đếm (`logevent stats`, T5), rồi tới người gọi.
        val report: (EventOutcome) -> Unit = { o -> count(o); onOutcome?.invoke(o) }
        if (!::logService.isInitialized) {
            val ep = endpoint
            if (ep != null && ep.isEmpty()) {
                if (telemetryOffLogged.compareAndSet(false, true)) android.util.Log.i(LogUtil.TAG, "telemetry off: DATABUCKETS_BASE_URL rỗng, không gửi event (từ $eventName)")
            } else if (notInitLogged.compareAndSet(false, true)) {
                LogUtil.logE("LogEventServer not initialized: dropping events until init (first: $eventName)")
            }
            Logs.d("LogEventServer not initialized, dropping event: $eventName")
            report(EventOutcome.DROPPED)
            return
        }
        // Kill switch `logevent`: không gửi. DROPPED (onResult false) để decision log giữ bản ghi trên đĩa.
        if (killed()) {
            if (killLogged.compareAndSet(false, true)) {
                LogUtil.logE("kill switch: logevent — không gửi event lên server (từ $eventName)")
            }
            report(EventOutcome.DROPPED)
            return
        }
        // logevent_control (T7): tắt hoặc lấy mẫu ra → DROPPED (như kill switch); được giữ ở tỉ lệ < 1 thì mang sample_rate.
        val data = LogEventControl.gate(eventName, eventData, userId) ?: return report(EventOutcome.DROPPED)
        val generation = senderGeneration.get()
        try {
            requestScope.launch {
                val json = try {
                    buildRequestJson(eventName, data, userId, timestampMs).also { builtCount.incrementAndGet() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.logE("event dropped: $eventName — request body not built: ${e.message}")
                    // Cùng bản ghi sẽ hỏng lại mỗi lần: REJECTED để decision log xếp nó cuối (không xoá).
                    report(EventOutcome.REJECTED)
                    return@launch
                }
                if (generation != senderGeneration.get()) {
                    // Chỉ xảy ra trong test (resetSenderForTest): sender của event này đã bị thay.
                    LogUtil.logD("event dropped: $eventName — sender reset while building")
                    report(EventOutcome.DROPPED)
                    return@launch
                }
                Logs.d("Log to server: #event $eventName")
                TelemetryConsentGate.submitOrHold(LogEventQueue.Item(eventName, json, report)) {
                    // Chạy trong monitor của cổng (nhánh PASS/xả): tập bền chỉ giao việc cho luồng đĩa, ghi rồi mới
                    // submit ở đó (sign-off R1 (a)); kill switch → DROPPED, không ghi đĩa.
                    if (it.name in DurableEvents.DURABLE && !killed()) DurableEvents.storeThenSubmit(it, ::submitNow)
                    else submitNow(it)
                }
            }
        } catch (e: Exception) {
            LogUtil.logE("event dropped: $eventName — not scheduled: ${e.message}")
            report(EventOutcome.DROPPED)
        }
    }

    private fun killed() = SdkKillSwitch.current.killsLogevent(BuildConfig.SDK_VERSION)

    /** Vào sender, trừ khi kill switch hay logevent_control enabled=false tới lúc đang giữ/replay (m4, R-7 I2) → DROPPED; gate vẫn đếm cả cái bỏ vào `flushed N`. */
    internal fun submitNow(item: LogEventQueue.Item) {
        if (LogEventControl.sendAllowed()) return sender.submit(item)
        runCatching { item.onOutcome?.invoke(EventOutcome.DROPPED) }
            .onFailure { e -> LogUtil.logE("event onOutcome failed (${item.name}): ${e.message}") }
    }

    private fun count(o: EventOutcome) = when (o) {
        EventOutcome.ACCEPTED -> acceptedCount
        EventOutcome.REJECTED -> rejectedCount
        EventOutcome.DROPPED -> droppedCount
    }.incrementAndGet()

    /**
     * Sequence id, timestamp và `event_id` (envelope v2, [EventEnvelope]) gắn lúc dựng, không lúc gửi: lần gửi lại
     * mang đúng body cũ, nên server khử trùng được theo `event_id`/sequence id.
     */
    private suspend fun buildRequestJson(eventName: String, eventData: Map<String, Any>, userId: String?, timestampMs: Long?): String {
        val sequenceId = sequenceIdMutex.withLock {
            var id = PreferenceHelper.getInstance().get("SEQUENCE_ID", 0)
            id += 1
            PreferenceHelper.getInstance().setValue("SEQUENCE_ID", id)
            id
        }
        return EventEnvelope.wrap(createRequestJson(
            sequenceId = sequenceId,
            eventName = eventName,
            eventData = eventData,
            timestamp = timestampMs ?: System.currentTimeMillis(),
            userProperty = LogEventServerHelper.generateGlobalPropertiesData()
                .let { p -> if (userId != null) p.copy(userId = userId) else p }
        ), EventEnvelope.newEventId())
    }
}
