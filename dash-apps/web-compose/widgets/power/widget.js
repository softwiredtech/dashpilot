// Regen / discharge bar as in web-vanilla: zero in the middle, fed a signed
// -100..100 level. Orientation follows the cell shape (CSS), JS only writes --start / --len.
function create(ctx) {
  const { el, props } = ctx;
  if (props.regenColor) el.style.setProperty("--regen", props.regenColor);
  if (props.driveColor) el.style.setProperty("--drive", props.driveColor);

  let text = null;
  if (props.showLabel || props.showValue) {
    const head = document.createElement("div");
    head.className = "power__head";
    if (props.showLabel) {
      const label = document.createElement("span");
      label.className = "power__label";
      label.textContent = props.label || ctx.label("value");
      head.appendChild(label);
    }
    if (props.showValue) {
      const value = document.createElement("span");
      value.className = "power__value";
      text = document.createTextNode("");
      const unit = document.createElement("span");
      unit.className = "power__unit";
      unit.textContent = ctx.unit("value");
      value.append(text, unit);
      head.appendChild(value);
    }
    el.appendChild(head);
  }

  const track = document.createElement("div");
  track.className = "power__track";
  const center = document.createElement("div");
  center.className = "power__center";
  const fill = document.createElement("div");
  fill.className = "power__fill";
  track.append(fill, center);
  el.appendChild(track);

  const quantize = (r) => Math.round(r * 500) / 500;

  function span(v) {
    const half = Math.min(1, Math.abs(v) / 100) * 0.5;
    return v < 0 ? [0.5 - half, 0.5] : [0.5, 0.5 + half];
  }

  let lastText = null, lastStart = null, lastLen = null, lastRegen = null;
  return {
    update(values) {
      const v = values.value;
      if (text) {
        const next = ctx.format("value", v, undefined);
        if (next !== lastText) {
          lastText = next;
          text.nodeValue = next;
        }
      }
      const [a, b] = v === undefined ? [0.5, 0.5] : span(v);
      const start = quantize(a), len = quantize(b - a);
      if (start !== lastStart) {
        lastStart = start;
        el.style.setProperty("--start", start);
      }
      if (len !== lastLen) {
        lastLen = len;
        el.style.setProperty("--len", len);
      }
      const regen = v < 0 ? "1" : "0";
      if (regen !== lastRegen) {
        lastRegen = regen;
        el.dataset.regen = regen;
      }
    },
  };
}
