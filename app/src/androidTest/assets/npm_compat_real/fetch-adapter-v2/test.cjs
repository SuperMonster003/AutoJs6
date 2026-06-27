"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const adapter = require("fetch-adapter-v2-probe");
  const summary = adapter.describe({ capability: "network", performRequest: false });
  assert.equal(summary, "controlled-fetch-adapter/no-network-call");
  console.log("realnpm.fetch-adapter-v2=" + summary);
  return "PASS";
};
