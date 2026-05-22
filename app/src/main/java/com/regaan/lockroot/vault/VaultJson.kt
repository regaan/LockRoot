package com.regaan.lockroot.vault

import com.regaan.lockroot.crypto.Base64Codec
import com.regaan.lockroot.crypto.KdfParams
import com.regaan.lockroot.crypto.UnsupportedVaultFormatException
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object VaultJson {
    fun vaultToBytes(vault: Vault): ByteArray = vaultToJson(vault).toString().toByteArray(StandardCharsets.UTF_8)

    fun vaultFromBytes(bytes: ByteArray): Vault = vaultFromJson(JSONObject(String(bytes, StandardCharsets.UTF_8)))

    fun envelopeToBytes(envelope: VaultEnvelope): ByteArray {
        val root = JSONObject()
            .put("magic", envelope.magic)
            .put("version", envelope.version)
            .put(
                "kdf",
                JSONObject()
                    .put("name", envelope.kdf)
                    .put("memory", envelope.kdfParams.memoryKiB)
                    .put("iterations", envelope.kdfParams.iterations)
                    .put("parallelism", envelope.kdfParams.parallelism)
                    .put("salt", Base64Codec.encode(envelope.kdfParams.salt)),
            )
            .put(
                "cipher",
                JSONObject()
                    .put("name", envelope.cipher)
                    .put("nonce", Base64Codec.encode(envelope.nonce)),
            )
            .put("ciphertext", Base64Codec.encode(envelope.ciphertext))
            .put("tag", Base64Codec.encode(envelope.tag))

        return root.toString(2).toByteArray(StandardCharsets.UTF_8)
    }

    fun envelopeFromBytes(bytes: ByteArray): VaultEnvelope {
        val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val kdfValue = root.get("kdf")
        val cipherValue = root.get("cipher")

        val kdfName: String
        val kdfParams: KdfParams
        if (kdfValue is JSONObject) {
            kdfName = kdfValue.getString("name")
            kdfParams = KdfParams(
                memoryKiB = kdfValue.getInt("memory"),
                iterations = kdfValue.getInt("iterations"),
                parallelism = kdfValue.getInt("parallelism"),
                salt = Base64Codec.decode(kdfValue.getString("salt")),
            )
        } else {
            val argon2id = root.getJSONObject("argon2id")
            kdfName = kdfValue.toString()
            kdfParams = KdfParams(
                memoryKiB = argon2id.getInt("memory"),
                iterations = argon2id.getInt("iterations"),
                parallelism = argon2id.getInt("parallelism"),
                salt = Base64Codec.decode(argon2id.getString("salt")),
            )
        }

        val cipherName: String
        val nonce: ByteArray
        if (cipherValue is JSONObject) {
            cipherName = cipherValue.getString("name")
            nonce = Base64Codec.decode(cipherValue.getString("nonce"))
        } else {
            cipherName = cipherValue.toString()
            nonce = Base64Codec.decode(root.getString("nonce"))
        }

        return VaultEnvelope(
            magic = root.getString("magic"),
            version = root.getInt("version"),
            kdf = kdfName,
            kdfParams = kdfParams,
            cipher = cipherName,
            nonce = nonce,
            ciphertext = Base64Codec.decode(root.getString("ciphertext")),
            tag = Base64Codec.decode(root.getString("tag")),
        )
    }

    fun associatedData(envelope: VaultEnvelope): ByteArray {
        val text = listOf(
            envelope.magic,
            envelope.version.toString(),
            envelope.kdf,
            envelope.kdfParams.memoryKiB.toString(),
            envelope.kdfParams.iterations.toString(),
            envelope.kdfParams.parallelism.toString(),
            Base64Codec.encode(envelope.kdfParams.salt),
            envelope.cipher,
            Base64Codec.encode(envelope.nonce),
        ).joinToString("|")

        return text.toByteArray(StandardCharsets.UTF_8)
    }

    fun validateEnvelope(
        envelope: VaultEnvelope,
        expectedMagic: String,
        supportedCiphers: Set<String> = setOf("xchacha20-poly1305"),
    ) {
        if (envelope.magic != expectedMagic && !acceptedLegacyMagic(envelope.magic, expectedMagic)) {
            throw UnsupportedVaultFormatException("Unsupported file type.")
        }
        if (envelope.version !in setOf(1, VaultFileCodec.CURRENT_VERSION)) {
            throw UnsupportedVaultFormatException("Unsupported vault version.")
        }
        if (envelope.kdf != "argon2id") {
            throw UnsupportedVaultFormatException("Unsupported KDF.")
        }
        if (envelope.cipher !in supportedCiphers) {
            throw UnsupportedVaultFormatException("Unsupported cipher.")
        }
    }

    private fun acceptedLegacyMagic(actualMagic: String, expectedMagic: String): Boolean =
        (expectedMagic == VaultFileCodec.VAULT_MAGIC && actualMagic == "LOCKROOT") ||
            (expectedMagic == VaultFileCodec.EXPORT_MAGIC && actualMagic == "LOCKROOT-EXPORT")

    private fun vaultToJson(vault: Vault): JSONObject {
        val entries = JSONArray()
        vault.entries.forEach { entry ->
            entries.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("website", entry.website)
                    .put("username", entry.username)
                    .put("password", entry.password)
                    .put("notes", entry.notes)
                    .put("tags", JSONArray(entry.tags)),
            )
        }

        return JSONObject()
            .put("vaultId", vault.vaultId)
            .put("schemaVersion", vault.schemaVersion)
            .put("entries", entries)
    }

    private fun vaultFromJson(root: JSONObject): Vault {
        val entries = mutableListOf<VaultEntry>()
        val rawEntries = root.getJSONArray("entries")
        for (index in 0 until rawEntries.length()) {
            val raw = rawEntries.getJSONObject(index)
            val tags = raw.optJSONArray("tags")
            entries.add(
                VaultEntry(
                    id = raw.getString("id"),
                    title = raw.getString("title"),
                    website = raw.optString("website"),
                    username = raw.optString("username"),
                    password = raw.optString("password"),
                    notes = raw.optString("notes"),
                    tags = (0 until (tags?.length() ?: 0)).map { tags!!.getString(it) },
                ),
            )
        }

        return Vault(
            vaultId = root.getString("vaultId"),
            schemaVersion = root.getInt("schemaVersion"),
            entries = entries,
        )
    }
}
