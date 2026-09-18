package com.example.data.email

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Cấu hình thông tin máy chủ SMTP gửi email thật.
 * Tự động đọc từ BuildConfig được nạp từ file .env.
 */
data class SmtpConfig(
  val host: String = try { BuildConfig.SMTP_HOST.ifBlank { "smtp.gmail.com" } } catch (_: Throwable) { "smtp.gmail.com" },
  val port: String = try { BuildConfig.SMTP_PORT.ifBlank { "587" } } catch (_: Throwable) { "587" },
  val senderEmail: String = try { BuildConfig.SMTP_SENDER_EMAIL } catch (_: Throwable) { "" },
  val senderPassword: String = try { BuildConfig.SMTP_SENDER_PASSWORD } catch (_: Throwable) { "" },
  val senderName: String = try { BuildConfig.SMTP_SENDER_NAME.ifBlank { "InLove App" } } catch (_: Throwable) { "InLove App" }
) {
  val isConfigured: Boolean
    get() = senderEmail.isNotBlank() && senderPassword.isNotBlank()
}

/**
 * Dịch vụ gửi email THẬT qua giao thức SMTP (Gmail SMTP, Custom SMTP, Relay)
 * Sử dụng JavaMail Android, kết nối bảo mật TLS/STARTTLS trên Dispatchers.IO.
 */
class RealSmtpEmailSender(private val config: SmtpConfig = SmtpConfig()) {

  companion object {
    private const val TAG = "RealSmtpEmailSender"
  }

  fun isReady(): Boolean = config.isConfigured

  fun getConfigSummary(): String {
    return if (config.isConfigured) {
      "SMTP Ready: ${config.senderEmail} -> ${config.host}:${config.port}"
    } else {
      "SMTP Unconfigured (SMTP_SENDER_EMAIL hoặc SMTP_SENDER_PASSWORD chưa có trong .env)"
    }
  }

  /**
   * Gửi email thật chứa mã OTP xác thực đến hòm thư người nhận.
   */
  suspend fun sendOtpEmail(
    recipientEmail: String,
    otpCode: String,
    purpose: OtpPurpose
  ): Result<Unit> = withContext(Dispatchers.IO) {
    if (!config.isConfigured) {
      val errorMsg = "SMTP chưa được cấu hình. Vui lòng thêm SMTP_SENDER_EMAIL và SMTP_SENDER_PASSWORD vào file .env"
      Log.w(TAG, errorMsg)
      return@withContext Result.failure(IllegalStateException(errorMsg))
    }

    try {
      Log.d(TAG, "Đang kết nối SMTP ${config.host}:${config.port} để gửi mail tới $recipientEmail...")

      val props = Properties().apply {
        put("mail.smtp.host", config.host)
        put("mail.smtp.port", config.port)
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.starttls.required", "true")
        put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
        put("mail.smtp.connectiontimeout", "12000")
        put("mail.smtp.timeout", "12000")
      }

      val session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication {
          return PasswordAuthentication(config.senderEmail.trim(), config.senderPassword.trim())
        }
      })

      val purposeText = when (purpose) {
        OtpPurpose.REGISTRATION -> "đăng ký tài khoản InLove"
        OtpPurpose.PASSWORD_RESET -> "đặt lại mật khẩu tài khoản"
        OtpPurpose.LINK_PARTNER -> "ghép đôi tình yêu 1-1"
      }

      val subject = "[InLove] Mã xác thực $otpCode cho $purposeText"
      val htmlContent = buildHtmlOtpEmail(otpCode, purposeText)

      val message = MimeMessage(session).apply {
        setFrom(InternetAddress(config.senderEmail.trim(), config.senderName, "UTF-8"))
        setRecipient(Message.RecipientType.TO, InternetAddress(recipientEmail.trim()))
        setSubject(subject, "UTF-8")
        setContent(htmlContent, "text/html; charset=UTF-8")
      }

      Transport.send(message)
      Log.i(TAG, "✅ Gửi email THẬT thành công tới $recipientEmail! OTP: $otpCode")
      Result.success(Unit)
    } catch (me: MessagingException) {
      val errorMsg = "Lỗi gửi mail SMTP tới $recipientEmail: ${me.message}"
      Log.e(TAG, errorMsg, me)
      Result.failure(me)
    } catch (e: Exception) {
      val errorMsg = "Lỗi không xác định khi gửi mail tới $recipientEmail: ${e.message}"
      Log.e(TAG, errorMsg, e)
      Result.failure(e)
    }
  }

  private fun buildHtmlOtpEmail(otpCode: String, purposeDescription: String): String {
    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #FFF5F7; margin: 0; padding: 24px 12px; color: #2D2527; }
          .card { max-width: 500px; margin: 0 auto; background: #FFFFFF; border-radius: 24px; padding: 32px 24px; box-shadow: 0 10px 30px rgba(255, 64, 129, 0.08); border: 1px solid #FFE4E9; }
          .header { text-align: center; margin-bottom: 24px; }
          .heart-icon { font-size: 36px; line-height: 1; }
          .logo-text { font-size: 26px; font-weight: 800; color: #FF4081; margin-top: 6px; letter-spacing: -0.5px; }
          .tagline { font-size: 13px; color: #9E8E95; margin-top: 2px; }
          .greeting { font-size: 15px; color: #2D2527; margin-bottom: 12px; font-weight: 600; }
          .desc { font-size: 14px; color: #5E5257; line-height: 1.6; margin-bottom: 24px; }
          .otp-container { background: #FFF0F4; border: 2px dashed #FF80AB; border-radius: 18px; padding: 20px; text-align: center; margin: 20px 0 28px 0; }
          .otp-label { font-size: 12px; font-weight: 700; color: #FF4081; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 6px; }
          .otp-value { font-size: 38px; font-weight: 800; letter-spacing: 8px; color: #D81B60; font-family: 'Courier New', Courier, monospace; }
          .warning-box { background: #FFF9FA; border-left: 4px solid #FF80AB; padding: 12px 16px; border-radius: 0 12px 12px 0; font-size: 13px; color: #6D6065; line-height: 1.5; }
          .footer { text-align: center; font-size: 12px; color: #B3A1A8; margin-top: 32px; border-top: 1px solid #FFEBF0; padding-top: 18px; }
        </style>
      </head>
      <body>
        <div class="card">
          <div class="header">
            <div class="heart-icon">💖</div>
            <div class="logo-text">InLove</div>
            <div class="tagline">Đếm ngày yêu thương & Gắn kết trái tim</div>
          </div>
          <div class="greeting">Xin chào bạn,</div>
          <div class="desc">
            Bạn đang thực hiện <strong>$purposeDescription</strong> trên ứng dụng InLove. Dưới đây là mã xác thực bảo mật 6 số của bạn:
          </div>
          <div class="otp-container">
            <div class="otp-label">MÃ XÁC THỰC (OTP)</div>
            <div class="otp-value">$otpCode</div>
          </div>
          <div class="warning-box">
            ⏰ <strong>Hiệu lực:</strong> Mã có hiệu lực trong <strong>5 phút</strong>.<br>
            🔒 <strong>Bảo mật:</strong> Vui lòng tuyệt đối không chia sẻ mã này cho bất kỳ ai.
          </div>
          <div class="footer">
            © 2026 InLove Application • Lưu giữ từng khoảnh khắc ngọt ngào
          </div>
        </div>
      </body>
      </html>
    """.trimIndent()
  }
}
