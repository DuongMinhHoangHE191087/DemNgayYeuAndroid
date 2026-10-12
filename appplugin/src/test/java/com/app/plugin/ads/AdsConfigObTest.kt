package com.app.plugin.ads

import android.content.Context
import org.junit.Test
import org.mockito.Mockito.mock

class AdsConfigObTest {
    @Test
    fun `reading and saving without a context is a no-op, not a crash`() {
        val config = AdsConfigOb(mock(Context::class.java)).apply { mContext = null }

        config.loadFromMem()
        config.saveCurr()
    }
}
