"nodejs";

(async function main() {
  const accessibility = require("accessibility");
  const enabled = await accessibility.isEnabled();

  console.log("sample.accessibility-click-text.enabled=" + enabled);
  if (enabled) {
    const clicked = await accessibility.clickText("OK", { timeoutMs: 3000 });
    console.log("sample.accessibility-click-text.clicked=" + clicked);
  } else {
    console.log("sample.accessibility-click-text.clicked=SKIPPED");
  }

  console.log("sample.accessibility-click-text=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
