"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("url-buffer-codec-probe");
  const summary = probe.summarize("https://example.test/a/b?z=last&a=one%20two");

  assert.equal(summary, "urlbuffer:example.test:a=one+two&z=last:L2EvYg==");
  console.log("realnpm.url-buffer-codec=" + summary);
  return "PASS";
};
