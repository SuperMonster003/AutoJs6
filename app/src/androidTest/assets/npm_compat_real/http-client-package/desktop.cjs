"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const http = require("http");
  const agent = new http.Agent({ keepAlive: true, maxSockets: 2 });

  try {
    assert.equal(typeof http.get, "function");
    assert.equal(agent instanceof http.Agent, true);
    assert.equal(agent.keepAlive, true);
    assert.equal(agent.maxSockets, 2);
    assert.equal(typeof agent.destroy, "function");
    console.log("realnpm.http-client-package=http:client/desktop-api/agent");
    return "PASS";
  } finally {
    agent.destroy();
  }
};
