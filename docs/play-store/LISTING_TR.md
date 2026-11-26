# Google Play Mağaza Metinleri (Türkçe)

## Uygulama Adı (en fazla 30 karakter)
`ŞifreKasa` *(9 karakter)*

## Kısa Açıklama (en fazla 80 karakter)
`Açık kaynak, çevrimdışı şifre, 2FA ve geçiş anahtarı (Passkey) yöneticisi.` *(74 karakter)*

## Tam Açıklama (en fazla 4000 karakter)
ŞifreKasa, gizliliğini merkeze alan, açık kaynaklı ve çevrimdışı öncelikli bir şifre, 2FA (TOTP) ve geçiş anahtarı (Passkey) yöneticisidir.

Verilerin yalnızca kendi cihazında, kanıtlanmış modern kriptografiyle (Argon2id + XChaCha20-Poly1305) şifrelenerek saklanır. Analitik, reklam, takipçi veya çökme raporu SDK'sı içermez.

ÖNE ÇIKAN ÖZELLİKLER
• Güçlü Yerel Şifreleme: Kasa dosyası Argon2id (128 MiB) ve XChaCha20-Poly1305 (libsodium) ile korunur.
• Biyometrik Kilit & Otomatik Kilitleme: Güçlü biyometri (parmak izi) desteği, arka plana geçince veya hareketsizlikte otomatik kilit ve süreli pano temizleme.
• 2FA (TOTP) Desteği: QR kod tarayarak veya elle ekleyerek RFC 6238 uyumlu tek kullanımlık doğrulama kodları üretin.
• Geçiş Anahtarları (Passkey): Android Credential Manager ile modern, parolasız oturum açma ve PRF uzantısı desteği.
• Güvenli Otomatik Doldurma: Uygulamalarda ve tarayıcılarda oltalama korumalı alan adı eşleşmesiyle hızlı doldurma.
• Şifreli Yedekleme ve İçe Aktarım: Ayrı parolayla şifrelenmiş .skx yedek dosyası oluşturun, dilediğiniz yere (cihaz veya Google Drive) otomatik yedekleyin ve güvenle geri yükleyin.
• Bilgisayara Şifre Gönderme (İsteğe Bağlı): Açık kaynaklı tarayıcı eklentisi ve kendi aktarıcınız üzerinden X25519 + AES-256-GCM uçtan uca şifreli, tek seferlik güvenli gönderim.
• Ekran Güvenliği: Ekran görüntüsü, ekran kaydı ve son uygulamalar önizlemesi engellenir.

AÇIK KAYNAK VE ŞEFFAF
ŞifreKasa'nın tüm kaynak kodu, dosya biçimi ve tehdit modeli GitHub üzerinde herkesin incelemesine açıktır:
https://github.com/b11s3c/SifreKasa

Gereksinim: Android 14 ve üzeri.

## Sürüm Notları (v0.1.1 — İlk Yayın)
- Şifreli yerel kasa (Argon2id + XChaCha20-Poly1305) ve biyometrik kilit
- 2FA (TOTP) QR tarama ve kod üretimi
- Android Otomatik Doldurma ve Geçiş Anahtarı (Passkey + PRF) sağlayıcısı
- Şifreli .skx dışa/içe aktarım ve otomatik yedekleme
- Bilgisayara QR ile uçtan uca şifreli gönderim
