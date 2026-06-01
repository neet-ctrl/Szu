---
name: ADB TLS CertificateVerify fix
description: Why Conscrypt must parse/generate the RSA private key using its own provider for wireless ADB TLS to work.
---

## Rule

`AdbKey.parsePkcs8Pem()` and `AdbKey.generateAndSaveToFile()` MUST use
`KeyFactory.getInstance("RSA", conscryptProvider)` and
`KeyPairGenerator.getInstance("RSA", conscryptProvider)`.
The `AdbKey.sslContext` MUST use the **same** shared `conscryptProvider` instance.

## Why

Android adbd (wireless ADB, port `_adb-tls-connect._tcp`) enforces mTLS with
`SSL_VERIFY_FAIL_IF_NO_PEER_CERT`. TLS 1.3 CertificateVerify requires RSA-PSS
signing (`rsa_pss_rsae_sha256` or `rsa_pss_pss_sha256`).

Conscrypt's JNI layer can only do RSA-PSS with **OpenSSL-backed** private keys
(keys produced by Conscrypt's own provider). If the key is a plain Java
`RSAPrivateKey` (from the default provider), Conscrypt fails to sign
CertificateVerify, sends an empty Certificate message, and adbd sends
`close_notify` → `SSLException("connection closed")` → the misleading error:

  "TLS handshake failed (connection closed). Check that this device ran a fresh pair."

Pairing TLS always succeeds (adbd does NOT check the client cert against
authorized keys during pairing, only during connection), hiding the real cause.

## How to apply

- `parsePkcs8Pem`: `KeyFactory.getInstance("RSA", conscryptProvider)`
- `generateAndSaveToFile`: `KeyPairGenerator.getInstance("RSA", conscryptProvider)`
- `sslContext` lazy: `SSLContext.getInstance("TLSv1.3", conscryptProvider)`
- `conscryptProvider` is a companion object `lazy { Conscrypt.newProvider() }`
- File format (PKCS8 PEM) doesn't change — backward compatible, no re-pairing needed.

The `chooseClientAlias` fix (returning alias unconditionally regardless of key type)
is ALSO needed (added by previous agent) — without it, no cert is sent at all.
Both fixes are required together.
