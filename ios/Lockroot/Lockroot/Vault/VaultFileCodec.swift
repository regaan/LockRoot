import Foundation

final class VaultFileCodec {
    static let vaultMagic = "Lockroot_VAULT"
    static let exportMagic = "Lockroot_EXPORT"
    static let currentVersion = 2

    private let crypto: CryptoService
    private var supportedCipherNames: Set<String> {
        [crypto.cipherName, crypto.portableCipherName]
    }

    init(crypto: CryptoService = CryptoService()) {
        self.crypto = crypto
    }

    func createSession(vault: Vault, password: String) throws -> (Data, VaultSession) {
        try createSession(vault: vault, passwordData: Data(password.utf8))
    }

    func createSession(vault: Vault, passwordData: Data) throws -> (Data, VaultSession) {
        let kdfParams = try crypto.defaultKdfParams()
        var key = try crypto.deriveKey(passwordData: passwordData, params: kdfParams)

        do {
            let encrypted = try encryptVaultWithKey(vault, key: key, kdfParams: kdfParams)
            return (encrypted, VaultSession(vault: vault, key: key, kdfParams: kdfParams))
        } catch {
            crypto.wipe(&key)
            throw error
        }
    }

    func decryptSession(data: Data, password: String) throws -> VaultSession {
        try decryptSession(data: data, passwordData: Data(password.utf8))
    }

    func decryptSession(data: Data, passwordData: Data) throws -> VaultSession {
        let envelope = try VaultJson.envelopeFromData(data)
        try VaultJson.validate(envelope, expectedMagic: Self.vaultMagic, supportedCipherNames: supportedCipherNames)

        var key = try crypto.deriveKey(passwordData: passwordData, params: envelope.kdfParams)
        var plaintext = Data()

        do {
            plaintext = try decryptEnvelope(envelope, key: key)
            let vault = try VaultJson.vaultFromData(plaintext)
            crypto.wipe(&plaintext)
            return VaultSession(vault: vault, key: key, kdfParams: envelope.kdfParams)
        } catch {
            crypto.wipe(&key)
            crypto.wipe(&plaintext)
            throw error
        }
    }

    func needsMigration(data: Data, expectedMagic: String = VaultFileCodec.vaultMagic) throws -> Bool {
        let envelope = try VaultJson.envelopeFromData(data)
        return envelope.magic != expectedMagic ||
            envelope.version != Self.currentVersion ||
            envelope.cipher != crypto.portableCipherName
    }

    func encryptVaultWithKey(
        _ vault: Vault,
        key: Data,
        kdfParams: KdfParams,
        magic: String = VaultFileCodec.vaultMagic,
        cipherName: String? = nil,
        version: Int = VaultFileCodec.currentVersion
    ) throws -> Data {
        var plaintext = try VaultJson.vaultToData(vault)
        let selectedCipher = cipherName ?? crypto.portableCipherName
        let nonceSize = selectedCipher == crypto.portableCipherName ? CryptoService.aesGcmNonceBytes : CryptoService.nonceBytes
        let nonce = try crypto.randomBytes(count: nonceSize)
        let emptyEnvelope = VaultEnvelope(
            magic: magic,
            version: version,
            kdf: "argon2id",
            kdfParams: kdfParams,
            cipher: selectedCipher,
            nonce: nonce,
            ciphertext: Data(),
            tag: Data()
        )

        defer { crypto.wipe(&plaintext) }

        let payload = try encryptPlaintext(plaintext, envelope: emptyEnvelope, key: key)

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

    func encryptVault(
        _ vault: Vault,
        password: String,
        magic: String = VaultFileCodec.vaultMagic,
        version: Int = VaultFileCodec.currentVersion
    ) throws -> Data {
        try encryptVault(vault, passwordData: Data(password.utf8), magic: magic, version: version)
    }

    func encryptVault(
        _ vault: Vault,
        passwordData: Data,
        magic: String = VaultFileCodec.vaultMagic,
        version: Int = VaultFileCodec.currentVersion
    ) throws -> Data {
        let kdfParams = try crypto.defaultKdfParams()
        var key = try crypto.deriveKey(passwordData: passwordData, params: kdfParams)
        defer { crypto.wipe(&key) }
        return try encryptVaultWithKey(
            vault,
            key: key,
            kdfParams: kdfParams,
            magic: magic,
            cipherName: crypto.portableCipherName,
            version: version
        )
    }

    func decryptVault(data: Data, password: String, magic: String = VaultFileCodec.vaultMagic) throws -> Vault {
        try decryptVault(data: data, passwordData: Data(password.utf8), magic: magic)
    }

    func decryptVault(data: Data, passwordData: Data, magic: String = VaultFileCodec.vaultMagic) throws -> Vault {
        let envelope = try VaultJson.envelopeFromData(data)
        try VaultJson.validate(envelope, expectedMagic: magic, supportedCipherNames: supportedCipherNames)
        var key = try crypto.deriveKey(passwordData: passwordData, params: envelope.kdfParams)
        var plaintext = Data()

        defer {
            crypto.wipe(&key)
            crypto.wipe(&plaintext)
        }

        plaintext = try decryptEnvelope(envelope, key: key)
        return try VaultJson.vaultFromData(plaintext)
    }

    private func encryptPlaintext(_ plaintext: Data, envelope: VaultEnvelope, key: Data) throws -> AeadPayload {
        let associatedData = VaultJson.associatedData(envelope)
        if envelope.cipher == crypto.portableCipherName {
            return try crypto.encryptAesGcm(
                plaintext: plaintext,
                key: key,
                nonce: envelope.nonce,
                associatedData: associatedData
            )
        }

        return try crypto.encrypt(
            plaintext: plaintext,
            key: key,
            nonce: envelope.nonce,
            associatedData: associatedData
        )
    }

    private func decryptEnvelope(_ envelope: VaultEnvelope, key: Data) throws -> Data {
        let payload = AeadPayload(ciphertext: envelope.ciphertext, tag: envelope.tag)
        let associatedData = VaultJson.associatedData(envelope)
        if envelope.cipher == crypto.portableCipherName {
            return try crypto.decryptAesGcm(
                payload: payload,
                key: key,
                nonce: envelope.nonce,
                associatedData: associatedData
            )
        }

        return try crypto.decrypt(
            payload: payload,
            key: key,
            nonce: envelope.nonce,
            associatedData: associatedData
        )
    }
}
