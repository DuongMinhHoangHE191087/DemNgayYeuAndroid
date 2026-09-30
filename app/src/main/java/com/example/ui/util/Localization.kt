package com.example.ui.util

import android.content.Context
import android.content.res.Configuration
import com.example.R
import java.util.Locale

enum class AppLanguage {
  VI, EN
}

object LocaleManager {
  private const val PREFS_NAME = "inlove_preferences"
  private const val KEY_APP_LANGUAGE = "key_app_language"
  private const val KEY_FIRST_LAUNCH = "key_first_launch"
  private const val KEY_WALLPAPER_URL = "key_wallpaper_url"
  private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
  private const val KEY_SOUND_ENABLED = "key_sound_enabled"

  fun getInitialLanguage(context: Context): AppLanguage {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedLang = prefs.getString(KEY_APP_LANGUAGE, null)
    if (savedLang != null) {
      return try {
        AppLanguage.valueOf(savedLang)
      } catch (e: Exception) {
        AppLanguage.EN
      }
    }
    // Foreign user (system language is not Vietnamese) -> Default to English (EN)
    // Vietnam user (system language is "vi") -> Default to Vietnamese (VI)
    val systemLang = Locale.getDefault().language
    return if (systemLang.equals("vi", ignoreCase = true)) AppLanguage.VI else AppLanguage.EN
  }

  fun saveLanguage(context: Context, language: AppLanguage) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
  }

  fun isFirstLaunch(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
  }

  fun setFirstLaunchCompleted(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
  }

  /** Null means "no wallpaper saved yet" — caller keeps its own hardcoded default in that case. */
  fun getSavedWallpaperUrl(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_WALLPAPER_URL, null)
  }

  fun saveWallpaperUrl(context: Context, url: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_WALLPAPER_URL, url).apply()
  }

  fun isNotificationsEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
  }

  fun saveNotificationsEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
  }

  fun isSoundEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_SOUND_ENABLED, true)
  }

  fun saveSoundEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
  }
}

data class AppStrings(
  // Nav items
  val navHome: String,
  val navMemories: String,
  val navCalendar: String,
  val navGifts: String,
  val navReminders: String,
  val navSettings: String,

  // Quick Action Bar
  val actionWallpaper: String,
  val actionMemory: String,
  val actionCouple: String,
  val actionGuide: String,
  val actionLanguage: String,

  // Home Screen
  val daysInLove: String,
  val nextMilestoneTitle: String,
  val nextMilestoneHeader: String,
  val daysRemainingFormat: String,
  val progressTowards: String,
  val romanticAdviceFormat: String,
  val btnGiftIdeas: String,
  val btnAnniversaryCalendar: String,
  val quoteSectionTag: String,
  val quoteSectionTitle: String,
  val btnShuffleQuote: String,
  val btnSendToPartner: String,
  val milestoneCelebrationTitle: String,
  val milestoneCelebrationBtn: String,

  // Top Bar & Ads
  val adPlaceholderNotice: String,
  val countingDaysNotice: String,

  // Settings Screen
  val settingsTitle: String,
  val settingsProfileCardEdit: String,
  val settingsSweetDaysFormat: String,
  val settingsDbSecured: String,
  val settingsSectionProfile: String,
  val settingsEditProfileTitle: String,
  val settingsEditProfileSub: String,
  val settingsCaptureMemoryTitle: String,
  val settingsCaptureMemorySub: String,
  val settingsDbSyncTitle: String,
  val settingsDbSyncSub: String,
  val settingsDbSyncToast: String,
  val settingsSectionAppearance: String,
  val settingsLanguageTitle: String,
  val settingsLanguageSub: String,
  val settingsLanguageToggleLabel: String,
  val settingsLanguageCurrentEn: String,
  val settingsLanguageCurrentVi: String,
  val settingsWallpaperTitle: String,
  val settingsWallpaperSub: String,
  val settingsGuideTitle: String,
  val settingsGuideSub: String,
  val settingsSectionNotifications: String,
  val settingsNotifAnniversaryTitle: String,
  val settingsNotifAnniversarySub: String,
  val settingsNotifEnabledToast: String,
  val settingsNotifDisabledToast: String,
  val settingsNotifSoundTitle: String,
  val settingsNotifSoundSub: String,
  val settingsSectionSecurity: String,
  val settingsLockTitle: String,
  val settingsLockSub: String,
  val settingsAboutTitle: String,
  val settingsAboutSub: String,
  val settingsAboutToast: String,

  // Guide Dialog
  val guideTitle: String,
  val guideSubtitle: String,
  val guideTabCounter: String,
  val guideTabPairing: String,
  val guideTabMemories: String,
  val guideTabCalendar: String,
  val guideTabGifts: String,
  val guideTabReminders: String,
  val guideTabSettings: String,
  val btnGotIt: String,

  // Wallpaper Dialog
  val wallpaperTitle: String,
  val wallpaperSubtitle: String,
  val wallpaperPresetCherry: String,
  val wallpaperPresetSunset: String,
  val wallpaperPresetStarry: String,
  val wallpaperPresetPastel: String,
  val wallpaperCustomUrl: String,
  val btnApplyWallpaper: String,

  // Memory Dialog & Screen
  val memoryAlbumTitle: String,
  val memoryAlbumSubtitle: String,
  val memoryTitle: String,
  val memorySubtitle: String,
  val memoryTitleLabel: String,
  val memoryDateLabel: String,
  val memoryLocationLabel: String,
  val memoryNoteLabel: String,
  val memoryPickPhoto: String,
  val memoryCapturePhoto: String,
  val memoryPresetNotice: String,
  val memoryNotePlaceholder: String,
  val memoryUrlPlaceholder: String,
  val btnSaveMemory: String,
  val btnAddMemory: String,
  val memoryEmptyTitle: String,
  val memoryEmptySubtitle: String,

  // Language Dialog
  val langDialogTitle: String,
  val langDialogSubtitle: String,
  val langVietnamese: String,
  val langEnglish: String,
  val btnConfirm: String,
  val btnCancel: String,

  // Onboarding Strings
  val onboardingWelcomeTitle: String,
  val onboardingWelcomeSub: String,
  val onboardingLangSelectTitle: String,
  val onboardingCounterTitle: String,
  val onboardingCounterSub: String,
  val onboardingMemoriesTitle: String,
  val onboardingMemoriesSub: String,
  val onboardingReminderTitle: String,
  val onboardingReminderSub: String,
  val onboardingStartTitle: String,
  val onboardingStartSub: String,
  val onboardingBtnNext: String,
  val onboardingBtnBack: String,
  val onboardingBtnSkip: String,
  val onboardingBtnStart: String,

  // Calendar Strings
  val calendarTitle: String,
  val calendarFilterAll: String,
  val calendarFilterUpcoming: String,
  val calendarFilterPast: String,
  val calendarPrevMonth: String,
  val calendarNextMonth: String,
  val calendarAddAnniversary: String,

  // Gift Strings
  val giftScreenTitle: String,
  val giftCatAll: String,
  val giftCatAi: String,
  val giftCatJewelry: String,
  val giftCatTech: String,
  val giftCatFlowers: String,
  val giftCatTravel: String,
  val giftCatFashion: String,
  val giftCatDiy: String,
  val giftChecklistTitle: String,
  val giftChecklistAdd: String,

  // Reminder Strings
  val reminderScreenTitle: String,
  val reminderRealtimeHeader: String,
  val reminderCadenceCustom: String,
  val reminderCadenceWeekly: String,
  val reminderCadenceMonthly: String,
  val reminderCadenceAnnually: String,
  val reminderMarkAllRead: String,
  val reminderAddNew: String,

  // Pairing Strings
  val pairingTitle: String,
  val pairingSub: String,
  val pairingMyCode: String,
  val pairingPartnerCode: String,
  val pairingBtnSend: String,
  val pairingStatusConnected: String,
  val pairingStatusPending: String,
  val pairingBtnDisconnect: String,

  // Paywall Strings
  val paywallTitle: String,
  val paywallSub: String,
  val paywallBenefitsHeader: String,
  val paywallBenefitAds: String,
  val paywallBenefitAi: String,
  val paywallBenefitCloud: String,
  val paywallBenefitLock: String,
  val paywallBenefitTheme: String,
  val paywallPlanYearlyTitle: String,
  val paywallPlanYearlyPrice: String,
  val paywallPlanYearlySub: String,
  val paywallPlanYearlyBadge: String,
  val paywallPlanMonthlyTitle: String,
  val paywallPlanMonthlyPrice: String,
  val paywallPlanMonthlySub: String,
  val paywallPlanLifetimeTitle: String,
  val paywallPlanLifetimePrice: String,
  val paywallPlanLifetimeSub: String,
  val paywallPlanLifetimeBadge: String,
  val paywallBtnTrial: String,
  val paywallBtnMonthly: String,
  val paywallBtnLifetime: String,
  val paywallBtnRestore: String,
  val paywallBtnManage: String,
  val paywallTerms: String,
  val paywallPrivacy: String,

  // Auth Strings
  val authLoginTitle: String,
  val authRegisterTitle: String,
  val authEmailLabel: String,
  val authPasswordLabel: String,
  val authConfirmPasswordLabel: String,
  val authForgotPassword: String,
  val authBtnLogin: String,
  val authBtnRegister: String,
  val authBtnGuest: String,
  val authSwitchToRegister: String,
  val authSwitchToLogin: String
)

