using System.Security.Cryptography;

namespace Lockroot.Linux.Services;

public sealed class PasswordGenerator
{
    public const int MinLength = 12;
    public const int MaxLength = 128;

    private const string Lowercase = "abcdefghijkmnopqrstuvwxyz";
    private const string Uppercase = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private const string Numbers = "23456789";
    private const string Symbols = "!@#$%^&*()-_=+[]{};:,.?";

    public string Generate(
        int length = 24,
        bool lowercase = true,
        bool uppercase = true,
        bool numbers = true,
        bool symbols = true)
    {
        var groups = new List<string>();
        if (lowercase) groups.Add(Lowercase);
        if (uppercase) groups.Add(Uppercase);
        if (numbers) groups.Add(Numbers);
        if (symbols) groups.Add(Symbols);

        if (length is < MinLength or > MaxLength)
        {
            throw new ArgumentOutOfRangeException(nameof(length), $"Password length must be between {MinLength} and {MaxLength} characters.");
        }

        if (groups.Count == 0)
        {
            throw new InvalidOperationException("Select at least one character group.");
        }

        var all = string.Concat(groups);
        var chars = new List<char>(length);
        chars.AddRange(groups.Select(Pick));

        while (chars.Count < length)
        {
            chars.Add(Pick(all));
        }

        Shuffle(chars);
        return new string(chars.ToArray());
    }

    private static char Pick(string chars) => chars[RandomNumberGenerator.GetInt32(chars.Length)];

    private static void Shuffle(IList<char> chars)
    {
        for (var index = chars.Count - 1; index > 0; index--)
        {
            var swap = RandomNumberGenerator.GetInt32(index + 1);
            (chars[index], chars[swap]) = (chars[swap], chars[index]);
        }
    }
}
