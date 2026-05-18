import Foundation

final class VaultRepository {
    private let storage: VaultStorage
    private let codec: VaultFileCodec
    private var session: VaultSession?

    init(storage: VaultStorage, codec: VaultFileCodec = VaultFileCodec()) {
        self.storage = storage
        self.codec = codec
    }

    var hasVault: Bool {
        storage.exists
    }

    var currentEntries: [VaultEntry] {
        session?.vault.entries ?? []
    }

    func create(password: String) throws -> Vault {
        let vault = Vault()
        let (encrypted, newSession) = try codec.createSession(vault: vault, password: password)
        try storage.write(encrypted)
        session = newSession
        return vault
    }

    func unlock(password: String) throws -> Vault {
        let newSession = try codec.decryptSession(data: storage.read(), password: password)
        session = newSession
        return newSession.vault
    }

    func lock() {
        if let session {
            CryptoService().wipe(&session.key)
        }
        session = nil
    }

    func save() throws {
        guard let session else { throw CryptoError.locked }
        let encrypted = try codec.encryptVaultWithKey(session.vault, key: session.key, kdfParams: session.kdfParams)
        try storage.write(encrypted)
    }

    func upsert(_ entry: VaultEntry) throws {
        guard let session else { throw CryptoError.locked }
        if let index = session.vault.entries.firstIndex(where: { $0.id == entry.id }) {
            session.vault.entries[index] = entry
        } else {
            session.vault.entries.append(entry)
        }
        try save()
    }

    func delete(_ entry: VaultEntry) throws {
        guard let session else { throw CryptoError.locked }
        session.vault.entries.removeAll { $0.id == entry.id }
        try save()
    }

    func changeMasterPassword(currentPassword: String, newPassword: String) throws {
        guard let session else { throw CryptoError.locked }
        _ = try codec.decryptSession(data: storage.read(), password: currentPassword)
        let (encrypted, newSession) = try codec.createSession(vault: session.vault, password: newPassword)
        try storage.write(encrypted)
        self.session = newSession
    }

    func exportUnlocked(exportPassword: String) throws -> Data {
        guard let vault = session?.vault else { throw CryptoError.locked }
        return try codec.encryptVault(vault, password: exportPassword, magic: VaultFileCodec.exportMagic)
    }

    func decryptExport(data: Data, exportPassword: String) throws -> Vault {
        try codec.decryptVault(data: data, password: exportPassword, magic: VaultFileCodec.exportMagic)
    }

    func replaceUnlocked(_ vault: Vault) throws {
        guard let session else { throw CryptoError.locked }
        session.vault = vault
        try save()
    }

    func mergeUnlocked(_ imported: Vault) throws -> Int {
        guard let session else { throw CryptoError.locked }
        var added = 0
        var existingIds = Set(session.vault.entries.map(\.id))
        for entry in imported.entries where !existingIds.contains(entry.id) {
            session.vault.entries.append(entry)
            existingIds.insert(entry.id)
            added += 1
        }
        try save()
        return added
    }
}
