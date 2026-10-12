package com.app.plugin.ads

import android.app.Activity
import android.app.Dialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Crash monkey 2026-09-24: `WindowManager.BadTokenException` tại AdsMobPlNtFull.showWidthDialog —
 * dialog dựng sẵn cho Activity đã chết được show(). Meta và MAX cùng mẫu.
 */
class NtFullDialogHostTest {

    @Test
    fun `activity null, dang finish hoac da destroy thi khong dung duoc`() {
        assertFalse(NtFullDialogHost.isUsable(null))
        val finishing = mock(Activity::class.java)
        `when`(finishing.isFinishing).thenReturn(true)
        assertFalse(NtFullDialogHost.isUsable(finishing))
        val destroyed = mock(Activity::class.java)
        `when`(destroyed.isDestroyed).thenReturn(true)
        assertFalse(NtFullDialogHost.isUsable(destroyed))
        assertTrue(NtFullDialogHost.isUsable(mock(Activity::class.java)))
    }

    @Test
    fun `show nem loi cua so chet thi tra false, bao ly do va khong nem tiep`() {
        val dialog = mock(Dialog::class.java)
        doThrow(IllegalStateException("window dead")).`when`(dialog).show()
        var failure: Throwable? = null

        assertFalse(NtFullDialogHost.show(dialog) { failure = it })

        assertEquals("window dead", failure?.message)
    }

    @Test
    fun `show binh thuong thi tra true`() {
        assertTrue(NtFullDialogHost.show(mock(Dialog::class.java)) { throw AssertionError("không được gọi") })
    }
}
