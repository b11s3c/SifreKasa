import { normalizeRelayUrl } from "./crypto.js";

const api = globalThis.browser ?? globalThis.chrome;
const input = document.getElementById("relay");
const result = document.getElementById("result");

api.storage.local.get("relayUrl").then(({ relayUrl }) => { input.value = relayUrl ?? ""; });

document.getElementById("save").onclick = async () => {
  const url = normalizeRelayUrl(input.value);
  if (!url) {
    result.textContent = "Geçerli bir https adresi gir.";
    result.className = "error";
    return;
  }
  await api.storage.local.set({ relayUrl: url });
  input.value = url;
  result.textContent = "Kaydedildi.";
  result.className = "ok";
};
