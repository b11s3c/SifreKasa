# Google Play Store Listing (English)

## App Name (max 30 chars)
`ŞifreKasa` *(9 chars)*

## Short Description (max 80 chars)
`Open-source, offline-first password, 2FA (TOTP), and passkey manager.` *(69 chars)*

## Full Description (max 4000 chars)
ŞifreKasa is an open-source, offline-first password, 2FA (TOTP), and passkey manager designed with privacy as a core architectural guarantee.

Your vault stays encrypted strictly on your device using proven modern cryptography (Argon2id + XChaCha20-Poly1305 via libsodium). No analytics, no ads, no trackers, and no crash-reporting SDKs.

KEY FEATURES
• Strong Local Encryption: Encrypted with Argon2id (128 MiB) and XChaCha20-Poly1305.
• Biometrics & Auto-Lock: Hardware-backed biometric unlock, lock on background/inactivity, and timed clipboard clearing.
• 2FA (TOTP): RFC 6238 authenticator with camera QR scanning and manual entry.
• Passkeys (Credential Manager): Syncable encrypted passkeys with WebAuthn PRF support.
• Safe Autofill: Phishing-resistant domain matching across apps and trusted browsers.
• Encrypted Backup & Restore: Separate-password .skx encrypted backups with automatic Storage Access Framework sync.
• Send to PC (Optional): One-time, end-to-end encrypted (X25519 + AES-256-GCM) credential fill to the open-source browser extension via your own relay.
• Screen Protection: Blocks screenshots, screen recording, and recents previews.

OPEN SOURCE
Audit the full source code, file format, and threat model on GitHub:
https://github.com/b11s3c/SifreKasa

Requires Android 14+.

## Release Notes (v0.1.1 — Initial Release)
- Local encrypted vault (Argon2id + XChaCha20-Poly1305) with biometric unlock
- 2FA (TOTP) QR scanner and code generator
- Android Autofill and Passkey (Credential Manager + PRF) provider
- Encrypted .skx export/import and automatic backup
- End-to-end encrypted Send-to-PC via QR code
