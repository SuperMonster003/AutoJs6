# Construction-policy training prototype

This tool learns a numeric score for a **construction state and one legal next
action**, before a complete locate plan exists. It is independent of the existing
candidate ranker of the host feature schema. `policy.json` is an offline experiment artifact, not
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

A format 2 export (`export-manifest.json` with `format: 2`, written by the third
experiment) inserts `labelBest` between `label` and `observations`: the maximum
survival among the completed observed rollouts through that state/action. It is
an observed lower bound of the best continuation, never an exact optimum, and it
is never below the mean. Format 1 exports stay readable; only format 2 can fit
the best-continuation target.

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
`--early-stopping-rounds 32`, `--parity-max-rows 5000` and `--label mean`.
Iterations are bounded to 256 and depth to 6. Training uses CatBoostRegressor,
symmetric trees, RMSE, CPU, one thread, and a fixed learning rate of 0.05. Only
`train` fits the weights; only `valid` controls early stopping and best-iteration
selection. `test` and `real-test` are validated as protocol input but their labels
and vectors never enter either pool. Do not tune these options from held-out results.

`--label best` fits the best observed continuation of a format 2 export instead of
the mean; the resulting `policy.json` declares `scoreSemantics:
"best-observed-continuation-v1"` and `train-log.json` records `labelSelection`
and `exportFormat`. Both targets are comparable completion values of one state.
The Kotlin reader accepts either declaration and exposes it; the searcher never
sums scores across steps under either target.

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

## Second experiment: frontier retention and expansion

`ConstructionSearch` keeps `FAMILY_SCORE` as its default. The optional
`ANCHOR_PHASE_FAIR` controller retains breadth across `(anchor, phase)` buckets
and rotates both their retention and expansion; the model orders alternatives
within a bucket. It uses the same family quotas, total frontier cap and counter
budgets. No additional overflow queue stores pruned states. When anchors exceed
their quota, cyclic captured position chooses the survivors; anchor scores do
not override this choice. If a quota leaves only one state per anchor, advancing
phase can discard same-phase condition extensions. These are measured tradeoffs,
not claims that the controller always finds a usable plan.

The second experiment freezes the original 59-tree construction policy and the
tiny-v1 final ranker. It compares original search, legacy heuristic/model search,
and fair heuristic/model search. The five methods therefore separate controller
effects from model effects. This is an offline controller experiment, not a new
trained or installable model release.

From the repository root, set absolute input paths and create a new protocol:

```powershell
$env:CODEGEN_CONSTRUCTION_DATASET = [IO.Path]::GetFullPath('tools/codegen-model/out/cg-ds-2026.10-1')
$env:CODEGEN_CONSTRUCTION_EXPORT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-export-1')
$env:CODEGEN_CONSTRUCTION_POLICY_DIR = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-fit-1')
$env:CODEGEN_CONSTRUCTION_R2_PROTOCOL = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-protocol')
$env:CODEGEN_CONSTRUCTION_R2_MODE = 'freeze'
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionSecondExperimentTask'

$env:CODEGEN_CONSTRUCTION_R2_MODE = 'develop'
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-development')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionSecondExperimentTask'

& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/select_frontier.py `
  --protocol-dir $env:CODEGEN_CONSTRUCTION_R2_PROTOCOL `
  --development-dir $env:CODEGEN_CONSTRUCTION_OUTPUT
```

The freeze binds source identities, input hashes, model bytes, budgets and all
task memberships. Development uses only the original 168 training and 28
validation tasks. The selection tool recomputes quality from all five results
per task, refusing missing, duplicate, non-finite or held-out rows. It admits
`fair_model` only if validation coverage, best survival and top1 survival do not
decrease against `legacy_model`, at least one of these means improves on train
or valid, and every development run has no error or deadline stop. A rejected
decision remains recorded; do not edit it to open the holdout. This admission
criterion is separate from the roadmap's production release gate.

Only after the selection command succeeds, run:

```powershell
$env:CODEGEN_CONSTRUCTION_R2_DEVELOPMENT = $env:CODEGEN_CONSTRUCTION_OUTPUT
$env:CODEGEN_CONSTRUCTION_R2_MODE = 'holdout'
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-holdout')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionSecondExperimentTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_R2_MODE
```

