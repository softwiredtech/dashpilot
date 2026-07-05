(function () {
  const scaleProfiles = window.DashPilotAnalogShell.SPEED_SCALE_PROFILES;
  const GEAR_MAP = {
    1: "P",
    2: "R",
    3: "N",
    4: "D",
  };

  function numberOr(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function normalizeUnit(raw) {
    if (raw?.unit) return String(raw.unit).toUpperCase();
    if (raw?.speedUnit) return String(raw.speedUnit).toUpperCase();
    if (typeof raw?.isImperial === "boolean") return raw.isImperial ? "MPH" : "KPH";
    return "MPH";
  }

  function normalizeGear(raw) {
    if (raw?.gearLabel) return String(raw.gearLabel);
    if (typeof raw?.gear === "string") return raw.gear;
    const numericGear = Math.round(Number(raw?.gear));
    return GEAR_MAP[numericGear] || "D";
  }

  function scaleForUnit(unit) {
    const profile = scaleProfiles[unit === "KPH" ? "KPH" : "MPH"];
    return {
      min: profile.defaultMin,
      max: profile.defaultMax,
      majorStep: profile.majorStep,
      minorStep: profile.minorStep,
    };
  }

  function normalizeModeState(raw) {
    const direct = String(raw?.state || raw?.mode || "").toLowerCase();
    if (direct === "off" || direct === "mads" || direct === "full") return direct;
    if (raw?.fullAdas || raw?.adasEnabled || raw?.adasOn) return "full";
    if (raw?.madsEnabled || raw?.madsActive) return "mads";
    return "off";
  }

  function normalizeBlinker(raw) {
    const n = Math.round(Number(raw));
    return n === 1 || n === 2 ? n : 0;
  }

  function normalizeBlindSpot(raw) {
    return Number(raw) > 0;
  }

  function normalizeTheme(raw) {
    const explicit = String(raw?.theme || "").toLowerCase();
    if (explicit === "classic-911-light") return "classic-911-light";
    if (explicit === "classic-911") return "classic-911";
    if (typeof raw?.darkMode === "boolean") return raw.darkMode ? "classic-911" : "classic-911-light";
    return "classic-911";
  }

  function carStateToDashboardModel(raw = {}) {
    const unit = normalizeUnit(raw);
    const scale = scaleForUnit(unit);
    return {
      speed: {
        value: numberOr(raw.speed ?? raw.egoSpeed, 0),
        unit,
        gear: normalizeGear(raw),
        min: scale.min,
        max: scale.max,
        majorStep: scale.majorStep,
        minorStep: scale.minorStep,
        accSetSpeed: numberOr(raw.accSetSpeed, 0),
        speedLimit: numberOr(raw.speedLimit ?? raw.fusedSpeedLimit, 0),
        modeState: normalizeModeState(raw),
        leftBlinker: normalizeBlinker(raw.leftBlinker),
        rightBlinker: normalizeBlinker(raw.rightBlinker),
      },
      display: {
        theme: normalizeTheme(raw),
      },
      signals: {
        leftBlindSpot: normalizeBlindSpot(raw.leftBlindSpot),
        rightBlindSpot: normalizeBlindSpot(raw.rightBlindSpot),
      },
    };
  }

  window.carStateToAnalogDashboardModel = carStateToDashboardModel;
})();
