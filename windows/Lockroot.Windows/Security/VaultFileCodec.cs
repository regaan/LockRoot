using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Lockroot.Windows.Models;

namespace Lockroot.Windows.Security;

public sealed class VaultFileCodec
{
    public const string VaultMagic = "Lockroot_VAULT";
    public const string ExportMagic = "Lockroot_EXPORT";
    public const string LegacyVaultMagic = "LOCKROOT";
    public const string LegacyExportMagic = "LOCKROOT-EXPORT";
    public const int CurrentVersion = 2;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly CryptoService _crypto = new();

    public (byte[] Bytes, byte[] Key, KdfParams KdfParams) CreateSession(VaultDocument vault, ReadOnlySpan<char> password)
    {
        var kdfParams = _crypto.CreateKdfParams();
        var key = _crypto.DeriveKey(password, kdfParams);

        try
        {
            return (EncryptWithKey(vault, key, kdfParams, VaultMagic), key, kdfParams);
        }
        catch
        {
            CryptoService.Wipe(key);
            throw;
        }
    }

    public VaultSession DecryptSession(byte[] bytes, ReadOnlySpan<char> password)
    {
        var envelope = DecodeEnvelope(bytes);
        ValidateEnvelope(envelope, VaultMagic);
        var kdfParams = KdfFromEnvelope(envelope);
        var key = _crypto.DeriveKey(password, kdfParams);

        try
        {
            var vault = DecryptEnvelope(envelope, key);
            return new VaultSession(vault, key, kdfParams);
        }
        catch
        {
            CryptoService.Wipe(key);
            throw;
        }
    }

    public bool NeedsMigration(byte[] bytes, string expectedMagic = VaultMagic)
    {
        var envelope = DecodeEnvelope(bytes);
        return envelope.Magic != expectedMagic ||
               envelope.Version != CurrentVersion ||
               envelope.Cipher != CryptoService.CipherName;
    }

    public byte[] EncryptWithKey(
        VaultDocument vault,
        byte[] key,
        KdfParams kdfParams,
        string magic = VaultMagic,
        int version = CurrentVersion)
    {
        vault.UpdatedAt = DateTimeOffset.UtcNow;

        var plaintext = JsonSerializer.SerializeToUtf8Bytes(vault, JsonOptions);
        var nonce = RandomNumberGenerator.GetBytes(CryptoService.NonceBytes);
        var envelope = CreateEnvelope(magic, kdfParams, nonce, version);

        try
        {
            var aad = AssociatedData(envelope);
            var payload = _crypto.Encrypt(plaintext, key, nonce, aad);
            envelope.Ciphertext = Convert.ToBase64String(payload.Ciphertext);
            envelope.Tag = Convert.ToBase64String(payload.Tag);
            return EncodeEnvelope(envelope);
        }
        finally
        {
            CryptoService.Wipe(plaintext);
            CryptoService.Wipe(nonce);
        }
    }

    public byte[] Encrypt(
        VaultDocument vault,
        ReadOnlySpan<char> password,
        string magic = VaultMagic,
        int version = CurrentVersion)
    {
        var kdfParams = _crypto.CreateKdfParams();
        var key = _crypto.DeriveKey(password, kdfParams);

        try
        {
            return EncryptWithKey(vault, key, kdfParams, magic, version);
        }
        finally
        {
            CryptoService.Wipe(key);
        }
    }

    public VaultDocument Decrypt(byte[] bytes, ReadOnlySpan<char> password, string magic = VaultMagic)
    {
        var envelope = DecodeEnvelope(bytes);
        ValidateEnvelope(envelope, magic);
        var key = _crypto.DeriveKey(password, KdfFromEnvelope(envelope));

        try
        {
            return DecryptEnvelope(envelope, key);
        }
        finally
        {
            CryptoService.Wipe(key);
        }
    }

    private VaultDocument DecryptEnvelope(VaultEnvelope envelope, byte[] key)
    {
        var nonce = Convert.FromBase64String(envelope.Nonce);
        var ciphertext = Convert.FromBase64String(envelope.Ciphertext);
        var tag = Convert.FromBase64String(envelope.Tag);

        try
        {
            var plaintext = _crypto.Decrypt(ciphertext, tag, key, nonce, AssociatedData(envelope));
            try
            {
                return JsonSerializer.Deserialize<VaultDocument>(plaintext, JsonOptions)
                       ?? throw new CryptographicException("Invalid vault payload.");
            }
            finally
            {
                CryptoService.Wipe(plaintext);
            }
        }
        finally
        {
            CryptoService.Wipe(nonce);
            CryptoService.Wipe(ciphertext);
            CryptoService.Wipe(tag);
        }
    }

