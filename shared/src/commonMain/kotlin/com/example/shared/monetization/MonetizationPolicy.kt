package com.example.shared.monetization

import kotlinx.serialization.Serializable

@Serializable
enum class ProPlanBillingKind {
    SUBSCRIPTION_MONTHLY,
    SUBSCRIPTION_YEARLY,
    ONE_TIME_LIFETIME,
}

@Serializable
data class ProPlanSpec(
    val sku: String,
    val billingKind: ProPlanBillingKind,
    val titleVi: String,
    val titleEn: String,
    val badgeVi: String,
    val badgeEn: String,
    val freeTrialDays: Int = 0,
    val isRecommended: Boolean = false,
    val referencePriceVnd: Long = 0L,
    val referencePriceUsd: String = "",
    val savingsPercent: Int = 0,
    val taglineVi: String = "",
    val taglineEn: String = "",
)

/**
 * Single cross-platform source of truth for Pro SKUs and free starter quotas.
 * SKU constants MUST match Android `VipProductIds` and iOS `InLove.storekit`.
 */
object ProTierCatalog {
    const val MONTHLY = "vip_monthly"
    const val YEARLY = "vip_yearly"
    const val LIFETIME = "vip_lifetime"

    val ALL_SKUS: Set<String> = setOf(MONTHLY, YEARLY, LIFETIME)

    const val FREE_AI_GENERATIONS_PER_MONTH = 2
    const val FREE_SECRET_NOTES_LIMIT = 5
    const val VIP_AI_GENERATIONS_PER_MONTH = 60

    val PLANS: List<ProPlanSpec> = listOf(
        ProPlanSpec(
            sku = MONTHLY,
            billingKind = ProPlanBillingKind.SUBSCRIPTION_MONTHLY,
            titleVi = "InLove Pro Tháng",
            titleEn = "InLove Pro Monthly",
            badgeVi = "Linh hoạt",
            badgeEn = "Flexible",
            freeTrialDays = 0,
            isRecommended = false,
            referencePriceVnd = 39_000L,
            referencePriceUsd = "2.99",
            savingsPercent = 0,
            taglineVi = "Trải nghiệm linh hoạt theo tháng",
            taglineEn = "Flexible month-to-month",
        ),
        ProPlanSpec(
            sku = YEARLY,
            billingKind = ProPlanBillingKind.SUBSCRIPTION_YEARLY,
            titleVi = "InLove Pro Năm",
            titleEn = "InLove Pro Yearly",
            badgeVi = "Tiết kiệm nhất · 3 ngày miễn phí",
            badgeEn = "Best Value · 3-Day Free Trial",
            freeTrialDays = 3,
            isRecommended = true,
            referencePriceVnd = 199_000L,
            referencePriceUsd = "17.99",
            savingsPercent = 57,
            taglineVi = "Tiết kiệm 57% · Dùng thử 3 ngày miễn phí",
            taglineEn = "Save 57% · 3-day free trial",
        ),
        ProPlanSpec(
            sku = LIFETIME,
            billingKind = ProPlanBillingKind.ONE_TIME_LIFETIME,
            titleVi = "InLove Pro Trọn Đời",
            titleEn = "InLove Pro Lifetime",
            badgeVi = "Mua một lần, dùng trọn đời",
            badgeEn = "One-Time Unlock",
            freeTrialDays = 0,
            isRecommended = false,
            referencePriceVnd = 399_000L,
            referencePriceUsd = "39.99",
            savingsPercent = 0,
            taglineVi = "Thanh toán một lần · Gắn bó trọn đời",
            taglineEn = "One-time unlock · Forever yours",
        ),
    )

    fun isVipSku(productId: String): Boolean = productId in ALL_SKUS

    fun planFor(sku: String): ProPlanSpec? = PLANS.firstOrNull { it.sku == sku }
}

