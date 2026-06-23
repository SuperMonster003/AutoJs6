"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const ok = compat.text("OK").descContains("Confirm").clickable();
  const node = await ok.findOne(3000);
  if (node) {
    await ok.click({ timeoutMs: 3000 });
  }
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
