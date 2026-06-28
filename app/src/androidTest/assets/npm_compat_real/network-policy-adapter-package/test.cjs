"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const probe = require("network-policy-adapter-probe");
  const summary = probe.describe({
    url: "https://example.invalid/api",
    allowNetwork: false,
    adapter: "offline",
  });

  assert.equal(summary, "networkpolicy:https:denied:adapter");
  console.log("realnpm.network-policy-adapter=" + summary);
  return "PASS";
};
