import Foundation

struct KdfParams: Equatable {
    static let minMemoryKiB = 19_456
    static let maxMemoryKiB = 262_144
    static let minIterations = 2
    static let maxIterations = 10
    static let minParallelism = 1
    static let maxParallelism = 8
    static let minSaltBytes = 16
    static let maxSaltBytes = 64

    let memoryKiB: Int
    let iterations: Int
    let parallelism: Int
    let salt: Data

    init(memoryKiB: Int, iterations: Int, parallelism: Int, salt: Data) throws {
        guard (Self.minMemoryKiB...Self.maxMemoryKiB).contains(memoryKiB) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id memory cost.")
        }
        guard (Self.minIterations...Self.maxIterations).contains(iterations) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id iteration count.")
        }
        guard (Self.minParallelism...Self.maxParallelism).contains(parallelism) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id parallelism.")
        }
        guard (Self.minSaltBytes...Self.maxSaltBytes).contains(salt.count) else {
            throw CryptoError.unsupportedFormat("Unsupported Argon2id salt length.")
        }

        self.memoryKiB = memoryKiB
        self.iterations = iterations
        self.parallelism = parallelism
        self.salt = salt
    }
}
