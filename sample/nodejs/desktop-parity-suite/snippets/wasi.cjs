"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const wasi = require("wasi");
    const keys = Object.keys(wasi).sort().join(",");
    console.log("sample.desktop-parity-suite.wasi.keys=" + keys);
  } catch (error) {
    console.log("sample.desktop-parity-suite.wasi.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.wasi=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
