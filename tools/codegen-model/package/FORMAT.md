# Local model package format 1

`build_package.py` uses Python's standard library. It accepts only numeric,
single-output CatBoost symmetric-tree JSON, a Kotlin-produced feature schema,
the corresponding dataset manifest/training log/evaluation metrics, and explicit
license text plus a caller-provided SPDX expression. Packaging does not publish
a model, grant official trust, or establish that release gates have passed.

## Binary model

All fields use little-endian byte order. No alignment or padding is inserted.

| Offset or section | Type | Meaning |
| --- | --- | --- |
| 0 | 4 bytes | ASCII `AJCG` |
| 4 | `u16` | Binary format version, 1 |
| 6 | `u16` | Numeric feature count, 1..65,535 |
| 8 | `u32` | Tree count, 1..4,096 |
| 12 | `f64` | Bias |
| 20 | `f64` | Scale |
| Each tree | `u8` | Depth, 1..8 |
| Each tree split, repeated depth times | `u16`, `f32` | Flat feature index, border |
| Each tree leaf, repeated `2^depth` times | `f32` | Leaf value, in CatBoost order |
| Last 4 bytes | `u32` | CRC32 of every preceding byte, standard ZIP/zlib CRC32 |

The header is 28 bytes. Total length is `32 + sum(1 + 6*d + 4*2^d)`.
At the maximum tree count/depth this is 4,395,040 bytes. Declared counts must
consume the file exactly, including the final checksum; trailing data is invalid.

For split `i`, the bit is 1 exactly when `feature > border`. Equality takes bit 0.
Bit `i` contributes `1 << i` to the leaf index. Evaluate trees in stored order,
accumulating their float32 leaf values in float64, then return
`bias + scale * sum`. Negative scale and bias are valid. Inputs are float32;
the desktop `score_model` helper performs the same conversion before comparison.
Non-finite inputs, borders, leaves, scale, and bias are rejected. Float32
overflow is rejected instead of silently producing infinity.

CatBoost split `float_feature_index` refers to a float feature's `feature_index`.
The writer builds an explicit map from that field to `flat_feature_index` and
checks the flat column's `feature_id` against the ordered schema name. Array
position is not treated as a feature index. Internal and flat indices must each
cover the feature dimension exactly. Categorical/CTR/text/embedding data,
non-symmetric trees, NaN-dependent feature routing, and multiple outputs are
unsupported and rejected.

`encode_model(model_json, schema)` returns these bytes; `decode_model(bytes)`
returns `BinaryModel(feature_count, bias, scale, trees)`. A `BinaryTree` contains
tuples `features`, `borders`, and `leaves`. `score_model(model, features)` provides
the standard-library reference computation for desktop parity checks.

## Package container

An `.ajcgm` file is a ZIP with these five required entries, in this order:

1. `manifest.json`
2. `model.bin`
3. `features.json`
4. `MODEL_CARD.md`
5. `LICENSE`

The writer uses ZIP_STORED, timestamp `1980-01-01 00:00:00`, Unix regular-file
mode 0644, fixed creator system, no archive comment, and no additional entries.
Stored entries have compression ratio 1, so highly repetitive numerical models
cannot accidentally exceed the reader's maximum ratio of 200. These choices also
avoid output differences between zlib versions. Entry names contain no paths.
The reader also supports bounded compressed packages, but the writer deliberately
produces the more reproducible stored form.

Total uncompressed size is limited to 100 MiB. `model.bin` is limited to the
maximum binary size above; `features.json` is limited to 256 KiB; each other entry
is limited to 1 MiB. Text is UTF-8, JSON keys must be unique, and JSON numeric
values must be finite. Feature schema bytes and explicit license bytes are copied
without modification. The writer refuses to overwrite an existing package.

## Manifest fields

All roadmap E.2 fields are required:

| Field | Definition |
| --- | --- |
| `format` | Integer 1 |
| `modelId` | Matches `[a-z0-9][a-z0-9._-]{1,63}` |
| `displayName` | Language-to-name map with at least `en` |
| `versionCode`, `versionName` | Positive signed-32-bit integer; bounded nonempty string |
| `kind`, `algorithm` | `ranker`, `catboost-oblivious` |
| `featureSchemaVersion`, `featureNamesHash` | Exact values from `features.json` |
| `model` | `{file: "model.bin", size, sha256, trees, depth, features}`; depth is maximum depth |
| `dataset` | `{datasetId, groups, createdAt}`; groups counts training task groups with candidates |
| `metrics` | `test` and `realTest`, each `{top1Survival, ruleTop1Survival, regret, groups}` |
| `training` | `{catboostVersion, pythonVersion, hostCommit, trainedAt}` |
| `minHostVersionCode` | Positive signed-32-bit integer |
| `license`, `publisher` | Explicit caller-provided SPDX expression and publisher name |

Metric groups are nonnegative. When groups is zero all three values are null;
otherwise all are finite within `[0, 1]`. Empty real-test results therefore make
no claim about real-application performance. Dataset `createdAt` comes from the
dataset manifest's actual `generatedAt`; `trainedAt` comes from the training
log's actual `completedAt`; no package-build date substitutes for either.
`hostCommit` is the training log's `exportGeneratorCommit`.

Strings have at most 1,024 UTF-16 code units, with no control characters;
display names, version name, license expression, and publisher have at most 256.
Timestamps require ISO-8601 dates and times with an explicit UTC offset no larger
than 18 hours. Display-name keys are bounded language tags. Manifest JSON uses
sorted keys, two-space indentation, a final LF, and no platform newline rewriting.

## Provenance and licensing

Before writing, the tool checks that the model JSON's hash is a recorded training
artifact and that metrics refer to that run's recorded CBM or JSON. Dataset ID,
manifest/schema hashes, schema version/name hash, split coverage, training group
counts, and model dimensions must agree across the supplied records. Thus a report
for a different trained model or dataset cannot silently accompany a package.
This is consistency checking, not a signature or proof of an untrusted publisher's
claims. Official distribution verification is a separate roadmap phase.

The caller supplies both `--license-file` and `--spdx`; the tool does not choose
a license for a new model. Local evaluation may use an explicit LicenseRef and
its accompanying local-use terms. Repository test fixtures may use the existing
repository license when authorized. Neither choice grants a license to future
official models automatically. `MODEL_CARD.md` contains only aggregate facts,
intended use, license reference, measured metrics, and limitations; it contains
no layout snapshots, raw interface strings, selectors, or task IDs.
