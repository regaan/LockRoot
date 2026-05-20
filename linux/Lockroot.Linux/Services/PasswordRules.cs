namespace Lockroot.Linux.Services;

public static class PasswordRules
{
    public const int MinimumMasterPasswordLength = 12;

    public static PasswordStrength Evaluate(string password)
    {
        var score = 0;
        if (password.Length >= MinimumMasterPasswordLength) score++;
        if (password.Any(char.IsLower)) score++;
        if (password.Any(char.IsUpper)) score++;
        if (password.Any(char.IsDigit)) score++;
        if (password.Any(ch => !char.IsLetterOrDigit(ch))) score++;
        if (password.Length >= 20) score++;

        return score switch
        {
            <= 1 => new PasswordStrength("Weak password", 1, "#B42318"),
            2 or 3 => new PasswordStrength("Fair password", 2, "#B7791F"),
            4 => new PasswordStrength("Good password", 3, "#1D7F55"),
            _ => new PasswordStrength("Strong password", 4, "#0E6B49")
        };
    }
}

public sealed record PasswordStrength(string Label, int Bars, string Color);