The runner independently rechecks the decision and its hashes before evaluation.
It reports three separate cohorts: 28 captured tasks whose snapshot clusters
were unused in the first construction experiment, 24 independently authored new
synthetic tasks, and the 24 previously inspected synthetic regression tasks.
The captured tasks still belong to the same held-out list family and appeared
in the earlier P3 ranking evaluation. The synthetic tasks contain familiar UI
concepts and are not real application evidence. Only the newly authored cohort
is fresh to the complete pipeline research. No cohort may be discarded because
it yields no candidates, errors or late results.

Source changes after freezing require a new protocol and development run. Use
fresh output directories, preserve rejected experiments, and never retune on
the held-out results. The first experiment's exact reproduction requires its
recorded source revision; its source binding deliberately rejects later changes.

An explicit post-assessment diagnostic explains the nine fresh collection
coverage losses without changing any frozen experiment or training input:

```powershell
$env:CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-holdout')
$env:CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-diagnostics')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionSecondDiagnosticsTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS
Remove-Item Env:CODEGEN_CONSTRUCTION_R2_DIAGNOSTICS_OUTPUT
```

It checks the policy and source/result hashes, reproduces both policies' saved
candidate sets under unchanged counters, then legally replays the reference
plans. Its zero clock is for deterministic diagnosis, not a speed measurement.
Output excludes selector literals and raw plan/state/action keys. The inferred
first pruned prefix does not turn the forced continuation into a model-produced
trajectory and must not become a held-out training row.

## Third experiment: label semantics and collection coverage

The second experiment's diagnosis located every fresh coverage loss inside one
anchor/phase bucket: a valid `id` prefix lost to its siblings under the frozen
policy. Two hypotheses are tested together in the third experiment, both as
training-data changes with the grammar, features, controller and budgets left
unchanged:

1. The supervised target. The mean observed return of a prefix is pulled down by
   its many poor continuations, while search needs to retain prefixes that still
   have at least one strong continuation. `labelBest` is that observed maximum.
2. Training coverage. The first export sampled 28 tasks per screen family, so
   collection intents were one seventh of the data, and only the `FAMILY_SCORE`
   controller produced trajectories. The third export samples single and
   collection intents separately and alternates both controllers.

`ConstructionThirdExperimentTask` exports train/valid families only (the test
family is reserved for holdouts), four teacher runs per task (plain heuristic
under each controller, then seeded exploration under each), and writes format 2
rows with both labels. Then two policies are fitted from the same export:

```powershell
$env:CODEGEN_CONSTRUCTION_R3_MODE = 'export'
$env:CODEGEN_CONSTRUCTION_DATASET = [IO.Path]::GetFullPath('tools/codegen-model/out/cg-ds-2026.10-1')
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-export')
$env:CODEGEN_CONSTRUCTION_R3_SINGLE = '28'
$env:CODEGEN_CONSTRUCTION_R3_COLLECTION = '28'
$env:CODEGEN_CONSTRUCTION_TEACHER_RUNS = '4'
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionThirdExperimentTask'

& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/train.py `
  --dataset-dir $env:CODEGEN_CONSTRUCTION_OUTPUT --output-dir tools/codegen-model/out/construction-r3-fit-mean --label mean
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/train.py `
  --dataset-dir $env:CODEGEN_CONSTRUCTION_OUTPUT --output-dir tools/codegen-model/out/construction-r3-fit-best --label best
```

The frozen comparison has five methods under the `ANCHOR_PHASE_FAIR` controller
and the same tiny-v1 final ranker: the original search, the fixed heuristic, the
frozen second-round policy (`fair_model_r2`, the reference), and the two new
policies (`fair_model_mean`, `fair_model_best`). Every row also reports the same
candidates ordered by `RuleRanker` (`ruleTop1Survival`), a model-free second view
of whether constructed candidates reach the recommendation.

