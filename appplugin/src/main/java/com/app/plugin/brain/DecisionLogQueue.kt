package com.app.plugin.brain

import com.app.plugin.until.LogUtil
import java.io.File

/**
 * Hàng đợi bền vững cho decision log — sống sót qua mất mạng và qua cả việc process bị kill.
 *
 * ═══ VÌ SAO DECISION LOG CẦN, CÒN AD EVENT THÌ KHÔNG ═══
 *
 * `LogEventServer` gửi rồi quên: request hỏng là mất bản ghi. Với event ads, mất vài bản ghi
 * chỉ làm nhiễu thống kê. Với decision log thì khác về **bản chất**: mất bản ghi làm **lệch
 * suy luận nhân quả**, vì các lần mất không ngẫu nhiên — mạng kém tương quan với thiết bị yếu,
 * vùng địa lý, và cả hành vi người chơi. Một OPE tính trên dữ liệu thiếu có hệ thống cho ra
 * kết luận sai mà vẫn trông đầy đủ và tự tin.
 *
 * Yêu cầu khác nhau thì cơ chế khác nhau — đó là lý do brain có hàng đợi riêng thay vì dùng
 * chung đường fire-and-forget với ad event.
 *
 * ═══ THIẾT KẾ: KHÔNG THÊM PHỤ THUỘC ═══
 *
 * Một file append-only, mỗi bản ghi một dòng. Không cần Room/SQLite — thêm dependency vào SDK
 * là quyết định ảnh hưởng mọi app tích hợp (kích thước AAR, xung đột phiên bản), và bài toán
 * này không đòi hỏi truy vấn hay index, chỉ cần ghi nối đuôi và đọc tuần tự.
 *
 * Vòng đời một bản ghi:
 * ```text
 * log()  → ghi ngay xuống đĩa (bền vững)  → thử gửi luôn
 *                                             ├─ thành công → xoá khỏi hàng đợi
 *                                             └─ thất bại   → nằm lại, gửi lại ở lần flush sau
 * app khởi động lại → replay mọi bản ghi còn tồn
 * ```
 *
 * ═══ GIỚI HẠN ĐÃ BIẾT ═══
 *
 * Xoá bản ghi hiện làm bằng cách ghi lại cả file (rewrite). Chấp nhận được ở quy mô hàng nghìn
 * bản ghi; nếu hàng đợi thường xuyên lên tới hàng chục nghìn thì cần đổi sang cấu trúc khác
 * (ví dụ nhiều file segment, xoá theo segment). Bounded ở `maxRecords` để không bao giờ phình
 * vô hạn khi thiết bị offline dài ngày.
 */
internal class DecisionLogQueue(
    private val directory: File,
    /** Trần số bản ghi. Vượt trần thì bỏ bản ghi CŨ NHẤT — dữ liệu mới phản ánh policy hiện tại. */
    internal val maxRecords: Int = 5000
) {

    private val file: File by lazy { File(directory, FILE_NAME) }
    private val lock = Any()

    /** Thêm một bản ghi vào hàng đợi. Trả về false nếu ghi đĩa thất bại. */
    public fun enqueue(record: String): Boolean = synchronized(lock) {
        try {
            if (!directory.exists()) directory.mkdirs()
            // Bản ghi chứa xuống dòng sẽ phá vỡ định dạng một-dòng-một-bản-ghi.
            val safe = record.replace("\n", "\\n").replace("\r", "")
            file.appendText(safe + "\n")
            trimIfNeeded()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Đọc toàn bộ bản ghi đang chờ, theo đúng thứ tự ghi vào. Rỗng khi chưa có bản ghi nào; `null`
     * khi file có mà không đọc được (đã log) — người gọi không được coi đó là hàng đợi rỗng.
     */
    public fun peekAll(): List<String>? = synchronized(lock) { readRecords() }

    /** Số bản ghi đang chờ; `0` khi không đọc được file (lỗi đã log). */
    public fun size(): Int = peekAll()?.size ?: 0

    /**
     * Xoá những bản ghi đã gửi thành công. Trả về số bản ghi còn lại, để người gọi cập nhật bộ
     * đếm mà không phải đọc lại cả file; `-1` khi không đọc được file: khi đó KHÔNG ghi gì — ghi lại
     * từ một lần đọc hỏng là xoá sạch hàng đợi — và log lỗi. Bản ghi đã gửi nằm lại, gửi trùng lần sau.
     * Ghi lỗi thì trả số bản ghi đọc lại được (không đổi), hoặc `-1` khi đọc lại cũng lỗi.
     *
     * So khớp theo nội dung chứ không theo chỉ số: giữa lúc gửi và lúc xoá có thể có bản ghi
     * mới được thêm vào, nên chỉ số sẽ lệch và xoá nhầm bản ghi chưa gửi.
     */
    public fun removeAll(sent: Collection<String>): Int = synchronized(lock) {
        val remaining = peekAll()?.toMutableList()
        if (remaining == null) {
            LogUtil.logE("decision log remove skipped: queue unreadable, ${sent.size} sent record(s) kept")
            return -1
        }
        if (sent.isEmpty()) return remaining.size
        try {
            for (s in sent) remaining.remove(s)   // remove() bỏ đúng MỘT phần tử khớp
            writeAll(remaining)
            remaining.size
        } catch (e: Exception) {
            // Xoá thất bại chỉ dẫn tới gửi trùng, không mất dữ liệu. Trùng còn xử lý được
            // ở phía backend nhờ decision_id; mất thì không.
            LogUtil.logE("decision log remove failed, records kept: ${e.message}")
            // Không dùng size(): nó trả 0 khi đọc lỗi, người gọi sẽ tưởng file đã ngắn đi.
            peekAll()?.size ?: -1
        }
    }

    public fun clear(): Unit = synchronized(lock) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
        }
    }

    private fun trimIfNeeded() {
        val lines = readRecords() ?: return   // không đọc được thì không cắt: thà vượt trần còn hơn mất
        if (lines.size <= maxRecords) return
        writeAll(lines.takeLast(maxRecords))
    }

    /** `null` khi file có mà không đọc được; log lỗi (Điều 7: không hỏng câm). */
    private fun readRecords(): List<String>? = try {
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }
    } catch (e: Exception) {
        LogUtil.logE("decision log read failed: ${e.message}")
        null
    }

    private fun writeAll(lines: List<String>) {
        val tmp = File(directory, "$FILE_NAME.tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n")
        // Ghi ra file tạm rồi đổi tên: nếu process chết giữa chừng thì file gốc vẫn nguyên vẹn,
        // thà gửi trùng còn hơn mất sạch hàng đợi.
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    public companion object {
        private const val FILE_NAME = "brain_decision_queue.log"
    }
}
