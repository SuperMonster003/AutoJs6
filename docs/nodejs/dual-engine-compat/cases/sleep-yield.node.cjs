"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const startedAt = Date.now();

  await compat.sleep(25);

  if (Date.now() < startedAt) {
    throw new Error("clock moved backwards");
  }

  await compat.toastLog("sleep completed");
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
