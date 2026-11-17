package com.example.data.sync

import com.example.data.model.OnlineUserEntity
import com.example.data.repository.OnlineCoupleRepository
import com.example.domain.DataResidencyPolicy
import com.example.ui.util.Birthday
import java.security.MessageDigest

/** Phiên bản schema của doc users/{uid}; rules chỉ nhận đúng 1. */
const val PROFILE_SCHEMA_VERSION = 1L

/** Giới hạn độ dài khớp firestore.rules (test hợp đồng client-contract.test.js so với các số này). */
const val PROFILE_NAME_MAX = 100
const val PROFILE_BIO_MAX = 1000
const val PROFILE_INTERESTS_MAX = 1024
const val PROFILE_AVATAR_MAX = 2048

/** Danh sách trường được phép có trong users/{uid}; khớp userProfileFields() trong firestore.rules. updatedAt do wiring thêm lúc ghi. */
val PROFILE_SYNC_FIELDS = listOf(
  "displayName", "birthDate", "gender", "bio", "interestsCsv", "avatarUrl", "schemaVersion", "updatedAt"
)

private const val WELCOME_BIO_PREFIX = "Chào mừng bạn đến với InLove"
private val GENDERS = setOf("MALE", "FEMALE")
private val HTTPS_URL = Regex("^https://[^ ]+$")

/**
 * Bản chiếu hồ sơ cá nhân lên Firestore users/{uid}: chỉ 5 trường người dùng nhập + avatar (tuỳ cờ).
 * Mọi giá trị đã được làm sạch về trong giới hạn của rules, cả phía đẩy ([of]) lẫn phía kéo ([fromRemote])
 * đi qua CÙNG một bộ làm sạch. Dòng Room không bị cắt; chỉ bản chiếu bị cắt.
 * [avatarUrl] == null nghĩa là KHÔNG đẩy avatar (không có key trong map).
 */
