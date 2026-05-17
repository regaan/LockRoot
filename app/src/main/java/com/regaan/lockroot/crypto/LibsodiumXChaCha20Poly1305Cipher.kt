package com.regaan.lockroot.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD

class LibsodiumXChaCha20Poly1305Cipher(
    private val lazySodium: LazySodiumAndroid = LazySodiumAndroid(SodiumAndroid()),
) : AeadCipher {
    override val cipherName: String = "xchacha20-poly1305"
    override val nonceBytes: Int = AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES
    override val tagBytes: Int = AEAD.XCHACHA20POLY1305_IETF_ABYTES

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): AeadPayload {
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES) {
            "XChaCha20-Poly1305 key must be 32 bytes."
        }
        require(nonce.size == nonceBytes) {
            "XChaCha20-Poly1305 nonce must be 24 bytes."
        }

        val ciphertext = ByteArray(plaintext.size)
        val tag = ByteArray(tagBytes)
        val tagLength = LongArray(1)

        val success = lazySodium.cryptoAeadXChaCha20Poly1305IetfEncryptDetached(
            ciphertext,
            tag,
            tagLength,
            plaintext,
            plaintext.size.toLong(),
            associatedData,
            associatedData.size.toLong(),
            null,
            nonce,
            key,
        )

        if (!success || tagLength[0].toInt() != tagBytes) {
            throw AuthenticationFailedException()
        }

        return AeadPayload(ciphertext, tag)
    }

    override fun decrypt(
        payload: AeadPayload,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES) {
            "XChaCha20-Poly1305 key must be 32 bytes."
        }
        require(nonce.size == nonceBytes) {
            "XChaCha20-Poly1305 nonce must be 24 bytes."
        }
        require(payload.tag.size == tagBytes) {
            "XChaCha20-Poly1305 tag must be 16 bytes."
        }

        val plaintext = ByteArray(payload.ciphertext.size)
        val success = lazySodium.cryptoAeadXChaCha20Poly1305IetfDecryptDetached(
            plaintext,
            null,
            payload.ciphertext,
            payload.ciphertext.size.toLong(),
            payload.tag,
            associatedData,
            associatedData.size.toLong(),
            nonce,
            key,
        )

        if (!success) {
            throw AuthenticationFailedException()
        }

        return plaintext
    }
}
