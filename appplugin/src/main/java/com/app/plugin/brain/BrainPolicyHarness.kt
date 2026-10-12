package com.app.plugin.brain

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.app.plugin.brain.policy.IntegrityTokenSource
import com.app.plugin.brain.policy.PolicyClient
import com.app.plugin.until.AppUtil

/**
 * Harness brain (1.9.0 Task 5s, D28): nạp policy artifact từ intent extra qua **đúng đường production** của Remote Config
 * (`DecisionEngine.applyPolicy` → `BrainPolicy.apply` + `BrainPolicyStore`), chỉ nguồn JSON khác — Task R #17 không ghi
 * Remote Config/Firebase nào. **Arm khi và chỉ khi đủ 3 khoá** như `IapHarness`, xét theo thứ tự, thiếu khoá nào thì
 * dừng ở đó (không đọc intent, không áp, không ghi): (1) `AppUtil.isTestFlagOn`; (2) meta-data [META_KEY] = `true` (chỉ
 * `app/src/utility/AndroidManifest.xml`); (3) extra [EXTRA_POLICY] = chuỗi JSON. JSON bị từ chối → 1 dòng log, không arm.
 * Khoá 1 + 2 đọc một lần ở activity đầu (main). Móc: `AppPluginBase.onActivityCreated` (D41), cạnh `IapHarness` — một
 * móc cho cả hai harness; arm tối đa một lần mỗi process.
 */
internal object BrainPolicyHarness {
    const val META_KEY = "com.app.plugin.BRAIN_HARNESS"
    const val EXTRA_POLICY = "brain_policy"
    private const val TAG = "AppPlugin"

    /** Khoá 1 + 2 của process: `null` = chưa đọc. */
    @Volatile private var processKeys: Boolean? = null

    /** Version đã arm trong process này (`null` = harness không arm). */
    @Volatile var armedVersion: String? = null; private set

    @Volatile internal var logSink: (String) -> Unit = { Log.i(TAG, it) }

    fun onActivityCreated(activity: Activity) {
        if (processKeys == false || armedVersion != null) return
        try {
            val keys = processKeys ?: (AppUtil.isTestFlagOn(activity) && manifestKeyOn(activity)).also { processKeys = it }
            if (!keys) return
            // 2.3.0 SECBC: `/policy` với token giả, bỏ yêu cầu INTEGRITY_CLOUD_PROJECT; vẫn chỉ sau verdict VALID.
            activity.intent?.getStringExtra(PolicyClient.EXTRA_FAKE_TOKEN)?.let { t -> PolicyClient.runForHarness(activity, IntegrityTokenSource { _, _ -> t }) }
            val json = activity.intent?.getStringExtra(EXTRA_POLICY) ?: return
            val result = DecisionEngine.instance.applyPolicy(json)
            if (!result.applied) {
                logSink("brain harness: artifact bị từ chối, không arm — ${result.report.replace('\n', ' ')}")
                return
            }
            armedVersion = BrainPolicy.activeVersion()
            logSink("brain harness: armed version=$armedVersion")
        } catch (e: Exception) {
            logSink("brain harness: không arm được: ${e.message}")
        }
    }

    private fun manifestKeyOn(ctx: Context): Boolean =
        ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData?.getBoolean(META_KEY, false) == true

    /** Test: như process mới. */
    internal fun resetForTest() {
        processKeys = null
        armedVersion = null
        logSink = { Log.i(TAG, it) }
    }
}
