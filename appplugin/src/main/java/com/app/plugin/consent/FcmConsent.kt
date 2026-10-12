package com.app.plugin.consent

import android.content.Context
import android.util.Log
import com.app.plugin.firbase.FirebasePresence
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Khi nào FCM được chạy: auto-init và token. Không có token thì không có push, nên push trước consent
 * (điều Task 9 cấm) bị chặn ngay ở đây.
 *
 * Luật: tầng `PARTIAL`/`GRANTED`, hoặc ngoài GDPR (`IABTCF_gdprApplies = 0`) mà tầng không phải
 * `DENIED`. `UNKNOWN` và `gdprApplies == null` (không biết) không mở FCM; `DENIED` không bao giờ mở, kể
 * cả khi `IABTCF_gdprApplies = 0` (có thể là giá trị cũ còn lại khi người dùng đã vào EEA) (Điều 13).
 *
 * Hai điều về firebase-messaging 24.0.2 (đọc bytecode) quyết định cách làm:
 * - `getToken()` không xét cờ auto-init: gọi là tạo token. Nên lời gọi token phải nằm sau luật này.
 * - Bật auto-init (`setAutoInitEnabled(true)`) tự đồng bộ token. Nên auto-init cũng theo luật này,
 *   không bật cho người từ chối.
 *
 * Mọi lần gán `isAutoInitEnabled` và lời gọi token chạy trên luồng nền [executor] (`appplugin-fcm`), không trên
 * luồng gọi (main): gán cờ làm Firebase tạo `Store` đồng bộ (`AutoInit.setEnabled` → `startSyncIfNecessary` →
 * `getStore` → `Store.checkForRestore` → `noBackupFilesDir`), tức DiskRead ×2 và DiskWrite trên main — 3 vi phạm
 * StrictMode trên máy thật (DOC/66 D1, `DOC/testing/device-gate-65c9b673-20260925-092155.log`; emulator không lộ vì
 * thư mục đã có). Lúc mở app và sau consent dùng cùng một hàng đợi, nên `false` rồi `true` tới Firebase đúng thứ
 * tự; token lấy trong cùng việc, sau cờ.
 */
internal object FcmConsent {

    private const val THREAD_NAME = "appplugin-fcm"

    /** Prefs của SDK: `true` khi `deleteToken()` sau lúc rút consent chưa thành công; lần mở kế thử lại (review C Minor 6). */
    internal const val KEY_DELETE_PENDING = "mem_fcm_delete_pending"

