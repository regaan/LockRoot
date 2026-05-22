using System.Security.Cryptography;
using System.Text.Json;
using Lockroot.Windows.Models;
using WindowsCodec = Lockroot.Windows.Security.VaultFileCodec;
using LinuxCodec = Lockroot.Linux.Security.VaultFileCodec;
using LinuxVault = Lockroot.Linux.Models.VaultDocument;
using LinuxEntry = Lockroot.Linux.Models.VaultEntry;

RunWindowsCodecTests();
RunLinuxCodecTests();
Console.WriteLine("Desktop crypto smoke tests passed.");

static void RunWindowsCodecTests()
{
    var codec = new WindowsCodec();
    var password = "correct horse battery staple".ToCharArray();
    var wrongPassword = "wrong horse battery staple".ToCharArray();
    var vault = new VaultDocument
    {
        Entries =
        [
            new VaultEntry
            {
                Title = "GitHub",
                Username = "regaan",
                Password = "secret",
                Website = "github.com"
            }
        ]
    };

    var encrypted = codec.Encrypt(vault, password);
    AssertEnvelope(encrypted, WindowsCodec.VaultMagic, "aes-256-gcm", WindowsCodec.CurrentVersion);

    var decrypted = codec.Decrypt(encrypted, password);
    Require(decrypted.Entries.Count == 1, "Windows round-trip failed.");
    Require(decrypted.Entries[0].Password == "secret", "Windows password payload changed.");
    RequireThrows<CryptographicException>(() => codec.Decrypt(encrypted, wrongPassword), "Windows wrong password was accepted.");

    var tampered = TamperCiphertext(encrypted);
    RequireThrows<CryptographicException>(() => codec.Decrypt(tampered, password), "Windows tampered ciphertext was accepted.");

    var v1Encrypted = codec.Encrypt(vault, password, version: 1);
    Require(codec.NeedsMigration(v1Encrypted), "Windows v1 vault was not marked for migration.");
    Require(codec.Decrypt(v1Encrypted, password).Entries[0].Password == "secret", "Windows v1 vault did not decrypt.");
}

static void RunLinuxCodecTests()
{
    var codec = new LinuxCodec();
    var password = "correct horse battery staple".ToCharArray();
    var wrongPassword = "wrong horse battery staple".ToCharArray();
    var vault = new LinuxVault
    {
        Entries =
        [
            new LinuxEntry
            {
                Title = "Email",
                Username = "regaan",
                Password = "secret",
                Website = "example.com"
            }
        ]
    };

    var encrypted = codec.Encrypt(vault, password);
    AssertEnvelope(encrypted, LinuxCodec.VaultMagic, "aes-256-gcm", LinuxCodec.CurrentVersion);

    var decrypted = codec.Decrypt(encrypted, password);
    Require(decrypted.Entries.Count == 1, "Linux round-trip failed.");
    Require(decrypted.Entries[0].Password == "secret", "Linux password payload changed.");
    RequireThrows<CryptographicException>(() => codec.Decrypt(encrypted, wrongPassword), "Linux wrong password was accepted.");

    var tampered = TamperCiphertext(encrypted);
    RequireThrows<CryptographicException>(() => codec.Decrypt(tampered, password), "Linux tampered ciphertext was accepted.");

    var v1Encrypted = codec.Encrypt(vault, password, version: 1);
    Require(codec.NeedsMigration(v1Encrypted), "Linux v1 vault was not marked for migration.");
    Require(codec.Decrypt(v1Encrypted, password).Entries[0].Password == "secret", "Linux v1 vault did not decrypt.");
}

static void AssertEnvelope(byte[] bytes, string expectedMagic, string expectedCipher, int expectedVersion)
{
    using var document = JsonDocument.Parse(bytes);
    var root = document.RootElement;
    Require(root.GetProperty("magic").GetString() == expectedMagic, "Envelope magic mismatch.");
    Require(root.GetProperty("version").GetInt32() == expectedVersion, "Envelope version mismatch.");
    Require(root.GetProperty("kdf").ValueKind == JsonValueKind.Object, "Portable KDF object missing.");
    Require(root.GetProperty("kdf").GetProperty("name").GetString() == "argon2id", "KDF name mismatch.");
    Require(root.GetProperty("cipher").ValueKind == JsonValueKind.Object, "Portable cipher object missing.");
    Require(root.GetProperty("cipher").GetProperty("name").GetString() == expectedCipher, "Cipher name mismatch.");
}

static byte[] TamperCiphertext(byte[] bytes)
{
    using var document = JsonDocument.Parse(bytes);
    var root = document.RootElement;
    var parsed = JsonSerializer.Deserialize<Dictionary<string, object?>>(root.GetRawText())
                 ?? throw new InvalidOperationException("Could not parse envelope.");

    var ciphertext = root.GetProperty("ciphertext").GetString() ?? "";
    var replacement = ciphertext.Length > 4
        ? $"{(ciphertext[0] == 'A' ? 'B' : 'A')}{ciphertext[1..]}"
        : "AAAA";

    parsed["ciphertext"] = replacement;
    return JsonSerializer.SerializeToUtf8Bytes(parsed, new JsonSerializerOptions { WriteIndented = true });
}

static void Require(bool condition, string message)
{
    if (!condition)
    {
        throw new InvalidOperationException(message);
    }
}

static void RequireThrows<TException>(Action action, string message)
    where TException : Exception
{
    try
    {
        action();
    }
    catch (TException)
    {
        return;
    }

    throw new InvalidOperationException(message);
}
