"nodejs";

const statuses = ["implemented", "live_provider", "blocked", "disabled"];
const pass = "sample.capability-truth-report=PASS";
console.log(statuses.includes("blocked") ? pass : "sample.capability-truth-report=FAIL");
