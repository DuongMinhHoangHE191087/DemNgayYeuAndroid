package com.app.plugin.iap

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Quy đổi ngoại tệ ở client là chỗ rất dễ tạo số sai một cách im lặng. Bộ test này khoá lại
 * đúng một tính chất: **không bao giờ bịa ra con số khi chưa biết tỉ giá.**
 */
class FxRatesTest {

    @Before fun setUp() = FxRates.reset()
    @After fun tearDown() = FxRates.reset()

    @Test
    fun `mac dinh chi biet USD`() {
        // Nhúng sẵn bảng tỉ giá vào code là tự chuốc lấy số cũ: tỉ giá đổi hàng ngày còn
        // app thì hàng tháng mới release. Chỉ USD là hằng số thật (1.0).
        assertEquals(1.0, FxRates.toUsd(1_000_000L, "USD")!!, 1e-9)
        assertNull("chua nap ti gia thi phai tra null, khong duoc doan", FxRates.toUsd(1_000_000L, "EUR"))
    }

    @Test
    fun `quy doi dung khi da nap ti gia`() {
        FxRates.setRates(mapOf("EUR" to 1.08))
        // 9.99 EUR ở micros = 9_990_000
        assertEquals(10.79, FxRates.toUsd(9_990_000L, "EUR")!!, 0.01)
    }

    @Test
    fun `ma tien te khong phan biet hoa thuong`() {
        FxRates.setRates(mapOf("eur" to 1.08))
        assertEquals(FxRates.toUsd(1_000_000L, "EUR"), FxRates.toUsd(1_000_000L, "eur"))
    }

    @Test
    fun `ti gia vo ly bi tu choi thay vi duoc dung`() {
        // Số âm hoặc 0 chỉ có thể là lỗi cấu hình. Nhận vào rồi nhân lên sẽ tạo doanh thu
        // âm trong báo cáo — sai kiểu này rất khó truy ngược.
        FxRates.setRates(mapOf("EUR" to -1.0, "JPY" to 0.0, "GBP" to 1.27))
        assertNull(FxRates.toUsd(1_000_000L, "EUR"))
        assertNull(FxRates.toUsd(1_000_000L, "JPY"))
        assertEquals(1.27, FxRates.toUsd(1_000_000L, "GBP")!!, 1e-9)
    }

    @Test
    fun `khong the ghi de ti gia cua USD`() {
        FxRates.setRates(mapOf("USD" to 25000.0))
        assertEquals("USD luon la 1.0", 1.0, FxRates.toUsd(1_000_000L, "USD")!!, 1e-9)
    }

    @Test
    fun `ma tien te rong tra null`() {
        assertNull(FxRates.toUsd(1_000_000L, ""))
    }

    @Test
    fun `nap bang moi thay the bang cu hoan toan`() {
        FxRates.setRates(mapOf("EUR" to 1.08))
        FxRates.setRates(mapOf("GBP" to 1.27))
        assertNull("ti gia cu phai bien mat, khong duoc tich luy", FxRates.toUsd(1_000_000L, "EUR"))
        assertEquals(1.27, FxRates.toUsd(1_000_000L, "GBP")!!, 1e-9)
    }
}
