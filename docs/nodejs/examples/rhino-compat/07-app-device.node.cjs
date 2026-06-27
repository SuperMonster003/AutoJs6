"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const launched = await compat.app.launchPackage("org.example.app", { timeoutMs: 5000 });
  const screenOn = await compat.device.isScreenOn({ timeoutMs: 1000 });
  if (!screenOn) {
    await compat.device.wakeUp({ timeoutMs: 1000 });
  }
  await compat.device.vibrate(50, { timeoutMs: 1000 });
  console.log(JSON.stringify({ launched, screenOn }));
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
