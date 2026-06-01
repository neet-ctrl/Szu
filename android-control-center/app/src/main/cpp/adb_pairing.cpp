#include <jni.h>
#include <cstring>
#include <cstdlib>
#include <cinttypes>
#include <openssl/spake2.h>
#include <openssl/hkdf.h>
#include <openssl/evp.h>

#define LOG_TAG "AdbPairClient"
#include "logging.h"

static constexpr spake2_role_t kClientRole = spake2_role_alice;
static constexpr spake2_role_t kServerRole = spake2_role_bob;

static const uint8_t kClientName[] = "adb pair client";
static const uint8_t kServerName[] = "adb pair server";

static constexpr size_t kHkdfKeyLength = 16;

struct PairingContextNative {
    SPAKE2_CTX  *spake2_ctx;
    uint8_t      key[SPAKE2_MAX_MSG_SIZE];
    size_t       key_size;
    EVP_AEAD_CTX *aes_ctx;
    uint64_t     dec_sequence;
    uint64_t     enc_sequence;
};

// ── Constructor ──────────────────────────────────────────────────────────────

static jlong PairingContext_Constructor(JNIEnv *env, jclass /*clazz*/, jboolean isClient, jbyteArray jPassword) {
    spake2_role_t spake_role;
    const uint8_t *my_name, *their_name;
    size_t my_len, their_len;

    if (isClient) {
        spake_role  = kClientRole;
        my_name     = kClientName; my_len     = sizeof(kClientName);
        their_name  = kServerName; their_len  = sizeof(kServerName);
    } else {
        spake_role  = kServerRole;
        my_name     = kServerName; my_len     = sizeof(kServerName);
        their_name  = kClientName; their_len  = sizeof(kClientName);
    }

    auto spake2_ctx = SPAKE2_CTX_new(spake_role, my_name, my_len, their_name, their_len);
    if (!spake2_ctx) {
        LOGE("Unable to create SPAKE2 context.");
        return 0;
    }

    auto pswd_size = (size_t) env->GetArrayLength(jPassword);
    auto pswd      = env->GetByteArrayElements(jPassword, nullptr);

    size_t  key_size = 0;
    uint8_t key[SPAKE2_MAX_MSG_SIZE];
    int status = SPAKE2_generate_msg(spake2_ctx, key, &key_size, SPAKE2_MAX_MSG_SIZE,
                                     reinterpret_cast<uint8_t *>(pswd), pswd_size);
    env->ReleaseByteArrayElements(jPassword, pswd, 0);

    if (status != 1 || key_size == 0) {
        LOGE("Unable to generate SPAKE2 public key.");
        SPAKE2_CTX_free(spake2_ctx);
        return 0;
    }

    auto ctx = reinterpret_cast<PairingContextNative *>(malloc(sizeof(PairingContextNative)));
    memset(ctx, 0, sizeof(PairingContextNative));
    ctx->spake2_ctx = spake2_ctx;
    memcpy(ctx->key, key, key_size);
    ctx->key_size   = key_size;
    return reinterpret_cast<jlong>(ctx);
}

// ── Msg (our SPAKE2 message to send) ─────────────────────────────────────────

static jbyteArray PairingContext_Msg(JNIEnv *env, jobject /*obj*/, jlong ptr) {
    auto ctx     = reinterpret_cast<PairingContextNative *>(ptr);
    auto jOut    = env->NewByteArray(static_cast<jsize>(ctx->key_size));
    env->SetByteArrayRegion(jOut, 0, static_cast<jsize>(ctx->key_size),
                            reinterpret_cast<jbyte *>(ctx->key));
    return jOut;
}

// ── InitCipher (process their message, derive AES-128-GCM key) ───────────────

