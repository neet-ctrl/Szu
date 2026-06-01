package com.accu.connection

import timber.log.Timber
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Minimal ADB-over-TLS client for Android 11+ Wireless ADB session connections.
 *
 * After SPAKE2 pairing has registered our RSA key, the session port requires mTLS.
 * [dadb.Dadb.create()] only speaks plain TCP ADB — it cannot connect to this TLS
 * port and will fail with garbled TLS bytes parsed as bad ADB messages.
 *
 * This class opens a TLS socket (same [AdbKey.sslContext] used during pairing),
 * runs the ADB CNXN / AUTH handshake over TLS, and exposes [shell] for commands.
 *
 * Protocol reference: AOSP packages/modules/adb/adb.h
 */
class AdbWifiConnectClient private constructor(
    private val adbKey: AdbKey,
) : AutoCloseable {

    private var rawSocket: Socket? = null
    private var sslSocket: SSLSocket? = null
    private lateinit var din: DataInputStream
    private lateinit var dout: DataOutputStream

    companion object {
        private const val TAG = "AdbWifiConnect"

        private const val A_CNXN = 0x4e584e43
        private const val A_AUTH = 0x48545541
        private const val A_OPEN = 0x4e45504f
        private const val A_OKAY = 0x59414b4f
        private const val A_CLSE = 0x45534c43
        private const val A_WRTE = 0x45545257

        private const val AUTH_TOKEN     = 1
        private const val AUTH_SIGNATURE = 2

        private const val ADB_VERSION  = 0x01000000
        private const val MAX_DATA_LEN = 256 * 1024

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val SO_TIMEOUT_MS      = 20_000

        private const val CONN_STRING =
            "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex"

        /**
         * Connect to an Android 11+ wireless ADB session port over TLS.
         *
         * The [adbKey] must be the SAME key that was used during SPAKE2 pairing —
         * the device validates the mTLS client certificate against its trusted key list.
         *
         * @throws Exception if the TLS handshake or ADB CNXN exchange fails.
         */
        fun connect(host: String, port: Int, adbKey: AdbKey): AdbWifiConnectClient {
            val client = AdbWifiConnectClient(adbKey)
            client.open(host, port)
            return client
        }
    }

    private fun open(host: String, port: Int) {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        raw.tcpNoDelay = true
        rawSocket = raw

        val ssl = adbKey.sslContext.socketFactory
            .createSocket(raw, host, port, /*autoClose=*/ true) as SSLSocket
        ssl.soTimeout = SO_TIMEOUT_MS
        ssl.useClientMode = true
        // adbd requires TLS 1.3 on the session port (Android 11+).
        // Explicitly restrict to TLS 1.3 to prevent a downgrade negotiation that
        // adbd would reject, which also causes "connection closed" / EOF.
        ssl.enabledProtocols = arrayOf("TLSv1.3")

        try {
            ssl.startHandshake()
        } catch (e: Exception) {
            // Wrap with a clear message so the UI shows "TLS handshake failed"
            // rather than a cryptic Conscrypt internal message.
            // Common causes:
            //  - "connection closed" → adbd sent close_notify; usually means no client
            //    cert was sent (chooseClientAlias returned null for "RSASSA-PSS" key type)
            //  - "Handshake failed" → cipher mismatch
            throw IllegalStateException("TLS handshake failed (${e.message}). " +
                "Check that this device ran a fresh pair before connecting.", e)
        }
        sslSocket = ssl
        Timber.i("$TAG TLS handshake OK → $host:$port  " +
            "protocol=${ssl.session.protocol}  suite=${ssl.session.cipherSuite}")

        din  = DataInputStream(ssl.inputStream)
        dout = DataOutputStream(ssl.outputStream)

        try {
            doAdbHandshake()
        } catch (e: Exception) {
            throw IllegalStateException("ADB CNXN handshake failed (TLS was OK): ${e.message}", e)
        }
        Timber.i("$TAG ADB CNXN handshake complete ✓")
    }

    private fun doAdbHandshake() {
        writeMsg(A_CNXN, ADB_VERSION, MAX_DATA_LEN, CONN_STRING.toByteArray(Charsets.UTF_8))

        repeat(6) {
            val msg = readMsg()
            when (msg.cmd) {
                A_CNXN -> return
                A_AUTH -> {
                    check(msg.arg0 == AUTH_TOKEN) { "Unexpected AUTH type ${msg.arg0}" }
                    Timber.d("$TAG AUTH_TOKEN received — signing")
                    writeMsg(A_AUTH, AUTH_SIGNATURE, 0, adbKey.sign(msg.data))
                }
                else -> Timber.w("$TAG handshake: ignored cmd=0x${msg.cmd.toString(16)}")
            }
        }
        throw IllegalStateException("ADB CNXN not received after AUTH exchanges")
    }

    /**
     * Execute a shell command on the remote device.
     * Returns combined stdout + stderr as a trimmed string.
     * Thread-safe: synchronized on [dout] for writes.
     */
    fun shell(command: String): String {
        val localId = (System.nanoTime() and 0x7FFF_FFFFL).toInt().coerceAtLeast(1)
        writeMsg(A_OPEN, localId, 0, "shell:$command".toByteArray(Charsets.UTF_8))

        var remoteId = 0
        val output   = StringBuilder()
        var open     = true

        while (open) {
            val msg = readMsg()
            when (msg.cmd) {
                A_OKAY -> if (msg.arg1 == localId) remoteId = msg.arg0
                A_WRTE -> if (msg.arg1 == localId) {
                    output.append(String(msg.data, Charsets.UTF_8))
                    writeMsg(A_OKAY, localId, msg.arg0, ByteArray(0))
                }
                A_CLSE -> if (msg.arg1 == localId) {
                    if (remoteId > 0) writeMsg(A_CLSE, localId, remoteId, ByteArray(0))
                    open = false
                }
                else -> Timber.d("$TAG shell: cmd=0x${msg.cmd.toString(16)}")
            }
        }
        return output.toString().trimEnd('\n')
    }

    override fun close() {
        try { sslSocket?.close() } catch (_: Exception) {}
        try { rawSocket?.close() } catch (_: Exception) {}
        sslSocket = null
        rawSocket = null
    }

    private data class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun writeMsg(cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val check = data.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        val hdr = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(cmd); putInt(arg0); putInt(arg1)
            putInt(data.size); putInt(check); putInt(cmd.inv())
        }
        synchronized(dout) {
            dout.write(hdr.array())
            if (data.isNotEmpty()) dout.write(data)
            dout.flush()
        }
    }

    private fun readMsg(): Msg {
        val hdr = ByteArray(24).also { din.readFully(it) }
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.int; val arg0 = buf.int; val arg1 = buf.int
        val len = buf.int; buf.int; buf.int
        val data = if (len > 0) ByteArray(len).also { din.readFully(it) } else ByteArray(0)
        return Msg(cmd, arg0, arg1, data)
    }
}
