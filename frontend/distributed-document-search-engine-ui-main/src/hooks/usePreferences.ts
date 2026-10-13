import { useSyncExternalStore } from "react";
import { useAuth } from "../components/common/AuthContext";
import { isThemeName } from "../themes";
import type { ThemeName } from "../themes";

export type Preferences = {
  defaultAiAnalysis: boolean;
  showAiSummaries: boolean;
  density: "comfortable" | "compact";
  theme: ThemeName;
};

export const DEFAULT_PREFERENCES: Preferences = {
  defaultAiAnalysis: false,
  showAiSummaries: true,
  density: "comfortable",
  theme: "graphite",
};

const snapshots = new Map<string, Preferences>();
const listeners = new Set<() => void>();

function accountKey(token: string | null): string {
  try {
    const encoded = token!.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "=")));
    if (typeof payload.sub === "string") return `docnest:preferences:v1:${payload.sub}`;
  } catch { /* Signed-out sessions use defaults. */ }
  return "docnest:preferences:v1:guest";
}

function read(key: string): Preferences {
  const cached = snapshots.get(key);
  if (cached) return cached;
  let result = { ...DEFAULT_PREFERENCES };
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? "null");
    if (saved && typeof saved === "object") result = {
      defaultAiAnalysis: saved.defaultAiAnalysis === true,
      showAiSummaries: saved.showAiSummaries !== false,
      density: saved.density === "compact" ? "compact" : "comfortable",
      theme: isThemeName(saved.theme) ? saved.theme : "graphite",
    };
  } catch { /* Invalid or unavailable storage falls back to defaults. */ }
  snapshots.set(key, result);
  return result;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === null) snapshots.clear();
    else snapshots.delete(event.key);
    listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function usePreferences() {
  const { token } = useAuth();
  const key = accountKey(token);
  const preferences = useSyncExternalStore(subscribe, () => read(key));
  const savePreferences = (next: Preferences) => {
    localStorage.setItem(key, JSON.stringify(next));
    snapshots.set(key, { ...next });
    listeners.forEach((listener) => listener());
  };
  return { preferences, savePreferences };
}
