package com.mbridge.hostfix

import android.app.Activity
import android.content.pm.PackageManager
import com.app.plugin.app.AppPluginBase

/** 2.4.1 HOSTFIX m2: lớp Application nằm trong gói mediation (`com.mbridge.`) — Activity cùng gói vẫn không được làm host. */
class MediationApp(private val appId: String, private val pm: PackageManager) : AppPluginBase() {
    override fun getPackageName(): String = appId
    override fun getPackageManager(): PackageManager = pm
}

class MediationActivity : Activity()
