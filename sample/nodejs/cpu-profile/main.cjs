"nodejs";

try {
  require("inspector");
  console.log("sample.cpu-profile.mode=debug-only");
} catch (error) {
  console.log("sample.cpu-profile.mode=design-gated:" + (error && (error.autojs6Code || error.code || error.name)));
}

console.log("sample.cpu-profile=PASS");
