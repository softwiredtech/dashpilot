(function () {
  const geometry = window.DashPilotAnalogGeometry;
  const shell = window.DashPilotAnalogShell;

  function renderMissingDependency(message) {
    return `<div class="analog-gauge-shell"><svg class="analog-gauge-svg" viewBox="0 0 200 200" role="img" aria-label="Gauge unavailable"><text class="gauge-value gauge-value--small" x="100" y="102" text-anchor="middle">${message}</text></svg></div>`;
  }

  if (!geometry || !shell) {
    console.error("analog-speed-gauge requires scripts/analog-geometry.js and scripts/analog-shell.js before speed-gauge.js");

    class AnalogSpeedGaugeUnavailable extends HTMLElement {
      connectedCallback() {
        this.innerHTML = renderMissingDependency("SPEED UNAVAILABLE");
      }

      update() {
        this.innerHTML = renderMissingDependency("SPEED UNAVAILABLE");
      }
    }

    if (!customElements.get("analog-speed-gauge")) {
      customElements.define("analog-speed-gauge", AnalogSpeedGaugeUnavailable);
    }
    return;
  }

  const clamp = geometry.clamp;
  const fmt = geometry.fmt;
  const VIEWBOX = shell.VIEWBOX;
  const SURFACE = shell.SURFACE;
  const CLASSIC_911_SHELL = shell.CLASSIC_911_SHELL;
  const reliefOffsetForSurface = shell.reliefOffsetForSurface;

  const GAUGE = {
    ...VIEWBOX,
    startAngle: -120,
    sweepAngle: 240,
    maxTickCount: 200,
    glowStartRatio: 1.15,
    glowRampRatio: 0.1,
    textSafeRadius: 74.8,
    unitLabelWidth: 18,
    unitLabelY: -24,
    gearStrip: {
      y: 52,
      spacing: 16,
    },
    hub: { outer: 8.8 },
    radii: {
      minorTickInner: 82,
      minorTickOuter: 88,
      majorTickInner: 78,
      majorTickOuter: 89,
    },
  };

  const TRACKS = {
    accSet: { radius: 100 },
    speedLimit: {
      arcRadius: 62,
      markerRadius: 66.8,
      arcStrokeWidth: 2.8,
    },
    // Reserve the bottom-tail gap for mode state so it never collides with the compliance arc.
    mode: {
      cx: 100,
      cy: 100,
      arcRadius: 88.5,
      arcStrokeWidth: 4.8,
    },
  };

  const UNIT_ALIASES = {
    MI: "MPH",
    MPH: "MPH",
    KM: "KPH",
    KPH: "KPH",
    "KM/H": "KPH",
  };

  const SCALE_PROFILES = shell.SPEED_SCALE_PROFILES;

  const TEXT_METRICS = {
    numberCharHalfWidth: 3.35,
    numberHalfHeight: 5.6,
  };

  const TICK_RELIEF = Object.freeze({
    major: Object.freeze((() => {
      const relief = reliefOffsetForSurface(SURFACE.raised, 0.72);
      return {
        highlight: relief.highlight,
        shadow: {
          x: relief.shadow.x * 1.45,
          y: relief.shadow.y * 1.45,
        },
      };
    })()),
  });

  const TELLTALE = {
    // Bold arrow, points right (tip at +x), centered at origin. ~12 wide × 13.52 tall.
    path: "M 6 0 L -0.76 6.76 L -0.76 2.26 L -6 2.26 L -6 -2.26 L -0.76 -2.26 L -0.76 -6.76 Z",
    positions: {
      left: { x: 75, y: 55, rotate: 180 },
      right: { x: 125, y: 55, rotate: 0 },
    },
  };

  const TELLTALE_CONFIG = {
    scale: 0.95,
    spacing: 37,
    y: 59,
  };

  const MARKER_PATHS = {
    setBody: "M -4.3 -4.6 H 4.3 V 12.2 L 0 17.1 L -4.3 12.2 Z",
    setBodySimple: "M -3.6 -4.6 H 3.6 V 12.2 L 0 17.1 L -3.6 12.2 Z",
    setBodyTab: "M -4.55 -7.05 H 4.05 V 12.05 L 0.15 17.15 L -4.1 12.35 Z",
    setBodyGrounded: "M -4.45 -4.6 H 4.2 V 12.25 L 0.12 17.2 L -4.35 12.45 Z",
    setBodyBeveled: "M -4.95 -4.55 H 3.65 L 4.75 -3.45 V 11.55 L 0.4 17.05 L -4.55 12.2 V -3.65 Z",
    setBodyWorn: "M -4.7 -4.3 H 3.25 L 4.05 -3.5 V 11.2 L -0.15 16.1 L -4.35 11.7 V -3.6 Z",
    setHighlight: "M -3.0 -3 H 3.0 V -1.6 H -3.0 Z",
    setHighlightSimple: "M -2.5 -3 H 2.5 V -1.6 H -2.5 Z",
    setHighlightGrounded: "M -3.15 -2.95 H 2.95 V -1.75 H -3.15 Z",
    setHighlightBeveled: "M -3.35 -3.1 H 2.35 L 2.95 -2.48 H -3.35 Z",
    setHighlightWorn: "M -2.8 -2.65 H 1.9 L 2.3 -2.2 H -2.8 Z",
    setLip: "M -2.95 -2.65 H 2.45",
    setSeat: "M -2.6 12.05 H 2.55",
    setEdgeLight: "M -3.55 -2.3 V 10.65",
    setCrease: "M -2.25 10.6 L 0.05 13.95 L 2.2 10.55",
    setWear: "M -2.7 0.6 L -1.35 7.45",
    setShade: "M -4.3 11.75 H 4.3 L 0 17.1 Z",
    setShadeSimple: "M -3.6 11.75 H 3.6 L 0 17.1 Z",
    setShadeGrounded: "M -4.2 11.9 H 4.15 L 0.1 16.95 L -3.95 12.05 Z",
    setShadeBeveled: "M -4.55 11.5 H 4.2 L 0.45 16.7 L -3.6 12.0 Z",
    setShadeWorn: "M -4.1 10.95 H 3.7 L -0.05 15.65 L -3.35 11.8 Z",
    limitPointer: "M -5.6 -9.6 H 5.6 L 0 0 Z",
    limitHighlight: "M -4.35 -9.1 H 4.35 V -8 H -4.35 Z",
    limitShade: "M -3.55 -3.25 H 3.55 L 0 -0.25 Z",
  };

  const SET_MARKER_SCALE_X = 0.84;

  const NEEDLE = {
    halfWidth: 1.6,
    tipHalfWidth: 0.65,
    length: 74,
    tailLength: 4.2,
    sideShade: {
      side: "right",
      width: 0.82,
      tipWidth: 0.32,
    },
    highlight: {
      halfWidth: 0.28,
      tipHalfWidth: 0.16,
      length: 70,
      tailLength: 3.4,
      offsetX: -0.38,
    },
  };

  const ANIMATION = {
    responsePerSecond: 9,
    indicatorResponsePerSecond: 6,
    deadbandDegrees: 0.03,
    indicatorDeadbandDegrees: 0.12,
    assumedFrameMs: 1000 / 60,
  };

  const PERFORMANCE = {
    glowQuantum: 1 / 32,
    needleAngleQuantum: 0.12,
    indicatorAngleQuantum: 0.24,
  };

  let gaugeId = 0;

  const DATA_ATTRS = {
    value: "value",
    min: "min",
    max: "max",
    majorStep: "major-step",
    minorStep: "minor-step",
    unit: "unit",
    gear: "gear",
    accSetSpeed: "acc-set-speed",
    speedLimit: "speed-limit",
    modeState: "mode-state",
    leftBlinker: "left-blinker",
    rightBlinker: "right-blinker",
  };

  const TUNE_ATTRS = {
    modeArcCy: "mode-arc-cy",
    modeArcRadius: "mode-arc-radius",
  };

  const FULL_RENDER_ATTRS = [
    DATA_ATTRS.min,
    DATA_ATTRS.max,
    DATA_ATTRS.majorStep,
    DATA_ATTRS.minorStep,
    DATA_ATTRS.unit,
    ...Object.values(TUNE_ATTRS),
  ];

  const requiresFullRender = (attr) => FULL_RENDER_ATTRS.includes(attr);

  const warned = new Set();
  const warnOnce = (key, message) => {
    if (warned.has(key)) return;
    warned.add(key);
    console.warn(message);
  };

  const normalizeModeState = (rawState) => {
    const state = String(rawState || "off").toLowerCase();
    return state === "mads" || state === "full" ? state : "off";
  };

  const modeArcColorForState = (modeState) => {
    if (modeState === "mads") return "rgb(var(--mode-mads-rgb))";
    if (modeState === "full") return "rgb(var(--mode-full-rgb))";
    return null;
  };

  const normalizeGearState = (rawGear) => {
    const gear = String(rawGear || "D").trim().toUpperCase();
    switch (gear) {
      case "P":
      case "PARK":
        return { key: "p", label: "P" };
      case "R":
      case "REVERSE":
        return { key: "r", label: "R" };
      case "N":
      case "NEUTRAL":
        return { key: "n", label: "N" };
      default:
        return { key: "d", label: "D" };
    }
  };

  const normalizeSetMarkerStyle = (rawStyle) => {
    const style = String(rawStyle || "clean").trim().toLowerCase();
    return style === "grounded" || style === "beveled" || style === "worn" ? style : "clean";
  };

  const normalizeBlinker = (raw) => {
    const n = Math.round(Number(raw));
    return n === 1 || n === 2 ? n : 0;
  };

  const blinkerKey = (state) => {
    if (state === 2) return "solid";
    if (state === 1) return "blink";
    return "off";
  };

  const normalizedUnit = (rawUnit) => {
    const label = String(rawUnit ?? "MPH").toUpperCase();
    return {
      key: UNIT_ALIASES[label] || "MPH",
      label,
    };
  };

  const scaleProfileFor = (rawUnit) => {
    const { key, label } = normalizedUnit(rawUnit);
    const isKnownAlias = Object.prototype.hasOwnProperty.call(UNIT_ALIASES, label);
    if (!isKnownAlias) {
      warnOnce(`unit:${label}`, `[analog-speed-gauge] Unknown unit "${label}". Falling back to ${key} scale/profile.`);
    }
    return {
      key,
      label: key === "KPH" ? "KM/H" : key,
      ...SCALE_PROFILES[key],
    };
  };

  const validateScaleRange = (data, context = "gauge") => {
    if (!Number.isFinite(data.min) || !Number.isFinite(data.max) || data.max <= data.min) {
      warnOnce(`range:${context}:${data.min}:${data.max}`, `[analog-speed-gauge] Invalid scale range in ${context}: min=${data.min}, max=${data.max}.`);
      return false;
    }
    if (!(data.scale.majorStep > 0 && data.scale.minorStep > 0)) {
      warnOnce(`steps:${context}:${data.scale.majorStep}:${data.scale.minorStep}`, `[analog-speed-gauge] Invalid step config in ${context}: major=${data.scale.majorStep}, minor=${data.scale.minorStep}.`);
      return false;
    }
    if (data.scale.majorStep < data.scale.minorStep) {
      warnOnce(`step-order:${context}:${data.scale.majorStep}:${data.scale.minorStep}`, `[analog-speed-gauge] Major step should be >= minor step in ${context}.`);
    }
    if (data.scale.majorStep === data.scale.minorStep) {
      warnOnce(`step-equal:${context}:${data.scale.majorStep}`, `[analog-speed-gauge] Major and minor steps match in ${context}. Rendering major ticks only.`);
    }
    return true;
  };

  const animationFactor = (lastFrameAt, now, responsePerSecond = ANIMATION.responsePerSecond) => {
    const elapsedMs = lastFrameAt === undefined ? ANIMATION.assumedFrameMs : now - lastFrameAt;
    return 1 - Math.exp(-responsePerSecond * elapsedMs / 1000);
  };

  const quantize = (value, step) => {
    if (!Number.isFinite(value) || !(step > 0)) return value;
    return Math.round(value / step) * step;
  };

  const setAttrIfChanged = (node, name, value) => {
    if (!node) return;
    const next = String(value);
    if (node.getAttribute(name) === next) return;
    node.setAttribute(name, next);
  };

  const setTextIfChanged = (node, value) => {
    if (!node || node.textContent === value) return;
    node.textContent = value;
  };

  const setDatasetIfChanged = (node, key, value) => {
    if (!node || node.dataset[key] === value) return;
    node.dataset[key] = value;
  };

  const quantizedNeedleAngle = (angle) => quantize(angle, PERFORMANCE.needleAngleQuantum);
  const quantizedIndicatorAngle = (angle) => quantize(angle, PERFORMANCE.indicatorAngleQuantum);

  const text = (className, x, y, value) => `<text class="${className}" x="${fmt(x)}" y="${fmt(y)}">${value}</text>`;
  const escapeHtml = shell.escapeHtml;

  const needlePoints = ({ halfWidth, tipHalfWidth, length, tailLength = 0, offsetX = 0 }) => {
    const { cx, cy } = GAUGE;
    return `${cx + offsetX - halfWidth},${cy + tailLength} ${cx + offsetX + halfWidth},${cy + tailLength} ${cx + offsetX + tipHalfWidth},${cy - length} ${cx + offsetX - tipHalfWidth},${cy - length}`;
  };

  const needleSideShadePoints = ({ halfWidth, tipHalfWidth, length, tailLength, sideShade }) => {
    const { cx, cy } = GAUGE;
    const side = sideShade.side === "left" ? -1 : 1;
    return [
      `${cx + side * halfWidth},${cy + tailLength}`,
      `${cx + side * (halfWidth - sideShade.width)},${cy + tailLength}`,
      `${cx + side * (tipHalfWidth - sideShade.tipWidth)},${cy - length}`,
      `${cx + side * tipHalfWidth},${cy - length}`,
    ].join(" ");
  };

  const setSvgPartVisible = (part, visible) => {
    if (!part) return;
    part.toggleAttribute("hidden", !visible);
    if (visible) {
      part.removeAttribute("display");
      part.style.removeProperty("display");
    }
    else part.style.setProperty("display", "none");
  };

  const unwrapDefs = (markup) => markup.replace(/^\s*<defs>/, "").replace(/<\/defs>\s*$/, "");

  class AnalogSpeedGauge extends HTMLElement {
    static get observedAttributes() {
      return [...Object.values(DATA_ATTRS), ...Object.values(TUNE_ATTRS), "data-detail", "data-set-marker-style"];
    }

    constructor() {
      super();
      this.ids = {
        setBug: `set-bug-${++gaugeId}`,
        limitPointer: `limit-pointer-${gaugeId}`,
        needle: `needle-${gaugeId}`,
        modeArcGradient: `mode-arc-gradient-${gaugeId}`,
        faceGradient: `face-gradient-${gaugeId}`,
        faceSheen: `face-sheen-${gaugeId}`,
        faceEdgeShadow: `face-edge-shadow-${gaugeId}`,
        faceGrain: `face-grain-${gaugeId}`,
        faceMottle: `face-mottle-${gaugeId}`,
        faceClip: `face-clip-${gaugeId}`,
        outerLipHighlightFade: `outer-lip-highlight-fade-${gaugeId}`,
        innerShelfHighlightFade: `inner-shelf-highlight-fade-${gaugeId}`,
      };
      this.currentShellDetail = null;
      this.definitionsHtml = {
        shell: "",
        symbols: "",
        mode: "",
      };
      this.staticLayerHtml = undefined;
      this.staticLayersDirty = false;
      this.animationRaf = 0;
      this.lastFrameAt = undefined;
      this._cachedData = null;
      this._cachedFacePrint = null;
      this._facePrintKey = null;
      this._staticDefinitionsHtml = null;
      this._lastStaticDetail = null;
      this._symbolDefinitionsHtml = null;
      this._layers = null;
      this.lastLimitGlow = undefined;
      this.lastModeArcColor = undefined;
      this.lastAriaLabel = undefined;
    }

    connectedCallback() { this.render(); }

    disconnectedCallback() {
      cancelAnimationFrame(this.animationRaf);
      this.animationRaf = 0;
      this.lastFrameAt = undefined;
      this.currentNeedleAngle = undefined;
      this.currentSetAngle = undefined;
      this.currentLimitAngle = undefined;
      this.lastLimitGlow = undefined;
      this.lastModeArcColor = undefined;
      this.lastAriaLabel = undefined;
      this._cachedData = null;
      this._cachedFacePrint = null;
      this._facePrintKey = null;
      this._staticDefinitionsHtml = null;
      this._lastStaticDetail = null;
      this._symbolDefinitionsHtml = null;
      this._layers = null;
      this.parts = undefined;
    }

    attributeChangedCallback(name) {
      if (!this.isConnected) return;
      if (this.suppressAttributeUpdates) return;
      this.invalidateDataCache();
      if (name === "data-detail" || requiresFullRender(name)) {
        this.render();
      } else {
        this.updateDynamicParts();
      }
    }

    shellDetail() {
      return this.dataset.detail === "compact" ? "compact" : "full";
    }

    refreshStaticLayers() {
      const detail = this.shellDetail();
      const detailChanged = this.currentShellDetail !== detail || !this.staticLayerHtml;
      this.definitionsHtml = {
        shell: this.renderShellDefinitions(detail),
        symbols: this.renderSymbolDefinitions(),
        mode: this.renderModeDefinitions(),
      };
      this.staticLayersDirty = detailChanged;
      if (!detailChanged) return;

      this.currentShellDetail = detail;
      this.staticLayerHtml = {
        bezelAssembly: this.renderBezelAssembly(),
        hub: this.renderHub(),
        glass: this.renderGlass(),
      };
    }

    update(nextState = {}) {
      let needsRender = false;
      let changed = false;
      this.suppressAttributeUpdates = true;
      try {
        Object.entries(nextState).forEach(([key, value]) => {
          if (value == null) return;
          const attr = DATA_ATTRS[key];
          if (!attr) return;
          const next = String(value);
          if (this.getAttribute(attr) === next) return;
          this.setAttribute(attr, next);
          changed = true;
          if (requiresFullRender(attr)) needsRender = true;
        });
      } finally {
        this.suppressAttributeUpdates = false;
      }
      if (!changed) return;
      if (needsRender) this.render();
      else this.updateDynamicParts();
    }

    cacheParts() {
      this.parts = {
        needle: this.querySelector('[data-part="needle"]'),
        unit: this.querySelector('[data-part="unit"]'),
        svg: this.querySelector('[data-svg="dynamic"]'),
        complianceArc: this.querySelector('[data-part="compliance-arc"]'),
        complianceArcBase: this.querySelector('[data-part="compliance-arc-base"]'),
        complianceArcHot: this.querySelector('[data-part="compliance-arc-hot"]'),
        setMarker: this.querySelector('[data-part="set-marker"]'),
        limitMarker: this.querySelector('[data-part="limit-marker"]'),
      };
    }

    cacheLayers() {
      this._layers = {
        shellDefs: this.querySelector('[data-layer="shell-defs"]'),
        overlayShellDefs: this.querySelector('[data-layer="overlay-shell-defs"]'),
        symbolDefs: this.querySelector('[data-layer="symbol-defs"]'),
        modeDefs: this.querySelector('[data-layer="mode-defs"]'),
        face: this.querySelector('[data-layer="face"]'),
        bezel: this.querySelector('[data-layer="bezel"]'),
        compliance: this.querySelector('[data-layer="compliance"]'),
        markers: this.querySelector('[data-layer="markers"]'),
        readout: this.querySelector('[data-layer="readout"]'),
        hub: this.querySelector('[data-layer="hub"]'),
        needle: this.querySelector('[data-layer="needle"]'),
        glass: this.querySelector('[data-layer="glass"]'),
      };
    }

    numberAttr(name, fallback) {
      const parsed = Number(this.getAttribute(name) ?? fallback);
      return Number.isFinite(parsed) ? parsed : fallback;
    }

    positiveNumberAttr(name, fallback) {
      const parsed = this.numberAttr(name, fallback);
      return parsed > 0 ? parsed : fallback;
    }

    invalidateDataCache() {
      this._cachedData = null;
    }

    modeTrack() {
      return {
        ...TRACKS.mode,
        cy: this.numberAttr(TUNE_ATTRS.modeArcCy, TRACKS.mode.cy),
        arcRadius: this.positiveNumberAttr(TUNE_ATTRS.modeArcRadius, TRACKS.mode.arcRadius),
      };
    }

    telltaleConfig() {
      const { scale, spacing, y } = TELLTALE_CONFIG;
      const centerX = 100;
      const halfSpacing = spacing / 2;
      return {
        scale,
        positions: {
          left: { x: centerX - halfSpacing, y, rotate: 180 },
          right: { x: centerX + halfSpacing, y, rotate: 0 },
        },
      };
    }

    readData() {
      if (this._cachedData) return this._cachedData;
      const unitProfile = scaleProfileFor(this.getAttribute(DATA_ATTRS.unit));
      const min = this.numberAttr(DATA_ATTRS.min, unitProfile.defaultMin);
      const max = this.numberAttr(DATA_ATTRS.max, unitProfile.defaultMax);
      const gearState = normalizeGearState(this.getAttribute(DATA_ATTRS.gear));
      const data = {
        value: this.numberAttr(DATA_ATTRS.value, 0),
        min,
        max,
        unit: unitProfile.label,
        unitKey: unitProfile.key,
        scale: {
          majorStep: this.positiveNumberAttr(DATA_ATTRS.majorStep, unitProfile.majorStep),
          minorStep: this.positiveNumberAttr(DATA_ATTRS.minorStep, unitProfile.minorStep),
        },
        gear: gearState.label,
        gearState: gearState.key,
        accSetSpeed: this.numberAttr(DATA_ATTRS.accSetSpeed, 0),
        speedLimit: this.numberAttr(DATA_ATTRS.speedLimit, 0),
        modeState: normalizeModeState(this.getAttribute(DATA_ATTRS.modeState)),
        leftBlinker: normalizeBlinker(this.getAttribute(DATA_ATTRS.leftBlinker)),
        rightBlinker: normalizeBlinker(this.getAttribute(DATA_ATTRS.rightBlinker)),
      };
      this._cachedData = data;
      return data;
    }

    angleFor(data, value) {
      if (!validateScaleRange(data, "angleFor")) return GAUGE.startAngle;
      return geometry.valueToAngle(value, data.min, data.max, GAUGE.startAngle, GAUGE.sweepAngle);
    }

    valueForAngle(data, angle) {
      if (!validateScaleRange(data, "valueForAngle")) return data.min;
      return geometry.angleToValue(angle, data.min, data.max, GAUGE.startAngle, GAUGE.sweepAngle);
    }

    complianceArcCapAngle() {
      return (TRACKS.speedLimit.arcStrokeWidth / 2) / TRACKS.speedLimit.arcRadius * 180 / Math.PI;
    }

    numberPosition(data, value, angle) {
      const label = String(value);
      const mirrorLabel = String(data.min + data.max - value);
      const rad = geometry.gaugeDegToRad(angle);
      const halfWidth = Math.max(label.length, mirrorLabel.length) * TEXT_METRICS.numberCharHalfWidth;
      const halfHeight = TEXT_METRICS.numberHalfHeight;
      const outwardTextExtent = halfWidth * Math.abs(Math.cos(rad)) + halfHeight * Math.abs(Math.sin(rad));
      return geometry.polar(GAUGE.cx, GAUGE.cy, GAUGE.textSafeRadius - outwardTextExtent, angle);
    }

    complianceArcVisibleAngle(data, limitEdgeAngle = this.angleFor(data, clamp(data.speedLimit, data.min, data.max))) {
      const startAngle = GAUGE.startAngle;
      const capAngle = this.complianceArcCapAngle();
      const limitAngle = Math.max(startAngle, limitEdgeAngle - capAngle);
      return limitAngle;
    }

    complianceArcFullPath(data) {
      const startAngle = GAUGE.startAngle;
      const fullAngle = this.complianceArcVisibleAngle(data, this.angleFor(data, data.max));
      if (fullAngle <= startAngle) return "";
      return geometry.arcPath(GAUGE.cx, GAUGE.cy, TRACKS.speedLimit.arcRadius, startAngle, fullAngle);
    }

    complianceArcRevealRatio(data, limitEdgeAngle = this.angleFor(data, clamp(data.speedLimit, data.min, data.max))) {
      if (data.speedLimit <= 0) return 0;
      const startAngle = GAUGE.startAngle;
      const fullAngle = this.complianceArcVisibleAngle(data, this.angleFor(data, data.max));
      const visibleAngle = this.complianceArcVisibleAngle(data, limitEdgeAngle);
      if (fullAngle <= startAngle) return 0;
      return clamp((visibleAngle - startAngle) / (fullAngle - startAngle), 0, 1);
    }

    complianceArcDasharray(data, limitEdgeAngle = this.angleFor(data, clamp(data.speedLimit, data.min, data.max))) {
      const ratio = quantize(this.complianceArcRevealRatio(data, limitEdgeAngle), 1 / 256);
      return `${fmt(ratio)} 1`;
    }

    modeArcCircle() {
      const track = this.modeTrack();
      return `
        <circle class="mode-arc-band" cx="${fmt(track.cx)}" cy="${fmt(track.cy)}" r="${fmt(track.arcRadius)}" style="stroke:url(#${this.ids.modeArcGradient});--mode-arc-stroke-width:${track.arcStrokeWidth}" />`;
    }

    limitGlowForValue(value, speedLimit) {
      if (speedLimit <= 0) return 0;
      const ratio = value / speedLimit;
      return clamp((ratio - GAUGE.glowStartRatio) / GAUGE.glowRampRatio, 0, 1);
    }

    setLimitGlow(svg, glow) {
      if (!svg) return;
      const quantizedGlow = clamp(quantize(glow, PERFORMANCE.glowQuantum), 0, 1);
      if (this.lastLimitGlow === quantizedGlow) return;
      this.lastLimitGlow = quantizedGlow;
      svg.style.setProperty("--limit-glow", quantizedGlow);
    }

    setModeArcColor(color) {
      if (!color || this.lastModeArcColor === color) return;
      this.lastModeArcColor = color;
      this.style.setProperty("--mode-arc-color", color);
    }

    markerTransformForAngle(angle, radius) {
      if (![angle, radius].every(Number.isFinite) || radius <= 0) {
        warnOnce(`marker-transform:${angle}:${radius}`, `[analog-speed-gauge] Invalid marker transform inputs: angle=${angle}, radius=${radius}.`);
        return "";
      }
      const point = geometry.polar(GAUGE.cx, GAUGE.cy, radius, angle);
      return `translate(${fmt(point.x)} ${fmt(point.y)}) rotate(${angle})`;
    }

    renderShellDefinitions(detail) {
      if (this._staticDefinitionsHtml && this._lastStaticDetail === detail) return this._staticDefinitionsHtml;

      this._lastStaticDetail = detail;
      this._staticDefinitionsHtml = unwrapDefs(shell.renderClassicShellDefinitions({
        ids: this.ids,
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
        detail,
      }));
      return this._staticDefinitionsHtml;
    }

    renderSymbolDefinitions() {
      if (this._symbolDefinitionsHtml) return this._symbolDefinitionsHtml;

      this._symbolDefinitionsHtml = `
        <g id="${this.ids.limitPointer}">
          <ellipse class="limit-pointer-shadow-soft" cx="0" cy="-5.4" rx="4.8" ry="1.9" />
          <ellipse class="limit-pointer-shadow" cx="0" cy="-5.5" rx="3.5" ry="1.15" />
          <path class="limit-pointer" d="${MARKER_PATHS.limitPointer}" />
          <path class="limit-pointer-highlight" d="${MARKER_PATHS.limitHighlight}" />
          <path class="limit-pointer-shade" d="${MARKER_PATHS.limitShade}" />
        </g>
        <g id="${this.ids.needle}">
          <polygon class="needle-body" points="${needlePoints(NEEDLE)}" />
          <polygon class="needle-side-shade" points="${needleSideShadePoints(NEEDLE)}" />
          <polygon class="needle-highlight" points="${needlePoints(NEEDLE.highlight)}" />
        </g>
      `;
      return this._symbolDefinitionsHtml;
    }

    renderModeDefinitions() {
      const modeTrack = this.modeTrack();
      return `
        <radialGradient id="${this.ids.modeArcGradient}" cx="${modeTrack.cx}" cy="${modeTrack.cy}" r="${modeTrack.arcRadius + modeTrack.arcStrokeWidth / 2}" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--mode-arc-color)" stop-opacity="0" />
          <stop offset="84%" stop-color="var(--mode-arc-color)" stop-opacity="0" />
          <stop offset="88%" stop-color="var(--mode-arc-color)" stop-opacity="0.12" />
          <stop offset="93%" stop-color="var(--mode-arc-color)" stop-opacity="0.52" />
          <stop offset="97%" stop-color="var(--mode-arc-color)" stop-opacity="0.88" />
          <stop offset="100%" stop-color="var(--mode-arc-color)" stop-opacity="1" />
        </radialGradient>
      `;
    }

    renderMarkers() {
      const style = normalizeSetMarkerStyle(this.getAttribute("data-set-marker-style"));
      const variants = {
        clean: {
          body: MARKER_PATHS.setBodySimple,
          contactShadow: "",
          highlight: MARKER_PATHS.setHighlightSimple,
          lip: "",
          edgeLight: "",
          crease: "",
          wear: "",
          shade: MARKER_PATHS.setShadeSimple,
          useScaleX: false,
        },
        grounded: {
          body: MARKER_PATHS.setBodyGrounded,
          contactShadow: MARKER_PATHS.setBodyGrounded,
          highlight: MARKER_PATHS.setHighlightGrounded,
          lip: MARKER_PATHS.setSeat,
          edgeLight: MARKER_PATHS.setEdgeLight,
          crease: "",
          wear: "",
          shade: MARKER_PATHS.setShadeGrounded,
          useScaleX: true,
        },
        beveled: {
          body: MARKER_PATHS.setBodyBeveled,
          contactShadow: MARKER_PATHS.setBodyBeveled,
          highlight: MARKER_PATHS.setHighlightBeveled,
          lip: MARKER_PATHS.setLip,
          edgeLight: "",
          crease: MARKER_PATHS.setCrease,
          wear: "",
          shade: MARKER_PATHS.setShadeBeveled,
          useScaleX: true,
        },
        worn: {
          body: MARKER_PATHS.setBodyWorn,
          contactShadow: MARKER_PATHS.setBodyWorn,
          highlight: MARKER_PATHS.setHighlightWorn,
          lip: MARKER_PATHS.setLip,
          edgeLight: "",
          crease: MARKER_PATHS.setCrease,
          wear: MARKER_PATHS.setWear,
          shade: MARKER_PATHS.setShadeWorn,
          useScaleX: true,
        },
      };
      const marker = variants[style];
      const shadowMarkup = marker.useScaleX === false
        ? `
            <ellipse class="set-bug-shadow-soft" cx="0" cy="14.05" rx="4.2" ry="1.95" />
            <ellipse class="set-bug-shadow" cx="0" cy="14.0" rx="3.2" ry="1.2" />`
        : `
            <ellipse class="set-bug-shadow-soft" cx="0" cy="14.05" rx="4.95" ry="1.95" />
            <ellipse class="set-bug-shadow" cx="0" cy="14.0" rx="3.8" ry="1.2" />`;
      const contactShadow = marker.contactShadow ? `<path class="set-bug-contact-shadow" d="${marker.contactShadow}" transform="translate(0.95 1.4)" />` : "";
      const lip = marker.lip ? `<path class="set-bug-lip" d="${marker.lip}" />` : "";
      const edgeLight = marker.edgeLight ? `<path class="set-bug-edge-light" d="${marker.edgeLight}" />` : "";
      const crease = marker.crease ? `<path class="set-bug-crease" d="${marker.crease}" />` : "";
      const wear = marker.wear ? `<path class="set-bug-wear" d="${marker.wear}" />` : "";
      const markerContent = `
            ${shadowMarkup}
            ${contactShadow}
            <path class="set-bug-body" d="${marker.body}" />
            <path class="set-bug-highlight" d="${marker.highlight}" />
            ${lip}
            ${edgeLight}
            ${crease}
            ${wear}
            <path class="set-bug-shade" d="${marker.shade}" />`;
      return `
        <g class="bezel-bug set-marker set-marker--${style}" data-part="set-marker">
          ${marker.useScaleX === false ? markerContent : `<g transform="scale(${SET_MARKER_SCALE_X} 1)">${markerContent}
          </g>`}
        </g>
        <g class="limit-marker" data-part="limit-marker">
          <use href="#${this.ids.limitPointer}" />
        </g>
      `;
    }

    renderFacePrint(data) {
      // Face print output is determined by the effective scale range and steps.
      const key = `${data.unitKey}|${data.min}|${data.max}|${data.scale.majorStep}|${data.scale.minorStep}`;
      if (this._cachedFacePrint && this._facePrintKey === key) return this._cachedFacePrint;

      const minorSegments = [];
      const majorSegments = [];
      const labels = [];
      const range = data.max - data.min;
      const includeMinorTicks = data.scale.majorStep !== data.scale.minorStep;
      const densityStep = includeMinorTicks ? data.scale.minorStep : data.scale.majorStep;

      if (!validateScaleRange(data, "renderFacePrint") || range / densityStep > GAUGE.maxTickCount) {
        const empty = { ticks: "", labels: "" };
        this._facePrintKey = key;
        this._cachedFacePrint = empty;
        return empty;
      }

      if (includeMinorTicks) {
        for (let value = data.min; value <= data.max; value += data.scale.minorStep) {
          const angle = this.angleFor(data, value);
          minorSegments.push(geometry.segment(GAUGE.cx, GAUGE.cy, GAUGE.radii.minorTickInner, GAUGE.radii.minorTickOuter, angle));
        }
      }

      for (let value = data.min; value <= data.max; value += data.scale.majorStep) {
        const angle = this.angleFor(data, value);
        const { x: tx, y: ty } = this.numberPosition(data, value, angle);
        majorSegments.push(geometry.segment(GAUGE.cx, GAUGE.cy, GAUGE.radii.majorTickInner, GAUGE.radii.majorTickOuter, angle));
        labels.push(text("number-knockout", tx, ty, value));
        labels.push(text("number", tx, ty, value));
      }

      const result = {
        ticks: `
        <path class="minor-tick" d="${minorSegments.join(" ")}" />
        <path class="major-tick-shadow" transform="translate(${fmt(TICK_RELIEF.major.shadow.x)} ${fmt(TICK_RELIEF.major.shadow.y)})" d="${majorSegments.join(" ")}" />
        <path class="major-tick-highlight" transform="translate(${fmt(TICK_RELIEF.major.highlight.x)} ${fmt(TICK_RELIEF.major.highlight.y)})" d="${majorSegments.join(" ")}" />
        <path class="major-tick" d="${majorSegments.join(" ")}" />
        `,
        labels: labels.join(""),
      };
      this._facePrintKey = key;
      this._cachedFacePrint = result;
      return result;
    }

    renderFace(data) {
      const print = this.renderFacePrint(data);
      const detail = this.shellDetail();
      return shell.renderClassicShellFace({
        ids: this.ids,
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
        detail,
        contentMarkup: `
          <g>${this.modeArcCircle()}</g>
          ${print.ticks}
          ${print.labels}
          <g class="dial-telltales">${this.renderTelltales()}</g>
        `,
      });
    }

    renderComplianceArc(data) {
      const fullPath = this.complianceArcFullPath(data);
      const dasharray = this.complianceArcDasharray(data);
      return `
        <g data-part="compliance-arc">
          <path class="arc arc-limit-base" data-part="compliance-arc-base" d="${fullPath}" pathLength="1" stroke-dasharray="${dasharray}" />
          <path class="arc arc-limit-hot" data-part="compliance-arc-hot" d="${fullPath}" pathLength="1" stroke-dasharray="${dasharray}" />
        </g>
      `;
    }

    renderBezelAssembly() {
      return shell.renderClassicShellBezel({
        ids: this.ids,
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
      });
    }

    renderTelltales() {
      const config = this.telltaleConfig();
      return ["left", "right"].map((side) => {
        const pos = config.positions[side];
        const transform = `translate(${fmt(pos.x)} ${fmt(pos.y)}) rotate(${pos.rotate})`;
        const scale = config.scale !== 1 ? ` scale(${config.scale})` : "";
        return `
          <g class="dial-telltale dial-telltale--${side}" data-part="telltale-${side}" transform="${transform}">
            <g${scale ? ` transform="${scale}"` : ""}>
              <path class="telltale-print" d="${TELLTALE.path}" />
              <path class="telltale-lit" d="${TELLTALE.path}" />
            </g>
          </g>
        `;
      }).join("");
    }

    renderReadout(data) {
      const unit = data.unit.toLowerCase();
      return `
        <text class="label unit-label" data-part="unit" x="${GAUGE.cx}" y="${GAUGE.cy + GAUGE.unitLabelY}" textLength="${GAUGE.unitLabelWidth}" lengthAdjust="spacing">${escapeHtml(unit)}</text>
        ${this.renderGearStrip()}
      `;
    }

    renderGearStrip() {
      const { y, spacing } = GAUGE.gearStrip;
      const gears = ["P", "R", "N", "D"];
      const centerIndex = (gears.length - 1) / 2;
      return gears.map((gear, index) => {
        const x = GAUGE.cx + ((index - centerIndex) * spacing);
        return `<text class="gear-strip-letter" data-gear="${gear}" x="${fmt(x)}" y="${fmt(GAUGE.cy + y)}">${gear}</text>`;
      }).join("");
    }

    renderNeedle(data) {
      const angle = quantizedNeedleAngle(this.currentNeedleAngle ?? this.angleFor(data, data.value));
      return `
        <g class="needle" data-part="needle" transform="${geometry.rotationTransform(angle, GAUGE.cx, GAUGE.cy)}">
          <use href="#${this.ids.needle}" />
        </g>
      `;
    }

    renderHub() {
      return shell.renderClassicShellHub({
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
      });
    }

    overlayFaceClipId() {
      return `${this.ids.faceClip}-overlay`;
    }

    renderOverlayDefs() {
      return `
        <clipPath id="${this.overlayFaceClipId()}">
          <circle cx="${GAUGE.cx}" cy="${GAUGE.cy}" r="${CLASSIC_911_SHELL.dial.faceRadius}" />
        </clipPath>
      `;
    }

    renderGlass() {
      const detail = this.shellDetail();
      return shell.renderClassicShellGlass({
        ids: { faceClip: this.overlayFaceClipId() },
        profile: CLASSIC_911_SHELL,
        detail,
      });
    }

    renderStaticSvg(data) {
      return `
        <svg class="analog-gauge-svg analog-gauge-svg--static" data-svg="static" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="presentation" aria-hidden="true" style="--mode-arc-stroke-width: ${TRACKS.mode.arcStrokeWidth}">
          <defs data-layer="shell-defs">${this.definitionsHtml.shell}</defs>
          <defs data-layer="mode-defs">${this.definitionsHtml.mode}</defs>
          <g data-layer="face">${this.renderFace(data)}</g>
          <g class="bezel-assembly" data-layer="bezel">${this.staticLayerHtml.bezelAssembly}</g>
          <g data-layer="readout">${this.renderReadout(data)}</g>
          <g data-layer="hub">${this.staticLayerHtml.hub}</g>
        </svg>
      `;
    }

    renderDynamicSvg(data) {
      return `
        <svg class="analog-gauge-svg analog-gauge-svg--dynamic" data-svg="dynamic" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="img" aria-label="${escapeHtml(this.ariaLabel(data))}" style="--limit-arc-stroke-width: ${TRACKS.speedLimit.arcStrokeWidth}">
          <defs data-layer="symbol-defs">${this.definitionsHtml.symbols}</defs>
          <g data-layer="compliance">${this.renderComplianceArc(data)}</g>
          <g data-layer="markers">${this.renderMarkers()}</g>
          <g data-layer="needle">${this.renderNeedle(data)}</g>
        </svg>
      `;
    }

    renderOverlaySvg() {
      return `
        <svg class="analog-gauge-svg analog-gauge-svg--overlay" data-svg="overlay" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="presentation" aria-hidden="true">
          <defs data-layer="overlay-shell-defs">${this.renderOverlayDefs()}</defs>
          <g data-layer="glass">${this.staticLayerHtml.glass}</g>
        </svg>
      `;
    }

    render() {
      const previousNeedleAngle = this.currentNeedleAngle;
      const previousSetAngle = this.currentSetAngle;
      const previousLimitAngle = this.currentLimitAngle;
      cancelAnimationFrame(this.animationRaf);
      this.animationRaf = 0;
      this.lastFrameAt = undefined;
      this.currentNeedleAngle = previousNeedleAngle;
      this.currentSetAngle = previousSetAngle;
      this.currentLimitAngle = previousLimitAngle;

      this.invalidateDataCache();
      const data = this.readData();
      this.refreshStaticLayers();

      if (!this._layers) {
        this.innerHTML = `
          <div class="analog-gauge-layer-stack" data-part="gauge-stack">
            ${this.renderStaticSvg(data)}
            ${this.renderDynamicSvg(data)}
            ${this.renderOverlaySvg()}
          </div>
        `;
        this.cacheLayers();
      } else {
        this._layers.modeDefs.innerHTML = this.definitionsHtml.mode;
        this._layers.face.innerHTML = this.renderFace(data);
        this._layers.compliance.innerHTML = this.renderComplianceArc(data);
        this._layers.markers.innerHTML = this.renderMarkers();
        this._layers.readout.innerHTML = this.renderReadout(data);
        this._layers.needle.innerHTML = this.renderNeedle(data);
        if (this.staticLayersDirty) {
          this._layers.shellDefs.innerHTML = this.definitionsHtml.shell;
          this._layers.overlayShellDefs.innerHTML = this.renderOverlayDefs();
          this._layers.bezel.innerHTML = this.staticLayerHtml.bezelAssembly;
          this._layers.hub.innerHTML = this.staticLayerHtml.hub;
          this._layers.glass.innerHTML = this.staticLayerHtml.glass;
        }
      }
      this.lastLimitGlow = undefined;
      this.lastAriaLabel = undefined;
      this.cacheParts();
      this.updateDynamicParts();
    }

    updateDynamicParts() {
      this.invalidateDataCache();
      const data = this.readData();
      const {
        needle,
        unit,
        svg,
        complianceArc,
        complianceArcBase,
        complianceArcHot,
        setMarker,
        limitMarker,
      } = this.parts || {};

      if (needle) this.setNeedleTarget(this.angleFor(data, data.value));
      if (unit) setTextIfChanged(unit, data.unit.toLowerCase());
      const modeArcColor = modeArcColorForState(data.modeState);
      this.setModeArcColor(modeArcColor);
      setDatasetIfChanged(this, "gearState", data.gearState);
      setDatasetIfChanged(this, "modeState", data.modeState);
      setDatasetIfChanged(this, "leftBlinker", blinkerKey(data.leftBlinker));
      setDatasetIfChanged(this, "rightBlinker", blinkerKey(data.rightBlinker));

      if (complianceArc) {
        setSvgPartVisible(complianceArc, data.speedLimit > 0);
        const visibleLimitAngle = this.currentLimitAngle === undefined
          ? this.angleFor(data, data.speedLimit)
          : quantizedIndicatorAngle(this.currentLimitAngle);
        const dasharray = data.speedLimit > 0 ? this.complianceArcDasharray(data, visibleLimitAngle) : "0 1";
        setAttrIfChanged(complianceArcBase, "stroke-dasharray", dasharray);
        setAttrIfChanged(complianceArcHot, "stroke-dasharray", dasharray);
      }

      this.setLimitGlow(svg, data.speedLimit > 0 ? this.limitGlowForValue(data.value, data.speedLimit) : 0);

      if (setMarker) {
        setSvgPartVisible(setMarker, true);
        this.setIndicatorTarget("set", this.angleFor(data, clamp(data.accSetSpeed, data.min, data.max)));
      }

      if (limitMarker) {
        setSvgPartVisible(limitMarker, true);
        this.setIndicatorTarget("limit", this.angleFor(data, clamp(data.speedLimit, data.min, data.max)));
      }

      if (svg) {
        const ariaLabel = this.ariaLabel(data);
        if (this.lastAriaLabel !== ariaLabel) {
          this.lastAriaLabel = ariaLabel;
          setAttrIfChanged(svg, "aria-label", ariaLabel);
        }
      }
    }

    ariaLabel(data) {
      const parts = [`Speed ${Math.round(data.value)} ${data.unit}`];
      parts.push(`gear ${data.gear}`);
      parts.push(`mode ${data.modeState.toUpperCase()}`);
      if (data.accSetSpeed > 0) parts.push(`ACC set ${Math.round(data.accSetSpeed)} ${data.unit}`);
      if (data.speedLimit > 0) parts.push(`speed limit ${Math.round(data.speedLimit)} ${data.unit}`);
      return parts.join(". ");
    }

    setNeedleTarget(angle) {
      this.targetNeedleAngle = angle;
      if (this.currentNeedleAngle === undefined) this.currentNeedleAngle = angle;
      if (!this.animationRaf) this.animateAll(performance.now());
    }

    setIndicatorTarget(kind, angle) {
      let shouldAnimate = false;
      if (kind === "set") {
        this.targetSetAngle = angle;
        if (this.currentSetAngle === undefined) {
          this.currentSetAngle = angle;
          setAttrIfChanged(this.parts?.setMarker, "transform", this.markerTransformForAngle(quantizedIndicatorAngle(this.currentSetAngle), TRACKS.accSet.radius));
        } else {
          shouldAnimate = Math.abs(this.targetSetAngle - this.currentSetAngle) >= ANIMATION.indicatorDeadbandDegrees;
        }
      } else {
        this.targetLimitAngle = angle;
        if (this.currentLimitAngle === undefined) {
          this.currentLimitAngle = angle;
          const displayedLimitAngle = quantizedIndicatorAngle(this.currentLimitAngle);
          setAttrIfChanged(this.parts?.limitMarker, "transform", this.markerTransformForAngle(displayedLimitAngle, TRACKS.speedLimit.markerRadius));
          const data = this._cachedData ?? this.readData();
          if (this.parts?.complianceArc && data.speedLimit > 0) {
            const dasharray = this.complianceArcDasharray(data, displayedLimitAngle);
            setAttrIfChanged(this.parts.complianceArcBase, "stroke-dasharray", dasharray);
            setAttrIfChanged(this.parts.complianceArcHot, "stroke-dasharray", dasharray);
          }
        } else {
          shouldAnimate = Math.abs(this.targetLimitAngle - this.currentLimitAngle) >= ANIMATION.indicatorDeadbandDegrees;
        }
      }
      if (shouldAnimate && !this.animationRaf) this.animateAll(performance.now());
    }

    animateAll(now) {
      const data = this._cachedData ?? this.readData();
      const { needle, setMarker, limitMarker, complianceArc, complianceArcBase, complianceArcHot, svg } = this.parts || {};

      if (!needle && !setMarker && !limitMarker) {
        this.animationRaf = 0;
        this.lastFrameAt = undefined;
        return;
      }

      const factor = animationFactor(this.lastFrameAt, now);
      const indicatorFactor = animationFactor(this.lastFrameAt, now, ANIMATION.indicatorResponsePerSecond);
      const isFirstFrame = this.lastFrameAt === undefined;
      let moving = false;

      if (needle && this.currentNeedleAngle !== undefined) {
        const needleDiff = this.targetNeedleAngle - this.currentNeedleAngle;
        if (Math.abs(needleDiff) < ANIMATION.deadbandDegrees) {
          if (isFirstFrame || this.currentNeedleAngle !== this.targetNeedleAngle) {
            this.currentNeedleAngle = this.targetNeedleAngle;
            setAttrIfChanged(needle, "transform", geometry.rotationTransform(quantizedNeedleAngle(this.currentNeedleAngle), GAUGE.cx, GAUGE.cy));
          }
        } else {
          this.currentNeedleAngle += needleDiff * factor;
          moving = true;
          setAttrIfChanged(needle, "transform", geometry.rotationTransform(quantizedNeedleAngle(this.currentNeedleAngle), GAUGE.cx, GAUGE.cy));
        }
        if (complianceArc && svg) {
          const displayedValue = this.valueForAngle(data, this.currentNeedleAngle);
          this.setLimitGlow(svg, this.limitGlowForValue(displayedValue, data.speedLimit));
        }
      }

      if (setMarker && this.currentSetAngle !== undefined) {
        const setDiff = this.targetSetAngle - this.currentSetAngle;
        if (Math.abs(setDiff) < ANIMATION.indicatorDeadbandDegrees) {
          if (isFirstFrame || this.currentSetAngle !== this.targetSetAngle) {
            this.currentSetAngle = this.targetSetAngle;
            setAttrIfChanged(setMarker, "transform", this.markerTransformForAngle(quantizedIndicatorAngle(this.currentSetAngle), TRACKS.accSet.radius));
          }
        } else {
          this.currentSetAngle += setDiff * indicatorFactor;
          moving = true;
          setAttrIfChanged(setMarker, "transform", this.markerTransformForAngle(quantizedIndicatorAngle(this.currentSetAngle), TRACKS.accSet.radius));
        }
      }

      if (limitMarker && this.currentLimitAngle !== undefined) {
        const limitDiff = this.targetLimitAngle - this.currentLimitAngle;
        if (Math.abs(limitDiff) < ANIMATION.indicatorDeadbandDegrees) {
          if (isFirstFrame || this.currentLimitAngle !== this.targetLimitAngle) {
            this.currentLimitAngle = this.targetLimitAngle;
            const displayedLimitAngle = quantizedIndicatorAngle(this.currentLimitAngle);
            setAttrIfChanged(limitMarker, "transform", this.markerTransformForAngle(displayedLimitAngle, TRACKS.speedLimit.markerRadius));
            if (complianceArc && data.speedLimit > 0) {
              const dasharray = this.complianceArcDasharray(data, displayedLimitAngle);
              setAttrIfChanged(complianceArcBase, "stroke-dasharray", dasharray);
              setAttrIfChanged(complianceArcHot, "stroke-dasharray", dasharray);
            }
          }
        } else {
          this.currentLimitAngle += limitDiff * indicatorFactor;
          moving = true;
          const displayedLimitAngle = quantizedIndicatorAngle(this.currentLimitAngle);
          setAttrIfChanged(limitMarker, "transform", this.markerTransformForAngle(displayedLimitAngle, TRACKS.speedLimit.markerRadius));
          if (complianceArc && data.speedLimit > 0) {
            const dasharray = this.complianceArcDasharray(data, displayedLimitAngle);
            setAttrIfChanged(complianceArcBase, "stroke-dasharray", dasharray);
            setAttrIfChanged(complianceArcHot, "stroke-dasharray", dasharray);
          }
        }
      }

      this.lastFrameAt = moving ? now : undefined;
      this.animationRaf = moving ? requestAnimationFrame((frameAt) => this.animateAll(frameAt)) : 0;
    }
  }

  if (!customElements.get("analog-speed-gauge")) {
    customElements.define("analog-speed-gauge", AnalogSpeedGauge);
  }

})();
