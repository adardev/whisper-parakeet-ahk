package com.nemotron.voiceime.chat

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.autoInsets(includeTop: Boolean = true) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val bottom = if (ime > 0) ime else bars.bottom
        view.setPadding(view.paddingLeft, if (includeTop) bars.top else 0, view.paddingRight, bottom)
        insets
    }
}