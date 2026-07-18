"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { startServer } = require("./lib/server");
const { getterMap } = require("./lib/getters");
const { buildScenario, STEP_MS } = require("./lib/scenario");

const EXIT = { PASS: 0, INFRA: 3 };

function buildShimContent() {
  const map = getterMap();
  const frames = buildScenario(20000);

  return `(function() {
  const frames = ${JSON.stringify(frames)};
  const map = ${JSON.stringify(map)};
  const STEP_MS = ${STEP_MS};

  let index = 0;
  let state = frames[0];

  const native = {};
  for (const methodName in map) {
    const key = map[methodName];
    native[methodName] = function() {
      return state[key];
    };
  }
  window.NativeCarState = native;

  let connected = false;
  setInterval(() => {
    if (typeof window.onCarStateUpdate === "function") {
      if (!connected) {
        connected = true;
        console.log("[dev-shim] fake drive running");
      }
      const completedLoops = Math.floor(index / frames.length);
      const frame = frames[index % frames.length];
      state = {
        ...frame,
        currentTime: frame.currentTime + completedLoops * 20000
      };
      index++;
      try {
        window.onCarStateUpdate();
      } catch (e) {
        console.error("[dev-shim]", e);
      }
    }
  }, STEP_MS);
})();`;
}

function buildDevOptions(appDir) {
  const shimContent = buildShimContent();
  const indexPath = path.join(appDir, "index.html");
  const originalHtml = fs.readFileSync(indexPath, "utf8");
  const htmlSnippet = '<script src="/__dev-shim.js"></script>';

  // Inject shim into HTML: try head end, body end, then prepend.
  let injectedHtml;
  const headIndex = originalHtml.indexOf("</head>");
  if (headIndex !== -1) {
    injectedHtml = originalHtml.slice(0, headIndex) + htmlSnippet + originalHtml.slice(headIndex);
  } else {
    const bodyIndex = originalHtml.indexOf("</body>");
    if (bodyIndex !== -1) {
      injectedHtml = originalHtml.slice(0, bodyIndex) + htmlSnippet + originalHtml.slice(bodyIndex);
    } else {
      injectedHtml = htmlSnippet + originalHtml;
    }
  }

  const virtualFiles = {
    "/__dev-shim.js": shimContent,
    "/": injectedHtml,
    "/index.html": injectedHtml,
  };

  return { virtualFiles, htmlSnippet };
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length !== 1) {
    console.error("usage: node dev.js <app-dir>");
    process.exit(EXIT.INFRA);
  }

  const appDir = path.resolve(args[0]);
  if (!fs.existsSync(path.join(appDir, "index.html"))) {
    console.error(`infra: no index.html in ${appDir}`);
    process.exit(EXIT.INFRA);
  }

  const { virtualFiles } = buildDevOptions(appDir);

  const { port } = await startServer(appDir, { virtualFiles });
  console.log(`Server running at http://127.0.0.1:${port}/`);
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err);
    process.exit(EXIT.INFRA);
  });
}

module.exports = {
  buildShimContent,
  buildDevOptions,
};
