"use strict";

const test = require("node:test");
const assert = require("node:assert");
const path = require("node:path");
const { startServer } = require("../lib/server");
const { buildScenario, STEP_MS } = require("../lib/scenario");
const { getterMap } = require("../lib/getters");
const { driveApp } = require("../lib/drive");

async function drive(fixture, { durationMs = 2000, contractTimeoutMs = 3000, warmupMs = 0 } = {}) {
  const root = path.join(__dirname, "..", "fixtures", fixture);
  const { server, port } = await startServer(root);

  try {
    return await driveApp(`http://127.0.0.1:${port}/index.html`, {
      frames: buildScenario(durationMs),
      stepMs: STEP_MS,
      getterMap: getterMap(),
      contractTimeoutMs,
      warmupMs,
    });
  } finally {
    server.close();
  }
}

let fastAppRawPromise = null;
function getFastAppRaw() {
  if (!fastAppRawPromise) {
    fastAppRawPromise = drive("fast-app");
  }
  return fastAppRawPromise;
}

test("collects one sample per frame with sane values", async () => {
  const raw = await getFastAppRaw();

  assert.strictEqual(raw.error, null);
  assert.ok(raw.samples.length > 40 && raw.samples.length <= 50, `expected near-full sampling, got ${raw.samples.length}`);
  for (const sample of raw.samples) {
    assert.ok(sample.actualMs >= 0);
    assert.ok(sample.tickCpuMs >= 0);
    assert.ok(sample.mutations >= 0);
  }
});

test("state is visible to the app through NativeCarState", async () => {
  const raw = await getFastAppRaw();

  assert.strictEqual(raw.error, null);
  assert.match(raw.finalBody, /id="speed"[^>]*>[1-9]\d*</);
});

test("slow app burns tick CPU and stops at the wall-clock deadline", async () => {
  const raw = await drive("slow-app", { durationMs: 3000 });

  assert.strictEqual(raw.error, null);
  assert.ok(raw.samples.length < buildScenario(3000).length, "overloaded apps should stop at the deadline");
  const worst = Math.max(...raw.samples.map((sample) => sample.tickCpuMs));
  assert.ok(worst > 50, `expected an expensive tick, got ${worst}ms`);
});

test("missing onCarStateUpdate reports a contract error", async () => {
  const raw = await drive("no-contract-app", { contractTimeoutMs: 1500 });

  assert.match(raw.error, /contract:.*onCarStateUpdate/);
  assert.strictEqual(raw.samples.length, 0);
});

test("steady process cpu excludes pre-warmup work", async () => {
  const raw = await drive("fast-app", { durationMs: 3000, warmupMs: 1000 });

  assert.strictEqual(raw.error, null);
  assert.ok(raw.steadyProcessCpuMs > 0, "steady cpu should be measured");
});

test("app unhandled rejection is reported as a runtime error, not a crash", async () => {
  const raw = await drive("rejecting-app");

  assert.match(raw.error, /runtime: unhandled rejection: boom async/);
});

test("jsdom environment gaps (canvas, scrollTo, css) are not app errors", async () => {
  const raw = await drive("browser-only-app");

  assert.strictEqual(raw.error, null);
  assert.match(raw.finalBody, /id="speed"[^>]*>[1-9]\d*</);
});

test("apps that swallow window.onerror cannot hide crashes", async () => {
  const raw = await drive("onerror-swallowing-app");

  assert.match(raw.error, /runtime: uncaught error: hidden crash/);
});
