const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const ts = require("typescript");
const path = require("node:path");

function loadApi(name, extra) {
  const exports = {};
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, "../src/api", name + ".ts"), "utf8"),
    { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  vm.runInNewContext(code, {
    exports, URL, FormData, ...extra,
    require: (module) => module === "../config"
      ? { apiUrl: (route) => "http://localhost:8080" + route }
      : loadApi("errors", {}),
  });
  return exports;
}

test("search forwards pagination, filters and abort signal, preserving totals", async () => {
  const signal = new AbortController().signal;
  const api = loadApi("search.api", { fetch: async (url, options) => {
    const parsed = new URL(url);
    assert.equal(parsed.searchParams.get("page"), "2");
    assert.equal(parsed.searchParams.get("size"), "10");
    assert.equal(parsed.searchParams.get("tags"), "Java");
    assert.equal(parsed.searchParams.get("q"), "hello");
    assert.equal(options.signal, signal);
    assert.equal(options.headers.Authorization, "Bearer test-token");
    return { ok: true, json: async () => ({ total: 24, results: [{ documentId: "doc-1", title: "Title", status: "READY" }] }) };
  } });
  const result = await api.searchDocuments("hello", "test-token", { tags: " Java " }, 2, 10, signal);
  assert.equal(result.total, 24);
  assert.equal(result.documents[0].id, "doc-1");
});

test("an empty search preserves zero total and no results", async () => {
  const api = loadApi("search.api", { fetch: async () => ({ ok: true, json: async () => ({ total: 0, results: [] }) }) });
  const result = await api.searchDocuments("missing", "test-token");
  assert.equal(result.total, 0);
  assert.equal(result.documents.length, 0);
});

test("service failures and expired sessions have actionable messages", async () => {
  for (const [status, expected] of [[503, /service is currently unavailable/], [401, /session has expired/]]) {
    const api = loadApi("search.api", { fetch: async () => ({ ok: false, status }) });
    await assert.rejects(api.searchDocuments("hello", "test-token"), expected);
  }
});

test("library pagination keeps failure details", async () => {
  const api = loadApi("documents.api", { fetch: async (url) => {
    assert.match(url, /page=1&size=10/);
    return { ok: true, json: async () => ({ totalElements: 11, content: [{ documentId: "failed", status: "FAILED", failureCode: "RETRY_EXHAUSTED", failureCount: 3 }] }) };
  } });
  const result = await api.getDocuments("test-token", 1);
  assert.equal(result.totalElements, 11);
  assert.equal(result.content[0].failureCount, 3);
});

test("upload reports transfer progress and waits for a successful response", async () => {
  let request;
  class FakeXHR {
    constructor() { request = this; this.upload = {}; this.headers = {}; }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader(key, value) { this.headers[key] = value; }
    send(body) { this.body = body; }
  }
  const api = loadApi("documents.api", { XMLHttpRequest: FakeXHR });
  const progress = [];
  const pending = api.uploadDocument(new Blob(["test"]), {
    token: "test-token", idempotencyKey: "request-1", onProgress: (percent) => progress.push(percent),
  });
  assert.equal(request.method, "POST");
  assert.equal(request.headers["Idempotency-Key"], "request-1");
  request.upload.onprogress({ lengthComputable: true, loaded: 2, total: 4 });
  assert.deepEqual(progress, [50]);
  request.status = 200;
  request.responseText = '{"documentId":"saved","status":"UPLOADED"}';
  request.onload();
  assert.equal((await pending).documentId, "saved");
});

test("upload network failure is surfaced rather than reported as saved", async () => {
  let request;
  class FakeXHR {
    constructor() { request = this; this.upload = {}; }
    open() {}
    setRequestHeader() {}
    send() {}
  }
  const api = loadApi("documents.api", { XMLHttpRequest: FakeXHR });
  const pending = api.uploadDocument(new Blob(["test"]));
  request.onerror();
  await assert.rejects(pending, /service is currently unavailable/);
});
