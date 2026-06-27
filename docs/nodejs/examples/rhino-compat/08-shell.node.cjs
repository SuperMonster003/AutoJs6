"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const result = await compat.shell("echo ok", { timeoutMs: 1000 });
  console.log(result.stdout.trim());
  const access = compat.shell.checkAccess({ root: false });
  console.log(JSON.stringify(access));
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
