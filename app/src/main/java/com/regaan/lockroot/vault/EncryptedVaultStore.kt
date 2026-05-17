package com.regaan.lockroot.vault

interface EncryptedVaultStore {
    fun exists(): Boolean
    fun read(): ByteArray
    fun write(bytes: ByteArray)
}
