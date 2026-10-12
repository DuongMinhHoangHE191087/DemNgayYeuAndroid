package com.app.plugin.until

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUtilTest {
    @Test
    fun `a comma separated list gives one id per tester device`() {
        assertEquals(listOf("A1", "B2", "C3"), AppUtil.splitIds(" A1, B2,,C3 "))
    }

    @Test
    fun `an empty resource gives no ids`() {
        assertEquals(emptyList<String>(), AppUtil.splitIds(""))
        assertEquals(emptyList<String>(), AppUtil.splitIds(" , "))
    }
}
