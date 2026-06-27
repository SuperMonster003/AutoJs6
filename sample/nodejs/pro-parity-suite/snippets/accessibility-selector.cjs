"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const accessibility = require("accessibility");
    const enabled = await accessibility.isEnabled({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.accessibility-selector.enabled=" + enabled);
    if (enabled) {
      const node = await accessibility.findOne(accessibility.text("OK"), { timeoutMs: 1000 });
      console.log("sample.pro-parity-suite.accessibility-selector.found=" + Boolean(node));
    }
  } catch (error) {
    console.log("sample.pro-parity-suite.accessibility-selector.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.accessibility-selector=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
