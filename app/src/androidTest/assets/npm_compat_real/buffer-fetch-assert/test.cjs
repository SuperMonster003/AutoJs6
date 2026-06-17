"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const bufferProbe = require("buffer-equals-probe");
  const fetchProbe = require("fetch-fallback-probe");
  const assertionProbe = require("assertion-helper-probe");

  const buffer = bufferProbe();
  const fetch = await fetchProbe();
  const assertion = await assertionProbe();

  assert.equal(buffer.ok, true);
  assert.equal(fetch.ok, true);
  assert.equal(assertion.ok, true);

  console.log([
    "realnpm.buffer-fetch-assert=" + buffer.summary,
    fetch.summary,
    assertion.summary,
  ].join("|"));
  return "PASS";
};
