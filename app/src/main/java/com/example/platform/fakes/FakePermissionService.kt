package com.example.platform.fakes

import com.example.platform.NotificationPermission
import com.example.platform.PermissionService

class FakePermissionService(
  var status: NotificationPermission = NotificationPermission.NotDetermined,
  var statusAfterRequest: NotificationPermission = NotificationPermission.Granted,
) : PermissionService {
  var requestCount = 0
    private set
  var openSettingsCount = 0
    private set

  override fun notificationStatus(): NotificationPermission = status

  override fun requestNotifications(onResult: (NotificationPermission) -> Unit) {
    requestCount++
    status = statusAfterRequest
    onResult(status)
  }

  override fun openAppSettings() {
    openSettingsCount++
  }
}
