package com.accu.connection

import android.util.Log
import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Android Wireless ADB Pairing client — no `adb` binary, no Shizuku.
 *
 * Implements the exact protocol used by Android's adbd:
 *   - TLSv1.3 with our self-signed RSA cert (Conscrypt)
 *   - SPAKE2 via BoringSSL JNI (password = pairCode + TLS keying material)
 *   - AES-128-GCM encrypted PeerInfo exchange (8192-byte structs)
 *
 * References:
 *   AOSP packages/modules/adb/pairing_auth/pairing_auth.cc
 *   AOSP packages/modules/adb/pairing_connection/pairing_client.cpp
 */
object AdbWifiPairingClient {

    private const val TAG = "AdbWifiPairing"

    private const val VERSION: Byte       = 1
    private const val TYPE_SPAKE2: Byte   = 0
    private const val TYPE_PEER_INFO: Byte = 1

    private const val HEADER_SIZE         = 6          // version(1) + type(1) + payload_len(4)
    private const val MAX_PEER_INFO_SIZE  = 8192
    private const val MAX_PAYLOAD_SIZE    = MAX_PEER_INFO_SIZE * 2

    private const val PEER_TYPE_RSA_KEY: Byte = 0      // ADB_RSA_PUB_KEY

    private const val EXPORT_KEY_LABEL    = "adb-label\u0000"
    private const val EXPORT_KEY_SIZE     = 64

    /**
     * Pair with an Android device's wireless ADB pairing service.
     *
     * @param host          IP address discovered via mDNS (_adb-tls-pairing._tcp)
     * @param port          Pairing port from mDNS
     * @param pairingCode   6-digit code shown in Settings → Developer options → Wireless debugging
     * @param adbKey        Our RSA identity (must be the same key used for subsequent adb connect)
     * @param pubKeyBytes   Raw bytes of the ADB-format public key to register with adbd.
     *                      Pass [dadb.AdbKeyPair.publicKey] (the `.pub` file bytes) so the key
     *                      registered here is IDENTICAL to what adbd will compute from our TLS
     *                      certificate via android_pubkey_encode.  Falls back to
     *                      [AdbKey.adbPublicKey] if null.
     * @return true if pairing succeeded and the device now trusts our key for future connections.
     */
    fun pair(
        host: String,
        port: Int,
        pairingCode: String,
        adbKey: AdbKey,
        pubKeyBytes: ByteArray? = null,
    ): Boolean {
        return try {
            doPair(host, port, pairingCode, adbKey, pubKeyBytes)
        } catch (e: Exception) {
            Timber.e(e, "$TAG pair($host:$port) FAILED: ${e.message?.take(200)}")
            false
        }
    }

