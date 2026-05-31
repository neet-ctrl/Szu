package com.accu.services

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku UserService — runs in the Shizuku privileged process.
 * Provides elevated IPC for operations that need system-level access:
 *   - toggling mobile data / Wi-Fi / hotspot
 *   - writing secure settings
 *   - component enable/disable
 *   - package operations
 *
 * Instantiated & destroyed by the Shizuku framework; communicate via AIDL binder.
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    companion object {
        private const val TAG = "ShizukuUserService"

        private var serviceInstance: IShizukuUserService? = null
        private val userServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                Log.d(TAG, "ShizukuUserService connected")
                serviceInstance = IShizukuUserService.Stub.asInterface(binder)
            }
            override fun onServiceDisconnected(name: ComponentName) {
                Log.d(TAG, "ShizukuUserService disconnected")
                serviceInstance = null
            }
        }

        private val userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName("com.accu.controlcenter", ShizukuUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("user_service")
            .debuggable(false)
            .version(1)

        fun bind() {
            try {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind ShizukuUserService", e)
            }
        }

        fun unbind() {
            try {
                Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind ShizukuUserService", e)
            }
        }

        fun getService(): IShizukuUserService? = serviceInstance
    }

    override fun destroy() {
        Log.d(TAG, "ShizukuUserService.destroy() called")
    }

    override fun exit() {
        Log.d(TAG, "ShizukuUserService.exit() called")
        System.exit(0)
    }
}
