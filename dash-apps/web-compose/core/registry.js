// Widget registry. Widgets register themselves from widgets/_generated.js,
// which scripts/build-compose-widgets.js assembles from widgets/*/ — so adding
// a widget never means editing this file or index.html.
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  const widgets = new Map();

  function registerWidget(descriptor, create) {
    if (!descriptor || typeof descriptor.type !== "string" || typeof create !== "function") {
      console.error("[compose] invalid widget registration", descriptor && descriptor.type);
      return;
    }
    if (widgets.has(descriptor.type)) {
      console.error("[compose] duplicate widget type", descriptor.type);
      return;
    }
    widgets.set(descriptor.type, { descriptor, create });
  }

  // Descriptor defaults first, then whatever the layout stored. Keys the
  // descriptor no longer knows are kept, so a downgrade doesn't lose them.
  function resolveProps(descriptor, stored) {
    const props = {};
    for (const p of descriptor.props || []) props[p.key] = p.default;
    return Object.assign(props, stored || {});
  }

  function acceptedSignal(bind, id) {
    const signal = id ? DC.signals.get(id) : undefined;
    return signal && (!bind.kinds || bind.kinds.includes(signal.kind)) ? signal : null;
  }

  function resolveBinds(descriptor, stored) {
    const binds = {};
    for (const b of descriptor.binds || []) {
      binds[b.key] = acceptedSignal(b, stored && stored[b.key]) || acceptedSignal(b, b.default);
    }
    return binds;
  }

  DC.registerWidget = registerWidget;
  DC.registry = {
    get: (type) => widgets.get(type),
    all: () => Array.from(widgets.values()),
    resolveProps,
    resolveBinds,
    acceptedSignal,
  };
})();
