"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { percentile, summarize } = require("../lib/metrics");
const budgets = require("../lib/budgets.json");

function sample(scheduledMs, injectionLagMs, updateToPaintMs, actualMs = scheduledMs) {
  return { scheduledMs, actualMs, injectionLagMs, handlerMs: 1, updateToPaintMs };
}

function rawWith(overrides = {}) {
  return {
    t0: 1000,
    samples: [sample(6000, 5, 20), sample(6040, 6, 22), sample(6080, 4, 18)],
    longTasks: [],
    heapStart: 10 * 1024 * 1024,
    heapEnd: 11 * 1024 * 1024,
    ...overrides,
  };
}

test("percentile picks the right rank", () => {
  const values = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100];
  assert.strictEqual(percentile(values, 50), 50);
  assert.strictEqual(percentile(values, 95), 100);
  assert.strictEqual(percentile([], 95), 0);
});

test("clean run passes", () => {
  const result = summarize(rawWith(), budgets);
  assert.strictEqual(result.pass, true);
  assert.deepStrictEqual(result.violations, []);
});

test("warm-up samples and long tasks are excluded", () => {
  const result = summarize(
    rawWith({
      samples: [sample(100, 900, 900), sample(6000, 5, 20)],
      longTasks: [{ startTime: 1000 + 100, duration: 800 }],
    }),
    budgets
  );
  assert.strictEqual(result.pass, true);
});

test("injection lag violation is reported", () => {
  const result = summarize(rawWith({ samples: [sample(6000, 400, 20)] }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "injectionLagP95Ms"));
  assert.ok(result.violations.some((violation) => violation.name === "injectionLagMaxMs"));
});

test("post-warmup long task violation is reported", () => {
  const result = summarize(rawWith({ longTasks: [{ startTime: 1000 + 9000, duration: 500 }] }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "longestTaskMs"));
});

test("heap growth violation is reported", () => {
  const result = summarize(rawWith({ heapEnd: 10 * 1024 * 1024 + 50 * 1024 * 1024 }), budgets);
  assert.ok(result.violations.some((violation) => violation.name === "heapGrowthMb"));
});

test("null updateToPaint samples are tolerated", () => {
  const result = summarize(rawWith({ samples: [sample(6000, 5, null), sample(6040, 5, 20)] }), budgets);
  assert.strictEqual(result.pass, true);
});

test("warm-up is based on actual elapsed time", () => {
  const result = summarize(rawWith({ samples: [sample(100, 400, 20, 6000)] }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "injectionLagP95Ms"));
});
