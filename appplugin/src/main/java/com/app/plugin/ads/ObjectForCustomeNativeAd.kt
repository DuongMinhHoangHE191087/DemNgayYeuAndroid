package com.app.plugin.ads

import android.content.Context

public class ObjectForCustomeNativeAd(bgColor: Int, btActColor: Int) {
    public var bgColor: Int = 0
    public var actionColor: Int = 0

    init {
        this.bgColor = bgColor
        this.actionColor = btActColor
    }
}