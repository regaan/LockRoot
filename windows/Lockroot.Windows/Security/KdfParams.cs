using System.Security.Cryptography;

namespace Lockroot.Windows.Security;

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

    private int _memoryKiB = 65_536;
    private int _iterations = 3;
    private int _parallelism = 2;
    private byte[] _salt = [];

    public int MemoryKiB
    {
        get => _memoryKiB;
        init
        {
            if (value is < MinMemoryKiB or > MaxMemoryKiB)
            {
                throw new CryptographicException("Unsupported Argon2id memory cost.");
            }

            _memoryKiB = value;
        }
    }

    public int Iterations
    {
        get => _iterations;
        init
        {
            if (value is < MinIterations or > MaxIterations)
            {
                throw new CryptographicException("Unsupported Argon2id iteration count.");
            }

            _iterations = value;
        }
    }

    public int Parallelism
    {
        get => _parallelism;
        init
        {
            if (value is < MinParallelism or > MaxParallelism)
            {
                throw new CryptographicException("Unsupported Argon2id parallelism.");
            }

            _parallelism = value;
        }
    }

    public byte[] Salt
    {
        get => _salt;
        init
        {
            if (value.Length is < MinSaltBytes or > MaxSaltBytes)
            {
                throw new CryptographicException("Unsupported Argon2id salt length.");
            }

            _salt = value.ToArray();
        }
    }

    public void Validate()
    {
        _ = new KdfParams
        {
            MemoryKiB = MemoryKiB,
            Iterations = Iterations,
            Parallelism = Parallelism,
            Salt = Salt
        };
    }
}
