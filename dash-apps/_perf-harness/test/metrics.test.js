"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { percentile, summarize } = require("../lib/metrics");
const budgets = require("../lib/budgets.json");

function sample(actualMs, tickCpuMs, mutations) {
  return { actualMs, tickCpuMs, mutations };
}

function rawWith(overrides = {}) {
  return {
    samples: [sample(6000, 0.2, 1), sample(6040, 0.3, 2), sample(6080, 0.1, 0)],
    steadyProcessCpuMs: 3,
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
  assert.strictEqual(result.metrics.steadyFrames, 3);
});

test("warm-up samples are excluded", () => {
  const result = summarize(
    rawWith({ samples: [sample(100, 50, 500), sample(6000, 0.2, 1)] }),
    budgets
  );
  assert.strictEqual(result.pass, true);
  assert.strictEqual(result.metrics.steadyFrames, 1);
});

test("tick cpu violation is reported", () => {
  const result = summarize(rawWith({ samples: [sample(6000, 10, 1)] }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "tickCpuP95Ms"));
});

test("mutation churn violation is reported", () => {
  const result = summarize(rawWith({ samples: [sample(6000, 0.2, 60)] }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "mutationsPerTickP95"));
});

test("deferred work is charged via mean process cpu", () => {
  const result = summarize(rawWith({ steadyProcessCpuMs: 100 }), budgets);
  assert.strictEqual(result.pass, false);
  assert.ok(result.violations.some((violation) => violation.name === "meanProcessCpuPerTickMs"));
});

test("mean process cpu divides steady cpu by steady frames only", () => {
  const result = summarize(
    rawWith({
      samples: [sample(100, 50, 500), sample(6000, 0.2, 1), sample(6040, 0.2, 1)],
      steadyProcessCpuMs: 8,
    }),
    budgets
  );
  assert.strictEqual(result.metrics.meanProcessCpuPerTickMs, 4);
  assert.strictEqual(result.pass, true);
});

test("truncated run fails the min steady sample guard", () => {
  const result = summarize(rawWith(), budgets, 450);

  assert.strictEqual(result.pass, false);
  const violation = result.violations.find((v) => v.name === "minSteadySampleRatio");
  assert.ok(violation, "expected a minSteadySampleRatio violation");
  assert.strictEqual(violation.actual, 3);
  assert.strictEqual(violation.limit, 225);
  assert.strictEqual(violation.op, ">=");
});

test("full run passes the min steady sample guard", () => {
  const samples = Array.from({ length: 450 }, (_, i) => sample(2000 + i * 40, 0.2, 1));
  const result = summarize(rawWith({ samples }), budgets, 450);

  assert.strictEqual(result.pass, true);
  assert.strictEqual(result.limits.minSteadyFrames, 225);
});
