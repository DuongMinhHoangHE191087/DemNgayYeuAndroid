package com.app.plugin.consent

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.app.plugin.until.LogUtil
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedStatic
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * Lỗi đo 2026-09-24: bấm "Do not consent" thì UMP báo OBTAINED và `canRequestAds() = true`, TCF ghi
 * `IABTCF_PurposeConsents = 0000000000`, nhưng tầng vẫn là GRANTED và quảng cáo chạy cá nhân hoá.
 */
class ConsentManagerTierTest {
    private val googleOk = "0".repeat(754) + "1"

    @Before fun setUp() = ConsentState.resetForTest()
    @After fun tearDown() = ConsentState.resetForTest()

    private fun ump(status: Int, canRequestAds: Boolean = true): ConsentInformation {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.consentStatus).thenReturn(status)
        `when`(ci.canRequestAds()).thenReturn(canRequestAds)
        return ci
    }

    private fun contextWithTcf(gdpr: Int?, purposes: String, vendors: String = googleOk): Context {
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(gdpr != null)
        if (gdpr != null) `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(gdpr)
        `when`(prefs.getString("IABTCF_PurposeConsents", "")).thenReturn(purposes)
        `when`(prefs.getString("IABTCF_PurposeLegitimateInterests", "")).thenReturn("0000000000")
        `when`(prefs.getString("IABTCF_VendorConsents", "")).thenReturn(vendors)
        val context = mock(Context::class.java)
        `when`(context.packageName).thenReturn("com.example.app")
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    @Test
    fun `Do not consent tren form la DENIED du UMP bao OBTAINED`() {
        val tier = ConsentManager.tierFor(
            ump(ConsentInformation.ConsentStatus.OBTAINED, canRequestAds = true),
            contextWithTcf(gdpr = 1, purposes = "0000000000")
        )
        assertEquals(ConsentTier.DENIED, tier)
    }

    @Test
    fun `dong y het tren form la GRANTED`() {
        val tier = ConsentManager.tierFor(
            ump(ConsentInformation.ConsentStatus.OBTAINED),
            contextWithTcf(gdpr = 1, purposes = "1111111111")
        )
        assertEquals(ConsentTier.GRANTED, tier)
    }

    @Test
    fun `OBTAINED ma chua co context thi khong bao gio GRANTED`() {
        val tier = ConsentManager.tierFor(ump(ConsentInformation.ConsentStatus.OBTAINED), null)
        assertEquals(ConsentTier.UNKNOWN, tier)
    }

    @Test
    fun `OBTAINED ma khong co IABTCF thi khong bao gio GRANTED`() {
        val tier = ConsentManager.tierFor(
            ump(ConsentInformation.ConsentStatus.OBTAINED),
            contextWithTcf(gdpr = null, purposes = "")
        )
        assertEquals(ConsentTier.UNKNOWN, tier)
    }

    @Test
    fun `ngoai EEA NOT_REQUIRED van GRANTED`() {
        val tier = ConsentManager.tierFor(
            ump(ConsentInformation.ConsentStatus.NOT_REQUIRED),
            contextWithTcf(gdpr = null, purposes = "")
        )
        assertEquals(ConsentTier.GRANTED, tier)
    }

    @Test
    fun `REQUIRED chua tra loi thi DENIED du TCF cu con dong y`() {
        val tier = ConsentManager.tierFor(
            ump(ConsentInformation.ConsentStatus.REQUIRED),
            contextWithTcf(gdpr = 1, purposes = "1111111111")
        )
        assertEquals(ConsentTier.DENIED, tier)
    }

    // ═══ Đọc trạng thái UMP ném lỗi: log, không nuốt câm (Điều 7) ═══

    @Test
    fun `doc consentStatus nem loi thi log loi va tier UNKNOWN`() {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.consentStatus).thenThrow(IllegalStateException("ump boom"))

        mockStatic(Log::class.java).use { log ->
            val tier = ConsentManager.tierFor(ci, null)

            assertEquals(ConsentTier.UNKNOWN, tier)
            log.verify { Log.e(eq(LogUtil.TAG), contains("consentStatus")) }
            log.verify { Log.e(eq(LogUtil.TAG), contains("translate")) }
        }
    }

    // ═══ updateConsentInfo lỗi mạng: tầng dịch lại từ bộ nhớ đệm trên máy ═══

    private fun activityWith(context: Context): Activity {
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(mock(Resources::class.java))
        return activity
    }

    /** Lối vào luồng consent có nhánh lỗi mạng: [ConsentManager.updateConsentInfo] hoặc [ConsentManager.gatherConsent]. */
    private enum class Entry { UPDATE, GATHER }

    /**
     * Chạy [entry] với cuộc gọi mạng của UMP luôn lỗi. Trả tầng lúc `onDone` được gọi, `null` nếu
     * `onDone` không được gọi.
     */
    private fun tierAfterNetworkError(
        ci: ConsentInformation,
        context: Context,
        entry: Entry = Entry.UPDATE,
    ): ConsentTier? {
        val error = mock(FormError::class.java)
        `when`(error.errorCode).thenReturn(FormError.ErrorCode.INTERNET_ERROR)
        `when`(error.message).thenReturn("network down")
        doAnswer { inv ->
            inv.getArgument<ConsentInformation.OnConsentInfoUpdateFailureListener>(3)
                .onConsentInfoUpdateFailure(error)
            null
        }.`when`(ci).requestConsentInfoUpdate(
            any(Activity::class.java),
            any(ConsentRequestParameters::class.java),
            any(ConsentInformation.OnConsentInfoUpdateSuccessListener::class.java),
            any(ConsentInformation.OnConsentInfoUpdateFailureListener::class.java)
        )
        val activity = activityWith(context)
        var tierAtDone: ConsentTier? = null
        mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            try {
                when (entry) {
                    Entry.UPDATE -> ConsentManager.updateConsentInfo(activity) { tierAtDone = ConsentState.tier() }
                    Entry.GATHER -> ConsentManager.gatherConsent(activity) { tierAtDone = ConsentState.tier() }
                }
            } finally {
                // Bỏ `info` giả và mở cổng mới: object dùng chung giữa các test.
                ConsentManager.reset(activity)
            }
        }
        return tierAtDone
    }

    @Test
    fun `loi mang ma UMP da luu REQUIRED thi tier khoi phuc GRANTED ha xuong DENIED`() {
        ConsentState.setTier(ConsentTier.GRANTED)          // tầng khôi phục từ phiên trước
        val ci = ump(ConsentInformation.ConsentStatus.REQUIRED)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = 1, purposes = "1111111111"))

        assertEquals(ConsentTier.DENIED, tier)
    }

    @Test
    fun `loi mang ma UMP da luu OBTAINED va TCF tu choi thi DENIED`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val ci = ump(ConsentInformation.ConsentStatus.OBTAINED, canRequestAds = true)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = 1, purposes = "0000000000"))

        assertEquals(ConsentTier.DENIED, tier)
    }

    @Test
    fun `loi mang ma bo nho dem khong co cau tra loi thi giu tier hien tai`() {
        // Bộ nhớ đệm không nói gì (UMP UNKNOWN, không IABTCF) không phải lý do bỏ một lời từ chối đã có.
        ConsentState.setTier(ConsentTier.DENIED)
        val ci = ump(ConsentInformation.ConsentStatus.UNKNOWN)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = null, purposes = ""))

        assertEquals(ConsentTier.DENIED, tier)
    }

    // ═══ Task 9 phụ lục C: phần dư của Task 8 ═══

    @Test
    fun `loi mang ma bo nho dem khong co cau tra loi thi giu PARTIAL`() {
        ConsentState.setTier(ConsentTier.PARTIAL)
        val ci = ump(ConsentInformation.ConsentStatus.UNKNOWN)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = null, purposes = ""))

        assertEquals(ConsentTier.PARTIAL, tier)
    }

    @Test
    fun `loi mang ma bo nho dem khong co cau tra loi thi khong giu GRANTED, ha ve UNKNOWN va log`() {
        // GRANTED khôi phục từ phiên trước mà trên máy không còn câu trả lời nào xác nhận: UNKNOWN
        // (chưa được cấp), không phải GRANTED (Điều 13).
        ConsentState.setTier(ConsentTier.GRANTED)
        val ci = ump(ConsentInformation.ConsentStatus.UNKNOWN)

        mockStatic(Log::class.java).use { log ->
            val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = null, purposes = ""))

            assertEquals(ConsentTier.UNKNOWN, tier)
            log.verify { Log.e(eq(LogUtil.TAG), contains("dropping GRANTED")) }
        }
    }

    @Test
    fun `gatherConsent loi mang ma UMP da luu REQUIRED thi tier khoi phuc GRANTED ha xuong DENIED`() {
        // Trước Task 9 nhánh lỗi mạng của gatherConsent giữ nguyên tầng khôi phục, khác updateConsentInfo.
        ConsentState.setTier(ConsentTier.GRANTED)
        val ci = ump(ConsentInformation.ConsentStatus.REQUIRED)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = 1, purposes = "1111111111"), Entry.GATHER)

        assertEquals(ConsentTier.DENIED, tier)
    }

    @Test
    fun `gatherConsent loi mang ma bo nho dem khong co cau tra loi thi giu DENIED`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val ci = ump(ConsentInformation.ConsentStatus.UNKNOWN)

        val tier = tierAfterNetworkError(ci, contextWithTcf(gdpr = null, purposes = ""), Entry.GATHER)

        assertEquals(ConsentTier.DENIED, tier)
    }

    // ═══ Đọc UMP ném lỗi trong các hàm trả Boolean: false kèm log, không câm (Điều 7) ═══

    private fun <T> withUmpThrowing(block: (Context, MockedStatic<Log>) -> T): T {
        val context = mock(Context::class.java)
        return mockStatic(UserMessagingPlatform::class.java).use { ump ->
            ump.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenThrow(IllegalStateException("ump boom"))
            mockStatic(Log::class.java).use { log -> block(context, log) }
        }
    }

    @Test
    fun `isConsentResolved doc UMP loi thi false va log loi`() = withUmpThrowing { context, log ->
        assertFalse(ConsentManager.isConsentResolved(context))
        log.verify { Log.e(eq(LogUtil.TAG), contains("isConsentResolved")) }
    }

    @Test
    fun `isPrivacyOptionsRequired doc UMP loi thi false va log loi`() = withUmpThrowing { context, log ->
        assertFalse(ConsentManager.isPrivacyOptionsRequired(context))
        log.verify { Log.e(eq(LogUtil.TAG), contains("isPrivacyOptionsRequired")) }
    }
}
