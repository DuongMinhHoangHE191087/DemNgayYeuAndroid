package com.app.plugin.until

import android.content.Context
import androidx.core.content.edit

internal class PreUtil {

    public companion object {
        /** Internal để `PrefsWarmup` nạp sẵn file này trên luồng nền. */
        internal const val PREFF_FILE = "App_prefer_file"

        public fun getInt(context: Context, key: String, defva: Int): Int {
            val settings = context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE)
            return settings.getInt(key, defva)
        }

        public fun setInt(context: Context, key: String, va: Int) {
            context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE).edit { putInt(key, va) }
        }

        public fun getFloat(context: Context, key: String, defva: Float): Float {
            val settings = context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE)
            return settings.getFloat(key, defva)
        }

        public fun setFloat(context: Context, key: String, va: Float) {
            context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE).edit { putFloat(key, va) }
        }

        public fun getString(context: Context, key: String, defva: String): String {
            val settings = context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE)
            val re = settings.getString(key, defva)
            return re ?: defva
        }

        public fun setString(context: Context, key: String, va: String) {
            context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE).edit { putString(key, va) }
        }

        // NEW-06 FIX: thêm getLong/setLong để tránh truncate Long → Int khi lưu timestamp
        public fun getLong(context: Context, key: String, defva: Long): Long {
            val settings = context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE)
            return settings.getLong(key, defva)
        }

        public fun setLong(context: Context, key: String, va: Long) {
            context.getSharedPreferences(PREFF_FILE, Context.MODE_PRIVATE).edit { putLong(key, va) }
        }
    }
}