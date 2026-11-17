package com.example.golden

import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.sync.MemorySyncAdapter
import com.example.data.sync.SyncWorker
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-36: GV-201 (payload outbox do Moshi cu tao) va GV-202 (an toan URL cua OkHttp cu), sinh tu hanh vi Android HIEN TAI.
 *
 * Kieu payload `InLoveRepository` dua vao bang outbox (`rg -n -e Moshi -e moshi app/src/main/java/com/example/data`):
 *  - InLoveRepository.kt:323 `outboxEntryFor` memory: `SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(payload)`
 *    (UPSERT la entity day du; DELETE la tombstone da `stripForTombstone`);
 *  - InLoveRepository.kt:338 `outboxEntryFor` anniversary: `moshiAdapterFor<AnniversaryDateEntity>().toJson(item)`;
 *  - SyncWorker.kt:103 `moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()` (doc lai o SyncWorker.kt:78, :86).
 * Test dung instance Moshi RIENG dung y het ban san pham (khong dung `OutboxJson` cua D-12), nen D-12 chung minh
 * payload cu van doc duoc. Ngoai hai entity tren khong co kieu payload outbox nao khac.
 *
 * GV-202: `androidLegacy` lay tu `LegacyUrlOracle` NHUNG trong file nay (ban sao nguyen van quy tac
 * `MemorySyncAdapter.safeSharedPhotoUri`), vi D-20c doi `MemorySyncAdapter` sang `UrlSafety` con oracle phai dung yen.
 * `expected` la bang viet tay (hop dong cua `UrlSafety`, D-20c). D-20c xoa `urlGolden_embeddedOracleEqualsLegacyAdapter`.
 * Chu Viet, emoji, chu Kelvin va chu rong toan phan deu viet bang escape Unicode.
 */
class OutboxGoldenTest {
  private val zone = "Asia/Ho_Chi_Minh"
  private val outboxGoldenName = "GV-201-outbox-payloads.json"
  private val urlGoldenName = "GV-202-url-safety.json"

  private fun obj(vararg pairs: Pair<String, Any?>): LinkedHashMap<String, Any?> = linkedMapOf(*pairs)

  // ===== GV-201 =====
  // Dung y het SyncWorker.moshi (SyncWorker.kt dong 103). Hai entity co @JsonClass(generateAdapter = true) nen Moshi dung adapter sinh san;
  // KotlinJsonAdapterFactory chi la du phong (bo no khong doi payload hien nay). Test `embeddedMoshiEqualsProductionAdapter` canh gac phan con lai.
  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
  private val memoryAdapter = moshi.adapter(SharedMemoryEntity::class.java)
  private val anniversaryAdapter = moshi.adapter(AnniversaryDateEntity::class.java)

  private val emoji = "\uD83D\uDC96"
  private val anniversaryTitleDefault = "K\u1EF7 Ni\u1EC7m Ng\u00E0y Y\u00EAu"
  private val tripTitle = "Chuy\u1EBFn \u0111i \u0110\u00E0 L\u1EA1t"
  private val tripNote = "H\u00F4m nay tr\u1EDDi \u0111\u1EB9p, anh nh\u1EDB em nhi\u1EC1u l\u1EAFm"
  // "B\u1EA5t k\u1EF3 d\u1ECBp n\u00E0o" o dang NFD (dau tach roi); Moshi khong chuan hoa Unicode nen phai giu nguyen.
  private val nfdTitle = "Ba\u0302\u0301t ky\u0300 di\u0323p na\u0300o"
  private val specialChars = "quote\" back\\slash\nnewline\ttab </script> & ' \u2028\u2029 end"
  private val twoPow53Plus1 = 9_007_199_254_740_993L

