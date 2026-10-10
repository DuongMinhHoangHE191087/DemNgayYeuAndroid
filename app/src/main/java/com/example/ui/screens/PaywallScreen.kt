@file:Suppress("FunctionName")
package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ads.AdsManager
import com.example.billing.BillingManager
import com.example.billing.PurchaseEvent
import com.example.billing.findBestOffer
import com.example.billing.toPlanOffer

// ─── Nội dung chọn gói ───────────────────────────────────────────────────────
private enum class PaywallPlan { YEARLY, MONTHLY, LIFETIME }

/**
 * Màn hình Paywall lãng mạn — Romantic Rose Light Mode sang trọng, Material 3.
 *
 * Đặc điểm UI:
 *  - Nền gradient Soft Rose & Cream lãng mạn, thanh lịch, đồng bộ 100% Light Mode.
 *  - 3 gói (Năm, Tháng, Trọn đời); giá/dùng thử/gia hạn lấy động từ Google Play,
 *    không gắn nhãn "phổ biến"/"tiết kiệm" khi chưa có số liệu chứng minh.
 *  - Danh sách đặc quyền VIP với icon tích xanh lá rõ ràng.
 *  - Nút CTA gradient Rose-Crimson lớn, góc bo tròn.
 *  - Nút nhỏ "Khôi phục gói mua" ở chân trang — bắt buộc theo Google Play Policy.
 *  - Liên kết Terms of Service & Privacy Policy hiển thị rõ ràng.
 *  - [DisposableEffect] chặn App Open Ad khi màn hình đang hiển thị.
 *
 * Tuân thủ Google Play Developer Policy:
 *  - Subscription disclosure text đầy đủ (trial, renewal, cancel terms).
 *  - Nút Restore Purchases visible và functional.
 *  - Terms & Privacy links clickable.
 *
 * @param billingManager Instance [BillingManager] từ [AppServiceLocator].
 * @param adsManager Instance [AdsManager] từ [AppServiceLocator] — để suppress AOA.
 * @param onDismiss Callback khi người dùng đóng màn hình.
 */
