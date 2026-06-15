"use strict";

module.exports = async function run() {
  const assert = require("assert");
  const semver = require("semver");
  const qs = require("qs");

  assert.strictEqual(semver.valid("1.2.3"), "1.2.3");
  assert.strictEqual(semver.major("2.3.4"), 2);
  assert.strictEqual(semver.satisfies("1.2.3", "^1.0.0"), true);

  const parsed = qs.parse("name=AutoJs6&tag=node&tag=safe");
  assert.deepStrictEqual(parsed, { name: "AutoJs6", tag: ["node", "safe"] });

  const formatted = qs.stringify(parsed);
  const parsedUrl = new URL("/pkg?" + formatted, "https://example.invalid");
  assert.strictEqual(parsedUrl.searchParams.get("name"), "AutoJs6");

  console.log([
    "realnpm.semver-query-url=" + semver.major("2.3.4"),
    semver.satisfies("1.2.3", "^1.0.0"),
    parsed.tag.join("+"),
    parsedUrl.searchParams.get("name"),
  ].join("|"));
  return "PASS";
};
