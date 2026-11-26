# ŞifreKasa — Proje Hafızası (MEMORY)

> Bu dosya projenin yaşayan teknik hafızasıdır. Her önemli mimari karar, kural, bilinen tuzak ve yayın durumu burada tutulur. Yeni bir geliştirme oturumuna başlarken önce bu dosya ve [BACKLOG.md](BACKLOG.md) okunur.

## 1. Proje Özeti

- **Uygulama Adı:** ŞifreKasa
- **Application ID:** `dev.sifrekasa`
- **Hedef:** Google Play Store üzerinde uçtan uca yayınlanacak, çevrimdışı-öncelikli, sıfır telemetrili, yerel Android parola, 2FA (TOTP) ve geçiş anahtarı (Passkey/WebAuthn) yöneticisi.
- **Mevcut Sürüm:** `versionCode = 3`, `versionName = "0.1.2"` (`app/build.gradle.kts`)
- **Platform Desteği:** Android 11+ (`minSdk = 30`, `compileSdk = 36`, `targetSdk = 36`); Passkey sağlayıcısı Android 14+ (API 34+) cihazlarda aktif.
- **Dil:** Türkçe arayüz ve dokümantasyon (İngilizce mağaza/uygulama desteği backlog'da değerlendirilecek).

## 2. Teknoloji Yığını ve Sürümler

| Katman | Teknoloji / Sürüm |
|---|---|
| Derleme | Gradle `9.9.1`, AGP `9.5.1`, Kotlin `2.4.21`, JDK 17 |
| Android UI | Jetpack Compose (BOM `2026.06.01`), Material 3, Edge-to-Edge |
| Kriptografi (`:core` + `:app`) | `lazysodium-android:5.2.0@aar` / `lazysodium-java:5.1.4`, `jna:5.19.1`, Google Tink `1.24.0` (X25519 + HKDF-SHA256 + AES-256-GCM) |
| Platform Servisleri | Android AutofillService, Credential Manager (`androidx.credentials:1.6.0`), Biometric (`1.1.0` + `fragment:1.8.9`), CameraX (`1.5.3`) |
| QR İşleme | `com.google.zxing:core:3.5.4` |
| Tarayıcı Eklentisi | Manifest V3 (Chrome/Edge/Brave 133+, Firefox 130+), Vanilla JS, WebCrypto |
| Aktarıcı (Relay) | Cloudflare Workers + Durable Objects (`wrangler ^4`), tek kullanımlık 60 sn oturum |
| Test Araçları | JUnit 4, Bouncy Castle `1.87` (çapraz doğrulama), WebAuthn4j `0.32.0`, Node.js built-in test runner (`node --test`), `tools/emu-ui.py` |

## 3. Değiştirilemez Güvenlik Kuralları (Invariants)

Bu kurallar hiçbir özellik veya mağaza gereksinimi için bozulmaz (detaylar: [tehdit-modeli.md](../tehdit-modeli.md)):

1. **Sıfır Telemetri:** Analitik, çökme raporlama SDK'sı (Crashlytics vb.) ve reklam kütüphanesi kesinlikle eklenmez.
2. **Ağ İzolasyonu:** `INTERNET` izni yalnızca kullanıcı onaylı "Bilgisayara gönder" (`RelayClient`) akışında kullanılır.
3. **Bulut/Cihaz Yedeğine Sızma Yok:** `allowBackup="false"`, `fullBackupContent="false"`, `data_extraction_rules.xml` tüm alanları hariç tutar.
4. **Ekran Güvenliği:** `FLAG_SECURE` ve `setRecentsScreenshotEnabled(false)` üretim derlemelerinde her zaman aktiftir.
5. **Özel Kripto Yok:** Kasa (`SKV1`) ve dışa aktarım (`SKX1`) dosyaları Argon2id v1.3 + XChaCha20-Poly1305 (libsodium) kullanır; değişiklikler bağımsız kütüphanelerle (Bouncy Castle / Tink) çapraz doğrulanır.
6. **Bellek ve Log Hijyeni:** Parolalar ve anahtarlar `ByteArray`/`CharArray` olarak tutulup `Secrets.wipe()` ile sıfırlanır; hiçbir sır loglanmaz.
7. **Açık `<queries>` Listesi:** `QUERY_ALL_PACKAGES` izni asla eklenmez; yalnızca `assets/privileged_apps.json` ile senkronize açık tarayıcı paketleri sorgulanır.

## 4. Mimari Karar Kaydı (ADR)

| ID | Tarih | Karar | Gerekçe |
|---|---|---|---|
| ADR-001 | 2026-11-18 | Passkey özel anahtarları Android Keystore yerine şifreli kasada (`SKV1`) saklanır | Keystore anahtarları yedeklenemez; telefon kaybında tüm geçiş anahtarları kaybolur. Kasada tutularak şifreli `.skx` yedeğine dahil edilir (BE/BS bayrakları ayarlı). |
| ADR-002 | 2026-11-18 | `SodiumCryptoEngine` kaynağı `:core/src/sodium/kotlin` altında tek kopya tutulur | Hem `:core` JVM testlerinde (`lazysodium-java`) hem `:app` içinde (`lazysodium-android`) birebir aynı kod derlenir ve test edilir. |
| ADR-003 | 2026-11-18 | `abiFilters` dört mimariyi içerir (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) | Android 14+ 32-bit cihazlar (ör. Android Go) ve emülatörler desteklenirken kullanılmayan JNA mimarileri (`armeabi`, `mips`) dışarıda bırakılır. |
| ADR-004 | 2026-11-26 | Hafıza, backlog, test stratejisi ve günlük (daily) kayıt sistemi `docs/` altında tutulur | Geliştirme ve Google Play yayın sürecinin uçtan uca izlenebilir ve oturumlar arası kalıcı olması için. |

## 5. Bilinen Tuzaklar ve Teknik Notlar

- **`androidx.fragment:1.8.9` zorunlu:** `androidx.biometric:1.1.0` eski bir fragment sürümü çektiği için izin/sonuç başlatıcılarında `requestCode` çökmesine yol açar; `fragment:1.8.9` açıkça tutulmalıdır.
- **R8 / ProGuard:** `lazysodium` ve `jna` yansıma (reflection) kullandığı için `app/proguard-rules.pro` içindeki `-keep` kuralları korunmalıdır.
- **16 KB Bellek Sayfası Hizalaması (Google Play Zorunluluğu):** Android 15+ / Google Play politikası gereği `arm64-v8a` ve `x86_64` yerel `.so` kütüphanelerinin (`libsodium.so`, `libjnidispatch.so`) 16 KB hizalı olduğu AAB üzerinde mutlaka doğrulanmalıdır (`GP-02`).
- **`FLAG_SECURE` ve Ekran Görüntüsü:** Release derlemesinde `adb screencap` siyah ekran verir; emülatör testlerinde `tools/emu-ui.py` erişilebilirlik ağacını kullanır. Mağaza ekran görüntüleri için ayrı bir strateji gerekir (`GP-09`).
- **Node 22 Test Runner:** `extension/` ve `relay/` altındaki `package.json` dosyalarında `node --test test/` komutu Node 22'de hata veriyordu; `node --test 'test/*.test.mjs'` olarak düzeltildi.
- **İmzalama:** `sifrekasa-release.jks` ve `keystore.properties` yerelde mevcut ve `.gitignore` ile korunuyor; asla git'e eklenmez.

## 6. Google Play Yayın Durumu Özeti

- **Mevcut Aşama:** Faz 1 & 2 neredeyse tamamlandı — İmzalı Release AAB (6.32 MB, 16 KB hizalı, 0 Lint hatası), GitHub Pages + Gizlilik Politikası (TR/EN), uygulama içi Hakkında/Gizlilik bölümü, Data Safety & mağaza metinleri ve 512x512 ikon + 1024x500 Özellik Grafiği hazır.
- **GitHub Deposu & Pages:** `https://github.com/b11s3c/SifreKasa` → `https://b11s3c.github.io/SifreKasa/privacy-policy.html` (`main` / `/docs`).
- **Kalan Adımlar:**
  1. Değişiklikleri commit + push edip GitHub Pages'i etkinleştirmek (`GP-06`).
  2. Telefon ekran görüntüleri (`GP-10`) ve gerçek cihaz release QA testi (`GP-05`).
  3. Play Console'da `dev.sifrekasa` uygulamasını oluşturup AAB'yi İç Test → Kapalı Test → Üretim akışına yüklemek (`GP-12`..`GP-15`).
