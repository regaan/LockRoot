# Security Policy

Lockroot is a local password manager. Vulnerability reports are taken seriously because the app handles vault passwords, generated passwords, encrypted exports, and decrypted secrets while unlocked.

## Supported Versions

Security reports are accepted for the current `main` branch and any public release builds published by the maintainer.

## Reporting a Vulnerability

Please do not open a public GitHub issue for security vulnerabilities.

Send a private report to:

`regaan48@gmail.com`

Include as much detail as possible:

- affected version or commit
- device/emulator details
- platform and OS version
- reproduction steps
- expected behavior
- actual behavior
- logs only if they do not contain secrets
- proof-of-concept files only if they do not expose real user data
- whether a real vault/export file is involved

## Scope

Useful reports include:

- vault decryption bypass
- authenticated encryption misuse
- plaintext vault/export storage
- plaintext temporary files
- master password or key persistence
- import/export authentication bypass
- wrong password returning decrypted garbage
- vault/export metadata tampering that is not detected
- KDF parameter downgrade or validation bypass
- Android permission regression
- screenshot/screen-recording protection regression
- clipboard clearing failure caused by app logic
- denial-of-service through malformed vault/export metadata
- release artifact/signing workflow weakness
- dependency or native-library loading issue that affects vault security

Out of scope:

- attacks requiring a compromised/rooted OS with arbitrary app memory access
- someone photographing the screen with another device
- weak user-chosen master passwords
- social engineering
- vulnerabilities in test-only/debug-only artifacts
- reports that require users to install a malicious keyboard, screen reader, or accessibility service unless Lockroot grants that access itself

## Expected Security Properties

Reports are especially useful when they show a break in one of these expectations:

- the master password is not stored
- the raw vault key is not stored on disk
- vault and export files are authenticated
- wrong passwords fail authentication
- tampered ciphertext fails authentication
- tampered authenticated metadata fails authentication or validation
- encrypted exports require the export password
- release Android builds block screenshots and screen recordings
- Android app data is excluded from platform backup paths
- no plaintext vault/export is written to disk
- new vault and export files use the shared v2 envelope format with `Lockroot_VAULT` / `Lockroot_EXPORT`, Argon2id, and AES-256-GCM
- older compatible vault/export envelopes are migrated to the current format after successful unlock or save

## Handling

The maintainer will try to acknowledge valid reports, investigate impact, and fix confirmed issues before public disclosure.

When reporting, please avoid sending live secrets. If a vault proof-of-concept is needed, create a new test vault with fake entries and share only that file.

Do not include real passwords, real vault files, private keys, or other live secrets in reports.
