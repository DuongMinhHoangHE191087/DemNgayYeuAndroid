package com.app.plugin.consent

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.app.plugin.SdkKillSwitch
import com.app.plugin.logevent.ConsentEvents
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.PreferenceHelper
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * 1.7.0 Task 4: phễu consent lên server (`consent_form_shown`, `consent_choice`), chạy qua luồng UMP thật của
 * `ConsentManager` (UMP giả: info update thành công ngay, form giữ callback tới khi test "trả lời"). Request bắt ở
 * `ServerEventCapture` (chỗ Retrofit nhận body): mọi byte rời máy đi qua đó. Q2 (sign-off 1.7.0 R2): người `DENIED`
 * không có event consent nào tới server; tỉ lệ đồng ý lấy từ báo cáo AdMob Privacy & messaging.
 */
class ConsentFunnelEventTest {
    private val store: MutableMap<String, Any?> = Collections.synchronizedMap(mutableMapOf())
    private val helper = PreferenceHelper.getInstance()
    private var previousPrefs: SharedPreferences? = null

    @Before fun setUp() {
        SdkKillSwitch.current = SdkKillSwitch.NONE
        ConsentState.resetForTest()
        TelemetryConsentGate.resetForTest()
        previousPrefs = helper.preferences
        helper.preferences = inMemoryPrefs(store)
        resetLastTimestamp()
        resetPending()
    }

    @After fun tearDown() {
        TelemetryConsentGate.resetForTest()
        ConsentState.resetForTest()
        helper.preferences = previousPrefs
        SdkKillSwitch.current = SdkKillSwitch.NONE
        resetLastTimestamp()
        resetPending()
    }

    private fun resetLastTimestamp() =
        LogEventServer::class.java.getDeclaredField("lastTimestamp").apply { isAccessible = true }.setLong(null, 0L)

    /**
     * Form đang chờ câu trả lời của `ConsentEvents` là trạng thái của process: test trước (form hiện mà không trả lời) không
     * được để lại nó cho test sau (R-4 RV-B I1). Reflection như [resetLastTimestamp]: không thêm API/dòng dump.
     */
    private fun resetPending() =
        (ConsentEvents::class.java.getDeclaredField("pending").apply { isAccessible = true }.get(null) as AtomicReference<*>)
            .set(null)

    /**
     * UMP giả của một test: info update thành công ngay; `loadAndShowConsentFormIfRequired` và `showPrivacyOptionsForm` giữ
     * callback lại tới khi test đóng form. Tầng lúc đóng form do TCF của context quyết định (OBTAINED + `IABTCF_*`).
     */
    private class Ump(val ci: ConsentInformation, val activity: Activity) {
        var form: ConsentForm.OnConsentFormDismissedListener? = null
        var privacyForm: ConsentForm.OnConsentFormDismissedListener? = null

        /** Người dùng trả lời form đầu: UMP thành OBTAINED rồi callback đóng form. */
        fun answer() {
            obtained()
            close(form, "form")
        }

        /** Form đầu đóng mà chưa trả lời: UMP vẫn `REQUIRED`. */
        fun dismissUnanswered() = close(form, "form")

        /** Người dùng đóng form tuỳ chọn quyền riêng tư (UMP OBTAINED). */
        fun answerPrivacyOptions() {
            obtained()
            close(privacyForm, "privacy options form")
        }

        private fun obtained() {
            `when`(ci.consentStatus).thenReturn(ConsentInformation.ConsentStatus.OBTAINED)
            `when`(ci.canRequestAds()).thenReturn(true)
        }

        private fun close(listener: ConsentForm.OnConsentFormDismissedListener?, what: String) =
            checkNotNull(listener) { "UMP chưa được gọi hiện $what" }.onConsentFormDismissed(null)
    }

