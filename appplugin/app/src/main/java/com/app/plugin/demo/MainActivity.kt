package com.app.plugin.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.app.plugin.ads.AdsGiftShowCb
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.app.AppPluginManager
import com.app.plugin.consent.ConsentManager
import com.app.plugin.consent.ConsentState

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var bannerContainer: FrameLayout
    private lateinit var rectContainer: FrameLayout
    private lateinit var nativeContainer: FrameLayout
    private lateinit var ntFullContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 64, 32, 32)
        }
        scrollView.addView(root)

        statusView = TextView(this).apply {
            text = "Khởi tạo..."
            textSize = 16f
        }
        root.addView(statusView)

        // Mở lại form UMP (EEA hoặc US-state) để đổi lựa chọn giữa phiên: QA lượt ba của Task 8 (1.6.0),
        // opt-out GPP → listener consent đánh giá lại MAX/Meta/Firebase mà tầng không đổi. Cũng đóng dòng G4
        // "Mục Privacy options: demo chưa".
        root.addView(TextView(this).apply { text = "\n--- CONSENT ---" })
        root.addView(Button(this).apply {
            text = "Privacy options"
            setOnClickListener { openPrivacyOptions() }
        })

        // Sân thử brain: xem segment và kịch bản personalization đang chạy.
        root.addView(TextView(this).apply { text = "\n--- MONETIZATION BRAIN ---" })
        root.addView(Button(this).apply {
            text = "Mở Brain Playground (segment + personalization)"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, BrainPlaygroundActivity::class.java))
            }
        })

        // Banner & Native Collapse section
        root.addView(TextView(this).apply { text = "\n--- BANNER & NTCL ---" })
        bannerContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200)
        }
        root.addView(bannerContainer)
        root.addView(Button(this).apply {
            text = "Show Banner Only"
            setOnClickListener { showBanner(false) }
        })
        root.addView(Button(this).apply {
            text = "Show Banner + NtCl (Collapse)"
            setOnClickListener { showBanner(true) }
        })
        // Overlay NtCl mở rộng che các nút RECT bên dưới → agent E3 bấm trúng creative trên API 28
        // (invalid traffic, rủi ro chính sách/eCPM). Bấm nút này (sau ảnh) trước mọi cú bấm rect (T9 E3).
        root.addView(Button(this).apply {
            text = "Hide NtCl (trước khi bấm Rect)"
            setOnClickListener {
                AdsHelper.instance.hideNativeCl()
                statusView.text = "NtCl hidden"
            }
        })
        addRwiBannerSections(root, bannerContainer, statusView) // 1.9.0 Task 10: rwi, banner collapsible, NtCl x3 (DemoRwiBanner.kt)

        // Rect (MREC) section: harness cho vùng RectController (R8 giữ đường showRect/loadRect4Show, kịch bản máy
        // rect hiện → rời → quay lại đếm `load_fail_but_ads_not_null`).
        root.addView(TextView(this).apply { text = "\n--- RECT ---" })
        rectContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700)
        }
        root.addView(rectContainer)
        root.addView(Button(this).apply {
            text = "Load Rect"
            setOnClickListener { AdsHelper.instance.loadRect4Show(PlacementDefault.PLRectDefault, DemoLoadCb("rect load")) }
        })
        root.addView(Button(this).apply {
            text = "Show Rect"
            setOnClickListener { AdsHelper.instance.showRect(PlacementDefault.PLRectDefault, rectContainer, DemoLoadCb("rect")) }
        })
        root.addView(Button(this).apply {
            text = "Hide Rect"
            setOnClickListener { AdsHelper.instance.hideRect(rectContainer, true) }
        })

        // Interstitial section
        root.addView(TextView(this).apply { text = "\n--- INTERSTITIAL ---" })
        root.addView(Button(this).apply {
            text = "Show Interstitial"
            setOnClickListener { clickShowInterstitial() }
        })
        // 2.0.0 Task 0c (D22): đường sản phẩm — isDeltaTime mặc định (true) nên AdsBrain.shouldShowInterstitial được hỏi; `--drc` chạm nút này.
        root.addView(Button(this).apply {
            text = "Show Interstitial (brain)"
            setOnClickListener {
                AdsHelper.instance.currConfig.full2FlagShow = 0
                val shown = AdsHelper.instance.showInterstitial(PlacementDefault.PLFullDefault, 0, DemoShowCb("inter-brain"))
                reportShowReturned("inter-brain", shown)
            }
        })
        root.addView(Button(this).apply {
            text = "Interstitial → Full2"
            setOnClickListener { showInterstitial(chainFull2 = true) }
        })

        // Rewarded section
        root.addView(TextView(this).apply { text = "\n--- REWARDED ---" })
        root.addView(Button(this).apply {
            text = "Show Rewarded"
            setOnClickListener { clickShowRewarded() }
        })
        root.addView(Button(this).apply {
            text = "Rewarded → Full2"
            setOnClickListener { showRewarded(chainFull2 = true) }
        })

        // Native section
        root.addView(TextView(this).apply { text = "\n--- NATIVE ---" })
        nativeContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
        }
        root.addView(nativeContainer)
        root.addView(Button(this).apply {
            text = "Show Native"
            setOnClickListener { showNative() }
        })

        // Native Full section
        root.addView(TextView(this).apply { text = "\n--- NATIVE FULL (NTFULL) ---" })
        ntFullContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800)
        }
        root.addView(ntFullContainer)
        root.addView(Button(this).apply {
            text = "Show NtFull in Container"
            setOnClickListener { showNtFull() }
        })
        root.addView(Button(this).apply {
            text = "Show NtFull Full Screen (Full2)"
            setOnClickListener { showNtFullFullScreen() }
        })

        // App Open section
        root.addView(TextView(this).apply { text = "\n--- APP OPEN ---" })
        root.addView(Button(this).apply {
            text = "Show App Open"
            setOnClickListener { showAppOpen() }
        })
        // 2.4.0 DEMO-RES (OWNER 12): bật app open khi quay lại app (mặc định OFF, không lưu); `device_gate.sh --resume` chạm nút này
        root.addView(Button(this).apply {
            text = "App open resume: OFF"
            setOnClickListener {
                val on = !AdsHelper.instance.getFlag4ShowResume()
                AdsHelper.instance.setFlag4ShowResume(on)
                text = "App open resume: ${if (on) "ON" else "OFF"}"
            }
        })

        setContentView(scrollView)

        initAppPlugin()
        runDrive(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) = super.onNewIntent(intent).also { runDrive(intent) }

    // 2.1.0 Task 0c (§E): `--es drive <action>` gọi ĐÚNG hàm click của nút (cùng clickShow* mà setOnClickListener gọi); không extra = không làm gì.
    private fun runDrive(src: android.content.Intent?) {
        val action = src?.getStringExtra("drive")?.takeIf { com.app.plugin.until.AppUtil.isTestFlagOn(this) } ?: return // bản không flagTest bỏ qua `drive` (R-T0c)
        Log.i("AppPluginDemo", "drive=intent action=$action")
        if (action == "show_interstitial") clickShowInterstitial() else if (action == "show_rewarded") clickShowRewarded() else if (action == "show_rwi") clickShowRwi(statusView) else Drive242.run(this, action)
    }
    private fun clickShowInterstitial() = showInterstitial(chainFull2 = false)
    private fun clickShowRewarded() = showRewarded(chainFull2 = false)
    private fun initAppPlugin() {
        try {
            // initPlugin() đã được gọi trong DemoApplication (AppPluginBase.onCreate),
            // không gọi lại ở đây để tránh khởi tạo trùng (double-init AdsHelper/Firebase/Appsflyer)
            AppPluginManager.instance.setMainActivity(this)
            statusView.text = "Đang lấy consent..."

            // Mạng quảng cáo chỉ khởi tạo khi UMP cho phép (từ 1.3.0). Gọi startSdkInit lúc nào
            // cũng được: người dùng đã trả lời ở phiên trước thì khởi tạo ngay, chưa thì chờ form.
            AdsMobMy.instance.startSdkInit(applicationContext)
            // QA form EEA trên máy test: adb shell am start -n test.sdk.zd/com.app.plugin.demo.MainActivity --ez force_eea true
            ConsentManager.debugForceEea = intent.getBooleanExtra("force_eea", false)
            // QA form US-state trên máy test (Task 8-0, 1.6.0): adb shell am start -n test.sdk.zd/com.app.plugin.demo.MainActivity --ez force_us true
            // Property sống theo extra (review Task 8a m7): mở lại trong cùng process không có force_us thì bỏ US.
            if (intent.getBooleanExtra("force_us", false)) {
                System.setProperty("appplugin.debug_geography", "us")
            } else {
                System.clearProperty("appplugin.debug_geography")
            }
            ConsentManager.gatherConsent(this) {
                statusView.text = "Consent xong (${ConsentState.tier()}). Sẵn sàng test!"
            }

            // Cấu hình mặc định để test các định dạng kết hợp (full2 = ntfull)
            AdsHelper.instance.currConfig.full2FlagShow = 3 // 3 = Hiện Full2 sau cả Inter và Reward

            // App Open phải preload trước khi show, không show được ngay lần đầu
            AdsHelper.instance.loadOpenAdForThisTurn(PlacementDefault.PLFullSplash)
            preloadRwi()

        } catch (e: Exception) {
            Log.e("AppPluginDemo", "initPlugin failed", e)
            statusView.text = "initPlugin lỗi: ${e.message}"
        }
    }

    private fun openPrivacyOptions() {
        val required = ConsentManager.isPrivacyOptionsRequired(this)
        Log.i("AppPluginDemo", "privacy options: required=$required")
        if (!required) {
            // UMP không có form tuỳ chọn cho vùng này (NOT_REQUIRED): showPrivacyOptionsForm sẽ trả lỗi.
            Toast.makeText(this, "Privacy options: UMP báo không cần cho vùng này", Toast.LENGTH_SHORT).show()
        }
        ConsentManager.showPrivacyOptions(this) { tier ->
            Log.i("AppPluginDemo", "privacy options closed: tier=$tier")
            statusView.text = "Privacy options xong ($tier)."
        }
    }

    private fun showBanner(isCollapse: Boolean) {
        AdsHelper.instance.showBanner(
            PlacementDefault.PLBnDefault,
            bannerContainer,
            isCollapse = isCollapse,
            cbLoad = null,
            cbShow = null
        )
    }

    /**
     * Ghi mọi callback ra logcat (tag AppPluginDemo) và lên dòng trạng thái, để QA đọc được cả chuỗi
     * cha → full2 và đếm số lần trao thưởng: `adb logcat -s AppPluginDemo`.
     */
    private inner class DemoShowCb(private val label: String) : AdsGiftShowCb {
        private fun report(event: String) {
            Log.i("AppPluginDemo", "[$label] $event")
            statusView.text = "[$label] $event"
        }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) =
            report("showed pl=$placement")
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) =
            report("impression pl=$placement")
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) =
            report("click pl=$placement")
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) =
            report("dismissed pl=$placement")
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
            report("show fail pl=$placement: $err")
            Toast.makeText(this@MainActivity, "[$label] show fail: $err", Toast.LENGTH_SHORT).show()
        }
        override fun onAdRewarded(placement: String?, originPl: String?, adId: String?) {
            report("REWARDED pl=$placement")
            Toast.makeText(this@MainActivity, "Đã nhận thưởng!", Toast.LENGTH_SHORT).show()
        }
    }

    /** Callback load (rect): ghi ra logcat tag AppPluginDemo và dòng trạng thái, như [DemoShowCb]. */
    private inner class DemoLoadCb(private val label: String) : AdsLoadCb {
        private fun report(event: String) {
            Log.i("AppPluginDemo", "[$label] $event")
            statusView.text = "[$label] $event"
        }
        override fun onLoaded(placement: String, originPl: String, adId: String) =
            report("loaded pl=$originPl")
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
            report("load fail pl=$originPl: $error")
    }

    /**
     * full2FlagShow đặt ngay trước lúc show: config từ Remote Config về sau khi mở app sẽ ghi đè
     * giá trị đặt lúc khởi tạo. 1 = full2 sau interstitial, 2 = sau rewarded, 0 = tắt.
     */
    private fun showInterstitial(chainFull2: Boolean) {
        AdsHelper.instance.currConfig.full2FlagShow = if (chainFull2) 1 else 0
        val label = if (chainFull2) "inter+full2" else "inter"
        val shown = AdsHelper.instance.showInterstitial(
            PlacementDefault.PLFullDefault,
            0,
            DemoShowCb(label),
            isDeltaTime = false
        )
        reportShowReturned(label, shown)
    }

    private fun showRewarded(chainFull2: Boolean) {
        AdsHelper.instance.currConfig.full2FlagShow = if (chainFull2) 2 else 0
        val label = if (chainFull2) "reward+full2" else "reward"
        reportShowReturned(label, AdsHelper.instance.showRewarded(PlacementDefault.PLGiftDefault, DemoShowCb(label)))
    }

    /**
     * `false` là kết thúc của request (hợp đồng 1.3.1); một số đường lỗi mạng đồng bộ gọi
     * `onAdShowFail` rồi trả `false`. Ghi ra để một cú bấm lúc chưa load không trông như "mất callback".
     */
    private fun reportShowReturned(label: String, shown: Boolean) {
        if (shown) return
        Log.i("AppPluginDemo", "[$label] show returned false: không có quảng cáo để hiện (kèm onAdShowFail nếu lỗi mạng đồng bộ)")
        statusView.text = "[$label] show returned false"
        Toast.makeText(this, "[$label] chưa có quảng cáo", Toast.LENGTH_SHORT).show()
    }

    private fun showNative() {
        AdsHelper.instance.showNative(
            PlacementDefault.PLNtDefault,
            nativeContainer,
            object : AdsShowCb {
                override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
                    Toast.makeText(this@MainActivity, "Native show fail: $err", Toast.LENGTH_SHORT).show()
                }
            },
            isLoadNShow = true
        )
    }

    private fun showNtFull() {
        // Standalone ntfull test using getNativeFullForShow which returns a View
        val ntView = AdsHelper.instance.getNativeFullForShow(
            PlacementDefault.PLFullDefault,
            object : AdsShowCb {
                override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
                    Toast.makeText(this@MainActivity, "NtFull fail: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
        if (ntView != null) {
            ntFullContainer.removeAllViews()
            ntFullContainer.addView(ntView)
        } else {
            // If not loaded, try to load it first
            AdsHelper.instance.loadNativeFullForThisTurn(PlacementDefault.PLFullDefault)
            Toast.makeText(this, "NtFull đang load, thử lại sau giây lát", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNtFullFullScreen() {
        // Test chế độ Dialog toàn màn hình của NtFull (giống Interstitial, có nút tắt đếm ngược)
        AdsHelper.instance.showFull2(
            PlacementDefault.PLFull2Default,
            object : AdsShowCb {
                override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
                    Toast.makeText(this@MainActivity, "NtFull Full Screen fail: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showAppOpen() {
        val shown = AdsHelper.instance.showOpenAd(
            PlacementDefault.PLFullSplash,
            object : AdsShowCb {
                override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) {
                    Toast.makeText(this@MainActivity, "App Open show fail: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
        reportShowReturned("app open", shown)
    }

    override fun onDestroy() {
        AppPluginManager.instance.clearMainActivity()
        AdsHelper.instance.clearActivity()
        super.onDestroy()
    }
}
