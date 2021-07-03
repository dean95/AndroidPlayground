package com.aptoglobal.playground.core.connectivity

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityMonitor {

    fun onConnectivityChange(): Flow<Boolean>
}
