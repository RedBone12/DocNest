const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");

test("DocNest branding is consistent across browser title and visible headings", () => {
  const html = read("index.html");
  assert.match(html, /<title>DocNest<\/title>/);
  assert.match(html, /href="\/docnest\.svg"/);
  assert.ok(fs.existsSync(path.join(root, "public/docnest.svg")));
  for (const file of ["src/components/layout/Header.tsx", "src/pages/LoginPage.tsx"]) {
    const source = read(file);
    assert.match(source, />\s*DocNest\s*</);
    assert.doesNotMatch(source, /Document Manager|document-frontend/i);
  }
});

test("package identity matches the lockfile", () => {
  const pkg = JSON.parse(read("package.json"));
  const lock = JSON.parse(read("package-lock.json"));
  assert.equal(pkg.name, "docnest");
  assert.equal(lock.name, pkg.name);
  assert.equal(lock.packages[""].name, pkg.name);
});
