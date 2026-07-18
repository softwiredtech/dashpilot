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
