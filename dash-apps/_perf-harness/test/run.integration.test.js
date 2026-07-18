"use strict";

const test = require("node:test");
const assert = require("node:assert");
const path = require("node:path");
const fs = require("node:fs");
const os = require("node:os");
const { spawnSync } = require("node:child_process");

const HARNESS = path.join(__dirname, "..");
const RUN = path.join(HARNESS, "run.js");
const RESULTS = path.join(HARNESS, "results");

function runCli(args, extra = {}) {
  return spawnSync(process.execPath, [RUN, ...args], {
    cwd: HARNESS,
    encoding: "utf8",
    timeout: 300_000,
    ...extra,
  });
}

function reportPath(name) {
  return path.join(RESULTS, `${name}.json`);
}

test("fast fixture passes: exit 0, named report, markdown step summary", () => {
  fs.rmSync(reportPath("named-app"), { force: true });
  const summaryDir = fs.mkdtempSync(path.join(os.tmpdir(), "perf-harness-summary-"));
  const summaryPath = path.join(summaryDir, "summary.md");

  try {
    const result = runCli(
      ["fixtures/fast-app", "--app-name", "named-app"],
      { env: { ...process.env, GITHUB_STEP_SUMMARY: summaryPath, PERF_DURATION: "5000" } }
    );

    assert.strictEqual(result.status, 0, result.stdout + result.stderr);
    const report = JSON.parse(fs.readFileSync(reportPath("named-app"), "utf8"));
    assert.strictEqual(report.app, "named-app");
    assert.strictEqual(report.pass, true);
    assert.ok(report.metrics.steadyFrames > 0);
    assert.strictEqual(report.limits.minSteadyFrames, 38);
    assert.ok(report.metrics.steadyFrames >= report.limits.minSteadyFrames);
    assert.match(fs.readFileSync(summaryPath, "utf8"), /dash-app perf: `named-app`/);
  } finally {
    fs.rmSync(summaryDir, { recursive: true, force: true });
  }
});

test("slow fixture fails budgets (exit 1) and names the violation", () => {
  fs.rmSync(reportPath("slow-app"), { force: true });

  const result = runCli(["fixtures/slow-app"], { env: { ...process.env, PERF_DURATION: "5000" } });

  assert.strictEqual(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout, /tickCpu/);
});

test("missing contract exits 2 with a clear message", () => {
  const result = runCli(["fixtures/no-contract-app"], { env: { ...process.env, PERF_DURATION: "5000", PERF_CONTRACT_TIMEOUT: "1000" } });

  assert.strictEqual(result.status, 2, result.stdout + result.stderr);
  assert.match(result.stdout + result.stderr, /onCarStateUpdate/);
});

test("nonexistent app dir exits 3 (infra, not app)", () => {
  const result = runCli(["fixtures/does-not-exist"]);

  assert.strictEqual(result.status, 3, result.stdout + result.stderr);
});

test("invalid PERF_CONTRACT_TIMEOUT exits 3 without benchmarking", () => {
  const result = runCli(["fixtures/fast-app"], { env: { ...process.env, PERF_CONTRACT_TIMEOUT: "soon" } });

  assert.strictEqual(result.status, 3, result.stdout + result.stderr);
  assert.match(result.stderr, /PERF_CONTRACT_TIMEOUT/);
});

test("missing flag value exits 3", () => {
  const result = runCli(["fixtures/fast-app", "--app-name"]);

  assert.strictEqual(result.status, 3, result.stdout + result.stderr);
  assert.match(result.stdout + result.stderr, /argument missing/i);
});

test("app unhandled rejection exits 2 (app error), not 1", () => {
  const result = runCli(["fixtures/rejecting-app"], { env: { ...process.env, PERF_DURATION: "3000" } });

  assert.strictEqual(result.status, 2, result.stdout + result.stderr);
  assert.match(result.stdout + result.stderr, /unhandled rejection/);
});
