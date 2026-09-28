"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadCore } = require("./helpers");

const { layout } = loadCore(["core/layout.js"]);

const doc = (items, extra = {}) => ({
  schemaVersion: 1,
  layouts: { landscape: { cols: 12, rows: 6, items } },
  ...extra,
});

test("unreadable input is rejected, not guessed at", () => {
  assert.equal(layout.normalize("{not json"), null);
  assert.equal(layout.normalize(null), null);
  assert.equal(layout.normalize({ layouts: {} }), null);
  assert.equal(layout.normalize({ schemaVersion: 1, layouts: {} }), null);
});

test("a newer schema is left alone instead of being misread", () => {
  assert.equal(layout.normalize(doc([], { schemaVersion: 99 })), null);
});

test("accepts a JSON string as stored by the host", () => {
  const result = layout.normalize(JSON.stringify(doc([{ uid: "a", type: "readout", x: 0, y: 0, w: 2, h: 2 }])));
  assert.equal(result.layouts.landscape.items[0].uid, "a");
});

test("unknown widget types, props and binds survive normalization", () => {
  const result = layout.normalize(doc([
    { uid: "future", type: "hologram", x: 1, y: 1, w: 2, h: 2, props: { sparkle: 11 }, bind: { beam: "warpCore" }, note: "x" },
  ]));
  const item = result.layouts.landscape.items[0];
  assert.equal(item.type, "hologram");
  assert.deepEqual(item.props, { sparkle: 11 });
  assert.deepEqual(item.bind, { beam: "warpCore" });
  assert.equal(item.note, "x");
});

test("positions and sizes are clamped into the grid", () => {
  const result = layout.normalize(doc([
    { type: "readout", x: 11, y: 5, w: 4, h: 3 },
    { type: "readout", x: -3, y: -1, w: 0, h: 99 },
  ]));
  const [a, b] = result.layouts.landscape.items;
  assert.deepEqual([a.x, a.y, a.w, a.h], [8, 3, 4, 3]);
  assert.deepEqual([b.x, b.y, b.w, b.h], [0, 0, 1, 6]);
});

test("items without a type are dropped, items without a uid get one", () => {
  const result = layout.normalize(doc([{ x: 0, y: 0 }, { type: "lamp", x: 0, y: 0 }]));
  assert.equal(result.layouts.landscape.items.length, 1);
  assert.ok(result.layouts.landscape.items[0].uid);
});

test("a missing orientation falls back to the other one", () => {
  const result = layout.normalize(doc([{ uid: "a", type: "lamp", x: 0, y: 0, w: 1, h: 1 }]));
  assert.equal(layout.gridFor(result, "portrait"), result.layouts.landscape);
});
