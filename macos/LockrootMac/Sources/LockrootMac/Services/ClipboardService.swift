import AppKit
import Foundation

final class ClipboardService {
    private var clearTask: Task<Void, Never>?

    deinit {
        clearTask?.cancel()
    }

    @MainActor
    func copySecret(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
        let changeCount = pasteboard.changeCount

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

    func cancel() {
        clearTask?.cancel()
        clearTask = nil
    }
}
