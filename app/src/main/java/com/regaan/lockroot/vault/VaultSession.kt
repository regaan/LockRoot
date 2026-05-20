package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.KdfParams
import java.security.SecureRandom
import java.util.Arrays

class VaultSession(
    var vault: Vault,
    val key: ByteArray,
    val kdfParams: KdfParams,
) {
    fun clear() {
        secureRandom.nextBytes(key)
        Arrays.fill(key, 0)
        wipeSink = key.firstOrNull()?.toInt() ?: 0
    }

    companion object {
        private val secureRandom = SecureRandom()

        @Volatile
        private var wipeSink: Int = 0
    }
}
