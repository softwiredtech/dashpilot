"use strict";

function installShim(config) {
  const frames = Array.isArray(config.frames) ? config.frames : [];
  const stepMs = config.stepMs;
  const state = {};
  const firstFrame = frames[0] || {};
  for (const key of Object.keys(firstFrame)) state[key] = firstFrame[key];

  const native = {};
  for (const method of Object.keys(config.getterMap || {})) {
    const key = config.getterMap[method];
    native[method] = function () {
      return state[key];
    };
  }
  window.NativeCarState = native;

  function heap() {
    return performance.memory && typeof performance.memory.usedJSHeapSize === "number"
      ? performance.memory.usedJSHeapSize
      : 0;
  }

  const perf = {
    t0: 0,
    deadline: 0,
    samples: [],
    longTasks: [],
    heapStart: 0,
    heapEnd: 0,
    done: false,
    error: null,
  };
  window.__dashPerf = perf;

  function fail(message) {
    if (perf.done) return;
    perf.error = message;
    perf.heapEnd = heap();
    perf.done = true;
  }

  try {
    new PerformanceObserver(function (list) {
      for (const entry of list.getEntries()) {
        perf.longTasks.push({ startTime: entry.startTime, duration: entry.duration });
      }
    }).observe({ type: "longtask", buffered: true });
  } catch {
  }

  window.addEventListener("error", function (event) {
    if (!event) return;
    const error = event.error;
    const message = error && error.message ? error.message : event.message;
    if (message) fail("runtime: " + message);
  });

  window.addEventListener("unhandledrejection", function (event) {
    const reason = event && event.reason;
    const message = reason && reason.message ? reason.message : String(reason);
    fail("runtime: unhandled rejection: " + message);
  });

  function finish() {
    if (perf.done) return;
    perf.heapEnd = heap();
    perf.done = true;
  }

  function tick(index) {
    if (perf.done) return;
    if (performance.now() >= perf.deadline) {
      requestAnimationFrame(function () {
        setTimeout(finish, 0);
      });
      return;
    }

    const scheduled = perf.t0 + index * stepMs;
    const injectionLagMs = Math.max(0, performance.now() - scheduled);
    const frame = frames[index];
    for (const key in frame) state[key] = frame[key];

    if (typeof window.onCarStateUpdate !== "function") {
      fail("contract: window.onCarStateUpdate is not a function");
      return;
    }

    const handlerStart = performance.now();
    try {
      window.onCarStateUpdate();
    } catch (error) {
      fail("runtime: onCarStateUpdate threw: " + (error && error.message ? error.message : String(error)));
      return;
    }
    const handlerEnd = performance.now();

    const sample = {
      scheduledMs: index * stepMs,
      actualMs: handlerStart - perf.t0,
      injectionLagMs,
      handlerMs: handlerEnd - handlerStart,
      updateToPaintMs: null,
    };
    perf.samples.push(sample);

    requestAnimationFrame(function () {
      sample.updateToPaintMs = performance.now() - handlerStart;
    });

    if (index + 1 < frames.length && performance.now() < perf.deadline) {
      const delay = Math.max(0, scheduled + stepMs - performance.now());
      setTimeout(function () {
        tick(index + 1);
      }, delay);
      return;
    }

    requestAnimationFrame(function () {
      setTimeout(finish, 0);
    });
  }

  function start() {
    if (perf.done) return;
    if (!frames.length) {
      fail("runtime: no scenario frames provided");
      return;
    }
    perf.t0 = performance.now();
    perf.deadline = perf.t0 + frames.length * stepMs;
    perf.heapStart = heap();
    tick(0);
  }

  window.addEventListener("load", function () {
    if (perf.done) return;

    const deadline = performance.now() + config.contractTimeoutMs;
    (function waitForContract() {
      if (perf.done) return;
      if (typeof window.onCarStateUpdate === "function") {
        start();
        return;
      }
      if (performance.now() > deadline) {
        fail(
          "contract: window.onCarStateUpdate was never defined within " +
            config.contractTimeoutMs +
            "ms of page load"
        );
        return;
      }
      setTimeout(waitForContract, config.contractPollMs);
    })();
  });
}

module.exports = { installShim };
