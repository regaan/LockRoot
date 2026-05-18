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
            return "Authentication failed."
        case .unsupportedFormat(let message):
            return message
        case .invalidPassword:
            return "Password must not be empty."
        case .invalidCiphertext:
            return "Invalid encrypted data."
        case .randomFailure:
            return "Secure random generation failed."
        case .locked:
            return "Vault is locked."
        }
    }
}
