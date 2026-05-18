# Lockroot Mobile Release Notes

Lockroot is an open-source password manager focused on local control, privacy, and practical mobile security.

## Release Status

- Android is in Google Play internal testing.
- iOS is in closed testing/App Store preparation.
- These GitHub artifacts are temporary until public store links are available.

## Android

This release includes Android release build artifacts:

- `Lockroot-android-release.apk`
- `Lockroot-android-release.aab`
- `Lockroot-android-SHA256SUMS.txt`

The APK/AAB are release builds. They are signed with a temporary GitHub Actions signing key created during the workflow run, not the Google Play upload key.

Because the temporary signing key changes per workflow run, uninstall any previous GitHub-release APK before installing a newer one from GitHub.

## iOS

This release includes:

- `Lockroot-iOS-simulator.app.zip`
- `Lockroot-ios-simulator-SHA256SUMS.txt`

The iOS artifact is an unsigned simulator release build for verification only. It cannot be installed on a physical iPhone.

## Security Model

- Master password key derivation uses Argon2id.
- Vault encryption uses XChaCha20-Poly1305.
- Export/import uses a separate export password and derived key.
- Wrong passwords and modified vaults fail authentication.
- Lockroot has no recovery backdoor.

## Source Code

GitHub: https://github.com/regaan/LockRoot

Website: https://lockroot.rothackers.com
