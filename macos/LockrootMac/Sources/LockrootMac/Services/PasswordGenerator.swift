import Foundation
import Security

final class PasswordGenerator {
    private static let lowercase = Array("abcdefghijkmnopqrstuvwxyz")
    private static let uppercase = Array("ABCDEFGHJKLMNPQRSTUVWXYZ")
    private static let numbers = Array("23456789")
    private static let symbols = Array("!@#$%^&*()-_=+[]{};:,.?")

    func generate(
        length: Int = 24,
        lowercase: Bool = true,
        uppercase: Bool = true,
        numbers: Bool = true,
        symbols: Bool = true
    ) throws -> String {
        guard (12...128).contains(length) else {
            throw CryptoError.unsupportedFormat("Password length must be between 12 and 128.")
        }

        var groups: [[Character]] = []
        if lowercase { groups.append(Self.lowercase) }
        if uppercase { groups.append(Self.uppercase) }
        if numbers { groups.append(Self.numbers) }
        if symbols { groups.append(Self.symbols) }
        guard !groups.isEmpty else {
            throw CryptoError.unsupportedFormat("At least one character group is required.")
        }
        guard length >= groups.count else {
            throw CryptoError.unsupportedFormat("Length must fit all selected groups.")
        }

        var result = try groups.map { try randomCharacter(from: $0) }
        let all = groups.flatMap { $0 }
        while result.count < length {
            result.append(try randomCharacter(from: all))
        }

        for index in result.indices.reversed() {
            let swapIndex = try randomInt(upperBound: index + 1)
            result.swapAt(index, swapIndex)
        }
        return String(result)
    }

    private func randomCharacter(from characters: [Character]) throws -> Character {
        characters[try randomInt(upperBound: characters.count)]
    }

    private func randomInt(upperBound: Int) throws -> Int {
        precondition(upperBound > 0)
        let max = UInt32.max - (UInt32.max % UInt32(upperBound))
        var value: UInt32 = 0
        repeat {
            var bytes = [UInt8](repeating: 0, count: MemoryLayout<UInt32>.size)
            let count = bytes.count
            let status = bytes.withUnsafeMutableBytes { raw in
                guard let baseAddress = raw.baseAddress else { return errSecParam }
                return SecRandomCopyBytes(kSecRandomDefault, count, baseAddress)
            }
            guard status == errSecSuccess else { throw CryptoError.randomFailure }
            value =
                UInt32(bytes[0]) |
                (UInt32(bytes[1]) << 8) |
                (UInt32(bytes[2]) << 16) |
                (UInt32(bytes[3]) << 24)
        } while value >= max
        return Int(value % UInt32(upperBound))
    }
}
