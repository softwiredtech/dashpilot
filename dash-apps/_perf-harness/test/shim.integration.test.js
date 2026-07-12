"use strict";

const test = require("node:test");
const assert = require("node:assert");
const path = require("node:path");
const { chromium } = require("playwright");
const { startServer } = require("../lib/server");
const { buildScenario, STEP_MS } = require("../lib/scenario");
const { getterMap } = require("../lib/getters");
const { installShim } = require("../lib/shim");

async function runShim(fixture, { durationMs = 2000, contractTimeoutMs = 3000 } = {}) {
  const root = path.join(__dirname, "..", "fixtures", fixture);
  const { server, port } = await startServer(root);
  const browser = await chromium.launch();

  try {
    const page = await browser.newPage();
    await page.addInitScript(installShim, {
      frames: buildScenario(durationMs),
      stepMs: STEP_MS,
      getterMap: getterMap(),
      contractTimeoutMs,
    });
    await page.goto(`http://127.0.0.1:${port}/index.html`);
    await page.waitForFunction(
      "window.__dashPerf && window.__dashPerf.done === true",
      undefined,
      { timeout: durationMs + 30000 }
    );
    return await page.evaluate(() => JSON.parse(JSON.stringify(window.__dashPerf)));
  } finally {
    await browser.close();
    server.close();
  }
}

test("collects one sample per frame with sane timings", async () => {
  const raw = await runShim("fast-app");

  assert.strictEqual(raw.error, null);
  assert.ok(raw.samples.length > 40 && raw.samples.length <= 50, `expected near-full sampling, got ${raw.samples.length}`);
  for (const sample of raw.samples) {
    assert.ok(sample.injectionLagMs >= 0);
    assert.ok(sample.handlerMs >= 0);
  }
  assert.ok(raw.samples.filter((sample) => sample.updateToPaintMs != null).length > 40);
  assert.ok(raw.heapStart > 0);
});

test("state is visible to the app through NativeCarState", async () => {
  const root = path.join(__dirname, "..", "fixtures", "fast-app");
  const { server, port } = await startServer(root);
  const browser = await chromium.launch();

  try {
    const page = await browser.newPage();
    await page.addInitScript(installShim, {
      frames: buildScenario(2000),
      stepMs: STEP_MS,
      getterMap: getterMap(),
      contractTimeoutMs: 3000,
    });
    await page.goto(`http://127.0.0.1:${port}/index.html`);
    await page.waitForFunction(
      "window.__dashPerf && window.__dashPerf.done === true",
      undefined,
      { timeout: 30000 }
    );
    const text = await page.textContent("#speed");
    assert.notStrictEqual(text, "0");
  } finally {
    await browser.close();
    server.close();
  }
});

test("slow app accumulates injection lag", async () => {
  const raw = await runShim("slow-app", { durationMs: 3000 });

  assert.strictEqual(raw.error, null);
  assert.ok(raw.samples.length < buildScenario(3000).length, "overloaded apps should stop at the wall-clock deadline");
  const lastLag = raw.samples[raw.samples.length - 1].injectionLagMs;
  assert.ok(lastLag > 500, `expected runaway lag, got ${lastLag}ms`);
});

test("missing onCarStateUpdate reports a contract error", async () => {
  const raw = await runShim("no-contract-app", { contractTimeoutMs: 1500 });

  assert.match(raw.error, /onCarStateUpdate/);
  assert.strictEqual(raw.samples.length, 0);
});
