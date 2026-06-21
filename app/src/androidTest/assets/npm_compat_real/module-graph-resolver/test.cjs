"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const resolver = require("module-graph-resolver-probe");
  const summary = resolver.resolve({
    exports: { ".": { import: "./esm.mjs", require: "./cjs.cjs" }, "./feature": "./feature.js" },
  });
  assert.equal(summary, "graph:require=./cjs.cjs/import=./esm.mjs/keys=2");
  console.log("realnpm.module-graph-resolver=" + summary);
  return "PASS";
};
