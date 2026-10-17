const TOKEN_KEY = "authToken";
const USER_KEY = "authUser";

export function tokenExpiresAt(token: string): number {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" ? payload.exp * 1000 : 0;
  } catch { return 0; }
}

export function clearSession() {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem(TOKEN_KEY);
    storage.removeItem(USER_KEY);
    storage.removeItem("token");
  }
}

export function readSession(): string | null {
  try {
    const token = sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY);
    if (token && tokenExpiresAt(token) > Date.now()) return token;
    clearSession();
  } catch { /* Storage may be blocked by browser policy. */ }
  return null;
}

export function saveSession(token: string, email: string, remember: boolean) {
  clearSession();
  const storage = remember ? localStorage : sessionStorage;
  storage.setItem(TOKEN_KEY, token);
  storage.setItem(USER_KEY, email);
}

export function sessionEmail(): string | null {
  try { return sessionStorage.getItem(USER_KEY) ?? localStorage.getItem(USER_KEY); }
  catch { return null; }
}

export function passwordError(password: string): string | null {
  if (password.trim().length === 0 || password.length < 12)
    return "Use at least 12 characters for your password.";
  if (new TextEncoder().encode(password).length > 72)
    return "Your password must not exceed 72 UTF-8 bytes (some characters use more than one byte).";
  return null;
}
