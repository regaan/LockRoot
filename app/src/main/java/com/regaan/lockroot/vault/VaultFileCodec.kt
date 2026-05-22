package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.AeadPayload
import com.regaan.lockroot.crypto.CryptoService
import com.regaan.lockroot.crypto.KdfParams
import com.regaan.lockroot.crypto.LegacyAesGcmCipher

class VaultFileCodec(
    private val writeCrypto: CryptoService = CryptoService(aeadCipher = LegacyAesGcmCipher()),
    private val xChaChaCrypto: CryptoService = CryptoService(),
) {
    private val supportedCiphers = setOf(writeCrypto.cipherName, xChaChaCrypto.cipherName)

    fun createSession(vault: Vault, password: CharArray): Pair<ByteArray, VaultSession> {
        val kdfParams = writeCrypto.defaultKdfParams()
        val key = writeCrypto.deriveKey(password, kdfParams)
        return try {
            encryptVaultWithKey(vault, key, kdfParams) to VaultSession(vault, key, kdfParams)
        } catch (error: Throwable) {
            writeCrypto.wipe(key)
            throw error
        }
    }

    fun decryptSession(bytes: ByteArray, password: CharArray): VaultSession {
        val envelope = VaultJson.envelopeFromBytes(bytes)
        VaultJson.validateEnvelope(envelope, VAULT_MAGIC, supportedCiphers)
        val key = writeCrypto.deriveKey(password, envelope.kdfParams)
        val decryptCrypto = cryptoFor(envelope.cipher)
        val plaintext = try {
            decryptCrypto.decrypt(
                payload = AeadPayload(envelope.ciphertext, envelope.tag),
                key = key,
                nonce = envelope.nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
        } catch (error: Throwable) {
            writeCrypto.wipe(key)
            throw error
        }

        return try {
            VaultSession(
                vault = VaultJson.vaultFromBytes(plaintext),
                key = key,
                kdfParams = envelope.kdfParams,
            )
        } finally {
            writeCrypto.wipe(plaintext)
        }
    }

    fun encryptVaultWithKey(
        vault: Vault,
        key: ByteArray,
        kdfParams: KdfParams,
        magic: String = VAULT_MAGIC,
        version: Int = CURRENT_VERSION,
    ): ByteArray {
        val plaintext = VaultJson.vaultToBytes(vault)
        val nonce = writeCrypto.randomBytes(writeCrypto.nonceBytes)
        val envelope = VaultEnvelope(
            magic = magic,
            version = version,
            kdf = "argon2id",
            kdfParams = kdfParams,
            cipher = writeCrypto.cipherName,
            nonce = nonce,
            ciphertext = ByteArray(0),
            tag = ByteArray(0),
        )
        return try {
            val payload = writeCrypto.encrypt(
                plaintext = plaintext,
                key = key,
                nonce = nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
            VaultJson.envelopeToBytes(envelope.copy(ciphertext = payload.ciphertext, tag = payload.tag))
        } finally {
            writeCrypto.wipe(plaintext)
        }
    }

    fun encryptVault(
        vault: Vault,
        password: CharArray,
        magic: String = VAULT_MAGIC,
        version: Int = CURRENT_VERSION,
    ): ByteArray {
        val kdfParams = writeCrypto.defaultKdfParams()
        val key = writeCrypto.deriveKey(password, kdfParams)
        return try {
            encryptVaultWithKey(vault, key, kdfParams, magic, version)
        } finally {
            writeCrypto.wipe(key)
        }
    }

    fun decryptVault(bytes: ByteArray, password: CharArray, magic: String = VAULT_MAGIC): Vault {
        val envelope = VaultJson.envelopeFromBytes(bytes)
        VaultJson.validateEnvelope(envelope, magic, supportedCiphers)
        val key = writeCrypto.deriveKey(password, envelope.kdfParams)
        val decryptCrypto = cryptoFor(envelope.cipher)
        val plaintext = try {
            decryptCrypto.decrypt(
                payload = AeadPayload(envelope.ciphertext, envelope.tag),
                key = key,
                nonce = envelope.nonce,
                associatedData = VaultJson.associatedData(envelope),
            )
        } finally {
            writeCrypto.wipe(key)
        }

        return try {
            VaultJson.vaultFromBytes(plaintext)
        } finally {
            writeCrypto.wipe(plaintext)
        }
    }

    fun needsMigration(bytes: ByteArray, expectedMagic: String = VAULT_MAGIC): Boolean {
        val envelope = VaultJson.envelopeFromBytes(bytes)
        return envelope.magic != expectedMagic ||
            envelope.version != CURRENT_VERSION ||
            envelope.cipher != writeCrypto.cipherName
    }

    private fun cryptoFor(cipherName: String): CryptoService =
        if (cipherName == LegacyAesGcmCipher.CIPHER_NAME) writeCrypto else xChaChaCrypto

    companion object {
        const val VAULT_MAGIC = "Lockroot_VAULT"
        const val EXPORT_MAGIC = "Lockroot_EXPORT"
        const val CURRENT_VERSION = 2
    }
}
