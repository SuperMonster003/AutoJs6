"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const first = require("compile-cache-heavy-probe");
  const second = require("compile-cache-heavy-probe");

  const result = first.summarize("autojs");

  assert.equal(first, second);
  assert.equal(result, "compile-cache:reusable-graph/6/21/true");
  console.log("realnpm.compile-cache-package=" + result);
  return "PASS";
};
