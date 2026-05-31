package com.accu.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listens for package install / remove / replace events.
 * Notifies the app's live data so the App Manager refreshes its list automatically.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PackageChangeReceiver"
        const val ACTION_PACKAGE_CHANGED = "com.accu.action.PACKAGE_CHANGED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val action = intent.action ?: return

        Log.d(TAG, "Package event: $action → $packageName")

        val localBroadcast = Intent(ACTION_PACKAGE_CHANGED).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            putExtra("action", action)
        }
        context.sendBroadcast(localBroadcast)
    }
}
