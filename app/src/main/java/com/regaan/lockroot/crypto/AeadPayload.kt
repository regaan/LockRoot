package com.regaan.lockroot.crypto

data class AeadPayload(
    val ciphertext: ByteArray,
    val tag: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is AeadPayload &&
            ciphertext.contentEquals(other.ciphertext) &&
            tag.contentEquals(other.tag)

    override fun hashCode(): Int =
        31 * ciphertext.contentHashCode() + tag.contentHashCode()
}
