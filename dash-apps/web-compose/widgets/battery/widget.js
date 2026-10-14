// Battery icon: a shell whose fill follows the signal and changes color as the
// charge drops (low <= 20 %, critical <= 10 %, like the car's own indicator).
// The fill is one transform and the color one data attribute, each written
// only when it changes.
const LOW = 20, CRITICAL = 10;

function create(ctx) {
  const { el, props } = ctx;
  if (props.accent) el.style.setProperty("--accent", props.accent);

  let label = null;
  if (props.showLabel) {
    label = document.createElement("div");
    label.className = "battery__label";
    label.textContent = props.label || ctx.label("value");
  } else {
    el.dataset.bare = "1";
  }

  const icon = document.createElement("div");
  icon.className = "battery__icon";
  const shell = document.createElement("div");
  shell.className = "battery__shell";
  const fill = document.createElement("div");
  fill.className = "battery__fill";
  shell.appendChild(fill);
  const cap = document.createElement("div");
  cap.className = "battery__cap";
  icon.append(shell, cap);

  const row = document.createElement("div");
  row.className = "battery__row";
  row.appendChild(icon);

  let text = null;
  if (props.showValue) {
    const value = document.createElement("span");
    value.className = "battery__value";
    text = document.createTextNode("");
    const unit = document.createElement("span");
    unit.className = "battery__unit";
    unit.textContent = ctx.unit("value");
    value.append(text, unit);
    row.appendChild(value);
  }

  if (label) el.append(label);
  el.append(row);
  el.dataset.level = "none";

  const ratio = (v) => {
    const [min, max] = ctx.range("value") || [0, 100];
    return max > min ? Math.min(1, Math.max(0, (v - min) / (max - min))) : 0;
  };

  let lastText = null, lastTransform = null, lastLevel = "none";
  return {
    update(values) {
      const v = values.value;
      const r = v === undefined ? 0 : Math.round(ratio(v) * 100) / 100;
      const transform = "scaleX(" + r + ")";
      if (transform !== lastTransform) {
        lastTransform = transform;
        fill.style.transform = transform;
      }
      const level = v === undefined ? "none" : r * 100 <= CRITICAL ? "critical" : r * 100 <= LOW ? "low" : "ok";
      if (level !== lastLevel) {
        lastLevel = level;
        el.dataset.level = level;
      }
      if (text) {
        const next = ctx.format("value", v);
        if (next !== lastText) {
          lastText = next;
          text.nodeValue = next;
        }
      }
    },
  };
}
