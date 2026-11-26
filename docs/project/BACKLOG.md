# ŞifreKasa — Ürün ve Yayın Backlog'u

> **Öncelikler:** `P0` (Google Play yayını için zorunlu), `P1` (Yüksek değer / ilk güncellemeler), `P2` (İyileştirme / gelecek sürümler)
> **Durumlar:** `TODO` | `IN_PROGRESS` | `BLOCKED` | `DONE`

## Epic 1 — Proje Hafızası, Geliştirme ve Test Altyapısı

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| INFRA-01 | P0 | `DONE` | Kod tabanı analizi ve teknik denetim | Tüm modüller, Manifest, Gradle, güvenlik kuralları ve eksikler belgelendi |
| INFRA-02 | P0 | `DONE` | Proje hafıza ve geliştirme yapısı (`MEMORY.md`, `ARCHITECTURE.md`, `DEV_WORKFLOW.md`, `.agent/rules/`) | Oturumlar arası kalıcı hafıza ve çalışma kuralları oluşturuldu |
| INFRA-03 | P0 | `DONE` | Günlük (daily) kayıt sistemi (`docs/daily/`) | Şablon ve ilk günlük kayıt (`2026-11-26.md`) oluşturuldu |
| INFRA-04 | P0 | `DONE` | Backlog ve Google Play uçtan uca yayın planı | Tüm yayın gereksinimleri öncelikli maddelere bölündü |
| TEST-01 | P0 | `DONE` | Test stratejisi, `extension`/`relay` Node 22 test düzeltmesi, `:core` eksik testler, `:app` birim test temeli ve `scripts/test-all.sh` | `./scripts/test-all.sh` hatasız geçer |
| TEST-02 | P1 | `TODO` | GitHub Actions CI iş akışı (`.github/workflows/ci.yml`) | Her push/PR'da `:core:test`, `:app:testDebugUnitTest`, `:app:lintRelease`, `extension` testleri otomatik çalışır |

## Epic 2 — Google Play Teknik Yayın Hazırlığı

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| GP-01 | P0 | `DONE` | `applicationId` (`dev.sifrekasa`) nihai onayı ve sürüm numarası planı | Kullanıcı onayı alındı (`dev.sifrekasa`); açık kaynak GitHub deposu `b11s3c/SifreKasa` teyit edildi |
| GP-02 | P0 | `DONE` | İmzalı AAB (`bundleRelease`) üretimi ve **16 KB bellek sayfası hizalaması** kontrolü | `./gradlew :app:bundleRelease` başarılı (6.32 MB); tüm 64-bit `.so` (`libsodium.so`, `libjnidispatch.so` dahil) `16384` (16 KB) hizalı doğrulandı |
| GP-03 | P0 | `DONE` | Native debug symbols & R8 mapping yapılandırması | `ndk.debugSymbolLevel = "SYMBOL_TABLE"` eklendi; AAB `BUNDLE-METADATA` içinde R8 mapping ve native semboller üretiliyor |
| GP-04 | P0 | `DONE` | Android Lint (`lintRelease`) ve Play uyumluluk taraması | `./gradlew :app:lintRelease` 0 hata ile geçti (yalnızca bağımlılık sürüm önerileri) |
| GP-05 | P0 | `TODO` | Release derlemesiyle uçtan uca fiziksel/emülatör QA testi | `docs/testing/TEST_STRATEGY.md` içindeki yayın öncesi manuel QA listesinin tamamı geçti |

## Epic 3 — Gizlilik, Politika ve Mağaza Uyumluluğu

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| GP-06 | P0 | `IN_PROGRESS` | Gizlilik Politikası (TR + EN) hazırlanması ve herkese açık HTTPS URL'de yayınlanması | `docs/index.html`, `docs/privacy-policy.html`, `docs/.nojekyll` hazır; GitHub push + Repo Settings → Pages (`main` / `/docs`) açılacak |
| GP-07 | P0 | `DONE` | Uygulama içi "Hakkında / Sürüm / Gizlilik Politikası" bölümü (`SettingsScreen`) | `SettingsScreen.kt` içine sürüm (`BuildConfig`), Gizlilik Politikası ve GitHub kaynak kodu bağlantıları eklendi |
| GP-08 | P0 | `DONE` | Google Play Veri Güvenliği (Data Safety), İzinler, İçerik Derecelendirmesi (IARC) ve İnceleme Notları | `docs/play-store/DATA_SAFETY_AND_REVIEW.md` hazır |

## Epic 4 — Mağaza Vitrin Materyalleri (Store Listing)

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| GP-09 | P0 | `DONE` | Mağaza görselleri: 512x512 uygulama ikonu PNG + 1024x500 Özellik Grafiği (Feature Graphic) | `docs/play-store/assets/icon-512.png` ve `feature-graphic-1024x500.png` (RGB, şeffaflıksız) üretildi ve görsel olarak doğrulandı |
| GP-10 | P0 | `TODO` | Telefon ekran görüntüleri (4–8 adet, Türkçe arayüz) | Kasa listesi, kayıt ekleme/TOTP, şifre üretici, otomatik doldurma, ayarlar/yedekleme akışlarını gösteren temiz ekran görüntüleri hazır (`FLAG_SECURE` için demo/screenshot stratejisiyle) |
| GP-11 | P0 | `DONE` | Mağaza metinleri (Başlık ≤30, Kısa Açıklama ≤80, Tam Açıklama ≤4000, Sürüm Notları) | `docs/play-store/LISTING_TR.md` ve `LISTING_EN.md` hazır |

## Epic 5 — Google Play Console, Kapalı Test ve Canlıya Çıkış

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| GP-12 | P0 | `BLOCKED` | Google Play Console uygulama kaydı ve Play App Signing kurulumu | *(Kullanıcı Google Play Developer hesabı erişimi gerekir)* Uygulama oluşturuldu, yükleme anahtarı (upload key) tanımlandı |
| GP-13 | P0 | `TODO` | İç Test (Internal Testing) kanalına ilk AAB yüklemesi ve doğrulama | Play Store üzerinden indirilip gerçek cihazda kuruldu, çalıştı |
| GP-14 | P0 | `TODO` | Kapalı Test (Closed Testing — 12 test kullanıcısı, 14 kesintisiz gün) | Kişisel geliştirici hesabı gereksinimi karşılandı, test geri bildirimleri kapatıldı |
| GP-15 | P0 | `TODO` | Üretim (Production) incelemesine gönderim ve yayın | Uygulama onaylandı ve Google Play Store'da yayında |

## Epic 6 — Kalite, Güvenlik ve Gelecek İyileştirmeler

| ID | Öncelik | Durum | Görev | Kabul Kriteri |
|---|---|---|---|---|
| TEST-03 | P1 | `TODO` | `:app` Compose UI ve enstrümanlı testleri (`androidTest`) | Kasa oluşturma, kilit açma, kayıt ekleme/düzenleme, kilitlenme akışları otomatik test edilir |
| QUAL-01 | P1 | `TODO` | `WebAuthnTest.kt` deprecated `ServerProperty` uyarısının giderilmesi | `:core:compileTestKotlin` sıfır uyarıyla derlenir |
| QUAL-02 | P2 | `TODO` | İngilizce yerelleştirme (`values-en/strings.xml` ve UI metinlerinin kaynaklara taşınması) | Uygulama Türkçe ve İngilizce dillerini destekler |
| QUAL-03 | P2 | `TODO` | Fastlane Supply veya Gradle Play Publisher ile otomatik AAB yükleme hattı | Tek komutla Internal/Closed/Production yüklemesi yapılabilir |
