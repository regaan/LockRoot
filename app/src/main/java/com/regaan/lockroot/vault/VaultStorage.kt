package com.regaan.lockroot.vault

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class VaultStorage(context: Context) : EncryptedVaultStore {
    private val vaultFile: File = File(context.filesDir, VAULT_FILE_NAME)

    override fun exists(): Boolean = vaultFile.exists() && vaultFile.length() > 0L

    override fun read(): ByteArray = vaultFile.readBytes()

    override fun write(bytes: ByteArray) {
        val tempFile = File(vaultFile.parentFile, "$VAULT_FILE_NAME.tmp")
        FileOutputStream(tempFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }

        try {
            Files.move(
                tempFile.toPath(),
                vaultFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            if (!tempFile.renameTo(vaultFile)) {
                tempFile.copyTo(vaultFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    companion object {
        private const val VAULT_FILE_NAME = "Lockroot.vault.json"
    }
}
