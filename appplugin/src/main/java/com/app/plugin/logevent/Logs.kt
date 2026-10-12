package com.app.plugin.logevent

import android.util.Log
import com.app.plugin.BuildConfig

internal object Logs {
    fun d(value: String) {
        if (BuildConfig.DEBUG) {
            Log.d("LogEventSerVer", value)
        }
    }
}