@Serializable
enum class ProFeature(
    val titleVi: String,
    val titleEn: String,
    val descriptionVi: String,
    val descriptionEn: String,
) {
    AD_FREE(
        titleVi = "Không quảng cáo",
        titleEn = "Ad-Free Space",
        descriptionVi = "Giữ không gian hai bạn luôn riêng tư, liền mạch",
        descriptionEn = "Zero banners or interruptions across every tab",
    ),
    AI_GIFT_CONCIERGE_UNLIMITED(
        titleVi = "Trợ lý quà tặng & lời chúc",
        titleEn = "Gift & Love-Note Advisor",
        descriptionVi = "Chọn quà theo hồ sơ, lọc dị ứng (tối đa 60 lượt/tháng)",
        descriptionEn = "Dossier-matched picks & allergy filters (up to 60/month)",
    ),
    DEEP_PARTNER_DOSSIER_UNLIMITED(
        titleVi = "Hồ sơ người thương chuyên sâu",
        titleEn = "Unlimited Partner Dossier",
        descriptionVi = "Lưu không giới hạn ghi chú bí mật, số đo và mùi hương",
        descriptionEn = "Save unlimited secret hints, sizes, scents & gift history",
    ),
    EARLY_14D_30D_REMINDERS(
        titleVi = "Nhắc hẹn sớm 14–30 ngày",
        titleEn = "14–30 Day Early Alerts",
        descriptionVi = "Đủ thời gian đặt bàn và chuẩn bị quà đặt riêng",
        descriptionEn = "Extra time to reserve dining or custom gifts",
    ),
    MONTHIVERSARY_CADENCE(
        titleVi = "Kỷ niệm tròn tháng & mốc đẹp",
        titleEn = "Monthly & Special Milestones",
        descriptionVi = "Tự động báo tròn tháng cùng mốc 520, 1.314 ngày",
        descriptionEn = "Automatic monthly anniversaries plus 520 & 1,314 days",
    ),
    PREMIUM_WALLPAPERS_AND_WIDGETS(
        titleVi = "Hình nền đôi & Widget cao cấp",
        titleEn = "Curated Themes & Widgets",
        descriptionVi = "Mở bộ hình nền độc quyền và widget đếm ngày trên màn hình chính",
        descriptionEn = "Unlock couple themes and home-screen day counters",
    ),
    ;

    fun isUnlocked(isVip: Boolean, currentUsageCount: Int = 0): Boolean {
        if (isVip) return true
        return when (this) {
            AI_GIFT_CONCIERGE_UNLIMITED -> currentUsageCount < ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH
            DEEP_PARTNER_DOSSIER_UNLIMITED -> currentUsageCount < ProTierCatalog.FREE_SECRET_NOTES_LIMIT
            AD_FREE,
            EARLY_14D_30D_REMINDERS,
            MONTHIVERSARY_CADENCE,
            PREMIUM_WALLPAPERS_AND_WIDGETS -> false
        }
    }
}

@Serializable
enum class SacredZone {
    ONBOARDING,
    PAYWALL,
    PIN_LOCK,
    MILESTONE_CELEBRATION_DAY,
    MEMORY_CAPTURE_FLOW,
    PAIRING_ACCEPT_FLOW,
}

@Serializable
enum class AdPlacementType {
    BANNER,
    INTERSTITIAL,
    APP_OPEN,
}

@Serializable
enum class AdBlockReason {
    VIP_USER,
    NO_CONSENT,
    SACRED_ZONE,
    WARMUP_ACTIVE,
    COOLDOWN_ACTIVE,
    SESSION_CAP_REACHED,
}

@Serializable
data class AdSessionState(
    val isVip: Boolean = false,
    val hasConsent: Boolean = false,
    val activeSacredZones: Set<SacredZone> = emptySet(),
    val sessionStartedAtMs: Long = 0L,
    val lastInterstitialShownAtMs: Long = 0L,
    val interstitialsShownThisSession: Int = 0,
    val isCelebrationDay: Boolean = false,
)

@Serializable
data class AdDecision(
    val allowed: Boolean,
    val blockReason: AdBlockReason? = null,
)

@Serializable
enum class UpsellTrigger {
    AI_QUOTA_REACHED,
    SECRET_NOTES_LIMIT,
    EARLY_REMINDER_14D_30D,
    MONTHIVERSARY_CADENCE,
    WIDGET_OR_WALLPAPER_LOCKED,
    POST_MILESTONE_CELEBRATION,
}

