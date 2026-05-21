import Foundation

final class VaultFileCodec {
    static let vaultMagic = "Lockroot_VAULT"
    static let exportMagic = "Lockroot_EXPORT"

    private let crypto: CryptoService

    init(crypto: CryptoService = CryptoService()) {
        self.crypto = crypto
    }

    func createSession(vault: Vault, password: String) throws -> (Data, VaultSession) {
        let kdfParams = try crypto.defaultKdfParams()
        var key = try crypto.deriveKey(password: password, params: kdfParams)

        do {
            let encrypted = try encryptVaultWithKey(vault, key: key, kdfParams: kdfParams)
            return (encrypted, VaultSession(vault: vault, key: key, kdfParams: kdfParams))
        } catch {
            crypto.wipe(&key)
            throw error
        }
    }

    func decryptSession(data: Data, password: String) throws -> VaultSession {
        let envelope = try VaultJson.envelopeFromData(data)
        try VaultJson.validate(envelope, expectedMagic: Self.vaultMagic, cipherName: crypto.cipherName)

        var key = try crypto.deriveKey(password: password, params: envelope.kdfParams)
        var plaintext = Data()

        do {
            plaintext = try crypto.decrypt(
                payload: AeadPayload(ciphertext: envelope.ciphertext, tag: envelope.tag),
                key: key,
                nonce: envelope.nonce,
                associatedData: VaultJson.associatedData(envelope)
            )
            let vault = try VaultJson.vaultFromData(plaintext)
            crypto.wipe(&plaintext)
            return VaultSession(vault: vault, key: key, kdfParams: envelope.kdfParams)
        } catch {
            crypto.wipe(&key)
            crypto.wipe(&plaintext)
            throw error
        }
    }

    func encryptVaultWithKey(
        _ vault: Vault,
        key: Data,
        kdfParams: KdfParams,
        magic: String = VaultFileCodec.vaultMagic
    ) throws -> Data {
        var plaintext = try VaultJson.vaultToData(vault)
        let nonce = try crypto.randomBytes(count: CryptoService.nonceBytes)
        let emptyEnvelope = VaultEnvelope(
            magic: magic,
            version: 1,
            kdf: "argon2id",
            kdfParams: kdfParams,
            cipher: crypto.cipherName,
            nonce: nonce,
            ciphertext: Data(),
            tag: Data()
        )

        defer { crypto.wipe(&plaintext) }

        let payload = try crypto.encrypt(
            plaintext: plaintext,
            key: key,
            nonce: nonce,
            associatedData: VaultJson.associatedData(emptyEnvelope)
        )

        return try VaultJson.envelopeToData(
            VaultEnvelope(
                magic: emptyEnvelope.magic,
                version: emptyEnvelope.version,
                kdf: emptyEnvelope.kdf,
                kdfParams: emptyEnvelope.kdfParams,
                cipher: emptyEnvelope.cipher,
                nonce: emptyEnvelope.nonce,
                ciphertext: payload.ciphertext,
                tag: payload.tag
            )
        )
    }

    func encryptVault(_ vault: Vault, password: String, magic: String = VaultFileCodec.vaultMagic) throws -> Data {
        let kdfParams = try crypto.defaultKdfParams()
        var key = try crypto.deriveKey(password: password, params: kdfParams)
        defer { crypto.wipe(&key) }
        return try encryptVaultWithKey(vault, key: key, kdfParams: kdfParams, magic: magic)
    }

    func decryptVault(data: Data, password: String, magic: String = VaultFileCodec.vaultMagic) throws -> Vault {
        let envelope = try VaultJson.envelopeFromData(data)
        try VaultJson.validate(envelope, expectedMagic: magic, cipherName: crypto.cipherName)
        var key = try crypto.deriveKey(password: password, params: envelope.kdfParams)
        var plaintext = Data()

        defer {
            crypto.wipe(&key)
            crypto.wipe(&plaintext)
        }

        plaintext = try crypto.decrypt(
            payload: AeadPayload(ciphertext: envelope.ciphertext, tag: envelope.tag),
            key: key,
            nonce: envelope.nonce,
            associatedData: VaultJson.associatedData(envelope)
        )
        return try VaultJson.vaultFromData(plaintext)
    }
}
