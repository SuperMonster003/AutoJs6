"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const digest = require("webcrypto-digest-probe");

  const hash = await digest("autojs6");

  assert.equal(hash, "772912755fce314684f7f4906f6b23055fd36ee4a57db12307576f0609b9fa73");
  console.log("realnpm.webcrypto-digest=sha256:" + hash);
  return "PASS";
};
