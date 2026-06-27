"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const add = require("wasm-utility-v2-probe");
  const summary = await add(20, 22);
  assert.equal(summary, "wasm:v2/42/valid");
  console.log("realnpm.wasm-utility-v2=" + summary);
  return "PASS";
};
