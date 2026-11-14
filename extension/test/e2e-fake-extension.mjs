// Uçtan uca elle test: eklentinin yerine geçer. Bir istek üretir, QR içeriğini yazar, aktarıcıdan
// iletiyi bekler ve eklentinin gerçek kripto koduyla çözer.
//   node test/e2e-fake-extension.mjs <telefonun göreceği aktarıcı adresi> <bu makineden aktarıcı adresi> <alan adı> [uri dosyası]
import { writeFileSync } from "node:fs";
import { createSession, openMessage, requestUri, toBase64Url } from "../crypto.js";

const [phoneRelay, localRelay, domain, uriFile] = process.argv.slice(2);
const session = await createSession();
const expiresAt = Math.floor(Date.now() / 1000) + 60;
const uri = requestUri(session, phoneRelay, domain, expiresAt);
if (uriFile) writeFileSync(uriFile, uri);
console.log("URI:", uri);

const endpoint = `${localRelay}/v1/s/${toBase64Url(session.sessionId)}`;
while (Date.now() / 1000 < expiresAt + 5) {
  const response = await fetch(endpoint, { cache: "no-store" });
  if (response.ok) {
    const data = await openMessage(session, await response.json(), domain);
    console.log(`SONUÇ: çözüldü; alan adı=${data.d} kullanıcı=${data.u} şifre uzunluğu=${data.p.length} totp=${data.t ? data.t.length + " hane" : "yok"}`);
    console.log("İKİNCİ OKUMA (404 beklenir):", (await fetch(endpoint)).status);
    process.exit(0);
  }
  await new Promise((r) => setTimeout(r, 1000));
}
console.log("SONUÇ: süre doldu, ileti gelmedi");
process.exit(1);
