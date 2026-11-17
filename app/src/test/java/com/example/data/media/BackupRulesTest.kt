package com.example.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Sao lưu Android chỉ cho thư mục files/media (ảnh/video kỷ niệm và memories_backup.json).
 * Include duy nhất là files/media nên DB và SharedPreferences không bao giờ được sao lưu; không khai báo exclude
 * cho chúng vì lint FullBackupContent báo lỗi exclude nằm ngoài include. Đọc XML trực tiếp (JVM thuần);
 * cwd của unit test là thư mục app/.
 */
class BackupRulesTest {

  @Test
  fun manifestAllowsBackup() {
    val apps = named(parse("src/main/AndroidManifest.xml"), "application")
    assertEquals(1, apps.size)
    assertEquals("true", apps[0].getAttribute("android:allowBackup"))
  }

  @Test
  fun legacyBackupRulesIncludeOnlyMediaWithClientSideEncryption() {
    val includes = named(parse("src/main/res/xml/backup_rules.xml"), "include")
    assertEquals(1, includes.size)
    assertEquals("file", includes[0].getAttribute("domain"))
    assertEquals("media", includes[0].getAttribute("path"))
    assertEquals("clientSideEncryption", includes[0].getAttribute("requireFlags"))
  }

  @Test
  fun cloudBackupRequiresEncryptionAndIncludesMediaOnly() {
    val cloud = named(parse("src/main/res/xml/data_extraction_rules.xml"), "cloud-backup")
    assertEquals(1, cloud.size)
    assertEquals("true", cloud[0].getAttribute("disableIfNoEncryptionCapabilities"))
    assertMediaOnly(cloud[0], "cloud-backup")
  }

  @Test
  fun deviceTransferIncludesMediaOnly() {
    val transfer = named(parse("src/main/res/xml/data_extraction_rules.xml"), "device-transfer")
    assertEquals(1, transfer.size)
    assertMediaOnly(transfer[0], "device-transfer")
  }

  /** Đọc XML bằng DOM mặc định (không namespace-aware): tên có tiền tố như "android:allowBackup" dùng được. */
  private fun parse(path: String): Element =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement

  /** Mọi phần tử con cháu có tên [tag], theo thứ tự xuất hiện. */
  private fun named(parent: Element, tag: String): List<Element> {
    val nodes = parent.getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }
  }

  /** Một section của data_extraction_rules: đúng 1 include file/media, không requireFlags. */
  private fun assertMediaOnly(section: Element, where: String) {
    val includes = named(section, "include")
    assertEquals("$where: phải có đúng 1 include", 1, includes.size)
    assertEquals("file", includes[0].getAttribute("domain"))
    assertEquals("media", includes[0].getAttribute("path"))
    assertFalse("$where: data_extraction không dùng requireFlags", includes[0].hasAttribute("requireFlags"))
  }
}
