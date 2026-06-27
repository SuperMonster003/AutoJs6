"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const checkpoint = require("checkpoint-serializer-probe");
  const summary = checkpoint.describe({ version: 1, state: { cursor: 12 }, reason: "stop" });
  assert.equal(summary, "checkpoint:v1/stop/13");
  console.log("realnpm.checkpoint-serializer=" + summary);
  return "PASS";
};
