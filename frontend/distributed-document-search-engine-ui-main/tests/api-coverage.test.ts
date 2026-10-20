import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as auth from "../src/api/auth.api";
import * as documents from "../src/api/documents.api";
import { searchDocuments } from "../src/api/search.api";
import { apiFetch } from "../src/api/client";
import { apiUrl } from "../src/config";
import { serviceError, errorMessage } from "../src/api/errors";
import * as session from "../src/auth/session";

const fetchMock = vi.fn();
const respond = (status = 200, body: unknown = {}) => {
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
};
beforeEach(() => { localStorage.clear(); sessionStorage.clear(); vi.stubGlobal("fetch", fetchMock); fetchMock.mockReset(); });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe("authentication transport", () => {
  it("sends login choices and rejects missing tokens", async () => {
    respond(200, { token: "signed" });
    expect(await auth.loginRequest("demo@example.com", "password", true)).toBe("signed");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ rememberMe: true });
    respond(); await expect(auth.loginRequest("a", "b")).rejects.toThrow("no token");
    respond(401); await expect(auth.loginRequest("a", "b")).rejects.toThrow("Invalid email");
    respond(503); await expect(auth.loginRequest("a", "b")).rejects.toThrow("unavailable");
  });
  it("supports all account operations", async () => {
    respond(); await auth.registerRequest("a", "b");
    respond(200, { message: "sent" }); expect(await auth.forgotPasswordRequest("a")).toBe("sent");
    respond(); await auth.resetPasswordRequest("reset", "new");
    respond(); await auth.changePasswordRequest("token", "old", "new");
    respond(); await auth.logoutRequest("token");
    expect(fetchMock.mock.calls[4][1]).toMatchObject({ headers: { Authorization: "Bearer token" }, body: undefined });
    respond(200, { passwordResetEnabled: false }); expect(await auth.authCapabilities()).toEqual({ passwordResetEnabled: false });
  });
  it.each([
    [429, () => auth.registerRequest("a", "b"), "Too many"],
    [409, () => auth.registerRequest("a", "b"), "already registered"],
    [503, () => auth.forgotPasswordRequest("a"), "recovery is unavailable"],
    [400, () => auth.resetPasswordRequest("a", "b"), "invalid or expired"],
    [400, () => auth.changePasswordRequest("a", "b", "c"), "current password"],
    [500, () => auth.logoutRequest("a"), "unavailable"],
  ] as const)("handles account status %s", async (status, call, message) => {
    respond(status); await expect(call()).rejects.toThrow(message);
  });
  it.each([200, 401, 403, 503])("checks session status %s", async (status) => {
    respond(status);
    if (status === 503) await expect(auth.sessionIsValid("t")).rejects.toThrow("unavailable");
    else expect(await auth.sessionIsValid("t")).toBe(status === 200);
  });
});

describe("document transport", () => {
  it("normalizes library variants and forwards pagination", async () => {
    respond(200, { content: [{ id: "a", fileSize: 12 }, { documentId: "b", size: 3 }, {}], totalElements: 3 });
    const page = await documents.getDocuments("t");
    expect(page.content.map(d => [d.id, d.size])).toEqual([["a", 12], ["b", 3], ["", undefined]]);
    expect(fetchMock.mock.calls[0][0]).toContain("page=0&size=10");
    respond(500); await expect(documents.getDocuments("t", 1, 2)).rejects.toThrow();
  });
  it("handles status, download, analysis and deletion", async () => {
    respond(200, { status: "READY" }); expect(await documents.getDocumentStatus("a/b", "t")).toMatchObject({ status: "READY" });
    expect(fetchMock.mock.calls[0][0]).toContain("a%2Fb");
    respond(200, "download"); expect((await documents.downloadDocument("a", "t")).size).toBeGreaterThan(0);
    respond(); await documents.requestAiAnalysis("a", "t");
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); await documents.deleteDocument("a", "t");
    respond(); await documents.deleteDocument("a", "t");
  });
  it.each([documents.getDocumentStatus, documents.downloadDocument, documents.requestAiAnalysis, documents.deleteDocument])("propagates document failures", async call => {
    respond(403); await expect(call("a", "t")).rejects.toThrow("permission");
  });
  it("normalizes search results, drops missing IDs and applies filters", async () => {
    respond(200, { total: 3, results: [{ id: "a", fileSize: 7, status: "READY", contentHighlights: ["hit"], aiTags: ["tag"] }, { documentId: "b" }, {}] });
    const result = await searchDocuments("q", "t", { tags: " tag ", fromDate: "2026-01-01", toDate: "2026-12-31" }, 1, 2);
    expect(result.documents.map(d => d.id)).toEqual(["a", "b"]);
    expect(result.documents[1]).toMatchObject({ status: "UPLOADED", contentHighlights: [], aiTags: [] });
    expect(fetchMock.mock.calls[0][0]).toContain("tags=tag");
    respond(200, { total: 0, results: [] }); expect(await searchDocuments("", "t")).toEqual({ total: 0, documents: [] });
    respond(500); await expect(searchDocuments("q", "t", { tags: " " })).rejects.toThrow();
  });
});

describe("sessions and error messages", () => {
  const token = (payload: unknown) => `header.${btoa(JSON.stringify(payload))}.signature`;
  it("switches persistence and cleans stale sessions", () => {
    const valid = token({ exp: Date.now() / 1000 + 100 });
    session.saveSession(valid, "demo@example.com", false); expect(session.readSession()).toBe(valid); expect(session.sessionEmail()).toBe("demo@example.com");
    session.saveSession(valid, "remember@example.com", true); expect(session.readSession()).toBe(valid); expect(session.sessionEmail()).toBe("remember@example.com");
    sessionStorage.setItem("authToken", token({ exp: 1 })); expect(session.readSession()).toBeNull(); expect(session.sessionEmail()).toBeNull();
    expect(session.tokenExpiresAt("bad")).toBe(0); expect(session.tokenExpiresAt(token({ exp: "bad" }))).toBe(0);
    expect(session.readSession()).toBeNull();
  });
  it("handles blocked storage and password byte limits", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
    expect(session.readSession()).toBeNull(); expect(session.sessionEmail()).toBeNull();
    expect(session.passwordError(" ")).toContain("12"); expect(session.passwordError("short")).toContain("12");
    expect(session.passwordError("界".repeat(25))).toContain("72"); expect(session.passwordError("valid-password-123")).toBeNull();
  });
  it.each([0, 400, 401, 403, 413, 429, 500])("maps service status %s", status => { expect(serviceError(status)).not.toBe(""); });
  it("handles arbitrary errors and URL slashes", () => {
    expect(errorMessage(new Error("specific"))).toBe("specific"); expect(errorMessage(new TypeError("network"))).toContain("unavailable"); expect(errorMessage(null)).toContain("unavailable");
    expect(apiUrl("documents")).toBe(apiUrl("/documents"));
  });
  it("supports generic JSON/text requests and error fallbacks", async () => {
    session.saveSession(token({ exp: Date.now() / 1000 + 100 }), "a", false);
    respond(200, { ok: true }); expect(await apiFetch("/api", { headers: { Accept: "application/json" } })).toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toContain("Bearer");
    session.clearSession(); fetchMock.mockResolvedValueOnce(new Response("text")); expect(await apiFetch("/api")).toBe("text");
    for (const [body, statusText, message] of [["reason", "Bad", "reason"], ["", "Bad", "Bad"], ["", "", "API error"]]) {
      fetchMock.mockResolvedValueOnce(new Response(body, { status: 400, statusText })); await expect(apiFetch("/api")).rejects.toThrow(message);
    }
  });
});
