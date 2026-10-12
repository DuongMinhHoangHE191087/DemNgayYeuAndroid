package com.app.plugin.logevent

public object ScreenName {
    @Volatile
    public var name: String = "unknown"
}

internal object AdmobSDKInitState {
    var isInitComplete = 0
}