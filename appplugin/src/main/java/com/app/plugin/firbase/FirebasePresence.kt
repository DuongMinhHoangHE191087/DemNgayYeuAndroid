package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.app.AppPluginManager
import com.app.plugin.until.LogUtil
import com.google.firebase.FirebaseApp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process không có FirebaseApp mặc định (vd. bản debug không `google-services`, D7-IM) thì lời gọi Analytics/FCM ném "Default
 * FirebaseApp is not initialized" và SDK log E mỗi event (`im/e-im-report.md` #9, #15). 2.3.0 ROB3 (ROB-2 nhẹ, D21-IM, D22-230):
 * `FirHelper`, `PaidEvent`, `FcmConsent` hỏi đây trước; vắng → trả sớm, một dòng W mỗi process. Chỉ ghi nhớ "có": vắng thì hỏi
 * lại mỗi lần (rẻ: chép danh sách), nên app khởi tạo Firebase sau `super.onCreate()` có lại Analytics/FCM của SDK từ lần kế.
 */
internal object FirebasePresence {
    const val MISSING = "firebase: không có FirebaseApp mặc định — SDK bỏ Analytics/FCM của mình (FirHelper, ad_impression, FCM) tới khi app khởi tạo Firebase"
    private val defaultProbe: () -> Boolean? =
        { AppPluginManager.instance.getContext()?.let { ctx -> FirebaseApp.getApps(ctx).any { it.name == FirebaseApp.DEFAULT_APP_NAME } } }
    private val defaultWarn: (String) -> Unit = { Log.w(LogUtil.TAG, it) }

    /** Seam test. `null` = chưa biết (chưa có context: trước `initPlugin`) → coi như có, không ghi nhớ. */
    @Volatile var probe: () -> Boolean? = defaultProbe
    @Volatile var warn: (String) -> Unit = defaultWarn
    @Volatile private var present = false
    private val warned = AtomicBoolean(false)

    /** `false` = chưa có FirebaseApp mặc định: người gọi bỏ lời gọi Firebase. Probe ném → coi như có (đường cũ), W một lần. */
    fun available(): Boolean {
        if (present) return true
        val r = runCatching(probe)
        if (r.getOrNull() == true) present = true
        if (r.getOrNull() != false && r.isSuccess) return true
        if (warned.compareAndSet(false, true)) warn(r.exceptionOrNull()?.let { "firebase: không kiểm được FirebaseApp (${it.message})" } ?: MISSING)
        return r.isFailure
    }

    fun resetForTest() { present = false; warned.set(false); probe = defaultProbe; warn = defaultWarn }
}
