package com.app.plugin.ads

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class Full2DefaultCoverTest {

    private fun host(isFinishing: Boolean = false): Pair<Activity, FrameLayout> {
        val decor = mock(FrameLayout::class.java)
        val window = mock(Window::class.java)
        `when`(window.decorView).thenReturn(decor)
        val activity = mock(Activity::class.java)
        `when`(activity.window).thenReturn(window)
        `when`(activity.isFinishing).thenReturn(isFinishing)
        return activity to decor
    }

    @Test
    fun `show adds exactly one cover however often the transition is re-announced`() {
        val (activity, decor) = host()
        val cover = Full2DefaultCover()

        cover.show(activity, hostResumed = false)
        cover.show(activity, hostResumed = true)

        verify(decor, times(1)).addView(any(View::class.java), any(ViewGroup.LayoutParams::class.java))
    }

    @Test
    fun `hide removes the cover it added, and a second hide is a no-op`() {
        val (activity, decor) = host()
        val cover = Full2DefaultCover()

        cover.show(activity, hostResumed = false)
        cover.hide()
        cover.hide()

        verify(decor, times(1)).removeView(any(View::class.java))
    }

    @Test
    fun `cover can be raised again after it was cleared`() {
        val (activity, decor) = host()
        val cover = Full2DefaultCover()

        cover.show(activity, hostResumed = false)
        cover.hide()
        cover.show(activity, hostResumed = false)

        verify(decor, times(2)).addView(any(View::class.java), any(ViewGroup.LayoutParams::class.java))
    }

    @Test
    fun `no activity or a finishing one gets no cover`() {
        val (finishing, decor) = host(isFinishing = true)
        val cover = Full2DefaultCover()

        cover.show(null, hostResumed = true)
        cover.show(finishing, hostResumed = true)

        verify(decor, never()).addView(any(View::class.java), any(ViewGroup.LayoutParams::class.java))
    }

    @Test
    fun `a host that refuses the view does not crash the app`() {
        val (activity, decor) = host()
        doThrow(IllegalStateException("window gone"))
            .`when`(decor).addView(any(View::class.java), any(ViewGroup.LayoutParams::class.java))
        val cover = Full2DefaultCover()

        cover.show(activity, hostResumed = true)
        cover.hide()

        verify(decor, never()).removeView(any(View::class.java))
    }
}
