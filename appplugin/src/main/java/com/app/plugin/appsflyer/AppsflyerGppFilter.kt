package com.app.plugin.appsflyer

import android.content.Context
import android.util.Log
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.GppConsent
import com.app.plugin.until.LogUtil
import com.appsflyer.AppsFlyerLib

/**
 * Bộ lọc partner GPP của [AppsflyerHelper] (1.6.4 Task 2, tách khỏi `AppsflyerHelper.kt`). Không trạng thái: helper giữ
 * `sharingFilterApplied` và gọi [apply] trong khoá của nó. Không gọi `ConsentState` (thứ tự khoá, review C I1).
 */
internal object AppsflyerGppFilter {
    /**
     * Opt-out theo luật bang Mỹ (GPP, [ConsentRules.usOptOut]) → `setSharingFilterForPartners("all")`, **trước** `start`:
     * AppsFlyer áp bộ lọc cho lần start này, không chia sẻ dữ liệu với partner nào. GPP có mà không opt-out, và chính SDK
     * đã đặt bộ lọc trong process (start lại sau khi rút consent) → `setSharingFilterForPartners()` rỗng (tài liệu
     * AppsFlyer: gọi không tham số là gỡ), 1.6.1 Task 5 (T8a m8). Chỉ gỡ khi chính SDK đã đặt "all" trong process;
     * AppsFlyer chỉ có một bộ lọc, nên bộ lọc host tự đặt trước đó đã bị "all" thay và không được khôi phục khi gỡ. GPP
     * vắng thì không gọi gì (như MAX, Meta), trừ 1.6.3 Task 6 (quyết định chủ dự án C + GRANTED): SDK đã lọc, header không
     * còn section US 7–12 ([GppConsent.headerHasUsSection] `== false`) **và** [tier] `GRANTED` → gỡ; `GppSID=-1` một mình
     * hay chuỗi hỏng thì giữ. Đổi opt-out giữa phiên khi không start lại chỉ áp từ lần start kế ("Giới hạn đã biết").
     * Đọc `IABGPP_*` trên luồng của cổng analytics, cùng chỗ đọc `IABTCF_*`. `AppsFlyerConsent` (DMA/EU) không đổi.
     * Trả trạng thái mới của "SDK đã đặt bộ lọc trong process" ([applied] = trạng thái cũ).
     */
    internal fun apply(af: AppsFlyerLib, app: Context, tier: ConsentTier, applied: Boolean): Boolean {
        var sharingFilterApplied = applied
        val gpp = GppConsent.read(app)
        when {
            ConsentRules.usOptOut(gpp) -> {
                // = setSharingFilterForAllPartners() (deprecated V6.4; javap 6.16.2: gọi đúng dòng này với "all")
                af.setSharingFilterForPartners("all")
                sharingFilterApplied = true
                Log.d(LogUtil.TAG, "appsflyer sharing filter: all partners (${ConsentRules.gppLabel(gpp)})")
            }
            gpp != null && sharingFilterApplied -> {
                af.setSharingFilterForPartners()   // rỗng = không lọc partner nào
                sharingFilterApplied = false
                Log.d(LogUtil.TAG, "appsflyer sharing filter: cleared (${ConsentRules.gppLabel(gpp)})")
            }
            // 1.6.3 Task 6 (C + GRANTED): GPP đọc là vắng nhưng SDK đã lọc trong process; gỡ chỉ khi header không còn section
            // US (UMP viết lại chuỗi) VÀ tầng GRANTED. SID = -1 một mình (form EEA cùng process) không gỡ: chuỗi vẫn ghi opt-out.
            // headerHasUsSection == null (chuỗi vắng/hỏng) không gỡ (fail-closed).
            gpp == null && sharingFilterApplied && tier == ConsentTier.GRANTED && GppConsent.headerHasUsSection(app) == false -> {
                af.setSharingFilterForPartners()
                sharingFilterApplied = false
                Log.d(LogUtil.TAG, "appsflyer sharing filter: cleared (gpp=none, header không còn section US, tier=GRANTED)")
            }
            // gpp == null (không có GPP), hoặc chưa từng đặt: không gọi gì — SDK không ghi cờ giả (như MAX, Meta).
        }
        return sharingFilterApplied
    }
}
