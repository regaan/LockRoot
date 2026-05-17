package com.regaan.lockroot.crypto

interface AeadCipher {
    val cipherName: String
    val nonceBytes: Int
    val tagBytes: Int

    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): AeadPayload

    fun decrypt(
        payload: AeadPayload,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray
}
