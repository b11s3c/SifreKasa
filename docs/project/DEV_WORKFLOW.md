# ŞifreKasa — Geliştirme Akışı ve Kurallar (DEV WORKFLOW)

## 1. Oturum Başlangıç ve Bitiş Disiplini

Her geliştirme oturumu şu sırayı izler:

1. **Bağlam Yükleme:** `docs/project/MEMORY.md`, `docs/project/BACKLOG.md` ve en son `docs/daily/YYYY-MM-DD.md` dosyasını oku.
2. **Görev Seçimi:** Backlog'dan öncelikli (`P0` → `P1` → `P2`) bir iş seç ve durumunu `IN_PROGRESS` yap.
3. **Geliştirme ve Test:** Değişikliği yap, ilgili birim/entegrasyon testlerini yaz ve `./scripts/test-all.sh` ile doğrula.
4. **Kapanış ve Kayıt:**
   - `docs/project/BACKLOG.md` içinde tamamlanan işin durumunu `DONE` yap.
   - Yeni bir mimari karar veya teknik tuzak ortaya çıktıysa `docs/project/MEMORY.md` dosyasını güncelle.
   - `docs/daily/YYYY-MM-DD.md` dosyasına yapılanları, test sonuçlarını, riskleri ve sonraki adımı işle.

## 2. Dal (Branch) ve Commit Kuralları

- Ana dal: `main` (her zaman derlenebilir ve testleri geçer durumda tutulur).
- Özellik/düzeltme dalları (isteğe bağlı): `feat/<kısa-ad>`, `fix/<kısa-ad>`, `release/vX.Y.Z`.
- Commit mesajları: Mevcut depo stiline uygun olarak açık, kısa, Türkçe özet (ör. `Ayarlar ekranına gizlilik politikası ve sürüm bilgisi eklendi`).
- Sırlar ve anahtarlar (`*.jks`, `*.keystore`, `keystore.properties`, Play API JSON anahtarları) **asla** commit edilmez.

## 3. Tamamlanma Tanımı (Definition of Done — DoD)

Bir backlog maddesinin `DONE` sayılması için:
- [ ] Kod temiz derlenir (`./gradlew :core:test :app:testDebugUnitTest` geçer).
- [ ] Güvenlik değişmezleri (`docs/project/MEMORY.md` §3) korunmuştur.
- [ ] Değişiklik için ilgili birim testi eklendi veya güncellendi.
- [ ] İlgili dokümantasyon (`MEMORY.md`, `BACKLOG.md`, `daily/`) güncellendi.

## 4. Sık Kullanılan Komutlar

```bash
# Tüm otomatik testleri çalıştır (core + app unit + extension)
./scripts/test-all.sh

# Yalnızca çekirdek kripto/protokol testleri
./gradlew :core:test

# Yalnızca Android birim testleri
./gradlew :app:testDebugUnitTest

# Google Play için imzalı AAB üretimi
./gradlew :app:bundleRelease

# Yerel kurulum için imzalı Release APK
./gradlew :app:assembleRelease

# Android Lint denetimi
./gradlew :app:lintRelease
```
