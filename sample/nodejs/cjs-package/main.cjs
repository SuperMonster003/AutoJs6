"nodejs";

const assert = require("assert");
const greet = require("./lib/greet");
const answer = require("sample-answer");

assert.strictEqual(greet("AutoJs6"), "hello AutoJs6");
assert.strictEqual(answer.value, 42);

console.log("sample.cjs-package.greeting=" + greet("AutoJs6"));
console.log("sample.cjs-package.answer=" + answer.value);
console.log("sample.cjs-package=PASS");
