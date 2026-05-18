import Foundation

struct KdfParams: Equatable {
    let memoryKiB: Int
    let iterations: Int
    let parallelism: Int
    let salt: Data
}
