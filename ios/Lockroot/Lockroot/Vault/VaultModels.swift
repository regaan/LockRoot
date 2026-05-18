import Foundation

struct Vault: Codable, Equatable {
    var vaultId: String = UUID().uuidString
    var schemaVersion: Int = 1
    var entries: [VaultEntry] = []
}

struct VaultEntry: Codable, Identifiable, Equatable, Hashable {
    var id: String = UUID().uuidString
    var title: String
    var website: String
    var username: String
    var password: String
    var notes: String
    var tags: [String] = []

    static var empty: VaultEntry {
        VaultEntry(title: "", website: "", username: "", password: "", notes: "", tags: [])
    }
}
