# First model-assisted construction prototype

Date: 2026-10-07. Scope: the construction supplement to existing roadmap P1.2,
P2.4-P2.6, P3 and P4. This is an offline prototype using the real host plan,
verifier, renderer and numerical model interpreter. **Construction participation
and reproducibility are verified; the quality gate is not passed. P7 product
integration must wait for a better construction/search policy and fresh evidence.**

Local implementation commits: construction core `d534123563`, training/interpreter
`f278fd4732`, frozen experiments/diagnostics `0621659729`. This report accompanies
the evidence/progress commit. No host remote push was made.

All implementation is in JVM test sources and `tools/codegen-model/construction`.
The original production `CandidateSearch` and the installed host behavior are
unchanged. The weights are a dedicated construction-policy JSON artifact, not an
installable `.ajcgm` model or a new signed release. No raw layouts, trajectories
or training weights are added to Git; this report contains aggregate results.

## What was implemented

The constructor exposes legal incremental operations for the five existing plan
families: select a family/anchor, add actual captured attributes, choose an index,
append parent/child navigation, build an inner selector and finish. Only finish
creates the complete `LocatePlan`. Every completed plan uses the existing
`PlanVerifier`; accepted plans undergo the same greedy condition minimization.
The user's target set, attribute permissions and action-receiver contract remain
constraints. The policy never produces JavaScript, raw ids or new API names.

An independent schema has 87 state/action features, version 1, grammar version 1,
names hash `f2369debb29eaa54`. It uses capture-time structural and property-quality
facts, not later snapshots, truth markers, metadata group IDs or raw attribute
strings. Kotlin is the sole extractor. The policy scores an unfinished state's
next legal operation; its scores are not accumulated across different states.
Audits retain state/action decisions and actual valid/invalid terminal plans.

Search alternates plan families and keeps bounded per-family frontiers ordered
by predicted completion value. Verification includes minimization retries; node
references, captured nodes, frontier, pending successors, depth, expansions,
action scores and elapsed time are bounded. Cancellation propagates; a policy
failure can retain already verified results. Tests also caught and fixed an
insertion-order dependence when trimming approximately tied scores: successors
from one expansion are now ranked/truncated together with the existing frontier.

The two new constructors use the same grammar, budgets and parameters; their only
difference is the fixed heuristic versus trained policy. The grammar can form
attribute subsets beyond the original single/pair/triple/full enumeration and
negative child paths; those search-space changes are not credited to learning.
Canonical comparison normalizes condition ordering while preserving meaningful
family/path/index distinctions.

## Data and reproducibility

The pilot samples 28 tasks per original controlled screen family by a fixed
group-ID hash, preserving the existing family-level split: **224 groups = 168
train + 28 valid + 28 test**. These are captured states of the controlled fixture
application, not real-application evidence. The sampled test contains the `list`
family only; this limits any generalization conclusion.

Each task has one heuristic and two seeded exploratory teacher searches. The
teacher keeps finite 2,000-verification / 200-retained, 4,000-expansion,
24,000-action-score, 128-frontier and 30-second limits. It labels only observed
completed rollouts, using later-state survival after current-state verification
and minimization. Invalid complete plans receive zero; unfinished/pruned
branches do not. The label is the mean observed return, not an oracle best
continuation value or a calibrated success probability.

| Export fact | Value |
| --- | ---: |
| Observed state/action rows | 1,208,829 |
| Complete rollouts | 485,841 |
| Complete rollouts valid at capture | 320,708 |
| Scored state/action edges without a completed observation | 1,807,589 |
| Training action rows | 915,160 |
| Validation action rows | 139,228 |

Two complete exports are byte-identical, including the schema, CSV, compact
trajectory references and manifest. The manifest binds input/source hashes,
fixed sampling, label semantics and budgets. These rows are correlated decisions
from 224 tasks, not 1.2 million independent layouts.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `features.json` | 1,872 | `28c74afb8aa9a95b5211c6c829c2d4e784191aa30435da6a64b463baa9bff954` |
| `actions.csv` | 559,975,688 | `46930d68f7028adc5419ee9d5d5609cbdefc120183a7713706a1b875a899f25b` |
| `teacher-traces.jsonl` | 731,476,401 | `bb841a6c410684b758e436db49fed6a7c772ecd0d6c962104c9ef33e1b1ac26e` |
| `export-manifest.json` | 656,094 | `ea3adc9efb61879051dd01eebcbf11facb5ff6e0b2e4ed404095bf6002455360` |

