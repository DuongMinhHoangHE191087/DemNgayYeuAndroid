package com.app.plugin.until

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

public class PreferenceHelper {

    @Volatile
    public var preferences: SharedPreferences? = null
    public fun init(context: Context) {
        preferences = context.getSharedPreferences("XgameHCM-BaseAndroid", Context.MODE_PRIVATE)

    }

    public companion object {
        public fun getInstance(): PreferenceHelper = PreferenceHolder.preferenceHelper
    }

    private object PreferenceHolder {
        val preferenceHelper = PreferenceHelper()
    }


    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = this.edit()
        operation(editor)
        editor.apply()
    }

    public fun SharedPreferences.clean() {
        val editor = this.edit()
        editor.clear()
        editor.apply()
    }


    /**
     * Đặt một khoá lên editor theo kiểu giá trị. `null` xoá khoá (log cảnh báo riêng); kiểu chưa
     * hỗ trợ thì log lỗi và bỏ qua khoá đó.
     */
    private fun SharedPreferences.Editor.put(key: String, value: Any?) {
        when (value) {
            null -> {
                remove(key)
                Log.w("PreferenceHelper", "null value, key removed: $key")
            }
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            else -> Log.e("PreferenceHelper", "Not yet implemented: $key (${value.javaClass.name})")
        }
    }

    public fun setValue(key: String, value: Any?) {
        try {
            preferences?.edit { it.put(key, value) }
        } catch (e: Exception) {
            Log.e("PreferenceHelper", "Error: $e")
        }
    }

    /** Một lần edit cho nhiều khoá: một lần ghi đĩa thay vì N. */
    public fun setValues(values: Map<String, Any?>) {
        try {
            preferences?.edit { editor ->
                for ((key, value) in values) editor.put(key, value)
            }
        } catch (e: Exception) {
            Log.e("PreferenceHelper", "Error: $e")
        }
    }

    public fun remove(key: String) {
        try {
            preferences?.apply {
                edit { it.remove(key) }
            }
        } catch (e: Exception) {
            Log.e("PreferenceHelper", "Error: $e")
        }
    }

    public inline fun <reified T : Any> get(key: String, defaultValue: T): T {
        try {
            preferences?.apply {
                when (T::class) {
                    String::class -> return getString(key, defaultValue as String) as T
                    Int::class -> return getInt(key, defaultValue as Int) as T
                    Boolean::class -> return getBoolean(key, defaultValue as Boolean) as T
                    Float::class -> return getFloat(key, defaultValue as Float) as T
                    Long::class -> return getLong(key, defaultValue as Long) as T
                    else -> Log.e("PreferenceHelper", "Not yet implemented")
                }
            }
        } catch (e: Exception) {
            Log.e("PreferenceHelper", "Error: $e")
        }
        return defaultValue
    }

}