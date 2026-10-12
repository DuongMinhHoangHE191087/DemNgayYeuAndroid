package com.app.plugin.license

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.app.plugin.BuildConfig
import com.app.plugin.license.LicenseGate.Verdict
import com.app.plugin.until.LogUtil
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * 2.4.3 LCACHE (Hợp đồng A, quyết định (a)): nhớ verdict VALID qua cold start để bỏ parse + Ed25519 `BigInteger`.
 * Gọi từ `LicenseGate.verify` trên luồng `appplugin-license` (không bao giờ main). Prefs riêng [FILE], một mục `v1` =
 * `<khoá>|<kid>|<expSec>`; khoá = SHA-256 hex của các trường có tiền tố độ dài: `ALPC1`, `BuildConfig.SDK_VERSION`, gói,
 * token thô, tập cert (thường hoá, bỏ trùng, sắp xếp), tập `kid:hex(pub)` của `LicenseGate.keys` (sắp xếp).
 *
 * Trúng ⇔ khoá bằng **và** `expSec > clock()/1000` ⇒ `Valid(kid, expSec)`. Mọi lỗi (prefs/cert ném, mục hỏng, khoá khác,
 * hết hạn) ⇒ `LicenseGate.evaluate` đầy đủ như cũ — không bao giờ VALID vì lỗi. Chỉ ghi sau `Valid` (`apply()`); không cache
 * INVALID. Cert đọc một lần và `evaluate` dùng lại; cert ném ⇒ bỏ cache, `evaluate` với provider gốc (lý do giữ nguyên chữ).
 * Một dòng `Log.i` mỗi lần (mỗi process): `license: source=cache|verify ms=<n> since_start=<m> cert=<c>`. `clearLocalData`
 * xoá [FILE] ([clear]).
 */
internal object LicenseCache {
    const val FILE: String = "appplugin_license"
    private const val ENTRY = "v1"
    private const val SALT = "ALPC1"
    private const val CACHE = "cache"
    private const val VERIFY = "verify"

    /** Bản SDK trong khoá: field `private` (dump 0) để test đổi qua reflection (R-LA L2), không đọc hằng nội tuyến. */
    @Volatile private var sdkVersion: String = BuildConfig.SDK_VERSION

    /**
     * Verdict cho token [token] của [app]: cache hoặc verify đầy đủ; mọi ngoại lệ = `INVALID (error)` (fail-closed).
     * Dòng log (F1 C1): `ms` = wall của hàm này, `since_start` = từ [launchNs] (`launch`, trước `Thread.start`) tới verdict,
     * `cert` = wall đọc cert (IPC + SHA-256; 0 khi không đọc). Đồng hồ đơn điệu `System.nanoTime`, chỉ trên luồng license.
     */
    fun resolve(app: Context, launchNs: Long, token: () -> String?): Verdict {
        val startNs = System.nanoTime()
        var certNs = 0L
        val certs = {
            val t = System.nanoTime()
            try { LicenseGate.certsProvider(app) } finally { certNs += System.nanoTime() - t }
        }
        val (verdict, source) = try {
            lookup(app, token(), certs)
        } catch (e: Throwable) {
            Verdict.Invalid("error") to VERIFY
        }
        try {
            val end = System.nanoTime()
            Log.i(LogUtil.TAG, "license: source=$source ms=${ms(end - startNs)} since_start=${ms(end - launchNs)} cert=${ms(certNs)}")
        } catch (e: Throwable) {
            // log không được làm hỏng verdict
        }
        return verdict
    }

    private fun ms(ns: Long): Long = ns / 1_000_000

    private fun lookup(app: Context, raw: String?, readCerts: () -> List<String>): Pair<Verdict, String> {
        val pkg = app.packageName
        if (raw.isNullOrBlank()) return LicenseGate.evaluate(raw, pkg, readCerts, LicenseGate.clock()) to VERIFY
        val certs = try {
            readCerts()
        } catch (e: Throwable) {
            return LicenseGate.evaluate(raw, pkg, readCerts, LicenseGate.clock()) to VERIFY
        }
        val now = LicenseGate.clock()
        val prefs = try { app.getSharedPreferences(FILE, Context.MODE_PRIVATE) } catch (e: Throwable) { null }
        val key = try { key(pkg, raw, certs) } catch (e: Throwable) { null }
        hit(prefs, key, now)?.let { return it to CACHE }
        val verdict = LicenseGate.evaluate(raw, pkg, { certs }, now)
        if (verdict is Verdict.Valid && key != null) store(prefs, "$key|${verdict.kid}|${verdict.expSec}")
        return verdict to VERIFY
    }

    /** Mục khớp [key] và chưa hết hạn ở [nowMs]; khác, hỏng hay ném ⇒ `null` (verify đầy đủ). */
    private fun hit(prefs: SharedPreferences?, key: String?, nowMs: Long): Verdict.Valid? = try {
        val rest = key?.let { k -> prefs?.getString(ENTRY, null)?.takeIf { it.startsWith("$k|") }?.substring(k.length + 1) }
        val kid = rest?.substringBeforeLast('|', "")
        val exp = rest?.substringAfterLast('|', "")?.toLongOrNull()
        if (kid.isNullOrEmpty() || exp == null || exp <= nowMs / 1000) null else Verdict.Valid(kid, exp)
    } catch (e: Throwable) {
        null
    }

    private fun store(prefs: SharedPreferences?, value: String) {
        try {
            prefs?.edit()?.putString(ENTRY, value)?.apply()
        } catch (e: Throwable) {
            // không ghi được: lần mở sau verify đầy đủ
        }
    }

    /** SHA-256 hex của các trường có tiền tố độ dài (quyết định (a)); tập được thường hoá rồi sắp xếp. */
    private fun key(pkg: String, raw: String, certs: List<String>): String {
        val certSet = certs.map { it.lowercase() }.toSortedSet()
        val keySet = LicenseGate.keys().map { (kid, pub) -> "$kid:${hex(pub)}" }.sorted()
        val fields = listOf(SALT, sdkVersion, pkg, raw, "c${certSet.size}") + certSet + "k${keySet.size}" + keySet
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fields.forEach { f -> f.toByteArray(Charsets.UTF_8).let { out.writeInt(it.size); out.write(it) } }
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()))
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /** `clearLocalData`: xoá [FILE] (đồng bộ; gọi trên luồng nền của `LocalDataReset`). */
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
