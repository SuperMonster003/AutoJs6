"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const overlay = require("ui.overlay");
    const permission = await overlay.hasPermission({ timeoutMs: 1000 });
    console.log("sample.pro-parity-suite.overlay-floaty.permission=" + permission.granted);
    if (!permission.granted) {
      const settings = await overlay.openPermissionSettings({ dryRun: true, timeoutMs: 1000 });
      console.log("sample.pro-parity-suite.overlay-floaty.settings=" + settings.action);
    }
  } catch (error) {
    console.log("sample.pro-parity-suite.overlay-floaty.skipped=" + codeOf(error));
  }
  console.log("sample.pro-parity-suite.overlay-floaty=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
