package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.AuthenticationFailedException
import com.regaan.lockroot.crypto.Base64Codec
import com.regaan.lockroot.crypto.CryptoService
import com.regaan.lockroot.crypto.KdfParams
import com.regaan.lockroot.crypto.LegacyAesGcmCipher
import com.regaan.lockroot.crypto.TestAeadCipher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultFileCodecTest {
    private val codec = VaultFileCodec(CryptoService(aeadCipher = TestAeadCipher()))

    @Test
    fun correctPasswordDecryptsVault() {
        val vault = sampleVault()
        val encrypted = codec.encryptVault(vault, "strong master password".toCharArray())

        val decrypted = codec.decryptVault(encrypted, "strong master password".toCharArray())

        assertEquals("GitHub", decrypted.entries.single().title)
        assertEquals("secret-value", decrypted.entries.single().password)
    }

    @Test
    fun wrongPasswordFailsAuthentication() {
        val encrypted = codec.encryptVault(sampleVault(), "strong master password".toCharArray())

        assertThrows(AuthenticationFailedException::class.java) {
            codec.decryptVault(encrypted, "wrong master password".toCharArray())
        }
    }

    @Test
    fun modifiedHeaderFailsAuthentication() {
        val encrypted = codec.encryptVault(sampleVault(), "strong master password".toCharArray())
        val root = JSONObject(String(encrypted))
        val cipher = root.getJSONObject("cipher")
        val nonce = Base64Codec.decode(cipher.getString("nonce"))
        nonce[0] = (nonce[0].toInt() xor 1).toByte()
        cipher.put("nonce", Base64Codec.encode(nonce))

        assertThrows(AuthenticationFailedException::class.java) {
            codec.decryptVault(root.toString().toByteArray(), "strong master password".toCharArray())
        }
    }

    @Test
    fun newVaultsUseXChaCha20Poly1305() {
        val encrypted = codec.encryptVault(sampleVault(), "strong master password".toCharArray())
        val root = JSONObject(String(encrypted))

        assertEquals("xchacha20-poly1305", root.getJSONObject("cipher").getString("name"))
    }

    @Test
    fun legacyAesGcmVaultsRemainReadable() {
        val legacyCodec = VaultFileCodec(CryptoService(aeadCipher = LegacyAesGcmCipher()))
        val encrypted = legacyCodec.encryptVault(sampleVault(), "strong master password".toCharArray())

        val decrypted = codec.decryptVault(encrypted, "strong master password".toCharArray())

        assertEquals("secret-value", decrypted.entries.single().password)
    }

    @Test
    fun oversizedKdfParametersAreRejected() {
        val encrypted = codec.encryptVault(sampleVault(), "strong master password".toCharArray())
        val root = JSONObject(String(encrypted))
        root.getJSONObject("kdf").put("memory", 1_000_000_000)

        assertThrows(IllegalArgumentException::class.java) {
            codec.decryptVault(root.toString().toByteArray(), "strong master password".toCharArray())
        }
    }

    @Test
    fun vaultEnvelopeUsesByteArrayContentEquality() {
        val first = sampleEnvelope(nonce = byteArrayOf(1, 2, 3))
        val second = sampleEnvelope(nonce = byteArrayOf(1, 2, 3))
        val changed = sampleEnvelope(nonce = byteArrayOf(1, 2, 9))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        org.junit.Assert.assertNotEquals(first, changed)
    }

    private fun sampleVault(): Vault = Vault(
        entries = mutableListOf(
            VaultEntry(
                title = "GitHub",
                website = "github.com",
                username = "user@example.com",
                password = "secret-value",
                notes = "private",
                tags = listOf("dev"),
            ),
        ),
    )

    private fun sampleEnvelope(nonce: ByteArray): VaultEnvelope =
        VaultEnvelope(
            magic = VaultFileCodec.VAULT_MAGIC,
            version = 1,
            kdf = "argon2id",
            kdfParams = KdfParams(
                memoryKiB = 19_456,
                iterations = 2,
                parallelism = 1,
                salt = ByteArray(16) { it.toByte() },
            ),
            cipher = "xchacha20-poly1305",
            nonce = nonce,
            ciphertext = byteArrayOf(4, 5, 6),
            tag = byteArrayOf(7, 8, 9),
        )
}
