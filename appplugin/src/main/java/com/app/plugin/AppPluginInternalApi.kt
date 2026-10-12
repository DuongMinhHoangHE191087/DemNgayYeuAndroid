package com.app.plugin

/**
 * Đánh dấu SPI giữa các artifact appplugin (2.1.0 G1, Q1): `appplugin-game` gọi core qua đây thay vì friend-path.
 * App không dùng: SPI có thể đổi giữa hai bản mà không báo (CHANGELOG "Giới hạn đã biết"). Opt-in bắt buộc (ERROR).
 */
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "API nội bộ giữa các artifact appplugin; app không dùng")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class AppPluginInternalApi
