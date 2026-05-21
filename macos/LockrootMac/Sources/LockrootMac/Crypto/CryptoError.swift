import Foundation

enum CryptoError: LocalizedError {
    case authenticationFailed
    case unsupportedFormat(String)
    case invalidPassword
    case invalidCiphertext
    case randomFailure
    case locked

    var errorDescription: String? {
        switch self {
        case .authenticationFailed:
            "Authentication failed."
        case .unsupportedFormat(let message):
            message
        case .invalidPassword:
            "Password must not be empty."
        case .invalidCiphertext:
            "Invalid encrypted data."
        case .randomFailure:
            "Secure random generation failed."
        case .locked:
            "Vault is locked."
        }
    }
}
