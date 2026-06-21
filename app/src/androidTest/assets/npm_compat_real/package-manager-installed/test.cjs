"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("package-manager-installed-probe");
  const summary = probe.describe({ installed: ["left-pad-lite", "slugify-lite"], lockfile: true });
  assert.equal(summary, "pm-installed:2/lockfile");
  console.log("realnpm.package-manager-installed=" + summary);
  return "PASS";
};
