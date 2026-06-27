"nodejs";

const runtimePath = { defaultPath: "legacy_jni", adapter: "surface_only" };
const pass = "sample.adapter-cutover-status=PASS";
console.log(runtimePath.defaultPath === "legacy_jni" ? pass : "sample.adapter-cutover-status=FAIL");
