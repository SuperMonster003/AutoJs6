# R2 current-tree partial diagnostic bundle

`tools/python/verify-r2-current-tree-partial-bundle.ps1` is a read-only
aggregator for the seven real-plugin diagnostic runner outputs. It gives reviewers
one checkable inventory for the exact current APK hashes and eleven diagnostic
cases without turning those partial observations into R2 acceptance.

The verifier does not invoke Gradle or ADB, install packages, alter a device, or
write a file. Its only output is a JSON object on stdout. In particular, it must
never be redirected to `build/reports/python/r2-runtime-poc-gate.json`; that path
is reserved for the future canonical R2 gate.

## Required inputs

Capture the successful stdout JSON from each current-tree runner in a distinct,
noncanonical evidence file:

1. `run-r2-real-plugin-device-diagnostic.ps1`
2. `run-r2-real-plugin-execution-matrix-diagnostic.ps1`
3. `run-r2-real-plugin-cancel-rebind-diagnostic.ps1`
4. `run-r2-real-plugin-timeout-rebind-diagnostic.ps1`
5. `run-r2-real-plugin-reliable-pipe-diagnostic.ps1`
6. `run-r2-real-plugin-staging-timeout-diagnostic.ps1`
7. `run-r2-real-plugin-blocking-retirement-diagnostic.ps1`

Also supply the exact host, androidTest, and arm64 Python-plugin APK paths,
their expected SHA-256 values, and explicit `aapt2` and `apksigner` executable
paths. The verifier recomputes every local APK digest, invokes those two
read-only APK inspection tools, and requires every runner transcript to name
the same three package/digest pairs. It is pinned to serial `QV710AF65F`, as
are these diagnostics.

The local APK identity checks are not inferred from filenames or transcripts:

- `aapt2 dump badging` must report the exact packages
  `org.autojs.autojs6`, `org.autojs.autojs6.test`, and
  `io.github.supermonster003.autojs6.plugin.python.runtime`;
- the host and plugin must each contain exactly `arm64-v8a` native code, while
  the androidTest APK must contain no native ABI;
- `apksigner verify --print-certs` must report exactly one signer for each APK,
  pinned to SHA-256
  `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.

Example (replace the seven evidence paths and digest placeholders):

```powershell
pwsh -NoProfile -File tools/python/verify-r2-current-tree-partial-bundle.ps1 `
  -Serial QV710AF65F `
  -PositiveEvidence build/reports/python/diagnostics/r2-positive.json `
  -ExecutionMatrixEvidence build/reports/python/diagnostics/r2-execution-matrix.json `
  -CancelRebindEvidence build/reports/python/diagnostics/r2-cancel-rebind.json `
  -TimeoutRebindEvidence build/reports/python/diagnostics/r2-timeout-rebind.json `
  -ReliablePipeEvidence build/reports/python/diagnostics/r2-reliable-pipe.json `
  -StagingTimeoutEvidence build/reports/python/diagnostics/r2-staging-timeout.json `
  -BlockingRetirementEvidence build/reports/python/diagnostics/r2-blocking-retirement.json `
  -HostApk app/build/outputs/apk/app/debug/autojs6-v6.8.0-alpha7-arm64-v8a.apk `
  -HostSha256 <64-hex-host-sha256> `
  -TestApk app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk `
  -TestSha256 <64-hex-androidTest-sha256> `
  -PluginApk D:/idea-projects/AutoJs6-Plugin-Python-Runtime/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk `
  -PluginSha256 <64-hex-plugin-sha256> `
  -Aapt2Path E:/.android/sdk/build-tools/36.1.0/aapt2.exe `
  -ApkSignerPath E:/.android/sdk/build-tools/36.1.0/apksigner.bat
```

If a persistent aggregate is useful, redirect stdout only to an explicitly
noncanonical diagnostic path, for example:

```powershell
New-Item -ItemType Directory -Force build/reports/python/diagnostics | Out-Null
pwsh -NoProfile -File tools/python/verify-r2-current-tree-partial-bundle.ps1 <arguments> `
  > build/reports/python/diagnostics/r2-current-tree-partial-bundle.json
```

## Checks performed

For every transcript, the verifier requires:

- schema `1`, status `PARTIAL_DIAGNOSTIC`, and the runner-specific scope;
- exact serial, API, ABI, and fingerprint coherence across all seven files;
- a numeric, duplicate-free `device.frozenUserIds` sequence which includes user
  `0` and has the same ordered integer values in all seven files;
- exactly the current three package/SHA-256 pairs;
- exactly the runner-specific case identifiers and
  `OK_1_TEST_ZERO_SKIP` for all eleven cases, including the exact bounded
  blocking `os.read` and native `hashlib.pbkdf2_hmac` retirement methods;
- the runner-specific successful package-restoration marker, plus the explicit
  Android-user restoration marker where that runner emits one;
- JSON booleans `acceptanceReceiptWritten=false`,
  `r2DeviceAcceptance=false`, and `appDataSnapshotTaken=false`.

The seven evidence inputs must have distinct canonical paths and distinct file
SHA-256 values. When an input lives below a `build` directory, its immediate
evidence directory must not contain a `STALE`, `HISTORICAL`, or `INVALIDATED`
marker filename, nor a small JSON/text marker with `status=HISTORICAL_ONLY`.
This intentionally rejects a mixture of fresh and invalidated evidence even if
the seven selected JSON files otherwise look valid. Archive or remove an obsolete
marker only after producing a wholly new, coherent current-tree set; never edit
an old transcript to make it appear current.

The summary repeats `PARTIAL_DIAGNOSTIC`, includes the verified package, signer,
native-ABI, and frozen-user inventory, sets both receipt/acceptance flags to
false, and records that this verifier itself ran neither Gradle nor ADB. Any
missing, stale, duplicated, malformed, skipped, unrestored, cross-device, or
wrong-artifact input fails closed with a nonzero process exit.

## Evidence boundary

A green bundle is evidence for eleven current-artifact observations on one exact
API-level/arm64 physical device only. It does not cover the full API 24-36 and
x86_64 matrix, hostile caller/signer/UID cases, provider update or uninstall
races, arbitrary third-party native blocking beyond the bounded PBKDF2 case,
repeated lifecycle/FD stress, or the production host no-replay path. Therefore
it is not an R2 gate and cannot authorize Python engine routing or a formal PASS
receipt.
