"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { loadApp } = require("./helpers");

const DRIVING = {
  egoSpeed: 42, gear: 4, adasOn: true, leftBlinker: 0,
  fullPackEnergy: 78, nominalEnergyRemaining: 50, energyBuffer: 3,
  packVoltage: 380, packCurrent: 100, packTMax: 25, odometer: 12345,
  useImperial: false, darkMode: true, locale: "en",
};

// What iOS actually pushes: CarState fields, with the imperial flag as isImperial.
const iosPayload = ({ useImperial, ...rest }, overrides = {}) => ({ ...rest, isImperial: useImperial, ...overrides });

const layoutWith = (items) => JSON.stringify({ schemaVersion: 1, layouts: { landscape: { cols: 12, rows: 6, items } } });

test("Android path: getters are read on onCarStateUpdate and reach the widgets", async (t) => {
  const state = { ...DRIVING };
  const app = loadApp({ android: state });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();

  assert.equal(app.window.document.querySelectorAll(".cell").length, 15);
  assert.equal(app.text("speed"), "42km/h");
  assert.equal(app.text("gear"), "GearD");
  assert.equal(app.text("battery"), "Battery63%");
  assert.equal(app.text("power"), "Power38kW");
  assert.equal(app.widget("adas").dataset.on, "1");

  state.egoSpeed = 88;
  state.leftBlinker = 1;
  app.window.onCarStateUpdate();
  await app.settle();
  assert.equal(app.text("speed"), "88km/h");
  assert.equal(app.widget("blinker-left").dataset.on, "1");
  assert.deepEqual(app.errors, []);
});

test("iOS path: a pushed JSON state reaches the widgets", async (t) => {
  const app = loadApp();
  t.after(app.close);
  await app.settle();
  app.window.receiveMessage(iosPayload(DRIVING, { egoSpeed: 57 }));
  await app.settle();
  assert.equal(app.text("speed"), "57km/h");
  assert.equal(app.text("gear"), "GearD");
});

test("a tick where nothing changed writes nothing", async (t) => {
  const app = loadApp({ android: { ...DRIVING } });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();

  let records = 0;
  new app.window.MutationObserver((list) => (records += list.length))
    .observe(app.window.document.body, { subtree: true, attributes: true, childList: true, characterData: true });
  app.window.onCarStateUpdate();
  await app.settle();
  assert.equal(records, 0);
});

test("switching to imperial relabels units", async (t) => {
  const app = loadApp();
  t.after(app.close);
  await app.settle();
  app.window.receiveMessage(iosPayload(DRIVING, { isImperial: true }));
  await app.settle();
  assert.equal(app.text("speed"), "42mph");
  assert.match(app.text("odometer"), /mi$/);
});

test("the host locale picks the language and number format", async (t) => {
  const app = loadApp();
  t.after(app.close);
  await app.settle();
  app.window.receiveMessage(iosPayload(DRIVING, { locale: "hu-HU" }));
  await app.settle();
  assert.equal(app.window.document.documentElement.lang, "hu-HU");
  assert.match(app.text("battery"), /^Akkumulátor/);
  assert.equal(app.text("odometer").replace(/\s/g, " "), "Kilométeróra12 345km");
});

test("an unknown widget type renders a placeholder and the rest still works", async (t) => {
  const app = loadApp({
    android: { ...DRIVING },
    layout: layoutWith([
      { uid: "future", type: "hologram", x: 0, y: 0, w: 2, h: 2 },
      { uid: "speed", type: "readout", x: 2, y: 0, w: 2, h: 2, bind: { value: "egoSpeed" } },
    ]),
  });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  assert.ok(app.cell("future").querySelector(".cell__body--unknown"));
  assert.equal(app.text("speed"), "Speed42km/h");
});

test("a widget that throws is isolated to its own cell", async (t) => {
  const broken = `
    window.DashCompose.registerWidget(
      { type: "broken", version: 1, renderer: "dom", name: { en: "Broken" }, defaultSize: { w: 1, h: 1 },
        binds: [{ key: "value", kinds: ["number"], default: "egoSpeed" }] },
      function () { return { update() { throw new Error("boom"); } }; });`;
  const app = loadApp({
    android: { ...DRIVING },
    extraScript: broken,
    layout: layoutWith([
      { uid: "bad", type: "broken", x: 0, y: 0, w: 2, h: 2 },
      { uid: "speed", type: "readout", x: 2, y: 0, w: 2, h: 2, bind: { value: "egoSpeed" } },
    ]),
  });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  assert.ok(app.cell("bad").classList.contains("cell--failed"));
  assert.equal(app.text("speed"), "Speed42km/h");
  assert.deepEqual(app.errors, [], "the error must not escape as an uncaught exception");
  assert.ok(app.logged.some((l) => /broken \(bad\) failed in update/.test(l)));
});

test("a stored layout with a newer schema falls back to the default layout", async (t) => {
  const app = loadApp({ layout: JSON.stringify({ schemaVersion: 99, layouts: {} }) });
  t.after(app.close);
  await app.settle();
  assert.equal(app.window.document.querySelectorAll(".cell").length, 15);
});

test("a bind to a signal of the wrong kind is ignored rather than misrendered", async (t) => {
  const app = loadApp({
    android: { ...DRIVING },
    layout: layoutWith([{ uid: "lamp", type: "lamp", x: 0, y: 0, w: 2, h: 2, bind: { value: "egoSpeed" } }]),
  });
  t.after(app.close);
  await app.settle();
  app.window.onCarStateUpdate();
  await app.settle();
  assert.equal(app.widget("lamp").dataset.on, "0");
});
