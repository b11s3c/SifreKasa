// Sayfaya enjekte edilen doldurma işlevi. chrome.scripting.executeScript ile metne çevrilip
// gönderildiği için kendi kendine yeterli olmalı: dışarıdan hiçbir değişkene başvuramaz.

/** @returns {{username: boolean, password: boolean, totp: boolean}} hangi alanların doldurulduğu */
export function fillLogin(username, password, totp) {
  const visible = (el) => {
    if (el.disabled || el.readOnly || el.type === "hidden") return false;
    const style = getComputedStyle(el);
    return style.visibility !== "hidden" && style.display !== "none" && el.getClientRects().length > 0;
  };
  // Çerçeveler (React, Vue…) değeri kendi durumlarında tuttuğu için yerel "value" atayıcısı ve olaylar gerekir.
  const setValue = (el, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
    el.focus();
    setter.call(el, value);
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  };
  const describe = (el) => [el.name, el.id, el.autocomplete, el.placeholder, el.getAttribute("aria-label")].join(" ").toLowerCase();

  const inputs = [...document.querySelectorAll("input")].filter(visible);
  const textual = (el) => ["text", "email", "tel", ""].includes((el.getAttribute("type") || "").toLowerCase());
  const isOtp = (el) => el.autocomplete === "one-time-code" || /otp|totp|2fa|mfa|verification|doğrulama|dogrulama/.test(describe(el));
  const isUserLike = (el) => el.type === "email" || /user|email|e-mail|login|kullan|eposta|e-posta/.test(describe(el));

  const passwordField = inputs.find((el) => el.type === "password");
  const otpField = inputs.find((el) => el !== passwordField && (textual(el) || el.type === "number") && isOtp(el));
  const candidates = inputs.filter((el) => textual(el) && el !== otpField);
  let usernameField = null;
  if (passwordField) {
    // Aynı formda, şifre alanından önce gelen son metin alanı.
    const sameForm = candidates.filter((el) => el.form === passwordField.form);
    const before = (sameForm.length ? sameForm : candidates)
      .filter((el) => passwordField.compareDocumentPosition(el) & Node.DOCUMENT_POSITION_PRECEDING);
    usernameField = before.filter(isUserLike).pop() ?? before.pop() ?? null;
  } else {
    // İki adımlı girişin ilk adımı: yalnızca kullanıcı adı sorulur.
    usernameField = candidates.find(isUserLike) ?? null;
  }

  const result = { username: false, password: false, totp: false };
  if (usernameField && username) { setValue(usernameField, username); result.username = true; }
  if (passwordField && password) { setValue(passwordField, password); result.password = true; }
  if (otpField && totp) { setValue(otpField, totp); result.totp = true; }
  return result;
}
