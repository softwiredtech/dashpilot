"use strict";

function percentile(values, p) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.max(0, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function summarize(raw, budgets, expectedSteadyFrames = 0) {
  const warmupMs = budgets.warmupMs.value;
  const steadySamples = raw.samples.filter((sample) => sample.actualMs >= warmupMs);

  const metrics = {
    steadyFrames: steadySamples.length,
    tickCpuP95Ms: percentile(steadySamples.map((sample) => sample.tickCpuMs), 95),
    mutationsPerTickP95: percentile(steadySamples.map((sample) => sample.mutations), 95),
    meanProcessCpuPerTickMs: steadySamples.length ? raw.steadyProcessCpuMs / steadySamples.length : 0,
  };

  const limits = {
    tickCpuP95Ms: budgets.tickCpuP95Ms.value,
    mutationsPerTickP95: budgets.mutationsPerTickP95.value,
    meanProcessCpuPerTickMs: budgets.meanProcessCpuPerTickMs.value,
    minSteadyFrames: Math.ceil(expectedSteadyFrames * budgets.minSteadySampleRatio.value),
  };

  const violations = [];
  for (const name of [
    "tickCpuP95Ms",
    "mutationsPerTickP95",
    "meanProcessCpuPerTickMs",
  ]) {
    if (metrics[name] >= limits[name]) {
      violations.push({ name, actual: metrics[name], limit: limits[name], op: "<" });
    }
  }

  if (metrics.steadyFrames < limits.minSteadyFrames) {
    violations.push({
      name: "minSteadySampleRatio",
      actual: metrics.steadyFrames,
      limit: limits.minSteadyFrames,
      op: ">=",
    });
  }

  return { metrics, limits, violations, pass: violations.length === 0 };
}

module.exports = { percentile, summarize };
