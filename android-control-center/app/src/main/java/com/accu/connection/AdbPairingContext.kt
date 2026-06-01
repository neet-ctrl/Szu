package com.accu.connection

/**
 * JNI wrapper over BoringSSL's SPAKE2 + AES-128-GCM for Android wireless ADB pairing.
 *
 * Mirrors the protocol in AOSP packages/modules/adb/pairing_auth/pairing_auth.cc.
 * The native code lives in app/src/main/cpp/adb_pairing.cpp.
 */
internal class AdbPairingContext private constructor(private val nativePtr: Long) {

    val msg: ByteArray = nativeMsg(nativePtr)

    fun initCipher(theirMsg: ByteArray): Boolean = nativeInitCipher(nativePtr, theirMsg)

    fun encrypt(input: ByteArray): ByteArray? = nativeEncrypt(nativePtr, input)

    fun decrypt(input: ByteArray): ByteArray? = nativeDecrypt(nativePtr, input)

    fun destroy() = nativeDestroy(nativePtr)

    private external fun nativeMsg(ptr: Long): ByteArray
    private external fun nativeInitCipher(ptr: Long, theirMsg: ByteArray): Boolean
    private external fun nativeEncrypt(ptr: Long, input: ByteArray): ByteArray?
    private external fun nativeDecrypt(ptr: Long, input: ByteArray): ByteArray?
    private external fun nativeDestroy(ptr: Long)

    companion object {
        init {
            System.loadLibrary("adb_pairing")
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long

        fun create(password: ByteArray): AdbPairingContext? {
            val ptr = nativeConstructor(true, password)
            return if (ptr != 0L) AdbPairingContext(ptr) else null
        }
    }
}
