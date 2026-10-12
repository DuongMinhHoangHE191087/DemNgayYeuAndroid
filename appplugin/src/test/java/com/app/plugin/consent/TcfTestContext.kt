package com.app.plugin.consent

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Vendor Google (755) đã đồng ý trong `IABTCF_VendorConsents`. */
internal val GOOGLE_VENDOR_OK = "0".repeat(754) + "1"

/**
 * Chuỗi GPP US National (section 7), header `DBABLA` (Type 3, Version 1, [7]) như ví dụ đặc tả IAB; core Version 1,
 * notice = 1, `SaleOptOut`/`SharingOptOut`/`TargetedAdvertisingOptOut` = 1 (opt-out). Dựng bằng encoder của
 * `GppConsentTest` (1.6.0 Task 8a).
 */
internal const val GPP_US_OPT_OUT = "DBABLA~BVVVAAA"

/** Như [GPP_US_OPT_OUT] nhưng ba trường opt-out = 2 (không opt-out); cùng tiền tố với ví dụ đặc tả `BVVqAAEABAA`. */
internal const val GPP_US_OK = "DBABLA~BVVqAAA"

/**
 * Context giả cho test: `IABTCF_*` trong preferences mặc định như UMP ghi, và (tuỳ chọn) string
 * resource `af_app_id`. [gdpr] `null` = khoá `IABTCF_gdprApplies` vắng. [gpp]: `IABGPP_HDR_GppString` đọc lúc hỏi
 * (đổi được giữa test), `IABGPP_GppSID` = [gppSid] (mặc định `"7"` khi có chuỗi); mặc định vắng.
 */
internal fun contextWithTcf(
    gdpr: Int?,
    purposes: String,
    vendors: String = GOOGLE_VENDOR_OK,
    afAppId: String? = null,
    gpp: () -> String? = { null },
    gppSid: () -> String? = { gpp()?.let { "7" } },
): Context {
    val prefs = tcfPrefs(gdpr, purposes, vendors, gpp, gppSid)
    val resources = mock(Resources::class.java)
    if (afAppId != null) {
        `when`(resources.getIdentifier("af_app_id", "string", "com.example.app")).thenReturn(AF_APP_ID_RES)
        `when`(resources.getString(AF_APP_ID_RES)).thenReturn(afAppId)
    }
    val context = mock(Context::class.java)
    `when`(context.packageName).thenReturn("com.example.app")
    `when`(context.resources).thenReturn(resources)
    `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
    return context
}

/**
 * Như [contextWithTcf] nhưng là `Application` (`applicationContext` là chính nó), với string resource
 * `facebook_app_id` = [fbAppId] (nếu khác `null`): cho `MetaSdkStarter`, vốn cần `Application`.
 */
internal fun applicationWithTcf(
    gdpr: Int?,
    purposes: String,
    fbAppId: String? = "1234567890123456",
    gpp: () -> String? = { null },
): Application {
    val resources = mock(Resources::class.java)
    if (fbAppId != null) {
        `when`(resources.getIdentifier("facebook_app_id", "string", "com.example.app")).thenReturn(FB_APP_ID_RES)
        `when`(resources.getString(FB_APP_ID_RES)).thenReturn(fbAppId)
    }
    val prefs = tcfPrefs(gdpr, purposes, GOOGLE_VENDOR_OK, gpp)
    val app = mock(Application::class.java)
    `when`(app.applicationContext).thenReturn(app)
    `when`(app.packageName).thenReturn("com.example.app")
    `when`(app.resources).thenReturn(resources)
    `when`(app.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
    return app
}

private fun tcfPrefs(
    gdpr: Int?,
    purposes: String,
    vendors: String,
    gpp: () -> String?,
    gppSid: () -> String? = { gpp()?.let { "7" } },
): SharedPreferences {
    val prefs = mock(SharedPreferences::class.java)
    `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(gdpr != null)
    if (gdpr != null) `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(gdpr)
    `when`(prefs.getString("IABTCF_PurposeConsents", "")).thenReturn(purposes)
    `when`(prefs.getString("IABTCF_PurposeLegitimateInterests", "")).thenReturn("0000000000")
    `when`(prefs.getString("IABTCF_VendorConsents", "")).thenReturn(vendors)
    `when`(prefs.getString(eq("IABGPP_HDR_GppString"), nullable(String::class.java))).thenAnswer { gpp() }
    `when`(prefs.getString(eq("IABGPP_GppSID"), nullable(String::class.java))).thenAnswer { gppSid() }
    return prefs
}

private const val AF_APP_ID_RES = 0x7f0f0001
private const val FB_APP_ID_RES = 0x7f0f0002
