"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("digest-stream-probe");
  const summary = await probe.summarize(["alpha", "beta", "gamma"]);

  assert.equal(summary, "alphabetagamma:c04a9408aace4db2");
  console.log("realnpm.stdlib-digest-stream=" + summary);
  return "PASS";
};
