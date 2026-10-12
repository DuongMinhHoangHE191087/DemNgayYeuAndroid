package com.app.plugin.logevent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hợp đồng thứ tự init telemetry trong `AppPluginBase.initPlugin` (DOC/66 D2): `LogEventServer.init` gọi
 * `DecisionLogger.flushQueue()`, replay trên luồng IO dựng body bằng `generateGlobalPropertiesData()`.
 * `LogEventServerHelper.init` (gán `app_name`, `app_version`, `country`, `session_id`…) phải chạy trước, nếu không bản
 * ghi replay có thể mang các trường đó rỗng (`.superpowers/sdd/research-startup-telemetry.md` mục B2). `user_id` từ
 * 1.4.0 đọc lúc dựng body (`LogEventServerHelperTest`, #13), không còn gán ở `init`. Quét mã nguồn như
 * `EventNameContractTest`; bỏ qua dòng chú thích.
 */
class LogEventInitOrderContractTest {
    private val appPluginBase = File("src/main/java/com/app/plugin/app/AppPluginBase.kt")

    /** Chỉ số (0-based) của dòng mã đầu tiên khớp [call]; -1 nếu không có. */
    private fun firstCallLine(lines: List<String>, call: Regex): Int = lines.indexOfFirst { line ->
        val code = line.trim()
        !code.startsWith("//") && !code.startsWith("*") && call.containsMatchIn(code)
    }

    @Test
    fun `LogEventServerHelper init truoc LogEventServer init, neu khong replay decision log mang user property rong`() {
        // Chạy sai thư mục làm việc thì không đọc được nguồn và test PASS giả: dừng ngay.
        assertTrue("Không thấy ${appPluginBase.absolutePath} (test phải chạy từ gốc module)", appPluginBase.isFile)
        val lines = appPluginBase.readLines()

        val helperInit = firstCallLine(lines, Regex("""\bLogEventServerHelper\.init\("""))
        val serverInit = firstCallLine(lines, Regex("""\bLogEventServer\.init\("""))

        assertTrue("AppPluginBase.kt không gọi LogEventServerHelper.init(", helperInit >= 0)
        assertTrue("AppPluginBase.kt không gọi LogEventServer.init(", serverInit >= 0)
        assertTrue(
            "AppPluginBase.kt:${helperInit + 1} LogEventServerHelper.init phải đứng trước " +
                "AppPluginBase.kt:${serverInit + 1} LogEventServer.init: replay decision log đọc user property khi dựng body",
            helperInit < serverInit
        )
    }

    @Test
    fun `TelemetryConsentGate install truoc LogEventServer init, neu khong event dau (replay, first_open) di truoc consent`() {
        assertTrue("Không thấy ${appPluginBase.absolutePath} (test phải chạy từ gốc module)", appPluginBase.isFile)
        val lines = appPluginBase.readLines()

        val gateInstall = firstCallLine(lines, Regex("""\bTelemetryConsentGate\.install\("""))
        val serverInit = firstCallLine(lines, Regex("""\bLogEventServer\.init\("""))

        assertTrue("AppPluginBase.kt không gọi TelemetryConsentGate.install( (O3 (a), 1.6.0 Task 6)", gateInstall >= 0)
        assertTrue(
            "AppPluginBase.kt:${gateInstall + 1} TelemetryConsentGate.install phải đứng trước " +
                "AppPluginBase.kt:${serverInit + 1} LogEventServer.init: init replay decision log, sessionStart gửi first_open",
            gateInstall in 0 until serverInit
        )
    }

    // IO1 (R-B1 m2/m3, M29): LogEventServer.init replay tập bền; DurableEvents.attach (trong AppPluginManager.initPlugin)
    // phải chạy trước, nếu không replay thấy hàng đợi chưa gắn và bỏ qua event tiền.
    @Test
    fun `DurableEvents attach trong AppPluginManager initPlugin chay truoc LogEventServer init, neu khong replay tap ben bo qua event tien`() {
        val manager = File("src/main/java/com/app/plugin/app/AppPluginManager.kt")
        assertTrue("Không thấy ${appPluginBase.absolutePath} (test phải chạy từ gốc module)", appPluginBase.isFile && manager.isFile)
        val baseLines = appPluginBase.readLines()

        val managerInit = firstCallLine(baseLines, Regex("""\bAppPluginManager\.instance\.initPlugin\("""))
        val serverInit = firstCallLine(baseLines, Regex("""\bLogEventServer\.init\("""))
        val attach = firstCallLine(manager.readLines(), Regex("""\bDurableEvents\.attach\("""))

        assertTrue("AppPluginBase.kt không gọi AppPluginManager.instance.initPlugin(", managerInit >= 0)
        assertTrue("AppPluginManager.kt không gọi DurableEvents.attach(", attach >= 0)
        assertTrue(
            "AppPluginBase.kt:${managerInit + 1} AppPluginManager.initPlugin (chứa DurableEvents.attach) phải đứng trước " +
                "AppPluginBase.kt:${serverInit + 1} LogEventServer.init: replay bền đọc hàng đợi đã gắn",
            managerInit in 0 until serverInit
        )
    }
}
