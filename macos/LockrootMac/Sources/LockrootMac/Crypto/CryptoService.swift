import Foundation
import Clibsodium
import Argon2Swift

final class CryptoService {
    static let keyBytes = 32
    static let saltBytes = 32
    static let nonceBytes = 24
    static let tagBytes = 16

    let cipherName = "xchacha20-poly1305"

    init() {
        _ = sodium_init()
    }

    func defaultKdfParams() throws -> KdfParams {
        try KdfParams(
            memoryKiB: 65_536,
            iterations: 3,
            parallelism: 2,
            salt: try randomBytes(count: Self.saltBytes)
        )
    }

    func randomBytes(count: Int) throws -> Data {
        guard count > 0 else { return Data() }
        var data = Data(count: count)
        try data.withUnsafeMutableBytes { raw in
            guard let baseAddress = raw.baseAddress else {
                throw CryptoError.randomFailure
            }
            randombytes_buf(baseAddress, count)
        }
        return data
    }

    func deriveKey(password: String, params: KdfParams) throws -> Data {
        guard !password.isEmpty else { throw CryptoError.invalidPassword }
        guard params.salt.count == Self.saltBytes else {
            throw CryptoError.unsupportedFormat("Invalid Argon2id salt length.")
        }

        var passwordData = Data(password.utf8)
        defer { wipe(&passwordData) }

        do {
            let result = try Argon2Swift.hashPasswordBytes(
                password: passwordData,
                salt: Salt(bytes: params.salt),
                iterations: params.iterations,
                memory: params.memoryKiB,
                parallelism: params.parallelism,
                length: Self.keyBytes,
                type: .id,
                version: .V13
            )
            let key = result.hashData()
            guard key.count == Self.keyBytes else {
                throw CryptoError.unsupportedFormat("Invalid Argon2id output length.")
            }
            return key
        } catch {
            throw CryptoError.authenticationFailed
        }
    }

    func encrypt(plaintext: Data, key: Data, nonce: Data, associatedData: Data) throws -> AeadPayload {
        guard key.count == Self.keyBytes else {
            throw CryptoError.unsupportedFormat("XChaCha20-Poly1305 key must be 32 bytes.")
        }
        guard nonce.count == Self.nonceBytes else {
            throw CryptoError.unsupportedFormat("XChaCha20-Poly1305 nonce must be 24 bytes.")
        }

        var ciphertext = Data(count: plaintext.count)
        var tag = Data(count: Self.tagBytes)
        var tagLength: UInt64 = 0

        let status = try ciphertext.withUnsafeMutableBytes { ciphertextRaw -> Int32 in
            try tag.withUnsafeMutableBytes { tagRaw -> Int32 in
                try plaintext.withUnsafeBytes { plaintextRaw -> Int32 in
                    try associatedData.withUnsafeBytes { adRaw -> Int32 in
                        try nonce.withUnsafeBytes { nonceRaw -> Int32 in
                            try key.withUnsafeBytes { keyRaw -> Int32 in
                                guard
                                    let ciphertextPointer = ciphertextRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let tagPointer = tagRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let plaintextPointer = plaintextRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let associatedDataPointer = adRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let noncePointer = nonceRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let keyPointer = keyRaw.bindMemory(to: UInt8.self).baseAddress
                                else {
                                    throw CryptoError.invalidCiphertext
                                }

                                return crypto_aead_xchacha20poly1305_ietf_encrypt_detached(
                                    ciphertextPointer,
                                    tagPointer,
                                    &tagLength,
                                    plaintextPointer,
                                    UInt64(plaintext.count),
                                    associatedDataPointer,
                                    UInt64(associatedData.count),
                                    nil,
                                    noncePointer,
                                    keyPointer
                                )
                            }
                        }
                    }
                }
            }
        }

        guard status == 0, tagLength == UInt64(Self.tagBytes) else {
            throw CryptoError.authenticationFailed
        }
        return AeadPayload(ciphertext: ciphertext, tag: tag)
    }

    func decrypt(payload: AeadPayload, key: Data, nonce: Data, associatedData: Data) throws -> Data {
        guard key.count == Self.keyBytes else {
            throw CryptoError.unsupportedFormat("XChaCha20-Poly1305 key must be 32 bytes.")
        }
        guard nonce.count == Self.nonceBytes else {
            throw CryptoError.unsupportedFormat("XChaCha20-Poly1305 nonce must be 24 bytes.")
        }
        guard payload.tag.count == Self.tagBytes else {
            throw CryptoError.invalidCiphertext
        }

        var plaintext = Data(count: payload.ciphertext.count)
        let status = try plaintext.withUnsafeMutableBytes { plaintextRaw -> Int32 in
            try payload.ciphertext.withUnsafeBytes { ciphertextRaw -> Int32 in
                try payload.tag.withUnsafeBytes { tagRaw -> Int32 in
                    try associatedData.withUnsafeBytes { adRaw -> Int32 in
                        try nonce.withUnsafeBytes { nonceRaw -> Int32 in
                            try key.withUnsafeBytes { keyRaw -> Int32 in
                                guard
                                    let plaintextPointer = plaintextRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let ciphertextPointer = ciphertextRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let tagPointer = tagRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let associatedDataPointer = adRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let noncePointer = nonceRaw.bindMemory(to: UInt8.self).baseAddress,
                                    let keyPointer = keyRaw.bindMemory(to: UInt8.self).baseAddress
                                else {
                                    throw CryptoError.invalidCiphertext
                                }

                                return crypto_aead_xchacha20poly1305_ietf_decrypt_detached(
                                    plaintextPointer,
                                    nil,
                                    ciphertextPointer,
                                    UInt64(payload.ciphertext.count),
                                    tagPointer,
                                    associatedDataPointer,
                                    UInt64(associatedData.count),
                                    noncePointer,
                                    keyPointer
                                )
                            }
                        }
                    }
                }
            }
        }

        guard status == 0 else {
            throw CryptoError.authenticationFailed
        }
        return plaintext
    }

    func wipe(_ data: inout Data) {
        guard !data.isEmpty else { return }
        data.withUnsafeMutableBytes { raw in
            guard let baseAddress = raw.baseAddress else { return }
            sodium_memzero(baseAddress, raw.count)
        }
        data.removeAll(keepingCapacity: false)
    }
}
