package com.app.plugin.firbase

import android.util.Log
import com.app.plugin.SdkKillSwitch
import com.app.plugin.crm.CrmBridge
import com.app.plugin.iap.BattlePass
import com.app.plugin.iap.PurchaseRouter
import com.app.plugin.iap.RecurringGrants
import com.app.plugin.offerwall.OfferwallManager
import com.app.plugin.until.LogUtil
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 2.0.0 C1d (D1, D16, plan §Hợp đồng C): 5 section RC của game (PurchaseRouter, BattlePass, RecurringGrants,
 * OfferwallManager, CrmBridge) đi qua registry thay vì 5 lời gọi trực tiếp trong `FirHelperConfig`; thứ tự áp và 5 dòng
 * `… (RemoteConfig): <report>` phải y hệt trước. Chạy đúng hai hàm thật `readRemoteCf` → `applyRemoteCf` (reflection,
 * như `BrainPolicyTest`) với một `FirebaseRemoteConfig` giả — cùng một test chạy được trên mã cũ (golden lấy từ đó).
 */
class RcSectionOrderTest {

    @Before fun setUp() = clearGame()

    @After fun tearDown() {
        clearGame()
        LogUtil.setFlagLog(1)
        SdkKillSwitch.current = SdkKillSwitch.NONE
    }

    private fun clearGame() {
        PurchaseRouter.clear(); BattlePass.clear(); RecurringGrants.clear(); OfferwallManager.clear(); CrmBridge.clear()
    }

    /** Các dòng `Log.d` của một lượt `readRemoteCf` + `applyRemoteCf` với giá trị RC [values] (khoá vắng = ""). */
    private fun applyLines(values: Map<String, String>): List<String> {
        val rc = mock(FirebaseRemoteConfig::class.java)
        `when`(rc.getString(anyString())).thenAnswer { values[it.arguments[0] as String] ?: "" }
        val cfg = FirHelperConfig()
        FirHelperConfig::class.java.getDeclaredField("remoteConfig\$delegate").apply { isAccessible = true }.set(cfg, lazyOf(rc))
        val read = FirHelperConfig::class.java.getDeclaredMethod("readRemoteCf").apply { isAccessible = true }
        val snapshotClass = Class.forName("com.app.plugin.firbase.FirHelperConfig\$RemoteCfSnapshot")
        val apply = FirHelperConfig::class.java.getDeclaredMethod("applyRemoteCf", snapshotClass).apply { isAccessible = true }
        val lines = CopyOnWriteArrayList<String>()
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java) { inv ->
            if (inv.method.name == "d" && inv.arguments.size == 2) lines += inv.arguments[1] as String
            0
        }.use {
            val snapshot = read.invoke(cfg)
            try {
                apply.invoke(cfg, snapshot)
            } catch (e: InvocationTargetException) {
                // đuôi hàm (AdsHelper.saveConfig/checkAfterLoadIdsConfig) không có context trong unit test; 5 section đã chạy trước đó
            }
        }
        return lines.filter { it.contains(" (RemoteConfig): ") }
    }

    @Test
    fun `G1 thu tu 5 section game va 5 dong RemoteConfig y het truoc (golden)`() {
        val lines = applyLines(
            mapOf(
                "purchase_routing_android" to """{"rules":[]}""",
                "battle_pass_android" to """{"season_id":"s1"}""",
                "recurring_grants_android" to """{"grants":[]}""",
                "offerwall_config_android" to """{"version":"ow_g1","enabled":false}""",
                "crm_bridge_android" to """{"version":"crm_g1","enabled":true,"sinks":["braze"]}"""
            )
        )
        val game = lines.filter { it.substringBefore(" (RemoteConfig): ") in GAME_TAGS }
        assertEquals(GOLDEN, game)
        assertEquals("ow_g1", OfferwallManager.activeVersion())
        assertEquals("crm_g1", CrmBridge.activeVersion())
    }

    private companion object {
        val GAME_TAGS = setOf("PurchaseRouter", "BattlePass", "RecurringGrants", "OfferwallManager", "CrmBridge")

        /** Lấy từ mã cũ (19e6c3f, 5 lời gọi trực tiếp `FirHelperConfig.kt:224-260`). */
        val GOLDEN = listOf(
            "PurchaseRouter (RemoteConfig): TỪ CHỐI cấu hình routing (thiếu trường 'version') — giữ nguyên 'chưa nạp'",
            "BattlePass (RemoteConfig): từ chối (thiếu trường version) — giữ nguyên bản chưa nạp",
            "RecurringGrants (RemoteConfig): từ chối (thiếu trường version) — giữ nguyên bản chưa nạp",
            "OfferwallManager (RemoteConfig): áp ow_g1: TẮT, cho phép mọi provider đã đăng ký",
            "CrmBridge (RemoteConfig): áp crm_g1: bật, chỉ [braze]"
        )
    }
}
