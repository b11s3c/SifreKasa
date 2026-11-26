# ŞifreKasa — Google Play Uçtan Uca Yayın Planı

Bu belge, ŞifreKasa'nın (`dev.sifrekasa`) mevcut geliştirme durumundan Google Play Store'da canlıya çıkışına kadar tüm teknik, hukuki/politika, vitrin ve operasyon adımlarını özetler.

## 1. Sorumluluk Paylaşımı (Otomasyon vs. Kullanıcı Eylemi)

- **Uçtan uca ajan tarafından yapılacaklar:**
  - Teknik hazırlık: AAB derlemesi, 16 KB `.so` hizalama kontrolü, native debug symbols, R8/ProGuard doğrulama, Lint temizliği, uygulama içi sürüm/gizlilik bağlantısı.
  - Test altyapısı: Tüm birim/entegrasyon testleri, yayın öncesi QA doğrulaması.
  - Politika ve metinler: Gizlilik Politikası (TR + EN, GitHub Pages'e hazır HTML/MD), Veri Güvenliği (Data Safety) form yanıtları, mağaza metinleri ve inceleme notları.
  - Vitrin görselleri: 512x512 ikon PNG, 1024x500 Özellik Grafiği ve telefon ekran görüntüleri.
- **Kullanıcı hesabı / Play Console erişimi gerektiren adımlar:**
  - Google Play Developer hesabı oturum açma ve uygulama oluşturma (veya Play API Service Account JSON / tarayıcı oturumu paylaşımı).
  - Kapalı Test için 12 test kullanıcısının (kişisel geliştirici hesabıysa) davet edilmesi ve 14 gün boyunca etkin kalması.

## 2. Kritik Teknik Kontroller

### 2.1 Application ID
- Mevcut değer: `dev.sifrekasa`.
- **Uyarı:** Google Play'e ilk AAB yüklendikten sonra `applicationId` bir daha değiştirilemez. Yayın öncesinde bu kimlik kesinleştirilmelidir.

### 2.2 16 KB Bellek Sayfası Hizalaması (Android 15/16 Play Zorunluluğu)
- Uygulama yerel kütüphane içerir: `lazysodium-android:5.2.0` (`libsodium.so`) ve `jna:5.19.1` (`libjnidispatch.so`).
- Google Play, `targetSdk >= 35` uygulamalarında 64-bit yerel kütüphanelerin (`arm64-v8a`, `x86_64`) **16 KB ELF LOAD segment hizalamasına** sahip olmasını zorunlu kılar.
- AAB üretildikten sonra `zipalign -c -P 16 -v 4` veya `llvm-objdump -p` / `readelf -l` ile tüm `.so` dosyalarının `LOAD` hizalaması (`2**14` = 16384) doğrulanmalıdır.

### 2.3 İmzalama ve Play App Signing
- Yerel `sifrekasa-release.jks` dosyası mevcuttur.
- Google Play'de **Play App Signing** etkinleştirilmelidir (yeni uygulamalar için zorunlu). Yerel keystore **Upload Key** olarak kullanılır ve güvenli şekilde çevrimdışı yedeklenir.

### 2.4 Native Debug Symbols & R8 Mapping
- `app/build.gradle.kts` içinde `release` bloğuna `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` eklenerek Play Console'un "yerel hata ayıklama sembolleri eksik" uyarısı önlenir.
- `app/build/outputs/mapping/release/mapping.txt` AAB içine otomatik gömülür.

## 3. Google Play Politika & Veri Güvenliği (Data Safety) Taslağı

ŞifreKasa'nın mimarisi gereği beyanlar son derece sade ve güçlüdür:

| Play Console Alanı | Beyan |
|---|---|
| **Veri toplama / paylaşma** | Hayır — uygulama geliştirici sunucularına hiçbir kullanıcı verisi toplamaz ve üçüncü taraflarla paylaşmaz. |
| **Kişisel veriler / Şifreler** | Tüm veriler yalnızca cihazda Argon2id + XChaCha20-Poly1305 ile şifrelenerek saklanır. |
| **İzin: `CAMERA`** | Yalnızca 2FA (TOTP) ve bilgisayara gönderme QR kodlarını cihazda taramak için kullanılır; görüntü kaydedilmez veya iletilmez. |
| **İzin: `INTERNET`** | Yalnızca kullanıcının kendi seçtiği ve onayladığı aktarıcıya (relay) tek kullanımlık uçtan uca şifreli gönderim yapmak içindir. Telemetri, reklam veya çökme raporu yoktur. |
| **Paket Görünürlüğü (`<queries>`)** | `QUERY_ALL_PACKAGES` kullanılmaz; yalnızca bilinen tarayıcıların imza sertifikasını doğrulamak için açık `<queries>` listesi bulunur. |
| **Reklamlar** | Uygulama reklam içermez. |
| **Hedef Kitle** | 18+ (veya 13+ — çocuklara yönelik değil). |
| **Gizlilik Politikası URL** | `CAMERA` izni nedeniyle **zorunlu**. Herkese açık HTTPS URL gerekir (ör. GitHub Pages). |

## 4. Yayın Aşamaları Takvimi

1. **Faz 1 — Teknik Hazırlık (`GP-01`..`GP-05`):** AAB derlemesi, 16 KB kontrolü, debug symbols, Lint, tam QA testi.
2. **Faz 2 — Politika ve Vitrin (`GP-06`..`GP-11`):** Gizlilik politikası yayını, Ayarlar ekranı güncellemesi, mağaza metinleri ve görselleri.
3. **Faz 3 — İç Test (`GP-12`..`GP-13`):** Play Console kaydı, Internal Testing yayını, mağaza üzerinden kurulum testi.
4. **Faz 4 — Kapalı Test (`GP-14`):** 12 test kullanıcısı, 14 gün takip ve geri bildirim düzeltmeleri.
5. **Faz 5 — Üretim (`GP-15`):** Production gönderimi, inceleme takibi ve canlıya çıkış.
