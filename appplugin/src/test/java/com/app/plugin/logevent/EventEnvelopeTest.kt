package com.app.plugin.logevent

import android.content.Context
import android.telephony.TelephonyManager
import com.app.plugin.BuildConfig
import com.app.plugin.SdkKillSwitch
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.google.gson.JsonParser
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Call
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Envelope v2 (plan 1.7.0 "Hợp đồng event v2", Task 1): body giữ trường v1 (`sequenceId`, `eventName`, `eventData`,
 * `ts`, `userProperty`) và thêm ở cấp cao nhất `event_id` (UUID sinh lúc dựng), `schema_version` = 2, `sdk_version`,
 * `package_name`, `consent_tier`. L1: định dạng quốc gia của body không phụ thuộc locale máy.
 */
class EventEnvelopeTest {
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @Test fun `E1 body co event_id UUID, schema_version 2, sdk_version, package_name, consent_tier`() {
        ServerEventCapture().use { s ->
            LogEventServer.logEvent("b", "s")
            val body = s.awaitRequests("behavior", 1).first()
            assertTrue(
                "body thiếu event_id/schema_version: $body",
                body.has("event_id") && uuid.matches(body.get("event_id").asString) &&
                    body.get("schema_version")?.asInt == 2
            )
            assertEquals("sdk_version: $body", BuildConfig.SDK_VERSION, body.get("sdk_version")?.asString)
            assertTrue("package_name: $body", body.get("package_name")?.isJsonPrimitive == true)
            assertEquals("consent_tier: $body", ConsentState.tier().name, body.get("consent_tier")?.asString)
            assertTrue(
                "trường v1 phải giữ: $body",
                listOf("sequenceId", "eventName", "eventData", "ts", "userProperty").all { body.has(it) }
            )
        }
    }

    @Test fun `E2 hai lan gui lai cung event giu cung event_id`() {
        val field = LogEventTransport::class.java.getDeclaredField("logService").apply { isAccessible = true }
        val calls = CopyOnWriteArrayList<HeldCall>()
        SdkKillSwitch.current = SdkKillSwitch.NONE
        LogEventServer.resetSenderForTest()
        field.set(null, object : LogService {
            override fun logActionsToServer(requestBody: RequestBody): Call<ResponseBody> =
                HeldCall(Buffer().also { requestBody.writeTo(it) }.readUtf8()).also { calls += it }
        })
        try {
            LogEventServer.logEvent("retry", "s")
            awaitTrue { calls.size == 1 }
            calls[0].fail()                       // lỗi mạng: hàng đợi gửi lại cùng body sau backoff 1 s
            awaitTrue { calls.size == 2 }
            val ids = calls.map { com.google.gson.JsonParser.parseString(it.body).asJsonObject.get("event_id")?.asString }
            assertTrue("event_id lần đầu/lần gửi lại: $ids", ids[0] != null && ids[0] == ids[1])
        } finally {
            field.set(null, null)
            LogEventServer.resetSenderForTest()
        }
    }

    /** 1.8.0 Task 15 R1 (mutation-killer E1): `consent_tier` theo `ConsentState` ở từng tier, không phải một hằng. */
    @Test fun `R1 consent_tier theo ConsentState o moi tier`() {
        try {
            val seen = ConsentTier.values().map { tier ->
                ConsentState.setTier(tier)
                tier.name to JsonParser.parseString(EventEnvelope.wrap("{}", "id")).asJsonObject.get("consent_tier")?.asString
            }
            assertEquals("consent_tier theo tier: $seen", ConsentTier.values().map { it.name to it.name }, seen)
        } finally {
            ConsentState.resetForTest()
        }
    }

    /** 1.8.0 Task 15 R2 (mutation-killer E1): `schema_version` là số JSON 2, không phải chuỗi "2" (`asInt` đọc được cả hai). */
    @Test fun `R2 schema_version la so JSON`() {
        val v = JsonParser.parseString(EventEnvelope.wrap("{}", "id")).asJsonObject.get("schema_version").asJsonPrimitive
        assertTrue("schema_version phải là số: $v", v.isNumber && !v.isString)
        assertEquals(2, v.asInt)
    }

    @Test fun `L1 ma quoc gia khong phu thuoc locale Tho Nhi Ky`() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val tm = mock(TelephonyManager::class.java)
            `when`(tm.simCountryIso).thenReturn("in")
            val ctx = mock(Context::class.java)
            `when`(ctx.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(tm)
            val m = LogEventServerHelper::class.java.getDeclaredMethod("getCountryCode", Context::class.java)
                .apply { isAccessible = true }
            assertEquals("IN", m.invoke(LogEventServerHelper, ctx))
        } finally {
            Locale.setDefault(old)
        }
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue("hết 5 s chờ request", System.currentTimeMillis() < deadline)
            Thread.sleep(5)
        }
    }
}
