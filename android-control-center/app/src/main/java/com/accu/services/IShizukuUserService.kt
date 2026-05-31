package com.accu.services

import android.os.IInterface

/**
 * Kotlin-friendly AIDL-style interface for the Shizuku UserService.
 * In a production build this would be generated from an .aidl file.
 * For now we define the interface in Kotlin and use Binder.Stub pattern.
 */
interface IShizukuUserService : IInterface {
    fun destroy()
    fun exit()

    abstract class Stub : android.os.Binder(), IShizukuUserService {
        init {
            attachInterface(this, IShizukuUserService::class.java.name)
        }

        override fun asBinder(): android.os.IBinder = this

        companion object {
            fun asInterface(binder: android.os.IBinder?): IShizukuUserService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(IShizukuUserService::class.java.name)
                if (local is IShizukuUserService) return local
                return null
            }
        }
    }
}
