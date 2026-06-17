"nodejs";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

fs.mkdirSync("out", { recursive: true });
fs.writeFileSync(path.join("out", "message.txt"), "scoped fs\n", "utf8");

const message = fs.readFileSync(path.join("out", "message.txt"), "utf8").trim();
assert.strictEqual(message, "scoped fs");

console.log("sample.fs-scoped.message=" + message);
console.log("sample.fs-scoped=PASS");
