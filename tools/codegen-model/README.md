# Layout code generation data and training tools

These offline development tools implement P2 and P3 of
[`layout-code-generation-model-roadmap.md`](../../docs/dev/layout-code-generation-model-roadmap.md).
They produce observed plan-survival labels using the host's actual candidate generator and verifier.
There is no training dependency in P2; Python uses only the standard library and `unittest`.
Task JSON is specified in [FORMAT.md](FORMAT.md).

The first complete controlled run is documented in the
[P2 acceptance report](reports/p2-dataset-2026.10-1.md): 2,688 task groups, 1,152 distinct snapshots,
133,704 candidate labels, zero discarded states, and byte-identical repeated JVM exports.

The [first P3 model report](reports/p3-first-model-2026.10-1.md) records a 6-tree candidate model,
two reproducible full fits, held-out comparison against the rule baseline, and the synthetic
private queue integration. Real application evaluation and Android inference remain subsequent work.

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

## Export survival labels and numeric features

From the repository root, run the explicitly enabled JVM exporter. Candidate search, all
cross-state checks, features, and the rule baseline use the same Kotlin implementations as the host.
Create the frozen split map before this export (the first manifest may have pending labels):

```powershell
python tools/codegen-model/dataset/split.py --input tools/codegen-model/out/cg-ds-2026.10-1 --output tools/codegen-model/out/cg-ds-2026.10-1 --dataset-id cg-ds-2026.10-1 --provenance tools/codegen-model/out/cg-ds-2026.10-1/collection.json
$env:CODEGEN_DATASET_DIR = (Resolve-Path 'tools/codegen-model/out/cg-ds-2026.10-1').Path
$env:CODEGEN_GENERATOR_COMMIT = (git rev-parse HEAD).Trim()
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.dataset.CodeGenDatasetExportTask'
Remove-Item Env:CODEGEN_DATASET_DIR, Env:CODEGEN_GENERATOR_COMMIT
```

`--rerun` forces only this test task, so changing the dataset path cannot accidentally reuse an
up-to-date result. Without `CODEGEN_DATASET_DIR`, normal unit-test runs skip this offline job.
Optional `CODEGEN_DATASET_OUTPUT_DIR` selects another output directory. For real input, keep
the input and all exported artifacts inside `CODEGEN_PRIVATE_DIR`, outside Git worktrees.
`CODEGEN_SPLIT_MAP` can select another frozen split-map file; its assignment must cover exactly
the exported groups, preserve complete fixture families, and put every real group in `real-test`.

The exporter writes deterministic `labels.jsonl`, `rules.jsonl`, `features.csv`, `features.json`,
and `export-manifest.json`. The CSV columns are `split,groupId,planKey,label` followed by the 64
ordered names in [FEATURES.md](FEATURES.md). The schema version is 1 and its name hash is
`9c6a5d8279ffca0e`. Metadata fields are CSV-quoted; every feature is a finite Kotlin `Float`.
Python reads these vectors directly and does not reconstruct features from layouts or labels.
It generates candidates only on `captureState`, then verifies those unchanged plans in each
other state. Survival is the observed fraction of successful future states. `rules.jsonl`
contains every group, including zero-candidate groups, and the rule-selected plan and survival.
The manifest records input, split-map, schema and output checksums. A failed parse or changed input does not
replace a previous complete export. Run twice with unchanged input, commit, and output options
to compare all five artifacts byte for byte. Refresh the dataset manifest with `dataset.split`
after exporting; it validates the Kotlin producer's hashes and records the feature/schema artifacts.

## Label real applications locally

Use the existing layout inspector's Save layout action to capture the same screen at different
times, app versions, or languages. Copy those snapshots into a private local directory outside
every Git worktree. From `tools/codegen-model`:

```powershell
$env:CODEGEN_PRIVATE_DIR = 'D:\codegen-private'
python -m label.label_real --snapshots D:\codegen-private\screen\s0.layout.json D:\codegen-private\screen\s1.layout.json --group-id real-example-1 --app com.example.app --screen-family checkout --output D:\codegen-private\tasks\example.cgtask.json --scope single --action click --target 'id submit'
```

