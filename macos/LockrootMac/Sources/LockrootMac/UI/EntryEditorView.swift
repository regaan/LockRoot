import SwiftUI

struct EntryEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var draft: VaultEntry
    @State private var showingPassword = false
    let onSave: (VaultEntry) -> Void
    let onDelete: (VaultEntry) -> Void

    init(entry: VaultEntry, onSave: @escaping (VaultEntry) -> Void, onDelete: @escaping (VaultEntry) -> Void) {
        _draft = State(initialValue: entry)
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text(draft.title.isEmpty ? "Add Entry" : "Edit Entry")
                        .font(.system(size: 30, weight: .black, design: .rounded))
                    Text("Store the complete login inside your encrypted vault.")
                        .foregroundStyle(LockrootTheme.muted)
                }
                Spacer()
                Button("Cancel") { dismiss() }
                Button("Save") {
                    onSave(normalizedDraft)
                    dismiss()
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(draft.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    SoftCard {
                        VStack(alignment: .leading, spacing: 16) {
                            field("Title", text: $draft.title, prompt: "GitHub")
                            field("Website", text: $draft.website, prompt: "https://github.com")
                            field("Username", text: $draft.username, prompt: "regaan")

                            VStack(alignment: .leading, spacing: 8) {
                                Text("Password")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(LockrootTheme.muted)
                                HStack {
                                    if showingPassword {
                                        TextField("Password", text: $draft.password)
                                            .textFieldStyle(.roundedBorder)
                                    } else {
                                        SecureField("Password", text: $draft.password)
                                            .textFieldStyle(.roundedBorder)
                                    }
                                    Button {
                                        showingPassword.toggle()
                                    } label: {
                                        Image(systemName: showingPassword ? "eye.slash" : "eye")
                                    }
                                    .buttonStyle(SecondaryButtonStyle())
                                }
                            }
                        }
                    }

                    SoftCard {
                        VStack(alignment: .leading, spacing: 16) {
                            Text("Notes")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(LockrootTheme.muted)
                            TextEditor(text: $draft.notes)
                                .font(.system(size: 14))
                                .frame(minHeight: 120)
                                .scrollContentBackground(.hidden)
                                .background(Color.black.opacity(0.025))
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

                            field("Tags", text: Binding(
                                get: { draft.tags.joined(separator: ", ") },
                                set: {
                                    draft.tags = $0
                                        .split(separator: ",")
                                        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                                        .filter { !$0.isEmpty }
                                }
                            ), prompt: "work, personal")
                        }
                    }

                    if !draft.title.isEmpty {
                        Button("Delete Entry", role: .destructive) {
                            onDelete(draft)
                            dismiss()
                        }
                    }
                }
            }
        }
        .padding(26)
        .background(LockrootTheme.background)
    }

    private var normalizedDraft: VaultEntry {
        var copy = draft
        copy.title = copy.title.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.website = copy.website.trimmingCharacters(in: .whitespacesAndNewlines)
        copy.username = copy.username.trimmingCharacters(in: .whitespacesAndNewlines)
        return copy
    }

    private func field(_ title: String, text: Binding<String>, prompt: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(LockrootTheme.muted)
            TextField(prompt, text: text)
                .textFieldStyle(.roundedBorder)
        }
    }
}
