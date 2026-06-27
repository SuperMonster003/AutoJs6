"nodejs";

const compat = require("autojs6:compat");

async function main() {
  await compat.toastLog("S11-28 dual-engine toast log");
  console.log("S11-28 dual-engine toast log");
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
