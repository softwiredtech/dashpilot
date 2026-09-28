// Host adapter: the only file that knows which platform it runs on.
//
// Android pushes nothing: it updates window.NativeCarState (@JavascriptInterface
// getters) and pings window.onCarStateUpdate(). iOS pushes one JSON object per
// tick through window.receiveMessage(json). Both end up as the same flat `raw`
// object, keyed by the CarState field names.
//
// Layout persistence and locale are optional host capabilities. Until a native
// side implements them, the app falls back to localStorage / navigator.language.
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  // CarState field -> Android getter. Anything missing on a platform reads as undefined.
  const NATIVE_GETTERS = {
    egoSteeringAngle: "getEgoSteeringAngle",
    egoSpeed: "getEgoSpeed",
    leftBlinker: "getLeftBlinker",
    rightBlinker: "getRightBlinker",
    gear: "getGear",
    adasOn: "isAdasOn",
    leftBlindSpot: "getLeftBlindSpot",
    rightBlindSpot: "getRightBlindSpot",
    fusedSpeedLimit: "getFusedSpeedLimit",
    stopLineDist: "getStopLineDist",
    trafficLightColor: "getTrafficLightColor",
    laneDepartureWarning: "getLaneDepartureWarning",
    sideCollisionWarning: "getSideCollisionWarning",
    anyDoorOpen: "getAnyDoorOpen",
    buckleStatus: "getBuckleStatus",
    accSetSpeed: "getAccSetSpeed",
    fullPackEnergy: "getFullPackEnergy",
    nominalEnergyRemaining: "getNominalEnergyRemaining",
    energyBuffer: "getEnergyBuffer",
    maxRegenPower: "getMaxRegenPower",
    maxDischargePower: "getMaxDischargePower",
    packVoltage: "getPackVoltage",
    packCurrent: "getPackCurrent",
    packTMin: "getPackTMin",
    packTMax: "getPackTMax",
    odometer: "getOdometer",
    selfdriveActive: "isSelfdriveActive",
    experimentalMode: "isExperimentalMode",
    madsActive: "isMadsActive",
    changingLane: "isChangingLane",
    phoneBattery: "getPhoneBattery",
    currentTime: "getCurrentTime",
    dataSourceType: "getDataSourceType",
    speedCameraDistance: "getSpeedCameraDistance",
    useImperial: "isImperial",
    darkMode: "isDarkMode",
  };
  const NATIVE_KEYS = Object.keys(NATIVE_GETTERS);

  const LAYOUT_STORAGE_KEY = "dashcompose.layout";

  // Fills `out` in place so the runtime can reuse one object per tick.
  function readNative(out) {
    for (let i = 0; i < NATIVE_KEYS.length; i++) {
      const key = NATIVE_KEYS[i];
      out[key] = nativeCall(NATIVE_GETTERS[key]);
    }
    return out;
  }

  // iOS names the imperial flag after the Android getter (isImperial); that
  // wire name wins whenever it is present. Everything else matches CarState.
  function readPushed(message, out) {
    for (const key in message) out[key] = message[key];
    if (message.isImperial !== undefined) out.useImperial = message.isImperial;
    return out;
  }

  function hasNative() {
    return typeof window.NativeCarState === "object" && window.NativeCarState !== null;
  }

  function hasNativeMethod(method) {
    return hasNative() && typeof window.NativeCarState[method] === "function";
  }

  // The Java bridge matches methods by argument count, so a getter gets no argument at all.
  function nativeCall(method, arg) {
    if (!hasNativeMethod(method)) return undefined;
    try {
      return arg === undefined ? window.NativeCarState[method]() : window.NativeCarState[method](arg);
    } catch (_error) {
      return undefined;
    }
  }

  // Sends `value` to whichever host is present; false when neither implements it.
  function toHost(androidMethod, iosHandlerName, value) {
    if (hasNativeMethod(androidMethod)) {
      nativeCall(androidMethod, value);
      return true;
    }
    const handlers = window.webkit && window.webkit.messageHandlers;
    const ios = handlers && handlers[iosHandlerName];
    if (!ios) return false;
    ios.postMessage(value);
    return true;
  }

  function storageGet(key) {
    try {
      return window.localStorage.getItem(key);
    } catch (_error) {
      return null;
    }
  }

  function storageSet(key, value) {
    try {
      window.localStorage.setItem(key, value);
    } catch (_error) {
      // Private mode / disabled storage: layout just won't persist.
    }
  }

  // Returns the saved layout document as a string, or null.
  // Android: NativeCarState.getComposeLayout(). iOS: injected before load as
  // window.__DASHPILOT_COMPOSE_LAYOUT__. Browser: localStorage.
  function loadLayout() {
    const fromAndroid = nativeCall("getComposeLayout");
    if (typeof fromAndroid === "string" && fromAndroid) return fromAndroid;
    const fromIos = window.__DASHPILOT_COMPOSE_LAYOUT__;
    if (typeof fromIos === "string" && fromIos) return fromIos;
    return storageGet(LAYOUT_STORAGE_KEY);
  }

  function saveLayout(json) {
    if (!toHost("saveComposeLayout", "composeLayout", json)) storageSet(LAYOUT_STORAGE_KEY, json);
  }

  // Tells the host the editor is open, so it can suspend its own gestures —
  // the native dashboard carousel reads a horizontal drag as "next dashboard",
  // which would otherwise fire while a widget is being dragged or resized.
  function setEditing(editing) {
    toHost("setComposeEditing", "composeEditing", !!editing);
  }

  // The app language can differ from the system one (per-app locale on Android,
  // in-app language on iOS), so the host's answer wins over navigator.language.
  function locale(raw) {
    const fromNative = nativeCall("getLocale");
    if (typeof fromNative === "string" && fromNative) return fromNative;
    if (raw && typeof raw.locale === "string" && raw.locale) return raw.locale;
    return (navigator.language || "en").toString();
  }

  DC.host = { readNative, readPushed, hasNative, loadLayout, saveLayout, setEditing, locale };
})();
