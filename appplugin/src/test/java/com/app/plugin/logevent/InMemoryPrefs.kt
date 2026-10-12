package com.app.plugin.logevent

import android.content.SharedPreferences
import org.mockito.Mockito.mock

/**
 * Prefs trong bộ nhớ cho test của `TelemetryConsentGate`: getter đọc [store] (hoặc mặc định), mọi `put*`/`remove` của editor
 * ghi thẳng vào [store]. [store] nên là map đồng bộ: gate ghi trên IO, luồng test đọc.
 */
internal fun inMemoryPrefs(store: MutableMap<String, Any?>): SharedPreferences {
    val editor = mock(SharedPreferences.Editor::class.java) { inv ->
        val name = inv.method.name
        when {
            name.startsWith("put") -> store[inv.getArgument(0)] = inv.getArgument<Any?>(1)
            name == "remove" -> store.remove(inv.getArgument<String>(0))
            name == "clear" -> store.clear()
        }
        when (inv.method.returnType) {
            SharedPreferences.Editor::class.java -> inv.mock
            java.lang.Boolean.TYPE -> true
            else -> null
        }
    }
    return mock(SharedPreferences::class.java) { inv ->
        when (inv.method.name) {
            "edit" -> editor
            "contains" -> store.containsKey(inv.getArgument(0))
            "getString", "getInt", "getBoolean", "getFloat", "getLong" ->
                store[inv.getArgument(0)] ?: inv.getArgument<Any?>(1)
            else -> null
        }
    }
}
