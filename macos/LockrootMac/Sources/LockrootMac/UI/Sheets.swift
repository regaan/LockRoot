import SwiftUI

struct ExportPasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    @State private var confirm = ""
    let onExport: (String, String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Export vault")
                .font(.system(size: 28, weight: .black, design: .rounded))
            Text("Create a separate export password. Import requires this exact password.")
                .foregroundStyle(LockrootTheme.muted)

            SecureField("Export password", text: $password)
                .textFieldStyle(.roundedBorder)
            SecureField("Confirm export password", text: $confirm)
                .textFieldStyle(.roundedBorder)
            PasswordStrengthView(password: password)

            Spacer()

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                    .buttonStyle(SecondaryButtonStyle())
                Button("Continue") {
                    onExport(password, confirm)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(password.isEmpty || confirm.isEmpty)
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct ImportPasswordSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var password = ""
    let onImport: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Import vault")
                .font(.system(size: 28, weight: .black, design: .rounded))
            Text("Enter the export password. Wrong password or tampered files fail authentication.")
                .foregroundStyle(LockrootTheme.muted)

            SecureField("Export password", text: $password)
                .textFieldStyle(.roundedBorder)

            Spacer()

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                    .buttonStyle(SecondaryButtonStyle())
                Button("Preview") {
                    onImport(password)
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(password.isEmpty)
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct ImportPreviewSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Import preview")
                .font(.system(size: 28, weight: .black, design: .rounded))
            Text("\(viewModel.pendingImport?.entries.count ?? 0) entries found")
                .foregroundStyle(LockrootTheme.muted)

            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(viewModel.pendingImport?.entries ?? []) { entry in
                        HStack(spacing: 12) {
                            Image(systemName: "key.horizontal")
                                .foregroundStyle(LockrootTheme.green)
                                .frame(width: 36, height: 36)
                                .background(LockrootTheme.softGreen)
                                .clipShape(Circle())
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.title)
                                    .font(.headline)
                                Text(entry.username.isEmpty ? entry.website : entry.username)
                                    .foregroundStyle(LockrootTheme.muted)
                            }
                            Spacer()
                        }
                        .padding(12)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(LockrootTheme.line))
                    }
                }
            }

            HStack {
                Button("Cancel") {
                    viewModel.pendingImport = nil
                    dismiss()
                }
                .buttonStyle(SecondaryButtonStyle())
                Spacer()
                Button("Replace Current Vault", role: .destructive) {
                    viewModel.replaceWithPendingImport()
                    dismiss()
                }
                Button("Merge Into Vault") {
                    viewModel.mergePendingImport()
                    dismiss()
                }
                .buttonStyle(PrimaryButtonStyle())
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct PasswordGeneratorSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var length = 24
    @State private var lowercase = true
    @State private var uppercase = true
    @State private var numbers = true
    @State private var symbols = true
    @State private var generated = ""

    let onUse: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Password Generator")
                .font(.system(size: 30, weight: .black, design: .rounded))

            SoftCard {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Text("Length")
                            .font(.headline)
                        Spacer()
                        Stepper("\(length) characters", value: $length, in: 12...128)
                    }

                    Divider()

                    Toggle("Lowercase", isOn: $lowercase)
                    Toggle("Uppercase", isOn: $uppercase)
                    Toggle("Numbers", isOn: $numbers)
                    Toggle("Symbols", isOn: $symbols)
                }
            }

            SoftCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Generated")
                        .font(.headline)
                    Text(generated.isEmpty ? "Tap Generate" : generated)
                        .font(.system(size: 15, design: .monospaced))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.black.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }

            HStack {
                Button("Close") { dismiss() }
                    .buttonStyle(SecondaryButtonStyle())
                Spacer()
                Button("Generate") {
                    generated = viewModel.generatePassword(
                        length: length,
                        lowercase: lowercase,
                        uppercase: uppercase,
                        numbers: numbers,
                        symbols: symbols
                    )
                }
                .buttonStyle(SecondaryButtonStyle())
                Button("Copy") {
                    let value = generated.isEmpty ? viewModel.generatePassword(length: length, lowercase: lowercase, uppercase: uppercase, numbers: numbers, symbols: symbols) : generated
                    onUse(value)
                    dismiss()
                }
                .buttonStyle(PrimaryButtonStyle())
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct ChangeMasterPasswordSheet: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var current = ""
    @State private var next = ""
    @State private var confirm = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Master Password")
                .font(.system(size: 28, weight: .black, design: .rounded))
            Text("Re-key the local vault with a new master password.")
                .foregroundStyle(LockrootTheme.muted)

            SecureField("Current password", text: $current)
                .textFieldStyle(.roundedBorder)
            SecureField("New password", text: $next)
                .textFieldStyle(.roundedBorder)
            SecureField("Confirm new password", text: $confirm)
                .textFieldStyle(.roundedBorder)
            PasswordStrengthView(password: next)

            Spacer()

            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                    .buttonStyle(SecondaryButtonStyle())
                Button("Save") {
                    viewModel.changeMasterPassword(current: current, next: next, confirm: confirm)
                    dismiss()
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(current.isEmpty || next.isEmpty || confirm.isEmpty)
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct LegalTextSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let bodyText: String

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text(title)
                    .font(.system(size: 28, weight: .black, design: .rounded))
                Spacer()
                Button("Close") { dismiss() }
                    .buttonStyle(SecondaryButtonStyle())
            }
            ScrollView {
                Text(bodyText)
                    .foregroundStyle(LockrootTheme.muted)
                    .lineSpacing(4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }
}

struct SettingsPanel: View {
    let onExport: () -> Void
    let onImport: () -> Void
    let onMaster: () -> Void

    @State private var legalSheet: LegalSheetKind?

    enum LegalSheetKind: Identifiable {
        case privacy
        case terms
        case about

        var id: String {
            switch self {
            case .privacy: "privacy"
            case .terms: "terms"
            case .about: "about"
            }
        }

        var title: String {
            switch self {
            case .privacy: "Privacy Policy"
            case .terms: "Terms and Conditions"
            case .about: "About Lockroot"
            }
        }

        var bodyText: String {
            switch self {
            case .privacy: LegalText.privacy
            case .terms: LegalText.terms
            case .about: LegalText.about
            }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            Text("Settings")
                .font(.system(size: 34, weight: .black, design: .rounded))

            SoftCard {
                VStack(spacing: 0) {
                    settingsButton("Export encrypted vault", subtitle: "Create a password-protected export file", icon: "square.and.arrow.up", action: onExport)
                    Divider()
                    settingsButton("Import encrypted vault", subtitle: "Preview, merge, or replace entries", icon: "square.and.arrow.down", action: onImport)
                    Divider()
                    settingsButton("Change master password", subtitle: "Re-key the local vault", icon: "key", action: onMaster)
                }
            }

            SoftCard {
                VStack(spacing: 0) {
                    settingsButton("Privacy Policy", subtitle: "Local-only, no telemetry, no account", icon: "hand.raised", action: { legalSheet = .privacy })
                    Divider()
                    settingsButton("Terms and Conditions", subtitle: "No recovery and user responsibility", icon: "doc.text", action: { legalSheet = .terms })
                    Divider()
                    settingsButton("About Lockroot", subtitle: "Creator, project, and contact", icon: "info.circle", action: { legalSheet = .about })
                }
            }
        }
        .sheet(item: $legalSheet) { item in
            LegalTextSheet(title: item.title, bodyText: item.bodyText)
                .frame(width: 620, height: 560)
        }
    }

    private func settingsButton(_ title: String, subtitle: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(LockrootTheme.green)
                    .frame(width: 44, height: 44)
                    .background(LockrootTheme.softGreen)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(LockrootTheme.ink)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(LockrootTheme.muted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(LockrootTheme.muted)
            }
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }
}
