package com.example.data.sync

import com.example.data.model.OnlineUserEntity
import com.example.data.sync.ProfileProjection.Remote
import com.example.ui.util.Birthday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P10: bản chiếu hồ sơ lên Firestore users/{uid}. Thuần Kotlin, không Android. */
class ProfileProjectionTest {

  private val welcomeSparkle = "Chào mừng bạn đến với InLove ✨"
  private val welcomeHeart = "Chào mừng bạn đến với InLove 💕"
  private val https = "https://graph.facebook.com/123/picture?type=large"

  private fun row(
    displayName: String = "An",
    birthDate: String = "05/05/1995",
    gender: String = "FEMALE",
    bio: String = "Xin chào",
    interestsCsv: String = "coffee,travel",
    avatarUrl: String = ""
  ) = OnlineUserEntity(
    uid = "u1",
    coupleCode = "AAAA-1111",
    displayName = displayName,
    birthDate = birthDate,
    gender = gender,
    bio = bio,
    interestsCsv = interestsCsv,
    avatarUrl = avatarUrl
  )

  private fun remote(
    displayName: Any? = "An",
    birthDate: Any? = "05/05/1995",
    gender: Any? = "FEMALE",
    bio: Any? = "Xin chào",
    interestsCsv: Any? = "coffee,travel",
    schemaVersion: Any? = 1L
  ): Map<String, Any?> = mapOf(
    "displayName" to displayName,
    "birthDate" to birthDate,
    "gender" to gender,
    "bio" to bio,
    "interestsCsv" to interestsCsv,
    "schemaVersion" to schemaVersion
  )

  // ---- hằng và hợp đồng khoá ----

  @Test
  fun constantsMatchRulesLimits() {
    assertEquals(1L, PROFILE_SCHEMA_VERSION)
    assertEquals(100, PROFILE_NAME_MAX)
    assertEquals(1000, PROFILE_BIO_MAX)
    assertEquals(1024, PROFILE_INTERESTS_MAX)
    assertEquals(2048, PROFILE_AVATAR_MAX)
    assertEquals(
      listOf("displayName", "birthDate", "gender", "bio", "interestsCsv", "avatarUrl", "schemaVersion", "updatedAt"),
      PROFILE_SYNC_FIELDS
    )
  }

  @Test
  fun remoteMapKeysAreInsideSyncFieldsAndNeverSensitive() {
    val forbidden = listOf(
      "password", "hash", "salt", "pin", "security", "email", "couple", "partner",
      "relationship", "status", "vip", "uid", "age", "zodiac"
    )
    for (r2 in listOf(false, true)) {
      val keys = ProfileProjection.of(row(avatarUrl = https), r2).toRemoteMap().keys
      assertTrue(PROFILE_SYNC_FIELDS.containsAll(keys))
      assertFalse(keys.contains("updatedAt"))
      for (k in keys) for (f in forbidden) assertFalse("$k contains $f", k.lowercase().contains(f))
    }
    for (k in PROFILE_SYNC_FIELDS) for (f in forbidden) assertFalse("$k contains $f", k.lowercase().contains(f))
  }

  @Test
  fun remoteMapHasSchemaVersionOneAsLong() {
    val m = ProfileProjection.of(row(), false).toRemoteMap()
    assertEquals(1L, m["schemaVersion"])
    assertTrue(m["schemaVersion"] is Long)
    assertEquals("FEMALE", m["gender"])
    assertEquals("05/05/1995", m["birthDate"])
  }

  // ---- of(): làm sạch phía đẩy ----

  @Test
  fun birthdayParseGivesFixedFormat() {
    val text = Birthday.parse("05/05/1995")?.text
    assertTrue(Regex("^[0-9]{2}/[0-9]{2}/[0-9]{4}$").matches(text.orEmpty()))
  }

  @Test
  fun birthDateIsNormalisedOrDropped() {
    assertEquals("05/05/1995", ProfileProjection.of(row(birthDate = "5/5/1995"), false).birthDate)
    assertEquals("", ProfileProjection.of(row(birthDate = "5/5/95"), false).birthDate)
    assertEquals("", ProfileProjection.of(row(birthDate = "rác"), false).birthDate)
    assertEquals("", ProfileProjection.of(row(birthDate = ""), false).birthDate)
  }

