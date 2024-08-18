package com.cak.japaclocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class ScreenLockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Start the ForegroundService when screen is locked
                val serviceIntent = Intent(context, ForegroundService::class.java)
                context.startService(serviceIntent)
                Log.d("ScreenLockReceiver", "Screen Off: ForegroundService started")
            }
            Intent.ACTION_SCREEN_ON -> {
                // Stop the ForegroundService when screen is unlocked
                val serviceIntent = Intent(context, ForegroundService::class.java)
                context.stopService(serviceIntent)
                Log.d("ScreenLockReceiver", "Screen On: ForegroundService stopped")
            }
        }
    }
}