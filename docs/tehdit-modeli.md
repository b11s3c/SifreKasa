# Tehdit modeli

Bu belge Şifre Kasa'nın neye karşı koruduğunu, neye karşı korumadığını ve hangi varsayımlara
dayandığını anlatır.

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
| Ağ üzerinden sızma, telemetri | Analitik, çökme raporlama ve reklam bileşeni yok. `INTERNET` izni yalnızca "bilgisayara gönder" içindir: tek ağ kodu, kullanıcı onayından sonra uçtan uca şifreli tek bir iletiyi kayıtlı aktarıcıya gönderir | Yedek konumu Drive ise yüklemeyi Drive uygulaması yapar; yalnızca şifreli dosyayı görür |
| Kayıtlara (log) sızma | Gizli alanlar loglanmaz; model sınıflarının `toString` çıktıları ve hata iletileri sır içermez | — |
| Kamera | Yalnızca QR taramak için; kareler bellekte çözülür, kaydedilmez | — |
| Sahte ya da hatalı kripto | Kendi algoritmamız yok; libsodium kullanılır, çıktılar iki bağımsız kütüphaneyle ve RFC vektörleriyle sınanır | Kütüphane ya da platform açıkları |

## Otomatik doldurma

| Tehdit | Önlem | Kalan risk |
|---|---|---|
| Servisin arka planda veri sızdırması | Servis kasaya dokunmaz; yanıtında yalnızca değer taşımayan "Şifre Kasa ile doldur" seçeneği vardır. Değerler, kullanıcı kilidi açıp kaydı seçtikten sonra seçim ekranından verilir | — |
| Oltalama sitesi | Kayıt yalnızca alan adı aynıysa ya da kayıttaki alan adının alt alan adıysa önerilir; benzer adlar eşleşmez | Kullanıcı "başka bir kayıt ara" ile elle yanlış siteye doldurabilir; ekranda uyarı gösterilir |
| Sahte tarayıcının yanlış alan adı bildirmesi | Bildirilen alan adına yalnızca Google'ın açık listesindeki ve imza sertifikası tutan tarayıcılarda güvenilir | Listede olmayan tarayıcılar uygulama gibi işlenir (otomatik eşleşme yok) |
| Sayfadaki gömülü çerçeveler (iframe) | Formda birden fazla farklı alan adı görülürse hiçbiri kullanılmaz, otomatik eşleşme yapılmaz | — |
| Kötü amaçlı yerel uygulama | Uygulamalar için otomatik eşleşme yoktur; kullanıcı kaydı kendisi arar, uygulamanın adı ve paket adı gösterilir | Uygulama ile site arasındaki bağ (Digital Asset Links) doğrulanmaz; karar kullanıcıdadır |

Yeni şifre kaydetme teklifi yapılmaz; form içerikleri okunmaz ve saklanmaz. Kayıtta 2FA varsa
doldurma anında güncel kod panoya alınır (pano yine süreyle temizlenir).

## Geçiş anahtarları

| Tehdit | Önlem | Kalan risk |
|---|---|---|
| Kullanıcı onayı olmadan imza | Servis imza üretmez. İmza ve anahtar üretimi yalnızca ekranda, kasa az önce açılmışsa ya da ayrıca biyometri / ekran kilidiyle doğrulandıktan sonra yapılır | — |
| Kilitliyken hangi sitelerde hesap olduğunun sızması | Kilitliyken sisteme yalnızca "kilidi aç" seçeneği verilir; anahtar listesi kasa açıldıktan sonra oluşturulur | — |
| Sahte tarayıcının başka site adına istekte bulunması | Web kökeni yalnızca listede olan ve imzası tutan çağıranlardan kabul edilir; diğerleri reddedilir | Listedeki bir tarayıcının kendisinin ele geçirilmesi |
| Yerel uygulamanın başka sitenin anahtarını istemesi | İmzalanan veride köken olarak uygulamanın imza özeti (`android:apk-key-hash:…`) yer alır; site bunu tanımıyorsa reddeder. Kullanıcıya isteyen uygulama gösterilir ve onayı alınır | Uygulama–site bağı (Digital Asset Links) ağ erişimi olmadığı için doğrulanmaz; koruma sitenin köken denetimine dayanır |
| Özel anahtarın ele geçmesi | Anahtar kasada, DEK ile şifreli durur; yedekte de şifrelidir | Anahtar donanıma bağlı değildir: kasayı ya da yedeği açabilen kişi anahtarı kopyalayabilir |

Özel anahtarların Android Keystore yerine kasada tutulması bilinçli bir tercihtir: Keystore'daki
anahtar cihazdan çıkarılamaz, dolayısıyla yedeklenemez ve telefon kaybolunca tüm geçiş anahtarları
da kaybolur. Karşılığında anahtarlar "eşitlenen" sınıftadır; yanıtlarda yedeklenebilir/yedeklendi
bayrakları (BE/BS) buna göre işaretlenir, imza sayacı tutulmaz ve cihaz kanıtı (attestation) verilmez.

**PRF uzantısı.** Bazı siteler ve uygulamalar (ör. şifreli sohbet yedekleri) geçiş anahtarından bir
şifreleme anahtarı türetir. Her geçiş anahtarının bu iş için ayrı, rastgele bir gizli değeri vardır;
çıktı = HMAC-SHA-256(gizli değer, SHA-256("WebAuthn PRF" ‖ 0x00 ‖ girdi)). Bu değer de kasada ve şifreli
yedekte durur. Geçiş anahtarı silinirse ondan türetilen anahtarlar bir daha üretilemez: o anahtarla
şifrelenmiş veri (ör. yedek) kalıcı olarak açılamaz hale gelir.

Bilgisayardaki tarayıcının gösterdiği QR ile oturum açmada (hybrid akış) Bluetooth yakınlık denetimi
ve şifreli tünel Android / Play Services tarafından yürütülür; Şifre Kasa'ya istek, çağıranı Play
Services olan sıradan bir istek olarak ulaşır ve yukarıdaki kurallar aynen geçerlidir.

## Bilgisayara gönderme

Protokol ve güvenlik özellikleri [gonderme-protokolu.md](gonderme-protokolu.md) belgesindedir. Özet:

| Tehdit | Önlem | Kalan risk |
|---|---|---|
| Aktarıcının ya da ağın veriyi görmesi/değiştirmesi | X25519 + HKDF + AES-256-GCM ile uçtan uca şifreleme; açık anahtar QR ile aktarıcının dışından taşınır | Üst veri: gönderim zamanı ve IP adresleri |
| Yakalanan iletinin yeniden kullanılması | Her gönderimde iki tarafta yeni anahtar; oturum tek kullanımlık, 60 saniye ömürlü | — |
| Sahte QR ile oltalama | Alan adı gösterilir, yalnızca eşleşen kayıtlar önerilir, aktarıcı sabitlenir, her gönderim onay ister | Eklenti kalıcı kimlik saklamadığı için QR'ın kaynağı kriptografik olarak doğrulanamaz; kullanıcı yalnızca kendi eklentisinin QR'ını okutmalıdır |
| Bilgisayarda kalıcı iz | Eklenti yalnızca aktarıcı adresini saklar; anahtar ve şifre yalnızca bellekte, açılır pencere kapanınca yok olur | Doldurulan değerler sayfanın betiklerine ve tarayıcının kendi şifre kaydetme teklifine açıktır |
| Yanlış sekmeye doldurma | Yalnızca isteği başlatan sekme; doldurmadan önce sekmenin hâlâ aynı alan adında olduğu denetlenir | — |

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
