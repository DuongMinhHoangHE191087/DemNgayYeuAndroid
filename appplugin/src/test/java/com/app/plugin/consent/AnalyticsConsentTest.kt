package com.app.plugin.consent

import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Consent Mode v2: bốn loại, denied cho tới khi biết; ngoài GDPR thì granted. */
class AnalyticsConsentTest {
    private fun s(a: Boolean, b: Boolean, c: Boolean, d: Boolean) = AnalyticsConsent.Settings(a, b, c, d)

    private val all = s(true, true, true, true)
    private val none = s(false, false, false, false)
    private val partial = s(true, true, false, false)

    @Before fun setUp() {
        ConsentState.resetForTest()
        AnalyticsConsent.resetForTest()
    }

    @After fun tearDown() {
        ConsentState.resetForTest()
        AnalyticsConsent.resetForTest()
    }

    /**
     * `PreferenceHelper` (singleton dùng chung cả JVM) trỏ vào prefs trong bộ nhớ trong [block], rồi trả lại
     * prefs cũ. [block] nhận kho khoá → giá trị để kiểm cờ đã persist.
     */
    private fun withPrefs(block: (MutableMap<String, Any>) -> Unit) {
        val helper = PreferenceHelper.getInstance()
        val previous = helper.preferences
        val store = mutableMapOf<String, Any>()
        helper.preferences = inMemoryPrefs(store)
        try {
            block(store)
        } finally {
            helper.preferences = previous
        }
    }

