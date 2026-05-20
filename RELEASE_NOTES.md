# Lockroot Mobile Release Notes

Lockroot is an open-source password manager focused on local control, privacy, and practical mobile security.

## Release Status

- Android is in Google Play internal testing.
- iOS is live on the App Store: https://apps.apple.com/app/id6770449898
- GitHub Android artifacts are temporary until the public Play Store link is available.

## Android

This release includes Android release build artifacts:

- `Lockroot-android-release.apk`
- `Lockroot-android-release.aab`
- `Lockroot-android-SHA256SUMS.txt`

The APK/AAB are release builds. They are signed with a temporary GitHub Actions signing key created during the workflow run, not the Google Play upload key.

Because the temporary signing key changes per workflow run, uninstall any previous GitHub-release APK before installing a newer one from GitHub.

## iOS

The iOS app is available on the App Store:

- https://apps.apple.com/app/id6770449898

The GitHub release workflow may also attach a simulator build for verification, but iPhone users should install Lockroot from the App Store.

## Security Model

- Master password key derivation uses Argon2id.
- Vault encryption uses XChaCha20-Poly1305.
- Export/import uses a separate export password and derived key.
- Wrong passwords and modified vaults fail authentication.
- Lockroot has no recovery backdoor.

## Source Code

GitHub: https://github.com/regaan/LockRoot

Website: https://lockroot.rothackers.com
