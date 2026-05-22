import AppKit
import SwiftUI

@main
struct LockrootMacApp: App {
    @StateObject private var viewModel = VaultViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup("Lockroot") {
            RootView()
                .environmentObject(viewModel)
                .frame(minWidth: 1040, minHeight: 700)
                .preferredColorScheme(.light)
                .onChange(of: scenePhase) { phase in
                    if phase != .active, viewModel.phase == .unlocked {
                        viewModel.lock()
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: NSApplication.willTerminateNotification)) { _ in
                    viewModel.clearClipboard()
                    viewModel.lock()
                }
        }
        .commands {
            CommandMenu("Vault") {
                Button("Lock Vault") {
                    viewModel.lock()
                }
                .keyboardShortcut("l", modifiers: [.command, .shift])
                .disabled(viewModel.phase != .unlocked)
            }
        }
    }
}
