// Label, big value, unit. Layout adapts to the cell through container queries
// in widget.css; this file only writes the value text.
function create(ctx) {
  const { el, props } = ctx;
  el.dataset.align = props.align;
  if (props.accent) el.style.setProperty("--accent", props.accent);

  let label = null;
  if (props.showLabel) {
    label = document.createElement("div");
    label.className = "readout__label";
    label.textContent = props.label || ctx.label("value");
  } else {
    el.dataset.bare = "1";
  }

  const line = document.createElement("div");
  line.className = "readout__line";
  const value = document.createElement("span");
  value.className = "readout__value";
  const text = document.createTextNode("");
  value.appendChild(text);
  const unit = document.createElement("span");
  unit.className = "readout__unit";
  unit.textContent = ctx.unit("value");
  line.append(value, unit);

  if (label) el.append(label);
  el.append(line);
  el.style.setProperty("--unit-chars", unit.textContent.length);

  // --chars drives the font size in widget.css so long values (odometer) fit.
  // It changes only when the text length does, not every tick.
  let last = null, lastLength = -1;
  return {
    update(values) {
      const next = ctx.format("value", values.value, props.precision ?? undefined);
      if (next === last) return;
      last = next;
      text.nodeValue = next;
      if (next.length !== lastLength) {
        lastLength = next.length;
        el.style.setProperty("--chars", next.length);
      }
    },
  };
}
