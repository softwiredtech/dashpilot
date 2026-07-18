"use strict";

const { JSDOM, VirtualConsole } = require("jsdom");

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function buildResult(samples = [], steadyProcessCpuMs = 0, finalBody = "", error = null) {
  return { samples, steadyProcessCpuMs, finalBody, error };
}

// jsdom environment gaps, not app bugs: missing resources, unimplemented
// browser APIs (canvas, scrollTo), and CSS that jsdom's parser rejects all
// work fine in the real WebView.
const BENIGN_JSDOM_ERROR_PREFIXES = [
  "Could not load",
  "Not implemented",
  "Could not parse CSS",
];

async function driveApp(url, config) {
  const frames = config.frames;
  const stepMs = config.stepMs;
  const warmupMs = config.warmupMs || 0;
  const state = { ...frames[0] };
  const errors = [];

  const virtualConsole = new VirtualConsole();
  virtualConsole.on("jsdomError", (error) => {
    const message = String((error && error.message) || error);
    if (!BENIGN_JSDOM_ERROR_PREFIXES.some((prefix) => message.startsWith(prefix))) {
      errors.push(message);
    }
  });

  // jsdom never dispatches window "unhandledrejection"; app rejections escape
  // to Node's process-level default handler and would kill the run with exit 1.
  // Temporarily isolate unhandledRejection listeners to prevent Node's test runner
  // from intercepting rejections during tests.
  const originalListeners = [...process.listeners("unhandledRejection")];
  process.removeAllListeners("unhandledRejection");

  const onUnhandledRejection = (reason) => {
    errors.push("unhandled rejection: " + ((reason && reason.message) || String(reason)));
  };
  process.on("unhandledRejection", onUnhandledRejection);

  let dom;
  try {
    dom = await JSDOM.fromURL(url, {
      runScripts: "dangerously",
      resources: "usable",
      pretendToBeVisual: true,
      virtualConsole,
      beforeParse(window) {
        const native = {};
        for (const method of Object.keys(config.getterMap || {})) {
          const key = config.getterMap[method];
          native[method] = function () {
            return state[key];
          };
        }
        window.NativeCarState = native;

        if (!window.matchMedia) {
          window.matchMedia = () => ({
            matches: false,
            addEventListener() {},
            removeEventListener() {},
            addListener() {},
            removeListener() {},
          });
        }

        // Fires even when the app's own window.onerror returns true, which
        // makes jsdom suppress its "jsdomError" report for uncaught exceptions.
        window.addEventListener("error", (event) => {
          const detail =
            (event && event.error && event.error.message) || (event && event.message) || "unknown error";
          errors.push("uncaught error: " + detail);
        });
      },
    });

    const window = dom.window;

    function runtimeError(samples) {
      return buildResult(samples, 0, "", "runtime: " + errors[0]);
    }

    const contractTimeoutMs = config.contractTimeoutMs || 10000;
    const contractDeadline = Date.now() + contractTimeoutMs;

    while (typeof window.onCarStateUpdate !== "function") {
      if (errors.length) return runtimeError([]);
      if (Date.now() > contractDeadline) {
        return buildResult(
          [],
          0,
          "",
          `contract: window.onCarStateUpdate was never defined within ${contractTimeoutMs}ms of page load`
        );
      }
      await sleep(100);
    }

    const observer = new window.MutationObserver(() => {});
    observer.observe(window.document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      characterData: true,
    });
    observer.takeRecords();

    const samples = [];
    let steadyCpuStart = null;
    const t0 = performance.now();
    const deadline = t0 + frames.length * stepMs;

    for (let index = 0; index < frames.length; index += 1) {
      if (errors.length) return runtimeError(samples);
      if (performance.now() >= deadline) break;
      if (steadyCpuStart === null && performance.now() - t0 >= warmupMs) {
        steadyCpuStart = process.cpuUsage();
      }

      Object.assign(state, frames[index]);
      const tickStart = process.hrtime.bigint();

      try {
        window.onCarStateUpdate();
      } catch (error) {
        errors.unshift("onCarStateUpdate threw: " + ((error && error.message) || String(error)));
        return runtimeError(samples);
      }

      samples.push({
        actualMs: performance.now() - t0,
        tickCpuMs: Number(process.hrtime.bigint() - tickStart) / 1e6,
        mutations: observer.takeRecords().length,
      });

      if (errors.length) return runtimeError(samples);

      const nextScheduled = t0 + (index + 1) * stepMs;
      await sleep(Math.max(0, nextScheduled - performance.now()));
    }

    const steadyCpu = steadyCpuStart ? process.cpuUsage(steadyCpuStart) : { user: 0, system: 0 };
    return buildResult(
      samples,
      (steadyCpu.user + steadyCpu.system) / 1000,
      window.document.body.innerHTML,
      errors.length ? "runtime: " + errors[0] : null
    );
  } finally {
    process.off("unhandledRejection", onUnhandledRejection);
    for (const listener of originalListeners) {
      process.on("unhandledRejection", listener);
    }
    if (dom) dom.window.close();
  }
}

module.exports = { driveApp };
