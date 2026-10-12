package com.app.plugin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bánh cóc G8 (DOC/65 §3) cho cả SDK, plan 1.6.0 Task 0: mọi file `src/main/java/**/*.kt` ≤ 800 dòng; file trong
 * [LIMITS] có trần riêng thấp hơn (1.6.4 Task 2: không còn file nào trên 800). Không bao giờ nâng trần quá số đo hiện tại + 10; không @Ignore. `AdsHelper.kt` giữ cổng riêng
 * `AdsHelperSizeGateTest` (700).
 */
class SdkFileSizeGateTest {
    @Test
    fun `khong file nao cua SDK vuot tran G8`() {
        val root = File("src/main/java")
        assertTrue("Không thấy ${root.absolutePath}: test chạy sai thư mục làm việc, cổng sẽ xanh rỗng", root.isDirectory)
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Chỉ quét ${files.size} file .kt — sai gốc?", files.size >= MIN_FILES)
        val over = files.mapNotNull { f ->
            val rel = f.relativeTo(root).path
            val limit = LIMITS.entries.firstOrNull { rel.endsWith(it.key) }?.value ?: DEFAULT_LIMIT
            val lines = f.readLines().size
            if (lines > limit) "$rel = $lines > $limit" else null
        }
        assertTrue("File vượt trần (DOC/65 G8; plan 1.6.0 Task 0): $over", over.isEmpty())
    }

    /** 2.1.0 P2 (R-G1): `appplugin-game` không có cổng cỡ file; mọi file ≤ đo lớn nhất + 10 (372 + 10 tại 2.1.0 G1). */
    @Test
    fun `khong file nao cua appplugin-game vuot tran`() {
        val root = File("appplugin-game/src/main/java")
        assertTrue("Không thấy ${root.absolutePath}: test chạy sai thư mục làm việc, cổng sẽ xanh rỗng", root.isDirectory)
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Chỉ quét ${files.size} file .kt của -game — sai gốc?", files.size >= GAME_MIN_FILES)
        val over = files.mapNotNull { f ->
            val lines = f.readLines().size
            if (lines > GAME_LIMIT) "${f.relativeTo(root).path} = $lines > $GAME_LIMIT" else null
        }
        assertTrue("File -game vượt trần $GAME_LIMIT (R-G1, 2.1.0 P2): $over", over.isEmpty())
    }

