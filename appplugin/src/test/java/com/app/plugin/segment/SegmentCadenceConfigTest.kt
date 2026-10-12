package com.app.plugin.segment

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentCadenceConfigTest {

    @Test
    fun defaultMatchesDocumentedValues() {
        val config = SegmentCadenceConfig.DEFAULT
        assertEquals(10, config.hipTabSwitchThreshold)
        assertEquals(6, config.pspTabSwitchThreshold)
        assertEquals(4, config.liaTabSwitchThreshold)
        assertEquals(0L, config.globalGuardrailMillis)
    }
}
