import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { LockKeyhole } from "lucide-react";
import { useAuth } from "./AuthContext";
import { PasswordField } from "./PasswordField";
import { changePasswordRequest } from "../../api/auth.api";
import { passwordError } from "../../auth/session";
import { errorMessage } from "../../api/errors";

export function AccountSecurity() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    const invalid = passwordError(password);
    if (invalid || password !== confirm) { setError(invalid ?? "Passwords do not match."); return; }
    setBusy(true); setError("");
    try {
      await changePasswordRequest(token, current, password);
      window.dispatchEvent(new Event("docnest:session-expired"));
      navigate("/login", { replace: true, state: { message: "Password changed. All sessions have been signed out. Please sign in again." } });
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }
  return <section className="mx-auto mt-6 max-w-3xl rounded-xl border bg-white p-6">
    <h2 className="flex items-center gap-2 text-lg font-semibold"><LockKeyhole size={20} /> Account Security</h2>
    <p className="mb-5 mt-2 text-sm text-gray-500">Change your password. All existing sessions will be signed out. Your documents will not be changed.</p>
    <form onSubmit={submit} className="max-w-md space-y-4" aria-busy={busy}>
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <PasswordField label="Current password" value={current} onChange={setCurrent} autoComplete="current-password" />
      <PasswordField label="New password" value={password} onChange={setPassword} minLength={12} />
      <p className="text-xs text-gray-500">Use at least 12 characters, up to 72 UTF-8 bytes.</p>
      <PasswordField label="Confirm new password" value={confirm} onChange={setConfirm} minLength={12} />
      <button disabled={busy} className="rounded-lg bg-black px-5 py-2 text-sm font-medium text-white disabled:opacity-50">{busy ? "Updating password..." : "Update password"}</button>
    </form>
  </section>;
}
