package com.app.plugin.brain.policy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.brain.AdsBrain
import com.app.plugin.brain.BrainPolicy
import com.app.plugin.brain.BrainPolicyHarness
import com.app.plugin.brain.BrainPolicyStore
import com.app.plugin.firbase.FirHelperConfig
import com.app.plugin.license.LicenseGate
import com.app.plugin.logevent.inMemoryPrefs
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.3.0 SECBC (hợp đồng C, D2–D5-230): khung chung của test `PolicyClient`. Transport, nguồn token và meta-data là bản giả
 * (0 mạng, 0 Play Integrity thật); luồng chạy thật trên `appplugin-policy`, test chờ bằng `drainForTest`.
 */
internal abstract class PolicyClientFixture {
    protected class Call(val method: String, val url: String, val headers: Map<String, String>, val thread: String)

    protected val store: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())
    protected val policyPrefs: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())   // 2.4.0: back-off
    protected var previousPrefs: SharedPreferences? = null
    protected val logs = CopyOnWriteArrayList<String>()
    protected val calls = CopyOnWriteArrayList<Call>()
    protected val nonces = CopyOnWriteArrayList<String>()
    protected val metaThreads = CopyOnWriteArrayList<String>()
    protected val ctx: Context = mock(Context::class.java)
    protected var nonceResp = PolicyResponse(200, """{"nonce":"$P_NONCE","expires_in_s":120}""")
    protected var policyResp = PolicyResponse(200, """{"app_id":"demo","source":"app","reason":null,"policy":$P_POLICY}""")
    protected var token: String? = P_TOKEN
    protected var meta: Map<String, Any?> = P_FULL_META

    @Before fun setUp() {
        previousPrefs = PreferenceHelper.getInstance().preferences
        PreferenceHelper.getInstance().preferences = inMemoryPrefs(store)
        BrainPolicy.clear()
        BrainPolicyHarness.resetForTest()
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(inMemoryPrefs(policyPrefs))
        AdsBrain.enabled = true; AdsBrain.rolloutFraction = 1.0   // 2.4.0 CARRY-POL: policy chỉ chạy khi brain bật + trong rollout
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
    }

    @After fun tearDown() {
        PolicyClient.resetForTest()
        BrainPolicyHarness.resetForTest()
        LicenseGate.resetForTest()
        BrainPolicy.clear()
        AdsBrain.enabled = false; AdsBrain.rolloutFraction = 1.0
        PreferenceHelper.getInstance().preferences = previousPrefs
    }

    /** Như process mới: verdict [v], seam giả đọc trạng thái hiện tại của test; [freshInstall]: cài mới (xoá mốc back-off + artifact đã áp). */
    protected fun arm(v: LicenseGate.Verdict, freshInstall: Boolean = true) {
        PolicyClient.resetForTest()
        if (freshInstall) { policyPrefs.clear(); BrainPolicy.clear(); store.remove(BrainPolicyStore.PREF_KEY); AdsBrain.rolloutFraction = 1.0 }
        LicenseGate.resetForTest(v)
        logs.clear(); calls.clear(); nonces.clear(); metaThreads.clear()
        PolicyClient.logSink = { logs += it }
        PolicyClient.metaReader = { metaThreads += Thread.currentThread().name; meta }
        PolicyClient.tokenSourceFactory = { _, _ -> IntegrityTokenSource { n, timeout -> assertEquals(10_000L, timeout); nonces += n; token } }
        PolicyClient.transport = PolicyTransport { m, url, h -> calls += Call(m, url, h, Thread.currentThread().name); if (m == "POST") nonceResp else policyResp }
    }

    protected fun install() { PolicyClient.install(ctx); PolicyClient.drainForTest() }

    protected fun assertNotApplied(why: String) {
        assertEquals(why, "built-in-default", BrainPolicy.activeVersion())
        assertNull(why, store[BrainPolicyStore.PREF_KEY])
        assertNull(why, PolicyClient.serverVersion)
    }
}

internal const val P_URL = "http://127.0.0.1:8089/"
internal const val P_KEY = "key-SECRET-456"
internal const val P_TOKEN = "tok-SECRET-123"
internal const val P_NONCE = "N0nce_SECRET-abcdefghijklmnopqrstuvwxyz0123"
internal const val P_POLICY = """{"version":"policy_harness_230","enabled":false,"experiment":{"rollout_fraction":0}}"""
internal val P_FULL_META: Map<String, Any?> = mapOf(
    PolicyClient.META_URL to P_URL, PolicyClient.META_API_KEY to P_KEY, PolicyClient.META_PROJECT to "123456789012",
)

