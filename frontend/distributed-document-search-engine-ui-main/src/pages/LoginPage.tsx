import { useState } from "react";
import { FileText } from "lucide-react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../components/common/AuthContext";
import { errorMessage } from "../api/errors";
import { PasswordField } from "../components/common/PasswordField";

export const LoginPage = () => {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const location = useLocation();
  const notice = (location.state as { message?: string } | null)?.message;

  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      await login(email, password, rememberMe);
      navigate("/documents");
    } catch (err: unknown) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 px-4 py-10">
      <div className="w-full max-w-md bg-white rounded-2xl shadow-lg p-8">
        <div className="flex justify-center mb-4">
          <div className="bg-black text-white p-3 rounded-xl">
            <FileText size={24} />
          </div>
        </div>

        <h1 className="text-2xl font-semibold text-center">DocNest</h1>
        <p className="text-sm text-gray-500 text-center mt-1 mb-6">
          Sign in to access your documents
        </p>

        {notice && <p role="status" className="mb-4 rounded-lg bg-green-50 p-3 text-sm text-green-800">{notice}</p>}
        <form onSubmit={handleSubmit} className="space-y-4" aria-busy={loading}>
          {error && (
            <div role="alert" className="bg-red-50 text-red-600 text-sm p-3 rounded-lg">
              {error}
            </div>
          )}

          <div>
            <label htmlFor="login-email" className="text-sm font-medium block mb-1">Email</label>
            <input
              type="email"
              id="login-email"
              autoComplete="username"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              maxLength={320}
              className="w-full bg-gray-100 rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-black"
            />
          </div>

          <PasswordField label="Password" value={password} onChange={setPassword} autoComplete="current-password" />
          <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
            <label className="flex cursor-pointer items-center gap-2"><input type="checkbox" checked={rememberMe} onChange={(event) => setRememberMe(event.target.checked)} className="h-4 w-4 accent-gray-900" />Remember me</label>
            <Link to="/forgot-password" className="underline">Forgot password?</Link>
          </div>
          <p className="text-xs text-gray-500">Keep me signed in for up to 30 days on this device. Leave unchecked on shared devices.</p>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-2 bg-black text-white py-2.5 rounded-lg font-medium hover:opacity-90 transition disabled:opacity-60"
          >
            {loading ? "Signing in..." : "Sign In"}
          </button>
        </form>
        <p className="mt-6 text-center text-sm text-gray-500">New to DocNest? <Link to="/register" className="font-medium text-gray-900 underline">Create an account</Link></p>
      </div>
    </main>
  );
};
