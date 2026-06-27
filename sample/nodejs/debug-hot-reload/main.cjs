"nodejs";

const reload = { debugOnly: true, packagedRelease: false };
const pass = "sample.debug-hot-reload=PASS";
console.log(reload.debugOnly && !reload.packagedRelease ? pass : "sample.debug-hot-reload=FAIL");
