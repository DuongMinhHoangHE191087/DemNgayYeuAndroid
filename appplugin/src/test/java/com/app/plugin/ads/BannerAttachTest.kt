package com.app.plugin.ads

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Crash monkey 2026-09-24: `IllegalStateException: The specified child already has a parent` tại
 * loadBannerCircle.onLoaded. Sau khi Activity tạo lại, banner trong cache vẫn gắn ở container cũ
 * rồi bị addView vào container mới. Mọi đường gắn banner phải đi qua BannerAttach.
 */
class BannerAttachTest {

    @Test
    fun `banner con gan o container cu thi go ra truoc roi moi gan`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old).thenReturn(null)

        assertTrue(BannerAttach.attach(parent, view))

        val order = inOrder(old, parent)
        order.verify(old).removeView(view)
        order.verify(parent).addView(view)
    }

    @Test
    fun `banner da nam dung container thi khong gan lai`() {
        val view = mock(View::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(parent)

        assertTrue(BannerAttach.attach(parent, view))

        verify(parent, never()).removeView(view)
        verify(parent, never()).addView(view)
    }

    @Test
    fun `go khong duoc thi bao false va khong addView`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old)

        assertFalse(BannerAttach.attach(parent, view))

        verify(parent, never()).addView(view)
    }

    @Test
    fun `removeView nem loi thi bao false va khong addView`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old)
        doThrow(IllegalStateException("detach boom")).`when`(old).removeView(view)

        assertFalse(BannerAttach.attach(parent, view))

        verify(old).removeView(view)
        verify(parent, never()).addView(view)
    }

    @Test
    fun `cha khong phai ViewGroup thi bao false va khong addView`() {
        val view = mock(View::class.java)
        val oldParent = mock(ViewParent::class.java)
        val parent = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(oldParent)

        assertFalse(BannerAttach.attach(parent, view))

        verify(parent, never()).addView(view)
    }

    @Test
    fun `detach go view khoi container dang giu no`() {
        val view = mock(View::class.java)
        val old = mock(ViewGroup::class.java)
        `when`(view.parent).thenReturn(old)

        BannerAttach.detach(view)

        verify(old).removeView(view)
    }

    @Test
    fun `detach null hoac view khong co cha thi khong lam gi va khong nem`() {
        BannerAttach.detach(null)
        val view = mock(View::class.java)
        `when`(view.parent).thenReturn(null)
        BannerAttach.detach(view)
    }
}
