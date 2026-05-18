import Foundation
import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var showingSecurity = false
    @State private var showingAbout = false
    @State private var showingPrivacy = false
    @State private var showingTerms = false
    @State private var showingMasterChange = false

    let onExport: () -> Void
    let onImport: () -> Void
    let onGenerate: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("Vault") {
                    row("Export encrypted vault", "Create a password-protected export file", "square.and.arrow.up") {
                        dismissThen(onExport)
                    }
                    row("Import encrypted vault", "Preview, merge, or replace entries", "square.and.arrow.down") {
                        dismissThen(onImport)
                    }
                    row("Change master password", "Re-key the local vault", "key.fill") { showingMasterChange = true }
                }

                Section("Tools") {
                    row("Password generator", "Generate a strong password", "sparkles") {
                        dismissThen(onGenerate)
                    }
                    row("Security center", "Review Lockroot protections", "shield.checkered") { showingSecurity = true }
                }

                Section("Legal") {
                    row("Privacy Policy", "Local-only, no telemetry, no account", "hand.raised.fill") { showingPrivacy = true }
                    row("Terms and Conditions", "No recovery and user responsibility", "doc.text.fill") { showingTerms = true }
                    row("About Lockroot", "Created by REGAAN", "info.circle.fill") { showingAbout = true }
                }

                Section {
                    Button("Lock Vault", role: .destructive) {
                        dismiss()
                        viewModel.lock()
                    }
                }
            }
            .navigationTitle("Settings")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .sheet(isPresented: $showingSecurity) { SecurityCenterSheet() }
        .sheet(isPresented: $showingAbout) { AboutSheet() }
        .sheet(isPresented: $showingPrivacy) { LegalSheet(title: "Privacy Policy") }
        .sheet(isPresented: $showingTerms) { LegalSheet(title: "Terms and Conditions") }
        .sheet(isPresented: $showingMasterChange) { ChangeMasterPasswordSheet() }
    }

    private func row(_ title: String, _ subtitle: String, _ system: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: system)
                    .foregroundStyle(LockrootTheme.green)
                    .frame(width: 28)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .foregroundStyle(LockrootTheme.ink)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(LockrootTheme.muted)
                }
            }
        }
        .buttonStyle(.plain)
    }

    private func dismissThen(_ action: @escaping () -> Void) {
        dismiss()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            action()
        }
    }
}
