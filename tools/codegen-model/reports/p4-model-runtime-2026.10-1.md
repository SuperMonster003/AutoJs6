# P4 model package and Kotlin runtime acceptance (2026-10-03)

P4.1-P4.6 implement a reproducible compact model package, bounded reader, immutable Kotlin tree interpreter, verified-candidate ranker, CatBoost parity and an opt-in JVM benchmark. There is no Android ML runtime dependency. Model selection/download/UI integration remains P5-P7 work.

## Artifacts and provenance

- Source model: P3 controlled-only six-tree ranker, schema 1, 64 features, names hash `9c6a5d8279ffca0e`.
- Candidate metadata still records the original dataset, feature-export commit, training versions/date and the held-out metrics. Real-test has zero groups and null metrics.
- Both candidate and tiny packages were constructed twice from identical inputs and compared byte for byte.
- ZIP_STORED, fixed entry order, fixed timestamp and fixed permissions make package bytes independent of zlib compression versions.

| Artifact | Size (bytes) | Notes |
| --- | ---: | --- |
| model.bin | 1,790 | Six trees, maximum depth 6; little-endian Float32 splits/leaves, Double bias/scale, CRC32 |
| local-candidate-v1.ajcgm | 6,922 | Unpublished local evaluation notice; formal distribution license not selected |
| tiny-test-v1.ajcgm | 23,633 | MPL-2.0 repository test artifact, below 64 KiB |

The tiny model reuses the P3 model learned entirely from controlled fixture tasks. It is stored with 152 CatBoost reference candidates and four complete groups under `app/src/test/resources/codegen/models/`. Local Git attributes preserve the binary package and LF hashes. The full candidate/reference output stays under ignored `tools/codegen-model/out/p4/`.

| Package | SHA-256 |
| --- | --- |
| local-candidate-v1.ajcgm | `712baca7e1a7947eaba45823731873b8a075d52311a52578cd6349b0e0c86d78` |
| tiny-test-v1.ajcgm | `9ab137a6e184f52246768a905ea98a09a03cf4da97f221526d70e7b6f32afc17` |

## Reader and scoring behavior

- All five entries are required. The loader checks central/local ZIP records, exact boundaries, compression method/ratio, CRC, size limits, UTF-8/strict JSON, metadata and schema compatibility, SHA-256 and every binary tree field.
- A bounded disk snapshot allows full archive validation without retaining an untrusted ZIP in heap. Model binary size is further bounded by the format maximum of 4,395,040 bytes; card/license are streamed. Android callers supply the current host version and an existing writable application cache directory.
- Normal tree scoring uses only scalar variables and loops. The constructor checks and copies arrays; callers cannot mutate model data. Scores use strict `>` borders, split-bit order and `bias + scale * sum`.
- ModelRanker preserves original Verified objects and candidate membership. The 150 ms default budget covers feature extraction, inference and sorting. Expected failures return RankerUnavailable for P7 fallback; cancellation continues to propagate.
- Near-score ties match P3: descending maximum-anchored batches with differences below 1e-6, then deterministic rule order.

## Parity and tests

| Check | Candidates | Complete groups | Maximum absolute score difference | Top choices |
| --- | ---: | ---: | ---: | --- |
| Full Kotlin package versus CatBoost | 5,012 | 130 | 9.110787901889239e-9 | All identical |
| Bundled tiny Kotlin package versus CatBoost | 152 | 4 | 9.110787901889239e-9 | All identical |

The full sample starts with the requested 5,000 test candidates and includes the final group's remaining 12 candidates. Completing that group makes every top-choice check meaningful. The score tolerance is 1e-4. This verifies the actual Kotlin reader/interpreter on the JVM; it is not an Android-device benchmark.

- Combined JVM run: 295 passed, zero failures/errors; the two P2 offline data/queue jobs were skipped as expected. Full parity and the opt-in benchmark both ran.
- Python tools with actual CatBoost integrations enabled: 115 passed, zero failures/skips.
- Reader: 20 tests, including 2,000 seeded byte mutations, dishonest declared sizes, actual streaming limits and temporary-directory cleanup.
- Model scoring/ranking: 16 tests, including hand-calculated trees, equal-border routing, negative scale/bias, immutable arrays, concurrent reads, candidate preservation, near-tie chains and timeout/error/cancellation handling.
- Packaging: 13 tests include an independently specified two-tree 76-byte hex golden. Parity exporter: six tests include Float32 boundary conversion and top-choice rejection even when score error is within tolerance.

## JVM performance measurements

Environment: Windows 11, Intel Core i5-12400, JDK 21.0.1. Fixed seed 20261003, five warmups and 30 measured runs. The benchmark uses 1,024 depth-6 trees and 200 independently verified plans in a 5,000-node tree.

| Stage | P50 (ms) | P95 (ms) |
| --- | ---: | ---: |
| Warm package load | 8.3208 | 9.8535 |
| 200-candidate feature extraction | 11.6705 | 13.9883 |
| 200-candidate scoring | 5.5798 | 17.8159 |
| Features plus scoring | 17.4368 | 29.8281 |

First package load: 69.3239 ms. Backing-array data: 320,512 bytes (313 KiB); binary: 300,064 bytes. The array figure excludes object headers, temporary parsing buffers and feature contexts. Raw samples are preserved in the companion metrics report.

P8.2 will measure Redmi loading, scoring and resident-memory budgets. The current PC numbers are not used to claim those device gates passed.

## Maintainer inputs for subsequent stages

- Select the formal model distribution license and its LICENSE text before publishing a candidate. The local evaluation notice makes no distribution choice; the bundled repository test package already uses MPL-2.0.
- P5 requires maintainer-owned offline signing keys and maintainer execution of signing/workflow dispatch, plus a chosen fork or private repository for the release-workflow acceptance run. Private keys remain with the maintainer.
- Real-app evaluation data remains outstanding: the roadmap suggests at least 30 applications and 150 manually confirmed task groups, with at least three states each. This work did not collect real app pages.
- No additional hardware or manual phone operation was needed for P4. The existing Redmi remains the planned P8 test device.

Commands are documented in [the tool README](../README.md); package fields in [the package format](../package/FORMAT.md); machine-readable evidence in [the companion report](p4-model-runtime-2026.10-1.metrics.json).
