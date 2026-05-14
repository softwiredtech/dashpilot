(function () {
  let mounted = false;
  let pendingCarState = null;
  let pendingNativeCarState = false;
  let dashboardRaf = 0;
  let gauges = null;

  function queryGauges() {
    return {
      speed: document.querySelector("analog-speed-gauge"),
      root: document.querySelector("[data-dashboard-root]"),
    };
  }

  function applyModel(model) {
    if (!gauges) gauges = queryGauges();
    gauges.speed?.update(model.speed);
    if (gauges.root && model.signals) {
      const nextLeft = model.signals.leftBlindSpot ? "active" : "idle";
      const nextRight = model.signals.rightBlindSpot ? "active" : "idle";
      if (gauges.root.dataset.bsmLeft !== nextLeft) gauges.root.dataset.bsmLeft = nextLeft;
      if (gauges.root.dataset.bsmRight !== nextRight) gauges.root.dataset.bsmRight = nextRight;
    }
  }

  function flushPendingCarState() {
    dashboardRaf = 0;
    if (typeof window.carStateToAnalogDashboardModel !== "function") {
      console.error("[web-analog] dashboard-model.js must load before dashboard-runtime.js");
      return;
    }
    const useNativeCarState = pendingNativeCarState;
    pendingNativeCarState = false;
    const carState = useNativeCarState ? readNativeCarState() : pendingCarState || {};
    const model = window.carStateToAnalogDashboardModel(carState);
    if (model) applyModel(model);
  }

  function receiveMessage(carState) {
    pendingNativeCarState = false;
    pendingCarState = carState;
    if (dashboardRaf) return;
    dashboardRaf = requestAnimationFrame(flushPendingCarState);
  }

  function receiveNativeCarState() {
    pendingNativeCarState = true;
    pendingCarState = null;
    if (dashboardRaf) return;
    dashboardRaf = requestAnimationFrame(flushPendingCarState);
  }

  function readNative(method, fallback) {
    try {
      if (!window.NativeCarState) return fallback;
      const fn = window.NativeCarState[method];
      return typeof fn === "function" ? fn.call(window.NativeCarState) : fallback;
    } catch (_error) {
      return fallback;
    }
  }

  function readNativeCarState() {
    return {
      egoSpeed: readNative("getEgoSpeed", 0),
      gear: readNative("getGear", 4),
      adasOn: readNative("isAdasOn", false),
      madsActive: readNative("isMadsActive", false),
      accSetSpeed: readNative("getAccSetSpeed", 0),
      fusedSpeedLimit: readNative("getFusedSpeedLimit", 0),
      isImperial: readNative("isImperial", true),
      leftBlinker: readNative("getLeftBlinker", 0),
      rightBlinker: readNative("getRightBlinker", 0),
      leftBlindSpot: readNative("getLeftBlindSpot", 0),
      rightBlindSpot: readNative("getRightBlindSpot", 0),
    };
  }

  function mount() {
    if (mounted) return;
    mounted = true;
    gauges = queryGauges();

    if (window.NativeCarState) {
      receiveNativeCarState();
    }
  }

  document.addEventListener("DOMContentLoaded", mount, { once: true });

  window.DashPilotAnalogDashboard = {
    mount,
    applyModel,
  };

  window.onCarStateUpdate = function () {
    receiveNativeCarState();
  };
  window.setAnalogDashboardData = applyModel;
  window.receiveMessage = receiveMessage;
})();
