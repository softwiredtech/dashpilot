(function () {
  const geometry = window.DashPilotAnalogGeometry;
  const fmt = geometry.fmt;
  let defsCounter = 0;

  const VIEWBOX = Object.freeze({
    width: 200,
    height: 200,
    cx: 100,
    cy: 100,
  });

  const SURFACE = Object.freeze({
    raised: "raised",
    recessed: "recessed",
  });

  const LIGHTING = Object.freeze({
    directionDeg: 345,
    raisedArcSpreadDeg: 112,
    recessedArcSpreadDeg: 92,
  });

  const SHELL_DETAIL_PROFILES = Object.freeze({
    full: Object.freeze({
      faceTexture: true,
      faceEdgeShadow: true,
      glassEffects: true,
    }),
    compact: Object.freeze({
      faceTexture: false,
      faceEdgeShadow: true,
      glassEffects: false,
    }),
  });

  const SPEED_SCALE_PROFILES = Object.freeze({
    MPH: Object.freeze({ defaultMin: 0, defaultMax: 140, majorStep: 20, minorStep: 10 }),
    KPH: Object.freeze({ defaultMin: 0, defaultMax: 220, majorStep: 20, minorStep: 10 }),
  });

  const HTML_ESCAPE_MAP = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  };

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => HTML_ESCAPE_MAP[char]);
  }

  function normalizedDetail(detail) {
    return detail === "full" ? "full" : "compact";
  }

  function shellDetail(detail) {
    return SHELL_DETAIL_PROFILES[normalizedDetail(detail)];
  }

  function directionalPoint(directionDeg, radiusPercent) {
    const radians = geometry.gaugeDegToRad(directionDeg);
    const x = 50 + Math.cos(radians) * radiusPercent;
    const y = 50 + Math.sin(radians) * radiusPercent;
    return {
      x: `${x.toFixed(2)}%`,
      y: `${y.toFixed(2)}%`,
    };
  }

  function lightingForSurface(surface) {
    const isRecessed = surface === SURFACE.recessed;
    const spread = isRecessed ? LIGHTING.recessedArcSpreadDeg : LIGHTING.raisedArcSpreadDeg;
    return {
      highlight: geometry.zoneAround(isRecessed ? geometry.oppositeAngle(LIGHTING.directionDeg) : LIGHTING.directionDeg, spread),
      shadow: geometry.zoneAround(isRecessed ? LIGHTING.directionDeg : geometry.oppositeAngle(LIGHTING.directionDeg), spread),
    };
  }

  function reliefOffsetForSurface(surface, distance) {
    const radians = geometry.gaugeDegToRad(LIGHTING.directionDeg);
    const sign = surface === SURFACE.recessed ? 1 : -1;
    return {
      shadow: { x: Math.cos(radians) * distance * sign, y: Math.sin(radians) * distance * sign },
      highlight: { x: -Math.cos(radians) * distance * sign, y: -Math.sin(radians) * distance * sign },
    };
  }

  function lightLine(cx, cy, radius = 42, directionDeg = LIGHTING.directionDeg) {
    const radians = geometry.gaugeDegToRad(directionDeg);
    const dx = Math.cos(radians) * radius;
    const dy = Math.sin(radians) * radius;
    return {
      x1: cx + dx,
      y1: cy + dy,
      x2: cx - dx,
      y2: cy - dy,
    };
  }

  function arcFadeGradient(id, cx, cy, radius, zone, colorVar) {
    const start = geometry.polar(cx, cy, radius, zone.startAngle);
    const end = geometry.polar(cx, cy, radius, zone.endAngle);
    return `
      <linearGradient id="${id}" x1="${fmt(start.x)}" y1="${fmt(start.y)}" x2="${fmt(end.x)}" y2="${fmt(end.y)}" gradientUnits="userSpaceOnUse">
        <stop offset="0%" stop-color="${colorVar}" stop-opacity="0" />
        <stop offset="14%" stop-color="${colorVar}" stop-opacity="1" />
        <stop offset="86%" stop-color="${colorVar}" stop-opacity="1" />
        <stop offset="100%" stop-color="${colorVar}" stop-opacity="0" />
      </linearGradient>
    `;
  }

  const CLASSIC_911_SHELL = (() => {
    const outerLipLighting = lightingForSurface(SURFACE.raised);
    const hubLighting = lightingForSurface(SURFACE.raised);
    const innerShelfHighlightZone = geometry.zoneAround(geometry.oppositeAngle(LIGHTING.directionDeg), LIGHTING.recessedArcSpreadDeg);
    const bezel = Object.freeze({
      outerLip: Object.freeze({
        surface: SURFACE.raised,
        outerRadius: 97.5,
        innerRadius: 90.5,
        materialClass: "bezel",
        highlight: Object.freeze({
          radius: 99.0,
          zone: Object.freeze({ ...outerLipLighting.highlight }),
          className: "bezel-highlight-outer",
        }),
      }),
      innerShelf: Object.freeze({
        surface: SURFACE.recessed,
        outerRadius: 90.85,
        innerRadius: 89.35,
        materialClass: "inner-shelf",
        highlight: Object.freeze({
          radius: 94.0,
          zone: Object.freeze({ ...innerShelfHighlightZone }),
          className: "inner-shelf-highlight",
        }),
        shadow: Object.freeze({
          radius: 92.2,
          zone: Object.freeze({ ...geometry.oppositeZone(innerShelfHighlightZone) }),
          className: "inner-shelf-shadow",
        }),
      }),
    });

    return Object.freeze({
      bezel,
      dial: Object.freeze({
        faceInset: 0.15,
        faceRadius: bezel.innerShelf.innerRadius - 0.15,
      }),
      hub: Object.freeze({
        surface: SURFACE.raised,
        outerRadius: 8.8,
        highlight: Object.freeze({
          radius: 7.7,
          zone: Object.freeze({ ...hubLighting.highlight }),
          className: "hub-highlight",
        }),
        shadow: Object.freeze({
          radius: 7.85,
          zone: Object.freeze({ ...hubLighting.shadow }),
          className: "hub-shadow",
        }),
      }),
    });
  })();

  function applyLightingCssVars(root = document.documentElement) {
    if (!root) return;

    const raisedHighlight = directionalPoint(LIGHTING.directionDeg, 32);
    const raisedShadow = directionalPoint(geometry.oppositeAngle(LIGHTING.directionDeg), 32);
    const recessedHighlight = directionalPoint(geometry.oppositeAngle(LIGHTING.directionDeg), 32);
    const recessedShadow = directionalPoint(LIGHTING.directionDeg, 32);
    const backdropRaisedHighlight = directionalPoint(LIGHTING.directionDeg, 18);
    const backdropRaisedShadow = directionalPoint(geometry.oppositeAngle(LIGHTING.directionDeg), 18);
    const backdropRecessedHighlight = directionalPoint(geometry.oppositeAngle(LIGHTING.directionDeg), 18);
    const backdropRecessedShadow = directionalPoint(LIGHTING.directionDeg, 18);
    const tickRelief = reliefOffsetForSurface(SURFACE.raised, 0.55);

    root.style.setProperty("--lighting-direction-deg", `${LIGHTING.directionDeg}deg`);
    root.style.setProperty("--lighting-opposite-deg", `${geometry.oppositeAngle(LIGHTING.directionDeg)}deg`);
    root.style.setProperty("--raised-highlight-x", raisedHighlight.x);
    root.style.setProperty("--raised-highlight-y", raisedHighlight.y);
    root.style.setProperty("--raised-shadow-x", raisedShadow.x);
    root.style.setProperty("--raised-shadow-y", raisedShadow.y);
    root.style.setProperty("--recessed-highlight-x", recessedHighlight.x);
    root.style.setProperty("--recessed-highlight-y", recessedHighlight.y);
    root.style.setProperty("--recessed-shadow-x", recessedShadow.x);
    root.style.setProperty("--recessed-shadow-y", recessedShadow.y);
    root.style.setProperty("--backdrop-raised-highlight-x", backdropRaisedHighlight.x);
    root.style.setProperty("--backdrop-raised-highlight-y", backdropRaisedHighlight.y);
    root.style.setProperty("--backdrop-raised-shadow-x", backdropRaisedShadow.x);
    root.style.setProperty("--backdrop-raised-shadow-y", backdropRaisedShadow.y);
    root.style.setProperty("--backdrop-recessed-highlight-x", backdropRecessedHighlight.x);
    root.style.setProperty("--backdrop-recessed-highlight-y", backdropRecessedHighlight.y);
    root.style.setProperty("--backdrop-recessed-shadow-x", backdropRecessedShadow.x);
    root.style.setProperty("--backdrop-recessed-shadow-y", backdropRecessedShadow.y);
    root.style.setProperty("--raised-relief-highlight-offset-x", `${tickRelief.highlight.x.toFixed(2)}px`);
    root.style.setProperty("--raised-relief-highlight-offset-y", `${tickRelief.highlight.y.toFixed(2)}px`);
    root.style.setProperty("--raised-relief-shadow-offset-x", `${tickRelief.shadow.x.toFixed(2)}px`);
    root.style.setProperty("--raised-relief-shadow-offset-y", `${tickRelief.shadow.y.toFixed(2)}px`);
  }

  applyLightingCssVars();

  function renderClassicShellDefinitions(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const detail = shellDetail(options.detail);
    const { cx, cy } = viewBox;
    const { faceRadius } = profile.dial;
    const { outerLip, innerShelf } = profile.bezel;
    const sheenLine = lightLine(cx, cy, faceRadius * 0.58, geometry.oppositeAngle(LIGHTING.directionDeg));

    return `
      <defs>
        <radialGradient id="${ids.faceGradient}" cx="46%" cy="42%" r="66%">
          <stop offset="0%" stop-color="var(--face-core)" />
          <stop offset="58%" stop-color="var(--face-mid)" />
          <stop offset="100%" stop-color="var(--face-edge)" />
        </radialGradient>
        <linearGradient id="${ids.faceSheen}" x1="${fmt(sheenLine.x1)}" y1="${fmt(sheenLine.y1)}" x2="${fmt(sheenLine.x2)}" y2="${fmt(sheenLine.y2)}" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--face-sheen)" />
          <stop offset="38%" stop-color="var(--face-sheen-mid)" />
          <stop offset="100%" stop-color="var(--face-sheen-edge)" />
        </linearGradient>
        <radialGradient id="${ids.faceEdgeShadow}" cx="50%" cy="50%" r="50%">
          <stop offset="95%" stop-color="var(--face-edge-shadow)" stop-opacity="0" />
          <stop offset="100%" stop-color="var(--face-edge-shadow)" stop-opacity="1" />
        </radialGradient>
        ${arcFadeGradient(ids.outerLipHighlightFade, cx, cy, outerLip.highlight.radius, outerLip.highlight.zone, "var(--bezel-highlight)")}
        ${arcFadeGradient(ids.innerShelfHighlightFade, cx, cy, innerShelf.highlight.radius, innerShelf.highlight.zone, "var(--inner-shelf-highlight)")}
        ${detail.faceTexture ? `
        <pattern id="${ids.faceGrain}" width="9" height="9" patternUnits="userSpaceOnUse">
          <path d="M 1 1 H 1.35 M 6.6 2.2 H 7.05 M 3.2 5.9 H 3.55 M 8.2 7.4 H 8.55" stroke="var(--face-grain)" stroke-width="0.5" stroke-linecap="round" />
          <path d="M 4.9 0.7 H 5.1 M 1.9 7.8 H 2.15 M 7.3 5.1 H 7.55 M 4.1 3.4 H 4.28" stroke="var(--face-grain)" stroke-width="0.28" stroke-linecap="round" />
        </pattern>
        <pattern id="${ids.faceMottle}" width="21" height="17" patternUnits="userSpaceOnUse">
          <path d="M 2.2 4.2 C 4.6 3.1 6.1 4.8 8.4 3.6 M 12.8 12.6 C 15.1 11.2 17.8 12.4 19.2 10.7" stroke="var(--face-mottle)" stroke-width="0.55" stroke-linecap="round" fill="none" />
          <path d="M 5.3 13.8 C 6.4 13.2 7.2 13.9 8.4 13.1 M 14.8 5.8 C 15.7 5.1 16.6 5.5 17.5 4.8" stroke="var(--face-mottle)" stroke-width="0.38" stroke-linecap="round" fill="none" />
        </pattern>` : ""}
        <clipPath id="${ids.faceClip}">
          <circle cx="${cx}" cy="${cy}" r="${faceRadius}" />
        </clipPath>
      </defs>
    `;
  }

  function renderClassicShellFace(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const detail = shellDetail(options.detail);
    const contentMarkup = options.contentMarkup || "";
    const { cx, cy } = viewBox;
    const { faceRadius } = profile.dial;
    return `
      <circle class="face" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceGradient})" />
      ${contentMarkup}
      <circle class="face-sheen" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceSheen})" />
      ${detail.faceTexture ? `<circle class="face-mottle" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceMottle})" clip-path="url(#${ids.faceClip})" />` : ""}
      ${detail.faceTexture ? `<circle class="face-grain" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceGrain})" clip-path="url(#${ids.faceClip})" />` : ""}
      ${detail.faceEdgeShadow ? `<circle class="face-edge-shadow" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceEdgeShadow})" />` : ""}
    `;
  }

  function renderClassicShellBezel(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const { cx, cy } = viewBox;
    const { outerLip, innerShelf } = profile.bezel;
    const { faceRadius } = profile.dial;
    return `
      <path class="${outerLip.materialClass}" d="${geometry.ringPath(cx, cy, outerLip.outerRadius, outerLip.innerRadius)}" />
      <path class="${outerLip.highlight.className}" style="stroke:url(#${ids.outerLipHighlightFade})" d="${geometry.arcPath(cx, cy, outerLip.highlight.radius, outerLip.highlight.zone.startAngle, outerLip.highlight.zone.endAngle)}" />
      <path class="${innerShelf.materialClass}" d="${geometry.ringPath(cx, cy, innerShelf.outerRadius, innerShelf.innerRadius)}" />
      <path class="${innerShelf.highlight.className}" style="stroke:url(#${ids.innerShelfHighlightFade})" d="${geometry.arcPath(cx, cy, innerShelf.highlight.radius, innerShelf.highlight.zone.startAngle, innerShelf.highlight.zone.endAngle)}" />
      <path class="${innerShelf.shadow.className}" d="${geometry.arcPath(cx, cy, innerShelf.shadow.radius, innerShelf.shadow.zone.startAngle, innerShelf.shadow.zone.endAngle)}" />
      <circle class="face-rim" cx="${cx}" cy="${cy}" r="${faceRadius}" />
    `;
  }

  function renderClassicShellHub(options = {}) {
    const { profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const { cx, cy } = viewBox;
    const { outerRadius, highlight, shadow } = profile.hub;
    return `
      <g class="hub">
        <circle class="hub-outer" cx="${cx}" cy="${cy}" r="${outerRadius}" />
        <path class="${highlight.className}" d="${geometry.arcPath(cx, cy, highlight.radius, highlight.zone.startAngle, highlight.zone.endAngle)}" />
        <path class="${shadow.className}" d="${geometry.arcPath(cx, cy, shadow.radius, shadow.zone.startAngle, shadow.zone.endAngle)}" />
      </g>
    `;
  }

  function renderClassicShellGlass() {
    return "";
  }

  function classicShellIds(instanceId) {
    const defsId = instanceId || `analog-gauge-${++defsCounter}`;
    return {
      faceGradient: `${defsId}-face-gradient`,
      faceSheen: `${defsId}-face-sheen`,
      faceEdgeShadow: `${defsId}-face-edge-shadow`,
      faceGrain: `${defsId}-face-grain`,
      faceMottle: `${defsId}-face-mottle`,
      faceClip: `${defsId}-face-clip`,
      outerLipHighlightFade: `${defsId}-outer-lip-highlight-fade`,
      innerShelfHighlightFade: `${defsId}-inner-shelf-highlight-fade`,
    };
  }

  window.DashPilotAnalogShell = {
    VIEWBOX,
    SURFACE,
    LIGHTING,
    CLASSIC_911_SHELL,
    SPEED_SCALE_PROFILES,
    applyLightingCssVars,
    escapeHtml,
    reliefOffsetForSurface,
    renderClassicShellDefinitions,
    renderClassicShellFace,
    renderClassicShellBezel,
    renderClassicShellHub,
    renderClassicShellGlass,
  };
})();
