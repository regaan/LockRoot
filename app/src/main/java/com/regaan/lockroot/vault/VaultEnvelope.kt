package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.KdfParams

data class VaultEnvelope(
    val magic: String,
    val version: Int,
    val kdf: String,
    val kdfParams: KdfParams,
    val cipher: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is VaultEnvelope &&
            magic == other.magic &&
            version == other.version &&
            kdf == other.kdf &&
            kdfParams == other.kdfParams &&
            cipher == other.cipher &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext) &&
            tag.contentEquals(other.tag)

    override fun hashCode(): Int {
        var result = magic.hashCode()
        result = 31 * result + version
        result = 31 * result + kdf.hashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + cipher.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}
