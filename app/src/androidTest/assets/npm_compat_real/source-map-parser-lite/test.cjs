"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const parser = require("source-map-parser-lite-probe");
  const summary = parser.describe({
    version: 3,
    sources: ["src/main.ts"],
    sourcesContent: ["throw new Error('x');"],
    mappings: "AAAA",
  });
  assert.equal(summary, "sourcemap:v3/1/true");
  console.log("realnpm.source-map-parser-lite=" + summary);
  return "PASS";
};