@Serializable
data class SoftPaywallPrompt(
    val trigger: UpsellTrigger,
    val feature: ProFeature,
    val titleVi: String,
    val titleEn: String,
    val messageVi: String,
    val messageEn: String,
    val ctaVi: String,
    val ctaEn: String,
    val recommendedSku: String,
)

/**
 * Win-win monetization rules:
 * - Zero ads for VIP users, missing UMP consent, celebration days, or any SacredZone.
 * - Enforces 60s warmup, 120s interstitial cooldown, and max 3 interstitials/session.
 * - Contextual non-intrusive soft-paywall prompts tied to genuine user intent.
 */
object AdPlacementPolicy {
    const val SESSION_WARMUP_MS: Long = 60_000L
    const val INTERSTITIAL_COOLDOWN_MS: Long = 120_000L
    const val MAX_INTERSTITIALS_PER_SESSION: Int = 3

    fun isInSacredZone(state: AdSessionState): Boolean =
        state.isCelebrationDay || state.activeSacredZones.isNotEmpty()

    fun evaluate(type: AdPlacementType, state: AdSessionState, nowMs: Long): AdDecision {
        if (state.isVip) return AdDecision(allowed = false, blockReason = AdBlockReason.VIP_USER)
        if (!state.hasConsent) return AdDecision(allowed = false, blockReason = AdBlockReason.NO_CONSENT)
        if (isInSacredZone(state)) return AdDecision(allowed = false, blockReason = AdBlockReason.SACRED_ZONE)

        if (type == AdPlacementType.BANNER) {
            return AdDecision(allowed = true, blockReason = null)
        }

        val elapsedSinceStart = nowMs - state.sessionStartedAtMs
        if (elapsedSinceStart < SESSION_WARMUP_MS) {
            return AdDecision(allowed = false, blockReason = AdBlockReason.WARMUP_ACTIVE)
        }

        if (type == AdPlacementType.INTERSTITIAL) {
            if (state.interstitialsShownThisSession >= MAX_INTERSTITIALS_PER_SESSION) {
                return AdDecision(allowed = false, blockReason = AdBlockReason.SESSION_CAP_REACHED)
            }
            if (state.lastInterstitialShownAtMs > 0L) {
                val elapsedSinceLast = nowMs - state.lastInterstitialShownAtMs
                if (elapsedSinceLast < INTERSTITIAL_COOLDOWN_MS) {
                    return AdDecision(allowed = false, blockReason = AdBlockReason.COOLDOWN_ACTIVE)
                }
            }
        }
        return AdDecision(allowed = true, blockReason = null)
    }

    fun canShowBanner(state: AdSessionState, nowMs: Long = 0L): Boolean =
        evaluate(AdPlacementType.BANNER, state, nowMs).allowed

    fun canShowInterstitial(state: AdSessionState, nowMs: Long): Boolean =
        evaluate(AdPlacementType.INTERSTITIAL, state, nowMs).allowed

    fun canShowAppOpen(state: AdSessionState, nowMs: Long): Boolean =
        evaluate(AdPlacementType.APP_OPEN, state, nowMs).allowed

    fun recordInterstitialShown(state: AdSessionState, shownAtMs: Long): AdSessionState =
        state.copy(
            lastInterstitialShownAtMs = shownAtMs,
            interstitialsShownThisSession = state.interstitialsShownThisSession + 1,
        )

