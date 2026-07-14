#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { parseArgs: nodeParseArgs } = require("node:util");
const { chromium } = require("playwright");

const { startServer } = require("./lib/server");
const { buildScenario, STEP_MS } = require("./lib/scenario");
const { getterMap } = require("./lib/getters");
const { installShim } = require("./lib/shim");
const { summarize } = require("./lib/metrics");
const budgets = require("./lib/budgets.json");

const RESULT_TIMEOUT_MULTIPLIER = 4;
const RESULT_TIMEOUT_PADDING_MS = 60000;

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

async function benchmarkOnce(appDir, duration) {
  const { server, port } = await startServer(appDir);
  const browser = await chromium.launch();

  try {
    const context = await browser.newContext({
      viewport: {
        width: budgets.viewport.value.width,
        height: budgets.viewport.value.height,
      },
      deviceScaleFactor: budgets.viewport.value.deviceScaleFactor,
    });
    const page = await context.newPage();
    const pageErrors = [];

    page.on("pageerror", (error) => pageErrors.push(error.message));

    const cdp = await context.newCDPSession(page);
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: budgets.cpuThrottle.value });

    await page.addInitScript(installShim, {
      frames: buildScenario(duration),
      stepMs: STEP_MS,
      getterMap: getterMap(),
    });

    await page.goto(`http://127.0.0.1:${port}/index.html`, { waitUntil: "load" });
    await page.waitForFunction(
      "window.__dashPerf && window.__dashPerf.done === true",
      undefined,
      {
        timeout: duration * RESULT_TIMEOUT_MULTIPLIER + RESULT_TIMEOUT_PADDING_MS,
      }
    );

    const raw = await page.evaluate(() => JSON.parse(JSON.stringify(window.__dashPerf)));

    if (raw.error) throw new AppError(raw.error);
    if (pageErrors.length > 0) throw new AppError("page errors during run:\n" + pageErrors.join("\n"));

    return summarize(raw, budgets);
  } finally {
    await browser.close();
    server.close();
  }
}

function renderTable(appName, result, duration) {
  const rows = [
    ["injection lag p95", result.metrics.injectionLagP95Ms, budgets.injectionLagP95Ms.value],
    ["injection lag max", result.metrics.injectionLagMaxMs, budgets.injectionLagMaxMs.value],
    ["longest task", result.metrics.longestTaskMs, budgets.longestTaskMs.value],
  ];
  const lines = [
    `### dash-app perf: \`${appName}\` — ${result.pass ? "✅ PASS" : "❌ FAIL"} (throttle ${budgets.cpuThrottle.value}x, ${duration / 1000}s)`,
    "",
    "| metric | measured | budget | |",
    "|---|---|---|---|",
  ];

  for (const [name, actual, limit] of rows) {
    lines.push(`| ${name} | ${actual.toFixed(1)} | < ${limit} | ${actual < limit ? "✅" : "❌"} |`);
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
  let duration = budgets.defaultDurationMs.value;
  if (process.env.PERF_DURATION) {
    duration = Number(process.env.PERF_DURATION);
    if (!Number.isFinite(duration) || duration <= 0) {
      throw new Error(
        `PERF_DURATION must be a positive number of milliseconds, got "${process.env.PERF_DURATION}"`
      );
    }
  }

  const result = await benchmarkOnce(appDir, duration);
  const report = {
    app: appName,
    throttle: budgets.cpuThrottle.value,
    durationMs: duration,
    metrics: result.metrics,
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
        `FAIL ${violation.name}: ${violation.actual.toFixed(1)} (budget < ${violation.limit}) — ${budgets[violation.name].why}`
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
