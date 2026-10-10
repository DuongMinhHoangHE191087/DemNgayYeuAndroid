package com.example.domain.media

import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Trả về publicId cần lưu trên kỷ niệm, hoặc null khi kỷ niệm chỉ ở trên máy này. */
class UploadMemoryMediaUseCase(private val repository: MemoryMediaRepository) {
  suspend operator fun invoke(
    shareWithCouple: Boolean,
    relationshipId: String?,
    memoryId: String,
    media: MemoryMediaFile?,
  ): Result<String?> {
    if (!shareWithCouple || media == null || relationshipId.isNullOrBlank()) return Result.success(null)
    return runCatchingNonCancellation { repository.upload(relationshipId, memoryId, media) }
  }
}

sealed interface SaveSharedMemoryOutcome {
  data class Saved(val unpaired: Boolean) : SaveSharedMemoryOutcome
  data object NotSignedIn : SaveSharedMemoryOutcome
  data class InvalidMedia(val message: String) : SaveSharedMemoryOutcome
  data object UploadFailed : SaveSharedMemoryOutcome
  data object SaveFailed : SaveSharedMemoryOutcome
}

/**
 * Lưu kỷ niệm mới. Kỷ niệm riêng tư hoặc chưa ghép đôi không bao giờ được tải lên; kỷ niệm chia sẻ
 * chỉ được ghi khi ảnh/video đã tải lên xong, nên không có kỷ niệm nào trỏ tới ảnh chưa được xác nhận.
 */
class SaveSharedMemoryUseCase(private val uploadMedia: UploadMemoryMediaUseCase) {
  suspend operator fun invoke(
    signedIn: Boolean,
    relationshipId: String?,
    wantsShare: Boolean,
    media: MemoryMediaFile?,
    store: suspend (memoryId: String, shareWithCouple: Boolean, publicId: String?) -> Unit,
  ): SaveSharedMemoryOutcome {
    if (wantsShare && !signedIn) return SaveSharedMemoryOutcome.NotSignedIn
    // Chưa ghép đôi thì chưa có ai để chia sẻ: lưu riêng trên máy và báo cho người dùng biết.
    val unpaired = wantsShare && relationshipId.isNullOrBlank()
    val shareWithCouple = wantsShare && !unpaired
    if (media != null) {
      validateMemoryMedia(media.kind, media.mimeType, media.sizeBytes, media.durationSeconds)?.let {
        return SaveSharedMemoryOutcome.InvalidMedia(it)
      }
    }
    // memoryId cũng là syncId: máy chủ chỉ chấp nhận publicId = tiền tố + memoryId.
    val memoryId = UUID.randomUUID().toString()
    val publicId = uploadMedia(shareWithCouple, relationshipId, memoryId, media).getOrElse {
      return SaveSharedMemoryOutcome.UploadFailed
    }
    return try {
      store(memoryId, shareWithCouple, publicId)
      SaveSharedMemoryOutcome.Saved(unpaired)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      SaveSharedMemoryOutcome.SaveFailed
    }
  }
}

class ResolveMemoryMediaUrlUseCase(private val repository: MemoryMediaRepository) {
  /** Chỉ cấp URL cho kỷ niệm của đúng cặp đôi đang đăng nhập; kỷ niệm của cặp khác không gửi yêu cầu nào. */
  suspend operator fun invoke(currentRelationshipId: String?, relationshipId: String, memoryId: String): Result<String> {
    if (currentRelationshipId.isNullOrBlank() || relationshipId != currentRelationshipId || memoryId.isBlank()) {
      return Result.failure(IllegalStateException("Kỷ niệm không thuộc cặp đôi hiện tại hoặc thiếu mã."))
    }
    return runCatchingNonCancellation { repository.deliveryUrl(relationshipId, memoryId) }
  }
}

// runCatching nuốt cả CancellationException; coroutine bị huỷ phải được ném tiếp.
private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> = try {
  Result.success(block())
} catch (e: CancellationException) {
  throw e
} catch (e: Exception) {
  Result.failure(e)
}
