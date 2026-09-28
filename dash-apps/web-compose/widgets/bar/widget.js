// Horizontal bar. Ranges that cross zero (power: regen vs. discharge) fill
// from the zero point outwards. One transform write per visible change.
function create(ctx) {
  const { el, props } = ctx;
  if (props.accent) el.style.setProperty("--accent", props.accent);

  const head = document.createElement("div");
  head.className = "bar__head";
  if (props.showLabel) {
    const label = document.createElement("span");
    label.className = "bar__label";
    label.textContent = props.label || ctx.label("value");
    head.appendChild(label);
  }

  let text = null;
  if (props.showValue) {
    const value = document.createElement("span");
    value.className = "bar__value";
    text = document.createTextNode("");
    const unit = document.createElement("span");
    unit.className = "bar__unit";
    unit.textContent = ctx.unit("value");
    value.append(text, unit);
    head.appendChild(value);
    el.style.setProperty("--unit-chars", unit.textContent.length);
  }

  const track = document.createElement("div");
  track.className = "bar__track";
  const fill = document.createElement("div");
  fill.className = "bar__fill";
  track.appendChild(fill);
  if (head.firstChild) el.append(head);
  else el.dataset.bare = "1";
  el.append(track);

  function bounds() {
    const range = ctx.range("value") || [0, 100];
    const min = props.min ?? range[0];
    const max = props.max ?? range[1];
    return max > min ? [min, max] : [0, 1];
  }

  const ratio = (v, [min, max]) => Math.min(1, Math.max(0, (v - min) / (max - min)));
  // 0.2 % steps: finer changes are invisible and would only cost writes.
  const quantize = (r) => Math.round(r * 500) / 500;

  let lastText = null, lastLength = -1;
  let lastTransform = null;
  return {
    update(values) {
      const v = values.value;
      if (text) {
        const next = ctx.format("value", v, props.precision ?? undefined);
        if (next !== lastText) {
          lastText = next;
          text.nodeValue = next;
          if (next.length !== lastLength) {
            lastLength = next.length;
            el.style.setProperty("--chars", next.length);
          }
        }
      }
      const b = bounds();
      let start = 0, end = 0;
      if (v !== undefined) {
        const zero = ratio(0, b);
        const at = ratio(v, b);
        start = quantize(Math.min(zero, at));
        end = quantize(Math.max(zero, at));
      }
      const transform = "translateX(" + start * 100 + "%) scaleX(" + (end - start) + ")";
      if (transform !== lastTransform) {
        lastTransform = transform;
        fill.style.transform = transform;
      }
    },
  };
}
