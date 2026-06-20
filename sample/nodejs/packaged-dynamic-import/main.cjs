"nodejs";

(async function main() {
  const featureName = "formatter";
  const feature = await import("./features/" + featureName + ".mjs");
  console.log("sample.packaged-dynamic-import.result=" + feature.format("dynamic"));
  console.log("sample.packaged-dynamic-import=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
