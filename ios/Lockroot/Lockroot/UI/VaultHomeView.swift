import Foundation
import SwiftUI
import UniformTypeIdentifiers

struct VaultHomeView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @State private var editingEntry: VaultEntry?
    @State private var showingSettings = false
    @State private var showingGenerator = false
    @State private var showingExport = false
    @State private var showingImporter = false
    @State private var showingImportPassword = false
    @State private var importData = Data()
    @State private var exportDocument: VaultExportDocument?
    @State private var showingExporter = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    homeHeader

                    HStack(spacing: 12) {
                        Image(systemName: "magnifyingglass")
                            .foregroundStyle(LockrootTheme.muted)
                        TextField("Search entries", text: $viewModel.query)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .foregroundStyle(LockrootTheme.ink)
                            .tint(LockrootTheme.green)
                    }
                    .padding()
                    .background(LockrootTheme.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(LockrootTheme.line))

                    quickActions

                    if viewModel.filteredEntries.isEmpty {
                        emptyState
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(viewModel.filteredEntries) { entry in
                                EntryCard(entry: entry) {
                                    editingEntry = entry
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .background(LockrootTheme.background)
            .navigationBarHidden(true)
        }
        .sheet(item: $editingEntry) { entry in
            EntryEditorView(entry: entry) { updated in
                viewModel.save(entry: updated)
            } onDelete: { entry in
                viewModel.delete(entry: entry)
            }
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView(
                onExport: { showingExport = true },
                onImport: { showingImporter = true },
                onGenerate: { showingGenerator = true }
            )
        }
        .sheet(isPresented: $showingGenerator) {
            PasswordGeneratorSheet { generated in
                viewModel.copyToClipboard(generated)
            }
        }
        .sheet(isPresented: $showingExport) {
            ExportPasswordSheet { password, confirm in
                if let data = viewModel.exportVault(password: password, confirm: confirm) {
                    exportDocument = VaultExportDocument(data: data)
                    showingExporter = true
                    showingExport = false
                }
            }
        }
        .sheet(isPresented: $showingImportPassword) {
            ImportPasswordSheet { password in
                viewModel.previewImport(data: importData, password: password)
                showingImportPassword = false
            }
        }
        .sheet(isPresented: Binding(
            get: { viewModel.pendingImport != nil },
            set: { if !$0 { viewModel.pendingImport = nil } }
        )) {
            ImportPreviewSheet()
        }
        .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.lockrootExport, .json, .data], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer {
                    if access { url.stopAccessingSecurityScopedResource() }
                }
                importData = try Data(contentsOf: url)
                showingImportPassword = true
            } catch {
                viewModel.errorMessage = error.localizedDescription
            }
        }
        .fileExporter(
            isPresented: $showingExporter,
            document: exportDocument,
            contentType: .lockrootExport,
            defaultFilename: "lockroot-export.lpexport"
        ) { result in
            if case .failure(let error) = result {
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private var homeHeader: some View {
        SoftCard {
            HStack(spacing: 16) {
                Image("LockrootIcon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 58, height: 58)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(alignment: .leading, spacing: 5) {
                    Text("Lockroot")
                        .font(.system(size: 32, weight: .black, design: .rounded))
                        .foregroundStyle(LockrootTheme.ink)
                    Text("\(viewModel.entries.count) entries")
                        .foregroundStyle(LockrootTheme.muted)
                }

                Spacer()

                Button {
                    viewModel.lock()
                } label: {
                    Label("Lock", systemImage: "lock.fill")
                        .labelStyle(.iconOnly)
                        .frame(width: 46, height: 46)
                        .foregroundStyle(.white)
                        .background(LockrootTheme.green)
                        .clipShape(Circle())
                }
            }
        }
    }

    private var quickActions: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            actionButton("Add", system: "plus.circle.fill") {
                editingEntry = .empty
            }
            actionButton("Generate", system: "sparkles") {
                showingGenerator = true
            }
            actionButton("Settings", system: "gearshape.fill") {
                showingSettings = true
            }
        }
    }

    private func actionButton(_ title: String, system: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: system)
                    .font(.title2)
                    .foregroundStyle(LockrootTheme.green)
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(LockrootTheme.ink)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 92)
            .background(LockrootTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(LockrootTheme.line))
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "lock.square.stack.fill")
                .font(.system(size: 58))
                .foregroundStyle(LockrootTheme.green)
            Text("No entries yet")
                .font(.title2.bold())
                .foregroundStyle(LockrootTheme.ink)
            Text("Add your first entry to get started.")
                .foregroundStyle(LockrootTheme.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 70)
    }
}
