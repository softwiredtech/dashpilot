"use strict";

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const { startServer } = require("../lib/server");

async function withServer(files, fn) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "perf-harness-"));

  for (const [name, content] of Object.entries(files)) {
    const filePath = path.join(root, name);
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(filePath, content);
  }

  const { server, port } = await startServer(root);

  try {
    await fn(port);
  } finally {
    await new Promise((resolve) => server.close(resolve));
    fs.rmSync(root, { recursive: true, force: true });
  }
}

test("serves index.html at / with text/html", async () => {
  await withServer({ "index.html": "<html></html>" }, async (port) => {
    const res = await fetch(`http://127.0.0.1:${port}/`);

    assert.strictEqual(res.status, 200);
    assert.strictEqual(res.headers.get("content-type"), "text/html");
  });
});

test("serves .wasm with application/wasm", async () => {
  await withServer({ "mod.wasm": "\0asm" }, async (port) => {
    const res = await fetch(`http://127.0.0.1:${port}/mod.wasm`);

    assert.strictEqual(res.headers.get("content-type"), "application/wasm");
  });
});

test("serves .glb and .wgsl with WebView-matching MIME", async () => {
  await withServer({ "m.glb": "x", "s.wgsl": "x" }, async (port) => {
    const glb = await fetch(`http://127.0.0.1:${port}/m.glb`);
    const wgsl = await fetch(`http://127.0.0.1:${port}/s.wgsl`);

    assert.strictEqual(glb.headers.get("content-type"), "model/gltf-binary");
    assert.strictEqual(wgsl.headers.get("content-type"), "text/plain");
  });
});

test("404 on missing file", async () => {
  await withServer({}, async (port) => {
    const res = await fetch(`http://127.0.0.1:${port}/nope.js`);

    assert.strictEqual(res.status, 404);
  });
});

test("403 on path traversal", async () => {
  await withServer({}, async (port) => {
    const res = await fetch(`http://127.0.0.1:${port}/..%2f..%2fetc%2fpasswd`);

    assert.strictEqual(res.status, 403);
  });
});
