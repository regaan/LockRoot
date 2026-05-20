using System.IO;

namespace Lockroot.Windows.Vault;

public sealed class VaultStorage
{
    public string VaultPath { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
        "Lockroot",
        "lockroot.vault");

    public bool Exists() => File.Exists(VaultPath);

    public byte[] Read() => File.ReadAllBytes(VaultPath);

    public void Write(byte[] bytes)
    {
        var directory = Path.GetDirectoryName(VaultPath)
                        ?? throw new InvalidOperationException("Invalid vault path.");
        Directory.CreateDirectory(directory);

        var tempPath = VaultPath + ".tmp";
        File.WriteAllBytes(tempPath, bytes);

        if (File.Exists(VaultPath))
        {
            File.Replace(tempPath, VaultPath, null);
        }
        else
        {
            File.Move(tempPath, VaultPath);
        }
    }
}
