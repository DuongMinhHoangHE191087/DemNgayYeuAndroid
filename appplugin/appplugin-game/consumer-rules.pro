# appplugin-game consumer rules — phần game của consumer-rules.pro gốc (2.0.0 C2a); 2.1.0 G1 (F5): nơi DUY NHẤT giữ keep GameModule.
# 2.0.0 C1d (D16): core dò GameModule bằng Class.forName tên cố định (RcSectionRegistry.GAME_MODULE_CLASS) rồi gọi install() qua reflection.
-keep class com.app.plugin.game.GameModule { public static final com.app.plugin.game.GameModule INSTANCE; public void install(); }
