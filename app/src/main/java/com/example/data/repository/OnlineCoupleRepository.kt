package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.InviteStatus
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import com.example.ui.util.ProfileUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OnlineCoupleRepository(
  private val dao: InLoveDao,
  context: Context,
  private val scope: CoroutineScope
) {

  // Demo user IDs for seamless 1-1 testing on device
  companion object {
    const val USER_A_ID = "test_user_a"
    const val USER_A_CODE = "TEST-8888"
    const val USER_A_NAME = "Tester A"

    const val USER_B_ID = "test_user_b"
    const val USER_B_CODE = "TEST-9999"
    const val USER_B_NAME = "Tester B"

    val AVAILABLE_INTERESTS = listOf(
      "coffee" to "Cà phê ☕",
      "travel" to "Du lịch ✈️",
      "technology" to "Công nghệ 💻",
      "cycling" to "Xe đạp 🚴",
      "fashion" to "Thời trang 👗",
      "music" to "Âm nhạc 🎵",
      "cinema" to "Điện ảnh 🎬",
      "cooking" to "Nấu ăn 🍳",
      "books" to "Sách & Thơ 📚",
      "gaming" to "Chơi game 🎮"
    )

    fun createEmptyUser(uid: String = "guest_user"): OnlineUserEntity {
      return OnlineUserEntity(
        uid = uid,
        displayName = "Bạn",
        email = "",
        coupleCode = ProfileUtils.generateRandomCoupleCode(),
        partnerId = null,
        relationshipId = null,
        status = OnlineStatus.SINGLE,
        interestsCsv = "coffee,travel",
        avatarUrl = "",
        gender = "MALE",
        birthDate = "",
        age = 0,
        zodiac = "",
        bio = "Chào mừng bạn đến với InLove 💕",
        isProfileSetup = false,
        isCurrentUser = true
      )
    }
  }

  // Active User StateFlows
  private val _currentUserId = MutableStateFlow("guest_user")
  val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

  private val _currentUser = MutableStateFlow(createEmptyUser())
  val currentUser: StateFlow<OnlineUserEntity> = _currentUser.asStateFlow()

  private val _partnerUser = MutableStateFlow<OnlineUserEntity?>(null)
  val partnerUser: StateFlow<OnlineUserEntity?> = _partnerUser.asStateFlow()

  private val _activeRelationship = MutableStateFlow<OnlineRelationshipEntity?>(null)
  val activeRelationship: StateFlow<OnlineRelationshipEntity?> = _activeRelationship.asStateFlow()

  private val _incomingInvite = MutableStateFlow<OnlineInviteEntity?>(null)
  val incomingInvite: StateFlow<OnlineInviteEntity?> = _incomingInvite.asStateFlow()

  private val _outgoingInvite = MutableStateFlow<OnlineInviteEntity?>(null)
  val outgoingInvite: StateFlow<OnlineInviteEntity?> = _outgoingInvite.asStateFlow()

  private val _relationshipStatus = MutableStateFlow(OnlineStatus.SINGLE)
  val relationshipStatus: StateFlow<String> = _relationshipStatus.asStateFlow()

  private val _mutualInterests = MutableStateFlow<Set<String>>(emptySet())
  val mutualInterests: StateFlow<Set<String>> = _mutualInterests.asStateFlow()

  // Optional Firestore instance
  private var firestore: FirebaseFirestore? = null

  init {
    try {
      if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
      }
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore not initialized or offline: ${e.message}")
    }

    scope.launch {
      refreshState()
    }
  }

  suspend fun setCurrentUserId(uid: String) = withContext(Dispatchers.IO) {
    if (uid.isBlank()) {
      _currentUserId.value = "guest_user"
      _currentUser.value = createEmptyUser()
      _partnerUser.value = null
      _activeRelationship.value = null
      _relationshipStatus.value = OnlineStatus.SINGLE
      return@withContext
    }
    _currentUserId.value = uid
    refreshState()
  }

  suspend fun refreshState() = withContext(Dispatchers.IO) {
    val currentUid = _currentUserId.value
    var me = dao.getOnlineUserByUidSync(currentUid)
    if (me == null) {
      val defaultUser = createEmptyUser(currentUid)
      dao.insertOnlineUser(defaultUser)
      me = defaultUser
    }
    _currentUser.value = me
    _relationshipStatus.value = me.status

    val partner = if (!me.partnerId.isNullOrBlank()) {
      dao.getOnlineUserByUidSync(me.partnerId)
    } else null
    _partnerUser.value = partner

    val rel = if (!me.relationshipId.isNullOrBlank()) {
      dao.getOnlineRelationshipSync(me.relationshipId)
    } else null
    _activeRelationship.value = rel

    // Sync incoming & outgoing invites from database
    val incInvite = dao.getIncomingInviteByTargetCodeSync(me.coupleCode)
    _incomingInvite.value = incInvite

    val outInvite = dao.getActiveOutgoingInviteSync(me.uid)
    _outgoingInvite.value = outInvite

    // Calculate mutual interests
    if (partner != null && me.status == OnlineStatus.COUPLED) {
      val myInterests = me.interests.toSet()
      val partnerInterests = partner.interests.toSet()
      _mutualInterests.value = myInterests.intersect(partnerInterests)
    } else {
      _mutualInterests.value = emptySet()
    }
  }

  suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
    refreshState()
  }

  // Switch demo user to test 2-way invite and breakup from both sides
  suspend fun switchDemoUserSync() = withContext(Dispatchers.IO) {
    val current = _currentUserId.value
    val nextUid = if (current == USER_A_ID) USER_B_ID else USER_A_ID
    _currentUserId.value = nextUid

    val userA = dao.getOnlineUserByUidSync(USER_A_ID)
    val userB = dao.getOnlineUserByUidSync(USER_B_ID)

    if (userA != null && userB != null) {
      dao.updateOnlineUser(userA.copy(isCurrentUser = (nextUid == USER_A_ID)))
      dao.updateOnlineUser(userB.copy(isCurrentUser = (nextUid == USER_B_ID)))
    }
    refreshState()
  }

  fun switchDemoUser() {
    scope.launch {
      switchDemoUserSync()
    }
  }

  // Update My Profile - user can ONLY edit their own profile
  suspend fun updateMyProfile(
    name: String,
    birthDate: String,
    avatarUrl: String,
    gender: String,
    bio: String
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val trimmedName = name.trim()
    val isSetup = trimmedName.isNotEmpty() && trimmedName != "Vô danh"
    val finalDisplayName = if (isSetup) trimmedName else "Vô danh"

    // Automatically calculate age and zodiac from birth date
    val calculatedAge = ProfileUtils.calculateAge(birthDate)
    val calculatedZodiac = ProfileUtils.calculateZodiac(birthDate).first

    val updatedMe = me.copy(
      displayName = finalDisplayName,
      birthDate = birthDate.trim(),
      age = calculatedAge,
      zodiac = calculatedZodiac,
      avatarUrl = avatarUrl.trim().ifEmpty { me.avatarUrl },
      gender = gender,
      bio = bio.trim(),
      isProfileSetup = isSetup
    )

    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe

    // If coupled, sync current user's part into CoupleProfileEntity
    val currentProfile = dao.getCoupleProfileSync()
    if (currentProfile != null && updatedMe.status == OnlineStatus.COUPLED) {
      val isMale = updatedMe.gender == "MALE"
      val newProfile = if (isMale) {
        currentProfile.copy(
          partner1Name = updatedMe.effectiveDisplayName,
          partner1Birthday = updatedMe.birthDate,
          partner1Age = updatedMe.age,
          partner1Zodiac = updatedMe.zodiac,
          partner1ProfilePicture = updatedMe.avatarUrl
        )
      } else {
        currentProfile.copy(
          partner2Name = updatedMe.effectiveDisplayName,
          partner2Birthday = updatedMe.birthDate,
          partner2Age = updatedMe.age,
          partner2Zodiac = updatedMe.zodiac,
          partner2ProfilePicture = updatedMe.avatarUrl
        )
      }
      dao.insertCoupleProfile(newProfile)
    }

    refreshState()
    return@withContext true to "Đã cập nhật hồ sơ cá nhân thành công!"
  }

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

  // Search user by code or full shared link
  suspend fun searchUserByCodeOrLink(input: String): OnlineUserEntity? = withContext(Dispatchers.IO) {
    val code = ProfileUtils.extractCoupleCode(input)
    if (code.isEmpty()) return@withContext null
    dao.getOnlineUserByCoupleCodeSync(code)
  }

  // Search user by code, name, or email for Set Love
  suspend fun searchUserByCodeOrNameOrEmail(input: String): OnlineUserEntity? = withContext(Dispatchers.IO) {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return@withContext null
    val code = ProfileUtils.extractCoupleCode(trimmed)
    if (code.isNotEmpty()) {
      val byCode = dao.getOnlineUserByCoupleCodeSync(code)
      if (byCode != null) return@withContext byCode
    }
    val results = dao.searchOnlineUsersSync(trimmed)
    if (results.isNotEmpty()) {
      return@withContext results.first()
    }
    null
  }

  suspend fun getAllPotentialPartners(): List<OnlineUserEntity> = withContext(Dispatchers.IO) {
    val all = dao.getAllOnlineUsersListSync()
    val me = _currentUser.value
    all.filter { it.uid != me.uid }
  }

  // 1-1 Set Love Invite Sending with proposed love date and love note
  suspend fun sendSetLoveInvite(
    targetCodeOrLink: String,
    proposedStartDateMillis: Long = System.currentTimeMillis(),
    loveNote: String = ""
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val trimmedCode = ProfileUtils.extractCoupleCode(targetCodeOrLink)

    if (trimmedCode.isEmpty()) {
      return@withContext false to "Vui lòng nhập mã ghép đôi hoặc dán link hợp lệ!"
    }
    if (me.status == OnlineStatus.COUPLED) {
      return@withContext false to "Bạn đã trong mối quan hệ 1-1, không thể gửi lời mời mới!"
    }
    if (trimmedCode.equals(me.coupleCode, ignoreCase = true)) {
      return@withContext false to "Không thể tự kết đôi với chính mã của bạn!"
    }

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
  }

  // Accept incoming invite with confirmed love date
  suspend fun acceptSetLoveInvite(
    inviteId: String,
    confirmedStartDateMillis: Long? = null
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val incoming = _incomingInvite.value ?: dao.getInviteByIdSync(inviteId)
      ?: return@withContext false to "Không tìm thấy thông tin lời mời!"

    val sender = dao.getOnlineUserByUidSync(incoming.senderUid)
      ?: return@withContext false to "Không tìm thấy người gửi lời mời!"

    val finalStartDate = confirmedStartDateMillis
      ?: if (incoming.proposedStartDate > 0) incoming.proposedStartDate
      else System.currentTimeMillis()
    val finalStartDateText = ProfileUtils.formatDate(finalStartDate)
    val finalLoveDays = ProfileUtils.calculateLoveDays(finalStartDate)

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
      // insertOnlineRelationship, not updateOnlineRelationship: @Update matches by primary
      // key and silently does nothing if the row doesn't exist yet locally — which it never
      // does at this point, since this relationship id is brand new. insertOnlineRelationship
      // uses OnConflictStrategy.REPLACE, so it correctly creates the row here.
      val synced = relationship.copy(pendingSync = false)
      dao.insertOnlineRelationship(synced)
      _activeRelationship.value = synced
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore pairing sync error, will retry via outbox in a later task: ${e.message}")
      dao.insertOnlineRelationship(relationship)
      _activeRelationship.value = relationship
    }

    // Update both users to COUPLED
    val updatedMe = me.copy(
      status = OnlineStatus.COUPLED,
      partnerId = sender.uid,
      relationshipId = relId
    )
    val updatedSender = sender.copy(
      status = OnlineStatus.COUPLED,
      partnerId = me.uid,
      relationshipId = relId
    )

    dao.updateOnlineUser(updatedMe)
    dao.updateOnlineUser(updatedSender)

    _currentUser.value = updatedMe
    _partnerUser.value = updatedSender
    _relationshipStatus.value = OnlineStatus.COUPLED

    // Clean up invite
    dao.deleteOnlineInvite(incoming.inviteId)
    _incomingInvite.value = null
    _outgoingInvite.value = null

    // Update couple profile in Room for Home screen
    val currentProfile = dao.getCoupleProfileSync()
    val isMeMale = updatedMe.gender == "MALE"
    val p1 = if (isMeMale) updatedMe else updatedSender
    val p2 = if (!isMeMale) updatedMe else updatedSender

    val syncedProfile = CoupleProfileEntity(
      id = 1,
      partner1Name = p1.effectiveDisplayName,
      partner1Birthday = p1.birthDate,
      partner1ProfilePicture = p1.avatarUrl,
      partner1Age = p1.age,
      partner1Zodiac = p1.zodiac,
      partner2Name = p2.effectiveDisplayName,
      partner2Birthday = p2.birthDate,
      partner2ProfilePicture = p2.avatarUrl,
      partner2Age = p2.age,
      partner2Zodiac = p2.zodiac,
      loveTitle = currentProfile?.loveTitle?.ifBlank { "InLove" } ?: "InLove",
      loveDays = finalLoveDays,
      anniversaryDate = finalStartDateText,
      updatedAt = System.currentTimeMillis()
    )
    dao.insertCoupleProfile(syncedProfile)

    refreshState()
    return@withContext true to "Chúc mừng hai bạn đã chính thức Set Love 1-1 bên nhau! ❤️"
  }

  // Reject incoming invite
  suspend fun rejectSetLoveInvite(inviteId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val incoming = _incomingInvite.value
    if (incoming != null) {
      dao.deleteOnlineInvite(incoming.inviteId)
      val sender = dao.getOnlineUserByUidSync(incoming.senderUid)
      if (sender != null && sender.status == OnlineStatus.PENDING_INVITE) {
        dao.updateOnlineUser(sender.copy(status = OnlineStatus.SINGLE))
      }
    }
    _incomingInvite.value = null
    return@withContext true to "Đã từ chối lời mời kết đôi."
  }

  // Cancel outgoing invite
  suspend fun cancelSentInvite(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val updatedMe = me.copy(status = OnlineStatus.SINGLE)
    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe
    _relationshipStatus.value = OnlineStatus.SINGLE
    _outgoingInvite.value = null
    _incomingInvite.value = null
    return@withContext true to "Đã hủy lời mời kết đôi."
  }

  // Step 1 of Breakup: Request 2-way Breakup
  suspend fun requestBreakup(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val rel = _activeRelationship.value ?: return@withContext false to "Không tìm thấy mối quan hệ hiện tại!"

    val updatedRel = rel.copy(
      status = RelationshipStatus.PENDING_BREAKUP,
      breakupRequestedBy = me.uid,
      breakupRequestedAt = System.currentTimeMillis()
    )

    dao.updateOnlineRelationship(updatedRel)
    _activeRelationship.value = updatedRel

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

    return@withContext true to "Đã gửi yêu cầu hủy Set Love đến đối phương. Chờ xác nhận 2 chiều."
  }

  // Step 2 of Breakup: Partner confirms Breakup
  suspend fun confirmBreakup(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val partner = _partnerUser.value
    val rel = _activeRelationship.value ?: return@withContext false to "Không tìm thấy mối quan hệ hiện tại!"

    // Terminate relationship
    val terminatedRel = rel.copy(
      status = RelationshipStatus.TERMINATED,
      terminatedAt = System.currentTimeMillis()
    )
    dao.updateOnlineRelationship(terminatedRel)
    _activeRelationship.value = terminatedRel

    try {
      firestore?.collection("relationships")?.document(rel.relationshipId)?.update(
        mapOf("status" to RelationshipStatus.TERMINATED, "terminatedAt" to terminatedRel.terminatedAt)
      )?.await()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-confirm sync error: ${e.message}")
    }

    // Both users return to SINGLE (soft locks shared memories)
    val updatedMe = me.copy(
      status = OnlineStatus.SINGLE,
      partnerId = null,
      relationshipId = null
    )
    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe
    _relationshipStatus.value = OnlineStatus.SINGLE

    if (partner != null) {
      val updatedPartner = partner.copy(
        status = OnlineStatus.SINGLE,
        partnerId = null,
        relationshipId = null
      )
      dao.updateOnlineUser(updatedPartner)
      _partnerUser.value = updatedPartner
    }

    _mutualInterests.value = emptySet()
    refreshState()
    return@withContext true to "Đã hoàn tất hủy Set Love. Hai bạn đã trở về trạng thái Độc thân."
  }

  // Step 2 of Breakup: Partner rejects Breakup
  suspend fun rejectBreakup(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val rel = _activeRelationship.value ?: return@withContext false to "Không tìm thấy mối quan hệ hiện tại!"

    val revertedRel = rel.copy(
      status = RelationshipStatus.ACTIVE,
      breakupRequestedBy = null,
      breakupRequestedAt = null
    )
    dao.updateOnlineRelationship(revertedRel)
    _activeRelationship.value = revertedRel

    try {
      firestore?.collection("relationships")?.document(rel.relationshipId)?.update(
        mapOf("status" to RelationshipStatus.ACTIVE, "breakupRequestedBy" to null, "breakupRequestedAt" to null)
      )?.await()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-reject sync error: ${e.message}")
    }

    return@withContext true to "Đã từ chối lời chia tay. Mối quan hệ tiếp tục được giữ gìn! ❤️"
  }

  // Force Breakup after 7-14 days timeout
  suspend fun forceBreakup(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    return@withContext confirmBreakup()
  }

  // Update user personal interests
  suspend fun toggleInterest(interestKey: String) = withContext(Dispatchers.IO) {
    val me = _currentUser.value
    val currentSet = me.interests.toMutableSet()
    if (currentSet.contains(interestKey)) {
      currentSet.remove(interestKey)
    } else {
      currentSet.add(interestKey)
    }

    val updatedMe = me.copy(interestsCsv = currentSet.joinToString(","))
    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe

    val partner = _partnerUser.value
    if (partner != null && updatedMe.status == OnlineStatus.COUPLED) {
      _mutualInterests.value = currentSet.intersect(partner.interests.toSet())
    }
  }

  // Get memories accessible for current relationship
  fun getAccessibleMemories(): Flow<List<SharedMemoryEntity>> {
    return dao.getAllSharedMemories().map { list ->
      val status = _relationshipStatus.value
      val relId = _currentUser.value.relationshipId
      if (status == OnlineStatus.COUPLED && !relId.isNullOrBlank()) {
        list.filter { it.relationshipId == null || it.relationshipId == relId }
      } else {
        emptyList()
      }
    }
  }
}
