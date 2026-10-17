import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { loginRequest, logoutRequest, sessionIsValid } from "../../api/auth.api";
import { AuthContext } from "./AuthContext";
import { clearSession, readSession, saveSession, tokenExpiresAt } from "../../auth/session";

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [token, setToken] = useState<string | null>(readSession);

  const login = useCallback(async (email: string, password: string, rememberMe = false) => {
    const nextToken = await loginRequest(email.trim(), password, rememberMe);
    try { saveSession(nextToken, email.trim().toLowerCase(), rememberMe); }
    catch {
      void logoutRequest(nextToken).catch(() => {});
      throw new Error("Browser storage is unavailable. Allow site storage before signing in.");
    }
    setToken(nextToken);
    return nextToken;
  }, []);

  const expire = useCallback(() => {
    try { clearSession(); } catch { /* Still clear the in-memory session. */ }
    setToken(null);
  }, []);

  const logout = useCallback(async () => {
    const previous = token;
    expire();
    if (previous) {
      try { await logoutRequest(previous); }
      catch { throw new Error("Signed out on this browser, but the server could not revoke the session. It will remain valid until expiry."); }
    }
  }, [token, expire]);

  useEffect(() => {
    const onExpired = () => expire();
    const onStorage = (event: StorageEvent) => {
      if (event.key === "authToken" || event.key === null) setToken(readSession());
    };
    window.addEventListener("docnest:session-expired", onExpired);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener("docnest:session-expired", onExpired);
      window.removeEventListener("storage", onStorage);
    };
  }, [expire]);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    const verify = async () => {
      if (tokenExpiresAt(token) <= Date.now()) { expire(); return; }
      try { if (!(await sessionIsValid(token)) && !cancelled) expire(); }
      catch { /* A network outage must not silently log the user out. */ }
    };
    void verify();
    const timer = window.setInterval(() => { void verify(); }, 30000);
    const visibility = () => { if (document.visibilityState === "visible") void verify(); };
    document.addEventListener("visibilitychange", visibility);
    return () => { cancelled = true; window.clearInterval(timer); document.removeEventListener("visibilitychange", visibility); };
  }, [token, expire]);

  return (
    <AuthContext.Provider value={{ token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