  private val baseMemory = SharedMemoryEntity(
    id = 7L,
    title = "Chuyen di Da Lat",
    dateText = "2024-02-14",
    note = "ghi chu",
    photoUri = "https://img.example/p.jpg",
    location = "Da Lat",
    isFavorite = true,
    anniversaryTitle = "Ky niem",
    createdAt = 1_700_000_000_000L,
    relationshipId = "rel-1",
    authorId = "uid-1",
    authorName = "An",
    mediaType = "IMAGE",
    videoUri = null,
    cloudinaryPublicId = null,
    cloudinaryUrl = null,
    isCloudinaryStored = true,
    fileSizeFormatted = "1.2 MB",
    durationSeconds = 0,
    privacyLevel = "COUPLE_ONLY",
    syncId = "mem-001",
    updatedAt = 1_700_000_100_000L,
    deleted = false,
    pendingSync = true,
  )

  private val baseAnniversary = AnniversaryDateEntity(
    id = 3L,
    title = "Ky niem 1 nam",
    dateText = "2023-05-20",
    type = "LOVE",
    description = "mo ta",
    isAnnual = true,
    notificationEnabled = true,
    reminderDaysBefore = 3,
    daysRemaining = 12,
    createdAt = 1_700_000_000_000L,
    relationshipId = "rel-1",
    syncId = "ann-001",
    updatedAt = 1_700_000_100_000L,
    deleted = false,
    pendingSync = true,
  )

  // Ban sao `InLoveRepository.stripForTombstone` (private): tombstone chi mang khoa, khong mang noi dung.
  private fun SharedMemoryEntity.tombstone(): SharedMemoryEntity = copy(
    title = "", dateText = "", photoUri = "", note = "", location = "", anniversaryTitle = "",
    authorName = "", fileSizeFormatted = "", videoUri = null, cloudinaryPublicId = null, cloudinaryUrl = null,
    isFavorite = false, durationSeconds = 0, deleted = true,
  )

  private class OutboxCase(val id: String, val entity: Any)

  private val outboxCases: List<OutboxCase> = listOf(
    OutboxCase(
      "memory-all-fields-populated",
      baseMemory.copy(
        title = tripTitle,
        note = tripNote,
        location = "\u0110\u00E0 L\u1EA1t",
        anniversaryTitle = anniversaryTitleDefault,
        authorName = "B\u1EA1n",
        mediaType = "VIDEO",
        videoUri = "https://img.example/v.mp4",
        cloudinaryPublicId = "inlove_mem_mem-001",
        cloudinaryUrl = "https://res.cloudinary.com/demo/video/upload/v.mp4",
        durationSeconds = 3600,
      ),
    ),
    OutboxCase("memory-null-fields-omitted", baseMemory.copy(relationshipId = null)),
    OutboxCase("memory-tombstone-stripped", baseMemory.copy(cloudinaryPublicId = "inlove_mem_mem-001").tombstone()),
    OutboxCase(
      "memory-longs-above-2-pow-53",
      baseMemory.copy(id = twoPow53Plus1, createdAt = twoPow53Plus1, updatedAt = Long.MAX_VALUE),
    ),
    OutboxCase("memory-vietnamese-nfc", baseMemory.copy(title = tripTitle, note = tripNote)),
    OutboxCase("memory-vietnamese-nfd-not-normalized", baseMemory.copy(title = nfdTitle, note = "$nfdTitle $emoji")),
    OutboxCase("memory-emoji-surrogate-pair", baseMemory.copy(title = "Yeu $emoji", note = "$emoji$emoji")),
    OutboxCase("memory-json-special-characters", baseMemory.copy(note = specialChars)),
    OutboxCase(
      "memory-false-booleans-and-defaults-kept",
      baseMemory.copy(isFavorite = false, isCloudinaryStored = false, pendingSync = false, deleted = false),
    ),
    OutboxCase(
      "memory-empty-strings-kept",
      baseMemory.copy(title = "", dateText = "", note = "", photoUri = "", location = "", authorId = "", fileSizeFormatted = ""),
    ),
    OutboxCase(
      "anniversary-all-fields-populated",
      baseAnniversary.copy(
        title = "K\u1EF7 ni\u1EC7m 1 n\u0103m y\u00EAu nhau",
        type = "WEDDING",
        description = "M\u00F4 t\u1EA3 \u0111\u1EA7y \u0111\u1EE7",
        reminderDaysBefore = 7,
      ),
    ),
    OutboxCase("anniversary-null-relationship-omitted", baseAnniversary.copy(relationshipId = null)),
    OutboxCase("anniversary-tombstone-deleted", baseAnniversary.copy(deleted = true)),
    OutboxCase(
      "anniversary-longs-above-2-pow-53",
      baseAnniversary.copy(id = twoPow53Plus1, createdAt = twoPow53Plus1, updatedAt = Long.MAX_VALUE),
    ),
    OutboxCase("anniversary-vietnamese-and-emoji", baseAnniversary.copy(title = "Sinh nh\u1EADt $emoji", description = nfdTitle)),
    OutboxCase(
      "anniversary-int-extremes",
      baseAnniversary.copy(reminderDaysBefore = Int.MAX_VALUE, daysRemaining = Int.MIN_VALUE),
    ),
  )

