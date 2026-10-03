# P3 first dedicated ranker acceptance (2026-10-03)

P3.1-P3.6 now cover Kotlin feature export, reproducible local fitting, held-out evaluation, and a private active-learning queue. These results are aggregate controlled-fixture measurements; they do not establish performance on real applications.

## Dataset and feature boundary

Dataset `cg-ds-2026.10-1` has 2,688 task groups and 133,704 verified candidates. The 64 schema-1 features have name hash `9c6a5d8279ffca0e`. Only Kotlin computes the vectors; future states supply labels and never affect capture-state features.

| Split | Groups | Candidates | Screen families |
| --- | ---: | ---: | --- |
| train | 2,016 | 103,992 | dialog, grid, login, nested-scroll, no-id, tabs |
| valid | 336 | 16,752 | settings |
| test | 336 | 12,960 | list |
| real-test | 0 | 0 | - |

All seeds and states of a screen family remain together. The eight equally sized families use the previously documented 6/1/1 rounding. The entire `list` family is held out from both fitting and validation; the reported test result is for that family.

## Training and artifact sizes

- Machine: Windows 11 x64, Intel Core i5-12400, 6 cores / 12 logical processors. Fitting uses one CPU thread.
- Runtime: Python 3.12.5, CatBoost 1.2.10, NumPy 2.2.6.
- YetiRank, symmetric depth-6 trees, learning rate 0.05, seed 20261003, maximum 1,024 rounds. Validation NDCG@1 stops after 64 non-improving rounds. Test/real-test never participate in fitting or early stopping.
- The fit evaluated 70 rounds and retained the best 6 trees (zero-based best iteration 5). Both full fits retained the same model.
- Full fitting times: 14.284951 s and 15.420205 s, excluding dataset validation/import.
- Capture source commit: `5a3f74d9a33df3037c55e196979ea4fdde8da76b`; feature-export source commit: `166910c968fe3f95e00a6a49cff4ce617d8feb72`.

| Artifact | Bytes | KiB |
| --- | ---: | ---: |
| model.cbm | 21,536 | 21.031 |
| model.json | 46,518 | 45.428 |
| model.raw.json | 57,994 | 56.635 |
| features.json | 1,268 | 1.238 |

Canonical `model.json` is byte-identical across both fits. The original JSON differs only in `model_info.model_guid` and `model_info.train_finish_time`; both originals are retained. Canonicalization removes those metadata fields only and leaves splits, borders, leaf values, feature definitions and scale/bias untouched.

Canonical model SHA-256: `cf70fe937ff4f087af7eb063ab3dbbbd691cb9e9b0c0bcc7befaa0da2a2df045`.
Feature CSV SHA-256: `6befb941a13dfffad95fb9bf91a0df67dbd3e98491fcbe12b7a9583874ba3c40`.

## Held-out metrics

| Metric | Model / test | Rule baseline / test |
| --- | ---: | ---: |
| Mean top-1 survival | 0.876488095 | 0.328869048 |
| Regret relative to best available candidate | 0.049107143 | - |
| Perfect survival fraction | 0.752976190 | - |
| Fraction worse than rule by more than 0.01 | 0.000000000 | - |
| Top-3 contains a best-survival candidate | 0.901785714 | - |
| Scorable / total groups | 336 / 336 | 336 / 336 |

Mean top-1 survival improves by 54.761905 percentage points. No test group is worse than the rule baseline, so the regression list is empty. Survival is the observed fraction of two future states, not a predicted success probability.

| Mutation bucket | Groups | Model survival | Rule survival |
| --- | ---: | ---: | ---: |
| delete | 112 | 0.888392857 | 0.169642857 |
| insert | 105 | 1.000000000 | 0.385714286 |
| locale | 112 | 0.857142857 | 0.446428571 |
| rename-ids | 112 | 0.888392857 | 0.348214286 |
| retext | 112 | 0.883928571 | 0.415178571 |
| shuffle | 112 | 0.629464286 | 0.294642857 |
| sibling | 112 | 1.000000000 | 0.321428571 |
| strip-ids | 112 | 0.883928571 | 0.142857143 |
| wrap | 119 | 0.865546218 | 0.432773109 |

Mutation buckets overlap: each contains tasks with that mutation in an evaluation state. They summarize whole-task survival and do not isolate a causal effect of one mutation in a combined state. The model selected SELF in 96 test groups and SELF_INDEX in 240. The detailed aggregate JSON also contains family/screen/source breakdowns.

## Reproducibility and workflow verification

- Kotlin feature/label export run twice: all five artifacts were byte-identical. P2 labels and rule-baseline hashes remained unchanged.
- JVM regression: 256 regular tests passed; the 2 explicitly enabled offline jobs were skipped in the ordinary run and then both executed successfully against full features and synthetic private queue inputs.
- Python 3.12 test suite with actual CatBoost integrations enabled: 96 tests passed, no failures or skips.
- Actual CBM versus canonical JSON prediction comparison on 12,960 test candidates: maximum score difference 1.1102230246251565e-16; all 336 top choices agreed. This is desktop format verification, not the future Android interpreter test.
- Active-learning integration: 40 synthetic private single-state tasks produced 5 queued disagreements at score-gap threshold 0.1; 11 had the same top choice and 24 exceeded the gap threshold. No target or future truth was changed. These controlled test copies are not real-app data.
- Model, raw exports, vectors and detailed local reports remain under ignored `out/`; only aggregate reports and tools are committed. P2 manifests are preserved locally in the dataset history directory.

## Scope and next roadmap stage

The real-app annotation/queue tools are operational, but `real-test` has zero groups and null metrics. The suggested 150 real tasks have not been collected. Real-app publication gates cannot yet be evaluated, and these controlled results must not be described as general app accuracy.

P4 remains responsible for the compact model package, Kotlin inference, CatBoost-to-Android parity, and inference/load benchmarks. The model is a local candidate and has not been installed into the host generation path or published to a model catalog.

Reproduce the commands with [the tool README](../README.md). Numeric aggregates are in [the companion metrics file](p3-first-model-2026.10-1.metrics.json).
