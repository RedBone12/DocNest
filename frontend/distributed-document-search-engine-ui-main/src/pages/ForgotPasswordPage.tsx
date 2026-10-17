import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AuthShell } from "../components/common/AuthShell";
import { authCapabilities, forgotPasswordRequest } from "../api/auth.api";
import { errorMessage } from "../api/errors";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [available, setAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(false);
  useEffect(() => {
    let cancelled = false;
    void authCapabilities().then((data) => { if (!cancelled) setAvailable(data.passwordResetEnabled); })
      .catch((cause) => { if (!cancelled) setError(errorMessage(cause)); });
    return () => { cancelled = true; };
  }, []);
  useEffect(() => {
    if (!cooldown) return;
    const timer = window.setTimeout(() => setCooldown(false), 60000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try { setMessage(await forgotPasswordRequest(email.trim())); setCooldown(true); }
    catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <AuthShell title="Forgot your password?" description="Enter your account email to request a password reset link.">
    {available === false && <p role="status" className="mb-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">Password recovery is not configured on this server. Please contact the administrator.</p>}
    {available === null && !error && <p role="status" className="mb-4 text-sm text-gray-500">Checking email service...</p>}
    {error && <p role="alert" className="mb-4 text-sm text-red-700">{error} <button className="underline" onClick={() => window.location.reload()}>Try again</button></p>}
    {message && <p role="status" className="mb-4 rounded-lg bg-green-50 p-3 text-sm text-green-800">{message}</p>}
    <form onSubmit={submit} className="space-y-4" aria-busy={busy}>
      <div><label htmlFor="recovery-email" className="mb-1 block text-sm font-medium">Email</label>
        <input id="recovery-email" type="email" autoComplete="email" maxLength={320} required value={email}
          onChange={(event) => setEmail(event.target.value)} className="w-full rounded-lg border bg-gray-50 px-3 py-2" /></div>
      <button disabled={available !== true || busy || cooldown} className="w-full rounded-lg bg-gray-900 py-2.5 text-white disabled:opacity-50">
        {busy ? "Requesting link..." : cooldown ? "Please wait before requesting another link" : "Send reset link"}
      </button>
    </form>
    <p className="mt-4 text-xs text-gray-500">Links expire after 15 minutes and can be used once. For your privacy, we do not disclose whether an account exists.</p>
    <Link className="mt-6 block text-center text-sm underline" to="/login">Back to sign in</Link>
  </AuthShell>;
}
