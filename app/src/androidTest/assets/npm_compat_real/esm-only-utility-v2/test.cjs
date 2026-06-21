"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const mod = await import("./node_modules/esm-only-util-v2/index.mjs");
  const summary = mod.formatPhase("phase10");
  assert.equal(summary, "esm:PHASE10");
  console.log("realnpm.esm-only-utility-v2=" + summary);
  return "PASS";
};
