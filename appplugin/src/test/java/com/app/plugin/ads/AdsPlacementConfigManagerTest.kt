package com.app.plugin.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parser cấu hình placement — đường đi của mọi thay đổi cấu hình quảng cáo từ xa.
 *
 * Vì sao lớp này đáng test trước những lớp khác trong `ads/`: nó **nuốt lỗi có chủ đích**.
 * JSON hỏng thì `parserConfig` `return` im lặng, và cấu hình mới đơn giản là không được áp.
 * Không crash, không log, không có gì cho biết. Đó là hành vi đúng cho production — nhưng nó
 * cũng có nghĩa là một lỗi parse sẽ không bao giờ tự lộ ra, nên nó phải bị khoá bằng test.
 */
class AdsPlacementConfigManagerTest {

    private fun mgr() = AdsPlacementConfigManager()

    // ═══ getConfig ═══

    @Test
    fun `placement chua biet duoc tao voi flag mac dinh cua caller`() {
        val m = mgr()
        val cf = m.getConfig("full_home", 2)
        assertNotNull(cf)
        assertEquals("full_home", cf!!.placement)
        assertEquals(2, cf.flagShow)
    }

    @Test
    fun `goi lai tra ve DUNG doi tuong cu khong phai ban sao`() {
        // Đây là tính chất mấu chốt: `AdsPlacementConfig` mang state đang chạy (isLoading,
        // isShowing, countshow). Trả về một bản sao mới sẽ khiến trạng thái "đang hiện quảng
        // cáo" biến mất và hai quảng cáo chồng lên nhau.
        val m = mgr()
        val a = m.getConfig("full_home", 2)!!
        a.isShowing = true
        val b = m.getConfig("full_home", 9)!!
        assertEquals("phai la cung mot instance", true, b.isShowing)
        assertEquals("flagDf khong duoc ghi de len config da ton tai", 2, b.flagShow)
    }

    // ═══ parserConfig ═══

    @Test
    fun `ap duoc flag tu json`() {
        val m = mgr()
        m.parserConfig("""{"full_home":{"flag":1}}""")
        assertEquals(1, m.getConfig("full_home", 9)!!.flagShow)
    }

    @Test
    fun `mot khoa co the mang nhieu placement ngan cach bang dau phay`() {
        val m = mgr()
        m.parserConfig("""{"full_home,full_level,full_pause":{"flag":-1}}""")
        for (p in listOf("full_home", "full_level", "full_pause")) {
            assertEquals("$p phai duoc ap", -1, m.getConfig(p, 9)!!.flagShow)
        }
    }

    @Test
    fun `flag dang so thuc van doc duoc`() {
        // Firebase Remote Config trả JSON number, và Gson map sang Double. Không xử lý sẽ
        // ném NumberFormatException trên "1.0" và cả khối cấu hình bị bỏ.
        val m = mgr()
        m.parserConfig("""{"full_home":{"flag":1.0}}""")
        assertEquals(1, m.getConfig("full_home", 9)!!.flagShow)
    }

    @Test
    fun `flag khong doc duoc thi ve 1 chu khong ve 0`() {
        // Quan trọng về chiều an toàn: 0/-1 nghĩa là TẮT quảng cáo. Một giá trị rác không
        // được phép âm thầm tắt doanh thu của studio.
        val m = mgr()
        m.parserConfig("""{"full_home":{"flag":"khong-phai-so"}}""")
        assertEquals(1, m.getConfig("full_home", 9)!!.flagShow)
    }

    // ═══ Hành vi khi dữ liệu hỏng ═══

    @Test
    fun `json hong khong lam vo gi va khong doi cau hinh dang chay`() {
        val m = mgr()
        m.parserConfig("""{"full_home":{"flag":-1}}""")
        m.parserConfig("""{ day khong phai json """)
        assertEquals("cau hinh cu phai duoc giu nguyen", -1, m.getConfig("full_home", 9)!!.flagShow)
    }

    @Test
    fun `chuoi qua ngan bi bo qua`() {
        val m = mgr()
        m.parserConfig("{}")
        assertEquals(9, m.getConfig("full_home", 9)!!.flagShow)
    }

    @Test
    fun `gia tri khong phai object bi bo qua nhung khong chan cac muc khac`() {
        // Một mục hỏng không được làm hỏng cả khối — nếu không, một lỗi gõ nhầm ở placement
        // này sẽ âm thầm huỷ cấu hình của mọi placement còn lại.
        val m = mgr()
        m.parserConfig("""{"full_home":"chuoi-khong-hop-le","full_level":{"flag":-1}}""")
        assertEquals(9, m.getConfig("full_home", 9)!!.flagShow)
        assertEquals(-1, m.getConfig("full_level", 9)!!.flagShow)
    }

    @Test
    fun `khoi thieu flag khong ghi de gia tri dang chay`() {
        val m = mgr()
        m.parserConfig("""{"full_home":{"flag":-1}}""")
        m.parserConfig("""{"full_home":{"khong_co_flag":123}}""")
        assertEquals(-1, m.getConfig("full_home", 9)!!.flagShow)
    }

    @Test
    fun `parser tao ra placement chua tung duoc hoi toi`() {
        val m = mgr()
        m.parserConfig("""{"full_moi_toanh":{"flag":-1}}""")
        assertEquals(-1, m.getConfig("full_moi_toanh", 9)!!.flagShow)
    }
}
