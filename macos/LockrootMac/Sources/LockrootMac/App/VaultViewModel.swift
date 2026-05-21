import Foundation

@MainActor
final class VaultViewModel: ObservableObject {
    enum Phase {
        case setup
        case unlock
        case unlocked
    }

    @Published private(set) var phase: Phase
    @Published private(set) var entries: [VaultEntry] = []
    @Published var query = ""
    @Published var errorMessage: String?
    @Published var infoMessage: String?
    @Published var pendingImport: Vault?

    private let repository = VaultRepository(storage: VaultStorage())
    private let generator = PasswordGenerator()
    private let clipboard = ClipboardService()
    private var failedUnlockAttempts = 0
    private var unlockBlockedUntil: Date?

    init() {
        phase = repository.hasVault ? .unlock : .setup
    }

    deinit {
        clipboard.cancel()
        repository.lock()
    }

    var filteredEntries: [VaultEntry] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !term.isEmpty else { return entries }
        return entries.filter { entry in
            entry.title.lowercased().contains(term) ||
            entry.website.lowercased().contains(term) ||
            entry.username.lowercased().contains(term) ||
            entry.notes.lowercased().contains(term) ||
            entry.tags.joined(separator: " ").lowercased().contains(term)
        }
    }

    func createVault(password: String, confirm: String, acceptedTerms: Bool) {
        guard acceptedTerms else {
            errorMessage = "Accept the Terms and Conditions before creating your vault."
            return
        }
        guard password == confirm else {
            errorMessage = "Passwords do not match."
            return
        }
        guard password.count >= 12 else {
            errorMessage = "Use at least 12 characters for the master password."
            return
        }

        do {
            let vault = try repository.create(password: password)
            entries = vault.entries
            phase = .unlocked
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func unlock(password: String) {
        if let remaining = unlockBlockRemaining(), remaining > 0 {
            errorMessage = "Too many failed attempts. Try again in \(Int(ceil(remaining))) seconds."
            return
        }

        do {
            let vault = try repository.unlock(password: password)
            entries = vault.entries
            phase = .unlocked
            failedUnlockAttempts = 0
            unlockBlockedUntil = nil
        } catch {
            registerFailedUnlock()
        }
    }

    func lock() {
        repository.lock()
        clipboard.cancel()
        entries = []
        pendingImport = nil
        phase = repository.hasVault ? .unlock : .setup
    }

    func save(entry: VaultEntry) {
        do {
            try repository.upsert(entry)
            entries = repository.currentEntries
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func delete(entry: VaultEntry) {
        do {
            try repository.delete(entry)
            entries = repository.currentEntries
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func changeMasterPassword(current: String, next: String, confirm: String) {
        guard next == confirm else {
            errorMessage = "Passwords do not match."
            return
        }
        guard next.count >= 12 else {
            errorMessage = "Use at least 12 characters for the new master password."
            return
        }

        do {
            try repository.changeMasterPassword(currentPassword: current, newPassword: next)
            entries = repository.currentEntries
            infoMessage = "Master password changed."
        } catch {
            errorMessage = "Current password is wrong or the vault could not be re-keyed."
        }
    }

    func exportVault(password: String, confirm: String) -> Data? {
        guard password == confirm else {
            errorMessage = "Export passwords do not match."
            return nil
        }
        guard password.count >= 12 else {
            errorMessage = "Use at least 12 characters for the export password."
            return nil
        }

        do {
            return try repository.exportUnlocked(exportPassword: password)
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    func previewImport(data: Data, password: String) {
        do {
            pendingImport = try repository.decryptExport(data: data, exportPassword: password)
        } catch {
            errorMessage = "Wrong export password or corrupted export file."
        }
    }

    func mergePendingImport() {
        guard let pendingImport else { return }
        do {
            let count = try repository.mergeUnlocked(pendingImport)
            entries = repository.currentEntries
            self.pendingImport = nil
            infoMessage = "Imported \(count) entries."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func replaceWithPendingImport() {
        guard let pendingImport else { return }
        do {
            try repository.replaceUnlocked(pendingImport)
            entries = repository.currentEntries
            self.pendingImport = nil
            infoMessage = "Vault replaced with imported entries."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func generatePassword(length: Int, lowercase: Bool, uppercase: Bool, numbers: Bool, symbols: Bool) -> String {
        do {
            return try generator.generate(
                length: length,
                lowercase: lowercase,
                uppercase: uppercase,
                numbers: numbers,
                symbols: symbols
            )
        } catch {
            errorMessage = error.localizedDescription
            return ""
        }
    }

    func copyToClipboard(_ text: String) {
        clipboard.copySecret(text)
        infoMessage = "Copied. Clipboard will clear shortly."
    }

    private func registerFailedUnlock() {
        failedUnlockAttempts += 1

        guard failedUnlockAttempts >= 3 else {
            errorMessage = "Wrong password or corrupted vault."
            return
        }

        let delaySeconds = min(30, Int(pow(2.0, Double(min(failedUnlockAttempts - 2, 5)))))
        unlockBlockedUntil = Date().addingTimeInterval(TimeInterval(delaySeconds))
        errorMessage = "Wrong password or corrupted vault. Try again in \(delaySeconds) seconds."
    }

    private func unlockBlockRemaining() -> TimeInterval? {
        guard let unlockBlockedUntil else { return nil }
        let remaining = unlockBlockedUntil.timeIntervalSinceNow
        if remaining <= 0 {
            self.unlockBlockedUntil = nil
            return nil
        }
        return remaining
    }
}
