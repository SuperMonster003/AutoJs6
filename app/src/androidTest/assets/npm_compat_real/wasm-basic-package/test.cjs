"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const addProbe = require("wasm-add-probe");

  const result = await addProbe(19, 23);

  assert.equal(result.value, 42);
  assert.equal(result.summary, "wasm:add/42/valid");
  console.log("realnpm.wasm-basic-package=" + result.summary);
  return "PASS";
};
