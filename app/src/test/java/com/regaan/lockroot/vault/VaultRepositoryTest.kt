package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.AuthenticationFailedException
import com.regaan.lockroot.crypto.Base64Codec
import com.regaan.lockroot.crypto.CryptoService
import com.regaan.lockroot.crypto.LegacyAesGcmCipher
import com.regaan.lockroot.crypto.TestAeadCipher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRepositoryTest {
    @Test
    fun changeMasterPasswordRequiresCurrentPassword() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        repository.create("old strong master".toCharArray())

        assertThrows(AuthenticationFailedException::class.java) {
            repository.changeMasterPassword(
                currentPassword = "wrong strong master".toCharArray(),
                newPassword = "new strong master".toCharArray(),
            )
        }
    }

    @Test
    fun changeMasterPasswordRekeysVault() {
        val store = InMemoryStore()
        val repository = VaultRepository(store, testCodec())
        val vault = repository.create("old strong master".toCharArray())
        vault.entries.add(
            VaultEntry(
                title = "GitHub",
                website = "github.com",
                username = "user@example.com",
                password = "secret-value",
                notes = "private",
            ),
        )
        repository.save()

        repository.changeMasterPassword(
            currentPassword = "old strong master".toCharArray(),
            newPassword = "new strong master".toCharArray(),
        )
        repository.lock()

        assertThrows(AuthenticationFailedException::class.java) {
            repository.unlock("old strong master".toCharArray())
        }

        val unlocked = repository.unlock("new strong master".toCharArray())
        assertEquals("secret-value", unlocked.entries.single().password)
        assertTrue(store.exists())
    }

    @Test
    fun exportUsesSeparatePassword() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        repository.create("master password value".toCharArray())

        val export = repository.exportUnlocked("export password value".toCharArray())

        assertThrows(AuthenticationFailedException::class.java) {
            repository.decryptExport(export, "master password value".toCharArray())
        }
    }

    @Test
    fun exportUsesPortableAesGcmEnvelope() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        repository.create("master password value".toCharArray())

        val export = repository.exportUnlocked("export password value".toCharArray())
        val root = JSONObject(String(export))

        assertEquals(VaultFileCodec.EXPORT_MAGIC, root.getString("magic"))
        assertEquals("argon2id", root.getJSONObject("kdf").getString("name"))
        assertEquals("aes-256-gcm", root.getJSONObject("cipher").getString("name"))
        assertEquals(12, Base64Codec.decode(root.getJSONObject("cipher").getString("nonce")).size)
    }

    @Test
    fun exportRejectsWrongExportPassword() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        repository.create("master password value".toCharArray())

        val export = repository.exportUnlocked("export password value".toCharArray())

        assertThrows(AuthenticationFailedException::class.java) {
            repository.decryptExport(export, "wrong export password".toCharArray())
        }
    }

    @Test
    fun decryptExportWithCorrectPasswordReturnsPreviewableVault() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        val vault = repository.create("master password value".toCharArray())
        vault.entries.add(
            VaultEntry(
                title = "Imported GitHub",
                website = "github.com",
                username = "user@example.com",
                password = "secret-value",
                notes = "private",
            ),
        )

        val export = repository.exportUnlocked("export password value".toCharArray())
        val decrypted = repository.decryptExport(export, "export password value".toCharArray())

        assertEquals("Imported GitHub", decrypted.entries.single().title)
    }

    @Test
    fun mergeUnlockedAddsImportedEntriesAndRenamesDuplicateIds() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        val current = repository.create("master password value".toCharArray())
        val duplicateId = "same-id"
        current.entries.add(
            VaultEntry(
                id = duplicateId,
                title = "Current GitHub",
                website = "github.com",
                username = "current",
                password = "current-secret",
                notes = "",
            ),
        )

        val imported = Vault(
            entries = mutableListOf(
                VaultEntry(
                    id = duplicateId,
                    title = "Imported GitHub",
                    website = "github.com",
                    username = "imported",
                    password = "imported-secret",
                    notes = "",
                ),
            ),
        )

        val importedCount = repository.mergeUnlocked(imported)

        assertEquals(1, importedCount)
        assertEquals(2, current.entries.size)
        assertEquals("Imported GitHub imported", current.entries.last().title)
        assertNotEquals(duplicateId, current.entries.last().id)
    }

    @Test
    fun replaceUnlockedSwapsCurrentVaultForImportedVault() {
        val repository = VaultRepository(InMemoryStore(), testCodec())
        repository.create("master password value".toCharArray()).entries.add(
            VaultEntry(
                title = "Current",
                website = "",
                username = "",
                password = "current-secret",
                notes = "",
            ),
        )

        repository.replaceUnlocked(
            Vault(
                entries = mutableListOf(
                    VaultEntry(
                        title = "Imported",
                        website = "",
                        username = "",
                        password = "imported-secret",
                        notes = "",
                    ),
                ),
            ),
        )

        assertEquals("Imported", repository.unlockedVault!!.entries.single().title)
    }

    @Test
    fun replacedImportedVaultIsSavedToDisk() {
        val store = InMemoryStore()
        val repository = VaultRepository(store, testCodec())
        repository.create("master password value".toCharArray()).entries.add(
            VaultEntry(
                title = "Current",
                website = "",
                username = "",
                password = "current-secret",
                notes = "",
            ),
        )

        repository.replaceUnlocked(
            Vault(
                entries = mutableListOf(
                    VaultEntry(
                        title = "Imported",
                        website = "",
                        username = "",
                        password = "imported-secret",
                        notes = "",
                    ),
                ),
            ),
        )
        repository.save()
        repository.lock()

        val unlocked = repository.unlock("master password value".toCharArray())

        assertEquals("Imported", unlocked.entries.single().title)
        assertEquals("imported-secret", unlocked.entries.single().password)
    }

    @Test
    fun v1AesGcmVaultIsMigratedToPortableV2OnUnlock() {
        val store = InMemoryStore()
        val legacyCodec = VaultFileCodec(
            writeCrypto = CryptoService(aeadCipher = LegacyAesGcmCipher()),
            xChaChaCrypto = CryptoService(aeadCipher = TestAeadCipher()),
        )
        val legacyVault = Vault(
            entries = mutableListOf(
                VaultEntry(
                    title = "Legacy",
                    website = "",
                    username = "",
                    password = "legacy-secret",
                    notes = "",
                ),
            ),
        )
        store.write(
            legacyCodec.encryptVault(
                legacyVault,
                "master password value".toCharArray(),
                version = 1,
            ),
        )

        val repository = VaultRepository(store, testCodec())
        repository.unlock("master password value".toCharArray())

        val root = JSONObject(String(store.read()))

        assertEquals(2, root.getInt("version"))
        assertEquals("aes-256-gcm", root.getJSONObject("cipher").getString("name"))
        assertEquals(12, Base64Codec.decode(root.getJSONObject("cipher").getString("nonce")).size)
    }

    private class InMemoryStore : EncryptedVaultStore {
        private var bytes: ByteArray? = null

        override fun exists(): Boolean = bytes?.isNotEmpty() == true

        override fun read(): ByteArray = bytes?.copyOf() ?: error("No vault stored.")

        override fun write(bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }
    }

    private fun testCodec(): VaultFileCodec = VaultFileCodec(
        writeCrypto = CryptoService(aeadCipher = LegacyAesGcmCipher()),
        xChaChaCrypto = CryptoService(aeadCipher = TestAeadCipher()),
    )
}
