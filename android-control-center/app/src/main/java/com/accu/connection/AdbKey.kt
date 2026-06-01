package com.accu.connection

import android.annotation.SuppressLint
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * RSA identity for ADB wireless pairing and connections.
 *
 * Wraps the SAME private key that dadb writes to [accu_adb_key] (PKCS8 PEM).
 * Both worlds share one key:
 *   - [AdbKey]      → TLS client cert (Conscrypt) + SPAKE2 PeerInfo payload
 *   - dadb AdbKeyPair → ADB AUTH challenge/response signing during connect
 *
 * Use [fromFile] to construct — it reads dadb's existing key file or generates
 * a new one if the file doesn't exist yet.
 */
class AdbKey private constructor(
    val privateKey: RSAPrivateKey,
    name: String
) {

    val publicKey: RSAPublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(RSAPublicKeySpec(privateKey.modulus, RSAKeyGenParameterSpec.F4)) as RSAPublicKey

    val certificate: X509Certificate = buildCert(privateKey, publicKey)

    /**
     * ADB public key in the format adbd expects for BOTH:
     *   • PeerInfo.data during SPAKE2 pairing  (confirmed from Shizuku AdbPairingClient.kt)
     *   • A_AUTH ADB_AUTH_RSAPUBLICKEY payload  (traditional ADB auth path)
     *   • Storage in /data/misc/adb/adb_keys    (file format on device)
     *
     * Format: BASE64(524_raw_bytes) + " " + name + "\0"
     *
     * Shizuku AdbPairingClient line 178:
     *   PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.adbPublicKey)
     * This is the same base64+name format — confirming it is correct for PeerInfo.
     */
    val adbPublicKey: ByteArray by lazy { publicKey.toAdbEncoded(name) }

    /**
     * Raw 524-byte binary of the ADB RSA public key struct (no base64, no name suffix).
     *
     * NOT used in normal flow — kept for diagnostic/debugging purposes.
     * PeerInfo uses [adbPublicKey] (base64+name), not raw bytes.
     */
    val adbPublicKeyRaw: ByteArray by lazy { publicKey.toAdbRaw() }

    // ── TLSv1.3 context — system Conscrypt (com.android.org.conscrypt), exactly like Shizuku ──

    val sslContext: SSLContext by lazy {
        // SSLContext.getInstance("TLSv1.3") with no provider = Android system Conscrypt
        // (com.android.org.conscrypt).  This is exactly what Shizuku's AdbKey does.
        // System Conscrypt knows how to sign CertificateVerify with a plain Java RSAPrivateKey.
        val ctx = SSLContext.getInstance("TLSv1.3")
        ctx.init(arrayOf(buildKeyManager()), arrayOf(buildTrustManager()), SecureRandom())
        ctx
    }

    // ── ADB AUTH signing (RSA/ECB/NoPadding with PKCS#1 SHA-1 padding prefix) ──

    private val PKCS1_SHA1_PAD = byteArrayOf(
        0x00, 0x01, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0x00,
        0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00,
        0x04, 0x14
    )

    fun sign(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        cipher.update(PKCS1_SHA1_PAD)
        return cipher.doFinal(token)
    }

    // ── SSL helpers ───────────────────────────────────────────────────────────

    private fun buildKeyManager() = object : X509ExtendedKeyManager() {
        private val alias = "key"

        // Return our alias only for "RSA" key type — exactly like Shizuku AdbKey.kt line 189:
        //   for (keyType in keyTypes) { if (keyType == "RSA") return alias }
        // System Conscrypt (com.android.org.conscrypt) reports the CertificateRequest
        // key type as "RSA", so this check succeeds and we send our client cert.
        override fun chooseClientAlias(types: Array<out String>?, i: Array<out Principal>?, s: Socket?): String? {
            types?.forEach { if (it == "RSA") return alias }
            return null
        }
        override fun chooseEngineClientAlias(types: Array<out String>?, i: Array<out Principal>?, e: SSLEngine?): String? {
            types?.forEach { if (it == "RSA") return alias }
            return null
        }
        override fun getCertificateChain(a: String?) = if (a == alias) arrayOf(certificate) else null
        override fun getPrivateKey(a: String?)        = if (a == alias) privateKey else null
        override fun getClientAliases(t: String?, i: Array<out Principal>?) = arrayOf(alias)
        override fun getServerAliases(t: String, i: Array<out Principal>?)  = null
        override fun chooseServerAlias(t: String, i: Array<out Principal>?, s: Socket?) = null
    }

    @SuppressLint("TrustAllX509TrustManager")
    private fun buildTrustManager() = object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?, s: Socket?)   {}
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?, e: SSLEngine?) {}
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?)               {}
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?, s: Socket?)   {}
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?, e: SSLEngine?) {}
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?)               {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {

        /**
         * Load from a PKCS8 PEM file.
         * Uses the default KeyFactory (system provider — same as Shizuku's approach).
         * System Conscrypt handles RSA-PSS signing during TLS 1.3 CertificateVerify
         * with a plain Java RSAPrivateKey — proven by Shizuku working on Android 11+.
         */
        fun fromFile(privFile: File, name: String): AdbKey {
            val priv = if (privFile.exists()) parsePkcs8Pem(privFile)
                       else generateAndSaveToFile(privFile)
            return AdbKey(priv, name)
        }

        // Matches Shizuku: KeyFactory.getInstance("RSA") — no explicit provider.
        private fun parsePkcs8Pem(file: File): RSAPrivateKey {
            val pem = file.readText()
            val b64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val der = Base64.decode(b64, Base64.DEFAULT)
            return KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
        }

        // Matches Shizuku: KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA) — no explicit provider.
        private fun generateAndSaveToFile(privFile: File): RSAPrivateKey {
            val kpg  = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            val kp   = kpg.generateKeyPair()
            val priv = kp.private as RSAPrivateKey
            val pem  = buildString {
                appendLine("-----BEGIN PRIVATE KEY-----")
                appendLine(Base64.encodeToString(priv.encoded, Base64.DEFAULT).trim())
                append("-----END PRIVATE KEY-----")
            }
            privFile.writeText(pem)
            return priv
        }

        private fun buildCert(priv: RSAPrivateKey, pub: RSAPublicKey): X509Certificate {
            val signer  = JcaContentSignerBuilder("SHA256withRSA").build(priv)
            val builder = X509v3CertificateBuilder(
                X500Name("CN=ACCU"), BigInteger.ONE,
                Date(0), Date(2_461_449_600L * 1000L),
                Locale.ROOT, X500Name("CN=ACCU"),
                SubjectPublicKeyInfo.getInstance(pub.encoded)
            )
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(builder.build(signer).encoded)) as X509Certificate
        }
    }
}

