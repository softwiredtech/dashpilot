#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { chromium } = require("playwright");

const { startServer } = require("./lib/server");
const { buildScenario, STEP_MS } = require("./lib/scenario");
const { getterMap } = require("./lib/getters");
const { installShim } = require("./lib/shim");
const { summarize } = require("./lib/metrics");
const budgets = require("./lib/budgets.json");

const EXIT = { PASS: 0, BUDGET_FAIL: 1, APP_ERROR: 2, INFRA: 3 };

class AppError extends Error {}

function parseArgs(argv) {
  const args = {
    appName: null,
    throttle: budgets.cpuThrottle.value,
    duration: budgets.defaultDurationMs.value,
    runs: budgets.defaultRuns.value,
    quiet: false,
  };
  const positional = [];

  let index;
  function flagValue(flag) {
    index += 1;
    const value = argv[index];
    if (value === undefined) {
      throw new Error(`${flag} requires a value`);
    }
    return value;
  }

  for (index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--app-name") args.appName = flagValue(arg);
    else if (arg === "--throttle") args.throttle = Number(flagValue(arg));
    else if (arg === "--duration") args.duration = Number(flagValue(arg));
    else if (arg === "--runs") args.runs = Number(flagValue(arg));
    else if (arg === "--quiet") args.quiet = true;
    else positional.push(arg);
  }

  if (positional.length !== 1) {
    throw new Error(
      "usage: node run.js <app-dir> [--app-name NAME] [--throttle N] [--duration MS] [--runs N] [--quiet]"
    );
  }

  if (!Number.isFinite(args.throttle) || args.throttle <= 0) {
    throw new Error(`--throttle must be a positive number, got ${args.throttle}`);
  }
  if (!Number.isFinite(args.duration) || args.duration <= 0) {
    throw new Error(`--duration must be a positive number, got ${args.duration}`);
  }
  if (!Number.isInteger(args.runs) || args.runs <= 0) {
    throw new Error(`--runs must be a positive integer, got ${args.runs}`);
  }

  args.appDir = positional[0];
  return args;
}

async function benchmarkOnce(appDir, args) {
  const { server, port } = await startServer(appDir);
  const browser = await chromium.launch({ args: ["--enable-precise-memory-info"] });

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
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: args.throttle });

    await page.addInitScript(installShim, {
      frames: buildScenario(args.duration),
      stepMs: STEP_MS,
      getterMap: getterMap(),
      contractTimeoutMs: budgets.contractTimeoutMs.value,
      contractPollMs: budgets.contractPollMs.value,
    });

    await page.goto(`http://127.0.0.1:${port}/index.html`, { waitUntil: "load" });
    await page.waitForFunction(
      "window.__dashPerf && window.__dashPerf.done === true",
      undefined,
      {
        timeout:
          args.duration * budgets.resultTimeoutMultiplier.value + budgets.resultTimeoutPaddingMs.value,
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

function pickBest(results) {
  return [...results].sort(
    (left, right) =>
      left.violations.length - right.violations.length ||
      left.metrics.injectionLagP95Ms - right.metrics.injectionLagP95Ms
  )[0];
}

function renderTable(appName, best, args) {
  const rows = [
    ["injection lag p95", best.metrics.injectionLagP95Ms, budgets.injectionLagP95Ms.value],
    ["injection lag max", best.metrics.injectionLagMaxMs, budgets.injectionLagMaxMs.value],
    ["update→paint p95", best.metrics.updateToPaintP95Ms, budgets.updateToPaintP95Ms.value],
    ["longest task", best.metrics.longestTaskMs, budgets.longestTaskMs.value],
    ["heap growth (MB)", best.metrics.heapGrowthMb, budgets.heapGrowthMb.value],
  ];
  const lines = [
    `### dash-app perf: \`${appName}\` — ${best.pass ? "✅ PASS" : "❌ FAIL"} (throttle ${args.throttle}x, ${args.duration / 1000}s, best of ${args.runs})`,
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
  const results = [];

  for (let index = 0; index < args.runs; index += 1) {
    if (!args.quiet) console.log(`run ${index + 1}/${args.runs} for ${appName}...`);
    results.push(await benchmarkOnce(appDir, args));
  }

  const best = pickBest(results);
  const report = {
    app: appName,
    throttle: args.throttle,
    durationMs: args.duration,
    runs: results,
    best,
    pass: best.pass,
  };
  const resultsDir = path.join(__dirname, "results");
  const table = renderTable(appName, best, args);

  fs.mkdirSync(resultsDir, { recursive: true });
  fs.writeFileSync(path.join(resultsDir, `${appName}.json`), JSON.stringify(report, null, 2));

  console.log(table);
  if (!best.pass) {
    for (const violation of best.violations) {
      console.log(
        `FAIL ${violation.name}: ${violation.actual.toFixed(1)} (budget < ${violation.limit}) — ${budgets[violation.name].why}`
      );
    }
  }
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, table);
  }

  process.exit(best.pass ? EXIT.PASS : EXIT.BUDGET_FAIL);
}

main().catch((error) => {
  if (error instanceof AppError) {
    console.error(`dash-app error: ${error.message}`);
    process.exit(EXIT.APP_ERROR);
  }

  console.error(`harness infrastructure error: ${error.stack || error}`);
  process.exit(EXIT.INFRA);
});
