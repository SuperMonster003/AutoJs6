# Third construction experiment: supervised target and collection coverage

Date: 2026-10-08. Scope: continued construction work within roadmap P1.2,
P2.4-P2.6, P3 and P4. **The preregistered development admission was rejected,
so the reserved holdout was not opened. Both new policies recover every
diagnosed coverage loss of the second experiment on the inspected regression
cohorts, but the frozen final ranker turns their larger candidate sets into
worse first recommendations. Do not promote any of these policies, and do not
proceed to P7 integration.**

The experiment changes training data only: a second teacher export carries two
supervised targets per state/action, samples single and collection intents with
separate quotas, and alternates both frontier controllers. The grammar, the 87
state/action features, the `ANCHOR_PHASE_FAIR` controller, the budgets, the
final ranker and the production engine are unchanged. No APK, signed model or
release is involved; the host receives local commits only.

Implementation commits: `0d0737f189` (second label semantics in the Python
tools and the Kotlin reader), `0b510f8dd0` (independently authored third
synthetic holdout), and the third experiment task, selection tool and regression
diagnostic in the commit that accompanies this report. Aggregate evidence is in
[construction-third-2026.10-1.metrics.json](construction-third-2026.10-1.metrics.json).
The [first](construction-first-2026.10-1.md) and
[second](construction-second-2026.10-1.md) reports remain the historical baselines.

## Hypotheses and what was changed

The second experiment's diagnostic located all nine fresh collection coverage
losses inside one anchor/phase bucket: the valid outer `id` prefix lost to its
siblings under the frozen policy, whose score for that prefix was about 0.32.
Two training-data hypotheses were tested together:

1. **Supervised target.** The mean observed return of a prefix is pulled down by
   its many poor continuations, whereas beam search needs to keep prefixes that
   still own at least one strong continuation. A format 2 export therefore adds
   `labelBest`, the maximum survival among the completed observed rollouts
   through a state/action. It is an observed lower bound of the best
   continuation, never an exact optimum, and never below the mean. `train.py
   --label best` fits it and declares `best-observed-continuation-v1`; the
   Kotlin reader accepts both declarations and exposes them. Neither target is
   summed across construction steps.
2. **Training coverage.** The first export took 28 tasks per screen family, so
   collection intents were one seventh of the rows, and only the `FAMILY_SCORE`
   controller produced trajectories. The third export takes 28 single and 28
   collection tasks per family and runs four teachers per task: the plain
   heuristic under each controller, then seeded exploration under each.

The test family stays reserved: the export contains the six training families
and the validation family only. Everything else that could influence the result
was frozen before any development run was evaluated.

## Export and fits

| Export fact | Value |
| --- | ---: |
| Tasks | 392 = 336 train (168 single + 168 collection) + 56 valid (28 + 28) |
| Teacher runs | 1,568 (785 `EXHAUSTED`, 783 `VERIFICATION_BUDGET`, no deadline or policy failure) |
| Observed state/action rows | 2,626,578 |
| Scored edges without a completed observation | 3,804,975 |
| Complete rollouts | 1,347,459, of which 1,039,904 valid at capture |
| `actions.csv` | 1,229,054,024 B, `3912823ed955bf5dbd749403a25e6bf06529b71466b8707cae3e4d624971555e` |
| `teacher-traces.jsonl` | 1,971,299,337 B, `d2a25fbb33815735cb634daf3e09895f658b1956e9bbdb0f22114edbb1762a8a` |
| `features.json` | 1,872 B, `28c74afb8aa9a95b5211c6c829c2d4e784191aa30435da6a64b463baa9bff954` (unchanged schema) |

The export took 8 min 44 s on the Windows/JDK desktop. Teacher budgets are the
first experiment's: 2,000 verifications, 200 retained candidates, 128 frontier
slots, 4,000 expansions, 24,000 action scores and 30 s per run.

Two policies were fitted from this one export with the existing pinned
CatBoost 1.2.10 / NumPy 2.2.6 / Python 3.12.5, depth 6, at most 256 iterations,
RMSE, one thread, seed 20261007, validation early stopping and equal row weights.
Only train fits; only valid selects the stopping point. Each fit was repeated
once; canonical model JSON, policy JSON and parity rows are byte-identical.

| Policy | Trees | Learn / valid RMSE | Fit seconds (repeat) | `policy.json` | SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| `fair_model_mean` (`observed-rollout-survival-v1`) | 144 | 0.18825 / 0.23623 | 79.92 (79.14) | 217,698 B | `98a09d2fd7f6ebefd47fe278fa1b3b708650a489e5913dc705ccd8d68cea6fc4` |
| `fair_model_best` (`best-observed-continuation-v1`) | 159 | 0.20071 / 0.24114 | 86.04 (81.34) | 244,396 B | `48f1014a0f850027a18ac46f567262dfe6be63f75b4035947cd6603c285bc1dd` |

