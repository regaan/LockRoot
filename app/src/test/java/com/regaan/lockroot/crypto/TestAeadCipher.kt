package com.regaan.lockroot.crypto

import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class TestAeadCipher : AeadCipher {
    override val cipherName: String = "xchacha20-poly1305"
    override val nonceBytes: Int = 24
    override val tagBytes: Int = 16

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): AeadPayload {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce.copyOf(12)))
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
        val combined = payload.ciphertext + payload.tag
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce.copyOf(12)))
            cipher.updateAAD(associatedData)
            cipher.doFinal(combined)
        } catch (error: AEADBadTagException) {
            throw AuthenticationFailedException(error)
        } catch (error: GeneralSecurityException) {
            throw AuthenticationFailedException(error)
        }
    }

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
