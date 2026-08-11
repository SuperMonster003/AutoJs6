# R2 device diagnostic failure evidence

`write-r2-device-diagnostic-failure-evidence.ps1` preserves a failed Python R2
device diagnostic without turning the failure into acceptance evidence. It is a
source-only post-processing helper: it reads an already-finished transcript and
local input files, and it never starts Gradle, ADB, an instrumentation runner, or
another device operation.

## Output contract

The default root is `build/reports/python/r2-failures`. Each successful capture
publishes one immutable-by-convention directory whose name contains a UTC stamp
and a random GUID:

```text
build/reports/python/r2-failures/
  manifest.jsonl
  r2-failure-20260810T123456789Z-<32-hex-guid>/
    failure.json
    raw-transcript.bin
```

The helper writes both bundle files in a unique staging directory with
`CreateNew`, flushes them, and atomically renames that directory without an
overwrite option. It then opens `manifest.jsonl` with one exclusive writer,
validates every existing line, its SHA-256 chain, and the referenced bundle
lengths/hashes, and appends exactly one flushed JSONL record. Existing manifest
bytes are never rewritten or truncated.
A partial final manifest line is treated as corruption and blocks later appends;
the helper does not repair or discard earlier evidence.

If bundle publication succeeds but manifest append fails, the uniquely named
bundle remains as orphaned failure evidence and the command exits non-zero. It
must be investigated; it must not be silently deleted or represented as an
accepted run.

`failure.json` schema 1 records:

- the fixed `FAILURE_CAPTURED` status and failure-only scope;
- UTC time, GUID-bearing failure ID, exact device serial, diagnostic name,
  instrumentation selector, non-zero runner exit code, and a bounded summary;
- byte length and SHA-256 for every supplied artifact and source file;
- a byte-exact retained transcript with byte length and SHA-256;
- explicit mutation-attempt/observation and restoration-verification booleans;
- `formalReceiptWritten=false`, `r2DeviceAcceptance=false`, and
  `runtimeRoutingAuthorized=false`.

The manifest records the evidence/transcript paths, lengths, hashes, monotonically
increasing sequence, previous-entry hash, and its own payload hash. It is a
failure ledger, not a formal gate receipt. The helper never writes
`r2-runtime-poc-gate.json`, `r2-device-conformance-gate.json`, or any other gate
receipt.

## Capture example

Run the diagnostic separately and redirect both streams to a raw file while
retaining its non-zero process exit code. After all device restoration checks
have completed, invoke the helper with the exact inputs used by that run:

```powershell
& tools/python/write-r2-device-diagnostic-failure-evidence.ps1 `
  -Serial QV710AF65F `
  -DiagnosticName r2-real-plugin-timeout-rebind `
  -Selector 'org.autojs.autojs.python.PythonRuntimePluginRealIntegrationTest#timeoutKillsOldBinderThenExactRebindRunsFiniteRequestOnce' `
  -RunnerExitCode 1 `
  -FailureSummary 'Instrumentation returned a non-zero exit after provider timeout.' `
  -RawTranscript build/reports/python/diagnostics/r2-timeout-rebind.raw.log `
  -Artifact @(
    'host=app/build/outputs/apk/app/debug/autojs6-v6.8.0-alpha7-arm64-v8a.apk',
    'test=app/build/outputs/apk/androidTest/app/debug/app-app-debug-androidTest.apk',
    'plugin=D:/idea-projects/AutoJs6-Plugin-Python-Runtime/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk'
  ) `
  -Source @(
    'runner=tools/python/run-r2-real-plugin-timeout-rebind-diagnostic.ps1',
    'instrumentation=app/src/androidTest/java/org/autojs/autojs/python/PythonRuntimePluginRealIntegrationTest.kt'
  ) `
  -MutationAttempted `
  -MutationObserved `
  -RestorationAttempted `
  -PackageStateRestorationVerified `
  -AndroidUserSetRestorationVerified `
  -RestorationVerified
```

Every `Artifact` and `Source` value uses `lowercase-role=literal-path` syntax.
Roles and resolved paths must be unique within their collection. Files are
opened without writer sharing while they are hashed, so the helper fails closed
if an input is still being written. The raw transcript defaults to a 64 MiB
limit; `-MaximumTranscriptBytes` may lower or raise it within the declared
1-byte to 1-GiB bound.

For a failure before any device mutation, omit all mutation/restoration switches.
`MutationObserved` requires `MutationAttempted`. Any restoration-verification
flag requires `RestorationAttempted`, and overall restoration verification also
requires both package-state and Android-user-set verification. These are caller
assertions preserved for audit; the helper does not contact the device to infer
them.

`-OutputDirectory` exists only to support isolated local parser/negative tests.
Operational captures should omit it so the default failure ledger is used.

## Source-only checks

The following checks need only PowerShell 7 and disposable local files. They do
not authorize or invoke Gradle/ADB:

- exit code `0` is rejected before any output is created;
- incoherent mutation/restoration flags are rejected;
- malformed or duplicate `role=path` inputs are rejected;
- two valid captures produce different UTC+GUID directories and manifest
  sequences 1 then 2;
- parsing both evidence objects confirms schema 1, failure-only status, false
  acceptance flags, selector/exit-code preservation, and exact file hashes;
- decoding each manifest line, rebuilding its ordered payload with fixed UTC
  precision, and hashing that canonical JSON confirms the entry hash and
  previous-entry chain;
- a manifest with a partial final line blocks a subsequent append rather than
  rewriting prior bytes.

The console JSON returned after a capture is only a locator and hash summary for
the failure bundle. Neither it nor the ledger is sufficient to check an R2 or R3
roadmap gate.