    /** Dựng [Ump] (trạng thái đầu [status], form tuỳ chọn bắt buộc hay không) và chạy [body] trong mock tĩnh của UMP. */
    private fun withUmp(
        context: Context,
        status: Int,
        privacyOptionsRequired: Boolean = false,
        body: (Ump) -> Unit,
    ) {
        val ci = mock(ConsentInformation::class.java)
        `when`(ci.consentStatus).thenReturn(status)
        `when`(ci.canRequestAds()).thenReturn(status != ConsentInformation.ConsentStatus.REQUIRED)
        `when`(ci.privacyOptionsRequirementStatus).thenReturn(
            if (privacyOptionsRequired) ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            else ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED
        )
        doAnswer { inv ->
            inv.getArgument<ConsentInformation.OnConsentInfoUpdateSuccessListener>(2).onConsentInfoUpdateSuccess()
            null
        }.`when`(ci).requestConsentInfoUpdate(
            any(Activity::class.java),
            any(ConsentRequestParameters::class.java),
            any(ConsentInformation.OnConsentInfoUpdateSuccessListener::class.java),
            any(ConsentInformation.OnConsentInfoUpdateFailureListener::class.java)
        )
        val resources = context.resources   // ngoài chuỗi stub: gọi mock bên trong `when` là stub dở
        val activity = mock(Activity::class.java)
        `when`(activity.applicationContext).thenReturn(context)
        `when`(activity.resources).thenReturn(resources)
        val ump = Ump(ci, activity)
        mockStatic(UserMessagingPlatform::class.java).use { statics ->
            statics.`when`<ConsentInformation> { UserMessagingPlatform.getConsentInformation(any(Context::class.java)) }
                .thenReturn(ci)
            statics.`when`<Unit> {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(any(Activity::class.java), any())
            }.thenAnswer { inv -> ump.form = inv.getArgument(1); null }
            statics.`when`<Unit> {
                UserMessagingPlatform.showPrivacyOptionsForm(any(Activity::class.java), any())
            }.thenAnswer { inv -> ump.privacyForm = inv.getArgument(1); null }
            try {
                body(ump)
            } finally {
                ConsentManager.reset(activity)   // bỏ `info` giả, mở cổng mới: object dùng chung giữa các test
            }
        }
    }

    /** App gọi `gatherConsent`; UMP báo `REQUIRED` và hiện form đầu. [body] chạy khi form đang hiện. */
    private fun withConsentForm(context: Context, body: (Ump) -> Unit) =
        withUmp(context, ConsentInformation.ConsentStatus.REQUIRED) { ump ->
            ConsentManager.gatherConsent(ump.activity) {}
            body(ump)
        }

    /** Người đã đồng ý (GRANTED, TCF [purposes]) mở form tuỳ chọn quyền riêng tư. */
    private fun withPrivacyOptions(context: Context, required: Boolean, body: (Ump) -> Unit) =
        withUmp(context, ConsentInformation.ConsentStatus.OBTAINED, privacyOptionsRequired = required) { ump ->
            ConsentManager.showPrivacyOptions(ump.activity)
            body(ump)
        }

    /** Tên các event `consent_*` đã tới server (không chờ). */
    private fun consentRequests(server: ServerEventCapture): List<String> =
        server.allRequests().map { it.get("eventName").asString }.filter { it.startsWith("consent_") }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("hết 5 s mà chưa: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }

    // ═══ F1 ═══

    @Test fun `F1 form hien phat consent_form_shown gdpr_applies 1`() {
        val context = contextWithTcf(gdpr = 1, purposes = "")
        // Cổng không install (gửi ngay): F1 chứng minh event được phát; cổng consent do F2–F4, F7 kiểm.
        ServerEventCapture().use { server ->
            withConsentForm(context) {
                val shown = server.await(EventNames.CONSENT_FORM_SHOWN, 1)
                assertEquals("consent_form_shown tới server khi form hiện", 1, shown.size)
                assertEquals(1, shown.single().get("gdpr_applies").asInt)
                assertEquals("initial", shown.single().get("form_kind").asString)
            }
        }
    }

    // ═══ F2 ═══

    @Test fun `F2 GRANTED consent_choice tier GRANTED latency_ms ge 0 toi server`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", gpp = { GPP_US_OK })
        TelemetryConsentGate.install(context)
        val startNanos = System.nanoTime()
        ServerEventCapture().use { server ->
            withConsentForm(context) { form ->
                Thread.sleep(ANSWER_DELAY_MS)   // người dùng đọc form
                form.answer()

                val choices = server.await(EventNames.CONSENT_CHOICE, 1)
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                assertEquals("consent_choice tới server sau khi đồng ý", 1, choices.size)
                val choice = choices.single()
                assertEquals("tầng sau updateTier", "GRANTED", choice.get("tier").asString)
                val latencyMs = choice.get("latency_ms").asLong
                assertTrue("latency_ms từ lúc hiện form: $ANSWER_DELAY_MS <= $latencyMs <= $elapsedMs",
                    latencyMs in ANSWER_DELAY_MS..elapsedMs)
                assertEquals(1, choice.get("gdpr_applies").asInt)
                assertEquals("7", choice.get("gpp_sections").asString)
                assertEquals("initial", choice.get("form_kind").asString)
                assertEquals("form_shown giữ trước consent rồi xả khi GRANTED", 1, server.await(EventNames.CONSENT_FORM_SHOWN, 1).size)
            }
        }
    }

    // ═══ F3 (đặc tả: xanh cả trên mã cũ, nơi chưa có event consent) ═══

