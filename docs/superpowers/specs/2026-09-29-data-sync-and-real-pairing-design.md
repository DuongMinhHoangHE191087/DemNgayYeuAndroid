# Design Spec: Offline-First Data Sync & Real Two-Device Pairing

## Overview

Today Room is not the single source of truth: `OnlineCoupleRepository` and the
dead `Firebase3NFService` each keep their own `MutableStateFlow` mirrors of
Room data, refreshed by hand. Couple pairing ("Set Love 1-1") never leaves a
single device — `searchUserByCodeOrLink` and `acceptSetLoveInvite` only read
local Room, so two real phones cannot pair. There are two parallel Firestore
schemas (`users`/`relationships`/`invites`/`memories`/`anniversaries` — wired
to the UI — and `users_3nf`/`relationships_3nf`/`invites_3nf`/`memories_3nf` —
written only by the never-invoked `Firebase3NFService`). Preset sync
(`gift_ideas`, `milestone_presets`) uses `OnConflictStrategy.REPLACE` with
Room `autoGenerate` ids, so every refresh inserts duplicate rows instead of
upserting. There is no WorkManager, no connectivity detection, and no outbox.

This spec makes Room the sole source of truth for syncable data, adds a
generic offline outbox + `WorkManager` sync engine, and redesigns couple
pairing as a real two-device Firestore flow — **without adding a backend**
(no Cloud Functions). It supersedes Tasks B1, B2 and B3 in
`docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`: B1 is
redesigned here (not just "connected"), B2 is folded into the same sync
engine, and B3's premise (activate `Firebase3NFService`) is replaced — that
service and the `_3nf` schema are deleted as dead code, not revived.

**Decision already made with the project owner:** pairing's invite/accept/
breakup state machine stays 100% client + Firestore-rules-only (Approach 3
below). No Cloud Functions, no new server infrastructure.

**Revision (2026-09-30):** an adversarial review of the resulting
implementation plan against the real codebase found and fixed 6 blocking
issues before implementation started — most importantly, a missing Firestore
rule for the `memories`/`anniversaries` subcollections Part A's content tier
actually uses (would have silently defeated content sync entirely), a
listener race in the identity tier, and a gap where the original "drop
`partnerId`" design was never wired to actually propagate a remote pairing
change to the inviting device's UI. See "Data model changes" in Part B for
the resulting, corrected `partnerId` design and the updated rules list at
the end of Part B.

## Part A — Generic Offline Sync Engine

### Problem this solves
Any table that must exist on both partners' devices (memories, anniversaries,
later: badges, per-couple gift reminders) needs the same three things: a
globally unique id, a way to know what changed since last sync, and a queue
that survives being offline. Building this once, generically, avoids
repeating outbox/listener plumbing per feature.

### Room schema changes (version 12 → 13, real migration, `exportSchema = true`)
Add four columns to `SharedMemoryEntity` and `AnniversaryDateEntity` (the two
tables this spec wires end-to-end):
- `syncId: String` — UUID, generated client-side at creation. This, not the
  Room `Long` autoGenerate id, is the identity used across devices and is
  the Firestore document id.
- `updatedAt: Long` — epoch millis, set locally on every write and
  overwritten with the server value when a remote change is applied.
- `deleted: Boolean = false` — tombstone; deletes are soft locally until the
  outbox confirms the remote delete, then a cleanup pass purges tombstones
  older than 30 days.
- `pendingSync: Boolean = true` — replaces the already-unused `isSynced`
  column (which is deleted). `true` means the outbox still owes a push.

Add a migration `MIGRATION_12_13` in `AppDatabase.kt` (ALTER TABLE ADD COLUMN
for each, default values as above — no data loss, no destructive fallback
needed for this step). Turn on `exportSchema = true` and commit the
generated schema JSON under `app/schemas/` (this is also the fix for Task
A6/the master plan's "Room has no migration" item, applied here for the
first time this spec touches the database — future schema changes must add
their own migration, `fallbackToDestructiveMigrationOnDowngrade` stays only
as a downgrade safety net, never an upgrade path).

