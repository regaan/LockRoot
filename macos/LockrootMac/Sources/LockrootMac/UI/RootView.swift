import SwiftUI

struct RootView: View {
    @EnvironmentObject private var viewModel: VaultViewModel

    var body: some View {
        ZStack {
            LockrootTheme.background.ignoresSafeArea()
            switch viewModel.phase {
            case .setup:
                SetupView()
            case .unlock:
                UnlockView()
            case .unlocked:
                DashboardView()
            }
        }
        .alert("Lockroot", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) { viewModel.errorMessage = nil }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .alert("Lockroot", isPresented: Binding(
            get: { viewModel.infoMessage != nil },
            set: { if !$0 { viewModel.infoMessage = nil } }
        )) {
            Button("OK", role: .cancel) { viewModel.infoMessage = nil }
        } message: {
            Text(viewModel.infoMessage ?? "")
        }
    }
}
