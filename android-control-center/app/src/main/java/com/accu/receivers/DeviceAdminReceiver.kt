package com.accu.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ACCDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device Admin enabled — Freeze via policy is now available", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device Admin disabled — Policy-based freeze unavailable", Toast.LENGTH_SHORT).show()
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {}
    override fun onPasswordFailed(context: Context, intent: Intent) {}
    override fun onPasswordSucceeded(context: Context, intent: Intent) {}
}
