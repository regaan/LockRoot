import Foundation

struct AeadPayload: Equatable {
    let ciphertext: Data
    let tag: Data
}
