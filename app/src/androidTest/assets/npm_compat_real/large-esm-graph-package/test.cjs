"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const summarize = require("large-esm-graph");

  const result = summarize();

  assert.equal(result.summary, "esm-graph/5/15/phase9");
  console.log("realnpm.large-esm-graph-package=" + result.summary);
  return "PASS";
};