    private fun doPair(
        host: String,
        port: Int,
        pairingCode: String,
        adbKey: AdbKey,
        pubKeyBytes: ByteArray? = null,
    ): Boolean {
        // ── 1. TLSv1.3 handshake (Conscrypt, our cert in key manager) ─────────
        val rawSocket  = Socket(host, port).also { it.tcpNoDelay = true }
        val sslSocket  = adbKey.sslContext.socketFactory
            .createSocket(rawSocket, host, port, true) as SSLSocket
        sslSocket.soTimeout = 15_000
        sslSocket.startHandshake()
        Timber.d("$TAG TLS handshake OK with $host:$port")

        val din  = DataInputStream(sslSocket.inputStream)
        val dout = DataOutputStream(sslSocket.outputStream)

        return try {
            // ── 2. Derive SPAKE2 password = pairCode || TLS keying material ───
            val codeBytes    = pairingCode.toByteArray(Charsets.UTF_8)
            // Use com.android.org.conscrypt.Conscrypt.exportKeyingMaterial via reflection —
            // exactly like Shizuku.  HiddenApiBypass (installed in ACCApplication) ensures
            // the method is accessible on Android 9+.
            val keyMaterial  = exportKeyingMaterialSystemConscrypt(sslSocket, EXPORT_KEY_LABEL, EXPORT_KEY_SIZE)
            val password     = codeBytes + keyMaterial

            // ── 3. Create SPAKE2 context (BoringSSL JNI) ─────────────────────
            val pairingCtx = checkNotNull(AdbPairingContext.create(password)) {
                "Failed to create SPAKE2 pairing context"
            }

            try {
                // ── 4. Exchange SPAKE2 messages — CLIENT SENDS FIRST ────────
                writePacket(dout, TYPE_SPAKE2, pairingCtx.msg)
                Timber.d("$TAG sent SPAKE2 msg (${pairingCtx.msg.size} bytes)")

                val theirSpake2 = readPacket(din, TYPE_SPAKE2)
                Timber.d("$TAG received SPAKE2 msg (${theirSpake2.size} bytes)")

                check(pairingCtx.initCipher(theirSpake2)) {
                    "SPAKE2 cipher init failed — wrong pairing code?"
                }
                Timber.d("$TAG SPAKE2 cipher initialised (AES-128-GCM)")

                // ── 5. Exchange PeerInfo — CLIENT SENDS FIRST ────────────────
                // PeerInfo format confirmed from Shizuku AdbPairingClient.kt (line 178):
                //   PeerInfo(ADB_RSA_PUB_KEY, key.adbPublicKey)
                // where adbPublicKey = BASE64(524_raw_bytes) + " name\0"
                // adbd accepts this and writes it to adb_keys.
                val keyToSend = pubKeyBytes ?: adbKey.adbPublicKey
                val peerInfoBuf = ByteBuffer.allocate(MAX_PEER_INFO_SIZE).order(ByteOrder.BIG_ENDIAN)
                peerInfoBuf.put(PEER_TYPE_RSA_KEY)
                peerInfoBuf.put(keyToSend, 0, keyToSend.size.coerceAtMost(MAX_PEER_INFO_SIZE - 1))

                val encrypted = checkNotNull(pairingCtx.encrypt(peerInfoBuf.array())) {
                    "Failed to encrypt PeerInfo"
                }
                writePacket(dout, TYPE_PEER_INFO, encrypted)
                Timber.d("$TAG sent encrypted PeerInfo (${keyToSend.size} key bytes, " +
                    "source=${if (pubKeyBytes != null) "dadb .pub" else "toAdbEncoded"})")

                // ── 6. Read server's PeerInfo — decrypt to verify code ───────
                val theirEncPeerInfo = readPacket(din, TYPE_PEER_INFO)
                val decrypted = pairingCtx.decrypt(theirEncPeerInfo)
                    ?: throw AdbInvalidPairingCodeException()

                check(decrypted.size == MAX_PEER_INFO_SIZE) {
                    "Server PeerInfo size mismatch: got ${decrypted.size}, expected $MAX_PEER_INFO_SIZE"
                }

                Timber.i("$TAG ✓ pairing SUCCEEDED with $host:$port — device trusts our key")
                true

            } finally {
                pairingCtx.destroy()
            }
        } finally {
            runCatching { sslSocket.close() }
            runCatching { rawSocket.close() }
        }
    }

    // ── System Conscrypt exportKeyingMaterial (via reflection, like Shizuku) ─────

    /**
     * Calls com.android.org.conscrypt.Conscrypt.exportKeyingMaterial via reflection.
     *
     * Shizuku calls this directly (they have hidden API stubs + hiddenapibypass).
     * We use reflection + HiddenApiBypass (installed in ACCApplication.onCreate)
     * to achieve the exact same result without compile-time hidden API stubs.
     *
     * The socket MUST be a system Conscrypt socket (created via
     * SSLContext.getInstance("TLSv1.3") with no explicit provider).
     */
    private fun exportKeyingMaterialSystemConscrypt(
        socket: javax.net.ssl.SSLSocket,
        label: String,
        length: Int
    ): ByteArray {
        val clazz  = Class.forName("com.android.org.conscrypt.Conscrypt")
        val method = clazz.getMethod(
            "exportKeyingMaterial",
            javax.net.ssl.SSLSocket::class.java,
            String::class.java,
            ByteArray::class.java,
            Int::class.java
        )
        return method.invoke(null, socket, label, null, length) as ByteArray
    }

    // ── Packet I/O (identical to AOSP pairing_connection) ────────────────────

    private fun writePacket(dout: DataOutputStream, type: Byte, payload: ByteArray) {
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(VERSION)
        buf.put(type)
        buf.putInt(payload.size)
        dout.write(buf.array())
        dout.write(payload)
        dout.flush()
    }

    private fun readPacket(din: DataInputStream, expectedType: Byte): ByteArray {
        val headerBytes = ByteArray(HEADER_SIZE)
        din.readFully(headerBytes)
        val buf     = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get()
        val type    = buf.get()
        val size    = buf.int

        check(version == VERSION)      { "Unexpected packet version: $version (want $VERSION)" }
        check(type == expectedType)    { "Unexpected packet type: $type (want $expectedType)" }
        check(size in 1..MAX_PAYLOAD_SIZE) { "Packet payload size out of range: $size" }

        Log.d(TAG, "read packet type=${type.toInt()} size=$size")
        return ByteArray(size).also { din.readFully(it) }
    }
}

class AdbInvalidPairingCodeException : Exception("ADB pairing failed — wrong code or SPAKE2 auth tag mismatch")
