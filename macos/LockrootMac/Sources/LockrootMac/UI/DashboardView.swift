import Foundation
import SwiftUI
import UniformTypeIdentifiers

private enum MacRoute: String, CaseIterable {
    case home
    case allEntries
    case generator
    case importVault
    case exportVault
    case masterPassword
    case settings

    var title: String {
        switch self {
        case .home: "Home"
        case .allEntries: "All Entries"
        case .generator: "Password Generator"
        case .importVault: "Import"
        case .exportVault: "Export"
        case .masterPassword: "Master Password"
        case .settings: "Settings"
        }
    }

    var icon: String {
        switch self {
        case .home: "house"
        case .allEntries: "list.bullet.rectangle"
        case .generator: "sparkles"
        case .importVault: "square.and.arrow.down"
        case .exportVault: "square.and.arrow.up"
        case .masterPassword: "lock"
        case .settings: "gearshape"
        }
    }
}

extension UTType {
    static let lockrootExport = UTType(exportedAs: "com.regaan.lockroot.export", conformingTo: .json)
}

struct VaultExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.lockrootExport, .json, .data] }
    var data: Data

    init(data: Data = Data()) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

struct DashboardView: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    @State private var route: MacRoute = .home
    @State private var editingEntry: VaultEntry?
    @State private var showingGenerator = false
    @State private var showingExportPassword = false
    @State private var showingImportPicker = false
    @State private var showingImportPassword = false
    @State private var showingMasterPassword = false
    @State private var importData = Data()
    @State private var exportDocument: VaultExportDocument?
    @State private var showingExporter = false

    private let contentColumns = [
        GridItem(.adaptive(minimum: 220, maximum: 320), spacing: 14)
    ]

    var body: some View {
        HStack(spacing: 0) {
            sidebar
                .frame(width: 260)
            Divider()
            mainContent
        }
        .background(LockrootTheme.background)
        .sheet(item: $editingEntry) { entry in
            EntryEditorView(entry: entry) { updated in
                viewModel.save(entry: updated)
            } onDelete: { entry in
                viewModel.delete(entry: entry)
            }
            .frame(width: 640, height: 680)
        }
        .sheet(isPresented: $showingGenerator) {
            PasswordGeneratorSheet { generated in
                viewModel.copyToClipboard(generated)
            }
            .frame(width: 560, height: 620)
        }
        .sheet(isPresented: $showingExportPassword) {
            ExportPasswordSheet { password, confirm in
                if let data = viewModel.exportVault(password: password, confirm: confirm) {
                    exportDocument = VaultExportDocument(data: data)
                    showingExportPassword = false
                    showingExporter = true
                }
            }
            .frame(width: 520, height: 340)
        }
        .sheet(isPresented: $showingImportPassword) {
            ImportPasswordSheet { password in
                viewModel.previewImport(data: importData, password: password)
                showingImportPassword = false
            }
            .frame(width: 520, height: 300)
        }
        .sheet(isPresented: Binding(
            get: { viewModel.pendingImport != nil },
            set: { if !$0 { viewModel.pendingImport = nil } }
        )) {
            ImportPreviewSheet()
                .frame(width: 680, height: 620)
        }
        .sheet(isPresented: $showingMasterPassword) {
            ChangeMasterPasswordSheet()
                .frame(width: 520, height: 440)
        }
        .fileImporter(isPresented: $showingImportPicker, allowedContentTypes: [.lockrootExport, .json, .data], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
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

    private var sidebar: some View {
        VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 14) {
                LockrootIconImage(size: 48)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Lockroot")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(LockrootTheme.ink)
                    Text("Password Manager")
                        .font(.system(size: 13))
                        .foregroundStyle(LockrootTheme.muted)
                }
            }
            .padding(.top, 32)
            .padding(.horizontal, 24)

            VStack(spacing: 8) {
                ForEach([MacRoute.home, .allEntries], id: \.self) { item in
                    Button {
                        activateSidebar(item)
                    } label: {
                        SidebarRow(title: item.title, systemImage: item.icon, selected: route == item)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)

            VStack(alignment: .leading, spacing: 8) {
                Text("TOOLS")
                    .font(.caption)
                    .foregroundStyle(LockrootTheme.muted)
                    .padding(.horizontal, 16)
                ForEach([MacRoute.generator, .importVault, .exportVault], id: \.self) { item in
                    Button {
                        activateSidebar(item)
                    } label: {
                        SidebarRow(title: item.title, systemImage: item.icon, selected: route == item)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)

            VStack(alignment: .leading, spacing: 8) {
                Text("VAULT")
                    .font(.caption)
                    .foregroundStyle(LockrootTheme.muted)
                    .padding(.horizontal, 16)
                ForEach([MacRoute.masterPassword, .settings], id: \.self) { item in
                    Button {
                        activateSidebar(item)
                    } label: {
                        SidebarRow(title: item.title, systemImage: item.icon, selected: route == item)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)

            Spacer()

            SoftCard(padding: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "shield.fill")
                        .foregroundStyle(.white)
                        .frame(width: 42, height: 42)
                        .background(LockrootTheme.green)
                        .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Your data is safe")
                            .font(.system(size: 13, weight: .bold))
                        Text("Vault data is encrypted locally.")
                            .font(.system(size: 12))
                            .foregroundStyle(LockrootTheme.muted)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(LockrootTheme.sidebar)
    }

    private var mainContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                toolbar
                if route == .settings {
                    SettingsPanel(
                        onExport: { showingExportPassword = true },
                        onImport: { showingImportPicker = true },
                        onMaster: { showingMasterPassword = true }
                    )
                } else if route == .allEntries {
                    Text("All Entries")
                        .font(.system(size: 34, weight: .black, design: .rounded))
                    entriesPanel
                } else {
                    hero
                    stats
                    actions
                    entriesPanel
                }
            }
            .padding(.horizontal, 32)
            .padding(.vertical, 28)
        }
    }

    private var toolbar: some View {
        HStack(spacing: 18) {
            HStack(spacing: 12) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(LockrootTheme.muted)
                TextField("Search entries...", text: $viewModel.query)
                    .textFieldStyle(.plain)
                Text("⌘ K")
                    .font(.caption)
                    .foregroundStyle(LockrootTheme.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.04))
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            .background(LockrootTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(LockrootTheme.line))
            .frame(maxWidth: 560)

            Spacer()

            Button {
                editingEntry = .empty
            } label: {
                Label("Add Entry", systemImage: "plus")
            }
            .buttonStyle(PrimaryButtonStyle())

            Button {
                viewModel.lock()
            } label: {
                Image(systemName: "lock.fill")
                    .frame(width: 42, height: 42)
            }
            .buttonStyle(SecondaryButtonStyle())
            .help("Lock vault")
        }
    }

    private var hero: some View {
        SoftCard(padding: 0) {
            HStack(spacing: 28) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(LockrootTheme.green)
                    .frame(width: 150, height: 130)
                    .background(
                        Circle()
                            .fill(LockrootTheme.softGreen)
                            .frame(width: 130, height: 130)
                    )
                VStack(alignment: .leading, spacing: 10) {
                    Text("Welcome back!")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(LockrootTheme.ink)
                    Text("Access, manage and protect your passwords, all in one secure place.")
                        .font(.system(size: 15))
                        .foregroundStyle(LockrootTheme.muted)
                }
                Spacer()
            }
            .padding(26)
            .background(
                LinearGradient(
                    colors: [LockrootTheme.softGreen.opacity(0.85), .white],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
        }
    }

    private var stats: some View {
        LazyVGrid(columns: contentColumns, spacing: 14) {
            statCard("Total Entries", value: "\(viewModel.entries.count)", icon: "folder.badge.key")
            statCard("Vault", value: "Local", icon: "lock.shield")
            statCard("Clipboard", value: "20s", icon: "doc.on.clipboard")
        }
    }

    private func statCard(_ title: String, value: String, icon: String) -> some View {
        SoftCard {
            HStack(spacing: 15) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundStyle(LockrootTheme.green)
                    .frame(width: 48, height: 48)
                    .background(LockrootTheme.softGreen)
                    .clipShape(Circle())
                VStack(alignment: .leading, spacing: 4) {
                    Text(value)
                        .font(.system(size: 26, weight: .bold))
                    Text(title)
                        .font(.system(size: 13))
                        .foregroundStyle(LockrootTheme.muted)
                }
                Spacer()
            }
        }
    }

    private var actions: some View {
        LazyVGrid(columns: contentColumns, spacing: 14) {
            actionCard("Search", subtitle: "Find your entries", icon: "magnifyingglass") {
                route = .allEntries
            }
            actionCard("Add Entry", subtitle: "Create a new entry", icon: "plus") {
                editingEntry = .empty
            }
            actionCard("Import", subtitle: "Preview encrypted file", icon: "square.and.arrow.down") {
                showingImportPicker = true
            }
            actionCard("Password Generator", subtitle: "Generate strong passwords", icon: "sparkles") {
                showingGenerator = true
            }
            actionCard("Export", subtitle: "Create encrypted file", icon: "square.and.arrow.up") {
                showingExportPassword = true
            }
            actionCard("Master Password", subtitle: "Re-key your vault", icon: "key") {
                showingMasterPassword = true
            }
        }
    }

    private func actionCard(_ title: String, subtitle: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            SoftCard {
                HStack(spacing: 16) {
                    Image(systemName: icon)
                        .font(.title2)
                        .foregroundStyle(LockrootTheme.green)
                        .frame(width: 50, height: 50)
                        .background(LockrootTheme.softGreen)
                        .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 6) {
                        Text(title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(LockrootTheme.ink)
                        Text(subtitle)
                            .font(.system(size: 13))
                            .foregroundStyle(LockrootTheme.muted)
                    }
                    Spacer()
                }
            }
        }
        .buttonStyle(.plain)
    }

    private var entriesPanel: some View {
        SoftCard(padding: 22) {
            if viewModel.filteredEntries.isEmpty {
                VStack(spacing: 14) {
                    Image(systemName: "lock.square.stack.fill")
                        .font(.system(size: 66))
                        .foregroundStyle(LockrootTheme.green)
                    Text("No entries yet")
                        .font(.system(size: 24, weight: .bold))
                    Text("Add your first entry to get started.")
                        .foregroundStyle(LockrootTheme.muted)
                    Button {
                        editingEntry = .empty
                    } label: {
                        Label("Add Entry", systemImage: "plus")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 60)
            } else {
                LazyVGrid(columns: contentColumns, spacing: 14) {
                    ForEach(viewModel.filteredEntries) { entry in
                        EntryCard(entry: entry) {
                            editingEntry = entry
                        }
                    }
                }
            }
        }
    }

    private func activateSidebar(_ item: MacRoute) {
        switch item {
        case .home, .allEntries, .settings:
            route = item
        case .generator:
            showingGenerator = true
        case .importVault:
            showingImportPicker = true
        case .exportVault:
            showingExportPassword = true
        case .masterPassword:
            showingMasterPassword = true
        }
    }
}

struct EntryCard: View {
    let entry: VaultEntry
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            SoftCard {
                HStack(spacing: 14) {
                    Image(systemName: "key.horizontal.fill")
                        .font(.title3)
                        .foregroundStyle(LockrootTheme.green)
                        .frame(width: 44, height: 44)
                        .background(LockrootTheme.softGreen)
                        .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 5) {
                        Text(entry.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(LockrootTheme.ink)
                            .lineLimit(1)
                        Text(entry.username.isEmpty ? entry.website : entry.username)
                            .font(.system(size: 13))
                            .foregroundStyle(LockrootTheme.muted)
                            .lineLimit(1)
                    }
                    Spacer()
                }
            }
        }
        .buttonStyle(.plain)
    }
}
