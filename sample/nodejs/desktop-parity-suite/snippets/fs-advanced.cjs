"nodejs";

const fs = require("fs");
const fsp = require("fs/promises");

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const dir = "./desktop-parity-fs";
  const file = dir + "/out.txt";
  try {
    await fsp.mkdir(dir, { recursive: true });
    const handle = await fsp.open(file, "w+");
    try {
      await handle.writeFile("desktop-fs");
      await handle.sync();
      console.log("sample.desktop-parity-suite.fs-advanced.handle=true");
    } finally {
      await handle.close();
    }
    try {
      const watcher = fs.watch(dir, { persistent: false }, () => {});
      watcher.close();
      console.log("sample.desktop-parity-suite.fs-advanced.watch=true");
    } finally {
      await fsp.rm(dir, { recursive: true, force: true });
    }
  } catch (error) {
    console.log("sample.desktop-parity-suite.fs-advanced.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.fs-advanced=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
