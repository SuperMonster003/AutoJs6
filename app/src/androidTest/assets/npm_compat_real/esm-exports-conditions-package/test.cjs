"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("exports-conditions-probe");
  const feature = require("exports-conditions-probe/feature");
  const summary = probe.describe(feature);

  assert.equal(summary, "exports:require:feature:2");
  console.log("realnpm.esm-exports-conditions=" + summary);
  return "PASS";
};
