package com.app.plugin.ads

/** Configure on the main thread before loading ads. Zero refresh delegates to the network. */
public class AdsOptimizationPolicy {
    public var adaptiveBanner: Boolean = true
    internal var refreshOverridden = false
        private set
    public var bannerRefreshSeconds: Int = 60
        set(value) { field = if (value == 0) 0 else value.coerceIn(30, 150); refreshOverridden = true }

    // Explicit opt-in: hosts know which placements have compatible layouts and lifecycle.
    private val nativeSources = mutableMapOf<String, List<String>>()
    public fun setNativeReuseSources(target: String, sources: List<String>) {
        require(target.startsWith("nt_")) { "Native target must start with nt_" }
        require(sources.all { it.startsWith("nt_") }) { "Native sources must start with nt_" }
        nativeSources[target] = sources.filter { it != target }.distinct().toList()
    }
    public fun nativeReuseSources(target: String): List<String> = nativeSources[target].orEmpty().toList()
}