  private fun typeName(e: Any): String = when (e) {
    is SharedMemoryEntity -> "SharedMemoryEntity"
    is AnniversaryDateEntity -> "AnniversaryDateEntity"
    else -> throw AssertionError("Kieu payload khong ho tro: ${e::class.java.name}")
  }

  private fun legacyJson(e: Any): String = when (e) {
    is SharedMemoryEntity -> memoryAdapter.toJson(e)
    is AnniversaryDateEntity -> anniversaryAdapter.toJson(e)
    else -> throw AssertionError("Kieu payload khong ho tro: ${e::class.java.name}")
  }

  // Toan bo thuoc tinh cua constructor; Int doi sang Long vi GoldenWriter khong nhan Int.
  private fun fieldsOf(e: Any): Map<String, Any?> = when (e) {
    is SharedMemoryEntity -> obj(
      "id" to e.id,
      "title" to e.title,
      "dateText" to e.dateText,
      "note" to e.note,
      "photoUri" to e.photoUri,
      "location" to e.location,
      "isFavorite" to e.isFavorite,
      "anniversaryTitle" to e.anniversaryTitle,
      "createdAt" to e.createdAt,
      "relationshipId" to e.relationshipId,
      "authorId" to e.authorId,
      "authorName" to e.authorName,
      "mediaType" to e.mediaType,
      "videoUri" to e.videoUri,
      "cloudinaryPublicId" to e.cloudinaryPublicId,
      "cloudinaryUrl" to e.cloudinaryUrl,
      "isCloudinaryStored" to e.isCloudinaryStored,
      "fileSizeFormatted" to e.fileSizeFormatted,
      "durationSeconds" to e.durationSeconds.toLong(),
      "privacyLevel" to e.privacyLevel,
      "syncId" to e.syncId,
      "updatedAt" to e.updatedAt,
      "deleted" to e.deleted,
      "pendingSync" to e.pendingSync,
    )
    is AnniversaryDateEntity -> obj(
      "id" to e.id,
      "title" to e.title,
      "dateText" to e.dateText,
      "type" to e.type,
      "description" to e.description,
      "isAnnual" to e.isAnnual,
      "notificationEnabled" to e.notificationEnabled,
      "reminderDaysBefore" to e.reminderDaysBefore.toLong(),
      "daysRemaining" to e.daysRemaining.toLong(),
      "createdAt" to e.createdAt,
      "relationshipId" to e.relationshipId,
      "syncId" to e.syncId,
      "updatedAt" to e.updatedAt,
      "deleted" to e.deleted,
      "pendingSync" to e.pendingSync,
    )
    else -> throw AssertionError("Kieu payload khong ho tro: ${e::class.java.name}")
  }

  private fun outboxCaseMaps(): List<Map<String, Any?>> = outboxCases.map { c ->
    val json = legacyJson(c.entity)
    obj(
      "id" to c.id,
      "input" to obj("type" to typeName(c.entity), "fields" to fieldsOf(c.entity)),
      "expected" to json,
      "androidLegacy" to json,
      "divergence" to null,
      "optional" to false,
    )
  }

  private fun outboxRoot(): Map<String, Any?> = obj("schema" to 1L, "zone" to zone, "cases" to outboxCaseMaps())