static jboolean PairingContext_InitCipher(JNIEnv *env, jobject /*obj*/, jlong ptr, jbyteArray jTheirMsg) {
    auto ctx           = reinterpret_cast<PairingContextNative *>(ptr);
    auto their_msg_len = (size_t) env->GetArrayLength(jTheirMsg);

    if (their_msg_len > SPAKE2_MAX_MSG_SIZE) {
        LOGE("their_msg size [%zu] > SPAKE2_MAX_MSG_SIZE [%d].", their_msg_len, SPAKE2_MAX_MSG_SIZE);
        return JNI_FALSE;
    }

    auto their_msg = env->GetByteArrayElements(jTheirMsg, nullptr);

    size_t  key_material_len = 0;
    uint8_t key_material[SPAKE2_MAX_KEY_SIZE];
    int status = SPAKE2_process_msg(ctx->spake2_ctx, key_material, &key_material_len,
                                    sizeof(key_material),
                                    reinterpret_cast<uint8_t *>(their_msg), their_msg_len);
    env->ReleaseByteArrayElements(jTheirMsg, their_msg, 0);

    if (status != 1) {
        LOGE("SPAKE2_process_msg failed — wrong pairing code?");
        return JNI_FALSE;
    }

    uint8_t       aes_key[kHkdfKeyLength];
    const uint8_t info[]  = "adb pairing_auth aes-128-gcm key";
    status = HKDF(aes_key, sizeof(aes_key), EVP_sha256(),
                  key_material, key_material_len,
                  nullptr, 0,
                  info, sizeof(info) - 1);
    if (status != 1) {
        LOGE("HKDF failed.");
        return JNI_FALSE;
    }

    ctx->aes_ctx = EVP_AEAD_CTX_new(EVP_aead_aes_128_gcm(), aes_key, sizeof(aes_key),
                                    EVP_AEAD_DEFAULT_TAG_LENGTH);
    if (!ctx->aes_ctx) {
        LOGE("EVP_AEAD_CTX_new failed.");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// ── Encrypt ──────────────────────────────────────────────────────────────────

static jbyteArray PairingContext_Encrypt(JNIEnv *env, jobject /*obj*/, jlong ptr, jbyteArray jIn) {
    auto ctx     = reinterpret_cast<PairingContextNative *>(ptr);
    auto in      = env->GetByteArrayElements(jIn, nullptr);
    auto in_size = (size_t) env->GetArrayLength(jIn);

    auto nonce_len = EVP_AEAD_nonce_length(EVP_AEAD_CTX_aead(ctx->aes_ctx));
    uint8_t nonce[nonce_len];
    memset(nonce, 0, nonce_len);
    memcpy(nonce, &ctx->enc_sequence, sizeof(ctx->enc_sequence));

    auto    out_size = in_size + EVP_AEAD_max_overhead(EVP_AEAD_CTX_aead(ctx->aes_ctx));
    uint8_t out[out_size];
    size_t  written = 0;

    int ok = EVP_AEAD_CTX_seal(ctx->aes_ctx, out, &written, out_size,
                               nonce, nonce_len,
                               reinterpret_cast<uint8_t *>(in), in_size,
                               nullptr, 0);
    env->ReleaseByteArrayElements(jIn, in, 0);

    if (!ok) {
        LOGE("Encrypt failed.");
        return nullptr;
    }
    ++ctx->enc_sequence;

    auto jOut = env->NewByteArray(static_cast<jsize>(written));
    env->SetByteArrayRegion(jOut, 0, static_cast<jsize>(written), reinterpret_cast<jbyte *>(out));
    return jOut;
}

// ── Decrypt ──────────────────────────────────────────────────────────────────

static jbyteArray PairingContext_Decrypt(JNIEnv *env, jobject /*obj*/, jlong ptr, jbyteArray jIn) {
    auto ctx     = reinterpret_cast<PairingContextNative *>(ptr);
    auto in      = env->GetByteArrayElements(jIn, nullptr);
    auto in_size = (size_t) env->GetArrayLength(jIn);

    auto nonce_len = EVP_AEAD_nonce_length(EVP_AEAD_CTX_aead(ctx->aes_ctx));
    uint8_t nonce[nonce_len];
    memset(nonce, 0, nonce_len);
    memcpy(nonce, &ctx->dec_sequence, sizeof(ctx->dec_sequence));

    uint8_t out[in_size];
    size_t  written = 0;

    int ok = EVP_AEAD_CTX_open(ctx->aes_ctx, out, &written, in_size,
                               nonce, nonce_len,
                               reinterpret_cast<uint8_t *>(in), in_size,
                               nullptr, 0);
    env->ReleaseByteArrayElements(jIn, in, 0);

    if (!ok) {
        LOGE("Decrypt failed — wrong pairing code or corrupted packet.");
        return nullptr;
    }
    ++ctx->dec_sequence;

    auto jOut = env->NewByteArray(static_cast<jsize>(written));
    env->SetByteArrayRegion(jOut, 0, static_cast<jsize>(written), reinterpret_cast<jbyte *>(out));
    return jOut;
}

// ── Destroy ───────────────────────────────────────────────────────────────────

static void PairingContext_Destroy(JNIEnv * /*env*/, jobject /*obj*/, jlong ptr) {
    auto ctx = reinterpret_cast<PairingContextNative *>(ptr);
    SPAKE2_CTX_free(ctx->spake2_ctx);
    if (ctx->aes_ctx) EVP_AEAD_CTX_free(ctx->aes_ctx);
    free(ctx);
}

// ── JNI registration ──────────────────────────────────────────────────────────

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return -1;

    static const JNINativeMethod methods[] = {
        {"nativeConstructor", "(Z[B)J",  reinterpret_cast<void *>(PairingContext_Constructor)},
        {"nativeMsg",         "(J)[B",   reinterpret_cast<void *>(PairingContext_Msg)},
        {"nativeInitCipher",  "(J[B)Z",  reinterpret_cast<void *>(PairingContext_InitCipher)},
        {"nativeEncrypt",     "(J[B)[B", reinterpret_cast<void *>(PairingContext_Encrypt)},
        {"nativeDecrypt",     "(J[B)[B", reinterpret_cast<void *>(PairingContext_Decrypt)},
        {"nativeDestroy",     "(J)V",    reinterpret_cast<void *>(PairingContext_Destroy)},
    };

    jclass cls = env->FindClass("com/accu/connection/AdbPairingContext");
    if (!cls) { LOGE("FindClass(AdbPairingContext) failed"); return -1; }
    env->RegisterNatives(cls, methods, sizeof(methods) / sizeof(methods[0]));
    return JNI_VERSION_1_6;
}
