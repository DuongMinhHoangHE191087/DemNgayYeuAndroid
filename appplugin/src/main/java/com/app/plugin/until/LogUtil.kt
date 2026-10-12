package com.app.plugin.until

import android.util.Log

public class LogUtil {
    public companion object {
        public const val TAG: String = "AppPlugin"
        private var flagLog:Int = 1

        public fun setFlagLog(flag:Int) {
            flagLog = flag
        }

        public fun logD(msg:String) {
            if (flagLog > 2) {
                Log.d(TAG, msg)
            }
        }

        public fun logW(msg:String) {
            if (flagLog > 1) {
                Log.w(TAG, msg)
            }
        }

        public fun logE(msg:String) {
            if (flagLog > 0) {
                Log.e(TAG, msg)
            }
        }
    }
}