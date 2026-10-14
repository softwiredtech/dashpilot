"use strict";
const fs = require("node:fs");
const path = require("node:path");

const SCHEMA_PATH = path.join(__dirname, "..", "dash-apps", "manifest.schema.json");
const APPS_DIR = path.join(__dirname, "..", "dash-apps");

const ROOT_KEYS = new Set(["$schema", "manifestVersion", "id", "settings"]);

const schema = JSON.parse(fs.readFileSync(SCHEMA_PATH, "utf8"));
const validSettings = new Set(schema.properties.settings.items.enum);
const idRe = new RegExp(schema.properties.id.pattern);

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
  const expectedVersion = schema.properties.manifestVersion.const;
  if (!Number.isInteger(m.manifestVersion) || m.manifestVersion !== expectedVersion) {
    errors.push(`${folder}: manifestVersion must be ${expectedVersion}`);
  }
  if (typeof m.id !== "string" || !idRe.test(m.id)) {
    errors.push(`${folder}: id must match ${schema.properties.id.pattern}`);
  }
  if (!Array.isArray(m.settings)) {
    errors.push(`${folder}: settings must be an array`);
  } else {
    const seen = new Set();
    for (const s of m.settings) {
      if (!validSettings.has(s)) errors.push(`${folder}/settings: "${s}" is not a valid setting`);
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
