"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("perf-async-hooks-probe");
  const summary = await probe.summarize(["outer", "inner"]);

  assert.equal(summary, "asyncperf:outer>inner>outer:true");
  console.log("realnpm.perf-async-hooks=" + summary);
  return "PASS";
};
