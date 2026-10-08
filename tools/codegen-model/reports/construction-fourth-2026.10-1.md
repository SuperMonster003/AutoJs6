# Fourth construction experiment: the merged pipeline and a refitted final ranker

Date: 2026-10-08. Scope: continued construction work within roadmap P1.2, P3.3,
P3.4, P4 and D19. **The preregistered development admission was rejected, so
the reserved holdout stays unopened. On every captured development task the
merged pipeline (rule search candidates united with the frozen best-continuation
construction candidates, ranked by a ranker refitted on that union) ties the
original search with the same ranker: construction adds candidates to 660 of
672 tasks but never raises a task's quality ceiling or changes a first choice.
Do not promote any policy or ranker, and do not proceed to P7 integration.**

The construction policy, the grammar, the 87 state/action features, the
`ANCHOR_PHASE_FAIR` controller and the budgets are the third experiment's. Two
things changed: feature extraction now intersects indexed single-condition
matches instead of rescanning the capture for every scored prefix (D19 on large
captures), and the final ranker was refitted on the merged candidate
distribution with the production trainer. No APK, signed model or release is
involved; the host receives local commits only.

Implementation commits: `8047323a74` (indexed matching and its equivalence
test), `e2b8bfc9c4` (merged candidate export, experiment ranker reader and
export, provenance acceptance), `839cbf02a5` (fourth experiment task and
admission tool), and the regression diagnostic with this report. Aggregate
evidence is in
[construction-fourth-2026.10-1.metrics.json](construction-fourth-2026.10-1.metrics.json).
The [first](construction-first-2026.10-1.md), [second](construction-second-2026.10-1.md)
and [third](construction-third-2026.10-1.md) reports remain the historical baselines.

## Indexed matching keeps the search identical

`ConstructionGrammar.matches` now scans the verifier once per distinct single
condition and intersects the resulting pre-order position lists, restricted to
the contiguous pre-order range of the subtree. `ConstructionGrammarMatchingTest`
compares more than 5,000 selectors across the captured fixtures and all three
synthetic cohorts with the verifier node for node, and replays every scored
action of a frozen search through a fresh grammar with identical feature vectors.
The development run of this experiment reproduces the third experiment's
`fair_model_best` candidate sets and counters on **392 of 392** shared tasks.

On the 4,003-node `nearby-anchor` seed 3 collection, construction feature time
fell from 585 ms to 210 ms and the whole construction from an 800 ms deadline to
374 ms; the task remains unsolved by every method, including the rule search,
which spends 427 ms and 2,000 verifications there without a candidate. Over the
672 development tasks construction took 112 ms (P50), 198 ms (P95) and 605 ms
at most, with feature extraction at 17 / 32 / 103 ms.

## Merged candidate export and the refitted ranker

`ConstructionMergedExportTask` ran the rule search and the frozen policy
(`48f1014a0f850027a18ac46f567262dfe6be63f75b4035947cd6603c285bc1dd`) on every
train/valid task of `cg-ds-2026.10-1`, united the verified candidates by canonical
plan, labelled them by later-state survival and featurized them within the
union under the host's 64-feature schema, following the P3 dataset contract.

| Merged export fact | Value |
| --- | ---: |
| Tasks | 2,352 = 2,016 train + 336 valid; the test family stays reserved |
| Candidates | 130,768: 97,360 rule only, 23,384 in both, 10,024 construction only |
| Rule verifications / construction verifications | 4,704,000 / 3,219,232 |
| Construction stops | 1,880 `EXHAUSTED`, 472 `VERIFICATION_BUDGET`, no deadline |
| `features.csv` | 59,896,574 B, `2ab774125be49773cbcac111425ab1dcaaf83179c930611a80ed0257dfafea85` |

