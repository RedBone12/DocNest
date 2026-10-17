import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthShell } from "../components/common/AuthShell";
import { PasswordField } from "../components/common/PasswordField";
import { resetPasswordRequest } from "../api/auth.api";
import { errorMessage } from "../api/errors";
import { clearSession, passwordError } from "../auth/session";

export function ResetPasswordPage() {
  const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get("token") ?? "");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  useEffect(() => {
    // Remove the secret from browser history; retain it only in component memory.
    window.history.replaceState(window.history.state, "", window.location.pathname);
  }, []);
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const invalid = passwordError(password);
    if (invalid || password !== confirm) { setError(invalid ?? "Passwords do not match."); return; }
    setBusy(true); setError("");
    try {
      await resetPasswordRequest(token, password);
      try { clearSession(); } catch { /* Server has already revoked old sessions. */ }
      window.dispatchEvent(new Event("docnest:session-expired"));
      navigate("/login", { replace: true, state: { message: "Password reset. Sign in with your new password. All previous sessions have been signed out." } });
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <AuthShell title="Choose a new password" description="Your new password will replace the old one and sign out previous sessions.">
    {!token ? <p role="alert" className="mb-4 text-sm text-red-700">This reset link is missing its token. Please request a new link.</p> :
      <form onSubmit={submit} className="space-y-4" aria-busy={busy}>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        <PasswordField label="New password" value={password} onChange={setPassword} minLength={12} />
        <p className="text-xs text-gray-500">At least 12 characters, up to 72 UTF-8 bytes.</p>
        <PasswordField label="Confirm new password" value={confirm} onChange={setConfirm} minLength={12} />
        <button disabled={busy} className="w-full rounded-lg bg-gray-900 py-2.5 text-white disabled:opacity-50">{busy ? "Resetting password..." : "Reset password"}</button>
      </form>}
    <Link className="mt-6 block text-center text-sm underline" to="/forgot-password">Request a new link</Link>
    <Link className="mt-3 block text-center text-sm underline" to="/login">Back to sign in</Link>
  </AuthShell>;
}