  @Test
  fun longBioIsCutToLimit() {
    assertEquals(PROFILE_BIO_MAX, ProfileProjection.of(row(bio = "a".repeat(5000)), false).bio.length)
  }

  @Test
  fun longNameIsCutToLimit() {
    assertEquals(PROFILE_NAME_MAX, ProfileProjection.of(row(displayName = "b".repeat(300)), false).displayName.length)
  }

  @Test
  fun nameIsTrimmedAndControlCharsDropped() {
    assertEquals("An Binh", ProfileProjection.of(row(displayName = "  An\u0007 Binh \n "), false).displayName)
  }

  @Test
  fun cutNeverLeavesLoneHighSurrogate() {
    val p = ProfileProjection.of(row(displayName = "a".repeat(99) + "💕"), false)
    assertEquals(99, p.displayName.length)
  }

  @Test
  fun bioKeepsNewlineButDropsOtherControlChars() {
    assertEquals("a\nbc", ProfileProjection.of(row(bio = "a\nb\u0000c\r\t"), false).bio)
  }

  @Test
  fun unknownGenderBecomesMale() {
    assertEquals("MALE", ProfileProjection.of(row(gender = "X"), false).gender)
    assertEquals("MALE", ProfileProjection.of(row(gender = ""), false).gender)
    assertEquals("MALE", ProfileProjection.of(row(gender = "MALE"), false).gender)
    assertEquals("FEMALE", ProfileProjection.of(row(gender = "FEMALE"), false).gender)
  }

  @Test
  fun unknownInterestsAreFiltered() {
    assertEquals("coffee,music", ProfileProjection.of(row(interestsCsv = "coffee, hack ,music,,zzz"), false).interestsCsv)
    assertEquals("", ProfileProjection.of(row(interestsCsv = "zzz"), false).interestsCsv)
  }

  @Test
  fun roomRowIsNeverChanged() {
    val r = row(displayName = "b".repeat(300), bio = "a".repeat(5000), birthDate = "5/5/95")
    ProfileProjection.of(r, false)
    assertEquals(300, r.displayName.length)
    assertEquals(5000, r.bio.length)
    assertEquals("5/5/95", r.birthDate)
  }

  // ---- avatar ----

  @Test
  fun avatarKeyAbsentWhenFlagOffEvenForHttps() {
    val p = ProfileProjection.of(row(avatarUrl = https), false)
    assertNull(p.avatarUrl)
    assertFalse(p.toRemoteMap().containsKey("avatarUrl"))
  }

  @Test
  fun avatarKeyPresentWhenFlagOnAndHttps() {
    val p = ProfileProjection.of(row(avatarUrl = https), true)
    assertEquals(https, p.avatarUrl)
    assertEquals(https, p.toRemoteMap()["avatarUrl"])
  }

  @Test
  fun blankAvatarIsEmptyStringWhenFlagOn() {
    assertEquals("", ProfileProjection.of(row(avatarUrl = ""), true).avatarUrl)
    assertEquals("", ProfileProjection.of(row(avatarUrl = "  "), true).avatarUrl)
    assertEquals("", ProfileProjection.of(row(avatarUrl = ""), true).toRemoteMap()["avatarUrl"])
  }

  @Test
  fun localAvatarNeverGetsAKey() {
    for (local in listOf("file:///data/user/0/x/avatars/a.jpg", "content://media/1", "/storage/emulated/0/a.jpg", "http://insecure.example/a.jpg", "https://a b")) {
      val p = ProfileProjection.of(row(avatarUrl = local), true)
      assertNull(local, p.avatarUrl)
      assertFalse(local, p.toRemoteMap().containsKey("avatarUrl"))
    }
  }

  @Test
  fun avatarLongerThanLimitGetsNoKey() {
    val ok = "https://x.example/" + "a".repeat(PROFILE_AVATAR_MAX - "https://x.example/".length)
    assertEquals(PROFILE_AVATAR_MAX, ok.length)
    assertEquals(ok, ProfileProjection.of(row(avatarUrl = ok), true).avatarUrl)
    assertNull(ProfileProjection.of(row(avatarUrl = ok + "a"), true).avatarUrl)
  }

