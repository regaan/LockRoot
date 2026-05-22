import AppKit
import Foundation

final class ClipboardService {
    private var clearTask: Task<Void, Never>?
    private var ownedChangeCount: Int?

    deinit {
        clearTask?.cancel()
    }

    @MainActor
    func copySecret(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        let changeCount = pasteboard.changeCount
        ownedChangeCount = changeCount

        clearTask?.cancel()
        clearTask = Task { [changeCount] in
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if NSPasteboard.general.changeCount == changeCount {
                    NSPasteboard.general.clearContents()
                }
            }
        }
    }

    @MainActor
    func clearOwnedClipboard() {
        clearTask?.cancel()
        clearTask = nil

        defer { ownedChangeCount = nil }

        guard let ownedChangeCount else {
            return
        }

        if NSPasteboard.general.changeCount == ownedChangeCount {
            NSPasteboard.general.clearContents()
        }
    }

    func cancel() {
        clearTask?.cancel()
        clearTask = nil
    }
}
