"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  try {
    const math = require("../vendor/pure-math");
    console.log("sample.desktop-parity-suite.npm-dependency.sum=" + math.sum([8, 13, 21]));
  } catch (error) {
    console.log("sample.desktop-parity-suite.npm-dependency.skipped=" + codeOf(error));
  }
  console.log("sample.desktop-parity-suite.npm-dependency=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
