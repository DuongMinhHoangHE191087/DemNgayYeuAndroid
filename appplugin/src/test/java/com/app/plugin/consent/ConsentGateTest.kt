package com.app.plugin.consent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateTest {
    @Test
    fun `only the first caller starts the consent flow`() {
        val gate = ConsentGate()

        assertTrue(gate.start {})
        assertFalse(gate.start {})
        assertFalse(gate.start {})
    }

    @Test
    fun `callbacks wait for finish and run once in arrival order`() {
        val gate = ConsentGate()
        val calls = mutableListOf<String>()
        gate.start { calls += "first" }
        gate.start { calls += "recreated activity" }

        assertEquals(emptyList<String>(), calls)

        gate.finish()
        gate.finish()

        assertEquals(listOf("first", "recreated activity"), calls)
    }

    @Test
    fun `a caller after finish runs immediately and does not restart the flow`() {
        val gate = ConsentGate()
        gate.start {}
        gate.finish()
        var ran = false

        val shouldStart = gate.start { ran = true }

        assertTrue(ran)
        assertFalse(shouldStart)
        assertTrue(gate.isOpen)
    }

    @Test
    fun `a callback that re-enters the gate does not deadlock`() {
        val gate = ConsentGate()
        var nested = false
        gate.start { gate.start { nested = true } }

        gate.finish()

        assertTrue(nested)
    }
}
