"nodejs";

const compat = require("autojs6:compat");

async function main() {
  await compat.toast("hello from Node compat");
  await compat.toastLog("logged from Node compat");
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
