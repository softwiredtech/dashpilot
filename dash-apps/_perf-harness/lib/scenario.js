"use strict";

const { GETTERS } = require("./getters");

const STEP_MS = 40;

function defaultState() {
  const state = {};
  for (const getter of Object.values(GETTERS)) {
    state[getter.key] = getter.default;
  }
  return state;
}

function frameAt(index) {
  const t = (index * STEP_MS) / 1000;
  const state = defaultState();

  state.egoSpeed = 60 * (1 - Math.cos((2 * Math.PI * t) / 40));
  state.egoSteeringAngle = 90 * Math.sin((2 * Math.PI * t) / 8);
  state.leftBlinker = t % 12 < 3 ? 1 : 0;
  state.rightBlinker = (t + 6) % 12 < 3 ? 1 : 0;
  state.adasOn = t >= 10;
  state.accSetSpeed = t >= 10 ? (Math.floor(t / 5) % 2 === 0 ? 90 : 110) : 0;
  state.leftBlindSpot = t % 14 < 1 ? 1 : 0;
  state.rightBlindSpot = (t + 7) % 14 < 1 ? 1 : 0;
  state.fusedSpeedLimit = t < 20 ? 50 : 100;
  state.trafficLightColor = Math.floor(t / 6) % 3;
  state.stopLineDist = Math.max(0, 100 - (t % 20) * 5);
  state.laneDepartureWarning = t % 25 < 0.5 ? 1 : 0;
  state.speedCameraDistance = t >= 30 && t < 40 ? Math.round(500 - (t - 30) * 50) : -1;
  state.gear = 4;
  state.buckleStatus = 1;
  state.currentTime = 1751700000000 + Math.round(t * 1000);
  state.phoneBattery = 80;
  state.odometer = 42000 + t * 0.02;
  state.fullPackEnergy = 75;
  state.nominalEnergyRemaining = 50 - t * 0.005;
  state.packVoltage = 380;
  state.packCurrent = -40 * Math.sin((2 * Math.PI * t) / 40);

  return state;
}

function buildScenario(durationMs) {
  const frames = [];
  for (let index = 0; index * STEP_MS < durationMs; index += 1) {
    frames.push(frameAt(index));
  }
  return frames;
}

module.exports = { STEP_MS, buildScenario, defaultState, frameAt };
