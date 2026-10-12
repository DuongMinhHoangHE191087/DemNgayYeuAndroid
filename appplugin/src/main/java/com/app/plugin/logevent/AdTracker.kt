package com.app.plugin.logevent

internal object AdTracker {
    private val adStates = java.util.concurrent.ConcurrentHashMap<String, AdState>()

    fun updateStatus(adType: String, status: AdStatus) {
        adStates[adType] = AdState(status)
    }

    fun getStatus(adType: String): Int = adStates[adType]?.status?.code ?: AdStatus.NotLoaded.code
}

internal enum class AdStatus(val code: Int) {
    NotLoaded(0),
    Loading(1),
    Loaded(2),
    Failed(3)
}

internal data class AdState(
    var status: AdStatus
)
