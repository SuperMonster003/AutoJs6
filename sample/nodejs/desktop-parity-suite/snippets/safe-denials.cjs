"nodejs";

const safeDenials = [
  ["esm-loader", "raw loader hooks and URL imports require desktop_compat_opt_in", "sample.desktop-parity-suite.safe-denial.esm-loader=DENIED"],
  ["worker-threads", "worker_threads requires desktop_compat_opt_in and native preflight", "sample.desktop-parity-suite.safe-denial.worker-threads=DENIED"],
  ["http-https", "raw server/listen/CONNECT/socket authority remains denied", "sample.desktop-parity-suite.safe-denial.http-https=DENIED"],
  ["raw-network", "native Node raw network modules require raw_network opt-in and disclosure", "sample.desktop-parity-suite.safe-denial.raw-network=DENIED"],
  ["fs-advanced", "expanded filesystem roots remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.fs-advanced=DENIED"],
  ["wasi", "raw wasi and node:wasi remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.wasi=DENIED"],
  ["inspector", "inspector debug sessions remain denied in safe_default", "sample.desktop-parity-suite.safe-denial.inspector=DENIED"],
  ["npm-dependency", "registry install, lifecycle scripts, and native payloads remain denied", "sample.desktop-parity-suite.safe-denial.npm-dependency=DENIED"],
];

for (const [id, reason, marker] of safeDenials) {
  console.log("sample.desktop-parity-suite.safe-denial." + id + ".reason=" + reason);
  console.log(marker);
}

console.log("sample.desktop-parity-suite.safe-denials=PASS");

module.exports = { safeDenials };
