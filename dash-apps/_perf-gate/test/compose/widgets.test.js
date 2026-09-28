"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { APP_DIR } = require("./helpers");
const { build, validate, loadSignals } = require("../../scripts/build-compose-widgets.js");

const signals = loadSignals();

test("every bundled widget passes validation", () => {
  const result = build();
  assert.deepEqual(result.errors, []);
  assert.ok(result.count >= 4);
});

test("generated files are up to date", () => {
  const result = build();
  assert.equal(fs.readFileSync(path.join(APP_DIR, "widgets", "_generated.js"), "utf8"), result.js,
    "run: node scripts/build-compose-widgets.js");
  assert.equal(fs.readFileSync(path.join(APP_DIR, "widgets", "_generated.css"), "utf8"), result.css,
    "run: node scripts/build-compose-widgets.js");
});

test("the generated CSS has no comment that swallows the first rule", () => {
  const css = build().css;
  const firstRule = css.indexOf("{");
  const header = css.slice(0, css.indexOf("*/") + 2);
  assert.ok(!header.slice(2, -2).includes("*/"));
  assert.ok(firstRule > header.length);
});

const valid = () => ({
  type: "sample",
  version: 1,
  renderer: "dom",
  name: { en: "Sample" },
  defaultSize: { w: 2, h: 2 },
  binds: [{ key: "value", kinds: ["number"], default: "egoSpeed" }],
  props: [{ key: "mode", type: "enum", options: ["a", "b"], default: "a", name: { en: "Mode" } }],
});

test("a valid descriptor has no errors", () => {
  assert.deepEqual(validate("sample", valid(), signals), []);
});

test("descriptor mistakes are reported", () => {
  const cases = [
    [(d) => (d.type = "other"), /must match the folder name/],
    [(d) => (d.renderer = "flash"), /renderer must be one of/],
    [(d) => delete d.name, /name\.en is required/],
    [(d) => (d.binds[0].default = "warpCore"), /not a known signal/],
    [(d) => (d.binds[0].default = "gear"), /is a enum, not one of number/],
    [(d) => delete d.props[0].default, /needs a default/],
    [(d) => (d.props[0].default = "c"), /default must be one of its options/],
    [(d) => (d.extra = true), /unknown property "extra"/],
  ];
  for (const [mutate, expected] of cases) {
    const d = valid();
    mutate(d);
    const errors = validate("sample", d, signals);
    assert.ok(errors.some((e) => expected.test(e)), `expected ${expected} in ${JSON.stringify(errors)}`);
  }
});

test("a widget folder missing its files or create() is rejected", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "compose-widgets-"));
  try {
    fs.mkdirSync(path.join(dir, "sample"));
    fs.writeFileSync(path.join(dir, "sample", "descriptor.json"), JSON.stringify(valid()));
    fs.writeFileSync(path.join(dir, "sample", "widget.js"), "function make(ctx) {}\n");
    const { errors } = build(dir);
    assert.ok(errors.some((e) => /missing preview\.svg/.test(e)));
    assert.ok(errors.some((e) => /must define `function create\(ctx\)`/.test(e)));
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
