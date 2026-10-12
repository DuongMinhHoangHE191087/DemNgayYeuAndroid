package com.app.plugin.until

import android.content.SharedPreferences
import android.util.Log
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

/**
 * Đo 2026-09-24: `AppPluginBase.onCreate` ghi prefs bằng từng `setValue`, mỗi lần một edit và một lần
 * ghi đĩa. `setValues` gộp mọi khoá vào một edit.
 */
class PreferenceHelperTest {

    private val editor: SharedPreferences.Editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
    private val prefs: SharedPreferences = mock(SharedPreferences::class.java).also {
        `when`(it.edit()).thenReturn(editor)
    }
    private val helper = PreferenceHelper().apply { preferences = prefs }

    @Test
    fun `setValues ghi moi khoa trong mot lan edit`() {
        helper.setValues(
            mapOf("s" to "x", "i" to 7, "b" to false, "f" to 1.5f, "l" to 9L)
        )

        verify(prefs, times(1)).edit()
        verify(editor).putString("s", "x")
        verify(editor).putInt("i", 7)
        verify(editor).putBoolean("b", false)
        verify(editor).putFloat("f", 1.5f)
        verify(editor).putLong("l", 9L)
        verify(editor, times(1)).apply()
        verifyNoMoreInteractions(editor)
    }

    @Test
    fun `setValues bo qua kieu khong ho tro, log loi va van ghi cac khoa con lai`() {
        mockStatic(Log::class.java).use { log ->
            helper.setValues(mapOf("list" to listOf(1), "ok" to true))

            log.verify { Log.e(eq("PreferenceHelper"), contains("list")) }
        }
        verify(prefs, times(1)).edit()
        verify(editor).putBoolean("ok", true)
        verify(editor, times(1)).apply()
        verifyNoMoreInteractions(editor)
    }

    @Test
    fun `setValues voi null thi xoa khoa va log rieng`() {
        mockStatic(Log::class.java).use { log ->
            helper.setValues(mapOf("gone" to null, "ok" to 1))

            log.verify { Log.w(eq("PreferenceHelper"), contains("gone")) }
            log.verify({ Log.e(eq("PreferenceHelper"), contains("gone")) }, never())
        }
        verify(prefs, times(1)).edit()
        verify(editor).remove("gone")
        verify(editor).putInt("ok", 1)
        verify(editor, times(1)).apply()
        verifyNoMoreInteractions(editor)
    }

    @Test
    fun `setValue voi null thi xoa khoa`() {
        helper.setValue("gone", null)

        verify(prefs, times(1)).edit()
        verify(editor).remove("gone")
        verify(editor, times(1)).apply()
        verifyNoMoreInteractions(editor)
    }

    @Test
    fun `setValue van ghi mot khoa trong mot lan edit`() {
        helper.setValue("n", 3)

        verify(prefs, times(1)).edit()
        verify(editor).putInt("n", 3)
        verify(editor, times(1)).apply()
        verifyNoMoreInteractions(editor)
    }
}