    private fun inMemoryPrefs(store: MutableMap<String, Any>): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            store[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
        }
        `when`(editor.putBoolean(anyString(), anyBoolean())).thenAnswer {
            store[it.getArgument<String>(0)] = it.getArgument<Boolean>(1)
            editor
        }
        `when`(editor.remove(anyString())).thenAnswer {
            store.remove(it.getArgument<String>(0))
            editor
        }
        return prefs
    }

    // ═══ Bảng ánh xạ ═══

    @Test fun `ngoai GDPR thi cap het`() =
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = false))

    @Test fun `GRANTED thi cap het`() =
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.GRANTED, gdprApplies = true))

    @Test fun `PARTIAL thi luu tru duoc, khong dung du lieu nguoi dung, khong ca nhan hoa`() =
        assertEquals(partial, AnalyticsConsent.settingsFor(ConsentTier.PARTIAL, gdprApplies = true))

    @Test fun `DENIED va UNKNOWN trong EEA thi tu choi het`() {
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.DENIED, gdprApplies = true))
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = true))
    }

    /** Phụ lục A1: thiếu `IABTCF_gdprApplies` (null) là không biết, xử lý như có GDPR, không fail-open. */
    @Test fun `khong biet GDPR co ap dung khong thi theo tier, UNKNOWN tu choi het`() {
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = null))
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.DENIED, gdprApplies = null))
        assertEquals(partial, AnalyticsConsent.settingsFor(ConsentTier.PARTIAL, gdprApplies = null))
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.GRANTED, gdprApplies = null))
    }

    /**
     * Fix wave 2, Minor 6: `IABTCF_gdprApplies = 0` không thắng một lời từ chối. Giá trị 0 có thể là giá
     * trị cũ còn lại khi người dùng đã vào EEA; `DENIED` là câu trả lời thật nên luôn từ chối hết.
     */
    @Test fun `DENIED thang IABTCF_gdprApplies = 0 thi van tu choi het`() =
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.DENIED, gdprApplies = false))

    @Test fun `ngoai GDPR ma tang khong phai DENIED thi cap het`() {
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = false))
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.PARTIAL, gdprApplies = false))
        assertEquals(all, AnalyticsConsent.settingsFor(ConsentTier.GRANTED, gdprApplies = false))
    }

    // ═══ Đặt vào Firebase (phụ lục B #6: hai nhánh của DISABLE_COLLECTION_WHEN_DENIED) ═══

    private fun consentMap(storage: Boolean, ads: Boolean, userData: Boolean, personalization: Boolean) = mapOf(
        ConsentType.ANALYTICS_STORAGE to status(storage),
        ConsentType.AD_STORAGE to status(ads),
        ConsentType.AD_USER_DATA to status(userData),
        ConsentType.AD_PERSONALIZATION to status(personalization),
    )

    private fun status(granted: Boolean) = if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED

    @Test fun `mac dinh tat han thu thap khi tu choi`() =
        assertTrue(AnalyticsConsent.DISABLE_COLLECTION_WHEN_DENIED)

    @Test fun `tu choi va tat thu thap thi Consent Mode denied roi tat collection`() {
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, none, disableWhenDenied = true)

        val order = inOrder(fa)
        order.verify(fa).setConsent(consentMap(false, false, false, false))
        order.verify(fa).setAnalyticsCollectionEnabled(false)
    }

    @Test fun `tu choi ma khong tat thu thap thi chi Consent Mode denied, khong dung toi collection`() {
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, none, disableWhenDenied = false)

        verify(fa).setConsent(consentMap(false, false, false, false))
        verify(fa, never()).setAnalyticsCollectionEnabled(anyBoolean())
    }

    @Test fun `PARTIAL thi du lieu nguoi dung va ca nhan hoa denied, SDK chua tung tat thi khong bat collection`() {
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, partial, disableWhenDenied = true)

        verify(fa).setConsent(consentMap(true, true, false, false))
        verify(fa, never()).setAnalyticsCollectionEnabled(anyBoolean())
    }

    @Test fun `GRANTED thi cap ca bon, SDK chua tung tat thi khong bat collection`() {
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, all, disableWhenDenied = true)

        verify(fa).setConsent(consentMap(true, true, true, true))
        verify(fa, never()).setAnalyticsCollectionEnabled(anyBoolean())
    }

    // ═══ Ai sở hữu thu thập (đợt sửa cuối Phase B, mục 6 — phương án a) ═══
    //
    // SDK chỉ bật lại thu thập khi CHÍNH NÓ đã tắt (cờ `analytics_collection_disabled_by_sdk` trong
    // PreferenceHelper). Trước đây mỗi lần cổng mở với tầng cho phép, SDK gọi `setAnalyticsCollectionEnabled
    // (true)` và đè công tắc "chia sẻ dữ liệu sử dụng = tắt" của app mỗi lần mở app.

    @Test fun `app da tu tat collection, tang GRANTED thi SDK khong bat lai`() = withPrefs { store ->
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, all, disableWhenDenied = true)   // lần mở app, cổng mở, GRANTED

        verify(fa, never()).setAnalyticsCollectionEnabled(anyBoolean())
        assertFalse(store.containsKey(AnalyticsConsent.KEY_COLLECTION_DISABLED_BY_SDK))
    }

    @Test fun `SDK tat vi DENIED roi tang GRANTED thi bat lai, xoa co`() = withPrefs { store ->
        val fa = mock(FirebaseAnalytics::class.java)

        AnalyticsConsent.applySettings(fa, none, disableWhenDenied = true)
        assertEquals("co persist khi SDK tat", true, store[AnalyticsConsent.KEY_COLLECTION_DISABLED_BY_SDK])
        AnalyticsConsent.applySettings(fa, all, disableWhenDenied = true)
        AnalyticsConsent.applySettings(fa, partial, disableWhenDenied = true)   // đã bật lại: không gọi nữa

        val order = inOrder(fa)
        order.verify(fa).setAnalyticsCollectionEnabled(false)
        order.verify(fa).setAnalyticsCollectionEnabled(true)
        verify(fa, times(1)).setAnalyticsCollectionEnabled(true)
        assertFalse("bat lai thi xoa co", store.containsKey(AnalyticsConsent.KEY_COLLECTION_DISABLED_BY_SDK))
    }

    @Test fun `SDK tat o process truoc thi process sau van bat lai nho co persist`() = withPrefs { _ ->
        val fa = mock(FirebaseAnalytics::class.java)
        AnalyticsConsent.applySettings(fa, none, disableWhenDenied = true)

        AnalyticsConsent.resetForTest()   // process mới: chỉ còn những gì trên đĩa
        AnalyticsConsent.applySettings(fa, all, disableWhenDenied = true)

        verify(fa).setAnalyticsCollectionEnabled(true)
    }

    @Test fun `PreferenceHelper chua init thi co giu trong bo nho, log mot lan`() {
        val helper = PreferenceHelper.getInstance()
        val previous = helper.preferences
        helper.preferences = null
        try {
            val fa = mock(FirebaseAnalytics::class.java)
            mockStatic(Log::class.java).use { log ->
                AnalyticsConsent.applySettings(fa, none, disableWhenDenied = true)
                AnalyticsConsent.applySettings(fa, none, disableWhenDenied = true)
                AnalyticsConsent.applySettings(fa, all, disableWhenDenied = true)

                log.verify({ Log.e(eq(LogUtil.TAG), contains("PreferenceHelper not initialized")) }, times(1))
            }
            verify(fa).setAnalyticsCollectionEnabled(true)   // cùng process: cờ trong bộ nhớ đủ để bật lại
        } finally {
            helper.preferences = previous
        }
    }

    // ═══ apply(context): tầng hiện tại + IABTCF ═══

    @Test fun `apply doc tang hien tai va IABTCF roi dat vao Firebase`() {
        ConsentState.setTier(ConsentTier.DENIED)
        val context = contextWithTcf(gdpr = 1, purposes = "0000000000")
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)

            AnalyticsConsent.apply(context)
        }

        verify(fa).setConsent(consentMap(false, false, false, false))
        verify(fa).setAnalyticsCollectionEnabled(false)
    }

    @Test fun `apply UNKNOWN khi thieu IABTCF_gdprApplies thi tu choi het`() {
        val context = contextWithTcf(gdpr = null, purposes = "")
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)

            AnalyticsConsent.apply(context)
        }

        verify(fa).setConsent(consentMap(false, false, false, false))
        verify(fa).setAnalyticsCollectionEnabled(false)
    }

    @Test fun `apply loi thi log loi, khong nem ra ngoai`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }
                .thenThrow(IllegalStateException("firebase boom"))
            mockStatic(Log::class.java).use { log ->
                AnalyticsConsent.apply(context)

                log.verify { Log.e(eq(LogUtil.TAG), contains("analytics consent apply failed")) }
            }
        }
    }

    // ═══ GPP: opt-out theo luật bang Mỹ (1.6.0 Task 8a, O2) ═══

    @Test fun `GRANTED va US opt-out thi luu tru duoc, ad_user_data va ad_personalization denied`() =
        assertEquals(
            "GRANTED + GPP opt-out phải ra Settings(true, true, false, false)",
            partial,
            AnalyticsConsent.settingsFor(ConsentTier.GRANTED, gdprApplies = false, usOptOut = true),
        )

    @Test fun `PARTIAL va US opt-out giu PARTIAL, DENIED khong doi`() {
        assertEquals(partial, AnalyticsConsent.settingsFor(ConsentTier.PARTIAL, gdprApplies = true, usOptOut = true))
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.DENIED, gdprApplies = false, usOptOut = true))
        assertEquals(none, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = null, usOptOut = true))
    }

    @Test fun `ngoai GDPR va US opt-out thi van luu tru, chi bo du lieu nguoi dung va ca nhan hoa`() =
        assertEquals(partial, AnalyticsConsent.settingsFor(ConsentTier.UNKNOWN, gdprApplies = false, usOptOut = true))

    @Test fun `khong opt-out thi ban ba tham so trung ban hai tham so`() {
        for (tier in ConsentTier.values()) for (gdpr in listOf(true, false, null)) {
            assertEquals(
                "$tier gdpr=$gdpr",
                AnalyticsConsent.settingsFor(tier, gdpr),
                AnalyticsConsent.settingsFor(tier, gdpr, usOptOut = false),
            )
        }
    }

    @Test fun `apply voi GPP opt-out trong prefs thi setConsent tu choi du lieu nguoi dung va ca nhan hoa, log gpp=optOut`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OPT_OUT })
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
            mockStatic(Log::class.java).use { log ->
                AnalyticsConsent.apply(context)

                log.verify { Log.d(eq(LogUtil.TAG), contains("gpp=optOut")) }
            }
        }

        verify(fa).setConsent(consentMap(true, true, false, false))
        verify(fa, never()).setAnalyticsCollectionEnabled(false)
        assertEquals("GPP không đổi tầng", ConsentTier.GRANTED, ConsentState.tier())
    }

    @Test fun `apply voi GPP khong opt-out thi cap het nhu cu, log gpp=ok`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 0, purposes = "", gpp = { GPP_US_OK })
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
            mockStatic(Log::class.java).use { log ->
                AnalyticsConsent.apply(context)

                log.verify { Log.d(eq(LogUtil.TAG), contains("gpp=ok")) }
            }
        }

        verify(fa).setConsent(consentMap(true, true, true, true))
    }

    @Test fun `apply khong co GPP thi log gpp=none`() {
        ConsentState.setTier(ConsentTier.GRANTED)
        val context = contextWithTcf(gdpr = 0, purposes = "")
        val fa = mock(FirebaseAnalytics::class.java)
        mockStatic(FirebaseAnalytics::class.java).use { firebase ->
            firebase.`when`<FirebaseAnalytics> { FirebaseAnalytics.getInstance(any()) }.thenReturn(fa)
            mockStatic(Log::class.java).use { log ->
                AnalyticsConsent.apply(context)

                log.verify { Log.d(eq(LogUtil.TAG), contains("gpp=none")) }
            }
        }

        verify(fa).setConsent(consentMap(true, true, true, true))
    }
}
