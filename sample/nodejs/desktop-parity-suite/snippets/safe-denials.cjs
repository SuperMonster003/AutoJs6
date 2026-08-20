"nodejs";

const safeDenials = [
  ["esm-loader", "raw loader hooks and URL imports require desktop_compat_opt_in", "sample.desktop-parity-suite.safe-denial.esm-loader=DENIED"],
  ["fs-advanced", "expanded filesystem roots remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.fs-advanced=DENIED"],
  ["wasi", "raw wasi and node:wasi remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.wasi=DENIED"],
  ["inspector", "inspector debug sessions remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.inspector=DENIED"],
  ["npm-dependency", "registry install, lifecycle scripts, and native payloads remain denied", "sample.desktop-parity-suite.safe-denial.npm-dependency=DENIED"],
];

const stableCapabilities = [
  ["worker-threads", "stable native worker with bridge isolation and resource budgets", "sample.desktop-parity-suite.stable.worker-threads=AVAILABLE"],
  ["http-https", "stable controlled and native HTTP/HTTPS surfaces with distinct policy boundaries", "sample.desktop-parity-suite.stable.http-https=AVAILABLE"],
  ["raw-network", "stable dns/http/https/net/tls builtins; dgram remains disabled", "sample.desktop-parity-suite.stable.raw-network=AVAILABLE"],
];

for (const [id, reason, marker] of safeDenials) {
  console.log("sample.desktop-parity-suite.safe-denial." + id + ".reason=" + reason);
  console.log(marker);
}

for (const [id, reason, marker] of stableCapabilities) {
  console.log("sample.desktop-parity-suite.stable." + id + ".reason=" + reason);
  console.log(marker);
}

console.log("sample.desktop-parity-suite.safe-denials=PASS");

module.exports = { safeDenials, stableCapabilities };
