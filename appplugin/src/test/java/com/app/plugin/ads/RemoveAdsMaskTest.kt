package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAdsMaskTest {
    @Test
    fun `the Pro mask removes every format`() {
        for (flag in 0 until RemoveAdsMask.BIT_COUNT) {
            assertTrue(RemoveAdsMask.isRemoved(RemoveAdsMask.ALL, flag))
        }
    }

    /**
     * Quyết định #15 (DOC/66, 2026-09-25): native collapsible là lớp phủ đáy ~45 % màn hình, gây gián đoạn hơn
     * banner 50dp mà gói rẻ đã gỡ, nên [RemoveAdsMask.INTERRUPTIVE] gỡ cả nó. Native inline và rewarded vẫn còn.
     */
    @Test
    fun `the interruptive mask removes native collapsible, keeps inline native and rewarded`() {
        val mask = RemoveAdsMask.INTERRUPTIVE

        assertTrue(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_BANNER))
        assertTrue(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_RECT))
        assertTrue(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_FULLSCREEN))
        assertTrue(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_NATIVE_COLLAPSIBLE))
        assertFalse(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_NATIVE))
        assertFalse(RemoveAdsMask.isRemoved(mask, RemoveAdsMask.FLAG_REWARDED))
        assertEquals(0b010111, mask)
    }

    /** #15 chỉ đổi INTERRUPTIVE: gói Pro và "không gỡ gì" giữ nguyên giá trị (mask đã lưu trên máy vẫn đúng nghĩa). */
    @Test
    fun `ALL and NONE are unchanged`() {
        assertEquals(0b111111, RemoveAdsMask.ALL)
        assertEquals(0, RemoveAdsMask.NONE)
        for (flag in 0 until RemoveAdsMask.BIT_COUNT) {
            assertFalse(RemoveAdsMask.isRemoved(RemoveAdsMask.NONE, flag))
        }
    }

    @Test
    fun `flags outside the mask are never removed`() {
        assertFalse(RemoveAdsMask.isRemoved(RemoveAdsMask.ALL, -1))
        assertFalse(RemoveAdsMask.isRemoved(RemoveAdsMask.ALL, RemoveAdsMask.BIT_COUNT))
        assertFalse(RemoveAdsMask.isRemoved(-1, 6))
    }

    @Test
    fun `sanitize drops bits beyond the known formats`() {
        assertEquals(RemoveAdsMask.ALL, RemoveAdsMask.sanitize(-1))
        assertEquals(RemoveAdsMask.NONE, RemoveAdsMask.sanitize(1 shl RemoveAdsMask.BIT_COUNT))
    }
}
