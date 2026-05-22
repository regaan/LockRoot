# Vault File Format

Lockroot stores vault and export data in a JSON envelope. This document describes the current v2 format and the older layouts that Lockroot can still read.

## v2 Envelope (Current)

All platforms write this encrypted envelope format. The file is UTF-8 encoded JSON.

```json
{
  "magic": "Lockroot_VAULT",
  "version": 2,
  "kdf": {
    "name": "argon2id",
    "memory": 65536,
    "iterations": 3,
    "parallelism": 2,
    "salt": "<base64>"
  },
  "cipher": {
    "name": "aes-256-gcm",
    "nonce": "<base64>"
  },
  "ciphertext": "<base64>",
  "tag": "<base64>"
}
```

### Magic Strings

| Type | Magic |
|---|---|
| Vault | `Lockroot_VAULT` |
| Export | `Lockroot_EXPORT` |

The magic distinguishes vaults from exports. You can't accidentally import a vault file as an export or vice versa.

### Fields

| Field | Type | Description |
|---|---|---|
| `magic` | string | File type identifier. |
| `version` | integer | Format version. Currently `2`. |
| `kdf.name` | string | KDF algorithm. Always `argon2id`. |
| `kdf.memory` | integer | Argon2id memory cost in KiB. |
| `kdf.iterations` | integer | Argon2id time cost (passes). |
| `kdf.parallelism` | integer | Argon2id lane count. |
| `kdf.salt` | string | Base64-encoded random salt. |
| `cipher.name` | string | AEAD algorithm. Currently `aes-256-gcm`. |
| `cipher.nonce` | string | Base64-encoded random nonce. |
| `ciphertext` | string | Base64-encoded encrypted vault data. |
| `tag` | string | Base64-encoded AEAD authentication tag. |

### Plaintext Payload

After decryption, the plaintext is a JSON object representing the vault contents:

```json
{
  "vaultId": "a1b2c3...",
  "schemaVersion": 1,
  "entries": [
    {
      "id": "d4e5f6...",
      "title": "GitHub",
      "website": "github.com",
      "username": "user",
      "password": "secret",
      "notes": "",
      "tags": ["dev", "work"]
    }
  ]
}
```

Desktop (C#) vaults use a slightly different payload shape — entries have `createdAt` / `updatedAt` timestamps and the root object uses `version` / `createdAt` / `updatedAt` instead of `vaultId` / `schemaVersion`. The encrypted envelope format is identical across all platforms, but the decrypted JSON inside is not. This means a desktop app importing a mobile export (or vice versa) needs to handle the other payload schema to fully parse entries.

## v1 / Legacy Formats

Lockroot can still read older envelopes. The differences:

### Flat-key layout (v1)

Older files stored KDF and cipher info as flat top-level keys instead of nested objects:

```json
{
  "magic": "Lockroot_VAULT",
  "version": 1,
  "kdf": "argon2id",
  "argon2id": {
    "memory": 65536,
    "iterations": 3,
    "parallelism": 2,
    "salt": "<base64>"
  },
  "cipher": "xchacha20-poly1305",
  "nonce": "<base64>",
  "ciphertext": "<base64>",
  "tag": "<base64>"
}
```

Notice `kdf` is a plain string, `argon2id` params are a separate top-level object, `cipher` is a plain string, and `nonce` sits at the root.

### Legacy magic strings

Before the v2 migration, Windows and Linux used `LOCKROOT` and `LOCKROOT-EXPORT` as magic strings. These are still accepted on read.

| Legacy Magic | Treated As |
|---|---|
| `LOCKROOT` | `Lockroot_VAULT` |
| `LOCKROOT-EXPORT` | `Lockroot_EXPORT` |

### Legacy cipher

Mobile platforms (Android, iOS, macOS) originally used XChaCha20-Poly1305 with a 24-byte nonce. Those files are still decryptable on mobile platforms.

Desktop platforms always used AES-256-GCM, so they never encounter the XChaCha20 cipher in practice.

## Migration

After a vault is successfully decrypted, Lockroot checks if the file needs migration:

```
needsMigration = (magic != expected) || (version != 2) || (cipher != "aes-256-gcm")
```

If any of those conditions are true **and the platform can decrypt the old file**, the vault is re-encrypted with the current v2 parameters and saved. The key stays the same (it was just derived), so this doesn't require re-entering the password.

Migration depends on cipher support:

- **Mobile platforms** (Android, iOS, macOS) carry the XChaCha20-Poly1305 reader, so they can migrate old mobile vaults that used XChaCha20.
- **Desktop platforms** (Windows, Linux) only support AES-256-GCM. They can migrate old desktop vaults (legacy `LOCKROOT` magic, version 1) but cannot decrypt old mobile XChaCha vaults.

This is a one-way upgrade. Once a vault is migrated, it stays in v2 format.

## Associated Data (AAD)

The AEAD authentication binds the entire envelope header. The AAD is constructed as a pipe-delimited string:

```
{magic}|{version}|{kdf_name}|{memory}|{iterations}|{parallelism}|{salt_b64}|{cipher_name}|{nonce_b64}
```

This is encoded as UTF-8 bytes and passed as the associated data to AES-GCM (or XChaCha20-Poly1305 for legacy reads). If any header field is altered, decryption fails.

## File Storage

| Platform | Location |
|---|---|
| Android | App-private internal storage |
| iOS | App container with `.completeFileProtection` |
| macOS | `~/Library/Application Support/Lockroot/` |
| Windows | `%LOCALAPPDATA%\Lockroot\` |
| Linux | `~/.local/share/Lockroot/` |

Exports go wherever the user picks through the system file dialog.
