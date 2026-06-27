"nodejs";

const esmModule = require("./value.mjs");
const value = esmModule.default || esmModule.value;

console.log("sample.require-esm.value=" + value);
console.log("sample.require-esm=PASS");
