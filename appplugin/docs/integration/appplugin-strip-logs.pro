# Tuỳ chọn (2.1.0 SC1, D4): bỏ lời gọi LogUtil.logD/logW của SDK khỏi bản release để chuỗi log không nằm trong dex.
# Thêm file này vào proguardFiles của app release: proguardFiles(..., "path/to/appplugin-strip-logs.pro").
# Đừng bật ở bản app chạy cổng thiết bị của SDK: các cổng đọc dòng logD (flag test bật). Production: logD/logW vốn đã im.
-assumenosideeffects class com.app.plugin.until.LogUtil$Companion {
    public void logD(java.lang.String);
    public void logW(java.lang.String);
}
