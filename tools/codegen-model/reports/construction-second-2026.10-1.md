# Second construction experiment: fair frontier scheduling

Date: 2026-10-07. Scope: continued construction work within roadmap P1.2,
P2.4-P2.6, P3 and P4. **The controller passes its preregistered development
admission check, but the learned policy fails fresh synthetic assessment.
Do not promote this controller to the default or proceed to P7 integration.**

The experiment implements a bounded alternative for retaining and expanding
unfinished plans, freezes new assessment tasks, and separates controller effects
from learned-policy effects. It does not train new weights, change the production
engine, install an APK, sign a model, or publish a release. All implementation
remains in test sources and offline tools. The host receives local commits only.

Implementation commits: `ca1a62ec97` (controller and tests), `47dfed7c29`
(independently authored synthetic tasks), `723dc5c99b` (frozen evaluation and
development admission). Aggregate evidence is in
[construction-second-2026.10-1.metrics.json](construction-second-2026.10-1.metrics.json).
The [first report](construction-first-2026.10-1.md) remains the historical baseline.

## Controller and unchanged constraints

The default `FAMILY_SCORE` controller is preserved. The opt-in
`ANCHOR_PHASE_FAIR` controller keeps the same per-family quotas and family round
robin. Within each family it groups prefixes by anchor and construction phase,
gives each bucket one slot before a second, and rotates anchors and their phases
when expanding. Only candidates within a bucket compete by policy score.

The model still selects legal next operations before complete plans exist. No
grammar, feature, heuristic weight, construction-model weight or final-ranker
weight changes in this experiment. The existing model remains 59 trees,
84,454 bytes of JSON, with 18,467 bytes of numerical backing arrays. The latter
excludes interpreter objects, captured nodes, frontier, traces and other memory;
it is not a measurement of resident application memory.

Every constructor receives the same request and uses the existing plan verifier,
condition minimizer and renderer. Candidate verification is limited to 2,000
attempts including minimization; at most 200 verified candidates are retained.
The four incremental variants share 64 active prefixes, 2,000 expanded states,
12,000 scored actions, 128 anchor positions and the existing depth/capture caps.
There is no hidden overflow queue or increased model budget.

The original search retains its counter-based API plus the same 1,000 ms total
cancellation used in experiment one. Incremental searches receive an 800 ms
search allocation; indexing, final ranking and rendering share the 1,000 ms
whole-result deadline. Overdue results count as zero quality. Cancellation is
cooperative; measured elapsed time is recorded rather than described as a
preemptive execution limit. Future-state scoring is outside these timings.

Two controller limitations were documented before assessment. When anchors
outnumber their quota, cyclic captured position selects surviving anchors;
their policy scores do not override this selection. If every anchor gets only
one slot, advancing phase can evict condition extensions at that anchor. The
finite quota cannot preserve every anchor and every continuation.

## Frozen protocol and selection

The five compared methods are:

| Report name | Constructor | Next-action scoring | Final ranking |
| --- | --- | --- | --- |
| Original | Existing `CandidateSearch` | Existing enumeration | Frozen tiny v1 |
| Legacy heuristic | `FAMILY_SCORE` | Fixed heuristic | Frozen tiny v1 |
| Legacy model | `FAMILY_SCORE` | Frozen construction model | Frozen tiny v1 |
| Fair heuristic | `ANCHOR_PHASE_FAIR` | Fixed heuristic | Frozen tiny v1 |
| Fair model | `ANCHOR_PHASE_FAIR` | Frozen construction model | Frozen tiny v1 |

The complete rule pipeline is measured separately by applying `RuleRanker` to
the original candidates. It must not be confused with the table's Original
method, which uses the frozen final model to isolate constructor effects.

Before any development result was evaluated, the final protocol bound source
hashes, input files, task memberships, budgets, policy bytes and final-ranker
bytes. A preceding protocol directory was only a compilation/provenance preflight;
no quality results were evaluated with it. Final protocol SHA-256:

`6ab228dc2a9e6e548708bd275e265cd854e8a052bbb881979fe1d3e160adda06`.

The unchanged construction-policy SHA-256 is
`ecb04d2f7086b6c1065ff949b821be3d5e37a06ca2e5c5deca63b478acaffa62`;
the final-ranker package SHA-256 is
`9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`.

Development uses the same 168 training and 28 validation tasks as the first
construction export. A Python admission tool recomputes metrics from all five
results per task and writes an immutable selection record. It admits Fair model
only when validation coverage, candidate-best survival and top1 survival do not
decrease versus Legacy model, at least one of these means improves on train or
valid, and all development runs finish without an error/deadline stop. Kotlin
independently rechecks membership, hashes and the criteria before assessment.
This admission check is not the roadmap's model publication gate.

