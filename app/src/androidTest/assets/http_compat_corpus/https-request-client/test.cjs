"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("https-request-wrapper");

  const summary = await client.post("https://example.invalid/https-request", "secure-data");
  assert.equal(summary, "httpnpm.https-request=201|Created|POST|/https-request|payload=secure-data|agent=true");
  return summary;
};