object LocalizedStrings {
  fun fromContext(context: Context, lang: AppLanguage): AppStrings {
    val locale = when (lang) {
      AppLanguage.VI -> Locale.forLanguageTag("vi")
      AppLanguage.EN -> Locale.forLanguageTag("en")
    }
    val config = Configuration(context.resources.configuration).apply {
      setLocale(locale)
    }
    val localizedContext = context.createConfigurationContext(config)
    val res = localizedContext.resources

    return AppStrings(
      navHome = res.getString(R.string.nav_home),
      navMemories = res.getString(R.string.nav_memories),
      navCalendar = res.getString(R.string.nav_calendar),
      navGifts = res.getString(R.string.nav_gifts),
      navReminders = res.getString(R.string.nav_reminders),
      navSettings = res.getString(R.string.nav_settings),

      actionWallpaper = res.getString(R.string.action_wallpaper),
      actionMemory = res.getString(R.string.action_memory),
      actionCouple = res.getString(R.string.action_couple),
      actionGuide = res.getString(R.string.action_guide),
      actionLanguage = res.getString(R.string.action_language),

      daysInLove = res.getString(R.string.days_in_love),
      nextMilestoneTitle = res.getString(R.string.next_milestone_title),
      nextMilestoneHeader = res.getString(R.string.next_milestone_header),
      daysRemainingFormat = res.getString(R.string.days_remaining_format),
      progressTowards = res.getString(R.string.progress_towards),
      romanticAdviceFormat = res.getString(R.string.romantic_advice_format),
      btnGiftIdeas = res.getString(R.string.btn_gift_ideas),
      btnAnniversaryCalendar = res.getString(R.string.btn_anniversary_calendar),
      quoteSectionTag = res.getString(R.string.quote_section_tag),
      quoteSectionTitle = res.getString(R.string.quote_section_title),
      btnShuffleQuote = res.getString(R.string.btn_shuffle_quote),
      btnSendToPartner = res.getString(R.string.btn_send_to_partner),
      milestoneCelebrationTitle = res.getString(R.string.milestone_celebration_title),
      milestoneCelebrationBtn = res.getString(R.string.milestone_celebration_btn),

      adPlaceholderNotice = res.getString(R.string.ad_placeholder_notice),
      countingDaysNotice = res.getString(R.string.counting_days_notice),

      settingsTitle = res.getString(R.string.settings_title),
      settingsProfileCardEdit = res.getString(R.string.settings_profile_card_edit),
      settingsSweetDaysFormat = res.getString(R.string.settings_sweet_days_format),
      settingsDbSecured = res.getString(R.string.settings_db_secured),
      settingsSectionProfile = res.getString(R.string.settings_section_profile),
      settingsEditProfileTitle = res.getString(R.string.settings_edit_profile_title),
      settingsEditProfileSub = res.getString(R.string.settings_edit_profile_sub),
      settingsCaptureMemoryTitle = res.getString(R.string.settings_capture_memory_title),
      settingsCaptureMemorySub = res.getString(R.string.settings_capture_memory_sub),
      settingsDbSyncTitle = res.getString(R.string.settings_db_sync_title),
      settingsDbSyncSub = res.getString(R.string.settings_db_sync_sub),
      settingsDbSyncToast = res.getString(R.string.settings_db_sync_toast),
      settingsSectionAppearance = res.getString(R.string.settings_section_appearance),
      settingsLanguageTitle = res.getString(R.string.settings_language_title),
      settingsLanguageSub = res.getString(R.string.settings_language_sub),
      settingsLanguageToggleLabel = res.getString(R.string.settings_language_toggle_label),
      settingsLanguageCurrentEn = res.getString(R.string.settings_language_current_en),
      settingsLanguageCurrentVi = res.getString(R.string.settings_language_current_vi),
      settingsWallpaperTitle = res.getString(R.string.settings_wallpaper_title),
      settingsWallpaperSub = res.getString(R.string.settings_wallpaper_sub),
      settingsGuideTitle = res.getString(R.string.settings_guide_title),
      settingsGuideSub = res.getString(R.string.settings_guide_sub),
      settingsSectionNotifications = res.getString(R.string.settings_section_notifications),
      settingsNotifAnniversaryTitle = res.getString(R.string.settings_notif_anniversary_title),
      settingsNotifAnniversarySub = res.getString(R.string.settings_notif_anniversary_sub),
      settingsNotifEnabledToast = res.getString(R.string.settings_notif_enabled_toast),
      settingsNotifDisabledToast = res.getString(R.string.settings_notif_disabled_toast),
      settingsNotifSoundTitle = res.getString(R.string.settings_notif_sound_title),
      settingsNotifSoundSub = res.getString(R.string.settings_notif_sound_sub),
      settingsSectionSecurity = res.getString(R.string.settings_section_security),
      settingsLockTitle = res.getString(R.string.settings_lock_title),
      settingsLockSub = res.getString(R.string.settings_lock_sub),
      settingsAboutTitle = res.getString(R.string.settings_about_title),
      settingsAboutSub = res.getString(R.string.settings_about_sub),
      settingsAboutToast = res.getString(R.string.settings_about_toast),

      guideTitle = res.getString(R.string.guide_title),
      guideSubtitle = res.getString(R.string.guide_subtitle),
      guideTabCounter = res.getString(R.string.guide_tab_counter),
      guideTabPairing = res.getString(R.string.guide_tab_pairing),
      guideTabMemories = res.getString(R.string.guide_tab_memories),
      guideTabCalendar = res.getString(R.string.guide_tab_calendar),
      guideTabGifts = res.getString(R.string.guide_tab_gifts),
      guideTabReminders = res.getString(R.string.guide_tab_reminders),
      guideTabSettings = res.getString(R.string.guide_tab_settings),
      btnGotIt = res.getString(R.string.btn_got_it),

      wallpaperTitle = res.getString(R.string.wallpaper_title),
      wallpaperSubtitle = res.getString(R.string.wallpaper_subtitle),
      wallpaperPresetCherry = res.getString(R.string.wallpaper_preset_cherry),
      wallpaperPresetSunset = res.getString(R.string.wallpaper_preset_sunset),
      wallpaperPresetStarry = res.getString(R.string.wallpaper_preset_starry),
      wallpaperPresetPastel = res.getString(R.string.wallpaper_preset_pastel),
      wallpaperCustomUrl = res.getString(R.string.wallpaper_custom_url),
      btnApplyWallpaper = res.getString(R.string.btn_apply_wallpaper),

      memoryAlbumTitle = res.getString(R.string.memory_album_title),
      memoryAlbumSubtitle = res.getString(R.string.memory_album_subtitle),
      memoryTitle = res.getString(R.string.memory_title),
      memorySubtitle = res.getString(R.string.memory_subtitle),
      memoryTitleLabel = res.getString(R.string.memory_title_label),
      memoryDateLabel = res.getString(R.string.memory_date_label),
      memoryLocationLabel = res.getString(R.string.memory_location_label),
      memoryNoteLabel = res.getString(R.string.memory_note_label),
      memoryPickPhoto = res.getString(R.string.memory_pick_photo),
      memoryCapturePhoto = res.getString(R.string.memory_capture_photo),
      memoryPresetNotice = res.getString(R.string.memory_preset_notice),
      memoryNotePlaceholder = res.getString(R.string.memory_note_placeholder),
      memoryUrlPlaceholder = res.getString(R.string.memory_url_placeholder),
      btnSaveMemory = res.getString(R.string.btn_save_memory),
      btnAddMemory = res.getString(R.string.btn_add_memory),
      memoryEmptyTitle = res.getString(R.string.memory_empty_title),
      memoryEmptySubtitle = res.getString(R.string.memory_empty_subtitle),

      langDialogTitle = res.getString(R.string.lang_dialog_title),
      langDialogSubtitle = res.getString(R.string.lang_dialog_subtitle),
      langVietnamese = res.getString(R.string.lang_vietnamese),
      langEnglish = res.getString(R.string.lang_english),
      btnConfirm = res.getString(R.string.btn_confirm),
      btnCancel = res.getString(R.string.btn_cancel),

      // Onboarding
      onboardingWelcomeTitle = res.getString(R.string.onboarding_welcome_title),
      onboardingWelcomeSub = res.getString(R.string.onboarding_welcome_sub),
      onboardingLangSelectTitle = res.getString(R.string.onboarding_lang_select_title),
      onboardingCounterTitle = res.getString(R.string.onboarding_counter_title),
      onboardingCounterSub = res.getString(R.string.onboarding_counter_sub),
      onboardingMemoriesTitle = res.getString(R.string.onboarding_memories_title),
      onboardingMemoriesSub = res.getString(R.string.onboarding_memories_sub),
      onboardingReminderTitle = res.getString(R.string.onboarding_reminder_title),
      onboardingReminderSub = res.getString(R.string.onboarding_reminder_sub),
      onboardingStartTitle = res.getString(R.string.onboarding_start_title),
      onboardingStartSub = res.getString(R.string.onboarding_start_sub),
      onboardingBtnNext = res.getString(R.string.onboarding_btn_next),
      onboardingBtnBack = res.getString(R.string.onboarding_btn_back),
      onboardingBtnSkip = res.getString(R.string.onboarding_btn_skip),
      onboardingBtnStart = res.getString(R.string.onboarding_btn_start),

      // Calendar
      calendarTitle = res.getString(R.string.calendar_title),
      calendarFilterAll = res.getString(R.string.calendar_filter_all),
      calendarFilterUpcoming = res.getString(R.string.calendar_filter_upcoming),
      calendarFilterPast = res.getString(R.string.calendar_filter_past),
      calendarPrevMonth = res.getString(R.string.calendar_prev_month),
      calendarNextMonth = res.getString(R.string.calendar_next_month),
      calendarAddAnniversary = res.getString(R.string.calendar_add_anniversary),

      // Gifts
      giftScreenTitle = res.getString(R.string.gift_screen_title),
      giftCatAll = res.getString(R.string.gift_cat_all),
      giftCatAi = res.getString(R.string.gift_cat_ai),
      giftCatJewelry = res.getString(R.string.gift_cat_jewelry),
      giftCatTech = res.getString(R.string.gift_cat_tech),
      giftCatFlowers = res.getString(R.string.gift_cat_flowers),
      giftCatTravel = res.getString(R.string.gift_cat_travel),
      giftCatFashion = res.getString(R.string.gift_cat_fashion),
      giftCatDiy = res.getString(R.string.gift_cat_diy),
      giftChecklistTitle = res.getString(R.string.gift_checklist_title),
      giftChecklistAdd = res.getString(R.string.gift_checklist_add),

      // Reminders
      reminderScreenTitle = res.getString(R.string.reminder_screen_title),
      reminderRealtimeHeader = res.getString(R.string.reminder_realtime_header),
      reminderCadenceCustom = res.getString(R.string.reminder_cadence_custom),
      reminderCadenceWeekly = res.getString(R.string.reminder_cadence_weekly),
      reminderCadenceMonthly = res.getString(R.string.reminder_cadence_monthly),
      reminderCadenceAnnually = res.getString(R.string.reminder_cadence_annually),
      reminderMarkAllRead = res.getString(R.string.reminder_mark_all_read),
      reminderAddNew = res.getString(R.string.reminder_add_new),

      // Pairing
      pairingTitle = res.getString(R.string.pairing_title),
      pairingSub = res.getString(R.string.pairing_sub),
      pairingMyCode = res.getString(R.string.pairing_my_code),
      pairingPartnerCode = res.getString(R.string.pairing_partner_code),
      pairingBtnSend = res.getString(R.string.pairing_btn_send),
      pairingStatusConnected = res.getString(R.string.pairing_status_connected),
      pairingStatusPending = res.getString(R.string.pairing_status_pending),
      pairingBtnDisconnect = res.getString(R.string.pairing_btn_disconnect),

      // Paywall
      paywallTitle = res.getString(R.string.paywall_title),
      paywallSub = res.getString(R.string.paywall_sub),
      paywallBenefitsHeader = res.getString(R.string.paywall_benefits_header),
      paywallBenefitAds = res.getString(R.string.paywall_benefit_ads),
      paywallBenefitAi = res.getString(R.string.paywall_benefit_ai),
      paywallBenefitCloud = res.getString(R.string.paywall_benefit_cloud),
      paywallBenefitLock = res.getString(R.string.paywall_benefit_lock),
      paywallBenefitTheme = res.getString(R.string.paywall_benefit_theme),
      paywallPlanYearlyTitle = res.getString(R.string.paywall_plan_yearly_title),
      paywallPlanYearlyPrice = res.getString(R.string.paywall_plan_yearly_price),
      paywallPlanYearlySub = res.getString(R.string.paywall_plan_yearly_sub),
      paywallPlanYearlyBadge = res.getString(R.string.paywall_plan_yearly_badge),
      paywallPlanMonthlyTitle = res.getString(R.string.paywall_plan_monthly_title),
      paywallPlanMonthlyPrice = res.getString(R.string.paywall_plan_monthly_price),
      paywallPlanMonthlySub = res.getString(R.string.paywall_plan_monthly_sub),
      paywallPlanLifetimeTitle = res.getString(R.string.paywall_plan_lifetime_title),
      paywallPlanLifetimePrice = res.getString(R.string.paywall_plan_lifetime_price),
      paywallPlanLifetimeSub = res.getString(R.string.paywall_plan_lifetime_sub),
      paywallPlanLifetimeBadge = res.getString(R.string.paywall_plan_lifetime_badge),
      paywallBtnTrial = res.getString(R.string.paywall_btn_trial),
      paywallBtnMonthly = res.getString(R.string.paywall_btn_monthly),
      paywallBtnLifetime = res.getString(R.string.paywall_btn_lifetime),
      paywallBtnRestore = res.getString(R.string.paywall_btn_restore),
      paywallBtnManage = res.getString(R.string.paywall_btn_manage),
      paywallTerms = res.getString(R.string.paywall_terms),
      paywallPrivacy = res.getString(R.string.paywall_privacy),

      // Auth
      authLoginTitle = res.getString(R.string.auth_login_title),
      authRegisterTitle = res.getString(R.string.auth_register_title),
      authEmailLabel = res.getString(R.string.auth_email_label),
      authPasswordLabel = res.getString(R.string.auth_password_label),
      authConfirmPasswordLabel = res.getString(R.string.auth_confirm_password_label),
      authForgotPassword = res.getString(R.string.auth_forgot_password),
      authBtnLogin = res.getString(R.string.auth_btn_login),
      authBtnRegister = res.getString(R.string.auth_btn_register),
      authBtnGuest = res.getString(R.string.auth_btn_guest),
      authSwitchToRegister = res.getString(R.string.auth_switch_to_register),
      authSwitchToLogin = res.getString(R.string.auth_switch_to_login)
    )
  }

