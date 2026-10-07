# Construction-policy training prototype

This tool learns a numeric score for a **construction state and one legal next
action**, before a complete locate plan exists. It is independent of the existing
64-feature candidate ranker. `policy.json` is an offline experiment artifact, not
an installable `.ajcgm` package; training does not sign, publish, or replace models.

## Kotlin export contract

The input directory contains exactly these required files; additional evidence
may coexist in that directory:

- `features.json`: `format: 1`, `role: "construction-state-action"`,
  `grammarVersion: 1`, `featureSchemaVersion: 1`, ordered `names`, and `namesHash`.
  The hash is the first 16 lowercase hexadecimal characters of SHA-256 over names
  joined with LF, without a trailing LF.
- `actions.csv`: columns `split,groupId,stateKey,actionKey,label,observations`,
  followed by every numeric feature in schema order. Python does not recreate
  features from layouts, paths, target truth, or future snapshots. It converts
  the exported numbers to Float32, matching Kotlin and CatBoost's numeric pool.
  The loader keeps each vector in a standard-library `array('f')` rather than
  one Python float object per value; pool creation uses a single preallocated
  Float32 matrix. This storage choice does not change features or sample order.
- `export-manifest.json`: `format: 1`, nonempty `datasetId`, `featuresSha256`,
  `actionsSha256`, and `groups` objects with `groupId`, `split`, `source`, and
  `screenFamily`. Additional exporter provenance and coverage metadata are
  preserved in the training log.

Each label is the mean survival of **observed, completed exploration rollouts**
after that state/action, within `[0, 1]`. It is not an exact optimal continuation
value. `observations` is a positive integer. Unexplored or unfinished actions
must be omitted, not recorded as zero-survival examples. A task with no observed
rows remains present in manifest groups and exporter coverage statistics.

The loader checks checksums, unique schema names, exact CSV column order, finite
Float32 features, label range, positive observation counts, unique
`(groupId, stateKey, actionKey)`, and group/split consistency. A screen family must
not span splits. Fixture groups may use `train`, `valid`, or `test`; real groups
may use only `real-test`. Inputs containing real data and every derived output
must stay under `CODEGEN_PRIVATE_DIR`, outside Git worktrees.

## Reproducible training

Use Python 3.12 with the existing pinned dependencies in
[`../train/requirements.txt`](../train/requirements.txt). From the host root:

```powershell
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/train.py `
  --dataset-dir tools/codegen-model/out/construction-export `
  --output-dir tools/codegen-model/out/construction-fit-1
```

Alternatively, run `python -m construction.train` from `tools/codegen-model`.
The Python API is `construction.train.train(dataset_dir, output_dir, **options)`.

Defaults are `--iterations 256`, `--depth 6`, `--seed 20261007`,
`--early-stopping-rounds 32`, and `--parity-max-rows 5000`. Iterations are bounded
to 256 and depth to 6. Training uses CatBoostRegressor, symmetric trees, RMSE,
CPU, one thread, and a fixed learning rate of 0.05. Only `train` fits the weights;
only `valid` controls early stopping and best-iteration selection. `test` and
`real-test` are validated as protocol input but their labels and vectors never
enter either pool. Do not tune these options from held-out results.

State/action rows receive **equal weight**. Observation counts describe teacher
exploration coverage; they do not establish independent samples and are not used
as weights. The model predicts the same-scale observed mean return. Raw scores
are not clamped to `[0, 1]`, calibrated as probabilities, or added across steps.
End-to-end search quality requires separate held-out experiments.

The output must be a new directory separate from the frozen export. Controlled
outputs may use ignored `tools/codegen-model/out` or a directory outside Git.
An existing output is refused. Files are prepared in a checked sibling temporary
directory, then moved into an exclusively created output; `train-log.json` is
published last. A directory without that log is incomplete and must not be used.
The tool never overwrites such a directory after an interrupted publication.

## Artifacts and interpreter parity

- `model.cbm`: original CatBoost model, including volatile training metadata.
- `model.raw.json`: original numeric CatBoost JSON.
- `model.json`: deterministic JSON serialization after removing only
  `model_info.model_guid` and `model_info.train_finish_time`. Numerical model
  values remain unchanged. Compare this artifact, not `.cbm`, across repeat fits.
- `features.json`: exact input schema bytes.
- `policy.json`: `format: 1`, `role: "construction-policy"`, grammar/schema
  versions, feature hash/count, `scoreSemantics: "observed-rollout-survival-v1"`,
  and a `model` object containing `depths`, `splitOffsets`, `leafOffsets`,
  `featureIndices`, `thresholds`, `leafValues`, `bias`, and `scale`. Thresholds and
  leaves are Float32; bias/scale and accumulation are Float64. The existing
  numeric tree codec checks the in-memory conversion, but no ranking package is
  produced. A CatBoost depth-zero constant tree uses a one-split tree with equal
  leaves, preserving its position in the sum and satisfying the host interpreter.
