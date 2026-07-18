#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { parseArgs: nodeParseArgs } = require("node:util");

const { startServer } = require("./lib/server");
const { buildScenario, STEP_MS } = require("./lib/scenario");
const { getterMap } = require("./lib/getters");
const { driveApp } = require("./lib/drive");
const { summarize } = require("./lib/metrics");
const budgets = require("./lib/budgets.json");

const EXIT = { PASS: 0, BUDGET_FAIL: 1, APP_ERROR: 2, INFRA: 3 };

class AppError extends Error {}

function parseArgs(argv) {
  const { values, positionals } = nodeParseArgs({
    args: argv,
    options: { "app-name": { type: "string" } },
    allowPositionals: true,
  });

  if (positionals.length !== 1) {
    throw new Error("usage: node run.js <app-dir> [--app-name NAME]");
  }

  return { appName: values["app-name"] ?? null, appDir: positionals[0] };
}

function positiveMsFromEnv(name) {
  const rawValue = process.env[name];
  if (!rawValue) return undefined;
  const value = Number(rawValue);
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number of milliseconds, got "${rawValue}"`);
  }
  return value;
}

async function benchmarkOnce(appDir, duration, contractTimeoutMs) {
  const { server, port } = await startServer(appDir);

  try {
    const raw = await driveApp(`http://127.0.0.1:${port}/index.html`, {
      frames: buildScenario(duration),
      stepMs: STEP_MS,
      getterMap: getterMap(),
      warmupMs: budgets.warmupMs.value,
      contractTimeoutMs,
    });

    if (raw.error) throw new AppError(raw.error);

    const expectedSteadyFrames = Math.max(
      0,
      Math.floor((duration - budgets.warmupMs.value) / STEP_MS)
    );
    return summarize(raw, budgets, expectedSteadyFrames);
  } finally {
    server.close();
  }
}

const fmt = (value) => (Number.isInteger(value) ? String(value) : value.toFixed(1));

function renderTable(appName, result, duration) {
  const rows = [
    ["tick cpu p95", result.metrics.tickCpuP95Ms, result.limits.tickCpuP95Ms, "<"],
    ["mutations/tick p95", result.metrics.mutationsPerTickP95, result.limits.mutationsPerTickP95, "<"],
    ["mean cpu/tick", result.metrics.meanProcessCpuPerTickMs, result.limits.meanProcessCpuPerTickMs, "<"],
    ["steady frames", result.metrics.steadyFrames, result.limits.minSteadyFrames, ">="],
  ];
  const lines = [
    `### dash-app perf: \`${appName}\` — ${result.pass ? "✅ PASS" : "❌ FAIL"} (jsdom tripwire, ${duration / 1000}s)`,
    "",
    "| metric | measured | budget | |",
    "|---|---|---|---|",
  ];

  for (const [name, actual, limit, op] of rows) {
    const ok = op === "<" ? actual < limit : actual >= limit;
    lines.push(`| ${name} | ${fmt(actual)} | ${op} ${limit} | ${ok ? "✅" : "❌"} |`);
  }

  return lines.join("\n") + "\n\n";
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const appDir = path.resolve(args.appDir);

  if (!fs.existsSync(path.join(appDir, "index.html"))) {
    console.error(`infra: no index.html in ${appDir}`);
    process.exit(EXIT.INFRA);
  }

  const appName = args.appName || path.basename(appDir);
  const duration = positiveMsFromEnv("PERF_DURATION") ?? budgets.defaultDurationMs.value;
  const contractTimeoutMs = positiveMsFromEnv("PERF_CONTRACT_TIMEOUT");

  const result = await benchmarkOnce(appDir, duration, contractTimeoutMs);
  const report = {
    app: appName,
    durationMs: duration,
    metrics: result.metrics,
    limits: result.limits,
    violations: result.violations,
    pass: result.pass,
  };
  const resultsDir = path.join(__dirname, "results");
  const table = renderTable(appName, result, duration);

  fs.mkdirSync(resultsDir, { recursive: true });
  fs.writeFileSync(path.join(resultsDir, `${appName}.json`), JSON.stringify(report, null, 2));

  console.log(table);
  if (!result.pass) {
    for (const violation of result.violations) {
      console.log(
        `FAIL ${violation.name}: ${fmt(violation.actual)} (budget ${violation.op} ${violation.limit}) — ${budgets[violation.name].why}`
      );
    }
  }
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, table);
  }

  process.exit(result.pass ? EXIT.PASS : EXIT.BUDGET_FAIL);
}

main().catch((error) => {
  if (error instanceof AppError) {
    console.error(`dash-app error: ${error.message}`);
    process.exit(EXIT.APP_ERROR);
  }

  console.error(`harness infrastructure error: ${error.stack || error}`);
  process.exit(EXIT.INFRA);
});
