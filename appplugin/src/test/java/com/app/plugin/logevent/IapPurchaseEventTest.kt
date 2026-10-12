package com.app.plugin.logevent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Khoá payload của event `iap_purchase` (doc 45 mục E.1, mục F).
 *
 * Trường đáng chú ý nhất là `purchase_token`: biên nhận Play Billing phải chảy trong log
 * pipeline thì backend MỚI có đầu vào để xác thực giao dịch phía server — đóng khoảng trống
 * "quyền lợi tin vào những gì Play trả về trên máy". Test này khoá rằng token có mặt ở giao
 * dịch Play Store và hợp lệ là chuỗi rỗng ở kênh web-shop (nơi không tồn tại token Play).
 */
class IapPurchaseEventTest {

    @Test
    fun `giao dich Play Store mang theo purchase token`() {
        val d = LogEventServer.iapPurchaseEventData(
            productId = "remove_ads",
            orderId = "GPA.1234-5678",
            priceMicros = 1_990_000L,
            currencyCode = "USD",
            quantity = 1,
            channel = "PLAY_STORE",
            isSubscription = false,
            purchaseToken = "opaque.play.token.abc"
        )
        assertEquals("opaque.play.token.abc", d["purchase_token"])
        assertEquals("remove_ads", d["product_id"])
        assertEquals("GPA.1234-5678", d["order_id"])
        assertEquals(1_990_000L, d["price_micros"])
        assertEquals("USD", d["currency_code"])
        assertEquals(1, d["quantity"])
        assertEquals("PLAY_STORE", d["channel"])
        assertEquals(0, d["is_subscription"])
    }

    @Test
    fun `kenh web shop khong co token Play — truong rong chu khong vang mat`() {
        val d = LogEventServer.iapPurchaseEventData(
            productId = "gems_500",
            orderId = "WEB-9981",
            priceMicros = 4_990_000L,
            currencyCode = "USD",
            quantity = 1,
            channel = "WEB_SHOP",
            isSubscription = false,
            purchaseToken = ""
        )
        assertEquals("", d["purchase_token"])
        assertEquals("WEB_SHOP", d["channel"])
    }

    @Test
    fun `subscription danh dau dung`() {
        val d = LogEventServer.iapPurchaseEventData(
            productId = "vip_monthly", orderId = "GPA.sub-1", priceMicros = 4_990_000L,
            currencyCode = "USD", quantity = 1, channel = "PLAY_STORE",
            isSubscription = true, purchaseToken = "tok.sub"
        )
        assertEquals(1, d["is_subscription"])
    }
}
