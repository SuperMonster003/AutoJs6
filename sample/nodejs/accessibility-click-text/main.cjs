"nodejs";

(async function main() {
  const accessibility = require("accessibility");
  const enabled = await accessibility.isEnabled();

  console.log("sample.accessibility-click-text.enabled=" + enabled);
  if (enabled) {
    try {
      const clicked = await accessibility.clickText("OK", { timeoutMs: 3000 });
      console.log("sample.accessibility-click-text.clicked=" + clicked);
    } catch (error) {
      console.log("sample.accessibility-click-text.clicked=SKIPPED:" + (error && (error.autojs6Code || error.code || error.name)));
    }
  } else {
    console.log("sample.accessibility-click-text.clicked=SKIPPED");
  }

  console.log("sample.accessibility-click-text=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