    fun softPaywallUpsell(
        trigger: UpsellTrigger,
        isVip: Boolean,
        currentCount: Int = 0,
    ): SoftPaywallPrompt? {
        if (isVip) return null
        return when (trigger) {
            UpsellTrigger.AI_QUOTA_REACHED -> {
                if (currentCount < ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH) return null
                SoftPaywallPrompt(
                    trigger = trigger,
                    feature = ProFeature.AI_GIFT_CONCIERGE_UNLIMITED,
                    titleVi = "Đã dùng ${ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH} lượt gợi ý AI tháng này",
                    titleEn = "Used ${ProTierCatalog.FREE_AI_GENERATIONS_PER_MONTH} AI concierge runs this month",
                    messageVi = "Nâng cấp InLove Pro để dùng tới ${ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH} lượt gợi ý mỗi tháng, kèm 3 ngày dùng thử miễn phí gói năm",
                    messageEn = "Upgrade to InLove Pro for up to ${ProTierCatalog.VIP_AI_GENERATIONS_PER_MONTH} monthly concierge runs, starting with a 3-day free yearly trial",
                    ctaVi = "Dùng thử Pro 3 ngày",
                    ctaEn = "Start 3-day Pro trial",
                    recommendedSku = ProTierCatalog.YEARLY,
                )
            }
            UpsellTrigger.SECRET_NOTES_LIMIT -> {
                if (currentCount < ProTierCatalog.FREE_SECRET_NOTES_LIMIT) return null
                SoftPaywallPrompt(
                    trigger = trigger,
                    feature = ProFeature.DEEP_PARTNER_DOSSIER_UNLIMITED,
                    titleVi = "Đã lưu tối đa ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} ghi chú bí mật",
                    titleEn = "Saved ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} secret surprise notes",
                    messageVi = "Gói miễn phí lưu tối đa ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} ghi chú. Nâng cấp InLove Pro để lưu thêm không giới hạn",
                    messageEn = "Free accounts store up to ${ProTierCatalog.FREE_SECRET_NOTES_LIMIT} notes. Upgrade to InLove Pro for unlimited notes",
                    ctaVi = "Xem gói InLove Pro",
                    ctaEn = "View InLove Pro",
                    recommendedSku = ProTierCatalog.YEARLY,
                )
            }
            UpsellTrigger.EARLY_REMINDER_14D_30D -> SoftPaywallPrompt(
                trigger = trigger,
                feature = ProFeature.EARLY_14D_30D_REMINDERS,
                titleVi = "Nhắc sớm 14–30 ngày trước dịp lớn",
                titleEn = "Early 14–30 day reminders",
                messageVi = "Đặt bàn và chuẩn bị quà làm riêng trước cao điểm lễ với lịch nhắc sớm Pro",
                messageEn = "Book dining and custom gifts ahead of peak dates with Pro early alerts",
                ctaVi = "Bật nhắc sớm với Pro",
                ctaEn = "Enable with Pro",
                recommendedSku = ProTierCatalog.YEARLY,
            )
            UpsellTrigger.MONTHIVERSARY_CADENCE -> SoftPaywallPrompt(
                trigger = trigger,
                feature = ProFeature.MONTHIVERSARY_CADENCE,
                titleVi = "Nhắc kỷ niệm tròn tháng tự động",
                titleEn = "Monthly monthiversary reminders",
                messageVi = "Theo dõi từng mốc tròn tháng cùng các cột mốc 520 và 1314 ngày với InLove Pro",
                messageEn = "Celebrate every month together plus 520 and 1314 day milestones with Pro",
                ctaVi = "Xem gói InLove Pro",
                ctaEn = "View InLove Pro",
                recommendedSku = ProTierCatalog.YEARLY,
            )
            UpsellTrigger.WIDGET_OR_WALLPAPER_LOCKED -> SoftPaywallPrompt(
                trigger = trigger,
                feature = ProFeature.PREMIUM_WALLPAPERS_AND_WIDGETS,
                titleVi = "Giao diện và widget InLove Pro",
                titleEn = "InLove Pro themes and widgets",
                messageVi = "Đưa bộ đếm ngày yêu ra màn hình chính cùng bộ hình nền riêng và không quảng cáo",
                messageEn = "Put your love counter on the home screen with curated themes and no ads",
                ctaVi = "Xem gói InLove Pro",
                ctaEn = "View InLove Pro",
                recommendedSku = ProTierCatalog.YEARLY,
            )
            UpsellTrigger.POST_MILESTONE_CELEBRATION -> SoftPaywallPrompt(
                trigger = trigger,
                feature = ProFeature.AD_FREE,
                titleVi = "Không gian hai bạn không quảng cáo",
                titleEn = "Keep your couple space ad-free",
                messageVi = "Loại bỏ toàn bộ quảng cáo và lưu giữ kỷ niệm trọn vẹn với InLove Pro",
                messageEn = "Remove all ads and keep every milestone uninterrupted with InLove Pro",
                ctaVi = "Xem gói InLove Pro",
                ctaEn = "View InLove Pro",
                recommendedSku = ProTierCatalog.LIFETIME,
            )
        }
    }
}
