"nodejs";

const Module = require("module");

if (typeof Module.enableCompileCache === "function") {
  try {
    const result = Module.enableCompileCache();
    console.log("sample.compile-cache.status=" + result.status);
  } catch (error) {
    console.log("sample.compile-cache.status=blocked:" + (error && (error.code || error.name)));
  }
} else {
  console.log("sample.compile-cache.status=unavailable");
}

const first = require("./lib/counter.cjs");
const second = require("./lib/counter.cjs");

console.log("sample.compile-cache.same-module=" + (first === second));
console.log("sample.compile-cache=PASS");
