"use strict";
const fs = require("node:fs");
const path = require("node:path");

const APPS_DIR = path.join(__dirname, "..", "dash-apps");

const ROOT_KEYS = new Set(["manifestVersion", "id", "settings"]);
const MANIFEST_VERSION = 1;
const ID_RE = /^[a-z0-9-]+$/;
const VALID_SETTINGS = new Set([
  "showPhoneBattery",
  "showCarBattery",
  "showOdometer",
  "alwaysOnBlindSpotMonitor",
  "useImperial",
  "darkMode",
  "renderQuality",
  "darkModeBackgroundGray",
]);

const dirs = fs.readdirSync(APPS_DIR).filter((name) => name.startsWith("web-"));
if (dirs.length === 0) {
  console.error("No dash-apps found in dash-apps/web-*/");
  process.exit(1);
}

const errors = [];
for (const folder of dirs) {
  const manifestPath = path.join(APPS_DIR, folder, "manifest.json");
  if (!fs.existsSync(manifestPath)) {
    errors.push(`${folder}: missing manifest.json`);
    continue;
  }

  const m = JSON.parse(fs.readFileSync(manifestPath, "utf8"));

  for (const key of Object.keys(m)) {
    if (!ROOT_KEYS.has(key)) errors.push(`${folder}: unknown property "${key}"`);
  }
  if (m.manifestVersion !== MANIFEST_VERSION) {
    errors.push(`${folder}: manifestVersion must be ${MANIFEST_VERSION}`);
  }
  if (typeof m.id !== "string" || !ID_RE.test(m.id)) {
    errors.push(`${folder}: id must match ${ID_RE}`);
  }
  if (!Array.isArray(m.settings)) {
    errors.push(`${folder}: settings must be an array`);
  } else {
    const seen = new Set();
    for (const s of m.settings) {
      if (!VALID_SETTINGS.has(s)) errors.push(`${folder}/settings: "${s}" is not a valid setting`);
      if (seen.has(s)) errors.push(`${folder}/settings: "${s}" is duplicated`);
      seen.add(s);
    }
  }

  if (m.id !== undefined && typeof m.id === "string") {
    const expectedFolder = `web-${m.id}`;
    if (folder !== expectedFolder) {
      errors.push(`${folder}: folder name does not match id "${m.id}" (expected "${expectedFolder}")`);
    }
  }
}

if (errors.length > 0) {
  console.error(`Manifest validation failed:\n${errors.map((e) => `  - ${e}`).join("\n")}`);
  process.exit(1);
}

console.log(`${dirs.length} dash-app manifests valid`);
