package com.app.plugin.brain

import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper

/**
 * Artifact brain cuối cùng được **chấp nhận** (1.9.0 Task 5s, D7), giữ qua cold start.
 *
 * Trước 1.9.0 artifact chỉ sống trong bộ nhớ: mỗi lần mở app brain chạy giá trị mặc định trong code cho tới khi Remote
 * Config về, nên `enabled:false` hay `rollout_fraction: 0` (đường lùi khẩn cấp) mất hiệu lực ở mọi cold start.
 *
 * - [save]: chỉ `DecisionEngine.applyPolicy` gọi, và chỉ khi `BrainPolicy.apply` đã chấp nhận — bản hỏng không bao giờ
 *   thay bản đang chạy ở lần mở sau. Ghi qua `PreferenceHelper.setValue` = `apply()`: không ghi đĩa đồng bộ trên main,
 *   không giữ khoá nào (#9/#31).
 * - [load]: `DecisionEngine` lúc dựng (lần chạm đầu trong `initPlugin`, trước quyết định đầu tiên). File prefs đã được
 *   `AppPluginBase.onCreate` nạp (PrefsWarmup) — đây là đọc bộ nhớ, cùng file với `BrainStorage`.
 *
 * Cùng mẫu `AdsPolicy.save/restore` (`mem_ads_policy_android`).
 */
internal object BrainPolicyStore {
    const val PREF_KEY = "mem_brain_policy_android"

    fun save(json: String) = PreferenceHelper.getInstance().setValue(PREF_KEY, json)

    /** Bản đã lưu; `null` khi chưa có (hoặc prefs chưa `init`, như trong unit test không cài prefs). */
    fun load(): String? = PreferenceHelper.getInstance().get(PREF_KEY, "").takeIf { it.isNotBlank() }

    /** Lúc dựng engine: áp bản đã lưu bằng [apply]; bị từ chối (hỏng, thiếu version) thì đúng một logE, artifact đang chạy giữ nguyên (mặc định trong code khi chưa có bản nào, R-T5s m3). */
    fun restore(apply: (String) -> BrainPolicy.ApplyResult) {
        val result = apply(load() ?: return)
        if (!result.applied) LogUtil.logE("brain policy: saved artifact rejected on restore, keeping the current artifact (${result.report})")
    }
}
