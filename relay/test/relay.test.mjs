// Çalışan bir aktarıcıya karşı protokol testi:  RELAY_URL=http://127.0.0.1:8787 node --test test/
import assert from "node:assert/strict";
import { test } from "node:test";
import { randomBytes } from "node:crypto";

const RELAY = process.env.RELAY_URL;
const sid = () => randomBytes(16).toString("base64url");
const message = (c = "c".repeat(60)) => JSON.stringify({ v: 1, k: "k".repeat(43), c });
const post = (id, body) => fetch(`${RELAY}/v1/s/${id}`, { method: "POST", body, headers: { "Content-Type": "application/json" } });
const get = (id) => fetch(`${RELAY}/v1/s/${id}`);

test("ileti bir kez okunur, sonra silinir", { skip: !RELAY }, async () => {
  const id = sid();
  assert.equal((await get(id)).status, 404);
  assert.equal((await post(id, message())).status, 201);
  const first = await get(id);
  assert.equal(first.status, 200);
  assert.deepEqual(await first.json(), JSON.parse(message()));
  assert.equal(first.headers.get("access-control-allow-origin"), "*");
  assert.equal(first.headers.get("cache-control"), "no-store");
  assert.equal((await get(id)).status, 404);
});

test("oturum tek kullanımlıktır", { skip: !RELAY }, async () => {
  const id = sid();
  assert.equal((await post(id, message())).status, 201);
  assert.equal((await post(id, message())).status, 409, "dolu oturuma ikinci ileti");
  await get(id);
  assert.equal((await post(id, message())).status, 409, "kullanılmış oturuma yeniden yazma");
});

test("geçersiz istekler reddedilir", { skip: !RELAY }, async () => {
  assert.equal((await post(sid(), "değil json")).status, 400);
  assert.equal((await post(sid(), JSON.stringify({ v: 1, k: "kısa", c: "c".repeat(60) }))).status, 400);
  assert.equal((await post(sid(), JSON.stringify({ v: 1, k: "k".repeat(43), c: "c".repeat(60), fazladan: 1 }))).status, 400);
  assert.equal((await post(sid(), message("c".repeat(5000)))).status, 413);
  assert.equal((await post("kisa", message())).status, 404);
  assert.equal((await fetch(`${RELAY}/`)).status, 404);
  assert.equal((await fetch(`${RELAY}/v1/s/${sid()}`, { method: "DELETE" })).status, 405);
  assert.equal((await fetch(`${RELAY}/v1/s/${sid()}`, { method: "OPTIONS" })).status, 204);
});
