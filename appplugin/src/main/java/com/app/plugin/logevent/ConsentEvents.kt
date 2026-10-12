package com.app.plugin.logevent

import android.content.Context
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GppConsent
import com.app.plugin.consent.TcfConsent
import com.app.plugin.until.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Phễu consent lên server (1.7.0 Task 4, plan "Hợp đồng event v2"): `consent_form_shown` ngay trước khi UMP hiện form
 * (`initial` khi UMP báo `REQUIRED`, `privacy_options` khi app mở form tuỳ chọn), `consent_choice` khi form có câu trả lời
 * (`ConsentManager.updateTier`, **sau** `ConsentState.setTier`: event mang tầng mới và đi qua cổng theo tầng đó).
 *
 * Q2 (sign-off 1.7.0 R2): không có đường gửi riêng. Hai event đi [AdEventLog] → `LogEventTransport.sendEvent` →
 * `TelemetryConsentGate` như mọi event: trước câu trả lời thì giữ (PINNED, tràn trần không bị đẩy ra), `DENIED` thì
 * `form_shown` đang giữ bị bỏ và `consent_choice` vào vòng RAM (xả chỉ khi tầng thành `GRANTED`/`PARTIAL` trong cùng process).
 * Người `DENIED` vì vậy không có byte consent nào tới backend; tỉ lệ đồng ý lấy từ báo cáo AdMob Privacy & messaging.
 *
 * Luồng: [shown]/[choice] (bản nhận `Context`) chạy trên main trong callback UMP, chỉ ghi mốc thời gian; đọc `IABTCF_*`/`IABGPP_*`
 * (prefs) và dựng event chạy trên IO. Không hàm nào ném ra `ConsentManager`: lỗi → một dòng logE, luồng consent đi tiếp.
 */
internal object ConsentEvents {
    const val INITIAL = "initial"
    const val PRIVACY_OPTIONS = "privacy_options"

    /** Form đã hiện, chờ câu trả lời: mốc [shownAtMs] (đồng hồ đơn điệu) và loại form. */
    private class Pending(val shownAtMs: Long, val formKind: String)

    private val pending = AtomicReference<Pending?>(null)
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Form sắp hiện: ghi mốc, phát `consent_form_shown` trên IO. Form hiện lại (Activity tạo lại, form tuỳ chọn) thay mốc cũ:
     * `latency_ms` đo từ lần hiện người dùng trả lời.
     */
    fun shown(context: Context?, formKind: String) = guarded("shown") {
        pending.set(Pending(nowMs(), formKind))
        onIo { AdEventLog.consentFormShown(gdprAppliesOf(context), formKind) }
    }

    /**
     * Form có câu trả lời, [tier] là tầng **đã** cập nhật. Chỉ phát khi có form đang chờ (mỗi lần hiện một `consent_choice`);
     * không có thì bỏ qua (cập nhật tầng không đến từ form).
     */
    fun choice(tier: ConsentTier, context: Context?) = guarded("choice") {
        val form = pending.getAndSet(null) ?: return@guarded
        val latencyMs = (nowMs() - form.shownAtMs).coerceAtLeast(0L)
        onIo { AdEventLog.consentChoice(tier.name, gdprAppliesOf(context), gppSectionsOf(context), latencyMs, form.formKind) }
    }

    /** `IABTCF_gdprApplies` → 1/0, `-1` khi vắng hoặc không đọc được (không phải "ngoài GDPR"). */
    internal fun gdprAppliesOf(context: Context?): Int = when (context?.let(TcfConsent::gdprApplies)) {
        true -> 1
        false -> 0
        null -> -1
    }

    /** Id section GPP trong header (`7`, `7_8`), nối `_`; rỗng khi không có GPP hay không có section US áp dụng. */
    internal fun gppSectionsOf(context: Context?): String =
        context?.let(GppConsent::read)?.sectionIds?.joinToString("_").orEmpty()

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private fun onIo(block: () -> Unit) {
        ioScope.launch {
            try {
                block()
            } catch (e: Exception) {
                LogUtil.logE("consent funnel event not sent: ${e.message}")
            }
        }
    }

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            LogUtil.logE("ConsentEvents.$what failed: ${e.message}")
        }
    }
}
