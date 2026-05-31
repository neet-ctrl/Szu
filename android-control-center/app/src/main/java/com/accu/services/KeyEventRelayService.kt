package com.accu.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

class KeyEventRelayService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("KeyEventRelayService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyCode = intent?.getIntExtra(EXTRA_KEY_CODE, 0) ?: return START_NOT_STICKY
        val action = intent.getIntExtra(EXTRA_ACTION, KeyEvent.ACTION_DOWN)
        handleKeyEvent(keyCode, action)
        return START_NOT_STICKY
    }

    private fun handleKeyEvent(keyCode: Int, action: Int) {
        val prefs = getSharedPreferences("keymapper_prefs", MODE_PRIVATE)
        val mappingsJson = prefs.getString("mappings", "[]") ?: return
        Timber.d("KeyEventRelay: keyCode=$keyCode action=$action")
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        const val EXTRA_KEY_CODE = "key_code"
        const val EXTRA_ACTION = "key_action"
    }
}