Python parity keeps 5,000 observed vectors in 2,644 complete action groups with
a maximum Float32 score difference of 5.11e-9 (mean) and 7.36e-9 (best). The
Kotlin `ConstructionPolicyModelTest` parity check on the same files reproduces
those maxima and identical action orders for every state under the anchored
`1e-6` tie rule. Tree backing arrays hold 45,072 B (mean) and 49,767 B (best) of
numbers; these are not resident-memory measurements.

## Frozen protocol, development and the rejected admission

Five methods share the request, verifier, `LocatePlan` grammar, condition
minimizer, canonical comparison, counter budgets, the 800 ms search allocation,
the 1,000 ms whole-run deadline and the frozen tiny v1 final ranker
(`9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17`):

| Report name | Constructor | Next-action scoring |
| --- | --- | --- |
| Original (`baseline`) | Existing `CandidateSearch` | Existing enumeration |
| Fair heuristic | `ANCHOR_PHASE_FAIR` | Fixed heuristic |
| Fair model r2 (reference) | `ANCHOR_PHASE_FAIR` | Frozen second-round 59-tree policy |
| Fair model mean | `ANCHOR_PHASE_FAIR` | 144-tree policy, mean label |
| Fair model best | `ANCHOR_PHASE_FAIR` | 159-tree policy, best-continuation label |

The `FAMILY_SCORE` variants were not better in the second experiment and are not
repeated. Every row additionally reports the same candidates ordered by the
deterministic `RuleRanker` (`ruleTop1Survival`), a model-free second view of
whether constructed candidates reach the recommendation.

The protocol (SHA-256
`721aa3519e921c72f13a33c7a10a2bdf80c9fb1d6aa543007adc290dcb306452`) bound the
export, both fits, the reference policy bytes, the ranker bytes, all source
identities and all task memberships before any development run. Its selection
rule was preregistered: choose between the two new policies by validation
`top1Survival`, then `bestSurvival`, then `coverage`, ties preferring the
best-continuation label; admit the choice only if no validation measure falls
below the reference policy, at least one measure strictly improves on train or
valid, and every development run completes without an error or deadline stop.

Development ran the 392 exported tasks, 1,960 runs, without any error, deadline
or policy failure; the longest whole run took 363.66 ms.

| Development split | Method | Coverage | Candidate-best | Top1 | Top3-best | Rule-ordered top1 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Train, 336 | Original | 100% | 98.21% | 93.60% | 94.79% | 84.23% |
| Train, 336 | Fair heuristic | 100% | 89.58% | 84.08% | 84.08% | 84.08% |
| Train, 336 | Fair model r2 | 100% | 96.43% | 94.35% | 95.24% | 88.24% |
| Train, 336 | Fair model mean | 100% | 97.47% | 95.09% | 95.98% | 88.24% |
| Train, 336 | Fair model best | 100% | 97.02% | 93.60% | 94.05% | 89.14% |
| Valid, 56 | Original | 100% | 98.21% | 93.75% | 94.64% | 73.21% |
| Valid, 56 | Fair heuristic | 100% | 80.36% | 73.21% | 73.21% | 73.21% |
| Valid, 56 | Fair model r2 | 100% | 96.43% | 94.64% | 95.54% | 88.39% |
| Valid, 56 | Fair model mean | 100% | 97.32% | 93.75% | 96.43% | 88.39% |
| Valid, 56 | Fair model best | 100% | 97.32% | 93.75% | 94.64% | 88.39% |

The two new policies tie on every validation selection measure, so the
preregistered tie rule chose `fair_model_best`. The admission then failed on one
criterion: validation top1 survival 93.75% is below the reference's 94.64%. The
selection record (SHA-256
`324c97f1164c7993cd8acbe96d53eb466691535ac17b229f729cb02e9840d5c5`) keeps
`passed: false`, and no reserved task was evaluated.

The validation difference is five single-target `settings` tasks, identical for
both new policies: three Bluetooth/Wi-Fi tasks drop from 1.0 to 0.5 and two rise
from 0.5 to 1.0, a net loss of one half task out of 56. In all five tasks the
candidate set already contains a survival-1 plan; the new policies return more
candidates (11-19 instead of 9-10) and the frozen final ranker picks a worse
first candidate from the larger set. Candidate-best survival improves on one
validation task and never worsens. On the 28 validation collection tasks every
model constructor reaches 100% on every measure, including the rule-ordered
view, while the original search ordered by rules reaches 69.64%.

The reference policy was early-stopped on the first export's 28 validation tasks,
which are drawn from the same `settings` family as this validation split; its
validation figure may therefore be slightly favored. The admission rule was
preregistered and is not relaxed for that reason.

## Regression diagnostic on inspected cohorts

Because the admission failed, the fresh cohorts (28 captured tasks with snapshot
clusters unused by every construction experiment, and the 24 newly authored
`ConstructionThirdSyntheticFixtures` tasks) remain unopened. A separate
`ConstructionThirdRegressionDiagnosticsTask` evaluated the five frozen methods
only on cohorts that earlier experiments had already inspected. These are
regression evidence about the diagnosed cases, not independent generalization
evidence and not an admission. All 76 tasks stay in every denominator.