// ── ADB public key wire format (AOSP android_pubkey.c) ───────────────────────

private const val MODULUS_SIZE       = 2048 / 8
private const val MODULUS_SIZE_WORDS = MODULUS_SIZE / 4

/**
 * Raw 524-byte binary struct matching AOSP android_pubkey_encode() output.
 *
 * struct RSAPublicKey {
 *   uint32_t modulus_size_words;   // = 64 (little-endian)
 *   uint32_t n0inv;                // -1/n[0] mod 2^32 (little-endian)
 *   uint32_t n[64];                // modulus, 64 little-endian words
 *   uint32_t rr[64];               // R^2 mod N (montgomery), 64 words
 *   uint32_t exponent;             // = 65537 (little-endian)
 * };  // total = 4+4+256+256+4 = 524 bytes
 *
 * This is what adbd expects in PeerInfo.data during SPAKE2 pairing.
 * adbd then base64-encodes it and writes to adb_keys.
 * On TLS connect, adbd encodes the cert's public key the same way and compares.
 */
private fun RSAPublicKey.toAdbRaw(): ByteArray {
    val r32   = BigInteger.ZERO.setBit(32)
    val n0inv = modulus.remainder(r32).modInverse(r32).negate()
    val r     = BigInteger.ZERO.setBit(MODULUS_SIZE * 8)
    val rr    = r.modPow(BigInteger.valueOf(2), modulus)
    return ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(MODULUS_SIZE_WORDS)
        putInt(n0inv.toInt())
        modulus.toAdbWordArray().forEach { putInt(it) }
        rr.toAdbWordArray().forEach     { putInt(it) }
        putInt(publicExponent.toInt())
    }.array()
}

private fun BigInteger.toAdbWordArray(): IntArray {
    val words = IntArray(MODULUS_SIZE_WORDS)
    val r32   = BigInteger.ZERO.setBit(32)
    var tmp   = this
    for (i in 0 until MODULUS_SIZE_WORDS) {
        val (q, r) = tmp.divideAndRemainder(r32)
        words[i]   = r.toInt()
        tmp         = q
    }
    return words
}

private fun RSAPublicKey.toAdbEncoded(name: String): ByteArray {
    val r32    = BigInteger.ZERO.setBit(32)
    val n0inv  = modulus.remainder(r32).modInverse(r32).negate()
    val r      = BigInteger.ZERO.setBit(MODULUS_SIZE * 8)
    val rr     = r.modPow(BigInteger.valueOf(2), modulus)

    val buf = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(MODULUS_SIZE_WORDS)
    buf.putInt(n0inv.toInt())
    modulus.toAdbWordArray().forEach { buf.putInt(it) }
    rr.toAdbWordArray().forEach     { buf.putInt(it) }
    buf.putInt(publicExponent.toInt())

    val b64      = Base64.encode(buf.array(), Base64.NO_WRAP)
    val namePart = " $name\u0000".toByteArray()
    return b64 + namePart
}
