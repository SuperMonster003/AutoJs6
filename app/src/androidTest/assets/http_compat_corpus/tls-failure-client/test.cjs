"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("tls-failure-wrapper");

  const summary = await client.probe();
  assert.equal(summary, "httpnpm.tls-failure=ERR_AUTOJS6_NETWORK_ERROR|fake TLS handshake failed");
  return summary;
};
