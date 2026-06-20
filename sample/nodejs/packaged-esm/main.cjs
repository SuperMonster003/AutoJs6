"nodejs";

(async function main() {
  const entry = await import("./src/message.mjs");
  console.log("sample.packaged-esm.message=" + entry.message);
  console.log("sample.packaged-esm=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
