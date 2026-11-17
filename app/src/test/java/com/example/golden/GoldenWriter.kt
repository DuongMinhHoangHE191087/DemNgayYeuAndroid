package com.example.golden

import java.io.File

/**
 * Ghi và kiểm tra file golden JSON xác định (LF, thụt 2 dấu cách, ASCII thuần).
 * Chế độ VERIFY (mặc định) so với file trên đĩa; chế độ UPDATE (GOLDEN_UPDATE=1) ghi file.
 * Không chuẩn hoá Unicode: NFC và NFD của cùng một chữ ra hai chuỗi khác nhau.
 */
class GoldenWriter(
  private val dir: File = File("../shared/src/commonTest/resources/golden"),
  private val generate: Boolean = System.getenv("GOLDEN_UPDATE") == "1",
) {
  fun check(name: String, value: Map<*, *>) {
    val text = render(value)
    val file = File(dir, name)
    if (generate) {
      dir.mkdirs()
      file.writeBytes(text.toByteArray(Charsets.UTF_8))
      return
    }
    if (!file.isFile) throw AssertionError("Thiếu golden $name tại ${file.path} (chạy với GOLDEN_UPDATE=1)")
    val actual = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    if (actual != text) throw AssertionError("Golden $name lệch (chạy với GOLDEN_UPDATE=1 để cập nhật)")
  }

  private fun render(value: Map<*, *>): String {
    require(!value.containsKey("generatedBy")) { "generatedBy do GoldenWriter cung cấp" }
    val root = LinkedHashMap<Any?, Any?>()
    root["generatedBy"] = "GoldenWriter"
    root.putAll(value)
    return StringBuilder().also { write(it, root, 0) }.append('\n').toString()
  }

  private fun write(sb: StringBuilder, v: Any?, depth: Int) {
    when (v) {
      null -> sb.append("null")
      is String -> quote(sb, v)
      is Long, is Boolean -> sb.append(v)
      is Map<*, *> -> block(sb, '{', '}', v.entries, depth) { e ->
        val k = e.key
        require(k is String) { "Khoá phải là String: $k" }
        quote(sb, k)
        sb.append(": ")
        write(sb, e.value, depth + 1)
      }
      is List<*> -> block(sb, '[', ']', v, depth) { write(sb, it, depth + 1) }
      else -> throw IllegalArgumentException("Kiểu không được hỗ trợ: ${v::class.java.name}")
    }
  }

  private fun <T> block(sb: StringBuilder, open: Char, close: Char, items: Collection<T>, depth: Int, item: (T) -> Unit) {
    if (items.isEmpty()) {
      sb.append(open).append(close)
      return
    }
    sb.append(open).append('\n')
    items.forEachIndexed { i, it ->
      sb.append("  ".repeat(depth + 1))
      item(it)
      sb.append(if (i < items.size - 1) ",\n" else "\n")
    }
    sb.append("  ".repeat(depth)).append(close)
  }

  private fun quote(sb: StringBuilder, s: String) {
    sb.append('"')
    for (c in s) {
      when {
        c == '"' -> sb.append("\\\"")
        c == '\\' -> sb.append("\\\\")
        c.code < 0x20 || c.code > 0x7e -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> sb.append(c)
      }
    }
    sb.append('"')
  }
}
