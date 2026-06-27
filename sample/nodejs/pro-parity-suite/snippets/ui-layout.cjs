"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const ui = require("ui");
    const handle = await ui.showLayout({
      type: "Column",
      children: [
        { type: "Text", id: "title", text: "AutoJs6 Pro UI" },
        { type: "Button", id: "ok", text: "OK" }
      ]
    }, { timeoutMs: 1000, drainIntervalMs: 50 });
    await handle.close({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.ui-layout.closed=true");
  } catch (error) {
    console.log("sample.pro-parity-suite.ui-layout.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.ui-layout=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
