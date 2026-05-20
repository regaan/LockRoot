namespace Lockroot.Linux.Models;

public sealed class VaultDocument
{
    public int Version { get; set; } = 1;
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public List<VaultEntry> Entries { get; set; } = [];
}
