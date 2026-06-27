"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("wasi-policy-probe");

  const result = probe();

  assert.equal(result.summary, "wasi:policy-aware/true");
  console.log("realnpm.wasi-policy-package=" + result.summary);
  return "PASS";
};
