import SwiftUI

struct UnlockView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @State private var password = ""
    @FocusState private var focused: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 30) {
                Image("LockrootIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 150, height: 150)
                    .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
                    .padding(.top, 78)

                VStack(spacing: 10) {
                    Text("Unlock vault")
                        .font(.system(size: 34, weight: .black, design: .rounded))
                        .foregroundStyle(LockrootTheme.ink)
                    Text("Enter your master password to continue.")
                        .foregroundStyle(LockrootTheme.muted)
                }

                SoftCard {
                    VStack(spacing: 18) {
                        SecureField("Master Password", text: $password)
                            .textContentType(.password)
                            .focused($focused)
                            .submitLabel(.go)
                            .onSubmit { viewModel.unlock(password: password) }
                            .font(.headline)
                            .foregroundStyle(LockrootTheme.ink)
                            .tint(LockrootTheme.green)
                            .padding()
                            .background(LockrootTheme.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(LockrootTheme.line))

                        Button("Unlock") {
                            viewModel.unlock(password: password)
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                }

                Text("No recovery. No cloud. No internet permission.")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(LockrootTheme.green)
            }
            .padding(22)
        }
        .background(LockrootTheme.background)
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            focused = true
        }
    }
}
