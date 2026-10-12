package com.app.plugin.brain.policy

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.util.concurrent.TimeUnit

/**
 * Nguồn token Play Integrity cho `/policy` (2.3.0 SECBC, hợp đồng C). [request] chạy trên luồng `appplugin-policy`,
 * chặn tối đa `timeoutMs`; `null` = mọi lỗi (hết giờ, Play services vắng, ném) — client khi đó không gọi `GET /policy`.
 * Unit test dùng nguồn giả; harness (`BrainPolicyHarness`, extra `policy_fake_token`) dùng `IntegrityTokenSource { _, _ -> t }`.
 */
internal fun interface IntegrityTokenSource {
    fun request(nonce: String, timeoutMs: Long): String?
}

/**
 * Classic Play Integrity (`IntegrityTokenRequest` + nonce của server + cloud project number). Không test JVM được và
 * không gọi Google thật trước O3: chỉ dựng khi app khai `com.app.plugin.INTEGRITY_CLOUD_PROJECT`. Lỗi → `null`, không log
 * (thông điệp lỗi có thể mang nonce).
 */
internal class PlayIntegrityTokenSource(context: Context, private val cloudProject: Long) : IntegrityTokenSource {
    private val app: Context = context.applicationContext ?: context

    override fun request(nonce: String, timeoutMs: Long): String? = try {
        val manager = IntegrityManagerFactory.create(app)
        val request = IntegrityTokenRequest.builder().setNonce(nonce).setCloudProjectNumber(cloudProject).build()
        Tasks.await(manager.requestIntegrityToken(request), timeoutMs, TimeUnit.MILLISECONDS).token()
    } catch (e: Throwable) {
        null
    }
}
