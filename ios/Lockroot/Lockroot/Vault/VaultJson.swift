import Foundation

enum VaultJson {
    private struct EnvelopeDTO: Codable {
        struct KdfDTO: Codable {
            let name: String
            let memory: Int
            let iterations: Int
            let parallelism: Int
            let salt: String
        }

        struct CipherDTO: Codable {
            let name: String
            let nonce: String
        }

        let magic: String
        let version: Int
        let kdf: KdfDTO
        let cipher: CipherDTO
        let ciphertext: String
        let tag: String
    }

    static func vaultToData(_ vault: Vault) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        return try encoder.encode(vault)
    }

    static func vaultFromData(_ data: Data) throws -> Vault {
        try JSONDecoder().decode(Vault.self, from: data)
    }

    static func envelopeToData(_ envelope: VaultEnvelope) throws -> Data {
        let dto = EnvelopeDTO(
            magic: envelope.magic,
            version: envelope.version,
            kdf: .init(
                name: envelope.kdf,
                memory: envelope.kdfParams.memoryKiB,
                iterations: envelope.kdfParams.iterations,
                parallelism: envelope.kdfParams.parallelism,
                salt: envelope.kdfParams.salt.base64EncodedString()
            ),
            cipher: .init(
                name: envelope.cipher,
                nonce: envelope.nonce.base64EncodedString()
            ),
            ciphertext: envelope.ciphertext.base64EncodedString(),
            tag: envelope.tag.base64EncodedString()
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(dto)
    }

    static func envelopeFromData(_ data: Data) throws -> VaultEnvelope {
        let dto = try JSONDecoder().decode(EnvelopeDTO.self, from: data)
        guard
            let salt = Data(base64Encoded: dto.kdf.salt),
            let nonce = Data(base64Encoded: dto.cipher.nonce),
            let ciphertext = Data(base64Encoded: dto.ciphertext),
            let tag = Data(base64Encoded: dto.tag)
        else {
            throw CryptoError.invalidCiphertext
        }

        return VaultEnvelope(
            magic: dto.magic,
            version: dto.version,
            kdf: dto.kdf.name,
            kdfParams: KdfParams(
                memoryKiB: dto.kdf.memory,
                iterations: dto.kdf.iterations,
                parallelism: dto.kdf.parallelism,
                salt: salt
            ),
            cipher: dto.cipher.name,
            nonce: nonce,
            ciphertext: ciphertext,
            tag: tag
        )
    }

    static func associatedData(_ envelope: VaultEnvelope) -> Data {
        [
            envelope.magic,
            String(envelope.version),
            envelope.kdf,
            String(envelope.kdfParams.memoryKiB),
            String(envelope.kdfParams.iterations),
            String(envelope.kdfParams.parallelism),
            envelope.kdfParams.salt.base64EncodedString(),
            envelope.cipher,
            envelope.nonce.base64EncodedString(),
        ]
        .joined(separator: "|")
        .data(using: .utf8) ?? Data()
    }

    static func validate(_ envelope: VaultEnvelope, expectedMagic: String, cipherName: String) throws {
        guard envelope.magic == expectedMagic else {
            throw CryptoError.unsupportedFormat("Unsupported file type.")
        }
        guard envelope.version == 1 else {
            throw CryptoError.unsupportedFormat("Unsupported vault version.")
        }
        guard envelope.kdf == "argon2id" else {
            throw CryptoError.unsupportedFormat("Unsupported KDF.")
        }
        guard (19_456...262_144).contains(envelope.kdfParams.memoryKiB) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id memory cost.")
        }
        guard (2...10).contains(envelope.kdfParams.iterations) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id iteration count.")
        }
        guard (1...8).contains(envelope.kdfParams.parallelism) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id parallelism.")
        }
        guard (16...64).contains(envelope.kdfParams.salt.count) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id salt length.")
        }
        guard envelope.cipher == cipherName else {
            throw CryptoError.unsupportedFormat("Unsupported cipher.")
        }
    }
}