### New table: sync outbox
```kotlin
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,      // "memory" | "anniversary" | "invite" | "relationship" | "couple_code"
    val syncId: String,          // matches the entity's syncId / Firestore doc id
    val operation: String,       // "UPSERT" | "DELETE"
    val payloadJson: String,     // Moshi-serialized snapshot at enqueue time
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)
```
One outbox table for every syncable entity type keeps the Worker generic
instead of writing one Worker per feature.

### `data/sync/` package (new)
- `EntitySyncAdapter<T>` interface: `fun toFirestoreMap(entity: T): Map<String, Any?>`,
  `fun fromFirestoreDoc(doc: DocumentSnapshot): T`, `val collectionPath: (relationshipId: String) -> String`,
  `val dao: SyncableDao<T>` (a small interface exposing `upsertLocal`,
  `markDeleted`, `getBySyncId`).
- `MemorySyncAdapter`, `AnniversarySyncAdapter` — the two concrete
  implementations this spec ships.
- `SyncOutboxDao` (Room DAO: insert, `getPending(limit)`, `markAttempt`,
  `delete`).
- `SyncWorker : CoroutineWorker` — drains the outbox: for each pending row,
  looks up its adapter by `entityType`, pushes via `set()`/`delete()` on the
  matching Firestore doc, deletes the outbox row on success, increments
  `attemptCount` + exponential backoff (`WorkManager`'s built-in
  `BackoffPolicy.EXPONENTIAL`) on failure. Enqueued as unique periodic work
  (`ExistingPeriodicWorkPolicy.KEEP`, 15 min floor per WorkManager, plus a
  one-shot `enqueueUniqueWork` triggered immediately after every local write
  so pushes aren't stuck waiting for the periodic tick) with a `NetworkType.CONNECTED`
  constraint.
- `SyncCoordinator` — a process-lifetime singleton (added to
  `AppServiceLocator`, **not** built inside `InLoveViewModel`, to avoid the
  same leaked-`CoroutineScope` pattern already found in `AuthRepository` and
  `OnlineCoupleRepository`). It runs two listener tiers, started/stopped
  together by login state (`AuthRepository.authState` becoming
  `LoggedIn`/logged out — no separate "active relationship" precondition,
  since relationship membership itself is unknown until the first tier below
  runs):
  1. **Identity tier, always on while logged in:** listens to
     `relationships` where `user1 == myUid OR user2 == myUid` (a single
     `Filter.or` query — two independent listeners on the two fields would
     race each other and could tear down the content tier one of them just
     started), and to `invites` where `targetUid == myUid`. Both are written
     into Room via the same generic outbox/adapter mechanism as any other
     synced entity. This tier is also what keeps `OnlineUserEntity.partnerId`
     correct from the remote side (see "Data model changes" below) and
     signals `OnlineCoupleRepository` to refresh its UI-facing state whenever
     it does — this is the mechanism that makes a remote pairing/breakup
     event reach the *other* device's UI, not just the device that performed
     the action.
  2. **Content tier, started/stopped reactively by tier 1's result:** the
     moment Room's relationship query yields a row with `status == "ACTIVE"`,
     `SyncCoordinator` registers `addSnapshotListener` on that relationship's
     `memories` and `anniversaries` queries and writes incoming changes into
     Room inside a `@Transaction` DAO method (last-write-wins by comparing
     incoming `updatedAt` against the local row before overwriting — a local
     row with `pendingSync = true` and a newer local `updatedAt` wins over an
     older remote change). These two listeners are removed the moment that
     relationship's status stops being `"ACTIVE"` (terminated) or login ends.

### Fixing the two leaked scopes this spec already touches
`AuthRepository` and `OnlineCoupleRepository` each currently do
`private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` and
are constructed fresh inside `InLoveViewModel.init` — the scope is never
cancelled. Since this spec is already changing both files' sync logic,
change their constructors to accept a `CoroutineScope` parameter and pass
`viewModelScope` from `InLoveViewModel` (which is already cancelled
automatically in `onCleared()`). This removes both leaks with no new
infrastructure.

### Preset dedupe bug (gift_ideas, milestone_presets)
Both use `OnConflictStrategy.REPLACE` with Room `autoGenerate` ids and
insert with `id = 0` / `id = index+1`, so a refresh duplicates rows (gift
ideas) or overwrites unrelated rows by coincidence of index (milestones).
Fix: add a `remoteId: String` column (the Firestore document id, stable)
to `GiftIdeaEntity` and the milestone preset entity, and change the DAO
insert to `@Upsert` keyed by a **unique index on `remoteId`**
(`@Index(value = ["remoteId"], unique = true)`) instead of relying on the
Room primary key. This is a small, self-contained fix riding along in the
same migration (`MIGRATION_12_13` also adds this column + index).

### Connectivity detection
`NetworkMonitor` (new, `data/sync/NetworkMonitor.kt`): wraps
`ConnectivityManager.NetworkCallback`, exposes
`val isOnline: StateFlow<Boolean>`, registered once at `InLoveApplication`
level via `AppServiceLocator`. `SyncCoordinator` reads it to decide whether
to attempt an immediate push or wait for `WorkManager`'s own connectivity
constraint; the Settings screen and a small banner (reusing the existing
`AppModals`/toast pattern, not a new dialog) read it to show "Đang ngoại
tuyến — thay đổi sẽ đồng bộ khi có mạng" (fulfils master-plan Task B2).

## Part B — Real Pairing (Approach 3: no Cloud Functions)

### Why not just patch the existing rules further
`relationships` documents currently carry a `partnerId`-style identity
directly on **the user's own document** in the pairing flow the app was
built for, which means accepting an invite requires writing onto a document
owned by someone else. That is the exact shape of bug the project has
already shipped twice (relationship hijacking, self-approving invites).
Approach 3 removes the need for any cross-user document write entirely, by
making the **relationship document itself** — never the user document — the
place partnership is recorded, and by using Firestore's own document-id
matching to bind an accepted invite to at most one relationship.

### Data model changes
- **`partnerId` on `OnlineUserEntity` stays, but only as a local read-cache —
  it is never trusted by a Firestore rule, and no rule anywhere reads it.**
  (Revised from this spec's original "drop `partnerId` entirely": the
  security property that mattered — no client ever writes partnership onto a
  document it doesn't own, and no rule ever trusts a client-supplied
  `partnerId` — is fully preserved by Part B's design below; dropping the
  field outright would additionally require rebuilding
  `OnlineCoupleRepository`'s reactive state around a live Room query instead
  of its existing `refreshState()`-snapshot pattern, which is a larger,
  separate refactor than this spec's scope justifies.) The **source of
  truth** for "who is my partner" is still exclusively the `relationships`
  collection (`user1`/`user2` fields, queried by the identity tier above) —
  `partnerId` is a denormalized copy of that answer, kept correct by two
  independent writers that never conflict because they only ever write to
  the CURRENT device's own signed-in user's row: (a) the local pairing/
  breakup code path (`OnlineCoupleRepository`, unchanged from today), and
  (b) `SyncCoordinator`'s identity tier, which updates it — and only it, for
  the uid it was started with — whenever a remote `relationships` change
  implies a different answer, then signals `OnlineCoupleRepository` to
  refresh. `UserAccountEntity` never had a `partnerId` field and needs no
  change.
- **New collection `coupleCodes/{code}`**: `{ code: String, ownerUid: String,
  createdAt: Timestamp }`. Lets a partner be found without making `users`
  world-readable. `code` is an 8-character random alphanumeric string
  generated client-side (already how `OnlineUserEntity.coupleCode` works
  today, just moved to Firestore as the lookup path); 8 chars keeps
  brute-force `get()` spam expensive relative to Firestore's read pricing.
- **`relationships/{relationshipId}` — id is always the accepted invite's id.**
  This is the mechanism that replaces a Cloud Function: a relationship can
  only be *created* once, and only by referencing an invite that is already
  `ACCEPTED`, because the invite id and relationship id are the same
  document path.

### Pairing flow (sequence)
1. **Share code:** on first pairing screen visit, client writes
   `coupleCodes/{myCode}` (rule requires `ownerUid == auth.uid` and
   `!exists()` at that path, so codes can't be squatted or overwritten).
2. **Find partner:** Device B reads `coupleCodes/{enteredCode}` (rule: any
   authenticated user may `get` a single code doc by exact path; `list` is
   denied, so the collection can't be enumerated) → gets `ownerUid`.
3. **Send invite:** Device B creates `invites/{newInviteId}` with
   `{ senderUid: myUid, targetUid: ownerUid, status: "PENDING", createdAt }`
   (existing create rule already correct: sender must be `auth.uid`).
4. **See invite:** Device A's `SyncCoordinator` identity tier (already
   listening on `invites` where `targetUid == myUid` from the moment of
   login) surfaces the new `PENDING` row through Room like any other synced
   entity — no separate listener needed.
5. **Accept (two sequential writes, both idempotent, both go through the
   same outbox as any other write — so a crash between them is recovered by
   the outbox retry, not by hand-rolled two-phase-commit code):**
   - a) Update `invites/{inviteId}.status = "ACCEPTED"` — allowed by the
     existing rule (`receiverUid/targetUid == auth.uid`, status is not yet
     in the locked-fields list; this spec adds a transition check: old
     status must be `"PENDING"` and new status must be one of
     `["ACCEPTED", "DECLINED"]`).
   - b) Create `relationships/{inviteId}` (same id!) with
     `{ partnerAId: invite.senderUid, partnerBId: myUid, status: "ACTIVE",
     createdAt }`. New create rule (replaces the current one):
     ```
     allow create: if isAuthenticated() &&
       (request.resource.data.partnerAId == request.auth.uid ||
        request.resource.data.partnerBId == request.auth.uid) &&
       exists(/databases/$(database)/documents/invites/$(relationshipId)) &&
       get(/databases/$(database)/documents/invites/$(relationshipId)).data.status == 'ACCEPTED' &&
       get(/databases/$(database)/documents/invites/$(relationshipId)).data.senderUid == request.resource.data.partnerAId &&
       get(/databases/$(database)/documents/invites/$(relationshipId)).data.targetUid == request.resource.data.partnerBId;
     ```
     If step (b) is attempted a second time (retry after a crash, or a
     replay attempt), Firestore routes it to the `update` rule instead of
     `create` because the doc already exists — and the existing "identity
     fields are locked" update rule already rejects changing
     `partnerAId`/`partnerBId`, so replay is a safe no-op, not a second
     relationship.
6. **Breakup:** either member updates `relationships/{id}.status =
   "TERMINATED"` (already permitted by the existing update rule, unchanged).
   A terminated relationship's id is never reused (invite ids are one-shot),
   so re-pairing the same two people later creates a fresh invite/relationship
   pair — intentional, keeps history clean.

### Updated `firestore.rules` — collections to add/change
- Add `coupleCodes/{code}` (rules above).
- Replace `relationships/{relationshipId}` create rule with the
  invite-binding version above; update/get/delete rules are unchanged from
  the current file.
- Add a status-transition check to `invites/{inviteId}` update (currently
  only locks identity fields; add `resource.data.status == 'PENDING' &&
  request.resource.data.status in ['ACCEPTED', 'DECLINED']` for the
  receiver branch, and allow the sender branch to set `'CANCELLED'` from
  `'PENDING'`).
- **Delete**: `users_3nf`, `relationships_3nf`, `invites_3nf`,
  `memories_3nf` rule blocks, plus `Firebase3NFService.kt` and
  `Firebase3NFModels.kt` in code, plus their reads/writes in
  `InLoveViewModel.kt`. Nothing else references them (confirmed dead in the
  data-layer audit).
- **Add `relationships/{relId}/memories/{memId}` and
  `relationships/{relId}/anniversaries/{annId}` — subcollections, distinct
  from the existing top-level `/memories`/`/anniversaries` collections above
  (those are untouched). This is where Part A's content tier actually
  reads/writes (`MemorySyncAdapter`/`AnniversarySyncAdapter.collectionPath`).
  Without a rule here, every content push and listener falls through to the
  file's default-deny and fails — this was missing from the first draft of
  this spec and is the single most consequential gap an implementer must not
  skip. Both relationship members get full read/write, gated on
  `isRelationshipMember` of the parent `relationships/{relId}` doc.**
- The "my active relationship" query (`user1 == uid OR user2 == uid`, via a
  single `Filter.or`) is a disjunction of two single-field equalities, which
  Firestore auto-indexes without a manual composite index entry — no change
  needed to `firestore.indexes.json` for it.

## Error Handling
- Outbox push failure (offline, permission-denied, quota): row stays
  `pendingSync = true`, `WorkManager` retries with exponential backoff; the
  UI shows the existing per-item "pending" affordance pattern already used
  elsewhere, not a new component.
- Snapshot listener failure (e.g. `PERMISSION_DENIED` after a relationship
  is terminated mid-session): `SyncCoordinator` catches the listener error,
  stops that listener, and does not crash — logged, not surfaced as a user
  error, since it's an expected consequence of the other partner ending the
  relationship.
- Invite acceptance step (b) failing after step (a) succeeded: outbox retries
  step (b) using the stored `inviteId` (already `ACCEPTED`), which is safe
  because of the doc-id-binding rule above — no manual reconciliation code
  needed.

## Testing
- Robolectric + in-memory Room (existing pattern in `CloudEnrichmentDataTest`)
  for: `MIGRATION_12_13` runs against a v12 fixture DB without data loss;
  `SyncOutboxDao` enqueue/drain; `@Upsert` on `GiftIdeaEntity`/milestone
  presets no longer duplicates on repeated insert of the same `remoteId`.
- A fake `FirebaseFirestore`/adapter (matching the existing `isTestMode`
  pattern in `AuthRepository`) to unit-test `SyncWorker`'s push logic and
  `SyncCoordinator`'s last-write-wins merge, without a real network.
- `firebase-tools`' Firestore emulator (new, not currently used — add
  `firebase.json` emulator config) to run the actual `firestore.rules`
  against the pairing sequence above: a scripted test that (1) two
  emulator-auth'd users complete steps 1–5 and end up each seeing the other
  as partner, (2) a third user cannot create a `relationships` doc without
  a matching `ACCEPTED` invite, (3) a stale invite replay after termination
  is rejected, and (4) both relationship members can read/write the
  `memories`/`anniversaries` subcollections while a non-member cannot. This
  is the direct regression test for the two previously shipped pairing
  vulnerabilities, plus the subcollection-rules gap found in this spec's own
  review.
- Manual verification: two physical/emulator devices, two accounts, full
  pairing flow with airplane mode toggled mid-flow on one device to confirm
  outbox recovery.

## Global Constraints
- No Cloud Functions, no new backend service — pairing state machine is
  Firestore rules + client only, per owner decision.
- WorkManager periodic work respects the OS floor of 15 minutes; immediate
  pushes go through a one-shot `enqueueUniqueWork`, not a shorter periodic
  interval.
- `app/schemas/` must be committed once `exportSchema = true` is turned on.
- New Gradle dependency: `androidx.work:work-runtime-ktx` (not yet in
  `gradle/libs.versions.toml`).
