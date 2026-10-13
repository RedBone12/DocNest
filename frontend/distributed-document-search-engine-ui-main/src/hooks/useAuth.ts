import { useCallback, useEffect, useState } from "react";
import { loginRequest } from "../api/auth.api";

const STORAGE_KEY = "authToken";

export function useAuth() {
  const [token, setToken] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  });

  useEffect(() => {
    try {
      if (token) localStorage.setItem(STORAGE_KEY, token);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }, [token]);

  const login = useCallback(async (email: string, password: string) => {
    const t = await loginRequest(email, password);
    setToken(t);
    return t;
  }, []);

  const logout = useCallback(() => setToken(null), []);

  return { token, login, logout } as const;
}

export default useAuth;
