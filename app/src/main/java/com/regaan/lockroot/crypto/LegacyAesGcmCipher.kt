package com.regaan.lockroot.crypto

import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LegacyAesGcmCipher : AeadCipher {
    override val cipherName: String = CIPHER_NAME
    override val nonceBytes: Int = 12
    override val tagBytes: Int = 16

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): AeadPayload {
        require(key.size == CryptoService.KEY_BYTES) { "AES-256-GCM key must be 32 bytes." }
        require(nonce.size == nonceBytes) { "AES-GCM nonce must be 12 bytes." }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_BITS, nonce))
        cipher.updateAAD(associatedData)

        val combined = cipher.doFinal(plaintext)
        val splitAt = combined.size - tagBytes
        return AeadPayload(
            ciphertext = combined.copyOfRange(0, splitAt),
            tag = combined.copyOfRange(splitAt, combined.size),
        )
    }

    override fun decrypt(
        payload: AeadPayload,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        require(key.size == CryptoService.KEY_BYTES) { "AES-256-GCM key must be 32 bytes." }
        require(nonce.size == nonceBytes) { "AES-GCM nonce must be 12 bytes." }
        require(payload.tag.size == tagBytes) { "AES-GCM tag must be 16 bytes." }

        val combined = payload.ciphertext + payload.tag
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_BITS, nonce))
            cipher.updateAAD(associatedData)
            cipher.doFinal(combined)
        } catch (error: AEADBadTagException) {
            throw AuthenticationFailedException(error)
        } catch (error: GeneralSecurityException) {
            throw AuthenticationFailedException(error)
        }
    }

    companion object {
        const val CIPHER_NAME = "aes-256-gcm"
        private const val AES_GCM_TAG_BITS = 128
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
