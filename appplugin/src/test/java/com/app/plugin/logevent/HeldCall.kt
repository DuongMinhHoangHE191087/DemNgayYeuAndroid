package com.app.plugin.logevent

import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

/**
 * `Call` của Retrofit giữ callback lại: test tự quyết khi nào và trả lời gì. Không có I/O, trả lời chạy
 * đồng bộ trên luồng gọi [respond]/[fail].
 */
internal class HeldCall(val body: String) : Call<ResponseBody> {
    @Volatile
    private var callback: Callback<ResponseBody>? = null

    override fun enqueue(callback: Callback<ResponseBody>) {
        this.callback = callback
    }

    fun respond(response: Response<ResponseBody>) =
        checkNotNull(callback) { "call chưa enqueue" }.onResponse(this, response)

    fun fail(t: Throwable = IOException("offline")) =
        checkNotNull(callback) { "call chưa enqueue" }.onFailure(this, t)

    override fun execute(): Response<ResponseBody> = throw UnsupportedOperationException("test chỉ dùng enqueue")
    override fun isExecuted(): Boolean = callback != null
    override fun cancel() = Unit
    override fun isCanceled(): Boolean = false
    override fun clone(): Call<ResponseBody> = HeldCall(body)
    override fun request(): Request = Request.Builder().url("http://localhost/").build()
    override fun timeout(): Timeout = Timeout.NONE
}
