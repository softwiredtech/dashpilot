"use strict";

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const { GETTERS, getterMap } = require("../lib/getters");
const { STEP_MS, buildScenario, defaultState } = require("../lib/scenario");

const BRIDGE_PATH = path.join(__dirname, "..", "..", "..", "dashpilot-android", "app", "src", "main", "java", "com", "softwiredtech", "dashpilot", "js", "CarStateBridge.kt");

test("exposes exactly the 39 CarStateBridge getters", () => {
  assert.deepStrictEqual(Object.keys(GETTERS), [
    "getEgoSteeringAngle",
    "getEgoSpeed",
    "getLeftBlinker",
    "getRightBlinker",
    "getGear",
    "isAdasOn",
    "getLeftBlindSpot",
    "getRightBlindSpot",
    "getFusedSpeedLimit",
    "getStopLineDist",
    "getTrafficLightColor",
    "getBuckleStatus",
    "getAnyDoorOpen",
    "getLaneDepartureWarning",
    "getAccSetSpeed",
    "getFullPackEnergy",
    "getNominalEnergyRemaining",
    "getEnergyBuffer",
    "getMaxRegenPower",
    "getMaxDischargePower",
    "getPackVoltage",
    "getPackCurrent",
    "getPackTMin",
    "getPackTMax",
    "getOdometer",
    "isExperimentalMode",
    "isMadsActive",
    "getPhoneBattery",
    "getCurrentTime",
    "getShowPhoneBattery",
    "getShowCarBattery",
    "getShowOdometer",
    "isImperial",
    "isDarkMode",
    "isAlwaysOnBlindSpotMonitor",
    "getRenderQuality",
    "getDarkModeBackgroundGray",
    "isChangingLane",
    "getSpeedCameraDistance",
  ]);
  assert.strictEqual(Object.keys(GETTERS).length, 39);
  assert.strictEqual(GETTERS.isImperial.key, "useImperial");
  assert.strictEqual(GETTERS.isAdasOn.key, "adasOn");
  assert.strictEqual(GETTERS.getSpeedCameraDistance.default, -1);
  assert.strictEqual(GETTERS.getPhoneBattery.default, -1);
  assert.strictEqual(GETTERS.getRenderQuality.default, 3);
});

test("getterMap is plain method→key JSON", () => {
  const map = getterMap();

  assert.strictEqual(map.getEgoSpeed, "egoSpeed");
  assert.strictEqual(Object.keys(map).length, 39);
});

test("cadence is 25 Hz", () => {
  assert.strictEqual(STEP_MS, 40);
  assert.strictEqual(buildScenario(60000).length, 1500);
});

test("scenario is deterministic", () => {
  assert.deepStrictEqual(buildScenario(10000), buildScenario(10000));
});

test("every frame contains every getter-backed key and nothing undefined", () => {
  const keys = Object.values(GETTERS).map((getter) => getter.key);

  for (const frame of buildScenario(60000)) {
    for (const key of keys) {
      assert.notStrictEqual(frame[key], undefined, `missing ${key}`);
    }
  }
});

test("scenario exercises the animated signals", () => {
  const frames = buildScenario(60000);
  const speeds = frames.map((frame) => frame.egoSpeed);

  assert.ok(Math.max(...speeds) > 110, "speed sweep should approach 120");
  assert.ok(Math.min(...speeds) >= 0, "speed never negative");
  assert.ok(frames.some((frame) => frame.leftBlinker === 1) && frames.some((frame) => frame.leftBlinker === 0));
  assert.ok(frames.some((frame) => frame.leftBlindSpot === 1));
  assert.ok(frames.some((frame) => frame.fusedSpeedLimit === 50) && frames.some((frame) => frame.fusedSpeedLimit === 100));
  assert.ok(frames.some((frame) => frame.accSetSpeed > 0));
  assert.ok(frames.some((frame) => frame.speedCameraDistance > 0));
});

test("defaultState matches getter defaults", () => {
  const state = defaultState();

  assert.strictEqual(state.phoneBattery, -1);
  assert.strictEqual(state.adasOn, false);
});

test("GETTERS matches CarStateBridge.kt @JavascriptInterface methods", () => {
  assert.ok(fs.existsSync(BRIDGE_PATH), `CarStateBridge.kt not found at ${BRIDGE_PATH}`);

  const src = fs.readFileSync(BRIDGE_PATH, "utf8");
  const re = /@JavascriptInterface\s+fun\s+(\w+)\s*\(/g;
  const kotlinMethods = [];
  let match;
  while ((match = re.exec(src)) !== null) {
    kotlinMethods.push(match[1]);
  }

  const jsMethods = Object.keys(GETTERS).sort();
  kotlinMethods.sort();

  const onlyInKotlin = kotlinMethods.filter((m) => !jsMethods.includes(m));
  const onlyInJs = jsMethods.filter((m) => !kotlinMethods.includes(m));

  const lines = [];
  if (onlyInKotlin.length) {
    lines.push(`Missing from GETTERS: ${onlyInKotlin.join(", ")}`);
  }
  if (onlyInJs.length) {
    lines.push(`Not in CarStateBridge.kt: ${onlyInJs.join(", ")}`);
  }

  assert.ok(
    kotlinMethods.length === jsMethods.length,
    `Method count mismatch: ${kotlinMethods.length} in Kotlin, ${jsMethods.length} in GETTERS`
  );
  assert.ok(
    kotlinMethods.every((m, i) => m === jsMethods[i]),
    `Getter drift:\n${lines.join("\n")}`
  );
});
