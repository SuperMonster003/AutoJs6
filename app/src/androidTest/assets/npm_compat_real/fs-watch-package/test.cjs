"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("watcher-probe");

  const result = await probe("realnpm-watch.txt");

  assert.equal(result.ok, true);
  assert.match(result.summary, /^fswatch:(change|rename|event|timeout-fallback)\/closed$/);
  console.log("realnpm.fs-watch-package=" + result.summary);
  return "PASS";
};
