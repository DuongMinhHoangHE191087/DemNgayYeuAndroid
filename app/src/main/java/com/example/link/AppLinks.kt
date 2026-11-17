package com.example.link

/**
 * Doc/tao deep link. Chi dung Kotlin stdlib (se chuyen sang commonMain), khong URI/android/java.
 * Link den tu nguon khong tin cay: scheme/host khop CHINH XAC (khong phan biet hoa/thuong ASCII).
 */
object AppLinks {
  fun tabLink(tab: DeepLinkTab): String = "inlove://tab/" + tab.name.lowercase()

  fun pairLink(code: String): String = "https://inlove.app/pair?code=$code"

  fun parse(uri: String): DeepLink? {
    // Fragment bo truoc, de '#...' khong anh huong path/query.
    val s = uri.trim().substringBefore('#')
    val schemeEnd = s.indexOf("://")
    if (schemeEnd < 0) return null
    val scheme = s.substring(0, schemeEnd).asciiLower()
    val rest = s.substring(schemeEnd + 3)
    val hierarchy = rest.substringBefore('?')
    val query = rest.substringAfter('?', "")
    val slash = hierarchy.indexOf('/')
    val authority = (if (slash < 0) hierarchy else hierarchy.substring(0, slash)).asciiLower()
    val path = hierarchy.substring(authority.length)
    // path rong -> khong co segment; "/" -> mot segment rong (bi tu choi o ben duoi).
    val segments = if (path.isEmpty()) emptyList() else path.substring(1).removeSuffix("/").split('/')

    return when {
      scheme == "inlove" && authority == "tab" -> {
        val name = segments.singleOrNull() ?: return null
        DeepLinkTab.entries.firstOrNull { it.name.asciiLower() == name.asciiLower() }?.let { DeepLink.Tab(it) }
      }
      scheme == "inlove" && authority == "pair" && segments.size <= 1 -> pair(query, segments.firstOrNull())
      scheme == "https" && authority == "inlove.app" && segments.size in 1..2 && segments[0].asciiLower() == "pair" ->
        pair(query, segments.getOrNull(1))
      else -> null
    }
  }

  // Giong ProfileUtils.extractCoupleCode: query `code=` thang path; khong giai ma %XX; khong fallback "bo ky tu la".
  private fun pair(query: String, pathCode: String?): DeepLink.Pair? {
    val fromQuery = query.split('&', '?')
      .firstNotNullOfOrNull { p -> p.takeIf { it.substringBefore('=').asciiLower() == "code" && '=' in it }?.substringAfter('=')?.leadingCode() }
    val code = fromQuery ?: pathCode?.leadingCode() ?: return null
    return DeepLink.Pair(code.uppercase())
  }

  /** Cac ky tu hop le dau chuoi `[a-zA-Z0-9_-]+`, hoac null neu rong. */
  private fun String.leadingCode(): String? =
    takeWhile { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }.ifEmpty { null }

  // Chi ha chu A-Z; ky tu ngoai ASCII giu nguyen (lowercase() cua JVM va Native khac nhau o U+017F...).
  private fun String.asciiLower(): String = map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")
}
