using Lockroot.Linux.Models;
using Lockroot.Linux.Security;

namespace Lockroot.Linux.Vault;

public sealed class VaultRepository : IDisposable
{
    private readonly VaultStorage _storage = new();
    private readonly VaultFileCodec _codec = new();
    private VaultSession? _session;

    public bool HasVault => _storage.Exists();
    public bool IsUnlocked => _session is not null;
    public VaultDocument CurrentVault => _session?.Vault ?? throw new InvalidOperationException("Vault is locked.");
    public string VaultPath => _storage.VaultPath;

    public void Create(ReadOnlySpan<char> password)
    {
        var vault = new VaultDocument();
        var sessionData = _codec.CreateSession(vault, password);

        _storage.Write(sessionData.Bytes);
        _session?.Dispose();
        _session = new VaultSession(vault, sessionData.Key, sessionData.KdfParams);
    }

    public void Unlock(ReadOnlySpan<char> password)
    {
        var encrypted = _storage.Read();
        var session = _codec.DecryptSession(encrypted, password);
        _session?.Dispose();
        _session = session;
        if (_codec.NeedsMigration(encrypted))
        {
            Save();
        }
    }

    public void Lock()
    {
        _session?.Dispose();
        _session = null;
    }

    public void Upsert(VaultEntry entry)
    {
        var vault = CurrentVault;
        var existing = vault.Entries.FindIndex(item => item.Id == entry.Id);
        var saved = entry.Clone();
        saved.UpdatedAt = DateTimeOffset.UtcNow;

        if (existing >= 0)
        {
            vault.Entries[existing] = saved;
        }
        else
        {
            saved.CreatedAt = DateTimeOffset.UtcNow;
            vault.Entries.Add(saved);
        }

        Save();
    }

    public void Delete(string id)
    {
        CurrentVault.Entries.RemoveAll(entry => entry.Id == id);
        Save();
    }

    public void ClearAll()
    {
        CurrentVault.Entries.Clear();
        Save();
    }

    public byte[] Export(ReadOnlySpan<char> exportPassword) =>
        _codec.Encrypt(Clone(CurrentVault), exportPassword, VaultFileCodec.ExportMagic);

    public VaultDocument DecryptExport(byte[] bytes, ReadOnlySpan<char> exportPassword) =>
        _codec.Decrypt(bytes, exportPassword, VaultFileCodec.ExportMagic);

    public int Merge(VaultDocument imported)
    {
        var existingIds = CurrentVault.Entries.Select(entry => entry.Id).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var added = 0;

        foreach (var entry in imported.Entries)
        {
            var clone = entry.Clone();
            if (existingIds.Contains(clone.Id))
            {
                clone.Id = Guid.NewGuid().ToString("N");
            }

            CurrentVault.Entries.Add(clone);
            added++;
        }

        Save();
        return added;
    }

    public void Replace(VaultDocument imported)
    {
        CurrentVault.Entries = imported.Entries.Select(entry => entry.Clone()).ToList();
        Save();
    }

    public void ChangeMasterPassword(ReadOnlySpan<char> currentPassword, ReadOnlySpan<char> newPassword)
    {
        var verified = _codec.DecryptSession(_storage.Read(), currentPassword);
        verified.Dispose();

        var bytes = _codec.Encrypt(Clone(CurrentVault), newPassword);
        _storage.Write(bytes);
        Lock();
        Unlock(newPassword);
    }

    public void Save()
    {
        var session = _session ?? throw new InvalidOperationException("Vault is locked.");
        var bytes = _codec.EncryptWithKey(session.Vault, session.Key, session.KdfParams);
        _storage.Write(bytes);
    }

    public void Dispose() => Lock();

    private static VaultDocument Clone(VaultDocument source) =>
        new()
        {
            Version = source.Version,
            CreatedAt = source.CreatedAt,
            UpdatedAt = source.UpdatedAt,
            Entries = source.Entries.Select(entry => entry.Clone()).ToList()
        };
}
