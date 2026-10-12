package com.app.plugin.logevent

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.app.plugin.ads.AdsPolicyTiers
import com.app.plugin.brain.PlayerIdSource
import com.app.plugin.until.Constants
import com.app.plugin.until.PreferenceHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

internal object LogEventServerHelper {

    /**
     * Nguồn `user_id`: player id đang có hiệu lực của brain (`PlayerIdSource`), không phải ANDROID_ID (#13).
     * Đọc lúc dựng body, không đệm ở [init]: `MonetizationSdk.configure { playerId }` chạy sau [init] (sau
     * `super.onCreate()` của app) vẫn có hiệu lực cho event dựng sau đó. Test thay được.
     */
    @Volatile
    internal var userIdSource: () -> String = { PlayerIdSource.current() }

    // BUG-FIX: các field này được set 1 lần trong init() (main thread) nhưng đọc lại
    // từ coroutine Dispatchers.IO trong generateGlobalPropertiesData() → cần @Volatile
    // để đảm bảo cross-thread visibility (cùng họ với ScreenName.name)
    @Volatile
    private var appName: String = ""
    @Volatile
    private var appVersion: String = ""
    @Volatile
    private var language: String = ""
    @Volatile
    private var country: String = ""
    @Volatile
    private var deviceInfo: String = ""
    @Volatile
    private var sessionNumber: Int = 0
    @Volatile
    private var sessionId: String = ""
    @Volatile
    private var installDay: Int = 0
    @Volatile
    private var retentionDay: Int = 0
    private var platform: String = "Android"
    @Volatile
    private var abTest: Int = 4
    @Volatile
    private var packageName: String = ""

    internal fun currentSessionId(): String = sessionId

    fun init(context: Context) {
        appName = getAppName(context)
        appVersion = getVersionName(context)
        language = Locale.getDefault().language
        onCountryKnown(getCountryCode(context))
        deviceInfo = Build.MANUFACTURER
        sessionNumber = PreferenceHelper.getInstance().get(Constants.Common.TIME_OPEN, 0)
        sessionId = UUID.randomUUID().toString()
        installDay = getFirstInstallDay(context)
        retentionDay = getDaysSinceFirstOpen(context)
        abTest = PreferenceHelper.getInstance().get(Constants.EXPERIMENT_GROUP, 4)
        packageName = context.packageName.orEmpty()
        Log.i("AppPlugin", "logevent: session_id=$sessionId")
    }


    private fun getAppName(context: Context): String {
        try {
            val applicationInfo = context.applicationInfo
            return context.packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.e("Error", "Error: $e")
            return "unknown"
        }
    }

    private fun getVersionName(context: Context): String {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            // `versionName` là nullable thật (manifest có thể không khai báo). compileSdk 34
            // chưa gắn @Nullable nên Kotlin coi là platform type và không bắt lỗi; từ 35 trở
            // đi thì bắt. Trả "unknown" cho đồng nhất với nhánh catch bên dưới.
            return packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            Log.e("Error", "Error: $e")
            return "unknown"
        }
    }

    private fun getFirstInstallDay(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val firstInstallTime = packageInfo.firstInstallTime
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
            (sdf.format(Date(firstInstallTime))).toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            0
        }
    }

    private fun getDaysSinceFirstOpen(context: Context): Int {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val firstInstallTime =
                packageInfo.firstInstallTime
            val currentTime = System.currentTimeMillis()
            return ((currentTime - firstInstallTime) / (24 * 60 * 60 * 1000)).toInt()
        } catch (e: Exception) {
            Log.e("Error", "Error: $e")
            return 0
        }
    }

    private fun getCurrentDate(): Int {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
        return sdf.format(Date()).toInt()
    }

    private fun getCurrentTime(): Int {
        val sdf = SimpleDateFormat("HH", Locale.ROOT)
        return (sdf.format(Date())).toInt()
    }

    private fun getCurrentWeekday(): String {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        return when (day) {
            1 -> "1 - Sunday"
            2 -> "2 - Monday"
            3 -> "3 - Tuesday"
            4 -> "4 - Wednesday"
            5 -> "5 - Thursday"
            6 -> "6 - Friday"
            7 -> "7 - Saturday"
            else -> "unknown"
        }
    }


    private fun getCountryCode(context: Context): String {
        try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val simCountry = telephonyManager.simCountryIso
            if (!simCountry.isNullOrEmpty()) {
                return simCountry.uppercase(Locale.ROOT)
            }

            val networkCountry = telephonyManager.networkCountryIso
            if (!networkCountry.isNullOrEmpty()) {
                return networkCountry.uppercase(Locale.ROOT)
            }
        } catch (e: Exception) {
            Log.e("Error", "Error: $e")
        }
        return "DEFAULT"
    }

    /** Country vừa biết: ghi rồi áp lại `ads_policy_android` có `by_tier` đã khôi phục trước đó (1.9.0 Task 4, R7). */
    internal fun onCountryKnown(code: String) {
        country = code
        AdsPolicyTiers.onCountryKnown()
    }

    /** Quốc gia của thiết bị — cùng nguồn với cột `country` trong mọi event. */
    fun currentCountry(): String = country

    /** `package_name` của envelope v2 (`EventEnvelope`): `applicationContext.packageName` lúc [init], rỗng trước đó. */
    internal fun currentPackageName(): String = packageName

    /** Định danh người dùng — cùng nguồn với cột `user_id` trong mọi event ([userIdSource]). */
    fun currentUserId(): String = userIdSource()

    fun generateGlobalPropertiesData(): UserProperty {
        return UserProperty(
            userId = userIdSource(),
            appName = appName,
            appVersion = appVersion,
            language = language,
            country = country,
            deviceInfo = deviceInfo,
            eventDate = getCurrentDate(),
            sessionNumber = sessionNumber,
            sessionId = sessionId,
            installDay = installDay,
            retentionDay = retentionDay,
            localHour = getCurrentTime(),
            localWeekday = getCurrentWeekday(),
            platform = platform,
            abTest = PreferenceHelper.getInstance().get(Constants.EXPERIMENT_GROUP, 4)
        )
    }

}