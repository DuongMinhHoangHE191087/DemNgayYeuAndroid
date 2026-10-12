package com.app.plugin.ads

import android.util.Log
import com.app.plugin.logevent.ServerEventCapture
import com.app.plugin.until.Constants
import com.app.plugin.until.LogUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Waterfall eCPM — danh sách ad unit xếp theo giá, đồng bộ lại mỗi lần cấu hình từ xa đổi.
 *
 * ═══ VÌ SAO ĐÂY LÀ ĐIỂM ĐÁNG TEST NHẤT TRONG `ads/` ═══
 *
 * `listFromData()` phải hoà giải hai thứ cùng lúc: **thứ tự mới** từ Remote Config, và
 * **trạng thái đang chạy** của những quảng cáo đã tải xong hoặc đang tải. Làm sai theo hướng
 * "dựng lại tất cả cho sạch" sẽ **vứt đi một quảng cáo đã tải xong** — tức vứt đi một
 * impression đã trả tiền để lấy về, mỗi lần config đổi.
 *
 * Không có test nào ở đây trước bản này, và đây là 13.655 dòng code đang chạy trên tiền thật.
 */
class AdsEcmpsTest {

    private lateinit var adPl: AdsPlBase
    private lateinit var plEcmp: AdsPlacementEcmpBase
    private lateinit var ecmps: AdsEcmps

    /** Đồng hồ của waterfall: mốc load và tuổi quảng cáo đều đọc từ đây. */
    private var now = 1_000_000_000L

    @Before
    fun setUp() {
        // `AdsPlBase` nhận `Context` nên không dựng được thuần JVM. Chỉ cần nó trả về một
        // placement name — waterfall không gọi gì khác trên nó ở các đường đi được test
        // (trừ `destroyAd` khi bỏ quảng cáo hết hạn, mock trả `false`).
        adPl = mock(AdsPlBase::class.java)
        `when`(adPl.getLoadPlacement()).thenReturn("full_test")
        plEcmp = AdsPlacementEcmpBase(adPl)
        ecmps = plEcmp.adEcmps!!
        ecmps.nowMs = { now }
    }

    /**
     * Id ad unit thật của AdMob dài (`ca-app-pub-…/…`). Dùng id một ký tự trong test sẽ khiến
     * chuỗi ghép ngắn hơn ngưỡng `data.length > 5` của `listFromData` và bị bỏ qua im lặng —
     * test sẽ đỏ vì một lý do không liên quan gì tới thứ đang được kiểm.
     */
    private fun unit(name: String) = "ca-app-pub-3940256099942544/$name"

    private fun feed(vararg names: String) = ecmps.listFromData(names.joinToString(";") { unit(it) })

    private fun ids() = ecmps.list.map { it.adsId }

    private fun expect(vararg names: String) = names.map { unit(it) }

    private fun at(name: String) = ecmps.list.first { it.adsId == unit(name) }

    // ═══ Cổng đầu vào ═══

    @Test
    fun `chuoi qua ngan bi bo qua va khong dong vao danh sach dang chay`() {
        feed("id_a", "id_b")
        val before = ids()
        assertFalse("chuoi rong bi bo qua", ecmps.listFromData(""))
        assertFalse("chuoi ngan hon nguong bi bo qua", ecmps.listFromData("abc"))
        assertEquals("du lieu rac khong duoc xoa waterfall dang chay", before, ids())
    }

    @Test
    fun `nap lan dau tao du muc theo dung thu tu`() {
        assertTrue(feed("id_cao", "id_vua", "id_thap"))
        assertEquals(expect("id_cao", "id_vua", "id_thap"), ids())
    }

    @Test
    fun `thu tu tu remote config duoc giu nguyen`() {
        // Thứ tự CHÍNH LÀ waterfall — eCPM cao đứng trước. Sắp xếp lại là đổi doanh thu.
        feed("a", "b", "c")
        feed("c", "b", "a")
        assertEquals(expect("c", "b", "a"), ids())
    }

    // ═══ Tính chất quan trọng nhất: không vứt quảng cáo đã tải ═══

    @Test
    fun `quang cao DA TAI XONG duoc giu nguyen khi config doi`() {
        feed("a", "b", "c")
        val loaded = at("b")
        loaded.isLoaded = true
        loaded.adObject = Any()

        feed("a", "b", "c", "d")

        val after = at("b")
        assertSame("phai la DUNG doi tuong cu, khong phai ban dung lai", loaded, after)
        assertTrue(after.isLoaded)
        assertNotNull("mat adObject nghia la vut di mot impression da tra tien", after.adObject)
    }

    @Test
    fun `quang cao DANG TAI duoc giu nguyen`() {
        // Dựng lại một mục đang tải sẽ khiến callback của lượt tải cũ trỏ vào một đối tượng
        // đã bị bỏ rơi — quảng cáo tải xong nhưng không ai biết.
        feed("a", "b")
        val loading = at("a")
        loading.isLoading = true

        feed("b", "a")
        assertSame(loading, at("a"))
        assertTrue(at("a").isLoading)
    }

