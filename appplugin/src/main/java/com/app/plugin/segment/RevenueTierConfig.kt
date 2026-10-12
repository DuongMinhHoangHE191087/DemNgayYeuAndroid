package com.app.plugin.segment

public data class RevenueTierConfig(
    val starvedMinSessions: Int = 10,
    val productiveWindowMillis: Long = 14L * 24 * 60 * 60 * 1000,
    val starvedDismissKillSwitch: Int = 3,
    val killSwitchCooldownDays: Long = 30
) {
    public companion object {
        public val DEFAULT: RevenueTierConfig = RevenueTierConfig()
    }
}
