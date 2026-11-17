package com.example.platform

enum class NotificationPermission { NotDetermined, Granted, Denied }

// Pure Kotlin on purpose: the iOS side implements the same contract.
interface PermissionService {
  fun notificationStatus(): NotificationPermission

  fun requestNotifications(onResult: (NotificationPermission) -> Unit)

  fun openAppSettings()
}