`train/train.py` (YetiRank, NDCG@1 early stopping, depth 6, seed 20261003,
one thread) kept **10 trees** (best iteration 9) in 18.8 s; a repeat fit is
byte-identical. `construction/export_ranker.py` converts the fit into
`ranker.json` (role `candidate-ranker`, SHA-256
`172bf423b469b98852613437993bd4f634941d7c6785198dcc45d617b5101e7f`); the Kotlin
`ExperimentRankerModelTest` reproduces CatBoost on 4,985 candidates in 82
complete groups with a maximum difference of 7.12e-9 and identical group first
choices. The ranker was early-stopped on the whole validation family, so its
development figures favor it; the holdout would have decided.

## Frozen protocol, methods and the rejected admission

Each task runs one rule search (2,000 attempts / 200 retained), one construction
within the remaining 800 ms allocation, and one informational shared-budget pair
(1,000 / 100 each, so the pair stays within the D19 total of 2,000). Eight
rankings are then formed from those candidate sets with the same verifier,
canonical deduplication, renderer and a 1,000 ms whole-task cancellation:

| Method | Candidates | Final ranking |
| --- | --- | --- |
| `rule_pipeline` | rule search | `RuleRanker` (complete rule baseline, D20) |
| `baseline_tiny` | rule search | frozen tiny v1 |
| `baseline_ranker` | rule search | refitted ranker |
| `construction_ranker` | construction only | refitted ranker |
| `merged_rule` / `merged_tiny` / `merged_ranker` | rule ∪ construction | rule / tiny / refitted |
| `merged_ranker_shared` | halved rule ∪ halved construction | refitted (informational) |

The protocol (SHA-256
`53c035b90c0984e567f2cbf2017051f4daf04ac76c1cff45ce921f56e118ca4c`) binds the
merged export, the fit, the exported ranker, both policies' bytes, source
identities and all task memberships, and reuses the third protocol's reserved
holdout unchanged. Development is the third export's 336 training tasks plus all
336 validation-family tasks: 672 tasks, 5,376 rankings, no error or deadline
stop, longest whole task 635 ms. The preregistered admission required
`merged_ranker` not to fall below the three baselines on validation top1 and to
strictly improve top1 or top3 over `baseline_ranker` on train or valid.

| Development split | Method | Candidate-best | Top1 | Top3-best | Candidates | P95 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Train, 336 | `rule_pipeline` | 98.21% | 84.23% | 90.03% | 40.6 | 13 |
| Train, 336 | `baseline_tiny` | 98.21% | 93.60% | 94.79% | 40.6 | 14 |
| Train, 336 | `baseline_ranker` | 98.21% | 93.60% | 94.79% | 40.6 | 13 |
| Train, 336 | `construction_ranker` | 97.02% | 93.75% | 94.05% | 13.3 | 181 |
| Train, 336 | `merged_ranker` | 98.21% | 93.60% | 94.79% | 43.7 | 182 |
| Train, 336 | `merged_ranker_shared` | 98.21% | 93.60% | 94.79% | 40.1 | 145 |
| Valid, 336 | `rule_pipeline` | 95.98% | 72.02% | 76.79% | 49.9 | 14 |
| Valid, 336 | `baseline_tiny` | 95.98% | 87.05% | 88.54% | 49.9 | 15 |
| Valid, 336 | `baseline_ranker` | 95.98% | 87.05% | 88.54% | 49.9 | 15 |
| Valid, 336 | `construction_ranker` | 94.49% | 87.05% | 88.54% | 16.9 | 255 |
| Valid, 336 | `merged_ranker` | 95.98% | 87.05% | 88.54% | 54.7 | 256 |
| Valid, 336 | `merged_ranker_shared` | 95.98% | 87.05% | 88.54% | 46.8 | 199 |

Coverage is 100% for every method on both splits. `merged_ranker` clears the
no-regression rule (it beats the rule pipeline on 142 validation tasks and
loses 41, and ties both model baselines everywhere) but fails the construction
gain rule: against `baseline_ranker` it improves and worsens top1 and top3 on
**zero** tasks in either split. The selection record (SHA-256
`175f33af8e25fb5906b61e21889604cc38ceac73165af13d45c7375ec995d7f1`) keeps
`passed: false`; no reserved task was evaluated.

