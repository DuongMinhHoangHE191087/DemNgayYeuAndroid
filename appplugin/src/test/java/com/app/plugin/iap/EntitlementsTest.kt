package com.app.plugin.iap

import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.ads.RemoveAdsMask
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

class EntitlementsTest {

    @Before fun setUp() = Entitlements.clearAll()
    @After fun tearDown() = Entitlements.clearAll()

    @Test
    fun `mac dinh khong co quyen loi nao`() {
        assertFalse(Entitlements.hasRemoveAds())
        assertTrue(Entitlements.activeProductIds().isEmpty())
    }

    @Test
    fun `san pham da khai bao remove ads thi cap quyen loi`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly", "no_ads_forever"))
        Entitlements.grant("premium_monthly")
        assertTrue(Entitlements.hasRemoveAds())
    }

    @Test
    fun `san pham khong khai bao thi khong cap remove ads`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("coin_pack_small")
        assertFalse("mua coin khong duoc tat quang cao", Entitlements.hasRemoveAds())
        assertTrue("nhung van phai ghi nhan la dang so huu", Entitlements.isActive("coin_pack_small"))
    }

    @Test
    fun `thu hoi quyen loi khi het han`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        assertTrue(Entitlements.hasRemoveAds())
        Entitlements.revoke("premium_monthly")
        assertFalse("sub het han phai tra lai quang cao", Entitlements.hasRemoveAds())
    }

    @Test
    fun `con san pham khac van giu remove ads`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly", "no_ads_forever"))
        Entitlements.grant("premium_monthly")
        Entitlements.grant("no_ads_forever")
        Entitlements.revoke("premium_monthly")
        assertTrue("van con goi vinh vien", Entitlements.hasRemoveAds())
    }

    @Test
    fun `dong bo thay the toan bo trang thai`() {
        // Đây là ngữ nghĩa quan trọng nhất: `sync` phản ánh đúng những gì store trả về.
        // Nếu chỉ cộng thêm mà không gỡ đi, một sub đã huỷ sẽ vĩnh viễn còn hiệu lực trên máy.
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        Entitlements.grant("coin_pack")

        Entitlements.sync(setOf("coin_pack"))
        assertFalse("sub da bien mat khoi store thi phai het hieu luc", Entitlements.hasRemoveAds())
        assertTrue(Entitlements.isActive("coin_pack"))
        assertEquals(1, Entitlements.activeProductIds().size)
    }

    @Test
    fun `dong bo rong thi thu hoi tat ca`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        Entitlements.sync(emptySet())
        assertFalse(Entitlements.hasRemoveAds())
        assertTrue(Entitlements.activeProductIds().isEmpty())
    }

    @Test
    fun `doi danh sach san pham remove ads tinh lai ngay`() {
        // Danh sách này đến từ remote config. Đổi nó mà không tính lại sẽ khiến quyền lợi
        // của người đang sở hữu bị treo ở giá trị cũ cho tới lần mua kế tiếp.
        Entitlements.grant("vip_pass")
        assertFalse(Entitlements.hasRemoveAds())
        Entitlements.setRemoveAdsProducts(setOf("vip_pass"))
        assertTrue("khai bao muon van phai co hieu luc ngay", Entitlements.hasRemoveAds())
    }

    @Test
    fun `cap quyen loi hai lan khong nhan doi`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        Entitlements.grant("premium_monthly")
        assertEquals(1, Entitlements.activeProductIds().size)
    }

    // ═══ Mask theo sản phẩm (Điều 13.4) ═══

    @Test
    fun `goi re chi go dinh dang gian doan, rewarded van con`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.setRemoveAdsMasks(mapOf("no_ads_lite" to com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE))
        Entitlements.grant("no_ads_lite")

        val mask = Entitlements.removeAdsMask()
        assertEquals(com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE, mask)
        assertFalse(com.app.plugin.ads.RemoveAdsMask.isRemoved(mask, com.app.plugin.ads.RemoveAdsMask.FLAG_REWARDED))
        // #15: native collapsible (lớp phủ ~45 % màn hình) là định dạng gián đoạn, gói rẻ gỡ cả nó.
        assertTrue(com.app.plugin.ads.RemoveAdsMask.isRemoved(mask, com.app.plugin.ads.RemoveAdsMask.FLAG_NATIVE_COLLAPSIBLE))
        assertFalse(com.app.plugin.ads.RemoveAdsMask.isRemoved(mask, com.app.plugin.ads.RemoveAdsMask.FLAG_NATIVE))
        assertTrue(Entitlements.hasRemoveAds())
    }

    @Test
    fun `goi Pro khong khai mask thi go het`() {
        Entitlements.setRemoveAdsProducts(setOf("premium_monthly"))
        Entitlements.grant("premium_monthly")
        assertEquals(com.app.plugin.ads.RemoveAdsMask.ALL, Entitlements.removeAdsMask())
    }

    @Test
    fun `khong so huu gi thi mask rong`() {
        Entitlements.setRemoveAdsMasks(mapOf("no_ads_lite" to com.app.plugin.ads.RemoveAdsMask.INTERRUPTIVE))
        assertEquals(com.app.plugin.ads.RemoveAdsMask.NONE, Entitlements.removeAdsMask())
        assertFalse(Entitlements.hasRemoveAds())
    }

    @Test
    fun `mask song qua lan khoi tao lai, doc tu dia chu khong tu bo nho`() {
        withPersistedPrefs { store ->
            Entitlements.setRemoveAdsProducts(setOf("premium_monthly", "no_ads_lite"))
            Entitlements.setRemoveAdsMasks(mapOf("no_ads_lite" to RemoveAdsMask.INTERRUPTIVE))
            Entitlements.grant("no_ads_lite")

            // Xoá sạch bộ nhớ mà không đụng đĩa: tháo prefs ra trước khi clearAll.
            val helper = PreferenceHelper.getInstance()
            helper.preferences = null
            Entitlements.clearAll()
            assertEquals(RemoveAdsMask.NONE, Entitlements.removeAdsMask())

            // Process mới: chỉ còn những gì đã ghi xuống đĩa.
            helper.preferences = inMemoryPrefs(store)
            Entitlements.reloadForTest()
            assertEquals(
                "no_ads_lite nam trong removeAdsProducts nhung co mask rieng, phai giu INTERRUPTIVE",
                RemoveAdsMask.INTERRUPTIVE, Entitlements.removeAdsMask()
            )
            assertFalse(RemoveAdsMask.isRemoved(Entitlements.removeAdsMask(), RemoveAdsMask.FLAG_REWARDED))
        }
    }

    @Test
    fun `muc mask da luu bi hong thi log, bo qua, khong bao gio noi rong thanh ALL`() {
        withPersistedPrefs { store ->
            // Khoá và định dạng trên đĩa là hợp đồng với các bản đã cài — đổi là mất dữ liệu.
            // Mục hỏng: giá trị không phải số, id rỗng, thiếu ':', và -1 (sanitize(-1) = ALL).
            store["iap_remove_ads_products"] = "no_ads_lite|premium_monthly"
            store["iap_active_products"] = "no_ads_lite"
            store["entitlements_remove_ads_masks"] = "no_ads_lite:abc|:4|rac|lite_neg:-1|lite_ok:1"
            mockStatic(Log::class.java).use { log ->
                Entitlements.reloadForTest()

                assertNotEquals(
                    "mask hong cua san pham trong removeAdsProducts khong duoc roi ve ALL",
                    RemoveAdsMask.ALL, Entitlements.removeAdsMask()
                )
                assertEquals(RemoveAdsMask.NONE, Entitlements.removeAdsMask())

                Entitlements.grant("lite_neg")
                assertEquals("-1 tren dia la hong, khong phai ALL", RemoveAdsMask.NONE, Entitlements.removeAdsMask())

                Entitlements.grant("lite_ok")
                assertEquals("muc hop le canh muc hong van doc duoc", 1, Entitlements.removeAdsMask())

                log.verify { Log.e(eq(LogUtil.TAG), contains("no_ads_lite:abc")) }
                log.verify { Log.e(eq(LogUtil.TAG), contains("':4'")) }
                log.verify { Log.e(eq(LogUtil.TAG), contains("'rac'")) }
                log.verify { Log.e(eq(LogUtil.TAG), contains("lite_neg:-1")) }
            }
        }
    }

    @Test
    fun `id khong ghi duoc xuong dia thi log va bo, bit ngoai pham vi thi log va cat`() {
        withPersistedPrefs { store ->
            mockStatic(Log::class.java).use { log ->
                Entitlements.setRemoveAdsMasks(
                    mapOf("a|b" to RemoveAdsMask.ALL, "x:y" to RemoveAdsMask.ALL, " " to 1, "lite" to ((1 shl 6) or 1))
                )
                assertEquals("lite:1", store["entitlements_remove_ads_masks"])

                Entitlements.grant("a|b")
                Entitlements.grant("x:y")
                Entitlements.grant("lite")
                assertEquals(1, Entitlements.removeAdsMask())

                log.verify { Log.e(eq(LogUtil.TAG), contains("'a|b'")) }
                log.verify { Log.e(eq(LogUtil.TAG), contains("'x:y'")) }
                log.verify { Log.e(eq(LogUtil.TAG), contains("' '")) }
                // logE chứ không logW: lúc Application.onCreate cờ LogUtil mới là 1, logW không in.
                log.verify { Log.e(eq(LogUtil.TAG), contains("'lite'")) }
            }
        }
    }

    // ═══ Lưu trữ giả cho test sống qua process ═══

    /**
     * `PreferenceHelper` là singleton dùng chung với mọi lớp test khác trong cùng JVM, nên luôn
     * trả lại `preferences` cũ và dọn bộ nhớ của `Entitlements` sau khi xong.
     */
    private fun withPersistedPrefs(block: (MutableMap<String, String>) -> Unit) {
        val helper = PreferenceHelper.getInstance()
        val previous = helper.preferences
        val store = mutableMapOf<String, String>()
        helper.preferences = inMemoryPrefs(store)
        try {
            block(store)
        } finally {
            helper.preferences = null
            Entitlements.clearAll()
            helper.preferences = previous
        }
    }

    private fun inMemoryPrefs(store: MutableMap<String, String>): SharedPreferences {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(anyString(), anyString())).thenAnswer {
            store[it.getArgument<String>(0)] ?: it.getArgument<String>(1)
        }
        `when`(editor.putString(anyString(), anyString())).thenAnswer {
            store[it.getArgument<String>(0)] = it.getArgument<String>(1)
            editor
        }
        `when`(editor.remove(anyString())).thenAnswer {
            store.remove(it.getArgument<String>(0))
            editor
        }
        return prefs
    }

    /**
     * R6n1 — đặc tả + giết đột biến Y1 (bỏ `if (!verified) return` trong `recompute`): chưa kiểm xong thì không đẩy mask
     * nào sang AdsHelper (mask đã lưu giữ nguyên); kiểm xong mới đẩy.
     */
    @Test
    fun `R6n1 chua verify thi khong day mask sang AdsHelper`() {
        val ads = com.app.plugin.ads.AdsHelper.instance
        fun field(o: Any, n: String) = o.javaClass.getDeclaredField(n).apply { isAccessible = true }
        field(ads, "pendingRemoveAdsMask").set(ads, null)
        field(Entitlements, "verified").set(Entitlements, false) // cold start: lượt kiểm còn xếp hàng
        Entitlements.setRemoveAdsProducts(setOf("pro"))
        Entitlements.grant("pro")
        assertTrue("grant phải có hiệu lực trong bộ nhớ", Entitlements.hasRemoveAds())
        assertEquals("chưa kiểm xong: AdsHelper không nhận mask", null, field(ads, "pendingRemoveAdsMask").get(ads))

        Entitlements::class.java.getDeclaredMethod("verifyStored").apply { isAccessible = true }.invoke(Entitlements)
        assertEquals("kiểm xong: mask được đẩy", RemoveAdsMask.ALL, field(ads, "pendingRemoveAdsMask").get(ads))
    }
}
