// Eklentinin gerçek kripto kodunu (crypto.js) Node'un WebCrypto'suyla sınar.
import assert from "node:assert/strict";
import { test } from "node:test";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { createSession, domainOf, fromBase64Url, normalizeRelayUrl, openMessage, requestUri } from "../crypto.js";

const fixturePath = fileURLToPath(new URL("../../core/build/send-fixture.json", import.meta.url));

// Telefonun (Kotlin + Tink) ürettiği iletiyi eklenti çözebilmeli. Örnek dosyayı `./gradlew :core:test` üretir.
test("telefonun ürettiği ileti çözülür (Kotlin → JS)", { skip: !existsSync(fixturePath) && "önce ./gradlew :core:test" }, async () => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));
  const session = {
    privateKey: await crypto.subtle.importKey("pkcs8", Buffer.from(fixture.extensionPrivateKeyPkcs8, "base64"), { name: "X25519" }, false, ["deriveBits"]),
    publicKey: fromBase64Url(fixture.extensionPublicKey),
    sessionId: fromBase64Url(fixture.sessionId),
  };
  assert.deepEqual(await openMessage(session, fixture.message, fixture.domain), fixture.expected);

  // Yanlış alan adı, oturum ya da değiştirilmiş ileti reddedilmeli.
  await assert.rejects(openMessage(session, fixture.message, "evil.example"));
  await assert.rejects(openMessage({ ...session, sessionId: new Uint8Array(16) }, fixture.message, fixture.domain));
  const tampered = { ...fixture.message, c: fixture.message.c.slice(0, -2) + (fixture.message.c.endsWith("AA") ? "BB" : "AA") };
  await assert.rejects(openMessage(session, tampered, fixture.domain));
  await assert.rejects(openMessage(session, { v: 2, k: fixture.message.k, c: fixture.message.c }, fixture.domain));
});

test("her istek yeni anahtar ve oturum kimliği üretir; özel anahtar dışa aktarılamaz", async () => {
  const [a, b] = [await createSession(), await createSession()];
  assert.equal(a.publicKey.length, 32);
  assert.equal(a.sessionId.length, 16);
  assert.notDeepEqual(a.publicKey, b.publicKey);
  assert.notDeepEqual(a.sessionId, b.sessionId);
  assert.equal(a.privateKey.extractable, false);
  await assert.rejects(crypto.subtle.exportKey("pkcs8", a.privateKey));
});

test("QR içeriği beklenen alanları taşır", async () => {
  const session = await createSession();
  const uri = new URL(requestUri(session, "https://relay.example.workers.dev", "example.com", 1800000060));
  assert.equal(uri.protocol, "sifrekasa:");
  const q = uri.searchParams;
  assert.deepEqual([q.get("v"), q.get("r"), q.get("d"), q.get("e")], ["1", "https://relay.example.workers.dev", "example.com", "1800000060"]);
  assert.deepEqual(fromBase64Url(q.get("k")), session.publicKey);
  assert.deepEqual(fromBase64Url(q.get("s")), session.sessionId);
});

test("alan adı ve aktarıcı adresi doğrulaması", () => {
  assert.equal(domainOf("https://WWW.Example.com:8443/login?x=1"), "example.com");
  assert.equal(domainOf("chrome://extensions"), null);
  assert.equal(domainOf("file:///etc/passwd"), null);
  assert.equal(normalizeRelayUrl("https://relay.example.workers.dev/"), "https://relay.example.workers.dev");
  assert.equal(normalizeRelayUrl("http://127.0.0.1:8787"), "http://127.0.0.1:8787");
  for (const bad of ["http://relay.example.workers.dev", "https://u:p@relay.example", "https://relay.example/?a=1", "javascript:alert(1)", ""]) {
    assert.equal(normalizeRelayUrl(bad), null, bad);
  }
});
