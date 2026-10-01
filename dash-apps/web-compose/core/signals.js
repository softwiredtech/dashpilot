// Signal catalog: everything a widget can display, with enough metadata for a
// generic widget to render it sensibly (unit, range, precision).
//
// Widgets never read `raw` directly; they bind to a signal id. Adding a signal
// here makes it available to every widget whose `binds[].kinds` accepts its kind.
//
// Units follow the host convention used by the other dash-apps: the host sends
// speed already in the display unit, and converts odometer / temperatures /
// speed limit before they reach the WebView. So nothing is converted here, only
// labelled.
//
// `bus` is informational for now: "vehicle" needs the vehicle bus (DashKit),
// "openpilot" needs a comma device. The editor will use it to grey out signals
// the current data source can't provide.
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  const GEARS = { 1: "P", 2: "R", 3: "N", 4: "D" };

  function num(value) {
    const n = Number(value);
    return value === undefined || value === null || !Number.isFinite(n) ? undefined : n;
  }

  function flag(value) {
    if (value === undefined || value === null) return undefined;
    return Number(value) > 0 || value === true;
  }

  const byImperial = (metric, imperial) => (_raw, imp) => (imp ? imperial : metric);

  const UNIT_LABELS = {
    speed: (imp) => (imp ? "mph" : "km/h"),
    dist: (imp) => (imp ? "mi" : "km"),
    temp: (imp) => (imp ? "°F" : "°C"),
    kwh: () => "kWh",
    kw: () => "kW",
    volt: () => "V",
    amp: () => "A",
    percent: () => "%",
    deg: () => "°",
    meter: () => "m",
    none: () => "",
  };

  const SPEED_RANGE = byImperial([0, 220], [0, 140]);

  const SIGNALS = [
    // speed & driving
    { id: "egoSpeed", kind: "number", unit: "speed", precision: 0, range: SPEED_RANGE, category: "drive", bus: "any",
      read: (r) => num(r.egoSpeed) },
    { id: "accSetSpeed", kind: "number", unit: "speed", precision: 0, range: SPEED_RANGE, category: "drive", bus: "any",
      read: (r) => num(r.accSetSpeed) },
    { id: "fusedSpeedLimit", kind: "number", unit: "speed", precision: 0, range: SPEED_RANGE, category: "drive", bus: "any",
      read: (r) => { const v = num(r.fusedSpeedLimit); return v > 0 ? v : undefined; } },
    { id: "gear", kind: "enum", unit: "none", category: "drive", bus: "any",
      read: (r) => GEARS[Math.round(Number(r.gear))] },
    { id: "egoSteeringAngle", kind: "number", unit: "deg", precision: 0, range: () => [-180, 180], category: "drive", bus: "any",
      read: (r) => num(r.egoSteeringAngle) },

    // battery (vehicle bus)
    { id: "batteryPercent", kind: "number", unit: "percent", precision: 0, range: () => [0, 100], category: "battery", bus: "vehicle",
      read: (r) => {
        const full = num(r.fullPackEnergy), nominal = num(r.nominalEnergyRemaining), buffer = num(r.energyBuffer) || 0;
        if (!full || nominal === undefined || full <= buffer) return undefined;
        return Math.min(100, Math.max(0, ((nominal - buffer) / (full - buffer)) * 100));
      } },
    { id: "batteryEnergy", kind: "number", unit: "kwh", precision: 1, category: "battery", bus: "vehicle",
      range: (r) => [0, num(r.fullPackEnergy) || 100],
      read: (r) => {
        const nominal = num(r.nominalEnergyRemaining);
        if (!num(r.fullPackEnergy) || nominal === undefined) return undefined;
        return Math.max(0, nominal - (num(r.energyBuffer) || 0));
      } },
    { id: "packPower", kind: "number", unit: "kw", precision: 0, category: "battery", bus: "vehicle",
      range: (r) => [-(num(r.maxRegenPower) || 100), num(r.maxDischargePower) || 300],
      read: (r) => {
        const v = num(r.packVoltage), a = num(r.packCurrent);
        return v && a !== undefined ? (v * a) / 1000 : undefined;
      } },
    { id: "powerLevel", kind: "number", unit: "percent", precision: 0, range: () => [-100, 100], category: "battery", bus: "vehicle",
      read: (r) => (num(r.maxDischargePower) && num(r.maxRegenPower) ? num(r.powerLevel) : undefined) },
    { id: "packVoltage", kind: "number", unit: "volt", precision: 0, range: () => [250, 420], category: "battery", bus: "vehicle",
      read: (r) => num(r.packVoltage) || undefined },
    { id: "packCurrent", kind: "number", unit: "amp", precision: 0, range: () => [-400, 1200], category: "battery", bus: "vehicle",
      read: (r) => (num(r.packVoltage) ? num(r.packCurrent) : undefined) },
    { id: "packTMin", kind: "number", unit: "temp", precision: 0, range: byImperial([-20, 60], [-4, 140]), category: "battery", bus: "vehicle",
      read: (r) => (num(r.fullPackEnergy) ? num(r.packTMin) : undefined) },
    { id: "packTMax", kind: "number", unit: "temp", precision: 0, range: byImperial([-20, 60], [-4, 140]), category: "battery", bus: "vehicle",
      read: (r) => (num(r.fullPackEnergy) ? num(r.packTMax) : undefined) },
    { id: "maxRegenPower", kind: "number", unit: "kw", precision: 0, range: () => [0, 150], category: "battery", bus: "vehicle",
      read: (r) => num(r.maxRegenPower) || undefined },
    { id: "maxDischargePower", kind: "number", unit: "kw", precision: 0, range: () => [0, 400], category: "battery", bus: "vehicle",
      read: (r) => num(r.maxDischargePower) || undefined },
    { id: "odometer", kind: "number", unit: "dist", precision: 0, category: "vehicle", bus: "vehicle",
      read: (r) => num(r.odometer) || undefined },

    // surroundings
    { id: "stopLineDist", kind: "number", unit: "meter", precision: 0, range: () => [0, 200], category: "assist", bus: "any",
      read: (r) => { const v = num(r.stopLineDist); return v > 0 ? v : undefined; } },

    // phone & time
    { id: "phoneBattery", kind: "number", unit: "percent", precision: 0, range: () => [0, 100], category: "phone", bus: "any",
      read: (r) => { const v = num(r.phoneBattery); return v >= 0 ? v : undefined; } },
    { id: "clock", kind: "string", unit: "none", category: "phone", bus: "any",
      read: (r) => formatClock(num(r.currentTime) || Date.now()) },

    // assist & warnings (booleans)
    { id: "adasOn", kind: "bool", unit: "none", category: "assist", bus: "any", read: (r) => flag(r.adasOn) },
    { id: "selfdriveActive", kind: "bool", unit: "none", category: "assist", bus: "openpilot", read: (r) => flag(r.selfdriveActive) },
    { id: "experimentalMode", kind: "bool", unit: "none", category: "assist", bus: "openpilot", read: (r) => flag(r.experimentalMode) },
    { id: "madsActive", kind: "bool", unit: "none", category: "assist", bus: "openpilot", read: (r) => flag(r.madsActive) },
    { id: "changingLane", kind: "bool", unit: "none", category: "assist", bus: "any", read: (r) => flag(r.changingLane) },
    { id: "leftBlinker", kind: "bool", unit: "none", category: "signals", bus: "any", read: (r) => flag(r.leftBlinker) },
    { id: "rightBlinker", kind: "bool", unit: "none", category: "signals", bus: "any", read: (r) => flag(r.rightBlinker) },
    { id: "leftBlindSpot", kind: "bool", unit: "none", category: "signals", bus: "any", read: (r) => flag(r.leftBlindSpot) },
    { id: "rightBlindSpot", kind: "bool", unit: "none", category: "signals", bus: "any", read: (r) => flag(r.rightBlindSpot) },
    { id: "laneDepartureWarning", kind: "bool", unit: "none", category: "warnings", bus: "any", read: (r) => flag(r.laneDepartureWarning) },
    { id: "sideCollisionWarning", kind: "bool", unit: "none", category: "warnings", bus: "any", read: (r) => flag(r.sideCollisionWarning) },
    { id: "anyDoorOpen", kind: "bool", unit: "none", category: "warnings", bus: "any", read: (r) => flag(r.anyDoorOpen) },
    { id: "seatbeltUnbuckled", kind: "bool", unit: "none", category: "warnings", bus: "any",
      read: (r) => (r.buckleStatus === undefined ? undefined : Number(r.buckleStatus) === 0) },
  ];

  const byId = new Map(SIGNALS.map((s) => [s.id, s]));

  // Formatter caches: Intl constructors are too slow to build per tick.
  const numberFormats = new Map();
  let clockFormat = null, clockLocale = "", clockMinute = -1, clockText = "";

  function numberFormat(precision) {
    const key = DC.i18n.locale() + "|" + precision;
    let f = numberFormats.get(key);
    if (!f) {
      f = new Intl.NumberFormat(DC.i18n.locale(), { minimumFractionDigits: precision, maximumFractionDigits: precision });
      numberFormats.set(key, f);
    }
    return f;
  }

  function formatClock(ms) {
    const minute = Math.floor(ms / 60000);
    const locale = DC.i18n.locale();
    if (minute === clockMinute && locale === clockLocale) return clockText;
    if (!clockFormat || locale !== clockLocale) {
      clockFormat = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" });
      clockLocale = locale;
    }
    clockMinute = minute;
    clockText = clockFormat.format(new Date(ms));
    return clockText;
  }

  const MISSING = "—";

  // value -> display string. `precision` overrides the signal default (widget prop).
  function format(signal, value, precision) {
    if (value === undefined) return MISSING;
    switch (signal.kind) {
      case "number": return numberFormat(precision ?? signal.precision ?? 0).format(value);
      case "enum": {
        const key = signal.id + "." + value;
        const text = DC.t(key);
        return text === key ? String(value) : text;
      }
      case "bool": return value ? "●" : "○";
      default: return String(value);
    }
  }

  function unitLabel(signal, imperial) {
    return (UNIT_LABELS[signal.unit] || UNIT_LABELS.none)(imperial);
  }

  function range(signal, raw, imperial) {
    return signal.range ? signal.range(raw, imperial) : null;
  }

  DC.signals = {
    all: SIGNALS,
    get: (id) => byId.get(id),
    format,
    unitLabel,
    range,
    label: (signal) => DC.t("signal." + signal.id),
    MISSING,
  };
})();
