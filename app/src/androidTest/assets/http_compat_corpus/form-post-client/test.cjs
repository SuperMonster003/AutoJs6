"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const client = require("form-post-wrapper");

  const summary = await client.post("http://example.invalid/form-post", { alpha: "1", beta: "two" });
  assert.equal(summary, "httpnpm.form-post=202|application/x-www-form-urlencoded|alpha=1&beta=two");
  return summary;
};
