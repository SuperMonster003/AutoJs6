"nodejs";

function codeOf(error) {
  return String(error && (error.code || error.autojs6Code || error.name || "ERROR"));
}

(async function main() {
  const websocket = require("websocket");
  console.log("sample.live-websocket.provider=controlled-websocket");
  console.log("sample.live-websocket.limit=" + websocket.policy.defaultMaxMessageBytes);

  try {
    const connection = await websocket.connect("wss://example.invalid/sample", {
      timeoutMs: 1000,
      maxMessageBytes: 1024,
      maxQueueSize: 2
    });
    await connection.send("hello", { timeoutMs: 1000 });
    await connection.close(1000, "done", { timeoutMs: 1000 });
    console.log("sample.live-websocket.closed=true");
  } catch (error) {
    console.log("sample.live-websocket.unavailable=" + codeOf(error));
  }

  console.log("sample.live-websocket=PASS");
})().catch((error) => {
  console.error(error && (error.stack || error.message) || error);
  process.exitCode = 1;
});
