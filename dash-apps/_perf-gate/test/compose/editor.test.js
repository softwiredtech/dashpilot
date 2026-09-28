"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadApp } = require("./helpers");

const PARKED = { egoSpeed: 0, gear: 1, fullPackEnergy: 78, nominalEnergyRemaining: 50, energyBuffer: 3, darkMode: true, locale: "en" };

async function editing(t, state = PARKED) {
  const app = loadApp({ android: { ...state } });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  app.window.DashCompose.editor.open();
  return app;
}

const items = (app) => app.window.DashCompose.runtime.grid().items;
// The default layout fills the grid completely, so tests that add a widget free a cell first.
const freeSpace = (app) => {
  app.window.DashCompose.editor.select("cruise");
  byText(app, ".editor__selection .editor__btn", "Remove").click();
};
const byText = (app, selector, text) =>
  [...app.window.document.querySelectorAll(selector)].find((n) => n.textContent === text);

test("the editor opens on demand and marks the page as editing", async (t) => {
  const app = await editing(t);
  assert.ok(app.window.DashCompose.editor.isOpen());
  assert.ok(app.window.document.body.classList.contains("editing"));
  assert.ok(app.window.document.querySelector(".editor__bar"));
  assert.equal(app.window.document.querySelectorAll(".cell__resize").length, items(app).length);
});

test("it refuses to open while the car is moving, without a blocking dialog", async (t) => {
  const app = loadApp({ android: { ...PARKED, egoSpeed: 50 } });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  app.window.DashCompose.editor.open();
  assert.equal(app.window.DashCompose.editor.isOpen(), false);
  assert.equal(app.window.document.querySelector(".editor__toast").textContent, "Stop the car to edit the dashboard");
});

test("pulling away closes the editor and keeps the changes", async (t) => {
  const app = await editing(t);
  const before = items(app).length;
  freeSpace(app);
  assert.equal(items(app).length, before - 1);

  app.state.egoSpeed = 60;
  app.window.onCarStateUpdate();
  await app.settle();
  assert.equal(app.window.DashCompose.editor.isOpen(), false);
  assert.equal(JSON.parse(app.window.localStorage.getItem("dashcompose.layout")).layouts.landscape.items.length, before - 1);
});

test("a full grid offers nothing to add", async (t) => {
  const app = await editing(t);
  const before = items(app).length;
  app.window.DashCompose.editor.palette();

  const cards = [...app.window.document.querySelectorAll(".palette__item")];
  assert.ok(cards.length);
  assert.ok(cards.every((c) => c.disabled), "every widget is shown as unplaceable");
  assert.ok(cards.every((c) => /No free space/.test(c.textContent)));

  cards[0].click();
  assert.equal(items(app).length, before, "nothing is added when the grid is full");
});

test("adding from the palette places the widget in free space", async (t) => {
  const app = await editing(t);
  freeSpace(app);
  const before = items(app).length;
  app.window.DashCompose.editor.palette();
  assert.equal(app.window.document.querySelectorAll(".palette__item").length, 4);
  app.window.document.querySelector(".palette__item").click();

  const added = items(app)[items(app).length - 1];
  assert.equal(items(app).length, before + 1);
  assert.ok(!items(app).slice(0, -1).some((i) =>
    i.x < added.x + added.w && added.x < i.x + i.w && i.y < added.y + added.h && added.y < i.y + i.h), "must not overlap");
  assert.ok(app.cell(added.uid), "the new widget is mounted");
});

test("removing and duplicating a widget", async (t) => {
  const app = await editing(t);
  freeSpace(app);
  const before = items(app).length;
  app.window.DashCompose.editor.select("clock");
  byText(app, ".editor__selection .editor__btn", "Duplicate").click();
  assert.equal(items(app).length, before + 1);

  app.window.DashCompose.editor.select("clock");
  byText(app, ".editor__selection .editor__btn", "Remove").click();
  assert.equal(items(app).length, before);
  assert.equal(app.cell("clock"), null);
});

