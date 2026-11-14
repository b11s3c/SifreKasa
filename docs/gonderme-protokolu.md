# Bilgisayara şifre gönderme protokolü (sürüm 1)

Bilgisayarda hiçbir şifre saklanmaz. Bir sitede oturum açarken tarayıcı eklentisi QR gösterir; telefon
QR'ı okur, kullanıcı onaylar ve kimlik bilgisi uçtan uca şifreli olarak eklentiye ulaşır.

Kod: telefon `core/.../send/` ve `app/.../ui/SendScreen.kt`, eklenti `extension/crypto.js`,
aktarıcı `relay/src/index.js`.

## Akış

1. **Eklenti** (açılır pencere açılınca): yeni bir X25519 anahtar çifti ve 16 baytlık rastgele oturum
   kimliği üretir. Özel anahtar dışa aktarılamaz bir `CryptoKey`'dir ve yalnızca pencerenin belleğindedir.
2. **QR**: `sifrekasa://send?v=1&r=<aktarıcı>&s=<oturum>&k=<açık anahtar>&d=<alan adı>&e=<bitiş>`
   Alan adı, isteği başlatan sekmenin adresinden alınır (sayfanın içeriğinden değil). `e`, 60 saniye
   sonrasının Unix zamanıdır.
3. **Telefon**: QR'ı okur; sürümü, uzunlukları, süreyi ve aktarıcı adresini doğrular. Alan adını
   gösterir, eşleşen kayıtları önerir, biyometri ya da ekran kilidiyle onay ister.
4. **Şifreleme** (telefon; ilkeller Tink'ten):

   ```
   paylaşılan = X25519(telefonun bu gönderim için ürettiği özel anahtar, eklentinin açık anahtarı)
   anahtar    = HKDF-SHA256(ikm = paylaşılan, tuz = oturum kimliği,
                            bilgi = "SifreKasa/send/v1" ‖ eklenti açık anahtarı ‖ telefon açık anahtarı, 32 bayt)
   kutu       = AES-256-GCM(anahtar, rastgele 12 bayt nonce, düz metin, AD = "SifreKasa/send/v1|" ‖ alan adı)
   ileti      = {"v":1, "k": Base64url(telefon açık anahtarı), "c": Base64url(nonce ‖ şifreli metin ‖ etiket)}
   düz metin  = {"d": alan adı, "u": kullanıcı adı, "p": şifre, "t": güncel TOTP kodu ya da null}
   ```

5. **Aktarıcı**: telefon iletiyi `POST /v1/s/<oturum>` ile bırakır; eklenti saniyede bir
   `GET /v1/s/<oturum>` ile sorar. İleti ilk okumada silinir.
6. **Eklenti**: aynı türetmeyi WebCrypto ile yapar, iletiyi çözer, düz metindeki alan adının ve sekmenin
   o anki adresinin isteği başlatan alan adıyla aynı olduğunu denetler, yalnızca o sekmenin üst
   çerçevesindeki formu doldurur. Sonra anahtar başvurusunu bırakır.

## Aktarıcı

| | |
|---|---|
| `POST /v1/s/<oturum>` | 201; oturum doluysa ya da kullanılmışsa 409; biçim hatasında 400; 4 KiB üstünde 413 |
| `GET /v1/s/<oturum>` | 200 ve ileti (silinir); yoksa, kullanılmışsa ya da süresi dolmuşsa 404 |

Her oturum ayrı bir Durable Object'tir: en çok bir ileti alır, en çok bir kez verir, 60 saniye sonra
kendini siler. Aktarıcı yalnızca şifreli iletiyi, oturum kimliğini ve bağlantı üst verisini (IP, zaman)
görür; hiçbir şey loglamaz.

## Güvenlik özellikleri

- **Aktarıcı araya giremez.** Eklentinin açık anahtarı telefona QR ile, aktarıcının dışından ulaşır.
  Aktarıcı iletiyi değiştirirse AES-GCM doğrulaması başarısız olur.
- **İleri gizlilik.** İki taraf da her gönderimde yeni anahtar çifti üretir ve saklamaz.
- **Bağlam bağlama.** Anahtar oturum kimliğine ve iki açık anahtara, şifreli metin alan adına bağlıdır;
  başka bir oturum ya da site için üretilmiş ileti çözülemez.
- **Tek kullanım ve süre.** QR 60 saniye geçerlidir; telefon süresi dolmuş ya da 120 saniyeden uzun
  ömürlü isteği reddeder. Aktarıcıdaki ileti bir kez okunur ve en çok 60 saniye yaşar.
- **Aktarıcı sabitleme.** Telefon ilk kullanımda aktarıcı adresini kullanıcı onayıyla kaydeder; sonrasında
  başka bir aktarıcıyı gösteren QR'ı reddeder. Aktarıcı adresi https olmak zorundadır.
- **Düşük dereceli açık anahtar** (tümü sıfır paylaşılan sır) reddedilir.

## Bilinen sınırlar

- **Sahte QR.** Eklenti kalıcı bir kimlik saklamaz (gereksinim: diske hiçbir şey yazılmaz), dolayısıyla
  telefon QR'ın gerçekten senin eklentinden geldiğini kriptografik olarak bilemez. Bir saldırgan kendi
  anahtarıyla ve "banka.example" alan adıyla bir QR gösterip okutmanı sağlarsa, onayladığın kimlik
  bilgisi ona gider. Önlemler: telefon alan adını büyük harflerle gösterir, yalnızca o alan adıyla
  eşleşen kayıtları önerir, aktarıcı sabitlemesi başka aktarıcıları reddeder ve her gönderim için onay
  ister. **Yalnızca kendi bilgisayarındaki eklentinin gösterdiği QR'ı okut.**
- **Sayfadaki betikler.** Form doldurulduktan sonra değerler sayfanın kendi betiklerine açıktır; bu,
  şifreyi elle yazmakla aynıdır.
- **Gömülü çerçeveler.** Giriş formu başka bir kökenden gelen bir çerçevedeyse doldurulmaz.
- **Üst veri.** Aktarıcıyı işleten (Cloudflare ve hesabın sahibi olarak sen) ne zaman gönderim
  yapıldığını ve iki tarafın IP adreslerini görebilir; hangi site ya da hesap olduğunu göremez.
