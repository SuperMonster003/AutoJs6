"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("util-events-probe");
  const summary = await probe.summarize();

  assert.equal(summary, "utilevents:ready/14:true:true");
  console.log("realnpm.util-events=" + summary);
  return "PASS";
};
