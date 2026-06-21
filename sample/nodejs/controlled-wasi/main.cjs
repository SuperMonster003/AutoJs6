"nodejs";

const policy = { rawWasi: false, controlledProfile: "deferred" };
const pass = "sample.controlled-wasi=PASS";
console.log(!policy.rawWasi ? pass : "sample.controlled-wasi=FAIL");
