package com.regaan.lockroot.crypto

data class KdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray,
) {
    init {
        require(memoryKiB >= 19_456) { "Argon2id memory is too low." }
        require(memoryKiB <= 262_144) { "Argon2id memory is too high." }
        require(iterations >= 2) { "Argon2id iterations are too low." }
        require(iterations <= 10) { "Argon2id iterations are too high." }
        require(parallelism >= 1) { "Argon2id parallelism must be positive." }
        require(parallelism <= 8) { "Argon2id parallelism is too high." }
        require(salt.size >= 16) { "Argon2id salt must be at least 16 bytes." }
        require(salt.size <= 64) { "Argon2id salt is too large." }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is KdfParams &&
            memoryKiB == other.memoryKiB &&
            iterations == other.iterations &&
            parallelism == other.parallelism &&
            salt.contentEquals(other.salt)

    override fun hashCode(): Int {
        var result = memoryKiB
        result = 31 * result + iterations
        result = 31 * result + parallelism
        result = 31 * result + salt.contentHashCode()
        return result
    }
}
