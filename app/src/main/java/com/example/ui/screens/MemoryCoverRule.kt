package com.example.ui.screens

/**
 * Danh sách ảnh của hộp thoại thêm kỷ niệm sau khi người dùng chọn [picked]. Ảnh preset là URL https và được chọn sẵn khi có preset;
 * nếu giữ nó thì nó thành ảnh bìa còn ảnh vừa chọn bị bỏ rơi, nên bỏ preset đi (T15/A-2). Tối đa 10 ảnh.
 */
internal fun withPickedMedia(selected: List<String>, picked: List<String>): List<String> =
  (selected.filterNot { it.startsWith("https://", ignoreCase = true) } + picked).distinct().take(10)
