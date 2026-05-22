import Foundation
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    static let lockrootExport = UTType(exportedAs: "com.regaan.lockroot.export", conformingTo: .json)
}

struct VaultExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.lockrootExport, .json, .data] }
    var data: Data

    init(data: Data = Data()) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct ExportPasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirm = ""
    let onExport: (String, String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Export Password") {
                    SecureField("Password", text: $password)
                        .foregroundStyle(LockrootTheme.ink)
                    SecureField("Confirm Password", text: $confirm)
                        .foregroundStyle(LockrootTheme.ink)
                }
                Section {
                    Text("This password is separate from your master password. Import requires the same export password.")
                }
            }
            .navigationTitle("Export Vault")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Export") {
                        onExport(password, confirm)
                        password = ""
                        confirm = ""
                    }
                }
            }
        }
    }
}

struct ImportPasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    let onImport: (String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Export Password") {
                    SecureField("Password", text: $password)
                        .foregroundStyle(LockrootTheme.ink)
                }
                Section {
                    Text("Wrong password or tampered export files fail authentication.")
                }
            }
            .navigationTitle("Import Vault")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Preview") {
                        onImport(password)
                        password = ""
                    }
                }
            }
        }
    }
}

struct ImportPreviewSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("\(viewModel.pendingImport?.entries.count ?? 0) entries found")
                }

                Section("Preview") {
                    ForEach(viewModel.pendingImport?.entries ?? []) { entry in
                        VStack(alignment: .leading) {
                            Text(entry.title)
                                .font(.headline)
                                .foregroundStyle(LockrootTheme.ink)
                            Text(entry.username)
                                .foregroundStyle(LockrootTheme.muted)
                        }
                    }
                }

                Section {
                    Button("Merge Into Vault") {
                        viewModel.mergePendingImport()
                        dismiss()
                    }
                    Button("Replace Current Vault", role: .destructive) {
                        viewModel.replaceWithPendingImport()
                        dismiss()
                    }
                }
            }
            .navigationTitle("Import Preview")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.pendingImport = nil
                        dismiss()
                    }
                }
            }
        }
    }
}

struct PasswordGeneratorSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var length = 24.0
    @State private var lowercase = true
    @State private var uppercase = true
    @State private var numbers = true
    @State private var symbols = true
    @State private var generated = ""

    let onUse: (String) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Length") {
                    Stepper("\(Int(length)) characters", value: $length, in: 12...128, step: 1)
                        .foregroundStyle(LockrootTheme.ink)
                }
                Section("Character groups") {
                    Toggle("Lowercase", isOn: $lowercase)
                    Toggle("Uppercase", isOn: $uppercase)
                    Toggle("Numbers", isOn: $numbers)
                    Toggle("Symbols", isOn: $symbols)
                }
                Section("Generated") {
                    Text(generated.isEmpty ? "Tap Generate" : generated)
                        .font(.system(.body, design: .monospaced))
                        .foregroundStyle(LockrootTheme.ink)
                        .textSelection(.enabled)
                }
                Section {
                    Button("Generate") {
                        generated = viewModel.generatePassword(
                            length: Int(length),
                            lowercase: lowercase,
                            uppercase: uppercase,
                            numbers: numbers,
                            symbols: symbols
                        )
                    }
                    Button("Copy") {
                        let value = generated.isEmpty ? viewModel.generatePassword(length: Int(length), lowercase: lowercase, uppercase: uppercase, numbers: numbers, symbols: symbols) : generated
                        onUse(value)
                        dismiss()
                    }
                }
            }
            .navigationTitle("Password Generator")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            }
        }
    }
}

struct ChangeMasterPasswordSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var current = ""
    @State private var next = ""
    @State private var confirm = ""

    var body: some View {
        NavigationStack {
            Form {
                SecureField("Current password", text: $current)
                    .foregroundStyle(LockrootTheme.ink)
                SecureField("New password", text: $next)
                    .foregroundStyle(LockrootTheme.ink)
                SecureField("Confirm new password", text: $confirm)
                    .foregroundStyle(LockrootTheme.ink)
                PasswordStrengthView(password: next)
            }
            .navigationTitle("Master Password")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        viewModel.changeMasterPassword(current: current, next: next, confirm: confirm)
                        current = ""
                        next = ""
                        confirm = ""
                        dismiss()
                    }
                }
            }
        }
    }
}
