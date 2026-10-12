package com.app.plugin

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 C3b (D3, D17, Q7, D47-1): commit phá vỡ duy nhất của 2.0.0.
 * V1: 19 kiểu Q7 + 4 kiểu margin (D17) + `UserProperty` (D28) khai `internal` ở top-level trong nguồn (javap không thấy visibility lớp — F4,
 * 2.3.0 ROB3: margin thứ 5 `RemoveAdsMask` public lại (D15-IM: INTEGRATION.md §9 bước 6 bảo app dùng; V1 kiểm nó `public`),
 * nên census đọc nguồn). V2: `AdRequestFactory.build` và `AdsMobMy.getAdnetName` là thành viên `internal` (D47-1):
 * dump `api/appplugin.api` chỉ còn tên mangle `…$appplugin`, không còn chữ ký public gốc.
 */
class Q7CensusTest {
    private val src = File("src/main/java/com/app/plugin")

    /** Kiểu → file khai báo (đường dẫn dưới `com/app/plugin/`). */
    private val q7 = mapOf(
        "AdsConditionOb" to "AdsConditionOb.kt",
        "AdsBaseBannerCl" to "ads/AdsBaseBannerCl.kt",
        "AdsBaseRect" to "ads/AdsBaseRect.kt",
        "AdsPlBaseBn" to "ads/AdsPlBaseBn.kt",
        "AdsPlBaseFull" to "ads/AdsPlBaseFull.kt",
        "AdsPlBaseNative" to "ads/AdsPlBaseNative.kt",
        "AdsPlNtClBase" to "ads/AdsPlNtClBase.kt",
        "AdRequestFactory" to "ads/consent/AdRequestFactory.kt",
        "AppsflyerHelper" to "appsflyer/AppsflyerHelper.kt",
        "BanditPolicy" to "brain/BanditPolicy.kt",
        "DecisionLogQueue" to "brain/DecisionLogQueue.kt",
        "DecisionLogger" to "brain/DecisionLogger.kt",
        "HoldoutManager" to "brain/HoldoutManager.kt",
        "RewardModel" to "brain/RewardModel.kt",
        "FxRates" to "iap/FxRates.kt",
        "PlayIntegrityGuard" to "iap/PlayIntegrityGuard.kt",
        "EventRequest" to "logevent/EventRequest.kt",
        "LogService" to "logevent/LogService.kt",
        "PreUtil" to "until/PreUtil.kt",
    )
    private val margin = mapOf(
        "InstallReferrerHelper" to "attribution/InstallReferrerHelper.kt",
        "AdsBrain" to "brain/AdsBrain.kt",
        "ComfortIndex" to "brain/ComfortIndex.kt",
        "Constants" to "until/Constants.kt",
    )
    /** 2.3.0 ROB3 (D15-IM, cổng IM): mở lại `public`, không còn trong census internal. */
    private val reopened = mapOf("RemoveAdsMask" to "ads/RemoveAdsMask.kt")
    /** D28: public do sót (không keep; chỉ `createRequestBody` lộ, hàm đó cũng `internal`; 0 tham chiếu app). */
    private val d28 = mapOf("UserProperty" to "logevent/EventRequest.kt")

    private fun declaration(name: String, file: String): String? {
        val decl = Regex("^((public|internal|private|open|abstract|sealed|data|enum|inline|value|fun|annotation)\\s+)*(class|object|interface)\\s+$name\\b")
        return File(src, file).readLines().firstOrNull { decl.containsMatchIn(it) }
    }

    @Test
    fun `V1 census 19 kieu Q7 4 margin va UserProperty khai internal o top-level, RemoveAdsMask public`() {
        val all = q7 + margin + d28
        assertTrue("danh sách phải là 19 + 4 + 1 = 24", q7.size == 19 && margin.size == 4 && d28.size == 1 && all.size == 24)
        val missing = (all + reopened).filter { (n, f) -> declaration(n, f) == null }.keys
        assertTrue("không tìm thấy khai báo: $missing", missing.isEmpty())
        val notInternal = all.filter { (n, f) -> !declaration(n, f)!!.startsWith("internal ") }
            .map { (n, f) -> "$n ($f): ${declaration(n, f)}" }
        assertTrue("${all.size - notInternal.size}/24 internal; còn public:\n" + notInternal.joinToString("\n"), notInternal.isEmpty())
        val notPublic = reopened.filter { (n, f) -> !declaration(n, f)!!.startsWith("public ") }.keys
        assertTrue("2.3.0 ROB3: phải public: $notPublic", notPublic.isEmpty())
    }

    /** Thân khối lớp `cls` trong dump javap (dòng giữa header và `}`). */
    private fun block(dump: List<String>, cls: String): List<String> {
        val start = dump.indexOfFirst { it.startsWith("public ") && it.contains(" $cls {") }
        assertTrue("dump không có khối $cls", start >= 0)
        return dump.drop(start + 1).takeWhile { it != "}" }
    }

    @Test
    fun `V2 AdRequestFactory build va AdsMobMy getAdnetName khong con public trong dump`() {
        val dump = File("api/appplugin.api").readLines()
        val factory = block(dump, "com.app.plugin.ads.consent.AdRequestFactory")
        val mob = block(dump, "com.app.plugin.ads.adsmob.AdsMobMy")
        val leaked = factory.filter { it.contains(" build(") } + mob.filter { it.contains(" getAdnetName(") }
        assertTrue("còn chữ ký public:\n" + leaked.joinToString("\n"), leaked.isEmpty())
        assertTrue("thiếu build\$appplugin", factory.any { it.contains(" build\$appplugin(android.content.Context)") })
        assertTrue("thiếu getAdnetName\$appplugin", mob.any { it.contains(" getAdnetName\$appplugin(com.google.android.gms.ads.ResponseInfo)") })
    }
}
