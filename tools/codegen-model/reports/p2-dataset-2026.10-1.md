# P2 controlled dataset acceptance (2026-10-03)

P2.1-P2.7 supply the reproducible data-production path for the dedicated ranker. This report contains aggregate controlled-fixture statistics only. No model has been trained in P2.

## Provenance and collection

- Dataset: `cg-ds-2026.10-1`.
- Device: Redmi 22120RN86C, Android API 33; fixture versionCode 1. The installed AutoJs6 host was checked as 6.8.0 (5317).
- Capture source commit: `5a3f74d9a33df3037c55e196979ea4fdde8da76b`.
- JVM exporter source commit: `5a3f74d9a33df3037c55e196979ea4fdde8da76b`.
- Collection: 2026-10-03T08:18:44+00:00 to 2026-10-03T08:40:07+00:00.
- Elapsed: 1283.743 s (21.40 min).
- States: 1,152 attempted, 1,152 accepted, 0 discarded (0.00%).
- Tasks: 2,688 groups (2,304 single, 384 collection). The seven tasks in each scenario share its three captures; there are 1,152 distinct snapshots.
- All 8 screen families have 48 seeds and 336 task groups. All nine mutation kinds are present.
- Every future state has an observable change in ID, class, text, description, action-related flags, bounds, or child structure. No private oracle key occurs as a node property value.

## Split and label coverage

| Split | Groups | Candidates | Families |
| --- | ---: | ---: | --- |
| train | 2,016 | 103,992 | dialog, grid, login, nested-scroll, no-id, tabs |
| valid | 336 | 16,752 | settings |
| test | 336 | 12,960 | list |
| real-test | 0 | 0 | - |

Entire screen families are isolated. Eight equally sized, indivisible families round to 6/1/1, so the recorded group ratios are 75% / 12.5% / 12.5%. The 70/15/15 target cannot meet a three-point tolerance with eight atomic families. No seed or state crosses a split.

All 2,688 groups have candidates; 0 groups have none. There are 133,704 verified capture-state candidate labels, with all five plan families represented.

| Candidate family | Labels |
| --- | ---: |
| ANCHOR_PATH | 57,408 |
| ANCHOR_SEARCH | 12,792 |
| CONTAINER_ITEMS | 10,104 |
| SELF | 7,560 |
| SELF_INDEX | 45,840 |

| Survival | Labels |
| --- | ---: |
| 0.0 | 30,307 |
| 0.5 | 61,812 |
| 1.0 | 41,585 |

2,662 groups contain candidates with different survival labels. Survival is measured by the unchanged host PlanVerifier against the two independently annotated future states; it is not a model probability.

## Verification

- Android fixture build and all 5 fixture unit tests passed (8 families, 48 seeds, every mutation and combinations).
- Redmi fixture smoke: 8 independently restarted pairs produced identical truth bytes; all 16 captures associated all 6 targets. Actual click, long-click, escaped set-text, scroll-forward, and scroll-backward receipts passed in 35.158 s.
- A real floating-menu inspector capture and the worker capture both contained 29 nodes with identical preorder class sequences.
- Joint JVM regression: 239 tests passed, including the actual 56-group pilot export and the reader/exporter tests.
- Python tools: 51 tests passed, with no skips, including explicit annotation, family isolation, stale-export checks, UTF-16 input-hash ordering, interrupted writes, timeout counters, and Windows junction containment.
- Full-dataset labels/rules/export-manifest were exported twice with the same input and source commit and compared byte for byte.
- Raw collection artifacts are ignored under `tools/codegen-model/out/`; local reports and logs under `build/` are also ignored.

## Artifacts

The local dataset directory contains `collection.json`, task groups, snapshots and independent truth, `labels.jsonl`, `rules.jsonl`, `export-manifest.json`, `split-map.json`, `dataset-manifest.json`, and `acceptance-summary.json`. Use [the tool README](../README.md) to reproduce the pipeline.

| Artifact | SHA-256 |
| --- | --- |
| labels.jsonl | `17d3798e3a680e0de4c26d5ddf1e85ee7bea00c0555761a86ab381f999bb0ad0` |
| rules.jsonl | `54570d23a1bc663332949c4ff6fb0638506381284f1eff3ca47beb8f606ec94f` |
| export-manifest.json | `168542df7fe158d0a54293376a20f9eea3481d684ff8d6aa9aa1ae08cc73f179` |
| dataset-manifest.json | `64c753ccab071d885e7d429e5547ba1b5ff64b5b3f95c6565a88b12fa24503bc` |

## Remaining roadmap work

`features.csv` and its checksum remain explicitly pending until P3.2 implements the Kotlin feature exporter. No empty feature file or fabricated checksum was created. Training, model evaluation, packaging, and Android inference remain subsequent P3/P4 work.

The real-app annotation tool is implemented and tested; this dataset has zero real application tasks. The suggested 30 apps / 150 confirmed real tasks have not been collected. Controlled-template success alone does not establish performance on real applications.
