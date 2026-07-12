"use strict";

const fs = require("node:fs");
const path = require("node:path");

// Gate membership is declared in each app's manifest.json "perf" field.
// Absent manifest or absent perf field means gate: "standard" — new themes
// are benchmarked by default the moment they are registered.
function readPerfConfig(appDir, name) {
  const manifestPath = path.join(appDir, "manifest.json");
  let manifest = {};
  if (fs.existsSync(manifestPath)) {
    manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
  }
  const perf = manifest.perf || {};
  const gate = perf.gate || "standard";
  if (gate === "exempt" && (typeof perf.reason !== "string" || perf.reason.length < 10)) {
    throw new Error(`${name}: perf.gate "exempt" requires a perf.reason explaining why`);
  }
  return { gate, reason: perf.reason || null, root: perf.root || "." };
}

function selectTargets({ changedFiles, appsRoot }) {
  const harnessChanged = changedFiles.some((file) => file.startsWith("dash-apps/_perf-harness/"));

  let appNames;
  if (harnessChanged) {
    // A harness or budget change must prove every gated app still passes.
    appNames = fs
      .readdirSync(appsRoot)
      .filter((name) => name.startsWith("web-") && fs.statSync(path.join(appsRoot, name)).isDirectory());
  } else {
    appNames = [
      ...new Set(
        changedFiles
          .map((file) => (file.match(/^dash-apps\/(web-[^/]+)\//) || [])[1])
          .filter(Boolean)
      ),
    ];
  }

  const gated = [];
  const skipped = [];
  for (const name of appNames.sort()) {
    const appDir = path.join(appsRoot, name);
    if (!fs.existsSync(appDir)) continue;
    const config = readPerfConfig(appDir, name);
    if (config.gate === "exempt") skipped.push({ name, reason: config.reason });
    else gated.push({ name, root: config.root });
  }
  return { harnessChanged, gated, skipped };
}

module.exports = { selectTargets, readPerfConfig };

// CLI: changed file list on stdin (one per line, repo-relative), JSON out.
if (require.main === module) {
  const chunks = [];
  process.stdin.on("data", (chunk) => chunks.push(chunk));
  process.stdin.on("end", () => {
    try {
      const changedFiles = Buffer.concat(chunks)
        .toString("utf8")
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      const appsRoot = path.resolve(__dirname, "..", "..");
      process.stdout.write(JSON.stringify(selectTargets({ changedFiles, appsRoot }), null, 2) + "\n");
    } catch (error) {
      console.error(`targets error: ${error.message}`);
      process.exit(3);
    }
  });
}
