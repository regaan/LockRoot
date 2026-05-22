namespace Lockroot.Linux.Security;

public sealed class VaultEnvelope
{
    public string Magic { get; set; } = VaultFileCodec.VaultMagic;
    public int Version { get; set; } = VaultFileCodec.CurrentVersion;
    public string Kdf { get; set; } = CryptoService.KdfName;
    public Argon2idEnvelope Argon2id { get; set; } = new();
    public string Cipher { get; set; } = CryptoService.CipherName;
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
