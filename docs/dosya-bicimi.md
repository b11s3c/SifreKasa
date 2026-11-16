# Dosya biçimleri

ŞifreKasa iki ayrı dosya biçimi kullanır. İkisi de aynı kripto ilkellerine dayanır ama farklı
anahtarlarla şifrelenir ve birbirinin yerine kabul edilmez.

| | Kasa dosyası | Dışa aktarım / yedek dosyası |
|---|---|---|
| Uzantı, sihirli değer | `vault.skv`, `SKV1` | `.skx`, `SKX1` |
| Nerede durur | Yalnızca uygulamanın özel dizininde | Kullanıcının seçtiği yerde (Drive dahil) |
| Parola | Ana parola | Ayrı dışa aktarım / yedek parolası |
| Anahtar yapısı | Rastgele DEK, paroladan türetilen anahtarla sarılı | Doğrudan paroladan türetilen anahtar |
| Kod | `core/.../vault/VaultFormat.kt` | `core/.../vault/ExportFormat.kt` |

## Ortak ilkeller

- **Anahtar türetme:** Argon2id sürüm 1.3 (libsodium `crypto_pwhash`), 16 bayt rastgele tuz, 32 bayt çıktı.
  Yeni dosyalarda 3 geçiş ve 128 MiB bellek. Parametreler başlıkta yazılıdır; okurken 1–16 geçiş ve
  8 KiB–1 GiB dışındaki değerler türetme başlamadan reddedilir.
- **Şifreleme:** XChaCha20-Poly1305 (IETF), 32 bayt anahtar, 24 bayt rastgele nonce, 16 bayt etiket.
- **Parola kodlaması:** UTF-8 baytları, Unicode normalizasyonu uygulanmadan. Aynı parola farklı bir
  klavyede farklı bir Unicode biçimiyle yazılırsa eşleşmez.
- Tüm tamsayılar big-endian'dır. Gövde, UTF-8 JSON'dur.

## Kasa dosyası (`SKV1`, sürüm 1)

| Konum | Uzunluk | İçerik |
|---:|---:|---|
| 0 | 4 | `SKV1` |
| 4 | 1 | Biçim sürümü = 1 |
| 5 | 1 | KDF kimliği = 1 (Argon2id v1.3) |
| 6 | 4 | Argon2 geçiş sayısı |
| 10 | 4 | Argon2 bellek (KiB) |
| 14 | 16 | Tuz |
| 30 | 24 | DEK sarma nonce'u |
| 54 | 48 | Sarılmış DEK (32 bayt + 16 bayt etiket) |
| 102 | 24 | Gövde nonce'u |
| 126 | … | Gövde şifreli metni + 16 bayt etiket |

- **KEK** = Argon2id(ana parola, tuz). Sarılmış DEK = XChaCha20-Poly1305(KEK, sarma nonce'u, DEK),
  ek veri (AD) = 0–29. baytlar.
- **Gövde** = XChaCha20-Poly1305(DEK, gövde nonce'u, JSON), AD = 0–125. baytlar (başlığın tamamı).
  Böylece başlıktaki her bayt doğrulanır.
- Her kayıtta dosyanın tamamı yeni bir gövde nonce'uyla yeniden yazılır (atomik yazma).
- Ana parola değişince yalnızca tuz, sarma nonce'u ve sarılmış DEK değişir; DEK aynı kalır.

**Biyometrik sarma** kasa dosyasında değildir. DEK'in ikinci kopyası `dek.bio` dosyasında durur:
12 bayt IV + AES-256-GCM(Keystore anahtarı, DEK). Keystore anahtarı cihazdan çıkarılamaz, her
kullanımda güçlü biyometri ister ve yeni biyometri kaydında kalıcı olarak geçersizleşir. Bu dosya
hiçbir yedeğe girmez.

## Dışa aktarım / yedek dosyası (`SKX1`, sürüm 1)

| Konum | Uzunluk | İçerik |
|---:|---:|---|
| 0 | 4 | `SKX1` |
| 4 | 1 | Biçim sürümü = 1 |
| 5 | 1 | KDF kimliği = 1 (Argon2id v1.3) |
| 6 | 4 | Argon2 geçiş sayısı |
| 10 | 4 | Argon2 bellek (KiB) |
| 14 | 16 | Tuz |
| 30 | 24 | Nonce |
| 54 | … | Şifreli metin + 16 bayt etiket |

- **Anahtar** = Argon2id(dışa aktarım parolası, tuz).
- **Şifreli metin** = XChaCha20-Poly1305(anahtar, nonce, JSON), AD = 0–53. baytlar.
- Elle dışa aktarım ile yedek aynı biçimdedir; ikisi de aynı "içe aktar" / "yedekten geri yükle"
  yoluyla, yalnızca parolayla açılır.

### Otomatik yedek ve saklanan anahtar

Yedekleme kurulurken yedek parolasından anahtar bir kez türetilir. Parola saklanmaz; türetilen
anahtar, tuz ve KDF parametreleri kasanın içine (yani DEK ile şifreli olarak) yazılır. Kasa açıkken
yapılan her değişiklikte bu anahtarla, **yeni bir rastgele nonce** kullanılarak yedek dosyası yeniden
üretilir. Aynı anahtarla çok sayıda dosya şifrelemek, 192 bitlik rastgele nonce sayesinde güvenlidir.

Yedek anahtarı dışa aktarılan JSON'a yazılmaz ve içe aktarılan dosyadan alınmaz.

### JSON gövdesi

```json
{
  "format": 1,
  "exportedAt": 1794658053000,
  "vault": {
    "schema": 1,
    "entries": [
      {
        "id": "uuid",
        "title": "", "username": "", "password": "", "url": "", "notes": "",
        "createdAt": 0, "updatedAt": 0,
        "totp": { "secret": "BASE32", "algorithm": "SHA1", "digits": 6, "period": 30 },
        "passkey": {
          "credentialId": "base64url", "rpId": "example.com", "rpName": "",
          "userHandle": "base64url", "userName": "", "userDisplayName": "",
          "privateKey": "PKCS#8 DER (P-256), Base64", "createdAt": 0,
          "prfKey": "32 bayt, Base64 (PRF uzantısı için; eski anahtarlarda null)"
        }
      }
    ]
  }
}
```

Kasa dosyasının gövdesi yalnızca `vault` nesnesidir (ek olarak `backup` alanını içerebilir).
`totp` ve `passkey` yoksa `null`'dır. Bilinmeyen alanlar okurken yok sayılır; yeni alanlar varsayılan değerle
eklenir. Uyumsuz bir değişiklikte başlıktaki biçim sürümü artırılır ve eski sürümler dosyayı reddeder.

### İçe aktarım kuralları

- Kasa yokken: dosya çözülür, kullanıcı bu cihaz için yeni bir ana parola belirler.
- Kasa varken: kayıtlar kimliğe göre birleştirilir. Yeni kimlikler eklenir; aynı kimlikte `updatedAt`
  değeri daha büyük olan kalır; hiçbir kayıt silinmez.
- 64 MiB'tan büyük dosyalar okunmaz.

## Uygulama dışında açma

Dosyalar libsodium'un standart işlevleriyle açılabilir:
`crypto_pwhash(..., crypto_pwhash_ALG_ARGON2ID13)` ile anahtar,
`crypto_aead_xchacha20poly1305_ietf_decrypt` ile düz metin. Birim testleri aynı çıktıları Bouncy
Castle (Argon2id) ve Tink (XChaCha20-Poly1305) ile çapraz doğrular.
