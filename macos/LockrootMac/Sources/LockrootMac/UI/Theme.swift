import SwiftUI

enum LockrootTheme {
    static let background = Color(red: 0.96, green: 0.97, blue: 0.94)
    static let sidebar = Color(red: 0.97, green: 0.98, blue: 0.96)
    static let surface = Color.white
    static let ink = Color(red: 0.08, green: 0.09, blue: 0.08)
    static let muted = Color(red: 0.42, green: 0.46, blue: 0.45)
    static let green = Color(red: 0.10, green: 0.37, blue: 0.28)
    static let softGreen = Color(red: 0.88, green: 0.94, blue: 0.90)
    static let line = Color.black.opacity(0.08)
}

struct SoftCard<Content: View>: View {
    let padding: CGFloat
    let content: Content

    init(padding: CGFloat = 20, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .background(LockrootTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(LockrootTheme.line)
            )
            .shadow(color: .black.opacity(0.04), radius: 18, y: 8)
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .frame(height: 44)
            .background(LockrootTheme.green)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(LockrootTheme.green)
            .padding(.horizontal, 18)
            .frame(height: 44)
            .background(LockrootTheme.softGreen)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct LockrootIconImage: View {
    var size: CGFloat

    private var icon: Image {
#if SWIFT_PACKAGE
        return Image("LockrootIcon", bundle: .module)
#else
        return Image("LockrootIcon")
#endif
    }

    var body: some View {
        icon
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.2, style: .continuous))
    }
}

struct SidebarRow: View {
    let title: String
    let systemImage: String
    let selected: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 16, weight: .medium))
                .frame(width: 20)
            Text(title)
                .font(.system(size: 15, weight: .medium))
            Spacer()
        }
        .foregroundStyle(selected ? LockrootTheme.green : LockrootTheme.ink.opacity(0.82))
        .padding(.horizontal, 16)
        .frame(height: 48)
        .background(selected ? LockrootTheme.softGreen : Color.clear)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
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
        VStack(alignment: .leading, spacing: 9) {
            HStack(spacing: 8) {
                ForEach(0..<4, id: \.self) { index in
                    Capsule()
                        .fill(index < min(score, 4) ? LockrootTheme.green : Color.black.opacity(0.09))
                        .frame(height: 5)
                }
            }
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(password.isEmpty ? LockrootTheme.muted : LockrootTheme.green)
        }
    }
}
