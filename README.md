# Lockroot

<p align="center">
  <img src="docs/assets/lockroot.png" alt="Lockroot logo" width="520">
</p>

<p align="center">
  <strong>Encryption is not magic armor if your master password is <code>password123</code> wearing a fake mustache.</strong><br>
  <em>Lockroot can bury your vault in serious crypto, but it cannot save a password that would lose a fight to a sticky note.</em>
</p>

Lockroot is an offline password manager for Android, iOS, Windows, and Linux. It keeps a single encrypted vault on the device and does not need accounts, sync servers, analytics, ads, or telemetry.

No account. No sync. No ads. No analytics. No telemetry. No recovery backdoor.

The tradeoff is simple: if the master password is strong and remembered, the vault can be unlocked. If the master password is lost, the vault is gone.

## Download

- iOS App Store: https://apps.apple.com/app/id6770449898
- Android: Google Play internal testing
- Windows: GitHub release installer
- Linux: GitHub release packages
- Website: https://lockroot.rothackers.com

## Linux Install

Download Linux builds from the GitHub release page:

```text
https://github.com/regaan/LockRoot/releases
```

Recommended for most users:

```bash
chmod +x Lockroot-linux-x64-1.2.0.AppImage
./Lockroot-linux-x64-1.2.0.AppImage
```

Debian / Ubuntu:

```bash
sudo apt install ./Lockroot-linux-x64-1.2.0.deb
lockroot
```

Fedora / RHEL / openSUSE-style RPM systems:

```bash
sudo dnf install ./Lockroot-linux-x64-1.2.0.rpm
lockroot
```

Portable tarball:

```bash
mkdir lockroot
tar -xzf Lockroot-linux-x64-1.2.0.tar.gz -C lockroot
cd lockroot
./Lockroot
```

## What It Does

- Creates an encrypted local vault.
- Unlocks the vault with a master password.
- Stores titles, websites, usernames, passwords, notes, and tags inside ciphertext.
- Adds, edits, deletes, reveals, copies, and searches entries locally.
- Generates passwords with configurable length and character groups.
- Clears app-copied clipboard values automatically.
- Locks when the app goes to the background.
- Exports encrypted backups with a separate export password.
- Imports encrypted backups with preview, merge, or replace.
- Requires Terms and Privacy acceptance before first vault creation.

Android locks after inactivity and blocks screenshots/normal screen recordings with `FLAG_SECURE`.

iOS locks when the app leaves the foreground. iOS does not provide the same universal screenshot blocking API as Android.

Windows locks after inactivity or minimize, asks Windows to exclude vault windows from normal OS screen capture where supported, and ships as a normal installer flow with Terms and Conditions before install.

Linux locks after inactivity or minimize and ships as native desktop release packages.

## Security Design

Lockroot never uses the password directly as an encryption key.

```text
Master password
  -> Argon2id
  -> 256-bit vault key
  -> XChaCha20-Poly1305 / AES-256-GCM
  -> encrypted local vault
```

Encrypted exports use a separate password and key:

```text
Export password
  -> Argon2id with a new salt
  -> export key
  -> encrypted export file
```

Wrong passwords, modified vault files, and modified export files fail authentication. The app does not silently decrypt garbage.

## Crypto

- KDF: `Argon2id`
- Android KDF implementation: Bouncy Castle
- iOS KDF implementation: Argon2Swift
- Windows KDF implementation: Bouncy Castle
- Linux KDF implementation: Bouncy Castle
- Cipher: `XChaCha20-Poly1305` on Android/iOS, `AES-256-GCM` on Windows
- Android cipher implementation: LazySodium / libsodium
- iOS cipher implementation: Swift-Sodium / libsodium
- Windows cipher implementation: AES-256-GCM via Bouncy Castle
- Linux cipher implementation: AES-256-GCM via Bouncy Castle
- Vault metadata is authenticated as associated data.
- Each vault/export gets a random salt.
- Each encryption gets a fresh random nonce.
- The master password is never stored.
- The raw derived key is never written to disk.
- Legacy `AES-256-GCM` vaults can still be read and re-saved into the current format.

## Android Permissions

Lockroot currently declares zero Android permissions.

It does not request:

- Internet
- Camera
- Contacts
- Location
- Microphone
- Notifications
- Broad storage access

Import and export use Android's system document picker, so broad storage permission is not needed.

## iOS Notes

The iOS app lives in `ios/Lockroot`.

It is a native SwiftUI project using:

- SwiftUI for the app UI
- Argon2Swift for Argon2id key derivation
- Swift-Sodium / libsodium for XChaCha20-Poly1305
- iOS Application Support storage with complete file protection
- Swift Package Manager for dependencies

## Windows Notes

The Windows app lives in `windows/Lockroot.Windows`.

It is a native WPF desktop project using:

- .NET 8 WPF for the desktop UI
- Bouncy Castle for Argon2id and authenticated encryption
- `%APPDATA%\Lockroot` for local encrypted vault storage
- Inno Setup for the normal Windows installer wizard
- A first-run Terms and Conditions gate before vault creation

Build the Windows app:

