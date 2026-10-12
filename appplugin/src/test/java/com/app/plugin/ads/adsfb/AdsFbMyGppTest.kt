package com.app.plugin.ads.adsfb

import android.content.Context
import android.util.Log
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.facebook.ads.AdSettings
import com.facebook.ads.AudienceNetworkAds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never

/**
 * 1.6.0 Task 8a (O2): GPP opt-out bang Mỹ → Meta Audience Network `AdSettings.setDataProcessingOptions(["LDU"], 0, 0)`
 * **trước** `AudienceNetworkAds…initialize()`; có GPP mà không opt-out → `setDataProcessingOptions([])`; không có GPP
 * → không gọi. Đổi giữa phiên: [AdsFbMy.followGpp] gọi lại sau khởi tạo.
 */
class AdsFbMyGppTest {
    private val calls = mutableListOf<String>()

    @Before fun setUp() = ConsentState.resetForTest()
    @After fun tearDown() = ConsentState.resetForTest()

    private fun ctx(gpp: String?): Context = contextWithTcf(gdpr = 0, purposes = "", gpp = { gpp })

    /** Audience Network và `AdSettings` là mock tĩnh; mỗi `setDataProcessingOptions`/`initialize` ghi vào [calls]. */
    private fun withAudienceNetwork(block: (MockedStatic<AdSettings>, MockedStatic<Log>) -> Unit) {
        val builder = mock(AudienceNetworkAds.InitSettingsBuilder::class.java, RETURNS_SELF)
        doAnswer { calls += "initialize"; null }.`when`(builder).initialize()
        mockStatic(AudienceNetworkAds::class.java).use { an ->
            an.`when`<Boolean> { AudienceNetworkAds.isInitialized(any()) }.thenReturn(false)
            an.`when`<AudienceNetworkAds.InitSettingsBuilder> { AudienceNetworkAds.buildInitSettings(any()) }
                .thenReturn(builder)
            mockStatic(AdSettings::class.java).use { settings ->
                settings.`when`<Unit> { AdSettings.setDataProcessingOptions(any(), anyInt(), anyInt()) }.thenAnswer {
                    calls += "dpo(${it.getArgument<Array<String>>(0).toList()}, ${it.getArgument<Int>(1)}, ${it.getArgument<Int>(2)})"
                    null
                }
                settings.`when`<Unit> { AdSettings.setDataProcessingOptions(any()) }.thenAnswer {
                    calls += "dpo(${it.getArgument<Array<String>>(0).toList()})"
                    null
                }
                mockStatic(Log::class.java).use { log -> block(settings, log) }
            }
        }
    }

    @Test fun `GPP opt-out thi LDU truoc initialize, log LDU on`() = withAudienceNetwork { _, log ->
        AdsFbMy().startNetwork(ctx(GPP_US_OPT_OUT))

        assertEquals("LDU phải đặt trước initialize", listOf("dpo([LDU], 0, 0)", "initialize"), calls)
        log.verify { Log.d(eq(LogUtil.TAG), contains("ads fb: LDU on (gpp=optOut)")) }
    }

    @Test fun `GPP khong opt-out thi tat LDU ro rang truoc initialize`() = withAudienceNetwork { _, log ->
        AdsFbMy().startNetwork(ctx(GPP_US_OK))

        assertEquals(listOf("dpo([])", "initialize"), calls)
        log.verify { Log.d(eq(LogUtil.TAG), contains("ads fb: LDU off (gpp=ok)")) }
    }

    @Test fun `khong co GPP thi khong goi setDataProcessingOptions`() = withAudienceNetwork { settings, _ ->
        AdsFbMy().startNetwork(ctx(null))

        assertEquals(listOf("initialize"), calls)
        settings.verify({ AdSettings.setDataProcessingOptions(any(), anyInt(), anyInt()) }, never())
        settings.verify({ AdSettings.setDataProcessingOptions(any()) }, never())
    }

    /** Luật `tierAllows` không gate opt-out bang Mỹ: tầng nào cũng áp LDU khi mạng khởi tạo. */
    @Test fun `tang UNKNOWN trong GDPR va GPP opt-out thi van LDU`() = withAudienceNetwork { _, _ ->
        ConsentState.setTier(ConsentTier.UNKNOWN)

        AdsFbMy().startNetwork(contextWithTcf(gdpr = 1, purposes = "", gpp = { GPP_US_OPT_OUT }))

        assertEquals(listOf("dpo([LDU], 0, 0)", "initialize"), calls)
    }

    @Test fun `followGpp sau khoi tao thi goi lai, chua khoi tao hoac khong co GPP thi khong`() =
        withAudienceNetwork { _, _ ->
            AdsFbMy().followGpp(GppConsent.decode(GPP_US_OPT_OUT, "7"))
            assertEquals("chưa startNetwork: không gọi", emptyList<String>(), calls)

            val fb = AdsFbMy()
            fb.startNetwork(ctx(GPP_US_OK))
            fb.followGpp(GppConsent.decode(GPP_US_OPT_OUT, "7"))
            fb.followGpp(null)

            assertEquals(listOf("dpo([])", "initialize", "dpo([LDU], 0, 0)"), calls)
        }
}
