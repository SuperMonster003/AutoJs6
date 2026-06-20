"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("http-get-wrapper");

  const summary = await client.get("http://example.invalid/http-get?pkg=1");
  assert.equal(summary, "httpnpm.http-get=200|OK|GET|/http-get?pkg=1|http-get-ok");
  return summary;
};
