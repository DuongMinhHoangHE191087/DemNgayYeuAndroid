package com.app.plugin.ads

import android.content.Context
import android.content.res.Resources
import com.app.plugin.ads.adsmob.AdsMobMy
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal class AdsUtil {
    companion object {

        fun adIsFb(adapterAdName: String?, areaBlock: String?): Boolean {
            //return true
            if (areaBlock != null && areaBlock.length > 0 && adapterAdName != null) {
                if (adapterAdName.contains("facebook")) {
                    return true
                }
            }
            return false
        }
    }
}