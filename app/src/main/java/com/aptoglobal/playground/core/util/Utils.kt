package com.aptoglobal.playground.core.util

import android.os.Build
import com.aptoglobal.playground.BuildConfig

inline fun <T> doIfSdk29OrUpOrNull(action: () -> T): T? = if (BuildConfig.VERSION_CODE >= Build.VERSION_CODES.Q) action() else null
