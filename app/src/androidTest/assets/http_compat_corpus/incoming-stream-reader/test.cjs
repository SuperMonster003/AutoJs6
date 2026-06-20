"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const reader = require("incoming-stream-reader");

  const summary = await reader.read("http://example.invalid/incoming-stream");
  assert.equal(summary, "httpnpm.incoming-stream=200|stream-a,stream-b|ended=true");
  return summary;
};
