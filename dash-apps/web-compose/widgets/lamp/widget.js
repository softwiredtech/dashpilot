// On/off indicator for any bool signal. The whole visual state is one
// data-on attribute on the cell body; colors and blinking live in CSS.
function create(ctx) {
  const { el, props } = ctx;
  const ICONS = {
    dot: '<circle cx="12" cy="12" r="7"/>',
    "arrow-left": '<path d="M10 4 2 12l8 8v-5h12V9H10z"/>',
    "arrow-right": '<path d="m14 4 8 8-8 8v-5H2V9h12z"/>',
    warning: '<path d="M12 2 1 21h22L12 2zm-1 7h2v6h-2V9zm0 8h2v2h-2v-2z"/>',
    wheel: '<path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 3a7 7 0 0 1 6.9 6h-3.2a3.8 3.8 0 0 0-7.4 0H5.1A7 7 0 0 1 12 5zm-6.9 8h3.3a4 4 0 0 0 2.6 2.6v3.3A7 7 0 0 1 5.1 13zm7.9 5.9v-3.3a4 4 0 0 0 2.6-2.6h3.3a7 7 0 0 1-5.9 5.9z"/>',
  };

  el.style.setProperty("--lamp-on", props.color);
  el.dataset.on = "0";
  if (props.blink) el.dataset.blink = "1";
  if (props.hideWhenOff) el.dataset.hideOff = "1";

  const icon = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  icon.setAttribute("viewBox", "0 0 24 24");
  icon.setAttribute("class", "lamp__icon");
  icon.setAttribute("aria-hidden", "true");
  icon.innerHTML = ICONS[props.icon] || ICONS.dot;
  el.appendChild(icon);

  if (props.showLabel) {
    const label = document.createElement("div");
    label.className = "lamp__label";
    label.textContent = props.label || ctx.label("value");
    el.appendChild(label);
  }

  const steers = props.icon === "wheel";
  if (steers) {
    el.dataset.steer = "1";
    icon.style.transform = "rotate(0deg)";
  }
  let last = "0";
  let lastAngle = 0;
  return {
    update(values) {
      const next = values.value ? "1" : "0";
      if (next !== last) {
        last = next;
        el.dataset.on = next;
      }
      if (!steers) return;
      const angle = values.steering === undefined ? 0 : Math.round(values.steering);
      if (angle === lastAngle) return;
      lastAngle = angle;
      icon.style.transform = "rotate(" + angle + "deg)";
    },
  };
}