| Development split | Method | Coverage | Candidate-best survival | Top1 survival | Top3-best survival |
| --- | --- | ---: | ---: | ---: | ---: |
| Train, 168 | Legacy model | 100% | 93.15% | 91.37% | 92.86% |
| Train, 168 | Fair model | 100% | 96.13% | 91.37% | 92.86% |
| Valid, 28 | Legacy model | 100% | 85.71% | 82.14% | 85.71% |
| Valid, 28 | Fair model | 100% | 92.86% | 82.14% | 85.71% |

The preregistered admission passes. More useful candidates are found, but the
unchanged final ranker does not turn these gains into better top1/top3 results.
No search setting or weight was changed after observing these results.

## Assessment cohorts and results

All tasks have a capture and two later states. Coverage means that at least one
candidate satisfies the request on the capture. Survival is the proportion of
later states on which a plan still satisfies the request. Top3-best is the best
survival among the first three recommendations. All cohort means include empty
or failed runs as zero. These measurements do not establish action execution
success on a running application.

The 28 captured tasks exclude all snapshot paths shared with the first export's
224 tasks, including other requests of the same capture seed. They still belong
to the existing list family and appeared in the earlier P3 ranker evaluation.
They are new to the construction experiment, not independent new applications.

| Captured construction-unseen, 28 groups | Coverage | Candidate-best | Top1 | Top3-best | P95, ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original | 100% | 92.86% | 87.50% | 87.50% | 23.84 |
| Legacy heuristic | 100% | 57.14% | 57.14% | 57.14% | 276.43 |
| Legacy model | 100% | 87.50% | 87.50% | 87.50% | 161.82 |
| Fair heuristic | 100% | 73.21% | 42.86% | 73.21% | 221.65 |
| Fair model | 100% | 87.50% | 87.50% | 87.50% | 155.63 |

Fair model ties Legacy model in every task's reported quality metrics. It adds
five normalized candidates across this cohort but raises no task's quality
ceiling above Original. Fair model improves top1 on 20 tasks versus Fair
heuristic and ties eight. Original has better candidate-best quality on three
tasks. The separate complete rule pipeline has top1 survival 37.50%.

The primary fresh cohort contains 24 independently authored synthetic tasks:
four scenarios, three fixed seeds, single/collection requests, and two later
states. It covers permission check-state changes, localized toolbars, editable
forms with validation wrappers, and long-click receivers in media grids. Its
source was frozen before search evaluation, with normalized SHA-256
`73818feb25956eea483c7eb3ba957e46137e67c74fe7e5c9b132a83bcb66b927`.
Direct verifier tests prove that existing grammar plans can satisfy every task
across all three states; those witness plans are not supplied to search. Familiar
UI concepts overlap with development data. This is synthetic assessment, not a
sample of real applications or proof of entirely unseen semantic generalization.

| Fresh synthetic, 24 groups | Coverage | Candidate-best | Top1 | Top3-best | P95, ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original | 100% | 100% | 68.75% | 87.50% | 15.85 |
| Legacy heuristic | 50% | 6.25% | 6.25% | 6.25% | 172.67 |
| Legacy model | 50% | 6.25% | 6.25% | 6.25% | 170.00 |
| Fair heuristic | 100% | 81.25% | 81.25% | 81.25% | 205.09 |
| Fair model | 62.50% | 18.75% | 18.75% | 18.75% | 139.59 |

The controller helps both policies relative to their legacy variants. Fair
model recovers three tasks versus Legacy model, but remains far below Fair
heuristic: nine tasks lose current-layout coverage and 18 have worse top1
survival. It improves none of those metrics over Fair heuristic on any task.
All nine coverage losses are collection requests in the permission, toolbar
and editable-form scenarios. Every fresh task already has a survival-1 plan in
Original's candidate set, so this cohort cannot establish an increased quality
ceiling over Original. The complete rule pipeline has top1 survival **87.50%**,
also exposing a final-ranker generalization gap: Original plus the frozen tiny
ranker scores only 68.75% here.

The 24 first-experiment bottleneck tasks are reported only as previously seen
regression cases. They never participate in this round's development selection.

| Seen regression, 24 groups | Coverage | Candidate-best | Top1 | Top3-best |
| --- | ---: | ---: | ---: | ---: |
| Original | 62.50% | 50% | 25% | 37.50% |
| Legacy heuristic | 50% | 25% | 12.50% | 25% |
| Legacy model | 50% | 37.50% | 12.50% | 37.50% |
| Fair heuristic | 62.50% | 37.50% | 25% | 37.50% |
| Fair model | 50% | 25% | 12.50% | 25% |