@Composable
fun PaywallScreen(
    billingManager: BillingManager,
    adsManager: AdsManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hostActivity: Activity? = null
) {
    val context = LocalContext.current
    // MainActivity re-wraps LocalContext with createConfigurationContext (locale), which hides the
    // Activity, so the host passes it explicitly.
    val activity = com.example.billing.resolveHostActivity(hostActivity, context)
    val uriHandler = LocalUriHandler.current

    val productDetailsList by billingManager.productDetailsList.collectAsState()
    val isVipUser by com.example.di.AppServiceLocator.entitlementRepository.isVipUser.collectAsState()

    var selectedPlan by remember { mutableStateOf(PaywallPlan.YEARLY) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkleAlpha"
    )

    // ─── Chặn App Open Ad khi Paywall đang mở ────────────────────────────────
    // Tuân thủ Policy: AOA không được hiện đè lên màn hình thanh toán.
    DisposableEffect(Unit) {
        adsManager.setAppOpenAdSuppressed(true)
        onDispose {
            adsManager.setAppOpenAdSuppressed(false)
        }
    }

    // ─── Auto-dismiss khi mua VIP thành công ─────────────────────────────────
    LaunchedEffect(isVipUser) {
        if (isVipUser) {
            statusMessage = context.getString(R.string.paywall_vip_congrats)
            kotlinx.coroutines.delay(1200.milliseconds)
            onDismiss()
        }
    }

    // ─── Lắng nghe purchase events từ BillingManager ─────────────────────────
    LaunchedEffect(Unit) {
        billingManager.purchaseEvent.collect { event ->
            when (event) {
                is PurchaseEvent.Success -> {
                    statusMessage = context.getString(R.string.paywall_restore_success)
                }
                is PurchaseEvent.Pending -> {
                    statusMessage = context.getString(R.string.paywall_pending)
                    isRestoring = false
                }
                is PurchaseEvent.Error -> {
                    statusMessage = event.message
                    isRestoring = false
                }
            }
        }
    }

    // ─── Nền gradient Romantic Rose Light Mode ──────────────────────────────
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFF7FA), // Soft cream rose
            Color(0xFFFFF0F6), // Dreamy blush
            Color(0xFFFDE8F1)  // Light pastel plum
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // ─── Vòng sáng blur lãng mạn nhẹ nhàng ───────────────────────────────
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopCenter)
                .blur(100.dp)
                .background(
                    Color(0xFFFF2D75).copy(alpha = 0.09f),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomCenter)
                .blur(90.dp)
                .background(
                    Color(0xFFE91E63).copy(alpha = 0.07f),
                    shape = CircleShape
                )
        )

        // ─── Nội dung chính ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Nút đóng X ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.paywall_close),
                        tint = Color(0xFF8A2E5B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Badge & Icon Vương Miện ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF80AB),
                                Color(0xFFFF2D75)
                            )
                        )
                    )
                    .graphicsLayer { alpha = sparkleAlpha },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Tiêu đề ───────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.paywall_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1F0416),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_sub),
                fontSize = 14.sp,
                color = Color(0xFF8A2E5B),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ── Đặc quyền VIP ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color(0xFFFFD1E7),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.paywall_benefits_header),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                VipBenefitItem(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.paywall_benefit_ads)
                )
                VipBenefitItem(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.paywall_benefit_support)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Prices, trial and renewal text all come from the eligible Google Play offer (never hard-coded)
            val yearlyOffer = productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_YEARLY }?.toPlanOffer()
            val monthlyOffer = productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_MONTHLY }?.toPlanOffer()
            val lifetimeOffer = productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_LIFETIME }?.toPlanOffer()
            val loadingPrice = stringResource(R.string.paywall_price_loading)
            val dynamicYearlyPrice = yearlyOffer?.price ?: loadingPrice
            val dynamicMonthlyPrice = monthlyOffer?.price ?: loadingPrice
            val dynamicLifetimePrice = lifetimeOffer?.price ?: loadingPrice

            val selectedProductId = when (selectedPlan) {
                PaywallPlan.YEARLY -> BillingManager.PRODUCT_VIP_YEARLY
                PaywallPlan.MONTHLY -> BillingManager.PRODUCT_VIP_MONTHLY
                PaywallPlan.LIFETIME -> BillingManager.PRODUCT_VIP_LIFETIME
            }
            val selectedDetails = productDetailsList.find { it.productId == selectedProductId }
            val selectedOffer = when (selectedPlan) {
                PaywallPlan.YEARLY -> yearlyOffer
                PaywallPlan.MONTHLY -> monthlyOffer
                PaywallPlan.LIFETIME -> lifetimeOffer
            }
            val canPurchase = activity != null && selectedDetails != null && selectedOffer != null

            // ── Các gói đăng ký ───────────────────────────────────────────────

            // Gói NĂM — không gắn nhãn so sánh (chưa có dữ liệu chứng minh "phổ biến"/"tiết kiệm")
            PaywallPlanCard(
                title = stringResource(R.string.paywall_plan_yearly_title),
                price = dynamicYearlyPrice,
                subText = stringResource(R.string.paywall_plan_yearly_sub),
                badge = null,
                badgeColor = Color.Transparent,
                isSelected = selectedPlan == PaywallPlan.YEARLY,
                onClick = { selectedPlan = PaywallPlan.YEARLY }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Gói THÁNG
            PaywallPlanCard(
                title = stringResource(R.string.paywall_plan_monthly_title),
                price = dynamicMonthlyPrice,
                subText = stringResource(R.string.paywall_plan_monthly_sub),
                badge = null,
                badgeColor = Color.Transparent,
                isSelected = selectedPlan == PaywallPlan.MONTHLY,
                onClick = { selectedPlan = PaywallPlan.MONTHLY }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Gói TRỌN ĐỜI (LIFETIME)
            PaywallPlanCard(
                title = stringResource(R.string.paywall_plan_lifetime_title),
                price = dynamicLifetimePrice,
                subText = stringResource(R.string.paywall_plan_lifetime_sub),
                badge = stringResource(R.string.paywall_plan_lifetime_badge),
                badgeColor = Color(0xFFFF9800),
                isSelected = selectedPlan == PaywallPlan.LIFETIME,
                onClick = { selectedPlan = PaywallPlan.LIFETIME }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Thông báo trạng thái ──────────────────────────────────────────
            AnimatedVisibility(
                visible = statusMessage != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut()
            ) {
                statusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.contains("🎉")) Color(0xFFE8F5E9) else Color(0xFFFCE4EC),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (msg.contains("🎉")) Color(0xFF81C784) else Color(0xFFF48FB1)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (msg.contains("🎉")) Color(0xFF1B5E20) else Color(0xFFAD1457),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ── Disclosure right above the CTA: price, trial length, renewal and how to cancel
            val disclosure = when {
                selectedOffer == null -> stringResource(R.string.paywall_disclosure_unavailable)
                selectedPlan == PaywallPlan.LIFETIME -> stringResource(R.string.paywall_disclosure_lifetime, selectedOffer.price)
                else -> {
                    val period = stringResource(
                        if (selectedPlan == PaywallPlan.YEARLY) R.string.paywall_period_year else R.string.paywall_period_month
                    )
                    if (selectedOffer.hasTrial) {
                        val length = selectedOffer.trialDays?.let { context.resources.getQuantityString(R.plurals.paywall_trial_days, it, it) }
                            ?: stringResource(R.string.paywall_trial_unknown_length)
                        stringResource(R.string.paywall_disclosure_trial, length, selectedOffer.price, period)
                    } else {
                        stringResource(R.string.paywall_disclosure_paid, selectedOffer.price, period)
                    }
                }
            }
            Text(
                text = disclosure,
                fontSize = 12.sp,
                color = Color(0xFF6B3A52),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("paywall_disclosure")
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Nút CTA chính ─────────────────────────────────────────────────
            Button(
                onClick = {
                    val host = activity ?: return@Button
                    val details = selectedDetails ?: return@Button
                    statusMessage = null
                    // Same offer the disclosure was derived from
                    val offerToken = details.findBestOffer(preferFreeTrial = true)?.offerToken
                    // Subscriptions need an offer token; only the lifetime (INAPP) product has none
                    if (offerToken == null && selectedPlan != PaywallPlan.LIFETIME) return@Button
                    billingManager.launchPurchaseFlow(host, details, offerToken ?: "")
                },
                enabled = canPurchase,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("paywall_cta"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (canPurchase) 1f else 0.5f }
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF2D75), Color(0xFFE91E63))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            selectedOffer == null -> stringResource(R.string.paywall_loading_prices)
                            selectedPlan == PaywallPlan.LIFETIME -> stringResource(R.string.paywall_btn_lifetime)
                            selectedOffer.hasTrial -> stringResource(R.string.paywall_btn_trial)
                            selectedPlan == PaywallPlan.YEARLY -> stringResource(R.string.paywall_btn_yearly)
                            else -> stringResource(R.string.paywall_btn_monthly)
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Nút Khôi phục gói mua — BẮT BUỘC theo Google Play Policy ────
            TextButton(
                onClick = {
                    if (!isRestoring) {
                        isRestoring = true
                        statusMessage = context.getString(R.string.paywall_restoring)
                        billingManager.queryExistingPurchases { hasVip ->
                            isRestoring = false
                            statusMessage = if (hasVip) {
                                context.getString(R.string.paywall_restore_success)
                            } else {
                                context.getString(R.string.paywall_restore_empty)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFE91E63),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.paywall_btn_restore),
                    color = Color(0xFFE91E63),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            // Nút Quản Lý & Hủy Gói Thuê Bao Trực Tiếp trên Google Play (Google Play Policy Bắt Buộc)
            TextButton(
                onClick = {
                    uriHandler.openUri(com.example.config.LegalLinks.MANAGE_SUBSCRIPTIONS)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.paywall_btn_manage),
                    tint = Color(0xFF8A2E5B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.paywall_btn_manage),
                    color = Color(0xFF8A2E5B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFFFD1E7))
            Spacer(modifier = Modifier.height(12.dp))

            // ── Subscription disclosure text — Google Play Store yêu cầu ────
            Text(
                text = stringResource(R.string.paywall_cancel_info),
                fontSize = 11.sp,
                color = Color(0xFF8C6B7B),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Links pháp lý — clickable ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.paywall_terms),
                    color = Color(0xFF8A2E5B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        uriHandler.openUri(com.example.config.LegalLinks.termsOfService)
                    }
                )
                Text(text = "  •  ", color = Color(0xFFC498AE), fontSize = 11.sp)
                Text(
                    text = stringResource(R.string.paywall_privacy),
                    color = Color(0xFF8A2E5B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        uriHandler.openUri(com.example.config.LegalLinks.privacyPolicy)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

/** Một dòng đặc quyền VIP với icon và text chuẩn Light Mode */
@Composable
private fun VipBenefitItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00C853), // Xanh lá tươi nổi bật trên nền trắng
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color(0xFF1F0416),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 19.sp
        )
    }
}

/** Card hiển thị một gói đăng ký — highlight khi được chọn chuẩn Light Mode */
@Composable
private fun PaywallPlanCard(
    title: String,
    price: String,
    subText: String,
    badge: String?,
    badgeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(listOf(Color(0xFFFF2D75), Color(0xFFE91E63)))
    } else {
        Brush.horizontalGradient(
            listOf(Color(0xFFFFD1E7), Color(0xFFFFD1E7))
        )
    }
    val cardBackground = if (isSelected) {
        Color(0xFFFFF2F7) // Soft blush
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBackground)
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier.border(
                    width = 1.dp,
                    color = Color(0xFFFFD1E7),
                    shape = RoundedCornerShape(16.dp)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F0416),
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor,
                        modifier = Modifier
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = price,
                color = Color(0xFFE91E63),
                fontSize = 18.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subText,
                color = if (isSelected) Color(0xFF8A2E5B) else Color(0xFF6B4358),
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}
