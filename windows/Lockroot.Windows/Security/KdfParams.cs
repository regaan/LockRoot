namespace Lockroot.Windows.Security;

public sealed class KdfParams
{
    public int MemoryKiB { get; init; } = 65_536;
    public int Iterations { get; init; } = 3;
    public int Parallelism { get; init; } = 2;
    public byte[] Salt { get; init; } = [];
}
