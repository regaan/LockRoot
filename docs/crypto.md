# Cryptography

This covers how Lockroot encrypts and decrypts vault data.

## Key Derivation

Lockroot derives the vault key from the master password using Argon2id (RFC 9106, version 1.3).

Default parameters:

| Parameter | Value |
|---|---|
| Memory | 64 MiB (65,536 KiB) |
| Iterations | 3 |
| Parallelism | 2 lanes |
| Salt | 32 bytes, random |
| Output | 32 bytes (256-bit key) |

These defaults were chosen to take roughly 0.5–1 second on a mid-range phone and make GPU-based cracking expensive. The salt is generated fresh for every new vault and every export.

### Parameter Bounds

To prevent denial-of-service from crafted vault files with absurd KDF costs, all platforms enforce hard limits before running the KDF:

| Parameter | Min | Max |
|---|---|---|
| Memory (KiB) | 19,456 | 262,144 |
| Iterations | 2 | 10 |
| Parallelism | 1 | 8 |
| Salt length (bytes) | 16 | 64 |

A vault file with parameters outside these bounds is rejected before any key derivation runs.

## Authenticated Encryption

Current format (v2) uses AES-256-GCM everywhere:

| Property | Value |
|---|---|
| Algorithm | AES-256-GCM |
| Key size | 256 bits |
| Nonce size | 12 bytes (96 bits), random |
| Tag size | 16 bytes (128 bits) |

Older vaults from before the v2 migration may use XChaCha20-Poly1305 (24-byte nonce). Mobile platforms still carry the XChaCha20-Poly1305 reader so they can decrypt and migrate these files. Desktop platforms only ever used AES-256-GCM, so they don't need the XChaCha20 reader.

### Associated Data

Every encryption binds the envelope metadata into the AEAD as associated data. The AAD is a pipe-delimited UTF-8 string:

```
magic|version|kdf_name|memory|iterations|parallelism|salt_b64|cipher_name|nonce_b64
```

Example:

```
Lockroot_VAULT|2|argon2id|65536|3|2|<base64 salt>|aes-256-gcm|<base64 nonce>
```

This means you can't change the magic, version, KDF params, or cipher in the envelope without breaking authentication. Any tamper = decryption failure.

## Nonce Handling

Every encryption operation generates a fresh random nonce. Lockroot never reuses nonces and never derives nonces from deterministic inputs.

The nonce is stored in the envelope alongside the ciphertext. Since the key changes with every new salt (and the salt is random per-vault/per-export), the probability of a nonce collision is negligible.

## Password Lifecycle

The master password is never stored anywhere. Not on disk, not in preferences, not in a keychain.

How the password flows through the system:

1. User types password in the UI.
2. Password is converted to bytes (UTF-8).
3. Bytes are passed to Argon2id to derive the key.
4. Password bytes are wiped (zeroed) immediately after derivation.
5. The derived key is held in memory only while the vault is unlocked.
6. On lock, the key is wiped.

### Platform-Specific Wiping

- **Windows / Linux (C#):** Password accepted as `ReadOnlySpan<char>`. Key material wiped with `CryptographicOperations.ZeroMemory`, which the compiler cannot optimize away.
- **Android (Kotlin):** Password accepted as `CharArray`, converted to UTF-8 bytes. Wiped with `Arrays.fill(bytes, 0)` + volatile read fence. `CharArray` zeroed separately.
- **iOS / macOS (Swift):** Password converted to `Data` at the ViewModel boundary. Wiped with `sodium_memzero` (libsodium). The Swift `String` from the UI layer cannot be reliably zeroed—that's a Swift runtime limitation, not something the app can fix.

## Libraries

| Platform | KDF | AEAD (write) | AEAD (legacy read) | RNG |
|---|---|---|---|---|
| Android | Bouncy Castle Argon2id | JCE AES/GCM/NoPadding | lazysodium XChaCha20-Poly1305 | `SecureRandom` |
| iOS | Argon2Swift | CryptoKit AES.GCM | libsodium (Clibsodium) | libsodium `randombytes_buf` |
| macOS | Argon2Swift | CryptoKit AES.GCM | libsodium (Clibsodium) | libsodium `randombytes_buf` |
| Windows | Bouncy Castle Argon2id | .NET AesGcm | — | `RandomNumberGenerator` |
| Linux | Bouncy Castle Argon2id | .NET AesGcm | — | `RandomNumberGenerator` |

All libraries are well-known, maintained, and used widely in production. No custom cryptographic primitives.

## What Lockroot Does Not Do

- No password hashing for storage (the password is never stored).
- No RSA, no Diffie-Hellman, no asymmetric crypto at all. There's no key exchange because there's no server.
- No TLS. There's no network communication.
- No key stretching beyond Argon2id. One KDF, one pass, one key.
- No key escrow. No recovery. If the password is gone, the vault is gone.
