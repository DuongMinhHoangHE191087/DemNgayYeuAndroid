package com.app.plugin.brain.policy

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.util.concurrent.TimeUnit

/** Phản hồi HTTP của `/policy*`: mã + thân UTF-8; thân `null` = vắng hoặc vượt [PolicyTransport.MAX_BODY_BYTES]. */
internal class PolicyResponse(val code: Int, val body: String?)

/**
 * Lớp gửi của [PolicyClient] (2.3.0 SECBC): `POST <url>policy/nonce` và `GET <url>policy` với các header cho sẵn.
 * Chỉ gọi trên luồng `appplugin-policy`; ném khi lỗi mạng (client log `policy: error (<Lớp>)`). Unit test dùng bản giả.
 */
internal fun interface PolicyTransport {
    fun execute(method: String, url: String, headers: Map<String, String>): PolicyResponse

    companion object {
        const val MAX_BODY_BYTES: Int = 64 * 1024
    }
}

/**
 * Bản OkHttp: connect 5 s, read 8 s (> deadline giải mã backend 5 s, #22 2.4.0), cả lời gọi ≤ 20 s, không theo redirect (header
 * `X-Api-Key`/`X-Integrity-Token` không đi sang host khác),
 * đọc tối đa 64 KB + 1 byte — dài hơn = thân `null` (client coi là `parse`). Client OkHttp dựng lười trên luồng gọi.
 */
internal class OkHttpPolicyTransport : PolicyTransport {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)   // m2: trần cả lời gọi, server chậm không giữ luồng policy mãi
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override fun execute(method: String, url: String, headers: Map<String, String>): PolicyResponse {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (method == "POST") builder.post(ByteArray(0).toRequestBody(null)) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val source = response.body?.source() ?: return PolicyResponse(response.code, null)
            return PolicyResponse(response.code, readCapped(source))
        }
    }

    internal companion object {
        private const val TIMEOUT_S = 5L
        private const val READ_TIMEOUT_S = 8L   // 2.4.0 #22: backend `integrity.py` _HTTP_TIMEOUT_S = 5 s
        private const val CALL_TIMEOUT_S = 20L

        /** Thân UTF-8 nếu ≤ [PolicyTransport.MAX_BODY_BYTES], ngược lại `null` (không đọc hết thân lớn vào bộ nhớ). */
        fun readCapped(source: BufferedSource): String? {
            if (source.request(PolicyTransport.MAX_BODY_BYTES + 1L)) return null
            return source.buffer.readUtf8()
        }
    }
}