test("the settings panel is generated from the descriptor and applies changes", async (t) => {
  const app = await editing(t);
  app.window.DashCompose.editor.settings("speed");

  const labels = [...app.window.document.querySelectorAll(".field__label")].map((n) => n.textContent);
  assert.deepEqual(labels, ["Value", "Marker", "Label", "Minimum", "Maximum", "Decimals", "Arc color", "Show label"]);

  // Repoint the gauge at another signal.
  const select = app.window.document.querySelector(".field__control");
  select.value = "packTMax";
  select.dispatchEvent(new app.window.Event("change"));
  assert.equal(items(app).find((i) => i.uid === "speed").bind.value, "packTMax");
  assert.match(app.text("speed"), /°C$/);
});

test("Cancel restores the layout as it was when the editor opened", async (t) => {
  const app = await editing(t);
  const before = JSON.stringify(items(app));
  app.window.DashCompose.editor.select("clock");
  byText(app, ".editor__selection .editor__btn", "Remove").click();
  assert.notEqual(JSON.stringify(items(app)), before);

  byText(app, ".editor__bar .editor__btn", "Cancel").click();
  assert.equal(JSON.stringify(items(app)), before);
  assert.equal(app.window.localStorage.getItem("dashcompose.layout"), null, "Cancel must not save");
  assert.equal(app.window.document.body.classList.contains("editing"), false);
});

test("Done saves the layout and it survives a reload", async (t) => {
  const app = await editing(t);
  app.window.DashCompose.editor.select("clock");
  byText(app, ".editor__selection .editor__btn", "Remove").click();
  byText(app, ".editor__bar .editor__btn", "Done").click();

  const saved = app.window.localStorage.getItem("dashcompose.layout");
  assert.ok(saved);
  assert.ok(!JSON.parse(saved).layouts.landscape.items.some((i) => i.uid === "clock"));

  const reopened = loadApp({ android: { ...PARKED }, layout: saved });
  t.after(reopened.close);
  await reopened.settle();
  assert.equal(reopened.cell("clock"), null);
  assert.ok(reopened.cell("speed"));
});

test("iOS path: the injected layout is used and Done posts the new one back to the host", async (t) => {
  const stored = JSON.stringify({
    schemaVersion: 1,
    layouts: { landscape: { cols: 12, rows: 6, items: [{ uid: "only", type: "readout", x: 0, y: 0, w: 4, h: 2, bind: { value: "egoSpeed" } }] } },
  });
  const saves = [];
  const app = loadApp({ iosLayout: stored, iosSaves: saves });
  t.after(app.close);
  await app.settle();
  app.window.receiveMessage({ ...PARKED, isImperial: false });
  await app.settle();

  assert.equal(app.window.document.querySelectorAll(".cell").length, 1);
  assert.ok(app.cell("only"));

  app.window.DashCompose.editor.open();
  app.window.DashCompose.editor.palette();
  app.window.document.querySelector(".palette__item").click();
  byText(app, ".editor__bar .editor__btn", "Done").click();

  assert.equal(saves.length, 1, "the layout goes to the host, not to browser storage");
  assert.equal(JSON.parse(saves[0]).layouts.landscape.items.length, 2);
});

test("the host is told when the editor opens and closes (Android)", async (t) => {
  const flags = [];
  const app = loadApp({ android: { ...PARKED }, nativeExtra: { setComposeEditing: (v) => flags.push(v) } });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();

  app.window.DashCompose.editor.open();
  assert.deepEqual(flags, [true], "the native carousel has to stop swiping");

  byText(app, ".editor__bar .editor__btn", "Done").click();
  assert.deepEqual(flags, [true, false]);
});

test("Cancel and driving away also release the host (iOS)", async (t) => {
  const editing = [];
  const app = loadApp({ android: { ...PARKED }, iosEditing: editing });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();

  app.window.DashCompose.editor.open();
  byText(app, ".editor__bar .editor__btn", "Cancel").click();
  assert.deepEqual(editing, [true, false], "Cancel must not leave the carousel stuck");

  app.window.DashCompose.editor.open();
  app.state.egoSpeed = 60;
  app.window.onCarStateUpdate();
  await app.settle();
  assert.deepEqual(editing, [true, false, true, false], "driving away releases it too");
});
