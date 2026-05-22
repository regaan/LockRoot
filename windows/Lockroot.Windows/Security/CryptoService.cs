using System.Security.Cryptography;
using System.Text;
using Org.BouncyCastle.Crypto.Generators;
using Org.BouncyCastle.Crypto.Parameters;

namespace Lockroot.Windows.Security;

public sealed class CryptoService
{
    public const string KdfName = "argon2id";
    public const string CipherName = "aes-256-gcm";
    public const int KeyBytes = 32;
    public const int NonceBytes = 12;
    public const int TagBytes = 16;

    public KdfParams CreateKdfParams() =>
        new()
        {
            MemoryKiB = 65_536,
            Iterations = 3,
            Parallelism = 2,
            Salt = RandomNumberGenerator.GetBytes(32)
        };

    public byte[] DeriveKey(ReadOnlySpan<char> password, KdfParams parameters)
    {
        if (IsEmptyOrWhiteSpace(password))
        {
            throw new ArgumentException("Password is required.", nameof(password));
        }

        parameters.Validate();

        var passwordBytes = GC.AllocateArray<byte>(Encoding.UTF8.GetByteCount(password), pinned: true);
        var key = GC.AllocateArray<byte>(KeyBytes, pinned: true);

        try
        {
            Encoding.UTF8.GetBytes(password, passwordBytes);

            var generator = new Argon2BytesGenerator();
            var argon2Params = new Argon2Parameters.Builder(Argon2Parameters.Argon2id)
                .WithVersion(Argon2Parameters.Version13)
                .WithMemoryAsKB(parameters.MemoryKiB)
                .WithIterations(parameters.Iterations)
                .WithParallelism(parameters.Parallelism)
                .WithSalt(parameters.Salt)
                .Build();

            generator.Init(argon2Params);
            generator.GenerateBytes(passwordBytes, key);
            return key;
        }
        finally
        {
            CryptographicOperations.ZeroMemory(passwordBytes);
        }
    }

    public (byte[] Ciphertext, byte[] Tag) Encrypt(byte[] plaintext, byte[] key, byte[] nonce, byte[] associatedData)
    {
        ValidateAesInputs(key, nonce);
        var ciphertext = new byte[plaintext.Length];
        var tag = new byte[TagBytes];

        using var aes = new AesGcm(key, TagBytes);
        aes.Encrypt(nonce, plaintext, ciphertext, tag, associatedData);
        return (ciphertext, tag);
    }

    public byte[] Decrypt(byte[] ciphertext, byte[] tag, byte[] key, byte[] nonce, byte[] associatedData)
    {
        ValidateAesInputs(key, nonce);

        if (tag.Length != TagBytes)
        {
            throw new CryptographicException("Invalid authentication tag.");
        }

        var plaintext = new byte[ciphertext.Length];
        using var aes = new AesGcm(key, TagBytes);
        aes.Decrypt(nonce, ciphertext, tag, plaintext, associatedData);
        return plaintext;
    }

    public static void Wipe(byte[] value)
    {
        if (value.Length == 0)
        {
            return;
        }

        RandomNumberGenerator.Fill(value);
        CryptographicOperations.ZeroMemory(value);
    }

    private static void ValidateAesInputs(byte[] key, byte[] nonce)
    {
        if (key.Length != KeyBytes)
        {
            throw new CryptographicException("AES-256-GCM key must be 32 bytes.");
        }

        if (nonce.Length != NonceBytes)
        {
            throw new CryptographicException("AES-GCM nonce must be 12 bytes.");
        }
    }

    private static bool IsEmptyOrWhiteSpace(ReadOnlySpan<char> value)
    {
        if (value.IsEmpty)
        {
            return true;
        }

        foreach (var character in value)
        {
            if (!char.IsWhiteSpace(character))
            {
                return false;
            }
        }

        return true;
    }
}