  // ---- isPristine ----

  @Test
  fun pristineRowForBothWelcomeBiosAndBlankBio() {
    assertTrue(ProfileProjection.isPristine(row(birthDate = "", bio = welcomeSparkle)))
    assertTrue(ProfileProjection.isPristine(row(birthDate = "", bio = welcomeHeart)))
    assertTrue(ProfileProjection.isPristine(row(birthDate = "", bio = "")))
    assertTrue(ProfileProjection.isPristine(row(birthDate = " ", bio = "  ")))
  }

  @Test
  fun notPristineWhenBirthDateOrCustomBio() {
    assertFalse(ProfileProjection.isPristine(row(birthDate = "05/05/1995", bio = "")))
    assertFalse(ProfileProjection.isPristine(row(birthDate = "05/05/1995", bio = welcomeHeart)))
    assertFalse(ProfileProjection.isPristine(row(birthDate = "", bio = "Xin chào")))
  }

  @Test
  fun rawBirthDateCountsAsNotPristineEvenWhenProjectionIsBlank() {
    val r = row(birthDate = "5/5/95", bio = welcomeSparkle)
    assertEquals("", ProfileProjection.of(r, false).birthDate)
    assertFalse(ProfileProjection.isPristine(r))
    assertTrue(ProfileProjection.isPristine(ProfileProjection.of(r, false)))
  }

  @Test
  fun pristineOverloadOnProjectionMatchesRowCases() {
    fun p(birthDate: String, bio: String) = ProfileProjection.of(row(birthDate = birthDate, bio = bio), false)
    assertTrue(ProfileProjection.isPristine(p("", welcomeSparkle)))
    assertTrue(ProfileProjection.isPristine(p("", welcomeHeart)))
    assertTrue(ProfileProjection.isPristine(p("", "")))
    assertFalse(ProfileProjection.isPristine(p("05/05/1995", "")))
    assertFalse(ProfileProjection.isPristine(p("05/05/1995", welcomeHeart)))
    assertFalse(ProfileProjection.isPristine(p("", "Xin chào")))
  }

  // ---- key ----

  @Test
  fun keyIsStable64LowercaseHex() {
    val k = ProfileProjection.of(row(), false).key()
    assertEquals(k, ProfileProjection.of(row(), false).key())
    assertTrue(Regex("^[0-9a-f]{64}$").matches(k))
  }

  @Test
  fun keyChangesWhenAnyFieldChanges() {
    val base = ProfileProjection.of(row(avatarUrl = https), true)
    val keys = listOf(
      base.copy(displayName = "Khác"),
      base.copy(birthDate = ""),
      base.copy(gender = "MALE"),
      base.copy(bio = "Khác"),
      base.copy(interestsCsv = "music"),
      base.copy(avatarUrl = ""),
      base.copy(avatarUrl = null)
    ).map { it.key() }
    for (k in keys) assertNotEquals(base.key(), k)
    assertEquals(keys.size, keys.toSet().size)
  }

  // ---- fromRemote ----

  @Test
  fun nullMapIsAbsent() {
    assertEquals(Remote.Absent, ProfileProjection.fromRemote(null, true))
  }

  @Test
  fun roundTripIsValid() {
    for (r2 in listOf(false, true)) {
      for (r in listOf(row(), row(avatarUrl = https), row(birthDate = "5/5/95", bio = "a".repeat(5000), gender = "X"), row(avatarUrl = "file:///a.jpg"))) {
        val p = ProfileProjection.of(r, r2)
        assertEquals(Remote.Valid(p), ProfileProjection.fromRemote(p.toRemoteMap(), r2))
      }
    }
  }

  @Test
  fun roundTripKeepsAvatarWhenFlagOn() {
    val p = ProfileProjection.of(row(avatarUrl = https), true)
    assertEquals(Remote.Valid(p), ProfileProjection.fromRemote(p.toRemoteMap(), true))
    assertEquals(https, p.avatarUrl)
  }

