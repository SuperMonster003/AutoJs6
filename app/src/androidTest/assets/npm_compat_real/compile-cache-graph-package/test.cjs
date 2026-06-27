"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const first = require("cache-graph-probe");
  const second = require("cache-graph-probe");

  const result = first();

  assert.equal(first, second);
  assert.equal(result.summary, "cache-graph/v2/15/abcde");
  console.log("realnpm.compile-cache-graph-package=" + result.summary);
  return "PASS";
};
