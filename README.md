# Lockroot

![Lockroot logo](docs/assets/lockroot.png)

Lockroot is an offline Android password manager. It keeps a single encrypted vault on the device and does not ask for internet access.

No account. No sync. No ads. No analytics. No telemetry. No recovery backdoor.

The tradeoff is simple: if the master password is strong and remembered, the vault can be unlocked. If the master password is lost, the vault is gone.

## What It Does

- Creates an encrypted local vault.
- Unlocks the vault with a master password.
- Stores titles, websites, usernames, passwords, notes, and tags inside ciphertext.
- Adds, edits, deletes, reveals, copies, and searches entries locally.
- Generates passwords with configurable length and character groups.
- Clears app-copied clipboard values automatically.
- Locks when the app goes to the background.
- Locks after inactivity.
- Blocks screenshots and normal Android screen recordings with `FLAG_SECURE`.
- Exports encrypted backups with a separate export password.
- Imports encrypted backups with preview, merge, or replace.
- Requires Terms and Privacy acceptance before first vault creation.

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
- KDF implementation: Bouncy Castle
- Cipher: `XChaCha20-Poly1305`
- Cipher implementation: LazySodium / libsodium
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
