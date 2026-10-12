package com.app.plugin.segment

import android.content.Context
import android.content.res.Resources
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.inMemoryPrefs
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.Executor

/**
 * Chung cho test segment 2.2.0 (không Robolectric): prefs trong bộ nhớ sau một `Context` giả, RC giả qua seam
 * `SegmentHooks.rcLong`/`rcString`, sink user property đếm được, và engine mới (constructor private) cho test không
 * muốn dùng singleton. [reset] trả mọi seam về mặc định "không RC, sink ghi vào [properties]".
 */
internal object SegmentTestSupport {
    val properties: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())

    fun prefsContext(store: MutableMap<String, Any?> = newStore()): Context {
        val context = mock(Context::class.java)
        val prefs = inMemoryPrefs(store)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        val resources = mock(Resources::class.java) // mạng quảng cáo giả đọc resource lúc dựng (AdsBase.loadFromMem)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.applicationContext).thenReturn(context) // EcpmBucketResolver.init giữ applicationContext
        return context
    }

    fun newStore(): MutableMap<String, Any?> = Collections.synchronizedMap(mutableMapOf())

    fun rc(longs: Map<String, Long> = emptyMap(), strings: Map<String, String> = emptyMap()) {
        SegmentHooks.rcLong = { longs[it] }
        SegmentHooks.rcString = { strings[it] }
    }

    fun newEngine(): UserSegmentEngine =
        UserSegmentEngine::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    /** [tier] null: không chạm `ConsentState` (test của lớp khác dùng segment mà không đổi tầng consent của nó). */
    fun reset(tier: ConsentTier? = ConsentTier.GRANTED) {
        SegmentHooks.resetForTest()
        freshSingleton()
        SegmentStartup.executor = Executor { it.run() } // nạp đồng bộ trên luồng test; test D27 thay bằng executor tay
        rc()
        properties.clear()
        SegmentHooks.propertySink = { name, value -> properties += name to value }
        if (tier == null) return
        ConsentState.resetForTest()
        ConsentState.setTier(tier)
    }

    /** Singleton về trạng thái trước `init` (context null, scorer mặc định): test không xanh nhờ trạng thái rò từ test trước. */
    fun freshSingleton() {
        val e = UserSegmentEngine.instance
        val fresh = newEngine() // mọi field thể hiện (context, scorer, config, forcedSegment, mốc thời gian, flow, tầng cuối…)
        UserSegmentEngine::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            .forEach { it.isAccessible = true; it.set(e, it.get(fresh)) }
    }

    const val HOUR_MS: Long = 60L * 60 * 1000
}
