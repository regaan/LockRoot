import SwiftUI

struct EntryCard: View {
    @EnvironmentObject private var viewModel: VaultViewModel
    let entry: VaultEntry
    let onEdit: () -> Void

    var body: some View {
        SoftCard {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(entry.title.isEmpty ? "Untitled" : entry.title)
                            .font(.headline)
                        if !entry.website.isEmpty {
                            Text(entry.website)
                                .font(.subheadline)
                                .foregroundStyle(LockrootTheme.muted)
                        }
                    }
                    Spacer()
                    Button(action: onEdit) {
                        Image(systemName: "square.and.pencil")
                    }
                    .foregroundStyle(LockrootTheme.green)
                }

                if !entry.username.isEmpty {
                    HStack {
                        Text(entry.username)
                            .foregroundStyle(LockrootTheme.muted)
                            .lineLimit(1)
                        Spacer()
                        Button("Copy user") {
                            viewModel.copyToClipboard(entry.username)
                        }
                        .font(.caption.weight(.bold))
                        .foregroundStyle(LockrootTheme.green)
                    }
                }

                HStack {
                    Text("Password")
                        .foregroundStyle(LockrootTheme.muted)
                    Spacer()
                    Button("Copy password") {
                        viewModel.copyToClipboard(entry.password)
                    }
                    .font(.caption.weight(.bold))
                    .foregroundStyle(LockrootTheme.green)
                }

                if !entry.tags.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(entry.tags, id: \.self) { tag in
                                Text(tag)
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(LockrootTheme.softGreen)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
            }
        }
    }
}
