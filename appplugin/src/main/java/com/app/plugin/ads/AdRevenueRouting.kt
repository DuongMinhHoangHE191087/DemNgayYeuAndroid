package com.app.plugin.ads

import android.content.Context
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil

/**
 * Doc 62 mục 2.3: app đã liên kết AdMob với Firebase trên console thì Firebase tự ghi
 * `ad_impression` cho AdMob; SDK ghi thêm là đếm đôi doanh thu. Chỉ chặn event Firebase đó —
 * AppsFlyer, sự kiện hiển thị và log server không phụ thuộc liên kết.
 */
internal object AdRevenueRouting {
    /** App đặt integer resource `ads_admob_firebase_linked` = 1 khi AdMob đã liên kết Firebase. */
    @Volatile
    var admobLinkedToFirebase: Boolean = false

    fun shouldLogFirebaseAdImpression(platform: String): Boolean =
        !(platform == "admob" && admobLinkedToFirebase)

    /**
     * Đúng 1 là đã liên kết. App không khai (`getResourceInt` trả 0) hay khai giá trị khác là chưa
     * liên kết, SDK ghi `ad_impression` như trước. Đọc lỗi thì log và coi là chưa liên kết (Điều 7):
     * đếm đôi còn lọc lại được, doanh thu AdMob mất khỏi Firebase thì không.
     */
    fun readAdmobLinked(context: Context): Boolean =
        try {
            AppUtil.getResourceInt(context, "ads_admob_firebase_linked") == 1
        } catch (e: Exception) {
            LogUtil.logE("ads_admob_firebase_linked read failed, AdMob ad_impression stays on: ${e.message}")
            false
        }
}
