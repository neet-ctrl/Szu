package com.accu.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.accu.services.AutoFreezeService

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            val prefs = context.getSharedPreferences("accu_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_freeze_on_screen_off", false)) {
                AutoFreezeService.triggerFreeze(context)
            }
        }
    }
}
