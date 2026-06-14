"nodejs";

const assert = require("assert");
const leftPad = require("left-pad-lite");
const slugify = require("slugify-lite");

const value = leftPad(slugify("AutoJs6 Safe Node"), 21, ".");
assert.strictEqual(value, "....autojs6-safe-node");

console.log("sample.npm-pure-js-cjs.value=" + value);
console.log("sample.npm-pure-js-cjs=PASS");
