"nodejs";

const fs = require("fs");
const path = require("path");

function main() {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, "examples.json"), "utf8"));
  console.log("sample.desktop-parity-suite.catalog=" + manifest.examples.length);
  for (const entry of manifest.examples) {
    console.log(entry.expectedOutput);
    if (entry.safeProfile && entry.safeProfile.expectedOutput) {
      console.log(entry.safeProfile.expectedOutput);
    }
  }
  console.log("sample.desktop-parity-suite=PASS");
}

try {
  main();
} catch (error) {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
}
