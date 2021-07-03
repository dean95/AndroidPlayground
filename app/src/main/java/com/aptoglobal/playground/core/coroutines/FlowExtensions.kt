package com.aptoglobal.playground.core.coroutines

import kotlinx.coroutines.flow.MutableSharedFlow

fun <T> mutableSharedFlow() = MutableSharedFlow<T>(extraBufferCapacity = 1)
