package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hợp đồng của [WaterfallShowFailHold], không qua AdsHelper. */
class WaterfallShowFailHoldTest {

    private val delivered = mutableListOf<String>()

    /** Như callback onAdShowFail của vòng: bị giữ thì return, không thì chuyển tới app. */
    private fun onAdShowFail(hold: WaterfallShowFailHold, err: String) {
        if (hold.hold { onAdShowFail(hold, err) }) return
        delivered += err
    }

    @Test
    fun `mang fail dong bo roi mang sau hien duoc - khong loi nao toi app`() {
        val hold = WaterfallShowFailHold("full_home")

        val first = hold.during { onAdShowFail(hold, "act_destroy"); false }
        val second = hold.during { true }
        hold.settle(second)

        assertFalse(first)
        assertTrue(second)
        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun `moi mang deu fail - dung mot loi, loi dau tien, chay o settle`() {
        val hold = WaterfallShowFailHold("full_home")

        hold.during { onAdShowFail(hold, "first"); false }
        hold.during { false }
        hold.during { onAdShowFail(hold, "second"); onAdShowFail(hold, "second-again"); false }
        assertEquals("chưa settle thì chưa có lỗi nào tới app", emptyList<String>(), delivered)
        hold.settle(false)

        assertEquals(listOf("first"), delivered)
    }

    @Test
    fun `khong mang nao bao loi - settle khong goi gi`() {
        val hold = WaterfallShowFailHold("full_home")

        hold.during { false }
        hold.settle(false)

        assertEquals(emptyList<String>(), delivered)
    }

    @Test
    fun `mang bao loi dong bo ma van tra true - loi chay o settle, sau vong`() {
        val hold = WaterfallShowFailHold("full_home")

        hold.during { onAdShowFail(hold, "earlier"); false }
        val shown = hold.during { onAdShowFail(hold, "shown-but-failed"); true }
        assertEquals(emptyList<String>(), delivered)
        hold.settle(shown)

        assertEquals(listOf("shown-but-failed"), delivered)
    }

    @Test
    fun `loi bat dong bo sau khi showAd tra ve - khong bi giu`() {
        val hold = WaterfallShowFailHold("full_home")
        hold.during { true }
        hold.settle(true)

        onAdShowFail(hold, "async")

        assertEquals(listOf("async"), delivered)
    }

    @Test
    fun `showAd nem loi - cua so giu dong lai, loi sau do khong bi giu`() {
        val hold = WaterfallShowFailHold("full_home")

        runCatching { hold.during { throw IllegalStateException("boom") } }
        onAdShowFail(hold, "later")

        assertEquals(listOf("later"), delivered)
    }
}