    @Test
    fun `muc dang tai bi go khoi config van duoc giu lai`() {
        // Bỏ nó đi giữa chừng là rò rỉ: lượt tải vẫn đang chạy và sẽ trả callback về hư không.
        feed("a", "b")
        at("a").isLoaded = true

        feed("b", "c")
        assertTrue("muc da tai phai con trong danh sach", ids().contains(unit("a")))
    }

    @Test
    fun `muc chua tai gi ma bi go khoi config thi bi loai`() {
        feed("a", "b")
        feed("b")
        assertEquals(expect("b"), ids())
    }

    // ═══ Cờ "có mục mới" điều khiển việc tải lại ═══

    @Test
    fun `tra ve true khi co id moi`() {
        feed("a", "b")
        assertTrue("them id moi phai bao co thay doi", feed("a", "b", "c"))
    }

    @Test
    fun `tra ve false khi chi doi thu tu`() {
        // `AdsPlacementEcmpBase.parserEcpms` dùng giá trị này để quyết định có tải lại không.
        // Báo "có mới" khi chỉ đảo thứ tự sẽ gây tải lại thừa mỗi lần fetch config.
        feed("a", "b", "c")
        assertFalse(feed("c", "b", "a"))
    }

    // ═══ Ad unit mặc định (lớp đáy của waterfall) ═══

    @Test
    fun `id mac dinh duoc them vao neu config khong nhac toi`() {
        // Đây là lớp đáy — thứ giữ cho placement còn quảng cáo khi mọi tầng eCPM đều trượt.
        ecmps.setDefaultIds(unit("id_mac_dinh"))
        feed("a", "b")
        assertTrue(ids().contains(unit("id_mac_dinh")))
        assertTrue(at("id_mac_dinh").isDefault)
    }

    @Test
    fun `nhieu id mac dinh deu duoc them`() {
        ecmps.setDefaultIds("${unit("df_1")};${unit("df_2")}")
        feed("a", "b")
        assertTrue(ids().containsAll(listOf(unit("df_1"), unit("df_2"))))
        assertTrue(at("df_1").isDefault)
        assertTrue(at("df_2").isDefault)
    }

    @Test
    fun `id mac dinh khong bi nhan ban khi config da co no`() {
        ecmps.setDefaultIds(unit("df_1"))
        feed("a", "df_1", "b")
        assertEquals(1, ids().count { it == unit("df_1") })
    }

    @Test
    fun `id mac dinh khong bi loai khi config moi bo no ra`() {
        ecmps.setDefaultIds(unit("df_1"))
        feed("a", "df_1")
        feed("a", "b")
        assertTrue("lop day khong duoc bien mat", ids().contains(unit("df_1")))
    }

    // ═══ Chọn quảng cáo ═══

    @Test
    fun `getAdLoaded chi tra ve muc vua isLoaded vua co adObject`() {
        feed("a", "b", "c")
        ecmps.list[0].isLoaded = true                    // thiếu adObject
        ecmps.list[1].adObject = Any()                   // thiếu isLoaded
        assertNull("hai trang thai lech nhau khong duoc coi la san sang", ecmps.getAdLoaded())

        ecmps.list[2].isLoaded = true
        ecmps.list[2].adObject = Any()
        assertEquals(unit("c"), ecmps.getAdLoaded()!!.adsId)
    }

    @Test
    fun `getAdLoaded tra ve muc dau tien san sang theo thu tu waterfall`() {
        feed("cao", "vua", "thap")
        for (i in listOf(1, 2)) {
            ecmps.list[i].isLoaded = true; ecmps.list[i].adObject = Any()
        }
        assertEquals("phai lay muc eCPM cao nhat trong so da san sang", unit("vua"), ecmps.getAdLoaded()!!.adsId)
    }

    // ═══ Tuổi tối đa (Task 11: app open hết hạn sau 4 giờ) ═══

    private fun loadNow(name: String): AdsEcmp = at(name).apply { isLoaded = true; adObject = Any() }

    @Test
    fun `moc load lay tu dong ho cua waterfall`() {
        feed("a")
        assertEquals(now, loadNow("a").loadedAtMillis)
    }