    @Test fun `F3 DENIED 0 request consent_ (Q2)`() {
        val context = contextWithTcf(gdpr = 1, purposes = "0000000000")
        TelemetryConsentGate.install(context)
        ServerEventCapture().use { server ->
            withConsentForm(context) { form ->
                form.answer()
                assertEquals("tầng sau khi từ chối", ConsentTier.DENIED, ConsentState.tier())

                Thread.sleep(1_000)
                assertEquals("Q2: 0 request consent_* sau DENIED", emptyList<String>(), consentRequests(server))
                assertEquals("Q2: 0 request nào sau DENIED", 0, server.allRequests().size)
            }
        }
    }

    // ═══ F4 ═══

    @Test fun `F4 giu truoc consent tran 50 khong day consent_form_shown ra`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        TelemetryConsentGate.install(context)
        ServerEventCapture().use { server ->
            withConsentForm(context) {
                val deadline = System.currentTimeMillis() + 1_000
                while (TelemetryConsentGate.heldCount == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
                val base = TelemetryConsentGate.heldCount.toLong()
                (1..TelemetryConsentGate.HOLD_CAP).forEach { i ->
                    LogEventServer.logEvent("b$i", "s")
                    awaitTrue("b$i đã qua cổng") {
                        TelemetryConsentGate.heldCount + TelemetryConsentGate.evictedCount == base + i
                    }
                }
                assertTrue("bộ đệm không vượt trần", TelemetryConsentGate.heldCount <= TelemetryConsentGate.HOLD_CAP)

                ConsentState.setTier(ConsentTier.GRANTED)

                val shown = server.await(EventNames.CONSENT_FORM_SHOWN, 1)
                assertEquals("consent_form_shown không bị đẩy ra khỏi bộ đệm trước consent", 1, shown.size)
            }
        }
    }

    // ═══ R-4 (I1): form tuỳ chọn quyền riêng tư ═══

