# ŞifreKasa — Test Stratejisi ve Yayın Öncesi QA Planı

## 1. Test Piramidi

| Katman | Konum | Araç | Kapsam |
|---|---|---|---|
| **1. Çekirdek Birim & Çapraz Kripto Testleri** | `core/src/test/` | JUnit 4 + Lazysodium JVM + Bouncy Castle + Tink + WebAuthn4j | Argon2id, XChaCha20-Poly1305, `SKV1`/`SKX1`, TOTP RFC 6238, WebAuthn/CBOR/PRF, `DomainMatcher`, `VaultMerge`, `SendProtocol`, `QrDecoder`, `PasswordGenerator` |
| **2. Android Birim & Politika Testleri** | `app/src/test/` | JUnit 4 | Manifest güvenlik değişmezleri (`allowBackup=false`, izinler, `<queries>` ↔ `privileged_apps.json` uyumu), veri çıkarma kuralları, iş mantığı |
| **3. Tarayıcı Eklentisi & Uyumluluk Testleri** | `extension/test/` | Node.js `node --test` | WebCrypto ↔ Kotlin `SendProtocol` çapraz çözme (`interop.test.mjs`), QR ve alan adı doğrulama |
| **4. Aktarıcı (Relay) Entegrasyon Testleri** | `relay/test/` | Node.js `node --test` + `wrangler dev` | Tek kullanımlık oturum, 409/404/413 sınırları, 60 sn süre aşımı |
| **5. Emülatör / Cihaz Uçtan Uca QA** | `tools/emu-ui.py` + Gerçek Cihaz | ADB + UI Automator | Release AAB/APK üzerinde tam kullanıcı akışı doğrulaması |

## 2. Otomatik Testleri Çalıştırma

Tek komutla tüm çevrimdışı otomatik testleri çalıştırmak için:

```bash
./scripts/test-all.sh
```

Bu betik sırasıyla:
1. `./gradlew :core:test` (Kotlin → JS interop vektörünü de üretir)
2. `./gradlew :app:testDebugUnitTest`
3. `cd extension && npm test` (Kotlin → JS çapraz çözme testi dahil)

Aktarıcı (relay) testleri için (ayrı terminalde `cd relay && npm run dev` çalışırken):
```bash
cd relay && RELAY_URL=http://127.0.0.1:8787 npm test
```

## 3. Google Play Yayın Öncesi Manuel QA Kontrol Listesi

Her release (`bundleRelease` / `assembleRelease`) öncesinde gerçek Android 14+ cihazda doğrulanır:

- [ ] **İlk Kurulum:** Yeni kasa oluşturma, zayıf/uyuşmayan parola hataları, kasa açılışı.
- [ ] **Biyometri & Kilit:** Biyometri etkinleştirme, arka plana geçince kilitlenme, hareketsizlik süresiyle kilitlenme, yanlış ana parola reddi.
- [ ] **Kayıt & 2FA:** Kayıt ekleme/düzenleme/silme, şifre üretici, QR ile TOTP ekleme, elle TOTP ekleme, kopyalama + pano süre sonunda temizlenme.
- [ ] **Yedekleme & Geri Yükleme:** `.skx` dışa aktarma, otomatik SAF yedeği, temiz kurulumda `.skx` içe aktarma ve kayıt birleştirme.
- [ ] **Otomatik Doldurma (Autofill):** Chrome / başka uygulamada doldurma, alan adı eşleşmesi, TOTP'nin panoya alınması.
- [ ] **Geçiş Anahtarları (Passkey):** Kayıt oluşturma ve oturum açma (destekleyen test sitesi: `webauthn.io` vb.).
- [ ] **Bilgisayara Gönder:** Eklenti QR tarama, alan adı onayı, tek seferlik doldurma.
- [ ] **Güvenlik:** Ekran görüntüsü ve son uygulamalar önizlemesinin engelli olduğu (`FLAG_SECURE`), R8/ProGuard altında çökme olmadığı (libsodium/JNA).
