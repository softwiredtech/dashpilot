(function () {
  const geometry = window.DashPilotAnalogGeometry;
  const shell = window.DashPilotAnalogShell;

  function renderMissingDependency(message) {
    return `<svg class="analog-gauge-svg" viewBox="0 0 200 200" role="img" aria-label="Gauge unavailable"><text class="gauge-value" x="100" y="102" text-anchor="middle">${message}</text></svg>`;
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

  /*
   * Radial cross-section, outside-in:
   * 100.0 ACC set marker track
   * 97.5-90.5 bezel outer lip
   * 90.85-89.35 shelf
   * 89.2 face rim
   * 89.0 / 88.0 major / minor tick outer
   * 88.5 mode arc band
   * 78.0 / 82.0 major / minor tick inner
   * 74.8 dial text safe radius
   * 74.0 needle tip
   * 66.8 / 62.0 limit marker / compliance arc
   * 8.8 hub
   */
  const GAUGE = {
    ...VIEWBOX,
    startAngle: -120,
    sweepAngle: 240,
    radii: {
      minorTickInner: 82,
      minorTickOuter: 88,
      majorTickInner: 78,
      majorTickOuter: 89,
    },
  };

  const DIAL_TEXT = {
    textSafeRadius: 74.8,
    unitLabelWidth: 18,
    unitLabelY: -24,
    gearStrip: {
      y: 52,
      spacing: 16,
    },
  };

  const TRACKS = {
    accSet: { radius: 100 },
    speedLimit: {
      arcRadius: 62,
      markerRadius: 66.8,
      arcStrokeWidth: 2.8,
      glowStartRatio: 1.15,
      glowRampRatio: 0.1,
    },
    // Reserve the bottom-tail gap for mode state so it never collides with the compliance arc.
    mode: {
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
    scale: 0.95,
    spacing: 37,
    y: 59,
  };

  const MARKER_PATHS = {
    setBody: "M -3.6 -4.6 H 3.6 V 12.2 L 0 17.1 L -3.6 12.2 Z",
    setHighlight: "M -2.5 -3 H 2.5 V -1.6 H -2.5 Z",
    setShade: "M -3.6 11.75 H 3.6 L 0 17.1 Z",
    limitPointer: "M -5.6 -9.6 H 5.6 L 0 0 Z",
    limitHighlight: "M -4.35 -9.1 H 4.35 V -8 H -4.35 Z",
    limitShade: "M -2.50 -3.25 H 2.50 L 0 -0.25 Z",
  };

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
    indicatorAngleQuantum: 0.24,
    maxTickCount: 200,
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

  const FULL_RENDER_ATTRS = [
    DATA_ATTRS.min,
    DATA_ATTRS.max,
    DATA_ATTRS.majorStep,
    DATA_ATTRS.minorStep,
    DATA_ATTRS.unit,
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
    if (visible) part.style.removeProperty("display");
    else part.style.setProperty("display", "none");
  };

  const unwrapDefs = (markup) => markup.replace(/^\s*<defs>/, "").replace(/<\/defs>\s*$/, "");

  class AnalogSpeedGauge extends HTMLElement {
    static get observedAttributes() {
      return Object.values(DATA_ATTRS);
    }

    constructor() {
      super();
      const instanceId = ++gaugeId;
      this.ids = {
        limitPointer: `limit-pointer-${instanceId}`,
        modeArcGradient: `mode-arc-gradient-${instanceId}`,
        faceGradient: `face-gradient-${instanceId}`,
        faceSheen: `face-sheen-${instanceId}`,
        faceEdgeShadow: `face-edge-shadow-${instanceId}`,
        faceGrain: `face-grain-${instanceId}`,
        faceMottle: `face-mottle-${instanceId}`,
        faceClip: `face-clip-${instanceId}`,
        outerLipHighlightFade: `outer-lip-highlight-fade-${instanceId}`,
        shelfHighlightFade: `shelf-highlight-fade-${instanceId}`,
      };
      this.animationRaf = 0;
      this.lastFrameAt = undefined;
      this._cachedData = null;
      this._cachedFacePrint = null;
      this._facePrintKey = null;
      this._staticMarkup = null;
      this._layers = null;
      this.lastLimitGlow = undefined;
      this.lastDisplayedNeedleAngle = undefined;
      this.lastAriaLabel = undefined;
    }

    connectedCallback() {
      // Deferred from the constructor: custom element constructors must not add attributes,
      // and setting inline style properties creates the style attribute.
      this.applyTrackCssVars();
      this.render();
    }

    disconnectedCallback() {
      cancelAnimationFrame(this.animationRaf);
      this.animationRaf = 0;
      this.lastFrameAt = undefined;
      this.currentNeedleAngle = undefined;
      this.currentSetAngle = undefined;
      this.currentLimitAngle = undefined;
      this.lastLimitGlow = undefined;
      this.lastDisplayedNeedleAngle = undefined;
      this.lastAriaLabel = undefined;
      this._cachedData = null;
      this._cachedFacePrint = null;
      this._facePrintKey = null;
      this._layers = null;
      this.parts = undefined;
    }

    attributeChangedCallback(name) {
      if (!this.isConnected) return;
      if (this.suppressAttributeUpdates) return;
      this.invalidateDataCache();
      if (requiresFullRender(name)) {
        this.render();
      } else {
        this.updateDynamicParts();
      }
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
        face: this.querySelector('[data-layer="face"]'),
        compliance: this.querySelector('[data-layer="compliance"]'),
        markers: this.querySelector('[data-layer="markers"]'),
        readout: this.querySelector('[data-layer="readout"]'),
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

    telltaleConfig() {
      const { scale, spacing, y } = TELLTALE;
      const halfSpacing = spacing / 2;
      return {
        scale,
        positions: {
          left: { x: GAUGE.cx - halfSpacing, y, rotate: 180 },
          right: { x: GAUGE.cx + halfSpacing, y, rotate: 0 },
        },
      };
    }

    applyTrackCssVars() {
      this.style.setProperty("--limit-arc-stroke-width", String(TRACKS.speedLimit.arcStrokeWidth));
      this.style.setProperty("--mode-arc-stroke-width", String(TRACKS.mode.arcStrokeWidth));
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
      if (TRACKS.speedLimit.arcRadius <= 0) return 0;
      return (TRACKS.speedLimit.arcStrokeWidth / 2) / TRACKS.speedLimit.arcRadius * 180 / Math.PI;
    }

    numberPosition(data, value, angle) {
      const label = String(value);
      const mirrorLabel = String(data.min + data.max - value);
      const rad = geometry.gaugeDegToRad(angle);
      const halfWidth = Math.max(label.length, mirrorLabel.length) * TEXT_METRICS.numberCharHalfWidth;
      const halfHeight = TEXT_METRICS.numberHalfHeight;
      const outwardTextExtent = halfWidth * Math.abs(Math.cos(rad)) + halfHeight * Math.abs(Math.sin(rad));
      return geometry.polar(GAUGE.cx, GAUGE.cy, DIAL_TEXT.textSafeRadius - outwardTextExtent, angle);
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
      return `
        <circle class="mode-arc-band" cx="${fmt(GAUGE.cx)}" cy="${fmt(GAUGE.cy)}" r="${fmt(TRACKS.mode.arcRadius)}" stroke="url(#${this.ids.modeArcGradient})" />`;
    }

    limitGlowForValue(value, speedLimit) {
      if (speedLimit <= 0) return 0;
      const ratio = value / speedLimit;
      return clamp((ratio - TRACKS.speedLimit.glowStartRatio) / TRACKS.speedLimit.glowRampRatio, 0, 1);
    }

    setLimitGlow(part, glow) {
      if (!part) return;
      const quantizedGlow = clamp(quantize(glow, PERFORMANCE.glowQuantum), 0, 1);
      if (this.lastLimitGlow === quantizedGlow) return;
      this.lastLimitGlow = quantizedGlow;
      part.style.setProperty("--limit-glow", quantizedGlow);
    }

    needleRotation(angle) {
      return `rotate(${fmt(angle, 3)}deg)`;
    }

    applyNeedleAngle(displayedAngle) {
      const needle = this.parts?.needle;
      if (!needle) return;
      if (this.lastDisplayedNeedleAngle === displayedAngle) return;
      this.lastDisplayedNeedleAngle = displayedAngle;
      needle.style.transform = this.needleRotation(displayedAngle);
    }

    markerTransformForAngle(angle, radius) {
      if (![angle, radius].every(Number.isFinite) || radius <= 0) {
        warnOnce(`marker-transform:${angle}:${radius}`, `[analog-speed-gauge] Invalid marker transform inputs: angle=${angle}, radius=${radius}.`);
        return "";
      }
      const point = geometry.polar(GAUGE.cx, GAUGE.cy, radius, angle);
      return `translate(${fmt(point.x)} ${fmt(point.y)}) rotate(${angle})`;
    }

    staticMarkup() {
      if (this._staticMarkup) return this._staticMarkup;
      this._staticMarkup = {
        shellDefinitions: unwrapDefs(shell.renderClassicShellDefinitions({
          ids: this.ids,
          viewBox: GAUGE,
          profile: CLASSIC_911_SHELL,
        })),
        modeDefinitions: this.renderModeDefinitions(),
        symbolDefinitions: this.renderSymbolDefinitions(),
        bezelAssembly: this.renderBezelAssembly(),
        hub: this.renderHub(),
      };
      return this._staticMarkup;
    }

    renderSymbolDefinitions() {
      return this.renderLimitPointerSymbol();
    }

    renderLimitPointerSymbol() {
      return `
        <g id="${this.ids.limitPointer}">
          <path class="limit-pointer" d="${MARKER_PATHS.limitPointer}" />
          <path class="limit-pointer-highlight" d="${MARKER_PATHS.limitHighlight}" />
          <path class="limit-pointer-shade" d="${MARKER_PATHS.limitShade}" />
        </g>
      `;
    }

    renderModeDefinitions() {
      return `
        <radialGradient id="${this.ids.modeArcGradient}" cx="${GAUGE.cx}" cy="${GAUGE.cy}" r="${TRACKS.mode.arcRadius + TRACKS.mode.arcStrokeWidth / 2}" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--mode-arc-color)" stop-opacity="0" />
          <stop offset="84%" stop-color="var(--mode-arc-color)" stop-opacity="0" />
          <stop offset="88%" stop-color="var(--mode-arc-color)" stop-opacity="0.12" />
          <stop offset="93%" stop-color="var(--mode-arc-color)" stop-opacity="0.52" />
          <stop offset="97%" stop-color="var(--mode-arc-color)" stop-opacity="0.88" />
          <stop offset="100%" stop-color="var(--mode-arc-color)" stop-opacity="1" />
        </radialGradient>
      `;
    }

    renderSetMarker() {
      return `
        <g class="set-marker" data-part="set-marker">
          <path class="set-marker-body" d="${MARKER_PATHS.setBody}" />
          <path class="set-marker-highlight" d="${MARKER_PATHS.setHighlight}" />
          <path class="set-marker-shade" d="${MARKER_PATHS.setShade}" />
        </g>
      `;
    }

    renderLimitMarker() {
      return `
        <g class="limit-marker" data-part="limit-marker">
          <use href="#${this.ids.limitPointer}" />
        </g>
      `;
    }

    renderMarkers() {
      return `${this.renderSetMarker()}${this.renderLimitMarker()}`;
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

      if (!validateScaleRange(data, "renderFacePrint") || range / densityStep > PERFORMANCE.maxTickCount) {
        const empty = { ticks: "", labels: "" };
        this._facePrintKey = key;
        this._cachedFacePrint = empty;
        return empty;
      }

      if (includeMinorTicks) {
        const minorTickCount = Math.round(range / data.scale.minorStep);
        for (let index = 0; index <= minorTickCount; index += 1) {
          const value = data.min + index * data.scale.minorStep;
          const angle = this.angleFor(data, value);
          minorSegments.push(geometry.segment(GAUGE.cx, GAUGE.cy, GAUGE.radii.minorTickInner, GAUGE.radii.minorTickOuter, angle));
        }
      }

      const majorTickCount = Math.round(range / data.scale.majorStep);
      for (let index = 0; index <= majorTickCount; index += 1) {
        const value = data.min + index * data.scale.majorStep;
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
      return shell.renderClassicShellFace({
        ids: this.ids,
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
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
        <text class="label unit-label" data-part="unit" x="${GAUGE.cx}" y="${GAUGE.cy + DIAL_TEXT.unitLabelY}" textLength="${DIAL_TEXT.unitLabelWidth}" lengthAdjust="spacing">${escapeHtml(unit)}</text>
        ${this.renderGearStrip()}
      `;
    }

    renderGearStrip() {
      const { y, spacing } = DIAL_TEXT.gearStrip;
      const gears = ["P", "R", "N", "D"];
      const centerIndex = (gears.length - 1) / 2;
      return gears.map((gear, index) => {
        const x = GAUGE.cx + ((index - centerIndex) * spacing);
        return `<text class="gear-strip-letter" data-gear="${gear}" x="${fmt(x)}" y="${fmt(GAUGE.cy + y)}">${gear}</text>`;
      }).join("");
    }

    renderNeedle() {
      return `
        <svg class="analog-gauge-svg" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="presentation" aria-hidden="true">
          <g>
            <polygon class="needle-body" points="${needlePoints(NEEDLE)}" />
            <polygon class="needle-side-shade" points="${needleSideShadePoints(NEEDLE)}" />
            <polygon class="needle-highlight" points="${needlePoints(NEEDLE.highlight)}" />
          </g>
        </svg>
      `;
    }

    renderHub() {
      return shell.renderClassicShellHub({
        viewBox: GAUGE,
        profile: CLASSIC_911_SHELL,
      });
    }

    renderStaticSvg(data) {
      const staticMarkup = this.staticMarkup();
      return `
        <svg class="analog-gauge-svg analog-gauge-svg--static" data-svg="static" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="presentation" aria-hidden="true">
          <defs data-layer="shell-defs">${staticMarkup.shellDefinitions}</defs>
          <defs data-layer="mode-defs">${staticMarkup.modeDefinitions}</defs>
          <g data-layer="face">${this.renderFace(data)}</g>
          <g class="bezel-assembly" data-layer="bezel">${staticMarkup.bezelAssembly}</g>
          <g data-layer="readout">${this.renderReadout(data)}</g>
          <g data-layer="hub">${staticMarkup.hub}</g>
        </svg>
      `;
    }

    renderDynamicSvg(data) {
      const staticMarkup = this.staticMarkup();
      return `
        <svg class="analog-gauge-svg analog-gauge-svg--dynamic" data-svg="dynamic" viewBox="0 0 ${GAUGE.width} ${GAUGE.height}" role="img" aria-label="${escapeHtml(this.ariaLabel(data))}">
          <defs data-layer="symbol-defs">${staticMarkup.symbolDefinitions}</defs>
          <g data-layer="compliance">${this.renderComplianceArc(data)}</g>
          <g data-layer="markers">${this.renderMarkers()}</g>
        </svg>
      `;
    }

    renderNeedleLayer(data) {
      const angle = this.currentNeedleAngle ?? this.angleFor(data, data.value);
      return `
        <div class="analog-gauge-needle-layer" data-part="needle" style="transform: ${this.needleRotation(angle)}">
          ${this.renderNeedle()}
        </div>
      `;
    }

    render() {
      cancelAnimationFrame(this.animationRaf);
      this.animationRaf = 0;
      this.lastFrameAt = undefined;

      this.invalidateDataCache();
      const data = this.readData();

      if (!this._layers) {
        this.innerHTML = `
          <div class="analog-gauge-layer-stack" data-part="gauge-stack">
            ${this.renderStaticSvg(data)}
            ${this.renderDynamicSvg(data)}
            ${this.renderNeedleLayer(data)}
          </div>
        `;
        this.cacheLayers();
      } else {
        this._layers.face.innerHTML = this.renderFace(data);
        this._layers.compliance.innerHTML = this.renderComplianceArc(data);
        this._layers.markers.innerHTML = this.renderMarkers();
        this._layers.readout.innerHTML = this.renderReadout(data);
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
      setDatasetIfChanged(this, "gearState", data.gearState);
      setDatasetIfChanged(this, "modeState", data.modeState);
      setDatasetIfChanged(this, "leftBlinker", blinkerKey(data.leftBlinker));
      setDatasetIfChanged(this, "rightBlinker", blinkerKey(data.rightBlinker));

      if (complianceArc) {
        setSvgPartVisible(complianceArc, data.speedLimit > 0);
        const visibleLimitAngle = this.currentLimitAngle === undefined
          ? quantizedIndicatorAngle(this.angleFor(data, data.speedLimit))
          : quantizedIndicatorAngle(this.currentLimitAngle);
        const dasharray = data.speedLimit > 0 ? this.complianceArcDasharray(data, visibleLimitAngle) : "0 1";
        setAttrIfChanged(complianceArcBase, "stroke-dasharray", dasharray);
        setAttrIfChanged(complianceArcHot, "stroke-dasharray", dasharray);
      }

      this.setLimitGlow(complianceArcHot, data.speedLimit > 0 ? this.limitGlowForValue(data.value, data.speedLimit) : 0);

      if (setMarker) {
        const hasAccSetSpeed = data.accSetSpeed > 0;
        setSvgPartVisible(setMarker, hasAccSetSpeed);
        if (hasAccSetSpeed) {
          this.setIndicatorTarget("set", this.angleFor(data, clamp(data.accSetSpeed, data.min, data.max)));
        }
      }

      if (limitMarker) {
        const hasSpeedLimit = data.speedLimit > 0;
        setSvgPartVisible(limitMarker, hasSpeedLimit);
        if (hasSpeedLimit) {
          this.setIndicatorTarget("limit", this.angleFor(data, clamp(data.speedLimit, data.min, data.max)));
        }
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

    channelDefinitions(data, parts = this.parts) {
      const { needle, setMarker, limitMarker, complianceArc, complianceArcBase, complianceArcHot } = parts || {};
      return {
        needle: {
          part: needle,
          currentKey: "currentNeedleAngle",
          targetKey: "targetNeedleAngle",
          deadband: ANIMATION.deadbandDegrees,
          responsePerSecond: ANIMATION.responsePerSecond,
          displayAngle: (angle) => angle,
          apply: (displayedAngle) => {
            this.applyNeedleAngle(displayedAngle);
            const displayedValue = this.valueForAngle(data, this.currentNeedleAngle);
            this.setLimitGlow(complianceArcHot, this.limitGlowForValue(displayedValue, data.speedLimit));
          },
        },
        set: {
          part: setMarker,
          currentKey: "currentSetAngle",
          targetKey: "targetSetAngle",
          deadband: ANIMATION.indicatorDeadbandDegrees,
          responsePerSecond: ANIMATION.indicatorResponsePerSecond,
          displayAngle: quantizedIndicatorAngle,
          apply: (displayedAngle) => {
            setAttrIfChanged(setMarker, "transform", this.markerTransformForAngle(displayedAngle, TRACKS.accSet.radius));
          },
        },
        limit: {
          part: limitMarker,
          currentKey: "currentLimitAngle",
          targetKey: "targetLimitAngle",
          deadband: ANIMATION.indicatorDeadbandDegrees,
          responsePerSecond: ANIMATION.indicatorResponsePerSecond,
          displayAngle: quantizedIndicatorAngle,
          apply: (displayedAngle) => this.applyLimitAngle(displayedAngle, data, { limitMarker, complianceArc, complianceArcBase, complianceArcHot }),
        },
      };
    }

    setChannelTarget(kind, angle) {
      const data = this._cachedData ?? this.readData();
      const channel = this.channelDefinitions(data)[kind];
      if (!channel) return;
      this[channel.targetKey] = angle;
      if (this[channel.currentKey] === undefined) {
        this[channel.currentKey] = angle;
        channel.apply(channel.displayAngle(angle));
        return;
      }
      const shouldAnimate = Math.abs(this[channel.targetKey] - this[channel.currentKey]) >= channel.deadband;
      if (shouldAnimate && !this.animationRaf) this.animateAll(performance.now());
    }

    setNeedleTarget(angle) {
      this.setChannelTarget("needle", angle);
    }

    applyLimitAngle(displayedAngle, data, parts = this.parts) {
      setAttrIfChanged(parts?.limitMarker, "transform", this.markerTransformForAngle(displayedAngle, TRACKS.speedLimit.markerRadius));
      if (!parts?.complianceArc || data.speedLimit <= 0) return;
      const dasharray = this.complianceArcDasharray(data, displayedAngle);
      setAttrIfChanged(parts.complianceArcBase, "stroke-dasharray", dasharray);
      setAttrIfChanged(parts.complianceArcHot, "stroke-dasharray", dasharray);
    }

    setIndicatorTarget(kind, angle) {
      this.setChannelTarget(kind, angle);
    }

    animateChannel(channel, isFirstFrame, now) {
      if (!channel.part) return false;
      const currentAngle = this[channel.currentKey];
      const targetAngle = this[channel.targetKey];
      if (currentAngle === undefined || targetAngle === undefined) return false;
      const factor = animationFactor(this.lastFrameAt, now, channel.responsePerSecond);
      const diff = targetAngle - currentAngle;
      if (Math.abs(diff) < channel.deadband) {
        if (isFirstFrame || currentAngle !== targetAngle) {
          this[channel.currentKey] = targetAngle;
          channel.apply(channel.displayAngle(targetAngle));
        }
        return false;
      }
      this[channel.currentKey] = currentAngle + diff * factor;
      channel.apply(channel.displayAngle(this[channel.currentKey]));
      return true;
    }

    animateAll(now) {
      const data = this._cachedData ?? this.readData();
      const channels = Object.values(this.channelDefinitions(data));
      const visibleChannels = channels.filter((channel) => channel.part);

      if (!visibleChannels.length) {
        this.animationRaf = 0;
        this.lastFrameAt = undefined;
        return;
      }

      const isFirstFrame = this.lastFrameAt === undefined;
      let moving = false;
      visibleChannels.forEach((channel) => {
        moving = this.animateChannel(channel, isFirstFrame, now) || moving;
      });

      this.lastFrameAt = moving ? now : undefined;
      this.animationRaf = moving ? requestAnimationFrame((frameAt) => this.animateAll(frameAt)) : 0;
    }
  }

  if (!customElements.get("analog-speed-gauge")) {
    customElements.define("analog-speed-gauge", AnalogSpeedGauge);
  }

})();