Choose targets using `index 3` (zero-based preorder), `index 3,7` for a collection, `text QUERY`,
or `id QUERY`. Subsequent states recommend correspondences using full ID, class, text, and relative
position. Every state still requires an explicit `confirm`, `skip`, or `change`; a recommendation,
empty response, or EOF never approves a target. All confirmed states keep the same target count.
Skipping the first state writes nothing. One confirmed state may be saved for later collection,
but the survival exporter requires at least two. `--mutations` can describe each state's changes.

The tool copies snapshots only when needed to keep relative references below the task folder,
and only within the private directory. Raw layouts, tasks, labels, and split maps from real apps
stay private. The initial automated dataset contains controlled fixture data only; completing
this annotation tool does not mean the roadmap's suggested real-app evaluation sample has been
collected. Real application groups belong exclusively in `real-test` at this stage.

## Freeze splits and write the manifest

After collection finishes, run from `tools/codegen-model`:

```powershell
python -m dataset.split --input out/cg-ds-2026.10-1 --output out/cg-ds-2026.10-1 --dataset-id cg-ds-2026.10-1 --provenance out/cg-ds-2026.10-1/collection.json
```

The command writes `split-map.json` and `dataset-manifest.json`. It rejects incomplete collection,
disagreement between recorded and discovered groups, inconsistent capture counters, and a discard
rate above 2%. It detects `labels.jsonl`, `rules.jsonl`, `export-manifest.json`, and later
`features.csv` beside its output. Alternatively pass `--labels`, `--rules`, and `--features`.
The exporter input digest and artifact hashes reject stale labels after task or snapshot changes.
If the input roots differ from the exporter's input directory, supply `--export-input-root`.

Run once before label export if a split map is needed, then repeat the same command after the JVM
export. Missing labels or features are explicitly pending with null hashes, never placeholder
files. P2 originally ended with verified labels and `features-pending`; P3.2 supplies the real
feature/schema files and their checksums, moving the manifest to `complete`.
`--generated-at` accepts a fixed timestamp for reproducible manifest comparison.

The hash assignment unit is the entire `screenFamily`, including all seeds and states. Quotas
use 70/15/15 with largest-remainder rounding. Eight indivisible, equally sized families therefore
produce 6/1/1 families: **75% / 12.5% / 12.5%**. A three-percentage-point bound is impossible for
this eight-family dataset; tests verify the target tolerance with sufficiently many families and
verify the exact rounding for eight. The manifest records actual family and group ratios. Freeze
the split map before training; introducing new families can change hash-ranked assignments.

Real application tasks always go to independent `real-test`, grouped by app. For a dataset that
contains any real tasks, all combined labels, features, split maps, and manifests must also stay
in `CODEGEN_PRIVATE_DIR`. Publish only separately reviewed aggregate reports.

## Train a candidate model

Training uses Python 3.12 and the exact PC dependencies in `train/requirements.txt`:
CatBoost 1.2.10 and NumPy 2.2.6. Create an isolated environment under ignored `out/`, then train
from the completed dataset manifest. Run these commands from the repository root:

```powershell
py -3.12 -m venv tools/codegen-model/out/venv-py312
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m pip install -r tools/codegen-model/train/requirements.txt
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/train/train.py --dataset tools/codegen-model/out/cg-ds-2026.10-1 --output tools/codegen-model/out/p3-first
```

The default is CPU `CatBoostRanker`, YetiRank, symmetric depth-6 trees, at most 1,024 iterations,
seed `20261003`, one thread, learning rate `0.05`, and validation `NDCG:top=1` early stopping
after 64 non-improving iterations. Only train and valid participate in fitting and early stopping.
The first dataset's observed labels `0 / 0.5 / 1` become grades `0 / 1 / 2`. Other survival
fractions remain valid for evaluation but require an explicit future training-grade policy;
the current trainer rejects them instead of silently changing their meaning.

`--output` must name a new directory outside the frozen dataset. The completed run contains
`model.cbm`, CatBoost's original `model.raw.json`, canonical `model.json`, `features.json`, and
`train-log.json`. The log records input and producer hashes, capture and feature-export commits,
all parameters, dependency versions, machine, timing, tree count, and validation history.

CatBoost's raw JSON contains a model GUID and training-completion time. Canonical `model.json`
removes only `model_info.model_guid` and `model_info.train_finish_time`, retaining the original
export for audit. Numerical splits, thresholds, leaves, feature definitions and scale/bias are
unchanged. Train again into a second directory with the same dataset/options and compare both
the canonical bytes and the numerical contents of the original exports. Android package and
inference parity are handled by the subsequent P4 tools.

