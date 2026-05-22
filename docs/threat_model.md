# Threat Model

This describes what Lockroot is designed to protect, what it can't protect, and where the boundaries are.

## What Lockroot Protects

### Vault data at rest

The vault file on disk is encrypted with AES-256-GCM, keyed by Argon2id from the master password. Without the password, the file is indistinguishable from random data (up to the JSON envelope structure, which is public metadata).

An attacker who copies the vault file gets:
- The KDF parameters (public: memory, iterations, parallelism, salt).
- The cipher name and nonce (public).
- The ciphertext and authentication tag.

They do not get the password, the derived key, or any plaintext.

### Offline brute-force resistance

Argon2id at 64 MiB / 3 iterations makes password guessing expensive. A single attempt on a modern GPU takes significantly longer than SHA-256 or bcrypt at comparable security margins. Combined with the minimum 12-character password requirement, weak-password brute-forcing is impractical for reasonable password choices.

### Export file security

Exports are encrypted with a separate password and separate salt. Knowing the vault password does not help decrypt an export, and vice versa. The export uses the same v2 envelope format but with `Lockroot_EXPORT` magic, so it can't be confused with a vault file.

### Clipboard exposure

Copied secrets are automatically cleared after a short delay (platform-dependent, typically 20–30 seconds). On Windows and macOS, Lockroot also clears its own clipboard content when the app closes.

### Screen capture

Android blocks screenshots and screen recordings via `FLAG_SECURE`. Windows requests capture exclusion via `SetWindowDisplayAffinity`. iOS and Linux don't have equivalent APIs.

## What Lockroot Does Not Protect

### Compromised device

If the OS is rooted, jailbroken, or has malware with memory access, Lockroot can't help. The decrypted vault sits in app memory while unlocked. A process with `ptrace` or equivalent can read it.

### Shoulder surfing

Lockroot has password reveal buttons. Someone watching the screen can see secrets. That's a physical security problem, not a software one.

### Weak passwords

The minimum is 12 characters. Lockroot does not enforce complexity rules beyond that. `aaaaaaaaaaaa` passes the length check. The KDF makes brute-forcing harder, but it can't make a predictable password unpredictable.

### Keyloggers and malicious input methods

If the keyboard is compromised, the password is compromised before Lockroot ever sees it.

### Memory forensics after lock

When the vault is locked, key material is wiped. But:
- The OS may have paged memory to swap.
- The GC (on Android / .NET) may have copied objects before they were zeroed.
- Swift strings from the UI layer can't be reliably zeroed.

Wiping is best-effort. It raises the bar significantly, but a forensic examiner with a memory dump and enough patience might recover fragments.

### Lost password

No recovery mechanism exists. No server, no recovery key, no admin reset. If the password is lost, the vault is permanently inaccessible. This is by design.

## Trust Boundaries

```
┌──────────────────────────────────────┐
│  User's device (trusted boundary)    │
│                                      │
│  ┌─────────────┐   ┌──────────────┐  │
│  │ Lockroot    │   │ OS / Kernel  │  │
│  │ (app)       │   │ (trusted)    │  │
│  │             │   │              │  │
│  │ plaintext   │   │ filesystem   │  │
│  │ in memory   │   │ encryption   │  │
│  │ while       │   │ (if present) │  │
│  │ unlocked    │   │              │  │
│  └──────┬──────┘   └──────────────┘  │
│         │                            │
│         │ encrypted vault file       │
│         ▼                            │
│  ┌─────────────┐                     │
│  │ Local disk  │                     │
│  │ (untrusted  │                     │
│  │  at rest)   │                     │
│  └─────────────┘                     │
│                                      │
└──────────────────────────────────────┘
          │
          │  export file (encrypted)
          ▼
   ┌─────────────┐
   │ USB / email  │
   │ / cloud      │  (untrusted transport)
   └─────────────┘
```

Lockroot trusts the OS, the hardware, and the user. It does not trust the filesystem (hence encryption) or any transport channel (hence encrypted exports).

There is no network boundary because Lockroot makes no network connections.

## Attack Surfaces

### Vault file parsing

The vault file is JSON. Malformed JSON, extreme field sizes, or unexpected types could cause issues during parsing. All platforms validate required envelope fields, reject unsupported KDF/cipher values, and reject out-of-range KDF parameters before running cryptographic operations. Unknown non-critical JSON fields may be ignored by parsers, so they are not treated as security controls.

### Import flow

Imported files go through the same envelope validation and decryption. A crafted import file could attempt:
- KDF parameter abuse (blocked by parameter caps).
- Oversized ciphertext (bounded by available memory).
- Invalid base64 (caught during decode).
- Tampered metadata (caught by AEAD authentication).

### Clipboard

Secrets sit in the system clipboard until cleared. Other apps with clipboard access can read them during that window. On Android 13+, the OS shows a clipboard access notification. On older Android, iOS, and desktop platforms, clipboard access is silent.

### UI interactions

Password reveal buttons show secrets in plaintext on screen. The auto-lock timer (60 seconds on desktop, immediate on background for mobile) limits exposure, but during active use the secrets are visible.

## Platform-Specific Notes

### Android

- `FLAG_SECURE` blocks screenshots and task-switcher previews.
- `android:allowBackup="false"` and `android:dataExtractionRules` exclude vault data from ADB and cloud backups.
- No permissions declared. The app has no network, camera, contacts, or storage access.
- Import/export uses `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` (SAF), which gives scoped access to a single file.

### iOS

- Vault file written with `.completeFileProtection` — encrypted by the OS when the device is locked.
- Locks immediately when the app moves to background.
- No equivalent to `FLAG_SECURE`. iOS does not offer apps a way to block screenshots.
- No biometric unlock (not implemented yet).

### macOS

- Sandbox-ready for App Store builds.
- Clipboard tracking clears Lockroot-owned content on app termination.
- Same crypto stack as iOS (CryptoKit + libsodium).

### Windows

- `SetWindowDisplayAffinity` requests screen capture exclusion.
- Auto-lock on inactivity (60 seconds) and on minimize.
- Clipboard cleared on app close.
- Per-user installer — no admin privileges required.

### Linux

- Built with Avalonia (cross-platform .NET UI).
- Auto-lock on inactivity (60 seconds) and on minimize.
- No screen capture protection — X11 doesn't support it. Wayland is better but Avalonia doesn't expose the API yet.
