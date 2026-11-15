// Açılır pencere: QR gösterir, aktarıcıdan şifreli iletiyi bekler, çözer ve YALNIZCA isteği başlatan
// sekmedeki formu doldurur. Oturum anahtarı bu pencerenin belleğinde yaşar; pencere kapanınca yok olur.
import { createSession, domainOf, normalizeRelayUrl, openMessage, requestUri, toBase64Url } from "./crypto.js";
import { fillLogin } from "./fill.js";

const api = globalThis.browser ?? globalThis.chrome;
const LIFETIME_SECONDS = 60;
const POLL_MS = 1000;
// Süre dolunca QR kendiliğinden yenilenir; pencere açık unutulursa aktarıcıyı sonsuza dek yoklamasın diye sınırlıdır.
const MAX_AUTO_RENEWALS = 10;
let renewals = 0;
const $ = (id) => document.getElementById(id);

function setStatus(text, kind = "") {
  $("status").textContent = text;
  $("status").className = kind;
}

function drawQr(text) {
  const qr = qrcode(0, "M"); // vendor/qrcode.js (qrcode-generator 1.4.4)
  qr.addData(text);
  qr.make();
  const canvas = $("qr");
  const count = qr.getModuleCount();
  const scale = Math.floor(canvas.width / count);
  const margin = Math.floor((canvas.width - scale * count) / 2);
  const context = canvas.getContext("2d");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#000";
  for (let row = 0; row < count; row++) {
    for (let col = 0; col < count; col++) {
      if (qr.isDark(row, col)) context.fillRect(margin + col * scale, margin + row * scale, scale, scale);
    }
  }
  canvas.hidden = false;
}

async function start() {
  for (const id of ["qr", "totpBox", "hint", "retry", "options"]) $(id).hidden = true;

  const [tab] = await api.tabs.query({ active: true, currentWindow: true });
  const domain = tab?.url ? domainOf(tab.url) : null;
  $("domain").textContent = domain ?? "—";
  if (!domain) return setStatus("Bu sayfada kullanılamaz (yalnızca web sayfaları).", "error");

  const relayUrl = normalizeRelayUrl((await api.storage.local.get("relayUrl")).relayUrl ?? "");
  if (!relayUrl) {
    $("options").hidden = false;
    return setStatus("Önce aktarıcı adresini ayarla.", "error");
  }

  let session = await createSession();
  const expiresAt = Math.floor(Date.now() / 1000) + LIFETIME_SECONDS;
  drawQr(requestUri(session, relayUrl, domain, expiresAt));
  $("hint").hidden = false;
  const endpoint = `${relayUrl}/v1/s/${toBase64Url(session.sessionId)}`;

  try {
    let message = null;
    // Telefon son anda gönderirse diye bitişten birkaç saniye sonrasına kadar beklenir.
    while (!message && Date.now() / 1000 < expiresAt + 5) {
      const left = Math.max(0, expiresAt - Math.floor(Date.now() / 1000));
      setStatus(`Telefon bekleniyor… ${left} sn`);
      try {
        const response = await fetch(endpoint, { cache: "no-store", credentials: "omit" });
        if (response.ok) message = await response.json();
      } catch {
        // Ağ hatası: süre dolana dek yeniden denenir.
      }
      if (!message) await new Promise((resolve) => setTimeout(resolve, POLL_MS));
    }
    $("qr").hidden = true;
    $("hint").hidden = true;
    if (!message) {
      if (renewals < MAX_AUTO_RENEWALS) {
        renewals += 1;
        session = null;
        // Eski oturumun anahtarı bırakıldı; yeni anahtar ve yeni oturum kimliğiyle yeni QR.
        setTimeout(start, 0);
        return;
      }
      $("retry").hidden = false;
      return setStatus("Süre doldu.", "error");
    }
    renewals = 0;

    const data = await openMessage(session, message, domain);

    // Sekme bu arada başka bir siteye gitmiş olabilir; öyleyse hiçbir şey doldurulmaz.
    const current = await api.tabs.get(tab.id);
    if (domainOf(current.url ?? "") !== domain) return setStatus("Sekme başka bir siteye geçmiş; doldurulmadı.", "error");

    const [{ result }] = await api.scripting.executeScript({
      target: { tabId: tab.id },
      func: fillLogin,
      args: [data.u ?? "", data.p ?? "", data.t ?? ""],
    });
    if (!result?.username && !result?.password) {
      setStatus("Şifre alındı ama sayfada giriş alanı bulunamadı.", "error");
    } else {
      setStatus("Dolduruldu.", "ok");
    }
    if (data.t && !result?.totp) {
      $("totp").textContent = data.t;
      $("totpBox").hidden = false;
      $("copyTotp").onclick = () => navigator.clipboard.writeText(data.t);
    }
  } catch (error) {
    $("qr").hidden = true;
    $("retry").hidden = false;
    setStatus("İleti çözülemedi ya da doldurulamadı.", "error");
  } finally {
    // Özel anahtar dışa aktarılamaz bir CryptoKey'dir; başvuruyu bırakmak onu erişilmez kılar.
    session = null;
  }
}

$("retry").onclick = () => {
  renewals = 0;
  start();
};
$("options").onclick = () => api.runtime.openOptionsPage();
start();
