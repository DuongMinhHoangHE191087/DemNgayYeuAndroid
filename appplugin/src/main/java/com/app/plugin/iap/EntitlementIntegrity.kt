package com.app.plugin.iap

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.app.plugin.until.LogUtil
import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Khoá ký bản ghi quyền lợi (1.8.0 Task 6, D10). Chỉ dùng trên luồng "appplugin-billing" (Keystore là IPC, không lên main). */
internal interface MacKey {
    fun mac(data: ByteArray): ByteArray
}

/**
 * Khoá HMAC-SHA256 trong AndroidKeyStore, alias [EntitlementIntegrity.ALIAS]: không đọc ra được, không theo backup/chuyển máy,
 * nên bản ghi khôi phục sang máy khác là [StoredEntitlements.Invalid] và Play cấp lại đúng tập đang sở hữu. Ném lỗi khi không
 * có provider (JVM test, vài OEM) — [EntitlementIntegrity.selectKey] rơi về [ProcessRandomMacKey].
 */
internal class KeystoreMacKey : MacKey {
    private val key: Key = KeyStore.getInstance(STORE).apply { load(null) }.getKey(EntitlementIntegrity.ALIAS, null)
        ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, STORE)
            .apply { init(KeyGenParameterSpec.Builder(EntitlementIntegrity.ALIAS, KeyProperties.PURPOSE_SIGN).build()) }
            .generateKey()

    override fun mac(data: ByteArray): ByteArray = EntitlementIntegrity.hmac(key, data)

    private companion object {
        const val STORE = "AndroidKeyStore"
    }
}

/** Fallback khi Keystore lỗi: khoá ngẫu nhiên chỉ sống trong process. Bản ghi của process trước thành Invalid → Play khôi phục. */
internal class ProcessRandomMacKey : MacKey {
    private val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, EntitlementIntegrity.ALGO)

    override fun mac(data: ByteArray): ByteArray = EntitlementIntegrity.hmac(key, data)
}

/**
 * Kết quả kiểm bản đã lưu. [ids] là tập được nhận; [masks] (chỉ bản có chữ ký) thay mask đọc từ khoá chưa ký cho tới khi app
 * gọi `setRemoveAdsMasks` trong process. [result] là chữ trong log (không dùng tên lớp: R8 đổi tên).
 */
internal sealed class StoredEntitlements(val result: String, val ids: Set<String>, val masks: Map<String, Int>? = null) {
    class Verified(ids: Set<String>, masks: Map<String, Int>?) : StoredEntitlements("Verified", ids, masks)

    /** Giá trị chưa ký của bản < 1.8.0: nhận một lần rồi ký lại. */
    class Legacy(ids: Set<String>) : StoredEntitlements("Legacy", ids)

    /** MAC sai, hỏng, hay khoá khác (backup sang máy khác): rỗng, khôi phục từ Play, không bao giờ thu hồi gì trên Play. */
    object Invalid : StoredEntitlements("Invalid", emptySet())
}

/** Bản ghi `v1|<ids sort>|<masks sort>|<hex mac>` (dạng cố định, D10): ids nối `,`, masks `id:mask` nối `,`. */
internal object EntitlementIntegrity {
    const val ALIAS = "appplugin_ent_v1"
    const val ALGO = "HmacSHA256"
    private const val VERSION = "v1"

    /** Không có bản ghi lẫn khoá cũ: không có gì để kiểm hay áp. */
    val NOTHING = StoredEntitlements.Verified(emptySet(), null)

    /** Khoá đang dùng; `null` = chọn ở lần ký/kiểm đầu tiên (luồng billing). Test đặt khoá giả. */
    @Volatile var key: MacKey? = null

    private fun key(): MacKey = key ?: selectKey { KeystoreMacKey() }.also { key = it }

    /** Keystore ném lỗi → [ProcessRandomMacKey] và một dòng cảnh báo (một lần mỗi process: khoá được giữ lại). */
    fun selectKey(keystore: () -> MacKey): MacKey = try {
        keystore().also { it.mac(ByteArray(1)) }
    } catch (e: Exception) {
        LogUtil.logE("ent key: Keystore lỗi (${e.javaClass.simpleName}: ${e.message}), dùng ProcessRandomMacKey — quyền lợi lấy lại từ Play mỗi process")
        ProcessRandomMacKey()
    }

    fun hmac(key: Key, data: ByteArray): ByteArray = Mac.getInstance(ALGO).run { init(key); doFinal(data) }

    /** Ký trạng thái. Id rỗng hay chứa `|`, `,`, `:` không ghi được theo dạng trên nên bỏ. `null` = khoá lỗi (có log). */
    fun sign(ids: Set<String>, masks: Map<String, Int>): String? {
        fun ok(id: String) = id.isNotBlank() && id.none { it in "|,:" }
        val payload = listOf(
            VERSION,
            ids.filter(::ok).sorted().joinToString(","),
            masks.filterKeys(::ok).map { "${it.key}:${it.value}" }.sorted().joinToString(",")
        ).joinToString("|")
        return try {
            "$payload|${hex(key().mac(payload.toByteArray()))}"
        } catch (e: Exception) {
            LogUtil.logE("ent sign failed: ${e.message}")
            null
        }
    }

    /** Kiểm [record] (khoá mới); không có thì [legacyIds] (khoá cũ chưa ký). Không có gì = Verified rỗng. */
    fun check(record: String, legacyIds: Set<String>): StoredEntitlements {
        if (record.isEmpty()) return if (legacyIds.isEmpty()) NOTHING else StoredEntitlements.Legacy(legacyIds)
        val cut = record.lastIndexOf('|')
        val parts = if (cut < 0) emptyList() else record.substring(0, cut).split('|')
        if (parts.size != 3 || parts[0] != VERSION) return StoredEntitlements.Invalid
        val expected = try { hex(key().mac(record.substring(0, cut).toByteArray())) } catch (e: Exception) { return StoredEntitlements.Invalid }
        if (!MessageDigest.isEqual(expected.toByteArray(), record.substring(cut + 1).toByteArray())) return StoredEntitlements.Invalid
        val masks = parts[2].split(',').filter { it.isNotEmpty() }.associate { entry ->
            entry.substringBefore(':') to (entry.substringAfter(':', "").toIntOrNull() ?: return StoredEntitlements.Invalid)
        }
        return StoredEntitlements.Verified(parts[1].split(',').filter { it.isNotEmpty() }.toSet(), masks)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
