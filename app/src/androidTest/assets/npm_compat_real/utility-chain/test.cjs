"use strict";

module.exports = async function run() {
  const assert = require("assert");
  const isNumber = require("is-number");
  const isOdd = require("is-odd");
  const kindOf = require("kind-of");
  const once = require("once");

  let calls = 0;
  const wrapped = once((value) => {
    calls += 1;
    return String(value).toUpperCase();
  });

  const first = wrapped("alpha");
  const second = wrapped("beta");

  assert.strictEqual(isNumber("42"), true);
  assert.strictEqual(isOdd("13"), true);
  assert.strictEqual(kindOf(Buffer.from("x")), "buffer");
  assert.strictEqual(first, "ALPHA");
  assert.strictEqual(second, "ALPHA");
  assert.strictEqual(calls, 1);

  console.log([
    "realnpm.utility-chain=is-number:" + isNumber("42"),
    "is-odd:" + isOdd("13"),
    "kind:" + kindOf(Buffer.from("x")),
    "once:" + first + ":" + second + ":" + calls,
  ].join("|"));
  return "PASS";
};
