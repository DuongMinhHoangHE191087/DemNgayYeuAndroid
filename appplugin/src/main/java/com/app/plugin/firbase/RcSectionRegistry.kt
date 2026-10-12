package com.app.plugin.firbase

import com.app.plugin.AppPluginInternalApi
import com.app.plugin.brain.BrainSignalListener
import com.app.plugin.until.LogUtil
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Seam core → game (2.0.0 C1d, D1/D16, plan §Hợp đồng C): khoá RC → hàm áp trả về báo cáo. `FirHelperConfig` đọc giá trị
 * mọi khoá đã đăng ký trên luồng config, áp theo **thứ tự đăng ký** trên main và ghi `<tag> (RemoteConfig): <report>`;
 * core không gọi lớp game nào. Mã game tự đăng ký trong `GameModule.install()` ([GAME_MODULE_CLASS], keep rule trong
 * `appplugin-game/consumer-rules.pro`, 2.1.0 G1); core dò lớp đó **một lần** mỗi process ([ensureGameModule]): sau init RC,
 * hoặc ở tín hiệu brain đầu tiên nếu tới trước. Đăng ký chỉ giữ hàm: 0 I/O, 0 gửi (R-P1). Lớp vắng → 0 section, không lỗi.
 * 2.1.0 G1: object public chỉ để `appplugin-game` gọi [register] (SPI opt-in [AppPluginInternalApi]); mọi thứ khác `internal`.
 */
public object RcSectionRegistry {
    /** Một section: khoá RC, nhãn dòng log, hàm áp (chuỗi RC khác rỗng → báo cáo). */
    internal class Section(val key: String, val tag: String, val apply: (String) -> String)

    internal const val GAME_MODULE_CLASS = "com.app.plugin.game.GameModule"
    private val defaultClassFor: (String) -> Class<*> = { Class.forName(it) }
    /** Cách nạp lớp `GameModule`; test thay để đếm lần dò hoặc giả "vắng". */
    @Volatile internal var classFor: (String) -> Class<*> = defaultClassFor
    private val lock = Any()
    @Volatile private var probed = false
    private val sections = CopyOnWriteArrayList<Section>()

    /** Đăng ký; cùng khoá → thay tại chỗ (giữ vị trí). Thứ tự đăng ký = thứ tự áp và thứ tự dòng log. */
    @AppPluginInternalApi
    public fun register(key: String, tag: String, apply: (String) -> String) {
        synchronized(lock) {
            val i = sections.indexOfFirst { it.key == key }
            if (i >= 0) sections[i] = Section(key, tag, apply) else sections.add(Section(key, tag, apply))
        }
    }

    /** Ảnh chụp các section đã đăng ký, theo thứ tự đăng ký. */
    internal fun sections(): List<Section> = sections.toList()

    /** Dò [GAME_MODULE_CLASS] đúng một lần mỗi process, mọi kết cục (không ném); luồng khác chờ tới khi `install()` xong. */
    internal fun ensureGameModule() {
        if (probed) return
        synchronized(lock) {
            if (!probed) {
                try { install() } finally { probed = true }
            }
        }
    }

    private fun install() {
        val cls = try {
            classFor(GAME_MODULE_CLASS)
        } catch (e: ClassNotFoundException) {
            LogUtil.logD("game: không có $GAME_MODULE_CLASS — 0 section game")
            return
        } catch (e: Throwable) {
            LogUtil.logE("game: không nạp được $GAME_MODULE_CLASS: $e")
            return
        }
        try {
            val before = sections.size
            cls.getMethod("install").invoke(cls.getField("INSTANCE").get(null))
            // 2.1.0 G1 (R-G1 I1): bằng chứng dò "có" cho cổng thiết bị (APK game), cặp với dòng "không có" ở trên.
            LogUtil.logD("game: GameModule installed — ${sections.size - before} section")
        } catch (e: Throwable) {
            LogUtil.logE("game: không cài được $GAME_MODULE_CLASS: $e")
        }
    }

    /** Về như process mới: 0 section, 0 listener, dò lại ở lần sau bằng `Class.forName`. Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest() {
        synchronized(lock) {
            probed = false
            sections.clear()
            BrainSignalListener.installed = null
            classFor = defaultClassFor
        }
    }
}
