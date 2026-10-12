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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** PC12–PC14 (2.3.0 SECBC): Q3 RC bị bỏ qua sau `/policy`, harness `policy_fake_token`, dòng `policy:` qua `Log.i`. */
internal class PolicyClientWiringTest : PolicyClientFixture() {
    @Test fun `PC12 sau khi ap policy server, RC brain_policy_android bi bo qua toi het process`() {
        applyRemoteBrainPolicy("""{"version":"rc-truoc"}""")
        assertEquals("truoc /policy: RC van ap", "rc-truoc", BrainPolicy.activeVersion())
        install()
        assertEquals("policy_harness_230", BrainPolicy.activeVersion())
        applyRemoteBrainPolicy("""{"version":"rc-sau"}""")
        applyRemoteBrainPolicy("""{"version":"rc-sau-2"}""")
        assertEquals("server thang RC trong process", "policy_harness_230", BrainPolicy.activeVersion())
        assertEquals("dung 1 dong rc_skipped: $logs", 1, logs.count { it == "policy: rc_skipped version=policy_harness_230 source=app" })
    }

    @Test fun `PC12b ap RC va ap server loai tru nhau qua khoa PolicyClient, server thang va log dung version`() {
        val getDone = CountDownLatch(1)
        PolicyClient.transport = PolicyTransport { m, _, _ -> if (m == "POST") nonceResp else policyResp.also { getDone.countDown() } }
        val rc = Thread { applyRemoteBrainPolicy("""{"version":"rc-race"}""") }
        synchronized(PolicyClient) {   // giữ khoá: cả hai lần áp phải đứng chờ, không ai chen giữa
            PolicyClient.install(ctx)
            assertTrue("GET da xong", getDone.await(5, TimeUnit.SECONDS))
            rc.start()
            assertTrue("luong policy cho khoa truoc khi ap", awaitBlocked { it.name == PolicyClient.THREAD_NAME })
            assertTrue("luong RC cho khoa truoc khi kiem + ap", awaitBlocked { it === rc })
            assertEquals("chua ai ap khi khoa con giu", "built-in-default", BrainPolicy.activeVersion())
        }
        rc.join(5_000); PolicyClient.drainForTest()
        applyRemoteBrainPolicy("""{"version":"rc-sau"}""")
        assertEquals("server thang", "policy_harness_230", BrainPolicy.activeVersion())
        assertEquals("policy_harness_230", PolicyClient.serverVersion)
        assertEquals(listOf("policy: applied version=policy_harness_230 source=app"), logs.filter { it.startsWith("policy: applied") })
    }

    /** Chờ ≤ 5 s tới khi có luồng khớp [match] ở trạng thái BLOCKED (đang chờ monitor). */
    private fun awaitBlocked(match: (Thread) -> Boolean): Boolean {
        repeat(250) { if (Thread.getAllStackTraces().keys.any { match(it) && it.state == Thread.State.BLOCKED }) return true; Thread.sleep(20) }
        return false
    }

    @Test fun `PC13 harness du 3 khoa + extra dung token gia, khong can INTEGRITY_CLOUD_PROJECT, thieu flagTest thi khong chay`() {
        meta = P_FULL_META - PolicyClient.META_PROJECT
        BrainPolicyHarness.onActivityCreated(activity(flag = null))
        PolicyClient.drainForTest()
        assertEquals("thieu flagTest: 0 mang", 0, calls.size)

        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2))
        BrainPolicyHarness.resetForTest()
        PolicyClient.install(ctx)   // đường sản phẩm: off (thiếu project) — không chặn harness
        BrainPolicyHarness.onActivityCreated(activity())
        BrainPolicyHarness.onActivityCreated(activity())
        PolicyClient.drainForTest()
        assertEquals("harness 1 luot: ${calls.map { it.method }}", 2, calls.size)
        assertEquals("fake-ok", calls.last().headers["X-Integrity-Token"])
        assertEquals(listOf("policy: off (${PolicyClient.META_PROJECT})", "policy: applied version=policy_harness_230 source=app"), logs)
    }

    @Test fun `PC13b harness khi verdict INVALID thi 0 mang`() {
        arm(LicenseGate.Verdict.Invalid("pkg"))
        BrainPolicyHarness.onActivityCreated(activity())
        PolicyClient.drainForTest()
        assertEquals(0, calls.size)
        assertEquals(0, logs.size)
    }

    /** #12 (2.4.0): kiểm bằng seam, không đọc text nguồn — dòng của từng luồng tới `logSink`, `logSink` mặc định = `Log.i`. */
    @Test fun `PC14 moi dong policy di Log i qua logSink, khong logD`() {
        val seen = mutableListOf<String>()
        AdsBrain.enabled = false; install(); seen += logs   // policy: off (brain)
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2)); AdsBrain.enabled = true
        policyPrefs["policy_last_attempt_ms"] = System.currentTimeMillis(); install(); seen += logs   // policy: skip (backoff …)
        arm(LicenseGate.Verdict.Valid("test", Long.MAX_VALUE / 2)); install(); seen += logs   // policy: applied …
        assertEquals(listOf("policy: off (brain)", "policy: skip (backoff 24h)", "policy: applied version=policy_harness_230 source=app"), seen)
        PolicyClient.resetForTest()
        mockStatic(Log::class.java).use { log ->
            for (line in seen + "policy: default (no_token)") {
                PolicyClient.logSink(line)
                log.verify({ Log.i(LogUtil.TAG, line) }, times(1))
            }
            log.verify({ Log.d(anyString(), anyString()) }, never())
        }
    }

    private fun activity(flag: String? = "1"): Activity {
        val pkg = "test.sdk.zd"; val res = mock(Resources::class.java)
        `when`(res.getIdentifier("flagTest", "string", pkg)).thenReturn(if (flag == null) 0 else 42)
        `when`(res.getString(42)).thenReturn(flag)
        val bundle = mock(Bundle::class.java); `when`(bundle.getBoolean(BrainPolicyHarness.META_KEY, false)).thenReturn(true)
        val pm = mock(PackageManager::class.java); `when`(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).thenReturn(ApplicationInfo().apply { metaData = bundle })
        val intent = mock(Intent::class.java); `when`(intent.getStringExtra(PolicyClient.EXTRA_FAKE_TOKEN)).thenReturn("fake-ok")
        return mock(Activity::class.java).also {
            `when`(it.packageName).thenReturn(pkg); `when`(it.resources).thenReturn(res)
            `when`(it.packageManager).thenReturn(pm); `when`(it.intent).thenReturn(intent)
        }
    }

    /** Snapshot RC chỉ có `brain_policy_android` qua đúng `FirHelperConfig.applyRemoteCf` (như `BrainPolicyTest`). */
    private fun applyRemoteBrainPolicy(json: String) {
        val snapshotClass = Class.forName("com.app.plugin.firbase.FirHelperConfig\$RemoteCfSnapshot")
        val ctor = snapshotClass.declaredConstructors.single().apply { isAccessible = true }
        val names = snapshotClass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }
        val args = ctor.parameterTypes.mapIndexed { i, t -> if (names[i] == "brainPolicy") json else if (t == String::class.java) "" else null }
        val apply = FirHelperConfig::class.java.getDeclaredMethod("applyRemoteCf", snapshotClass).apply { isAccessible = true }
        try { apply.invoke(FirHelperConfig(), ctor.newInstance(*args.toTypedArray())) } catch (e: InvocationTargetException) {
            // đuôi hàm (AdsHelper) không có context trong unit test — bước brain đã chạy trước đó
        } finally { SdkKillSwitch.current = SdkKillSwitch.NONE }
    }
}
