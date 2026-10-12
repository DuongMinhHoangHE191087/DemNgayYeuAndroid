package com.app.plugin.ads.adsmax

import android.content.Context
import android.util.Log
import com.app.plugin.ads.AdSdkStartup
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GPP_US_OK
import com.app.plugin.consent.GPP_US_OPT_OUT
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.contextWithTcf
import com.app.plugin.until.LogUtil
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.6.0 Task 8a (O2): GPP opt-out bang Mỹ → `AppLovinPrivacySettings.setDoNotSell` **trước** `AppLovinSdk.initialize`;
 * có GPP mà không opt-out → `setDoNotSell(false)`; không có GPP → không gọi (AppLovin tự đọc GPP). Không xét tầng
 * consent. Đổi giữa phiên: [AdsMaxMy.followGpp] gọi lại sau khởi tạo.
 */
class AdsMaxMyGppTest {
    private val calls = mutableListOf<String>()

    @Before fun setUp() = ConsentState.resetForTest()

    @After fun tearDown() {
        ConsentState.resetForTest()
        // `AdsMaxMy.instance` là singleton: hai test dưới ghi `initializeRequested` bằng reflection trên chính
        // instance đó (để `AdSdkStartup.resetForTest()` có việc); đưa về `false` để không rò sang test khác dùng
        // `AdsMaxMy.instance`.
        AdsMaxMy.instance.resetForTest()
    }

    /** Reflection trên trường riêng tư `initializeRequested` (review Task 8a m2): dựng lại trạng thái "đã start
     * MAX" của [AdsMaxMy.instance] mà không phải gọi qua `startNetwork` (kéo theo mock AppLovin đầy đủ). */
    private fun setInitializeRequested(target: AdsMaxMy, value: Boolean) {
        val field = AdsMaxMy::class.java.getDeclaredField("initializeRequested")
        field.isAccessible = true
        field.setBoolean(target, value)
    }

    /** Context có `admax_devKey` (MAX được khởi tạo) và GPP [gpp] trong prefs mặc định. */
    private fun maxCtx(gpp: String?): Context {
        val context = contextWithTcf(gdpr = 0, purposes = "", gpp = { gpp })
        `when`(context.resources.getIdentifier("admax_devKey", "string", "com.example.app")).thenReturn(DEV_KEY_RES)
        `when`(context.resources.getString(DEV_KEY_RES)).thenReturn("maxDevKey")
        return context
    }

    /** AppLovin là mock tĩnh; mỗi `setDoNotSell`/`initialize` ghi vào [calls] theo thứ tự. */
    private fun withAppLovin(block: (MockedStatic<AppLovinPrivacySettings>, MockedStatic<Log>) -> Unit) {
        val sdk = mock(AppLovinSdk::class.java)
        doAnswer { calls += "initialize"; null }.`when`(sdk).initialize(any(), any())
        val builder = mock(AppLovinSdkInitializationConfiguration.Builder::class.java, RETURNS_SELF)
        mockStatic(AppLovinSdkInitializationConfiguration::class.java).use { cfg ->
            cfg.`when`<AppLovinSdkInitializationConfiguration.Builder> {
                AppLovinSdkInitializationConfiguration.builder(anyString(), any(Context::class.java))
            }.thenReturn(builder)
            mockStatic(AppLovinSdk::class.java).use { lib ->
                lib.`when`<AppLovinSdk> { AppLovinSdk.getInstance(any()) }.thenReturn(sdk)
                mockStatic(AppLovinPrivacySettings::class.java).use { privacy ->
                    privacy.`when`<Unit> { AppLovinPrivacySettings.setDoNotSell(anyBoolean(), any()) }
                        .thenAnswer { calls += "setDoNotSell(${it.getArgument<Boolean>(0)})"; null }
                    mockStatic(Log::class.java).use { log -> block(privacy, log) }
                }
            }
        }
    }

    @Test fun `GPP opt-out thi setDoNotSell(true) truoc initialize, log doNotSell=true`() = withAppLovin { _, log ->
        AdsMaxMy().startNetwork(maxCtx(GPP_US_OPT_OUT))

        assertEquals("setDoNotSell(true) phải trước initialize", listOf("setDoNotSell(true)", "initialize"), calls)
        log.verify { Log.d(eq(LogUtil.TAG), contains("ads max: doNotSell=true (gpp=optOut)")) }
    }

    @Test fun `GPP khong opt-out thi setDoNotSell(false) truoc initialize`() = withAppLovin { _, _ ->
        AdsMaxMy().startNetwork(maxCtx(GPP_US_OK))

        assertEquals("GPP không opt-out phải ghi rõ doNotSell=false", listOf("setDoNotSell(false)", "initialize"), calls)
    }

