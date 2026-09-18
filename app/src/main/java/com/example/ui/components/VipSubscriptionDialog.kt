@file:Suppress("FunctionName")
package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.billingclient.api.ProductDetails
import com.example.billing.BillingManager
import com.example.billing.PurchaseEvent
import com.example.data.model.SubscriptionTier
import com.example.di.AppServiceLocator
import com.example.ui.theme.Primary
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VipSubscriptionDialog(
  currentTier: SubscriptionTier,
  isVip: Boolean,
  onDismiss: () -> Unit,
  onUpgrade: (SubscriptionTier) -> Unit
) {
  var selectedTier by remember {
    mutableStateOf(if (isVip && currentTier != SubscriptionTier.FREE) currentTier else SubscriptionTier.VIP_YEARLY)
  }
  var isRestoring by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  val rawContext = LocalContext.current
  val activity = rawContext as? Activity
  val billingManager = try { AppServiceLocator.billingManager } catch (_: Exception) { null }
  val productDetailsList: List<ProductDetails> by (billingManager?.productDetailsList?.collectAsState()
    ?: remember { mutableStateOf<List<ProductDetails>>(emptyList()) })
  val uriHandler = LocalUriHandler.current

  LaunchedEffect(Unit) {
    billingManager?.purchaseEvent?.collect { event ->
      when (event) {
        is PurchaseEvent.Success -> {
          statusMessage = "🎉 Thanh toán thành công! VIP đã được kích hoạt."
          delay(1500.milliseconds)
          onDismiss()
        }
        is PurchaseEvent.Pending -> {
          statusMessage = "⏳ Giao dịch đang được xử lý. Gói VIP sẽ tự động kích hoạt khi Google Play hoàn tất."
        }
        is PurchaseEvent.Error -> {
          statusMessage = event.message
          isRestoring = false
        }
      }
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = Color.White,
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(vertical = 24.dp)
        .testTag("vip_subscription_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = CircleShape,
              color = Color(0xFFFFF3E0),
              modifier = Modifier.size(36.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.WorkspacePremium,
                  contentDescription = null,
                  tint = Color(0xFFFFA000),
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "InLove Premium VIP",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = Color(0xFF212121)
            )
          }

          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.Gray)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero Banner with Gradient
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = Color.Transparent),
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFC2185B))
              ),
              shape = RoundedCornerShape(20.dp)
            )
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = if (isVip) "💎 BẠN ĐANG LÀ THÀNH VIÊN VIP" else "✨ NÂNG TẦM TRẢI NGHIỆM TÌNH YÊU",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Tận hưởng không gian tình yêu lãng mạn thuần khiết không bị làm phiền bởi quảng cáo cùng trợ lý AI thông minh.",
              color = Color.White.copy(alpha = 0.95f),
              fontSize = 12.sp,
              textAlign = TextAlign.Center,
              lineHeight = 17.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Features list
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          VipFeatureRow(
            icon = Icons.Filled.Block,
            title = "100% Không Quảng Cáo",
            description = "Ẩn hoàn toàn banner quảng cáo trên toàn bộ các màn hình app."
          )
          VipFeatureRow(
            icon = Icons.Filled.AutoAwesome,
            title = "Trợ lý AI Gợi ý Quà Tặng & Hẹn Hò",
            description = "Gợi ý cá nhân hóa theo sở thích chung của 2 bạn và ngày kỷ niệm."
          )
          VipFeatureRow(
            icon = Icons.Filled.CloudUpload,
            title = "Lưu Trữ Đám Mây Không Giới Hạn",
            description = "Sao lưu ảnh & video kỷ niệm chất lượng gốc qua Cloudinary an toàn."
          )
          VipFeatureRow(
            icon = Icons.Filled.Favorite,
            title = "Huy Hiệu VIP Tình Yêu Vĩnh Cửu",
            description = "Hiển thị vương miện VIP lấp lánh cạnh tên của bạn và người thương."
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "Chọn gói trải nghiệm phù hợp:",
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
          color = Color(0xFF424242),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Tier selection cards
        val tiers = listOf(
          SubscriptionTier.VIP_MONTHLY,
          SubscriptionTier.VIP_YEARLY,
          SubscriptionTier.LIFETIME
        )

        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          tiers.forEach { tier ->
            val isSelected = selectedTier == tier
            val isCurrent = isVip && currentTier == tier

            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = if (isSelected) Color(0xFFFFF0F5) else Color(0xFFF5F5F5)
              ),
              border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Primary) else null,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedTier = tier }
                .testTag("tier_option_${tier.code}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = tier.titleVi,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.5.sp,
                      color = if (isSelected) Primary else Color(0xFF212121)
                    )
                    if (tier == SubscriptionTier.VIP_YEARLY) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF5252),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                      ) {
                        Text(
                          text = "HOT",
                          color = Color.White,
                          fontSize = 9.sp,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                      }
                    }
                    if (isCurrent) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "(Đang dùng)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(3.dp))
                  Text(
                    text = tier.descriptionVi,
                    fontSize = 11.5.sp,
                    color = Color.Gray,
                    lineHeight = 15.sp
                  )
                }

                Spacer(modifier = Modifier.width(10.dp))

                val formattedPrice = when (tier) {
                  SubscriptionTier.VIP_MONTHLY -> {
                    productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_MONTHLY }
                      ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                  }
                  SubscriptionTier.VIP_YEARLY -> {
                    productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_YEARLY }
                      ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                  }
                  SubscriptionTier.LIFETIME -> {
                    productDetailsList.find { it.productId == BillingManager.PRODUCT_VIP_LIFETIME }
                      ?.oneTimePurchaseOfferDetails?.formattedPrice
                  }
                  SubscriptionTier.FREE -> null
                } ?: tier.priceVi

                Text(
                  text = formattedPrice,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 13.sp,
                  color = if (isSelected) Primary else Color(0xFF424242)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Thông báo trạng thái thanh toán
        statusMessage?.let { msg ->
          androidx.compose.material3.Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (msg.contains("\uD83C\uDF89") || msg.contains("\u2705") || msg.contains("🎉")) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
          ) {
            Text(
              text = msg,
              fontSize = 12.sp,
              textAlign = TextAlign.Center,
              color = if (msg.contains("\uD83C\uDF89") || msg.contains("\u2705") || msg.contains("🎉")) Color(0xFF2E7D32) else Color(0xFFE65100),
              modifier = Modifier.padding(10.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action Button — kết nối BillingManager thực
        Button(
          onClick = {
            if (activity == null || billingManager == null) {
              statusMessage = "Không thể kết nối Google Play Billing lúc này. Vui lòng thử lại sau."
              return@Button
            }
            val productId = when (selectedTier) {
              SubscriptionTier.VIP_MONTHLY -> BillingManager.PRODUCT_VIP_MONTHLY
              SubscriptionTier.VIP_YEARLY -> BillingManager.PRODUCT_VIP_YEARLY
              SubscriptionTier.LIFETIME -> BillingManager.PRODUCT_VIP_LIFETIME
              SubscriptionTier.FREE -> return@Button
            }
            val details: ProductDetails? = productDetailsList.find { it.productId == productId }
            if (details != null) {
              val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
              billingManager.launchPurchaseFlow(activity, details, offerToken)
            } else {
              billingManager.startBillingConnection()
              statusMessage = "Đang kết nối Google Play Store, vui lòng thử lại..."
            }
          },
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Primary),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("btn_confirm_vip_upgrade")
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
          ) {
            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isVip && selectedTier == currentTier) "Đã Kích Hoạt Gói Này" else "Kích Hoạt ${selectedTier.titleVi}",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Nút Khôi phục gói mua — bắt buộc theo Google Play Policy
        androidx.compose.material3.TextButton(
          onClick = {
            if (!isRestoring && billingManager != null) {
              isRestoring = true
              statusMessage = "\u23F3 Đang khôi phục gói mua..."
              billingManager.queryExistingPurchases { hasVip ->
                isRestoring = false
                statusMessage = if (hasVip) "\u2705 Đã khôi phục thành công gói VIP!"
                               else "\u2139\uFE0F Không tìm thấy giao dịch nào với tài khoản này."
              }
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("btn_restore_purchases")
        ) {
          if (isRestoring) {
            androidx.compose.material3.CircularProgressIndicator(
              modifier = Modifier.size(14.dp), color = Primary, strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(text = "Khôi phục gói mua (Restore Purchases)", color = Color.Gray, fontSize = 12.sp)
        }

        // Quản lý / Hủy gói trên Google Play (Google Play Developer Policy)
        TextButton(
          onClick = {
            uriHandler.openUri("https://play.google.com/store/account/subscriptions?package=com.aistudio.inlove.kmrv")
          },
          modifier = Modifier.fillMaxWidth().testTag("btn_manage_subscriptions")
        ) {
          Text(
            text = "Quản lý & Hủy gói trên Google Play ↗",
            color = Color(0xFF8A2E5B),
            fontSize = 11.5.sp
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Links pháp lý
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
          Text(
            text = "Điều khoản",
            color = Color(0xFF1565C0),
            fontSize = 10.5.sp,
            modifier = Modifier.clickable { uriHandler.openUri("https://inloveapp.com/terms") }
          )
          Text(text = " \u2022 ", color = Color.LightGray, fontSize = 10.5.sp)
          Text(
            text = "Chính sách bảo mật",
            color = Color(0xFF1565C0),
            fontSize = 10.5.sp,
            modifier = Modifier.clickable { uriHandler.openUri("https://inloveapp.com/privacy") }
          )
        }
      }
    }
  }
}

@Composable
private fun VipFeatureRow(
  icon: ImageVector,
  title: String,
  description: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top
  ) {
    Surface(
      shape = CircleShape,
      color = Primary.copy(alpha = 0.12f),
      modifier = Modifier.size(28.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = Primary,
          modifier = Modifier.size(16.dp)
        )
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        color = Color(0xFF212121)
      )
      Text(
        text = description,
        fontSize = 11.5.sp,
        color = Color.Gray,
        lineHeight = 15.sp
      )
    }
  }
}
