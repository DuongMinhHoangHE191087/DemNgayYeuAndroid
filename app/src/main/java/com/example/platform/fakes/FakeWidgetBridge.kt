package com.example.platform.fakes

import com.example.platform.WidgetBridge

/** Cầu nối widget giả cho test: ghi lại lệnh gọi, trả thư mục và kết quả thumbnail đã cấu hình. */
class FakeWidgetBridge(var directory: String? = null, var thumbnailResult: Boolean = true) : WidgetBridge {
  /** "reloadTimelines", "writeThumbnail:<sourcePath>:<fileName>:<maxPixel>", "clear", theo thứ tự gọi; snapshotDirectory() là truy vấn nên không ghi. */
  val calls = mutableListOf<String>()

  override fun snapshotDirectory(): String? = directory

  override fun reloadTimelines() {
    calls += "reloadTimelines"
  }

  override fun writeThumbnail(sourcePath: String, fileName: String, maxPixel: Int): Boolean {
    calls += "writeThumbnail:$sourcePath:$fileName:$maxPixel"
    return thumbnailResult
  }

  override fun clear() {
    calls += "clear"
  }
}
