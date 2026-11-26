---
description: ŞifreKasa proje hafızası, backlog, test ve günlük (daily) çalışma kuralları
---

# ŞifreKasa Çalışma Kuralları

1. **Önce Hafızayı Oku:** Bu projede herhangi bir geliştirme, hata giderme veya yayın işlemine başlamadan önce `docs/project/MEMORY.md` ve `docs/project/BACKLOG.md` dosyalarını kontrol et.
2. **Güvenlik Değişmezlerini Koru:** `docs/project/MEMORY.md` §3'teki güvenlik kurallarını (sıfır telemetri, `FLAG_SECURE`, `allowBackup=false`, özel kripto yok, açık `<queries>`, sır loglama yok) asla bozma.
3. **Günlük (Daily) Kayıt Tut:** Anlamlı her çalışma oturumunda `docs/daily/YYYY-MM-DD.md` dosyasını oluştur veya güncelle (`docs/daily/TEMPLATE.md` formatıyla).
4. **Backlog ve Hafızayı Güncel Tut:** Tamamlanan görevleri `docs/project/BACKLOG.md` içinde `DONE` olarak işaretle; yeni mimari kararları (ADR) veya teknik bulguları `docs/project/MEMORY.md` dosyasına ekle.
5. **Doğrulama:** Kod değişikliklerinden sonra `./scripts/test-all.sh` (veya ilgili modül testleri) çalıştırıp sonuçları doğrula.
