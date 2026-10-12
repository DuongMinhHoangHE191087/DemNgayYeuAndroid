package com.app.plugin.ads

import android.content.Context

internal abstract class AdsBaseBannerCl(type: Int, context: Context) : AdsBaseBanner(type, context) {
    override fun getPlacementDef(): String {
        return PlacementDefault.PLClDefault
    }
}