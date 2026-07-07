package com.hermes.satellite.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> Log.d(TAG, "Screen off")
            Intent.ACTION_SCREEN_ON -> Log.d(TAG, "Screen on")
            Intent.ACTION_USER_PRESENT -> Log.d(TAG, "User present")
        }
    }

    companion object {
        private const val TAG = "Satellite.Screen"
    }
}
