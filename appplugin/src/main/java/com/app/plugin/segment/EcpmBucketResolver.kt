package com.app.plugin.segment

import android.content.Context
import com.app.plugin.until.PreUtil

/**
 * Rolling-window mean of recent ad-impression eCPM values, derived into a HIGH/LOW
 * bucket consumed by [SegmentScorer]. The window is persisted across process death
 * via [PreUtil] so the signal survives normal app lifecycle, mirroring how
 * [UserSegmentEngine] persists its other rolling state (e.g. KEY_SESSION_TIMESTAMPS).
 *
 * Bucket semantics: window mean >= [thresholdMicros] is HIGH, else LOW.
 * The scorer's own `ecpmMinImpressions` threshold gates score effects until enough
 * impressions have accumulated; the resolver does not duplicate that check.
 *
 * Window size and threshold are Remote-Config overridable per app via
 * `seg_ecpm_window_size` and `seg_ecpm_high_threshold_micros` — defaults match
 * [DEFAULT_WINDOW_SIZE] and [DEFAULT_HIGH_BUCKET_THRESHOLD_MICROS].
 */
// init() only ever passes context.applicationContext, which lives as long as the process (SC fork).
@android.annotation.SuppressLint("StaticFieldLeak")
internal class EcpmBucketResolver private constructor(
    private val context: Context?,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val thresholdMicros: Long = DEFAULT_HIGH_BUCKET_THRESHOLD_MICROS
) {
    private val lock = Any()
    private val window: ArrayDeque<Long> = ArrayDeque()

    init {
        if (context != null) {
            val str = PreUtil.getString(context, KEY_ECPM_WINDOW, "")
            if (str.isNotEmpty()) {
                str.split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
                    .takeLast(windowSize)
                    .forEach { window.addLast(it) }
            }
        }
    }

    fun recordImpression(valueMicros: Long): EcpmBucket = synchronized(lock) {
        window.addLast(valueMicros)
        while (window.size > windowSize) {
            window.removeFirst()
        }
        persist()
        bucketFromWindow()!!
    }

    fun currentBucket(): EcpmBucket? = synchronized(lock) {
        bucketFromWindow()
    }

    private fun bucketFromWindow(): EcpmBucket? {
        if (window.isEmpty()) return null
        val mean = window.sum() / window.size
        return if (mean >= thresholdMicros) EcpmBucket.HIGH else EcpmBucket.LOW
    }

    private fun persist() {
        val ctx = context ?: return
        PreUtil.setString(ctx, KEY_ECPM_WINDOW, window.joinToString(","))
    }

    companion object {
        @Volatile
        private var sharedInstance: EcpmBucketResolver? = null

        const val DEFAULT_WINDOW_SIZE = 10
        const val DEFAULT_HIGH_BUCKET_THRESHOLD_MICROS = 500_000L
        private const val KEY_ECPM_WINDOW = "seg_ecpm_window"
        private const val RC_KEY_WINDOW_SIZE = "seg_ecpm_window_size"
        private const val RC_KEY_THRESHOLD_MICROS = "seg_ecpm_high_threshold_micros"

        fun init(context: Context) {
            val windowSize = SegmentHooks.rcLong(RC_KEY_WINDOW_SIZE)?.toInt()?.coerceAtLeast(1) ?: DEFAULT_WINDOW_SIZE
            val thresholdMicros = SegmentHooks.rcLong(RC_KEY_THRESHOLD_MICROS)?.coerceAtLeast(0L)
                ?: DEFAULT_HIGH_BUCKET_THRESHOLD_MICROS
            sharedInstance = EcpmBucketResolver(
                context.applicationContext,
                windowSize = windowSize,
                thresholdMicros = thresholdMicros
            )
        }

        val instance: EcpmBucketResolver
            get() = sharedInstance ?: synchronized(this) {
                sharedInstance ?: EcpmBucketResolver(null).also { sharedInstance = it }
            }

        /**
         * Test-only factory: an in-memory resolver with no persistence dependency.
         * Each call returns a fresh instance so tests don't share state. Uses the
         * documented defaults so existing test expectations remain stable.
         */
        fun newForTesting(): EcpmBucketResolver = EcpmBucketResolver(null)
    }
}

