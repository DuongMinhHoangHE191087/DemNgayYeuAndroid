package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkMonitorTest {

  @Test
  fun isOnline_reflectsShadowConnectivityState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val monitor = NetworkMonitor(context)

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val shadow = org.robolectric.Shadows.shadowOf(cm) as ShadowConnectivityManager
    shadow.setDefaultNetworkActive(false)

    assert(!monitor.isOnline.value) { "NetworkMonitor must report offline when the default network is inactive" }
  }
}
