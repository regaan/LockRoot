namespace Lockroot.Linux.Services;

public static class LegalText
{
    public const string Terms =
        "Lockroot is a local password manager. Your master password is the only way to unlock your vault. There is no password recovery, no server-side reset, and no backdoor. If you lose the master password, the vault data cannot be recovered.\n\n" +
        "You are responsible for keeping your passwords, exports, backups, and devices secure. Encrypted exports require the export password used during export. A wrong password or modified file will fail authentication.\n\n" +
        "Lockroot stores vault data locally on this device. The app is provided as-is, without a guarantee that it can prevent compromise of an already infected or physically controlled device.";

    public const string Privacy =
        "Lockroot is designed as a local-only password manager. The Linux build does not require an account, cloud sync, telemetry, analytics, ads, or remote configuration.\n\n" +
        "Vault entries, notes, usernames, passwords, and tags are stored inside an encrypted local vault file. Clipboard contents are cleared automatically after a short delay when Lockroot copies a secret.\n\n" +
        "No app can promise protection from a fully compromised operating system, hostile accessibility tooling, or a user-selected weak password. Choose a strong master password and protect your device.";

    public const string About =
        "Lockroot\nSecure. Private. Yours.\n\n" +
        "Created by REGAAN, Security Researcher, Offensive Engineer, and Full-Stack Developer.\n\n" +
        "GitHub: github.com/regaan/LockRoot\nWebsite: rothackers.com\nContact: regaan48@gmail.com\n\n" +
        "Linux build: Avalonia UI, .NET, Argon2id, AES-256-GCM, local encrypted vault storage.";
}
