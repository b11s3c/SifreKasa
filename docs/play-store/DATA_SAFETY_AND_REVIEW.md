# Google Play — Veri Güvenliği (Data Safety) ve İnceleme Beyanları

Bu dosya Google Play Console formlarını birebir ve tutarlı doldurmak için hazırlanmıştır.

## 1. Gizlilik Politikası URL
- **URL:** `https://b11s3c.github.io/SifreKasa/privacy-policy.html`
- **Ön koşul:** GitHub Repo → Settings → Pages → Source: `Deploy from a branch` → Branch: `main`, Folder: `/docs`.

## 2. Veri Güvenliği (Data Safety Form)
- **Uygulamanız gerekli kullanıcı verisi türlerinden herhangi birini topluyor veya paylaşıyor mu?** → **Hayır (No)**
- **Açıklama (gerekirse):** Tüm parolalar, TOTP gizli anahtarları ve geçiş anahtarları yalnızca kullanıcının cihazında Argon2id + XChaCha20-Poly1305 ile şifrelenerek tutulur. Analitik, çökme raporlama veya reklam SDK'sı yoktur. İsteğe bağlı "Bilgisayara gönder" özelliği yalnızca kullanıcının kendi seçtiği aktarıcıya (relay) tek kullanımlık uçtan uca şifreli ileti gönderir.

## 3. Uygulama İçeriği (App Content) Beyanları
- **Reklamlar (Ads):** Hayır, uygulamam reklam içermiyor.
- **Uygulama Erişimi (App Access):** Tüm işlevler özel erişim kısıtlaması olmadan kullanılabilir (sunucu hesabı gerekmez).
  - **İnceleyici Notu (Reviewer Instructions):**
    > ŞifreKasa is an offline-first password, 2FA (TOTP), and passkey manager. No online account or login credentials are required to review the app. On first launch, create a local vault by entering any master password (e.g., `TestPassword123!`). All data stays encrypted on-device. The `CAMERA` permission is optional and used only for scanning TOTP / Send-to-PC QR codes. The `INTERNET` permission is used only when the user explicitly scans a Send-to-PC QR code and confirms sending an end-to-end encrypted credential to their own relay server.
- **İçerik Derecelendirmesi (IARC):** Yardımcı Program / Üretkenlik (Utility / Productivity) — Şiddet, cinsellik, kumar, kullanıcılar arası sohbet yok → `PEGI 3` / `Everyone`.
- **Hedef Kitle (Target Audience):** 18 yaş ve üzeri (veya 13+ — çocuklara yönelik değil).
- **Haber Uygulaması / COVID-19 / Finans / Sağlık / Devlet:** Hiçbiri (Hayır).

## 4. İzin Gerekçeleri
- `android.permission.CAMERA`: Yalnızca TOTP ve Bilgisayara Gönder QR kodlarını cihazda taramak için.
- `android.permission.INTERNET`: Yalnızca kullanıcı onaylı, uçtan uca şifreli Bilgisayara Gönder akışı için.
- `<queries>`: `QUERY_ALL_PACKAGES` kullanılmaz; yalnızca Autofill / Passkey için bilinen tarayıcıların imza doğrulaması amacıyla açık paket listesi içerir.
