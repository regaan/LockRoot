package com.regaan.lockroot.vault

import java.util.UUID

data class Vault(
    val vaultId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = 1,
    val entries: MutableList<VaultEntry> = mutableListOf(),
) {
    fun copyMutable(): Vault = copy(entries = entries.map { it.copy() }.toMutableList())
}

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var website: String,
    var username: String,
    var password: String,
    var notes: String,
    var tags: List<String> = emptyList(),
)