    companion object {
        const val GAME_LIMIT = 382 // 372 (readLines, BattlePass.kt) 2026-10-03 + 10
        const val GAME_MIN_FILES = 5 // 6 file .kt tại 2.1.0 G1
        const val DEFAULT_LIMIT = 800
        const val MIN_FILES = 150 // 186 file .kt tại 8f04bba; dưới mức này là quét sai gốc (review Task 0 M1)
        val LIMITS = mapOf(
            "logevent/LogEventServer.kt" to 731,   // 721 (readLines) 2026-09-30, 1.7.0 Task 1 (logAd* uỷ quyền AdEventLog; trước 776)
            "brain/DecisionLogger.kt" to 580,      // 570 (readLines) 2026-10-01, 1.9.0 Task 0a (trần = số đo + 10; D32)
            "brain/DecisionEngine.kt" to 281,      // 274 (readLines) 2026-10-05, 2.2.0 LIC D28-220: gate giấy phép + feedback bỏ license_* (D5 +10 trên trần plan 271)
            "brain/AdsBrain.kt" to 689,            // 688 (readLines) 2026-10-05, 2.2.0 LIC D30-220: hàng đợi giữ exposure/first-purchase (D5 +10 trên trần plan 679)
            "consent/GppConsent.kt" to 182,        // 172 (readLines) 2026-09-29, 1.6.4 Task 2 (tách GppBits; trước 249)
            "appsflyer/AppsflyerHelper.kt" to 416, // 406 (readLines) 2026-09-29, 1.6.4 Task 2 (tách AppsflyerGppFilter; trước 444)
            "logevent/DurableEvents.kt" to 242,    // 234 (readLines) 2026-09-30, 1.8.0 Task 7 (+6 event funnel; trước 232)
            "logevent/LogEventControl.kt" to 152,  // 142 (readLines) 2026-09-30, 1.8.0 Task 7 (chỉ dời comment; trước 142)
            "ads/AdsHelper.kt" to 700,             // trùng AdsHelperSizeGateTest; giữ cả hai
            "iap/IapHelper.kt" to 455,             // 445 (readLines) 2026-10-01, 1.9.0 Task 11 (tách PurchaseFlow; trước 459)
            "iap/PurchaseRecorder.kt" to 120,      // 2.0.0 B1 (tách bước ghi sổ khỏi IapHelper; plan ≤ 120)
            "paywall/PaywallManager.kt" to 321,    // 289 (readLines) 2026-09-30, 1.8.0 Task 9 (schema v2, parse tách PaywallConfigV2; trước 281; trần = 281 + 40, D18)
            "iap/Entitlements.kt" to 250,          // 247 (readLines) 2026-09-30, 1.8.0 Task 6 (HMAC + copy-on-write; trước 225; D18 = 225 + 25)
            "logevent/LogEventSender.kt" to 215,   // 205 (readLines) 2026-09-30, 1.8.0 Task 10 (signals /push giao ServerSignals; trước 203; plan ≤ 225)
            "app/AppPluginBase.kt" to 331,         // 321 (readLines) 2026-10-07, 2.4.1 HOSTFIX D36 + R-HOSTFIX (nhận host theo launcher/gói app; đo + 10; trước 295)
            "iap/SubscriptionLifecycle.kt" to 360, // 360 (readLines) 2026-10-01, 1.8.0 Task 11 (khối in_app_messages; trước 352; plan ≤ 360)
            "ads/adsmob/AdsMobPlNtFull.kt" to 589, // 579 (readLines) 2026-10-02, 2.0.0 C1a (tách binder + nền, 3 site T6; trước 791; D1 ≤ 700)
            "license/Ed25519.kt" to 105,           // 95 (readLines) 2026-10-03, 2.1.0 P2 (đo + 10; SA1 đặt 200 = trần plan)
            "license/LicenseToken.kt" to 96,       // 86 (readLines) 2026-10-03, 2.1.0 P2 (đo + 10; trước 150)
            "license/B64Url.kt" to 40,             // 2.2.0 MIN24 (D9-220: codec base64url Kotlin thuần ≤ 40)
            "license/LicenseGate.kt" to 214,       // 206 → 214 2026-10-09, 2.4.3 LCACHE (+1 since_start) + FASTSTART-A (+7 poster front-async); đo, trần plan ≤ 216
            "license/LicenseKeys.kt" to 30,        // 20 (readLines) 2026-10-03, 2.1.0 P2 (đo + 10; trước 40)
            "firbase/RcSectionRegistry.kt" to 90,  // 80 (readLines) 2026-10-03, 2.1.0 P2 (đo + 10)
            "ads/adsmob/AdsMobNtFullBinder.kt" to 104,     // 94 (readLines) 2026-10-03, 2.0.0 C1a/C3b (M13; đo + 10)
            "ads/adsmob/AdsMobNtFullBackground.kt" to 156, // 146 (readLines), như trên (M13)
            "ads/adsfb/AdsFbPlNtCl.kt" to 338,     // 328 (readLines) 2026-10-03, 2.0.0 C3b (M18; plan ghi 337 theo đo 327 cũ)
            "ads/adsfb/AdsFbPlNtFull.kt" to 673,   // 663 (readLines), như trên (M18)
            "segment/UserSegmentEngine.kt" to 328, // 327 (readLines) 2026-10-04, 2.2.0 SEG fix 2 (R-SEG I7: recordEventNow tách khỏi recordEvent; D5 nâng ≤ 10: 318 → 328)
            "segment/SegmentHooks.kt" to 120,      // 115 (readLines) 2026-10-04, 2.2.0 SEG fix 1 (trần plan ≤ 120, thấp hơn đo + 10)
            "segment/SegmentStartup.kt" to 88,     // 79 (readLines) 2026-10-04, 2.2.0 SEG fix 1 (nạp RC nền + hàng đợi, D27-220; đo + 10)
        )
    }
}
