package com.app.plugin.iap

/**
 * Quy đổi giá store về USD — **chỉ khi biết chắc tỉ giá**, ngược lại trả `null`.
 *
 * ═══ VÌ SAO TRẢ NULL THAY VÌ ĐOÁN ═══
 *
 * Store trả giá theo tiền tệ địa phương. Nếu SDK cứ nhét thẳng con số đó vào
 * `PlayerState.purchaseValueLifetimeUsd`, một giao dịch 99.000 VND sẽ được ghi là "99000 USD"
 * và mọi thứ đọc trường đó về sau (mô hình LTV, ComfortIndex, báo cáo doanh thu) đều sai một
 * cách im lặng — kiểu sai không bao giờ crash nên không ai phát hiện.
 *
 * Nhúng sẵn bảng tỉ giá vào code cũng không phải lời giải: tỉ giá đổi hằng ngày, app hàng
 * tháng mới release. Vì vậy chỉ `USD = 1.0` là hằng số biên dịch; mọi tỉ giá khác phải được
 * nạp từ xa (policy artifact, khoá `fx_rates`) và có thể cập nhật mà không cần release.
 *
 * ═══ SỰ THẬT VẪN NGUYÊN VẸN Ở EVENT ═══
 *
 * Không quy đổi được **không** làm mất dữ liệu: `logPurchase` luôn gửi `price_micros` +
 * `currency_code` nguyên bản lên server, nơi có tỉ giá thật để quy đổi đúng. Chỗ duy nhất
 * bị ảnh hưởng là tổng USD tích luỹ trên máy, và nó được để trống một cách trung thực thay
 * vì được lấp bằng số bịa.
 *
 * 1.8.0 (D3): `iap_purchase` không còn trường `usd` — USD tính ở server (FxRates phía backend); SDK chỉ dùng lớp này cho tổng USD của brain.
 */
internal object FxRates {

    private const val MICROS = 1_000_000.0

    @Volatile
    private var rates: Map<String, Double> = emptyMap()

    /**
     * Nạp bảng tỉ giá "1 đơn vị tiền tệ = ? USD". Thay thế toàn bộ bảng cũ.
     *
     * Thay thế chứ không gộp: gộp sẽ giữ lại tỉ giá cũ của những mã tiền tệ bị gỡ khỏi
     * artifact mới, và đó chính xác là loại số cũ mà cả cơ chế này sinh ra để tránh.
     */
    public fun setRates(newRates: Map<String, Double>) {
        rates = newRates.entries
            .filter { it.value > 0.0 && it.value.isFinite() }
            .associate { it.key.uppercase() to it.value }
    }

    public fun reset() {
        rates = emptyMap()
    }

    /** Số mã tiền tệ đang biết tỉ giá (không kể USD) — hiển thị trong báo cáo nạp artifact. */
    public fun knownCurrencyCount(): Int = rates.size

    /**
     * @return giá trị USD, hoặc `null` nếu chưa biết tỉ giá của `currencyCode`.
     *   Caller **phải** xử lý `null` bằng cách bỏ qua giá trị, không được coi là 0.0 —
     *   "chưa biết đáng giá bao nhiêu" khác hẳn "đáng giá 0 đồng".
     */
    public fun toUsd(priceMicros: Long, currencyCode: String): Double? {
        if (currencyCode.isBlank()) return null
        val code = currencyCode.uppercase()
        val amount = priceMicros / MICROS
        if (code == "USD") return amount
        val rate = rates[code] ?: return null
        return amount * rate
    }
}
