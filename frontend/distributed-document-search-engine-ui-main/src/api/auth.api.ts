import { apiUrl } from "../config";
import { serviceError } from "./errors";

type TokenResponse = {
  token: string;
};

export async function loginRequest(
  email: string,
  password: string,
  rememberMe = false,
): Promise<string> {
  const res = await fetch(apiUrl("/auth/login"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, rememberMe }),
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

async function accountRequest(path: string, body?: unknown, token?: string, method = "POST") {
  const response = await fetch(apiUrl(`/auth/${path}`), {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(10000),
  });
  if (!response.ok) {
    if (response.status === 429) throw new Error("Too many attempts. Please wait 15 minutes before trying again.");
    if (path === "register" && response.status === 409) throw new Error("This email is already registered. Sign in or reset your password.");
    if (path === "forgot-password" && response.status === 503)
      throw new Error("Password recovery is unavailable. Please contact the administrator or try again later.");
    if (path === "reset-password" && response.status === 400)
      throw new Error("This reset link is invalid or expired, or the password does not meet the requirements. Request a new link if needed.");
    if (path === "change-password" && response.status === 400)
      throw new Error("Check your current password and the new password requirements.");
    throw new Error(serviceError(response.status));
  }
  return response;
}

export async function registerRequest(email: string, password: string) {
  await accountRequest("register", { email, password });
}
export async function forgotPasswordRequest(email: string): Promise<string> {
  const response = await accountRequest("forgot-password", { email });
  return ((await response.json()) as { message: string }).message;
}
export async function resetPasswordRequest(token: string, password: string) {
  await accountRequest("reset-password", { token, password });
}
export async function changePasswordRequest(token: string, currentPassword: string, newPassword: string) {
  await accountRequest("change-password", { currentPassword, newPassword }, token);
}
export async function logoutRequest(token: string) { await accountRequest("logout", undefined, token); }
export async function sessionIsValid(token: string): Promise<boolean> {
  const response = await fetch(apiUrl("/auth/session"), {
    headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(10000),
  });
  if (response.status === 401 || response.status === 403) return false;
  if (!response.ok) throw new Error(serviceError(response.status));
  return true;
}
export async function authCapabilities(): Promise<{ passwordResetEnabled: boolean }> {
  const response = await accountRequest("capabilities", undefined, undefined, "GET");
  return response.json();
}
