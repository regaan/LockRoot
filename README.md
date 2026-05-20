# Lockroot

![Lockroot logo](docs/assets/lockroot.png)

Lockroot is an offline password manager for Android and iOS. It keeps a single encrypted vault on the device and does not need accounts, sync servers, analytics, ads, or telemetry.

No account. No sync. No ads. No analytics. No telemetry. No recovery backdoor.

The tradeoff is simple: if the master password is strong and remembered, the vault can be unlocked. If the master password is lost, the vault is gone.

## Download

- iOS App Store: https://apps.apple.com/app/id6770449898
- Android: Google Play internal testing
- Website: https://lockroot.rothackers.com

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

Android also locks after inactivity and blocks screenshots/normal screen recordings with `FLAG_SECURE`.

iOS locks when the app leaves the foreground. iOS does not provide the same universal screenshot blocking API as Android.

## Security Design

Lockroot never uses the password directly as an encryption key.

```text
Master password
  -> Argon2id
  -> 256-bit vault key
  -> XChaCha20-Poly1305
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
- Cipher: `XChaCha20-Poly1305`
- Android cipher implementation: LazySodium / libsodium
- iOS cipher implementation: Swift-Sodium / libsodium
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
