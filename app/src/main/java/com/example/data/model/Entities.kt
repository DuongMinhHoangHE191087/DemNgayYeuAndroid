package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

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
  @ColumnInfo(defaultValue = "''") val remoteId: String = ""
)

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
  @ColumnInfo(defaultValue = "''") val remoteId: String = ""
)

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val text: String,
  val iconName: String = "check",
  val isCompleted: Boolean = false
)

@Entity(tableName = "custom_reminders")
data class CustomReminderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val dateText: String,
  val details: String,
  val daysRemainingText: String,
  val iconType: String = "stars",
  val alarmTimeMillis: Long? = null,
  val alarmTimeFormatted: String = ""
)

@Entity(tableName = "reminder_settings")
data class ReminderCadenceEntity(
  @PrimaryKey val key: String,
  val label: String,
  val isEnabled: Boolean
)

@Entity(tableName = "couple_profile")
data class CoupleProfileEntity(
  @PrimaryKey val id: Int = 1,
  val partner1Name: String,
  val partner1Birthday: String,
  val partner1ProfilePicture: String,
  val partner1Age: Int = 0,
  val partner1Zodiac: String = "",
  val partner2Name: String,
  val partner2Birthday: String,
  val partner2ProfilePicture: String,
  val partner2Age: Int = 0,
  val partner2Zodiac: String = "",
  val loveTitle: String = "InLove",
  val loveDays: Int = 0,
  val anniversaryDate: String = "",
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shared_memories")
@JsonClass(generateAdapter = true)
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
  val mediaType: String = "IMAGE", // "IMAGE" or "VIDEO"
  val videoUri: String? = null,
  val cloudinaryPublicId: String? = null,
  val cloudinaryUrl: String? = null,
  val isCloudinaryStored: Boolean = true,
  val fileSizeFormatted: String = "",
  val durationSeconds: Int = 0,
  // Phân quyền (Permissions): "COUPLE_ONLY", "PRIVATE", "PUBLIC"
  val privacyLevel: String = "COUPLE_ONLY",
  // Offline-first sync (Task 1, 2026-09-29 data-sync-and-real-pairing plan)
  @ColumnInfo(defaultValue = "''") val syncId: String = "",
  val updatedAt: Long = 0,
  @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
  @ColumnInfo(defaultValue = "0") val pendingSync: Boolean = false
)

@Entity(tableName = "love_badges")
data class LoveBadgeEntity(
  @PrimaryKey val id: String,
  val targetDays: Int,
  val titleVi: String,
  val titleEn: String,
  val descVi: String,
  val descEn: String,
  val tier: String, // "BRONZE", "SILVER", "GOLD", "RUBY", "DIAMOND", "COSMIC"
  val iconType: String,
  val rewardQuoteVi: String,
  val rewardQuoteEn: String,
  val isClaimed: Boolean = false,
  val claimedTimestamp: Long? = null,
  val customNote: String = ""
)

@Entity(tableName = "anniversary_dates")
@JsonClass(generateAdapter = true)
data class AnniversaryDateEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val dateText: String,
  val type: String = "LOVE", // "LOVE", "FIRST_DATE", "FIRST_KISS", "PROPOSAL", "WEDDING", "CUSTOM"
  val description: String = "",
  val isAnnual: Boolean = true,
  val notificationEnabled: Boolean = true,
  val reminderDaysBefore: Int = 3,
  val daysRemaining: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val relationshipId: String? = null,
  @ColumnInfo(defaultValue = "''") val syncId: String = "",
  val updatedAt: Long = 0,
  @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
  @ColumnInfo(defaultValue = "0") val pendingSync: Boolean = false
)

@Entity(tableName = "gift_reminders")
data class GiftReminderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val recipient: String = "Người ấy",
  val occasion: String = "Kỷ niệm ngày yêu",
  val dueDateText: String = "",
  val estimatedBudget: String = "",
  val notes: String = "",
  val isCompleted: Boolean = false,
  val alarmTimeMillis: Long? = null,
  val alarmTimeFormatted: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

// Online 1-1 Set Love Models & Statuses
object OnlineStatus {
  const val SINGLE = "SINGLE"
  const val PENDING_INVITE = "PENDING_INVITE"
  const val COUPLED = "COUPLED"
}

object RelationshipStatus {
  const val ACTIVE = "ACTIVE"
  const val PENDING_BREAKUP = "PENDING_BREAKUP"
  const val TERMINATED = "TERMINATED"
}

object InviteStatus {
  const val PENDING = "PENDING"
  const val ACCEPTED = "ACCEPTED"
  const val REJECTED = "REJECTED"
  const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "online_users")
data class OnlineUserEntity(
  @PrimaryKey val uid: String,
  val displayName: String = "Vô danh",
  val email: String = "",
  val coupleCode: String,
  val partnerId: String? = null,
  val relationshipId: String? = null,
  val status: String = OnlineStatus.SINGLE,
  val interestsCsv: String = "coffee,travel,technology,music",
  val avatarUrl: String = "",
  val gender: String = "MALE",
  val birthDate: String = "",
  val age: Int = 0,
  val zodiac: String = "",
  val bio: String = "",
  val isProfileSetup: Boolean = false,
  val isCurrentUser: Boolean = false,
  val role: String = "USER_FREE",
  val subscriptionTier: String = "FREE",
  val isVip: Boolean = false
) {
  val interests: List<String>
    get() = interestsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

  val effectiveDisplayName: String
    get() = if (!isProfileSetup || displayName.isBlank() || displayName == "Vô danh") "" else displayName

  val userRole: UserRole
    get() = UserRole.fromCode(role)

  val tier: SubscriptionTier
    get() = SubscriptionTier.fromCode(subscriptionTier)

  val isAdFree: Boolean
    get() = isVip || RbacPolicy.isAdFree(userRole, tier)
}

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
  @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
  @ColumnInfo(defaultValue = "0") val pendingSync: Boolean = false
)

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
  @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
  @ColumnInfo(defaultValue = "0") val pendingSync: Boolean = false
) {
  val effectiveSenderName: String
    get() = senderName.ifBlank { "Vô danh" }
}

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
  @PrimaryKey val uid: String,
  val email: String,
  val passwordHash: String,
  val salt: String,
  val displayName: String,
  val coupleCode: String,
  val avatarUrl: String = "",
  val failedAttempts: Int = 0,
  val lockoutUntil: Long = 0L,
  val lastLoginAt: Long = 0L,
  val createdAt: Long = System.currentTimeMillis(),
  val securityQuestion: String = "Nơi đầu tiên hai bạn hẹn hò?",
  val securityAnswerHash: String = "",
  val appPin: String = "",
  val isPinEnabled: Boolean = false,
  val sessionToken: String = "",
  val role: String = "USER_FREE",
  val subscriptionTier: String = "FREE",
  val isVip: Boolean = false
) {
  val userRole: UserRole
    get() = UserRole.fromCode(role)

  val tier: SubscriptionTier
    get() = SubscriptionTier.fromCode(subscriptionTier)

  val isAdFree: Boolean
    get() = isVip || RbacPolicy.isAdFree(userRole, tier)
}

@Entity(tableName = "security_audit_logs")
data class SecurityAuditLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val accountEmail: String,
  val action: String, // "LOGIN_SUCCESS", "LOGIN_FAILED", "REGISTER", "LOCKOUT", "LOGOUT", "PASSWORD_CHANGED", "PIN_CHANGED", "PASSWORD_RESET"
  val timestamp: Long = System.currentTimeMillis(),
  val detail: String = ""
)

@Entity(tableName = "sync_outbox")
@JsonClass(generateAdapter = true)
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
