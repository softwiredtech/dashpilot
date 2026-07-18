"use strict";

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const { startServer } = require("../lib/server");
const { buildDevOptions } = require("../dev");

const fixturesDir = path.join(__dirname, "..", "fixtures", "fast-app");

test("dev-configured server tests", async () => {
  const { virtualFiles } = buildDevOptions(fixturesDir);

  // 1. Start the dev-configured server against fixtures/fast-app
  const { server, port } = await startServer(fixturesDir, { virtualFiles });

  try {
    // Assertion 1: Served index.html contains the /__dev-shim.js script tag.
    const resHtml = await fetch(`http://127.0.0.1:${port}/index.html`);
    const htmlText = await resHtml.text();
    assert.ok(htmlText.includes('/__dev-shim.js'), "injected HTML must include the script tag");

    // Assertion 2: /__dev-shim.js response contains NativeCarState and parses as JS (new Function(src)).
    const resShim = await fetch(`http://127.0.0.1:${port}/__dev-shim.js`);
    const shimSrc = await resShim.text();
    assert.ok(shimSrc.includes('NativeCarState'), "shim source must contain NativeCarState");
    // Verify it parses as JS
    assert.doesNotThrow(() => {
      new Function(shimSrc);
    }, "shim source must be valid JavaScript");
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }

  // Assertion 3: A plain startServer(rootDir) (no extras) response is byte-identical to the file on disk.
  const { server: plainServer, port: plainPort } = await startServer(fixturesDir);
  try {
    const resHtmlPlain = await fetch(`http://127.0.0.1:${plainPort}/index.html`);
    const plainText = await resHtmlPlain.text();
    const diskText = fs.readFileSync(path.join(fixturesDir, "index.html"), "utf8");
    assert.strictEqual(plainText, diskText, "plain server response must be byte-identical to disk file");
  } finally {
    await new Promise((resolve) => plainServer.close(resolve));
  }
});
