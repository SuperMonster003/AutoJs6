"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("http-default-denial-probe");

  const result = await probe();

  assert.equal(result.ok, true);
  assert.match(result.summary, /^http:client\/default-denied\/ERR_AUTOJS6_(EMBEDDED_NODE_)?NETWORK_DISABLED\/agent$/);
  console.log("realnpm.http-client-package=" + result.summary);
  return "PASS";
};
