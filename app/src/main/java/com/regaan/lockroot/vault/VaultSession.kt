package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.KdfParams
import java.util.Arrays

class VaultSession(
    var vault: Vault,
    val key: ByteArray,
    val kdfParams: KdfParams,
) {
    fun clear() {
        Arrays.fill(key, 0)
    }
}
