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
        VStack(spacing: 28) {
            LockrootIconImage(size: 116)

            VStack(spacing: 10) {
                Text("Set up your vault")
                    .font(.system(size: 38, weight: .black, design: .rounded))
                    .foregroundStyle(LockrootTheme.ink)
                Text("Create a master password to encrypt and protect your local macOS vault.")
                    .font(.system(size: 16))
                    .foregroundStyle(LockrootTheme.muted)
                    .multilineTextAlignment(.center)
            }

            SoftCard(padding: 24) {
                VStack(alignment: .leading, spacing: 18) {
                    Text("Master Password")
                        .font(.headline)
                    SecureField("At least 12 characters", text: $password)
                        .textFieldStyle(.roundedBorder)
                        .focused($focused, equals: .password)
                    PasswordStrengthView(password: password)

                    Divider()

                    Text("Confirm Password")
                        .font(.headline)
                    SecureField("Repeat master password", text: $confirm)
                        .textFieldStyle(.roundedBorder)
                        .focused($focused, equals: .confirm)

                    HStack(spacing: 10) {
                        Toggle("", isOn: $acceptedTerms)
                            .toggleStyle(.checkbox)
                        Text("I agree to the")
                            .foregroundStyle(LockrootTheme.muted)
                        Button("Terms and Conditions") {
                            showingTerms = true
                        }
                        .buttonStyle(.link)
                    }
                    .font(.system(size: 14))
                }
            }
            .frame(width: 520)

            SoftCard(padding: 18) {
                HStack(spacing: 14) {
                    Image(systemName: "shield.lefthalf.filled")
                        .font(.system(size: 24))
                        .foregroundStyle(LockrootTheme.green)
                        .frame(width: 42, height: 42)
                        .background(LockrootTheme.softGreen)
                        .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 5) {
                        Text("No recovery by design")
                            .font(.headline)
                        Text("Your master password is the only way to access your vault. Lockroot cannot recover it.")
                            .font(.system(size: 14))
                            .foregroundStyle(LockrootTheme.muted)
                    }
                }
            }
            .frame(width: 520)

            Button {
                viewModel.createVault(password: password, confirm: confirm, acceptedTerms: acceptedTerms)
                password = ""
                confirm = ""
            } label: {
                Label("Create Vault", systemImage: "arrow.right")
                    .frame(width: 230)
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(!acceptedTerms || password.isEmpty || confirm.isEmpty)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
        .sheet(isPresented: $showingTerms) {
            LegalTextSheet(title: "Terms and Conditions", bodyText: LegalText.terms)
                .frame(width: 620, height: 560)
        }
        .onAppear {
            focused = .password
        }
    }
}

struct UnlockView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @State private var password = ""
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 30) {
            LockrootIconImage(size: 116)

            VStack(spacing: 10) {
                Text("Unlock vault")
                    .font(.system(size: 38, weight: .black, design: .rounded))
                    .foregroundStyle(LockrootTheme.ink)
                Text("Enter your master password to continue.")
                    .font(.system(size: 16))
                    .foregroundStyle(LockrootTheme.muted)
            }

            SoftCard(padding: 24) {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Master Password")
                        .font(.headline)
                    SecureField("Master password", text: $password)
                        .textFieldStyle(.roundedBorder)
                        .focused($focused)
                        .onSubmit {
                            viewModel.unlock(password: password)
                            password = ""
                        }
                    Button {
                        viewModel.unlock(password: password)
                        password = ""
                    } label: {
                        Label("Unlock", systemImage: "arrow.right")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(password.isEmpty)
                }
            }
            .frame(width: 460)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(40)
        .onAppear {
            focused = true
        }
    }
}