## Training and numerical checks

Python 3.12.5, CatBoost 1.2.10, NumPy 2.2.6; CPU, one thread, fixed seed 20261007,
depth 6, at most 256 iterations, RMSE and validation early stopping. State/action
rows are equally weighted. Observation counts are metadata, not independent
sample weights. Only train fits and valid selects the stopping point; held-out
rows never enter either pool or model selection.

The selected policy has **59 trees** (best iteration 58). Two fits took
21.703551 / 17.529494 seconds excluding dataset loading. Canonical model JSON,
policy JSON, schema and parity data are byte-identical. Raw CatBoost metadata
contains volatile identifiers/timestamps and is retained separately, not claimed
byte-identical. Held-out-data isolation is also tested by replacing test labels,
vectors and observation counts while requiring the same training outputs.

| Model fact | Value |
| --- | --- |
| `policy.json` | 84,454 B, about 82.47 KiB |
| Policy SHA-256 | `ecb04d2f7086b6c1065ff949b821be3d5e37a06ca2e5c5deca63b478acaffa62` |
| Canonical `model.json` SHA-256 | `f58e35d77347bc363ae170568d937288affefe18114daf5b70333cd7127b4554` |
| Kotlin numerical parity | 5,000 observed vectors in 2,837 complete observed-action groups |
| Maximum score difference | `3.709102247562157e-9`, below `1e-4` |
| Per-state action order | All identical, including the anchored `1e-6` tie rule |
| Tree backing-array payload | 18,467 B |
| One desktop JVM policy load observation | 39.7698 ms |

Array payload excludes object headers, JSON parsing, feature context, verifier
caches and construction traces. It is not a 32 MiB resident-memory acceptance.
The JSON reader is explicitly test-only and checks role/schema/semantics, strict
field types, bounded size/count/depth, finite values and array consistency before
using the existing `ObliviousTreeModel` interpreter.

## Frozen comparison

Three constructors share the same requests, actual verifier, `LocatePlan` format,
normalization, and the exact existing tiny-v1 final ranker weights
(`9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`).
The final ranker is not refitted per method. All have a 2,000-verification /
200-retained ceiling. The new searches additionally have 12,000 action scores,
2,000 expansions, a 64-prefix frontier, four navigation steps and an 800 ms
construction allocation. The wrapper applies a 1,000 ms total result deadline
including tree indexing, final scoring and rendering; late results count as
failures. Cancellation/checks are cooperative and elapsed CPU time is reported.
The original baseline cannot return its partial work after cancellation.

The separate bottleneck cohort was authored before inspecting any policy or
evaluation: four scenario families, three seeds, single/collection variants,
24 tasks total. Two captures contain 4,003 nodes (4,027 in the last later state).
These tasks are synthetic, not captured real applications. Warm-up uses training
captures only; method order rotates, and no parameters change after evaluation.
Every scheduled task remains in the denominator, including zero-candidate cases.
Cross-method regret/recall uses the candidate union's reference best.

| Holdout | Constructor | Current valid coverage | Mean candidate best survival | Top-1 survival | Best survival in top 3 | Desktop JVM P95 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Captured fixture, 28 | Original search | 100% | 92.86% | 91.07% | 91.07% | 16.05 ms |
| Captured fixture, 28 | New grammar + heuristic | 100% | 57.14% | 57.14% | 57.14% | 207.14 ms |
| Captured fixture, 28 | New grammar + learned policy | 100% | 91.07% | 91.07% | 91.07% | 158.32 ms |
| Synthetic bottlenecks, 24 | Original search | 62.50% | 50.00% | 25.00% | 37.50% | 603.21 ms |
| Synthetic bottlenecks, 24 | New grammar + heuristic | 50.00% | 25.00% | 12.50% | 25.00% | 470.23 ms |
| Synthetic bottlenecks, 24 | New grammar + learned policy | 50.00% | 37.50% | 12.50% | 37.50% | 349.88 ms |

Survival is the observed fraction of later states still satisfying the task,
not a probability of success in an arbitrary application. Timing is a single
desktop comparison with audits enabled, not the roadmap's phone benchmark.
All 744 method runs completed without an exception or total-deadline failure;
maximum observed duration was 697.20 ms. There are zero real-application groups.

