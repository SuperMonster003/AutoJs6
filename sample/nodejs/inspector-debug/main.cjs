"nodejs";

try {
  const inspector = require("inspector");
  inspector.open(0, "127.0.0.1", false);
  const url = inspector.url() || "";
  console.log("sample.inspector-debug.loopback=" + url.startsWith("ws://127.0.0.1:"));
  inspector.close();
} catch (error) {
  console.log("sample.inspector-debug.debug-request-required=" + (error && (error.autojs6Code || error.code || error.name)));
}

console.log("sample.inspector-debug=PASS");
