const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const ts = require("typescript");

function load(file, globals = {}, imports = {}) {
  const exports = {};
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src", file), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  vm.runInNewContext(code, {
    exports, atob, Date, TextEncoder, AbortSignal, ...globals,
    require: (name) => { if (!(name in imports)) throw new Error("Unexpected import: " + name); return imports[name]; },
  });
  return exports;
}
function storage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key), values };
}
function jwt(exp) { return "header." + Buffer.from(JSON.stringify({ sub: "user-id", exp })).toString("base64url") + ".signature"; }

test("Remember me selects persistent or tab storage without ever storing passwords", () => {
  const localStorage = storage(), sessionStorage = storage();
  const session = load("auth/session.ts", { localStorage, sessionStorage });
  const token = jwt(Math.floor(Date.now() / 1000) + 3600);
  session.saveSession(token, "member@example.test", false);
  assert.equal(localStorage.getItem("authToken"), null);
  assert.equal(sessionStorage.getItem("authToken"), token);
  assert.equal(session.readSession(), token);
  assert.equal(session.sessionEmail(), "member@example.test");
  session.saveSession(token, "member@example.test", true);
  assert.equal(localStorage.getItem("authToken"), token);
  assert.equal(sessionStorage.getItem("authToken"), null);
  assert.equal(localStorage.values.has("password"), false);
  session.clearSession();
  assert.equal(localStorage.values.size, 0);
  assert.equal(sessionStorage.values.size, 0);
});

test("expired and malformed tokens are removed instead of entering protected routes", () => {
  for (const token of [jwt(Math.floor(Date.now() / 1000) - 1), "broken-token"]) {
    const localStorage = storage(), sessionStorage = storage();
    localStorage.setItem("authToken", token);
    localStorage.setItem("authUser", "member@example.test");
    const session = load("auth/session.ts", { localStorage, sessionStorage });
    assert.equal(session.readSession(), null);
    assert.equal(localStorage.getItem("authUser"), null);
  }
});

test("new password rules include BCrypt's UTF-8 byte limit", () => {
  const session = load("auth/session.ts");
  assert.match(session.passwordError("short"), /12 characters/);
  assert.match(session.passwordError("界".repeat(25)), /72 UTF-8 bytes/);
  assert.equal(session.passwordError("a".repeat(72)), null);
  assert.equal(session.passwordError("A long unique passphrase"), null);
});

function authApi(fetch) {
  return load("api/auth.api.ts", { fetch }, {
    "../config": { apiUrl: (route) => "http://localhost:8080" + route },
    "./errors": { serviceError: (status) => `HTTP ${status}` },
  });
}

test("login sends Remember me choice to the server", async () => {
  const api = authApi(async (url, options) => {
    assert.equal(url, "http://localhost:8080/auth/login");
    assert.equal(JSON.parse(options.body).rememberMe, true);
    return { ok: true, json: async () => ({ token: "signed-token" }) };
  });
  assert.equal(await api.loginRequest("member@example.test", "long-test-passphrase", true), "signed-token");
});

test("recovery exposes unavailable mail and duplicate registration errors without claiming success", async () => {
  const unavailable = authApi(async () => ({ ok: false, status: 503 }));
  await assert.rejects(unavailable.forgotPasswordRequest("member@example.test"), /recovery is unavailable/);
  const duplicate = authApi(async () => ({ ok: false, status: 409 }));
  await assert.rejects(duplicate.registerRequest("member@example.test", "long-test-passphrase"), /already registered/);
});

test("session check distinguishes revocation from an outage", async () => {
  assert.equal(await authApi(async () => ({ ok: false, status: 401 })).sessionIsValid("token"), false);
  await assert.rejects(authApi(async () => ({ ok: false, status: 503 })).sessionIsValid("token"), /503/);
});

test("password reset and change send secrets in the body and logout authenticates the session", async () => {
  const calls = [];
  const api = authApi(async (url, options) => { calls.push({ url, options }); return { ok: true, status: 204 }; });
  await api.resetPasswordRequest("one-use-secret", "long-new-passphrase");
  assert.equal(calls[0].url.includes("one-use-secret"), false);
  assert.equal(JSON.parse(calls[0].options.body).token, "one-use-secret");
  await api.changePasswordRequest("session-token", "old-passphrase", "new-passphrase");
  assert.equal(calls[1].options.headers.Authorization, "Bearer session-token");
  await api.logoutRequest("session-token");
  assert.equal(calls[2].options.headers.Authorization, "Bearer session-token");
});