The table isolates construction by giving all three methods the same model final
ranker. The complete original rule-only pipeline (CandidateSearch + RuleRanker)
has top-1 survival 35.71% on captured test and 25.00% on synthetic test. The rise
from 35.71% to 91.07% cannot be credited to the new constructor: the original
candidate search with that same model final ranker already reaches 91.07%.

On captured test, the learned policy improves top-1 over the same-grammar
heuristic in 19/28 tasks and ties the rest, but ties the original search's top-1
on every task. Its candidate-best quality falls on one task and never improves.
No candidate outside the original baseline set is found in this cohort.

On synthetic test, three dynamic-attribute single-target tasks each gain one
new valid candidate with survival 1.0. Baseline already has other survival-1.0
candidates in those tasks, so novelty is established but a higher quality ceiling
is not. The unchanged final ranker does not select these good new candidates as
top-1. Three collection tasks lose all valid coverage, while other top-1 values
tie the original search. Top-3 quality improves on three tasks and worsens on
three, which the equal mean would otherwise hide. Across 52 held-out tasks,
the learned constructor raises the original search's quality ceiling on **zero**.

## Coverage diagnosis, without retuning

The three lost collection tasks belong to dynamic attributes. Their original
baseline has three valid CONTAINER_ITEMS plans per task. A separate explicit
diagnostic replays each through the unchanged grammar, checking each legal
operation, canonical identity and the same verifier, then aligns the required
prefix with actual heuristic/model expansions.

All **18 plan/policy replays** are legal and verified. All failures are classified
as beam pruning, not an unavailable operator, anchor truncation, depth limit or
verifier failure. The heuristic loses the needed anchor before expanding its
outer selector; the model does score that selector's id but the successor is
discarded before the end-outer step. Different anchors and construction stages
compete inside the CONTAINER_ITEMS family's 32 frontier slots. `EXHAUSTED` means
the retained frontier has emptied, not that all legal plans were searched.

The lost-prefix scores are matched to actual logged expansions. Later steps in
the forced replay are explicitly counterfactual, not decisions the original
run made. The diagnostic neither changes the frozen evaluation nor fixes those
cases. It establishes a concrete next investigation: prefix retention/exploration
and the comparability/coverage of learned completion values across construction
stages. The terminal ranker's handling of new candidates also needs rechecking.

## Validation and next boundary

- Final host JVM regression: **380 cases, 372 passed, 0 failures/errors, 8 skipped**.
  Skips: the four existing opt-in export/external parity/benchmark jobs, two
  Windows symlink-permission cases, the opt-in public-network test, and this
  offline export/evaluation task. Construction-policy parity and the coverage
  diagnostic were explicitly enabled and passed.
- Construction core: 17 tests, including versioned schema goldens, manually
  checked vectors, actual policy-dependent construction, all five plan families,
  request preservation, count/deadline/cancellation behavior and tie handling.
- Construction reader: six ordinary cases plus full trained-policy parity;
  bottleneck fixtures: three cases; coverage diagnostic: one explicit case.
- Python construction tests: 14 passed, including actual repeated fits and
  held-out isolation. Compact Float32 rows reduce offline memory without changing
  values or training semantics.
- Full teacher exports reproduced byte-for-byte twice, and the full policy was
  trained reproducibly twice. See the adjacent aggregate metrics JSON.

This iteration is a frozen research baseline, not approval to package or expose
construction in the management/generation UI. No signed test or production
assets were changed. Further construction/label/terminal-scoring changes should
be developed with training/validation material and assessed on new unused
holdouts, rather than tuning on these inspected test tasks. Real-application
data and phone memory/performance acceptance are still required later.

Reproduction commands and artifact protocol are in
[`../construction/README.md`](../construction/README.md). Local ignored artifacts:
`out/construction-export-1`, `out/construction-export-repeat-1`,
`out/construction-fit-1`, `out/construction-fit-repeat-1`,
`out/construction-evaluation-1`. The earlier `construction-smoke-1` used a
pre-fix search revision and is only an I/O smoke record, not the training data
or effect evidence above. Build, parity, regression and diagnostic logs remain
under the host's ignored `build/codegen-construction-*` paths.