- `parity.jsonl`: complete **observed** action groups from train/valid, at most
  the requested row count, without truncating any selected state. Every record
  has `format`, `split`, `groupId`, `stateKey`, `actionKey`, Float32 `features`,
  and CatBoost's raw `expectedScore`. Unobserved legal actions are not invented.
- `train-log.json`: all input hashes, original export metadata, versions, seed,
  requested/resolved parameters, split counts, observation histogram, validation
  history, best iteration, model dimensions, output hashes, and parity results.

The conversion must stay within absolute score error `1e-4`. Per-state action
order must also match: sort exact scores descending, then form disjoint batches
whose score is within `1e-6` inclusive of each batch's maximum; use `actionKey`
ascending within a batch. Do not use a nontransitive pairwise approximate sort.
Parity does not establish search-policy usefulness, runtime budget, or package
compatibility. Those require independent Kotlin and end-to-end validation.

## Kotlin trajectory export and comparison

The prototype deliberately lives in
`app/src/test/java/org/autojs/autojs/core/accessibility/codegen/construction/`.
It uses the production verifier, plan representation, renderers and tree
interpreter, without adding experimental code or weights to the host APK.
The original `CandidateSearch` remains unchanged.

From the host root, using the configured JDK/Android SDK, export controlled
captures to a **new** output directory:

```powershell
$env:CODEGEN_CONSTRUCTION_MODE = 'export'
$env:CODEGEN_CONSTRUCTION_DATASET = (Resolve-Path 'tools/codegen-model/out/cg-ds-2026.10-1').Path
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-export')
$env:CODEGEN_CONSTRUCTION_PER_FAMILY = '28'
$env:CODEGEN_CONSTRUCTION_TEACHER_RUNS = '3'
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionExperimentTask'
```

Sampling is fixed by a seeded group-ID hash within each original screen family;
the original train/valid/test assignment is preserved. Each task gets one fixed
heuristic rollout search and two seeded exploratory searches. Every run has
bounded verification, frontier, expansion, action-score and time budgets.
The exporter fails on a teacher deadline rather than silently publishing a
wall-clock-dependent training set. Complete verified plans supply later-state
survival; verifier-rejected complete plans supply zero; unfinished branches do
not supply labels. `teacher-traces.jsonl` references the observed feature rows
by `(groupId,stateKey,actionKey)` to avoid repeating vectors for every completion.
The manifest is written last and binds data, source hashes and fixed budgets.

After training with the preceding command, evaluate in another new directory:

```powershell
$env:CODEGEN_CONSTRUCTION_MODE = 'evaluate'
$env:CODEGEN_CONSTRUCTION_EXPORT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-export')
$env:CODEGEN_CONSTRUCTION_POLICY_DIR = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-fit-1')
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-evaluation')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionExperimentTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_MODE
```

The three constructors are frozen `CandidateSearch`, the new grammar with its
fixed heuristic, and that same grammar with the trained policy. All use the same
frozen tiny-v1 final ranker, verifier, plan format and request. Constructors
have a 2,000-verification / 200-retained-candidate ceiling; the two new searches
also share 12,000 action scores, 2,000 expansions, a 64-prefix frontier and an
800 ms search allocation. A 1,000 ms total result deadline includes capture-tree
indexing, final ranking and rendering. Checks are cooperative; overdue results
are rejected and actual elapsed time is recorded, not claimed to be preempted.
The original baseline API cannot return partial results when its cancellation
signal fires. A deadline is not a reason to remove a task from the denominator.

The original sampled holdout and 24 separately authored synthetic bottleneck
tasks are reported as different cohorts. All three candidates' union defines the
reference best for cross-constructor regret/recall. Source hashes, input hashes,
policy provenance and exact ranker bytes are checked or recorded. Once held-out
results are inspected, do not tune the policy/budgets on them and call the same
tasks new independent evidence.

Kotlin parity and the explicit coverage diagnostic can be run separately:

```powershell
$env:CODEGEN_CONSTRUCTION_POLICY_DIR = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-fit-1')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionPolicyModelTest'
$env:CODEGEN_CONSTRUCTION_DIAGNOSTICS_DIR = $env:CODEGEN_CONSTRUCTION_POLICY_DIR
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionCoverageDiagnosticsTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_DIAGNOSTICS_DIR
```

The diagnostic replays baseline plans through legal grammar operations and
compares their prefixes to actual expansions. It is an explanation of a frozen
evaluation, not a policy update or evidence that a failed case was fixed.

## Tests

```powershell
$env:CODEGEN_TEST_TRAINING = '1'
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m unittest discover `
  -s tools/codegen-model/construction -t tools/codegen-model -p test_train.py
```

The opt-in real training test fits twice with unchanged input, then changes only
held-out rows and fits again. Canonical model JSON, policy JSON, and parity rows
must remain byte-identical. Protocol tests cover tampering, family leakage,
unobserved actions, private paths, output protection, Float32 bounds, complete
parity groups, near ties, and constant-tree conversion. Without the environment
flag, only the actual CatBoost fit test is skipped.
