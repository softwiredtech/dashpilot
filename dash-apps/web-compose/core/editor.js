// In-app layout editor: long-press the dashboard to open it.
//
// Nothing here runs while the dashboard is just displaying — no listeners
// beyond the long-press detector, no per-tick work. Edits change the layout
// document in memory; Done saves it through the host, Cancel restores the
// snapshot taken when the editor opened.
//
// The settings panel is generated from each widget's descriptor (binds + props),
// so a new widget folder becomes fully configurable with no editor changes.
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  const LONG_PRESS_MS = 550;
  const MOVE_TOLERANCE = 8;
  const DRIVING_SPEED = 3; // above this the editor refuses to open and closes itself

  let open = false;
  let snapshot = null;
  let selectedUid = null;
  let chrome = null;
  let sheet = null;

  const doc = () => DC.runtime.doc();
  const grid = () => DC.runtime.grid();
  const t = (key) => DC.t(key);
  const clone = (value) => JSON.parse(JSON.stringify(value));

  let toastTimer = 0;
  // Never a modal dialog here: it freezes the WebView behind it until dismissed.
  function toast(message) {
    let node = document.querySelector(".editor__toast");
    if (!node) {
      node = el("div", "editor__toast");
      document.body.appendChild(node);
    }
    node.textContent = message;
    node.classList.add("editor__toast--on");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove("editor__toast--on"), 2600);
  }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  // ---------- geometry ----------

  function metrics() {
    const root = DC.runtime.root();
    const style = getComputedStyle(root);
    const g = grid();
    const gap = doc().gap;
    const box = root.getBoundingClientRect();
    const px = (value) => parseFloat(value) || 0;
    const width = root.clientWidth - px(style.paddingLeft) - px(style.paddingRight);
    const height = root.clientHeight - px(style.paddingTop) - px(style.paddingBottom);
    return {
      left: box.left + px(style.paddingLeft),
      top: box.top + px(style.paddingTop),
      stepX: (width + gap) / g.cols,
      stepY: (height + gap) / g.rows,
      cols: g.cols,
      rows: g.rows,
    };
  }

  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  function overlaps(a, b) {
    return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
  }

  function inGrid(rect) {
    return rect.x >= 0 && rect.y >= 0 && rect.x + rect.w <= grid().cols && rect.y + rect.h <= grid().rows;
  }

  function isFree(rect, ignoreUid) {
    return inGrid(rect) && !grid().items.some((item) => item.uid !== ignoreUid && overlaps(rect, item));
  }

  function findSpot(w, h) {
    const g = grid();
    for (let y = 0; y <= g.rows - h; y++) {
      for (let x = 0; x <= g.cols - w; x++) {
        if (isFree({ x, y, w, h })) return { x, y };
      }
    }
    return null;
  }

  // Largest size that actually fits, from the widget's default down to its
  // minimum. Returns null when not even the minimum fits anywhere — a widget is
  // never placed larger than the free space it goes into.
  function placement(descriptor) {
    const min = descriptor.minSize || { w: 1, h: 1 };
    const def = descriptor.defaultSize;
    const sizes = [];
    for (let h = min.h; h <= def.h; h++) {
      for (let w = min.w; w <= def.w; w++) sizes.push({ w, h });
    }
    sizes.sort((a, b) => b.w * b.h - a.w * a.h || b.w - a.w);
    for (const size of sizes) {
      const spot = findSpot(size.w, size.h);
      if (spot) return { x: spot.x, y: spot.y, w: size.w, h: size.h };
    }
    return null;
  }

  const itemByUid = (uid) => grid().items.find((i) => i.uid === uid);

  // Single rule for every way a widget can be placed: it must sit inside the
  // grid, within its own size limits, and not on top of another widget.
  function tryRect(item, rect) {
    const descriptor = (DC.registry.get(item.type) || {}).descriptor || {};
    const min = descriptor.minSize || { w: 1, h: 1 };
    const max = descriptor.maxSize || { w: grid().cols, h: grid().rows };
    if (rect.w < min.w || rect.h < min.h || rect.w > max.w || rect.h > max.h) return false;
    if (!isFree(rect, item.uid)) return false;
    if (rect.x === item.x && rect.y === item.y && rect.w === item.w && rect.h === item.h) return false;
    Object.assign(item, rect);
    return true;
  }

  // Swapping trades whole areas, so it always fits; size limits are ignored on purpose.
  function swap(a, b) {
    const area = { x: a.x, y: a.y, w: a.w, h: a.h };
    Object.assign(a, { x: b.x, y: b.y, w: b.w, h: b.h });
    Object.assign(b, area);
  }

  function itemAt(m, clientX, clientY) {
    const x = Math.floor((clientX - m.left) / m.stepX);
    const y = Math.floor((clientY - m.top) / m.stepY);
    return grid().items.find((i) => x >= i.x && x < i.x + i.w && y >= i.y && y < i.y + i.h);
  }

  function applyArea(cell, item) {
    cell.style.gridArea = (item.y + 1) + " / " + (item.x + 1) + " / span " + item.h + " / span " + item.w;
  }

  // ---------- editing gestures ----------

  function startGesture(event, cell, item, mode) {
    const m = metrics();
    const descriptor = (DC.registry.get(item.type) || {}).descriptor || {};
    const min = descriptor.minSize || { w: 1, h: 1 };
    const max = descriptor.maxSize || { w: m.cols, h: m.rows };
    const startX = event.clientX, startY = event.clientY;
    const origin = { x: item.x, y: item.y, w: item.w, h: item.h };
    let moved = false;
    let swapWith = null;

    function markSwap(target) {
      if (target === swapWith) return;
      swapWith = target;
      for (const other of DC.runtime.root().querySelectorAll(".cell")) {
        other.classList.toggle("cell--swap", !!target && other.dataset.uid === target.uid);
      }
    }

    // Capture can fail for an odd pointer id; dragging must still work.
    try {
      cell.setPointerCapture(event.pointerId);
    } catch (_error) {}
    cell.classList.add("cell--dragging");

    function onMove(moveEvent) {
      const dx = Math.round((moveEvent.clientX - startX) / m.stepX);
      const dy = Math.round((moveEvent.clientY - startY) / m.stepY);
      if (!moved && Math.hypot(moveEvent.clientX - startX, moveEvent.clientY - startY) > MOVE_TOLERANCE) moved = true;
      if (!moved) return;

      const next = mode === "move"
        ? { x: clamp(origin.x + dx, 0, m.cols - origin.w), y: clamp(origin.y + dy, 0, m.rows - origin.h), w: origin.w, h: origin.h }
        : {
            x: origin.x, y: origin.y,
            w: clamp(origin.w + dx, min.w, Math.min(max.w, m.cols - origin.x)),
            h: clamp(origin.h + dy, min.h, Math.min(max.h, m.rows - origin.y)),
          };
      if (tryRect(item, next)) {
        applyArea(cell, item);
        markSwap(null);
      } else if (mode === "move") {
        const target = itemAt(m, moveEvent.clientX, moveEvent.clientY);
        markSwap(target && target !== item ? target : null);
      }
    }

    function onUp(upEvent) {
      cell.removeEventListener("pointermove", onMove);
      cell.removeEventListener("pointerup", onUp);
      cell.removeEventListener("pointercancel", onUp);
      cell.classList.remove("cell--dragging");
      const target = upEvent.type === "pointerup" ? swapWith : null;
      markSwap(null);
      if (!moved) {
        select(item.uid);
        return;
      }
      if (target) swap(item, target);
      // Size changes need a remount so the widget can lay out for its new box.
      if (mode === "resize" || target) DC.runtime.remount();
      select(item.uid);
    }

    cell.addEventListener("pointermove", onMove);
    cell.addEventListener("pointerup", onUp);
    cell.addEventListener("pointercancel", onUp);
  }

  function decorateCells() {
    const root = DC.runtime.root();
    for (const cell of root.querySelectorAll(".cell")) {
      const item = itemByUid(cell.dataset.uid);
      if (!item) continue;
      cell.classList.add("cell--editable");
      cell.classList.toggle("cell--selected", cell.dataset.uid === selectedUid);

      const handle = el("div", "cell__resize");
      handle.appendChild(el("span"));
      cell.appendChild(handle);

      cell.addEventListener("pointerdown", (event) => {
        if (!open || event.button > 0) return;
        event.preventDefault();
        startGesture(event, cell, item, event.target.closest(".cell__resize") ? "resize" : "move");
      });
    }
  }

  function select(uid) {
    selectedUid = uid;
    const root = DC.runtime.root();
    for (const cell of root.querySelectorAll(".cell")) {
      cell.classList.toggle("cell--selected", cell.dataset.uid === uid);
    }
    renderToolbar();
  }

  // ---------- chrome ----------

  // The chrome is an overlay, so the grid gets padded by its measured height.
  function updateInset() {
    const height = chrome ? chrome.offsetHeight : 0;
    document.documentElement.style.setProperty("--editor-inset", height + "px");
  }

  function renderToolbar() {
    const bar = chrome.querySelector(".editor__selection");
    bar.textContent = "";
    const item = selectedUid ? itemByUid(selectedUid) : null;
    if (!item) {
      bar.appendChild(el("span", "editor__hint", t("editor.hint")));
      updateInset();
      return;
    }
    const entry = DC.registry.get(item.type);
    bar.appendChild(el("span", "editor__name", entry ? DC.i18n.localized(entry.descriptor.name) : item.type));
    bar.appendChild(button(t("editor.settings"), () => openSettings(item), "editor__btn"));
    bar.appendChild(button(t("editor.duplicate"), () => duplicate(item), "editor__btn"));
    bar.appendChild(button(t("editor.delete"), () => remove(item), "editor__btn editor__btn--danger"));
    updateInset();
  }

  function button(label, onClick, className) {
    const b = el("button", className || "editor__btn", label);
    b.type = "button";
    b.addEventListener("click", onClick);
    return b;
  }

  function buildChrome() {
    chrome = el("div", "editor");
    const top = el("div", "editor__bar");
    top.append(
      button(t("editor.cancel"), cancel, "editor__btn"),
      el("span", "editor__title", t("editor.title")),
      button("+ " + t("editor.add"), openPalette, "editor__btn"),
      button(t("editor.done"), done, "editor__btn editor__btn--primary"),
    );
    chrome.append(top, el("div", "editor__selection"));
    document.body.appendChild(chrome);
    renderToolbar();
    window.addEventListener("resize", updateInset);
  }

  // ---------- sheets ----------

  function closeSheet() {
    if (sheet) sheet.remove();
    sheet = null;
  }

  function openSheet(title) {
    closeSheet();
    sheet = el("div", "sheet");
    const panel = el("div", "sheet__panel");
    const head = el("div", "sheet__head");
    head.append(el("span", "sheet__title", title), button(t("editor.close"), closeSheet, "editor__btn"));
    panel.appendChild(head);
    const body = el("div", "sheet__body");
    panel.appendChild(body);
    sheet.appendChild(panel);
    sheet.addEventListener("pointerdown", (event) => {
      if (event.target === sheet) closeSheet();
    });
    document.body.appendChild(sheet);
    return body;
  }

  function openPalette() {
    const body = openSheet(t("editor.add"));
    const list = el("div", "palette");
    for (const { descriptor } of DC.registry.all()) {
      const card = el("button", "palette__item");
      card.type = "button";
      const img = document.createElement("img");
      img.src = "widgets/" + descriptor.type + "/preview.svg";
      img.alt = "";
      card.append(img, el("span", null, DC.i18n.localized(descriptor.name)));
      if (!placement(descriptor)) {
        card.disabled = true;
        card.classList.add("palette__item--full");
        card.append(el("span", "palette__note", t("editor.full")));
      }
      card.addEventListener("click", () => add(descriptor));
      list.appendChild(card);
    }
    body.appendChild(list);
  }

  function field(labelText, control) {
    const wrap = el("label", "field");
    wrap.append(el("span", "field__label", labelText), control);
    return wrap;
  }

  // Signal picker for one bind, grouped by category and filtered by kind.
  function bindControl(item, bind, onChange) {
    const select = el("select", "field__control");
    if (!bind.required) {
      const none = el("option", null, t("editor.none"));
      none.value = "";
      select.appendChild(none);
    }
    const groups = {};
    for (const signal of DC.signals.all) {
      if (bind.kinds && !bind.kinds.includes(signal.kind)) continue;
      (groups[signal.category] = groups[signal.category] || []).push(signal);
    }
    for (const category in groups) {
      const group = document.createElement("optgroup");
      group.label = t("category." + category);
      for (const signal of groups[category]) {
        const option = el("option", null, DC.signals.label(signal));
        option.value = signal.id;
        group.appendChild(option);
      }
      select.appendChild(group);
    }
    const shown = DC.registry.acceptedSignal(bind, item.bind && item.bind[bind.key])
      || DC.registry.acceptedSignal(bind, bind.default);
    select.value = shown ? shown.id : "";
    select.addEventListener("change", () => {
      item.bind = item.bind || {};
      if (select.value) item.bind[bind.key] = select.value;
      else delete item.bind[bind.key];
      onChange();
    });
    return select;
  }

  function propControl(item, prop, onChange) {
    const current = item.props && prop.key in item.props ? item.props[prop.key] : prop.default;
    let control;

    const commit = (value) => {
      item.props = item.props || {};
      if (value === null || value === undefined) delete item.props[prop.key];
      else item.props[prop.key] = value;
      onChange();
    };

    if (prop.type === "bool") {
      control = el("input", "field__control field__control--check");
      control.type = "checkbox";
      control.checked = !!current;
      control.addEventListener("change", () => commit(control.checked));
    } else if (prop.type === "enum") {
      control = el("select", "field__control");
      for (const option of prop.options) {
        const node = el("option", null, option);
        node.value = option;
        control.appendChild(node);
      }
      control.value = current;
      control.addEventListener("change", () => commit(control.value));
    } else if (prop.type === "color") {
      control = el("div", "field__control field__control--color");
      const picker = el("input");
      picker.type = "color";
      picker.value = current || "#3b82f6";
      picker.addEventListener("input", () => commit(picker.value));
      const auto = button(t("editor.auto"), () => {
        commit(null);
        picker.value = "#3b82f6";
      }, "editor__btn editor__btn--small");
      control.append(picker, auto);
    } else if (prop.type === "number") {
      control = el("input", "field__control");
      control.type = "number";
      control.inputMode = "decimal";
      if (prop.min !== undefined) control.min = prop.min;
      if (prop.max !== undefined) control.max = prop.max;
      control.placeholder = t("editor.auto");
      control.value = current === null || current === undefined ? "" : current;
      control.addEventListener("change", () => commit(control.value === "" ? null : Number(control.value)));
    } else {
      control = el("input", "field__control");
      control.type = "text";
      control.value = current || "";
      control.addEventListener("change", () => commit(control.value || null));
    }
    return control;
  }

  function changeType(item, type) {
    item.type = type;
    DC.runtime.remount();
    select(item.uid);
    openSettings(item);
  }

  function typeControl(item) {
    const control = el("select", "field__control");
    for (const { descriptor } of DC.registry.all()) {
      const option = el("option", null, DC.i18n.localized(descriptor.name));
      option.value = descriptor.type;
      control.appendChild(option);
    }
    control.value = item.type;
    control.addEventListener("change", () => changeType(item, control.value));
    return control;
  }

  function openSettings(item) {
    const entry = DC.registry.get(item.type);
    if (!entry) return;
    const descriptor = entry.descriptor;
    const body = openSheet(DC.i18n.localized(descriptor.name));
    let labelInput = null;
    const defaultLabel = () => {
      const signal = DC.registry.resolveBinds(descriptor, item.bind).value;
      return signal ? DC.signals.label(signal) : "";
    };
    // Re-mounting on every change keeps the preview honest, including size rules.
    const refresh = () => {
      DC.runtime.remount();
      select(item.uid);
      if (labelInput) labelInput.placeholder = defaultLabel();
    };

    body.appendChild(field(t("editor.type"), typeControl(item)));

    if ((descriptor.binds || []).length) {
      body.appendChild(el("h3", "sheet__section", t("editor.shows")));
      for (const bind of descriptor.binds) {
        const label = bind.name ? DC.i18n.localized(bind.name) : t("editor.shows");
        body.appendChild(field(label, bindControl(item, bind, refresh)));
      }
    }
    if ((descriptor.props || []).length) {
      body.appendChild(el("h3", "sheet__section", t("editor.appearance")));
      for (const prop of descriptor.props) {
        const control = propControl(item, prop, refresh);
        if (prop.key === "label") {
          labelInput = control;
          control.placeholder = defaultLabel();
        }
        body.appendChild(field(DC.i18n.localized(prop.name), control));
      }
    }
  }

  // ---------- document edits ----------

  function add(descriptor) {
    const spot = placement(descriptor);
    if (!spot) {
      toast(t("editor.nospace"));
      return;
    }
    const uid = descriptor.type + "-" + Date.now().toString(36);
    grid().items.push({ uid, type: descriptor.type, x: spot.x, y: spot.y, w: spot.w, h: spot.h });
    closeSheet();
    DC.runtime.remount();
    select(uid);
  }

  function duplicate(item) {
    const spot = findSpot(item.w, item.h);
    if (!spot) {
      toast(t("editor.nospace"));
      return;
    }
    const copy = Object.assign(clone(item), spot, { uid: item.type + "-" + Date.now().toString(36) });
    grid().items.push(copy);
    DC.runtime.remount();
    select(copy.uid);
  }

  function remove(item) {
    const items = grid().items;
    items.splice(items.indexOf(item), 1);
    selectedUid = null;
    DC.runtime.remount();
  }

  // ---------- open / close ----------

  function driving() {
    const speed = Number(DC.runtime.raw().egoSpeed);
    return Number.isFinite(speed) && speed > DRIVING_SPEED;
  }

  function openEditor() {
    if (open) return;
    if (driving()) {
      toast(t("editor.locked"));
      return;
    }
    open = true;
    snapshot = clone(doc());
    selectedUid = null;
    document.body.classList.add("editing");
    DC.host.setEditing(true);
    buildChrome();
    decorateCells();
  }

  function teardown() {
    open = false;
    selectedUid = null;
    DC.host.setEditing(false);
    closeSheet();
    if (chrome) chrome.remove();
    chrome = null;
    window.removeEventListener("resize", updateInset);
    document.documentElement.style.removeProperty("--editor-inset");
    document.body.classList.remove("editing");
  }

  function done() {
    teardown();
    DC.runtime.save();
    DC.runtime.remount();
  }

  function cancel() {
    const restore = snapshot;
    teardown();
    DC.runtime.setDoc(restore);
  }

  // Re-decorate after every remount while the editor is open.
  DC.onMounted = function () {
    if (open) decorateCells();
  };

  // Checked on the tick, not on remount: pulling away has to close the editor
  // even when nothing else caused a re-render.
  DC.onFrame = function () {
    if (open && driving()) done();
  };

  // Long-press anywhere on the dashboard opens the editor.
  (function installLongPress() {
    let timer = 0, startX = 0, startY = 0;
    const cancelPress = () => {
      clearTimeout(timer);
      timer = 0;
    };
    document.addEventListener("pointerdown", (event) => {
      if (open || event.button > 0) return;
      startX = event.clientX;
      startY = event.clientY;
      timer = setTimeout(openEditor, LONG_PRESS_MS);
    });
    document.addEventListener("pointermove", (event) => {
      if (timer && Math.hypot(event.clientX - startX, event.clientY - startY) > MOVE_TOLERANCE) cancelPress();
    });
    document.addEventListener("pointerup", cancelPress);
    document.addEventListener("pointercancel", cancelPress);
  })();

  if (window.location.search.indexOf("edit") >= 0) {
    document.addEventListener("DOMContentLoaded", () => setTimeout(openEditor, 0), { once: true });
  }
})();
