# ŞifreKasa aktarıcısı

Telefon ile tarayıcı eklentisi arasında tek bir şifreli iletiyi taşıyan küçük bir Cloudflare Worker.
Yalnızca şifreli veri görür; oturum tek kullanımlıktır ve 60 saniyede silinir. Protokol:
[../docs/gonderme-protokolu.md](../docs/gonderme-protokolu.md).

## Kendi hesabına kurma

Bir Cloudflare hesabı gerekir (ücretsiz plan yeterlidir; Durable Objects SQLite arka ucuyla ücretsiz
planda kullanılabilir).

```bash
npm install
```

```bash
npx wrangler login
```

```bash
npx wrangler deploy
```

Komut sonunda `https://sifrekasa-relay.<hesap-adın>.workers.dev` biçiminde bir adres yazdırır.
Bu adresi tarayıcı eklentisinin ayarlarına gir. Telefon, adresi ilk QR okutmada sorup kaydeder.

## Yerelde çalıştırma ve test

```bash
npm run dev
```

```bash
RELAY_URL=http://127.0.0.1:8787 npm test
```
