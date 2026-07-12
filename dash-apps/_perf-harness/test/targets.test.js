"use strict";
const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { selectTargets } = require("../lib/targets");

function makeAppsRoot(apps) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "targets-"));
  for (const [name, manifest] of Object.entries(apps)) {
    fs.mkdirSync(path.join(root, name), { recursive: true });
    if (manifest !== null) {
      fs.writeFileSync(path.join(root, name, "manifest.json"), JSON.stringify(manifest));
    }
  }
  return root;
}

test("changed standard app is gated", () => {
  const appsRoot = makeAppsRoot({ "web-a": { perf: { gate: "standard" } } });
  const result = selectTargets({ changedFiles: ["dash-apps/web-a/index.html"], appsRoot });
  assert.deepStrictEqual(result.gated, [{ name: "web-a", root: "." }]);
  assert.deepStrictEqual(result.skipped, []);
  assert.strictEqual(result.harnessChanged, false);
});

test("missing manifest or missing perf field defaults to gated", () => {
  const appsRoot = makeAppsRoot({ "web-a": null, "web-b": { id: "b" } });
  const result = selectTargets({
    changedFiles: ["dash-apps/web-a/x.js", "dash-apps/web-b/y.js"],
    appsRoot,
  });
  assert.deepStrictEqual(result.gated.map((gated) => gated.name), ["web-a", "web-b"]);
});

test("exempt app is skipped with its reason", () => {
  const appsRoot = makeAppsRoot({
    "web-3d": { perf: { gate: "exempt", reason: "GPU renderer, unfair in CI" } },
  });
  const result = selectTargets({ changedFiles: ["dash-apps/web-3d/index.html"], appsRoot });
  assert.deepStrictEqual(result.gated, []);
  assert.deepStrictEqual(result.skipped, [{ name: "web-3d", reason: "GPU renderer, unfair in CI" }]);
});

test("exempt without reason throws", () => {
  const appsRoot = makeAppsRoot({ "web-x": { perf: { gate: "exempt" } } });
  assert.throws(() => selectTargets({ changedFiles: ["dash-apps/web-x/a.js"], appsRoot }), /reason/);
});

test("harness change expands to all standard apps only", () => {
  const appsRoot = makeAppsRoot({
    "web-a": { perf: { gate: "standard" } },
    "web-3d": { perf: { gate: "exempt", reason: "GPU renderer, unfair in CI" } },
  });
  const result = selectTargets({
    changedFiles: ["dash-apps/_perf-harness/lib/budgets.json"],
    appsRoot,
  });
  assert.strictEqual(result.harnessChanged, true);
  assert.deepStrictEqual(result.gated.map((gated) => gated.name), ["web-a"]);
  assert.deepStrictEqual(result.skipped.map((skipped) => skipped.name), ["web-3d"]);
});

test("custom root is passed through", () => {
  const appsRoot = makeAppsRoot({ "web-built": { perf: { gate: "standard", root: "dist" } } });
  const result = selectTargets({ changedFiles: ["dash-apps/web-built/App.tsx"], appsRoot });
  assert.deepStrictEqual(result.gated, [{ name: "web-built", root: "dist" }]);
});

test("non-app and deleted-app changes are ignored", () => {
  const appsRoot = makeAppsRoot({ "web-a": {} });
  const result = selectTargets({
    changedFiles: ["dashpilot-android/foo.kt", "dash-apps/web-gone/old.js", "dash-apps/README.md"],
    appsRoot,
  });
  assert.deepStrictEqual(result.gated, []);
  assert.deepStrictEqual(result.skipped, []);
});
