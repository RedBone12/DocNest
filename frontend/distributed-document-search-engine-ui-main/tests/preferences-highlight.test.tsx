import { act, render, renderHook, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { usePreferences, DEFAULT_PREFERENCES } from "../src/hooks/usePreferences";
import { HighlightedText } from "../src/components/documents/HighlightedText";
import { isThemeName, THEMES } from "../src/themes";

const state = vi.hoisted(() => ({ token: null as string | null }));
vi.mock("../src/components/common/AuthContext", () => ({ useAuth: () => ({ token: state.token }) }));
const account = (subject: unknown) => `h.${btoa(JSON.stringify({ sub: subject }))}.s`;
beforeEach(() => { localStorage.clear(); state.token = account(crypto.randomUUID()); window.dispatchEvent(new StorageEvent("storage", { key: null })); });

it("isolates account preferences, persists and updates subscribers", () => {
  const first = renderHook(() => usePreferences());
  expect(first.result.current.preferences).toEqual(DEFAULT_PREFERENCES);
  const second = renderHook(() => usePreferences());
  const next = { defaultAiAnalysis: true, showAiSummaries: false, density: "compact" as const, theme: "graphite" as const };
  act(() => first.result.current.savePreferences(next));
  expect(second.result.current.preferences).toEqual(next);
  state.token = account("other"); second.rerender();
  expect(second.result.current.preferences).toEqual(DEFAULT_PREFERENCES);
  first.unmount(); second.unmount();
});

it.each([null, "bad", account(123)])("uses safe defaults for guest or malformed identity %s", token => {
  state.token = token;
  const hook = renderHook(() => usePreferences());
  expect(hook.result.current.preferences).toEqual(DEFAULT_PREFERENCES);
});

it.each(["{broken", "null", "1", JSON.stringify({ defaultAiAnalysis: true, showAiSummaries: false, density: "compact", theme: "invalid" }), JSON.stringify({ defaultAiAnalysis: false, showAiSummaries: true, density: "other", theme: "graphite" })])("validates stored preferences %s", saved => {
  const sub = crypto.randomUUID(); state.token = account(sub);
  const key = `docnest:preferences:v1:${sub}`; localStorage.setItem(key, saved);
  const hook = renderHook(() => usePreferences());
  expect(hook.result.current.preferences.theme).toBe("graphite");
  act(() => { localStorage.setItem(key, JSON.stringify(DEFAULT_PREFERENCES)); window.dispatchEvent(new StorageEvent("storage", { key })); });
  expect(hook.result.current.preferences).toEqual(DEFAULT_PREFERENCES);
  act(() => window.dispatchEvent(new StorageEvent("storage", { key: null })));
  expect(hook.result.current.preferences).toEqual(DEFAULT_PREFERENCES);
});

it("escapes HTML and highlights only complete marker pairs", () => {
  const { container } = render(<HighlightedText text={'plain <script>bad</script> [[HIGHLIGHT]]match[[/HIGHLIGHT]] [[HIGHLIGHT]]unclosed'} />);
  expect(screen.getByText("match").tagName).toBe("MARK");
  expect(container.querySelector("script")).toBeNull();
  expect(container.textContent).toContain("[[HIGHLIGHT]]unclosed");
  expect(isThemeName("invalid")).toBe(false);
  for (const theme of THEMES) expect(isThemeName(theme.id)).toBe(true);
});
