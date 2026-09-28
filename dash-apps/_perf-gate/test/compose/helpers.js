"use strict";
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { JSDOM } = require("jsdom");

const APP_DIR = path.join(__dirname, "..", "web-compose");

// Runs core files in a bare context (no DOM) and returns window.DashCompose.
function loadCore(files) {
  const window = {};
  const context = vm.createContext({ window, console, Intl, Map, Set, Math, Number, String, Date, Array, Object, JSON });
  for (const file of files) {
    vm.runInContext(fs.readFileSync(path.join(APP_DIR, file), "utf8"), context, { filename: file });
  }
  return window.DashCompose;
}

// Loads the full app in jsdom with scripts inlined (same approach as the perf gate).
// `android`: object of CarState values served through NativeCarState getters.
// `layout`: string returned by NativeCarState.getComposeLayout().
// `extraScript`: source injected right after widgets/_generated.js.
// `iosLayout`: what the native side injects before the first script runs.
// `iosSaves`: array that receives what the page posts back to the host.
// `iosEditing`: array that receives the editor-open flag posted to the host.
// `nativeExtra`: extra methods on NativeCarState (Android host capabilities).
function loadApp({ android, layout, extraScript, iosLayout, iosSaves, iosEditing, nativeExtra } = {}) {
  const inline = (src) => "<script>" + fs.readFileSync(path.join(APP_DIR, src), "utf8") + "</script>";
  let html = fs.readFileSync(path.join(APP_DIR, "index.html"), "utf8")
    .replace(/<link[^>]*>/g, "")
    .replace(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*><\/script>/gi, (_, src) =>
      inline(src) + (src === "widgets/_generated.js" && extraScript ? "<script>" + extraScript + "</script>" : ""));

  const errors = [];
  const logged = [];
  const dom = new JSDOM(html, {
    // A real origin: without one jsdom has an opaque origin and localStorage throws,
    // which is exactly what the app must survive (see host.js) but not what we test here.
    url: "https://appassets.androidplatform.net/assets/web-compose/index.html",
    runScripts: "dangerously",
    pretendToBeVisual: true,
    beforeParse(window) {
      window.matchMedia = () => ({ matches: false, addEventListener() {} });
      if (iosLayout !== undefined) window.__DASHPILOT_COMPOSE_LAYOUT__ = iosLayout;
      if (iosSaves || iosEditing) {
        const handlers = {};
        if (iosSaves) handlers.composeLayout = { postMessage: (body) => iosSaves.push(body) };
        if (iosEditing) handlers.composeEditing = { postMessage: (body) => iosEditing.push(body) };
        window.webkit = { messageHandlers: handlers };
      }
      window.addEventListener("error", (e) => errors.push(e.message));
      window.console.error = (...args) => logged.push(args.map(String).join(" "));
      if (android || layout !== undefined) {
        const state = android || {};
        window.NativeCarState = new Proxy({}, {
          get(_t, method) {
            if (nativeExtra && method in nativeExtra) return nativeExtra[method];
            if (method === "getComposeLayout") return () => layout;
            if (typeof method !== "string") return undefined;
            const key = method === "isImperial" ? "useImperial"
              : method.startsWith("get") ? method[3].toLowerCase() + method.slice(4)
              : method.startsWith("is") ? method[2].toLowerCase() + method.slice(3) : null;
            return key === null ? undefined : () => state[key];
          },
        });
      }
    },
  });
  const w = dom.window;
  const cell = (uid) => w.document.querySelector(`[data-uid="${uid}"]`);
  return {
    window: w,
    // The live object behind the NativeCarState getters: mutate it like the bridge does.
    state: android,
    errors,
    logged,
    cell,
    text: (uid) => cell(uid).textContent.replace(/\s+/g, " ").trim(),
    widget: (uid) => cell(uid).querySelector(".widget"),
    // Wait for the rAF-coalesced tick to flush.
    settle: () => new Promise((r) => setTimeout(r, 60)),
    close: () => w.close(),
  };
}

module.exports = { APP_DIR, loadCore, loadApp };
