using System.Security.Cryptography;

namespace Lockroot.Linux.Security;

public sealed class KdfParams
{
    public const int MinMemoryKiB = 19_456;
    public const int MaxMemoryKiB = 262_144;
    public const int MinIterations = 2;
    public const int MaxIterations = 10;
    public const int MinParallelism = 1;
    public const int MaxParallelism = 8;
    public const int MinSaltBytes = 16;
    public const int MaxSaltBytes = 64;

    public int MemoryKiB { get; set; }
    public int Iterations { get; set; }
    public int Parallelism { get; set; }
    public byte[] Salt { get; set; } = [];

    public void Validate()
    {
        if (MemoryKiB is < MinMemoryKiB or > MaxMemoryKiB)
        {
            throw new CryptographicException("Unsupported Argon2id memory parameter.");
        }

        if (Iterations is < MinIterations or > MaxIterations)
        {
            throw new CryptographicException("Unsupported Argon2id iteration parameter.");
        }

        if (Parallelism is < MinParallelism or > MaxParallelism)
        {
            throw new CryptographicException("Unsupported Argon2id parallelism parameter.");
        }

        if (Salt.Length is < MinSaltBytes or > MaxSaltBytes)
        {
            throw new CryptographicException("Unsupported Argon2id salt length.");
        }
    }
}
