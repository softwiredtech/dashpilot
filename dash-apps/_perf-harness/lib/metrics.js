"use strict";

function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.max(0, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function summarize(raw, budgets) {
  const warmupMs = budgets.warmupMs.value;
  const steadySamples = raw.samples.filter((sample) => sample.actualMs >= warmupMs);
  const injectionLags = steadySamples.map((sample) => sample.injectionLagMs);
  const steadyLongTasks = raw.longTasks.filter((task) => task.startTime >= raw.t0 + warmupMs);

  const metrics = {
    steadyFrames: steadySamples.length,
    injectionLagP95Ms: percentile(injectionLags, 95),
    injectionLagMaxMs: injectionLags.length ? Math.max(...injectionLags) : 0,
    longestTaskMs: steadyLongTasks.reduce((max, task) => Math.max(max, task.duration), 0),
  };

  const violations = [];
  for (const name of [
    "injectionLagP95Ms",
    "injectionLagMaxMs",
    "longestTaskMs",
  ]) {
    if (metrics[name] >= budgets[name].value) {
      violations.push({ name, actual: metrics[name], limit: budgets[name].value });
    }
  }

  return { metrics, violations, pass: violations.length === 0 };
}

module.exports = { percentile, summarize };
