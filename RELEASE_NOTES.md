# Lockroot 1.1.1 Release Notes

Lockroot 1.1.1 is a hardening release for Android, iOS, and Windows. It fixes audit findings around unlock throttling, password policy consistency, clipboard handling, capture protection, KDF validation, and release packaging.

## Security Fixes

- Android screenshot and screen-recording protection now applies in all build types with `FLAG_SECURE`.
- Android vault session key cleanup now uses random overwrite, zeroing, and a volatile sink.
- iOS unlock now throttles repeated failed password attempts with exponential backoff.
- iOS validates Argon2id KDF parameters before accepting vault/export metadata.
- iOS clipboard auto-clear no longer captures the copied secret in the delayed cleanup task.
- Windows master and export passwords now require at least 12 characters.
- Windows unlock now throttles repeated failed password attempts with exponential backoff.
- Windows now auto-locks after inactivity and when minimized.
- Windows asks the OS to exclude Lockroot windows from normal screen capture where supported.
- Windows clipboard auto-clear no longer stores the copied secret for comparison.
- Windows Argon2id parameters are now range-validated before key derivation.
- Windows derived vault keys are allocated in pinned managed arrays and wiped after use.

## App Fixes

- iOS search now includes notes, matching Android behavior.
- Windows password generator now enforces a 12 to 128 character range.
- README now documents Android, iOS, and Windows security behavior more accurately.

## Release Packaging

- Android release is now `versionCode 3` / `versionName 1.1.1`.
- Windows installer is now labeled `1.1.1`.
- GitHub release workflow now publishes Android and Windows artifacts only.
- The old iOS simulator artifact workflow was removed because iOS is live on the App Store.
- The accidental `ios.zip` archive is removed and no longer allowlisted in `.gitignore`.

## Downloads

- iOS App Store: https://apps.apple.com/app/id6770449898
- GitHub: https://github.com/regaan/LockRoot
- Website: https://lockroot.rothackers.com
