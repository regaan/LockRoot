import SwiftUI

struct SetupView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @State private var password = ""
    @State private var confirm = ""
    @State private var acceptedTerms = false
    @State private var showingTerms = false
    @FocusState private var focused: Field?

    private enum Field {
        case password
        case confirm
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 26) {
                Image("LockrootIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 142, height: 142)
                    .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                    .padding(.top, 28)

                VStack(spacing: 10) {
                    Text("Set up your vault")
                        .font(.system(size: 34, weight: .black, design: .rounded))
                        .foregroundStyle(LockrootTheme.ink)
                    Text("Create a master password to encrypt and protect your local vault.")
                        .font(.body)
                        .foregroundStyle(LockrootTheme.muted)
                        .multilineTextAlignment(.center)
                }

                SoftCard {
                    VStack(alignment: .leading, spacing: 18) {
                        SecureField("Master Password", text: $password)
                            .textContentType(.newPassword)
                            .focused($focused, equals: .password)
                            .submitLabel(.next)
                            .onSubmit { focused = .confirm }
                            .font(.headline)
                            .foregroundStyle(LockrootTheme.ink)
                            .tint(LockrootTheme.green)
                            .padding()
                            .background(LockrootTheme.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(LockrootTheme.line))

                        PasswordStrengthView(password: password)

                        SecureField("Confirm Password", text: $confirm)
                            .textContentType(.newPassword)
                            .focused($focused, equals: .confirm)
                            .submitLabel(.done)
                            .font(.headline)
                            .foregroundStyle(LockrootTheme.ink)
                            .tint(LockrootTheme.green)
                            .padding()
                            .background(LockrootTheme.surface)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(LockrootTheme.line))

                        Toggle(isOn: $acceptedTerms) {
                            Button("I agree to the Terms and Conditions") {
                                showingTerms = true
                            }
                            .foregroundStyle(LockrootTheme.green)
                        }
                    }
                }

                SoftCard {
                    HStack(alignment: .top, spacing: 14) {
                        Image(systemName: "shield.lefthalf.filled")
                            .foregroundStyle(LockrootTheme.green)
                            .font(.title2)
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Important")
                                .font(.headline)
                            Text("Your master password is the only way to access your vault. Lockroot cannot recover it.")
                                .foregroundStyle(LockrootTheme.muted)
                        }
                    }
                }

                Button("Create Vault") {
                    viewModel.createVault(password: password, confirm: confirm, acceptedTerms: acceptedTerms)
                }
                .buttonStyle(PrimaryButtonStyle())
            }
            .padding(22)
        }
        .background(LockrootTheme.background)
        .scrollDismissesKeyboard(.interactively)
        .sheet(isPresented: $showingTerms) {
            LegalSheet(title: "Terms and Conditions")
        }
    }
}
