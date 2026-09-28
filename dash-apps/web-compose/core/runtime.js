// Runtime: mounts the layout into a CSS grid and runs the tick.
//
// One tick = read the host once, compute only the signals the current layout
// uses, diff them against the previous tick, and call update() on the widgets
// whose signals changed. Widgets write only inside their own cell, so a tick
// never touches the grid container itself.
//
// Anything that changes what a widget *is* — orientation, units, language,
// the layout document — remounts instead of updating. Those are rare.
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  let root = null;
  let doc = null;
  let instances = [];
  let usedSignals = [];
  const lastValues = new Map();

  let mounted = false;
  let raf = 0;
  let pendingNative = false;
  let pendingMessage = null;
  const raw = {};

  let orientation = "landscape";
  let imperial = false;
  let theme = "";
  let localeTag = "";

  function portraitQuery() {
    return typeof window.matchMedia === "function" ? window.matchMedia("(orientation: portrait)") : null;
  }

  function loadDocument() {
    const saved = DC.layout.normalize(DC.host.loadLayout());
    return saved || DC.layout.normalize(DC.defaultLayout);
  }

  function reportWidgetError(instance, phase, error) {
    if (instance.failed) return;
    instance.failed = true;
    instance.cell.classList.add("cell--failed");
    console.error("[compose] widget " + instance.item.type + " (" + instance.item.uid + ") failed in " + phase, error);
  }

  function createCell(item) {
    const cell = document.createElement("div");
    cell.className = "cell";
    cell.dataset.uid = item.uid;
    cell.dataset.type = item.type;
    cell.style.gridArea = (item.y + 1) + " / " + (item.x + 1) + " / span " + item.h + " / span " + item.w;
    // The body is the size container; the widget renders into an inner root so
    // its own @container rules can match it (a container can't query itself).
    const body = document.createElement("div");
    body.className = "cell__body";
    cell.appendChild(body);
    return { cell, body };
  }

  function mountPlaceholder(body, item) {
    body.classList.add("cell__body--unknown");
    const label = document.createElement("span");
    label.textContent = DC.t("widget.unknown");
    body.appendChild(label);
  }

  function mountItem(item) {
    const { cell, body } = createCell(item);
    root.appendChild(cell);
    const entry = DC.registry.get(item.type);
    if (!entry) {
      mountPlaceholder(body, item);
      return null;
    }

    const binds = DC.registry.resolveBinds(entry.descriptor, item.bind);
    const props = DC.registry.resolveProps(entry.descriptor, item.props);
    const deps = [];
    for (const key in binds) if (binds[key]) deps.push(binds[key].id);

    const el = document.createElement("div");
    el.className = "widget w-" + item.type;
    body.appendChild(el);

    const instance = { item, cell, binds, deps, widget: null, failed: false, values: {} };
    const ctx = {
      el,
      props,
      binds,
      size: { w: item.w, h: item.h },
      imperial,
      t: DC.t,
      label: (key) => (binds[key] ? DC.signals.label(binds[key]) : ""),
      unit: (key) => (binds[key] ? DC.signals.unitLabel(binds[key], imperial) : ""),
      format: (key, value, precision) => (binds[key] ? DC.signals.format(binds[key], value, precision) : DC.signals.MISSING),
      range: (key) => (binds[key] ? DC.signals.range(binds[key], raw, imperial) : null),
    };
    try {
      instance.widget = entry.create(ctx) || {};
    } catch (error) {
      reportWidgetError(instance, "create", error);
    }
    return instance;
  }

  function destroyAll() {
    for (const instance of instances) {
      if (instance.failed || !instance.widget || typeof instance.widget.destroy !== "function") continue;
      try {
        instance.widget.destroy();
      } catch (error) {
        reportWidgetError(instance, "destroy", error);
      }
    }
    instances = [];
    usedSignals = [];
    lastValues.clear();
    root.textContent = "";
  }

  function mount() {
    destroyAll();
    const grid = DC.layout.gridFor(doc, orientation);
    root.style.setProperty("--cols", grid.cols);
    root.style.setProperty("--rows", grid.rows);
    root.style.setProperty("--gap", doc.gap + "px");

    const used = new Set();
    for (const item of grid.items) {
      const instance = mountItem(item);
      if (!instance) continue;
      instances.push(instance);
      for (const id of instance.deps) used.add(id);
    }
    usedSignals = Array.from(used, (id) => DC.signals.get(id));
    // First tick after a mount pushes every value, not just changes.
    tick(true);
    if (DC.onMounted) DC.onMounted();
  }

  function applyTheme() {
    const next = raw.darkMode === false ? "light" : "dark";
    if (next === theme) return;
    theme = next;
    document.documentElement.dataset.theme = next;
  }

  // Changes that alter labels or units need a remount; returns true if one happened.
  function applyEnvironment() {
    const nextImperial = raw.useImperial === true || Number(raw.useImperial) > 0;
    const nextLocale = DC.host.locale(raw);
    if (nextImperial === imperial && nextLocale === localeTag) return false;
    imperial = nextImperial;
    if (nextLocale !== localeTag) {
      localeTag = nextLocale;
      DC.i18n.setLocale(nextLocale);
      document.documentElement.lang = DC.i18n.locale();
    }
    mount();
    return true;
  }

  function tick(force) {
    const dirty = force ? null : new Set();
    for (const signal of usedSignals) {
      const value = signal.read(raw);
      if (!force && lastValues.get(signal.id) === value) continue;
      lastValues.set(signal.id, value);
      if (dirty) dirty.add(signal.id);
    }
    if (dirty && dirty.size === 0) return;

    for (const instance of instances) {
      if (instance.failed || typeof instance.widget.update !== "function") continue;
      if (dirty && !instance.deps.some((id) => dirty.has(id))) continue;
      const values = instance.values;
      for (const key in instance.binds) {
        const signal = instance.binds[key];
        values[key] = signal ? lastValues.get(signal.id) : undefined;
      }
      try {
        instance.widget.update(values);
      } catch (error) {
        reportWidgetError(instance, "update", error);
      }
    }
  }

  function flush() {
    raf = 0;
    if (pendingNative) {
      pendingNative = false;
      DC.host.readNative(raw);
    }
    if (pendingMessage) {
      DC.host.readPushed(pendingMessage, raw);
      pendingMessage = null;
    }
    applyTheme();
    // applyEnvironment() remounts (which ticks) when units or language changed.
    if (!applyEnvironment()) tick(false);
    if (DC.onFrame) DC.onFrame();
  }

  function schedule() {
    if (!raf) raf = requestAnimationFrame(flush);
  }

  function boot() {
    if (mounted) return;
    mounted = true;
    root = document.querySelector("[data-compose-root]");
    localeTag = DC.host.locale(raw);
    DC.i18n.setLocale(localeTag);
    document.documentElement.lang = DC.i18n.locale();
    doc = loadDocument();

    const query = portraitQuery();
    orientation = query && query.matches ? "portrait" : "landscape";
    if (query && typeof query.addEventListener === "function") {
      query.addEventListener("change", (event) => {
        orientation = event.matches ? "portrait" : "landscape";
        mount();
      });
    }

    mount();
    if (DC.host.hasNative()) {
      pendingNative = true;
      schedule();
    }
  }

  // Android: getters on window.NativeCarState changed.
  window.onCarStateUpdate = function () {
    pendingNative = true;
    schedule();
  };

  // iOS (and demo mode): one pushed state object per tick.
  window.receiveMessage = function (message) {
    if (!message || typeof message !== "object") return;
    pendingMessage = message;
    schedule();
  };

  // Everything the editor needs: read the document, replace it, know the grid.
  DC.runtime = {
    doc: () => doc,
    grid: () => DC.layout.gridFor(doc, orientation),
    orientation: () => orientation,
    root: () => root,
    raw: () => raw,
    // Never persists: the editor saves once, on Done.
    setDoc(next) {
      doc = next;
      mount();
    },
    save: () => DC.host.saveLayout(JSON.stringify(doc)),
    remount: mount,
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot, { once: true });
  } else {
    boot();
  }
})();
