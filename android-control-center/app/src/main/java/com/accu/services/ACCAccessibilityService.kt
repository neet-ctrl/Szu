package com.accu.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * Accessibility service for Key Mapper functionality.
 * Intercepts key events and routes them to the automation engine.
 */
class ACCAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ACCAccessibilityService? = null
        const val ACTION_KEY_EVENT = "com.accu.ACTION_KEY_EVENT"
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = serviceInfo.also { info ->
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Timber.d("ACC Accessibility Service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val intent = Intent(ACTION_KEY_EVENT).apply {
                putExtra("keyCode", event.keyCode)
                putExtra("flags", event.flags)
                putExtra("metaState", event.metaState)
                putExtra("deviceId", event.deviceId)
                putExtra("timestamp", event.eventTime)
            }
            sendBroadcast(intent)
        }
        // Return false to allow normal processing — true to consume the key
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun performGlobalAction(action: Int): Boolean = super.performGlobalAction(action)
}
