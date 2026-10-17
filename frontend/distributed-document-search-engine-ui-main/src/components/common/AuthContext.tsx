import { createContext, useContext } from "react";

export type AuthContextType = {
  token: string | null;
  login: (email: string, password: string, rememberMe?: boolean) => Promise<string>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextType | null>(null);

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
};
