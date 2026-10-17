import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthShell } from "../components/common/AuthShell";
import { PasswordField } from "../components/common/PasswordField";
import { registerRequest } from "../api/auth.api";
import { errorMessage } from "../api/errors";
import { passwordError } from "../auth/session";

export function RegisterPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    const invalid = passwordError(password);
    if (invalid || password !== confirm) { setError(invalid ?? "Passwords do not match."); return; }
    setBusy(true); setError("");
    try {
      await registerRequest(email.trim(), password);
      navigate("/login", { replace: true, state: { message: "Account created. Sign in with your email and password." } });
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <AuthShell title="Create your account" description="Keep your documents organized with DocNest.">
    <form onSubmit={submit} className="space-y-4" aria-busy={busy}>
      {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      <div><label htmlFor="register-email" className="mb-1 block text-sm font-medium">Email</label>
        <input id="register-email" type="email" autoComplete="email" required maxLength={320} value={email}
          onChange={(event) => setEmail(event.target.value)} className="w-full rounded-lg border bg-gray-50 px-3 py-2" /></div>
      <PasswordField label="Password" value={password} onChange={setPassword} minLength={12} />
      <p className="text-xs text-gray-500">Use at least 12 characters, up to 72 UTF-8 bytes. A unique passphrase is a good choice.</p>
      <PasswordField label="Confirm password" value={confirm} onChange={setConfirm} minLength={12} />
      <button disabled={busy} className="w-full rounded-lg bg-gray-900 py-2.5 text-white disabled:opacity-50">{busy ? "Creating account..." : "Create account"}</button>
    </form>
    <p className="mt-6 text-center text-sm text-gray-500">Already have an account? <Link className="font-medium text-gray-900 underline" to="/login">Sign in</Link></p>
  </AuthShell>;
}
