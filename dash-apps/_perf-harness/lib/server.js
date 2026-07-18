"use strict";

const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const MIME = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".mjs": "text/javascript",
  ".css": "text/css",
  ".json": "application/json",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf",
};

function startServer(rootDir) {
  const root = path.resolve(rootDir);

  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let pathname;

      try {
        pathname = decodeURIComponent(new URL(req.url, "http://127.0.0.1").pathname);
      } catch {
        res.writeHead(400);
        res.end("bad request");
        return;
      }

      const relativePath = pathname === "/" ? "index.html" : pathname.slice(1);
      const filePath = path.resolve(root, relativePath);
      const relative = path.relative(root, filePath);

      if (relative.startsWith("..") || path.isAbsolute(relative)) {
        res.writeHead(403);
        res.end("forbidden");
        return;
      }

      fs.readFile(filePath, (error, data) => {
        if (error) {
          res.writeHead(404);
          res.end("not found");
          return;
        }

        res.writeHead(200, {
          "Content-Type": MIME[path.extname(filePath).toLowerCase()] || "application/octet-stream",
        });
        res.end(data);
      });
    });

    server.listen(0, "127.0.0.1", () => {
      resolve({ server, port: server.address().port });
    });
  });
}

module.exports = { startServer };
