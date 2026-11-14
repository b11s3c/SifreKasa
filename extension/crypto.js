// Gönderme protokolünün eklenti tarafı (sürüm 1). Ayrıntı: docs/gonderme-protokolu.md
// Yalnızca tarayıcının WebCrypto'su kullanılır; anahtarlar bellekte kalır, hiçbir yere yazılmaz.

const LABEL = "SifreKasa/send/v1";
const encoder = new TextEncoder();

export const toBase64Url = (bytes) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

export const fromBase64Url = (text) => {
  const binary = atob(text.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
};

const concat = (...parts) => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let offset = 0;
  for (const p of parts) { out.set(p, offset); offset += p.length; }
  return out;
};

/** Her istek için yeni X25519 anahtar çifti ve oturum kimliği. Özel anahtar dışa aktarılamaz. */
export async function createSession() {
  const pair = await crypto.subtle.generateKey({ name: "X25519" }, false, ["deriveBits"]);
  return {
    privateKey: pair.privateKey,
    publicKey: new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey)),
    sessionId: crypto.getRandomValues(new Uint8Array(16)),
  };
}

/** Telefonun okuyacağı QR içeriği. */
export function requestUri(session, relayUrl, domain, expiresAt) {
  const query = new URLSearchParams({
    v: "1",
    r: relayUrl,
    s: toBase64Url(session.sessionId),
    k: toBase64Url(session.publicKey),
    d: domain,
    e: String(expiresAt),
  });
  return `sifrekasa://send?${query}`;
}

/**
 * Aktarıcıdan gelen iletiyi çözer. İleti başka bir alan adı, oturum ya da anahtar için üretilmişse
 * (ya da yolda değiştirilmişse) AES-GCM doğrulaması başarısız olur ve hata fırlatılır.
 * @returns {{d: string, u: string, p: string, t: string|null}}
 */
export async function openMessage(session, message, domain) {
  if (message?.v !== 1 || typeof message.k !== "string" || typeof message.c !== "string") throw new Error("Geçersiz ileti");
  const phonePublic = fromBase64Url(message.k);
  const box = fromBase64Url(message.c);
  if (phonePublic.length !== 32 || box.length < 12 + 16) throw new Error("Geçersiz ileti");

  const peer = await crypto.subtle.importKey("raw", phonePublic, { name: "X25519" }, false, []);
  const shared = await crypto.subtle.deriveBits({ name: "X25519", public: peer }, session.privateKey, 256);
  const hkdf = await crypto.subtle.importKey("raw", shared, "HKDF", false, ["deriveKey"]);
  const key = await crypto.subtle.deriveKey(
    { name: "HKDF", hash: "SHA-256", salt: session.sessionId, info: concat(encoder.encode(LABEL), session.publicKey, phonePublic) },
    hkdf,
    { name: "AES-GCM", length: 256 },
    false,
    ["decrypt"],
  );
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: box.slice(0, 12), additionalData: encoder.encode(`${LABEL}|${domain}`), tagLength: 128 },
    key,
    box.slice(12),
  );
  const data = JSON.parse(new TextDecoder().decode(plaintext));
  if (data.d !== domain) throw new Error("İleti başka bir site için");
  return data;
}

/** Sekme adresinden alan adı: küçük harf, baştaki "www." olmadan; http(s) değilse null. */
export function domainOf(url) {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") return null;
    return parsed.hostname.toLowerCase().replace(/\.$/, "").replace(/^www\./, "") || null;
  } catch {
    return null;
  }
}

/** Aktarıcı adresi https olmalı; geliştirme için yalnızca localhost'ta http'ye izin verilir. */
export function normalizeRelayUrl(text) {
  try {
    const url = new URL(String(text).trim());
    const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
    if (url.protocol !== "https:" && !(local && url.protocol === "http:")) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return (url.origin + url.pathname).replace(/\/+$/, "");
  } catch {
    return null;
  }
}
