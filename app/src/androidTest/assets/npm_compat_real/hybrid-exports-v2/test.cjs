"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const cjs = require("hybrid-exports-v2-probe");
  const esm = await import("./node_modules/hybrid-exports-v2-probe/esm.mjs");
  const summary = `${cjs.mode()}+${esm.mode()}`;
  assert.equal(summary, "cjs+esm");
  console.log("realnpm.hybrid-exports-v2=hybrid:" + summary);
  return "PASS";
};
