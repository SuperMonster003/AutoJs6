"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("wasm-buffer-probe");

  const result = await probe();

  assert.equal(result.summary, "wasm:buffer/17/321");
  console.log("realnpm.wasm-buffer-package=" + result.summary);
  return "PASS";
};
