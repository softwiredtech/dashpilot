(function () {
  const geometry = {
    clamp(value, min, max) {
      return Math.min(max, Math.max(min, value));
    },

    fmt(value, digits = 2) {
      return Number(value).toFixed(digits);
    },

    degToRad(deg) {
      return deg * Math.PI / 180;
    },

    // Gauge angles are 0 at 12 o'clock and increase clockwise.
    gaugeDegToRad(deg) {
      return (deg - 90) * Math.PI / 180;
    },

    oppositeAngle(angle) {
      return (angle + 180) % 360;
    },

    zoneAround(centerAngle, spreadAngle) {
      return {
        startAngle: centerAngle - spreadAngle / 2,
        endAngle: centerAngle + spreadAngle / 2,
      };
    },

    oppositeZone(zone) {
      return {
        startAngle: geometry.oppositeAngle(zone.startAngle),
        endAngle: geometry.oppositeAngle(zone.endAngle),
      };
    },

    edgeForDirection(directionDeg) {
      const normalized = ((directionDeg % 360) + 360) % 360;
      return ["top", "right", "bottom", "left"][Math.round(normalized / 90) % 4];
    },

    polar(cx, cy, radius, angleDeg) {
      const radians = geometry.gaugeDegToRad(angleDeg);
      return {
        x: cx + radius * Math.cos(radians),
        y: cy + radius * Math.sin(radians),
      };
    },

    segment(cx, cy, innerRadius, outerRadius, angleDeg) {
      if (![cx, cy, innerRadius, outerRadius, angleDeg].every(Number.isFinite)) return "";
      const start = geometry.polar(cx, cy, innerRadius, angleDeg);
      const end = geometry.polar(cx, cy, outerRadius, angleDeg);
      return `M ${geometry.fmt(start.x)} ${geometry.fmt(start.y)} L ${geometry.fmt(end.x)} ${geometry.fmt(end.y)}`;
    },

    rotationTransform(angle, cx, cy) {
      return `rotate(${angle} ${cx} ${cy})`;
    },

    ringPath(cx, cy, outerRadius, innerRadius) {
      if (![cx, cy, outerRadius, innerRadius].every(Number.isFinite)) return "";
      if (!(outerRadius > innerRadius && innerRadius > 0)) return "";

      const outerStart = geometry.polar(cx, cy, outerRadius, 0);
      const outerEnd = geometry.polar(cx, cy, outerRadius, 180);
      const innerStart = geometry.polar(cx, cy, innerRadius, 0);
      const innerEnd = geometry.polar(cx, cy, innerRadius, 180);

      return [
        `M ${geometry.fmt(outerStart.x)} ${geometry.fmt(outerStart.y)}`,
        `A ${geometry.fmt(outerRadius)} ${geometry.fmt(outerRadius)} 0 1 1 ${geometry.fmt(outerEnd.x)} ${geometry.fmt(outerEnd.y)}`,
        `A ${geometry.fmt(outerRadius)} ${geometry.fmt(outerRadius)} 0 1 1 ${geometry.fmt(outerStart.x)} ${geometry.fmt(outerStart.y)}`,
        `M ${geometry.fmt(innerStart.x)} ${geometry.fmt(innerStart.y)}`,
        `A ${geometry.fmt(innerRadius)} ${geometry.fmt(innerRadius)} 0 1 0 ${geometry.fmt(innerEnd.x)} ${geometry.fmt(innerEnd.y)}`,
        `A ${geometry.fmt(innerRadius)} ${geometry.fmt(innerRadius)} 0 1 0 ${geometry.fmt(innerStart.x)} ${geometry.fmt(innerStart.y)}`,
        "Z",
      ].join(" ");
    },

    arcPath(cx, cy, radius, startAngle, endAngle) {
      if (![cx, cy, radius, startAngle, endAngle].every(Number.isFinite) || radius <= 0) return "";

      let normalizedEnd = endAngle;
      while (normalizedEnd <= startAngle) normalizedEnd += 360;

      const start = geometry.polar(cx, cy, radius, startAngle);
      const end = geometry.polar(cx, cy, radius, normalizedEnd);
      const largeArcFlag = normalizedEnd - startAngle > 180 ? "1" : "0";

      return `M ${geometry.fmt(start.x)} ${geometry.fmt(start.y)} A ${geometry.fmt(radius)} ${geometry.fmt(radius)} 0 ${largeArcFlag} 1 ${geometry.fmt(end.x)} ${geometry.fmt(end.y)}`;
    },

    valueToAngle(value, min, max, startAngle, sweepAngle) {
      if (![value, min, max, startAngle, sweepAngle].every(Number.isFinite) || max <= min) return NaN;
      const t = geometry.clamp((value - min) / (max - min), 0, 1);
      return startAngle + (sweepAngle * t);
    },

    angleToValue(angle, min, max, startAngle, sweepAngle) {
      if (![angle, min, max, startAngle, sweepAngle].every(Number.isFinite) || max <= min || sweepAngle === 0) return NaN;
      const t = geometry.clamp((angle - startAngle) / sweepAngle, 0, 1);
      return min + (max - min) * t;
    },

  };

  window.DashPilotAnalogGeometry = geometry;
})();