Why nothing changes: on these captured families the rule search already reaches
its counter budget with 40-50 verified candidates per task, and its candidate
best is never exceeded by a construction candidate (0 of 672 tasks; construction
alone is below it on 18). The union therefore adds 2,682 candidates that the
refitted ranker, like the tiny ranker, never puts first. The refitted ranker also
reproduces the tiny ranker's first choices on every baseline set, so the merged
distribution did not teach it a different preference.

## Regression diagnostic on inspected cohorts

Because the admission failed, the reserved cohorts remain unopened. A separate
`ConstructionFourthRegressionDiagnosticsTask` evaluated the eight frozen methods
on the three inspected cohorts only. These are regression evidence, never
selection evidence; all 76 tasks stay in every denominator.

| Seen generalization (second fresh cohort), 24 | Candidate-best | Top1 | Top3-best |
| --- | ---: | ---: | ---: |
| `rule_pipeline` | 100% | **87.50%** | 100% |
| `baseline_tiny` | 100% | 68.75% | 87.50% |
| `baseline_ranker` | 100% | 56.25% | 83.33% |
| `construction_ranker` | 87.50% | 37.50% | 79.17% |
| `merged_rule` | 100% | 87.50% | 100% |
| `merged_tiny` | 100% | 68.75% | 87.50% |
| `merged_ranker` | 100% | 56.25% | 83.33% |

The learned rankers generalize worse than the deterministic rules on these
authored scenarios, and the refitted ranker is worse than the tiny one. The
union keeps the rule search's ceiling of 100% but inherits whichever ranker's
first choice. On the 28 inspected captured tasks every model ranker reaches
87.50% top1 against 37.50% for the rule pipeline, the mirror image. On the 24
first-experiment bottleneck tasks all methods tie at 25% top1 and 62.5%
coverage; the 4,003-node task is reported as a hard deadline for every method
because the whole-task cancellation also covers the informational shared pair,
but both of its main searches had already finished with zero candidates.

## Reproducibility, limits and next work

- The merged export, the ranker fit (byte-identical repeat) and the exported
  ranker are reproducible; the development run reproduces the third
  experiment's construction output exactly. Timing is a single desktop
  observation per task, not a Redmi measurement or a 5,000-node gate. There are
  still zero real-application groups.
- The informational shared-budget variant ties the separate-budget variant on
  every development measure at 1,972 / 1,930 verifications per task (train /
  valid), inside the D19 total, with a lower P95 (145 / 199 ms).
- Validation: the final host JVM regression over the codegen, selector filter,
  inspector and codegen UI packages ran **489 cases, 472 passed, 0
  failures/errors, 17 skipped** (fifteen opt-in offline export, experiment,
  diagnostic, parity and benchmark jobs and two Windows symlink-permission
  cases); the merged export, ranker parity, freeze, development run and the
  regression diagnostic were executed explicitly. The Python codegen-model
  tests pass with 158 cases (4 opt-in skips).

Four rounds now point the same way. On the controlled fixture families the rule
search's candidate ceiling is already 96-98%, a learned construction policy
reproduces it but does not exceed it, and the first recommendation is decided by
the final ranker. Learned rankers beat the rules in distribution and lose on
authored scenarios; the rules do the opposite. Construction contributed on
synthetic bottlenecks the rule search cannot cover (three tasks in the first
experiment) and on candidate-set cleanliness, never on captured data. The
remaining options are maintainer decisions recorded in the roadmap entry: collect
real-application task groups (P2.5, Q8) before any further construction training,
or re-scope the product path to rule search plus a learned ranker with construction
kept as a research track. The reserved holdout (28 captured, 24 synthetic) stays
unopened for whichever protocol follows.

Private experiment files remain under ignored `tools/codegen-model/out/`:
`construction-merged-1`, `construction-merged-fit-1` and its `-repeat-1`,
`construction-merged-ranker-1`, `construction-r4-protocol-1`,
`construction-r4-development-1` and `construction-r4-regression-1`. Raw
snapshots, per-task selectors, trajectories and model weights are not committed.
