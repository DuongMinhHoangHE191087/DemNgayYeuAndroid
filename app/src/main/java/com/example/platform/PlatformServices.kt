package com.example.platform

import com.example.alarm.plan.NotificationGateway
import com.example.alarm.plan.fakes.FakeNotificationGateway
import com.example.platform.fakes.FakePermissionService
import com.example.platform.fakes.FakePrivacyCoverGateway
import com.example.platform.fakes.FakeShareGateway
import com.example.platform.fakes.FakeWidgetBridge

/** Gom các cổng nền tảng để truyền một chỗ; fakes() dựng bộ giả mới mỗi lần gọi. */
data class PlatformServices(
  val notifications: NotificationGateway,
  val permissions: PermissionService,
  val share: ShareGateway,
  val privacyCover: PrivacyCoverGateway,
  val widgets: WidgetBridge
) {
  companion object {
    fun fakes(): PlatformServices = PlatformServices(
      FakeNotificationGateway(), FakePermissionService(), FakeShareGateway(), FakePrivacyCoverGateway(), FakeWidgetBridge()
    )
  }
}
