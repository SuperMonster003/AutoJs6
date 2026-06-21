"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const transform = require("web-streams-transform-probe");
  const summary = await transform(["alpha", "beta"]);
  assert.equal(summary, "ALPHA|BETA");
  console.log("realnpm.web-streams-transform=" + summary);
  return "PASS";
};
