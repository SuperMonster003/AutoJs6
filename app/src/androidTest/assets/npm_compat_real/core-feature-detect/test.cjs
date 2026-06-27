"use strict";

module.exports = async function run() {
  const assert = require("assert");
  const constantsProbe = require("constants-probe");
  const utilTypesProbe = require("util-types-probe");
  const eventsProbe = require("events-probe");
  const diagnosticsProbe = require("diagnostics-probe");

  const constants = constantsProbe();
  const types = utilTypesProbe();
  const events = await eventsProbe();
  const diagnostics = diagnosticsProbe();

  assert.strictEqual(constants.ok, true);
  assert.strictEqual(types.ok, true);
  assert.strictEqual(events.ok, true);
  assert.strictEqual(diagnostics.ok, true);

  console.log([
    "realnpm.core-feature-detect=" + constants.summary,
    types.summary,
    events.summary,
    diagnostics.summary,
  ].join("|"));
  return "PASS";
};