  // Backwards compatible get() function for non-composable calls
  fun get(lang: AppLanguage): AppStrings {
    return when (lang) {
      AppLanguage.VI -> vietnameseStatic
      AppLanguage.EN -> englishStatic
    }
  }

  val vietnameseStatic = AppStrings(
    navHome = "Trang Chủ",
    navMemories = "Kỷ Niệm",
    navCalendar = "Lịch & Hẹn",
    navGifts = "Gợi Ý Quà",
    navReminders = "Nhắc Hẹn",
    navSettings = "Cài Đặt",

    actionWallpaper = "Đổi Nền",
    actionMemory = "Thêm Ảnh",
    actionCouple = "Đồng Hành",
    actionGuide = "Hướng Dẫn",
    actionLanguage = "Ngôn Ngữ",

    daysInLove = "ngày yêu nhau",
    nextMilestoneTitle = "Cột Mốc %d Ngày Yêu",
    nextMilestoneHeader = "GỢI Ý KỶ NIỆM TIẾP THEO",
    daysRemainingFormat = "Còn %d ngày",
    progressTowards = "Tiến độ đạt mốc",
    romanticAdviceFormat = "💡 Gợi ý lãng mạn: Chỉ còn %d ngày nữa là chạm mốc %d ngày yêu! Hãy lên kế hoạch đặt bàn hẹn hò đặc biệt hoặc chuẩn bị món quà bất ngờ dành tặng cho %s nhé!",
    btnGiftIdeas = "Gợi Ý Quà Tặng",
    btnAnniversaryCalendar = "Lịch Kỷ Niệm",
    quoteSectionTag = "DÒNG CẢM XÚC ĐÔI LỨA",
    quoteSectionTitle = "Phản Hồi & Lời Yêu Thương 💕",
    btnShuffleQuote = "Đổi Lời Chúc 💫",
    btnSendToPartner = "Gửi Bạn Ấy 💌",
    milestoneCelebrationTitle = "🎉 Chúc Mừng Cột Mốc Kỷ Niệm!",
    milestoneCelebrationBtn = "Phóng Mưa Tim Kỷ Niệm 💕",

    adPlaceholderNotice = "Khu vực chuẩn bị sẵn để tích hợp Plugin Quảng Cáo (AdMob)",
    countingDaysNotice = "InLove: Đang đếm %d ngày yêu nhau ❤️",

    settingsTitle = "Cài Đặt",
    settingsProfileCardEdit = "Chỉnh sửa",
    settingsSweetDaysFormat = "Đã đồng hành %d ngày ngọt ngào ❤️",
    settingsDbSecured = "💾 Dữ liệu lưu an toàn (Chạm để chỉnh sửa)",
    settingsSectionProfile = "Hồ Sơ & Kỷ Niệm Yêu Thương",
    settingsEditProfileTitle = "Chỉnh sửa thông tin 2 bạn",
    settingsEditProfileSub = "Tên, ngày sinh, cung hoàng đạo & ngày bắt đầu yêu",
    settingsCaptureMemoryTitle = "Chụp ảnh & Lưu giữ kỷ niệm",
    settingsCaptureMemorySub = "Thêm khoảnh khắc đáng nhớ vào dòng thời gian",
    settingsDbSyncTitle = "Đồng bộ bộ nhớ Room Database",
    settingsDbSyncSub = "Dữ liệu được lưu trữ tự động và bền vững",
    settingsDbSyncToast = "Dữ liệu Room Database đang đồng bộ hoàn hảo!",
    settingsSectionAppearance = "Giao Diện & Cá Nhân Hóa",
    settingsLanguageTitle = "Ngôn Ngữ Ứng Dụng",
    settingsLanguageSub = "Chuyển đổi giao diện giữa Tiếng Việt & English",
    settingsLanguageToggleLabel = "Chuyển nhanh",
    settingsLanguageCurrentEn = "Đang dùng: English 🇬🇧",
    settingsLanguageCurrentVi = "Đang dùng: Tiếng Việt 🇻🇳",
    settingsWallpaperTitle = "Đổi Hình Nền Đôi",
    settingsWallpaperSub = "Tùy chọn nền hoa anh đào, hoàng hôn biển, bầu trời sao",
    settingsGuideTitle = "Cẩm Nang Hướng Dẫn",
    settingsGuideSub = "Xem hướng dẫn đếm ngày, lịch kỷ niệm & mẹo quà tặng",
    settingsSectionNotifications = "Thông Báo & Lời Nhắc",
    settingsNotifAnniversaryTitle = "Thông báo mốc kỷ niệm",
    settingsNotifAnniversarySub = "Nhắc nhở trước 7 ngày, 3 ngày & 1 ngày",
    settingsNotifEnabledToast = "Đã bật nhắc thông báo kỷ niệm",
    settingsNotifDisabledToast = "Đã tắt nhắc thông báo",
    settingsNotifSoundTitle = "Âm thanh chuông báo yêu thương",
    settingsNotifSoundSub = "Giai điệu lãng mạn nhẹ nhàng",
    settingsSectionSecurity = "Bảo Mật & Thông Tin",
    settingsLockTitle = "Khóa ứng dụng riêng tư",
    settingsLockSub = "Bảo vệ khoảnh khắc riêng tư bằng vân tay/PIN",
    settingsAboutTitle = "Về InLove",
    settingsAboutSub = "Phiên bản 1.0.0 • Dành cho các cặp đôi",
    settingsAboutToast = "InLove - Lưu giữ trọn vẹn từng khoảnh khắc yêu thương ❤️",

    guideTitle = "Cẩm Nang Sử Dụng InLove",
    guideSubtitle = "Khám phá các tính năng giúp hâm nóng tình cảm đôi bạn",
    guideTabCounter = "Đếm Ngày Yêu",
    guideTabPairing = "Ghép Đôi",
    guideTabMemories = "Kho Kỷ Niệm",
    guideTabCalendar = "Lịch Kỷ Niệm",
    guideTabGifts = "Gợi Ý Quà",
    guideTabReminders = "Nhắc Hẹn",
    guideTabSettings = "Cài Đặt",
    btnGotIt = "Đã Hiểu ✨",

    wallpaperTitle = "Đổi Hình Nền Lãng Mạn",
    wallpaperSubtitle = "Chọn phông nền ngọt ngào làm đẹp cho trang chủ của đôi bạn",
    wallpaperPresetCherry = "Hoa Anh Đào Mộng Mơ",
    wallpaperPresetSunset = "Hoàng Hôn Tình Yêu",
    wallpaperPresetStarry = "Đêm Sao Lãng Mạn",
    wallpaperPresetPastel = "Hồng Phấn Ngọt Ngào",
    wallpaperCustomUrl = "Liên kết ảnh tùy chọn (URL)",
    btnApplyWallpaper = "Áp Dụng Hình Nền 🌸",

    memoryAlbumTitle = "Album Kỷ Niệm Đôi Ta 📸",
    memoryAlbumSubtitle = "Những bức ảnh và khoảnh khắc đáng nhớ nhất trong hành trình yêu",
    memoryTitle = "Lưu Giữ Khoảnh Khắc Kỷ Niệm",
    memorySubtitle = "Chụp ảnh hoặc chọn từ thư viện để lưu vào nhật ký tình yêu",
    memoryTitleLabel = "Tiêu đề kỷ niệm *",
    memoryDateLabel = "Ngày kỷ niệm (dd/MM/yyyy) *",
    memoryLocationLabel = "Địa điểm hẹn hò",
    memoryNoteLabel = "Dòng nhật ký & cảm xúc",
    memoryPickPhoto = "Chọn Từ Thư Viện 🖼️",
    memoryCapturePhoto = "Chụp Ảnh Mới 📸",
    memoryPresetNotice = "Hoặc chọn nhanh ảnh mẫu lãng mạn:",
    memoryNotePlaceholder = "Nhập dòng nhật ký hoặc lời nhắn gửi ngọt ngào...",
    memoryUrlPlaceholder = "Dán liên kết hình ảnh kỷ niệm (URL)...",
    btnSaveMemory = "Lưu Vào Kỷ Niệm ❤️",
    btnAddMemory = "Thêm Kỷ Niệm Mới",
    memoryEmptyTitle = "Chưa có bức ảnh kỷ niệm nào",
    memoryEmptySubtitle = "Hãy chụp hoặc chọn bức ảnh đầu tiên để lưu lại khoảnh khắc ngọt ngào của hai bạn!",

    langDialogTitle = "Chọn Ngôn Ngữ / Select Language",
    langDialogSubtitle = "Vui lòng chọn ngôn ngữ bạn muốn sử dụng trong ứng dụng",
    langVietnamese = "Tiếng Việt 🇻🇳",
    langEnglish = "English 🇬🇧",
    btnConfirm = "Xác Nhận",
    btnCancel = "Hủy",

    // Onboarding
    onboardingWelcomeTitle = "Chào Mừng Đến Với InLove 💕",
    onboardingWelcomeSub = "Không gian lãng mạn lưu giữ trọn vẹn từng khoảnh khắc tình yêu đôi bạn",
    onboardingLangSelectTitle = "Chọn Ngôn Ngữ Hiển Thị",
    onboardingCounterTitle = "Đếm Trọn Từng Ngày Yêu ⏳",
    onboardingCounterSub = "Đếm chính xác từng ngày bên nhau, bắn tim kỷ niệm chúc mừng các cột mốc: 100, 200, 365, 1000 ngày!",
    onboardingMemoriesTitle = "Kho Kỷ Niệm & Nhật Ký Đôi 📸",
    onboardingMemoriesSub = "Lưu lại ảnh hẹn hò, địa điểm ý nghĩa và dòng cảm xúc ngọt ngào an toàn trên máy và đồng bộ đám mây.",
    onboardingReminderTitle = "Nhắc Hẹn Thông Minh & Gợi Ý Quà 🎁",
    onboardingReminderSub = "Không bao giờ quên ngày quan trọng nhờ chuông báo trước (7 ngày, 3 ngày, 1 ngày) kèm gợi ý quà tặng từ AI.",
    onboardingStartTitle = "Sẵn Sàng Bắt Đầu Hành Trình 🌸",
    onboardingStartSub = "Cá nhân hóa tên 2 bạn, ngày bắt đầu yêu, hoặc khám phá ngay ứng dụng!",
    onboardingBtnNext = "Tiếp tục",
    onboardingBtnBack = "Quay lại",
    onboardingBtnSkip = "Bỏ qua",
    onboardingBtnStart = "Bắt Đầu Ngay ✨",

    // Calendar
    calendarTitle = "Lịch Kỷ Niệm",
    calendarFilterAll = "Tất cả",
    calendarFilterUpcoming = "Sắp tới",
    calendarFilterPast = "Đã qua",
    calendarPrevMonth = "Tháng trước",
    calendarNextMonth = "Tháng sau",
    calendarAddAnniversary = "Thêm Ngày Kỷ Niệm",

    // Gifts
    giftScreenTitle = "Gợi Ý Quà Tặng",
    giftCatAll = "Tất cả",
    giftCatAi = "AI Đề Xuất ✨",
    giftCatJewelry = "Trang Sức",
    giftCatTech = "Công Nghệ",
    giftCatFlowers = "Hoa & Thiệp",
    giftCatTravel = "Du Lịch & Hẹn Hò",
    giftCatFashion = "Thời Trang",
    giftCatDiy = "Thủ Công & DIY",
    giftChecklistTitle = "Danh Sách Cần Chuẩn Bị",
    giftChecklistAdd = "Thêm Việc Chuẩn Bị",

    // Reminders
    reminderScreenTitle = "Trung Tâm Nhắc Hẹn",
    reminderRealtimeHeader = "Nhắc Hẹn Thời Gian Thực",
    reminderCadenceCustom = "Tùy Chỉnh",
    reminderCadenceWeekly = "Hàng Tuần",
    reminderCadenceMonthly = "Hàng Tháng",
    reminderCadenceAnnually = "Hàng Năm",
    reminderMarkAllRead = "Đã Xem Tất Cả",
    reminderAddNew = "Thêm Lời Nhắc Mới",

    // Pairing
    pairingTitle = "Kết Nối Đôi Bạn 1-1",
    pairingSub = "Ghép đôi cùng người ấy để đồng bộ đếm ngày và kỷ niệm thời gian thực",
    pairingMyCode = "Mã Kết Nối Của Bạn",
    pairingPartnerCode = "Nhập Mã Của Người Ấy",
    pairingBtnSend = "Gửi Lời Mời Kết Nối",
    pairingStatusConnected = "Đã Kết Đôi ❤️",
    pairingStatusPending = "Đang Chờ Phản Hồi ⏳",
    pairingBtnDisconnect = "Hủy Kết Đôi",

    // Paywall
    paywallTitle = "INLOVE PREMIUM",
    paywallSub = "Tình yêu không giới hạn — Gắn kết mọi kỷ niệm trọn vẹn",
    paywallBenefitsHeader = "Đặc Quyền VIP",
    paywallBenefitAds = "Tắt hoàn toàn 100% quảng cáo vĩnh viễn",
    paywallBenefitAi = "Gợi ý quà tặng & thư tình theo sở thích riêng",
    paywallBenefitCloud = "Sao lưu ảnh kỷ niệm HD lên đám mây",
    paywallBenefitLock = "Khóa ứng dụng bằng mã PIN riêng tư",
    paywallBenefitTheme = "Huy hiệu Premium & Theme độc quyền dành cho cặp đôi",
    paywallPlanYearlyTitle = "Gói 1 Năm — Được Yêu Thích ❤️",
    paywallPlanYearlyPrice = "299.000 đ / năm",
    paywallPlanYearlySub = "3 ngày dùng thử miễn phí, sau đó ~24.900 đ/tháng",
    paywallPlanYearlyBadge = "TIẾT KIỆM 50%",
    paywallPlanMonthlyTitle = "Gói 1 Tháng",
    paywallPlanMonthlyPrice = "49.000 đ / tháng",
    paywallPlanMonthlySub = "Thanh toán linh hoạt, hủy bất kỳ lúc nào",
    paywallPlanLifetimeTitle = "Gói Trọn Đời Vĩnh Cửu 💫",
    paywallPlanLifetimePrice = "699.000 đ một lần",
    paywallPlanLifetimeSub = "Thanh toán duy nhất 1 lần — Sử dụng mãi mãi",
    paywallPlanLifetimeBadge = "MÃI MÃI",
    paywallBtnTrial = "🎁 BẮT ĐẦU DÙNG THỬ 3 NGÀY MIỄN PHÍ",
    paywallBtnMonthly = "💳 ĐĂNG KÝ THÁNG NGAY",
    paywallBtnLifetime = "👑 NÂNG CẤP TRỌN ĐỜI NGAY",
    paywallBtnRestore = "Khôi phục gói mua (Restore Purchases)",
    paywallBtnManage = "Quản lý & Hủy gói cước trên Google Play",
    paywallTerms = "Điều khoản dịch vụ",
    paywallPrivacy = "Chính sách bảo mật",

    // Auth
    authLoginTitle = "Đăng Nhập InLove",
    authRegisterTitle = "Tạo Tài Khoản InLove",
    authEmailLabel = "Địa chỉ Email",
    authPasswordLabel = "Mật khẩu",
    authConfirmPasswordLabel = "Xác nhận mật khẩu",
    authForgotPassword = "Quên mật khẩu?",
    authBtnLogin = "Đăng Nhập",
    authBtnRegister = "Đăng Ký",
    authBtnGuest = "Tiếp tục với chế độ Khách",
    authSwitchToRegister = "Chưa có tài khoản? Đăng ký ngay",
    authSwitchToLogin = "Đã có tài khoản? Đăng nhập"
  )

