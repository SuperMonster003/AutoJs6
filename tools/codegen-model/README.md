# Layout code generation dataset tools

These offline development tools implement P2 of
[`layout-code-generation-model-roadmap.md`](../../docs/dev/layout-code-generation-model-roadmap.md).
They produce observed plan-survival labels using the host's actual candidate generator and verifier.
There is no training dependency in P2; Python uses only the standard library and `unittest`.
Task JSON is specified in [FORMAT.md](FORMAT.md).

## Build and prepare

From the repository root, build the controlled View application:

```powershell
.\gradlew.bat --offline :test-apps:codegen-fixture:assembleDebug :test-apps:codegen-fixture:testDebugUnitTest
adb -s DEVICE install -t -r test-apps/codegen-fixture/build/outputs/apk/debug/codegen-fixture-debug.apk
python -m unittest discover tools/codegen-model
```

Use an installed AutoJs6 build containing P1, with its native accessibility service enabled.
The collector requires an explicit device serial and captures only
`org.autojs.test.codegen.fixture`. Keep the device unlocked and leave the fixture in the foreground
while collection runs. Do not run instrumentation against the host during collection, because it
restarts the accessibility-service process on some devices.

The [fixture README](../../test-apps/codegen-fixture/README.md) documents screen names, mutations,
private ground truth, action receipts, and the readiness handshake.

## Capture

From `tools/codegen-model`, first run a small pilot:

```powershell
python -m capture.collect --serial DEVICE --output out/pilot --dataset-id cg-pilot --seeds 1
```

Then use a fresh directory for a complete dataset:

```powershell
python -m capture.collect --serial DEVICE --output out/cg-ds-2026.10-1 --dataset-id cg-ds-2026.10-1 --seeds 48
```

Use `--adb C:\path\to\adb.exe` if adb is not on PATH. Each of eight families has 48 deterministic
seeds and three states: a base, one isolated mutation, and a pair of mutations. Each complete
scenario supplies six single-target groups and one collection group, for 2,688 groups and 1,152
captured layouts. Collection targets are the six independently tagged controls, including when
no candidate can express the whole set; the later label exporter reports that coverage explicitly.
No-id pages substitute meaningful changes for ID mutations that would have no effect.

`capture.js` is started once through `RunIntentActivity` and reads requests in a separate run
directory under the host's own external files. Each request has a new sequence number and run token. The fixture
publishes readiness only after its geometry is stable, then the worker calls the existing
`NodeInfo.capture` and `LayoutSnapshotFiles.save` Java entry points. It copies and removes only
the snapshot it just saved, so hundreds of temporary layouts are not left in the user's layout list.
The worker exits on the collector's stop request, or after 180 seconds without a request.

Ground truth is read separately through `run-as` from the fixture's private storage. Association
uses exact visible screen bounds, then depth to disambiguate equal rectangles; class is audited
after matching. A mismatch rejects the state instead of guessing. Oracle keys never become node
attributes or model features. States without all six targets reject the whole scenario.

Every run records attempted, accepted, and discarded states, reasons, timing, device, fixture
version, source commit, and the collection plan in `collection.json`. Completed scenarios are
checkpointed; repeating the same command resumes unfinished work only with matching provenance.
Scenarios are assembled in a sibling staging directory outside the exporter input root and
published by one directory rename. A published but uncheckpointed scenario is retained outside
the input root before retrying, so old tasks cannot refer to replacement snapshots. Interrupted
and timed-out captures count as discards. Rejected completed scenarios are not silently retried.
Three consecutive failures stop the run;
a final discard rate above 2% fails collection. Inspect and fix the cause before making a new dataset.

Raw controlled snapshots, task files, truth, labels, and manifests belong under ignored `out/`.
Commit tool sources and aggregate reports only. Real application data must use the separate
`CODEGEN_PRIVATE_DIR` workflow and must never be placed in this repository.

For the P2.3 device equivalence check, run this interactive helper, use the real floating menu's
layout-inspection button, close the inspector, then press Enter in the terminal:

```powershell
python -m capture.compare_inspector --serial DEVICE --output out/inspector-comparison
```

It compares node counts and the entire preorder class-name sequence against the worker capture,
and writes `comparison.json`. The inspector capture is read from the UI's actual capture object;
the helper does not replace it with another scripted capture.

## Export survival labels

From the repository root, run the explicitly enabled JVM exporter. Candidate search, all
cross-state checks, and the rule baseline use the same Kotlin implementations as the host:

```powershell
$env:CODEGEN_DATASET_DIR = (Resolve-Path 'tools/codegen-model/out/cg-ds-2026.10-1').Path
$env:CODEGEN_GENERATOR_COMMIT = (git rev-parse HEAD).Trim()
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.dataset.CodeGenDatasetExportTask'
Remove-Item Env:CODEGEN_DATASET_DIR, Env:CODEGEN_GENERATOR_COMMIT
```

`--rerun` forces only this test task, so changing the dataset path cannot accidentally reuse an
up-to-date result. Without `CODEGEN_DATASET_DIR`, normal unit-test runs skip this offline job.
Optional `CODEGEN_DATASET_OUTPUT_DIR` selects another output directory. For real input, keep
the input and all exported artifacts inside `CODEGEN_PRIVATE_DIR`, outside Git worktrees.

The exporter writes deterministic `labels.jsonl`, `rules.jsonl`, and `export-manifest.json`.
It generates candidates only on `captureState`, then verifies those unchanged plans in each
other state. Survival is the observed fraction of successful future states. `rules.jsonl`
contains every group, including zero-candidate groups, and the rule-selected plan and survival.
The manifest records input and output checksums. A failed parse or changed input does not
replace a previous complete export. Run twice with unchanged input, commit, and output options
to compare all three artifacts byte for byte.
