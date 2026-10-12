package com.app.plugin.ads

import android.content.Context
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Nguồn của `cf_ads_placement` (2.4.2 SWITCH, D1-242); [prefKey] = bản ghi riêng của nguồn trong `App_prefer_file`. */
internal enum class PlacementSource(val prefKey: String) {
    REMOTE_CONFIG("mem_cf_ads_placement_rc"),
    FIRESTORE("mem_cf_ads_placement_fs"),
}

/**
 * Công tắc placement theo nguồn (2.4.2 SWITCH, Hợp đồng B + D1-242), tương đương `PlacementSwitches` của fork qua
 * `cf_ads_placement`: mỗi nguồn nhớ những khoá chính nó đã tắt; lần áp kế bật lại (`flagShow = 1`) đúng
 * `cũ của nguồn − mới của nguồn − khoá nguồn kia đang tắt` (fork `reEnabled(previous, current)` theo từng nguồn).
 * Chuỗi rỗng/`{}` = nguồn không đặt gì. JSON hỏng: giữ nguyên trạng thái và bản lưu, `logE`. Khoá `k` áp cho `k` và
 * bí danh (`full_x` ↔ `x`), JSON có cả hai thì khoá đúng tên thắng. Lưu: bản ghi của nguồn ([PlacementSource.prefKey])
 * và bản gộp [KEY_MERGED] (như trước, đọc lúc cold start); app nâng từ 2.4.1 chưa có bản ghi nguồn → bản gộp coi là RC.
 */
internal class PlacementSwitchState(private val manager: AdsPlacementConfigManager) {
    private val flagsBySource = mutableMapOf<PlacementSource, Map<String, Int>>()

    /** Cold start ([AdsHelper.initAdsHelper]): áp bản gộp, nạp bản ghi theo nguồn (không áp lại). */
    fun restore(context: Context) {
        val merged = PreUtil.getString(context, KEY_MERGED, "")
        manager.parserConfig(merged)
        val rc = PreUtil.getString(context, PlacementSource.REMOTE_CONFIG.prefKey, "")
        val fs = PreUtil.getString(context, PlacementSource.FIRESTORE.prefKey, "")
        val legacy = rc.isEmpty() && fs.isEmpty() && merged.isNotEmpty()
        flagsBySource[PlacementSource.REMOTE_CONFIG] = parse(if (legacy) merged else rc).orEmpty()
        flagsBySource[PlacementSource.FIRESTORE] = parse(fs).orEmpty()
    }

    fun apply(context: Context, source: PlacementSource, data: String) {
        val flags = parse(data)
        if (flags == null) {
            LogUtil.logE("ads placement switches: ${source.name} cf_ads_placement JSON hỏng, giữ nguyên")
            return
        }
        val other = flagsBySource[otherOf(source)].orEmpty()
        val reEnabled = offKeys(flagsBySource[source].orEmpty()) - offKeys(flags) - offKeys(other)
        reEnabled.forEach { manager.peekConfig(it)?.flagShow = 1 }
        flags.forEach { (key, flag) -> manager.getConfig(key, flag)?.flagShow = flag }
        flagsBySource[source] = flags
        PreUtil.setString(context, source.prefKey, if (flags.isEmpty()) "" else data)
        PreUtil.setString(context, KEY_MERGED, encode(other + flags))
        LogUtil.logD("ads placement switches ${source.name}: off=${offKeys(flags)} reEnabled=$reEnabled")
    }

    /** Nguồn này đang đặt ít nhất một khoá (lần áp không rỗng gần nhất chưa bị chuỗi rỗng thay). */
    fun hasRecord(source: PlacementSource): Boolean = flagsBySource[source].orEmpty().isNotEmpty()

    private fun otherOf(source: PlacementSource): PlacementSource =
        if (source == PlacementSource.REMOTE_CONFIG) PlacementSource.FIRESTORE else PlacementSource.REMOTE_CONFIG

    private fun offKeys(flags: Map<String, Int>): Set<String> = flags.filterValues { it < 0 }.keys

    private fun encode(flags: Map<String, Int>): String =
        if (flags.isEmpty()) "" else Gson().toJson(flags.mapValues { (_, flag) -> mapOf("flag" to flag) })

    companion object {
        const val KEY_MERGED: String = "mem_cf_ads_placement"

        /** `null` = JSON hỏng; rỗng = chuỗi rỗng, `{}`, `null`. Khoá có dấu phẩy tách nhiều placement như [AdsPlacementConfigManager.parserConfig]. */
        fun parse(data: String): Map<String, Int>? {
            if (data.isBlank()) return emptyMap()
            val raw: Map<String, Any>? = try {
                Gson().fromJson(data, object : TypeToken<Map<String, Any>>() {}.type)
            } catch (e: Exception) {
                return null
            }
            val exact = mutableMapOf<String, Int>()
            val alias = mutableMapOf<String, Int>()
            for ((key, value) in raw.orEmpty()) {
                val cf = value as? Map<*, *> ?: continue
                if (!cf.containsKey("flag")) continue
                val flag = (cf["flag"].toString().toDoubleOrNull() ?: 1.0).toInt()
                for (k in key.split(",")) {
                    exact[k] = flag
                    alias[if (k.startsWith("full_")) k.removePrefix("full_") else "full_$k"] = flag
                }
            }
            return alias + exact
        }
    }
}
