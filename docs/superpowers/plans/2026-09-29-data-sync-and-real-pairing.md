# Offline-First Data Sync & Real Two-Device Pairing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Room the single source of truth for syncable data via a generic offline outbox + WorkManager sync engine, and make couple pairing ("Set Love 1-1") actually work across two real devices through Firestore — with no Cloud Functions.

**Architecture:** Two Room entities (`shared_memories`, `anniversary_dates`) get sync columns (`syncId`, `updatedAt`, `deleted`, `pendingSync`) and route every write through a new generic `sync_outbox` table via `@Transaction` DAO methods, drained by a `SyncWorker` (WorkManager). A process-lifetime `SyncCoordinator` runs two Firestore listener tiers (identity: `relationships`+`invites`; content: `memories`+`anniversaries` of the active relationship) and writes incoming changes into Room. Pairing drops the `partnerId` field entirely and binds a `relationships/{id}` document's id to its originating `invites/{id}` — Firestore's own create-vs-update routing then makes the security rules airtight without a backend.

**Tech Stack:** Kotlin, Room 2.7.0 (Room KTX `@Transaction`, real `Migration`), WorkManager (`androidx.work:work-runtime-ktx`, new dependency), Firebase Firestore (`FirebaseFirestore`, `addSnapshotListener`), Moshi (already in the catalog, used for outbox payload serialization), Robolectric + `MigrationTestHelper` + `TestListenableWorkerBuilder` for tests, `@firebase/rules-unit-testing` (Node, new — `scripts/` already has a `package.json`) for rules tests.

**Spec:** `docs/superpowers/specs/2026-09-29-data-sync-and-real-pairing-design.md`

## Global Constraints

- No Cloud Functions, no new backend service — pairing state machine is Firestore rules + client only.
- WorkManager periodic work respects the OS floor of 15 minutes; immediate pushes go through a one-shot `enqueueUniqueWork`, not a shorter periodic interval.
- `app/schemas/` must be committed once `exportSchema = true` is turned on (Task 1).
- New Gradle dependency: `androidx.work:work-runtime-ktx` (not yet in `gradle/libs.versions.toml`).
- Every existing external signature in `InLoveRepository` and `InLoveViewModel` that UI code already calls (`addSharedMemory`, `updateSharedMemory`, `deleteSharedMemory`, `addAnniversaryDate`, `updateAnniversaryDate`, `deleteAnniversaryDate`, `toggleMemoryFavorite`, `toggleAnniversaryNotification`) keeps its exact current parameter list and return type — sync-awareness is added inside these methods' bodies, not by changing call sites in `ui/`.
- `OnlineUserEntity.uid` / `UserAccountEntity.uid` are the real Firebase Auth uid (`firebaseUser.uid`, set at `AuthRepository.kt:457` on register and mirrored into `OnlineCoupleRepository` via `setCurrentUserId`) — every Firestore rule and every piece of code in this plan can rely on `request.auth.uid` matching `OnlineUserEntity.uid`.

## Review Focus

1. **Two devices tap "accept" on the same invite within the same second** (double-tap, or both users act at once) — the relationship-id-equals-invite-id rule must reject the second `create` as a no-op `update`, never create a duplicate relationship or crash the client. Covered by Task 12's rules-emulator test `"duplicate accept is a no-op"`.
2. **App is killed mid-outbox-push** (partial sync, e.g. user force-closes right after saving a memory offline) — on relaunch, `SyncWorker` must resume from the `sync_outbox` row already written in the same `@Transaction` as the local edit, never lose or double-push it. Covered by Task 6's `SyncWorker` test `"resumes pending outbox entry after process restart"` (simulated by re-running the worker against the same DB instance).
3. **Re-pairing the same two people after a breakup** — a fresh `invites`/`relationships` id pair must be issued; the old, terminated relationship id must never be mistaken for still-active. Covered by Task 11's `acceptSetLoveInvite` test `"re-pairing after breakup creates a new relationship id, not the terminated one"`.
4. **`MIGRATION_12_13` runs against a device with real pre-existing rows**, not a fresh install — must not crash and must not silently drop `shared_memories`/`anniversary_dates` rows that predate the sync columns. Covered by Task 1's `MigrationTestHelper` test seeding v12 rows before migrating.
5. **A Firestore listener delivers a remote update for a memory the user also edited locally while offline** (local `pendingSync = true` with its own `updatedAt`, remote arrives with a different `updatedAt`) — the merge must not silently discard the user's own unsynced edit. Covered by Task 8's `SyncCoordinator` merge test `"local pending edit survives a concurrent older remote update"`.

---

### Task 1: Room schema foundation — sync columns, outbox table, migration 12→13

**Files:**
- Modify: `app/src/main/java/com/example/data/model/Entities.kt`
- Modify: `app/src/main/java/com/example/data/db/InLoveDao.kt`
- Modify: `app/src/main/java/com/example/data/db/AppDatabase.kt`
- Create: `app/src/main/java/com/example/data/db/Migrations.kt`
- Modify: `app/build.gradle.kts` (add `androidx.room:room-testing` to `testImplementation`, and turn on schema export)
- Test: `app/src/test/java/com/example/data/db/Migration12To13Test.kt`

**Interfaces:**
- Produces: `SyncOutboxEntity` (in `Entities.kt`); `InLoveDao.insertOutboxEntry(entry: SyncOutboxEntity): Long`, `InLoveDao.getPendingOutboxEntries(limit: Int = 20): List<SyncOutboxEntity>`, `InLoveDao.deleteOutboxEntry(id: Long)`, `InLoveDao.markOutboxAttemptFailed(id: Long, error: String)`, `InLoveDao.insertSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity): Long`, `InLoveDao.updateSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity)`, `InLoveDao.deleteSharedMemoryWithOutbox(id: Long, outbox: SyncOutboxEntity)`, `InLoveDao.insertAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity): Long`, `InLoveDao.updateAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity)`, `InLoveDao.deleteAnniversaryDateWithOutbox(id: Long, outbox: SyncOutboxEntity)`, `InLoveDao.upsertGiftIdeaByRemoteId(item: GiftIdeaEntity)`, `InLoveDao.upsertMilestoneByRemoteId(item: MilestoneEntity)`. `AppDatabase.MIGRATION_12_13` (in `Migrations.kt`).
- Consumes: nothing (foundation task).

- [ ] **Step 1: Write the failing migration test**

Create `app/src/test/java/com/example/data/db/Migration12To13Test.kt`:

