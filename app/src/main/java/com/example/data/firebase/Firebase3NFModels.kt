package com.example.data.firebase

/**
 * Third Normal Form (3NF) Database Model Definitions for Firebase Cloud & Local Sync.
 *
 * 1NF: Atomic values for all attributes, unique Primary Keys (PK).
 * 2NF: All non-key attributes are fully functionally dependent on the Primary Key.
 * 3NF: No transitive functional dependencies. Redundant replicated fields
 *      (e.g., sender profile in invites, partner details in relationships) are removed
 *      and linked via foreign keys (FK) referencing users_3nf or relationships_3nf.
 */

data class FirebaseUser3NF(
  val uid: String = "",                  // [PK] Primary Key
  val email: String = "",
  val displayName: String = "",
  val coupleCode: String = "",
  val avatarUrl: String = "",
  val gender: String = "MALE",
  val birthDate: String = "",
  val age: Int = 0,
  val zodiac: String = "",
  val bio: String = "",
  val createdAt: Long = System.currentTimeMillis()
)

data class FirebaseRelationship3NF(
  val relationshipId: String = "",        // [PK] Primary Key
  val user1Uid: String = "",              // [FK] Foreign Key -> users_3nf.uid
  val user2Uid: String = "",              // [FK] Foreign Key -> users_3nf.uid
  val startDate: Long = 0L,
  val startDateText: String = "",
  val loveTitle: String = "InLove",
  val status: String = "ACTIVE",          // "ACTIVE", "PENDING_BREAKUP", "TERMINATED"
  val createdAt: Long = System.currentTimeMillis()
)

data class FirebaseInvite3NF(
  val inviteId: String = "",              // [PK] Primary Key
  val senderUid: String = "",             // [FK] Foreign Key -> users_3nf.uid
  val targetCoupleCode: String = "",
  val targetUid: String? = null,          // [FK] Foreign Key -> users_3nf.uid
  val proposedStartDate: Long = 0L,
  val proposedStartDateText: String = "",
  val loveNote: String = "",
  val status: String = "PENDING",         // "PENDING", "ACCEPTED", "REJECTED", "CANCELLED"
  val createdAt: Long = System.currentTimeMillis()
)

data class FirebaseMemory3NF(
  val memoryId: String = "",              // [PK] Primary Key
  val relationshipId: String = "",        // [FK] Foreign Key -> relationships_3nf.relationshipId
  val authorUid: String = "",             // [FK] Foreign Key -> users_3nf.uid
  val title: String = "",
  val dateText: String = "",
  val note: String = "",
  val photoUri: String = "",
  val location: String = "",
  val isFavorite: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)

data class FirebaseAnniversary3NF(
  val anniversaryId: String = "",         // [PK] Primary Key
  val relationshipId: String = "",        // [FK] Foreign Key -> relationships_3nf.relationshipId
  val title: String = "",
  val dateText: String = "",
  val type: String = "LOVE",              // "LOVE", "FIRST_DATE", "FIRST_KISS", "PROPOSAL", "WEDDING"
  val reminderDaysBefore: Int = 3,
  val isAnnual: Boolean = true,
  val createdAt: Long = System.currentTimeMillis()
)

data class FirebaseBadge3NF(
  val badgeId: String = "",               // [PK] Primary Key
  val relationshipId: String = "",        // [FK] Foreign Key -> relationships_3nf.relationshipId
  val badgeKey: String = "",
  val targetDays: Int = 100,
  val tier: String = "BRONZE",
  val isClaimed: Boolean = false,
  val claimedTimestamp: Long? = null,
  val customNote: String = ""
)

data class FirebaseGift3NF(
  val giftId: String = "",                // [PK] Primary Key
  val relationshipId: String = "",        // [FK] Foreign Key -> relationships_3nf.relationshipId
  val title: String = "",
  val recipient: String = "",
  val occasion: String = "",
  val dueDateText: String = "",
  val estimatedBudget: String = "",
  val notes: String = "",
  val isCompleted: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)

/**
 * 3NF Database Verification and Integrity Report
 */
data class Database3NFReport(
  val is1NFCompliant: Boolean = true,
  val is2NFCompliant: Boolean = true,
  val is3NFCompliant: Boolean = true,
  val referentialIntegrityScore: Int = 100,
  val totalUsersCount: Int = 0,
  val totalRelationshipsCount: Int = 0,
  val totalInvitesCount: Int = 0,
  val totalMemoriesCount: Int = 0,
  val totalAnniversariesCount: Int = 0,
  val totalBadgesCount: Int = 0,
  val totalGiftsCount: Int = 0,
  val verifiedTimestamp: Long = System.currentTimeMillis(),
  val summaryNotes: List<String> = emptyList()
)