data class ProfileProjection(
  val displayName: String,
  val birthDate: String,
  val gender: String,
  val bio: String,
  val interestsCsv: String,
  val avatarUrl: String?
) {

  /** 5 trường + avatarUrl (chỉ khi khác null) + schemaVersion. KHÔNG có updatedAt: wiring thêm serverTimestamp. */
  fun toRemoteMap(): Map<String, Any?> {
    val m = linkedMapOf<String, Any?>(
      "displayName" to displayName,
      "birthDate" to birthDate,
      "gender" to gender,
      "bio" to bio,
      "interestsCsv" to interestsCsv
    )
    if (avatarUrl != null) m["avatarUrl"] = avatarUrl
    m["schemaVersion"] = PROFILE_SCHEMA_VERSION
    return m
  }

  /** SHA-256 hex (64 ký tự thường) của map đã sắp theo key, mỗi dòng "k=v\n". Không chứa PII khi lưu. */
  fun key(): String {
    val canonical = toRemoteMap().toSortedMap().entries.joinToString("") { "${it.key}=${it.value}\n" }
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }

  /** Kết quả đọc doc từ xa. */
  sealed interface Remote {
    /** Doc không tồn tại. */
    object Absent : Remote

    /** Doc cũ không có schemaVersion, đã đọc ở chế độ dễ dãi. */
    data class Legacy(val p: ProfileProjection) : Remote

    /** schemaVersion khác 1 hoặc doc sai hình (chế độ chặt): không áp dụng. */
    object Invalid : Remote

    /** Doc schema 1 hợp lệ. */
    data class Valid(val p: ProfileProjection) : Remote
  }

  companion object {

    /** Bản chiếu của dòng cục bộ; avatar chỉ có khi [avatarsOnR2] bật và là URL https hợp lệ (hoặc rỗng). */
    fun of(row: OnlineUserEntity, avatarsOnR2: Boolean = DataResidencyPolicy.avatarsOnR2): ProfileProjection =
      sanitised(
        row.displayName, row.birthDate, row.gender, row.bio, row.interestsCsv,
        if (avatarsOnR2) sanitiseAvatar(row.avatarUrl) else null
      )

    /**
     * Hồ sơ cục bộ còn "nguyên sơ": chưa có ngày sinh (giá trị THÔ, nên "5/5/95" không phải nguyên sơ) và bio trống/bio chào mừng.
     * ponytail: chỉ sửa sở thích hoặc tên vẫn tính là nguyên sơ (trần chấp nhận được vì doc cũ rất hiếm); nâng cấp = cờ "đã sửa" trong prefs.
     */
    fun isPristine(row: OnlineUserEntity): Boolean = pristine(row.birthDate, row.bio)

    /** Cùng điều kiện cho phía từ xa, trên dữ liệu đã chiếu. */
    fun isPristine(p: ProfileProjection): Boolean = pristine(p.birthDate, p.bio)

    /**
     * Đọc doc từ xa (ranh giới tin cậy). null -> Absent; không có schemaVersion -> Legacy; schemaVersion khác 1 -> Invalid;
     * schema 1 thiếu/sai kiểu trường chuỗi bắt buộc hoặc gender lạ -> Invalid; còn lại Valid. Avatar bị bỏ qua khi [avatarsOnR2] tắt.
     * ponytail: Legacy dễ dãi (trường thiếu/sai kiểu -> "", giới tính lạ -> MALE) vì không kiểm được bản ngoài lịch sử git; nâng cấp = coi Legacy sai hình là Invalid.
     */
    fun fromRemote(map: Map<String, Any?>?, avatarsOnR2: Boolean = DataResidencyPolicy.avatarsOnR2): Remote {
      if (map == null) return Remote.Absent
      // Firestore trả số nguyên là Long; chấp nhận Int/Double bằng 1 vì rules so `== 1` và test dùng map thô.
      val strict = map.containsKey("schemaVersion")
      if (strict && !isVersionOne(map["schemaVersion"])) return Remote.Invalid
      val name = map["displayName"] as? String
      val birth = map["birthDate"] as? String
      val gender = map["gender"] as? String
      val bio = map["bio"] as? String
      val interests = map["interestsCsv"] as? String
      if (strict && (name == null || birth == null || bio == null || interests == null || gender == null || gender !in GENDERS)) {
        return Remote.Invalid
      }
      val avatar = if (avatarsOnR2) (map["avatarUrl"] as? String)?.let { sanitiseAvatar(it) } else null
      val p = sanitised(name.orEmpty(), birth.orEmpty(), gender.orEmpty(), bio.orEmpty(), interests.orEmpty(), avatar)
      return if (strict) Remote.Valid(p) else Remote.Legacy(p)
    }

    private fun pristine(birthDate: String, bio: String): Boolean =
      birthDate.isBlank() && (bio.isBlank() || bio.startsWith(WELCOME_BIO_PREFIX))

    private fun isVersionOne(v: Any?): Boolean = v is Number && v.toDouble() == PROFILE_SCHEMA_VERSION.toDouble()

    /** Bộ làm sạch DUY NHẤT cho cả đẩy và kéo. Idempotent: làm sạch lại kết quả không đổi gì. */
    private fun sanitised(
      displayName: String,
      birthDate: String,
      gender: String,
      bio: String,
      interestsCsv: String,
      avatarUrl: String?
    ): ProfileProjection = ProfileProjection(
      displayName = cut(displayName.filterNot { it.isISOControl() }.trim(), PROFILE_NAME_MAX).trimEnd(),
      birthDate = Birthday.parse(birthDate)?.text ?: "",
      gender = if (gender in GENDERS) gender else "MALE",
      bio = cut(bio.filter { it == '\n' || !it.isISOControl() }, PROFILE_BIO_MAX),
      interestsCsv = cut(
        interestsCsv.split(",").map { it.trim() }
          .filter { k -> OnlineCoupleRepository.AVAILABLE_INTERESTS.any { it.first == k } }
          .joinToString(","),
        PROFILE_INTERESTS_MAX
      ),
      avatarUrl = avatarUrl
    )

    /** "" khi trống, URL khi là https hợp lệ trong giới hạn, null (không đẩy) khi khác (file:, content:, đường dẫn, http). */
    private fun sanitiseAvatar(raw: String): String? = when {
      raw.isBlank() -> ""
      raw.length <= PROFILE_AVATAR_MAX && HTTPS_URL.matches(raw) -> raw
      else -> null
    }

    /** take(n) nhưng không để lại nửa cặp surrogate ở cuối. */
    private fun cut(s: String, n: Int): String =
      if (s.length <= n) s else if (s[n - 1].isHighSurrogate()) s.take(n - 1) else s.take(n)
  }
}