Fair heuristic recovers the three previously missing dynamic-attribute
collections. Fair model does not recover them, and loses the best surviving
single-target candidate on three dynamic-attribute tasks versus Legacy model.
Their top1 was already zero in both model variants; the top3 loss remains real.

## Post-assessment coverage diagnosis

An opt-in diagnostic repeats only the nine fresh collection coverage losses,
with the frozen policy, grammar, features and original counter budgets. A zero
clock removes timing noise; these repetitions are not new benchmark or quality
observations. Both methods' 18 canonical candidate sets exactly reproduce the
saved assessment, and every search again ends `EXHAUSTED`.

All nine Fair heuristic reference plans replay legally, remain valid on the
capture, and have canonical plans identical to their reference. Replaying those
plans against the Fair model trace finds the first missing prefix at `OUTER`,
immediately after adding an outer `id` condition, on **all nine** tasks. Its
incoming model score is 0.31874065364536364 in six tasks and 0.3241989442904014 in
three. Already scored actions match their recorded policy scores exactly; the
forced remainder is a diagnostic counterfactual, not an actual completed model
trajectory or training example.

Each affected family has at most 13 possible anchor/phase buckets and 32 frontier
slots. Every live bucket can receive a slot, so missing complete anchor/phase
buckets cannot explain these losses. The valid ID prefix loses to other
prefixes **within the same anchor and phase**. This establishes a remaining
selection failure with the frozen policy, even after cross-bucket scheduling is
made fair. It does not establish whether a different supervised target, more
training coverage or better features will generalize; those remain controlled
development experiments for the next iteration. All diagnostic counts and
source/result bindings are included in the aggregate evidence.

## Reproducibility, limits and next work

The 196 development plus 76 assessment tasks produced **1,360** comparison runs;
30 training-task warmup runs are excluded. All comparisons completed without
errors or deadline stops. Maximum whole-run elapsed time was 418.213 ms on the
Windows/JDK desktop environment. These are one observation per task/method,
not Redmi measurements, a 5,000-node performance gate, or resident-memory tests.

Replaying the unchanged Original, Legacy heuristic and Legacy model on their
660 shared runs reproduces the first experiment's normalized candidate sets,
survival values and final rank positions exactly. Timing is not expected to be
byte-identical. Fair model stays within all original limits: peak frontier 64,
peak cursor anchors 24, at most 2,000 verification attempts, 6,478 action scores
and 791 expanded states across these runs. Pending successor peak is 1,501,
separately bounded by the action-score cap, not confused with the 64 retained
prefixes. Features, weights and grammar remain unchanged.

Validation completed with 484 passing JVM tests, 10 conditional skips and zero
failures/errors across codegen, selector filters, inspector and codegen UI
packages. Skips comprise seven explicit offline/network-independent jobs, two
existing Windows symlink tests and one public-download integration test. The
second experiment's freeze/develop/holdout jobs were separately executed, and
the final suite includes the actual 5,000-vector construction-policy parity
check and the 18 diagnostic replays. Python has 23 passing tests, including
actual repeated CatBoost fits, held-out-data isolation and nine selection-gate
checks. No model downloads, signing or phone operations were needed.

The present evidence supports keeping a bounded scheduling comparison and its
evaluation tools. It does not support replacing the default controller, shipping
the learned policy, or claiming that candidate construction now meets users'
expectations. There are still zero real-application assessment groups.

Next work remains within the existing roadmap entries: improve construction
training coverage and the relation between its supervised value and the search
objective, then evaluate the final ranker on the resulting candidate distribution.
Mean observed-rollout survival currently estimates average observed completion
quality, which may differ from the objective of retaining at least one strong
continuation. That is a training hypothesis, not a cause proven by this experiment.
Any changed label, feature or exploration policy must be compared on train/valid
first. Keep these inspected cohorts as regression evidence; reserve new tasks
before the next final assessment. Product integration and capability packaging
continue only after credible construction and end-to-end quality evidence.

Private experiment files remain under ignored `tools/codegen-model/out/`:
`construction-r2-protocol-2`, `construction-r2-development-1`, and
`construction-r2-holdout-1`, plus `construction-r2-diagnostics-1`. The protocol, selection and both result hashes are
recorded in the aggregate JSON. Raw snapshots, per-task selectors, trajectories,
and model weights are not committed.
