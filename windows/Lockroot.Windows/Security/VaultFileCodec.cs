using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Lockroot.Windows.Models;

namespace Lockroot.Windows.Security;

public sealed class VaultFileCodec
{
    public const string VaultMagic = "LOCKROOT";
    public const string ExportMagic = "LOCKROOT-EXPORT";

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly CryptoService _crypto = new();

    public (byte[] Bytes, byte[] Key, KdfParams KdfParams) CreateSession(VaultDocument vault, string password)
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

    public VaultSession DecryptSession(byte[] bytes, string password)
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

    public byte[] EncryptWithKey(VaultDocument vault, byte[] key, KdfParams kdfParams, string magic = VaultMagic)
    {
        vault.UpdatedAt = DateTimeOffset.UtcNow;

        var plaintext = JsonSerializer.SerializeToUtf8Bytes(vault, JsonOptions);
        var nonce = RandomNumberGenerator.GetBytes(CryptoService.NonceBytes);
        var envelope = CreateEnvelope(magic, kdfParams, nonce);

        try
        {
            var aad = AssociatedData(envelope);
            var payload = _crypto.Encrypt(plaintext, key, nonce, aad);
            envelope.Ciphertext = Convert.ToBase64String(payload.Ciphertext);
            envelope.Tag = Convert.ToBase64String(payload.Tag);
            return JsonSerializer.SerializeToUtf8Bytes(envelope, JsonOptions);
        }
        finally
        {
            CryptoService.Wipe(plaintext);
            CryptoService.Wipe(nonce);
        }
    }

    public byte[] Encrypt(VaultDocument vault, string password, string magic = VaultMagic)
    {
        var kdfParams = _crypto.CreateKdfParams();
        var key = _crypto.DeriveKey(password, kdfParams);

        try
        {
            return EncryptWithKey(vault, key, kdfParams, magic);
        }
        finally
        {
            CryptoService.Wipe(key);
        }
    }

    public VaultDocument Decrypt(byte[] bytes, string password, string magic = VaultMagic)
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

    private static VaultEnvelope DecodeEnvelope(byte[] bytes) =>
        JsonSerializer.Deserialize<VaultEnvelope>(bytes, JsonOptions)
        ?? throw new CryptographicException("Invalid vault file.");

    private static VaultEnvelope CreateEnvelope(string magic, KdfParams kdfParams, byte[] nonce) =>
        new()
        {
            Magic = magic,
            Version = 1,
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
        if (envelope.Magic != expectedMagic)
        {
            throw new CryptographicException("Unsupported vault file type.");
        }

        if (envelope.Version != 1 || envelope.Kdf != CryptoService.KdfName || envelope.Cipher != CryptoService.CipherName)
        {
            throw new CryptographicException("Unsupported vault format.");
        }
    }

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