    /** Luồng nền của FCM: một luồng daemon, tạo lần đầu có việc; cũng chạy hẹn giờ của [delayed]. */
    private val fcmThread: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, THREAD_NAME).apply { isDaemon = true } }
    }

    /**
     * Hàng đợi của mọi lần đặt cờ auto-init và lấy token: một luồng ([fcmThread]), nên việc tới Firebase
     * đúng thứ tự được xếp. Test thay bằng executor giữ Runnable lại.
     */
    internal var executor: Executor = Executor { fcmThread.execute(it) }

    /** Hẹn một Runnable sau N ms trên [fcmThread] (hạn của việc token, Task J2). Test thay bằng hẹn giờ tay. */
    internal var delayed: (Runnable, Long) -> Unit = { r, ms -> fcmThread.schedule(r, ms, TimeUnit.MILLISECONDS) }

    /** Test: làn token về trạng thái rảnh (việc đang bay/đang chờ của test trước không kẹt test sau). */
    internal fun resetForTest() { tokenLane.reset(); holdRetryArmed.set(false); holdRetryGen++ }

    /** Việc token (Task I6): không có, lấy token, xoá token. */
    private const val OP_NONE = 0
    private const val OP_FETCH = 1
    private const val OP_DELETE = 2

    /**
     * Hạn của một việc token đang bay (Task J2, review Task I M2): Task của Firebase không bao giờ hoàn tất thì sau
     * hạn này việc đó coi là xong-thất-bại (một dòng logE), việc đang chờ chạy tiếp; completion tới muộn bị bỏ.
     */
    private const val TOKEN_OP_TIMEOUT_MS = 30_000L
    private const val HOLD_RETRY_FIRST_MS = 500L   // #31 [retryHold]
    private const val HOLD_RETRY_MAX = 8
    private val holdRetryArmed = AtomicBoolean(false)
    @Volatile private var holdRetryGen = 0

    /**
     * Làn token dùng chung của process (Task J2): lần xoá lại lúc mở app ([holdAutoInitUntilConsent]) và mọi việc của
     * [Follower] xếp cùng một làn, nên lần lấy token sau consent trong cùng phiên chờ lần xoá đó xong.
     */
    private val tokenLane = TokenLane()

    private fun opName(op: Int): String = when (op) {
        OP_FETCH -> "fetch"
        OP_DELETE -> "delete"
        else -> "none"
    }

    /** Một việc token: loại [op] và cách chạy nó; [start] nhận hàm báo xong (gọi đúng một lần, kể cả khi lỗi/ném). */
    private class TokenOp(val op: Int, val start: (onDone: () -> Unit) -> Unit)

    /**
     * Một việc token chạy tại một thời điểm (Task I6). Việc tới khi việc trước chưa xong thì chờ ở [queued]; hai việc
     * ngược chiều cùng chờ thì huỷ nhau (việc đang chạy đã khớp tầng cuối). Mỗi việc được nhận mang một mã ([seq]):
     * completion và hạn ([TOKEN_OP_TIMEOUT_MS], hẹn qua [delayed] lúc nhận) chỉ có hiệu lực khi mã còn là việc đang
     * bay — completion muộn sau hạn, hay hạn cũ sau completion, không đụng việc kế. Mọi trường chỉ đọc/ghi trong
     * `synchronized(this)`; khoá không bao giờ giữ khi gọi ra ngoài (chỉ `executor.execute` và [delayed], đều chỉ xếp
     * hàng).
     */
    private class TokenLane {
        private var inFlight = OP_NONE
        private var seq = 0L
        private var queued: TokenOp? = null

        /**
         * Rảnh thì nhận [job]: trả mã việc (> 0), người gọi chạy `job.start(doneCallback(mã))` trên [executor] (sau cờ
         * auto-init của cùng lần gọi). Bận thì xếp chờ hoặc huỷ việc ngược chiều đang chờ, trả 0.
         */
        @Synchronized
        fun claimOrQueue(job: TokenOp): Long {
            if (inFlight == OP_NONE) return claim(job.op)
            val waiting = queued
            queued = if (waiting == null || waiting.op == job.op) job else null
            Log.d(
                LogUtil.TAG,
                if (queued == null) "fcm token ${opName(job.op)} cancels queued ${opName(waiting?.op ?: OP_NONE)} (in flight: ${opName(inFlight)})"
                else "fcm token ${opName(job.op)} deferred until ${opName(inFlight)} completes"
            )
            return 0L
        }

        /** Trong khoá: [op] thành việc đang bay với mã mới, hẹn hạn cho mã đó. */
        private fun claim(op: Int): Long {
            inFlight = op
            val id = ++seq
            delayed(Runnable { finish(id, timedOut = true) }, TOKEN_OP_TIMEOUT_MS)
            return id
        }

        /** Hàm báo xong cho việc mã [id]: chạy đúng một lần dù được gọi nhiều lần. */
        fun doneCallback(id: Long): () -> Unit {
            val once = AtomicBoolean(false)
            return { if (once.compareAndSet(false, true)) finish(id, timedOut = false) }
        }

        /**
         * Việc mã [id] xong (completion, hoặc hết hạn khi [timedOut]): việc chờ (nếu có) thành việc đang bay và chạy.
         * Final review Minor 5: việc xoá hết hạn thì đặt cờ [KEY_DELETE_PENDING] như khi xoá lỗi, để lần mở kế xoá lại;
         * completion thật tới muộn vẫn sửa cờ (listener của `deleteToken()`). Cờ ghi ngoài khoá của làn (prefs có thể
         * chạm đĩa lần đầu), trên luồng hẹn giờ ([fcmThread]).
         */
        private fun finish(id: Long, timedOut: Boolean) {
            if (finishLocked(id, timedOut) == OP_DELETE && timedOut) setDeletePending(true)
        }

        /** Trong khoá: kết thúc việc mã [id]; trả loại việc vừa kết thúc, [OP_NONE] khi [id] không còn là việc đang bay. */
        @Synchronized
        private fun finishLocked(id: Long, timedOut: Boolean): Int {
            if (id != seq || inFlight == OP_NONE) {
                if (!timedOut) Log.d(LogUtil.TAG, "fcm token op completion ignored: not the op in flight (late after timeout)")
                return OP_NONE
            }
            val finished = inFlight
            if (timedOut) {
                LogUtil.logE("fcm token ${opName(finished)} did not complete within $TOKEN_OP_TIMEOUT_MS ms; treated as failed")
            }
            val next = queued
            queued = null
            inFlight = OP_NONE
            if (next != null) {
                val nextId = claim(next.op)
                executor.execute { next.start(doneCallback(nextId)) }
            }
            return finished
        }

        @Synchronized
        fun reset() {
            inFlight = OP_NONE
            queued = null
            seq++
        }
    }

    fun allows(tier: ConsentTier, gdprApplies: Boolean?): Boolean = ConsentRules.tierAllows(tier, gdprApplies)

    /**
     * Lúc mở app, trước khi UMP trả lời: tắt auto-init nếu tầng hiện tại (đã khôi phục từ phiên trước)
     * chưa cho phép FCM. Firebase đọc cờ đã lưu trước cờ manifest, và bản ≤ 1.3.x lưu `true` mỗi lần
     * mở app, nên manifest `firebase_messaging_auto_init_enabled=false` không đủ trên các máy đó.
     *
     * Lần trước rút consent mà `deleteToken()` lỗi (cờ [KEY_DELETE_PENDING]) và tầng vẫn chưa cho phép: xoá lại một lần,
     * cùng việc, sau cờ auto-init (review C Minor 6), qua [tokenLane] như mọi việc token của [Follower] (Task J2): lần
     * lấy token sau consent trong cùng phiên chờ lần xoá này xong. Cờ đọc trên [executor], không trên luồng gọi.
     *
     * Tầng đã cho phép thì không đụng vào FCM. Cờ gán trên [executor]. Lỗi thì log, không ném. Việc trên [executor] chỉ giữ
     * `context.applicationContext` (Task L mục 4); [context] chỉ đọc trên luồng gọi. FirebaseApp vắng → [retryHold] (2.4.0 #31): cờ
     * auto-init `true` đã lưu (≤ 1.3.x) không chạy trước consent khi app tự khởi tạo Firebase muộn; [holdRetryGen] đổi = huỷ chuỗi hẹn.
     */
    fun holdAutoInitUntilConsent(context: Context) {
        if (!FirebasePresence.available()) return retryHold(context.applicationContext ?: context, holdRetryGen, 0)
        try {
            val tier = ConsentState.tier()
            val gdprApplies = TcfConsent.gdprApplies(context)
            if (allows(tier, gdprApplies)) return
            // Task L mục 4: việc dưới có thể nằm trong làn tới 30 s+ — chỉ giữ application context, không giữ context người gọi
            // (vd. Activity). `?: context` chỉ để test: `Context` thật luôn có `applicationContext` khác null; context giả trả null.
            val appContext = context.applicationContext ?: context
            executor.execute {
                try {   // 2.4.0 m3: đọc lại tầng lúc chạy — `follow` cho phép đã xếp trước thì không ghi `false` đè lên
                    if (!allows(ConsentState.tier(), TcfConsent.gdprApplies(appContext))) FirebaseMessaging.getInstance().isAutoInitEnabled = false
                    // Log thẳng, không qua cờ LogUtil: xếp hàng từ Application.onCreate, có thể trước khi cờ log bật.
                    Log.d(LogUtil.TAG, "fcm auto-init held until consent (tier=$tier, gdprApplies=$gdprApplies)")
                } catch (e: Exception) {
                    LogUtil.logE("fcm auto-init hold failed: ${e.message}")
                }
                if (isDeletePending()) {
                    Log.d(LogUtil.TAG, "fcm token delete retried at launch: earlier delete did not succeed (tier=$tier)")
                    // Task J2: cùng làn với Follower, để lần lấy token sau consent trong phiên này chờ lần xoá này xong.
                    // Review Task J Minor 2: Follower có thể đã nhận việc lấy token (tầng cho phép lại) trước khi việc này
                    // chạy → việc xoá xếp sau; tới lượt thì kiểm lại tầng (ngoài khoá), đã cho phép thì không xoá token vừa
                    // lấy (nợ đã được Follower xoá), chỉ mở làn. Task K mục 3: `gdprApplies` cũng đọc lại lúc tới lượt (trên
                    // executor), không dùng giá trị chụp lúc gọi: UMP có thể đã ghi `IABTCF_gdprApplies = 0` (ngoài GDPR +
                    // UNKNOWN = cho phép) sau khi hàm này được gọi.
                    val job = TokenOp(OP_DELETE) { done ->
                        if (allows(ConsentState.tier(), TcfConsent.gdprApplies(appContext))) {
                            Log.d(LogUtil.TAG, "fcm token delete retry at launch skipped: tier allows FCM now")
                            done()
                        } else {
                            deleteToken(done)
                        }
                    }
                    val id = tokenLane.claimOrQueue(job)
                    if (id != 0L) job.start(tokenLane.doneCallback(id))
                }
            }
        } catch (e: Exception) {
            LogUtil.logE("fcm auto-init hold failed: ${e.message}")
        }
    }

    /** 2.4.0 #31 (F1a m11): FirebaseApp vắng → hỏi lại (0,5 s, 1 s… ×[HOLD_RETRY_MAX]); có → chạy lại [holdAutoInitUntilConsent] 1 lần. */
    private fun retryHold(app: Context, gen: Int, attempt: Int) {
        if (gen != holdRetryGen || attempt >= HOLD_RETRY_MAX || (attempt == 0 && !holdRetryArmed.compareAndSet(false, true))) return
        delayed(Runnable {
            if (gen == holdRetryGen && FirebasePresence.available()) holdAutoInitUntilConsent(app) else retryHold(app, gen, attempt + 1)
        }, HOLD_RETRY_FIRST_MS shl attempt)
    }

    /**
     * Sau khi UMP trả lời: đặt auto-init theo tầng mỗi lần được gọi (lúc task `analytics` chạy và mỗi
     * lần tầng đổi), và lấy token một lần mỗi lần tầng cho phép sau khi chưa có token (lần đầu cho cả process).
     *
     * Rút lại consent (tầng chuyển từ cho phép sang không cho phép giữa hai lần [follow]; lần đầu không tính) tắt
     * auto-init (cờ được lưu, phiên sau không tự đồng bộ) rồi xoá token đã có (`deleteToken()`, #8 DOC/66), để push
     * không còn tới người đã rút. Token được coi là chưa lấy: lần cấp lại lấy token mới. Xoá lỗi thì lần mở kế thử lại
     * ([holdAutoInitUntilConsent]); tầng cho phép lại thì bỏ nợ đó.
     */
    class Follower(private val fetchToken: (onDone: () -> Unit) -> Unit) {
        private val tokenFetched = AtomicBoolean(false)

        /** `allowed` của lần [follow] trước; `null` trước lần đầu. Chỉ đọc/ghi trong `synchronized(this)`. */
        private var lastAllowed: Boolean? = null

        /**
         * Đọc tầng, quyết định và xếp hàng trong cùng một khoá (review C Minor 2): hai lần `follow` trên hai luồng (cổng
         * mở trên main, `setTier` của app trên luồng nền) xếp hàng đúng thứ tự quyết định, và lần xếp sau cùng mang tầng
         * mới nhất lúc nó giữ khoá. Không thì việc xoá token có thể chạy sau lần lấy mới, hoặc `true` tới Firebase cuối
         * cùng khi tầng là DENIED. `executor.execute` chỉ xếp hàng (luồng nền riêng), không gọi ngược vào đây; khoá này
         * không bao giờ giữ khi gọi hàm `@Synchronized` của `ConsentState` (`tier()` là đọc volatile).
         *
         * Lấy và xoá token là Task bất đồng bộ: thứ tự xếp hàng không phải thứ tự xong (review C Minor 3). Nên một việc
         * token chỉ bắt đầu khi việc trước đã báo xong, qua [tokenLane] (Task I6, J2): cấp lại khi `deleteToken()` chưa
         * xong (kể cả lần xoá lại lúc mở app) thì lấy token trong completion của delete, rút consent khi đang lấy token
         * thì xoá sau khi lần lấy xong. Hai quyết định ngược nhau cùng chờ thì huỷ nhau (việc đang chạy đã khớp tầng
         * cuối). Việc đang bay quá [TOKEN_OP_TIMEOUT_MS] coi là xong-thất-bại. Cờ auto-init vẫn theo tầng ngay ở mỗi lần
         * gọi. Khoá của Follower giữ khi gọi [TokenLane.claimOrQueue] (khoá của làn), thứ tự luôn Follower → làn; làn
         * không bao giờ gọi ngược vào Follower trong khoá của nó.
         */
        fun follow(context: Context) {
            if (!FirebasePresence.available()) return // không có FirebaseApp: không cờ, không token (ROB3)
            // Quyết định trên luồng gọi, trước khi xếp hàng: hai lần follow liên tiếp không lấy hai token, không xoá hai
            // lần. Rút consent đặt lại cờ token, nên lần cho phép kế lấy token mới (sau khi việc xoá xong).
            synchronized(this) {
                val tier = ConsentState.tier()
                val allowed = allows(tier, TcfConsent.gdprApplies(context))
                val withdrawn = lastAllowed == true && !allowed
                lastAllowed = allowed
                if (withdrawn) tokenFetched.set(false)
                val fetch = allowed && tokenFetched.compareAndSet(false, true)
                val wanted = when {
                    withdrawn -> OP_DELETE
                    fetch -> OP_FETCH
                    else -> OP_NONE
                }
                val job = if (wanted == OP_NONE) null else TokenOp(wanted) { done -> runTokenOp(wanted, done) }
                val id = if (job == null) 0L else tokenLane.claimOrQueue(job)
                executor.execute { applyOnFcmThread(tier, allowed, job.takeIf { id != 0L }, id) }
            }
        }

        /**
         * Trên [executor]: cờ auto-init trước, rồi việc token [job] nếu làn đã nhận nó (mã [id]). Mỗi bước tự bắt lỗi và
         * logE, không ném.
         */
        private fun applyOnFcmThread(tier: ConsentTier, allowed: Boolean, job: TokenOp?, id: Long) {
            try {
                FirebaseMessaging.getInstance().isAutoInitEnabled = allowed
                Log.d(
                    LogUtil.TAG,
                    if (allowed) "fcm auto-init enabled after consent (tier=$tier)"
                    else "fcm held after consent answer (tier=$tier): auto-init off, no token"
                )
            } catch (e: Exception) {
                LogUtil.logE("fcm auto-init update failed: ${e.message}")
            }
            if (job?.op == OP_DELETE) job.start(tokenLane.doneCallback(id))
            if (allowed) clearDeletePending()
            if (job?.op == OP_FETCH) job.start(tokenLane.doneCallback(id))
        }

        /** Trên [executor]: chạy [op]; xong (kể cả lỗi hay ném) thì [onDone] đúng một lần. */
        private fun runTokenOp(op: Int, onDone: () -> Unit) {
            if (op == OP_DELETE) {
                deleteToken(onDone)
                return
            }
            try {
                fetchToken(onDone)
            } catch (e: Exception) {
                LogUtil.logE("fcm token fetch after consent failed: ${e.message}")
                onDone()
            }
        }

    }

    /**
     * Xoá token hiện có sau khi rút consent; kết quả được log (listener chạy trên main). Lỗi thì logE, không ném, và
     * đặt cờ [KEY_DELETE_PENDING] để lần mở kế thử lại; thành công thì xoá cờ. [onDone] chạy đúng một lần sau kết quả
     * (hoặc sau lỗi ném ngay), để việc token kế ([Follower]) chỉ bắt đầu sau đó.
     */
    private fun deleteToken(onDone: () -> Unit = {}) {
        try {
            FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
                try {
                    if (task.isSuccessful) {
                        Log.d(LogUtil.TAG, "fcm token deleted after consent withdrawn")
                        setDeletePending(false)
                    } else {
                        LogUtil.logE("fcm token delete after consent withdrawn failed: ${task.exception?.message}")
                        setDeletePending(true)
                    }
                } finally {
                    onDone()
                }
            }
        } catch (e: Exception) {
            LogUtil.logE("fcm token delete after consent withdrawn failed: ${e.message}")
            setDeletePending(true)
            onDone()
        }
    }

    /** Trên [executor]: đọc prefs (có thể chạm đĩa lần đầu). */
    private fun isDeletePending(): Boolean = PreferenceHelper.getInstance().get(KEY_DELETE_PENDING, false)

    /** Trên [executor]: tầng cho phép lại, token mới sẽ lấy; chỉ ghi khi cờ còn. */
    private fun clearDeletePending() {
        if (isDeletePending()) setDeletePending(false)
    }

    /** Ghi bằng `apply` (không chặn luồng gọi). Prefs chưa khởi tạo thì logE: cờ không giữ được qua lần mở kế. */
    private fun setDeletePending(pending: Boolean) {
        val prefs = PreferenceHelper.getInstance()
        if (prefs.preferences == null) {
            LogUtil.logE("fcm token delete retry flag not saved: PreferenceHelper not initialized (pending=$pending)")
            return
        }
        if (pending) prefs.setValue(KEY_DELETE_PENDING, true) else prefs.remove(KEY_DELETE_PENDING)
    }
}
