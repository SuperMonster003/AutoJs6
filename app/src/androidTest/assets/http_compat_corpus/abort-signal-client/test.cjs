"use strict";

module.exports = async function run(ctx) {
  const assert = require("assert/strict");
  const client = require("abort-signal-wrapper");

  const summary = await client.probe(ctx);
  assert.equal(summary, "httpnpm.abort-signal=ERR_AUTOJS6_NETWORK_ABORTED|ERR_AUTOJS6_NETWORK_ABORTED|pre=0");
  return summary;
};
