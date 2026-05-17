package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.AeadPayload
import com.regaan.lockroot.crypto.CryptoService
import com.regaan.lockroot.crypto.KdfParams
import com.regaan.lockroot.crypto.LegacyAesGcmCipher

class VaultFileCodec(
    private val crypto: CryptoService = CryptoService(),
) {
    private val legacyAesGcmCrypto = CryptoService(aeadCipher = LegacyAesGcmCipher())
    private val supportedCiphers = setOf(crypto.cipherName, LegacyAesGcmCipher.CIPHER_NAME)

    fun createSession(vault: Vault, password: CharArray): Pair<ByteArray, VaultSession> {
        val kdfParams = crypto.defaultKdfParams()
        val key = crypto.deriveKey(password, kdfParams)
        return try {
            encryptVaultWithKey(vault, key, kdfParams) to VaultSession(vault, key, kdfParams)
        } catch (error: Throwable) {
            crypto.wipe(key)
            throw error
        }
    }

    fun decryptSession(bytes: ByteArray, password: CharArray): VaultSession {
        val envelope = VaultJson.envelopeFromBytes(bytes)
        VaultJson.validateEnvelope(envelope, VAULT_MAGIC, supportedCiphers)
        val key = crypto.deriveKey(password, envelope.kdfParams)
        val decryptCrypto = cryptoFor(envelope.cipher)
        val plaintext = try {
            decryptCrypto.decrypt(
                payload = AeadPayload(envelope.ciphertext, envelope.tag),
                key = key,
                nonce = envelope.nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
        } catch (error: Throwable) {
            crypto.wipe(key)
            throw error
        }

        return try {
            VaultSession(
                vault = VaultJson.vaultFromBytes(plaintext),
                key = key,
                kdfParams = envelope.kdfParams,
            )
        } finally {
            crypto.wipe(plaintext)
        }
    }

    fun encryptVaultWithKey(
        vault: Vault,
        key: ByteArray,
        kdfParams: KdfParams,
        magic: String = VAULT_MAGIC,
    ): ByteArray {
        val plaintext = VaultJson.vaultToBytes(vault)
        val nonce = crypto.randomBytes(crypto.nonceBytes)
        val envelope = VaultEnvelope(
            magic = magic,
            version = 1,
            kdf = "argon2id",
            kdfParams = kdfParams,
            cipher = crypto.cipherName,
            nonce = nonce,
            ciphertext = ByteArray(0),
            tag = ByteArray(0),
        )
        return try {
            val payload = crypto.encrypt(
                plaintext = plaintext,
                key = key,
                nonce = nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
            VaultJson.envelopeToBytes(envelope.copy(ciphertext = payload.ciphertext, tag = payload.tag))
        } finally {
            crypto.wipe(plaintext)
        }
    }

    fun encryptVault(vault: Vault, password: CharArray, magic: String = VAULT_MAGIC): ByteArray {
        val kdfParams = crypto.defaultKdfParams()
        val key = crypto.deriveKey(password, kdfParams)
        return try {
            encryptVaultWithKey(vault, key, kdfParams, magic)
        } finally {
            crypto.wipe(key)
        }
    }

    fun decryptVault(bytes: ByteArray, password: CharArray, magic: String = VAULT_MAGIC): Vault {
        val envelope = VaultJson.envelopeFromBytes(bytes)
        VaultJson.validateEnvelope(envelope, magic, supportedCiphers)
        val key = crypto.deriveKey(password, envelope.kdfParams)
        val decryptCrypto = cryptoFor(envelope.cipher)
        val plaintext = try {
            decryptCrypto.decrypt(
                payload = AeadPayload(envelope.ciphertext, envelope.tag),
                key = key,
                nonce = envelope.nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
        } finally {
            crypto.wipe(key)
        }

        return try {
            VaultJson.vaultFromBytes(plaintext)
        } finally {
            crypto.wipe(plaintext)
        }
    }

    private fun cryptoFor(cipherName: String): CryptoService =
        if (cipherName == LegacyAesGcmCipher.CIPHER_NAME) legacyAesGcmCrypto else crypto

    companion object {
        const val VAULT_MAGIC = "Lockroot_VAULT"
        const val EXPORT_MAGIC = "Lockroot_EXPORT"
    }
}
