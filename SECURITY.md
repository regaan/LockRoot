# Security Policy

Lockroot is a local-only password manager, so vulnerability reports are taken seriously.

## Supported Versions

Security reports are accepted for the current `main` branch and any public release builds published by the maintainer.

## Reporting a Vulnerability

Please do not open a public GitHub issue for security vulnerabilities.

Send a private report to:

`regaan48@gmail.com`

Include as much detail as possible:

- affected version or commit
- device/emulator details
- Android version
- reproduction steps
- expected behavior
- actual behavior
- logs only if they do not contain secrets
- proof-of-concept files only if they do not expose real user data

## Scope

Useful reports include:

- vault decryption bypass
- authenticated encryption misuse
- plaintext vault/export storage
- master password or key persistence
- import/export authentication bypass
- Android permission regression
- screenshot/screen-recording protection regression
- clipboard clearing failure caused by app logic
- denial-of-service through malformed vault/export metadata

Out of scope:

- attacks requiring a compromised/rooted OS with arbitrary app memory access
- someone photographing the screen with another device
- weak user-chosen master passwords
- social engineering
- vulnerabilities in test-only/debug-only artifacts

## Handling

The maintainer will try to acknowledge valid reports, investigate impact, and fix confirmed issues before public disclosure.

Do not include real passwords, real vault files, private keys, or other live secrets in reports.
