package com.example.data.dossier

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.dossier.fakes.FakePartnerDossierCloudGateway
import com.example.shared.dossier.GivenGiftRecord
import com.example.shared.dossier.PartnerDossier
import com.example.shared.dossier.PartnerDossierJson
import com.example.shared.dossier.SecretHintNote
import com.example.shared.sync.CoupleCodeParser
import com.example.shared.sync.OnlineSyncPhase
import com.example.shared.sync.OnlineSyncPhaseResolver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudPartnerDossierSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dossierDir: File
    private lateinit var scope: CoroutineScope
    private var activeScope = "acct:user1"
    private var signedInUid: String? = "uid_user_1"
    private var online = true
    private var nowMs = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dossierDir = tempFolder.newFolder("cloud_dossier")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun newLocalRepo(): LocalJsonPartnerDossierRepository =
        LocalJsonPartnerDossierRepository(
            dossierDir = dossierDir,
            dao = db.inLoveDao(),
            activeScopeProvider = { activeScope },
            clock = { nowMs },
        )

    @Test
    fun syncNow_guestUser_isNoOpAndMakesZeroCloudCalls() = runBlocking {
        signedInUid = null
        val localRepo = newLocalRepo()
        val gateway = FakePartnerDossierCloudGateway()
        val sync = CloudPartnerDossierSync(
            localStore = localRepo,
            gateway = gateway,
            signedInUidProvider = { signedInUid },
            isOnlineProvider = { online },
            scope = scope,
            debounceMs = 0L,
        )

        localRepo.save(PartnerDossier(scopeId = activeScope, nickname = "GuestLove", updatedAtEpochMs = 500L))
        sync.schedulePush(localRepo.dossier.value)
        val synced = sync.syncNow()

        assertFalse(synced)
        assertEquals(0, gateway.fetchCount)
        assertEquals(0, gateway.pushCount)
    }

    @Test
    fun syncNow_mergesOfflineEditsWithNewerRemoteAndPreservesBothSecretNotes() = runBlocking {
        val localRepo = newLocalRepo()
        val gateway = FakePartnerDossierCloudGateway()
        val sync = CloudPartnerDossierSync(
            localStore = localRepo,
            gateway = gateway,
            signedInUidProvider = { signedInUid },
            isOnlineProvider = { online },
            scope = scope,
            debounceMs = 0L,
        )

        val localDossier = PartnerDossier(
            scopeId = activeScope,
            nickname = "OldNick",
            freeformLikes = "coffee",
            budgetMaxVnd = 500_000L,
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "note_local", note = "Handmade scarf", createdAtEpochMs = 900L),
            ),
            updatedAtEpochMs = 1_000L,
        )
        localRepo.save(localDossier)

        val remoteDossier = PartnerDossier(
            scopeId = "uid_user_1",
            nickname = "Linh",
            freeformLikes = "matcha, travel",
            budgetMaxVnd = 1_500_000L,
            secretSurpriseNotes = listOf(
                SecretHintNote(id = "note_remote", note = "Perfume sample", createdAtEpochMs = 1_500L),
            ),
            giftHistory = listOf(
                GivenGiftRecord(id = "gift_1", title = "Silver necklace", givenDateIso = "2026-02-14", priceVnd = 900_000L),
            ),
            updatedAtEpochMs = 2_000L,
        )
        gateway.seedRemote(
            "uid_user_1",
            PartnerDossierRemoteDocument(
                payloadJson = PartnerDossierJson.encode(remoteDossier),
                schemaVersion = FirestorePartnerDossierCloudGateway.SCHEMA_VERSION,
                updatedAtEpochMs = 2_000L,
            ),
        )

        val ok = sync.syncNow()
        assertTrue(ok)

        val mergedLocal = localRepo.dossier.value
        assertEquals(activeScope, mergedLocal.scopeId)
        assertEquals("Linh", mergedLocal.nickname)
        assertEquals(1_500_000L, mergedLocal.budgetMaxVnd)
        assertEquals(listOf("note_remote", "note_local"), mergedLocal.secretSurpriseNotes.map { it.id })
        assertEquals(listOf("gift_1"), mergedLocal.giftHistory.map { it.id })

        val pushedDoc = gateway.currentRemote("uid_user_1")
        assertNotNull(pushedDoc)
        val pushedDecoded = PartnerDossierJson.decodeOrNull(pushedDoc!!.payloadJson)!!
        assertEquals("Linh", pushedDecoded.nickname)
        assertEquals(2, pushedDecoded.secretSurpriseNotes.size)
        assertEquals(1, pushedDecoded.giftHistory.size)
    }

    @Test
    fun syncNow_whenOffline_queuesDirtyPushAndSyncsOnReconnect() = runBlocking {
        val localRepo = newLocalRepo()
        val gateway = FakePartnerDossierCloudGateway()
        val sync = CloudPartnerDossierSync(
            localStore = localRepo,
            gateway = gateway,
            signedInUidProvider = { signedInUid },
            isOnlineProvider = { online },
            scope = scope,
            debounceMs = 0L,
        )

        online = false
        val edited = PartnerDossier(
            scopeId = activeScope,
            nickname = "Minh",
            secretSurpriseNotes = listOf(SecretHintNote(id = "offline_1", note = "Matcha tin", createdAtEpochMs = 1_200L)),
            updatedAtEpochMs = 1_200L,
        )
        localRepo.save(edited)
        sync.schedulePush(edited)

        assertTrue(sync.hasPendingPush())
        assertEquals(0, gateway.pushCount)
        assertFalse(sync.syncNow())

        online = true
        assertTrue(sync.syncNow())
        assertFalse(sync.hasPendingPush())
        assertEquals(1, gateway.pushCount)
        val stored = PartnerDossierJson.decodeOrNull(gateway.currentRemote("uid_user_1")!!.payloadJson)!!
        assertEquals("Minh", stored.nickname)
        assertEquals("Matcha tin", stored.secretSurpriseNotes.single().note)
    }

    @Test
    fun schedulePush_whenOnline_pushesAfterDebounce() = runBlocking {
        val localRepo = newLocalRepo()
        val gateway = FakePartnerDossierCloudGateway()
        val sync = CloudPartnerDossierSync(
            localStore = localRepo,
            gateway = gateway,
            signedInUidProvider = { signedInUid },
            isOnlineProvider = { online },
            scope = scope,
            debounceMs = 20L,
        )

        val updated = PartnerDossier(scopeId = activeScope, nickname = "Vy", updatedAtEpochMs = 5_000L)
        localRepo.save(updated)
        sync.schedulePush(updated)

        val deadline = System.currentTimeMillis() + 3_000L
        while (gateway.pushCount == 0 && System.currentTimeMillis() < deadline) {
            delay(20L)
        }
        assertEquals(1, gateway.pushCount)
        assertFalse(sync.hasPendingPush())
        assertEquals("Vy", PartnerDossierJson.decodeOrNull(gateway.currentRemote("uid_user_1")!!.payloadJson)!!.nickname)
    }

    @Test
    fun syncNow_whenFetchUnavailable_neverOverwritesRemote() = runBlocking {
        val localRepo = newLocalRepo()
        val gateway = FakePartnerDossierCloudGateway().apply { fetchUnavailable = true }
        val sync = CloudPartnerDossierSync(
            localStore = localRepo,
            gateway = gateway,
            signedInUidProvider = { signedInUid },
            isOnlineProvider = { online },
            scope = scope,
            debounceMs = 0L,
        )

        gateway.seedRemote(
            "uid_user_1",
            PartnerDossierRemoteDocument(
                payloadJson = PartnerDossierJson.encode(PartnerDossier(nickname = "RemoteImportant", updatedAtEpochMs = 9_000L)),
                schemaVersion = FirestorePartnerDossierCloudGateway.SCHEMA_VERSION,
                updatedAtEpochMs = 9_000L,
            ),
        )
        localRepo.save(PartnerDossier(scopeId = activeScope, nickname = "StaleLocal", updatedAtEpochMs = 100L))

        assertFalse(sync.syncNow())
        assertEquals(0, gateway.pushCount)
        assertEquals("RemoteImportant", PartnerDossierJson.decodeOrNull(gateway.currentRemote("uid_user_1")!!.payloadJson)!!.nickname)
    }

    @Test
    fun sharedSyncPhaseResolverAndCodeParser_integrateCleanly() {
        assertEquals(
            OnlineSyncPhase.COUPLED_OFFLINE_QUEUE,
            OnlineSyncPhaseResolver.resolve(
                isSignedIn = true,
                isCoupled = true,
                isBreakupPending = false,
                hasOutgoingInvite = false,
                hasIncomingInvite = false,
                isOnline = false,
            ),
        )
        assertEquals("AB-1234", CoupleCodeParser.extractCode("inlove://pair?code=ab-1234"))
    }
}
