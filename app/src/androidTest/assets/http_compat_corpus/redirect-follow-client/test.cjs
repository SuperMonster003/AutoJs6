"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("follow-redirects-lite");

  const summary = await client.get("http://example.invalid/redirect-start");
  assert.equal(summary, "httpnpm.redirect-follow=200|OK|/redirect-final|redirect-ok");
  return summary;
};