| Seen generalization (second fresh cohort), 24 | Coverage | Candidate-best | Top1 | Top3-best | Rule-ordered top1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original | 100% | 100% | 68.75% | 87.50% | 87.50% |
| Fair heuristic | 100% | 81.25% | 81.25% | 81.25% | 81.25% |
| Fair model r2 | 62.50% | 18.75% | 18.75% | 18.75% | 18.75% |
| Fair model mean | 100% | 68.75% | 50.00% | 66.67% | 62.50% |
| Fair model best | 100% | **87.50%** | 62.50% | 79.17% | 68.75% |

Both new policies recover all nine collection coverage losses of the second
experiment. The best-continuation policy raises the candidate quality ceiling
on 21 of 24 tasks against the reference and on 3 against the heuristic, never
lowering it against the heuristic; its mean candidate-best survival exceeds the
heuristic's. Its first recommendation is still below the heuristic on six tasks:
the three permission-state collections now hold two candidates each, of which the
frozen final ranker picks the one that fails later, while the heuristic's single
candidate survives. The complete rule pipeline (original search plus
`RuleRanker`) remains the best end-to-end method on this cohort at 87.50%.

| Seen bottleneck (first synthetic cohort), 24 | Coverage | Candidate-best | Top1 | Top3-best |
| --- | ---: | ---: | ---: | ---: |
| Original | 62.50% | 50.00% | 25.00% | 37.50% |
| Fair heuristic | 62.50% | 37.50% | 25.00% | 37.50% |
| Fair model r2 | 50.00% | 25.00% | 12.50% | 25.00% |
| Fair model mean | 62.50% | 37.50% | 25.00% | 37.50% |
| Fair model best | 62.50% | 37.50% | 25.00% | 25.00% |

Both new policies match the heuristic here and recover the three
dynamic-attribute collections the reference lost. The original search keeps a
higher ceiling on three tasks. One run reached a budget edge: on the 4,003-node
`nearby-anchor` seed 3 collection the best-continuation policy spent its whole
800 ms allocation (`DEADLINE`, zero candidates, counted as zero quality), and on
the matching single task the heuristic and the mean policy hit the 12,000
action-score cap. The D19 time budget is therefore not yet established for the
new policies on large captures.

| Seen captured (second captured holdout), 28 | Coverage | Candidate-best | Top1 | Top3-best | Rule-ordered top1 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original | 100% | 92.86% | 87.50% | 87.50% | 37.50% |
| Fair heuristic | 100% | 73.21% | 42.86% | 73.21% | 57.14% |
| Fair model r2 | 100% | 87.50% | 87.50% | 87.50% | 87.50% |
| Fair model mean | 100% | 87.50% | 87.50% | 87.50% | 76.79% |
| Fair model best | 100% | 87.50% | 87.50% | 87.50% | 85.71% |

All three learned policies tie on every ranked measure here; only the
rule-ordered view separates them.

## Reproducibility, limits and next work

- Development and regression runs stay within the frozen limits: peak frontier
  64, at most 2,000 verification attempts, 12,000 action scores (the cap was
  reached), 1,347 expanded states, pending successors at most 1,501 and 24 fair
  cursor anchors. Policy loads on the desktop JVM took 34.19 ms (reference,
  first load), 10.54 ms (mean) and 10.93 ms (best).
- Timing is one desktop observation per task/method, not a Redmi measurement, a
  5,000-node performance gate or a resident-memory test. There are still zero
  real-application groups.
- The export reproduces the first export's schema bytes; both fits reproduce
  byte-for-byte on repeat. The export itself was not repeated this round.
- Validation: the final host JVM regression over the codegen, selector filter,
  inspector and codegen UI packages ran **481 cases, 469 passed, 0
  failures/errors, 12 skipped** (ten opt-in offline export/evaluation/diagnostic/
  parity/benchmark jobs and two Windows symlink-permission cases). The third
  export, freeze, development run, regression diagnostic, both parity checks and
  the third fixture tests were executed explicitly. The construction Python
  tests pass with 35 cases, including the two opt-in real fits.

The evidence supports keeping the second label, the per-scope sampling and the
two-controller teacher as development tools. It does not support replacing the
reference policy, shipping any learned policy, or claiming that construction
meets users' expectations. The remaining gap is now concentrated in the final
ranking of enlarged candidate sets: the frozen tiny v1 ranker was fitted on the
original search's candidate distribution and prefers plans that the constructors'
new candidates outlive. Next work stays within the existing roadmap entries:
refit and re-evaluate the terminal ranker on candidates from both the rule search
and the constructors (P3.3-P3.4), bound construction time on large captures
(D19), then freeze a new protocol. The 28 captured and 24 synthetic reserved
tasks were never evaluated and remain available as that protocol's holdout; the
cohorts inspected here must stay regression-only.

Private experiment files remain under ignored `tools/codegen-model/out/`:
`construction-r3-export-1`, `construction-r3-fit-mean-1`,
`construction-r3-fit-best-1`, their `-repeat-1` directories,
`construction-r3-protocol-1`, `construction-r3-development-1` and
`construction-r3-regression-1`. Raw snapshots, per-task selectors, trajectories
and model weights are not committed.
