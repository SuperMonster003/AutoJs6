"nodejs";

const versions = process.versions || {};
const features = {
  node: versions.node || "unknown",
  v8: versions.v8 || "unknown",
  uv: versions.uv || "unknown",
  arch: process.arch || "unknown",
  platform: process.platform || "unknown"
};

console.log("sample.node-runtime-info.node=" + features.node);
console.log("sample.node-runtime-info.arch=" + features.platform + "/" + features.arch);
console.log("sample.node-runtime-info=PASS");
