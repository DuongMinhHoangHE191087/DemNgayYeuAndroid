package com.app.plugin.firbase

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.app.plugin.app.AppPluginManager
import com.app.plugin.until.LogUtil

internal class FirHelperWorker (appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.d(LogUtil.TAG, "Performing long running task in scheduled job")
        val data = inputData.keyValueMap
        Log.d(LogUtil.TAG, "Worker input data payload: $data")
        return Result.success()
    }
}