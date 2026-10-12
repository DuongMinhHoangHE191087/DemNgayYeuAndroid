package com.app.plugin.demo

import android.os.StrictMode
import android.util.Log
import com.app.plugin.MonetizationSdk
import com.app.plugin.app.AppPluginBase
import com.app.plugin.until.AppUtil

/**
 * Mẫu tích hợp tối thiểu — đây chính là toàn bộ những gì một studio phải viết.
 *
 * `AppPluginBase` lo vòng đời: khởi tạo lưu trữ, pipeline log, hàng đợi decision log, phiên
 * của brain, và fetch config. `MonetizationSdk.configure()` lo phần tuỳ chọn, theo đúng thứ
 * tự an toàn mà studio không cần biết.
 */
class DemoApplication : AppPluginBase() {

    override fun onCreate() {
        // Cổng G2 (DOC/65): đọc/ghi đĩa trên main thread của SDK phải = 0. Chỉ bật khi flagTest.
        // Dựng từ policy hiện tại để giữ mặc định của nền tảng (NetworkOnMainThread vẫn crash
        // như app thật); chỉ thêm phát hiện đọc/ghi đĩa, phạt bằng log.
        if (AppUtil.isTestFlagOn(this)) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy())
                    .detectDiskReads().detectDiskWrites()
                    .penaltyLog().build()
            )
            // Canary (1.6.2 Task 2): cổng device_gate cần thấy dòng này để "by sdk=0" là bằng chứng G2.
            Log.i("AppPluginDemo", "strictmode armed: detectDiskReads detectDiskWrites penaltyLog")
        }
        super.onCreate()

        val report = MonetizationSdk.configure(this) {
            // Bật brain cho app demo để Brain Playground có gì mà xem. App thật nên để mặc
            // định TẮT rồi bật bằng một quyết định kinh doanh có ý thức.
            brainEnabled = true
            // Mở dần là quy trình bắt buộc (doc 38 mục F), không phải giá trị mặc định —
            // ở đây để 1.0 vì app demo chỉ có một thiết bị.
            brainRolloutFraction = 1.0

            // Id sản phẩm minh hoạ: chưa khai báo trên Play Console nên billing sẽ báo không
            // lấy được giá. Đó là hành vi đúng — SDK không giả vờ có sản phẩm.
            inappProducts = listOf("demo_coin_pack")
            subsProducts = listOf("demo_premium_monthly")
            consumableProducts = setOf("demo_coin_pack")
            removeAdsProducts = setOf("demo_premium_monthly")
        }

        // In báo cáo tự kiểm tra. Đây là thứ đầu tiên nên nhìn khi mới thả SDK vào một dự án:
        // nó bắt được những cấu hình "im lặng không làm gì" và id gõ nhầm.
        Log.i("AppPluginDemo", "\n" + report.describe())
    }
}
