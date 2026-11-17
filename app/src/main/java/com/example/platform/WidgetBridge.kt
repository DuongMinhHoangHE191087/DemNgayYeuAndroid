package com.example.platform

/** Cầu nối tới widget iOS: thư mục snapshot, reload timeline và thumbnail. */
interface WidgetBridge {
  fun snapshotDirectory(): String?
  fun reloadTimelines()
  fun writeThumbnail(sourcePath: String, fileName: String, maxPixel: Int): Boolean
  /** Xoá snapshot và thư mục thumbs/ rồi buộc reload, không qua bộ gộp. */
  fun clear()
}