    @Test fun `khong co GPP thi khong goi setDoNotSell`() = withAppLovin { privacy, _ ->
        AdsMaxMy().startNetwork(maxCtx(null))

        assertEquals(listOf("initialize"), calls)
        privacy.verify({ AppLovinPrivacySettings.setDoNotSell(anyBoolean(), any()) }, never())
    }

    /** Opt-out bang Mỹ không phụ thuộc tầng: `DENIED` (không cho Meta/AppsFlyer) vẫn không bán dữ liệu cho MAX. */
    @Test fun `tang DENIED va GPP opt-out thi van setDoNotSell(true), khong gate bang tierAllows`() =
        withAppLovin { _, _ ->
            ConsentState.setTier(ConsentTier.DENIED)

            AdsMaxMy().startNetwork(maxCtx(GPP_US_OPT_OUT))

            assertEquals(listOf("setDoNotSell(true)", "initialize"), calls)
        }

    @Test fun `followGpp sau khoi tao thi goi lai setDoNotSell, chua khoi tao thi khong`() = withAppLovin { privacy, _ ->
        val before = AdsMaxMy()
        before.followGpp(maxCtx(GPP_US_OPT_OUT), GppConsent.decode(GPP_US_OPT_OUT, "7"))
        privacy.verify({ AppLovinPrivacySettings.setDoNotSell(anyBoolean(), any()) }, never())

        val max = AdsMaxMy()
        val context = maxCtx(GPP_US_OK)
        max.startNetwork(context)
        max.followGpp(context, GppConsent.decode(GPP_US_OPT_OUT, "7"))
        max.followGpp(context, null)   // GPP biến mất: không ghi cờ giả

        assertEquals(listOf("setDoNotSell(false)", "initialize", "setDoNotSell(true)"), calls)
        privacy.verify({ AppLovinPrivacySettings.setDoNotSell(eq(true), any()) }, times(1))
    }

    /**
     * Đặc tả giữ nguyên (review Task 8a m2): không qua `AdSdkStartup.resetForTest()` thì
     * `initializeRequested` của [AdsMaxMy.instance] không đổi — `followGpp` vẫn áp GPP như trước, gọi
     * `setDoNotSell` đúng một lần. Xanh cả trước và sau khi thêm [AdsMaxMy.resetForTest]: bài kiểm chỉ hành vi
     * giữ, không phải hành vi mới của test dưới.
     */
    @Test fun `khong qua AdSdkStartup resetForTest thi followGpp tren instance da khoi tao van goi setDoNotSell 1 lan`() =
        withAppLovin { privacy, _ ->
            setInitializeRequested(AdsMaxMy.instance, true)

            AdsMaxMy.instance.followGpp(maxCtx(GPP_US_OPT_OUT), GppConsent.decode(GPP_US_OPT_OUT, "7"))

            privacy.verify({ AppLovinPrivacySettings.setDoNotSell(eq(true), any()) }, times(1))
        }

    /**
     * Review Task 8a m2: test trước đã "start" MAX (`initializeRequested = true` trên singleton) không được làm
     * test sau ăn theo — `AdSdkStartup.resetForTest()` phải đưa `AdsMaxMy.instance` về như process mới. Mã cũ
     * (chưa gọi `AdsMaxMy.instance.resetForTest()` trong `AdSdkStartup.resetForTest`) để `initializeRequested`
     * nguyên `true`, `followGpp` sau đó vẫn gọi `setDoNotSell` một lần → đỏ.
     */
    @Test fun `AdSdkStartup resetForTest dua initializeRequested ve false, followGpp sau do khong goi setDoNotSell`() =
        withAppLovin { privacy, _ ->
            setInitializeRequested(AdsMaxMy.instance, true)

            AdSdkStartup.resetForTest()
            AdsMaxMy.instance.followGpp(maxCtx(GPP_US_OPT_OUT), GppConsent.decode(GPP_US_OPT_OUT, "7"))

            privacy.verify({ AppLovinPrivacySettings.setDoNotSell(anyBoolean(), any()) }, never())
        }

    @Test fun `devKey trong thi khong khoi tao, khong goi setDoNotSell`() = withAppLovin { privacy, _ ->
        AdsMaxMy().startNetwork(contextWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OPT_OUT }))

        assertEquals(emptyList<String>(), calls)
        privacy.verify({ AppLovinPrivacySettings.setDoNotSell(anyBoolean(), any()) }, never())
    }

    private companion object {
        const val DEV_KEY_RES = 0x7f0f0101
    }
}
