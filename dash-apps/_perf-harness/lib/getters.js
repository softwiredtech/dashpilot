"use strict";

// Mirrors CarStateBridge.kt @JavascriptInterface methods.
// Drift is caught by the GETTERS-matches-Kotlin test in scenario.test.js.
const GETTERS = {
  getEgoSteeringAngle: { key: "egoSteeringAngle", default: 0 },
  getEgoSpeed: { key: "egoSpeed", default: 0 },
  getLeftBlinker: { key: "leftBlinker", default: 0 },
  getRightBlinker: { key: "rightBlinker", default: 0 },
  getGear: { key: "gear", default: 0 },
  isAdasOn: { key: "adasOn", default: false },
  getLeftBlindSpot: { key: "leftBlindSpot", default: 0 },
  getRightBlindSpot: { key: "rightBlindSpot", default: 0 },
  getFusedSpeedLimit: { key: "fusedSpeedLimit", default: 0 },
  getStopLineDist: { key: "stopLineDist", default: 0 },
  getTrafficLightColor: { key: "trafficLightColor", default: 0 },
  getBuckleStatus: { key: "buckleStatus", default: 0 },
  getAnyDoorOpen: { key: "anyDoorOpen", default: 0 },
  getLaneDepartureWarning: { key: "laneDepartureWarning", default: 0 },
  getAccSetSpeed: { key: "accSetSpeed", default: 0 },
  getFullPackEnergy: { key: "fullPackEnergy", default: 0 },
  getNominalEnergyRemaining: { key: "nominalEnergyRemaining", default: 0 },
  getEnergyBuffer: { key: "energyBuffer", default: 0 },
  getMaxRegenPower: { key: "maxRegenPower", default: 0 },
  getMaxDischargePower: { key: "maxDischargePower", default: 0 },
  getPackVoltage: { key: "packVoltage", default: 0 },
  getPackCurrent: { key: "packCurrent", default: 0 },
  getPackTMin: { key: "packTMin", default: 0 },
  getPackTMax: { key: "packTMax", default: 0 },
  getOdometer: { key: "odometer", default: 0 },
  isExperimentalMode: { key: "experimentalMode", default: false },
  isMadsActive: { key: "madsActive", default: false },
  getPhoneBattery: { key: "phoneBattery", default: -1 },
  getCurrentTime: { key: "currentTime", default: 0 },
  getShowPhoneBattery: { key: "showPhoneBattery", default: true },
  getShowCarBattery: { key: "showCarBattery", default: true },
  getShowOdometer: { key: "showOdometer", default: true },
  isImperial: { key: "useImperial", default: false },
  isDarkMode: { key: "darkMode", default: false },
  isAlwaysOnBlindSpotMonitor: { key: "alwaysOnBlindSpotMonitor", default: true },
  getRenderQuality: { key: "renderQuality", default: 3 },
  getDarkModeBackgroundGray: { key: "darkModeBackgroundGray", default: 0 },
  isChangingLane: { key: "changingLane", default: false },
  getSpeedCameraDistance: { key: "speedCameraDistance", default: -1 },
};

function getterMap() {
  const map = {};
  for (const [methodName, getter] of Object.entries(GETTERS)) {
    map[methodName] = getter.key;
  }
  return map;
}

module.exports = { GETTERS, getterMap };
