package com.aptoglobal.playground.core.connectivity

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkRequest
import com.aptoglobal.playground.core.coroutines.mutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkConnectivityMonitorImpl(
    context: Context
) : NetworkConnectivityMonitor {

    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    private val connectivityPublisher = mutableSharedFlow<Boolean>()

    init {
        val networkCallback = createNetworkCallback()
        val networkRequest = createNetworkRequest()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onConnectivityChange(): Flow<Boolean> = connectivityPublisher.distinctUntilChanged()

    private fun createNetworkCallback() = object : ConnectivityManager.NetworkCallback() {
        val validNetworks = mutableSetOf<Network>()

        override fun onAvailable(network: Network) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            val hasInternet = networkCapabilities?.hasCapability(NET_CAPABILITY_INTERNET)
            hasInternet?.let {
                if (it) validNetworks.add(network)
                updateConnectivityStatus()
            }
        }

        override fun onLost(network: Network) {
            validNetworks.remove(network)
            updateConnectivityStatus()
        }

        private fun updateConnectivityStatus() = connectivityPublisher.tryEmit(validNetworks.isNotEmpty())
    }

    private fun createNetworkRequest() = NetworkRequest.Builder()
        .addCapability(NET_CAPABILITY_INTERNET)
        .build()
}
