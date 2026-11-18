package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.cloud.AndroidCloudFunctionsClient
import com.example.data.cloud.CloudFunctionsClient
import com.example.data.cloud.CloudFunctionsCode
import com.example.data.cloud.CloudFunctionsException
import com.example.data.db.InLoveDao
import com.example.data.model.CoupleProfileEntity
import com.example.data.model.InviteStatus
import com.example.data.model.OnlineInviteEntity
import com.example.data.model.OnlineRelationshipEntity
import com.example.data.model.OnlineStatus
import com.example.data.model.OnlineUserEntity
import com.example.data.model.RelationshipStatus
import com.example.data.model.SharedMemoryEntity
import com.example.data.sync.ProfileProjection
import com.example.ui.util.Birthday
import com.example.ui.util.DEFAULT_LOVE_NOTE
import com.example.ui.util.DEFAULT_LOVE_TITLE
import com.example.ui.util.ProfileUtils
import com.example.ui.util.WELCOME_BIO
import com.example.ui.util.cleanName
import com.example.ui.util.isLegacyWelcomeBio
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout


/** Firestore queue writes offline and never completes; bound the wait so offline flows fall through to Room-only. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitBounded(): T = withTimeout(5_000L) { await() }

class OnlineCoupleRepository(
  private val dao: InLoveDao,
  context: Context,
  private val scope: CoroutineScope,
  private val useFirestore: Boolean = true, // false = chế độ cục bộ (test/demo): ghép đôi chỉ ghi Room
  private val functions: CloudFunctionsClient? = if (useFirestore) AndroidCloudFunctionsClient() else null // null = không gọi Cloud Functions
) {

  // Demo user IDs for seamless 1-1 testing on device
  companion object {
    const val GUEST_UID = "guest_user"
    const val USER_A_ID = "test_user_a"
    const val USER_A_CODE = "TEST-8888"
    const val USER_A_NAME = "Tester A"

    const val USER_B_ID = "test_user_b"
    const val USER_B_CODE = "TEST-9999"
    const val USER_B_NAME = "Tester B"

    val AVAILABLE_INTERESTS = listOf(
      "coffee" to "Cà phê",
      "travel" to "Du lịch",
      "technology" to "Công nghệ",
      "cycling" to "Xe đạp",
      "fashion" to "Thời trang",
      "music" to "Âm nhạc",
      "cinema" to "Điện ảnh",
      "cooking" to "Nấu ăn",
      "books" to "Sách & Thơ",
      "gaming" to "Chơi game"
    )

    fun createEmptyUser(uid: String = GUEST_UID): OnlineUserEntity {
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
        bio = WELCOME_BIO,
        isProfileSetup = false,
        isCurrentUser = true
      )
    }
  }

  // Active User StateFlows
  private val _currentUserId = MutableStateFlow(GUEST_UID)
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

  private val stateMutex = Mutex()

  init {
    try {
      if (useFirestore && com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
      }
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore not initialized or offline: ${e.message}")
    }

    mergeAvatarFromDao()
    scope.launch { refreshState() }
    scope.launch { if (cleanStoredText()) refreshState() }
  }

  /**
   * Idempotent cleanup of text older builds stored: names to canonical form (NFC, no emoji, see [cleanName]) and the
   * auto-generated welcome bio without its emoji. A bio the user typed is never touched. Writes only rows that change.
   */
  suspend fun cleanStoredText(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      var changed = 0
      dao.getAllOnlineUsersListSync().forEach { u ->
        val name = cleanName(u.displayName)
        // compare-and-set updates: a concurrent save changes the "expected" value, so we never overwrite it with a stale copy
        if (isLegacyWelcomeBio(u.bio)) changed += dao.replaceOnlineUserBio(u.uid, u.bio, WELCOME_BIO)
        if (name != u.displayName) {
          // an emoji-only name becomes "nobody yet": same shape as a profile never set up
          changed += if (name.isEmpty()) dao.replaceOnlineUserName(u.uid, u.displayName, "Vô danh", false)
          else dao.replaceOnlineUserName(u.uid, u.displayName, name, u.isProfileSetup)
        }
      }
      dao.getCoupleProfileSync()?.let { p ->
        cleanName(p.partner1Name).takeIf { it.isNotEmpty() && it != p.partner1Name }?.let { changed += dao.replaceCouplePartner1Name(p.partner1Name, it) }
        cleanName(p.partner2Name).takeIf { it.isNotEmpty() && it != p.partner2Name }?.let { changed += dao.replaceCouplePartner2Name(p.partner2Name, it) }
      }
      changed > 0
    }.getOrElse { Log.w("OnlineCoupleRepo", "cleanStoredText failed: ${it.message}"); false }
  }

  /** Gộp avatar do AvatarPublisher ghi thẳng vào DAO vào _currentUser, chỉ cho cùng uid (T21). */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun mergeAvatarFromDao() {
    scope.launch {
      _currentUserId.flatMapLatest { uid -> dao.getOnlineUserByUid(uid) }
        .map { row -> row?.let { it.uid to it.avatarUrl } }
        .distinctUntilChanged()
        .collect { row ->
          if (row != null) _currentUser.update { if (it.uid == row.first && it.avatarUrl != row.second) it.copy(avatarUrl = row.second) else it }
        }
    }
  }

  /**
   * Hộp thoại sửa hồ sơ giữ avatar `file:` từ lúc mở. Nếu AvatarPublisher đã đổi nó sang https và xoá file trong lúc đó,
   * bản nháp cũ không được ghi lại một `file:` đã mất (T15/B-F2). Avatar vừa chọn thì file còn tồn tại nên đi qua bình thường.
   */
  private fun keepLiveAvatar(live: String, submitted: String): String {
    val gone = submitted.startsWith("file:", ignoreCase = true) &&
      android.net.Uri.parse(submitted).path?.let { !java.io.File(it).exists() } == true
    return if (gone && live.startsWith("https://", ignoreCase = true)) live else submitted
  }

  /** Avatar hiện có trong Room: AvatarPublisher có thể đã đổi nó trong lúc một `me` cũ được giữ qua một lần gọi mạng. */
  private suspend fun liveAvatar(uid: String, fallback: String): String =
    dao.getOnlineUserByUidSync(uid)?.avatarUrl ?: fallback

  /**
   * Ghi cả hàng "tôi" nhưng giữ avatar đang có trong Room, để một `me` cũ không đè lại avatar vừa được publish.
   * Không dùng cho updateMyProfile: người dùng chủ động đổi avatar ở đó.
   */
  // ponytail: đọc rồi ghi không nguyên tử; còn một khoảng dưới mili giây giữa hai lệnh. Nếu cần kín: DAO chỉ cập nhật cột trạng thái.
  private suspend fun saveMe(updated: OnlineUserEntity): OnlineUserEntity =
    updated.copy(avatarUrl = liveAvatar(updated.uid, updated.avatarUrl)).also { dao.updateOnlineUser(it) }

  suspend fun setCurrentUserId(uid: String) = withContext(Dispatchers.IO) {
    if (uid.isBlank()) {
      stateMutex.withLock {
        _currentUserId.value = GUEST_UID
        _currentUser.value = createEmptyUser()
        _partnerUser.value = null
        _activeRelationship.value = null
        _relationshipStatus.value = OnlineStatus.SINGLE
      }
      return@withContext
    }
    _currentUserId.value = uid
    refreshState()
  }

  // One writer at a time: init's refresh and setCurrentUserId's refresh used to race, last writer won.
  suspend fun refreshState() = withContext(Dispatchers.IO) { stateMutex.withLock { reloadState() } }

  private suspend fun reloadState() {
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
    bio: String,
    clearBirthday: Boolean = false
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    // Room trước cache: trạng thái/đối tác do lớp đồng bộ ghi sau khi cache nạp không bị ghi đè.
    val me = dao.getOnlineUserByUidSync(_currentUser.value.uid) ?: _currentUser.value
    val trimmedName = cleanName(name)
    val isSetup = trimmedName.isNotEmpty() && trimmedName != "Vô danh"
    val finalDisplayName = if (isSetup) trimmedName else "Vô danh"

    // Một ngày sinh hiệu lực: rỗng/sai thì giữ giá trị đã lưu (đường đổi tên gửi lại ngày sinh cũ, kể cả dạng "5/5/95")
    // clearBirthday = người dùng chủ động xoá (nút X): ghi rỗng, tuổi 0, cung "Chưa rõ".
    val effective = if (clearBirthday) "" else Birthday.parse(birthDate)?.text ?: me.birthDate
    val calculatedAge = ProfileUtils.calculateAge(effective)
    val calculatedZodiac = ProfileUtils.calculateZodiac(effective).first

    val updatedMe = me.copy(
      displayName = finalDisplayName,
      birthDate = effective,
      age = calculatedAge,
      zodiac = calculatedZodiac,
      avatarUrl = keepLiveAvatar(me.avatarUrl, avatarUrl.trim()),
      gender = gender,
      bio = bio.trim(),
      isProfileSetup = isSetup
    )

    dao.updateOnlineUser(updatedMe)
    _currentUser.value = updatedMe

    mirrorMe(updatedMe, isSetup)

    refreshState()
    return@withContext true to "Đã cập nhật hồ sơ cá nhân thành công!"
  }

  /** Chép hồ sơ "tôi" sang account đăng nhập (đổi tên) và couple profile (partner1*); dùng chung cho updateMyProfile và applyRemoteProfile. */
  private suspend fun mirrorMe(updatedMe: OnlineUserEntity, isSetup: Boolean) {
    if (isSetup) {
      dao.getUserAccountByUid(updatedMe.uid)?.takeIf { it.displayName != updatedMe.displayName }
        ?.let { dao.updateUserAccount(it.copy(displayName = updatedMe.displayName)) }
    }

    // Nơi duy nhất ghi "tôi" vào Room: partner1* luôn là tôi (theo vai trò, không theo giới tính).
    // Chỉ cập nhật khi đã có dòng; không tạo dòng mới, không đụng partner2*, tiêu đề, ngày yêu.
    val currentProfile = dao.getCoupleProfileSync()
    if (currentProfile != null) {
      dao.insertCoupleProfile(
        currentProfile.copy(
          partner1Name = updatedMe.effectiveDisplayName,
          partner1Birthday = updatedMe.birthDate,
          partner1Age = updatedMe.age,
          partner1Zodiac = updatedMe.zodiac,
          partner1ProfilePicture = updatedMe.avatarUrl
        )
      )
    }
  }

  /**
   * Áp dụng hồ sơ từ Firestore lên dòng "tôi" của [uid] (ProfileSync). Trả false (không ghi gì) khi dòng không có,
   * không phải người dùng hiện tại, hoặc key cục bộ đã đổi khỏi [expectedLocalKey].
   * Chỉ ghi trường mà giá trị đã chiếu KHÁC (giữ ngày sinh thô, bio dài, giới tính lạ trên máy); avatar theo B-02:
   * không bao giờ xoá ảnh file cục bộ. Không đụng email, coupleCode, partnerId, relationshipId, status.
   * ponytail: updateMyProfile không giữ khoá chung, nên còn cửa sổ đua vài ms giữa kiểm key và ghi; nâng cấp = Mutex trong repository.
   * ponytail: 12 hàm ghép đôi bắt đầu từ bản sao _currentUser rồi saveMe(me.copy(...)) ghi CẢ dòng, nên một hàm đã đọc bản sao cũ
   * có thể ghi đè trường vừa áp dụng; khoá cục bộ lệch nên ProfileSync đẩy hồ sơ cũ đó một lần; nâng cấp = đọc lại Room trong các hàm ghi.
   * KHÔNG BAO GIỜ gọi refreshState() khi đang giữ stateMutex (khoá không tái nhập).
   */
  suspend fun applyRemoteProfile(uid: String, p: ProfileProjection, expectedLocalKey: String): Boolean = withContext(Dispatchers.IO) {
    val me = dao.getOnlineUserByUidSync(uid) ?: return@withContext false
    if (!me.isCurrentUser) return@withContext false
    val local = ProfileProjection.of(me)
    if (local.key() != expectedLocalKey) return@withContext false

    val name = (if (local.displayName == p.displayName) me.displayName else p.displayName).ifBlank { me.displayName }
    val birthDate = if (local.birthDate == p.birthDate) me.birthDate else p.birthDate
    val birthChanged = birthDate != me.birthDate
    val updated = me.copy(
      displayName = name,
      birthDate = birthDate,
      age = if (birthChanged) ProfileUtils.calculateAge(birthDate) else me.age,
      zodiac = if (birthChanged) ProfileUtils.calculateZodiac(birthDate).first else me.zodiac,
      gender = if (local.gender == p.gender) me.gender else p.gender,
      bio = if (local.bio == p.bio) me.bio else p.bio,
      interestsCsv = if (local.interestsCsv == p.interestsCsv) me.interestsCsv else p.interestsCsv,
      avatarUrl = remoteAvatar(me.avatarUrl, p.avatarUrl),
      isProfileSetup = name.isNotEmpty() && name != "Vô danh"
    )

    dao.updateOnlineUser(updated)
    if (_currentUser.value.uid == uid) _currentUser.value = updated
    mirrorMe(updated, updated.isProfileSetup)
    refreshState()
    true
  }

  /** B-02: null = giữ; "" chỉ xoá avatar https; https chỉ thay avatar trống hoặc https. Ảnh file cục bộ không bao giờ bị thay hay xoá. */
  private fun remoteAvatar(live: String, remote: String?): String {
    val liveIsRemote = live.startsWith("https://", ignoreCase = true)
    return when {
      remote == null -> live
      remote.isEmpty() -> if (liveIsRemote) "" else live
      remote.startsWith("https://", ignoreCase = true) && (live.isBlank() || liveIsRemote) -> remote
      else -> live
    }
  }

  /**
   * Đăng ký [code] của [uid] lên coupleCodes/{code} để người khác tìm được bằng mã.
   * Rules chỉ cho tạo mới, không ghi đè: đã có đúng chủ thì thành công, có chủ khác thì báo lỗi.
   */
  suspend fun publishCoupleCode(uid: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
    val fs = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore chưa sẵn sàng"))
    if (uid.isBlank() || code.isBlank()) return@withContext Result.failure(IllegalArgumentException("Thiếu uid hoặc mã ghép đôi"))
    try {
      val doc = fs.collection("coupleCodes").document(code)
      val snap = doc.get().awaitBounded()
      when {
        !snap.exists() -> doc.set(mapOf("code" to code, "ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp())).awaitBounded()
        snap.getString("ownerUid") == uid -> Unit // rules không cho update, nên không ghi lại
        else -> return@withContext Result.failure(IllegalStateException("Mã ghép đôi đã thuộc tài khoản khác"))
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "publishCoupleCode failed: ${e.message}")
      Result.failure(e)
    }
  }

  /** Resolves a partner's uid from their shared code via the public coupleCodes lookup. */
  suspend fun lookupOwnerUidByCode(code: String): String? = withContext(Dispatchers.IO) {
    // ponytail: chỉ chế độ cục bộ tường minh (useFirestore=false, test/demo) tra mã trong Room; đã yêu cầu Firestore mà khởi tạo lỗi thì trả null, không tin Room.
    if (!useFirestore) return@withContext dao.getOnlineUserByCoupleCodeSync(code)?.uid
    val fs = firestore ?: return@withContext null
    try {
      fs.collection("coupleCodes").document(code).get().awaitBounded().getString("ownerUid")
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "lookupOwnerUidByCode error: ${e.message}")
      null
    }
  }

  /**
   * Tìm đối tác theo mã/link qua coupleCodes (remote) thay vì bảng Room cục bộ.
   * Chỉ có ownerUid từ lookup, nên nếu chưa có hàng cục bộ thì trả hồ sơ tối thiểu (uid + mã).
   */
  private suspend fun findPartnerByCode(code: String): OnlineUserEntity? {
    if (code.isEmpty()) return null
    val ownerUid = lookupOwnerUidByCode(code) ?: return null
    if (ownerUid == _currentUser.value.uid) return null
    return dao.getOnlineUserByUidSync(ownerUid) ?: OnlineUserEntity(uid = ownerUid, coupleCode = code)
  }

  // Search user by code or full shared link
  suspend fun searchUserByCodeOrLink(input: String): OnlineUserEntity? = withContext(Dispatchers.IO) {
    findPartnerByCode(ProfileUtils.extractCoupleCode(input))
  }

  // Search user by code, name, or email for Set Love
  suspend fun searchUserByCodeOrNameOrEmail(input: String): OnlineUserEntity? = withContext(Dispatchers.IO) {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return@withContext null
    findPartnerByCode(ProfileUtils.extractCoupleCode(trimmed))?.let { return@withContext it }
    // ponytail: tên/email chỉ tìm trong Room cục bộ vì users không đọc công khai (spec); cần callable function nếu muốn tìm từ xa.
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
      senderAvatar = ProfileUtils.shareableAvatar(me.avatarUrl),
      senderCoupleCode = me.coupleCode,
      senderBirthDate = me.birthDate,
      senderAge = me.age,
      senderZodiac = me.zodiac,
      senderBio = me.bio,
      targetCoupleCode = trimmedCode,
      targetUid = targetOwnerUid,
      proposedStartDate = proposedStartDateMillis,
      proposedStartDateText = ProfileUtils.formatDate(proposedStartDateMillis),
      loveNote = loveNote.trim().ifEmpty { DEFAULT_LOVE_NOTE },
      status = InviteStatus.PENDING,
      createdAt = now,
      updatedAt = now,
      pendingSync = true
    )

    dao.insertOnlineInvite(invite)
    _outgoingInvite.value = invite

    val updatedMe = saveMe(me.copy(status = OnlineStatus.PENDING_INVITE))
    _currentUser.value = updatedMe
    _relationshipStatus.value = OnlineStatus.PENDING_INVITE

    if (targetUser.uid == _partnerUser.value?.uid) {
      _incomingInvite.value = invite
    }

    try {
      firestore?.collection("invites")?.document(invite.inviteId)?.set(invite)?.awaitBounded()
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

    // Hàng người gửi có thể chưa có trong Room (máy mới/chưa đồng bộ): dựng tạm từ lời mời, chỉ ghi sau khi máy chủ xác nhận.
    val senderBirth = Birthday.normalize(incoming.senderBirthDate)
    val sender = dao.getOnlineUserByUidSync(incoming.senderUid) ?: OnlineUserEntity(
      uid = incoming.senderUid,
      displayName = incoming.senderName,
      coupleCode = incoming.senderCoupleCode,
      avatarUrl = incoming.senderAvatar,
      birthDate = senderBirth, // giá trị từ xa sai định dạng thì rỗng
      age = ProfileUtils.calculateAge(senderBirth), // tuổi và cung suy từ ngày sinh đã chuẩn hoá, không tin số gửi kèm (T15/A-3)
      zodiac = ProfileUtils.calculateZodiac(senderBirth).first,
      bio = incoming.senderBio,
      isProfileSetup = incoming.senderName.isNotBlank() && incoming.senderName != "Vô danh"
    )

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

    // Máy chủ nhận lời mời + tạo relationship (id = id lời mời) trong một transaction; rules cấm client tự tạo.
    // Gọi lặp an toàn (idempotent). Chỉ ghi COUPLED cục bộ sau khi máy chủ xác nhận.
    try {
      // Client đã tự áp timeout 20s + vùng, nên không bọc withTimeout ở đây.
      functions?.call(
        "acceptCoupleInvite",
        mapOf("inviteId" to incoming.inviteId, "startDateMillis" to finalStartDate, "startDateText" to finalStartDateText)
      )
      // insertOnlineRelationship, not updateOnlineRelationship: @Update matches by primary
      // key and silently does nothing if the row doesn't exist yet locally — which it never
      // does at this point, since this relationship id is brand new. insertOnlineRelationship
      // uses OnConflictStrategy.REPLACE, so it correctly creates the row here.
      val synced = relationship.copy(pendingSync = false)
      dao.insertOnlineRelationship(synced)
      _activeRelationship.value = synced
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "acceptCoupleInvite error: ${e.message}")
      // Kết nối thật mà lỗi/hết giờ: KHÔNG ghi trạng thái COUPLED cục bộ (server chưa có relationship). Giữ lời mời để thử lại.
      if (functions != null) {
        val rejected = (e as? CloudFunctionsException)?.code in setOf(
          CloudFunctionsCode.FAILED_PRECONDITION,
          CloudFunctionsCode.PERMISSION_DENIED,
          CloudFunctionsCode.NOT_FOUND
        )
        return@withContext false to if (rejected) {
          "Không thể chấp nhận lời mời này (đã xử lý, hết hiệu lực, hoặc một trong hai bạn đang có mối quan hệ khác)."
        } else {
          "Chưa kết nối được máy chủ, vui lòng thử lại khi có mạng."
        }
      }
      dao.insertOnlineRelationship(relationship)
      _activeRelationship.value = relationship
    }

    // Update both users to COUPLED. `me` được giữ qua cuộc gọi máy chủ tới 20 giây: avatar lấy lại từ Room khi ghi.
    val updatedMe = saveMe(
      me.copy(
        status = OnlineStatus.COUPLED,
        partnerId = sender.uid,
        relationshipId = relId
      )
    )
    val updatedSender = sender.copy(
      status = OnlineStatus.COUPLED,
      partnerId = me.uid,
      relationshipId = relId
    )

    dao.insertOnlineUser(updatedSender) // REPLACE: updateOnlineUser sẽ không làm gì nếu hàng người gửi chưa có

    _currentUser.value = updatedMe
    _partnerUser.value = updatedSender
    _relationshipStatus.value = OnlineStatus.COUPLED

    // Clean up invite
    dao.deleteOnlineInvite(incoming.inviteId)
    _incomingInvite.value = null
    _outgoingInvite.value = null

    // Update couple profile in Room for Home screen
    val currentProfile = dao.getCoupleProfileSync()
    val p1 = updatedMe // partner1* = tôi, partner2* = người ấy, bất kể giới tính
    val p2 = updatedSender

    // Tiêu đề cũ chỉ giữ khi chưa từng yêu cặp khác (lần đầu hoặc quay lại đúng người cũ); người mới nhận tiêu đề mặc định.
    val pair = setOf(me.uid, sender.uid)
    val keepTitle = dao.getAllRelationshipsSync().none { it.relationshipId != relId && setOf(it.user1, it.user2) != pair }

    val syncedProfile = CoupleProfileEntity(
      id = 1,
      partner1Name = p1.effectiveDisplayName,
      partner1Birthday = p1.birthDate,
      partner1ProfilePicture = liveAvatar(me.uid, p1.avatarUrl), // REPLACE bên dưới: đọc lại sát lúc ghi
      partner1Age = p1.age,
      partner1Zodiac = p1.zodiac,
      partner2Name = p2.effectiveDisplayName,
      partner2Birthday = p2.birthDate,
      partner2ProfilePicture = p2.avatarUrl,
      partner2Age = p2.age,
      partner2Zodiac = p2.zodiac,
      loveTitle = currentProfile?.loveTitle?.takeIf { keepTitle && it.isNotBlank() } ?: DEFAULT_LOVE_TITLE,
      loveDays = finalLoveDays,
      anniversaryDate = finalStartDateText,
      updatedAt = System.currentTimeMillis(),
      // insertCoupleProfile là REPLACE: giữ nguyên sở thích đã nhập
      likesCsv = currentProfile?.likesCsv ?: "",
      budgetMaxVnd = currentProfile?.budgetMaxVnd ?: 0,
      occasionRegion = currentProfile?.occasionRegion ?: ""
    )
    dao.insertCoupleProfile(syncedProfile)

    refreshState()
    return@withContext true to "Chúc mừng hai bạn đã chính thức Set Love 1-1 bên nhau! ❤️"
  }

  // Reject incoming invite
  suspend fun rejectSetLoveInvite(inviteId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val incoming = _incomingInvite.value ?: dao.getInviteByIdSync(inviteId)
    if (incoming != null) {
      // Must reach Firestore, not just delete the local row: otherwise the identity-tier
      // invites listener (SyncCoordinator, queried with no status filter) still sees this
      // invite as PENDING remotely and silently re-inserts it into Room on the next snapshot.
      try {
        firestore?.collection("invites")?.document(incoming.inviteId)
          ?.update(mapOf("status" to InviteStatus.DECLINED))?.awaitBounded()
      } catch (e: Exception) {
        Log.d("OnlineCoupleRepo", "Firestore invite-decline sync error, will retry via outbox in a later task: ${e.message}")
      }
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
    val outgoing = _outgoingInvite.value ?: dao.getActiveOutgoingInviteSync(me.uid)

    if (outgoing != null) {
      // Same reasoning as rejectSetLoveInvite: without this, the receiver's invites listener
      // still sees PENDING remotely and can still Accept an invite the sender already cancelled.
      try {
        firestore?.collection("invites")?.document(outgoing.inviteId)
          ?.update(mapOf("status" to InviteStatus.CANCELLED))?.awaitBounded()
      } catch (e: Exception) {
        Log.d("OnlineCoupleRepo", "Firestore invite-cancel sync error, will retry via outbox in a later task: ${e.message}")
      }
      dao.deleteOnlineInvite(outgoing.inviteId)
    }

    val updatedMe = saveMe(me.copy(status = OnlineStatus.SINGLE))
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
      )?.awaitBounded()
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
      )?.awaitBounded()
    } catch (e: Exception) {
      Log.d("OnlineCoupleRepo", "Firestore breakup-confirm sync error: ${e.message}")
    }

    // Both users return to SINGLE (soft locks shared memories)
    val updatedMe = saveMe(
      me.copy(
        status = OnlineStatus.SINGLE,
        partnerId = null,
        relationshipId = null
      )
    )
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
      )?.awaitBounded()
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

    val updatedMe = saveMe(me.copy(interestsCsv = currentSet.joinToString(",")))
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