  // Ghi vao thu muc tam (khong dung GOLDEN_UPDATE) de kiem tra dinh dang ma khong phu thuoc file da commit.
  private fun renderToText(root: Map<String, Any?>): String {
    val dir = Files.createTempDirectory("golden-d36").toFile()
    try {
      GoldenWriter(dir, generate = true).check("tmp.json", root)
      return File(dir, "tmp.json").readText(Charsets.UTF_8)
    } finally {
      dir.deleteRecursively()
    }
  }

  // Ngoai chuoi, mot so JSON khong duoc co dau cham, e hay E (khong Float/Double).
  private fun assertNoFloats(label: String, text: String) {
    var inString = false
    var escaped = false
    val token = StringBuilder()
    fun flush() {
      if (token.any { it == '.' || it == 'e' || it == 'E' }) throw AssertionError("$label: so thuc `$token`")
      token.setLength(0)
    }
    for (ch in text) {
      if (inString) {
        if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') inString = false
        continue
      }
      when {
        ch == '"' -> { flush(); inString = true }
        ch.isDigit() || ch == '-' || (token.isNotEmpty() && (ch == '.' || ch == '+' || ch == 'e' || ch == 'E')) -> token.append(ch)
        else -> flush()
      }
    }
    flush()
  }

  @Test
  fun outboxGolden_matchesCommitted() {
    GoldenWriter().check(outboxGoldenName, outboxRoot())
  }

  @Test
  fun outboxGolden_hasNoFloatsAndHasGeneratedBy() {
    val text = renderToText(outboxRoot())
    assertTrue(text.startsWith("{\n  \"generatedBy\": \"GoldenWriter\",\n  \"schema\": 1,\n  \"zone\": \"Asia/Ho_Chi_Minh\",\n"))
    assertTrue("file phai la ASCII thuan", text.all { it.code < 0x80 })
    assertNoFloats("GV-201 golden", text)
    for (c in outboxCaseMaps()) assertNoFloats("GV-201 ${c["id"]}", c["expected"] as String)
    val ids = outboxCases.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    assertTrue("can ca memory va anniversary", outboxCases.any { it.entity is SharedMemoryEntity } && outboxCases.any { it.entity is AnniversaryDateEntity })
    for (c in outboxCaseMaps()) {
      assertEquals("${c["id"]}: androidLegacy phai bang expected", c["expected"], c["androidLegacy"])
      assertEquals(null, c["divergence"])
      assertEquals(false, c["optional"])
    }
  }

