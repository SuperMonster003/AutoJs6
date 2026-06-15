"use strict";

module.exports = async function run() {
  const assert = require("assert");
  const path = require("path");
  const minimatch = require("minimatch");
  const hashSum = require("hash-sum");

  const files = [
    "src/index.js",
    "src/index.test.js",
    "README.md",
    "lib/util.cjs",
  ];
  const matches = files.filter(minimatch.filter("src/*.js")).join(",");
  const base = path.posix.basename(matches.split(",")[0]);
  const digest = hashSum({ matches, package: "autojs6" });

  assert.strictEqual(matches, "src/index.js,src/index.test.js");
  assert.strictEqual(base, "index.js");
  assert.strictEqual(digest.length, 12);

  console.log("realnpm.path-glob-crypto=" + matches + "|" + base + "|" + digest);
  return "PASS";
};