```powershell
$env:CODEGEN_CONSTRUCTION_R3_EXPORT = $env:CODEGEN_CONSTRUCTION_OUTPUT
$env:CODEGEN_CONSTRUCTION_R3_FIT_MEAN = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-fit-mean')
$env:CODEGEN_CONSTRUCTION_R3_FIT_BEST = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-fit-best')
$env:CODEGEN_CONSTRUCTION_POLICY_DIR = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-fit-1')
$env:CODEGEN_CONSTRUCTION_EXPORT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-export-1')
$env:CODEGEN_CONSTRUCTION_R2_PROTOCOL = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r2-protocol-2')
$env:CODEGEN_CONSTRUCTION_R3_PROTOCOL = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-protocol')
$env:CODEGEN_CONSTRUCTION_R3_MODE = 'freeze'
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionThirdExperimentTask'

$env:CODEGEN_CONSTRUCTION_R3_MODE = 'develop'
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-development')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionThirdExperimentTask'

& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/select_third.py `
  --protocol-dir $env:CODEGEN_CONSTRUCTION_R3_PROTOCOL --development-dir $env:CODEGEN_CONSTRUCTION_OUTPUT

$env:CODEGEN_CONSTRUCTION_R3_DEVELOPMENT = $env:CODEGEN_CONSTRUCTION_OUTPUT
$env:CODEGEN_CONSTRUCTION_R3_MODE = 'holdout'
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-holdout')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionThirdExperimentTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_R3_MODE
```

The freeze binds the export, both fits (their input hashes, declared labels and
artifacts), the reference policy bytes, the ranker bytes, source identities and all
task memberships. The selection tool chooses between the two new policies by
validation `top1Survival`, then `bestSurvival`, then `coverage`, ties preferring
`fair_model_best`; the preregistered order and tie rule are in the protocol and
the tool refuses any other criteria. The chosen policy is admitted only when no
validation measure regresses against `fair_model_r2`, at least one measure
strictly improves on train or valid, and every development run completes without
an error or deadline stop. Kotlin rechecks the choice and the admission before any
held-out task is loaded. A rejected decision remains recorded.

The holdout reports five cohorts: 28 captured tasks whose snapshot clusters were
unused by every earlier construction experiment, the 24 newly authored
`ConstructionThirdSyntheticFixtures` tasks, and three regression cohorts (the 24
first-experiment bottleneck tasks, the 24 second-experiment generalization tasks
and the 28 second-experiment captured tasks), all previously inspected and never
used for selection. No cohort may be dropped for zero candidates, errors or late
results.

## Fourth experiment: the merged pipeline and a refitted final ranker

The third experiment left two gaps: the frozen tiny v1 ranker was fitted on the
original search's candidate distribution and chose worse first candidates from
the constructors' larger sets, and one 4,003-node capture exhausted the 800 ms
search allocation. The fourth experiment addresses both without changing the
construction policy, the grammar or the features:

- `ConstructionGrammar.matches` now intersects per-condition match lists
  (one verifier scan per distinct single condition, restricted to the contiguous
  pre-order range of the subtree). `ConstructionGrammarMatchingTest` proves node
  for node equality with the verifier and unchanged feature vectors.
- `ConstructionMergedExportTask` exports the distribution the roadmap pipeline
  actually ranks: the rule search's verified candidates united with the frozen
  best-continuation policy's verified candidates, deduplicated by canonical plan,
  labelled by later-state survival and featurized within the union, for the
  train/valid families only. The artifacts follow the P3 dataset contract, so
  `train/train.py` fits the final ranker unchanged; `train/data.py` accepts the
  exporter's provenance. `construction/export_ranker.py` converts that fit into
  `ranker.json` (role `candidate-ranker`, host feature schema, 64 columns for schema 1 and 75 for schema 2) with parity rows
  that `ExperimentRankerModelTest` checks.

```powershell
$env:CODEGEN_CONSTRUCTION_DATASET = [IO.Path]::GetFullPath('tools/codegen-model/out/cg-ds-2026.10-1')
$env:CODEGEN_CONSTRUCTION_R3_FIT_BEST = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-fit-best-1')
$env:CODEGEN_CONSTRUCTION_MERGED_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-merged')
$env:CODEGEN_GENERATOR_COMMIT = (git rev-parse HEAD)
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionMergedExportTask'

& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/train/train.py `
  --dataset $env:CODEGEN_CONSTRUCTION_MERGED_OUTPUT --output tools/codegen-model/out/construction-merged-fit
& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/export_ranker.py `
  --fit-dir tools/codegen-model/out/construction-merged-fit --dataset-dir $env:CODEGEN_CONSTRUCTION_MERGED_OUTPUT `
  --output-dir tools/codegen-model/out/construction-merged-ranker
```

