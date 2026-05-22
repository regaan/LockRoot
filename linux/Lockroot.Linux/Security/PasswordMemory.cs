using System.Runtime.InteropServices;
using System.Security.Cryptography;

namespace Lockroot.Linux.Security;

public static class PasswordMemory
{
    public static char[] FromString(string? value) =>
        string.IsNullOrEmpty(value) ? [] : value.ToCharArray();

    public static void Wipe(char[] value)
    {
        if (value.Length == 0)
        {
            return;
        }

        CryptographicOperations.ZeroMemory(MemoryMarshal.AsBytes(value.AsSpan()));
    }
}
