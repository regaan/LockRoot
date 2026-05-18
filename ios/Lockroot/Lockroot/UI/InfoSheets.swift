import SwiftUI

struct SecurityCenterSheet: View {
    @Environment(\.dismiss) private var dismiss

    private let items = [
        ("No network code", "The iOS app has no account, sync, analytics, ads, or telemetry code."),
        ("Argon2id key derivation", "Master and export passwords are hardened before encryption."),
        ("XChaCha20-Poly1305", "Vault and export files use authenticated encryption."),
        ("Wrong password fails", "Incorrect passwords and tampered files fail authentication."),
        ("Clipboard auto-clear", "Copied secrets are cleared from the pasteboard after a short delay."),
        ("App lock on background", "The in-memory vault locks when the app leaves the foreground."),
    ]

    var body: some View {
        NavigationStack {
            List(items, id: \.0) { title, subtitle in
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(LockrootTheme.ink)
                    Text(subtitle)
                        .foregroundStyle(LockrootTheme.muted)
                }
            }
            .navigationTitle("Security Center")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

struct LegalSheet: View {
    @Environment(\.dismiss) private var dismiss
    let title: String

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text(title)
                        .font(.largeTitle.bold())
                        .foregroundStyle(LockrootTheme.ink)
                    Text(bodyText)
                        .foregroundStyle(LockrootTheme.muted)
                        .lineSpacing(4)
                }
                .padding()
            }
            .background(LockrootTheme.background)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private var bodyText: String {
        if title.contains("Privacy") {
            return """
            Lockroot is an offline, local-first password manager.

            Lockroot does not collect, transmit, sell, rent, or share personal data. Vault entries, usernames, passwords, notes, tags, generated passwords, and encrypted exports remain under your control.

            The app does not use cloud sync, analytics, ads, remote configuration, telemetry, or account login.

            Export files are encrypted with a separate export password. Import requires the same export password and fails if the file is tampered with or the password is wrong.

            Contact: regaan48@gmail.com
            """
        }

        return """
        Lockroot has no recovery backdoor. You are responsible for remembering your master password and safely storing encrypted backups if you choose to create them.

        If your device is lost, damaged, reset, or wiped, you need your own encrypted backup to restore your vault.

        Lockroot is provided as-is, without warranties of uninterrupted availability, absolute security, or fitness for a particular purpose.

        Do not use Lockroot for unlawful activity or in ways that violate applicable laws, platform policies, or the rights of others.

        Contact: regaan48@gmail.com
        """
    }
}

struct AboutSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Image("LockrootIcon")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 100, height: 100)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

                    Text("Lockroot")
                        .font(.largeTitle.bold())
                        .foregroundStyle(LockrootTheme.ink)
                    Text("Secure. Private. Yours.")
                        .font(.headline)
                        .foregroundStyle(LockrootTheme.green)

                    Text("""
                    Created by REGAAN.

                    Published on iOS by Camora Technologies.
                    Publisher website: camoratechnologies.com

                    Security Researcher, Offensive Engineer, and Full-Stack Developer from Chennai, India.

                    Website: rothackers.com
                    GitHub: github.com/regaan
                    LinkedIn: linkedin.com/in/regaan
                    Email: regaan48@gmail.com
                    """)
                    .foregroundStyle(LockrootTheme.muted)
                    .lineSpacing(4)
                }
                .padding()
            }
            .background(LockrootTheme.background)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

struct PasswordStrengthView: View {
    let password: String

    private var score: Int {
        var result = 0
        if password.count >= 12 { result += 1 }
        if password.count >= 16 { result += 1 }
        if password.rangeOfCharacter(from: .lowercaseLetters) != nil { result += 1 }
        if password.rangeOfCharacter(from: .uppercaseLetters) != nil { result += 1 }
        if password.rangeOfCharacter(from: .decimalDigits) != nil { result += 1 }
        if password.rangeOfCharacter(from: CharacterSet(charactersIn: "!@#$%^&*()-_=+[]{};:,.?")) != nil { result += 1 }
        return result
    }

    private var label: String {
        if password.isEmpty { return "Enter password" }
        if score <= 2 { return "Weak password" }
        if score <= 4 { return "Good password" }
        return "Strong password"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ForEach(0..<4, id: \.self) { index in
                    Capsule()
                        .fill(index < min(score, 4) ? LockrootTheme.green : Color.black.opacity(0.08))
                        .frame(height: 5)
                }
            }
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(password.isEmpty ? LockrootTheme.muted : LockrootTheme.green)
        }
    }
}
