import type { ReactNode } from "react";
import { FileText } from "lucide-react";
import { Link } from "react-router-dom";

export function AuthShell({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <main className="flex min-h-dvh items-center justify-center bg-linear-to-br from-gray-50 to-slate-100 px-4 py-10">
    <section className="w-full max-w-md rounded-2xl border bg-white p-6 shadow-xs sm:p-8">
      <Link to="/login" className="mb-6 flex items-center justify-center gap-2 text-xl font-semibold" aria-label="DocNest sign in">
        <span className="rounded-xl bg-gray-900 p-2 text-white"><FileText size={24} /></span>DocNest
      </Link>
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="mb-6 mt-2 text-sm text-gray-500">{description}</p>
      {children}
    </section>
  </main>;
}
