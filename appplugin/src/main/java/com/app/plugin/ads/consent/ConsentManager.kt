package com.app.plugin.ads.consent

import android.app.Activity
import android.content.Context
import com.app.plugin.consent.ConsentManager as UnifiedConsentManager

/**
 * Lớp chuyển tiếp cho app đang gọi `ConsentManager.instance` của bản fork (Speaker Clean,
 * Invoice Maker). Mọi lời gọi đi thẳng về [com.app.plugin.consent.ConsentManager], nên hai kiểu
 * gọi dùng chung một luồng UMP và một [com.app.plugin.consent.ConsentGate].
 *
 * Giữ trong suốt 2.x, gỡ ở 3.0.
 *
 * Khác bản fork: chế độ `flagTest` không còn tự ép khu vực EEA. Muốn giữ hành vi cũ khi QA thì
 * đặt `com.app.plugin.consent.ConsentManager.debugForceEea = BuildConfig.DEBUG` trước khi gọi.
 */
@Deprecated(
    "Dùng com.app.plugin.consent.ConsentManager (object). Lớp này sẽ bị gỡ ở 3.0.",
    ReplaceWith("com.app.plugin.consent.ConsentManager"),
)
public class ConsentManager private constructor() {
    public companion object {
        public val instance: ConsentManager by lazy { ConsentManager() }

        public fun isConsentResolved(context: Context): Boolean =
            UnifiedConsentManager.isConsentResolved(context)
    }

    public fun gatherConsent(activity: Activity, onDone: () -> Unit) {
        UnifiedConsentManager.gatherConsent(activity, onDone)
    }

    public fun isPrivacyOptionsRequired(context: Context): Boolean =
        UnifiedConsentManager.isPrivacyOptionsRequired(context)

    public fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit) {
        UnifiedConsentManager.showPrivacyOptions(activity) { onDismissed() }
    }
}
