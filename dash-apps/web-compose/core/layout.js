// Layout document: the user's dashboard, stored by the host as a JSON string.
//
// {
//   "schemaVersion": 1,
//   "gap": 12,
//   "layouts": {
//     "landscape": { "cols": 12, "rows": 6, "items": [ item, ... ] },
//     "portrait":  { "cols": 6, "rows": 12, "items": [ item, ... ] }
//   }
// }
// item: { "uid", "type", "x", "y", "w", "h", "bind": { key: signalId }, "props": {},
//          "border": { "color": "#rrggbb", "width": 1-6 } (optional) }
//
// Compatibility rules (the document outlives app versions in both directions):
// - unknown widget type  -> kept, rendered as a placeholder
// - unknown prop / bind  -> kept untouched
// - newer schemaVersion  -> not interpreted; the default layout is shown and
//                           the stored document is left alone
(function () {
  const DC = (window.DashCompose = window.DashCompose || {});

  const SCHEMA_VERSION = 1;
  const ORIENTATIONS = ["landscape", "portrait"];

  // Step n upgrades a version-n document to n+1. Append, never edit.
  const MIGRATIONS = [];

  function int(value, fallback) {
    const n = Math.round(Number(value));
    return Number.isFinite(n) ? n : fallback;
  }

  function normalizeGrid(grid) {
    if (!grid || typeof grid !== "object") return null;
    const cols = Math.min(24, Math.max(1, int(grid.cols, 12)));
    const rows = Math.min(24, Math.max(1, int(grid.rows, 6)));
    const items = [];
    for (const raw of Array.isArray(grid.items) ? grid.items : []) {
      if (!raw || typeof raw.type !== "string") continue;
      const w = Math.min(cols, Math.max(1, int(raw.w, 1)));
      const h = Math.min(rows, Math.max(1, int(raw.h, 1)));
      items.push(Object.assign({}, raw, {
        uid: String(raw.uid || raw.type + "-" + items.length),
        x: Math.min(cols - w, Math.max(0, int(raw.x, 0))),
        y: Math.min(rows - h, Math.max(0, int(raw.y, 0))),
        w,
        h,
      }));
    }
    return { cols, rows, items };
  }

  // Returns a usable document, or null if `input` can't be used.
  function normalize(input) {
    let doc = input;
    if (typeof doc === "string") {
      try {
        doc = JSON.parse(doc);
      } catch (_error) {
        return null;
      }
    }
    if (!doc || typeof doc !== "object") return null;
    let version = int(doc.schemaVersion, 0);
    if (version < 1 || version > SCHEMA_VERSION) return null;
    while (version < SCHEMA_VERSION) {
      doc = MIGRATIONS[version - 1](doc);
      version++;
    }

    const layouts = {};
    for (const o of ORIENTATIONS) {
      const grid = normalizeGrid(doc.layouts && doc.layouts[o]);
      if (grid) layouts[o] = grid;
    }
    if (!layouts.landscape && !layouts.portrait) return null;
    return Object.assign({}, doc, {
      schemaVersion: SCHEMA_VERSION,
      gap: Math.max(0, int(doc.gap, 12)),
      layouts,
    });
  }

  // Missing orientation falls back to the other one rather than to nothing.
  function gridFor(doc, orientation) {
    return doc.layouts[orientation] || doc.layouts.landscape || doc.layouts.portrait;
  }

  DC.layout = { SCHEMA_VERSION, normalize, gridFor };
})();
