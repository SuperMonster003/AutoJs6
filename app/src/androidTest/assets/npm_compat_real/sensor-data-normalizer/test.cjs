"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const sensors = require("sensor-data-normalizer-probe");
  const summary = sensors.describe([
    { t: 30, values: [0, 1, 2] },
    { t: 10, values: [2, 1, 0] },
  ]);
  assert.equal(summary, "sensor:2/10-30/3");
  console.log("realnpm.sensor-data-normalizer=" + summary);
  return "PASS";
};
