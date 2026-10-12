package com.app.plugin.until

internal object Constants {
    public const val EXPERIMENT_GROUP: String = "EXPERIMENT_GROUP"

    public object App {
        public const val NAME: String = "android_xcore"
        public const val PACKAGE: String = "com.sdk.android_xcore"
        public const val PREFERENCE_NAME: String = "shimeji_wallpaper_call"
    }

    public object DataBucket {
        // Rỗng = không gửi log sự kiện lên DataBucket, như cả hai app fork đang chạy. App nào cần
        // thì bật bằng meta-data DATABUCKETS_BASE_URL / DATABUCKETS_API_KEY (AppPluginBase).
        public const val BASE_URL: String = ""
        public const val API_KEY: String = ""
    }

    /** Tên event: `com.app.plugin.logevent.EventNames` (hợp đồng Điều 9.2, `EventNameContractTest`). */
    public object LogEvent {
        // Không dùng (deprecated): endpoint cũ, không đọc ở đâu trong SDK; mọi event đi DATABUCKETS_BASE_URL. Không gắn
        // @Deprecated: annotation thêm `getBASE_URL$annotations` vào dump và làm cổng façade đỏ (1.7.0 Task 8, R-8).
        public const val BASE_URL: String = "https://logapi.backendxgame.xyz/"
        public const val SEQUENCE_ID: String = "SEQUENCE_ID"
    }

    public object AdShowFailReason {
        public const val OPEN_FLAG_RESUME: String = "open_flag_resume"
        public const val AD_FULL_SHOWING: String = "ad_full_showing"
        public const val POST_AD_SCREEN_COOLDOWN: String = "post_ad_screen_cooldown"
        public const val SHOW_AFTER_CLICK_AD: String = "show_after_click"
        public const val REMOVE_AD: String = "remove_ad"
        public const val NOT_ALLOW_SHOW: String = "not_allow_show"
        public const val NOT_CONFIG: String = "not_config"
        public const val AD_SHOWING: String = "ad_showing"
        public const val CONFIG_NOT: String = "cf_not"
        public const val AD_SHOW_NULL: String = "ad_show_null"
        public const val NOT_PLM_CF: String = "not_plm_cf"
        public const val SHOW_NOT_LOADED: String = "show_not_loaded"
        public const val SHOW_NOT_LOADED_ON_SHOW: String = "show_not_loaded_on_show"
        public const val SHOW_WHEN_ACT_NULL: String = "show_when_act_null"
        public const val FAIL_TO_SHOW_FULL_CONTENT: String = "fail_to_show_full_content"
        public const val FAIL_TO_SHOW_FULL_CONTENT_OPEN: String = "open_fail_to_show_full_content"
        public const val LIST_SHOW_EMPTY: String = "list_show_empty"
        public const val VIEW_NULL: String = "view_null"
        public const val AD_OBJECT_NULL: String = "ad_object_null"
        public const val VIEW_ATTACH_NULL: String = "view_attach_null"
        public const val VIEW_ATTACH_NULL_IN_CALL_BACK: String = "view_attach_null_in_callback"
        public const val NOT_CONTAIN_PLACEMENT: String = "not_contain_placement"
        public const val LOADED_BUT_ADS_NULL: String = "loaded_but_ads_null"
        public const val LOAD_FAIL_BUT_ADS_NOT_NULL: String = "load_fail_but_ads_not_null"

        // App open đã load quá tuổi tối đa (mặc định 4 giờ, Google: hết hạn sau 4 giờ; `app_open_max_age_minutes`
        // của `ads_policy_android`) bị bỏ, không hiện; lần load kế tải mới.
        public const val APP_OPEN_EXPIRED: String = "app_open_expired"

        // Nhãn chung khi quảng cáo đã load quá tuổi tối đa mà placement không khai nhãn riêng: interstitial, rewarded
        // và native full quá `fullscreen_max_age_minutes` của `ads_policy_android` (mặc định 60 phút) bị bỏ, không
        // hiện; lần load kế tải mới. MAX interstitial và rewarded không có tuổi tối đa (MAX tự hết hạn và nạp lại).
        // App open dùng APP_OPEN_EXPIRED.
        public const val AD_EXPIRED: String = "ad_expired"

        // Luật tần suất thống nhất (AdsPolicy, doc 62 F, khoá `ads_policy_android`). Tách khỏi NOT_ALLOW_SHOW
        // để đối soát được lượt nào mất vì luật nào.
        public const val POLICY_FIRST_SESSION: String = "policy_first_session"
        public const val POLICY_MIN_GAP: String = "policy_min_gap"
        public const val POLICY_APP_OPEN_SESSION: String = "policy_app_open_session"

        // 1.9.0 Task 2: người dùng bỏ qua màn intro của rewarded interstitial (đếm ngược + "Bỏ qua"); quảng cáo giữ lại.
        // `val` internal, không `const`: không thêm hằng public vào API (getter mangle `$appplugin`, cổng façade).
        internal val RWI_INTRO_SKIPPED = "rwi_intro_skipped"

        public const val ACT_DESTROY: String = "act_destroy"

        // Full2 bị bỏ vì quảng cáo cha đóng do Activity host bị huỷ (xoay, đổi ngôn ngữ, dark mode — trong cửa sổ
        // AdsHelper.clearActivity): không nối Full2 lên màn hình tái tạo (chính sách Play "show unexpectedly", #6).
        public const val HOST_DESTROYED: String = "host_destroyed"
        public const val SHOW_FULL_WHEN_FLAG_SHOWING: String = "show_full_when_flag_showing"
        public const val SHOW_FULL_WHEN_REMOVE_ADS: String = "show_full_when_remove_ads"

        // Kill switch `sdk_kill_android` (ads, hoặc theo phiên bản) chặn lượt này. Tách khỏi nhãn
        // remove-ads: impression mất vì ops tắt quảng cáo phải phân biệt được với người dùng đã trả tiền.
        public const val SHOW_BLOCKED_KILL_SWITCH: String = "show_blocked_kill_switch"

        // Monetization Brain từ chối lượt hiện này. Tách riêng khỏi NOT_ALLOW_SHOW để phân
        // biệt được "luật tĩnh chặn" với "brain chặn" khi đối soát ARPDAU giữa nhóm
        // treatment và holdout — gộp chung thì không biết mất impression vì lý do nào.
        public const val SHOW_FULL_BRAIN_DECLINED: String = "show_full_brain_declined"
    }

    public object Common {
        public const val TIME_OPEN: String = "TIME_OPEN"
        public const val IS_FETCH_FIREBASE: String = "IS_FETCH_FIREBASE"
    }

    //in app
    public object Inject {
        public const val API: String = "API"
    }

    public object InitAds {
        public const val NT_INTRO_LOADED: String = "NT_INTRO_LOADED"
        public const val NT_LANGUAGE_LOADED: String = "NT_LANGUAGE_LOADED"
        public const val NT_LANGUAGE_2_LOADED: String = "NT_LANGUAGE_2_LOADED"
        public const val FULL_SPLASH_LOADED: String = "FULL_SPLASH_LOADED"
        public const val FULL_2_SPLASH_LOADED: String = "FULL_2_SPLASH_LOADED"
        public const val RECT_SPLASH_LOADED: String = "RECT_SPLASH_LOADED"
        public const val FULL_SPLASH_SHOWED: String = "FULL_SPLASH_SHOWED"
        public const val IS_LOAD_FULL: String = "IS_LOAD_FULL"
    }

}