package com.example.platform

import com.example.alarm.plan.fakes.FakeNotificationGateway
import com.example.platform.fakes.FakePermissionService
import com.example.platform.fakes.FakePrivacyCoverGateway
import com.example.platform.fakes.FakeShareGateway
import com.example.platform.fakes.FakeShareGateway.Share
import com.example.platform.fakes.FakeWidgetBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformServicesTest {

  @Test
  fun fakesRecordCallsInOrder() {
    val share = FakeShareGateway()
    share.share("a")
    share.share("b", "T")
    assertEquals(listOf(Share("a", null), Share("b", "T")), share.shares)

    val cover = FakePrivacyCoverGateway()
    cover.setCoverEnabled(true)
    cover.setCoverEnabled(false)
    cover.setCoverEnabled(true)
    assertEquals(listOf(true, false, true), cover.calls)

    val widgets = FakeWidgetBridge()
    widgets.reloadTimelines()
    widgets.writeThumbnail("/s/a.jpg", "a.jpg", 256)
    widgets.clear()
    assertEquals(listOf("reloadTimelines", "writeThumbnail:/s/a.jpg:a.jpg:256", "clear"), widgets.calls)
  }

  @Test
  fun noopWidgetBridgeReportsNoContainerAndRejectsThumbnails() {
    val bridge = NoopWidgetBridge()
    assertNull(bridge.snapshotDirectory())
    assertFalse(bridge.writeThumbnail("/s/a.jpg", "a.jpg", 256))
  }

  @Test
  fun fakesBundleExposesEveryGateway() {
    val s = PlatformServices.fakes()
    assertIs<FakeNotificationGateway>(s.notifications)
    assertIs<FakePermissionService>(s.permissions)
    assertIs<FakeShareGateway>(s.share)
    assertIs<FakePrivacyCoverGateway>(s.privacyCover)
    assertIs<FakeWidgetBridge>(s.widgets)

    val t = PlatformServices.fakes()
    assertNotSame(s.notifications, t.notifications)
    assertNotSame(s.permissions, t.permissions)
    assertNotSame(s.share, t.share)
    assertNotSame(s.privacyCover, t.privacyCover)
    assertNotSame(s.widgets, t.widgets)
  }

  @Test
  fun fakeWidgetBridgeReturnsConfiguredDirectory() {
    assertEquals("/data/widget", FakeWidgetBridge("/data/widget").snapshotDirectory())
    assertNull(FakeWidgetBridge().snapshotDirectory())

    val bridge = FakeWidgetBridge()
    bridge.directory = "/x"
    assertEquals("/x", bridge.snapshotDirectory())
    assertEquals(emptyList<String>(), bridge.calls)
  }

  @Test
  fun fakeWidgetBridgeRecordsClearInOrder() {
    val bridge = FakeWidgetBridge()
    bridge.reloadTimelines()
    bridge.clear()
    bridge.reloadTimelines()
    assertEquals(listOf("reloadTimelines", "clear", "reloadTimelines"), bridge.calls)
    assertFalse(FakeWidgetBridge(thumbnailResult = false).writeThumbnail("/s/a.jpg", "a.jpg", 256))
    assertTrue(FakeWidgetBridge().writeThumbnail("/s/a.jpg", "a.jpg", 256))
  }

  @Test
  fun noopWidgetBridgeClearIsSafe() {
    val bridge = NoopWidgetBridge()
    bridge.clear()
    bridge.clear()
    bridge.reloadTimelines()
    assertNull(bridge.snapshotDirectory())
  }

  @Test
  fun fakePrivacyCoverRecordsRepeatedValues() {
    val cover = FakePrivacyCoverGateway()
    cover.setCoverEnabled(true)
    cover.setCoverEnabled(true)
    assertEquals(listOf(true, true), cover.calls)
  }
}
