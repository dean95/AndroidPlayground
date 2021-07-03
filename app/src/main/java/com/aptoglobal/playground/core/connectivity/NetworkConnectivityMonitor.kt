package com.aptoglobal.playground.core.connectivity

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityMonitor {

    /**
     * It emits `true` if the device has a valid network connection and is able to reach the internet. Otherwise, it emits `false`.
     */
    fun onConnectivityChange(): Flow<Boolean>
}
