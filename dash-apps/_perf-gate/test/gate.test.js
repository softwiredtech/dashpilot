"use strict";

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const gateDir = path.resolve(__dirname, "..");

describe("perf gate", () => {
  it("fast-app passes", () => {
    const r = spawnSync("node", ["gate.js", "fixtures/fast-app", "--name", "fast"], {
      cwd: gateDir,
      encoding: "utf-8",
    });
    assert.strictEqual(r.status, 0, "exit code 0, stderr: " + r.stderr);
    assert.ok(r.stdout.startsWith("PASS"), "stdout starts with PASS: " + r.stdout);
  });

  it("churn-app FAILS with clear message", () => {
    const r = spawnSync("node", ["gate.js", "fixtures/churn-app", "--name", "churn"], {
      cwd: gateDir,
      encoding: "utf-8",
    });
    assert.strictEqual(r.status, 1, "exit code 1, stdout: " + r.stdout);
    assert.ok(r.stdout.startsWith("FAIL"), "stdout starts with FAIL: " + r.stdout);
    assert.ok(r.stdout.includes("may cause dashboard lag"), "stdout mentions user impact: " + r.stdout);
    assert.ok(r.stdout.includes("child updates per frame"), "stdout mentions child updates per frame: " + r.stdout);
    assert.ok(r.stdout.includes("Fix: update #tape"), "stdout has fix hint with element name: " + r.stdout);
  });

  it("no-contract APP_ERROR", () => {
    const tmpDir = fs.mkdtempSync("perf-gate-no-contract-");
    try {
      fs.writeFileSync(path.join(tmpDir, "index.html"), "<!DOCTYPE html><html><body><script></script></body></html>");
      const r = spawnSync("node", ["gate.js", tmpDir, "--name", "no-contract"], {
        cwd: gateDir,
        encoding: "utf-8",
      });
      assert.strictEqual(r.status, 2, "exit code 2, stderr: " + r.stderr + ", stdout: " + r.stdout);
    } finally {
      fs.rmSync(tmpDir, { recursive: true, force: true });
    }
  });
});
