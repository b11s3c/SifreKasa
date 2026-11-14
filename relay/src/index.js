// Şifre Kasa aktarıcısı (Cloudflare Worker + Durable Object).
//
// Telefon ile tarayıcı eklentisi arasında tek bir şifreli iletiyi taşır. Aktarıcı anahtarları hiç görmez:
// eklentinin açık anahtarı telefona QR ile, aktarıcının dışından ulaşır. Burada saklanan tek şey,
// en çok 60 saniye yaşayan ve bir kez okunabilen şifreli iletidir. Hiçbir şey loglanmaz.
//
//   POST /v1/s/<oturum>   telefon şifreli iletiyi bırakır   201 | 409 (oturum dolu ya da kullanılmış)
//   GET  /v1/s/<oturum>   eklenti iletiyi alır ve siler     200 | 404 (henüz yok, kullanılmış ya da süresi dolmuş)
import { DurableObject } from "cloudflare:workers";

const TTL_MS = 60_000;
const MAX_BODY_BYTES = 4096;
const PATH = /^\/v1\/s\/([A-Za-z0-9_-]{22})$/; // 16 baytlık oturum kimliği, Base64url

const HEADERS = {
  "Access-Control-Allow-Origin": "*", // eklenti sayfaları ve telefon farklı kökenlerden gelir; ileti zaten şifrelidir
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Cache-Control": "no-store",
  "Content-Type": "application/json",
};

const reply = (status, body) => new Response(body === null ? null : JSON.stringify(body), { status, headers: HEADERS });

/** İletinin biçimini denetler; içeriğini çözemez. */
function isValidMessage(text) {
  try {
    const m = JSON.parse(text);
    return m !== null && typeof m === "object" && m.v === 1 &&
      typeof m.k === "string" && /^[A-Za-z0-9_-]{43}$/.test(m.k) &&
      typeof m.c === "string" && /^[A-Za-z0-9_-]{38,}$/.test(m.c) &&
      Object.keys(m).length === 3;
  } catch {
    return false;
  }
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return reply(204, null);
    const match = PATH.exec(new URL(request.url).pathname);
    if (!match) return reply(404, { error: "not_found" });
    const session = env.SESSIONS.get(env.SESSIONS.idFromName(match[1]));

    if (request.method === "GET") {
      const message = await session.take();
      return message === null ? reply(404, { error: "empty" }) : new Response(message, { status: 200, headers: HEADERS });
    }
    if (request.method === "POST") {
      if (Number(request.headers.get("Content-Length") ?? 0) > MAX_BODY_BYTES) return reply(413, { error: "too_large" });
      const text = await request.text();
      if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) return reply(413, { error: "too_large" });
      if (!isValidMessage(text)) return reply(400, { error: "invalid" });
      return (await session.put(text)) ? reply(201, { ok: true }) : reply(409, { error: "used" });
    }
    return reply(405, { error: "method" });
  },
};

/** Tek bir oturum: en çok bir ileti alır, en çok bir kez verir, 60 saniye sonra kendini siler. */
export class Session extends DurableObject {
  async put(message) {
    const storage = this.ctx.storage;
    if ((await storage.get("state")) !== undefined) return false;
    const expires = Date.now() + TTL_MS;
    await storage.put({ state: "full", message, expires });
    await storage.setAlarm(expires);
    return true;
  }

  async take() {
    const storage = this.ctx.storage;
    if ((await storage.get("state")) !== "full") return null;
    const [message, expires] = [await storage.get("message"), await storage.get("expires")];
    // İleti hemen silinir; "kullanıldı" işareti süre dolana dek kalır ki aynı oturuma yeniden yazılamasın.
    await storage.delete("message");
    await storage.put("state", "used");
    return Date.now() > expires ? null : message;
  }

  async alarm() {
    await this.ctx.storage.deleteAll();
  }
}
