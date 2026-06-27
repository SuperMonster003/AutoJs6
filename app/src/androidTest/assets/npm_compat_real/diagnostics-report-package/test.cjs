"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("diagnostics-report-probe");

  const result = probe();

  assert.equal(result.summary, "diagnostics:inspector-free/true");
  console.log("realnpm.diagnostics-report-package=" + result.summary);
  return "PASS";
};
