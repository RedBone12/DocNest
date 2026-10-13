import { useState } from "react";
import { Settings, Sparkles, LayoutList, RotateCcw, Palette, Check } from "lucide-react";
import { DEFAULT_PREFERENCES, usePreferences } from "../hooks/usePreferences";
import type { Preferences } from "../hooks/usePreferences";
import { THEMES } from "../themes";

export const SettingsPage = () => {
  const { preferences, savePreferences } = usePreferences();
  return <PreferencesEditor preferences={preferences} onSave={savePreferences} />;
};

function PreferencesEditor({ preferences, onSave }: {
  preferences: Preferences;
  onSave: (next: Preferences) => void;
}) {
  const [draft, setDraft] = useState<Preferences | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const current = draft ?? preferences;
  const changed = JSON.stringify(current) !== JSON.stringify(preferences);

  function update(patch: Partial<Preferences>) {
    setDraft({ ...current, ...patch });
    setError("");
    setMessage("");
  }

  function save(next: Preferences) {
    try {
      onSave(next);
      setDraft(null);
      setError("");
      setMessage("Your preferences have been saved.");
    } catch {
      setError("Could not save your preferences. Please allow browser storage and try again.");
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <div className="flex items-center gap-3">
          <Settings className="h-6 w-6 text-gray-700" />
          <h1 className="text-2xl font-semibold text-gray-900">Settings</h1>
        </div>
        <p className="mt-2 text-sm text-gray-500">Make DocNest work the way you prefer.</p>
        <p className="mt-1 text-xs text-gray-500">Preferences are saved for your account in this browser.</p>
      </div>

      <form onSubmit={(event) => { event.preventDefault(); save(current); }} className="space-y-6">
        <section className="rounded-xl border bg-white p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold"><Palette size={20} /> Appearance</h2>
          <fieldset className="mt-5">
            <legend className="text-sm font-medium">Color theme</legend>
            <p className="mt-1 text-sm text-gray-500">Choose a palette, then save your changes to apply it across DocNest.</p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {THEMES.map((theme) => (
                <label key={theme.id} className="theme-choice relative cursor-pointer rounded-xl border-2 p-4 transition-shadow hover:shadow-sm" style={{ borderColor: current.theme === theme.id ? theme.colors[0] : "var(--theme-border)" }}>
                  <input type="radio" name="color-theme" value={theme.id} checked={current.theme === theme.id} onChange={() => update({ theme: theme.id })} className="peer sr-only" />
                  <span className="absolute inset-0 rounded-xl peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2" style={{ outlineColor: theme.colors[0] }} />
                  <span className="flex items-center justify-between">
                    <span className="flex gap-1.5" aria-hidden="true">
                      {theme.colors.map((color) => <span key={color} className="h-6 w-6 rounded-full ring-1 ring-black/10" style={{ backgroundColor: color }} />)}
                    </span>
                    {current.theme === theme.id && <Check size={18} style={{ color: theme.colors[0] }} aria-hidden="true" />}
                  </span>
                  <span className="mt-3 block text-sm font-semibold">{theme.name}</span>
                  <span className="mt-1 block text-xs text-gray-500">{theme.description}</span>
                </label>
              ))}
            </div>
          </fieldset>
        </section>

        <section className="rounded-xl border bg-white p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold"><Sparkles size={20} /> AI Preferences</h2>
          <label className="mt-5 flex cursor-pointer items-start justify-between gap-6">
            <span>
              <span className="block text-sm font-medium">Enable AI analysis by default</span>
              <span id="ai-default-description" className="mt-1 block text-sm text-gray-500">Preselect AI analysis for new uploads. You can turn it off for each upload.</span>
            </span>
            <input type="checkbox" role="switch" aria-describedby="ai-default-description ai-disclosure" checked={current.defaultAiAnalysis} onChange={(event) => update({ defaultAiAnalysis: event.target.checked })} className="mt-1 h-5 w-5 shrink-0 accent-black" />
          </label>
          <p id="ai-disclosure" className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-800">Enabling this preference allows new uploads to send their title and up to 12,000 characters of extracted text to OpenAI for analysis. Review the upload option before sending sensitive documents.</p>
          <label className="mt-5 flex cursor-pointer items-start justify-between gap-6 border-t pt-5">
            <span>
              <span className="block text-sm font-medium">Show AI summaries</span>
              <span className="mt-1 block text-sm text-gray-500">Display generated summaries and tags in your document list. Hiding them does not delete them or stop analysis.</span>
            </span>
            <input type="checkbox" role="switch" checked={current.showAiSummaries} onChange={(event) => update({ showAiSummaries: event.target.checked })} className="mt-1 h-5 w-5 shrink-0 accent-black" />
          </label>
        </section>

        <section className="rounded-xl border bg-white p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold"><LayoutList size={20} /> Document Display</h2>
          <label htmlFor="document-density" className="mt-5 block text-sm font-medium">List density</label>
          <p id="density-description" className="mt-1 text-sm text-gray-500">Choose how much space appears between documents.</p>
          <select id="document-density" aria-describedby="density-description" value={current.density} onChange={(event) => update({ density: event.target.value as Preferences["density"] })} className="mt-3 w-full rounded-lg border bg-white px-3 py-2 sm:w-64">
            <option value="comfortable">Comfortable</option>
            <option value="compact">Compact</option>
          </select>
        </section>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <button type="button" onClick={() => { if (window.confirm("Restore default preferences? Your documents will not be changed.")) save({ ...DEFAULT_PREFERENCES }); }} className="flex items-center gap-2 rounded-lg border bg-white px-4 py-2 text-sm hover:bg-gray-50"><RotateCcw size={16} /> Restore defaults</button>
          <button type="submit" disabled={!changed} className="rounded-lg bg-black px-5 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-40">Save changes</button>
        </div>
        <p role="status" className="text-sm text-green-700">{message}</p>
        {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
      </form>
    </div>
  );
}
