"nodejs";

try {
  const inspector = require("inspector");
  console.log("sample.inspector-debug.url=" + (inspector.url() || "disabled"));
} catch (error) {
  console.log("sample.inspector-debug.disabled=" + (error && (error.autojs6Code || error.code || error.name)));
}

console.log("sample.inspector-debug=PASS");
