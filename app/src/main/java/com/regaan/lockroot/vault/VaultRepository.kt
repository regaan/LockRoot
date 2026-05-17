package com.regaan.lockroot.vault

import java.util.UUID

class VaultRepository(
    private val storage: EncryptedVaultStore,
    private val codec: VaultFileCodec = VaultFileCodec(),
) {
    private var session: VaultSession? = null

    val unlockedVault: Vault?
        get() = session?.vault

    fun hasVault(): Boolean = storage.exists()

    fun create(password: CharArray): Vault {
        val vault = Vault()
        val (encrypted, newSession) = codec.createSession(vault, password)
        try {
            storage.write(encrypted)
            session?.clear()
            session = newSession
        } catch (error: Throwable) {
            newSession.clear()
            throw error
        }
        return vault
    }

    fun unlock(password: CharArray): Vault {
        val newSession = codec.decryptSession(storage.read(), password)
        session?.clear()
        session = newSession
        return newSession.vault
    }

    fun save() {
        val current = requireNotNull(session) { "Vault is locked." }
        val encrypted = codec.encryptVaultWithKey(current.vault, current.key, current.kdfParams)
        storage.write(encrypted)
    }

    fun changeMasterPassword(currentPassword: CharArray, newPassword: CharArray) {
        requireNotNull(session) { "Vault is locked." }

        val verifiedSession = codec.decryptSession(storage.read(), currentPassword)
        verifiedSession.clear()

        val vault = requireNotNull(session?.vault) { "Vault is locked." }.copyMutable()
        val (encrypted, newSession) = codec.createSession(vault, newPassword)
        try {
            storage.write(encrypted)
            session?.clear()
            session = newSession
        } catch (error: Throwable) {
            newSession.clear()
            throw error
        }
    }

    fun exportUnlocked(exportPassword: CharArray): ByteArray {
        val vault = requireNotNull(session?.vault) { "Vault is locked." }
        return codec.encryptVault(vault.copyMutable(), exportPassword, VaultFileCodec.EXPORT_MAGIC)
    }

    fun decryptExport(bytes: ByteArray, exportPassword: CharArray): Vault =
        codec.decryptVault(bytes, exportPassword, VaultFileCodec.EXPORT_MAGIC)

    fun replaceUnlocked(vault: Vault) {
        val current = requireNotNull(session) { "Vault is locked." }
        current.vault = vault
    }

    fun mergeUnlocked(imported: Vault): Int {
        val current = requireNotNull(session?.vault) { "Vault is locked." }
        val existingIds = current.entries.map { it.id }.toMutableSet()
        imported.entries.forEach { importedEntry ->
            val entry = if (existingIds.contains(importedEntry.id)) {
                importedEntry.copy(
                    id = UUID.randomUUID().toString(),
                    title = "${importedEntry.title} imported",
                )
            } else {
                importedEntry.copy()
            }
            existingIds.add(entry.id)
            current.entries.add(entry)
        }
        return imported.entries.size
    }

    fun lock() {
        session?.clear()
        session = null
    }
}