    @Test fun `F5 privacy options bat buoc - form_shown va choice deu form_kind privacy_options`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        TelemetryConsentGate.install(context)
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerEventCapture().use { server ->
            withPrivacyOptions(context, required = true) { ump ->
                ump.answerPrivacyOptions()

                val shown = server.await(EventNames.CONSENT_FORM_SHOWN, 1)
                val choices = server.await(EventNames.CONSENT_CHOICE, 1)
                assertEquals("đúng một consent_form_shown", 1, shown.size)
                assertEquals("đúng một consent_choice", 1, choices.size)
                assertEquals("privacy_options", shown.single().get("form_kind").asString)
                assertEquals("privacy_options", choices.single().get("form_kind").asString)
                assertEquals("GRANTED", choices.single().get("tier").asString)
            }
        }
    }

    @Test fun `F6 privacy options khong bat buoc - 0 consent_form_shown`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        TelemetryConsentGate.install(context)
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerEventCapture().use { server ->
            withPrivacyOptions(context, required = false) { ump ->
                ump.answerPrivacyOptions()

                assertEquals("UMP không hiện form: 0 consent_form_shown", 0,
                    server.awaitRequests(EventNames.CONSENT_FORM_SHOWN, 0, 1_000).size)
                assertEquals("không có form thì không có lựa chọn: 0 consent_choice", 0,
                    server.awaitRequests(EventNames.CONSENT_CHOICE, 0, 0).size)
            }
        }
    }

    @Test fun `F7 rut lai ve DENIED qua privacy options - consent_choice khong gui, 0 byte sau DENIED (Q2)`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        TelemetryConsentGate.install(context)
        ConsentState.setTier(ConsentTier.GRANTED)
        ServerEventCapture().use { server ->
            withPrivacyOptions(context, required = true) { ump ->
                assertEquals("form_shown phát lúc còn GRANTED được gửi", 1, server.await(EventNames.CONSENT_FORM_SHOWN, 1).size)
                val prefs = context.getSharedPreferences("any", 0)
                `when`(prefs.getString("IABTCF_PurposeConsents", "")).thenReturn("0000000000")   // bấm "Do not consent"

                ump.answerPrivacyOptions()
                assertEquals("tầng sau khi rút lại", ConsentTier.DENIED, ConsentState.tier())
                val before = server.allRequests().size

                Thread.sleep(1_000)
                assertEquals("Q2: consent_choice không tới server", emptyList<String>(),
                    consentRequests(server).filter { it == EventNames.CONSENT_CHOICE })
                assertEquals("Q2: 0 request mới sau DENIED", before, server.allRequests().size)
            }
        }
    }

    // ═══ R-4 (I2): mỗi lần hiện một consent_choice, chỉ khi có câu trả lời ═══
    // Cổng không install (gửi ngay) ở F8, F9: kiểm event có được phát hay không, không kiểm cổng.

    @Test fun `F8 form dong khi UMP van REQUIRED - 0 consent_choice`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        ServerEventCapture().use { server ->
            withConsentForm(context) { ump ->
                assertEquals(1, server.await(EventNames.CONSENT_FORM_SHOWN, 1).size)

                ump.dismissUnanswered()

                assertEquals("form đóng chưa trả lời không phải lựa chọn", 0,
                    server.awaitRequests(EventNames.CONSENT_CHOICE, 0, 1_000).size)
            }
        }
    }

    @Test fun `F9 updateTier lan hai sau khi tra loi - van dung mot consent_choice`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111")
        ServerEventCapture().use { server ->
            withConsentForm(context) { ump ->
                ump.answer()
                assertEquals(1, server.await(EventNames.CONSENT_CHOICE, 1).size)

                ConsentManager.updateConsentInfo(ump.activity) {}   // info update thành công → updateTier lần hai

                assertEquals("một lần hiện form, một consent_choice", 1,
                    server.awaitRequests(EventNames.CONSENT_CHOICE, 0, 1_000).size)
            }
        }
    }

    // ═══ R-4 RV-B: trường của payload (I2, m2, m3) ═══

    @Test fun `F10 PARTIAL - consent_choice bao tier PARTIAL`() {
        // Purpose 1, 2, 7, 9, 10 (không 3, 4): quảng cáo không cá nhân hoá được, profiling không → PARTIAL.
        val context = contextWithTcf(gdpr = 1, purposes = "1100001011")
        TelemetryConsentGate.install(context)
        ServerEventCapture().use { server ->
            withConsentForm(context) { form ->
                form.answer()
                assertEquals("tầng sau khi trả lời", ConsentTier.PARTIAL, ConsentState.tier())

                val choices = server.await(EventNames.CONSENT_CHOICE, 1)
                assertEquals("PARTIAL qua cổng: consent_choice tới server", 1, choices.size)
                assertEquals("PARTIAL", choices.single().get("tier").asString)
            }
        }
    }

    @Test fun `F11 IABTCF_gdprApplies vang - gdpr_applies -1, khong phai 0`() {
        val context = contextWithTcf(gdpr = null, purposes = "")
        // Cổng không install (gửi ngay): kiểm giá trị trường, không kiểm cổng.
        ServerEventCapture().use { server ->
            withConsentForm(context) {
                val shown = server.await(EventNames.CONSENT_FORM_SHOWN, 1)
                assertEquals(1, shown.size)
                assertEquals("khoá vắng = không biết (-1), không phải ngoài GDPR (0)", -1, shown.single().get("gdpr_applies").asInt)
            }
        }
    }

    @Test fun `F12 consent_choice giu truoc consent (OBTAINED thieu IABTCF) khong bi day ra khi tran 50`() {
        // UMP OBTAINED mà không có `IABTCF_*`: tầng UNKNOWN, `IABTCF_gdprApplies` vắng → cổng giữ cả hai event.
        val context = contextWithTcf(gdpr = null, purposes = "")
        TelemetryConsentGate.install(context)
        ServerEventCapture().use { server ->
            withConsentForm(context) { form ->
                form.answer()
                assertEquals(ConsentTier.UNKNOWN, ConsentState.tier())
                awaitTrue("form_shown và choice vào bộ đệm") { TelemetryConsentGate.heldCount == 2 }
                (1..TelemetryConsentGate.HOLD_CAP).forEach { i ->
                    LogEventServer.logEvent("b$i", "s")
                    awaitTrue("b$i đã qua cổng") {
                        TelemetryConsentGate.heldCount + TelemetryConsentGate.evictedCount == 2L + i
                    }
                }

                ConsentState.setTier(ConsentTier.GRANTED)

                val choices = server.await(EventNames.CONSENT_CHOICE, 1)
                assertEquals("consent_choice không bị đẩy ra khỏi bộ đệm trước consent", 1, choices.size)
                assertEquals("UNKNOWN", choices.single().get("tier").asString)
            }
        }
    }

    @Test fun `F13 gpp_sections hai section noi bang gach duoi`() {
        val context = contextWithTcf(gdpr = 1, purposes = "1111111111", gpp = { GPP_US_7_8 }, gppSid = { "7_8" })
        // Cổng không install (gửi ngay): kiểm giá trị trường, không kiểm cổng.
        ServerEventCapture().use { server ->
            withConsentForm(context) { form ->
                form.answer()

                val choices = server.await(EventNames.CONSENT_CHOICE, 1)
                assertEquals(1, choices.size)
                assertEquals("7_8", choices.single().get("gpp_sections").asString)
            }
        }
    }

    private companion object {
        const val ANSWER_DELAY_MS = 50L

        /** GPP US National (7) + California (8), không opt-out; dựng bằng encoder của `GppConsentTest` (header [7, 8]). */
        const val GPP_US_7_8 = "DBACLY~BVVqAAA~BVoAAA"
    }
}
