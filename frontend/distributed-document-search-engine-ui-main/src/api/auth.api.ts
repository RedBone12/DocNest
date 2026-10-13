import { apiUrl } from "../config";
import { serviceError } from "./errors";

type TokenResponse = {
  token: string;
};

export async function loginRequest(
  email: string,
  password: string,
): Promise<string> {
  const res = await fetch(apiUrl("/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    throw new Error(
      res.status === 401
        ? "Invalid email or password"
        : serviceError(res.status),
    );
  }

  const body = (await res.json()) as TokenResponse;
  if (!body.token) throw new Error("Login response has no token");
  return body.token;
}
