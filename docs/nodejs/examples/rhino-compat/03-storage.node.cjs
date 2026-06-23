"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const store = compat.storages.create("rhino-compat");
  await store.put("enabled", true);
  const enabled = await store.get("enabled", false);
  await compat.toast(String(enabled));
  await store.remove("enabled");
}

main().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