  val englishStatic = AppStrings(
    navHome = "Home",
    navMemories = "Memories",
    navCalendar = "Calendar",
    navGifts = "Gifts",
    navReminders = "Reminders",
    navSettings = "Settings",

    actionWallpaper = "Wallpaper",
    actionMemory = "Add Photo",
    actionCouple = "Partner",
    actionGuide = "Guide",
    actionLanguage = "Language",

    daysInLove = "days in love",
    nextMilestoneTitle = "Milestone %d Days",
    nextMilestoneHeader = "UPCOMING ANNIVERSARY",
    daysRemainingFormat = "%d days left",
    progressTowards = "Milestone Progress",
    romanticAdviceFormat = "💡 Romantic hint: Only %d days until reaching %d days of love! Plan a special dinner date or surprise %s with a heartfelt gift!",
    btnGiftIdeas = "Gift Suggestions",
    btnAnniversaryCalendar = "Anniversary Calendar",
    quoteSectionTag = "LOVE EXPRESSIONS",
    quoteSectionTitle = "Sweet Quotes & Reflections 💕",
    btnShuffleQuote = "New Quote 💫",
    btnSendToPartner = "Send to Bae 💌",
    milestoneCelebrationTitle = "🎉 Milestone Reached!",
    milestoneCelebrationBtn = "Shower Floating Hearts 💕",

    adPlaceholderNotice = "Ready slot reserved for Ad Plugin integration (AdMob)",
    countingDaysNotice = "InLove: Counting %d days of romance ❤️",

    settingsTitle = "Settings",
    settingsProfileCardEdit = "Edit",
    settingsSweetDaysFormat = "%d sweet days together ❤️",
    settingsDbSecured = "💾 Room Database secured (Tap to edit)",
    settingsSectionProfile = "Profile & Sweet Memories",
    settingsEditProfileTitle = "Edit Couple Profile",
    settingsEditProfileSub = "Names, birthdays, zodiacs & love start date",
    settingsCaptureMemoryTitle = "Capture & Save Memory",
    settingsCaptureMemorySub = "Add milestone memory to timeline",
    settingsDbSyncTitle = "Room Database Storage Sync",
    settingsDbSyncSub = "All memories securely saved offline",
    settingsDbSyncToast = "Room Database is synced perfectly!",
    settingsSectionAppearance = "Appearance & Personalization",
    settingsLanguageTitle = "App Language",
    settingsLanguageSub = "Switch UI between English & Tiếng Việt",
    settingsLanguageToggleLabel = "Quick Switch",
    settingsLanguageCurrentEn = "Active: English 🇬🇧",
    settingsLanguageCurrentVi = "Active: Tiếng Việt 🇻🇳",
    settingsWallpaperTitle = "Change Couple Wallpaper",
    settingsWallpaperSub = "Cherry blossom, beach sunset, starry night",
    settingsGuideTitle = "App Guide & Tips",
    settingsGuideSub = "How to count days, anniversaries & gift tips",
    settingsSectionNotifications = "Notifications & Reminders",
    settingsNotifAnniversaryTitle = "Anniversary Notifications",
    settingsNotifAnniversarySub = "Reminders 7 days, 3 days & 1 day before",
    settingsNotifEnabledToast = "Anniversary reminders enabled",
    settingsNotifDisabledToast = "Reminders disabled",
    settingsNotifSoundTitle = "Sweet Melody Sound",
    settingsNotifSoundSub = "Gentle romantic chime sound",
    settingsSectionSecurity = "Security & About",
    settingsLockTitle = "Private App Lock",
    settingsLockSub = "Protect memories with biometric lock",
    settingsAboutTitle = "About InLove",
    settingsAboutSub = "Version 1.0.0 • Crafted for couples",
    settingsAboutToast = "InLove - Cherishing every sweet moment together ❤️",

    guideTitle = "InLove User Guide",
    guideSubtitle = "Explore features crafted to cherish every moment of your love",
    guideTabCounter = "Love Counter",
    guideTabPairing = "Pairing",
    guideTabMemories = "Memories Album",
    guideTabCalendar = "Calendar",
    guideTabGifts = "Gift Ideas",
    guideTabReminders = "Reminders",
    guideTabSettings = "Settings",
    btnGotIt = "Got it ✨",

    wallpaperTitle = "Change Romantic Wallpaper",
    wallpaperSubtitle = "Choose a dreamy background theme for your love home screen",
    wallpaperPresetCherry = "Cherry Blossom Dream",
    wallpaperPresetSunset = "Sunset Romance",
    wallpaperPresetStarry = "Starry Love Night",
    wallpaperPresetPastel = "Sweet Pastel Glow",
    wallpaperCustomUrl = "Custom Image URL",
    btnApplyWallpaper = "Apply Wallpaper 🌸",

    memoryAlbumTitle = "Our Love Memories Album 📸",
    memoryAlbumSubtitle = "Cherished photos & timeless moments in our romantic journey",
    memoryTitle = "Capture Special Moments",
    memorySubtitle = "Take a photo or pick from gallery to save in love diary",
    memoryTitleLabel = "Memory Title *",
    memoryDateLabel = "Anniversary Date (dd/MM/yyyy) *",
    memoryLocationLabel = "Date Location",
    memoryNoteLabel = "Diary Thoughts & Feelings",
    memoryPickPhoto = "Pick from Gallery 🖼️",
    memoryCapturePhoto = "Take Photo 📸",
    memoryPresetNotice = "Or pick a romantic sample photo:",
    memoryNotePlaceholder = "Write a sweet diary note or loving thought...",
    memoryUrlPlaceholder = "Paste photo memory link (URL)...",
    btnSaveMemory = "Save to Memories ❤️",
    btnAddMemory = "Add New Memory",
    memoryEmptyTitle = "No memories saved yet",
    memoryEmptySubtitle = "Capture or pick your first photo to begin your love album together!",

    langDialogTitle = "Select Language / Chọn Ngôn Ngữ",
    langDialogSubtitle = "Please select your preferred display language for the app",
    langVietnamese = "Tiếng Việt 🇻🇳",
    langEnglish = "English 🇬🇧",
    btnConfirm = "Confirm",
    btnCancel = "Cancel",

    // Onboarding
    onboardingWelcomeTitle = "Welcome to InLove 💕",
    onboardingWelcomeSub = "Cherishing every precious moment of your romantic love story",
    onboardingLangSelectTitle = "Select Display Language",
    onboardingCounterTitle = "Cherish Every Day in Love ⏳",
    onboardingCounterSub = "Accurately count days together and celebrate memorable milestones: 100, 200, 365, 1000 days!",
    onboardingMemoriesTitle = "Memories & Private Diary 📸",
    onboardingMemoriesSub = "Capture photos, date spots and sweet thoughts safely preserved offline and synced across devices.",
    onboardingReminderTitle = "Smart Reminders & AI Gifts 🎁",
    onboardingReminderSub = "Never forget important anniversaries with advance alerts (7d, 3d, 1d) plus AI romantic gift suggestions.",
    onboardingStartTitle = "Ready to Begin Your Journey 🌸",
    onboardingStartSub = "Personalize couple names, your anniversary start date, or start exploring immediately!",
    onboardingBtnNext = "Continue",
    onboardingBtnBack = "Back",
    onboardingBtnSkip = "Skip",
    onboardingBtnStart = "Get Started ✨",

    // Calendar
    calendarTitle = "Anniversary Calendar",
    calendarFilterAll = "All",
    calendarFilterUpcoming = "Upcoming",
    calendarFilterPast = "Past",
    calendarPrevMonth = "Previous Month",
    calendarNextMonth = "Next Month",
    calendarAddAnniversary = "Add Anniversary",

    // Gifts
    giftScreenTitle = "Gift Suggestions",
    giftCatAll = "All",
    giftCatAi = "AI Suggestions ✨",
    giftCatJewelry = "Jewelry",
    giftCatTech = "Tech Gadgets",
    giftCatFlowers = "Flowers & Cards",
    giftCatTravel = "Travel & Date",
    giftCatFashion = "Fashion",
    giftCatDiy = "Handmade & DIY",
    giftChecklistTitle = "Preparation Checklist",
    giftChecklistAdd = "Add Checklist Item",

    // Reminders
    reminderScreenTitle = "Reminders Hub",
    reminderRealtimeHeader = "Real-time Reminder Center",
    reminderCadenceCustom = "Custom",
    reminderCadenceWeekly = "Weekly",
    reminderCadenceMonthly = "Monthly",
    reminderCadenceAnnually = "Annually",
    reminderMarkAllRead = "Mark All Read",
    reminderAddNew = "Add Reminder",

    // Pairing
    pairingTitle = "Couple Connection 1-1",
    pairingSub = "Pair with your loved one to synchronize day counter and memories in real time",
    pairingMyCode = "Your Invite Code",
    pairingPartnerCode = "Enter Partner's Code",
    pairingBtnSend = "Send Connection Request",
    pairingStatusConnected = "Connected ❤️",
    pairingStatusPending = "Awaiting Response ⏳",
    pairingBtnDisconnect = "Disconnect",

    // Paywall
    paywallTitle = "INLOVE PREMIUM",
    paywallSub = "Unlimited Love — Cherish every precious moment together",
    paywallBenefitsHeader = "VIP Privileges",
    paywallBenefitAds = "100% Ad-Free forever",
    paywallBenefitAi = "Personalized gift ideas & love letter suggestions",
    paywallBenefitCloud = "HD memory photo backup to the cloud",
    paywallBenefitLock = "PIN-protected private app lock",
    paywallBenefitTheme = "Exclusive couple themes & Premium badges",
    paywallPlanYearlyTitle = "1-Year Plan — Most Popular ❤️",
    paywallPlanYearlyPrice = "$12.99 / year",
    paywallPlanYearlySub = "3 days free trial, then ~$1.08/month",
    paywallPlanYearlyBadge = "SAVE 50%",
    paywallPlanMonthlyTitle = "1-Month Plan",
    paywallPlanMonthlyPrice = "$1.99 / month",
    paywallPlanMonthlySub = "Flexible billing, cancel anytime",
    paywallPlanLifetimeTitle = "Lifetime Eternal Access 💫",
    paywallPlanLifetimePrice = "$29.99 one-time",
    paywallPlanLifetimeSub = "Pay once — Cherish love forever",
    paywallPlanLifetimeBadge = "LIFETIME",
    paywallBtnTrial = "🎁 START 3-DAY FREE TRIAL",
    paywallBtnMonthly = "💳 SUBSCRIBE MONTHLY NOW",
    paywallBtnLifetime = "👑 UPGRADE LIFETIME NOW",
    paywallBtnRestore = "Restore Purchases",
    paywallBtnManage = "Manage Subscriptions on Google Play",
    paywallTerms = "Terms of Service",
    paywallPrivacy = "Privacy Policy",

    // Auth
    authLoginTitle = "Sign In to InLove",
    authRegisterTitle = "Create InLove Account",
    authEmailLabel = "Email address",
    authPasswordLabel = "Password",
    authConfirmPasswordLabel = "Confirm password",
    authForgotPassword = "Forgot password?",
    authBtnLogin = "Sign In",
    authBtnRegister = "Create Account",
    authBtnGuest = "Continue as Guest",
    authSwitchToRegister = "Don't have an account? Sign up",
    authSwitchToLogin = "Already have an account? Sign in"
  )
}
