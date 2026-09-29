package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime connectivity observer. Registered once from [com.example.di.AppServiceLocator]
 * (Task 8) so every consumer (SyncCoordinator, Settings offline banner) reads the same instance
 * instead of each registering its own ConnectivityManager.NetworkCallback.
 */
class NetworkMonitor(context: Context) {

  private val _isOnline = MutableStateFlow(currentlyOnline(context))
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      _isOnline.value = true
    }

    override fun onLost(network: Network) {
      _isOnline.value = currentlyOnline(context)
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
  }

  init {
    val request = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()
    connectivityManager.registerNetworkCallback(request, callback)
  }

  private fun currentlyOnline(context: Context): Boolean {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
