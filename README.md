# Lockroot

<p align="center">
  <img src="docs/assets/lockroot.png" alt="Lockroot logo" width="520">
</p>

<p align="center">
  <strong>Encryption does not save a master password like <code>password123</code>.</strong><br>
  <em>Lockroot can protect the vault properly, but it cannot make a weak password brave.</em>
</p>

A local password manager for Android, iOS, Windows, Linux, and macOS.

No account. No cloud. No analytics. No telemetry. No recovery backdoor. The vault lives on the device, encrypted with a master password. Lose the password, lose the vault. That's the deal.

## Download

| Platform | Link |
|---|---|
| iOS | [App Store](https://apps.apple.com/app/id6770449898) |
| Android | Google Play (internal testing) |
| Windows | [GitHub Releases](https://github.com/regaan/LockRoot/releases) |
| Linux | [GitHub Releases](https://github.com/regaan/LockRoot/releases) |
| macOS | Build from source (see below) |
| Website | [lockroot.rothackers.com](https://lockroot.rothackers.com) |

## What It Does

- Encrypted local vault, unlocked with a master password.
- Store titles, websites, usernames, passwords, notes, and tags.
- Add, edit, delete, search, and copy entries.
- Generate passwords with configurable length and character types.
- Auto-clear clipboard after copying secrets.
- Auto-lock on inactivity, minimize, or background (platform-dependent).
- Export encrypted backups with a separate export password.
- Import backups with preview, merge, or full replace.
- Shared vault envelope format across all platforms. Export and import works best between same-family platforms (mobile↔mobile, desktop↔desktop). Cross-family import works at the envelope level, but the inner payload shape has minor differences between mobile and desktop.

## How It Works

```
Master password → Argon2id (64 MiB, 3 passes) → 256-bit key → AES-256-GCM → encrypted vault
```

Exports use a different password and a different salt. Knowing one doesn't help with the other.

Wrong password = authentication failure, not garbage decryption. The AEAD tag catches it.

For the full cryptographic design, see [docs/crypto.md](docs/crypto.md).

## Vault Format

All platforms share the same v2 envelope format:

```json
{
  "magic": "Lockroot_VAULT",
  "version": 2,
  "kdf": { "name": "argon2id", "memory": 65536, ... },
  "cipher": { "name": "aes-256-gcm", "nonce": "..." },
  "ciphertext": "...",
  "tag": "..."
}
```

Older vault files (v1, different magic strings, XChaCha20-Poly1305) are migrated to v2 on unlock when, and only when, the current platform can decrypt the old file. Mobile platforms carry the XChaCha20 reader so they can migrate old mobile vaults. Desktop platforms only support AES-GCM, so they can migrate old desktop vaults (legacy magic/version) but not old mobile XChaCha vaults. For the full format specification, see [docs/vault_format.md](docs/vault_format.md).

## Security

See [docs/threat_model.md](docs/threat_model.md) for what Lockroot protects against and where the limits are. The short version:

**Protects:**
- Vault data at rest (AES-256-GCM + Argon2id).
- Offline brute-force (64 MiB memory-hard KDF).
- Envelope tampering (AEAD binds all metadata).
- Clipboard leaks (auto-clear + clear on app close).
- Screen capture on Android and Windows.

**Does not protect:**
- Compromised / rooted devices.
- Weak master passwords (minimum is 12 chars, no complexity rules).
- Keyloggers or malicious input methods.
- Someone watching the screen.
- Lost passwords — there is no recovery.

For vulnerability reports, see [SECURITY.md](SECURITY.md).

## Platform Notes

**Android** - No permissions. Screenshots blocked. Backup excluded. Import/export through system file picker.

**iOS** - Locks on background. Vault stored with `.completeFileProtection`. No screenshot blocking (iOS doesn't offer it).

**Windows** - WPF / .NET 8. Auto-lock on inactivity and minimize. Screen capture exclusion requested. Per-user installer (no admin).

**Linux** - Avalonia. Auto-lock on inactivity and minimize. No screen capture protection (X11 limitation).

**macOS** - SwiftUI. Sandboxed. Clipboard cleared on app close. Uses the shared v2 encrypted envelope and the same payload family as iOS.

## Build From Source

### Android

Open in Android Studio and run the `app` module. Or from terminal:

```bash
./gradlew clean testReleaseUnitTest assembleRelease bundleRelease
```

### iOS

```
ios/Lockroot/Lockroot.xcodeproj
```

Open in Xcode, resolve packages, select the `Lockroot` scheme, run.

### macOS

Xcode project:

```
macos/Lockroot/Lockroot.xcodeproj
```

Or the Swift package version:

```
macos/LockrootMac/Package.swift
```

### Windows

```powershell
dotnet restore .\windows\Lockroot.Windows\Lockroot.Windows.csproj --configfile .\windows\Lockroot.Windows\NuGet.Config -r win-x64
dotnet publish .\windows\Lockroot.Windows\Lockroot.Windows.csproj -c Release -r win-x64 --self-contained true
```

Build the installer:

```powershell
iscc .\windows\installer\lockroot.iss
```

### Linux

```bash
dotnet build ./linux/Lockroot.Linux/Lockroot.Linux.csproj -c Release
dotnet publish ./linux/Lockroot.Linux/Lockroot.Linux.csproj -c Release -r linux-x64 --self-contained true
```

Or grab a package from [GitHub Releases](https://github.com/regaan/LockRoot/releases):

```bash
# AppImage
chmod +x Lockroot-linux-x64-1.2.0.AppImage && ./Lockroot-linux-x64-1.2.0.AppImage

# Debian / Ubuntu
sudo apt install ./Lockroot-linux-x64-1.2.0.deb

# Fedora / RHEL
sudo dnf install ./Lockroot-linux-x64-1.2.0.rpm
```

## No Recovery

There is no forgot-password flow. No recovery key. No server backup. No admin reset.

If the master password is lost, the vault cannot be decrypted. Keep an encrypted export somewhere safe.

## Repository Layout

```
app/                        Android (Kotlin)
  src/main/java/.../
    crypto/                 Argon2id, AES-GCM, XChaCha20 wrappers
    vault/                  Vault models, codec, repository, storage
    security/               Clipboard guard
    ui/                     Illustrations

ios/Lockroot/               iOS (Swift)
  Lockroot/App/             Entry point, ViewModel
  Lockroot/Crypto/          CryptoKit + libsodium
  Lockroot/Vault/           Codec, storage, repository
  Lockroot/UI/              SwiftUI views

macos/LockrootMac/          macOS (Swift, SwiftPM)
  Sources/LockrootMac/      Same structure as iOS
  Tests/                    v2 codec tests

windows/Lockroot.Windows/   Windows (C#, WPF / .NET 8)
  Security/                 Argon2id, AES-GCM
  Vault/                    Repository, storage
  Services/                 Clipboard, settings, generator

linux/Lockroot.Linux/       Linux (C#, Avalonia)
  Security/                 Argon2id, AES-GCM
  Vault/                    Repository, storage
  Services/                 Legal, generator, password rules

docs/
  crypto.md                 Cryptographic design
  vault_format.md           Vault file format specification
  threat_model.md           Threat model and security boundaries
```

## Documentation

| Document | What it covers |
|---|---|
| [docs/crypto.md](docs/crypto.md) | KDF parameters, AEAD, password lifecycle, per-platform wiping, libraries |
| [docs/vault_format.md](docs/vault_format.md) | v2 envelope, field definitions, legacy format support, migration behavior |
| [docs/threat_model.md](docs/threat_model.md) | What's protected, what isn't, trust boundaries, attack surfaces |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting policy |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines |

## License

[AGPL-3.0-or-later](LICENSE)

## Creator

Built by Regaan.

Security Researcher, Offensive Engineer, and Full-Stack Developer from Chennai, India.

- Website: [rothackers.com](https://rothackers.com)
- GitHub: [github.com/regaan](https://github.com/regaan)
- LinkedIn: [linkedin.com/in/regaan](https://linkedin.com/in/regaan)
- Email: regaan48@gmail.com
