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
        try create(passwordData: Data(password.utf8))
    }

    func create(passwordData: Data) throws -> Vault {
        let vault = Vault()
        let (encrypted, newSession) = try codec.createSession(vault: vault, passwordData: passwordData)
        try storage.write(encrypted)
        session = newSession
        return vault
    }

    func unlock(password: String) throws -> Vault {
        try unlock(passwordData: Data(password.utf8))
    }

    func unlock(passwordData: Data) throws -> Vault {
        let encrypted = storage.read()
        let newSession = try codec.decryptSession(data: encrypted, passwordData: passwordData)
        session = newSession
        if try codec.needsMigration(data: encrypted) {
            try save()
        }
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
        try changeMasterPassword(
            currentPasswordData: Data(currentPassword.utf8),
            newPasswordData: Data(newPassword.utf8)
        )
    }

    func changeMasterPassword(currentPasswordData: Data, newPasswordData: Data) throws {
        guard let session else { throw CryptoError.locked }
        _ = try codec.decryptSession(data: storage.read(), passwordData: currentPasswordData)
        let (encrypted, newSession) = try codec.createSession(vault: session.vault, passwordData: newPasswordData)
        try storage.write(encrypted)
        self.session = newSession
    }

    func exportUnlocked(exportPassword: String) throws -> Data {
        try exportUnlocked(exportPasswordData: Data(exportPassword.utf8))
    }

    func exportUnlocked(exportPasswordData: Data) throws -> Data {
        guard let vault = session?.vault else { throw CryptoError.locked }
        return try codec.encryptVault(vault, passwordData: exportPasswordData, magic: VaultFileCodec.exportMagic)
    }

    func decryptExport(data: Data, exportPassword: String) throws -> Vault {
        try decryptExport(data: data, exportPasswordData: Data(exportPassword.utf8))
    }

    func decryptExport(data: Data, exportPasswordData: Data) throws -> Vault {
        try codec.decryptVault(data: data, passwordData: exportPasswordData, magic: VaultFileCodec.exportMagic)
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
