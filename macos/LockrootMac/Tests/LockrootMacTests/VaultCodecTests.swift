import XCTest
@testable import LockrootMac

final class VaultCodecTests: XCTestCase {
    func testV2AesGcmRoundTrip() throws {
        let codec = VaultFileCodec()
        let encrypted = try codec.encryptVault(sampleVault(), password: "correct horse battery staple")
        let envelope = try VaultJson.envelopeFromData(encrypted)

        XCTAssertEqual(envelope.magic, VaultFileCodec.vaultMagic)
        XCTAssertEqual(envelope.version, VaultFileCodec.currentVersion)
        XCTAssertEqual(envelope.cipher, "aes-256-gcm")
        XCTAssertEqual(envelope.nonce.count, CryptoService.aesGcmNonceBytes)

        let decrypted = try codec.decryptVault(data: encrypted, password: "correct horse battery staple")
        XCTAssertEqual(decrypted.entries.first?.password, "secret-value")
    }

    func testWrongPasswordFails() throws {
        let codec = VaultFileCodec()
        let encrypted = try codec.encryptVault(sampleVault(), password: "correct horse battery staple")

        XCTAssertThrowsError(
            try codec.decryptVault(data: encrypted, password: "wrong horse battery staple")
        )
    }

    func testTamperedCiphertextFails() throws {
        let codec = VaultFileCodec()
        var encrypted = try codec.encryptVault(sampleVault(), password: "correct horse battery staple")
        let tamperIndex = encrypted.index(before: encrypted.endIndex)
        encrypted[tamperIndex] = encrypted[tamperIndex] ^ 0x01

        XCTAssertThrowsError(
            try codec.decryptVault(data: encrypted, password: "correct horse battery staple")
        )
    }

    func testV1VaultIsAcceptedAndMarkedForMigration() throws {
        let codec = VaultFileCodec()
        let encrypted = try codec.encryptVault(
            sampleVault(),
            password: "correct horse battery staple",
            version: 1
        )

        XCTAssertTrue(try codec.needsMigration(data: encrypted))
        let decrypted = try codec.decryptVault(data: encrypted, password: "correct horse battery staple")
        XCTAssertEqual(decrypted.entries.first?.password, "secret-value")
    }

    private func sampleVault() -> Vault {
        Vault(
            entries: [
                VaultEntry(
                    title: "GitHub",
                    website: "github.com",
                    username: "regaan",
                    password: "secret-value",
                    notes: "private",
                    tags: ["dev"]
                )
            ]
        )
    }
}