  @Test
  fun outboxGolden_nullFieldsAreOmittedAndLongsKeepPrecision() {
    // Neu 2^53+1 doi qua Double thi mat do chinh xac: ca nay chi co y nghia khi so lon hon 2^53.
    assertTrue(twoPow53Plus1.toDouble().toLong() != twoPow53Plus1)
    val byId = outboxCases.associate { it.id to legacyJson(it.entity) }

    // Moshi bo gia tri null (khong serializeNulls), cho moi ca.
    for ((id, json) in byId) assertFalse("$id: payload khong duoc co :null", json.contains(":null"))
    val nulls = byId.getValue("memory-null-fields-omitted")
    for (key in listOf("relationshipId", "videoUri", "cloudinaryPublicId", "cloudinaryUrl")) {
      assertFalse("khoa null $key phai bi bo", nulls.contains("\"$key\""))
    }
    assertTrue(nulls.contains("\"title\":"))
    assertFalse(byId.getValue("anniversary-null-relationship-omitted").contains("\"relationshipId\""))

    // Long > 2^53 giu nguyen tung chu so.
    for (id in listOf("memory-longs-above-2-pow-53", "anniversary-longs-above-2-pow-53")) {
      val json = byId.getValue(id)
      assertTrue("$id: thieu 9007199254740993", json.contains("\"id\":9007199254740993"))
      assertTrue(json.contains("\"createdAt\":9007199254740993"))
      assertTrue(json.contains("\"updatedAt\":9223372036854775807"))
      assertFalse(json.contains("9007199254740992"))
    }

    // Tombstone chi mang khoa; false van duoc ghi.
    val tomb = byId.getValue("memory-tombstone-stripped")
    assertTrue(tomb.contains("\"title\":\"\"") && tomb.contains("\"deleted\":true") && tomb.contains("\"isFavorite\":false"))
    assertTrue(byId.getValue("memory-false-booleans-and-defaults-kept").contains("\"pendingSync\":false"))

    // Int cuc tri van la so nguyen.
    val extremes = byId.getValue("anniversary-int-extremes")
    assertTrue(extremes.contains("\"reminderDaysBefore\":2147483647") && extremes.contains("\"daysRemaining\":-2147483648"))

    // Chuoi NFD that su la NFD va Moshi khong chuan hoa no; ky tu dac biet duoc escape de doc lai duoc.
    assertTrue(nfdTitle != nfc(nfdTitle) && nfdTitle.length > nfc(nfdTitle).length)
    val nfdJson = byId.getValue("memory-vietnamese-nfd-not-normalized")
    assertTrue(nfdJson.contains(nfdTitle) && !nfdJson.contains(nfc(nfdTitle)))
    assertTrue(byId.getValue("memory-json-special-characters").contains("\\\"") )
    assertTrue(byId.getValue("memory-emoji-surrogate-pair").contains(emoji))

    // Moi payload cu doc lai dung entity (SyncWorker.kt:78, :86).
    for (c in outboxCases) {
      val json = legacyJson(c.entity)
      val back: Any? = when (c.entity) {
        is SharedMemoryEntity -> memoryAdapter.fromJson(json)
        else -> anniversaryAdapter.fromJson(json)
      }
      assertEquals("${c.id}: doc lai", c.entity, back)
    }
  }

  @Test
  fun outboxGolden_embeddedMoshiEqualsProductionAdapter() {
    // Instance Moshi cua test phai cho cung payload voi `SyncWorker.moshiAdapterFor` that (ca ghi lan doc).
    // D-12 doi `OutboxJson` thi xoa hoac sua test nay.
    for (c in outboxCases) {
      val production = when (c.entity) {
        is SharedMemoryEntity -> SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(c.entity)
        is AnniversaryDateEntity -> SyncWorker.moshiAdapterFor<AnniversaryDateEntity>().toJson(c.entity)
        else -> throw AssertionError("Kieu payload khong ho tro: ${c.entity::class.java.name}")
      }
      assertEquals("${c.id}: Moshi cua test lech SyncWorker.moshi", production, legacyJson(c.entity))
    }
  }

  // ===== GV-202 =====
  // Ban sao NGUYEN VAN quy tac cua `MemorySyncAdapter.safeSharedPhotoUri` (MemorySyncAdapter.kt dong 69-74) va
  // `httpsHostOf` (FirebaseMemoryMediaRepository.kt dong 140): OkHttp `toHttpUrlOrNull`.
  private object LegacyUrlOracle {
    fun safeSharedPhotoUri(raw: String?): String {
      if (raw.isNullOrBlank()) return ""
      val url = raw.toHttpUrlOrNull() ?: return ""
      val cloudinary = url.host == "cloudinary.com" || url.host.endsWith(".cloudinary.com")
      return if (url.isHttps && !cloudinary) raw else ""
    }

    // Host sau chuan hoa cua OkHttp (http hoac https), hoac null.
    fun host(raw: String): String? = raw.toHttpUrlOrNull()?.host

    fun exported(raw: String): Boolean = safeSharedPhotoUri(raw).isNotEmpty()
  }

  private class UrlCase(
    val id: String,
    val input: String,
    val host: String?,
    val exported: Boolean,
    val divergence: String? = null,
  )

  private val userinfo = "url-userinfo-dropped"
  private val idn = "url-idn-host-dropped"
  private val odd = "url-odd-host-dropped"
  private val divergenceNames = setOf(userinfo, idn, odd)

  private val kelvinHost = "https://\u212Aelvin.example.com/x"
  private val fullwidthHost = "https://res.\uFF43loudinary.com/x"