    private static VaultEnvelope DecodeEnvelope(byte[] bytes)
    {
        using var document = JsonDocument.Parse(bytes);
        var root = document.RootElement;
        var kdf = root.GetProperty("kdf");
        var cipher = root.GetProperty("cipher");

        string kdfName;
        Argon2idEnvelope argon2id;
        if (kdf.ValueKind == JsonValueKind.Object)
        {
            kdfName = RequiredString(kdf, "name");
            argon2id = new Argon2idEnvelope
            {
                Memory = kdf.GetProperty("memory").GetInt32(),
                Iterations = kdf.GetProperty("iterations").GetInt32(),
                Parallelism = kdf.GetProperty("parallelism").GetInt32(),
                Salt = RequiredString(kdf, "salt")
            };
        }
        else
        {
            kdfName = kdf.GetString() ?? "";
            var legacyArgon2id = root.GetProperty("argon2id");
            argon2id = new Argon2idEnvelope
            {
                Memory = legacyArgon2id.GetProperty("memory").GetInt32(),
                Iterations = legacyArgon2id.GetProperty("iterations").GetInt32(),
                Parallelism = legacyArgon2id.GetProperty("parallelism").GetInt32(),
                Salt = RequiredString(legacyArgon2id, "salt")
            };
        }

        var cipherName = cipher.ValueKind == JsonValueKind.Object ? RequiredString(cipher, "name") : cipher.GetString() ?? "";
        var nonce = cipher.ValueKind == JsonValueKind.Object ? RequiredString(cipher, "nonce") : RequiredString(root, "nonce");

        return new VaultEnvelope
        {
            Magic = RequiredString(root, "magic"),
            Version = root.GetProperty("version").GetInt32(),
            Kdf = kdfName,
            Argon2id = argon2id,
            Cipher = cipherName,
            Nonce = nonce,
            Ciphertext = RequiredString(root, "ciphertext"),
            Tag = RequiredString(root, "tag")
        };
    }

    private static byte[] EncodeEnvelope(VaultEnvelope envelope) =>
        JsonSerializer.SerializeToUtf8Bytes(
            new
            {
                magic = envelope.Magic,
                version = envelope.Version,
                kdf = new
                {
                    name = envelope.Kdf,
                    memory = envelope.Argon2id.Memory,
                    iterations = envelope.Argon2id.Iterations,
                    parallelism = envelope.Argon2id.Parallelism,
                    salt = envelope.Argon2id.Salt
                },
                cipher = new
                {
                    name = envelope.Cipher,
                    nonce = envelope.Nonce
                },
                ciphertext = envelope.Ciphertext,
                tag = envelope.Tag
            },
            JsonOptions);

    private static VaultEnvelope CreateEnvelope(string magic, KdfParams kdfParams, byte[] nonce, int version) =>
        new()
        {
            Magic = magic,
            Version = version,
            Kdf = CryptoService.KdfName,
            Cipher = CryptoService.CipherName,
            Nonce = Convert.ToBase64String(nonce),
            Argon2id = new Argon2idEnvelope
            {
                Memory = kdfParams.MemoryKiB,
                Iterations = kdfParams.Iterations,
                Parallelism = kdfParams.Parallelism,
                Salt = Convert.ToBase64String(kdfParams.Salt)
            }
        };

    private static void ValidateEnvelope(VaultEnvelope envelope, string expectedMagic)
    {
        if (envelope.Magic != expectedMagic && !IsAcceptedLegacyMagic(envelope.Magic, expectedMagic))
        {
            throw new CryptographicException("Unsupported vault file type.");
        }

        if ((envelope.Version != 1 && envelope.Version != CurrentVersion) ||
            envelope.Kdf != CryptoService.KdfName ||
            envelope.Cipher != CryptoService.CipherName)
        {
            throw new CryptographicException("Unsupported vault format.");
        }
    }

    private static bool IsAcceptedLegacyMagic(string actualMagic, string expectedMagic) =>
        (expectedMagic == VaultMagic && actualMagic == LegacyVaultMagic) ||
        (expectedMagic == ExportMagic && actualMagic == LegacyExportMagic);

    private static string RequiredString(JsonElement element, string propertyName) =>
        element.GetProperty(propertyName).GetString()
        ?? throw new CryptographicException("Invalid vault file.");

    private static KdfParams KdfFromEnvelope(VaultEnvelope envelope) =>
        new()
        {
            MemoryKiB = envelope.Argon2id.Memory,
            Iterations = envelope.Argon2id.Iterations,
            Parallelism = envelope.Argon2id.Parallelism,
            Salt = Convert.FromBase64String(envelope.Argon2id.Salt)
        };

    private static byte[] AssociatedData(VaultEnvelope envelope)
    {
        var aad = string.Join("|",
            envelope.Magic,
            envelope.Version,
            envelope.Kdf,
            envelope.Argon2id.Memory,
            envelope.Argon2id.Iterations,
            envelope.Argon2id.Parallelism,
            envelope.Argon2id.Salt,
            envelope.Cipher,
            envelope.Nonce);

        return Encoding.UTF8.GetBytes(aad);
    }
}

public sealed class VaultSession : IDisposable
{
    public VaultSession(VaultDocument vault, byte[] key, KdfParams kdfParams)
    {
        Vault = vault;
        Key = key;
        KdfParams = kdfParams;
    }

    public VaultDocument Vault { get; }
    public byte[] Key { get; }
    public KdfParams KdfParams { get; }

    public void Dispose() => CryptoService.Wipe(Key);
}
