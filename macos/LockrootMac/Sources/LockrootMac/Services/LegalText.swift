import Foundation

enum LegalText {
    static let privacy = """
    Lockroot is a local password manager for macOS.

    Lockroot does not collect, transmit, sell, rent, or share personal data. Vault entries, usernames, passwords, notes, tags, generated passwords, and encrypted exports remain under your control.

    The app does not use cloud sync, analytics, ads, remote configuration, telemetry, or account login.

    Export files are encrypted with a separate export password. Import requires the same export password and fails if the file is tampered with or the password is wrong.

    Contact: regaan48@gmail.com
    """

    static let terms = """
    Lockroot has no recovery backdoor. You are responsible for remembering your master password and safely storing encrypted backups if you choose to create them.

    If your device is lost, damaged, reset, or wiped, you need your own encrypted backup to restore your vault.

    Lockroot is provided as-is, without warranties of uninterrupted availability, absolute security, or fitness for a particular purpose.

    Do not use Lockroot for unlawful activity or in ways that violate applicable laws, platform policies, or the rights of others.

    Contact: regaan48@gmail.com
    """

    static let about = """
    Created by REGAAN.

    Security Researcher, Offensive Engineer, and Full-Stack Developer from Chennai, India.

    Website: rothackers.com
    GitHub: github.com/regaan
    LinkedIn: linkedin.com/in/regaan
    Email: regaan48@gmail.com
    """
}
