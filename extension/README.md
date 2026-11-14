# Şifre Kasa tarayıcı eklentisi (Manifest V3)

Telefondaki Şifre Kasa'dan QR ile, uçtan uca şifreli olarak etkin sekmedeki giriş formuna şifre alır.
Bilgisayarda şifre saklamaz; eklentinin sakladığı tek şey aktarıcının adresidir.

İzinler: `activeTab` (yalnızca simgeye tıkladığın sekme), `scripting` (o sekmedeki formu doldurmak),
`storage` (aktarıcı adresi). Tüm sitelere erişim izni istemez.

## Kurulum

**Chrome / Edge / Brave** (133 ve üstü): `chrome://extensions` → "Geliştirici modu" → "Paketlenmemiş
öğe yükle" → bu klasörü seç.

**Firefox** (130 ve üstü): `about:debugging#/runtime/this-firefox` → "Geçici Eklenti Yükle" →
bu klasördeki `manifest.json`. Geçici eklenti Firefox kapanınca kalkar; kalıcı kurulum için eklentinin
imzalanması gerekir.

Kurduktan sonra eklentinin ayarlarını açıp aktarıcı adresini gir (bkz. `../relay/README.md`).

## Kullanım

1. Giriş sayfasındayken eklenti simgesine tıkla; QR görünür.
2. Telefonda Şifre Kasa → **QR tara**. Telefonda gösterilen alan adının adres çubuğundakiyle aynı
   olduğunu denetle, kaydı seç, onayla.
3. Form dolar. Kayıtta 2FA varsa ve sayfada kod alanı yoksa kod pencerede gösterilir.

Pencere kapanırsa istek iptal olur; yeniden tıklayıp yeni QR oluştur.

## Test

```bash
npm test
```

`vendor/qrcode.js`: qrcode-generator 1.4.4 (MIT, Kazuhiko Arase), npm paketinden değiştirilmeden alınmıştır.
SHA-256: `18ae399f81182bc9de916e9c77b195df20cc58d6f2d55a62b085a299f1bf1780`
