"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const ws = require("websocket-protocol-util-probe");
  const summary = ws.describeTextFrame("phase10");
  assert.equal(summary, "frame:text/7/70686173653130");
  console.log("realnpm.websocket-protocol-util=" + summary);
  return "PASS";
};
