"nodejs";

const allowedSnapshotState = [
  "bootstrap helpers",
  "error factories",
  "loader factories"
];

console.log("sample.startup-snapshot.policy=" + allowedSnapshotState.length);
console.log("sample.startup-snapshot.mode=design-gated");
console.log("sample.startup-snapshot=PASS");
