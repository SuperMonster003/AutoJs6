"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const scheduler = require("long-running-scheduler-helper-probe");
  const summary = scheduler.describe({ heartbeatMs: 1500, stop: "user", timeoutMs: 6000 });
  assert.equal(summary, "long-running:4/user");
  console.log("realnpm.long-running-scheduler-helper=" + summary);
  return "PASS";
};
