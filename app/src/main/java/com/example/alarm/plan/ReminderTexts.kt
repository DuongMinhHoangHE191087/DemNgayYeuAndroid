package com.example.alarm.plan

/** Tiêu đề và nội dung của một thông báo. */
data class ReminderText(val title: String, val body: String)

/** Phần thay đổi của câu chữ; trường mà loại nhắc không dùng thì để mặc định. */
data class ReminderTextArgs(
  val title: String = "",
  val dateText: String = "",
  val daysBefore: Int = 0,
  val partnerName: String = "",
  val loveDays: Int = 0,
  val details: String = "",
)

/** Câu chữ thông báo nhắc hẹn. Thuần Kotlin, không dùng API Android. */
object ReminderTexts {

  /** Câu chữ của một loại nhắc, theo ngôn ngữ. */
  fun of(kind: ReminderKind, isEnglish: Boolean, args: ReminderTextArgs): ReminderText =
    if (isEnglish) english(kind, args) else vietnamese(kind, args)

  /** Câu chữ khi người dùng ẩn nội dung thông báo: không chứa tên, tiêu đề hay chữ của người dùng. */
  fun redacted(isEnglish: Boolean): ReminderText =
    if (isEnglish) {
      ReminderText("Reminder from InLove", "Open InLove for details")
    } else {
      ReminderText("Nhắc hẹn từ InLove", "Mở InLove để xem chi tiết")
    }

  private fun vietnamese(kind: ReminderKind, args: ReminderTextArgs): ReminderText {
    val partner = args.partnerName.ifBlank { "người ấy" }
    return when (kind) {
      // Khớp AlarmNotificationScheduler.scheduleAnniversaryNotification.
      ReminderKind.ADVANCE -> ReminderText(
        "🔔 Sắp Đến: ${args.title}",
        "Còn ${args.daysBefore} ngày nữa là đến ngày kỷ niệm '${args.title}' (${args.dateText}). Đừng quên chuẩn bị điều bất ngờ cho người ấy nhé! 💕"
      )
      // Khớp AlarmNotificationScheduler.dayOfTexts.
      ReminderKind.DAY_OF -> ReminderText(
        "🎉 Hôm Nay: ${args.title}!",
        "Hôm nay là ngày kỷ niệm đặc biệt '${args.title}' (${args.dateText})! Chúc hai bạn một ngày ngập tràn ngọt ngào và yêu thương! ❤️✨"
      )
      // Khớp AlarmNotificationScheduler.scheduleMilestoneNotification.
      ReminderKind.USER_MILESTONE -> ReminderText(
        "⭐ Cột Mốc Tình Yêu: ${args.title}",
        "Cột mốc ý nghĩa '${args.title}' (${args.dateText}) đang đến rất gần! Cùng người ấy lưu lại khoảnh khắc này nhé! 💖"
      )
      // Khớp AlarmNotificationScheduler.scheduleAllAnniversariesFromDb.
      ReminderKind.COUPLE_ANNIVERSARY -> ReminderText(
        "💑 Kỷ Niệm Ngày Yêu Nhau: ${args.title} ❤️",
        "Chúc mừng ngày kỷ niệm chính thức yêu nhau của hai bạn! Chặng đường ${args.loveDays} ngày yêu thương ngọt ngào! 🎉🌹"
      )
      // Khớp LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore.
      ReminderKind.LOVE_MILESTONE_DAY_OF -> ReminderText(
        "🎉 Hôm Nay: ${args.title}! ❤️",
        "Hôm nay là ${args.title} (${args.dateText}) của bạn và $partner! Chúc hai bạn có một ngày kỷ niệm thật hạnh phúc và ngập tràn yêu thương! 🥂✨"
      )
      // Khớp LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore.
      ReminderKind.LOVE_MILESTONE_ONE_DAY -> ReminderText(
        "🌹 Ngày Mai: ${args.title}!",
        "Ngày mai là ${args.title}! Hãy sẵn sàng đón một ngày thật lãng mạn bên $partner nhé! 💕"
      )
      // Khớp LoveAnniversaryMilestoneScheduler.scheduleMilestonesFromFirestore.
      ReminderKind.LOVE_MILESTONE_THREE_DAYS -> ReminderText(
        "🔔 Sắp Đến: ${args.title}!",
        "Còn 3 ngày nữa là đến cột mốc ${args.title} (${args.dateText})! Đừng quên chuẩn bị một món quà hoặc kế hoạch hẹn hò bất ngờ cho $partner nhé! 🎁💖"
      )
      // Không dịch: giữ nguyên chữ người dùng, như vòng lặp nhắc tùy chỉnh trong AlarmNotificationScheduler.scheduleAllAnniversariesFromDb.
      ReminderKind.CUSTOM -> ReminderText(args.title, args.details)
    }
  }

  private fun english(kind: ReminderKind, args: ReminderTextArgs): ReminderText {
    val partner = args.partnerName.ifBlank { "your partner" }
    return when (kind) {
      ReminderKind.ADVANCE -> ReminderText(
        "🔔 Coming Up: ${args.title}",
        "Only ${days(args.daysBefore)} left until your anniversary '${args.title}' (${args.dateText}). Don't forget to plan a surprise for your special someone! 💕"
      )
      ReminderKind.DAY_OF -> ReminderText(
        "🎉 Today: ${args.title}!",
        "Today is your special anniversary '${args.title}' (${args.dateText})! Wishing you both a day full of sweetness and love! ❤️✨"
      )
      ReminderKind.USER_MILESTONE -> ReminderText(
        "⭐ Love Milestone: ${args.title}",
        "The meaningful milestone '${args.title}' (${args.dateText}) is almost here! Capture this moment together with your special someone! 💖"
      )
      ReminderKind.COUPLE_ANNIVERSARY -> ReminderText(
        "💑 Our Love Anniversary: ${args.title} ❤️",
        "Congratulations on your official love anniversary! A journey of ${sweetDays(args.loveDays)} of love! 🎉🌹"
      )
      ReminderKind.LOVE_MILESTONE_DAY_OF -> ReminderText(
        "🎉 Today: ${args.title}! ❤️",
        "Today is ${args.title} (${args.dateText}) for you and $partner! Wishing you both a truly happy anniversary full of love! 🥂✨"
      )
      ReminderKind.LOVE_MILESTONE_ONE_DAY -> ReminderText(
        "🌹 Tomorrow: ${args.title}!",
        "Tomorrow is ${args.title}! Get ready for a truly romantic day with $partner! 💕"
      )
      ReminderKind.LOVE_MILESTONE_THREE_DAYS -> ReminderText(
        "🔔 Coming Up: ${args.title}!",
        "Only 3 days left until the milestone ${args.title} (${args.dateText})! Don't forget to prepare a gift or a surprise date plan for $partner! 🎁💖"
      )
      ReminderKind.CUSTOM -> ReminderText(args.title, args.details)
    }
  }

  private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

  private fun sweetDays(n: Int) = if (n == 1) "1 sweet day" else "$n sweet days"
}