/** PC1–PC11: luồng `/policy`, fail-closed, cổng giấy phép, meta-data, một lượt/process, luồng nền, log không lộ bí mật. */
internal class PolicyClientTest : PolicyClientFixture() {
    @Test fun `PC1 VALID va du 3 meta-data thi POST nonce, token, GET policy roi ap policy cua server`() {
        install()
        assertEquals("thu tu POST -> GET: ${calls.map { it.method }}", listOf("POST", "GET"), calls.map { it.method })
        assertEquals(listOf("${P_URL}policy/nonce", "${P_URL}policy"), calls.map { it.url })
        assertEquals(mapOf("X-Api-Key" to P_KEY), calls[0].headers)
        assertEquals(mapOf("X-Api-Key" to P_KEY, "X-Integrity-Token" to P_TOKEN), calls[1].headers)
        assertEquals("nguon token nhan nonce cua server", listOf(P_NONCE), nonces)
        assertEquals("applyPolicy 1 lan: activeVersion", "policy_harness_230", BrainPolicy.activeVersion())
        assertTrue("ban server duoc luu nhu RC", store[BrainPolicyStore.PREF_KEY].toString().contains("\"policy_harness_230\""))
        assertEquals(listOf("policy: applied version=policy_harness_230 source=app"), logs)
        assertEquals("policy_harness_230", PolicyClient.serverVersion)
    }

    @Test fun `PC2 token null thi khong GET va log default no_token`() {
        token = null
        install()
        assertEquals("0 GET khi khong co token: ${calls.map { it.method }}", listOf("POST"), calls.map { it.method })
        assertEquals(listOf("policy: default (no_token)"), logs)
        assertNotApplied("PC2")
    }

