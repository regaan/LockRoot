import Foundation
import SwiftUI

struct EntryEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: VaultEntry
    let onSave: (VaultEntry) -> Void
    let onDelete: (VaultEntry) -> Void

    init(entry: VaultEntry, onSave: @escaping (VaultEntry) -> Void, onDelete: @escaping (VaultEntry) -> Void) {
        _draft = State(initialValue: entry)
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Entry") {
                    TextField("Title", text: $draft.title)
                        .foregroundStyle(LockrootTheme.ink)
                    TextField("Website", text: $draft.website)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .foregroundStyle(LockrootTheme.ink)
                    TextField("Username", text: $draft.username)
                        .textInputAutocapitalization(.never)
                        .foregroundStyle(LockrootTheme.ink)
                    SecureField("Password", text: $draft.password)
                        .foregroundStyle(LockrootTheme.ink)
                }

                Section("Notes") {
                    TextField("Notes", text: $draft.notes, axis: .vertical)
                        .lineLimit(4...8)
                        .foregroundStyle(LockrootTheme.ink)
                }

                Section("Tags") {
                    TextField("Comma separated tags", text: Binding(
                        get: { draft.tags.joined(separator: ", ") },
                        set: {
                            draft.tags = $0
                                .split(separator: ",")
                                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                                .filter { !$0.isEmpty }
                            }
                    ))
                    .foregroundStyle(LockrootTheme.ink)
                }

                if !draft.title.isEmpty {
                    Section {
                        Button("Delete Entry", role: .destructive) {
                            onDelete(draft)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle(draft.title.isEmpty ? "Add Entry" : "Edit Entry")
            .lockrootFormStyle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        onSave(draft)
                        dismiss()
                    }
                    .disabled(draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
