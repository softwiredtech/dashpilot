"use strict";
// A widget is never placed larger than the free space it goes into, and never
// on top of another one — by adding, by moving or by resizing.
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadApp } = require("./helpers");

const PARKED = { egoSpeed: 0, gear: 1, darkMode: true, locale: "en" };

// A layout with exactly one hole of the given size in an otherwise full 12x6 grid.
function layoutWithHole(holeX, holeY, holeW, holeH) {
  const items = [];
  for (let y = 0; y < 6; y++) {
    for (let x = 0; x < 12; x++) {
      const inHole = x >= holeX && x < holeX + holeW && y >= holeY && y < holeY + holeH;
      if (!inHole) items.push({ uid: `f${x}-${y}`, type: "lamp", x, y, w: 1, h: 1, bind: { value: "adasOn" } });
    }
  }
  return JSON.stringify({ schemaVersion: 1, layouts: { landscape: { cols: 12, rows: 6, items } } });
}

async function editing(t, layout) {
  const app = loadApp({ android: { ...PARKED }, layout });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  app.window.DashCompose.editor.open();
  return app;
}

const items = (app) => app.window.DashCompose.runtime.grid().items;
const paletteCard = (app, type) =>
  [...app.window.document.querySelectorAll(".palette__item")]
    .find((c) => c.querySelector("img").getAttribute("src").includes("/" + type + "/"));

const overlaps = (a, b) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
const anyOverlap = (list) => list.some((a, i) => list.slice(i + 1).some((b) => overlaps(a, b)));

test("a widget bigger than every hole is not placed at all", async (t) => {
  // radial is 4x4 by default and 2x2 at minimum; the only hole is 1x2.
  const app = await editing(t, layoutWithHole(3, 2, 1, 2));
  const before = items(app).length;
  app.window.DashCompose.editor.palette();

  const card = paletteCard(app, "radial");
  assert.equal(card.disabled, true, "the palette shows up front that it does not fit");
  card.click();

  assert.equal(items(app).length, before, "nothing was added");
  assert.match(card.textContent, /No free space/);
});

test("a widget is placed at the largest size that fits the hole, never larger", async (t) => {
  // radial: default 4x4, minimum 2x2. The hole is exactly 2x2.
  const app = await editing(t, layoutWithHole(4, 1, 2, 2));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();

  const added = items(app)[items(app).length - 1];
  assert.deepEqual(
    { x: added.x, y: added.y, w: added.w, h: added.h },
    { x: 4, y: 1, w: 2, h: 2 },
    "shrunk to the hole instead of keeping its 4x4 default",
  );
  assert.equal(anyOverlap(items(app)), false);
});

test("a 3x2 hole takes the largest fitting size, not the default 4x4", async (t) => {
  const app = await editing(t, layoutWithHole(2, 3, 3, 2));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();

  const added = items(app)[items(app).length - 1];
  assert.ok(added.w <= 3 && added.h <= 2, `placed ${added.w}x${added.h}, which does not fit a 3x2 hole`);
  assert.equal(added.w * added.h, 6, "uses the whole hole");
  assert.equal(anyOverlap(items(app)), false);
});

test("duplicating keeps its size and is refused when that size does not fit", async (t) => {
  const app = await editing(t, layoutWithHole(0, 0, 2, 1));
  // Grow one lamp to 2x1 so its duplicate needs the whole hole.
  const lamp = items(app)[0];
  app.window.DashCompose.editor.select(lamp.uid);
  const before = items(app).length;

  [...app.window.document.querySelectorAll(".editor__selection .editor__btn")]
    .find((b) => b.textContent === "Duplicate").click();
  const copy = items(app)[items(app).length - 1];
  assert.equal(items(app).length, before + 1);
  assert.deepEqual({ w: copy.w, h: copy.h }, { w: lamp.w, h: lamp.h });
  assert.equal(anyOverlap(items(app)), false);

  // The hole is now smaller than what a further duplicate needs at some point:
  // fill it and confirm the next duplicate is refused.
  let guard = 0;
  while (guard++ < 10) {
    const count = items(app).length;
    app.window.DashCompose.editor.select(lamp.uid);
    [...app.window.document.querySelectorAll(".editor__selection .editor__btn")]
      .find((b) => b.textContent === "Duplicate").click();
    if (items(app).length === count) break;
  }
  assert.ok(guard < 10, "duplication stops when the grid is full");
  assert.equal(anyOverlap(items(app)), false);
  assert.match(app.window.document.querySelector(".editor__toast").textContent, /No free space/);
});

test("everything placed stays inside the grid", async (t) => {
  const app = await editing(t, layoutWithHole(10, 4, 2, 2));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "bar").click();
  for (const item of items(app)) {
    assert.ok(item.x >= 0 && item.y >= 0 && item.x + item.w <= 12 && item.y + item.h <= 6,
      `${item.uid} at ${item.x},${item.y} ${item.w}x${item.h} leaves the grid`);
  }
});

test("moving onto an occupied spot is refused", async (t) => {
  const app = await editing(t, layoutWithHole(0, 0, 2, 2));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();
  const added = items(app)[items(app).length - 1];

  assert.equal(app.window.DashCompose.editor.move(added.uid, 5, 2), false, "cannot move onto other widgets");
  assert.deepEqual({ x: added.x, y: added.y }, { x: 0, y: 0 }, "stays where it was");
  assert.equal(anyOverlap(items(app)), false);
});

test("moving outside the grid is refused", async (t) => {
  const app = await editing(t, layoutWithHole(0, 0, 2, 2));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();
  const added = items(app)[items(app).length - 1];

  assert.equal(app.window.DashCompose.editor.move(added.uid, 11, 0), false);
  assert.equal(app.window.DashCompose.editor.move(added.uid, -1, 0), false);
  assert.deepEqual({ x: added.x, y: added.y }, { x: 0, y: 0 });
});

test("growing into an occupied spot is refused, shrinking is allowed", async (t) => {
  const app = await editing(t, layoutWithHole(0, 0, 3, 3));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();
  const added = items(app)[items(app).length - 1];
  assert.deepEqual({ w: added.w, h: added.h }, { w: 3, h: 3 });

  assert.equal(app.window.DashCompose.editor.resize(added.uid, 4, 4), false, "the 4th column and row are taken");
  assert.deepEqual({ w: added.w, h: added.h }, { w: 3, h: 3 });

  assert.equal(app.window.DashCompose.editor.resize(added.uid, 2, 2), true, "shrinking is fine");
  assert.equal(anyOverlap(items(app)), false);
});

test("resizing below the widget's minimum is refused", async (t) => {
  const app = await editing(t, layoutWithHole(0, 0, 4, 4));
  app.window.DashCompose.editor.palette();
  paletteCard(app, "radial").click();
  const added = items(app)[items(app).length - 1];

  assert.equal(app.window.DashCompose.editor.resize(added.uid, 1, 1), false, "radial has a 2x2 minimum");
  assert.ok(added.w >= 2 && added.h >= 2);
});
