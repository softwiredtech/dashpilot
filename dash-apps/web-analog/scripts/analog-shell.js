(function () {
  const geometry = window.DashPilotAnalogGeometry;
  const fmt = geometry.fmt;

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

  /*
   * Glossary:
   * bezel: raised outer lip ring
   * shelf: recessed ring inside the bezel
   * rim: thin stroke at the face edge
   * face: dial plate
   * print: ticks, numbers, labels, telltales
   * relief: paired highlight and shadow offsets used to fake depth
   */

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
    const shelfHighlightZone = geometry.zoneAround(geometry.oppositeAngle(LIGHTING.directionDeg), LIGHTING.recessedArcSpreadDeg);
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
      shelf: Object.freeze({
        surface: SURFACE.recessed,
        outerRadius: 90.85,
        innerRadius: 89.35,
        materialClass: "shelf",
        highlight: Object.freeze({
          radius: 94.0,
          zone: Object.freeze({ ...shelfHighlightZone }),
          className: "shelf-highlight",
        }),
        shadow: Object.freeze({
          radius: 92.2,
          zone: Object.freeze({ ...geometry.oppositeZone(shelfHighlightZone) }),
          className: "shelf-shadow",
        }),
      }),
    });

    return Object.freeze({
      bezel,
      dial: Object.freeze({
        faceInset: 0.15,
        faceRadius: bezel.shelf.innerRadius - 0.15,
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

    const oppositeDirectionDeg = geometry.oppositeAngle(LIGHTING.directionDeg);
    const backdropRaisedHighlight = directionalPoint(LIGHTING.directionDeg, 18);
    const backdropRecessedHighlight = directionalPoint(oppositeDirectionDeg, 18);
    const backdropRecessedShadow = directionalPoint(LIGHTING.directionDeg, 18);

    root.style.setProperty("--lighting-direction-deg", `${LIGHTING.directionDeg}deg`);
    root.style.setProperty("--backdrop-raised-highlight-x", backdropRaisedHighlight.x);
    root.style.setProperty("--backdrop-raised-highlight-y", backdropRaisedHighlight.y);
    root.style.setProperty("--backdrop-recessed-highlight-x", backdropRecessedHighlight.x);
    root.style.setProperty("--backdrop-recessed-highlight-y", backdropRecessedHighlight.y);
    root.style.setProperty("--backdrop-recessed-shadow-x", backdropRecessedShadow.x);
    root.style.setProperty("--backdrop-recessed-shadow-y", backdropRecessedShadow.y);
  }

  applyLightingCssVars();

  function renderClassicShellDefinitions(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const { cx, cy } = viewBox;
    const { faceRadius } = profile.dial;
    const { outerLip, shelf } = profile.bezel;
    const sheenLine = lightLine(cx, cy, faceRadius * 0.58, geometry.oppositeAngle(LIGHTING.directionDeg));

    return `
      <defs>
        <radialGradient id="${ids.faceGradient}" cx="46%" cy="42%" r="66%">
          <stop offset="0%" stop-color="var(--dial-face-gradient-start)" />
          <stop offset="58%" stop-color="var(--dial-face-gradient-mid)" />
          <stop offset="100%" stop-color="var(--dial-face-gradient-end)" />
        </radialGradient>
        <linearGradient id="${ids.faceSheen}" x1="${fmt(sheenLine.x1)}" y1="${fmt(sheenLine.y1)}" x2="${fmt(sheenLine.x2)}" y2="${fmt(sheenLine.y2)}" gradientUnits="userSpaceOnUse">
          <stop offset="0%" stop-color="var(--dial-face-sheen-start)" />
          <stop offset="38%" stop-color="var(--dial-face-sheen-mid)" />
          <stop offset="100%" stop-color="var(--dial-face-sheen-end)" />
        </linearGradient>
        <radialGradient id="${ids.faceEdgeShadow}" cx="50%" cy="50%" r="50%">
          <stop offset="95%" stop-color="var(--dial-face-edge-shadow-stroke)" stop-opacity="0" />
          <stop offset="100%" stop-color="var(--dial-face-edge-shadow-stroke)" stop-opacity="1" />
        </radialGradient>
        ${arcFadeGradient(ids.outerLipHighlightFade, cx, cy, outerLip.highlight.radius, outerLip.highlight.zone, "var(--bezel-ring-highlight)")}
        ${arcFadeGradient(ids.shelfHighlightFade, cx, cy, shelf.highlight.radius, shelf.highlight.zone, "var(--shelf-highlight)")}
        <pattern id="${ids.faceGrain}" width="9" height="9" patternUnits="userSpaceOnUse">
          <path d="M 1 1 H 1.35 M 6.6 2.2 H 7.05 M 3.2 5.9 H 3.55 M 8.2 7.4 H 8.55" stroke="var(--dial-face-grain-stroke)" stroke-width="0.5" stroke-linecap="round" />
          <path d="M 4.9 0.7 H 5.1 M 1.9 7.8 H 2.15 M 7.3 5.1 H 7.55 M 4.1 3.4 H 4.28" stroke="var(--dial-face-grain-stroke)" stroke-width="0.28" stroke-linecap="round" />
        </pattern>
        <pattern id="${ids.faceMottle}" width="21" height="17" patternUnits="userSpaceOnUse">
          <path d="M 2.2 4.2 C 4.6 3.1 6.1 4.8 8.4 3.6 M 12.8 12.6 C 15.1 11.2 17.8 12.4 19.2 10.7" stroke="var(--dial-face-mottle-stroke)" stroke-width="0.55" stroke-linecap="round" fill="none" />
          <path d="M 5.3 13.8 C 6.4 13.2 7.2 13.9 8.4 13.1 M 14.8 5.8 C 15.7 5.1 16.6 5.5 17.5 4.8" stroke="var(--dial-face-mottle-stroke)" stroke-width="0.38" stroke-linecap="round" fill="none" />
        </pattern>
        <clipPath id="${ids.faceClip}">
          <circle cx="${cx}" cy="${cy}" r="${faceRadius}" />
        </clipPath>
      </defs>
    `;
  }

  function renderClassicShellFace(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const contentMarkup = options.contentMarkup || "";
    const { cx, cy } = viewBox;
    const { faceRadius } = profile.dial;
    return `
      <circle class="face" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceGradient})" />
      ${contentMarkup}
      <circle class="face-sheen" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceSheen})" />
      <circle class="face-mottle" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceMottle})" clip-path="url(#${ids.faceClip})" />
      <circle class="face-grain" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceGrain})" clip-path="url(#${ids.faceClip})" />
      <circle class="face-edge-shadow" cx="${cx}" cy="${cy}" r="${faceRadius}" fill="url(#${ids.faceEdgeShadow})" />
    `;
  }

  function renderClassicShellBezel(options = {}) {
    const { ids, profile = CLASSIC_911_SHELL, viewBox = VIEWBOX } = options;
    const { cx, cy } = viewBox;
    const { outerLip, shelf } = profile.bezel;
    const { faceRadius } = profile.dial;
    return `
      <path class="${outerLip.materialClass}" d="${geometry.ringPath(cx, cy, outerLip.outerRadius, outerLip.innerRadius)}" />
      <path class="${outerLip.highlight.className}" style="stroke:url(#${ids.outerLipHighlightFade})" d="${geometry.arcPath(cx, cy, outerLip.highlight.radius, outerLip.highlight.zone.startAngle, outerLip.highlight.zone.endAngle)}" />
      <path class="${shelf.materialClass}" d="${geometry.ringPath(cx, cy, shelf.outerRadius, shelf.innerRadius)}" />
      <path class="${shelf.highlight.className}" style="stroke:url(#${ids.shelfHighlightFade})" d="${geometry.arcPath(cx, cy, shelf.highlight.radius, shelf.highlight.zone.startAngle, shelf.highlight.zone.endAngle)}" />
      <path class="${shelf.shadow.className}" d="${geometry.arcPath(cx, cy, shelf.shadow.radius, shelf.shadow.zone.startAngle, shelf.shadow.zone.endAngle)}" />
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
  };
})();
