import SwiftUI

@main
struct LockrootApp: App {
    @StateObject private var viewModel = VaultViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(viewModel)
                .tint(LockrootTheme.green)
                .preferredColorScheme(.light)
                .onScenePhaseChange(scenePhase) { phase in
                    if phase == .inactive || phase == .background {
                        viewModel.lockForBackground()
                    }
                }
        }
    }
}

private extension View {
    @ViewBuilder
    func onScenePhaseChange(_ phase: ScenePhase, perform action: @escaping (ScenePhase) -> Void) -> some View {
        if #available(iOS 17.0, *) {
            self.onChange(of: phase) { _, newPhase in
                action(newPhase)
            }
        } else {
            self.onChange(of: phase) { newPhase in
                action(newPhase)
            }
        }
    }
}
