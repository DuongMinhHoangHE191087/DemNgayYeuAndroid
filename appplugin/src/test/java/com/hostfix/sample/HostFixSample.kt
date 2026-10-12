package com.hostfix.sample

import android.app.Activity
import android.content.pm.PackageManager
import com.app.plugin.app.AppPluginBase

/**
 * 2.4.1 HOSTFIX (D36) — app mẫu cho `AppPluginBaseHostTest`: lớp Application ở gói `com.hostfix.sample`, applicationId tuỳ test
 * (`com.hostfix.sample.debug` = bản debug có `applicationIdSuffix`, như Invoice Maker), launcher tuỳ test (PackageManager giả).
 */
class App(private val appId: String, private val pm: PackageManager) : AppPluginBase() {
    override fun getPackageName(): String = appId
    override fun getPackageManager(): PackageManager = pm
}

class MainActivity : Activity()

class OtherActivity : Activity()
