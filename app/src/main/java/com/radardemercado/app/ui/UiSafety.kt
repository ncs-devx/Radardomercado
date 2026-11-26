package com.radardemercado.app.ui

import android.content.Context
import android.content.Intent

fun Context.openSafely(intent: Intent): Boolean = runCatching {
    startActivity(intent)
    true
}.getOrDefault(false)
