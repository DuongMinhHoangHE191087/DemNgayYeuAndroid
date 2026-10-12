package com.app.plugin.logevent

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

internal interface LogService {
    @POST("push")
    public fun logActionsToServer(
        @Body requestBody: RequestBody
    ): Call<ResponseBody>
}