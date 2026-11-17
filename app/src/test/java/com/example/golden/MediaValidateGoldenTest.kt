package com.example.golden

import com.example.domain.media.MediaKind
import com.example.domain.media.formatExtensionOf
import com.example.domain.media.formatMediaSize
import com.example.domain.media.validateMemoryMedia
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GV-027: `validateMemoryMedia` (loi tieng Viet hoac null), `formatExtensionOf`, `formatMediaSize`.
 * `expected` (ke ca thong bao loi) lay tu loi goi that, khong go tay. `accept` o day chi la co viet tay de
 * ghim null / khac null.
 */
class MediaValidateGoldenTest {
  private val goldenName = "GV-027-media-validate.json"
  private val goldenDir = File("../shared/src/commonTest/resources/golden")
  private val mib = 1024L * 1024

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  private class V(val id: String, val kind: MediaKind, val mime: String, val size: Long, val duration: Int, val accept: Boolean)

  private fun img(id: String, mime: String, size: Long, accept: Boolean) = V(id, MediaKind.IMAGE, mime, size, 0, accept)
  private fun vid(id: String, mime: String, size: Long, duration: Int, accept: Boolean) = V(id, MediaKind.VIDEO, mime, size, duration, accept)

  private val validate = listOf(
    img("image-jpeg", "image/jpeg", 500_000, true),
    img("image-jpg", "image/jpg", 500_000, true),
    img("image-png", "image/png", 500_000, true),
    img("image-webp", "image/webp", 500_000, true),
    img("image-heic", "image/heic", 500_000, true),
    img("image-heif", "image/heif", 500_000, true),
    img("mime-uppercase", "IMAGE/JPEG", 500_000, true),
    img("image-gif-rejected", "image/gif", 500_000, false),
    img("image-pdf-rejected", "application/pdf", 500_000, false),
    img("empty-mime", "", 500_000, false),
    img("size-0", "image/jpeg", 0, false),
    img("size-1", "image/jpeg", 1, true),
    img("size-10-mib", "image/jpeg", 10 * mib, true),
    img("size-10-mib-plus-1", "image/jpeg", 10 * mib + 1, false),
    img("size-negative", "image/jpeg", -1, false),
    vid("video-mp4", "video/mp4", mib, 10, true),
    vid("video-mov", "video/quicktime", mib, 10, true),
    vid("video-avi", "video/x-msvideo", mib, 10, false),
    vid("video-mkv", "video/x-matroska", mib, 10, false),
    vid("video-size-0", "video/mp4", 0, 10, false),
    vid("video-size-negative", "video/mp4", -1, 10, false),
    vid("duration-0", "video/mp4", mib, 0, false),
    vid("duration-1", "video/mp4", mib, 1, true),
    vid("duration-60", "video/mp4", mib, 60, true),
    vid("duration-61", "video/mp4", mib, 61, false),
    vid("video-50-mib", "video/mp4", 50 * mib, 10, true),
    vid("video-50-mib-plus-1", "video/mp4", 50 * mib + 1, 10, false),
  )

  private class S(val id: String, val bytes: Long, val text: String?)

  // `text` = gia tri mong doi viet tay neu yeu cau da neu ro; null thi chi ghi nhan ket qua that.
  private val sizeText = listOf(
    S("size-text-1023-bytes", 1023, "1 KB"),
    S("size-text-1048575-bytes", 1_048_575, "1024 KB"),
    S("size-text-1048576", 1_048_576, null),
    S("size-text-half-up-1-25-mib", 1_310_720, "1.3 MB"),
    S("size-text-half-up-2-5-kb", 2_560, "3 KB"),
    S("size-text-0", 0, null),
    S("size-text-negative", -1, null),
  )

  private fun case(id: String, input: Map<String, Any?>, expected: Any?) = obj(
    "id" to id, "input" to input, "expected" to expected, "androidLegacy" to null, "divergence" to null, "optional" to false,
  )

  @Test
  fun mediaValidateGolden() {
    val cases = mutableListOf<Map<String, Any?>>()
    for (v in validate) {
      val error = validateMemoryMedia(v.kind, v.mime, v.size, v.duration)
      if (v.accept) assertNull("${v.id}: phai duoc chap nhan", error) else assertNotNull("${v.id}: phai bi tu choi", error)
      cases += case(
        v.id,
        obj("fn" to "validateMemoryMedia", "kind" to v.kind.name, "mimeType" to v.mime, "sizeBytes" to v.size, "durationSeconds" to v.duration.toLong()),
        obj("error" to error, "extension" to formatExtensionOf(v.mime)),
      )
    }
    for (s in sizeText) {
      val text = formatMediaSize(s.bytes)
      if (s.text != null) assertEquals("${s.id}: viet tay", s.text, text)
      cases += case(s.id, obj("fn" to "formatMediaSize", "sizeBytes" to s.bytes), obj("text" to text))
    }
    // Cac MIME ho tro cho dung duoi tep.
    assertEquals("heic", formatExtensionOf("image/heif"))
    assertNull(formatExtensionOf("application/pdf"))

    val ids = cases.map { it["id"] as String }
    assertEquals(34, cases.size)
    assertEquals(ids.size, ids.toSet().size)

    GoldenWriter().check(goldenName, obj("schema" to 1L, "cases" to cases))
    val file = File(goldenDir, goldenName)
    assertTrue("Thieu golden $goldenName", file.isFile)
    val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
    // Chi lay `id` cap ca: dong thut dung 6 dau cach trong khung cases.
    val committedIds = Regex("(?m)^ {6}\"id\": \"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
    assertEquals(ids, committedIds)
    assertEquals(34, committedIds.size)
    assertEquals(34, Regex("(?m)^ {6}\"divergence\": null").findAll(text).count())
    assertEquals(34, Regex("(?m)^ {6}\"androidLegacy\": null").findAll(text).count())
    assertEquals(34, Regex("(?m)^ {6}\"optional\": false").findAll(text).count())
  }
}
