"nodejs";

const compat = require("autojs6:compat");

async function main() {
  const value = "S11-28 clipboard roundtrip";

  await compat.setClip(value);
  const actual = await compat.getClip();

  if (actual !== value) {
    throw new Error("clipboard roundtrip failed");
  }

  await compat.toastLog("clipboard roundtrip ok");
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
