import SwiftUI

enum LockrootTheme {
    static let background = Color(red: 0.96, green: 0.97, blue: 0.94)
    static let surface = Color.white
    static let ink = Color(red: 0.08, green: 0.09, blue: 0.08)
    static let muted = Color(red: 0.43, green: 0.47, blue: 0.45)
    static let green = Color(red: 0.13, green: 0.37, blue: 0.29)
    static let softGreen = Color(red: 0.87, green: 0.93, blue: 0.89)
    static let line = Color.black.opacity(0.08)
}

struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .foregroundStyle(.white)
            .background(LockrootTheme.green)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
    }
}

struct SoftCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .padding(18)
            .foregroundStyle(LockrootTheme.ink)
            .background(LockrootTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(LockrootTheme.line)
            )
            .shadow(color: .black.opacity(0.06), radius: 16, y: 8)
    }
}

struct LockrootFormModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .background(LockrootTheme.background)
            .foregroundStyle(LockrootTheme.ink)
            .tint(LockrootTheme.green)
            .preferredColorScheme(.light)
    }
}

extension View {
    func lockrootFormStyle() -> some View {
        modifier(LockrootFormModifier())
    }
}
