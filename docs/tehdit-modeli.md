# Tehdit modeli

Bu belge Şifre Kasa'nın neye karşı koruduğunu, neye karşı korumadığını ve hangi varsayımlara
dayandığını anlatır. Aşama 4 (otomatik doldurma, geçiş anahtarları) ve Aşama 5 (tarayıcı eklentisi)
tamamlandıkça ilgili bölümler eklenecektir.

## Korunan varlıklar

- Giriş kayıtları: kullanıcı adları, şifreler, URL'ler, notlar.
- 2FA (TOTP) gizli anahtarları.
- Ana parola ve dışa aktarım / yedek parolası.
- Veri anahtarı (DEK) ve yedek anahtarı.

## Güven sınırları ve varsayımlar

- Android işletim sistemi, uygulama korumalı alanı ve donanım destekli Keystore sağlamdır.
- Cihaz root'lanmamıştır ve kötü amaçlı bir erişilebilirlik servisi ya da klavye kurulu değildir.
- libsodium ve platformun kripto sağlayıcısı doğru çalışır.
- Kullanıcı ana parolasını ve yedek parolasını tahmin edilemeyecek kadar güçlü seçmiştir.

## Tehditler ve önlemler

| Tehdit | Önlem | Kalan risk |
|---|---|---|
| Kilitli telefonun çalınması | Kasa dosyası DEK ile şifreli; DEK yalnızca ana parola (Argon2id, 128 MiB) ya da biyometri korumalı Keystore anahtarıyla açılır | Zayıf ana parola çevrimdışı tahmin edilebilir |
| Kilidi açık telefonun kısa süreli ele geçmesi | Arka plana geçince ve işlem yapılmayınca otomatik kilit; her biyometrik açılışta yeniden doğrulama | Kasa o an açıksa içerik görülebilir |
| Cihaza yeni parmak izi eklenmesi | Keystore anahtarı yeni biyometri kaydında geçersizleşir; ana parola gerekir | — |
| Yedek dosyasının ele geçmesi (Drive hesabı, paylaşılan klasör) | Dosya ayrı bir parolayla, Argon2id + XChaCha20-Poly1305 ile şifreli; düz metin alan yok | Zayıf yedek parolası çevrimdışı tahmin edilebilir; dosya boyutu yaklaşık kayıt sayısını ele verir |
| Yedek ya da kasa dosyasının değiştirilmesi | Başlık dahil her bayt AEAD ile doğrulanır; değişiklik "yanlış parola ya da bozuk dosya" olarak reddedilir | Saldırgan eski bir yedeği yenisinin yerine koyabilir (geri sarma); içerik yine de okunamaz |
| Bozuk ya da yarım yazılmış yedek | Yazılan dosya geri okunup bayt bayt karşılaştırılır; kasa dosyası atomik yazılır | Bulut sağlayıcısının sonradan bozması fark edilmez |
| Sistem yedeklerine sızma | `allowBackup=false` ve veri çıkarma kurallarıyla bulut yedeği ve cihazdan cihaza aktarım kapalı | — |
| Ekran görüntüsü, ekran kaydı, son uygulamalar önizlemesi | `FLAG_SECURE` ve son uygulamalar görüntüsünün kapatılması | Fiziksel olarak ekrana bakan biri |
| Panodan sızma | Kopyalanan değer "hassas" işaretlenir ve ayarlanan sürede silinir | Süre dolmadan panoyu okuyan başka bir uygulama ya da klavye |
| Ağ üzerinden sızma, telemetri | Uygulamada `INTERNET` izni, analitik ve çökme raporlama yok | Yedek konumu Drive ise yüklemeyi Drive uygulaması yapar; yalnızca şifreli dosyayı görür |
| Kayıtlara (log) sızma | Gizli alanlar loglanmaz; model sınıflarının `toString` çıktıları ve hata iletileri sır içermez | — |
| Kamera | Yalnızca QR taramak için; kareler bellekte çözülür, kaydedilmez | — |
| Sahte ya da hatalı kripto | Kendi algoritmamız yok; libsodium kullanılır, çıktılar iki bağımsız kütüphaneyle ve RFC vektörleriyle sınanır | Kütüphane ya da platform açıkları |

## Bilinçli tercihler

- **Yedek anahtarı kasada saklanır.** Otomatik yedeğin parola sormadan çalışabilmesi için yedek
  parolasından türetilen anahtar kasanın içinde durur. Kasayı açabilen biri bu anahtara da ulaşır;
  ancak o kişi zaten tüm içeriğe erişmiştir. Parolanın kendisi saklanmaz.
- **Dosya seçici açıkken arka plan kilidi ertelenir.** Dışa aktarım, yedek konumu ve içe aktarım
  için sistemin dosya seçicisi açıldığında kasa en çok 3 dakika açık kalır; yoksa seçim sonucu
  işlenemez. İşlem yapılmayınca kilitleme bu sırada da geçerlidir.
- **2FA anahtarları şifrelerle aynı kasadadır.** Kullanım kolaylığı için ikinci etken aynı yerde
  tutulur; kasayı ele geçiren ikisine birden ulaşır. Bu ayrımı önemseyen hesaplar için 2FA başka
  bir cihazda tutulmalıdır.
- **Hata ayıklama derlemesi.** Şu an kurulan APK `debuggable`'dır; bilgisayara USB ile bağlı ve
  hata ayıklamaya izinli bir cihazda uygulama verisi `adb` ile okunabilir (dosyalar yine şifrelidir).
  Günlük kullanım için imzalı sürüm derlemesi kullanılmalıdır.

## Kapsam dışı

- Root'lanmış cihaz, işletim sistemi açıkları, kötü amaçlı erişilebilirlik servisi ya da klavye.
- Kasa açıkken bellek dökümü alabilen saldırgan. Kotlin/JVM'de `String` değerleri silinemez;
  parolalar ve anahtarlar mümkün olan yerde bayt dizisi olarak tutulup kullanım sonrası sıfırlanır,
  ancak arayüzde gösterilen değerler çöp toplayıcıya kadar bellekte kalır.
- Zorla parola söyletme, omuz üstünden izleme.
- Ana parolanın unutulması: kurtarma yolu yoktur.
