package com.example.platform

/** Không có widget trên Android (DEC-03): không có thư mục snapshot, không ghi thumbnail. */
class NoopWidgetBridge : WidgetBridge {
  override fun snapshotDirectory(): String? = null

  override fun reloadTimelines() {}

  override fun writeThumbnail(sourcePath: String, fileName: String, maxPixel: Int): Boolean = false

  override fun clear() {}
}
