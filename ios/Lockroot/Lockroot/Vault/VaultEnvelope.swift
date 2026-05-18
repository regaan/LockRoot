import Foundation

struct VaultEnvelope: Equatable {
    let magic: String
    let version: Int
    let kdf: String
    let kdfParams: KdfParams
    let cipher: String
    let nonce: Data
    let ciphertext: Data
    let tag: Data
}

final class VaultSession {
    var vault: Vault
    var key: Data
    let kdfParams: KdfParams

    init(vault: Vault, key: Data, kdfParams: KdfParams) {
        self.vault = vault
        self.key = key
        self.kdfParams = kdfParams
    }
}
