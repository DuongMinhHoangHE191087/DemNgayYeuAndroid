package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.AnniversaryDateEntity
import com.example.data.model.ChecklistItemEntity
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.CustomReminderEntity
import com.example.data.model.GiftIdeaEntity
import com.example.data.model.GiftReminderEntity
import com.example.data.model.LoveBadgeEntity
import com.example.data.model.MilestoneEntity
import com.example.data.model.ReminderCadenceEntity
import com.example.data.model.SharedMemoryEntity
import com.example.data.model.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InLoveDao {
  // Milestones
  @Query("SELECT * FROM milestones ORDER BY id ASC")
  fun getAllMilestones(): Flow<List<MilestoneEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMilestones(items: List<MilestoneEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMilestone(item: MilestoneEntity): Long

  @Update
  suspend fun updateMilestone(item: MilestoneEntity)

  @Query("DELETE FROM milestones WHERE id = :id")
  suspend fun deleteMilestoneById(id: Long)

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

  // Gift Ideas
  @Query("SELECT * FROM gift_ideas ORDER BY id ASC")
  fun getAllGiftIdeas(): Flow<List<GiftIdeaEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGiftIdeas(items: List<GiftIdeaEntity>)

  @Update
  suspend fun updateGiftIdea(item: GiftIdeaEntity)

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

  // Checklist Items
  @Query("SELECT * FROM checklist_items ORDER BY id ASC")
  fun getAllChecklistItems(): Flow<List<ChecklistItemEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChecklistItems(items: List<ChecklistItemEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChecklistItem(item: ChecklistItemEntity): Long

  @Update
  suspend fun updateChecklistItem(item: ChecklistItemEntity)

  @Query("DELETE FROM checklist_items WHERE id = :id")
  suspend fun deleteChecklistItemById(id: Long)

  // Custom Reminders
  @Query("SELECT * FROM custom_reminders ORDER BY id DESC")
  fun getAllCustomReminders(): Flow<List<CustomReminderEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomReminders(items: List<CustomReminderEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCustomReminder(item: CustomReminderEntity): Long

  @Query("DELETE FROM custom_reminders WHERE id = :id")
  suspend fun deleteCustomReminderById(id: Long)

  // Reminder Cadences
  @Query("SELECT * FROM reminder_settings")
  fun getAllReminderCadences(): Flow<List<ReminderCadenceEntity>>

  @Query("SELECT * FROM reminder_settings")
  suspend fun getReminderCadencesList(): List<ReminderCadenceEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertReminderCadences(items: List<ReminderCadenceEntity>)

  @Update
  suspend fun updateReminderCadence(item: ReminderCadenceEntity)

  // Couple Profile
  @Query("SELECT * FROM couple_profile WHERE id = 1 LIMIT 1")
  fun getCoupleProfile(): Flow<CoupleProfileEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertCoupleProfile(profile: CoupleProfileEntity)

  // ponytail: no-op khi chưa có hàng hồ sơ (onboarding tạo nó); UI chỉ mở editor khi đã có hồ sơ.
  @Query("UPDATE couple_profile SET likesCsv = :likes, budgetMaxVnd = :budget, occasionRegion = :region WHERE id = 1")
  suspend fun updatePartnerPreferences(likes: String, budget: Long, region: String)

  // Shared Memories
  @Query("SELECT * FROM shared_memories WHERE deleted = 0 ORDER BY id DESC")
  fun getAllSharedMemories(): Flow<List<SharedMemoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSharedMemory(memory: SharedMemoryEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSharedMemories(memories: List<SharedMemoryEntity>)

  @Update
  suspend fun updateSharedMemory(memory: SharedMemoryEntity)

  // Soft delete, not DELETE FROM: the row must survive locally so a later echo of this same
  // tombstone from the remote listener (applyRemoteMemory) finds it via syncId and no-ops
  // instead of re-inserting it as a live row. getAllSharedMemories filters deleted = 0.
  @Query("UPDATE shared_memories SET deleted = 1, updatedAt = :deletedAt WHERE id = :id")
  suspend fun softDeleteSharedMemoryById(id: Long, deletedAt: Long)

  // Sync outbox
  @Insert
  suspend fun insertOutboxEntry(entry: SyncOutboxEntity): Long

  // Dòng đã lỗi nhiều lần xuống cuối hàng đợi, để một dòng hỏng không chặn các thay đổi phía sau.
  @Query("SELECT * FROM sync_outbox ORDER BY attemptCount ASC, createdAt ASC LIMIT :limit")
  suspend fun getPendingOutboxEntries(limit: Int = 20): List<SyncOutboxEntity>

  @Query("DELETE FROM sync_outbox WHERE id = :id")
  suspend fun deleteOutboxEntry(id: Long)

  // Payload là snapshot đầy đủ, nên bản mới nhất của một memory thay thế mọi bản cũ còn chờ.
  // Nếu giữ bản UPSERT cũ, nó có thể được gửi lại sau khi tombstone đã đi và ghi đè xoá.
  @Query("DELETE FROM sync_outbox WHERE entityType = :entityType AND syncId = :syncId")
  suspend fun deleteOutboxForSync(entityType: String, syncId: String)

  @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastError = :error WHERE id = :id")
  suspend fun markOutboxAttemptFailed(id: Long, error: String)

  // Memory / anniversary writes that atomically enqueue an outbox entry (Task 7)
  @Transaction
  suspend fun insertSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity): Long {
    val newId = insertSharedMemory(memory)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
    return newId
  }

  @Transaction
  suspend fun updateSharedMemoryWithOutbox(memory: SharedMemoryEntity, outbox: SyncOutboxEntity) {
    updateSharedMemory(memory)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
  }

  @Transaction
  suspend fun deleteSharedMemoryWithOutbox(id: Long, deletedAt: Long, outbox: SyncOutboxEntity) {
    softDeleteSharedMemoryById(id, deletedAt)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
  }

  // Love Milestone Badges
  @Query("SELECT * FROM love_badges ORDER BY targetDays ASC")
  fun getAllLoveBadges(): Flow<List<LoveBadgeEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLoveBadges(items: List<LoveBadgeEntity>)

  @Update
  suspend fun updateLoveBadge(item: LoveBadgeEntity)

  // Anniversary Dates Persistence
  @Query("SELECT * FROM anniversary_dates WHERE deleted = 0 ORDER BY id ASC")
  fun getAllAnniversaryDates(): Flow<List<AnniversaryDateEntity>>

  @Query("SELECT * FROM anniversary_dates WHERE deleted = 0 ORDER BY id ASC")
  suspend fun getAnniversaryDatesList(): List<AnniversaryDateEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnniversaryDates(items: List<AnniversaryDateEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnniversaryDate(item: AnniversaryDateEntity): Long

  @Update
  suspend fun updateAnniversaryDate(item: AnniversaryDateEntity)

  // Soft delete, not DELETE FROM: see softDeleteSharedMemoryById for why.
  @Query("UPDATE anniversary_dates SET deleted = 1, updatedAt = :deletedAt WHERE id = :id")
  suspend fun softDeleteAnniversaryDateById(id: Long, deletedAt: Long)

  @Transaction
  suspend fun insertAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity): Long {
    val newId = insertAnniversaryDate(item)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
    return newId
  }

  @Transaction
  suspend fun updateAnniversaryDateWithOutbox(item: AnniversaryDateEntity, outbox: SyncOutboxEntity) {
    updateAnniversaryDate(item)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
  }

  @Transaction
  suspend fun deleteAnniversaryDateWithOutbox(id: Long, deletedAt: Long, outbox: SyncOutboxEntity) {
    softDeleteAnniversaryDateById(id, deletedAt)
    deleteOutboxForSync(outbox.entityType, outbox.syncId)
    insertOutboxEntry(outbox)
  }

  @Query("SELECT * FROM anniversary_dates WHERE syncId = :syncId LIMIT 1")
  suspend fun getAnniversaryDateBySyncId(syncId: String): AnniversaryDateEntity?

  @Query("SELECT * FROM shared_memories WHERE syncId = :syncId LIMIT 1")
  suspend fun getSharedMemoryBySyncId(syncId: String): SharedMemoryEntity?

  // Direct suspend queries for background scheduling
  @Query("SELECT * FROM milestones ORDER BY id ASC")
  suspend fun getMilestonesList(): List<MilestoneEntity>

  @Query("SELECT * FROM couple_profile WHERE id = 1 LIMIT 1")
  suspend fun getCoupleProfileSync(): CoupleProfileEntity?

  @Query("SELECT * FROM custom_reminders ORDER BY id DESC")
  suspend fun getCustomRemindersList(): List<CustomReminderEntity>

  @Query("UPDATE custom_reminders SET alarmTimeMillis = :millis, alarmTimeFormatted = :formatted WHERE id = :id")
  suspend fun setCustomReminderAlarm(id: Long, millis: Long?, formatted: String)

  // Gift Reminders Persistence
  @Query("SELECT * FROM gift_reminders ORDER BY isCompleted ASC, id DESC")
  fun getAllGiftReminders(): Flow<List<GiftReminderEntity>>

  @Query("SELECT * FROM gift_reminders ORDER BY isCompleted ASC, id DESC")
  suspend fun getGiftRemindersList(): List<GiftReminderEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGiftReminders(items: List<GiftReminderEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertGiftReminder(item: GiftReminderEntity): Long

  @Update
  suspend fun updateGiftReminder(item: GiftReminderEntity)

  @Query("DELETE FROM gift_reminders WHERE id = :id")
  suspend fun deleteGiftReminderById(id: Long)

  // Online 1-1 Set Love DAO Operations
  @Query("SELECT * FROM online_users WHERE isCurrentUser = 1 LIMIT 1")
  fun getCurrentOnlineUser(): Flow<com.example.data.model.OnlineUserEntity?>

  @Query("SELECT * FROM online_users WHERE isCurrentUser = 1 LIMIT 1")
  suspend fun getCurrentOnlineUserSync(): com.example.data.model.OnlineUserEntity?

  @Query("SELECT * FROM online_users WHERE uid = :uid LIMIT 1")
  fun getOnlineUserByUid(uid: String): Flow<com.example.data.model.OnlineUserEntity?>

  @Query("SELECT * FROM online_users WHERE uid = :uid LIMIT 1")
  suspend fun getOnlineUserByUidSync(uid: String): com.example.data.model.OnlineUserEntity?

  @Query("SELECT * FROM online_users WHERE coupleCode = :code LIMIT 1")
  suspend fun getOnlineUserByCoupleCodeSync(code: String): com.example.data.model.OnlineUserEntity?

  @Query("SELECT * FROM online_users WHERE coupleCode LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%' LIMIT 10")
  suspend fun searchOnlineUsersSync(query: String): List<com.example.data.model.OnlineUserEntity>

  @Query("SELECT * FROM online_users")
  suspend fun getAllOnlineUsersListSync(): List<com.example.data.model.OnlineUserEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOnlineUser(user: com.example.data.model.OnlineUserEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOnlineUsers(users: List<com.example.data.model.OnlineUserEntity>)

  @Update
  suspend fun updateOnlineUser(user: com.example.data.model.OnlineUserEntity)

  @Query("SELECT * FROM online_relationships WHERE relationshipId = :relId LIMIT 1")
  fun getOnlineRelationship(relId: String): Flow<com.example.data.model.OnlineRelationshipEntity?>

  @Query("SELECT * FROM online_relationships WHERE relationshipId = :relId LIMIT 1")
  suspend fun getOnlineRelationshipSync(relId: String): com.example.data.model.OnlineRelationshipEntity?

  @Query("SELECT * FROM online_relationships")
  suspend fun getAllRelationshipsSync(): List<com.example.data.model.OnlineRelationshipEntity>

  @Query("SELECT * FROM online_invites")
  suspend fun getAllInvitesSync(): List<com.example.data.model.OnlineInviteEntity>

  @Query("SELECT * FROM online_relationships WHERE (user1 = :uid OR user2 = :uid) AND status = 'ACTIVE' LIMIT 1")
  suspend fun getActiveRelationshipForUser(uid: String): com.example.data.model.OnlineRelationshipEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOnlineRelationship(relationship: com.example.data.model.OnlineRelationshipEntity)

  @Update
  suspend fun updateOnlineRelationship(relationship: com.example.data.model.OnlineRelationshipEntity)

  @Query("SELECT * FROM online_invites WHERE targetCoupleCode = :code AND status = 'PENDING' ORDER BY createdAt DESC")
  fun getIncomingInvites(code: String): Flow<List<com.example.data.model.OnlineInviteEntity>>

  @Query("SELECT * FROM online_invites WHERE targetCoupleCode = :code AND status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
  suspend fun getIncomingInviteByTargetCodeSync(code: String): com.example.data.model.OnlineInviteEntity?

  @Query("SELECT * FROM online_invites WHERE inviteId = :inviteId LIMIT 1")
  suspend fun getInviteByIdSync(inviteId: String): com.example.data.model.OnlineInviteEntity?

  @Query("SELECT * FROM online_invites WHERE senderUid = :uid AND status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
  fun getActiveOutgoingInvite(uid: String): Flow<com.example.data.model.OnlineInviteEntity?>

  @Query("SELECT * FROM online_invites WHERE senderUid = :uid AND status = 'PENDING' ORDER BY createdAt DESC LIMIT 1")
  suspend fun getActiveOutgoingInviteSync(uid: String): com.example.data.model.OnlineInviteEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOnlineInvite(invite: com.example.data.model.OnlineInviteEntity)

  @Update
  suspend fun updateOnlineInvite(invite: com.example.data.model.OnlineInviteEntity)

  @Query("DELETE FROM online_invites WHERE inviteId = :inviteId")
  suspend fun deleteOnlineInvite(inviteId: String)

  // Filter memories by relationshipId
  @Query("SELECT * FROM shared_memories WHERE relationshipId = :relId ORDER BY id DESC")
  fun getSharedMemoriesForRelationship(relId: String): Flow<List<SharedMemoryEntity>>

  // User Account & Authentication Persistence
  @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
  suspend fun getUserAccountByEmail(email: String): com.example.data.model.UserAccountEntity?

  @Query("SELECT * FROM user_accounts WHERE uid = :uid LIMIT 1")
  suspend fun getUserAccountByUid(uid: String): com.example.data.model.UserAccountEntity?

  @Query("SELECT * FROM user_accounts WHERE sessionToken = :token LIMIT 1")
  suspend fun getUserAccountBySessionToken(token: String): com.example.data.model.UserAccountEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUserAccount(account: com.example.data.model.UserAccountEntity)

  @Update
  suspend fun updateUserAccount(account: com.example.data.model.UserAccountEntity)

  @Query("SELECT * FROM user_accounts ORDER BY createdAt DESC")
  fun getAllUserAccounts(): Flow<List<com.example.data.model.UserAccountEntity>>

  // Security Audit Logs
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSecurityLog(log: com.example.data.model.SecurityAuditLogEntity): Long

  @Query("SELECT * FROM security_audit_logs WHERE accountEmail = :email ORDER BY timestamp DESC LIMIT 20")
  fun getSecurityLogsForAccount(email: String): Flow<List<com.example.data.model.SecurityAuditLogEntity>>

  @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 20")
  fun getAllRecentSecurityLogs(): Flow<List<com.example.data.model.SecurityAuditLogEntity>>

  @Delete
  suspend fun deleteUserAccount(account: com.example.data.model.UserAccountEntity)

  @Query("DELETE FROM security_audit_logs WHERE accountEmail = :email")
  suspend fun deleteSecurityLogsForAccount(email: String)

  @Query("DELETE FROM shared_memories")
  suspend fun clearAllSharedMemories()

  @Query("DELETE FROM couple_profile")
  suspend fun clearCoupleProfile()

  @Query("DELETE FROM online_users WHERE uid = :uid")
  suspend fun deleteOnlineUser(uid: String)

  @Query("DELETE FROM online_relationships WHERE user1 = :uid OR user2 = :uid")
  suspend fun deleteOnlineRelationshipsForUser(uid: String)

  @Query("DELETE FROM online_invites WHERE senderUid = :uid OR targetUid = :uid")
  suspend fun deleteOnlineInvitesForUser(uid: String)
}
