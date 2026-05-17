package com.regaan.lockroot.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoServiceTest {
    private val crypto = CryptoService(aeadCipher = TestAeadCipher())

    @Test
    fun decryptsWithCorrectKeyAndAssociatedData() {
        val params = testKdfParams()
        val key = crypto.deriveKey("correct horse battery".toCharArray(), params)
        val nonce = ByteArray(crypto.nonceBytes) { index -> (index + 1).toByte() }
        val aad = "header-v1".toByteArray()
        val plaintext = "secret vault payload".toByteArray()

        val encrypted = crypto.encrypt(plaintext, key, nonce, aad)
        val decrypted = crypto.decrypt(encrypted, key, nonce, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun wrongAssociatedDataFailsAuthentication() {
        val params = testKdfParams()
        val key = crypto.deriveKey("correct horse battery".toCharArray(), params)
        val nonce = ByteArray(crypto.nonceBytes) { index -> (index + 2).toByte() }
        val encrypted = crypto.encrypt("secret".toByteArray(), key, nonce, "header-v1".toByteArray())

        assertThrows(AuthenticationFailedException::class.java) {
            crypto.decrypt(encrypted, key, nonce, "header-v2".toByteArray())
        }
    }

    @Test
    fun wrongKeyFailsAuthentication() {
        val nonce = ByteArray(crypto.nonceBytes) { index -> (index + 3).toByte() }
        val correctKey = crypto.deriveKey("correct horse battery".toCharArray(), testKdfParams(1))
        val wrongKey = crypto.deriveKey("wrong horse battery".toCharArray(), testKdfParams(1))
        val encrypted = crypto.encrypt("secret".toByteArray(), correctKey, nonce, "header".toByteArray())

        assertThrows(AuthenticationFailedException::class.java) {
            crypto.decrypt(encrypted, wrongKey, nonce, "header".toByteArray())
        }
    }

    @Test
    fun byteArrayValueObjectsUseContentEquality() {
        assertEquals(
            AeadPayload(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            AeadPayload(byteArrayOf(1, 2), byteArrayOf(3, 4)),
        )
        assertNotEquals(
            AeadPayload(byteArrayOf(1, 2), byteArrayOf(3, 4)),
            AeadPayload(byteArrayOf(1, 9), byteArrayOf(3, 4)),
        )

        assertEquals(testKdfParams(7), testKdfParams(7))
        assertNotEquals(testKdfParams(7), testKdfParams(8))
    }

    private fun testKdfParams(seed: Int = 0): KdfParams = KdfParams(
        memoryKiB = 19_456,
        iterations = 2,
        parallelism = 1,
        salt = ByteArray(16) { index -> (index + seed).toByte() },
    )
}