The standard Python tests do not require CatBoost. To include the actual two-fit and model-loading
tests in the pinned environment:

```powershell
$env:CODEGEN_TEST_TRAINING = '1'
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m unittest discover tools/codegen-model
Remove-Item Env:CODEGEN_TEST_TRAINING
```

The pinned versions provide Python 3.12 Windows wheels in [CatBoost's package release](https://pypi.org/project/catboost/1.2.10/)
and [NumPy's package release](https://pypi.org/project/numpy/2.2.6/). Training parameters follow
the [CatBoostRanker API](https://catboost.ai/docs/en/concepts/python-reference_catboostranker)
and [overfitting detector options](https://catboost.ai/docs/en/references/training-parameters/overfitting-detection).

## Evaluate held-out tasks

```powershell
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/train/evaluate.py --dataset tools/codegen-model/out/cg-ds-2026.10-1 --model tools/codegen-model/out/p3-first/model.cbm --output tools/codegen-model/out/p3-first/evaluation
```

The evaluator predicts with the actual model on test and independent real-test. It writes
`metrics.json` and `report.md` containing all six roadmap E.4 metrics, the original rule baseline,
coverage including groups with no candidates, and breakdowns by selected plan family, mutation,
screen family and source. Mutation buckets contain groups whose evaluation states carry that
mutation and can overlap. The 50 largest model-versus-rule regressions include the two plans
and their failed mutations for the next collection round.

Empty real-test has zero groups and null metrics. It cannot establish real-app quality or pass
the later real-test publication gate. Current tasks and snapshots are checked against the frozen
manifest as well as the feature/label hashes, so changing targets without re-exporting is rejected.
Detailed reports may include private text inside plan keys; reports from any real-containing
dataset stay in `CODEGEN_PRIVATE_DIR`. Public reports must contain reviewed aggregates only.

Score ties use a deterministic convention shared with the future Android ranker: sort exact
scores descending, form disjoint batches whose scores differ by less than `1e-6` from each batch
maximum, then order each batch by the Kotlin rule rank. This avoids a non-transitive approximate
comparator and does not depend on candidate input order.

## Prioritize the next private collection

First use `label_real.py` to confirm the capture-state target(s). Future states may be skipped;
the saved single-state task expresses the intended target without inventing future truth. A raw
layout with no selected target lacks a generation intent and cannot supply candidate plans.
Tasks with two or more confirmed states already support labeling and are excluded from this queue.

Export capture-only Kotlin candidates to a new private directory:

```powershell
$env:CODEGEN_PRIVATE_DIR = 'D:\codegen-private'
$env:CODEGEN_QUEUE_DIR = 'D:\codegen-private\tasks'
$env:CODEGEN_QUEUE_OUTPUT_DIR = 'D:\codegen-private\candidate-export-1'
$env:CODEGEN_GENERATOR_COMMIT = (git rev-parse HEAD).Trim()
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.dataset.CodeGenQueueExportTask'
Remove-Item Env:CODEGEN_QUEUE_DIR, Env:CODEGEN_QUEUE_OUTPUT_DIR, Env:CODEGEN_GENERATOR_COMMIT
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/train/queue.py --candidates D:\codegen-private\candidate-export-1\candidates.jsonl --features D:\codegen-private\candidate-export-1\features.json --model tools/codegen-model/out/p3-first/model.cbm --output D:\codegen-private\queue-1
```

The JVM export writes `candidates.jsonl`, `features.json`, and `queue-manifest.json`. It uses the
same capture-state search, verifier and feature extractor, without producing survival labels.
Each candidate records its plan key, zero-based rule rank and feature vector. Task/snapshot
checksums bind it to the maintained capture intent; changing targets or layouts requires re-export.
Existing export directories are preserved and completed exports are published together.

`queue.jsonl` contains only model/rule disagreements with a small raw score gap (default `0.1`,
configurable by `--max-score-gap`), ordered by gap and stable task identity. `--limit` defaults to
100; optional `--labels` excludes already labeled group IDs. `queue-summary.json` records exclusions
and provenance. The score gap is a collection heuristic, not a confidence probability. Follow the
queue to capture additional states and explicitly confirm targets with the annotation tool;
the queue does not edit existing truth or accept a recommended correspondence automatically.
