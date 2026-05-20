namespace Lockroot.Linux.Security;

public sealed class VaultEnvelope
{
    public string Magic { get; set; } = "";
    public int Version { get; set; }
    public string Kdf { get; set; } = "";
    public Argon2idEnvelope Argon2id { get; set; } = new();
    public string Cipher { get; set; } = "";
    public string Nonce { get; set; } = "";
    public string Ciphertext { get; set; } = "";
    public string Tag { get; set; } = "";
}

public sealed class Argon2idEnvelope
{
    public int Memory { get; set; }
    public int Iterations { get; set; }
    public int Parallelism { get; set; }
    public string Salt { get; set; } = "";
}
