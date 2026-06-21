"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const reader = require("capability-manifest-reader-probe");
  const summary = reader.describe({ capabilities: ["network", "storage"], providers: { network: "live" } });
  assert.equal(summary, "capabilities:2/live=1");
  console.log("realnpm.capability-manifest-reader=" + summary);
  return "PASS";
};