`ConstructionFourthExperimentTask` then freezes and runs eight methods per task
from one rule search, one construction (within the remaining 800 ms allocation)
and one shared-budget pair: `rule_pipeline`, `baseline_tiny`, `baseline_ranker`,
`construction_ranker`, `merged_rule`, `merged_tiny`, `merged_ranker` (the
admission candidate) and the informational `merged_ranker_shared`, which halves
both searches to 1,000 attempts / 100 retained candidates so the pair stays within
the D19 total of 2,000 verifications. Development is the third export's 336
training tasks plus every validation-family task (336); the reserved third
holdout (28 captured, 24 synthetic) is reused unchanged, and the three inspected
cohorts are regression only.

```powershell
$env:CODEGEN_CONSTRUCTION_MERGED = $env:CODEGEN_CONSTRUCTION_MERGED_OUTPUT
$env:CODEGEN_CONSTRUCTION_RANKER = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-merged-ranker')
$env:CODEGEN_CONSTRUCTION_R3_PROTOCOL = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-protocol')
$env:CODEGEN_CONSTRUCTION_R4_PROTOCOL = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r4-protocol')
$env:CODEGEN_CONSTRUCTION_R4_MODE = 'freeze'
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionFourthExperimentTask'

$env:CODEGEN_CONSTRUCTION_R4_MODE = 'develop'
$env:CODEGEN_CONSTRUCTION_R3_DEVELOPMENT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r3-development')
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r4-development')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionFourthExperimentTask'

& tools/codegen-model/out/venv-py312/Scripts/python.exe tools/codegen-model/construction/select_fourth.py `
  --protocol-dir $env:CODEGEN_CONSTRUCTION_R4_PROTOCOL --development-dir $env:CODEGEN_CONSTRUCTION_OUTPUT

$env:CODEGEN_CONSTRUCTION_R4_DEVELOPMENT = $env:CODEGEN_CONSTRUCTION_OUTPUT
$env:CODEGEN_CONSTRUCTION_R4_MODE = 'holdout'
$env:CODEGEN_CONSTRUCTION_OUTPUT = [IO.Path]::GetFullPath('tools/codegen-model/out/construction-r4-holdout')
.\gradlew.bat --offline :app:testAppDebugUnitTest --rerun --tests 'org.autojs.autojs.core.accessibility.codegen.construction.ConstructionFourthExperimentTask'
Remove-Item Env:CODEGEN_CONSTRUCTION_R4_MODE
```

The preregistered admission (`select_fourth.py`, rechecked by Kotlin before any
held-out task is loaded) requires that `merged_ranker` does not fall below the
complete rule pipeline, the original search with the tiny ranker, or the original
search with the refitted ranker on validation top1 survival, that it strictly
improves top1 or top3 over `baseline_ranker` on train or valid (so construction
itself contributes), and that every development run of every method completes
without an error or deadline stop. With `CODEGEN_CONSTRUCTION_R3_DEVELOPMENT`
set, the development run also reports how many construction candidate sets and
counters reproduce the third experiment's records after the matcher change.

## Tests

```powershell
$env:CODEGEN_TEST_TRAINING = '1'
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m unittest discover `
  -s tools/codegen-model/construction -t tools/codegen-model -p test_train.py
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m unittest discover `
  -s tools/codegen-model/construction -t tools/codegen-model -p test_select_frontier.py
& tools/codegen-model/out/venv-py312/Scripts/python.exe -m unittest discover `
  -s tools/codegen-model/construction -t tools/codegen-model -p test_select_third.py
```

The opt-in real training tests fit twice with unchanged input, then change only
held-out rows and fit again, and separately fit a format 2 fixture with both
labels. Canonical model JSON, policy JSON, and parity rows must remain
byte-identical across repeats; the two label targets must differ and declare
their own score semantics. Protocol tests cover tampering, family leakage,
unobserved actions, private paths, output protection, Float32 bounds, complete
parity groups, near ties, and constant-tree conversion. Without the environment
flag, only the actual CatBoost fit test is skipped.
