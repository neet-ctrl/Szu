package com.accu.connection

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.conscrypt.Conscrypt
import java.io.ByteArrayInputStream
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
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

private const val TAG = "AdbKey"

/**
 * Manages the RSA key pair used for ADB wireless pairing and connections.
 *
 * - Private key is generated once, encrypted with an AndroidKeyStore AES key, and stored
 *   in [AdbKeyStore] (typically SharedPreferences).
 * - The X.509 self-signed certificate is derived from the private key at runtime.
 * - [sslContext] is a Conscrypt-backed TLSv1.3 context that presents our cert, enabling
 *   [Conscrypt.exportKeyingMaterial] for the SPAKE2 password derivation.
 * - [adbPublicKey] is the 524-byte ADB wire-format encoding of our RSA public key, used
 *   as the PeerInfo payload during pairing.
 */
class AdbKey(private val adbKeyStore: AdbKeyStore, name: String) {

    companion object {
        private const val ANDROID_KEYSTORE    = "AndroidKeyStore"
        private const val ENCRYPTION_KEY_ALIAS = "_accu_adbkey_enc_"
        private const val TRANSFORMATION       = "AES/GCM/NoPadding"
        private const val IV_SIZE              = 12
        private const val TAG_SIZE             = 16
    }

    private val encryptionKey: Key = getOrCreateEncryptionKey()

    val privateKey: RSAPrivateKey  = getOrCreatePrivateKey()
    val publicKey:  RSAPublicKey   = KeyFactory.getInstance("RSA")
        .generatePublic(RSAPublicKeySpec(privateKey.modulus, RSAKeyGenParameterSpec.F4)) as RSAPublicKey

    val certificate: X509Certificate = run {
        val signer      = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        val x509Builder = X509v3CertificateBuilder(
            X500Name("CN=ACCU"),
            BigInteger.ONE,
            Date(0),
            Date(2_461_449_600L * 1000L),
            Locale.ROOT,
            X500Name("CN=ACCU"),
            SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        )
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(x509Builder.build(signer).encoded)) as X509Certificate
    }

    val adbPublicKey: ByteArray by lazy { publicKey.toAdbEncoded(name) }

    // ── SSLContext ────────────────────────────────────────────────────────────

    val sslContext: SSLContext by lazy {
        val provider   = Conscrypt.newProvider()
        val ctx        = SSLContext.getInstance("TLSv1.3", provider)
        ctx.init(arrayOf(buildKeyManager()), arrayOf(buildTrustManager()), SecureRandom())
        ctx
    }

    // ── Key management ────────────────────────────────────────────────────────

    private fun getOrCreateEncryptionKey(): Key {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        return ks.getKey(ENCRYPTION_KEY_ALIAS, null) ?: run {
            val spec = KeyGenParameterSpec.Builder(
                ENCRYPTION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply { init(spec) }.generateKey()
        }
    }

    private fun encrypt(plaintext: ByteArray, aad: ByteArray?): ByteArray? {
        if (plaintext.size > Int.MAX_VALUE - IV_SIZE - TAG_SIZE) return null
        val ciphertext = ByteArray(IV_SIZE + plaintext.size + TAG_SIZE)
        val cipher     = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        aad?.let { cipher.updateAAD(it) }
        cipher.doFinal(plaintext, 0, plaintext.size, ciphertext, IV_SIZE)
        System.arraycopy(cipher.iv, 0, ciphertext, 0, IV_SIZE)
        return ciphertext
    }

    private fun decrypt(ciphertext: ByteArray, aad: ByteArray?): ByteArray? {
        if (ciphertext.size < IV_SIZE + TAG_SIZE) return null
        val params = GCMParameterSpec(8 * TAG_SIZE, ciphertext, 0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, params)
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext, IV_SIZE, ciphertext.size - IV_SIZE)
    }

    private fun getOrCreatePrivateKey(): RSAPrivateKey {
        val aad  = ByteArray(16).also { "adbkey".toByteArray().copyInto(it) }
        val blob = adbKeyStore.get()
        if (blob != null) {
            try {
                val plaintext = decrypt(blob, aad)
                if (plaintext != null) {
                    return KeyFactory.getInstance("RSA")
                        .generatePrivate(PKCS8EncodedKeySpec(plaintext)) as RSAPrivateKey
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing key, regenerating: ${e.message}")
            }
        }
        val kpg  = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        kpg.initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
        val kp   = kpg.generateKeyPair()
        val priv = kp.private as RSAPrivateKey
        val enc  = encrypt(priv.encoded, aad)
        if (enc != null) adbKeyStore.put(enc)
        return priv
    }

    // ── ADB auth signing ─────────────────────────────────────────────────────

    private val PKCS1_SHA1_PADDING = byteArrayOf(
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
        cipher.update(PKCS1_SHA1_PADDING)
        return cipher.doFinal(token)
    }

    // ── SSL helpers ───────────────────────────────────────────────────────────

    private fun buildKeyManager() = object : X509ExtendedKeyManager() {
        private val alias = "key"
        override fun chooseClientAlias(types: Array<out String>, issuers: Array<out Principal>?, s: Socket?) =
            if (types.any { it == "RSA" }) alias else null
        override fun getCertificateChain(a: String?) = if (a == alias) arrayOf(certificate) else null
        override fun getPrivateKey(a: String?)        = if (a == alias) privateKey else null
        override fun getClientAliases(t: String?, i: Array<out Principal>?) = null
        override fun getServerAliases(t: String,   i: Array<out Principal>?) = null
        override fun chooseServerAlias(t: String,  i: Array<out Principal>?, s: Socket?) = null
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
}

// ── ADB public key wire format (AOSP android_pubkey.c) ───────────────────────

private const val MODULUS_SIZE       = 2048 / 8
private const val MODULUS_SIZE_WORDS = MODULUS_SIZE / 4
private const val RSA_KEY_WIRE_SIZE  = 524

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

    val buf = ByteBuffer.allocate(RSA_KEY_WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(MODULUS_SIZE_WORDS)
    buf.putInt(n0inv.toInt())
    modulus.toAdbWordArray().forEach { buf.putInt(it) }
    rr.toAdbWordArray().forEach     { buf.putInt(it) }
    buf.putInt(publicExponent.toInt())

    val b64      = Base64.encode(buf.array(), Base64.NO_WRAP)
    val namePart = " $name\u0000".toByteArray()
    return b64 + namePart
}

// ── AdbKeyStore ───────────────────────────────────────────────────────────────

interface AdbKeyStore {
    fun put(bytes: ByteArray)
    fun get(): ByteArray?
}

class PreferenceAdbKeyStore(private val prefs: SharedPreferences) : AdbKeyStore {
    private val key = "accu_adbkey_blob"
    override fun put(bytes: ByteArray) = prefs.edit { putString(key, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
    override fun get(): ByteArray? = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
}