    @Test
    fun `quang cao qua tuoi bi bo - bao ly do, huy object - roi tra muc sau con han`() {
        feed("cu", "moi")
        val old = loadNow("cu")
        val staleObject = old.adObject
        now += 2 * 60 * 60 * 1000L
        val fresh = loadNow("moi")
        now = old.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1 // "cu": 4 giờ + 1 ms; "moi": 2 giờ + 1 ms

        ServerEventCapture().use { server ->
            assertSame(fresh, ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS, expiredReason = Constants.AdShowFailReason.APP_OPEN_EXPIRED))
            assertEquals(listOf("full_test" to Constants.AdShowFailReason.APP_OPEN_EXPIRED), server.showFails(1))
        }
        assertFalse("hết hạn = chưa load, để lần load kế tải mới", old.isLoaded)
        assertNull(old.adObject)
        verify(adPl).destroyAd(staleObject)
    }

    /**
     * Task 10 (addendum B6): nhãn hết hạn do placement gọi quyết định. Placement không khai nhãn riêng mà
     * có tuổi tối đa thì nhận nhãn chung `ad_expired`, không mượn nhãn của app open.
     */
    @Test
    fun `khong truyen nhan het han thi dung nhan chung ad_expired, log theo nhan`() {
        feed("a")
        val item = loadNow("a")
        now = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        LogUtil.setFlagLog(3)
        try {
            ServerEventCapture().use { server ->
                mockStatic(Log::class.java).use { log ->
                    assertNull(ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS))
                    log.verify({ Log.d(eq(LogUtil.TAG), contains("ad_expired after")) }, times(1))
                }
                assertEquals(listOf("full_test" to Constants.AdShowFailReason.AD_EXPIRED), server.showFails(1))
            }
        } finally {
            LogUtil.setFlagLog(1)
        }
    }

    @Test
    fun `ranh gioi 4 gio - con 1 ms thi dung duoc, qua 1 ms thi het han`() {
        feed("a")
        val item = loadNow("a")

        now = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS - 1
        assertSame(item, ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS))

        now = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        assertNull(ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS))
        assertFalse(item.isLoaded)
    }

    @Test
    fun `khong truyen tuoi toi da thi khong bao gio het han - dinh dang khac giu hanh vi cu`() {
        feed("a")
        val item = loadNow("a")
        now += 100 * APP_OPEN_MAX_AGE_MS
        assertSame(item, ecmps.getAdLoaded())
        assertTrue(item.isLoaded)
        verify(adPl, never()).destroyAd(any())
    }

    @Test
    fun `gio he thong bi lui sau khi load - tuoi khong biet, coi nhu het han`() {
        feed("a")
        val item = loadNow("a")
        now = item.loadedAtMillis - 1
        assertNull(ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS))
        assertFalse(item.isLoaded)
    }

    @Test
    fun `destroyAd nem loi - quang cao van bi bo va loi duoc log`() {
        feed("a")
        val item = loadNow("a")
        `when`(adPl.destroyAd(any())).thenThrow(IllegalStateException("boom"))
        now = item.loadedAtMillis + APP_OPEN_MAX_AGE_MS + 1
        LogUtil.setFlagLog(3)
        try {
            mockStatic(Log::class.java).use { log ->
                assertNull(ecmps.getAdLoaded(APP_OPEN_MAX_AGE_MS, expiredReason = Constants.AdShowFailReason.APP_OPEN_EXPIRED))
                log.verify({ Log.d(eq(LogUtil.TAG), contains("app_open_expired after")) }, times(1))
                log.verify({ Log.e(eq(LogUtil.TAG), contains("boom")) }, times(1))
            }
        } finally {
            LogUtil.setFlagLog(1)
        }
        assertFalse(item.isLoaded)
        assertNull(item.adObject)
    }

    @Test
    fun `getEcmpForLoad theo chi so hien tai va tra null khi vuot bien`() {
        feed("a", "b", "c")
        assertEquals(unit("a"), ecmps.getEcmpForLoad()!!.adsId)
        ecmps.idxCurrEcpm = 2
        assertEquals(unit("c"), ecmps.getEcmpForLoad()!!.adsId)
        ecmps.idxCurrEcpm = 3
        assertNull("het tang waterfall phai tra null, khong duoc crash", ecmps.getEcmpForLoad())
        ecmps.idxCurrEcpm = -1
        assertNull(ecmps.getEcmpForLoad())
    }

    @Test
    fun `getEcmpWithObjectAd tim dung muc theo doi tuong quang cao`() {
        feed("a", "b", "c")
        val obj = Any()
        ecmps.list[1].adObject = obj
        assertEquals(unit("b"), ecmps.getEcmpWithObjectAd(obj)!!.adsId)
        assertNull(ecmps.getEcmpWithObjectAd(Any()))
        assertNull("null khong duoc khop voi muc chua co adObject", ecmps.getEcmpWithObjectAd(null))
    }

    // ═══ Vòng tròn dữ liệu ═══

    @Test
    fun `listIdsToString khop lai duoc voi listFromData`() {
        feed("a", "b", "c")
        assertEquals(expect("a", "b", "c").joinToString(";"), ecmps.listIdsToString())
    }

    @Test
    fun `danh sach rong cho ra chuoi rong`() {
        assertEquals("", ecmps.listIdsToString())
    }
}
