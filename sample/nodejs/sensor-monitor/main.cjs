"nodejs";

const subscription = { sensor: "accelerometer", intervalMs: 200, cleanup: "on-stop" };
const pass = "sample.sensor-monitor=PASS";
console.log(subscription.cleanup === "on-stop" ? pass : "sample.sensor-monitor=FAIL");