  @Test
  fun missingSchemaVersionIsLegacyAndLenient() {
    val m = mapOf<String, Any?>(
      "displayName" to "An",
      "birthDate" to 123L,
      "gender" to "ALIEN",
      "interestsCsv" to "coffee,zzz"
    )
    val r = ProfileProjection.fromRemote(m, false)
    assertTrue(r is Remote.Legacy)
    val p = (r as Remote.Legacy).p
    assertEquals("An", p.displayName)
    assertEquals("", p.birthDate)
    assertEquals("MALE", p.gender)
    assertEquals("", p.bio)
    assertEquals("coffee", p.interestsCsv)
    assertNull(p.avatarUrl)
  }

  @Test
  fun emptyMapIsLegacyWithEmptyFields() {
    val r = ProfileProjection.fromRemote(emptyMap(), false)
    assertEquals(Remote.Legacy(ProfileProjection("", "", "MALE", "", "", null)), r)
  }

  @Test
  fun schemaVersionNotOneIsInvalid() {
    for (v in listOf<Any?>(2L, 0L, 1.5, "1", true, null, Long.MAX_VALUE)) {
      assertEquals("schemaVersion=$v", Remote.Invalid, ProfileProjection.fromRemote(remote(schemaVersion = v), true))
    }
  }

  @Test
  fun schemaVersionOneAsAnyNumberTypeIsValid() {
    for (v in listOf<Any>(1L, 1, 1.0)) {
      assertTrue("schemaVersion=$v", ProfileProjection.fromRemote(remote(schemaVersion = v), true) is Remote.Valid)
    }
  }

  @Test
  fun strictModeRejectsMissingOrWrongTypedStringFields() {
    for (field in listOf("displayName", "birthDate", "gender", "bio", "interestsCsv")) {
      val missing = remote().toMutableMap().apply { remove(field) }
      assertEquals("missing $field", Remote.Invalid, ProfileProjection.fromRemote(missing, true))
      val wrong = remote().toMutableMap().apply { put(field, 5L) }
      assertEquals("wrong $field", Remote.Invalid, ProfileProjection.fromRemote(wrong, true))
      val nul = remote().toMutableMap().apply { put(field, null) }
      assertEquals("null $field", Remote.Invalid, ProfileProjection.fromRemote(nul, true))
    }
  }

  @Test
  fun strictModeRejectsUnknownGender() {
    assertEquals(Remote.Invalid, ProfileProjection.fromRemote(remote(gender = "X"), true))
    assertEquals(Remote.Invalid, ProfileProjection.fromRemote(remote(gender = "female"), true))
  }

  @Test
  fun validGoesThroughSameSanitiserAsOf() {
    val r = ProfileProjection.fromRemote(
      remote(displayName = "  " + "n".repeat(300), birthDate = "5/5/95", bio = "b".repeat(5000), interestsCsv = "music,zzz"),
      false
    )
    assertTrue(r is Remote.Valid)
    val p = (r as Remote.Valid).p
    assertEquals(PROFILE_NAME_MAX, p.displayName.length)
    assertEquals("", p.birthDate)
    assertEquals(PROFILE_BIO_MAX, p.bio.length)
    assertEquals("music", p.interestsCsv)
  }

  @Test
  fun avatarIgnoredWhenFlagOffAndKeptWhenOn() {
    val withAvatar = remote().toMutableMap().apply { put("avatarUrl", https) }
    assertNull((ProfileProjection.fromRemote(withAvatar, false) as Remote.Valid).p.avatarUrl)
    assertEquals(https, (ProfileProjection.fromRemote(withAvatar, true) as Remote.Valid).p.avatarUrl)
    val blank = remote().toMutableMap().apply { put("avatarUrl", "") }
    assertEquals("", (ProfileProjection.fromRemote(blank, true) as Remote.Valid).p.avatarUrl)
  }

  @Test
  fun badRemoteAvatarIsIgnoredNotInvalid() {
    for (bad in listOf<Any?>("file:///a.jpg", "http://a.example/x", "https://a b", 7L, null)) {
      val m = remote().toMutableMap().apply { put("avatarUrl", bad) }
      val r = ProfileProjection.fromRemote(m, true)
      assertTrue("avatar=$bad", r is Remote.Valid)
      assertNull("avatar=$bad", (r as Remote.Valid).p.avatarUrl)
    }
  }
}
