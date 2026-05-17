# Contributing

Thanks for taking the time to improve Lockroot.

Lockroot is security-sensitive software, so contributions should be small, reviewable, and clear about their impact.

## Before You Start

- Open an issue first for large changes.
- Keep security claims precise.
- Do not add internet access, analytics, ads, telemetry, cloud sync, or remote config.
- Do not add recovery/backdoor behavior.
- Do not commit secrets, keystores, vault files, export files, APKs, or AABs.

## Development Setup

Requirements:

- JDK 21
- Android SDK 36
- Android platform tools if installing on a device or emulator

Run the main checks:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

## Pull Request Rules

Every pull request should include:

- What changed.
- Why it changed.
- How it was tested.
- Security impact, if any.
- Screenshots for visible UI changes.

## Crypto Rules

- Do not invent custom cryptography.
- Do not use raw hashes as encryption keys.
- Do not store master passwords or raw derived keys on disk.
- Keep authenticated encryption for vault and export data.
- Keep vault metadata authenticated as associated data.
- Keep imports/export wrong-password behavior as authentication failure.

## UI Rules

- Keep the app offline and local-first.
- Keep sensitive screens protected.
- Keep forms scrollable and keyboard-safe.
- Keep destructive actions behind confirmation.
- Keep copy/reveal actions explicit.

## Tests

Add or update tests when changing:

- crypto behavior
- vault file format
- export/import
- password generation
- master password changes
- storage behavior

If a change cannot be tested automatically, explain how you tested it manually.
