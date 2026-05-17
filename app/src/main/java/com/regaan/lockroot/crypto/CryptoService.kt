package com.regaan.lockroot.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays

class CryptoService(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val aeadCipher: AeadCipher = LibsodiumXChaCha20Poly1305Cipher(),
) {
    val cipherName: String
        get() = aeadCipher.cipherName

    val nonceBytes: Int
        get() = aeadCipher.nonceBytes

    val tagBytes: Int
        get() = aeadCipher.tagBytes

    fun defaultKdfParams(): KdfParams = KdfParams(
        memoryKiB = DEFAULT_ARGON2_MEMORY_KIB,
        iterations = DEFAULT_ARGON2_ITERATIONS,
        parallelism = DEFAULT_ARGON2_PARALLELISM,
        salt = randomBytes(ARGON2_SALT_BYTES),
    )

    fun randomBytes(size: Int): ByteArray {
        require(size > 0) { "Random byte count must be positive." }
        return ByteArray(size).also { secureRandom.nextBytes(it) }
    }

    fun deriveKey(password: CharArray, params: KdfParams): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty." }

        val passwordBytes = utf8Bytes(password)
        return try {
            val argon2 = Argon2BytesGenerator()
            val argon2Params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(params.salt)
                .build()

            val key = ByteArray(KEY_BYTES)
            argon2.init(argon2Params)
            argon2.generateBytes(passwordBytes, key)
            key
        } finally {
            wipe(passwordBytes)
        }
    }

    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): AeadPayload = aeadCipher.encrypt(plaintext, key, nonce, associatedData)

    fun decrypt(
        payload: AeadPayload,
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray = aeadCipher.decrypt(payload, key, nonce, associatedData)

    fun wipe(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        secureRandom.nextBytes(bytes)
        Arrays.fill(bytes, 0)
        wipeSink = bytes[0].toInt()
    }

    fun wipe(chars: CharArray) {
        if (chars.isEmpty()) return
        Arrays.fill(chars, '\u0000')
        wipeSink = chars[0].code
    }

    private fun utf8Bytes(chars: CharArray): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
        val byteBuffer = encoder.encode(CharBuffer.wrap(chars))
        val output = ByteArray(byteBuffer.remaining())
        byteBuffer.get(output)
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0)
        }
        return output
    }

    companion object {
        const val KEY_BYTES = 32
        const val ARGON2_SALT_BYTES = 32
        const val DEFAULT_ARGON2_MEMORY_KIB = 65_536
        const val DEFAULT_ARGON2_ITERATIONS = 3
        const val DEFAULT_ARGON2_PARALLELISM = 2

        @Volatile
        private var wipeSink: Int = 0
    }
}
