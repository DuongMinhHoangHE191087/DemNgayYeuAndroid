@file:Suppress("FunctionName")
package com.example.ui.components

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.plugin.ads.AdsHelper
import com.example.di.AppServiceLocator
import com.example.ads.isAdsEligible
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Hiển thị Google AdMob Adaptive Banner trong Jetpack Compose — Production-ready.
 *
 * Đặc điểm kỹ thuật:
 *  - [isVip] = true → không render bất kỳ View nào, giải phóng hoàn toàn layout space.
 *  - Dùng [AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize] — kích thước
 *    tự động theo chiều rộng màn hình thực tế, chuẩn Google Adaptive Banner.
 *  - [DisposableEffect] đảm bảo [AdView.destroy] được gọi khi Composable rời khỏi cây
 *    UI → không bao giờ rò rỉ bộ nhớ (Window Leak).
 *  - Padding `vertical = 12.dp` ngăn Click Nhầm (Accidental Click) giữa banner và
 *    các nút điều hướng — tuân thủ Google Play Policy.
 *
 * @param isVip Nếu true, không render component này — người dùng VIP không thấy quảng cáo.
 * @param adUnitId Ad Unit ID từ AdMob Console. Mặc định là Google Test Banner ID.
 * @param modifier Modifier tùy chỉnh từ caller.
 * @param canRequestAds Đã có sự đồng ý UMP chưa — mặc định đọc từ AdsManager (test truyền thẳng).
 * @param sdkDisabled Cờ kill switch của SDK, đọc lại mỗi lần recompose — true thì ẩn banner.
 * @param createBanner Tạo View banner; mặc định là AdView thật (test truyền View giả).
 */
@Composable
fun ComposeBannerAd(
    isVip: Boolean,
    adUnitId: String = com.example.BuildConfig.ADMOB_BANNER_ID,
    modifier: Modifier = Modifier,
    canRequestAds: Boolean = AppServiceLocator.adsManager.canRequestAds.collectAsState().value,
    // Fail closed: không đọc được kill switch (SDK chưa init) → ẩn banner, không crash
    sdkDisabled: () -> Boolean = { runCatching { AdsHelper.instance.isDisableAds(0) }.getOrDefault(true) },
    createBanner: (Context, String) -> View = { ctx, unitId ->
        AdView(ctx).apply {
            // Adaptive Banner theo chiều rộng màn hình tính bằng DP
            @Suppress("DEPRECATION")
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    ctx,
                    ctx.resources.configuration.screenWidthDp
                )
            )
            this.adUnitId = unitId
            loadAd(AdRequest.Builder().build())
        }
    }
) {
    // Guard: VIP / chưa có sự đồng ý UMP / adUnitId rỗng / SDK kill switch → return sớm, không tốn layout
    if (!isAdsEligible(isVip, canRequestAds, adUnitId, killed = sdkDisabled())) return

    // Bao trong Box với padding vertical để ngăn Accidental Clicks theo Policy Google
    Box(
        modifier = modifier
            .testTag("compose_banner")
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx -> createBanner(ctx, adUnitId) },
            onRelease = { view -> (view as? AdView)?.destroy() }
        )
    }
}

/**
 * Alias ngược tương thích với code cũ dùng [AdBannerPlaceholder].
 * Các màn hình đang gọi [AdBannerPlaceholder] không cần đổi tên — delegate về [ComposeBannerAd].
 *
 * @param isVip Nếu true, ẩn hoàn toàn banner.
 * @param onClick Không dùng nữa (banner tự xử lý click) — giữ signature để tránh breaking change.
 * @param onUpgradeClick Callback mở dialog nâng cấp VIP.
 * @param modifier Modifier tùy chỉnh.
 */
@Composable
fun AdBannerPlaceholder(
    modifier: Modifier = Modifier,
    isVip: Boolean = false,
    onClick: (() -> Unit)? = null,
    onUpgradeClick: (() -> Unit)? = null
) {
    // Delegate sang ComposeBannerAd thực — tham số onClick/onUpgradeClick không còn dùng
    // nhưng giữ lại để không phải đổi tất cả call site trong MainActivity
    ComposeBannerAd(
        isVip = isVip,
        modifier = modifier
    )
}