```powershell
dotnet restore .\windows\Lockroot.Windows\Lockroot.Windows.csproj --configfile .\windows\Lockroot.Windows\NuGet.Config -r win-x64
dotnet publish .\windows\Lockroot.Windows\Lockroot.Windows.csproj -c Release -r win-x64 --self-contained true
```

Build the installer after publishing:

```powershell
iscc .\windows\installer\lockroot.iss
```

The installer output is generated at:

```text
windows/installer/output/LockrootSetup-1.1.1.exe
```

## Linux Notes

The Linux app lives in `linux/Lockroot.Linux`.

It is a native Avalonia desktop project using:

- Avalonia UI for the Linux desktop interface
- Bouncy Castle for Argon2id and AES-256-GCM
- `~/.config/Lockroot` for local encrypted vault storage
- the same vault/export format as Windows
- AppImage, `.deb`, `.rpm`, tarball, and Flatpak packaging metadata

Build the Linux app:

```powershell
$env:DOTNET_CLI_HOME = (Resolve-Path .dotnet-cli).Path
$env:NUGET_PACKAGES = (Resolve-Path .nuget\packages).Path
$env:APPDATA = (Resolve-Path .appdata).Path
$env:LOCALAPPDATA = (Resolve-Path .localappdata).Path
dotnet build .\linux\Lockroot.Linux\Lockroot.Linux.csproj -c Release --configfile .\linux\Lockroot.Linux\NuGet.Config -p:UsedAvaloniaProducts=
dotnet publish .\linux\Lockroot.Linux\Lockroot.Linux.csproj -c Release -r linux-x64 --self-contained true --configfile .\linux\Lockroot.Linux\NuGet.Config -p:UsedAvaloniaProducts= -p:PublishSingleFile=false
```

## No Recovery

There is no forgot-password flow.

There is no recovery key.

There is no server-side backup.

If the master password is lost, Lockroot cannot decrypt the vault. Keep an encrypted export somewhere safe if the data matters.

## Limits

Lockroot protects vault data at rest. It cannot fully protect secrets if the device itself is compromised.

Real risks include:

- rooted or compromised devices
- malicious keyboards
- malicious accessibility services
- fake or modified APKs
- someone watching the master password being typed
- someone recording the screen with another camera
- managed UI strings that cannot be force-wiped instantly by the app runtime

Use a trusted build, keep the device clean, and use a strong master password.

## Repository Layout

```text
app/src/main/java/com/regaan/lockroot/
  MainActivity.kt                 Native Android UI and app flow
  crypto/                         Argon2id, AEAD wrappers, crypto exceptions
  security/                       Clipboard clearing
  ui/                             Custom local illustrations
  vault/                          Vault models, file format, repository, storage

app/src/main/res/
  drawable*/                      Icons and Lockroot visual assets
  mipmap-anydpi/                  Adaptive launcher icon definitions
  values/                         App strings, colors, theme
  xml/                            Backup/data extraction rules

app/src/test/java/com/regaan/lockroot/
  crypto/                         Crypto behavior tests
  vault/                          Vault, import/export, generator tests

ios/Lockroot/
  Lockroot.xcodeproj              Native iOS Xcode project
  Lockroot/App/                   SwiftUI app entry and state model
  Lockroot/Crypto/                Argon2id and libsodium crypto service
  Lockroot/Vault/                 Vault models, codec, storage, repository
  Lockroot/UI/                    Setup, unlock, home, settings, sheets
  Lockroot/Resources/             App icons and image assets

windows/Lockroot.Windows/
  Lockroot.Windows.csproj         Native Windows WPF project
  MainWindow.xaml                  Setup, unlock, home, settings, and vault UI
  Security/                        Argon2id and authenticated vault encryption
  Vault/                           Vault repository, storage, and file codec
  Services/                        Settings and password generation
  Dialogs/                         Entry editor and password prompt windows
  Assets/                          App icon and Lockroot visual assets

windows/installer/
  lockroot.iss                     Inno Setup installer script
  terms.txt                        Installer Terms and Conditions

linux/Lockroot.Linux/
  Lockroot.Linux.csproj            Native Linux Avalonia project
  MainWindow.cs                    Setup, unlock, home, settings, import/export UI
  Security/                        Argon2id and AES-256-GCM vault encryption
  Vault/                           Vault repository, storage, and file codec
  Services/                        Legal text, password generation, strength rules
  Assets/                          Linux app icon and Lockroot visual assets

linux/packaging/
  appimage/                        AppImage packaging script and AppRun
  deb/                             Debian package script
  rpm/                             RPM package spec and script
  flatpak/                         Flatpak manifest

docs/assets/
  lockroot.png                    README logo
```


## License

Lockroot is licensed under the GNU Affero General Public License v3.0 or later.

SPDX identifier:

```text
AGPL-3.0-or-later
```

## Creator

Built by Regaan.

Security Researcher, Offensive Engineer, and Full-Stack Developer from Chennai, India.

- Website: `rothackers.com`
- GitHub: `github.com/regaan`
- LinkedIn: `linkedin.com/in/regaan`
- Email: `regaan48@gmail.com`