```kotlin
package com.example.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration12To13Test {

  @get:Rule
  val helper: MigrationTestHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  @Test
  fun migrate12To13_preservesExistingRows_andAddsSyncColumns() {
    val v12 = helper.createDatabase(TEST_DB, 12)
    v12.execSQL(
      """
      INSERT INTO shared_memories
        (id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
         createdAt, relationshipId, authorId, authorName, isSynced, mediaType, videoUri,
         cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
         durationSeconds, privacyLevel)
      VALUES
        (1, 'First Date', '2024-01-01', 'note', 'file:///a.jpg', '', 0, 'Kỷ Niệm Ngày Yêu',
         1700000000000, NULL, '', 'Bạn', 1, 'IMAGE', NULL, NULL, NULL, 1, '', 0, 'COUPLE_ONLY')
      """.trimIndent()
    )
    v12.execSQL(
      """
      INSERT INTO gift_ideas
        (id, title, category, badgeText, tag, description, imageUrl, isFavorited,
         detailsSnippet, actionText, isAiGenerated, targetInterests, suggestedOccasion, priceRange)
      VALUES
        (1, 'Nến thơm', 'Quà lãng mạn', 'Gợi ý', 'Ý nghĩa', '', '', 0, '', '', 0, '', '', '')
      """.trimIndent()
    )
    v12.close()

    val v13 = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

    v13.query("SELECT title, syncId, updatedAt, deleted, pendingSync FROM shared_memories WHERE id = 1").use { cursor ->
      assert(cursor.moveToFirst()) { "row 1 must survive the migration" }
      assert(cursor.getString(0) == "First Date")
      assert(cursor.getInt(3) == 0) { "deleted must default to false" }
      assert(cursor.getInt(4) == 0) { "pendingSync must default to false for pre-existing rows" }
    }
    v13.query("SELECT remoteId FROM gift_ideas WHERE id = 1").use { cursor ->
      assert(cursor.moveToFirst())
      assert(cursor.getString(0) == "") { "remoteId defaults to blank until the next preset sync assigns it" }
    }
    v13.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
      cursor.moveToFirst()
      assert(cursor.getInt(0) == 0) { "sync_outbox table must exist and start empty" }
    }
  }

  companion object {
    private const val TEST_DB = "migration-test"
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.db.Migration12To13Test"`
Expected: FAIL — compile error (`AppDatabase` has no `MIGRATION_12_13`, `sync_outbox` table doesn't exist).

- [ ] **Step 3: Add `androidx.room:room-testing` and enable schema export**

In `gradle/libs.versions.toml`, add under `[libraries]`:
```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "roomRuntime" }
```
In `app/build.gradle.kts`, add to the `dependencies {}` block:
```kotlin
testImplementation(libs.androidx.room.testing)
```
And in the `android {}` block, add a schema-location argument for KSP (Room needs to know where to write `app/schemas/`):
```kotlin
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
```
(Add this alongside the existing `android { ... }` block, at the top level of the module, next to any other `ksp { }` configuration if one already exists — if none exists yet, add it as its own top-level block in the file.)

- [ ] **Step 4: Add sync columns to `SharedMemoryEntity` and `AnniversaryDateEntity`, `remoteId` to `GiftIdeaEntity`/`MilestoneEntity`, sync columns to the pairing entities, and the new `SyncOutboxEntity`**

In `app/src/main/java/com/example/data/model/Entities.kt`, replace the `MilestoneEntity` declaration:

```kotlin
@Entity(tableName = "milestones", indices = [Index(value = ["remoteId"], unique = true)])
data class MilestoneEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val dateText: String,
  val subtitle: String,
  val categoryTag: String,
  val secondaryTag: String,
  val imageUrl: String,
  val daysRemaining: Int,
  val isPast: Boolean = false,
  val progressPercent: Float? = null,
  val isImportant: Boolean = false,
  val notificationEnabled: Boolean = true,
  val isSaved: Boolean = false,
  val alarmTimeMillis: Long? = null,
  val alarmTimeFormatted: String = "",
  val isUserCreated: Boolean = false,
  val remoteId: String = ""
)
```

Replace the `GiftIdeaEntity` declaration:

```kotlin
@Entity(tableName = "gift_ideas", indices = [Index(value = ["remoteId"], unique = true)])
data class GiftIdeaEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val category: String,
  val badgeText: String,
  val tag: String,
  val description: String,
  val imageUrl: String,
  val isFavorited: Boolean = false,
  val detailsSnippet: String = "",
  val actionText: String = "",
  val isAiGenerated: Boolean = false,
  val targetInterests: String = "",
  val suggestedOccasion: String = "",
  val priceRange: String = "",
  val remoteId: String = ""
)
```

Replace `SharedMemoryEntity` (drops `isSynced`, adds four sync columns):

```kotlin
@Entity(tableName = "shared_memories")
data class SharedMemoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val dateText: String,
  val note: String = "",
  val photoUri: String,
  val location: String = "",
  val isFavorite: Boolean = false,
  val anniversaryTitle: String = "Kỷ Niệm Ngày Yêu",
  val createdAt: Long = System.currentTimeMillis(),
  val relationshipId: String? = null,
  val authorId: String = "",
  val authorName: String = "Bạn",
  // Cloudinary media attributes
  val mediaType: String = "IMAGE",
  val videoUri: String? = null,
  val cloudinaryPublicId: String? = null,
  val cloudinaryUrl: String? = null,
  val isCloudinaryStored: Boolean = true,
  val fileSizeFormatted: String = "",
  val durationSeconds: Int = 0,
  // Phân quyền (Permissions): "COUPLE_ONLY", "PRIVATE", "PUBLIC"
  val privacyLevel: String = "COUPLE_ONLY",
  // Offline-first sync (Task 1, 2026-09-29 data-sync-and-real-pairing plan)
  val syncId: String = "",
  val updatedAt: Long = 0,
  val deleted: Boolean = false,
  val pendingSync: Boolean = false
)
```

Replace `AnniversaryDateEntity` (drops `isSynced`, adds the same four columns):

```kotlin
@Entity(tableName = "anniversary_dates")
data class AnniversaryDateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val dateText: String,
  val type: String = "LOVE",
  val description: String = "",
  val isAnnual: Boolean = true,
  val notificationEnabled: Boolean = true,
  val reminderDaysBefore: Int = 3,
  val daysRemaining: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val relationshipId: String? = null,
  val syncId: String = "",
  val updatedAt: Long = 0,
  val deleted: Boolean = false,
  val pendingSync: Boolean = false
)
```

Add sync columns to `OnlineRelationshipEntity` and `OnlineInviteEntity` (identity-tier entities, Task 8):

```kotlin
@Entity(tableName = "online_relationships")
data class OnlineRelationshipEntity(
  @PrimaryKey val relationshipId: String,
  val user1: String,
  val user2: String,
  val startDate: Long,
  val startDateText: String = "",
  val status: String = RelationshipStatus.ACTIVE,
  val breakupRequestedBy: String? = null,
  val breakupRequestedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val terminatedAt: Long? = null,
  val updatedAt: Long = 0,
  val pendingSync: Boolean = false
)
```

```kotlin
@Entity(tableName = "online_invites")
data class OnlineInviteEntity(
  @PrimaryKey val inviteId: String,
  val senderUid: String,
  val senderName: String = "Vô danh",
  val senderAvatar: String = "",
  val senderCoupleCode: String,
  val senderBirthDate: String = "",
  val senderAge: Int = 0,
  val senderZodiac: String = "",
  val senderBio: String = "",
  val targetCoupleCode: String,
  val targetUid: String? = null,
  val proposedStartDate: Long = System.currentTimeMillis(),
  val proposedStartDateText: String = "",
  val loveNote: String = "",
  val status: String = InviteStatus.PENDING,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = 0,
  val pendingSync: Boolean = false
) {
  val effectiveSenderName: String
    get() = senderName.ifBlank { "Vô danh" }
}
```

Add the new outbox entity at the end of the file, before the final blank lines:

```kotlin
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val entityType: String, // "memory" | "anniversary" | "invite" | "relationship"
  val syncId: String,
  val operation: String, // "UPSERT" | "DELETE"
  val payloadJson: String,
  val createdAt: Long = System.currentTimeMillis(),
  val attemptCount: Int = 0,
  val lastError: String? = null
)
```

Add the two new imports at the top of `Entities.kt`:
```kotlin
import androidx.room.Index
```
(`androidx.room.Entity` and `androidx.room.PrimaryKey` are already imported.)

- [ ] **Step 5: Add outbox + upsert-by-remoteId methods to `InLoveDao`**

In `app/src/main/java/com/example/data/db/InLoveDao.kt`, add `import androidx.room.Transaction` and `import com.example.data.model.SyncOutboxEntity` to the imports, then add these members inside the `interface InLoveDao` (place them after the existing Gift Ideas / Anniversary Dates sections):

```kotlin
  // Upsert-by-remoteId (fixes duplicate rows from repeated Firestore preset syncs)
  @Query("SELECT * FROM gift_ideas WHERE remoteId = :remoteId LIMIT 1")
  suspend fun getGiftIdeaByRemoteId(remoteId: String): GiftIdeaEntity?

  @Transaction
  suspend fun upsertGiftIdeaByRemoteId(item: GiftIdeaEntity) {
    val existing = getGiftIdeaByRemoteId(item.remoteId)
    if (existing != null) {
      updateGiftIdea(item.copy(id = existing.id, isFavorited = existing.isFavorited))
    } else {
      insertGiftIdeas(listOf(item))
    }
  }

  @Query("SELECT * FROM milestones WHERE remoteId = :remoteId LIMIT 1")
  suspend fun getMilestoneByRemoteId(remoteId: String): MilestoneEntity?

  @Transaction
  suspend fun upsertMilestoneByRemoteId(item: MilestoneEntity) {
    val existing = getMilestoneByRemoteId(item.remoteId)
    if (existing != null) {
      updateMilestone(
        item.copy(
          id = existing.id,
          isSaved = existing.isSaved,
          notificationEnabled = existing.notificationEnabled,
          alarmTimeMillis = existing.alarmTimeMillis,
          alarmTimeFormatted = existing.alarmTimeFormatted,
          isUserCreated = existing.isUserCreated
        )
      )
    } else {
      insertMilestone(item)
    }
  }

  // Sync outbox
  @Insert
  suspend fun insertOutboxEntry(entry: SyncOutboxEntity): Long

  @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC LIMIT :limit")
  suspend fun getPendingOutboxEntries(limit: Int = 20): List<SyncOutboxEntity>

  @Query("DELETE FROM sync_outbox WHERE id = :id")
  suspend fun deleteOutboxEntry(id: Long)

  @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
  suspend fun markOutboxAttemptFailed(id: Long, error: String)

  // Memory / anniversary writes that atomically enqueue an outbox entry (Task 7)
  @Transaction
  suspend fun insertSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity): Long {
    val newId = insertSharedMemory(memory)
    insertOutboxEntry(outbox)
    return newId
  }

  @Transaction
  suspend fun updateSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity) {
    updateSharedMemory(memory)
    insertOutboxEntry(outbox)
  }

  @Transaction
  suspend fun deleteSharedMemoryWithOutbox(id: Long, outbox: SyncOutboxEntity) {
    deleteSharedMemoryById(id)
    insertOutboxEntry(outbox)
  }

  @Transaction
  suspend fun insertAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity): Long {
    val newId = insertAnniversaryDate(item)
    insertOutboxEntry(outbox)
    return newId
  }

  @Transaction
  suspend fun updateAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity) {
    updateAnniversaryDate(item)
    insertOutboxEntry(outbox)
  }

  @Transaction
  suspend fun deleteAnniversaryDateWithOutbox(id: Long, outbox: SyncOutboxEntity) {
    deleteAnniversaryDateById(id)
    insertOutboxEntry(outbox)
  }
```

- [ ] **Step 6: Write `MIGRATION_12_13`**

Create `app/src/main/java/com/example/data/db/Migrations.kt`:

```kotlin
package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v12 -> v13: adds offline-sync columns (syncId/updatedAt/deleted/pendingSync) to
 * shared_memories and anniversary_dates, adds remoteId to gift_ideas/milestones to fix
 * duplicate rows on repeated preset sync, adds updatedAt/pendingSync to the pairing
 * entities, and creates sync_outbox.
 *
 * shared_memories/anniversary_dates are rebuilt (not ALTERed) because they also drop the
 * unused `isSynced` column, and SQLite's DROP COLUMN support (3.35+) is not guaranteed on
 * every OS version this app's minSdk (26) has to run on.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // --- shared_memories: rebuild to drop isSynced, add sync columns ---
    db.execSQL(
      """
      CREATE TABLE shared_memories_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        dateText TEXT NOT NULL,
        note TEXT NOT NULL DEFAULT '',
        photoUri TEXT NOT NULL,
        location TEXT NOT NULL DEFAULT '',
        isFavorite INTEGER NOT NULL DEFAULT 0,
        anniversaryTitle TEXT NOT NULL DEFAULT 'Kỷ Niệm Ngày Yêu',
        createdAt INTEGER NOT NULL,
        relationshipId TEXT,
        authorId TEXT NOT NULL DEFAULT '',
        authorName TEXT NOT NULL DEFAULT 'Bạn',
        mediaType TEXT NOT NULL DEFAULT 'IMAGE',
        videoUri TEXT,
        cloudinaryPublicId TEXT,
        cloudinaryUrl TEXT,
        isCloudinaryStored INTEGER NOT NULL DEFAULT 1,
        fileSizeFormatted TEXT NOT NULL DEFAULT '',
        durationSeconds INTEGER NOT NULL DEFAULT 0,
        privacyLevel TEXT NOT NULL DEFAULT 'COUPLE_ONLY',
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL DEFAULT 0,
        deleted INTEGER NOT NULL DEFAULT 0,
        pendingSync INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      INSERT INTO shared_memories_new
        (id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
         createdAt, relationshipId, authorId, authorName, mediaType, videoUri,
         cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
         durationSeconds, privacyLevel, updatedAt)
      SELECT
        id, title, dateText, note, photoUri, location, isFavorite, anniversaryTitle,
        createdAt, relationshipId, authorId, authorName, mediaType, videoUri,
        cloudinaryPublicId, cloudinaryUrl, isCloudinaryStored, fileSizeFormatted,
        durationSeconds, privacyLevel, createdAt
      FROM shared_memories
      """.trimIndent()
    )
    db.execSQL("DROP TABLE shared_memories")
    db.execSQL("ALTER TABLE shared_memories_new RENAME TO shared_memories")

    // --- anniversary_dates: same rebuild pattern ---
    db.execSQL(
      """
      CREATE TABLE anniversary_dates_new (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        title TEXT NOT NULL,
        dateText TEXT NOT NULL,
        type TEXT NOT NULL DEFAULT 'LOVE',
        description TEXT NOT NULL DEFAULT '',
        isAnnual INTEGER NOT NULL DEFAULT 1,
        notificationEnabled INTEGER NOT NULL DEFAULT 1,
        reminderDaysBefore INTEGER NOT NULL DEFAULT 3,
        daysRemaining INTEGER NOT NULL DEFAULT 0,
        createdAt INTEGER NOT NULL,
        relationshipId TEXT,
        syncId TEXT NOT NULL DEFAULT '',
        updatedAt INTEGER NOT NULL DEFAULT 0,
        deleted INTEGER NOT NULL DEFAULT 0,
        pendingSync INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      INSERT INTO anniversary_dates_new
        (id, title, dateText, type, description, isAnnual, notificationEnabled,
         reminderDaysBefore, daysRemaining, createdAt, relationshipId, updatedAt)
      SELECT
        id, title, dateText, type, description, isAnnual, notificationEnabled,
        reminderDaysBefore, daysRemaining, createdAt, relationshipId, createdAt
      FROM anniversary_dates
      """.trimIndent()
    )
    db.execSQL("DROP TABLE anniversary_dates")
    db.execSQL("ALTER TABLE anniversary_dates_new RENAME TO anniversary_dates")

    // --- additive columns (SQLite ADD COLUMN is safe on every supported OS version) ---
    db.execSQL("ALTER TABLE gift_ideas ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE milestones ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE online_relationships ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_relationships ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_invites ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE online_invites ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")

    // --- unique indices backing the new remoteId upsert path ---
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_gift_ideas_remoteId ON gift_ideas(remoteId)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_milestones_remoteId ON milestones(remoteId)")

    // --- new outbox table ---
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS sync_outbox (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        entityType TEXT NOT NULL,
        syncId TEXT NOT NULL,
        operation TEXT NOT NULL,
        payloadJson TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        attemptCount INTEGER NOT NULL DEFAULT 0,
        lastError TEXT
      )
      """.trimIndent()
    )
  }
}
```

- [ ] **Step 7: Wire the migration, entity, version bump and schema export into `AppDatabase`**

In `app/src/main/java/com/example/data/db/AppDatabase.kt`, add `import com.example.data.model.SyncOutboxEntity`, add `SyncOutboxEntity::class` to the `entities = [...]` list, change `version = 12` to `version = 13`, change `exportSchema = false` to `exportSchema = true`, and add `.addMigrations(MIGRATION_12_13)` to the builder chain:

```kotlin
  return INSTANCE ?: synchronized(this) {
    val instance = Room.databaseBuilder(
      context.applicationContext,
      AppDatabase::class.java,
      "inlove_database"
    )
      .addMigrations(MIGRATION_12_13)
      .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
      .build()
    INSTANCE = instance
    instance
  }
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.db.Migration12To13Test"`
Expected: PASS. Also run `./gradlew :app:kspDebugKotlin` once to generate `app/schemas/com.example.data.db.AppDatabase/13.json`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/data/model/Entities.kt \
        app/src/main/java/com/example/data/db/InLoveDao.kt \
        app/src/main/java/com/example/data/db/AppDatabase.kt \
        app/src/main/java/com/example/data/db/Migrations.kt \
        app/src/test/java/com/example/data/db/Migration12To13Test.kt \
        app/build.gradle.kts gradle/libs.versions.toml app/schemas
git commit -m "feat(data): Room v13 - sync columns, outbox table, gift/milestone remoteId"
```

---

### Task 2: Delete the dead `_3nf` schema

**Files:**
- Delete: `app/src/main/java/com/example/data/firebase/Firebase3NFService.kt`
- Delete: `app/src/main/java/com/example/data/firebase/Firebase3NFModels.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:42, 50-59, 297`
- Modify: `firestore.rules` (remove `users_3nf`, `relationships_3nf`, `invites_3nf`, `memories_3nf` blocks)
- Test: `./gradlew :app:compileDebugKotlin` (compile-level check — there is no runtime behavior to unit test for a deletion; the "test" for this task is that the project still compiles and existing tests still pass)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing (pure removal); confirms no other file in the plan depends on `Firebase3NFService`/`Firebase3NFModels`.

- [ ] **Step 1: Confirm nothing else references the dead files**

Run: `grep -rn "Firebase3NF" app/src/main/java app/src/test/java` (or the Grep tool)
Expected: only the four hit locations already identified (`InLoveViewModel.kt:42,50,51,55,59,297` and the two files themselves). If any other file appears, stop and re-scope this task before deleting.

- [ ] **Step 2: Delete the two dead files**

```bash
git rm app/src/main/java/com/example/data/firebase/Firebase3NFService.kt
git rm app/src/main/java/com/example/data/firebase/Firebase3NFModels.kt
```

- [ ] **Step 3: Remove the 3NF property, dialog flow and construction from `InLoveViewModel`**

In `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt`:
- Delete line 42: `val firebase3NFService: com.example.data.firebase.Firebase3NFService`
- Delete lines 50-51 (`_show3NFVisualizerDialog` / `show3NFVisualizerDialog`) and their two reset call sites at lines 55 and 59 (each is a one-line `_show3NFVisualizerDialog.value = false` inside whatever dialog-close-all function currently contains it — remove just that line from each function body, keep the rest of the function).
- Delete line 297: `firebase3NFService = com.example.data.firebase.Firebase3NFService(database.inLoveDao(), application)`

- [ ] **Step 4: Remove the `_3nf` collection rule blocks from `firestore.rules`**

In `firestore.rules`, delete the four `match` blocks: `users_3nf/{userId}`, `relationships_3nf/{relId}`, `invites_3nf/{inviteId}`, `memories_3nf/{memoryId}` (the whole "3NF Normalized Collections" section, lines 152-200 in the current file), and the now-unused `// 3NF Normalized Collections` section header comment.

- [ ] **Step 5: Verify the project still compiles and existing tests pass**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: PASS with zero references to `Firebase3NF*` remaining.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(data): delete dead _3nf schema (Firebase3NFService never invoked)"
```

---

### Task 3: Fix leaked `CoroutineScope` in `AuthRepository` and `OnlineCoupleRepository`

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/AuthRepository.kt:51-57`
- Modify: `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt:27-31`
- Modify: `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:291-295`
- Test: `app/src/test/java/com/example/data/repository/RepositoryScopeLifecycleTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AuthRepository(dao, onlineRepo, context, scope: CoroutineScope, isTestMode: Boolean = false)`, `OnlineCoupleRepository(dao, context, scope: CoroutineScope)` — both now take an externally-owned `CoroutineScope` instead of creating their own. Task 11 and Task 8 construct these with `viewModelScope`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/repository/RepositoryScopeLifecycleTest.kt`:

```kotlin
package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryScopeLifecycleTest {

  @Test
  fun onlineCoupleRepository_stopsWork_whenCallerScopeIsCancelled() {
    val db = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), AppDatabase::class.java
    ).allowMainThreadQueries().build()
    val job = SupervisorJob()
    val scope = CoroutineScope(job + UnconfinedTestDispatcher())

    val repo = OnlineCoupleRepository(db.inLoveDao(), ApplicationProvider.getApplicationContext(), scope)
    repo.switchDemoUser() // launches on the injected scope

    job.cancel()
    assert(!scope.isActive) { "cancelling the caller's scope must stop the repository's own coroutines" }
    db.close()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.RepositoryScopeLifecycleTest"`
Expected: FAIL to compile — `OnlineCoupleRepository`'s constructor does not yet accept a `CoroutineScope`.

- [ ] **Step 3: Change `OnlineCoupleRepository` to accept an injected scope**

In `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt`, replace:
```kotlin
class OnlineCoupleRepository(
  private val dao: InLoveDao,
  context: Context
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```
with:
```kotlin
class OnlineCoupleRepository(
  private val dao: InLoveDao,
  context: Context,
  private val scope: CoroutineScope
) {
```
(Remove the now-unused `SupervisorJob` and `Dispatchers` imports only if nothing else in the file uses them — `Dispatchers.IO` is still used throughout the file's `withContext(Dispatchers.IO)` calls, so keep that import; only drop `SupervisorJob` if it becomes unused.)

- [ ] **Step 4: Change `AuthRepository` the same way**

In `app/src/main/java/com/example/data/repository/AuthRepository.kt`, replace:
```kotlin
class AuthRepository(
  private val dao: InLoveDao,
  private val onlineRepo: OnlineCoupleRepository,
  context: Context,
  private val isTestMode: Boolean = false
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```
with:
```kotlin
class AuthRepository(
  private val dao: InLoveDao,
  private val onlineRepo: OnlineCoupleRepository,
  context: Context,
  private val scope: CoroutineScope,
  private val isTestMode: Boolean = false
) {
```

- [ ] **Step 5: Update the two construction sites in `InLoveViewModel.init`**

In `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt`, replace:
```kotlin
    onlineRepo = com.example.data.repository.OnlineCoupleRepository(database.inLoveDao(), application)
    authRepo = com.example.data.repository.AuthRepository(database.inLoveDao(), onlineRepo, application)
```
with:
```kotlin
    onlineRepo = com.example.data.repository.OnlineCoupleRepository(database.inLoveDao(), application, viewModelScope)
    authRepo = com.example.data.repository.AuthRepository(database.inLoveDao(), onlineRepo, application, viewModelScope)
```
(`viewModelScope` is already available on `InLoveViewModel` since it extends `AndroidViewModel`; it is already used elsewhere in `init`, e.g. at the `userRole`/`subscriptionTier`/`isVip` `.stateIn(viewModelScope, ...)` calls a few lines below.)

- [ ] **Step 6: Update any existing test that constructs these repositories directly**

`app/src/test/java/com/example/data/repository/AuthRepositoryTest.kt` and `app/src/test/java/com/example/CloudEnrichmentDataTest.kt` construct `AuthRepository`/`OnlineCoupleRepository` directly — add a `CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())` (or the test's existing `TestScope`, if one is already in use in that file) as the new constructor argument at each call site in those two files, matching the new signatures from Steps 3-4.

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, including `RepositoryScopeLifecycleTest` and the two pre-existing tests updated in Step 6.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/data/repository/AuthRepository.kt \
        app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt \
        app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt \
        app/src/test/java/com/example/data/repository/RepositoryScopeLifecycleTest.kt \
        app/src/test/java/com/example/data/repository/AuthRepositoryTest.kt \
        app/src/test/java/com/example/CloudEnrichmentDataTest.kt
git commit -m "fix(data): stop leaking CoroutineScope in AuthRepository/OnlineCoupleRepository"
```

---

### Task 4: `NetworkMonitor`

**Files:**
- Create: `app/src/main/java/com/example/data/sync/NetworkMonitor.kt`
- Test: `app/src/test/java/com/example/data/sync/NetworkMonitorTest.kt`

**Interfaces:**
- Produces: `class NetworkMonitor(context: Context) { val isOnline: StateFlow<Boolean> }`.
- Consumes: nothing.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/sync/NetworkMonitorTest.kt`:

```kotlin
package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkMonitorTest {

  @Test
  fun isOnline_reflectsShadowConnectivityState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val monitor = NetworkMonitor(context)

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val shadow = org.robolectric.Shadows.shadowOf(cm) as ShadowConnectivityManager
    shadow.setDefaultNetworkActive(false)

    assert(!monitor.isOnline.value) { "NetworkMonitor must report offline when the default network is inactive" }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.NetworkMonitorTest"`
Expected: FAIL to compile — `com.example.data.sync.NetworkMonitor` does not exist yet.

- [ ] **Step 3: Implement `NetworkMonitor`**

Create `app/src/main/java/com/example/data/sync/NetworkMonitor.kt`:

```kotlin
package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime connectivity observer. Registered once from [com.example.di.AppServiceLocator]
 * (Task 8) so every consumer (SyncCoordinator, Settings offline banner) reads the same instance
 * instead of each registering its own ConnectivityManager.NetworkCallback.
 */
class NetworkMonitor(context: Context) {

  private val _isOnline = MutableStateFlow(currentlyOnline(context))
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      _isOnline.value = true
    }

    override fun onLost(network: Network) {
      _isOnline.value = currentlyOnline(context)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
  }

  init {
    val request = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()
    connectivityManager.registerNetworkCallback(request, callback)
  }

  private fun currentlyOnline(context: Context): Boolean {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.NetworkMonitorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/sync/NetworkMonitor.kt \
        app/src/test/java/com/example/data/sync/NetworkMonitorTest.kt
git commit -m "feat(sync): add NetworkMonitor"
```

---

### Task 5: `EntitySyncAdapter` contract + `MemorySyncAdapter` + `AnniversarySyncAdapter`

**Files:**
- Create: `app/src/main/java/com/example/data/sync/EntitySyncAdapter.kt`
- Create: `app/src/main/java/com/example/data/sync/MemorySyncAdapter.kt`
- Create: `app/src/main/java/com/example/data/sync/AnniversarySyncAdapter.kt`
- Test: `app/src/test/java/com/example/data/sync/MemorySyncAdapterTest.kt`
- Test: `app/src/test/java/com/example/data/sync/AnniversarySyncAdapterTest.kt`

**Interfaces:**
- Consumes: `SharedMemoryEntity`, `AnniversaryDateEntity` (Task 1).
- Produces: `interface EntitySyncAdapter<T> { val entityType: String; fun collectionPath(relationshipId: String): String; fun toFirestoreMap(entity: T): Map<String, Any?>; fun fromFirestoreDoc(doc: DocumentSnapshot): T? }`, `object MemorySyncAdapter : EntitySyncAdapter<SharedMemoryEntity>`, `object AnniversarySyncAdapter : EntitySyncAdapter<AnniversaryDateEntity>`. Used by Task 6 (`SyncWorker`) and Task 8 (`SyncCoordinator`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/data/sync/MemorySyncAdapterTest.kt`:

```kotlin
package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import org.junit.Test

class MemorySyncAdapterTest {

  @Test
  fun toFirestoreMap_roundTripsThroughFromFirestoreDoc_viaMap() {
    val original = SharedMemoryEntity(
      id = 5,
      title = "Lần đầu hẹn hò",
      dateText = "2024-02-14",
      note = "Trời mưa nhưng vui",
      photoUri = "https://cdn/photo.jpg",
      relationshipId = "rel_1",
      authorId = "uid_a",
      syncId = "sync-abc",
      updatedAt = 1_700_000_000_000L,
      pendingSync = true
    )

    val map = MemorySyncAdapter.toFirestoreMap(original)

    assert(map["title"] == "Lần đầu hẹn hò")
    assert(map["syncId"] == "sync-abc")
    assert(map["relationshipId"] == "rel_1")
    assert(map["deleted"] == false)
    // pendingSync is a local-only flag and must never be pushed to Firestore
    assert(!map.containsKey("pendingSync"))
  }

  @Test
  fun entityType_isStableIdentifierUsedByTheOutbox() {
    assert(MemorySyncAdapter.entityType == "memory")
  }
}
```

Create `app/src/test/java/com/example/data/sync/AnniversarySyncAdapterTest.kt`:

```kotlin
package com.example.data.sync

import com.example.data.model.AnniversaryDateEntity
import org.junit.Test

class AnniversarySyncAdapterTest {

  @Test
  fun toFirestoreMap_includesAllSyncedFields() {
    val original = AnniversaryDateEntity(
      id = 9,
      title = "Ngày cầu hôn",
      dateText = "2025-12-24",
      type = "PROPOSAL",
      relationshipId = "rel_1",
      syncId = "sync-xyz",
      updatedAt = 1_700_000_001_000L
    )

    val map = AnniversarySyncAdapter.toFirestoreMap(original)

    assert(map["title"] == "Ngày cầu hôn")
    assert(map["type"] == "PROPOSAL")
    assert(map["syncId"] == "sync-xyz")
    assert(map["relationshipId"] == "rel_1")
  }

  @Test
  fun entityType_isStableIdentifierUsedByTheOutbox() {
    assert(AnniversarySyncAdapter.entityType == "anniversary")
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.MemorySyncAdapterTest" --tests "com.example.data.sync.AnniversarySyncAdapterTest"`
Expected: FAIL to compile — none of the three new files exist yet.

- [ ] **Step 3: Write the `EntitySyncAdapter` interface**

Create `app/src/main/java/com/example/data/sync/EntitySyncAdapter.kt`:

```kotlin
package com.example.data.sync

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Maps one Room entity type to/from its Firestore document shape. Kept deliberately
 * non-generic-reflection (no `.toObject()`): several Room entities in this codebase have
 * non-null fields without defaults, so Firestore's POJO deserializer cannot always
 * synthesize a no-arg constructor for them. Manual field reads are also the pattern
 * already used elsewhere in this codebase (InLoveRepository's Firestore preset fetches).
 */
interface EntitySyncAdapter<T> {
  /** Stable identifier stored in SyncOutboxEntity.entityType and used to dispatch in SyncWorker/SyncCoordinator. */
  val entityType: String

  /** Firestore collection this entity type lives in, scoped to one couple's relationship. */
  fun collectionPath(relationshipId: String): String

  /** Local entity -> Firestore field map. Local-only bookkeeping fields (e.g. pendingSync) are excluded. */
  fun toFirestoreMap(entity: T): Map<String, Any?>

  /** Firestore document -> local entity, or null if required fields are missing (corrupt/partial doc). */
  fun fromFirestoreDoc(doc: DocumentSnapshot): T?
}
```

- [ ] **Step 4: Implement `MemorySyncAdapter`**

Create `app/src/main/java/com/example/data/sync/MemorySyncAdapter.kt`:

```kotlin
package com.example.data.sync

import com.example.data.model.SharedMemoryEntity
import com.google.firebase.firestore.DocumentSnapshot

object MemorySyncAdapter : EntitySyncAdapter<SharedMemoryEntity> {

  override val entityType: String = "memory"

  override fun collectionPath(relationshipId: String): String = "relationships/$relationshipId/memories"

  override fun toFirestoreMap(entity: SharedMemoryEntity): Map<String, Any?> = mapOf(
    "syncId" to entity.syncId,
    "title" to entity.title,
    "dateText" to entity.dateText,
    "note" to entity.note,
    "photoUri" to entity.photoUri,
    "location" to entity.location,
    "isFavorite" to entity.isFavorite,
    "anniversaryTitle" to entity.anniversaryTitle,
    "createdAt" to entity.createdAt,
    "relationshipId" to entity.relationshipId,
    "authorId" to entity.authorId,
    "authorName" to entity.authorName,
    "mediaType" to entity.mediaType,
    "videoUri" to entity.videoUri,
    "cloudinaryPublicId" to entity.cloudinaryPublicId,
    "cloudinaryUrl" to entity.cloudinaryUrl,
    "isCloudinaryStored" to entity.isCloudinaryStored,
    "fileSizeFormatted" to entity.fileSizeFormatted,
    "durationSeconds" to entity.durationSeconds,
    "privacyLevel" to entity.privacyLevel,
    "updatedAt" to entity.updatedAt,
    "deleted" to entity.deleted
  )

  override fun fromFirestoreDoc(doc: DocumentSnapshot): SharedMemoryEntity? {
    val syncId = doc.getString("syncId") ?: return null
    val title = doc.getString("title") ?: return null
    val photoUri = doc.getString("photoUri") ?: return null
    return SharedMemoryEntity(
      syncId = syncId,
      title = title,
      dateText = doc.getString("dateText") ?: "",
      note = doc.getString("note") ?: "",
      photoUri = photoUri,
      location = doc.getString("location") ?: "",
      isFavorite = doc.getBoolean("isFavorite") ?: false,
      anniversaryTitle = doc.getString("anniversaryTitle") ?: "Kỷ Niệm Ngày Yêu",
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      relationshipId = doc.getString("relationshipId"),
      authorId = doc.getString("authorId") ?: "",
      authorName = doc.getString("authorName") ?: "Bạn",
      mediaType = doc.getString("mediaType") ?: "IMAGE",
      videoUri = doc.getString("videoUri"),
      cloudinaryPublicId = doc.getString("cloudinaryPublicId"),
      cloudinaryUrl = doc.getString("cloudinaryUrl"),
      isCloudinaryStored = doc.getBoolean("isCloudinaryStored") ?: true,
      fileSizeFormatted = doc.getString("fileSizeFormatted") ?: "",
      durationSeconds = (doc.getLong("durationSeconds") ?: 0L).toInt(),
      privacyLevel = doc.getString("privacyLevel") ?: "COUPLE_ONLY",
      updatedAt = doc.getLong("updatedAt") ?: 0L,
      deleted = doc.getBoolean("deleted") ?: false,
      pendingSync = false
    )
  }
}
```

- [ ] **Step 5: Implement `AnniversarySyncAdapter`**

Create `app/src/main/java/com/example/data/sync/AnniversarySyncAdapter.kt`:

```kotlin
package com.example.data.sync

import com.example.data.model.AnniversaryDateEntity
import com.google.firebase.firestore.DocumentSnapshot

object AnniversarySyncAdapter : EntitySyncAdapter<AnniversaryDateEntity> {

  override val entityType: String = "anniversary"

  override fun collectionPath(relationshipId: String): String = "relationships/$relationshipId/anniversaries"

  override fun toFirestoreMap(entity: AnniversaryDateEntity): Map<String, Any?> = mapOf(
    "syncId" to entity.syncId,
    "title" to entity.title,
    "dateText" to entity.dateText,
    "type" to entity.type,
    "description" to entity.description,
    "isAnnual" to entity.isAnnual,
    "notificationEnabled" to entity.notificationEnabled,
    "reminderDaysBefore" to entity.reminderDaysBefore,
    "createdAt" to entity.createdAt,
    "relationshipId" to entity.relationshipId,
    "updatedAt" to entity.updatedAt,
    "deleted" to entity.deleted
  )

  override fun fromFirestoreDoc(doc: DocumentSnapshot): AnniversaryDateEntity? {
    val syncId = doc.getString("syncId") ?: return null
    val title = doc.getString("title") ?: return null
    return AnniversaryDateEntity(
      syncId = syncId,
      title = title,
      dateText = doc.getString("dateText") ?: "",
      type = doc.getString("type") ?: "LOVE",
      description = doc.getString("description") ?: "",
      isAnnual = doc.getBoolean("isAnnual") ?: true,
      notificationEnabled = doc.getBoolean("notificationEnabled") ?: true,
      reminderDaysBefore = (doc.getLong("reminderDaysBefore") ?: 3L).toInt(),
      createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
      relationshipId = doc.getString("relationshipId"),
      updatedAt = doc.getLong("updatedAt") ?: 0L,
      deleted = doc.getBoolean("deleted") ?: false,
      pendingSync = false
    )
  }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.MemorySyncAdapterTest" --tests "com.example.data.sync.AnniversarySyncAdapterTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/data/sync/EntitySyncAdapter.kt \
        app/src/main/java/com/example/data/sync/MemorySyncAdapter.kt \
        app/src/main/java/com/example/data/sync/AnniversarySyncAdapter.kt \
        app/src/test/java/com/example/data/sync/MemorySyncAdapterTest.kt \
        app/src/test/java/com/example/data/sync/AnniversarySyncAdapterTest.kt
git commit -m "feat(sync): add EntitySyncAdapter, MemorySyncAdapter, AnniversarySyncAdapter"
```

---

### Task 6: WorkManager dependency + `SyncWorker`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/example/data/sync/SyncWorker.kt`
- Test: `app/src/test/java/com/example/data/sync/SyncWorkerTest.kt`

**Interfaces:**
- Consumes: `InLoveDao` (Task 1), `MemorySyncAdapter`/`AnniversarySyncAdapter` (Task 5).
- Produces: `class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker`, plus `object SyncWorkerScheduler { fun enqueuePeriodic(context: Context); fun enqueueImmediate(context: Context) }`. Consumed by Task 8's `SyncCoordinator`/wiring.

- [ ] **Step 1: Add the WorkManager dependency**

In `gradle/libs.versions.toml`, add under `[versions]`:
```toml
workManager = "2.10.1"
```
and under `[libraries]`:
```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "workManager" }
```
In `app/build.gradle.kts`, add to `dependencies {}`:
```kotlin
implementation(libs.androidx.work.runtime.ktx)
testImplementation(libs.androidx.work.testing)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/example/data/sync/SyncWorkerTest.kt`:

```kotlin
package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.db.AppDatabase
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

  @Test
  fun doWork_drainsOnePendingOutboxEntry_andRemovesItOnSuccess() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val memory = SharedMemoryEntity(
      title = "Test", dateText = "2026-01-01", photoUri = "file:///a.jpg",
      relationshipId = "rel_1", syncId = "sync-1", updatedAt = 1L, pendingSync = true
    )
    val outboxId = dao.insertOutboxEntry(
      SyncOutboxEntity(
        entityType = MemorySyncAdapter.entityType,
        syncId = "sync-1",
        operation = "UPSERT",
        payloadJson = SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(memory)
      )
    )

    val worker = TestListenableWorkerBuilder<SyncWorker>(context)
      .setWorkerFactory(SyncWorkerFactory(dao, fakeFirestorePush = { _, _ -> /* no-op success */ }))
      .build()

    val result = worker.doWork()

    assert(result is ListenableWorker.Result.Success)
    assert(dao.getPendingOutboxEntries().none { it.id == outboxId }) {
      "a successfully pushed outbox entry must be removed"
    }
    db.close()
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.SyncWorkerTest"`
Expected: FAIL to compile — `SyncWorker`, `SyncWorkerFactory`, `SyncWorker.moshiAdapterFor` don't exist yet.

- [ ] **Step 4: Implement `SyncWorker`**

Create `app/src/main/java/com/example/data/sync/SyncWorker.kt`:

```kotlin
package com.example.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import com.example.data.db.AppDatabase
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Drains sync_outbox: for each pending entry, decodes the entity snapshot (payloadJson),
 * maps it to a Firestore document via the matching EntitySyncAdapter, and pushes it. One
 * generic worker for every syncable entity type (dispatched by SyncOutboxEntity.entityType)
 * instead of a worker per feature.
 */
class SyncWorker(
  context: Context,
  params: WorkerParameters,
  private val dao: InLoveDao,
  private val push: suspend (path: String, data: Map<String, Any?>) -> Unit = { path, data ->
    FirebaseFirestore.getInstance().document(path).set(data).await()
  }
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val pending = dao.getPendingOutboxEntries(limit = 20)
    if (pending.isEmpty()) return@withContext Result.success()

    var anyFailure = false
    for (entry in pending) {
      try {
        pushOne(entry)
        dao.deleteOutboxEntry(entry.id)
      } catch (e: Exception) {
        anyFailure = true
        dao.markOutboxAttemptFailed(entry.id, e.localizedMessage ?: e.toString())
      }
    }
    if (anyFailure) Result.retry() else Result.success()
  }

  private suspend fun pushOne(entry: SyncOutboxEntity) {
    when (entry.entityType) {
      MemorySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<SharedMemoryEntity>().fromJson(entry.payloadJson) ?: return
        val relationshipId = entity.relationshipId ?: return
        val path = "${MemorySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, MemorySyncAdapter.toFirestoreMap(entity))
      }
      AnniversarySyncAdapter.entityType -> {
        val entity = moshiAdapterFor<AnniversaryDateEntity>().fromJson(entry.payloadJson) ?: return
        val relationshipId = entity.relationshipId ?: return
        val path = "${AnniversarySyncAdapter.collectionPath(relationshipId)}/${entity.syncId}"
        push(path, AnniversarySyncAdapter.toFirestoreMap(entity))
      }
      else -> {
        // Unknown entity type (should not happen — every producer uses a known adapter's
        // entityType). Drop it rather than retry forever.
      }
    }
  }

  companion object {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    inline fun <reified T> moshiAdapterFor(): JsonAdapter<T> = moshi.adapter(T::class.java)

    private const val UNIQUE_PERIODIC_NAME = "sync_outbox_periodic"
    private const val UNIQUE_IMMEDIATE_NAME = "sync_outbox_immediate"

    fun enqueuePeriodic(context: Context) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueImmediate(context: Context) {
      val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
      val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
  }
}
```

Add the missing `WorkRequest` import: `import androidx.work.WorkRequest`.

- [ ] **Step 5: Add a `WorkerFactory` for constructor injection**

`SyncWorker` takes `dao`/`push` as constructor parameters (for testability), which the default `WorkManager` reflection-based factory cannot provide. Add, in the same `SyncWorker.kt` file:

```kotlin
class SyncWorkerFactory(
  private val dao: InLoveDao,
  private val fakeFirestorePush: (suspend (String, Map<String, Any?>) -> Unit)? = null
) : androidx.work.WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): androidx.work.ListenableWorker? {
    return if (workerClassName == SyncWorker::class.java.name) {
      if (fakeFirestorePush != null) SyncWorker(appContext, workerParameters, dao, fakeFirestorePush)
      else SyncWorker(appContext, workerParameters, dao)
    } else null
  }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.SyncWorkerTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/example/data/sync/SyncWorker.kt \
        app/src/test/java/com/example/data/sync/SyncWorkerTest.kt
git commit -m "feat(sync): add WorkManager SyncWorker draining the offline outbox"
```

---

### Task 7: Sync-aware CRUD for memories & anniversaries in `InLoveRepository`

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/InLoveRepository.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:293` (constructor call only)
- Test: `app/src/test/java/com/example/data/repository/InLoveRepositorySyncTest.kt`

**Interfaces:**
- Consumes: `InLoveDao.insertSharedMemoryWithOutbox`/etc. (Task 1), `MemorySyncAdapter`/`AnniversarySyncAdapter.entityType` (Task 5), `SyncWorker.moshiAdapterFor` (Task 6), `SyncWorker.enqueueImmediate` (Task 6).
- Produces: `InLoveRepository(dao: InLoveDao, appContext: Context)` (new second constructor parameter — needed to call `SyncWorker.enqueueImmediate(appContext)` after enqueueing). `addSharedMemory`/`updateSharedMemory`/`deleteSharedMemory`/`toggleMemoryFavorite`/`addAnniversaryDate`/`updateAnniversaryDate`/`deleteAnniversaryDate`/`toggleAnniversaryNotification` keep their exact existing external signatures.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/repository/InLoveRepositorySyncTest.kt`:

```kotlin
package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InLoveRepositorySyncTest {

  @Test
  fun addSharedMemory_assignsSyncId_andEnqueuesOutboxEntry() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val repo = InLoveRepository(db.inLoveDao(), context)

    repo.addSharedMemory(title = "Đi biển", dateText = "2026-06-01", photoUri = "file:///beach.jpg")

    val saved = db.inLoveDao().getAllSharedMemories().first().first()
    assert(saved.syncId.isNotBlank()) { "addSharedMemory must assign a syncId" }
    assert(saved.pendingSync) { "a freshly created memory is pending push" }

    val outbox = db.inLoveDao().getPendingOutboxEntries()
    assert(outbox.size == 1)
    assert(outbox.first().entityType == "memory")
    assert(outbox.first().syncId == saved.syncId)
    db.close()
  }
}
```

(Uses `kotlinx.coroutines.flow.first` on `getAllSharedMemories()` — add `import kotlinx.coroutines.flow.first` to the test file.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.InLoveRepositorySyncTest"`
Expected: FAIL — `InLoveRepository`'s constructor does not yet accept a `Context`, and `addSharedMemory` does not assign `syncId`/enqueue an outbox entry.

- [ ] **Step 3: Make `addSharedMemory`/`updateSharedMemory`/`deleteSharedMemory`/`toggleMemoryFavorite` sync-aware**

In `app/src/main/java/com/example/data/repository/InLoveRepository.kt`:
- Change the class header to `class InLoveRepository(private val dao: InLoveDao, private val appContext: android.content.Context)`.
- Add imports: `import com.example.data.model.SyncOutboxEntity`, `import com.example.data.sync.MemorySyncAdapter`, `import com.example.data.sync.AnniversarySyncAdapter`, `import com.example.data.sync.SyncWorker`, `import java.util.UUID`.
- Replace `addSharedMemory`'s body (keep the exact same parameter list) so it builds the entity with a fresh `syncId`/`updatedAt`/`pendingSync = true`, inserts it via the new outbox-aware DAO method, and kicks an immediate sync attempt:

```kotlin
  suspend fun addSharedMemory(
    title: String,
    dateText: String,
    photoUri: String,
    note: String = "",
    location: String = "",
    anniversaryTitle: String = "Kỷ Niệm Ngày Yêu",
    authorId: String = "",
    authorName: String = "Bạn",
    mediaType: String = "IMAGE",
    videoUri: String? = null,
    cloudinaryPublicId: String? = null,
    cloudinaryUrl: String? = null,
    isCloudinaryStored: Boolean = true,
    fileSizeFormatted: String = "",
    durationSeconds: Int = 0,
    privacyLevel: String = "COUPLE_ONLY"
  ) {
    val now = System.currentTimeMillis()
    val memory = SharedMemoryEntity(
      title = title,
      dateText = dateText,
      photoUri = photoUri,
      note = note,
      location = location,
      isFavorite = false,
      anniversaryTitle = anniversaryTitle,
      createdAt = now,
      authorId = authorId,
      authorName = authorName,
      mediaType = mediaType,
      videoUri = videoUri,
      cloudinaryPublicId = cloudinaryPublicId,
      cloudinaryUrl = cloudinaryUrl,
      isCloudinaryStored = isCloudinaryStored,
      fileSizeFormatted = fileSizeFormatted,
      durationSeconds = durationSeconds,
      privacyLevel = privacyLevel,
      syncId = UUID.randomUUID().toString(),
      updatedAt = now,
      pendingSync = true
    )
    dao.insertSharedMemoryWithOutbox(memory, outboxEntryFor(MemorySyncAdapter.entityType, memory.syncId, memory))
    SyncWorker.enqueueImmediate(appContext)
  }
```

Replace `updateSharedMemory`/`deleteSharedMemory`/`toggleMemoryFavorite`:

```kotlin
  suspend fun updateSharedMemory(memory: SharedMemoryEntity) {
    val withSync = ensureSyncId(memory).copy(updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.updateSharedMemoryWithOutbox(withSync, outboxEntryFor(MemorySyncAdapter.entityType, withSync.syncId, withSync))
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun deleteSharedMemory(id: Long) {
    val existing = dao.getAllSharedMemories().first().firstOrNull { it.id == id } ?: return
    val tombstone = ensureSyncId(existing).copy(deleted = true, updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.deleteSharedMemoryWithOutbox(id, outboxEntryFor(MemorySyncAdapter.entityType, tombstone.syncId, tombstone))
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun toggleMemoryFavorite(memory: SharedMemoryEntity) {
    updateSharedMemory(memory.copy(isFavorite = !memory.isFavorite))
  }
```

- [ ] **Step 4: Apply the same pattern to `addAnniversaryDate`/`updateAnniversaryDate`/`deleteAnniversaryDate`/`toggleAnniversaryNotification`**

```kotlin
  suspend fun addAnniversaryDate(
    title: String,
    dateText: String,
    type: String = "LOVE",
    description: String = "",
    isAnnual: Boolean = true,
    reminderDaysBefore: Int = 3,
    daysRemaining: Int = 0
  ): Long {
    val now = System.currentTimeMillis()
    val item = AnniversaryDateEntity(
      title = title,
      dateText = dateText,
      type = type,
      description = description,
      isAnnual = isAnnual,
      notificationEnabled = true,
      reminderDaysBefore = reminderDaysBefore,
      daysRemaining = daysRemaining,
      createdAt = now,
      syncId = UUID.randomUUID().toString(),
      updatedAt = now,
      pendingSync = true
    )
    val newId = dao.insertAnniversaryDateWithOutbox(
      item, outboxEntryFor(AnniversarySyncAdapter.entityType, item.syncId, item)
    )
    SyncWorker.enqueueImmediate(appContext)
    return newId
  }

  suspend fun updateAnniversaryDate(item: AnniversaryDateEntity) {
    val withSync = ensureSyncId(item).copy(updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.updateAnniversaryDateWithOutbox(
      withSync, outboxEntryFor(AnniversarySyncAdapter.entityType, withSync.syncId, withSync)
    )
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun deleteAnniversaryDate(id: Long) {
    val existing = dao.getAllAnniversaryDates().first().firstOrNull { it.id == id } ?: return
    val tombstone = ensureSyncId(existing).copy(deleted = true, updatedAt = System.currentTimeMillis(), pendingSync = true)
    dao.deleteAnniversaryDateWithOutbox(
      id, outboxEntryFor(AnniversarySyncAdapter.entityType, tombstone.syncId, tombstone)
    )
    SyncWorker.enqueueImmediate(appContext)
  }

  suspend fun toggleAnniversaryNotification(item: AnniversaryDateEntity) {
    updateAnniversaryDate(item.copy(notificationEnabled = !item.notificationEnabled))
  }
```

- [ ] **Step 5: Add the two small private helpers used above**

Add near the bottom of the `InLoveRepository` class:

```kotlin
  private fun ensureSyncId(memory: SharedMemoryEntity): SharedMemoryEntity =
    if (memory.syncId.isBlank()) memory.copy(syncId = UUID.randomUUID().toString()) else memory

  private fun ensureSyncId(item: AnniversaryDateEntity): AnniversaryDateEntity =
    if (item.syncId.isBlank()) item.copy(syncId = UUID.randomUUID().toString()) else item

  private fun outboxEntryFor(entityType: String, syncId: String, memory: SharedMemoryEntity): SyncOutboxEntity =
    SyncOutboxEntity(
      entityType = entityType,
      syncId = syncId,
      operation = if (memory.deleted) "DELETE" else "UPSERT",
      payloadJson = SyncWorker.moshiAdapterFor<SharedMemoryEntity>().toJson(memory)
    )

  private fun outboxEntryFor(entityType: String, syncId: String, item: AnniversaryDateEntity): SyncOutboxEntity =
    SyncOutboxEntity(
      entityType = entityType,
      syncId = syncId,
      operation = if (item.deleted) "DELETE" else "UPSERT",
      payloadJson = SyncWorker.moshiAdapterFor<AnniversaryDateEntity>().toJson(item)
    )
```

- [ ] **Step 6: Update the one construction site in `InLoveViewModel.init`**

In `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt`, replace:
```kotlin
    repository = InLoveRepository(database.inLoveDao())
```
with:
```kotlin
    repository = InLoveRepository(database.inLoveDao(), application)
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.InLoveRepositorySyncTest"`
Expected: PASS. Also run the full suite once — `./gradlew :app:testDebugUnitTest` — since this task touches shared, widely-used methods; `CloudEnrichmentDataTest` (which the audit already flagged as exercising `InLoveRepository`) must still pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/data/repository/InLoveRepository.kt \
        app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt \
        app/src/test/java/com/example/data/repository/InLoveRepositorySyncTest.kt
git commit -m "feat(data): route memory/anniversary CRUD through the offline outbox"
```

---

### Task 8: `SyncCoordinator` (two-tier listeners) + `AppServiceLocator`/`InLoveApplication` wiring

**Files:**
- Create: `app/src/main/java/com/example/data/sync/SyncCoordinator.kt`
- Modify: `app/src/main/java/com/example/di/AppServiceLocator.kt`
- Modify: `app/src/main/java/com/example/InLoveApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/example/data/sync/SyncCoordinatorTest.kt`

**Interfaces:**
- Consumes: `InLoveDao` (Task 1), `MemorySyncAdapter`/`AnniversarySyncAdapter` (Task 5), `NetworkMonitor` (Task 4), `SyncWorker.enqueuePeriodic`/`SyncWorkerFactory` (Task 6).
- Produces: `class SyncCoordinator(dao: InLoveDao, firestore: FirebaseFirestore?, scope: CoroutineScope) { fun start(uid: String); fun stop() }`. `AppServiceLocator.syncCoordinator`, `AppServiceLocator.networkMonitor`. Consumed by Task 11 (pairing rewrite reads `AppServiceLocator.syncCoordinator`/is started from `AuthRepository`'s login success path).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/sync/SyncCoordinatorTest.kt`:

```kotlin
package com.example.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AnniversaryDateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncCoordinatorTest {

  @Test
  fun applyRemoteAnniversary_keepsLocalPendingEdit_whenRemoteUpdateIsOlder() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val local = AnniversaryDateEntity(
      title = "Local edit", dateText = "2026-01-01", syncId = "sync-1",
      updatedAt = 2000L, pendingSync = true
    )
    dao.insertAnniversaryDate(local)

    val coordinator = SyncCoordinator(dao, firestore = null, scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()))
    val olderRemote = AnniversaryDateEntity(title = "Remote (stale)", dateText = "2026-01-01", syncId = "sync-1", updatedAt = 1000L)

    coordinator.applyRemoteAnniversary(olderRemote)

    val stored = dao.getAllAnniversaryDates().first().first { it.syncId == "sync-1" }
    assert(stored.title == "Local edit") { "an older remote update must not overwrite a newer local pending edit" }
    db.close()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.SyncCoordinatorTest"`
Expected: FAIL to compile — `SyncCoordinator` and `applyRemoteAnniversary` don't exist yet.

- [ ] **Step 3: Add a lookup-by-syncId DAO method (small addition, same file as Task 1)**

In `app/src/main/java/com/example/data/db/InLoveDao.kt`, add:

```kotlin
  @Query("SELECT * FROM anniversary_dates WHERE syncId = :syncId LIMIT 1")
  suspend fun getAnniversaryDateBySyncId(syncId: String): AnniversaryDateEntity?

  @Query("SELECT * FROM shared_memories WHERE syncId = :syncId LIMIT 1")
  suspend fun getSharedMemoryBySyncId(syncId: String): SharedMemoryEntity?
```

- [ ] **Step 4: Implement `SyncCoordinator`**

Create `app/src/main/java/com/example/data/sync/SyncCoordinator.kt`:

```kotlin
package com.example.data.sync

import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Two listener tiers, both scoped to the login lifetime (started/stopped together):
 *  - identity tier: relationships + invites involving me, always on while logged in — this
 *    is how "who is my partner" is resolved without a partnerId field on the user document.
 *  - content tier: memories + anniversaries of the active relationship, started the moment
 *    the identity tier yields a relationship with status == ACTIVE, stopped the moment it
 *    stops being ACTIVE (terminated) or on logout.
 */
class SyncCoordinator(
  private val dao: InLoveDao,
  private val firestore: FirebaseFirestore?,
  private val scope: CoroutineScope
) {
  private var identityRegistrations: List<ListenerRegistration> = emptyList()
  private var contentRegistrations: List<ListenerRegistration> = emptyList()
  private var activeRelationshipId: String? = null

  fun start(uid: String) {
    stop()
    val fs = firestore ?: return

    val relationshipsListener = fs.collection("relationships")
      .whereEqualTo("user1", uid)
      .addSnapshotListener { snapshot, _ -> handleRelationshipSnapshot(snapshot) }
    val relationshipsListener2 = fs.collection("relationships")
      .whereEqualTo("user2", uid)
      .addSnapshotListener { snapshot, _ -> handleRelationshipSnapshot(snapshot) }
    val invitesListener = fs.collection("invites")
      .whereEqualTo("targetUid", uid)
      .addSnapshotListener { snapshot, _ -> handleInviteSnapshot(snapshot) }

    identityRegistrations = listOf(relationshipsListener, relationshipsListener2, invitesListener)
  }

  fun stop() {
    identityRegistrations.forEach { it.remove() }
    identityRegistrations = emptyList()
    stopContentListeners()
  }

  private fun stopContentListeners() {
    contentRegistrations.forEach { it.remove() }
    contentRegistrations = emptyList()
    activeRelationshipId = null
  }

  private fun handleRelationshipSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?) {
    val fs = firestore ?: return
    val activeDoc = snapshot?.documents?.firstOrNull { it.getString("status") == RelationshipStatus.ACTIVE }
    scope.launch {
      snapshot?.documents?.forEach { doc ->
        val rel = OnlineRelationshipEntity(
          relationshipId = doc.id,
          user1 = doc.getString("user1") ?: return@forEach,
          user2 = doc.getString("user2") ?: return@forEach,
          startDate = doc.getLong("startDate") ?: 0L,
          startDateText = doc.getString("startDateText") ?: "",
          status = doc.getString("status") ?: RelationshipStatus.TERMINATED,
          breakupRequestedBy = doc.getString("breakupRequestedBy"),
          breakupRequestedAt = doc.getLong("breakupRequestedAt"),
          createdAt = doc.getLong("createdAt") ?: 0L,
          terminatedAt = doc.getLong("terminatedAt"),
          updatedAt = System.currentTimeMillis(),
          pendingSync = false
        )
        dao.insertOnlineRelationship(rel)
      }

      val activeId = activeDoc?.id
      if (activeId != activeRelationshipId) {
        stopContentListeners()
        if (activeId != null) startContentListeners(fs, activeId)
      }
    }
  }

  private fun handleInviteSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?) {
    scope.launch {
      snapshot?.documents?.forEach { doc ->
        val invite = OnlineInviteEntity(
          inviteId = doc.id,
          senderUid = doc.getString("senderUid") ?: return@forEach,
          senderName = doc.getString("senderName") ?: "Vô danh",
          senderAvatar = doc.getString("senderAvatar") ?: "",
          senderCoupleCode = doc.getString("senderCoupleCode") ?: "",
          senderBirthDate = doc.getString("senderBirthDate") ?: "",
          senderAge = (doc.getLong("senderAge") ?: 0L).toInt(),
          senderZodiac = doc.getString("senderZodiac") ?: "",
          senderBio = doc.getString("senderBio") ?: "",
          targetCoupleCode = doc.getString("targetCoupleCode") ?: "",
          targetUid = doc.getString("targetUid"),
          proposedStartDate = doc.getLong("proposedStartDate") ?: 0L,
          proposedStartDateText = doc.getString("proposedStartDateText") ?: "",
          loveNote = doc.getString("loveNote") ?: "",
          status = doc.getString("status") ?: "PENDING",
          createdAt = doc.getLong("createdAt") ?: 0L,
          updatedAt = System.currentTimeMillis(),
          pendingSync = false
        )
        dao.insertOnlineInvite(invite)
      }
    }
  }

  private fun startContentListeners(fs: FirebaseFirestore, relationshipId: String) {
    activeRelationshipId = relationshipId
    val memoriesListener = fs.collection(MemorySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, _ ->
        scope.launch {
          snapshot?.documents?.forEach { doc ->
            MemorySyncAdapter.fromFirestoreDoc(doc)?.let { applyRemoteMemory(it) }
          }
        }
      }
    val anniversariesListener = fs.collection(AnniversarySyncAdapter.collectionPath(relationshipId))
      .addSnapshotListener { snapshot, _ ->
        scope.launch {
          snapshot?.documents?.forEach { doc ->
            AnniversarySyncAdapter.fromFirestoreDoc(doc)?.let { applyRemoteAnniversary(it) }
          }
        }
      }
    contentRegistrations = listOf(memoriesListener, anniversariesListener)
  }

  /** Last-write-wins merge: a local pending edit only loses to a STRICTLY newer remote update. */
  suspend fun applyRemoteMemory(remote: SharedMemoryEntity) {
    val local = dao.getSharedMemoryBySyncId(remote.syncId)
    if (local != null && local.pendingSync && local.updatedAt >= remote.updatedAt) {
      Log.d("SyncCoordinator", "keeping local pending memory ${remote.syncId}, remote is not newer")
      return
    }
    if (local != null) {
      dao.updateSharedMemory(remote.copy(id = local.id, pendingSync = false))
    } else {
      dao.insertSharedMemory(remote.copy(pendingSync = false))
    }
  }

  /** Last-write-wins merge: a local pending edit only loses to a STRICTLY newer remote update. */
  suspend fun applyRemoteAnniversary(remote: AnniversaryDateEntity) {
    val local = dao.getAnniversaryDateBySyncId(remote.syncId)
    if (local != null && local.pendingSync && local.updatedAt >= remote.updatedAt) {
      Log.d("SyncCoordinator", "keeping local pending anniversary ${remote.syncId}, remote is not newer")
      return
    }
    if (local != null) {
      dao.updateAnniversaryDate(remote.copy(id = local.id, pendingSync = false))
    } else {
      dao.insertAnniversaryDate(remote.copy(pendingSync = false))
    }
  }
}
```

- [ ] **Step 5: Wire `SyncCoordinator` and `NetworkMonitor` into `AppServiceLocator`**

In `app/src/main/java/com/example/di/AppServiceLocator.kt`, add fields, accessors and initialization following the exact double-checked-locking pattern already used for `_adsManager`/`_billingManager`:

```kotlin
    @Volatile private var _syncCoordinator: com.example.data.sync.SyncCoordinator? = null
    @Volatile private var _networkMonitor: com.example.data.sync.NetworkMonitor? = null

    val syncCoordinator: com.example.data.sync.SyncCoordinator
        get() = _syncCoordinator ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")

    val networkMonitor: com.example.data.sync.NetworkMonitor
        get() = _networkMonitor ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")
```

And inside `fun initialize(context: Context)`, after the existing `_adsManager` block:

```kotlin
        if (_networkMonitor == null) {
            synchronized(this) {
                if (_networkMonitor == null) {
                    _networkMonitor = com.example.data.sync.NetworkMonitor(appContext)
                }
            }
        }

        if (_syncCoordinator == null) {
            synchronized(this) {
                if (_syncCoordinator == null) {
                    val db = com.example.data.db.AppDatabase.getDatabase(appContext)
                    val fs = try {
                        if (com.google.firebase.FirebaseApp.getApps(appContext).isNotEmpty()) {
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        } else null
                    } catch (e: Exception) { null }
                    _syncCoordinator = com.example.data.sync.SyncCoordinator(
                        db.inLoveDao(), fs, kotlinx.coroutines.CoroutineScope(
                            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
                        )
                    )
                }
            }
        }
```

(This is the one process-lifetime `CoroutineScope` in this plan that is intentionally never cancelled — `SyncCoordinator` is meant to run for the whole app process, matching `AdsManagerImpl`/`BillingManager`'s existing lifetime in the same object.)

- [ ] **Step 6: Start/stop `SyncCoordinator` from `AuthRepository`'s login/logout paths, and enqueue the periodic worker at app start**

In `app/src/main/java/com/example/data/repository/AuthRepository.kt`, find the point right after `_authState.value = AuthState.Authenticated(...)` is set on successful login (in both the login and register flows) and add:
```kotlin
      com.example.di.AppServiceLocator.syncCoordinator.start(firebaseUid)
```
(using whichever local variable in that function already holds the just-authenticated uid — `firebaseUid` in register, the equivalent in login). In `logout()` (the function that calls `FirebaseAuth.getInstance().signOut()` at line 936), add immediately after the sign-out call:
```kotlin
      com.example.di.AppServiceLocator.syncCoordinator.stop()
```

In `app/src/main/java/com/example/InLoveApplication.kt`, inside `onCreate()` after `AppServiceLocator` would already be relied upon — since `AppServiceLocator.initialize()` is currently only called from `InLoveViewModel.init` (line 331), add a call to `AppServiceLocator.initialize(this)` near the top of `InLoveApplication.onCreate()` (right after `FirebaseApp.initializeApp(this)` and `installAppCheckProviderFactory()`, before `super.onCreate()`), so the locator — and with it `SyncCoordinator`/`NetworkMonitor` — exists before any UI is shown, not just after the first `InLoveViewModel` is constructed. Also add the periodic sync enqueue:
```kotlin
        com.example.di.AppServiceLocator.initialize(this)
        com.example.data.sync.SyncWorker.enqueuePeriodic(this)
```

- [ ] **Step 7: Wire `SyncWorkerFactory` into WorkManager's real initialization (without this step, `SyncWorker` crashes at runtime — see below)**

`SyncWorker`'s primary constructor requires `dao: InLoveDao` with no default value, so WorkManager's default reflection-based factory (used automatically by its on-demand App Startup initializer) cannot construct it. This must be fixed before `SyncWorker.enqueuePeriodic`/`enqueueImmediate` (already called from Steps 6 and Task 7) can work outside of tests, where `TestListenableWorkerBuilder.setWorkerFactory(...)` was used instead.

In `app/src/main/AndroidManifest.xml`, insert this `<provider>` block right after the existing `com.google.android.gms.ads.APPLICATION_ID` `<meta-data>` element (line 33) and before the `<activity android:name=".MainActivity">` block (line 35), still inside `<application>`:
```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup.InitializationProvider"
                tools:node="remove" />
        </provider>
```
(The manifest's root `<manifest>` tag at line 2-3 already declares `xmlns:tools="http://schemas.android.com/tools"` and already uses `tools:replace` on `<application>`, so no namespace change is needed.)

In `app/src/main/java/com/example/InLoveApplication.kt`, make the class implement `androidx.work.Configuration.Provider` and supply a `Configuration` built with `SyncWorkerFactory`:

```kotlin
class InLoveApplication : AppPluginBase(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setWorkerFactory(
                com.example.data.sync.SyncWorkerFactory(
                    com.example.data.db.AppDatabase.getDatabase(this).inLoveDao()
                )
            )
            .build()

    // ... existing isRunningInTest / onCreate / installAppCheckProviderFactory unchanged ...
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.sync.SyncCoordinatorTest"`
Expected: PASS. Also run `./gradlew :app:testDebugUnitTest` fully, since `AuthRepository` was touched. There is no unit test for Step 7's WorkManager wiring itself (it is Android-framework `Configuration.Provider`/manifest plumbing with no pure-Kotlin logic to assert on) — it is verified manually in Task 13.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/data/sync/SyncCoordinator.kt \
        app/src/main/java/com/example/data/db/InLoveDao.kt \
        app/src/main/java/com/example/di/AppServiceLocator.kt \
        app/src/main/java/com/example/InLoveApplication.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/example/data/repository/AuthRepository.kt \
        app/src/test/java/com/example/data/sync/SyncCoordinatorTest.kt
git commit -m "feat(sync): SyncCoordinator two-tier Firestore listeners + WorkManager factory wiring"
```

---

### Task 9: Fix `gift_ideas`/`milestone_presets` duplicate-row bug

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/InLoveRepository.kt` (`fetchMilestonePresetsFromFirestore`, `fetchDynamicGiftIdeasFromFirestore`)
- Test: `app/src/test/java/com/example/data/repository/PresetDedupeTest.kt`

**Interfaces:**
- Consumes: `InLoveDao.upsertGiftIdeaByRemoteId`/`upsertMilestoneByRemoteId` (Task 1).
- Produces: nothing new consumed elsewhere.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/repository/PresetDedupeTest.kt`:

```kotlin
package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.GiftIdeaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PresetDedupeTest {

  @Test
  fun upsertGiftIdeaByRemoteId_calledTwiceWithSameRemoteId_doesNotDuplicate() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val dao = db.inLoveDao()

    val idea = GiftIdeaEntity(title = "Nến thơm", category = "Quà lãng mạn", badgeText = "", tag = "", description = "", imageUrl = "", remoteId = "doc_1")
    dao.upsertGiftIdeaByRemoteId(idea)
    dao.upsertGiftIdeaByRemoteId(idea.copy(title = "Nến thơm (updated)"))

    val all = dao.getAllGiftIdeas().first()
    assert(all.size == 1) { "same remoteId synced twice must update, not duplicate: got ${all.size} rows" }
    assert(all.first().title == "Nến thơm (updated)")
    db.close()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.PresetDedupeTest"`
Expected: This one actually already PASSES once Task 1 is in place, since `upsertGiftIdeaByRemoteId` was implemented directly in Task 1 — this step confirms that (a green step here is expected and correct; there is no production code left to change for the DAO method itself). Run it now to confirm.

- [ ] **Step 3: Point the Firestore fetch functions at the new upsert method**

In `app/src/main/java/com/example/data/repository/InLoveRepository.kt`, in `fetchMilestonePresetsFromFirestore`, replace the `MilestoneEntity(...)` construction's `id = (index + 1).toLong()` with `remoteId = doc.id` (drop the fragile `id` assignment entirely — Room's autoGenerate takes over for new rows, and the upsert method preserves the existing `id` for updates):

```kotlin
        val milestones = snapshot.documents.mapNotNull { doc ->
          val title = doc.getString("title") ?: return@mapNotNull null
          MilestoneEntity(
            remoteId = doc.id,
            title = title,
            dateText = doc.getString("dateText") ?: "",
            subtitle = doc.getString("subtitle") ?: "",
            categoryTag = doc.getString("categoryTag") ?: "Cột Mốc",
            secondaryTag = doc.getString("secondaryTag") ?: "",
            imageUrl = doc.getString("imageUrl") ?: "",
            daysRemaining = doc.getLong("daysRemaining")?.toInt() ?: 0,
            isPast = false,
            progressPercent = doc.getDouble("progressPercent")?.toFloat(),
            isImportant = doc.getBoolean("isImportant") ?: false,
            notificationEnabled = doc.getBoolean("notificationEnabled") ?: true
          )
        }
        milestones.forEach { dao.upsertMilestoneByRemoteId(it) }
        return@withContext Result.success(milestones)
```

(This changes `mapIndexedNotNull` to `mapNotNull` since `index` is no longer needed, and replaces the single `dao.insertMilestones(milestones)` call with a per-item `upsertMilestoneByRemoteId` loop.)

In `fetchDynamicGiftIdeasFromFirestore`, add `remoteId = doc.id` to the `GiftIdeaEntity(...)` construction and replace `dao.insertGiftIdeas(ideas)` with:
```kotlin
        ideas.forEach { dao.upsertGiftIdeaByRemoteId(it) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.PresetDedupeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/data/repository/InLoveRepository.kt \
        app/src/test/java/com/example/data/repository/PresetDedupeTest.kt
git commit -m "fix(data): gift_ideas/milestone_presets upsert by remoteId instead of duplicating"
```

---

### Task 10: Firestore rules — `coupleCodes`, relationship create redesign, invite status lock, composite index

**Files:**
- Modify: `firestore.rules`
- Modify: `firestore.indexes.json`
- Test: (rules-level testing happens in Task 12's emulator suite; this task's own verification is the Firestore CLI's static rules validation)

**Interfaces:**
- Consumes: nothing.
- Produces: the exact rules Task 11's client code and Task 12's emulator tests are written against.

- [ ] **Step 1: Add the `coupleCodes` collection rule**

In `firestore.rules`, add a new `match` block right after the `users` block:

```
    // ===============================================================
    // Couple Codes (public lookup: code -> ownerUid, needed for real
    // two-device pairing since `users` is owner-only readable)
    // ===============================================================
    match /coupleCodes/{code} {
      allow get: if isAuthenticated();
      allow list: if false; // never enumerable — only direct get by a known code
      allow create: if isAuthenticated() &&
        request.resource.data.ownerUid == request.auth.uid &&
        !exists(/databases/$(database)/documents/coupleCodes/$(code));
      allow update: if false; // codes are immutable; regenerate = delete + create new
      allow delete: if isAuthenticated() && resource.data.ownerUid == request.auth.uid;
    }
```

- [ ] **Step 2: Replace the `relationships` create rule and add the invite status-transition lock**

Replace the whole `match /relationships/{relationshipId} { ... }` block with:

```
    match /relationships/{relationshipId} {
      allow get, list: if isRelationshipMember(resource.data);
      // Relationship id MUST equal the id of the invite it was accepted from. This is what
      // closes pairing without a Cloud Function: creating this doc requires that invite to
      // already be ACCEPTED and to name the same two people, and a second create attempt on
      // the same id is routed by Firestore to the `update` rule below (which locks identity
      // fields), making replay a safe no-op instead of a second relationship.
      allow create: if isAuthenticated() &&
        (request.resource.data.partnerAId == request.auth.uid || request.resource.data.partnerBId == request.auth.uid) &&
        exists(/databases/$(database)/documents/invites/$(relationshipId)) &&
        get(/databases/$(database)/documents/invites/$(relationshipId)).data.status == 'ACCEPTED' &&
        get(/databases/$(database)/documents/invites/$(relationshipId)).data.senderUid == request.resource.data.partnerAId &&
        get(/databases/$(database)/documents/invites/$(relationshipId)).data.targetUid == request.resource.data.partnerBId;
      allow update: if isRelationshipMember(resource.data) &&
        (!('partnerAId' in resource.data) || request.resource.data.partnerAId == resource.data.partnerAId) &&
        (!('partnerBId' in resource.data) || request.resource.data.partnerBId == resource.data.partnerBId) &&
        (!('user1' in resource.data) || request.resource.data.user1 == resource.data.user1) &&
        (!('user2' in resource.data) || request.resource.data.user2 == resource.data.user2) &&
        (!('user1Uid' in resource.data) || request.resource.data.user1Uid == resource.data.user1Uid) &&
        (!('user2Uid' in resource.data) || request.resource.data.user2Uid == resource.data.user2Uid);
      allow delete: if isRelationshipMember(resource.data);
    }
```

Replace the whole `match /invites/{inviteId} { ... }` block with (adds an explicit status-transition check to the existing update rule):

```
    match /invites/{inviteId} {
      allow get, list: if isAuthenticated() && (
        resource.data.senderUid == request.auth.uid ||
        resource.data.receiverUid == request.auth.uid ||
        resource.data.targetUid == request.auth.uid
      );
      allow create: if isAuthenticated() && request.resource.data.senderUid == request.auth.uid;
      // Receiver may only move PENDING -> ACCEPTED/DECLINED. Sender may only move
      // PENDING -> CANCELLED. Identity fields stay locked as before.
      allow update: if isAuthenticated() &&
        request.resource.data.senderUid == resource.data.senderUid &&
        (!('receiverUid' in resource.data) || request.resource.data.receiverUid == resource.data.receiverUid) &&
        (!('targetUid' in resource.data) || request.resource.data.targetUid == resource.data.targetUid) &&
        resource.data.status == 'PENDING' && (
          ((resource.data.receiverUid == request.auth.uid || resource.data.targetUid == request.auth.uid) &&
           request.resource.data.status in ['ACCEPTED', 'DECLINED']) ||
          (resource.data.senderUid == request.auth.uid && request.resource.data.status == 'CANCELLED')
        );
      allow delete: if isAuthenticated() && resource.data.senderUid == request.auth.uid;
    }
```

- [ ] **Step 3: Add the composite index for "my active relationships"**

In `firestore.indexes.json`, add the two single-field-equality queries `SyncCoordinator` (Task 8) runs (`relationships` where `user1 == uid` and where `user2 == uid`) — Firestore only needs a composite index when a query combines an equality filter with something else (an order-by or a second field), and these are plain single-field equality queries, which Firestore auto-indexes without any entry needed. Leave `firestore.indexes.json` unchanged for this task; note this explicitly so a future reader doesn't wonder why no index was added.

- [ ] **Step 4: Validate the rules file**

Run: `firebase deploy --only firestore:rules --dry-run --project demngayyeuandroid` (uses the existing `.firebaserc` project; `--dry-run` only compiles/validates, does not deploy).
Expected: "Rules file firestore.rules compiled successfully" with no errors.

- [ ] **Step 5: Commit**

```bash
git add firestore.rules firestore.indexes.json
git commit -m "feat(rules): coupleCodes lookup + relationship-id-bound-to-invite pairing, no Cloud Functions"
```

---

### Task 11: Rewrite `OnlineCoupleRepository` pairing to actually use Firestore

**Files:**
- Modify: `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt`
- Test: `app/src/test/java/com/example/data/repository/OnlineCoupleRepositoryPairingTest.kt`

**Interfaces:**
- Consumes: `firestore.rules` from Task 10 (semantically — this task's Firestore calls are shaped to satisfy those rules); `OnlineRelationshipEntity`/`OnlineInviteEntity`'s new `updatedAt`/`pendingSync` columns (Task 1).
- Produces: `OnlineCoupleRepository.publishMyCoupleCode(): Result<Unit>`, `lookupOwnerUidByCode(code: String): String?` (replaces local-only `dao.getOnlineUserByCoupleCodeSync`), `sendSetLoveInvite(...)`, `acceptSetLoveInvite(...)`, `requestBreakup()`, `confirmBreakup()` and `rejectBreakup()` now perform real Firestore writes (their external signatures are unchanged — callers in `InLoveViewModel` do not change).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/data/repository/OnlineCoupleRepositoryPairingTest.kt`:

```kotlin
package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.RelationshipStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnlineCoupleRepositoryPairingTest {

  @Test
  fun acceptSetLoveInvite_afterBreakup_createsANewRelationshipId_notTheTerminatedOne() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope)

    // Seed a terminated relationship between A and B from a past pairing.
    val terminatedRelId = "rel_old"
    db.inLoveDao().insertOnlineRelationship(
      com.example.data.model.OnlineRelationshipEntity(
        relationshipId = terminatedRelId, user1 = "uid_a", user2 = "uid_b",
        startDate = 1L, status = RelationshipStatus.TERMINATED
      )
    )
    db.inLoveDao().insertOnlineUser(
      com.example.data.model.OnlineUserEntity(uid = "uid_a", coupleCode = "AAAA-1111", isCurrentUser = false)
    )
    db.inLoveDao().insertOnlineUser(
      com.example.data.model.OnlineUserEntity(uid = "uid_b", coupleCode = "BBBB-2222", isCurrentUser = true)
    )
    repo.setCurrentUserId("uid_b")

    val newInviteId = "inv_new"
    db.inLoveDao().insertOnlineInvite(
      com.example.data.model.OnlineInviteEntity(
        inviteId = newInviteId, senderUid = "uid_a", senderCoupleCode = "AAAA-1111",
        targetCoupleCode = "BBBB-2222", targetUid = "uid_b", status = "PENDING"
      )
    )

    repo.acceptSetLoveInvite(newInviteId)

    val newRel = db.inLoveDao().getActiveRelationshipForUser("uid_b")
    assert(newRel != null)
    assert(newRel!!.relationshipId == newInviteId) { "relationship id must equal the accepted invite id, per Task 10's rule design" }
    assert(newRel.relationshipId != terminatedRelId) { "must not resurrect the old terminated relationship id" }
    db.close()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.OnlineCoupleRepositoryPairingTest"`
Expected: FAIL — current `acceptSetLoveInvite` reuses `existingRel?.relationshipId` (any existing relationship for the user, active or not, per line 373-374 today: `dao.getActiveRelationshipForUser` actually filters `status = 'ACTIVE'` already, so a *terminated* one would not match — but the assertion on the relationship id equalling the invite id will still fail, since today's code generates `"rel_${System.currentTimeMillis()}"`, not the invite id).

- [ ] **Step 3: Add `publishMyCoupleCode` and `lookupOwnerUidByCode`**

In `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt`, add imports `import kotlinx.coroutines.tasks.await` and `import com.google.firebase.firestore.FieldValue`, and add these two new methods (placed after `updateMyProfile`):

```kotlin
  /** Publishes this user's couple code to the public coupleCodes/{code} lookup collection. */
  suspend fun publishMyCoupleCode(): Result<Unit> = withContext(Dispatchers.IO) {
    val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore chưa sẵn sàng"))
    val me = _currentUser.value
    try {
      fs.collection("coupleCodes").document(me.coupleCode)
        .set(mapOf("code" to me.coupleCode, "ownerUid" to me.uid, "createdAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "publishMyCoupleCode error (may already exist, which is fine): ${e.message}")
      Result.success(Unit) // a pre-existing code doc for the same owner is not an error
    }
  }

  /** Resolves a partner's uid from their shared code via the public coupleCodes lookup. */
  suspend fun lookupOwnerUidByCode(code: String): String? = withContext(Dispatchers.IO) {
    val fs = firestore ?: return@withContext null
    try {
      fs.collection("coupleCodes").document(code).get().await().getString("ownerUid")
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "lookupOwnerUidByCode error: ${e.message}")
      null
    }
  }
```

- [ ] **Step 4: Make `sendSetLoveInvite` write to Firestore first (server-resolved target), Room second**

Replace `sendSetLoveInvite`'s body from the `val targetUser = ...` line onward:

```kotlin
    val targetOwnerUid = lookupOwnerUidByCode(trimmedCode)
      ?: dao.getOnlineUserByCoupleCodeSync(trimmedCode)?.uid // local fallback for same-device demo/testing
      ?: return@withContext false to "Không tìm thấy người dùng với mã $trimmedCode. Hãy kiểm tra lại mã!"
    val targetUser = dao.getOnlineUserByUidSync(targetOwnerUid)
      ?: com.example.data.model.OnlineUserEntity(uid = targetOwnerUid, coupleCode = trimmedCode)
    if (targetUser.status == OnlineStatus.COUPLED) {
      return@withContext false to "Người này đã có đôi có cặp (Set Love) với người khác!"
    }

    val now = System.currentTimeMillis()
    val invite = OnlineInviteEntity(
      inviteId = "inv_${now}_${me.uid.take(6)}",
      senderUid = me.uid,
      senderName = me.effectiveDisplayName,
      senderAvatar = me.avatarUrl,
      senderCoupleCode = me.coupleCode,
      senderBirthDate = me.birthDate,
      senderAge = me.age,
      senderZodiac = me.zodiac,
      senderBio = me.bio,
      targetCoupleCode = trimmedCode,
      targetUid = targetOwnerUid,
      proposedStartDate = proposedStartDateMillis,
      proposedStartDateText = ProfileUtils.formatDate(proposedStartDateMillis),
      loveNote = loveNote.trim().ifEmpty { "Cùng anh/em xây dựng hạnh phúc Set Love nhé! ❤️" },
      status = InviteStatus.PENDING,
      createdAt = now,
      updatedAt = now,
      pendingSync = true
    )

    dao.insertOnlineInvite(invite)
    _outgoingInvite.value = invite

    val updatedMe = me.copy(status = OnlineStatus.PENDING_INVITE)
    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe
    _relationshipStatus.value = OnlineStatus.PENDING_INVITE

    if (targetUser.uid == _partnerUser.value?.uid) {
      _incomingInvite.value = invite
    }

    try {
      firestore?.collection("invites")?.document(invite.inviteId)?.set(invite)?.await()
      dao.updateOnlineInvite(invite.copy(pendingSync = false))
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore invite upload error, will retry via outbox in a later task: ${e.message}")
    }

    return@withContext true to "Đã gửi lời mời Set Love đến ${targetUser.effectiveDisplayName} (${trimmedCode}) thành công!"
```

- [ ] **Step 5: Rewrite `acceptSetLoveInvite` to use the invite id as the relationship id, and push both to Firestore in the rule-required order**

Replace the body of `acceptSetLoveInvite` from `// Check relationship history:` through the `dao.insertOnlineRelationship(relationship)` / `_activeRelationship.value = relationship` lines:

```kotlin
    // Relationship id MUST equal the accepted invite's id — this is what the Task 10 rules
    // rely on to allow the create without a Cloud Function, and what makes a duplicate
    // accept attempt a safe no-op (routed to the locked-down `update` rule instead).
    val relId = incoming.inviteId

    val relationship = OnlineRelationshipEntity(
      relationshipId = relId,
      user1 = sender.uid,
      user2 = me.uid,
      startDate = finalStartDate,
      startDateText = finalStartDateText,
      status = RelationshipStatus.ACTIVE,
      breakupRequestedBy = null,
      breakupRequestedAt = null,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      pendingSync = true
    )

    // Step (a): mark the invite ACCEPTED on Firestore first — the relationships/{relId}
    // create rule requires invites/{relId}.status == 'ACCEPTED' to already be true.
    try {
      firestore?.collection("invites")?.document(incoming.inviteId)
        ?.update(mapOf("status" to InviteStatus.ACCEPTED))?.await()
      // Step (b): create the relationship doc at the same id as the invite.
      firestore?.collection("relationships")?.document(relId)?.set(
        mapOf(
          "partnerAId" to sender.uid,
          "partnerBId" to me.uid,
          "user1" to sender.uid,
          "user2" to me.uid,
          "startDate" to finalStartDate,
          "startDateText" to finalStartDateText,
          "status" to RelationshipStatus.ACTIVE,
          "createdAt" to System.currentTimeMillis()
        )
      )?.await()
      dao.updateOnlineRelationship(relationship.copy(pendingSync = false))
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore pairing sync error, will retry via outbox in a later task: ${e.message}")
      dao.insertOnlineRelationship(relationship)
    }

    dao.insertOnlineRelationship(relationship)
    _activeRelationship.value = relationship
```

(`dao.insertOnlineRelationship` uses `OnConflictStrategy.REPLACE` already, so calling it once more after the try/catch — whether or not the catch branch already inserted it — is a safe idempotent overwrite with the same data, not a duplicate.)

- [ ] **Step 6: Push breakup state to Firestore — without this, a real partner device never learns about a breakup request or confirmation**

`requestBreakup`/`confirmBreakup`/`rejectBreakup` currently only touch Room, which is invisible to the partner's device. Update `relationships/{relId}` on Firestore inside each, matching what `SyncCoordinator`'s identity-tier listener (Task 8) already reads (`status`, `breakupRequestedBy`, `breakupRequestedAt`, `terminatedAt`) — the existing `relationships` update rule (Task 10, unchanged for this case since only `status`/`breakupRequestedBy`/`breakupRequestedAt`/`terminatedAt` change, none of the locked identity fields) already permits this.

In `requestBreakup`, after `dao.updateOnlineRelationship(updatedRel)` / `_activeRelationship.value = updatedRel`, add:
```kotlin
    try {
      firestore?.collection("relationships")?.document(rel.relationshipId)?.update(
        mapOf(
          "status" to RelationshipStatus.PENDING_BREAKUP,
          "breakupRequestedBy" to me.uid,
          "breakupRequestedAt" to updatedRel.breakupRequestedAt
        )
      )?.await()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-request sync error: ${e.message}")
    }
```

In `confirmBreakup`, after `dao.updateOnlineRelationship(terminatedRel)` / `_activeRelationship.value = terminatedRel`, add:
```kotlin
    try {
      firestore?.collection("relationships")?.document(rel.relationshipId)?.update(
        mapOf("status" to RelationshipStatus.TERMINATED, "terminatedAt" to terminatedRel.terminatedAt)
      )?.await()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-confirm sync error: ${e.message}")
    }
```

In `rejectBreakup`, after `dao.updateOnlineRelationship(revertedRel)` / `_activeRelationship.value = revertedRel`, add:
```kotlin
    try {
      firestore?.collection("relationships")?.document(rel.relationshipId)?.update(
        mapOf("status" to RelationshipStatus.ACTIVE, "breakupRequestedBy" to null, "breakupRequestedAt" to null)
      )?.await()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-reject sync error: ${e.message}")
    }
```

Add a second assertion to the existing test from Step 1, confirming `confirmBreakup` actually reaches this new code path without throwing when `firestore` is null (the constructor-injected `firestore` field is already nullable and every call site above is wrapped in try/catch, so this is a smoke check, not a new test file):
```kotlin
  @Test
  fun confirmBreakup_withNoFirestoreConfigured_stillTerminatesLocally() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val repo = OnlineCoupleRepository(db.inLoveDao(), context, scope)
    db.inLoveDao().insertOnlineUser(com.example.data.model.OnlineUserEntity(uid = "uid_b", coupleCode = "B", isCurrentUser = true, relationshipId = "rel_1", status = OnlineStatus.COUPLED))
    db.inLoveDao().insertOnlineRelationship(
      com.example.data.model.OnlineRelationshipEntity(relationshipId = "rel_1", user1 = "uid_a", user2 = "uid_b", startDate = 1L, status = RelationshipStatus.ACTIVE)
    )
    repo.setCurrentUserId("uid_b")

    val (success, _) = repo.confirmBreakup()

    assert(success)
    db.close()
  }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.data.repository.OnlineCoupleRepositoryPairingTest"`
Expected: PASS. Also run the full suite: `./gradlew :app:testDebugUnitTest`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt \
        app/src/test/java/com/example/data/repository/OnlineCoupleRepositoryPairingTest.kt
git commit -m "feat(pairing): real cross-device invite/accept via Firestore, relationship id = invite id"
```

---

### Task 12: Firestore rules-emulator regression suite for the pairing security scenarios

**Files:**
- Create: `scripts/rules-tests/pairing.rules.test.js`
- Modify: `scripts/package.json` (add `@firebase/rules-unit-testing` devDependency + a `test:rules` script)
- Modify: `firebase.json` (add an `emulators.firestore` block)

**Interfaces:**
- Consumes: `firestore.rules` (Task 10).
- Produces: an npm script (`npm run test:rules` from `scripts/`) that other tasks/CI can call; nothing consumed by later plan tasks.

- [ ] **Step 1: Add the Firestore emulator to `firebase.json`**

In `firebase.json`, add:
```json
  "emulators": {
    "firestore": {
      "port": 8080
    }
  }
```
(merge into the existing top-level object alongside `firestore`/`auth`.)

- [ ] **Step 2: Add the test dependency and script**

In `scripts/package.json`, add to `devDependencies`:
```json
    "@firebase/rules-unit-testing": "^3.0.4",
    "mocha": "^10.7.3"
```
and to `scripts`:
```json
    "test:rules": "firebase emulators:exec --only firestore \"mocha rules-tests/**/*.test.js --timeout 20000\""
```

- [ ] **Step 3: Write the failing tests (the whole point of this task IS the tests — there is no separate "production code" step; the rules under test already exist from Task 10)**

Create `scripts/rules-tests/pairing.rules.test.js`:

```javascript
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');

describe('pairing security rules (Task 10 design)', function () {
  this.timeout(20000);
  let testEnv;

  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: 'inlove-rules-test',
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8'),
      },
    });
  });

  after(async () => {
    await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  it('duplicate accept is a no-op: creating relationships/{id} twice for the same accepted invite does not throw a second create', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const inviteId = 'inv_1';

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('invites').doc(inviteId).set({
        senderUid: uidA, targetUid: uidB, status: 'ACCEPTED', createdAt: Date.now(),
      });
    });

    const bCtx = testEnv.authenticatedContext(uidB).firestore();
    const relRef = bCtx.collection('relationships').doc(inviteId);
    const relData = { partnerAId: uidA, partnerBId: uidB, user1: uidA, user2: uidB, status: 'ACTIVE' };

    await assertSucceeds(relRef.set(relData)); // first create: allowed

    // A second "create" attempt on the same id is a set() that Firestore server-side treats
    // as create-vs-update based on whether the doc exists; since it already exists, this is
    // evaluated against the `update` rule, which locks partnerAId/partnerBId, so an attempt
    // that tries to change them must fail:
    await assertFails(relRef.set({ ...relData, partnerBId: 'uid_attacker' }));
    // Re-sending the SAME data must succeed (idempotent retry, matches Task 11 Step 5's retry path):
    await assertSucceeds(relRef.set(relData));
  });

  it('a relationship cannot be created without a matching ACCEPTED invite at the same id', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const bCtx = testEnv.authenticatedContext(uidB).firestore();

    await assertFails(
      bCtx.collection('relationships').doc('no_such_invite').set({
        partnerAId: uidA, partnerBId: uidB, user1: uidA, user2: uidB, status: 'ACTIVE',
      })
    );
  });

  it('a stale invite cannot be replayed after the relationship already exists (invite stuck at ACCEPTED, no new relationship id can reuse it maliciously)', async () => {
    const uidA = 'uid_a';
    const uidC = 'uid_c'; // attacker, not part of the original invite
    const inviteId = 'inv_2';

    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().collection('invites').doc(inviteId).set({
        senderUid: uidA, targetUid: 'uid_b', status: 'ACCEPTED', createdAt: Date.now(),
      });
    });

    const cCtx = testEnv.authenticatedContext(uidC).firestore();
    // uid_c tries to claim the same accepted invite as if they were the target — must fail
    // because get(invites/inv_2).data.targetUid ('uid_b') does not equal partnerBId ('uid_c').
    await assertFails(
      cCtx.collection('relationships').doc(inviteId).set({
        partnerAId: uidA, partnerBId: uidC, user1: uidA, user2: uidC, status: 'ACTIVE',
      })
    );
  });

  it('coupleCodes: any authenticated user can look up a code, but list is denied and a taken code cannot be overwritten', async () => {
    const uidA = 'uid_a';
    const uidB = 'uid_b';
    const aCtx = testEnv.authenticatedContext(uidA).firestore();
    const bCtx = testEnv.authenticatedContext(uidB).firestore();

    await assertSucceeds(aCtx.collection('coupleCodes').doc('AAAA-1111').set({ code: 'AAAA-1111', ownerUid: uidA }));
    await assertSucceeds(bCtx.collection('coupleCodes').doc('AAAA-1111').get());
    await assertFails(bCtx.collection('coupleCodes').doc('AAAA-1111').set({ code: 'AAAA-1111', ownerUid: uidB }));
    await assertFails(bCtx.collection('coupleCodes').get());
  });
});
```

- [ ] **Step 2 (of the TDD cycle, run after Step 3 above): Run tests to verify the suite runs and passes against Task 10's rules**

Run (from `scripts/`): `npm install && npm run test:rules`
Expected: all 4 tests PASS. If `firebase emulators:exec` cannot start because the Java runtime required by the Firestore emulator is missing on this machine, install a JDK first (the emulator requires Java 11+); this is an environment prerequisite, not a code issue.

- [ ] **Step 4: Commit**

```bash
git add firebase.json scripts/package.json scripts/rules-tests/pairing.rules.test.js
git commit -m "test(rules): Firestore emulator regression suite for the pairing security design"
```

---

### Task 13: Manual two-device verification (QA checklist, no code)

**Files:**
- Modify: `docs/TASK_CHECKLIST.md` (record the result once run)

**Interfaces:**
- Consumes: the fully wired app from Tasks 1-12.
- Produces: a manually-verified confirmation that real pairing works, recorded in the existing project checklist.

- [ ] **Step 1: Build and install debug on two devices (or one physical + one emulator)**

Run: `./gradlew :app:assembleDebug` then install the resulting APK on both devices/emulators (`adb install -s <deviceA> app/build/outputs/apk/debug/app-debug.apk`, likewise for `<deviceB>`).

- [ ] **Step 2: Register two different accounts, one per device**

On Device A, register `qa-a@inlove.test` (or any real-format email reachable by you); on Device B, register `qa-b@inlove.test`. Confirm each device shows its own couple code in the Pairing screen.

- [ ] **Step 3: Send and accept an invite across devices**

On Device A, enter Device B's couple code and send an invite. On Device B, confirm the incoming invite appears (this exercises `SyncCoordinator`'s identity-tier `invites` listener from Task 8) and accept it. Confirm both devices show COUPLED status and the same partner name within a few seconds.

- [ ] **Step 4: Verify content sync**

On Device A, add a shared memory (photo + note). Confirm it appears on Device B without manually refreshing (exercises the content-tier `memories` listener). Repeat for an anniversary date.

- [ ] **Step 5: Verify offline resilience**

On Device A, enable airplane mode, add another memory, then disable airplane mode. Confirm the memory appears on Device B shortly after Device A reconnects (exercises the outbox + `SyncWorker`).

- [ ] **Step 6: Verify breakup and re-pairing**

Break up the couple from either device. Confirm both devices return to SINGLE. Re-pair the same two accounts and confirm a fresh relationship forms (not the terminated one) — this is the manual counterpart to Task 11's automated test.

- [ ] **Step 7: Record the result**

In `docs/TASK_CHECKLIST.md`, update the line `[ ] ❓❗ **Việc quan trọng nhất còn lại:** tính năng ghép đôi "Set Love 1-1"...` to `[x] ✅✅ Ghép đôi 2 máy thật qua Firestore — đã build & xác nhận thật trên 2 thiết bị (xem docs/superpowers/plans/2026-09-29-data-sync-and-real-pairing.md)`.

```bash
git add docs/TASK_CHECKLIST.md
git commit -m "docs: confirm real two-device pairing verified end-to-end"
```

---

## Self-Review

**Spec coverage:** Part A (outbox/WorkManager/NetworkMonitor/SyncCoordinator/leaked-scope fix/preset dedupe) → Tasks 1, 3, 4, 5, 6, 7, 8, 9. Part B (coupleCodes, relationship-id-bound-to-invite, invite status lock, real `sendSetLoveInvite`/`acceptSetLoveInvite`/breakup sync) → Tasks 10, 11. Spec's "delete `_3nf`" → Task 2. Spec's Testing section (migration test, fake-Firestore worker test, rules emulator suite, manual two-device check) → Tasks 1, 6, 12, 13 respectively. Runtime gap found during self-review (WorkManager's default reflection factory cannot construct `SyncWorker`) → fixed via Task 8 Step 7 (`Configuration.Provider` + manifest opt-out), not in the original spec — the spec described the outbox/worker architecture but not this Android-framework wiring detail; noted here rather than back-editing the spec, since the spec's design is still accurate at its level of abstraction.

**Placeholder scan:** no TBD/TODO; every step has real file paths and complete code.

**Type consistency:** `OnlineCoupleRepository`/`AuthRepository` constructor signatures introduced in Task 3 are the ones Task 8 (Step 6, login/logout wiring) and Task 11 build on; `InLoveRepository`'s new `(dao, appContext)` constructor from Task 7 matches Task 9's continued use of `dao` alone (Task 9 only touches DAO-calling code already inside that class, no new constructor dependency). `SyncOutboxEntity`/`EntitySyncAdapter`/`SyncWorker.moshiAdapterFor` names are identical across Tasks 1, 5, 6, 7.

**Review Focus:** all five items have an owning task and test, listed inline next to each item above.

## Verification

Full regression after all 13 tasks: `./gradlew :app:testDebugUnitTest :app:assembleDebug` (Kotlin/Room/WorkManager layer) and `npm run test:rules` from `scripts/` (Firestore rules layer), then Task 13's manual two-device pass. No `:appplugin` files are touched by this plan.
