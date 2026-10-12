package com.app.plugin.consent

/**
 * Bộ đọc bit của chuỗi GPP cho [GppConsent] (1.6.4 Task 2, tách khỏi `GppConsent.kt`): base64url 6 bit/ký tự, số
 * nguyên big-endian, số Fibonacci và header (Type, Version, danh sách id section). Mọi lỗi là `IllegalArgumentException`.
 */
internal object GppBits {
    private const val B64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val HEADER_TYPE = 3
    private const val TYPE_BITS = 6
    private const val VERSION_BITS = 6
    private const val NUM_ENTRIES_BITS = 12
    private const val MAX_FIBONACCI_BITS = 30

    /**
     * Header: Type (= 3), Version, rồi danh sách id section (Fibonacci range, delta so với id trước). [maxIds] = số
     * section chuỗi thực có: range khổng lồ bị chặn trước khi mở rộng (review Task 7 I1: 291 ký tự từng gây OOM).
     */
    internal fun decodeHeader(bits: BooleanArray, maxIds: Int): List<Int> {
        val type = readInt(bits, 0, TYPE_BITS)
        require(type == HEADER_TYPE) { "header type $type, expected $HEADER_TYPE" }
        var pos = TYPE_BITS + VERSION_BITS
        val count = readInt(bits, pos, NUM_ENTRIES_BITS)
        pos += NUM_ENTRIES_BITS
        val ids = mutableListOf<Int>()
        var last = 0
        repeat(count) {
            val isRange = readInt(bits, pos, 1) == 1
            pos += 1
            val (first, afterFirst) = readFibonacci(bits, pos)
            val start = last + first
            pos = afterFirst
            last = if (isRange) {
                val (span, afterSpan) = readFibonacci(bits, pos)
                pos = afterSpan
                start + span
            } else {
                start
            }
            // Mỗi id cần một section trong chuỗi: chặn range khổng lồ (và tràn Int) trước khi mở rộng.
            require(last >= start && last - start + 1 <= maxIds - ids.size) {
                "header lists more sections than the string has ($maxIds)"
            }
            ids += start..last
        }
        return ids
    }

    internal fun toBits(encoded: String): BooleanArray {
        require(encoded.isNotEmpty()) { "empty section" }
        val bits = BooleanArray(encoded.length * 6)
        encoded.forEachIndexed { i, c ->
            val v = B64URL.indexOf(c)
            require(v >= 0) { "character '$c' outside base64url" }
            for (b in 0 until 6) bits[i * 6 + b] = (v shr (5 - b)) and 1 == 1
        }
        return bits
    }

    internal fun readInt(bits: BooleanArray, from: Int, width: Int): Int {
        require(from + width <= bits.size) { "truncated: need bit ${from + width}, have ${bits.size}" }
        var v = 0
        for (i in from until from + width) v = (v shl 1) or (if (bits[i]) 1 else 0)
        return v
    }

    /** Số Fibonacci (bit thấp trước: 1, 2, 3, 5…) kết thúc bằng hai bit `1` liền. Trả (giá trị, vị trí sau). */
    private fun readFibonacci(bits: BooleanArray, from: Int): Pair<Int, Int> {
        var weight = 1
        var next = 2
        var value = 0
        var prevOne = false
        var i = from
        while (true) {
            require(i < bits.size && i - from < MAX_FIBONACCI_BITS) { "bad Fibonacci field at bit $from" }
            val one = bits[i]
            if (one && prevOne) return value to i + 1
            if (one) value += weight
            prevOne = one
            val sum = weight + next
            weight = next
            next = sum
            i++
        }
    }
}
