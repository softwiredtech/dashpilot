"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { parseArgs } = require("node:util");
const { JSDOM } = require("jsdom");

// 25 Hz drive cadence; 150 frames = 6 s wall, fast for CI; first 50 (2 s) are warmup.
const STEP_MS = 40, TOTAL_FRAMES = 150, WARMUP_FRAMES = 50;
// Per-parent concentration: 8 sibling attribute writes (per-cell loop, the web-retro regression) = 8 attributed to one parent. in-place single-element updates = 1-2. Threshold 6 sits between.
const BUDGET_PER_PARENT = 6, STREAK = 3;
const CONTRACT_TIMEOUT_MS = 10000;

// derives key by convention; add a special case here only when Kotlin breaks the get/is naming pattern.
const keyFromMethod = (m) => m === "isImperial" ? "useImperial" : m.startsWith("get") ? m[3].toLowerCase() + m.slice(4) : m.startsWith("is") ? m[2].toLowerCase() + m.slice(3) : null;

// minimal scenario — oscillate speed, toggle blinkers, flip ADAS once. Render path runs every tick regardless of input variety; full signal sweep is not needed for a tripwire.
const buildScenario = () => Array.from({ length: TOTAL_FRAMES }, (_, i) => {
  const t = (i * STEP_MS) / 1000;
  return {
    egoSpeed: 60 * (1 - Math.cos(2 * Math.PI * t / 13)),
    leftBlinker: t % 4 < 1 ? 1 : 0, rightBlinker: (t + 2) % 4 < 1 ? 1 : 0,
    gear: 4, adasOn: t >= 4 ? 1 : 0, madsActive: t >= 5 && t < 10 ? 1 : 0,
    dataSourceType: "comma",
    leftBlindSpot: t % 5 < 1 ? 1 : 0, rightBlindSpot: (t + 2.5) % 5 < 1 ? 1 : 0,
    accSetSpeed: t >= 4 ? (Math.floor((t - 4) / 3) % 2 === 0 ? 90 : 110) : 0,
    fusedSpeedLimit: t < 8 ? 50 : 100, useImperial: 0, darkMode: 1,
    laneDepartureWarning: t % 7 < 0.2 ? 1 : 0,
  };
});

// inline <script src="..."> from disk so we can use JSDOM.fromString (no HTTP server). Handles vanilla <script defer src="X"></script>; widen if a future app uses type="module" or conditional loaders.
function inlineScripts(html, appDir) {
  return html.replace(/<script\b([^>]*\bsrc=["']([^"']+)["'][^>]*)><\/script>/gi, (match, attrs, src) => {
    try { return "<script>" + fs.readFileSync(path.resolve(appDir, src), "utf8") + "</script>"; }
    catch { return match; }
  });
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function gate(appDir) {
  const html = inlineScripts(fs.readFileSync(path.join(appDir, "index.html"), "utf8"), appDir);
  const state = { ...buildScenario()[0] };
  const errors = [];

  const dom = new JSDOM(html, {
    runScripts: "dangerously", pretendToBeVisual: true,
    beforeParse(window) {
      // Proxy NativeCarState: every method call returns current state[derivedKey]; unknown methods return undefined so app readNative fallback runs.
      window.NativeCarState = new Proxy({}, { get(_t, p) { const k = keyFromMethod(p); return k === null ? undefined : () => state[k]; } });
      if (!window.matchMedia) window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} });
      // jsdom fires 'error' even when app's window.onerror returns true (swallowed-crash detection).
      window.addEventListener("error", (e) => errors.push("uncaught error: " + ((e && e.error && e.error.message) || (e && e.message) || "unknown error")));
    },
  });

  try {
    const deadline = Date.now() + CONTRACT_TIMEOUT_MS;
    while (typeof dom.window.onCarStateUpdate !== "function") {
      if (errors.length) return { error: errors[0] };
      if (Date.now() > deadline) return { error: "contract timeout" };
      await sleep(100);
    }

    const observer = new dom.window.MutationObserver(() => {});
    observer.observe(dom.window.document.documentElement, { childList: true, subtree: true, attributes: true, characterData: true });
    observer.takeRecords();

    let streak = 0, worstPerParent = 0, fail = null;
    const wallStart = Date.now();
    for (let i = 0; i < TOTAL_FRAMES; i++) {
      Object.assign(state, buildScenario()[i]);
      try { dom.window.onCarStateUpdate(); } catch (e) { errors.unshift("onCarStateUpdate threw: " + (e.message || e)); break; }
      const records = observer.takeRecords();
      if (i < WARMUP_FRAMES) continue;
      const byParent = new Map();
      for (const r of records) {
        const p = r.target.parentNode;
        const key = p && p.tagName ? (p.id ? "#" + p.id : p.className ? "." + p.className.split(" ")[0] : p.tagName.toLowerCase()) : "<root>";
        byParent.set(key, (byParent.get(key) || 0) + 1);
      }
      let maxPerParent = 0, hotParent = "";
      for (const [k, n] of byParent) if (n > maxPerParent) { maxPerParent = n; hotParent = k; }
      if (maxPerParent > worstPerParent) worstPerParent = maxPerParent;
      streak = maxPerParent >= BUDGET_PER_PARENT ? streak + 1 : 0;
      if (streak >= STREAK) { fail = "may cause dashboard lag (driver sees stale speed/blinkers)\n  " + hotParent + ": " + maxPerParent + " child updates per frame (max " + BUDGET_PER_PARENT + ")\n  Fix: update " + hotParent + " once instead of each child"; break; }
      await sleep(Math.max(0, (i + 1) * STEP_MS - (Date.now() - wallStart)));
    }
    if (errors.length) return { error: errors[0] };
    if (fail) return { fail };
    return { pass: true, worstPerParent };
  } finally {
    dom.window.close();
  }
}

async function main() {
  const { values, positionals } = parseArgs({ args: process.argv.slice(2), options: { name: { type: "string" } }, allowPositionals: true });
  if (positionals.length !== 1) { console.error("usage: node gate.js <app-dir> [--name <name>]"); process.exit(3); }
  const appDir = path.resolve(positionals[0]);
  if (!fs.existsSync(path.join(appDir, "index.html"))) { console.error("infra: no index.html in " + appDir); process.exit(3); }
  // derive display name from dir; if basename is 'dist' (web-expo), use the parent dir name.
  const appName = values.name || (path.basename(appDir) === "dist" ? path.basename(path.dirname(appDir)) : path.basename(appDir));
  const r = await gate(appDir);
  if (r.error) { console.error("APP_ERROR " + appName + ": " + r.error); process.exit(2); }
  const line = r.fail
    ? "FAIL " + appName + " — " + r.fail
    : "PASS " + appName + "  maxPerParent=" + r.worstPerParent + "  budget=" + BUDGET_PER_PARENT + "  streak<" + STREAK;
  console.log(line);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, line + "\n");
  process.exit(r.fail ? 1 : 0);
}

main().catch((e) => { console.error("infra:", e.stack || e); process.exit(3); });
