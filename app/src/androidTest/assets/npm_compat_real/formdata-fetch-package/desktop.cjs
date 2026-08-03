"use strict";

module.exports = async function run() {
  const assert = require("assert/strict");
  const form = new FormData();
  form.append("alpha", "1");
  form.append("beta", "two");
  const entries = Array.from(form.entries()).map(([key, value]) => key + "=" + value).join(",");

  assert.equal(entries, "alpha=1,beta=two");
  assert.equal(typeof fetch, "function");
  console.log("realnpm.formdata-fetch-package=formdata:fetch/alpha=1,beta=two/desktop-api");
  return "PASS";
};
