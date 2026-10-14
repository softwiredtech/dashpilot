// Browser-only simulated drive for development: open index.html?demo.
// Options: &lang=hu|de|en, &imperial, &light, &parked, &source=comma|dashkit.
// Inert unless ?demo is present, and never runs next to a real host.
(function () {
  const params = new URLSearchParams(window.location.search);
  if (!params.has("demo") || window.NativeCarState || (window.webkit && window.webkit.messageHandlers)) return;

  const STEP_MS = 40;
  const FULL = 78, BUFFER = 3;
  const start = Date.now();
  let energy = 58, odometer = 48213.4, lastSpeed = 0;

  function frame() {
    const t = (Date.now() - start) / 1000;
    const speed = params.has("parked") ? 0 : Math.max(0, 65 - 60 * Math.cos((2 * Math.PI * t) / 24));
    const accel = (speed - lastSpeed) / (STEP_MS / 1000);
    lastSpeed = speed;
    const current = accel >= 0 ? 40 + accel * 18 + speed * 0.9 : accel * 22;
    energy -= Math.max(0, current) * 0.00000008 * 380;
    odometer += (speed / 3600) * (STEP_MS / 1000);
    const blinkPhase = t % 20;

    window.receiveMessage({
      egoSpeed: speed,
      accSetSpeed: t > 6 ? 110 : 0,
      fusedSpeedLimit: t % 30 < 15 ? 90 : 130,
      gear: 4,
      adasOn: t > 6,
      egoSteeringAngle: Math.sin(t * 0.5) * 45,
      leftBlinker: blinkPhase > 4 && blinkPhase < 7 ? 1 : 0,
      rightBlinker: blinkPhase > 12 && blinkPhase < 15 ? 1 : 0,
      leftBlindSpot: blinkPhase > 4.5 && blinkPhase < 6 ? 1 : 0,
      rightBlindSpot: 0,
      fullPackEnergy: FULL,
      nominalEnergyRemaining: energy,
      energyBuffer: BUFFER,
      maxRegenPower: 70,
      maxDischargePower: 300,
      packVoltage: 380,
      packCurrent: current,
      powerLevel: Math.max(-1, Math.min(1, (380 * current) / 1000 / (current < 0 ? 70 : 300))) * 100,
      packTMin: 22,
      packTMax: 26 + Math.sin(t / 30) * 2,
      odometer,
      buckleStatus: 1,
      phoneBattery: 81,
      currentTime: Date.now(),
      dataSourceType: params.get("source") || "dashkit",
      isImperial: params.has("imperial"),
      darkMode: !params.has("light"),
      locale: params.get("lang") || undefined,
    });
  }

  setInterval(frame, STEP_MS);
  frame();
})();
