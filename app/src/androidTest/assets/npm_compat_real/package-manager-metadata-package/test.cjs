"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("pm-metadata-probe");
  const summary = probe.summarize();

  assert.equal(summary, "pmmeta:pm-metadata-probe:pm-metadata-leaf:1.0.0");
  console.log("realnpm.package-manager-metadata=" + summary);
  return "PASS";
};
