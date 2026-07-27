package com.nemotron.voiceime.dhizuku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AirplaneReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // No longer used – AirplaneTileService handles everything directly
    }
}
