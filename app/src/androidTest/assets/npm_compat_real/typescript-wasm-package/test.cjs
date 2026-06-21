"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("ts-wasm-probe");

  const result = await probe(5, 6);

  assert.equal(result.metadata.source, "./src/index.cts");
  assert.equal(result.summary, "ts-wasm:typed/11");
  console.log("realnpm.typescript-wasm-package=" + result.summary);
  return "PASS";
};
