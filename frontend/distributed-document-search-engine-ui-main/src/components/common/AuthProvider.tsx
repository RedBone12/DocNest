import { useCallback, useState } from "react";
import type { ReactNode } from "react";
import { loginRequest } from "../../api/auth.api";
import { AuthContext } from "./AuthContext";

const STORAGE_KEY = "authToken";
const LEGACY_STORAGE_KEY = "token";
const USER_STORAGE_KEY = "authUser";

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem(STORAGE_KEY),
  );

  const login = useCallback(async (email: string, password: string) => {
    const nextToken = await loginRequest(email, password);
    localStorage.setItem(STORAGE_KEY, nextToken);
    localStorage.setItem(USER_STORAGE_KEY, email);
    setToken(nextToken);
    return nextToken;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    setToken(null);
  }, []);

  return (
    <AuthContext.Provider value={{ token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