    @Test fun `PC3 source default khong bao gio ap, ke ca version 0 hay policy hop le`() {
        val bodies = mapOf(
            """{"app_id":"demo","source":"default","reason":"not_accepted","policy":$P_POLICY}""" to "policy: default (not_accepted)",
            """{"app_id":"demo","source":"default","reason":"no_policy","policy":{"version":0}}""" to "policy: default (no_policy)",
            """{"source":"default","reason":"<script>","policy":$P_POLICY}""" to "policy: default (source)",
        )
        for ((body, line) in bodies) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            policyResp = PolicyResponse(200, body)
            install()
            assertEquals(body, listOf(line), logs)
            assertNotApplied(body)
        }
    }

    @Test fun `PC3b than server thieu truong source thi khong ap, log default source`() {
        policyResp = PolicyResponse(200, """{"app_id":"demo","policy":$P_POLICY}""")
        install()
        assertEquals(listOf("policy: default (source)"), logs)
        assertNotApplied("PC3b")
    }

    @Test fun `PC4 nonce khong 200 thi khong token, khong GET, khong ap`() {
        for (code in listOf(401, 429, 503, 500)) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            nonceResp = PolicyResponse(code, """{"detail":"x"}""")
            install()
            assertEquals("ma $code", listOf("POST"), calls.map { it.method })
            assertEquals(listOf("policy: default (nonce_$code)"), logs)
            assertTrue(nonces.isEmpty())
            assertNotApplied("ma $code")
        }
    }

    @Test fun `PC5 JSON hong, policy khong phai object, than qua 64 KB hay nonce ngan thi khong ap`() {
        val pad = "x".repeat(PolicyTransport.MAX_BODY_BYTES)
        val gets = mapOf(
            """{"source":"app","policy":""" to "policy: default (parse)",
            """{"source":"app","policy":$P_POLICY}x""" to "policy: default (parse)",
            """{"source":"app","policy":"policy_harness_230"}""" to "policy: default (parse)",
            """{"source":"app","policy":[1]}""" to "policy: default (parse)",
            """{"source":"app","policy":$P_POLICY,"pad":"$pad"}""" to "policy: default (parse)",
            null to "policy: default (parse)",
        )
        for ((body, line) in gets) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            policyResp = PolicyResponse(200, body)
            install()
            assertEquals(body?.take(60), listOf(line), logs)
            assertNotApplied(body?.take(60) ?: "null")
        }
        for (n in listOf("\"short-nonce-15c\"", "123456789012345678", "\"has space nonce 0123456789\"")) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            nonceResp = PolicyResponse(200, """{"nonce":$n}""")
            install()
            assertEquals(n, listOf("policy: default (nonce_parse)"), logs)
            assertEquals("nonce sai: 0 token, 0 GET", listOf("POST"), calls.map { it.method })
            assertNotApplied(n)
        }
    }

    @Test fun `CP4 BRAIN_POLICY_URL http chi 127 0 0 1 theo host da parse, khong startsWith`() {
        for (u in listOf("http://10.0.2.2/", "http://127.0.0.1@evil.com/", "http://127.0.0.1.evil.com/", "http://localhost/")) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2)); meta = P_FULL_META + (PolicyClient.META_URL to u); install()
            assertEquals(u, listOf("policy: off (${PolicyClient.META_URL})"), logs)
            assertEquals("$u: 0 mang", 0, calls.size)
        }
    }

    @Test fun `CP4b https moi host va http 127 0 0 1 co cong van hop le`() {
        for (u in listOf("http://127.0.0.1:8089/", "https://x/", "HTTPS://policy.example.com/v1/")) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2)); meta = P_FULL_META + (PolicyClient.META_URL to u); install()
            assertEquals(u, listOf("${u}policy/nonce", "${u}policy"), calls.map { it.url })
        }
    }

    @Test fun `CP5 OkHttp read timeout 8 s lon hon deadline backend 5 s, connect 5 s, ca loi goi 20 s`() {
        val lazy = OkHttpPolicyTransport::class.java.getDeclaredField("client\$delegate").apply { isAccessible = true }
        val c = (lazy.get(OkHttpPolicyTransport()) as Lazy<*>).value as okhttp3.OkHttpClient
        assertEquals(listOf(8_000, 5_000, 20_000), listOf(c.readTimeoutMillis, c.connectTimeoutMillis, c.callTimeoutMillis))
    }

    @Test fun `PC5b transport OkHttp doc toi da 64 KB, dai hon la null`() {
        val ok = "a".repeat(PolicyTransport.MAX_BODY_BYTES)
        assertEquals(ok, OkHttpPolicyTransport.readCapped(Buffer().writeUtf8(ok)))
        assertNull(OkHttpPolicyTransport.readCapped(Buffer().writeUtf8(ok + "b")))
    }

    @Test fun `PC6 BrainPolicy tu choi thi log rejected va khong luu`() {
        policyResp = PolicyResponse(200, """{"source":"app","policy":{"enabled":true}}""")
        install()
        assertEquals(1, logs.size)
        assertTrue(logs.single(), logs.single().startsWith("policy: rejected (TỪ CHỐI artifact"))
        assertNotApplied("PC6")
    }

    @Test fun `PC7 INVALID hay PENDING mai thi 0 mang`() {
        for (v in listOf(LicenseGate.Verdict.Invalid("sig"), LicenseGate.Verdict.Pending)) {
            arm(v)
            install()
            assertEquals("$v: 0 transport", 0, calls.size)
            assertEquals("$v: 0 doc meta-data", 0, metaThreads.size)
            assertEquals("$v: im lang", 0, logs.size)
        }
    }

    @Test fun `PC8 thieu hay sai tung khoa meta-data thi policy off, 0 mang`() {
        val cases = listOf(
            P_FULL_META - PolicyClient.META_URL to PolicyClient.META_URL,
            P_FULL_META + (PolicyClient.META_URL to "ftp://127.0.0.1/") to PolicyClient.META_URL,
            P_FULL_META + (PolicyClient.META_URL to "http://127.0.0.1:8089") to PolicyClient.META_URL,
            P_FULL_META - PolicyClient.META_API_KEY to PolicyClient.META_API_KEY,
            P_FULL_META - PolicyClient.META_PROJECT to PolicyClient.META_PROJECT,
            P_FULL_META + (PolicyClient.META_PROJECT to "0") to PolicyClient.META_PROJECT,
            P_FULL_META + (PolicyClient.META_PROJECT to "abc") to PolicyClient.META_PROJECT,
        )
        for ((m, key) in cases) {
            arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
            meta = m
            install(); install()
            assertEquals("$m", listOf("policy: off ($key)"), logs)
            assertEquals("$m: 0 mang", 0, calls.size)
        }
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        meta = P_FULL_META + (PolicyClient.META_PROJECT to 123456789)   // aapt ghi số ngắn thành Int
        install()
        assertEquals(2, calls.size)
    }

    @Test fun `PC9 hai lan VALID chi mot luot mang moi process`() {
        install(); install()
        LicenseGate.resetForTest(LicenseGate.Verdict.Valid("again", Long.MAX_VALUE / 2))
        install()
        assertEquals("1 POST + 1 GET: ${calls.map { it.method }}", 2, calls.size)
        assertEquals(1, logs.size)
    }

    @Test fun `PC10 install 0 IO tren luong goi, transport va meta-data chay tren appplugin-policy`() {
        arm(LicenseGate.Verdict.Pending)
        PolicyClient.install(ctx)
        assertEquals("install khi PENDING: 0 doc meta, 0 transport", listOf(0, 0), listOf(metaThreads.size, calls.size))
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        install()
        assertEquals(listOf(PolicyClient.THREAD_NAME), metaThreads)
        assertEquals(listOf(PolicyClient.THREAD_NAME, PolicyClient.THREAD_NAME), calls.map { it.thread })
        assertFalse(Thread.currentThread().name == PolicyClient.THREAD_NAME)
    }

    @Test fun `PC11 log khong bao gio chua nonce, token hay khoa API`() {
        val all = mutableListOf<String>()
        install(); all += logs   // applied
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        PolicyClient.transport = PolicyTransport { _, _, _ -> throw IOException("refused $P_KEY $P_NONCE") }
        install(); all += logs   // error
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        policyResp = PolicyResponse(200, """{"source":"app","policy":{"enabled":"$P_TOKEN"}}""")
        install(); all += logs   // rejected
        assertEquals(all.toString(), 3, all.size)
        assertTrue(all.toString(), "policy: error (IOException)" in all)
        for (secret in listOf(P_NONCE, P_TOKEN, P_KEY)) assertTrue("log lo '$secret': $all", all.none { secret in it })
    }

    @Test fun `PC11b loi lop vo danh log policy error Unknown`() {
        PolicyClient.transport = PolicyTransport { _, _, _ -> throw object : IOException("x $P_KEY") {} }
        install()
        assertEquals(listOf("policy: error (Unknown)"), logs)
    }
}
