"use strict";

module.exports = async function run(ctx) {
  const assert = require("assert/strict");
  const client = require("proxy-denial-wrapper");

  const summary = await client.probe(ctx);
  assert.equal(summary, "httpnpm.proxy-denial=ERR_AUTOJS6_NETWORK_POLICY_DENIED|ERR_AUTOJS6_NETWORK_POLICY_DENIED|dispatch=0");
  return summary;
};