  // `expected` = hop dong cua `UrlSafety` (D-20c). Bang `androidLegacy` tru cac ca co `divergence`.
  private val urlCases: List<UrlCase> = listOf(
    UrlCase("https-basic-ok", "https://a.b/c?d=e", "a.b", true),
    UrlCase("http-uppercase-scheme-and-host", "HTTP://EXAMPLE.com", "example.com", false),
    UrlCase("javascript-scheme", "javascript:alert(1)", null, false),
    UrlCase("file-scheme", "file:///x", null, false),
    UrlCase("content-scheme", "content://x", null, false),
    UrlCase("userinfo-in-authority", "https://user:pw@host/", null, false, userinfo),
    UrlCase("port-zero", "https://host:0/", null, false),
    UrlCase("port-65536", "https://host:65536/", null, false),
    UrlCase("trailing-dot-host", "https://host./", null, false, odd),
    UrlCase("triple-slash-no-host", "https:///path", null, false, odd),
    UrlCase("ipv6-literal-host", "https://[::1]/", null, false, odd),
    UrlCase("scheme-relative", "//cdn.example.com/x", null, false),
    UrlCase("empty-string", "", null, false),
    UrlCase("ftp-scheme", "ftp://x", null, false),
    UrlCase("cloudinary-res-host", "https://res.cloudinary.com/x/y.jpg", "res.cloudinary.com", false),
    UrlCase("cloudinary-uppercase-host", "https://RES.CLOUDINARY.COM/x", "res.cloudinary.com", false),
    UrlCase("cloudinary-trailing-dot", "https://res.cloudinary.com./x", null, false, odd),
    UrlCase("cloudinary-backslash-userinfo-trick", "https://res.cloudinary.com\\@evil.com/x", "res.cloudinary.com", false),
    UrlCase("cloudinary-percent-encoded-dot", "https://res.cloudinary.com%2eevil.com/x", null, false, odd),
    UrlCase("evilcloudinary-suffix-lookalike", "https://evilcloudinary.com/x", "evilcloudinary.com", true),
    UrlCase("cloudinary-as-subdomain-of-evil", "https://cloudinary.com.evil.com/x", "cloudinary.com.evil.com", true),
    UrlCase("cloudinary-port-443", "https://res.cloudinary.com:443/x", "res.cloudinary.com", false),
    UrlCase("cloudinary-port-99999", "https://res.cloudinary.com:99999/x", null, false),
    UrlCase("punycode-lookalike-host", "https://xn--clodinary-9ya.com/x", "xn--clodinary-9ya.com", true),
    UrlCase("cloudinary-apex-host", "https://cloudinary.com/x", "cloudinary.com", false),
    UrlCase("underscore-host", "https://a_b.example.com/x", null, false, odd),
    UrlCase("kelvin-sign-host", kelvinHost, null, false, idn),
    UrlCase("fullwidth-c-cloudinary-host", fullwidthHost, null, false, idn),
  )

  private val first14Inputs = listOf(
    "https://a.b/c?d=e", "HTTP://EXAMPLE.com", "javascript:alert(1)", "file:///x", "content://x",
    "https://user:pw@host/", "https://host:0/", "https://host:65536/", "https://host./", "https:///path",
    "https://[::1]/", "//cdn.example.com/x", "", "ftp://x",
  )

  private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

  private fun legacyMap(c: UrlCase) = obj("host" to LegacyUrlOracle.host(c.input), "exported" to LegacyUrlOracle.exported(c.input))

  private fun expectedMap(c: UrlCase) = obj("host" to c.host, "exported" to c.exported)

  private fun urlCaseMaps(): List<Map<String, Any?>> = urlCases.map { c ->
    obj(
      "id" to c.id,
      "input" to c.input,
      "expected" to expectedMap(c),
      "androidLegacy" to legacyMap(c),
      "divergence" to c.divergence,
      "optional" to false,
    )
  }

  private fun urlRoot(): Map<String, Any?> = obj("schema" to 1L, "zone" to zone, "cases" to urlCaseMaps())

