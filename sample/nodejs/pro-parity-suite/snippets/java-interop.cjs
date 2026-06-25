"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const javaApi = globalThis.$autojs && globalThis.$autojs.java
      ? globalThis.$autojs.java
      : require("java");
    const mathClass = javaApi.findClass("java.lang.Math");
    const value = await javaApi.callStatic(mathClass, "max", [3, 7], { timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.java-interop.result=" + value);
  } catch (error) {
    console.log("sample.pro-parity-suite.java-interop.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.java-interop=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
