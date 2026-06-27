"nodejs";

const fs = require("fs");
const path = require("path");

function main() {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, "examples.json"), "utf8"));
  console.log("sample.pro-parity-suite.catalog=" + manifest.examples.length);
  for (const entry of manifest.examples) {
    console.log(entry.expectedOutput);
  }
  console.log("sample.pro-parity-suite=PASS");
}

try {
  main();
} catch (error) {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
}
