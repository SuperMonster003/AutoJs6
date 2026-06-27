"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("wasm-worker-fallback-probe");

  const result = await probe();

  assert.equal(result.summary, "worker-wasm:fallback/9/true");
  console.log("realnpm.worker-wasm-fallback-package=" + result.summary);
  return "PASS";
};
