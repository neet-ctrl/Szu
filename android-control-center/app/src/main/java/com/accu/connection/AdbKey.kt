package com.accu.connection

import android.annotation.SuppressLint
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.conscrypt.Conscrypt
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

    val adbPublicKey: ByteArray by lazy { publicKey.toAdbEncoded(name) }

    // ── Conscrypt TLSv1.3 context — presents our cert, enables exportKeyingMaterial ──

    val sslContext: SSLContext by lazy {
        // Use the shared conscryptProvider (same instance used to parse/generate the key).
        // All three — key, cert, and SSLContext — MUST use the same Conscrypt provider so
        // the OpenSSL-backed private key is recognised when Conscrypt performs RSA-PSS
        // signing for the TLS 1.3 CertificateVerify message.
        val ctx = SSLContext.getInstance("TLSv1.3", conscryptProvider)
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

        // Always present our certificate regardless of the key-type list.
        //
        // Android adbd (TLS 1.3) sends a CertificateRequest whose signature_algorithms
        // extension only includes rsa_pss_pss_sha256 / rsa_pss_rsae_sha256.
        // Conscrypt maps those to key-type "RSASSA-PSS" (not "RSA"), so a strict
        // types.any { it == "RSA" } check returns null and we send no cert.
        // adbd enforces SSL_VERIFY_FAIL_IF_NO_PEER_CERT → sends close_notify →
        // startHandshake() throws SSLException("connection closed").
        //
        // We only ever have one key, so returning alias unconditionally is safe.
        override fun chooseClientAlias(types: Array<out String>?, i: Array<out Principal>?, s: Socket?) = alias
        override fun chooseEngineClientAlias(types: Array<out String>?, i: Array<out Principal>?, e: SSLEngine?) = alias
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
         * Single shared Conscrypt provider used for ALL key and SSL operations.
         *
         * This is the critical piece: when Conscrypt's TLS 1.3 layer creates the
         * CertificateVerify message it needs to perform RSA-PSS signing on the
         * private key.  Conscrypt can only do PSS operations on OpenSSL-backed keys
         * (i.e. keys that were generated or parsed via its own provider).  If we use
         * the default Java provider the key is a plain-Java RSAPrivateKey — Conscrypt
         * fails to sign CertificateVerify, sends an empty Certificate message, adbd
         * enforces SSL_VERIFY_FAIL_IF_NO_PEER_CERT, sends close_notify, and we get:
         *   "TLS handshake failed (connection closed)"
         *
         * Solution: use this shared Conscrypt provider for KeyFactory, KeyPairGenerator
         * AND SSLContext so every RSA key is OpenSSL-backed end-to-end.
         */
        internal val conscryptProvider: Provider by lazy { Conscrypt.newProvider() }

        /**
         * Load from a PKCS8 PEM file written by dadb's [AdbKeyPair.generate].
         * If the file doesn't exist, generates a new 2048-bit RSA key and writes it
         * in PKCS8 PEM format so dadb can also read it.
         *
         * **Always call this AFTER [dadb.AdbKeyPair.generate] has been called**,
         * so both share the exact same underlying RSA key — the one the device
         * authorizes during pairing is then used for all subsequent connections.
         */
        fun fromFile(privFile: File, name: String): AdbKey {
            val priv = if (privFile.exists()) {
                parsePkcs8Pem(privFile)
            } else {
                generateAndSaveToFile(privFile)
            }
            return AdbKey(priv, name)
        }

        /**
         * Parse the PKCS8 PEM using the **Conscrypt provider** so the resulting
         * RSAPrivateKey is OpenSSL-backed.  This is required for RSA-PSS signing
         * in TLS 1.3 CertificateVerify — see [conscryptProvider] doc above.
         */
        private fun parsePkcs8Pem(file: File): RSAPrivateKey {
            val pem = file.readText()
            val b64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val der = Base64.decode(b64, Base64.DEFAULT)
            return KeyFactory.getInstance("RSA", conscryptProvider)
                .generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
        }

        /**
         * Generate a new key pair using the **Conscrypt provider** for the same
         * OpenSSL-backed reason described in [conscryptProvider].
         */
        private fun generateAndSaveToFile(privFile: File): RSAPrivateKey {
            val kpg  = KeyPairGenerator.getInstance("RSA", conscryptProvider)
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