  @Test
  fun urlGolden_matchesCommitted() {
    GoldenWriter().check(urlGoldenName, urlRoot())
  }

  @Test
  fun urlGolden_has28CasesWithNamedDivergences() {
    assertEquals(28, urlCases.size)
    val ids = urlCases.map { it.id }
    assertEquals("id trung nhau", ids.size, ids.toSet().size)
    assertEquals(first14Inputs, urlCases.take(14).map { it.input })
    assertEquals(
      listOf(
        "https://res.cloudinary.com/x/y.jpg", "https://RES.CLOUDINARY.COM/x", "https://res.cloudinary.com./x",
        "https://res.cloudinary.com\\@evil.com/x", "https://res.cloudinary.com%2eevil.com/x", "https://evilcloudinary.com/x",
        "https://cloudinary.com.evil.com/x", "https://res.cloudinary.com:443/x", "https://res.cloudinary.com:99999/x",
        "https://xn--clodinary-9ya.com/x", "https://cloudinary.com/x", "https://a_b.example.com/x", kelvinHost, fullwidthHost,
      ),
      urlCases.drop(14).map { it.input },
    )
    // Chu Kelvin U+212A va chu rong U+FF43 phai that su co trong dau vao (khong bi editor doi thanh ASCII).
    assertTrue(kelvinHost.contains('\u212A') && kelvinHost.none { it == 'k' || it == 'K' })
    assertTrue(fullwidthHost.contains('\uFF43') && !fullwidthHost.contains("cloudinary"))
    assertTrue(kelvinHost.lowercase().startsWith("https://kelvin."))

    for (c in urlCases) {
      assertTrue("${c.id}: divergence khong ten hop le ${c.divergence}", c.divergence == null || c.divergence in divergenceNames)
      val diverges = expectedMap(c) != legacyMap(c)
      assertEquals("${c.id}: expected=${expectedMap(c)} legacy=${legacyMap(c)}; divergence phai co dung khi expected khac androidLegacy", diverges, c.divergence != null)
    }
    assertEquals(divergenceNames, urlCases.mapNotNull { it.divergence }.toSet())
    for (c in urlCaseMaps()) {
      assertEquals(false, c["optional"])
      assertEquals(listOf("host", "exported"), (c["expected"] as Map<*, *>).keys.toList())
    }
    assertNoFloats("GV-202 golden", renderToText(urlRoot()))
  }

  @Test
  fun urlGolden_expectedNeverLooserThanLegacy() {
    for (c in urlCases) {
      val legacyHost = LegacyUrlOracle.host(c.input)
      val legacyExported = LegacyUrlOracle.exported(c.input)
      assertTrue("${c.id}: exported rong hon androidLegacy", !c.exported || legacyExported)
      assertTrue("${c.id}: host ${c.host} khong co trong androidLegacy $legacyHost", c.host == null || c.host == legacyHost)
      assertTrue("${c.id}: exported thi phai co host", !c.exported || c.host != null)
      // Chi ca chat hon moi duoc co divergence: host rong di, hoac exported tat di.
      if (c.divergence != null) assertTrue("${c.id}: divergence nhung khong chat hon", c.host != legacyHost || c.exported != legacyExported)
    }
  }

  @Test
  fun urlGolden_embeddedOracleEqualsLegacyAdapter() {
    // D-20c xoa test nay: MemorySyncAdapter doi sang UrlSafety nen khong con la oracle.
    val extra = listOf("   ", "https://img.example/p.jpg", "http://img.example/p.jpg", "HTTPS://img.example/P.jpg")
    for (raw in urlCases.map { it.input } + extra) {
      assertEquals("oracle lech MemorySyncAdapter voi `$raw`", MemorySyncAdapter.safeSharedPhotoUri(raw), LegacyUrlOracle.safeSharedPhotoUri(raw))
    }
    assertEquals("", MemorySyncAdapter.safeSharedPhotoUri(null))
    assertEquals("", LegacyUrlOracle.safeSharedPhotoUri(null))
  }
}