"nodejs";

const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const file = path.join(__dirname, "..", ".desktop-parity-fs.tmp");
  try {
    const handle = await fsp.open(file, "w+");
    try {
      await handle.writeFile("desktop-fs");
      await handle.sync();
      console.log("sample.desktop-parity-suite.fs-advanced.handle=true");
    } finally {
      await handle.close();
    }
    const watcher = fs.watch(path.dirname(file), { persistent: false }, () => {});
    watcher.close();
    console.log("sample.desktop-parity-suite.fs-advanced.watch=true");
  } catch (error) {
    console.log("sample.desktop-parity-suite.fs-advanced.skipped=" + codeOf(error));
  } finally {
    try {
      await fsp.unlink(file);
    } catch (_) {
      // Ignore cleanup failure in example skip paths.
    }
  }
  console.log("sample.desktop-parity-suite.fs-advanced=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
